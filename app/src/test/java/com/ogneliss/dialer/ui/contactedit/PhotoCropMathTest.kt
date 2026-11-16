package com.ogneliss.dialer.ui.contactedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Окно обрезки фото контакта (2.7.3): падение «Cannot coerce value to an empty
 * range: maximum 527.275 is less than minimum 527.2751» на горизонтальном снимке.
 */
class PhotoCropMathTest {

    @Test
    fun `снимок на долю пикселя короче рамки — не падает, встаёт по центру`() {
        // Числа из журнала пользователя: граница рамки 527.275, снимок короче на 0.0001
        val side = 1000f
        val y = clampOffset(500f, frameStart = 527.275f, side = side, imageSize = side - 0.0001f)
        assertEquals(527.275f, y, 0.001f)
    }

    @Test
    fun `та же ось с настоящим расчётом масштаба не падает`() {
        // Горизонтальный снимок: масштаб по высоте, высота × масштаб ≈ сторона рамки
        val width = 4000f
        val height = 3000f
        val side = 1032f
        val scale = maxOf(side / width, side / height)
        val top0 = 527.275f
        val y = clampOffset(top0 - 3f, frameStart = top0, side = side, imageSize = height * scale)
        assertTrue(y <= top0 + 0.01f && y >= top0 - 0.01f)
    }

    @Test
    fun `снимок длиннее рамки — сдвиг ограничен краями`() {
        // Снимок 2000 при рамке 1000 от 100: допустимо [-900; 100]
        assertEquals(100f, clampOffset(300f, 100f, 1000f, 2000f), 0f)
        assertEquals(-900f, clampOffset(-1500f, 100f, 1000f, 2000f), 0f)
        assertEquals(-400f, clampOffset(-400f, 100f, 1000f, 2000f), 0f)
    }
}
