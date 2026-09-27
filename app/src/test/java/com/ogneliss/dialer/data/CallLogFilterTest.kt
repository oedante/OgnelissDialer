package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ФИЛЬТРОВ ЖУРНАЛА.
 *
 * Главная тонкость: карточка журнала объединяет несколько вызовов
 * разных типов. Группа из трёх звонков может содержать и пропущенный,
 * и успешный разговор.
 *
 * Наивная фильтрация по типу ПОСЛЕДНЕГО вызова спрятала бы пропущенный
 * звонок внутри группы, где после него был успешный разговор — то есть
 * именно то, что пользователь и ищет, включая фильтр «Пропущенные».
 */
class CallLogFilterTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private val now = 1_700_000_000_000L

    private fun group(
        key: String = "g1",
        e164: String = "+79001111111",
        lastType: CallType = CallType.INCOMING,
        lastDate: Long = now,
        incoming: Int = 1,
        outgoing: Int = 0,
        missed: Int = 0,
        rejected: Int = 0,
        sim: String? = "sim1",
        contactId: Long? = null
    ) = CallLogGroup(
        key = key, lastCallId = 1, number = e164, e164 = e164,
        displayName = null, photoUri = null, contactId = contactId,
        lastType = lastType, lastDate = lastDate, durationSec = 30,
        count = incoming + outgoing + missed + rejected,
        incomingCount = incoming, outgoingCount = outgoing,
        missedCount = missed, rejectedCount = rejected,
        totalDurationSec = 30, numbers = listOf(e164),
        subscriptionLabel = null, subscriptionId = sim
    )

    // ── Ключевой случай: пропущенный внутри группы ─────────────────────────

    @Test
    fun `пропущенный внутри группы с успешным разговором находится`() {
        // Человек звонил, вы не ответили, он перезвонил — и вы поговорили.
        // Последний вызов входящий, но пропущенный в группе есть,
        // и фильтр «Пропущенные» обязан показать эту карточку
        val groups = listOf(
            group(lastType = CallType.INCOMING, incoming = 1, missed = 2)
        )
        val result = CallLogFilter.apply(
            groups, CallLogFilter(types = setOf(CallType.MISSED)), now
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `группа без пропущенных отсеивается`() {
        val groups = listOf(group(incoming = 3, missed = 0))
        val result = CallLogFilter.apply(
            groups, CallLogFilter(types = setOf(CallType.MISSED)), now
        )
        assertTrue(result.isEmpty())
    }

    // ── Комбинирование условий ─────────────────────────────────────────────

    @Test
    fun `фильтры складываются а не заменяют друг друга`() {
        // «Пропущенные за сегодня» — осмысленный запрос из двух условий
        val groups = listOf(
            group(key = "today", missed = 1, incoming = 0, lastDate = now),
            group(key = "old", missed = 1, incoming = 0, lastDate = now - 10 * day)
        )
        val result = CallLogFilter.apply(
            groups,
            CallLogFilter(
                types = setOf(CallType.MISSED),
                period = CallLogFilter.Period.TODAY
            ),
            now
        )
        assertEquals(1, result.size)
        assertEquals("today", result.first().key)
    }

    @Test
    fun `несколько типов работают как ИЛИ`() {
        val groups = listOf(
            group(key = "in", incoming = 1),
            group(key = "out", incoming = 0, outgoing = 1),
            group(key = "missed", incoming = 0, missed = 1)
        )
        val result = CallLogFilter.apply(
            groups,
            CallLogFilter(types = setOf(CallType.INCOMING, CallType.OUTGOING)),
            now
        )
        assertEquals(2, result.size)
    }

    // ── Периоды ────────────────────────────────────────────────────────────

    @Test
    fun `сегодня считается от начала суток а не за 24 часа`() {
        // Звонок вчера в 23:50 не должен попадать в «сегодня» только
        // потому, что прошло меньше суток: человек мыслит календарными днями
        val startOfToday = CallLogFilter.periodStartMillis(
            CallLogFilter.Period.TODAY, now
        )!!
        val yesterdayLate = startOfToday - hour

        val groups = listOf(group(lastDate = yesterdayLate))
        val result = CallLogFilter.apply(
            groups, CallLogFilter(period = CallLogFilter.Period.TODAY), now
        )
        assertTrue("Вчерашний вечер не относится к сегодня", result.isEmpty())
    }

    @Test
    fun `неделя охватывает семь календарных дней`() {
        val groups = listOf(
            group(key = "recent", lastDate = now - 5 * day),
            group(key = "old", lastDate = now - 10 * day)
        )
        val result = CallLogFilter.apply(
            groups, CallLogFilter(period = CallLogFilter.Period.WEEK), now
        )
        assertEquals(1, result.size)
        assertEquals("recent", result.first().key)
    }

    // ── SIM ────────────────────────────────────────────────────────────────

    @Test
    fun `фильтр по SIM использует переданный резолвер`() {
        val groups = listOf(
            group(key = "sim0", sim = "acc0"),
            group(key = "sim1", sim = "acc1")
        )
        val result = CallLogFilter.apply(
            groups, CallLogFilter(simSlot = 1), now,
            slotResolver = { if (it == "acc1") 1 else 0 }
        )
        assertEquals(1, result.size)
        assertEquals("sim1", result.first().key)
    }

    // ── Незнакомые и спам ──────────────────────────────────────────────────

    @Test
    fun `фильтр незнакомых отсеивает контакты`() {
        val groups = listOf(
            group(key = "known", e164 = "+79001111111", contactId = 5),
            group(key = "unknown", e164 = "+79002222222")
        )
        val result = CallLogFilter.apply(
            groups, CallLogFilter(unknownOnly = true), now,
            knownNumbers = setOf("+79001111111")
        )
        assertEquals(1, result.size)
        assertEquals("unknown", result.first().key)
    }

    @Test
    fun `фильтр спама использует переданные метки`() {
        val groups = listOf(
            group(key = "spam", e164 = "+78001111111"),
            group(key = "normal", e164 = "+79002222222")
        )
        val result = CallLogFilter.apply(
            groups, CallLogFilter(spamOnly = true), now,
            spamNumbers = setOf("+78001111111")
        )
        assertEquals(1, result.size)
        assertEquals("spam", result.first().key)
    }

    // ── Служебное ──────────────────────────────────────────────────────────

    @Test
    fun `пустой фильтр возвращает список без изменений`() {
        val groups = listOf(group(key = "a"), group(key = "b"))
        val result = CallLogFilter.apply(groups, CallLogFilter(), now)
        assertEquals(groups, result)
    }

    @Test
    fun `признак активности отражает состояние`() {
        assertFalse(CallLogFilter().isActive)
        assertTrue(CallLogFilter(spamOnly = true).isActive)
        assertTrue(CallLogFilter(period = CallLogFilter.Period.WEEK).isActive)
    }

    @Test
    fun `счётчик активных условий считает каждое отдельно`() {
        val filter = CallLogFilter(
            types = setOf(CallType.MISSED),
            period = CallLogFilter.Period.WEEK,
            spamOnly = true
        )
        assertEquals(3, filter.activeCount)
    }
}
