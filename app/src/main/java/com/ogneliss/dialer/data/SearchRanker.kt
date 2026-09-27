package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.SortOrder
import com.ogneliss.dialer.t9.T9Engine

/**
 * РАНЖИРОВАНИЕ РЕЗУЛЬТАТОВ ПОИСКА.
 *
 * ── Почему порядок важнее, чем кажется ─────────────────────────────────────
 * При T9-поиске по трём цифрам находятся десятки контактов. Нужный обычно
 * один, и он должен быть первым — иначе пользователь набирает ещё цифры,
 * то есть поиск не сэкономил ему ни одного нажатия.
 *
 * До этого результаты выдавались в порядке телефонной книги, то есть
 * по алфавиту. «Абрамов», которому не звонили никогда, оказывался выше
 * «Любимой», которой звонят ежедневно.
 *
 * Настройка порядка сортировки существовала в Prefs, но нигде
 * не применялась — очередная настройка без эффекта.
 *
 * ── Что такое «релевантность» ──────────────────────────────────────────────
 * Комбинация трёх сигналов, а не один:
 *
 *   1. КАЧЕСТВО СОВПАДЕНИЯ. Совпадение с начала имени сильнее, чем
 *      с середины: набирая «526», человек обычно имеет в виду «Клинов»,
 *      а не «Александр Клин».
 *   2. ЧАСТОТА. С кем звонили чаще, тот и нужен вероятнее.
 *   3. ДАВНОСТЬ. Недавний собеседник вероятнее давнего, даже если
 *      суммарно звонков меньше.
 *
 * Один сигнал работает плохо: только по частоте наверх лезет служба
 * такси, только по давности — случайный вчерашний номер.
 */
object SearchRanker {

    /** Найденный контакт с сигналами для ранжирования. */
    data class Candidate(
        val displayName: String,
        val number: String,
        val e164: String,
        /** Число вызовов с этим номером. */
        val callCount: Int = 0,
        /** Время последнего вызова; 0 — звонков не было. */
        val lastCallDate: Long = 0,
        val starred: Boolean = false
    )

    /**
     * Вес совпадения с начала слова.
     * Такое совпадение почти всегда и есть искомое.
     */
    private const val PREFIX_MATCH_BONUS = 1000.0

    /** Вес избранного: пользователь уже отметил, что человек важен. */
    private const val STARRED_BONUS = 500.0

    /** Насколько сильно частота влияет на позицию. */
    private const val FREQUENCY_WEIGHT = 20.0

    /** Окно, в котором давность ещё что-то значит. */
    private const val RECENCY_WINDOW_DAYS = 90

    fun rank(
        candidates: List<Candidate>,
        query: String,
        order: SortOrder,
        nowMillis: Long = System.currentTimeMillis()
    ): List<Candidate> = when (order) {
        SortOrder.ALPHABET -> candidates.sortedBy { it.displayName.lowercase() }

        SortOrder.FREQUENCY -> candidates.sortedWith(
            compareByDescending<Candidate> { it.callCount }
                .thenBy { it.displayName.lowercase() }
        )

        SortOrder.RECENCY -> candidates.sortedWith(
            compareByDescending<Candidate> { it.lastCallDate }
                .thenBy { it.displayName.lowercase() }
        )

        SortOrder.RELEVANCE -> candidates
            .sortedByDescending { score(it, query, nowMillis) }
    }

    /**
     * Оценка кандидата. Больше — выше в списке.
     *
     * Сигналы складываются, а не перемножаются: перемножение обнулило бы
     * вклад качества совпадения для контакта без единого звонка,
     * а именно ему поиск и нужнее всего.
     */
    fun score(candidate: Candidate, query: String, nowMillis: Long): Double {
        var score = 0.0
        val digits = query.filter { it.isDigit() }

        // 1. Качество совпадения
        if (digits.isNotEmpty()) {
            val matchIndex = T9Engine.matchIndex(digits, candidate.displayName)
            score += when {
                matchIndex == 0 -> PREFIX_MATCH_BONUS
                matchIndex > 0 -> PREFIX_MATCH_BONUS / 2
                else -> 0.0
            }
            // Совпадение с началом номера тоже сильный сигнал:
            // человек часто помнит первые цифры
            if (T9Engine.normalizeNumber(candidate.number).startsWith(digits)) {
                score += PREFIX_MATCH_BONUS / 2
            }
        }

        // 2. Избранное
        if (candidate.starred) score += STARRED_BONUS

        // 3. Частота. Логарифм, а не линейная зависимость: разница между
        // 1 и 5 звонками важнее, чем между 100 и 105
        if (candidate.callCount > 0) {
            score += FREQUENCY_WEIGHT * kotlin.math.ln(1.0 + candidate.callCount)
        }

        // 4. Давность в пределах окна
        if (candidate.lastCallDate > 0) {
            val daysAgo = (nowMillis - candidate.lastCallDate) / DAY_MILLIS
            if (daysAgo < RECENCY_WINDOW_DAYS) {
                score += (RECENCY_WINDOW_DAYS - daysAgo).toDouble()
            }
        }

        return score
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
