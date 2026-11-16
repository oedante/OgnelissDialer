package com.ogneliss.dialer.data

import android.content.Context
import com.ogneliss.dialer.data.db.AppDatabase
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.concurrency.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/**
 * ПЕРЕНОС НА НОВЫЙ ТЕЛЕФОН (2.7.1).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО РЕШИЛ АВТОР
 * ══════════════════════════════════════════════════════════════════════════
 * 2.7.0 закрыла перенос совсем (обзор безопасности: правило исключало
 * только домен root, и уходило всё). Автор, 16.11.2026, вариант «Б»:
 * при копировании «телефон на телефон» данные человека переезжают,
 * служебное — нет; в облако — ничего. Копирование идёт напрямую со старого
 * телефона на новый (кабель или Wi-Fi), без интернета.
 *
 * ── Что переезжает (res/xml/data_extraction_rules.xml) ─────────────────────
 * База (заметки, отметки, чёрный и белый списки, напоминания, черновики,
 * избранные SMS), настройки (DataStore: ogneliss_prefs, speed_dial)
 * и настройки SMS (sms_settings). Не переезжают: журнал работы (logs),
 * выгрузки (share), ожидающие MMS, счётчики уведомлений, шаг экрана
 * входящего, отметка о сбое — это про старый телефон.
 *
 * ── Что убирается здесь, на новом телефоне ─────────────────────────────────
 * Правило переноса работает целыми файлами: из базы и настроек не выбрать
 * отдельные строки. Поэтому при первом запуске после переноса:
 * • отложенные SMS — удаляются: их отправит старый телефон, иначе одно
 *   сообщение ушло бы дважды (BootReceiver и SmsQueue отправили бы
 *   просроченное сразу);
 * • архив журнала, сведения о SIM, псевдозвонок — Prefs.forgetDeviceBound;
 * • кэш контактов и T9-индекс — по номерам записей адресной книги, на новом
 *   телефоне они другие; индекс собирается заново (T9IndexWorker);
 * • избранные SMS — ключ «sms:15» указывал бы на чужое сообщение: у SMS на
 *   новом телефоне другие номера. Копия текста остаётся в «Избранных»,
 *   ключ получает приставку «moved:» и ни с чем не совпадает;
 * • окно роли SMS и полоса автозапуска — спросить снова (SmsSettings);
 * • напоминания — будильники заново: будильники старого телефона сюда
 *   не переезжают.
 *
 * ── Как понять, что данные приехали ────────────────────────────────────────
 * Метка установки — случайная строка в двух местах: в настройках (они
 * переезжают) и в файле no_backup (Android его никогда не переносит).
 * Файл есть — тот же телефон. Файла нет, а в настройках метка есть — данные
 * пришли без файла, то есть переносом. Ни того, ни другого — первый запуск
 * или обновление с версии до 2.7.1; переносом это считается, только если
 * приложение поставлено только что, а данные уже лежат (перенос с телефона,
 * где стояла старая версия). Решение — [origin], чистая функция
 * (DeviceTransferTest).
 */
object DeviceTransfer {

    private const val TAG = "DeviceTransfer"

    /** Файл метки в no_backup — Android не переносит этот каталог никогда. */
    private const val MARKER = "install_marker"

    private const val DB_NAME = "ogneliss.db"
    private const val PREFS_FILE = "datastore/ogneliss_prefs.preferences_pb"

    enum class Origin {
        /** Тот же телефон: ничего не делать. */
        SAME_DEVICE,

        /** Первый запуск или обновление с версии без метки: только поставить метку. */
        FIRST_RUN,

        /** Данные приехали со старого телефона: убрать привязанное к нему. */
        MOVED
    }

    /**
     * @param markerExists файл метки в no_backup есть
     * @param storedId метка в настройках (null — нет)
     * @param hasData база или настройки уже лежат на диске
     * @param freshInstall приложение поставлено и с тех пор не обновлялось
     */
    internal fun origin(
        markerExists: Boolean,
        storedId: String?,
        hasData: Boolean,
        freshInstall: Boolean
    ): Origin = when {
        // Файл есть — тот же телефон, что бы ни лежало в настройках (их могли
        // сбросить): no_backup не переезжает, значит файл ставили здесь
        markerExists -> Origin.SAME_DEVICE
        storedId != null -> Origin.MOVED
        hasData && freshInstall -> Origin.MOVED
        else -> Origin.FIRST_RUN
    }

    /**
     * Проверка при запуске — из OgnelissApp.onCreate, до BootReceiver
     * и очереди SMS (Application.onCreate идёт раньше любого приёмника).
     * Обычный запуск — одна проверка файла. Только первый запуск после
     * установки ждёт чтения настроек и уборки: отложенное SMS не должно
     * уйти раньше, чем его уберут.
     */
    fun checkOnStart(
        context: Context,
        db: AppDatabase,
        prefs: Prefs,
        reminders: RemindersRepository
    ) {
        val marker = File(context.noBackupFilesDir, MARKER)
        if (marker.exists()) return
        runCatching {
            runBlocking(Dispatchers.IO) {
                // До любого чтения: чтение настроек файлов не создаёт, но порядок надёжнее
                val hasData = context.getDatabasePath(DB_NAME).exists() ||
                    File(context.filesDir, PREFS_FILE).exists()
                val freshInstall = runCatching {
                    @Suppress("DEPRECATION")
                    val info = context.packageManager.getPackageInfo(context.packageName, 0)
                    info.firstInstallTime == info.lastUpdateTime
                }.getOrDefault(false)
                val storedId = suspendRunCatching { prefs.installId.first() }.getOrNull()

                when (origin(false, storedId, hasData, freshInstall)) {
                    Origin.MOVED -> {
                        forgetDeviceBoundRows(db)
                        prefs.forgetDeviceBound()
                        com.ogneliss.dialer.sms.SmsSettings.forgetDevicePrompts(context)
                        suspendRunCatching { reminders.restoreAfterBoot() }
                        runCatching { com.ogneliss.dialer.background.T9IndexWorker.enqueue(context) }
                        Logger.i(TAG, "Данные перенесены с другого телефона: служебное убрано")
                    }
                    Origin.FIRST_RUN, Origin.SAME_DEVICE -> Unit
                }
                /*
                 * Метка — после уборки (сбой посреди неё повторится при следующем
                 * запуске), и сначала файл, потом настройки. Наоборот опасно:
                 * метка в настройках есть, файл не записался — следующий запуск
                 * на этом же телефоне принял бы за перенос и убрал отложенные SMS.
                 */
                val id = UUID.randomUUID().toString()
                marker.writeText(id)
                prefs.setInstallId(id)
            }
        }.onFailure { Logger.e(TAG, "Проверка переноса не удалась — повторю при следующем запуске", it) }
    }

    /**
     * Строки базы, привязанные к старому телефону (разбор — в описании объекта).
     * Одной транзакцией: перенос либо убран целиком, либо повторится.
     */
    internal fun forgetDeviceBoundRows(db: AppDatabase) {
        db.runInTransaction {
            val w = db.openHelper.writableDatabase
            w.execSQL("DELETE FROM sms_scheduled")
            w.execSQL("DELETE FROM contact_cache")
            w.execSQL("DELETE FROM t9_index")
            w.execSQL("UPDATE sms_starred SET msgKey = 'moved:' || msgKey WHERE msgKey NOT LIKE 'moved:%'")
        }
    }
}
