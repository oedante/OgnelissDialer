package com.ogneliss.dialer.sms

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.PersistableBundle
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.sms.mms.MmsPdu
import org.json.JSONObject

/**
 * MMS, КОТОРЫЕ ЕЩЁ НЕ СКАЧАЛИСЬ (2.1.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО БЫЛО НЕ ТАК
 * ══════════════════════════════════════════════════════════════════════════
 * Сеть присылает только уведомление о MMS (где его взять), само сообщение
 * лежит на сервере оператора. Скачивание не удалось (нет мобильных
 * данных, роуминг, слабая сеть) — было: одно уведомление «MMS не скачано»
 * без отправителя и кнопки, и всё. Уведомление сети при этом терялось:
 * повторить было нечем, и сообщение пропадало, хотя сервер ещё держал его.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО ТЕПЕРЬ
 * ══════════════════════════════════════════════════════════════════════════
 * • Уведомление сети запоминается ([Pending]: где скачать, номер
 *   транзакции, карта, отправитель) до успешной записи в хранилище.
 * • В переписке сразу — строка «MMS не загружено» (запись m-notification-ind,
 *   как у AOSP «Сообщений», MmsTransport.storeNotificationInd): касание —
 *   скачать сейчас ([downloadFromInbox]).
 * • Не скачалось — повтор сам, планировщиком системы (JobScheduler: есть
 *   сеть, пауза растёт — [RETRY_DELAYS_MS]); переживает перезагрузку.
 * • Кончились попытки — одно уведомление: от кого и «Загрузить»
 *   (MessageNotifications.mmsNotDownloaded). Это критично (правило автора:
 *   без действия человека сообщение потеряется), поэтому — показывается.
 *
 * Почему JobScheduler, а не WorkManager: задача одна, сеть — её
 * единственное условие, а платформенный планировщик не тянет базу
 * WorkManager в приёмник MMS. Разрешение ACCESS_NETWORK_STATE для условия
 * «есть сеть» (Android 14+) объявлено в манифесте.
 */
object MmsDownloads {

    private const val TAG = "MmsRetry"
    private const val PREFS = "mms_pending"
    private const val DONE_PREFIX = "done:"
    const val EXTRA_KEY = "mms_key"

    /** Повторы после неудачи: через минуту, 15 минут и 2 часа — затем уведомление. */
    private val RETRY_DELAYS_MS = longArrayOf(60_000L, 15 * 60_000L, 2 * 60 * 60_000L)

    /** Пока идёт одно скачивание, второе не начинается (повторное уведомление сети, касание). */
    private const val IN_FLIGHT_MS = 2 * 60_000L

    /**
     * Сторож: ответ на скачивание так и не пришёл (процесс выгрузили, служба
     * телефона упала) — через столько это считается неудачей ([onTimer]).
     * Без сторожа такое MMS повисло бы навсегда: повтор ставит только ответ.
     */
    private const val WATCHDOG_MS = 10 * 60_000L

    /** Забытые записи — через две недели: сервер оператора столько не хранит. */
    private const val KEEP_MS = 14L * 24 * 60 * 60_000L

    /** Скачанное помнится трое суток — повторное уведомление сети о нём не скачивается заново. */
    private const val DONE_KEEP_MS = 3L * 24 * 60 * 60_000L

    /**
     * Номера заданий — в своём окне ('MM' в старших байтах): WorkManager
     * раздаёт номера JobScheduler с нуля подряд и до этих не дойдёт.
     */
    private const val JOB_BASE = 0x4D4D0000
    private const val JOB_RANGE = 0x10000

    /** Скачивание, которое ещё не закончилось записью в хранилище. */
    data class Pending(
        val key: String,
        val contentLocation: String,
        val transactionId: String,
        val subId: Int?,
        val from: String?,
        /** Сколько раз уже не удалось. */
        val attempts: Int,
        /** Строка «не загружено» в хранилище (m-notification-ind); null — не записалась. */
        val inboxId: Long?,
        val receivedAt: Long,
        /** Когда началось последнее скачивание (0 — не идёт). */
        val startedAt: Long = 0L,
        /** Человек нажал «Загрузить» — неудача сразу снова уведомлением. */
        val manual: Boolean = false
    )

    fun keyOf(contentLocation: String, transactionId: String): String =
        transactionId.ifBlank { contentLocation }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Pending? =
        prefs(context).getString(key, null)?.let { runCatching { decode(it) }.getOrNull() }

    private fun all(context: Context): List<Pending> =
        prefs(context).all.filterKeys { !it.startsWith(DONE_PREFIX) }
            .values.mapNotNull { (it as? String)?.let { s -> runCatching { decode(s) }.getOrNull() } }

    private fun save(context: Context, p: Pending) {
        prefs(context).edit().putString(p.key, encode(p)).apply()
    }

    /** Начать скачивание [p] (startedAt уже отмечен) и поставить сторожа. */
    private fun launch(context: Context, p: Pending) {
        save(context, p)
        schedule(context, p.key, WATCHDOG_MS)
        MmsTransport.download(context, p)
    }

