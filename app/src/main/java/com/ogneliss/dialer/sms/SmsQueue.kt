package com.ogneliss.dialer.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ogneliss.dialer.background.CallbackReminder
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.data.db.ScheduledSmsDao
import com.ogneliss.dialer.data.db.ScheduledSmsEntity
import com.ogneliss.dialer.data.db.SmsDraftDao
import com.ogneliss.dialer.data.db.SmsDraftEntity
import com.ogneliss.dialer.di.ApplicationScope
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ОЧЕРЕДЬ ОТПРАВКИ SMS (2.2.0): отложенная отправка и «Отменить».
 *
 * ── Одна очередь на два случая ─────────────────────────────────────────────
 * «Отправить позже» (время выбирает человек) и «Отменить» после «Отправить»
 * (несколько секунд, настройка «Отмена отправки») — одно и то же: SMS
 * с временем отправки в таблице sms_scheduled. Переписка показывает
 * такие сообщения пузырём в конце ленты с «Отменить» / «Отправить сейчас».
 *
 * ── Как отправляется вовремя ───────────────────────────────────────────────
 * • Короткое ожидание (окно «Отменить») — таймер в процессе приложения:
 *   будильник Android на секунды неточен, а на части прошивок
 *   откладывается на минуты.
 * • Всегда — ещё и будильник на ближайшее: процесс могут закрыть, телефон
 *   перезагрузить (BootReceiver вызывает [rearm]). Точный, если система
 *   разрешает, иначе ступенчатый неточный, как у напоминаний
 *   (CallbackReminder.inexactWake).
 *
 * ── Не дважды ──────────────────────────────────────────────────────────────
 * Таймер и будильник могут сработать оба. Отправка под замком, и строка
 * удаляется ДО отправки: кто удалил (delete вернул 1), тот и отправляет.
 *
 * ── Не ушло ────────────────────────────────────────────────────────────────
 * SmsSender записывает исходящее в хранилище; не ушло — пузырь
 * «Не отправлено» с повтором, как у обычной отправки. Если отправить
 * нельзя вовсе (роль SMS ушла другому приложению), текст возвращается
 * в черновик переписки — не теряется молча. Просроченное больше чем
 * на сутки (телефон был выключен) тоже уходит в черновик: «с днём
 * рождения» на следующий день хуже, чем не отправленное.
 */
@Singleton
class SmsQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ScheduledSmsDao,
    private val drafts: SmsDraftDao,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    private val mutex = Mutex()

    /** Вся очередь — переписка берёт свои по addressKey. */
    fun all(): Flow<List<ScheduledSmsEntity>> = dao.all()

    /** Поставить SMS в очередь на [atMillis]. */
    suspend fun enqueue(address: String, body: String, subId: Int?, atMillis: Long, scheduled: Boolean): Long {
        val id = withContext(io) {
            dao.insert(
                ScheduledSmsEntity(
                    address = address,
                    addressKey = smsPinKey(address),
                    body = body,
                    subId = subId,
                    atMillis = atMillis,
                    scheduled = scheduled
                )
            )
        }
        Logger.i(TAG, if (scheduled) "SMS отложено" else "SMS ждёт окна отмены")
        armTimer(atMillis)
        rearm()
        return id
    }

    /** Убрать из очереди; вернуть строку — её текст пойдёт обратно в поле. null — уже ушло. */
    suspend fun cancel(id: Long): ScheduledSmsEntity? = withContext(io) {
        val entry = dao.get(id) ?: return@withContext null
        if (dao.delete(id) > 0) entry else null
    }.also { if (it != null) rearm() }

    /** Отправить сейчас, не дожидаясь срока. */
    suspend fun sendNow(id: Long) {
        withContext(io) { dao.reschedule(id, System.currentTimeMillis()) }
        sendDue()
    }

    /** Отправить всё, чему пришёл срок, и поставить будильник на следующее. */
    suspend fun sendDue() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val due = withContext(io) { dao.snapshot() }.filter { it.atMillis <= now + EARLY_MS }
            due.forEach { entry ->
                val mine = withContext(io) { dao.delete(entry.id) } > 0
                if (!mine) return@forEach
                if (now - entry.atMillis > STALE_MS) {
                    Logger.w(TAG, "Отложенное SMS просрочено больше чем на сутки — в черновик")
                    toDraft(entry)
                    return@forEach
                }
                val ok = withContext(io) { SmsSender.send(context, entry.address, entry.body, entry.subId) }
                if (!ok) {
                    Logger.w(TAG, "SMS из очереди не отправить — текст возвращён в черновик")
                    toDraft(entry)
                }
            }
        }
        rearm()
    }

    /** Будильник на ближайшее в очереди; очередь пуста — снять. */
    suspend fun rearm() {
        val next = withContext(io) { dao.snapshot() }.minOfOrNull { it.atMillis }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val now = System.currentTimeMillis()
        if (CallbackReminder.canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(next, now), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, CallbackReminder.inexactWake(now, next), pi)
        }
    }

    private fun armTimer(atMillis: Long) {
        val wait = atMillis - System.currentTimeMillis()
        if (wait > TIMER_MAX_MS) return
        scope.launch {
            delay(wait.coerceAtLeast(0))
            suspendRunCatching { sendDue() }
        }
    }

    /** Текст — в черновик переписки: к уже написанному, не вместо него. */
    private suspend fun toDraft(entry: ScheduledSmsEntity) = withContext(io) {
        val old = drafts.get(entry.addressKey)?.body.orEmpty()
        val body = if (old.isBlank()) entry.body else entry.body + "\n" + old
        drafts.upsert(SmsDraftEntity(entry.addressKey, body, System.currentTimeMillis()))
    }

    /** Будильник очереди: отправить пришедшее. */
    @dagger.hilt.android.AndroidEntryPoint
    class Receiver : BroadcastReceiver() {

        @Inject
        lateinit var queue: SmsQueue

        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            ).launch {
                suspendRunCatching { queue.sendDue() }
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsQueue"

        /** Неточный будильник может прийти чуть раньше — не ждать ради секунд. */
        private const val EARLY_MS = 15_000L

        /** Таймер в процессе — только на короткое ожидание (окно «Отменить»). */
        private const val TIMER_MAX_MS = 60_000L

        /** Просрочено больше чем на сутки — не отправлять, вернуть в черновик. */
        private const val STALE_MS = 24 * 60 * 60_000L

        private const val REQUEST_CODE = 0x534d53

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, Receiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
