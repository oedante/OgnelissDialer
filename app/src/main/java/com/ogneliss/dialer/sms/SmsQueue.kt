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
 * ── Не дважды и не потерять (2.4.2) ────────────────────────────────────────
 * Таймер и будильник могут сработать оба. Отправка под замком; перед
 * отправкой строка помечается «отправляется» (SmsQueueRules.claimMark):
 * кто пометил (claim вернул 1), тот и отправляет; удаляется строка после
 * отправки. Было: удалялась ДО отправки, и закрытый в этот миг процесс
 * терял SMS без следа. Прерванная отправка (пометка старше двух минут)
 * возвращает текст в черновик — разбор в SmsQueueRules.
 *
 * ── Не ушло ────────────────────────────────────────────────────────────────
 * SmsSender записывает исходящее в хранилище; не ушло в сети — пузырь
 * «Не отправлено» с повтором, как у обычной отправки. Если отправить
 * нельзя вовсе (роль SMS ушла другому приложению), текст возвращается
 * в черновик переписки. Просроченное больше чем на сутки (телефон был
 * выключен) тоже уходит в черновик: «с днём рождения» на следующий день
 * хуже, чем не отправленное.
 *
 * 2.4.2: в любом из этих случаев — одно уведомление (MessageNotifications.
 * queuedToDraft). Было молча: человек был уверен, что поздравление ушло.
 * По правилу автора это критично — нужно его действие.
 *
 * ── Время будильника (2.4.2) ───────────────────────────────────────────────
 * Приёмник будильника работает считаные секунды (goAsync). Отправка
 * из него — с запасом [RECEIVER_BUDGET_MS]: не успел — оставшееся ждёт
 * следующего будильника, который ставится сразу же (rearm). Почему не
 * WorkManager: срочная задача на Android 8–11 требует видимого
 * уведомления на каждую отправку, а интерфейс у приложения тихий.
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

    /**
     * Убрать из очереди; вернуть строку — её текст пойдёт обратно в поле.
     * null — уже ушло или уходит (отправляемое не отменить, 2.4.2).
     */
    suspend fun cancel(id: Long): ScheduledSmsEntity? = withContext(io) {
        val entry = dao.get(id)?.takeUnless { SmsQueueRules.isClaimed(it) } ?: return@withContext null
        if (dao.deleteWaiting(id) > 0) entry else null
    }.also { if (it != null) rearm() }

    /** Отправить сейчас, не дожидаясь срока. */
    suspend fun sendNow(id: Long) {
        withContext(io) { dao.reschedule(id, System.currentTimeMillis()) }
        sendDue()
    }

    /**
     * Отправить всё, чему пришёл срок, и поставить будильник на следующее.
     * [budgetMs] — сколько можно работать (приёмник будильника): не успел —
     * оставшееся отправит следующий будильник, он ставится сразу.
     */
    suspend fun sendDue(budgetMs: Long = Long.MAX_VALUE) {
        val started = android.os.SystemClock.elapsedRealtime()
        mutex.withLock {
            val now = System.currentTimeMillis()
            val rows = withContext(io) { dao.snapshot() }

            // Прерванные отправки (процесс закрыли посреди): ушло или нет — неизвестно
            SmsQueueRules.interrupted(rows, now).forEach { entry ->
                Logger.w(TAG, "Отправка SMS из очереди прервана — текст в черновик")
                toDraft(entry)
                withContext(io) { dao.delete(entry.id) }
                MessageNotifications.queuedToDraft(context, entry.address, interrupted = true, scheduled = entry.scheduled)
            }

            val due = SmsQueueRules.due(rows, now)
            // Будильник на проверку прерванной отправки — до того, как отправлять
            if (due.isNotEmpty()) arm(now + SmsQueueRules.RECOVER_MS + 1_000L)
            for (entry in due) {
                if (android.os.SystemClock.elapsedRealtime() - started > budgetMs) {
                    Logger.i(TAG, "Время приёмника вышло — остальное следующим будильником")
                    break
                }
                val mine = withContext(io) { dao.claim(entry.id, SmsQueueRules.claimMark(now)) } > 0
                if (!mine) continue
                if (SmsQueueRules.isStale(entry, now)) {
                    Logger.w(TAG, "Отложенное SMS просрочено больше чем на сутки — в черновик")
                    returnToDraft(entry)
                    continue
                }
                val ok = withContext(io) {
                    runCatching { SmsSender.send(context, entry.address, entry.body, entry.subId) }.getOrDefault(false)
                }
                if (ok) {
                    withContext(io) { dao.delete(entry.id) }
                } else {
                    Logger.w(TAG, "SMS из очереди не отправить — текст возвращён в черновик")
                    returnToDraft(entry)
                }
            }
        }
        rearm()
    }

    /** Будильник на ближайшее в очереди (и проверку отправляемых); очередь пуста — снять. */
    suspend fun rearm() {
        val next = SmsQueueRules.nextWake(withContext(io) { dao.snapshot() })
        if (next == null) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
            return
        }
        arm(next)
    }

    /** Будильник очереди на [at]: точный, если система разрешает, иначе ступенчатый неточный. */
    private fun arm(at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        if (CallbackReminder.canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(at, now), pending(context))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, CallbackReminder.inexactWake(now, at), pending(context))
        }
    }

    /**
     * Не ушло: текст — в черновик, ПОТОМ строку из очереди (сбой между ними
     * даст текст в черновике дважды, но не потеряет его) и одно уведомление.
     */
    private suspend fun returnToDraft(entry: ScheduledSmsEntity) {
        toDraft(entry)
        withContext(io) { dao.delete(entry.id) }
        MessageNotifications.queuedToDraft(context, entry.address, interrupted = false, scheduled = entry.scheduled)
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
                // С запасом до предела приёмника: остальное — следующим будильником
                suspendRunCatching { queue.sendDue(RECEIVER_BUDGET_MS) }
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsQueue"

        /** Таймер в процессе — только на короткое ожидание (окно «Отменить»). */
        private const val TIMER_MAX_MS = 60_000L

        /**
         * Сколько отправлять из приёмника будильника (2.4.2): приёмник с goAsync
         * система ждёт не дольше 10 секунд — с запасом на запись в базу.
         */
        const val RECEIVER_BUDGET_MS = 6_000L

        private const val REQUEST_CODE = 0x534d53

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, Receiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
