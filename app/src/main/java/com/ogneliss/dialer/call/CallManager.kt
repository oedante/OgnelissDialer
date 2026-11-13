package com.ogneliss.dialer.call

import com.ogneliss.dialer.log.CallDiagnostics
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Единая точка доступа к активным вызовам.
 * InCallService держит связь с системой, UI подписывается на StateFlow.
 */
object CallManager {

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState

    /** Инкремент при любом изменении состояния — заставляет Compose перерисоваться. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    /**
     * Ссылка на сервис телефонии.
     *
     * ── Про предупреждение об утечке ───────────────────────────────────────
     * Статическое поле с Context-объектом анализатор справедливо помечает
     * как потенциальную утечку. Здесь она невозможна по устройству:
     * ссылка ставится в onCallAdded и обнуляется в onCallRemoved,
     * когда активных вызовов не осталось.
     *
     * Альтернатива — держать состояние вызовов в самом сервисе — хуже:
     * экран вызова живёт в другом процессе жизненного цикла и должен
     * получать состояние независимо от того, запущен ли сервис прямо сейчас.
     */
    @Volatile
    var service: InCallService? = null
        internal set

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            // «Завершить и ответить» (2.3.0): ответ — когда прежний вызов действительно завершён
            if (state == Call.STATE_DISCONNECTED) answerAfterRelease(call)
            _revision.value++
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) { _revision.value++ }
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) { _revision.value++ }

        // Список вызовов, с которыми этот можно объединить, приходит
        // отдельно и позже: без этой подписки плитка «Объединить»
        // не появлялась бы, пока что-нибудь другое не перерисует экран
        override fun onConferenceableCallsChanged(call: Call, calls: MutableList<Call>) {
            _revision.value++
        }

        override fun onParentChanged(call: Call, parent: Call?) { _revision.value++ }

        /*
         * ПАУЗА НАБОРА «;» (2.1.0).
         *
         * Было: колбэка не было, и номер вида «+7 900 000-00-00;123»
         * после соединения молча ждал вечно — добавочный не уходил,
         * а на экране не было ничего, чем его отправить. Стало: запоминаем
         * вызов и остаток, экран вызова показывает кнопку «Отправить …»
         * (postDialContinue). Пауза «,» система проходит сама — сюда она
         * не приходит.
         */
        override fun onPostDialWait(call: Call, remainingPostDialSequence: String?) {
            _postDialWait.value = PostDialWait(call, remainingPostDialSequence.orEmpty())
            _revision.value++
        }
    }

    /** Вызов ждёт подтверждения остатка номера после «;». */
    data class PostDialWait(val call: Call, val remaining: String)

    private val _postDialWait = MutableStateFlow<PostDialWait?>(null)
    val postDialWait: StateFlow<PostDialWait?> = _postDialWait

    /**
     * Отправить (send = true) или отбросить остаток номера после «;».
     * Повторная «;» в остатке снова придёт в onPostDialWait.
     */
    fun continuePostDial(send: Boolean) {
        val wait = _postDialWait.value ?: return
        _postDialWait.value = null
        runCatching { wait.call.postDialContinue(send) }
            .onFailure { Logger.w("Call", "postDialContinue не выполнен", it) }
        _revision.value++
    }

    /*
     * ВЫБОР SIM ДЛЯ ИСХОДЯЩЕГО (2.1.0).
     *
     * Было: вызов в состоянии SELECT_PHONE_ACCOUNT («спрашивать каждый
     * раз», звонок из другого приложения или по ссылке tel:) висел
     * без конца — Telecom ждёт, что экран вызова спросит SIM и сообщит
     * выбор через phoneAccountSelected, а мы этого не делали. Стало:
     * экран вызова показывает выбор (SimSelectDialog), отмена кладёт трубку.
     */
    @Suppress("DEPRECATION")
    val awaitingAccount: Call?
        get() = CallLines.topLevel(lines).firstOrNull { it.state == Call.STATE_SELECT_PHONE_ACCOUNT }?.key

    /**
     * Аккаунты, которые Telecom предложил для выбора; пусто — взять все свои.
     *
     * С Android 10 список приходит ключом EXTRA_SUGGESTED_PHONE_ACCOUNTS
     * (предложения в том же порядке), старый AVAILABLE_PHONE_ACCOUNTS
     * устарел (предупреждение сборки 2.1.0, 08.11.2026). Старый ключ
     * остаётся запасным: до Android 10 он единственный, а прошивка,
     * которая не кладёт предложений, без него оставила бы окно выбора пустым.
     */
    fun suggestedAccounts(call: Call): List<android.telecom.PhoneAccountHandle> {
        val extras = call.details?.intentExtras ?: return emptyList()
        return runCatching {
            val suggested = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                suggestionsOf(extras)
            } else {
                emptyList()
            }
            suggested.ifEmpty { legacyAccountsOf(extras) }
        }.getOrNull().orEmpty()
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun suggestionsOf(extras: android.os.Bundle): List<android.telecom.PhoneAccountHandle> {
        val list = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArrayList(
                Call.EXTRA_SUGGESTED_PHONE_ACCOUNTS, android.telecom.PhoneAccountSuggestion::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArrayList<android.telecom.PhoneAccountSuggestion>(Call.EXTRA_SUGGESTED_PHONE_ACCOUNTS)
        }
        return list.orEmpty().map { it.phoneAccountHandle }
    }

    /** Старый ключ — до Android 10 и для прошивок без предложений (см. выше). */
    @Suppress("DEPRECATION")
    private fun legacyAccountsOf(extras: android.os.Bundle): List<android.telecom.PhoneAccountHandle> {
        val list = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArrayList(
                Call.AVAILABLE_PHONE_ACCOUNTS, android.telecom.PhoneAccountHandle::class.java
            )
        } else {
            extras.getParcelableArrayList<android.telecom.PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS)
        }
        return list.orEmpty()
    }

    fun selectAccount(call: Call, handle: android.telecom.PhoneAccountHandle) {
        Logger.i("Call", "SIM выбрана на экране вызова")
        runCatching { call.phoneAccountSelected(handle, false) }
            .onFailure { Logger.w("Call", "phoneAccountSelected не выполнен", it) }
    }

    internal fun add(call: Call) {
        call.registerCallback(callback)
        _calls.value = _calls.value + call
        _revision.value++
    }

    /** Telecom сообщил, можно ли сейчас добавить вызов (InCallService.onCanAddCallChanged). */
    internal fun canAddCallChanged() {
        _revision.value++
    }

    internal fun remove(call: Call) {
        answerAfterRelease(call)
        call.unregisterCallback(callback)
        // Вызов ушёл, не дождавшись «Отправить», — кнопку снимаем (2.1.0)
        if (_postDialWait.value?.call == call) _postDialWait.value = null
        _calls.value = _calls.value - call
        _revision.value++
    }

    internal fun updateAudio(state: CallAudioState?) {
        /*
         * Смена вывода звука — в журнал приложения (1.2.4). Вопрос
         * «вызов сбросился в наушниках — приложение виновато?» по логу
         * было не решить: не было видно ни куда шёл звук, ни когда
         * он переключался. Пишем только смену, не каждое обновление.
         */
        val before = _audioState.value?.route
        if (state != null && state.route != before) CallDiagnostics.logRoute(state)
        _audioState.value = state
    }

    /*
     * ЛИНИИ (2.4.2): все решения о текущем, удержанном и ждущем вызове —
     * в CallLines, без Telecom, с проверками по сценариям (CallLinesTest).
     * Здесь — только перевод объектов Telecom в линии и обратно.
     */
    @Suppress("DEPRECATION")
    private val lines: List<Line<Call>>
        get() = _calls.value.map { c ->
            Line(
                key = c,
                state = c.state,
                hasParent = c.parent != null,
                capabilities = c.details?.callCapabilities ?: 0,
                conferenceable = c.conferenceableCalls.toSet(),
                conference = isConference(c)
            )
        }

    /**
     * Вызов — сама конференция (2.4.3): у неё нет своего номера, на экране
     * и в уведомлении — «Конференция», а не имя последнего собеседника.
     * Свойство PROPERTY_CONFERENCE ставит сеть; участники могут прийти
     * позже самого вызова, поэтому и по ним.
     */
    fun isConference(call: Call): Boolean =
        call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true || call.children.isNotEmpty()

    /** Текущий вызов: разбор порядка — у CallLines.primary. */
    val primary: Call?
        get() = CallLines.primary(lines)?.key

    /** Вторая линия — вызов на удержании, пока говорим по первой. */
    val secondary: Call?
        get() = CallLines.secondary(lines)?.key

    /**
     * ВТОРОЙ ВХОДЯЩИЙ ВО ВРЕМЯ РАЗГОВОРА: экран вызова показывает его
     * отдельной плашкой с выбором, что делать с первым.
     */
    val waiting: Call?
        get() = CallLines.waiting(lines)?.key

    val hasIncoming: Boolean
        get() = CallLines.hasIncoming(lines)

    fun answer() = primary?.answer(VideoProfile.STATE_AUDIO_ONLY)
    /*
     * Отклонение и завершение по команде ПРИЛОЖЕНИЯ — в журнал (1.2.4):
     * кнопка на экране вызова или в уведомлении. Если вызов завершён,
     * а этой строки перед ним нет, — его завершило не приложение:
     * собеседник, сеть или кнопка гарнитуры (ею управляет система).
     */
    fun reject() {
        Logger.i("Call", "Отклонён из приложения")
        primary?.reject(false, null)
    }
    fun hangup() {
        Logger.i("Call", "Завершён из приложения")
        primary?.disconnect()
    }

    // ── Второй входящий ─────────────────────────────────────────────────────

    /**
     * Ответить на второй вызов, первый — на удержание.
     *
     * Удерживать первый вручную не нужно: Telecom при ответе на вызов,
     * пока другой активен, сам ставит активный на удержание, если сеть
     * это умеет. Ручной hold() перед answer() на части сетей гонялся бы
     * с системным и оставлял оба вызова на удержании.
     */
    fun answerWaitingHoldCurrent() {
        waiting?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Как отвечать на второй входящий — удержать или завершить текущий (разбор у CallLines.waitingAnswer). */
    val waitingAnswer: CallLines.WaitingAnswer?
        get() = CallLines.waitingAnswer(lines)

    /**
     * Ответить на второй входящий так, как позволяет сеть (2.4.3): кнопка
     * «Ответить» уведомления. Удержать текущий нельзя — завершить и ответить.
     */
    fun answerWaiting() {
        when (waitingAnswer) {
            CallLines.WaitingAnswer.END_CURRENT -> answerWaitingEndCurrent()
            CallLines.WaitingAnswer.HOLD_CURRENT -> answerWaitingHoldCurrent()
            null -> Unit
        }
    }

    /** Ответить на второй вызов, первый — завершить. */
    /*
     * «ЗАВЕРШИТЬ И ОТВЕТИТЬ» — ОТВЕТ ПОСЛЕ ЗАВЕРШЕНИЯ (2.3.0, проверка логики
     * двух линий по просьбе автора).
     *
     * Было: disconnect() текущего и сразу answer() второго. Завершение идёт
     * не мгновенно, и Telecom, отвечая на второй, видел первый ещё живым —
     * и пытался поставить его на удержание. На части сетей это давало
     * «удержание не удалось» и второй вызов не принимался. Так же, как
     * в системном диалере Android (AnswerOnDisconnected): завершаем текущий,
     * а отвечаем, когда он перешёл в «завершён»; не дождались за
     * [RELEASE_ANSWER_TIMEOUT_MS] — отвечаем всё равно.
     */
    @Volatile private var releaseThenAnswer: Pair<Call, Call>? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun answerWaitingEndCurrent() {
        when (val decision = CallLines.endAndAnswer(lines)) {
            null -> return
            is CallLines.EndAndAnswer.AnswerNow -> decision.incoming.answer(VideoProfile.STATE_AUDIO_ONLY)
            is CallLines.EndAndAnswer.ReleaseFirst -> {
                val incoming = decision.incoming
                Logger.i("Call", "Завершить и ответить: жду завершения текущего")
                releaseThenAnswer = decision.current to incoming
                decision.current.disconnect()
                main.postDelayed({
                    val pending = releaseThenAnswer ?: return@postDelayed
                    if (pending.second == incoming) {
                        Logger.w("Call", "Текущий не завершился вовремя — отвечаю на второй")
                        releaseThenAnswer = null
                        runCatching { incoming.answer(VideoProfile.STATE_AUDIO_ONLY) }
                    }
                }, RELEASE_ANSWER_TIMEOUT_MS)
            }
        }
    }

    private fun answerAfterRelease(released: Call) {
        val pending = releaseThenAnswer ?: return
        if (pending.first != released) return
        releaseThenAnswer = null
        @Suppress("DEPRECATION")
        if (CallLines.answerAfterRelease(pending, released, pending.second.state)) {
            runCatching { pending.second.answer(VideoProfile.STATE_AUDIO_ONLY) }
                .onFailure { Logger.w("Call", "Ответ после завершения не выполнен", it) }
        }
    }

    fun rejectWaiting() {
        waiting?.reject(false, null)
    }

    // ── Удержание ───────────────────────────────────────────────────────────

    /**
     * Можно ли поставить текущий вызов на удержание.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ПРОВЕРЯТЬ
     * ══════════════════════════════════════════════════════════════════════
     * Удержание — услуга сети, и не каждая сеть и не каждый тип вызова
     * её дают (у VoLTE и у экстренных вызовов свои правила). Система
     * сообщает это флагами CAPABILITY_HOLD и CAPABILITY_SUPPORT_HOLD.
     * Без проверки кнопка показывалась всегда и на неподдерживаемой сети
     * молча ничего не делала — выглядело как сломанная кнопка.
     *
     * Уже удержанный вызов снять с удержания можно всегда: иначе человек
     * застрял бы с вызовом, который нельзя вернуть.
     */
    /*
     * 2.3.0: только CAPABILITY_HOLD. Было — ещё и CAPABILITY_SUPPORT_HOLD,
     * а он значит «сеть удержание умеет, но СЕЙЧАС нельзя» (например,
     * у участника конференции): плитка «Удержать» горела, а нажатие ничего
     * не делало. Системный диалер в этом случае плитку гасит.
     */
    /** Разбор — у CallLines.canHold. */
    val canHold: Boolean
        get() = CallLines.canHold(lines)

    fun toggleHold() {
        when (val action = CallLines.holdAction(lines)) {
            is CallLines.HoldAction.Hold -> action.key.hold()
            is CallLines.HoldAction.Unhold -> action.key.unhold()
            null -> Unit
        }
    }

    // ── Две линии ───────────────────────────────────────────────────────────

    /**
     * Переключение между двумя линиями.
     *
     * Если текущий вызов — конференция, у которой сеть умеет переключаться
     * сама (CAPABILITY_SWAP_CONFERENCE), просим её: так делают сети, где
     * вторая линия живёт внутри конференции. Иначе — вручную: текущий
     * на удержание, удержанный обратно.
     */
    /*
     * ПЕРЕКЛЮЧИТЬ ЛИНИИ (2.3.0).
     *
     * Было: hold() текущего и сразу unhold() удержанного. Но Telecom,
     * снимая вызов с удержания, сам ставит на удержание активный
     * (CallsManager.unholdCall) — и выходило два запроса удержания подряд.
     * На IMS (VoLTE) второй отклоняется («удержание уже идёт»), и обе линии
     * могли остаться на удержании. Теперь — только unhold(): удержание
     * текущего и «обмен» для сети делает Telecom, как в системном диалере.
     */
    /** Переключить можно: обе линии соединены (разбор у CallLines.swap). */
    val canSwap: Boolean
        get() = CallLines.canSwap(lines)

    fun swapCalls() {
        when (val action = CallLines.swap(lines)) {
            is CallLines.Swap.Conference -> action.key.swapConference()
            is CallLines.Swap.Unhold -> action.key.unhold()
            null -> Unit
        }
    }

    /**
     * Можно ли объединить две линии.
     *
     * Разные сети объединяют по-разному, и система сообщает, какой путь
     * открыт. Раньше кнопка «Объединить» показывалась всегда, когда линий
     * две, и на сети без конференций молча не работала.
     */
    val canMerge: Boolean
        get() = CallLines.canMerge(lines)

    /**
     * Объединить в конференцию. Порядок — по документации Telecom, разбор
     * у CallLines.merge: указаны друг у друга — conference(); вызов сам
     * собирает конференцию — mergeConference(); иначе всё равно conference().
     */
    fun merge() {
        when (val action = CallLines.merge(lines)) {
            is CallLines.Merge.Conference -> action.key.conference(action.with)
            is CallLines.Merge.MergeConference -> action.key.mergeConference()
            null -> Unit
        }
    }

    // ── Новый вызов во время разговора ──────────────────────────────────────

    /**
     * Можно ли набрать ещё один номер, не прерывая разговор.
     *
     * Условия: текущий вызов идёт (не звонит и не набирается), его можно
     * удержать — иначе новому вызову некуда деть первый — и второй линии
     * ещё нет: большинство сетей держат две линии, третью не дают.
     */
    /*
     * 2.3.0: и ответ Telecom (InCallService.canAddCall): он учитывает
     * экстренный вызов, предел вызовов и ограничения сети — без этого
     * «Добавить» горело, а набранный номер Telecom отклонял.
     */
    val canAddCall: Boolean
        get() {
            val telecomAllows = runCatching { service?.canAddCall() }.getOrNull() ?: true
            return CallLines.canAddCall(lines, telecomAllows)
        }

    // ── Конференция ─────────────────────────────────────────────────────────

    /** Участники текущей конференции; пусто, если это обычный вызов. */
    val conferenceParticipants: List<Call>
        get() = primary?.children.orEmpty()

    /**
     * Отключить участника конференции.
     *
     * Только если сеть позволяет (CAPABILITY_DISCONNECT_FROM_CONFERENCE):
     * иначе disconnect() у участника на части сетей кладёт трубку всей
     * конференции.
     */
    fun canDropParticipant(child: Call): Boolean =
        child.details.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)

    fun dropParticipant(child: Call) {
        if (canDropParticipant(child)) child.disconnect()
    }

    /** Поговорить с участником отдельно: остальные уходят на удержание. */
    fun canSplitParticipant(child: Call): Boolean =
        child.details.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE)

    fun splitParticipant(child: Call) {
        if (canSplitParticipant(child)) child.splitFromConference()
    }

    fun playDtmf(c: Char) {
        primary?.playDtmfTone(c)
        primary?.stopDtmfTone()
    }

    fun setMuted(muted: Boolean) { service?.setMuted(muted) }

    /**
     * Переключение маршрута звука.
     *
     * @Suppress("DEPRECATION") — про setAudioRoute. Замена,
     * requestCallEndpointChange, появилась в API 34 и требует другой
     * модели: не «маршрут», а объект конечной точки, который надо
     * получить из колбэка. Переписывать стоит вместе с подъёмом minSdk,
     * а не ради снятия предупреждения.
     */
    @Suppress("DEPRECATION")
    fun setAudioRoute(route: Int) { service?.setAudioRoute(route) }

    val isMuted: Boolean get() = _audioState.value?.isMuted == true
    val isSpeakerOn: Boolean
        get() = _audioState.value?.route == CallAudioState.ROUTE_SPEAKER
    val isBluetoothAvailable: Boolean
        get() = (_audioState.value?.supportedRouteMask ?: 0) and
            CallAudioState.ROUTE_BLUETOOTH != 0

    /** «Завершить и ответить»: сколько ждать завершения текущего. */
    private const val RELEASE_ANSWER_TIMEOUT_MS = 2_000L
}
