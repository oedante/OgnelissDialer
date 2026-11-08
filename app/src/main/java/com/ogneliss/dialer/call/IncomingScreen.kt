package com.ogneliss.dialer.call

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.telecom.Call
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.IncomingCallAccess
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
 * или не выполняет просьбу окна включить экран.
 *
 * ── Что теперь ─────────────────────────────────────────────────────────────
 * 1. Экран выключен — служба вызовов включает его сама, короткой
 *    блокировкой сна с пробуждением ([wake]). В «Не беспокоить» — только
 *    если система пропускает этого звонящего (Android 13+, 2.1.0).
 * 2. Пока звонит, уведомление всегда входящее, с «Ответить» и «Отклонить»
 *    (2.1.0): тихое, пока экран вызова на виду, всплывающее — когда его нет.
 * 3. Экран вызова не показался за [SHOW_TIMEOUT_MS] ([checkShown]) —
 *    всплывающее уведомление ВСЕГДА (2.1.0): и при погасшем экране,
 *    и в «Не беспокоить» — дальше решает система, пропускать ли звонящего.
 *    «Не беспокоить» лишь не даёт винить прошивку (полоса в приложении).
 * 4. Экран включили или разблокировали, пока звонит, а экрана вызова нет —
 *    проверка заново ([onScreenOn], 2.1.0).
 * 5. «Показан» — это фокус окна, а не onStart (2.1.0): на MIUI окно
 *    запускается и за экраном блокировки, где его никто не видит.
 * 6. Окно создано, но фокуса нет — медленный телефон (Android Go, холодный
 *    запуск): прежде чем винить прошивку, ждём ещё [SLOW_START_RECHECK_MS].
 *
 * Причина видна в журнале приложения; полоса «Телефон не дал показать экран
 * вызова» с кнопкой на нужный экран — IncomingCallAccess.reportBlocked.
 */
internal object IncomingScreen {

    private const val TAG = "IncomingScreen"

    /** Сколько ждать экран вызова, прежде чем поднять уведомление с кнопками. */
    const val SHOW_TIMEOUT_MS = 1_500L

    /**
     * Окно вызова создано, но ещё не на виду (2.1.0): столько ещё ждём,
     * прежде чем сказать «телефон не дал показать». Было: 1,5 с на всё —
     * на Android Go и при холодном запуске окно не успевало, и полоса
     * появлялась у тех, у кого всё работает.
     */
    private const val SLOW_START_RECHECK_MS = 3_000L

    /** Экран включили или разблокировали: столько даём окну вызова выйти вперёд. */
    private const val SCREEN_ON_RECHECK_MS = 700L

    /**
     * Плашка входящего (настройка «Входящий поверх приложений») без ответа
     * так долго — открываем экран вызова целиком (2.1.0): плашку на части
     * прошивок убирает в шторку через несколько секунд, и ответить быстро
     * становится нечем.
     */
    const val BANNER_TIMEOUT_MS = 7_000L

    /**
     * Ушли с экрана вызова так недавно — приложение, открывшееся следом,
     * экран вызова не возвращает: человек ушёл сам («Назад»). Вернуться —
     * полосой «Вернуться к вызову».
     */
    private const val LEFT_RECENTLY_MS = 1_500L

    /** Глобальная настройка всплывающих уведомлений (скрытая, но читаемая). */
    private const val HEADS_UP_SETTING = "heads_up_notifications_enabled"

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

    /** Окно вызова между onStart и onStop. */
    @Volatile
    private var started = false

    /** Окно вызова создано (onCreate), пока звонит (2.1.0). */
    @Volatile
    private var created = false

    /** Когда окно вызова последний раз ушло с экрана (onStop). */
    @Volatile
    private var hiddenAt = 0L

    /** Экран вызова сейчас на виду: получил фокус после onStart (2.1.0). */
    @Volatile
    var visible = false
        private set

    /** Уведомление входящего сейчас всплывающее, с полноэкранным показом. */
    @Volatile
    var alerting = false
        private set

    /**
     * Входящий показан плашкой по настройке (2.1.0): экран вызова не
     * открывался намеренно — checkShown не считает это запретом прошивки.
     */
    @Volatile
    var bannerOnly = false

    /** Тихие входящие (тихие часы): у них своё уведомление, его не трогаем. */
    private val quietCalls: MutableSet<Call> = Collections.newSetFromMap(ConcurrentHashMap())

    private var wakeLock: PowerManager.WakeLock? = null

    private val main = Handler(Looper.getMainLooper())

    /** Слушает включение экрана и разблокировку, пока звонит (2.1.0). */
    private var screenReceiver: BroadcastReceiver? = null

    /** Контекст приложения, в котором слушатель зарегистрирован. */
    private var receiverContext: Context? = null

