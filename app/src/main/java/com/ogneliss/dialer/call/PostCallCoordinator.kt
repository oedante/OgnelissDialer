package com.ogneliss.dialer.call

import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * КООРДИНАТОР ЭКРАНА ПОСЛЕ ЗВОНКА.
 *
 * Назначение: связать завершение вызова в `InCallService` с показом
 * экрана заметки в интерфейсе.
 *
 * ── Почему через посредника, а не напрямую ─────────────────────────────────
 * `InCallService` живёт в том же процессе, но ничего не знает об экранах,
 * а Activity может быть вообще не запущена: пользователь принял звонок
 * с заблокированного экрана и после разговора вернулся на рабочий стол.
 *
 * Поэтому сервис просто кладёт сюда факт завершения, а интерфейс —
 * если он существует — заберёт его при следующем показе.
 *
 * ── Почему событие живёт ограниченное время ────────────────────────────────
 * Пользователь может открыть приложение через час после разговора.
 * Всплывающий тогда экран «добавьте заметку о звонке» выглядит нелепо
 * и раздражает. Событие протухает через несколько минут.
 */
@Singleton
class PostCallCoordinator @Inject constructor() {

    private companion object {
        const val TAG = "PostCall"

        /**
         * Сколько событие остаётся актуальным.
         * Две минуты: пользователь либо вернулся в приложение сразу
         * после разговора, либо уже занят другим.
         */
        const val EVENT_TTL_MS = 2 * 60 * 1000L

        /**
         * Ниже этой длительности разговор считается несостоявшимся.
         * Предлагать заметку о звонке, где никто не успел ничего сказать,
         * бессмысленно.
         */
        const val MIN_MEANINGFUL_DURATION_SEC = 3L
    }

    /** Данные завершённого вызова. */
    data class CompletedCall(
        val number: String,
        val displayName: String?,
        val durationSec: Long,
        val type: CallType,
        val wasAnswered: Boolean,
        val completedAt: Long = System.currentTimeMillis()
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - completedAt > EVENT_TTL_MS
    }

    private val _pending = MutableStateFlow<CompletedCall?>(null)
    val pending: StateFlow<CompletedCall?> = _pending

    /** Вызывается из InCallService при завершении вызова. */
    fun onCallCompleted(
        number: String?,
        displayName: String?,
        durationSec: Long,
        type: CallType
    ) {
        if (number.isNullOrBlank()) return

        val event = CompletedCall(
            number = number,
            displayName = displayName,
            durationSec = durationSec,
            type = type,
            wasAnswered = durationSec >= MIN_MEANINGFUL_DURATION_SEC
        )
        _pending.value = event
        Logger.d(TAG, "Вызов завершён: ${Logger.mask(number)}, ${durationSec}с")
    }

    /** Событие обработано или отклонено пользователем. */
    fun consume() {
        _pending.value = null
    }

    /**
     * Решение о показе экрана.
     *
     * ── Почему проверок так много ──────────────────────────────────────────
     * Экран после звонка есть и в списке желаемых функций, и в списке
     * раздражителей одновременно. Всплывающее окно после каждого разговора
     * с родными бесит быстрее, чем радует. Поэтому по умолчанию он
     * показывается только для незнакомых номеров и только когда разговор
     * действительно состоялся.
     *
     * @param enabled общий переключатель из настроек
     * @param unknownOnly показывать только для номеров вне контактов
     * @param isKnownContact номер есть в телефонной книге
     */
    fun shouldShow(
        event: CompletedCall?,
        enabled: Boolean,
        unknownOnly: Boolean,
        isKnownContact: Boolean
    ): Boolean {
        if (event == null) return false
        if (!enabled) return false
        if (event.isExpired) return false

        // Пропущенный или отклонённый вызов — заметку писать не о чем
        if (!event.wasAnswered) return false

        // Знакомый номер при включённой опции «только незнакомые»
        if (unknownOnly && isKnownContact) return false

        return true
    }
}
