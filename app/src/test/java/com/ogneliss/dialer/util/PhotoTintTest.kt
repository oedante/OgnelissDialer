package com.ogneliss.dialer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Выбор преобладающего цвета фото (1.3.5) — чистая часть PhotoTint. */
class PhotoTintTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `самый частый цветной цвет побеждает`() {
        val red = rgb(200, 60, 40)
        val blue = rgb(40, 70, 200)
        val pixels = IntArray(70) { red } + IntArray(30) { blue }
        assertEquals(red, PhotoTint.dominantOf(pixels))
    }

    @Test
    fun `серый, белый и чёрный фон не побеждают`() {
        val skin = rgb(220, 150, 120)
        val pixels = IntArray(500) { rgb(128, 128, 128) } + IntArray(300) { rgb(250, 250, 250) } +
            IntArray(300) { rgb(10, 10, 10) } + IntArray(50) { skin }
        assertEquals(skin, PhotoTint.dominantOf(pixels))
    }

    @Test
    fun `чёрно-белое фото — без заливки`() {
        val pixels = IntArray(100) { i -> rgb(i * 2, i * 2, i * 2) }
        assertNull(PhotoTint.dominantOf(pixels))
    }
}
