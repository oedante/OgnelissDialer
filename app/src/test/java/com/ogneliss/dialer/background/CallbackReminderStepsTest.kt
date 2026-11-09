package com.ogneliss.dialer.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Напоминание без точных будильников (2.1.1): к сроку ступенями.
 * Неточный будильник система вправе сдвинуть на 3/4 оставшегося — каждая
 * промежуточная побудка, даже сдвинутая так, обязана прийти раньше срока,
 * а последняя опоздать не больше чем на несколько минут.
 */
class CallbackReminderStepsTest {

    private val minute = 60_000L

    @Test
    fun `близко к сроку — сам срок`() {
        val now = 1_000_000L
        assertEquals(now + 5 * minute, CallbackReminder.inexactWake(now, now + 5 * minute))
        assertEquals(now + 10 * minute, CallbackReminder.inexactWake(now, now + 10 * minute))
    }

    @Test
    fun `далеко — середина, и даже худший сдвиг приходит раньше срока`() {
        val at = 10L * 60 * minute + 1_000_000L      // через 10 часов
        var now = 1_000_000L
        var steps = 0
        while (true) {
            val wake = CallbackReminder.inexactWake(now, at)
            if (wake == at) break
            val worst = wake + (0.75 * (wake - now)).toLong()
            assertTrue("побудка позже срока", worst < at)
            now = worst                                 // худший случай: опоздание на 3/4
            steps++
            assertTrue("слишком много побудок", steps < 20)
        }
        // последняя ступень — не дальше 10 минут: опоздание не больше 7,5 минуты
        assertTrue(at - now <= 10 * minute)
    }

    @Test
    fun `побудка за минуту до срока — уже срок`() {
        val at = 5_000_000L
        assertTrue(CallbackReminder.isDue(at - 30_000L, at))
        assertTrue(CallbackReminder.isDue(at + 1, at))
        assertFalse(CallbackReminder.isDue(at - 5 * minute, at))
        // будильник прежней версии без срока — срабатывает
        assertTrue(CallbackReminder.isDue(at, 0L))
    }
}
