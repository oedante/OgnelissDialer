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
        override fun onStateChanged(call: Call, state: Int) { _revision.value++ }
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
        get() = topLevel.firstOrNull { it.state == Call.STATE_SELECT_PHONE_ACCOUNT }

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

    internal fun remove(call: Call) {
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
     * ══════════════════════════════════════════════════════════════════════
     * ВЫЗОВЫ ВЕРХНЕГО УРОВНЯ
     * ══════════════════════════════════════════════════════════════════════
     * Когда два вызова объединяют в конференцию, Telecom создаёт третий
     * объект — саму конференцию, — а два прежних становятся её
     * участниками (у них появляется parent). Все три при этом лежат
     * в общем списке.
     *
     * Раньше выбор «текущего вызова» смотрел на весь список и мог выбрать
     * участника вместо конференции: у участников тоже состояние ACTIVE.
     * Экран показывал одного собеседника, а «Завершить» клал трубку
     * только ему. Теперь выбираем только среди вызовов без родителя.
     */
    @Suppress("DEPRECATION")
    private val topLevel: List<Call>
        get() = _calls.value.filter {
            it.parent == null &&
                it.state != Call.STATE_DISCONNECTED &&
                it.state != Call.STATE_DISCONNECTING
        }

    /**
     * Текущий вызов — тот, с которым человек сейчас имеет дело.
     *
     * ── Порядок выбора ─────────────────────────────────────────────────────
     * 1. Идущий разговор (ACTIVE).
     * 2. Набираемый (DIALING, CONNECTING и соседние): человек только что
     *    нажал «Добавить вызов», первый ушёл на удержание, и экран должен
     *    показывать новый — тот, что сейчас дозванивается.
     * 3. Звонящий (RINGING): входящий, когда других нет.
     * 4. Удержанный — если больше ничего не осталось.
     *
     * Раньше было «активный, иначе первый живой», и во время набора
     * второго номера первым живым оказывался удержанный — экран показывал
     * не тот вызов.
     *
     * @Suppress("DEPRECATION") — про Call.state. Замена,
     * Call.getDetails().getState(), появилась в API 31, а minSdk у нас 26.
     */
    @Suppress("DEPRECATION")
    val primary: Call?
        get() {
            val live = topLevel
            return live.firstOrNull { it.state == Call.STATE_ACTIVE }
                ?: live.firstOrNull { it.state in OUTGOING_STATES }
                ?: live.firstOrNull { it.state == Call.STATE_RINGING }
                ?: live.firstOrNull()
        }

    /** Вторая линия — вызов на удержании, пока говорим по первой. */
    @Suppress("DEPRECATION")
    val secondary: Call?
        get() {
            val main = primary
            return topLevel.firstOrNull { it != main && it.state == Call.STATE_HOLDING }
        }

    /**
     * ВТОРОЙ ВХОДЯЩИЙ ВО ВРЕМЯ РАЗГОВОРА.
     *
     * Раньше его не было видно вовсе: текущим оставался идущий разговор,
     * а звонящий вызов на экране не показывался — ответить на него можно
     * было только из шторки уведомлений, если повезёт. Теперь экран вызова
     * показывает его отдельной плашкой с выбором, что делать с первым.
     */
    @Suppress("DEPRECATION")
    val waiting: Call?
        get() {
            val main = primary
            return topLevel.firstOrNull { it != main && it.state == Call.STATE_RINGING }
        }

    @Suppress("DEPRECATION")
    val hasIncoming: Boolean
        get() = topLevel.any { it.state == Call.STATE_RINGING }

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

    /** Ответить на второй вызов, первый — завершить. */
    fun answerWaitingEndCurrent() {
        val incoming = waiting ?: return
        primary?.disconnect()
        incoming.answer(VideoProfile.STATE_AUDIO_ONLY)
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
    @Suppress("DEPRECATION")
    val canHold: Boolean
        get() {
            val c = primary ?: return false
            if (c.state == Call.STATE_HOLDING) return true
            return c.details.can(Call.Details.CAPABILITY_HOLD) ||
                c.details.can(Call.Details.CAPABILITY_SUPPORT_HOLD)
        }

    @Suppress("DEPRECATION")
    fun toggleHold() {
        val c = primary ?: return
        if (c.state == Call.STATE_HOLDING) c.unhold() else c.hold()
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
    fun swapCalls() {
        val main = primary
        if (main != null && main.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)) {
            main.swapConference()
            return
        }
        val hold = secondary ?: return
        main?.hold()
        hold.unhold()
    }

    /**
     * Можно ли объединить две линии.
     *
     * Разные сети объединяют по-разному, и система сообщает, какой путь
     * открыт. Раньше кнопка «Объединить» показывалась всегда, когда линий
     * две, и на сети без конференций молча не работала.
     */
    val canMerge: Boolean
        get() {
            val a = primary ?: return false
            val b = secondary ?: return false
            return b in a.conferenceableCalls ||
                a.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) ||
                b.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)
        }

    /**
     * Объединить в конференцию.
     *
     * Порядок — по документации Telecom:
     *
     * 1. вызовы указаны друг у друга как «можно объединить» — просим
     *    объединить именно их (так работает большинство сетей GSM);
     * 2. иначе, если вызов сам умеет собрать конференцию (сети CDMA
     *    и часть IMS) — mergeConference().
     *
     * Раньше был только первый путь, и на сетях второго типа кнопка
     * ничего не делала.
     */
    fun merge() {
        val a = primary ?: return
        val b = secondary ?: return
        when {
            b in a.conferenceableCalls -> a.conference(b)
            a.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> a.mergeConference()
            b.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> b.mergeConference()
            // Последняя попытка: часть прошивок не заполняет список, но
            // объединение принимает
            else -> a.conference(b)
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
    @Suppress("DEPRECATION")
    val canAddCall: Boolean
        get() {
            val c = primary ?: return false
            return c.state == Call.STATE_ACTIVE && canHold && secondary == null && waiting == null
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

    /** Состояния набираемого вызова: разбор у primary. */
    @Suppress("DEPRECATION")
    private val OUTGOING_STATES = setOf(
        Call.STATE_DIALING,
        Call.STATE_CONNECTING,
        Call.STATE_SELECT_PHONE_ACCOUNT,
        Call.STATE_NEW,
        Call.STATE_PULLING_CALL
    )
}
