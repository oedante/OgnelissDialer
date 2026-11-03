package com.ogneliss.dialer.call

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.telecom.Call
import com.ogneliss.dialer.log.Logger
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * ВХОДЯЩИЙ: ВКЛЮЧИТЬ ЭКРАН И ПОКАЗАТЬ ЭКРАН ВЫЗОВА (1.8.1).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Отзыв пользователя (Realme, Android 10): «при звонке не загорается экран,
 * хотя разрешения все есть». В журнале — ни одной ошибки: запуск экрана
 * вызова «удался». Так и бывает, когда прошивка молча не даёт открыть окно
 * из фона (на ColorOS, MIUI это отдельный запрет «всплывающих окон»)
 * или не выполняет просьбу окна включить экран. Телефон звонит в тёмном
 * экране, а в шторке — обычное уведомление «Текущий вызов» без кнопки
 * ответа: оно ставилось, потому что запуск экрана считался удавшимся.
 *
 * ── Что теперь ─────────────────────────────────────────────────────────────
 * 1. Экран выключен — служба вызовов включает его сама, короткой
 *    блокировкой сна с пробуждением ([wake]): так делают и системные
 *    диалеры на прошивках, где окно само экран не включает. В режиме
 *    «Не беспокоить» — не будим: решает система.
 * 2. (1.8.1–1.8.2) Экран выключен или заблокирован — сразу уведомление
 *    входящего с полноэкранным показом. С 1.8.3 — нет: на части прошивок
 *    оно звенело перед мелодией даже из канала без звука («сначала сигнал
 *    сообщения, потом звонок»). Всплывающее уведомление — только
 *    по [checkShown], когда окно вызова не показалось.
 * 3. Телефон в руках и разблокирован — экран вызова открывается напрямую,
 *    уведомление — тихое. Если за [SHOW_TIMEOUT_MS] экран так и не показался
 *    ([visible]), уведомление становится всплывающим, с кнопками.
 * 4. Экран вызова показался — уведомление снова тихое ([onShown]): две пары
 *    кнопок, в шторке и на экране, сбивают. Ушли с экрана, пока звонит,
 *    — уведомление снова с кнопками ([onHidden]).
 *
 * Причина видна в журнале приложения: «экран вызова не появился за 1,5 с»
 * значит, что прошивка не дала открыть окно; тогда стоит разрешить
 * приложению всплывающие окна и запуск из фона в настройках телефона.
 * С проверки прошивок (1.8.1) об этом говорит и само приложение: полоса
 * «Телефон не дал показать экран вызова» с кнопкой на нужный экран
 * ([checkShown] → IncomingCallAccess.reportBlocked).
 */
internal object IncomingScreen {

    private const val TAG = "IncomingScreen"

    /** Сколько ждать экран вызова, прежде чем поднять уведомление с кнопками. */
    const val SHOW_TIMEOUT_MS = 1_500L

    /**
     * Предел подсветки при пробуждении. Дальше экран держит окно вызова
     * (пока звонит), а не показалось оно — экран гаснет как обычно.
     */
    private const val WAKE_MS = 20_000L

    /**
     * Экран вызова показывался, пока звонит этот входящий. Отдельно от
     * [visible]: экран погасили кнопкой — окно ушло (onStop), но оно было,
     * и запрета прошивки тут нет.
     */
    @Volatile
    private var everShown = false

    /** Экран вызова сейчас на виду. */
    @Volatile
    var visible = false
        private set

    /** Уведомление входящего сейчас всплывающее, с кнопками ответа. */
    @Volatile
    var alerting = false
        private set

    /** Тихие входящие (тихие часы): у них своё уведомление, его не трогаем. */
    private val quietCalls: MutableSet<Call> = Collections.newSetFromMap(ConcurrentHashMap())

    private var wakeLock: PowerManager.WakeLock? = null

    fun markQuiet(call: Call) {
        quietCalls += call
    }

    fun forget(call: Call) {
        quietCalls -= call
        if (ringing() == null) {
            alerting = false
            everShown = false
            releaseWake()
        }
    }

