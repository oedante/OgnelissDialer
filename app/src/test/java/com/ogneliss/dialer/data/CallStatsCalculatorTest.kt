package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ РАСЧЁТА СТАТИСТИКИ.
 *
 * Первая часть, вынесенная из ViewModel при его разборе. Пока расчёт жил
 * внутри класса на 1200 строк, проверить арифметику было нельзя:
 * пришлось бы поднимать весь граф зависимостей.
 *
 * Здесь же проверяются два решения, которые легко сделать неправильно:
 * что включать в среднюю длительность и от чего считать долю пропущенных.
 */
class CallStatsCalculatorTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun group(
        key: String = "g",
        incoming: Int = 0,
        outgoing: Int = 0,
        missed: Int = 0,
        duration: Long = 0,
        lastDate: Long = now
    ) = CallLogGroup(
        key = key, lastCallId = 1, number = "+7900$key", e164 = "+7900$key",
        displayName = null, photoUri = null, contactId = null,
        lastType = CallType.INCOMING, lastDate = lastDate, durationSec = 0,
        count = incoming + outgoing + missed,
        incomingCount = incoming, outgoingCount = outgoing,
        missedCount = missed, rejectedCount = 0,
        totalDurationSec = duration, numbers = listOf("+7900$key"),
        subscriptionLabel = null, subscriptionId = null
    )

    @Test
    fun `пустой журнал не ломает расчёт`() {
        val stats = CallStatsCalculator.calculate(emptyList(), now)
        assertTrue(stats.isEmpty)
        assertEquals(1, stats.coverageDays)
        assertEquals(0, stats.averageDurationSec)
    }

    @Test
    fun `счётчики складываются по всем карточкам`() {
        val stats = CallStatsCalculator.calculate(
            listOf(
                group("a", incoming = 3, outgoing = 2, missed = 1),
                group("b", incoming = 1, missed = 2)
            ),
            now
        )
        assertEquals(4, stats.incoming)
        assertEquals(2, stats.outgoing)
        assertEquals(3, stats.missed)
        assertEquals(9, stats.totalCalls)
    }

    // ── Средняя длительность ───────────────────────────────────────────────

    @Test
    fun `средняя длительность считается без пропущенных`() {
        // Два разговора по 60 секунд и восемь пропущенных с нулём.
        // Если делить на все десять вызовов, средняя станет 12 секунд —
        // цифра, которая ничего не значит
        val stats = CallStatsCalculator.calculate(
            listOf(group("a", incoming = 2, missed = 8, duration = 120)),
            now
        )
        assertEquals("Делить нужно на состоявшиеся разговоры", 60, stats.averageDurationSec)
    }

    @Test
    fun `журнал без состоявшихся разговоров даёт нулевую среднюю`() {
        val stats = CallStatsCalculator.calculate(
            listOf(group("a", missed = 5, duration = 0)), now
        )
        assertEquals(0, stats.averageDurationSec)
    }

    // ── Доля пропущенных ───────────────────────────────────────────────────

    @Test
    fun `доля пропущенных считается от входящих а не от всех вызовов`() {
        // 5 входящих, 5 пропущенных, 90 исходящих.
        // От всех вызовов вышло бы 5 %, и показатель выглядел бы отличным,
        // хотя половина входящих пропущена
        val stats = CallStatsCalculator.calculate(
            listOf(group("a", incoming = 5, missed = 5, outgoing = 90)),
            now
        )
        assertEquals("Исходящие пропустить нельзя", 50, stats.missedRatePercent)
    }

    @Test
    fun `журнал без входящих даёт нулевую долю пропущенных`() {
        val stats = CallStatsCalculator.calculate(
            listOf(group("a", outgoing = 10)), now
        )
        assertEquals(0, stats.missedRatePercent)
    }

    // ── Топ собеседников ───────────────────────────────────────────────────

    @Test
    fun `топ сортируется по числу вызовов`() {
        val stats = CallStatsCalculator.calculate(
            listOf(
                group("редкий", incoming = 1, duration = 10000),
                group("частый", incoming = 50, duration = 100)
            ),
            now
        )
        assertEquals(
            "Человек мыслит категориями «с кем чаще», а не «на кого потратил часы»",
            "частый", stats.topContacts.first().key
        )
    }

    @Test
    fun `топ ограничен по длине`() {
        val many = (1..30).map { group("g$it", incoming = it) }
        val stats = CallStatsCalculator.calculate(many, now)
        assertEquals(CallStatsCalculator.TOP_CONTACTS_LIMIT, stats.topContacts.size)
    }

    // ── Глубина покрытия ───────────────────────────────────────────────────

    @Test
    fun `глубина считается по самой старой записи`() {
        val stats = CallStatsCalculator.calculate(
            listOf(
                group("свежая", lastDate = now - day),
                group("старая", lastDate = now - 90 * day)
            ),
            now
        )
        assertEquals(90, stats.coverageDays)
    }

    @Test
    fun `глубина не может быть нулевой`() {
        // Иначе интерфейс показал бы «посчитано за 0 дней»
        val stats = CallStatsCalculator.calculate(listOf(group("a", lastDate = now)), now)
        assertTrue(stats.coverageDays >= 1)
    }
}
