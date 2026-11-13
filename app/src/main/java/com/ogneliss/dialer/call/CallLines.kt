package com.ogneliss.dialer.call

import android.telecom.Call

/**
 * ЛИНИИ ВЫЗОВА — ПРАВИЛА БЕЗ TELECOM (2.4.2).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОТДЕЛЬНО
 * ══════════════════════════════════════════════════════════════════════════
 * Второй вызов, удержание и конференция — самая сложная логика приложения,
 * а проверить её можно было только живым звонком: на эмуляторе второй линии
 * нет, объект Call в тестах не создать. Обзор надёжности (автор взял его
 * 13.11.2026): у 642 тестов не было ни одного на CallManager.
 *
 * Здесь — все решения CallManager о линиях: какой вызов текущий, какой
 * удержан, какой ждёт ответа, что можно (удержать, добавить, объединить)
 * и что делать по кнопке (переключить, объединить, «Завершить и ответить»).
 * На входе — [Line]: состояние, участник ли конференции, возможности,
 * с кем можно объединить. CallManager строит линии из объектов Telecom
 * и выполняет решение; проверки — CallLinesTest, по сценариям.
 *
 * Поведение то же, что было в CallManager 2.3.0; разборы «почему так» —
 * у каждого правила ниже.
 */
data class Line<K>(
    val key: K,
    /** Call.STATE_*. */
    val state: Int,
    /** Участник конференции: у него есть родитель — сама конференция. */
    val hasParent: Boolean = false,
    /** Call.Details.getCallCapabilities(). */
    val capabilities: Int = 0,
    /** С какими вызовами сеть разрешает объединить (Call.getConferenceableCalls). */
    val conferenceable: Set<K> = emptySet(),
    /** Сама конференция (PROPERTY_CONFERENCE или есть участники), 2.4.3. */
    val conference: Boolean = false
) {
    fun can(capability: Int): Boolean = capabilities and capability == capability
}

@Suppress("DEPRECATION")
object CallLines {

    /** Состояния набираемого вызова: разбор у [primary]. */
    private val OUTGOING_STATES = setOf(
        Call.STATE_DIALING,
        Call.STATE_CONNECTING,
        Call.STATE_SELECT_PHONE_ACCOUNT,
        Call.STATE_NEW,
        Call.STATE_PULLING_CALL
    )

    /*
     * ВЫЗОВЫ ВЕРХНЕГО УРОВНЯ.
     *
     * Когда два вызова объединяют в конференцию, Telecom создаёт третий
     * объект — саму конференцию, — а два прежних становятся её участниками
     * (у них появляется parent). Все три лежат в общем списке. Выбор
     * текущего среди всех мог взять участника вместо конференции: экран
     * показывал одного собеседника, а «Завершить» клал трубку только ему.
     */
    fun <K> topLevel(lines: List<Line<K>>): List<Line<K>> = lines.filter {
        !it.hasParent && it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
    }

    /**
     * Текущий вызов — тот, с которым человек сейчас имеет дело:
     * 1. идущий разговор (ACTIVE);
     * 2. набираемый (DIALING и соседние): после «Добавить вызов» первый
     *    ушёл на удержание, и экран показывает новый;
     * 3. звонящий (RINGING), когда других нет;
     * 4. удержанный — если больше ничего не осталось.
     * Было «активный, иначе первый живой»: во время набора второго номера
     * первым живым оказывался удержанный — экран показывал не тот вызов.
     */
    fun <K> primary(lines: List<Line<K>>): Line<K>? {
        val live = topLevel(lines)
        return live.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: live.firstOrNull { it.state in OUTGOING_STATES }
            ?: live.firstOrNull { it.state == Call.STATE_RINGING }
            ?: live.firstOrNull()
    }

    /** Вторая линия — вызов на удержании, пока говорим по первой. */
    fun <K> secondary(lines: List<Line<K>>): Line<K>? {
        val main = primary(lines)
        return topLevel(lines).firstOrNull { it.key != main?.key && it.state == Call.STATE_HOLDING }
    }

    /** Второй входящий во время разговора: звонит, но текущий — другой. */
    fun <K> waiting(lines: List<Line<K>>): Line<K>? {
        val main = primary(lines)
        return topLevel(lines).firstOrNull { it.key != main?.key && it.state == Call.STATE_RINGING }
    }

    fun <K> hasIncoming(lines: List<Line<K>>): Boolean = topLevel(lines).any { it.state == Call.STATE_RINGING }

    /**
     * Можно ли удержать текущий. Удержанный снять можно всегда — иначе
     * человек застрял бы с вызовом, который нельзя вернуть. Иначе — только
     * CAPABILITY_HOLD (2.3.0): CAPABILITY_SUPPORT_HOLD значит «сеть умеет,
     * но сейчас нельзя» (участник конференции), плитка горела бы впустую.
     */
    fun <K> canHold(lines: List<Line<K>>): Boolean {
        val c = primary(lines) ?: return false
        if (c.state == Call.STATE_HOLDING) return true
        return c.can(Call.Details.CAPABILITY_HOLD)
    }

    /** Что делает «Удержать»: снять удержание или удержать текущий. */
    fun <K> holdAction(lines: List<Line<K>>): HoldAction<K>? {
        val c = primary(lines) ?: return null
        return if (c.state == Call.STATE_HOLDING) HoldAction.Unhold(c.key) else HoldAction.Hold(c.key)
    }

