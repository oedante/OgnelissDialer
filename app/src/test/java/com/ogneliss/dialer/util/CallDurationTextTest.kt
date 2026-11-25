package com.ogneliss.dialer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Длительность в строке журнала (2.9.8, форум, Bublikov34). Разбор — CallDurationText. */
class CallDurationTextTest {

    @Test
    fun `без разговора - ничего`() {
        assertNull(CallDurationText.short(0))
        assertNull(CallDurationText.short(-5))
    }

    @Test
    fun `минуты и секунды`() {
        assertEquals("0:07", CallDurationText.short(7))
        assertEquals("2:35", CallDurationText.short(155))
        assertEquals("59:59", CallDurationText.short(3599))
    }

    @Test
    fun `с часа - часы`() {
        assertEquals("1:00:00", CallDurationText.short(3600))
        assertEquals("1:02:10", CallDurationText.short(3730))
    }
}
