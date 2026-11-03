package com.ogneliss.dialer.ui.theme

import com.ogneliss.dialer.ui.theme.PhotoPalette as P
import com.ogneliss.dialer.ui.theme.ThemePresetMath as M
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Цвета снимка (1.8.4): подложка имени на обложке карточки и тоны живого
 * фона экрана вызова. Разбор — PhotoPalette.
 */
class PhotoPaletteTest {

    private fun c(hex: Long) = hex.toInt()

    private val lightTexts = intArrayOf(c(0xFF22252B), c(0xFF5B636E))
    private val darkTexts = intArrayOf(c(0xFFE2E4E9), c(0xFFC3C7CF))

    /** Снимок 40 × 40 из полос: [colors] с долями [shares]. */
    private fun stripes(colors: List<Int>, shares: List<Double>): IntArray {
        val px = IntArray(1600)
        var at = 0
        colors.zip(shares).forEach { (color, share) ->
            val n = (1600 * share).toInt()
            for (i in at until (at + n).coerceAtMost(1600)) px[i] = color
            at += n
        }
        for (i in at until 1600) px[i] = colors.last()
        return px
    }

    @Test
    fun `главный цвет — заметный, а не серый фон`() {
        // Серое небо на 60 % снимка и рыжая кошка на 40 %
        val px = stripes(listOf(c(0xFF8A8C90), c(0xFFC0702A)), listOf(0.6, 0.4))
        val first = P.swatches(px).first()
        assertTrue("тон ${first.hue}", first.hue in 20.0..35.0)
    }

    @Test
    fun `серый снимок — серый фон, без выдуманного цвета`() {
        val px = stripes(listOf(c(0xFF303030), c(0xFF909090), c(0xFFD0D0D0)), listOf(0.3, 0.4, 0.3))
        listOf(false, true).forEach { dark ->
            val texts = if (dark) darkTexts else lightTexts
            P.ambientTones(P.swatches(px), dark, texts).forEach { tone ->
                assertEquals(0.0, M.toHsl(tone).s, 0.02)
            }
        }
        assertEquals(0.0, M.toHsl(P.scrimTone(px, 40, 0.4f, 0.75f)).s, 0.02)
    }

    @Test
    fun `подложка имени — тёмная и своего тона, белое имя читается`() {
        val px = stripes(listOf(c(0xFF1E3A6E), c(0xFF2A4F8F)), listOf(0.5, 0.5))
        val tone = P.scrimTone(px, 40, 0.4f, 0.75f)
        val h = M.toHsl(tone)
        assertEquals(P.SCRIM_LIGHT, h.l, 0.01)
        assertTrue("тон ${h.h}", h.h in 205.0..230.0)
        assertTrue(M.contrast(c(0xFFFFFFFF), tone) >= 7.0)
    }

    @Test
    fun `тоны фона — пастель в светлой, глубокие в тёмной`() {
        val sw = P.themeSwatches(c(0xFF2F5DA3))
        P.ambientTones(sw, false, lightTexts).forEach { assertTrue(M.toHsl(it).l >= 0.835) }
        P.ambientTones(sw, true, darkTexts).forEach { assertTrue(M.toHsl(it).l <= 0.305) }
    }

    @Test
    fun `текст экрана вызова на любом тоне проходит AA`() {
        // Все тоны круга, в том числе жёлтый и зелёный: они ярче синего
        // при той же «светлоте», и серые подписи на них не читались бы
        for (hue in 0 until 360 step 15) {
            val sw = listOf(P.Swatch(hue.toDouble(), 0.9, 0.5, 1.0))
            listOf(false, true).forEach { dark ->
                val texts = if (dark) darkTexts else lightTexts
                val tones = P.ambientTones(sw, dark, texts)
                val moods = listOf(
                    tones,
                    P.moodTones(tones, active = true, holding = false, fromPhoto = false, greenHue = 135.0, dark = dark, texts = texts),
                    P.moodTones(tones, active = true, holding = false, fromPhoto = true, greenHue = 135.0, dark = dark, texts = texts),
                    P.moodTones(tones, active = false, holding = true, fromPhoto = false, greenHue = 135.0, dark = dark, texts = texts)
                )
                val halos = moods.map { P.haloTone(it[0], dark, texts) }
                (moods.flatMap { it.toList() } + halos).forEach { tone ->
                    texts.forEach { text ->
                        assertTrue("тон $hue, тёмная $dark", M.contrast(text, tone) >= 4.5)
                    }
                }
            }
        }
    }

    @Test
    fun `разговор без фото — к зелёному по короткой дуге`() {
        // Красный (350°) к зелёному (135°): короткий путь — через жёлтый,
        // а не через синий и фиолетовый
        val red = M.hsl(350.0, 0.6, 0.86)
        val moved = P.moodTones(intArrayOf(red), true, false, false, 135.0, false, lightTexts)[0]
        val h = M.toHsl(moved).h
        assertTrue("тон $h", h in 40.0..80.0)
    }

    @Test
    fun `пустой и прозрачный снимок — без падения`() {
        assertTrue(P.swatches(IntArray(0)).isEmpty())
        assertTrue(P.swatches(IntArray(100)).isEmpty())
        assertEquals(3, P.ambientTones(emptyList(), false, lightTexts).size)
        assertEquals(0.0, M.toHsl(P.scrimTone(IntArray(0), 0, 0f, 1f)).s, 1e-9)
    }
}
