package com.ogneliss.dialer.ui.theme

import com.ogneliss.dialer.settings.UiScale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * МАСШТАБ ИНТЕРФЕЙСА (1.8.1): 90 % по умолчанию — не поверх увеличенного
 * «Размера экрана» Android и не на планшете. Разбор — UiScaleMath.factor.
 *
 * Плотность передаётся явно: заводская 420 dpi, «Размер экрана» Android
 * крупнее — 480.
 */
class UiScaleMathTest {

    private val stable = 420

    @Test
    fun `обычный телефон — девяносто процентов`() {
        assertEquals(0.9f, UiScaleMath.factor(UiScale.DEFAULT, 411, 420, stable), 0.0001f)
    }

    @Test
    fun `увеличенный размер экрана Android не уменьшаем`() {
        assertEquals(1f, UiScaleMath.factor(UiScale.DEFAULT, 360, 480, stable), 0.0001f)
    }

    @Test
    fun `планшет и раскладушка — свои раскладки, без уменьшения`() {
        assertEquals(1f, UiScaleMath.factor(UiScale.DEFAULT, 600, 420, stable), 0.0001f)
    }

    @Test
    fun `остальные значения — как выбраны`() {
        assertEquals(1.1f, UiScaleMath.factor(UiScale.S110, 360, 480, stable), 0.0001f)
        assertEquals(1.25f, UiScaleMath.factor(UiScale.S125, 700, 420, stable), 0.0001f)
        assertEquals(1f, UiScaleMath.factor(UiScale.S100, 411, 420, stable), 0.0001f)
    }

    @Test
    fun `авто — как раньше`() {
        // Узкий экран — уменьшение, но не ниже AUTO_MIN
        assertEquals(UiScaleMath.AUTO_MIN, UiScaleMath.factor(UiScale.AUTO, 360, 420, stable), 0.0001f)
        // Увеличенный размер экрана — без уменьшения
        assertEquals(1f, UiScaleMath.factor(UiScale.AUTO, 360, 480, stable), 0.0001f)
    }
}