    /**
     * Можно ли набрать ещё номер, не прерывая разговор: текущий идёт,
     * его можно удержать, второй линии и ждущего входящего нет, и Telecom
     * не против ([telecomAllows] — InCallService.canAddCall: экстренный
     * вызов, предел вызовов, ограничения сети; 2.3.0).
     */
    fun <K> canAddCall(lines: List<Line<K>>, telecomAllows: Boolean): Boolean {
        val c = primary(lines) ?: return false
        return telecomAllows && c.state == Call.STATE_ACTIVE && canHold(lines) &&
            secondary(lines) == null && waiting(lines) == null
    }

    /**
     * ПЕРЕКЛЮЧИТЬ ЛИНИИ (2.3.0). Конференция, которая умеет переключаться
     * сама (CAPABILITY_SWAP_CONFERENCE), — её просим. Иначе — только снять
     * удержанный: удержание текущего делает Telecom (CallsManager.unholdCall).
     * Было hold() + unhold() — два запроса удержания подряд, на VoLTE второй
     * отклонялся, и обе линии могли остаться на удержании.
     */
    fun <K> swap(lines: List<Line<K>>): Swap<K>? {
        val current = primary(lines)
        /*
         * Только между соединёнными линиями (2.4.3): пока второй номер
         * набирается, снять удержание с первого значит дать Telecom повод
         * сбросить набор — на части сетей так и выходит. Системный диалер
         * в это время «Переключить» гасит.
         */
        if (current != null && current.state != Call.STATE_ACTIVE && current.state != Call.STATE_HOLDING) return null
        if (current != null && current.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)) return Swap.Conference(current.key)
        val held = secondary(lines) ?: return null
        return Swap.Unhold(held.key)
    }

    /** Можно ли переключить линии сейчас (плитка «Переключить», плашка удержанной). */
    fun <K> canSwap(lines: List<Line<K>>): Boolean = swap(lines) != null

    /** Можно ли объединить: сеть указала их друг другу или вызов сам собирает конференцию. */
    fun <K> canMerge(lines: List<Line<K>>): Boolean {
        val a = primary(lines) ?: return false
        val b = secondary(lines) ?: return false
        return b.key in a.conferenceable ||
            a.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) ||
            b.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)
    }

    /**
     * Объединить — по документации Telecom: указаны друг у друга —
     * conference() (большинство сетей GSM); вызов сам собирает конференцию —
     * mergeConference() (CDMA, часть IMS); иначе всё равно conference():
     * часть прошивок не заполняет список, но объединение принимает.
     */
    fun <K> merge(lines: List<Line<K>>): Merge<K>? {
        val a = primary(lines) ?: return null
        val b = secondary(lines) ?: return null
        return when {
            b.key in a.conferenceable -> Merge.Conference(a.key, b.key)
            a.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> Merge.MergeConference(a.key)
            b.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> Merge.MergeConference(b.key)
            else -> Merge.Conference(a.key, b.key)
        }
    }

    /**
     * «ЗАВЕРШИТЬ И ОТВЕТИТЬ» (2.3.0). Есть текущий другой вызов — сначала
     * завершить его, ответить, когда он перешёл в «завершён» (как
     * AnswerOnDisconnected системного диалера): иначе Telecom, отвечая,
     * видел первый живым и пытался его удержать — «удержание не удалось».
     * Текущего нет — ответить сразу.
     */
    fun <K> endAndAnswer(lines: List<Line<K>>): EndAndAnswer<K>? {
        val incoming = waiting(lines) ?: return null
        val current = primary(lines)
        return if (current == null || current.key == incoming.key) EndAndAnswer.AnswerNow(incoming.key)
        else EndAndAnswer.ReleaseFirst(current.key, incoming.key)
    }

    /**
     * КАК ОТВЕТИТЬ НА ВТОРОЙ ВХОДЯЩИЙ (2.4.3, обзор логики звонков).
     *
     * Текущий можно удержать (CAPABILITY_HOLD) — «Ответить и удержать».
     * Нельзя (сеть не держит удержание, набирается исходящий, экстренный) —
     * только «Завершить и ответить»: Telecom, отвечая, сам завершил бы
     * неудерживаемый вызов, и кнопка «Ответить и удержать» обещала бы
     * не то, что произойдёт. Так же делает системный диалер. Ждущего
     * нет — null.
     */
    fun <K> waitingAnswer(lines: List<Line<K>>): WaitingAnswer? {
        waiting(lines) ?: return null
        val current = primary(lines) ?: return WaitingAnswer.HOLD_CURRENT
        return if (current.can(Call.Details.CAPABILITY_HOLD)) WaitingAnswer.HOLD_CURRENT else WaitingAnswer.END_CURRENT
    }

    enum class WaitingAnswer { HOLD_CURRENT, END_CURRENT }

    /**
     * Ответить ли теперь: завершился именно тот вызов, которого ждали,
     * а второй ещё звонит (мог успеть сбросить — тогда отвечать не на что).
     */
    fun <K> answerAfterRelease(pending: Pair<K, K>?, released: K, incomingState: Int?): Boolean =
        pending != null && pending.first == released && incomingState == Call.STATE_RINGING

    sealed interface HoldAction<K> {
        data class Hold<K>(val key: K) : HoldAction<K>
        data class Unhold<K>(val key: K) : HoldAction<K>
    }

    sealed interface Swap<K> {
        data class Conference<K>(val key: K) : Swap<K>
        data class Unhold<K>(val key: K) : Swap<K>
    }

    sealed interface Merge<K> {
        data class Conference<K>(val key: K, val with: K) : Merge<K>
        data class MergeConference<K>(val key: K) : Merge<K>
    }

    sealed interface EndAndAnswer<K> {
        data class AnswerNow<K>(val incoming: K) : EndAndAnswer<K>
        data class ReleaseFirst<K>(val current: K, val incoming: K) : EndAndAnswer<K>
    }
}