    /**
     * Новое уведомление сети о MMS: запомнить и скачать. Повторное
     * уведомление о том же (центр MMS шлёт его снова, пока не услышит
     * «получено») — не второе скачивание параллельно и не повтор уже
     * скачанного.
     */
    fun start(context: Context, notification: MmsPdu.Notification, subId: Int?, inboxId: Long?) {
        prune(context)
        val key = keyOf(notification.contentLocation, notification.transactionId)
        if (prefs(context).contains(DONE_PREFIX + key)) {
            Logger.i(TAG, "Повторное уведомление о скачанном MMS — пропущено")
            return
        }
        val known = get(context, key)
        val now = System.currentTimeMillis()
        if (known != null && now - known.startedAt < IN_FLIGHT_MS) {
            Logger.i(TAG, "MMS уже скачивается — второе скачивание не начато")
            return
        }
        val p = known?.copy(subId = subId ?: known.subId, inboxId = known.inboxId ?: inboxId, startedAt = now)
            ?: Pending(
                key = key,
                contentLocation = notification.contentLocation,
                transactionId = notification.transactionId,
                subId = subId,
                from = notification.from,
                attempts = 0,
                inboxId = inboxId,
                receivedAt = now,
                startedAt = now
            )
        launch(context, p)
    }

    /** Строка «не загружено» этого уведомления уже есть — вторую не писать. */
    fun knownInboxId(context: Context, notification: MmsPdu.Notification): Long? =
        get(context, keyOf(notification.contentLocation, notification.transactionId))?.inboxId

    /**
     * Сработал таймер (MmsRetryJob): повтор после паузы или сторож.
     * Скачивание ещё числится идущим, а ответа нет дольше [IN_FLIGHT_MS] —
     * это неудача: следующий повтор или уведомление ([onFailed]); так
     * повисшее скачивание не крутится бесконечно.
     */
    fun onTimer(context: Context, key: String) {
        val p = get(context, key) ?: return
        if (p.startedAt != 0L) {
            if (System.currentTimeMillis() - p.startedAt < IN_FLIGHT_MS) return
            onFailed(context, key, "нет ответа", blocked = false)
            return
        }
        retryNow(context, key, manual = false)
    }

    /** Скачать сейчас: по таймеру (MmsRetryJob) или по «Загрузить». */
    fun retryNow(context: Context, key: String, manual: Boolean = true) {
        val p = get(context, key) ?: run {
            Logger.w(TAG, "Повтор MMS: запись не найдена")
            return
        }
        if (System.currentTimeMillis() - p.startedAt < IN_FLIGHT_MS) {
            Logger.i(TAG, "Повтор MMS: скачивание уже идёт")
            return
        }
        val next = p.copy(startedAt = System.currentTimeMillis(), manual = manual || p.manual)
        launch(context, next)
    }

