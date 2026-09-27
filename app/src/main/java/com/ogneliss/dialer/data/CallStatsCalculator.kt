package com.ogneliss.dialer.data

import androidx.compose.runtime.Immutable

/**
 * РАСЧЁТ СТАТИСТИКИ ПО ЖУРНАЛУ.
 *
 * ── Почему вынесено из ViewModel ───────────────────────────────────────────
 * Расчёт не зависит ни от Android, ни от репозиториев — только от списка
 * карточек. Пока он жил внутри ViewModel на 1200 строк, проверить его
 * было нельзя: пришлось бы поднимать весь граф зависимостей ради
 * арифметики.
 *
 * Это первый шаг разбора ViewModel: обособленные части выносятся
 * по одной, каждая с тестами.
 *
 * ── Про честность цифр ─────────────────────────────────────────────────────
 * Считаем по тому, что реально загружено. Система хранит ограниченный
 * журнал, и делать вид, что мы знаем больше, было бы обманом: поэтому
 * возвращается и глубина покрытия, которую интерфейс показывает рядом
 * с итогами.
 */
object CallStatsCalculator {

    /** Сколько собеседников показывать в топе. */
    const val TOP_CONTACTS_LIMIT = 10

    /**
     * @Immutable: сводка отображается на экране статистики целиком,
     * и поле topContacts типа List делало бы её нестабильной.
     */
    @Immutable
    data class CallStats(
        val totalCalls: Int,
        val incoming: Int,
        val outgoing: Int,
        val missed: Int,
        val totalDurationSec: Long,
        val topContacts: List<CallLogGroup>,
        /** За сколько дней посчитаны цифры. */
        val coverageDays: Int,
        /** Средняя длительность состоявшегося разговора, секунды. */
        val averageDurationSec: Long,
        /** Доля пропущенных от всех входящих, проценты. */
        val missedRatePercent: Int
    ) {
        val isEmpty: Boolean get() = totalCalls == 0
    }

    fun calculate(
        groups: List<CallLogGroup>,
        nowMillis: Long = System.currentTimeMillis()
    ): CallStats {
        if (groups.isEmpty()) {
            return CallStats(0, 0, 0, 0, 0, emptyList(), 1, 0, 0)
        }

        val incoming = groups.sumOf { it.incomingCount }
        val outgoing = groups.sumOf { it.outgoingCount }
        val missed = groups.sumOf { it.missedCount }
        val totalDuration = groups.sumOf { it.totalDurationSec }

        val oldest = groups.minOf { it.lastDate }
        val coverageDays = ((nowMillis - oldest) / DAY_MILLIS).toInt().coerceAtLeast(1)

        // Средняя длительность считается только по состоявшимся разговорам:
        // пропущенные с нулевой длительностью занизили бы её вдвое
        // и цифра перестала бы что-либо значить
        val answeredCalls = incoming + outgoing
        val average = if (answeredCalls > 0) totalDuration / answeredCalls else 0L

        // Доля пропущенных от ВСЕХ входящих, а не от всех вызовов:
        // исходящие пропустить нельзя, и включать их в знаменатель
        // значило бы занижать показатель
        val allIncoming = incoming + missed
        val missedRate = if (allIncoming > 0) missed * 100 / allIncoming else 0

        return CallStats(
            totalCalls = groups.sumOf { it.count },
            incoming = incoming,
            outgoing = outgoing,
            missed = missed,
            totalDurationSec = totalDuration,
            // Топ по числу вызовов, а не по времени: человек мыслит
            // категориями «с кем чаще общаюсь», а не «на кого потратил часы»
            topContacts = groups.sortedByDescending { it.count }.take(TOP_CONTACTS_LIMIT),
            coverageDays = coverageDays,
            averageDurationSec = average,
            missedRatePercent = missedRate
        )
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
