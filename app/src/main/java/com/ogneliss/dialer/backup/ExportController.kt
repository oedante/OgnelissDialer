package com.ogneliss.dialer.backup

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallLogRepository
import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.data.NumberNote
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.di.DefaultDispatcher
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выгрузки и резервные копии (1.3.4, фаза 4 — деление DialerViewModel).
 *
 * Было: всё это жило в DialerViewModel (3085 строк, 26 зависимостей).
 * Стало: отдельный контроллер по образцу ContactActionsController —
 * без состояния экрана: заметки, отметки и контакты ViewModel передаёт
 * параметрами. ViewModel только делегирует, экраны не менялись.
 */
@Singleton
class ExportController @Inject constructor(
    private val app: Application,
    private val callLogRepo: CallLogRepository,
    private val backupManager: BackupManager,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    /** Текстовая выгрузка истории одного абонента. null — не удалось. */
    suspend fun historyText(group: CallLogGroup, note: NumberNote?, spamMark: SpamMarkType?): String? =
        suspendRunCatching {
            withContext(defaultDispatcher) {
                val entries = callLogRepo.historyFor(group.number, limit = EXPORT_LIMIT)
                SubscriberHistoryExport.asText(
                    displayName = group.displayName,
                    number = group.number,
                    entries = entries,
                    note = note,
                    spamMark = spamMark
                )
            }
        }.getOrElse {
            Logger.e(TAG, "Не удалось собрать выгрузку по абоненту", it)
            null
        }

    /** Журнал вызовов в CSV. null — не удалось. */
    suspend fun callLogCsv(contacts: List<Contact>): String? = suspendRunCatching {
        withContext(defaultDispatcher) {
            val entries = callLogRepo.loadEntries(limit = CSV_EXPORT_LIMIT, offset = 0)

            // Имена подставляются из книги: в журнале системы их нет,
            // а в таблице «8 (918) …» без имени бесполезен
            val names = buildMap {
                contacts.forEach { contact ->
                    contact.numbers.forEach { put(it.value, contact.displayName) }
                }
            }

            CallLogCsvExport.asCsv(entries = entries, nameByNumber = names)
        }
    }.getOrElse {
        Logger.e(TAG, "Не удалось собрать выгрузку журнала", it)
        null
    }

    fun callLogCsvFileName(): String = CallLogCsvExport.suggestedFileName()

    fun historyFileName(group: CallLogGroup): String =
        SubscriberHistoryExport.suggestedFileName(group.displayName, group.number)

    fun backupFileName(): String = backupManager.suggestedFileName()

    suspend fun exportBackup(uri: Uri): Boolean = backupManager.export(uri)

    suspend fun importBackup(uri: Uri): Boolean = backupManager.import(uri)

    suspend fun writeText(uri: Uri, text: String): Boolean =
        withContext(defaultDispatcher) {
            runCatching {
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: return@runCatching false
                true
            }.getOrElse {
                Logger.e(TAG, "Не удалось записать файл", it)
                false
            }
        }

    /*
     * ПУСТОЙ ФАЙЛ ПОСЛЕ НЕУДАЧНОЙ ВЫГРУЗКИ (2.1.0).
     *
     * Окно «Сохранить как» создаёт файл ещё до записи. Было: запись
     * не удалась — внизу «Не удалось сохранить файл», а пустой файл
     * оставался в папке. Стало (правило автора 07.11.2026 «не сообщать —
     * выполнить или пропустить»): сообщения нет, а пустышка удаляется,
     * чтобы потом не принять её за выгрузку. Причина — в журнале
     * разработчика (её пишет тот, кто не смог записать).
     */
    suspend fun discard(uri: Uri) {
        withContext(defaultDispatcher) {
            suspendRunCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
                .onSuccess { Logger.i(TAG, "Пустой файл неудачной выгрузки удалён: $it") }
                .onFailure { Logger.w(TAG, "Пустой файл выгрузки не удалился", it) }
        }
    }

    /** Файлы журнала приложения одним текстом, новые сверху. */
    suspend fun logText(): String? = suspendRunCatching {
        withContext(defaultDispatcher) {
            Logger.files()
                .sortedBy { it.name }
                .reversed()
                .joinToString("\n") { it.readText() }
        }
    }.getOrNull()

    companion object {
        private const val TAG = "Export"

        /** Предел записей в выгрузке журнала. */
        const val CSV_EXPORT_LIMIT = 10_000

        /**
         * Предел записей в выгрузке.
         *
         * Системный журнал хранит ограниченную историю, и упереться
         * в этот предел на одном абоненте почти невозможно. Ограничение
         * здесь на случай, если провайдер отдаст неожиданно много:
         * файл на десятки тысяч строк никому не нужен, а собирать его
         * долго.
         */
        const val EXPORT_LIMIT = 1000
    }
}
