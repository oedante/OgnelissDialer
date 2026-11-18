package com.ogneliss.dialer.call

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.ogneliss.dialer.R
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ПСЕВДОЗВОНОК — «СВОИМ ВЫЗОВОМ» В СИСТЕМЕ ВЫЗОВОВ ANDROID (2.7.8).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Форум, valer4ik123: «фейк вызов не идёт на BT гарнитуру». Автор выбрал
 * вариант «А»: регистрировать псевдозвонок в системе так же, как свои
 * вызовы регистрируют мессенджеры (self-managed ConnectionService,
 * разрешение MANAGE_OWN_CALLS уже было).
 *
 * Мелодию по-прежнему играет экран псевдозвонка (FakeCallActivity): для
 * своих вызовов система её не играет. Зато она сообщает о вызове
 * гарнитуре, машине и часам: там видно, кто звонит, а кнопка гарнитуры
 * отвечает и сбрасывает. Ответили или сбросили там — экран псевдозвонка
 * узнаёт это из [state]; ответили или сбросили на экране — вызов в системе
 * становится разговором или завершается ([answer], [end]).
 *
 * ── Чего НЕ происходит ─────────────────────────────────────────────────────
 * • В журнал вызовов (свой и системный) псевдозвонок не попадает: система
 *   не записывает свои вызовы приложений, а на Android 13+ это ещё
 *   и запрещено явно (EXTRA_LOG_SELF_MANAGED_CALLS = false).
 * • Наш экран вызова (OgnelissInCallService) его не видит: он не объявляет
 *   приём своих вызовов приложений — показывает его FakeCallActivity.
 * • Разговора нет: после ответа в гарнитуре тишина, как и на экране.
 *
 * ── Если система вызов не приняла ──────────────────────────────────────────
 * Идёт настоящий разговор, производитель урезал систему вызовов, нет
 * разрешения — [start] возвращает false или приходит отказ
 * (onCreateIncomingConnectionFailed). Тогда псевдозвонок показывается
 * по-старому, без гарнитуры: экран открывается сам или через уведомление
 * во весь экран (FakeCall.ringFromBackground).
 */
object FakeTelecomCall {

    enum class State { IDLE, RINGING, ACTIVE }

    private const val TAG = "FakeTelecomCall"
    private const val ACCOUNT_ID = "fake_call"

    /** Не ответили за минуту — вызов в системе снимается, как неотвеченный. */
    private const val RING_LIMIT_MS = 60_000L

    /** Сколько ждать, пока система создаст вызов, прежде чем считать запрос потерянным. */
    private const val PENDING_LIMIT_MS = 5_000L

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var connection: FakeConnection? = null
    @Volatile private var pendingSince = 0L

    /** Экран псевдозвонка на виду: показывать вызов ещё раз не нужно. */
    @Volatile var activityVisible = false

    private val main = Handler(Looper.getMainLooper())
    private val ringTimeout = Runnable {
        val c = connection
        if (c != null && _state.value == State.RINGING) finish(c, DisconnectCause.MISSED)
    }

    private fun handle(context: Context) = PhoneAccountHandle(
        ComponentName(context, FakeCallConnectionService::class.java), ACCOUNT_ID
    )

    private fun isPending() = pendingSince != 0L &&
        SystemClock.elapsedRealtime() - pendingSince < PENDING_LIMIT_MS

