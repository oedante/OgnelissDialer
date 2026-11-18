package com.ogneliss.dialer.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Выбор слоя фона экрана вызова (2.7.4): все сочетания входов. */
class CallBackgroundLayerTest {

    private val bools = listOf(false, true)

    private fun expected(photoBackground: Boolean, photoTop: Boolean, custom: Boolean, ambient: Boolean) = when {
        photoBackground -> CallBackgroundLayer.PHOTO
        custom -> CallBackgroundLayer.CUSTOM
        photoTop -> CallBackgroundLayer.PLAIN
        ambient -> CallBackgroundLayer.AMBIENT
        else -> CallBackgroundLayer.PLAIN
    }

    @Test
    fun `все шестнадцать сочетаний`() {
        for (pb in bools) for (pt in bools) for (c in bools) for (a in bools) {
            assertEquals(
                "pb=$pb pt=$pt custom=$c ambient=$a",
                expected(pb, pt, c, a),
                CallBackgroundLayer.decide(pb, pt, c, a)
            )
        }
    }

    @Test
    fun `фото контакта на весь экран сильнее своего снимка`() {
        assertEquals(CallBackgroundLayer.PHOTO, CallBackgroundLayer.decide(true, false, true, true))
    }

    @Test
    fun `свой снимок вместо живого фона и ровной заливки`() {
        assertEquals(CallBackgroundLayer.CUSTOM, CallBackgroundLayer.decide(false, false, true, true))
        assertEquals(CallBackgroundLayer.CUSTOM, CallBackgroundLayer.decide(false, false, true, false))
    }

    @Test
    fun `свой снимок есть и под фото по ширине`() {
        assertEquals(CallBackgroundLayer.CUSTOM, CallBackgroundLayer.decide(false, true, true, true))
    }

    @Test
    fun `снимка нет — прежние слои`() {
        assertEquals(CallBackgroundLayer.AMBIENT, CallBackgroundLayer.decide(false, false, false, true))
        assertEquals(CallBackgroundLayer.PLAIN, CallBackgroundLayer.decide(false, false, false, false))
        assertEquals(CallBackgroundLayer.PLAIN, CallBackgroundLayer.decide(false, true, false, true))
    }

    // ── 2.7.6: цвет текста над своим снимком ──────────────────────────────

    private val darkPage = CallBackgroundLayer.luminance(0xFF121316.toInt())
    private val lightPage = CallBackgroundLayer.luminance(0xFFFFFFFF.toInt())

    @Test
    fun `яркость белого — 1, чёрного — 0`() {
        assertEquals(1.0, CallBackgroundLayer.luminance(0xFFFFFFFF.toInt()), 1e-9)
        assertEquals(0.0, CallBackgroundLayer.luminance(0xFF000000.toInt()), 1e-9)
    }

    @Test
    fun `красный снимок с форума в тёмной теме — белый текст`() {
        // Тёмно-красные волны (#8B0A0A) — как на снимке Saxena1
        val red = CallBackgroundLayer.luminance(0xFF8B0A0A.toInt())
        assertFalse(CallBackgroundLayer.darkTextOver(red, darkPage))
    }

    @Test
    fun `светлый снимок в светлой теме — тёмный текст`() {
        val sky = CallBackgroundLayer.luminance(0xFFDDEEFF.toInt())
        assertTrue(CallBackgroundLayer.darkTextOver(sky, lightPage))
    }

    @Test
    fun `в светлой теме подложка светлая — текст тёмный и на тёмном снимке`() {
        // Под текстом 40 % белой подложки: яркость не ниже 0,4 — чёрный контрастнее
        val night = CallBackgroundLayer.luminance(0xFF101828.toInt())
        assertTrue(CallBackgroundLayer.darkTextOver(night, lightPage))
    }

    @Test
    fun `тёмная тема и средний снимок — белый текст`() {
        val mid = CallBackgroundLayer.luminance(0xFF6A7A8A.toInt())
        assertFalse(CallBackgroundLayer.darkTextOver(mid, darkPage))
    }

    @Test
    fun `белый снимок в тёмной теме — тёмный текст`() {
        assertTrue(CallBackgroundLayer.darkTextOver(1.0, darkPage))
    }

    @Test
    fun `полоса берёт середину снимка, а не верх и низ`() {
        // 10×10: верхние 15 % и нижние 40 % белые, середина чёрная
        val w = 10
        val h = 10
        val px = IntArray(w * h) { i ->
            val y = i / w
            if (y < 1 || y >= 6) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        assertEquals(0.0, CallBackgroundLayer.bandLuminance(px, w, h), 1e-9)
    }

    @Test
    fun `пустой снимок — яркость 0`() {
        assertEquals(0.0, CallBackgroundLayer.bandLuminance(IntArray(0), 0, 0), 1e-9)
    }

    @Test
    fun `светлый низ снимка в тёмной теме — подписи кнопок тёмные`() {
        // Почти белый низ (#EEEEF2) под тёмной подложкой 0,62 — чёрный контрастнее
        val light = CallBackgroundLayer.luminance(0xFFEEEEF2.toInt())
        assertTrue(CallBackgroundLayer.darkTextOver(light, darkPage, CallBackgroundLayer.CONTROLS_BAND_SCRIM))
    }

    @Test
    fun `тёмный низ снимка в тёмной теме — подписи кнопок белые`() {
        val night = CallBackgroundLayer.luminance(0xFF141821.toInt())
        assertFalse(CallBackgroundLayer.darkTextOver(night, darkPage, CallBackgroundLayer.CONTROLS_BAND_SCRIM))
    }
}