    /**
     * «MMS не загружено — нажмите, чтобы загрузить» в переписке: по строке
     * хранилища. Запомненное скачивание есть — оно; нет (строку оставило
     * другое приложение или запись забыта) — адрес скачивания из самой строки.
     */
    fun downloadFromInbox(context: Context, mmsId: Long): Boolean {
        all(context).firstOrNull { it.inboxId == mmsId }?.let {
            retryNow(context, it.key)
            return true
        }
        val row = runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(MessageStore.MMS_URI, mmsId),
                arrayOf("ct_l", "tr_id", "sub_id"), null, null, null
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val location = c.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
                Triple(location, c.getString(1).orEmpty(), c.getInt(2).takeIf { !c.isNull(2) && it >= 0 })
            }
        }.onFailure { Logger.w(TAG, "Строка MMS не прочиталась", it) }.getOrNull() ?: return false
        val (location, tx, sub) = row
        val now = System.currentTimeMillis()
        val p = Pending(
            key = keyOf(location, tx),
            contentLocation = location,
            transactionId = tx,
            subId = sub,
            from = MmsTransport.senderOf(context, mmsId),
            attempts = RETRY_DELAYS_MS.size,
            inboxId = mmsId,
            receivedAt = now,
            startedAt = now,
            manual = true
        )
        launch(context, p)
        return true
    }

    /**
     * Скачано и записано: забыть, убрать строку «не загружено»
     * и уведомление о нём, снять отложенный повтор.
     */
    fun onDone(context: Context, key: String) {
        val p = get(context, key)
        prefs(context).edit().remove(key).putLong(DONE_PREFIX + key, System.currentTimeMillis()).apply()
        cancelJob(context, key)
        MessageNotifications.cancelMmsNotDownloaded(context, key)
        p?.inboxId?.let { id ->
            runCatching { context.contentResolver.delete(ContentUris.withAppendedId(MessageStore.MMS_URI, id), null, null) }
                .onFailure { Logger.w(TAG, "Строка «не загружено» не удалена", it) }
        }
    }

    /**
     * Не скачалось (или не записалось): повтор по расписанию; попытки
     * кончились или это было «Загрузить» — одно уведомление (кроме номеров
     * из списка блокировки: их человек слышать не хочет).
     */
    fun onFailed(context: Context, key: String, reason: String, blocked: Boolean) {
        val p = get(context, key) ?: run {
            Logger.w(TAG, "MMS не скачано ($reason), запись не найдена")
            return
        }
        val attempts = p.attempts + 1
        val retry = !p.manual && attempts <= RETRY_DELAYS_MS.size
        save(context, p.copy(attempts = attempts, startedAt = 0L, manual = false))
        if (retry) {
            val delay = RETRY_DELAYS_MS[attempts - 1]
            Logger.w(TAG, "MMS не скачано ($reason), попытка $attempts — повтор через ${delay / 1000} с")
            schedule(context, key, delay)
            return
        }
        Logger.w(TAG, "MMS не скачано ($reason), попытки кончились")
        // Сторож больше не нужен: дальше — только «Загрузить»
        cancelJob(context, key)
        // Строка «не загружено» — непрочитанной: видно в списке SMS
        p.inboxId?.let { id ->
            runCatching {
                context.contentResolver.update(
                    ContentUris.withAppendedId(MessageStore.MMS_URI, id),
                    ContentValues().apply { put("read", 0); put("seen", 0) }, null, null
                )
            }
        }
        if (!blocked) MessageNotifications.mmsNotDownloaded(context, key, p.from)
    }

    private fun jobId(key: String) = JOB_BASE + Math.floorMod(key.hashCode(), JOB_RANGE)

    private fun schedule(context: Context, key: String, delayMs: Long) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val extras = PersistableBundle().apply { putString(EXTRA_KEY, key) }
        fun build(persisted: Boolean) = JobInfo.Builder(jobId(key), ComponentName(context, MmsRetryJob::class.java))
            .setMinimumLatency(delayMs)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(persisted)
            .setExtras(extras)
            .build()
        // Переживает перезагрузку (нужен RECEIVE_BOOT_COMPLETED — он есть); не вышло — хотя бы до неё
        val result = runCatching { scheduler.schedule(build(persisted = true)) }
            .recoverCatching { scheduler.schedule(build(persisted = false)) }
            .onFailure { Logger.w(TAG, "Повтор MMS не поставлен", it) }
            .getOrDefault(JobScheduler.RESULT_FAILURE)
        if (result != JobScheduler.RESULT_SUCCESS) Logger.w(TAG, "Планировщик не принял повтор MMS")
    }

    private fun cancelJob(context: Context, key: String) {
        runCatching { context.getSystemService(JobScheduler::class.java)?.cancel(jobId(key)) }
    }

    /** Старые записи и отметки «скачано» — прочь. */
    private fun prune(context: Context) {
        val now = System.currentTimeMillis()
        val editor = prefs(context).edit()
        var changed = false
        prefs(context).all.forEach { (k, v) ->
            val old = if (k.startsWith(DONE_PREFIX)) (v as? Long)?.let { now - it > DONE_KEEP_MS } ?: true
            else (v as? String)?.let { s -> runCatching { now - decode(s).receivedAt > KEEP_MS }.getOrDefault(true) } ?: true
            if (old) { editor.remove(k); changed = true }
        }
        if (changed) editor.apply()
    }

    private fun encode(p: Pending): String = JSONObject().apply {
        put("key", p.key)
        put("ct_l", p.contentLocation)
        put("tr_id", p.transactionId)
        p.subId?.let { put("sub", it) }
        p.from?.let { put("from", it) }
        put("attempts", p.attempts)
        p.inboxId?.let { put("inbox", it) }
        put("received", p.receivedAt)
        put("started", p.startedAt)
        put("manual", p.manual)
    }.toString()

    private fun decode(s: String): Pending {
        val o = JSONObject(s)
        return Pending(
            key = o.getString("key"),
            contentLocation = o.getString("ct_l"),
            transactionId = o.optString("tr_id"),
            subId = if (o.has("sub")) o.getInt("sub") else null,
            from = if (o.has("from")) o.getString("from") else null,
            attempts = o.optInt("attempts"),
            inboxId = if (o.has("inbox")) o.getLong("inbox") else null,
            receivedAt = o.optLong("received"),
            startedAt = o.optLong("started"),
            manual = o.optBoolean("manual")
        )
    }
}

/**
 * Отложенный повтор скачивания MMS (2.1.0): система будит, когда есть сеть
 * и прошла пауза, — скачивание начинается, ответ придёт в
 * MmsTransport.DownloadedReceiver. Задание само по себе короткое.
 */
class MmsRetryJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val key = params.extras.getString(MmsDownloads.EXTRA_KEY)
        if (key != null) {
            runCatching { MmsDownloads.onTimer(applicationContext, key) }
                .onFailure { Logger.e("MmsRetry", "Повтор MMS не начат", it) }
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
