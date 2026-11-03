package com.ogneliss.dialer.ui.theme

import com.ogneliss.dialer.ui.theme.ThemePresetMath as M
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Цветовые темы как в Ogneliss Mail (1.8.4): разбор в ThemePresetMath.
 * Контраст — как test/unit/theme_presets_test.dart почты: все пары
 * «текст — фон» проходят AA (4,5 : 1).
 */
class ThemePresetMathTest {

    private fun c(hex: Long) = hex.toInt()

    // «Классика» диалера: светлая и тёмная (ui/theme/Color.kt)
    private val lightBacks = listOf(c(0xFFE8EBF0), c(0xFFFFFFFF), c(0xFFF2F5FA)) // Control, Canvas, Page
    private val lightText = c(0xFF22252B)
    private val lightTextVariant = c(0xFF5B636E)
    private val darkBacks = listOf(c(0xFF33363C), c(0xFF121316), c(0xFF1C1E22))  // Highest, Page, Card
    private val darkText = c(0xFFE2E4E9)
    private val darkTextVariant = c(0xFFC3C7CF)

    @Test
    fun `HSL туда и обратно — тот же цвет`() {
        listOf(c(0xFF2F5DA3), c(0xFFF2F5FA), c(0xFF22252B), c(0xFF146C2E), c(0xFFB4501E), c(0xFF000000), c(0xFFFFFFFF))
            .forEach { argb ->
                val h = M.toHsl(argb)
                assertEquals(Integer.toHexString(argb), argb, M.fromHsl(h.h, h.s, h.l))
            }
    }

    @Test
    fun `графит — чистый серый, акцент как в почте`() {
        val g = M.SPECS.getValue("GRAPHITE")
        val page = M.neutral(c(0xFFF2F5FA), g)
        val h = M.toHsl(page)
        assertEquals(0.0, h.s, 1e-9)
        // Подписи разделов почты на «Графите» — #4F5866 (снимок автора: #505761 после JPEG)
        assertEquals(c(0xFF4F5866), M.accentLight(g.accent, M.neutral(lightBacks[0], g)))
    }

    @Test
    fun `темы — те же, что в почте, в том же порядке`() {
        assertEquals(
            listOf("CLASSIC", "SKY", "OCEAN", "FOREST", "SUNSET", "GRAPHITE", "MIDNIGHT"),
            M.SPECS.keys.toList()
        )
        assertTrue(M.SPECS.getValue("MIDNIGHT").blackDark)
    }

    @Test
    fun `светлые темы — текст и акцент проходят AA`() {
        M.SPECS.forEach { (name, spec) ->
            val backs = lightBacks.map { M.neutral(it, spec) }
            val primary = M.accentLight(spec.accent, backs[0])
            assertTrue("$name: белый на акценте", M.contrast(c(0xFFFFFFFF), primary) >= 4.5)
            backs.forEach { bg ->
                assertTrue("$name: акцент на фоне", M.contrast(primary, bg) >= 4.5)
                assertTrue("$name: текст", M.contrast(M.text(M.neutral(lightText, spec), backs, false), bg) >= 4.5)
                assertTrue("$name: подписи", M.contrast(M.text(M.neutral(lightTextVariant, spec), backs, false), bg) >= 4.5)
            }
        }
    }

    @Test
    fun `тёмные темы — текст и акцент проходят AA`() {
        M.SPECS.forEach { (name, spec) ->
            val backs = darkBacks.map { M.neutral(it, spec) }
            val primary = M.accentDark(spec.accent, backs[0])
            backs.forEach { bg ->
                assertTrue("$name: акцент на фоне", M.contrast(primary, bg) >= 4.5)
                assertTrue("$name: текст", M.contrast(M.text(M.neutral(darkText, spec), backs, true), bg) >= 4.5)
                assertTrue("$name: подписи", M.contrast(M.text(M.neutral(darkTextVariant, spec), backs, true), bg) >= 4.5)
            }
            // Текст на акцентной заливке (кнопки) — тёмный тон акцента
            val onPrimary = M.accentTone(spec, 0.72, 0.14)
            assertTrue("$name: текст на акценте", M.contrast(onPrimary, primary) >= 4.5)
        }
    }

    @Test
    fun `контраст — как WCAG`() {
        assertEquals(21.0, M.contrast(c(0xFF000000), c(0xFFFFFFFF)), 0.01)
        assertEquals(1.0, M.contrast(c(0xFF777777), c(0xFF777777)), 1e-9)
    }
}