    /** Экран выключен. */
    fun screenOff(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive == false

    /** На экране блокировки. */
    fun locked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /**
     * Уведомление входящего: [alert] — всплывающее, с полноэкранным показом
     * и кнопками; иначе — тихое уведомление вызова.
     */
    fun notify(context: Context, name: String, alert: Boolean) {
        alerting = alert
        logChannelSound(context)
        runCatching { CallForegroundService.start(context, name, incoming = alert) }
            .onFailure { Logger.w(TAG, "Не удалось обновить уведомление вызова", it) }
    }

    /*
     * ЗВУК КАНАЛОВ ВЫЗОВА — В ЖУРНАЛ (1.8.2, 1.8.3).
     *
     * Жалоба на 1.8.1–1.8.2: «перед звонком сначала сигнал сообщения, потом
     * мелодия». Раз за запуск приложения журнал записывает, что у каналов
     * «Входящий вызов» и «Текущий вызов» на этом телефоне: звук мог
     * включить человек в настройках уведомлений или прошивка. Если жалоба
     * повторится, по журналу видно, наш ли это сигнал.
     */
    @Volatile
    private var channelLogged = false

    private fun logChannelSound(context: Context) {
        if (channelLogged) return
        channelLogged = true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(CallNotifications.CHANNEL_INCOMING, CallNotifications.CHANNEL_ONGOING).forEach { id ->
            runCatching {
                val channel = nm.getNotificationChannel(id) ?: return@runCatching
                val sound = channel.sound
                if (sound == null) Logger.i(TAG, "Канал $id: без звука, важность ${channel.importance}, вибрация ${channel.shouldVibrate()}")
                else Logger.w(TAG, "Канал $id со звуком: $sound, важность ${channel.importance} — он прозвучит перед мелодией")
            }
        }
    }

    /**
     * Включить экран: звонит, а он тёмный.
     *
     * SCREEN_BRIGHT_WAKE_LOCK устарел для обычной работы (вместо него —
     * флаг окна «не гасить экран»), но включить экран ИЗ СЛУЖБЫ, когда окна
     * ещё нет или прошивка его не показала, по-другому нельзя.
     */
    @Suppress("DEPRECATION")
    fun wake(context: Context) {
        val power = context.getSystemService(PowerManager::class.java) ?: return
        if (power.isInteractive) return
        // «Не беспокоить»: звонок мог быть приглушён системой — будить не нам
        if (doNotDisturb(context)) {
            Logger.i(TAG, "Входящий при «Не беспокоить» — экран не будим")
            return
        }
        releaseWake()
        wakeLock = runCatching {
            power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "OgnelissDialer:incoming"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_MS)
            }
        }.onFailure { Logger.w(TAG, "Не удалось включить экран", it) }.getOrNull()
        if (wakeLock != null) Logger.i(TAG, "Входящий при выключенном экране — включаю экран")
    }

    fun releaseWake() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    /** Экран вызова показался (onStart): уведомление больше не всплывает. */
    fun onShown(context: Context) {
        visible = true
        val call = ringing() ?: return
        everShown = true
        Logger.i(TAG, "Экран вызова на виду")
        if (call in quietCalls || !alerting) return
        notify(context, label(context, call), alert = false)
    }

    /** Ушли с экрана вызова (onStop), а телефон звонит: ответить — из уведомления. */
    fun onHidden(context: Context) {
        visible = false
        val call = ringing() ?: return
        // Экран погасили кнопкой — это не «ушли»: всплывать незачем
        if (call in quietCalls || alerting || screenOff(context)) return
        notify(context, label(context, call), alert = true)
    }

    /** Звонок перестал звонить: ответили, отклонили, сорвался. */
    fun ringingStopped() {
        if (ringing() == null) {
            alerting = false
            everShown = false
            releaseWake()
        }
    }

    /**
     * ЭКРАН ВЫЗОВА ПОКАЗАЛСЯ? Через [SHOW_TIMEOUT_MS] после входящего.
     *
     * Нет — прошивка не дала открыть окно (или не пустила его поверх
     * блокировки): уведомление с кнопками, если его ещё нет, и полоса
     * в приложении «Телефон не дал показать экран вызова»
     * (IncomingCallAccess). Не считаем запретом: звонок уже не звонит,
     * окно уже показывалось (погасили кнопкой) или на виду (второй
     * входящий во время разговора), «Не беспокоить» — там система сама
     * может не показать полноэкранное уведомление.
     */
    fun checkShown(context: Context) {
        val call = ringing() ?: return
        if (visible || everShown || call in quietCalls || doNotDisturb(context)) return
        Logger.w(TAG, "Экран вызова не появился за 1,5 с: ${com.ogneliss.dialer.util.IncomingCallAccess.describe(context)}")
        com.ogneliss.dialer.util.IncomingCallAccess.reportBlocked(context)
        // Уже всплывает — не дёргаем; экран погасили кнопкой — не будим
        if (!alerting && !screenOff(context)) notify(context, label(context, call), alert = true)
    }

    private fun doNotDisturb(context: Context): Boolean {
        val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        return filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    @Suppress("DEPRECATION")
    private fun ringing(): Call? = CallManager.calls.value.firstOrNull { it.state == Call.STATE_RINGING }

    private fun label(context: Context, call: Call): String = CallDetailsCompat.displayLabel(
        call.details,
        context.getString(com.ogneliss.dialer.R.string.call_unknown_number)
    )
}
