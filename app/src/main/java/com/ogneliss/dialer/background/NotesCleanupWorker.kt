package com.ogneliss.dialer.background

import com.ogneliss.dialer.concurrency.suspendRunCatching

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ogneliss.dialer.data.ContactsRepository
import com.ogneliss.dialer.data.NotesRepository
import com.ogneliss.dialer.data.NotesRetention
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.settings.Prefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * ОЧИСТКА УСТАРЕВШИХ ЗАМЕТОК.
 *
 * ── Почему фоновой задачей, а не при запуске ───────────────────────────────
 * Удаление данных пользователя не должно происходить в момент, когда он
 * открыл приложение и чего-то ждёт. Плюс операция затрагивает базу
 * целиком, и делать её на горячем пути запуска незачем.
 *
 * WorkManager выполнит её тогда, когда телефон свободен, и повторит,
 * если процесс прервали.
 *
 * ── Раз в сутки, а не чаще ─────────────────────────────────────────────────
 * Заметки устаревают месяцами. Проверять чаще — тратить батарею
 * на задачу, результат которой меняется раз в тридцать дней.
 *
 * ── Что не удаляется никогда ───────────────────────────────────────────────
 * Заметки с метками «спам» и «мошенники»: их писали именно затем,
 * чтобы помнить долго. Логика в NotesRetention, здесь только исполнение.
 */
@HiltWorker
class NotesCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notes: NotesRepository,
    private val blockedCalls: com.ogneliss.dialer.data.BlockedCallLog,
    /** Имена от оператора (1.8.0): старше года забываются здесь же. */
    private val networkNames: com.ogneliss.dialer.data.NetworkNames,
    private val contacts: ContactsRepository,
    private val prefs: Prefs
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        /*
         * Заодно чистим причины блокировки и имена от оператора — ДО проверки
         * срока заметок (1.8.0).
         *
         * Отдельная фоновая задача ради одной таблицы не окупается: каждая
         * стоит пробуждения процесса, а работы здесь на миллисекунды.
         *
         * Было: эта очистка стояла после выхода «заметки не удалять» — а это
         * умолчание. У большинства причины блокировки не чистились вовсе,
         * хотя срок им — 60 дней. Сроки этих таблиц от заметок не зависят.
         */
        suspendRunCatching { blockedCalls.purgeOlderThan() }
            .onFailure { Logger.w(TAG, "Не удалось очистить причины блокировки", it) }
        suspendRunCatching { networkNames.purgeOlderThan() }
            .onFailure { Logger.w(TAG, "Не удалось очистить имена от оператора", it) }

        val period = prefs.notesRetention.first()
        if (period == NotesRetention.Period.NEVER) {
            return Result.success()
        }

        return suspendRunCatching {
            val scope = prefs.notesRetentionScope.first()
            val allNotes = notes.all.first()

            // Номера из книги нужны, чтобы отличить разовые заметки
            // от заметок о людях, с которыми есть живая связь
            val knownNumbers = contacts.loadAll()
                .flatMapTo(HashSet()) { c -> c.numbers.map { it.e164 } }

            val toDelete = NotesRetention.selectForDeletion(
                notes = allNotes,
                period = period,
                scope = scope,
                knownNumbers = knownNumbers
            )

            toDelete.forEach { notes.delete(it) }

            if (toDelete.isNotEmpty()) {
                Logger.i(TAG, "Удалено устаревших заметок: ${toDelete.size}")
            }
            Result.success()
        }.getOrElse {
            Logger.e(TAG, "Не удалось очистить заметки", it)
            // Повтор имеет смысл: сбой мог быть временным,
            // а данные от этого не пострадают
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NotesCleanup"
        private const val WORK_NAME = "notes_cleanup"

        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<NotesCleanupWorker>(
                    1, TimeUnit.DAYS
                ).setConstraints(
                    Constraints.Builder()
                        // Только когда телефон не занят: очистка не срочная,
                        // и мешать пользователю ради неё незачем
                        .setRequiresBatteryNotLow(true)
                        .build()
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure { Logger.w(TAG, "Не удалось поставить очистку заметок", it) }
        }
    }
}
