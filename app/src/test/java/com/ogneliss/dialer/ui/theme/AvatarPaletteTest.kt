package com.ogneliss.dialer.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тон собеседника: портрет и входящие сообщения (2.1.2–2.1.3, AvatarPalette). */
class AvatarPaletteTest {

    private val white = Color.White
    private val neutral = Color(0xFFE8EBF0)

    @Test
    fun `у одного имени тон один, регистр и пробелы не важны`() {
        assertEquals(AvatarPalette.tone("Иван Петров"), AvatarPalette.tone("  иван петров "))
    }

    @Test
    fun `входящие — в тоне собеседника, без имени — нейтральные`() {
        val tinted = AvatarPalette.bubble("Иван Петров", dark = false, highContrast = false, surface = white, neutral = neutral)
        assertNotEquals(neutral, tinted)
        assertNotEquals(white, tinted)
        assertEquals(neutral, AvatarPalette.bubble(null, false, false, white, neutral))
        assertEquals(neutral, AvatarPalette.bubble("Иван Петров", false, highContrast = true, surface = white, neutral = neutral))
    }

    @Test
    fun `тон близок к цвету своих сообщений — нейтральная заливка`() {
        val name = "Иван Петров"
        val tone = AvatarPalette.tone(name)
        // Свои сообщения того же оттенка — входящие серые, иначе стороны сольются
        assertEquals(neutral, AvatarPalette.bubble(name, false, false, white, neutral, own = tone))
        // Противоположный оттенок — тон остаётся
        val opposite = Color(1f - tone.red, 1f - tone.green, 1f - tone.blue)
        assertNotEquals(neutral, AvatarPalette.bubble(name, false, false, white, neutral, own = opposite))
    }

    @Test
    fun `расстояние между оттенками — по кругу, серый далёк от всех`() {
        assertTrue(AvatarPalette.hueDistance(Color.Red, Color(0xFFFF2000)) < 10f)
        assertEquals(180f, AvatarPalette.hueDistance(Color.Red, Color.Cyan), 1f)
        assertEquals(180f, AvatarPalette.hueDistance(Color.Gray, Color.Red), 0.01f)
        // через ноль: 350° и 10° — рядом
        assertTrue(AvatarPalette.hueDistance(Color(0xFFFF002B), Color(0xFFFF2B00)) < 25f)
    }
}
