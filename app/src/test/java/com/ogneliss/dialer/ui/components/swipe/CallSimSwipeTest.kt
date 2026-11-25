package com.ogneliss.dialer.ui.components.swipe

import com.ogneliss.dialer.settings.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * «Позвонить с SIM 1 / SIM 2» (2.9.8, форум, Bublikov34: «свайп в одну сторону —
 * сим-карта 1 с её значком в момент свайпа, в другую — сим-карта 2»).
 */
class CallSimSwipeTest {

    private val strings = object : RevealActionFactory.Strings {
        override fun callTo(number: String) = "call $number"
        override fun callWithSim(number: String, simLabel: String) = "call $number $simLabel"
        override fun smsTo(number: String) = "sms $number"
        override fun messengerTo(number: String) = "msg $number"
        override fun actionLabel(action: SwipeAction) = action.name
    }

    @Test
    fun `слот карты у действия`() {
        assertEquals(0, SwipeAction.simSlotOf(SwipeAction.CALL_SIM1))
        assertEquals(1, SwipeAction.simSlotOf(SwipeAction.CALL_SIM2))
        assertNull(SwipeAction.simSlotOf(SwipeAction.CALL))
    }

    @Test
    fun `при свайпе виден номер карты`() {
        assertEquals(1, RevealActionFactory.appearanceOnly(SwipeAction.CALL_SIM1, strings).simBadge)
        assertEquals(2, RevealActionFactory.appearanceOnly(SwipeAction.CALL_SIM2, strings).simBadge)
        assertNull(RevealActionFactory.appearanceOnly(SwipeAction.CALL, strings).simBadge)
    }

    @Test
    fun `кнопка звонит со своей карты`() {
        val calls = mutableListOf<Pair<String, Int>>()
        val actions = RevealActionFactory.build(
            action = SwipeAction.CALL_SIM2,
            numbers = listOf("+79001112233"),
            labels = listOf(null),
            sims = emptyList(),
            strings = strings,
            onCall = { number, slot -> calls += number to slot },
            onSms = {},
            onMessenger = {},
            onSimple = {}
        )
        assertEquals(1, actions.size)
        assertEquals(2, actions.single().simBadge)
        actions.single().onClick()
        assertEquals(listOf("+79001112233" to 1), calls)
    }
}
