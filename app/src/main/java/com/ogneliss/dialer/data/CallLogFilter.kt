package com.ogneliss.dialer.data

import androidx.compose.runtime.Immutable

/**
 * ФИЛЬТРЫ ЖУРНАЛА ВЫЗОВОВ.
 *
 * Чистая логика без Android — покрыта тестами.
 *
 * ── Зачем нужны ────────────────────────────────────────────────────────────
 * Самый частый вопрос к журналу: «кто мне звонил, пока я был занят».
 * Без фильтра пропущенные приходится выискивать глазами среди
 * десятков обычных записей, а на них ещё и красная метка мелкая.
 *
 * Второй по частоте — «с какой SIM шёл тот разговор». При двух картах
 * это вопрос денег: рабочие звонки на личном тарифе стоят иначе.
 *
 * ── Почему фильтры комбинируются, а не выбираются по одному ────────────────
 * «Пропущенные за эту неделю» — осмысленный запрос, и он требует
 * двух условий сразу. Взаимоисключающие фильтры заставили бы
 * пользователя фильтровать глазами то, что не отфильтровало приложение.
 */
/**
 * @Immutable: фильтр читается при отрисовке каждой строки журнала
 * и панели фильтров. Без пометки Compose считал бы его изменившимся
 * при любом обновлении — из-за поля types типа Set.
 */
@Immutable
data class CallLogFilter(
    val types: Set<CallType> = emptySet(),
    /** Индекс слота SIM; null — любая. */
    val simSlot: Int? = null,
    val period: Period = Period.ALL,
    /** Только номера, помеченные как спам. */
    val spamOnly: Boolean = false,
    /** Только номера, которых нет в контактах. */
    val unknownOnly: Boolean = false,
    /**
     * Только номера с заметкой.
     *
     * Заметка — то, что человек написал сам, и искать её приходится
     * именно тогда, когда забыл, к какому номеру она относилась:
     * «где-то я записал, когда привезут мебель».
     */
    val withNoteOnly: Boolean = false,
    /** Только номера с назначенным напоминанием. */
    val withReminderOnly: Boolean = false,
    /**
     * ОЖИДАЮТ ОТВЕТА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЧТО ЭТО ЗА ОТБОР
     * ══════════════════════════════════════════════════════════════════════
     * Пропущенный звонок, на который не перезвонили, — это дело,
     * о котором забыли. В журнале он растворяется среди отвеченных:
     * через сутки его не найти, не просматривая всё подряд.
     *
     * Правило простое и проверяемое: есть пропущенный, и после него
     * не было исходящего на этот же номер. Никаких догадок о важности —
     * только факты из журнала.
     *
     * ⚠️ Отклонённые сюда не входят. Звонок, который человек сбросил
     * сам, — это не забытое дело, а решение.
     */
    val awaitingCallbackOnly: Boolean = false
) {

    enum class Period { ALL, TODAY, WEEK, MONTH }

    /** Активен ли хоть один фильтр — от этого зависит вид кнопки сброса. */
    val isActive: Boolean
        get() = types.isNotEmpty() || simSlot != null ||
            period != Period.ALL || spamOnly || unknownOnly ||
            withNoteOnly || withReminderOnly || awaitingCallbackOnly

    /** Сколько условий выбрано — показывается на значке фильтра. */
    val activeCount: Int
        get() = listOf(
            types.isNotEmpty(),
            simSlot != null,
            period != Period.ALL,
            spamOnly,
            unknownOnly,
            withNoteOnly,
            withReminderOnly,
            awaitingCallbackOnly
        ).count { it }

    companion object {

        /**
         * Применение фильтра к списку карточек.
         *
         * @param slotResolver определяет слот SIM по идентификатору аккаунта
         * @param spamNumbers номера с метками спама
         * @param knownNumbers номера из телефонной книги
         */
        fun apply(
            groups: List<CallLogGroup>,
            filter: CallLogFilter,
            nowMillis: Long = System.currentTimeMillis(),
            slotResolver: (String?) -> Int? = { null },
            spamNumbers: Set<String> = emptySet(),
            knownNumbers: Set<String> = emptySet(),
            /** Номера, у которых есть заметка. */
            notedNumbers: Set<String> = emptySet(),
            /** Номера с назначенным напоминанием. */
            remindedNumbers: Set<String> = emptySet()
        ): List<CallLogGroup> {
            if (!filter.isActive) return groups

            val periodStart = periodStartMillis(filter.period, nowMillis)

            return groups.filter { group ->
                // Тип вызова. У карточки их может быть несколько:
                // группа из трёх звонков содержит и входящие, и пропущенные.
                // Показываем карточку, если хоть один вызов подходит —
                // иначе пропущенный звонок «спрячется» внутри группы
                // с последующим успешным разговором
                val typeMatches = filter.types.isEmpty() || filter.types.any { type ->
                    when (type) {
                        CallType.MISSED -> group.missedCount > 0
                        CallType.INCOMING -> group.incomingCount > 0
                        CallType.OUTGOING -> group.outgoingCount > 0
                        CallType.REJECTED, CallType.BLOCKED -> group.rejectedCount > 0
                        else -> group.lastType == type
                    }
                }

                val simMatches = filter.simSlot == null ||
                    slotResolver(group.subscriptionId) == filter.simSlot

                val periodMatches = periodStart == null || group.lastDate >= periodStart

                val spamMatches = !filter.spamOnly || group.e164 in spamNumbers

                val unknownMatches = !filter.unknownOnly ||
                    (group.e164 !in knownNumbers && group.contactId == null)

                /*
                 * Считаем по записям группы: последний пропущенный
                 * против последнего исходящего.
                 *
                 * Группа — это все звонки с одним номером, поэтому
                 * сравнение честное: перезвонили ли ПОСЛЕ того, как
                 * пропустили.
                 */
                val awaitingMatches = !filter.awaitingCallbackOnly || run {
                    val lastMissed = group.entries
                        .filter { it.type == CallType.MISSED }
                        .maxOfOrNull { it.date }
                    val lastOutgoing = group.entries
                        .filter { it.type == CallType.OUTGOING }
                        .maxOfOrNull { it.date } ?: 0L

                    lastMissed != null && lastMissed > lastOutgoing
                }

                val noteMatches = !filter.withNoteOnly || group.e164 in notedNumbers
                val reminderMatches = !filter.withReminderOnly ||
                    group.e164 in remindedNumbers

                typeMatches && simMatches && periodMatches && spamMatches &&
                    unknownMatches && noteMatches && reminderMatches && awaitingMatches
            }
        }

        /**
         * Начало периода.
         *
         * «Сегодня» считается от начала суток, а не «за последние 24 часа»:
         * человек мыслит календарными днями. Звонок вчера в 23:50
         * не должен попадать в «сегодня» только потому, что прошло
         * меньше суток.
         */
        fun periodStartMillis(period: Period, nowMillis: Long): Long? = when (period) {
            Period.ALL -> null
            Period.TODAY -> startOfDay(nowMillis)
            Period.WEEK -> startOfDay(nowMillis) - 6 * DAY_MILLIS
            Period.MONTH -> startOfDay(nowMillis) - 29 * DAY_MILLIS
        }

        private fun startOfDay(millis: Long): Long {
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = millis
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis
        }

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