    /**
     * Сообщить системе о входящем псевдозвонке. true — вызов уже есть или
     * запрос принят (экран показывается из onShowIncomingCallUi); false —
     * не вышло, показывать по-старому.
     *
     * @Suppress("DEPRECATION") (2.8.4, предупреждения сборки автора): в Android 16
     * (compileSdk 36) CAPABILITY_SELF_MANAGED и EXTRA_LOG_SELF_MANAGED_CALLS
     * помечены устаревшими — Google советует библиотеку Jetpack core-telecom
     * (CallsManager). На всех версиях, включая 16, прежний способ работает;
     * переход — новая зависимость, только по решению автора. Подавление —
     * на этой функции, а не на файле: другие устаревшие вызовы будут видны.
     */
    @Suppress("DEPRECATION")
    fun start(context: Context, number: String = ""): Boolean {
        if (connection != null || isPending()) return true
        return runCatching {
            val tm = context.getSystemService(TelecomManager::class.java) ?: return false
            val account = handle(context)
            val extras = Bundle()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Гарнитуре, машине и часам вызов виден (по умолчанию тоже, но явно)
                extras.putBoolean(PhoneAccount.EXTRA_ADD_SELF_MANAGED_CALLS_TO_INCALLSERVICE, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // В системный журнал вызовов — нет (по умолчанию тоже, но явно)
                extras.putBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, false)
            }
            tm.registerPhoneAccount(
                PhoneAccount.builder(account, context.getString(R.string.fake_call_title))
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL))
                    .setExtras(extras)
                    .build()
            )
            // Идёт настоящий разговор и т. п. — система откажет; сразу по-старому
            if (!tm.isIncomingCallPermitted(account)) {
                Logger.i(TAG, "Система сейчас не принимает свой вызов — псевдозвонок без неё")
                return false
            }
            val request = Bundle()
            if (number.isNotBlank()) {
                request.putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null)
                )
            }
            pendingSince = SystemClock.elapsedRealtime()
            tm.addNewIncomingCall(account, request)
            true
        }.onFailure {
            pendingSince = 0L
            Logger.w(TAG, "Псевдозвонок в системе не зарегистрирован: ${it.message}")
        }.getOrDefault(false)
    }

    /** Имя и номер для гарнитуры и машины — когда экран их выбрал. */
    fun updateCaller(name: String, number: String) {
        val c = connection ?: return
        main.post {
            runCatching {
                if (number.isNotBlank()) {
                    c.setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), TelecomManager.PRESENTATION_ALLOWED)
                }
                if (name.isNotBlank()) c.setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
            }
        }
    }

    /** Ответили на экране. */
    fun answer() {
        val c = connection ?: return
        main.post {
            if (_state.value == State.RINGING) {
                runCatching { c.setActive() }
                main.removeCallbacks(ringTimeout)
                _state.value = State.ACTIVE
            }
        }
    }

    /** Сбросили или завершили на экране. */
    fun end() {
        val c = connection ?: return
        main.post {
            finish(c, if (_state.value == State.RINGING) DisconnectCause.REJECTED else DisconnectCause.LOCAL)
        }
    }

    // ── Из системы ──────────────────────────────────────────────────────────

    internal fun onCreated(c: FakeConnection) {
        connection = c
        pendingSince = 0L
        _state.value = State.RINGING
        main.removeCallbacks(ringTimeout)
        main.postDelayed(ringTimeout, RING_LIMIT_MS)
    }

    internal fun onFailed(context: Context) {
        pendingSince = 0L
        Logger.i(TAG, "Система отказала в своём вызове — псевдозвонок без неё")
        if (!activityVisible) FakeCall.ringFromBackground(context)
    }

    /** Ответили в гарнитуре, машине или на часах. */
    internal fun answeredOutside(context: Context, c: FakeConnection) {
        if (connection !== c || _state.value != State.RINGING) return
        runCatching { c.setActive() }
        main.removeCallbacks(ringTimeout)
        _state.value = State.ACTIVE
        // Плашка с мелодией больше не нужна; экран — если его ещё нет
        FakeCall.clearNotification(context)
        if (!activityVisible) runCatching { FakeCall.ringNow(context) }
    }

    /** Сбросили там же или система сняла вызов (например, ответили на настоящий). */
    internal fun endedOutside(context: Context, c: FakeConnection, cause: Int) {
        FakeCall.clearNotification(context)
        finish(c, cause)
    }

    private fun finish(c: FakeConnection, cause: Int) {
        runCatching { c.setDisconnected(DisconnectCause(cause)) }
        runCatching { c.destroy() }
        main.removeCallbacks(ringTimeout)
        if (connection === c) {
            connection = null
            _state.value = State.IDLE
        }
    }
}

/**
 * Служба своих вызовов приложения: система создаёт через неё псевдозвонок
 * (разбор у FakeTelecomCall). Других вызовов здесь нет.
 */
class FakeCallConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val c = FakeConnection(applicationContext)
        c.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        c.setAudioModeIsVoip(true)
        c.connectionCapabilities = Connection.CAPABILITY_MUTE
        request?.address?.let { c.setAddress(it, TelecomManager.PRESENTATION_ALLOWED) }
        c.setRinging()
        FakeTelecomCall.onCreated(c)
        return c
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        FakeTelecomCall.onFailed(applicationContext)
    }
}

/** Псевдозвонок в системе: события гарнитуры, машины и часов — в FakeTelecomCall. */
class FakeConnection(private val context: Context) : Connection() {

    /** Система просит показать входящий: экрана нет — как у вызова по времени. */
    override fun onShowIncomingCallUi() {
        if (!FakeTelecomCall.activityVisible) FakeCall.ringFromBackground(context)
    }

    override fun onAnswer(videoState: Int) = FakeTelecomCall.answeredOutside(context, this)

    override fun onReject() = FakeTelecomCall.endedOutside(context, this, DisconnectCause.REJECTED)

    override fun onDisconnect() = FakeTelecomCall.endedOutside(context, this, DisconnectCause.LOCAL)

    override fun onAbort() = FakeTelecomCall.endedOutside(context, this, DisconnectCause.CANCELED)
}
