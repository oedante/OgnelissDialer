package com.ogneliss.dialer.ui.theme

import androidx.compose.ui.graphics.Color
import com.ogneliss.dialer.data.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Цвет стрелки типа вызова (2.7.0, форум, 21vasya). */
class CallArrowColorTest {
    private val red = Color(0xFF111111)
    private val grey = Color(0xFF222222)

    private fun c(type: CallType, colored: Boolean, dark: Boolean = false) =
        callArrowColor(type, colored, dark, red, grey)

    @Test
    fun `выключено — всё как было`() {
        assertEquals(grey, c(CallType.OUTGOING, colored = false))
        assertEquals(grey, c(CallType.INCOMING, colored = false))
        assertEquals(red, c(CallType.MISSED, colored = false))
        assertEquals(grey, c(CallType.REJECTED, colored = false))
    }

    @Test
    fun `включено — исходящий зелёный, входящий синий, пропущенный красный`() {
        assertEquals(CallGreen, c(CallType.OUTGOING, colored = true))
        assertEquals(MessageBlue, c(CallType.INCOMING, colored = true))
        assertEquals(red, c(CallType.MISSED, colored = true))
    }

    @Test
    fun `в тёмной теме оттенки светлее`() {
        assertEquals(Green80, c(CallType.OUTGOING, colored = true, dark = true))
        assertEquals(Blue80, c(CallType.INCOMING, colored = true, dark = true))
        assertNotEquals(CallGreen, c(CallType.OUTGOING, colored = true, dark = true))
    }

    @Test
    fun `отклонённый и заблокированный остаются нейтральными`() {
        assertEquals(grey, c(CallType.REJECTED, colored = true))
        assertEquals(grey, c(CallType.BLOCKED, colored = true))
        assertEquals(grey, c(CallType.VOICEMAIL, colored = true))
    }
}