    fun markQuiet(call: Call) {
        quietCalls += call
    }

    fun forget(call: Call) {
        quietCalls -= call
        if (ringing() == null) resetRinging()
    }

    /** Экран выключен. */
    fun screenOff(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive == false

    /** На экране блокировки. */
    fun locked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /**
     * Звонит новый входящий (2.1.0): пока он звонит, слушаем включение
     * экрана и разблокировку — вдруг экрана вызова так и нет.
     */
    fun startRinging(context: Context) {
        if (screenReceiver != null) return
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                main.postDelayed({ onScreenOn(app) }, SCREEN_ON_RECHECK_MS)
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) }
        runCatching {
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiver = receiver
            receiverContext = app
        }.onFailure { Logger.w(TAG, "Не удалось слушать включение экрана", it) }
    }

    /**
     * Уведомление входящего [call]: [alert] — всплывающее, с полноэкранным
     * показом; иначе — тихое. Кнопки «Ответить» и «Отклонить» — в обоих.
     */
    fun notify(context: Context, call: Call, alert: Boolean) {
        alerting = alert
        logChannelSound(context)
        CallForegroundService.start(
            context, label(context, call), CallDetailsCompat.number(call.details),
            incoming = true, alert = alert
        )
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
     * ПЛАШКА ВХОДЯЩЕГО — ТОЛЬКО ГДЕ ОНА НАВЕРНЯКА ВСПЛЫВЁТ (2.1.0).
     *
     * Было: настройка «Входящий поверх приложений» включала плашку на любом
     * телефоне. Но на MIUI/HyperOS, vivo, ColorOS всплытие и экран вызова
     * из фона ограничены, а если канал входящих приглушён, уведомления
     * выключены, включён «Не беспокоить» или всплывающие уведомления
     * отключены в системе, плашка не появится вовсе — телефон звонит,
     * а на экране ничего. Стало: плашка — только когда всё это в порядке
     * (CallScreenRules.incomingBannerAllowed); иначе экран вызова целиком,
     * человек ничего не выбирает.
     */
    fun bannerSafe(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        val importance = runCatching {
            nm.getNotificationChannel(CallNotifications.CHANNEL_INCOMING)?.importance
        }.getOrNull() ?: NotificationManager.IMPORTANCE_NONE
        // Скрытая настройка: прочитать не дали — считаем включённой, как по умолчанию
        val headsUp = runCatching {
            Settings.Global.getInt(context.contentResolver, HEADS_UP_SETTING, 1) != 0
        }.getOrDefault(true)
        val allowed = CallScreenRules.incomingBannerAllowed(
            restrictiveShell = IncomingCallAccess.restrictiveShell,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channelImportance = importance,
            interruptionFilterAll = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL,
            headsUpOn = headsUp
        )
        if (!allowed) Logger.i(TAG, "Плашка входящего здесь может не всплыть (важность $importance, всплытие $headsUp) — экран вызова целиком")
        return allowed
    }

    /**
     * Плашка звонит [BANNER_TIMEOUT_MS] без ответа: пора открыть экран
     * вызова. true — открыть; режим плашки на этом заканчивается.
     */
    fun bannerExpired(call: Call): Boolean {
        if (!bannerOnly || ringing() != call || visible) return false
        bannerOnly = false
        return true
    }

    /**
     * Включить экран: звонит, а он тёмный.
     *
     * SCREEN_BRIGHT_WAKE_LOCK устарел для обычной работы (вместо него —
     * флаг окна «не гасить экран»), но включить экран ИЗ СЛУЖБЫ, когда окна
     * ещё нет или прошивка его не показала, по-другому нельзя.
     */
    @Suppress("DEPRECATION")
    fun wake(context: Context, number: String?) {
        val power = context.getSystemService(PowerManager::class.java) ?: return
        if (power.isInteractive) return
        /*
         * «НЕ БЕСПОКОИТЬ» — РЕШАЕТ СИСТЕМА (2.1.0).
         *
         * Было: в «Не беспокоить» экран не будили никогда — и звонок
         * от разрешённого контакта (исключения режима) звонил в тёмном
         * экране. Стало: Android 13+ сам говорит, пропускает ли режим этого
         * звонящего (matchesCallFilter); раньше — не будим, экран включит
         * полноэкранное уведомление, если система его пропустит.
         */
        if (doNotDisturb(context) && !passesDoNotDisturb(context, number)) {
            Logger.i(TAG, "Входящий при «Не беспокоить» — экран не будим, решает система")
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

    /** Звонящий проходит «Не беспокоить» (Android 13+; раньше узнать нельзя — false). */
    private fun passesDoNotDisturb(context: Context, number: String?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || number.isNullOrBlank()) return false
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { nm.matchesCallFilter(Uri.fromParts("tel", number, null)) }
            .onFailure { Logger.w(TAG, "Система не ответила про «Не беспокоить»", it) }
            .getOrDefault(false)
    }

    fun releaseWake() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    /** Окно вызова создано (onCreate) — пока звонит, это «запуск идёт». */
    fun onCreated() {
        if (ringing() != null) created = true
    }

    /** Окно вызова запущено (onStart), но ещё может быть за экраном блокировки. */
    fun onStarted() {
        started = true
    }

    /**
     * Экран вызова на виду — получил фокус (2.1.0; было — onStart):
     * уведомление больше не всплывает, две пары кнопок сбивают.
     */
    fun onShown(context: Context) {
        if (!started) return
        visible = true
        bannerOnly = false
        val call = ringing() ?: return
        if (!everShown) Logger.i(TAG, "Экран вызова на виду")
        everShown = true
        if (call in quietCalls || !alerting) return
        notify(context, call, alert = false)
    }

    /** Ушли с экрана вызова (onStop), а телефон звонит: ответить — из уведомления. */
    fun onHidden(context: Context) {
        started = false
        visible = false
        hiddenAt = SystemClock.elapsedRealtime()
        val call = ringing() ?: return
        // Экран погасили кнопкой — это не «ушли»: всплывать незачем.
        // Включат снова, а экрана вызова нет, — onScreenOn
        if (call in quietCalls || alerting || screenOff(context)) return
        notify(context, call, alert = true)
    }

    /** Звонок перестал звонить: ответили, отклонили, сорвался. */
    fun ringingStopped() {
        if (ringing() == null) resetRinging()
    }

    private fun resetRinging() {
        alerting = false
        bannerOnly = false
        everShown = false
        created = false
        releaseWake()
        val receiver = screenReceiver ?: return
        screenReceiver = null
        runCatching { receiverContext?.unregisterReceiver(receiver) }
            .onFailure { Logger.w(TAG, "Слушатель экрана не снят", it) }
        receiverContext = null
    }

    /**
     * ЭКРАН ВЫЗОВА ПОКАЗАЛСЯ? Через [SHOW_TIMEOUT_MS] после входящего.
     *
     * Нет — уведомление с кнопками всплывает ВСЕГДА (2.1.0). Было: при
     * погасшем экране и в «Не беспокоить» не всплывало — и ответить было
     * нечем. Пускать ли его на экран, решает система: исключения «Не
     * беспокоить» она проверяет по номеру в уведомлении.
     *
     * Полоса «Телефон не дал показать экран вызова» (IncomingCallAccess) —
     * не в «Не беспокоить» (там система могла не показать сама) и не сразу,
     * если окно уже создано: медленному телефону — ещё [SLOW_START_RECHECK_MS].
     */
    fun checkShown(context: Context) {
        val call = ringing() ?: return
        if (visible || everShown || bannerOnly || call in quietCalls) return
        if (!alerting) notify(context, call, alert = true)
        if (created) {
            val app = context.applicationContext
            main.postDelayed({ reportIfStillHidden(app) }, SLOW_START_RECHECK_MS)
        } else {
            report(context)
        }
    }

    /** Запуск экрана вызова бросил ошибку: кнопки сразу и полоса в приложении. */
    fun launchFailed(context: Context, call: Call) {
        if (!alerting) notify(context, call, alert = true)
        report(context)
    }

    private fun reportIfStillHidden(context: Context) {
        val call = ringing() ?: return
        if (visible || everShown || bannerOnly || call in quietCalls) return
        report(context)
    }

    private fun report(context: Context) {
        if (doNotDisturb(context)) {
            Logger.i(TAG, "Экран вызова не появился в «Не беспокоить» — прошивку не виню")
            return
        }
        Logger.w(TAG, "Экран вызова не появился: ${IncomingCallAccess.describe(context)}")
        IncomingCallAccess.reportBlocked(context)
    }

    /**
     * Экран включили или разблокировали, пока звонит (2.1.0). Экрана вызова
     * нет — проверяем, как после запуска; он уже показывался (погасили
     * кнопкой, а оболочка не вернула его поверх блокировки) — уведомление
     * с кнопками всплывает.
     */
    private fun onScreenOn(context: Context) {
        val call = ringing() ?: return
        if (visible || bannerOnly || call in quietCalls) return
        if (!everShown) checkShown(context)
        else if (!alerting) notify(context, call, alert = true)
    }

    /**
     * Приложение открыли, пока звонит (2.1.0): вернуть экран вызова сразу.
     * Не возвращаем, если человек только что ушёл с него сам: тогда наверху
     * приложения — полоса «Вернуться к вызову».
     */
    fun shouldReturnToCall(): Boolean {
        if (ringing() == null || visible) return false
        return SystemClock.elapsedRealtime() - hiddenAt > LEFT_RECENTLY_MS
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
