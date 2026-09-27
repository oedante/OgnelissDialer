package com.ogneliss.dialer.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Пороги раскладки на размерах настоящих устройств.
 *
 * Размеры — окна приложения в dp, как их отдаёт система: без строки
 * состояния и навигации, поэтому чуть меньше паспортных.
 */
class WindowLayoutTest {

    private fun mode(w: Int, h: Int) = WindowLayout.classify(w, h)

    @Test
    fun `телефон в портрете`() {
        assertEquals(LayoutMode.PHONE, mode(393, 808))   // Pixel 8
        assertEquals(LayoutMode.PHONE, mode(360, 740))   // небольшой телефон
    }

    @Test
    fun `телефон лёжа — по высоте, хотя по ширине он раскладушка`() {
        // Ширина 808 dp попала бы в FOLDABLE, но места по вертикали
        // у телефона лёжа меньше всех: поэтому высота проверяется первой
        assertEquals(LayoutMode.PHONE_LANDSCAPE, mode(808, 393))
        assertEquals(LayoutMode.PHONE_LANDSCAPE, mode(740, 360))
    }

    @Test
    fun `раскрытая раскладушка в портрете — не телефон`() {
        // Ради этого случая всё и затевалось: ориентация «портретная»,
        // а места вдвое больше, чем на телефоне
        assertEquals(LayoutMode.FOLDABLE, mode(673, 841))   // Galaxy Z Fold
        assertEquals(LayoutMode.FOLDABLE, mode(618, 841))   // Pixel Fold
    }

    @Test
    fun `планшет и раскладушка в альбомном`() {
        assertEquals(LayoutMode.TABLET, mode(1280, 800))  // планшет 10"
        assertEquals(LayoutMode.TABLET, mode(841, 673))   // раскладушка лёжа
    }

    @Test
    fun `планшет в портрете — раскладушка по ширине`() {
        assertEquals(LayoutMode.FOLDABLE, mode(800, 1280))
    }

    @Test
    fun `границы классов`() {
        assertEquals(LayoutMode.PHONE, mode(599, 900))
        assertEquals(LayoutMode.FOLDABLE, mode(600, 900))
        assertEquals(LayoutMode.FOLDABLE, mode(839, 900))
        assertEquals(LayoutMode.TABLET, mode(840, 900))
        assertEquals(LayoutMode.PHONE_LANDSCAPE, mode(900, 479))
        assertEquals(LayoutMode.TABLET, mode(900, 480))
    }
}
