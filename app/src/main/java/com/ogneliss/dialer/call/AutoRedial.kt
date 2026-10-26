package com.ogneliss.dialer.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.ogneliss.dialer.call.RedialPolicy.Decision
import com.ogneliss.dialer.call.RedialPolicy.Phase
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PhoneFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * АВТОДОЗВОН: СЕАНС, ПАУЗА И НАБОР (1.8.0).
 *
 * Правила — в RedialPolicy, здесь — то, что их исполняет:
 *
 *   вызов не прошёл ──► OFFER «Дозваниваться?» ──► WAITING отсчёт ──► CALLING
 *        (режим «Сразу» — сразу WAITING)              ▲                  │
 *                                                     └── не прошёл ─────┤
 *                         дозвонились / сбросил сам ──► конец сеанса ◄───┘
 *                         попытки кончились ──► GAVE_UP «Ещё раз»
 *
 * ── Почему объект, а не служба ─────────────────────────────────────────────
 * InCallService система отвязывает, как только кончился последний вызов, —
 * ровно тогда, когда начинается пауза автодозвона. Сеанс живёт в процессе
 * приложения; экран вызова на это время остаётся открытым (InCallActivity
 * не закрывается, пока есть сеанс), и процесс с видимым экраном система
 * не трогает.
 *
 * ── Где виден ──────────────────────────────────────────────────────────────
 * На экране вызова: вместо «Вызов завершён» — причина, отсчёт и кнопки
 * у большого пальца (RedialPanel.kt). Ушёл с экрана — в режиме «Сразу»
 * попытка всё равно наберётся, и экран вызова откроется, как при любом
 * исходящем; в режиме «Предлагать» предложение просто истечёт.
 *
 * Всё — в главном потоке: события InCallService приходят туда же,
 * таймеры идут на Dispatchers.Main. Гонок между ними нет.
 */
object AutoRedial {

    private const val TAG = "AutoRedial"

    /** Сколько ждать ответа на «Дозваниваться?». Дальше экран закрывается, как раньше. */
    private const val OFFER_TIMEOUT_MS = 15_000L

    /** Сколько висит «Не удалось дозвониться», если человек не у телефона. */
    private const val GAVE_UP_TIMEOUT_MS = 30_000L

    /**
     * Попытка набрана, а вызов так и не появился — набор не удался молча
     * (сеть, запрет прошивки). Не висеть же «Набираем снова…» вечно.
     */
    private const val START_TIMEOUT_MS = 10_000L

    /** Сеанс автодозвона — всё, что нужно экрану и следующей попытке. */
    data class Session(
        val number: String,
        val displayName: String?,
        /** SIM, с которой звонили: повтор идёт с неё же. */
        val account: PhoneAccountHandle?,
        val phase: Phase,
        val reason: RedialPolicy.Reason,
        /** Номер попытки: 0 — ещё ни одной (предложение), дальше 1…[maxAttempts]. */
        val attempt: Int,
        val maxAttempts: Int,
        val intervalSec: Int,
        /** Когда наберём (SystemClock.elapsedRealtime); 0 — не ждём. */
        val dialAt: Long = 0L
    )

    /** Настройки — копия из Prefs, обновляется службой вызовов. */
    data class Config(
        val mode: AutoRedialMode = AutoRedialMode.DEFAULT,
        val pace: RedialPace = RedialPace.DEFAULT
    )

    private val _state = MutableStateFlow<Session?>(null)

    /** Текущий сеанс; null — автодозвона нет. */
    val state: StateFlow<Session?> = _state.asStateFlow()

    @Volatile
    var config = Config()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timer: Job? = null

    /** Контекст приложения — для набора, когда службы вызовов уже нет. */
    @Volatile
    private var appContext: Context? = null

    /**
     * Появился вызов (InCallService.onCallAdded).
     *
     * Свой исходящий на тот же номер — попытка идёт. Человек сам набрал
     * тот же номер во время паузы — считаем попыткой, отсчёт не нужен.
     * Любой другой вызов — входящий, другой номер — сеанс заканчивается:
     * человек занят, набирать поверх нельзя.
     */
    @MainThread
    fun onCallAdded(number: String?, outgoing: Boolean) {
        val s = _state.value ?: return
        val same = outgoing && sameNumber(number, s.number)
        when {
            same && s.phase == Phase.CALLING -> Unit
            same && s.phase == Phase.WAITING -> {
                timer?.cancel()
                _state.value = s.copy(phase = Phase.CALLING, dialAt = 0L)
            }
            else -> stop()
        }
    }

    /**
     * Попытка соединилась — дозвонились (InCallService, переход в «разговор»).
     * Сеанс окончен сразу, а не в конце разговора: подпись «попытка 2 из 5»
     * под таймером уже не нужна, а второй входящий не должен его трогать.
     */
    @MainThread
    fun onCallConnected(number: String?) {
        val s = _state.value ?: return
        if (s.phase == Phase.CALLING && sameNumber(number, s.number)) {
            Logger.d(TAG, "Дозвонились на попытке ${s.attempt}")
            stop()
        }
    }

