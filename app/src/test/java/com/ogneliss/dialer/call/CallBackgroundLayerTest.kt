package com.ogneliss.dialer.call

import org.junit.Assert.assertEquals
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
}
