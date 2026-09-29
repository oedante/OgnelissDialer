package com.ogneliss.dialer.ui.components.swipe

import com.ogneliss.dialer.settings.SwipeAction
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Выключенный свайп («Нет действия») не должен давать ни одной кнопки.
 *
 * Раньше NONE попадал в общую ветку и превращался в кнопку панели:
 * в контактах выключенный свайп раскрывал панель с кнопкой, которая
 * ничего не делает. Пустой список для строки свайпа означает
 * «в эту сторону не тянется».
 */
class RevealActionFactoryTest {

    private val strings = object : RevealActionFactory.Strings {
        override fun callTo(number: String) = "call $number"
        override fun callWithSim(number: String, simLabel: String) = "call $number $simLabel"
        override fun smsTo(number: String) = "sms $number"
        override fun messengerTo(number: String) = "msg $number"
        override fun actionLabel(action: SwipeAction) = action.name
    }

    private fun build(action: SwipeAction, numbers: List<String>) = RevealActionFactory.build(
        action = action,
        numbers = numbers,
        labels = numbers.map { null },
        sims = emptyList(),
        strings = strings,
        onCall = { _, _ -> },
        onSms = {},
        onMessenger = {},
        onSimple = {}
    )

    @Test
    fun `NONE — ни одной кнопки, сколько бы номеров ни было`() {
        assertTrue(build(SwipeAction.NONE, listOf("+79001112233")).isEmpty())
        assertTrue(build(SwipeAction.NONE, listOf("+79001112233", "+79004445566")).isEmpty())
    }

    @Test
    fun `включённый свайп по-прежнему даёт кнопки`() {
        // Несколько номеров — панель выбора номера
        assertTrue(build(SwipeAction.SMS, listOf("+79001112233", "+79004445566")).isNotEmpty())
    }
}