    /**
     * Вызов закончился (InCallService.onCallRemoved).
     *
     * ⚠️ Вызывать ДО CallManager.remove: экран вызова закрывается, когда
     * список вызовов опустел и сеанса нет. Сеанс должен появиться раньше,
     * чем опустеет список, иначе экран закроется на полпути.
     */
    @MainThread
    fun onCallEnded(
        context: Context,
        number: String?,
        displayName: String?,
        account: PhoneAccountHandle?,
        outgoing: Boolean,
        connected: Boolean,
        cause: Int,
        otherCalls: Boolean
    ) {
        appContext = context.applicationContext
        val s = _state.value
        val ours = s != null && s.phase == Phase.CALLING && sameNumber(number, s.number)
        val outcome = RedialPolicy.outcome(outgoing, connected, cause)
        // Экстренный проверяется, только если иначе повторили бы:
        // запрос к системе не бесплатный
        val dialable = !number.isNullOrBlank() && RedialPolicy.isDialable(number) &&
            (outcome.reason == null || !isEmergency(context, number))
        val decision = RedialPolicy.afterCall(
            ourAttempt = ours,
            attempt = s?.attempt ?: 0,
            maxAttempts = s?.maxAttempts ?: 0,
            outcome = outcome,
            mode = config.mode,
            otherCalls = otherCalls,
            dialable = dialable
        )
        Logger.d(TAG, "Вызов завершён: $outcome, решение $decision")
        when (decision) {
            Decision.None -> Unit
            Decision.Stop -> stop()
            is Decision.Offer -> {
                val pace = config.pace
                begin(
                    Session(
                        number = number.orEmpty(),
                        displayName = displayName,
                        account = account,
                        phase = Phase.OFFER,
                        reason = decision.reason,
                        attempt = 0,
                        maxAttempts = pace.attempts,
                        intervalSec = pace.intervalSec
                    )
                )
                timer = scope.launch {
                    delay(OFFER_TIMEOUT_MS)
                    if (_state.value?.phase == Phase.OFFER) stop()
                }
            }
            is Decision.Wait -> {
                val base = if (ours && s != null) s else {
                    val pace = config.pace
                    Session(
                        number = number.orEmpty(),
                        displayName = displayName,
                        account = account,
                        phase = Phase.WAITING,
                        reason = decision.reason,
                        attempt = 0,
                        maxAttempts = pace.attempts,
                        intervalSec = pace.intervalSec
                    )
                }
                waitFor(base.copy(attempt = decision.attempt, reason = decision.reason))
            }
            is Decision.GiveUp -> {
                val base = s ?: return
                begin(base.copy(phase = Phase.GAVE_UP, reason = decision.reason, dialAt = 0L))
                timer = scope.launch {
                    delay(GAVE_UP_TIMEOUT_MS)
                    if (_state.value?.phase == Phase.GAVE_UP) stop()
                }
            }
        }
    }

    /** «Дозваниваться»: согласие на предложение — пауза, потом первая попытка. */
    @MainThread
    fun accept() {
        val s = _state.value?.takeIf { it.phase == Phase.OFFER } ?: return
        waitFor(s.copy(attempt = 1))
    }

    /** «Позвонить сейчас»: не ждать конца паузы. */
    @MainThread
    fun redialNow() {
        val s = _state.value?.takeIf { it.phase == Phase.WAITING } ?: return
        dial(s)
    }

    /** «Ещё раз» после неудачи: новый круг попыток, первая — сразу. */
    @MainThread
    fun retry() {
        val s = _state.value?.takeIf { it.phase == Phase.GAVE_UP } ?: return
        val pace = config.pace
        dial(s.copy(attempt = 1, maxAttempts = pace.attempts, intervalSec = pace.intervalSec))
    }

    /** «Остановить», «Закрыть», «Назад» — сеанс окончен, экран закроется сам. */
    @MainThread
    fun stop() {
        timer?.cancel()
        timer = null
        if (_state.value != null) Logger.d(TAG, "Автодозвон остановлен")
        _state.value = null
    }

    private fun begin(session: Session) {
        timer?.cancel()
        _state.value = session
    }

    private fun waitFor(session: Session) {
        val dialAt = SystemClock.elapsedRealtime() + session.intervalSec * 1000L
        val waiting = session.copy(phase = Phase.WAITING, dialAt = dialAt)
        begin(waiting)
        timer = scope.launch {
            delay(session.intervalSec * 1000L)
            val now = _state.value
            if (now?.phase == Phase.WAITING && now.attempt == waiting.attempt) dial(now)
        }
    }

    private fun dial(session: Session) {
        val context = appContext ?: return stop()
        val calling = session.copy(phase = Phase.CALLING, dialAt = 0L)
        begin(calling)
        if (!placeCall(context, session.number, session.account)) {
            Logger.w(TAG, "Повтор не набрался")
            stop()
            return
        }
        timer = scope.launch {
            delay(START_TIMEOUT_MS)
            val now = _state.value
            if (now?.phase == Phase.CALLING && now.attempt == calling.attempt &&
                CallManager.calls.value.isEmpty()
            ) {
                Logger.w(TAG, "Вызов повтора не появился")
                stop()
            }
        }
    }

    /**
     * Набор с той же SIM, что и в первый раз.
     *
     * Без CALL_PHONE не набираем: разрешение могли отозвать, пока шла пауза.
     */
    private fun placeCall(context: Context, number: String, account: PhoneAccountHandle?): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        val tm = context.getSystemService(TelecomManager::class.java) ?: return false
        val extras = Bundle()
        account?.let { extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        return runCatching { tm.placeCall(Uri.fromParts("tel", number, null), extras) }
            .onFailure { Logger.w(TAG, "Не удалось набрать повтор", it) }
            .isSuccess
    }

    /**
     * Экстренный номер — по данным системы: у неё список для страны,
     * SIM и сети. Не удалось спросить — считаем экстренным: лишний раз
     * не повторить безопаснее, чем повторить 112 без человека.
     */
    private fun isEmergency(context: Context, number: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(number) == true
        } else {
            // Замена появилась только в API 29; ниже — прежний способ
            @Suppress("DEPRECATION")
            PhoneNumberUtils.isEmergencyNumber(number)
        }
    }.getOrDefault(true)

    private fun sameNumber(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return PhoneFormatter.toE164(a) == PhoneFormatter.toE164(b)
    }
}
