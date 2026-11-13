package com.ogneliss.dialer.sms

import com.ogneliss.dialer.data.db.ScheduledSmsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Очередь SMS (2.4.2): пометка «отправляется» вместо удаления до отправки,
 * прерванная отправка — в черновик, будильник и на проверку помеченных.
 */
class SmsQueueRulesTest {

    private val now = 1_763_000_000_000L

    private fun row(id: Long, at: Long, scheduled: Boolean = true) =
        ScheduledSmsEntity(id = id, address = "+79000000000", addressKey = "79000000000", body = "т", subId = null, atMillis = at, scheduled = scheduled)

    @Test
    fun `пометка всегда меньше нуля и возвращает время`() {
        val claimed = row(1, SmsQueueRules.claimMark(now))
        assertTrue(SmsQueueRules.isClaimed(claimed))
        assertEquals(now, SmsQueueRules.claimedAt(claimed))
        assertNull(SmsQueueRules.claimedAt(row(2, now)))
        // Нулевое время не даёт «ждущей» пометки
        assertTrue(SmsQueueRules.claimMark(0L) < 0)
    }

    @Test
    fun `в отправку берутся только ждущие, у которых пришёл срок`() {
        val rows = listOf(
            row(1, now - 1_000),
            row(2, now + 60_000),
            row(3, SmsQueueRules.claimMark(now - 5_000)),
            row(4, now + 10_000, scheduled = true)
        )
        assertEquals(listOf(1L, 4L), SmsQueueRules.due(rows, now).map { it.id })
    }

    @Test
    fun `окно отмены выдерживается целиком — без запаса раньше`() {
        val undo = row(1, now + 5_000, scheduled = false)
        assertTrue(SmsQueueRules.due(listOf(undo), now).isEmpty())
        assertEquals(1, SmsQueueRules.due(listOf(undo), now + 5_000).size)
    }

    @Test
    fun `просроченное больше суток — не в сеть`() {
        assertTrue(SmsQueueRules.isStale(row(1, now - SmsQueueRules.STALE_MS - 1), now))
        assertFalse(SmsQueueRules.isStale(row(2, now - 60_000), now))
    }

    @Test
    fun `прерванная отправка — пометка старше двух минут`() {
        val fresh = row(1, SmsQueueRules.claimMark(now - 30_000))
        val stuck = row(2, SmsQueueRules.claimMark(now - SmsQueueRules.RECOVER_MS - 1))
        val waiting = row(3, now - 10_000_000)
        assertEquals(listOf(2L), SmsQueueRules.interrupted(listOf(fresh, stuck, waiting), now).map { it.id })
    }

    @Test
    fun `будильник — и на ближайший срок, и на проверку отправляемого`() {
        assertNull(SmsQueueRules.nextWake(emptyList()))
        assertEquals(now + 60_000, SmsQueueRules.nextWake(listOf(row(1, now + 60_000), row(2, now + 120_000))))
        // Отправляемое без других строк: будить на проверку, иначе ждало бы перезагрузки
        val claimedAt = now - 10_000
        assertEquals(
            claimedAt + SmsQueueRules.RECOVER_MS + 1_000,
            SmsQueueRules.nextWake(listOf(row(1, SmsQueueRules.claimMark(claimedAt))))
        )
    }

    @Test
    fun `переписка показывает только ждущие`() {
        val rows = listOf(row(1, now), row(2, SmsQueueRules.claimMark(now)))
        assertEquals(listOf(1L), SmsQueueRules.waiting(rows).map { it.id })
    }
}
