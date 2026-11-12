package com.ogneliss.dialer.ui.theme

import com.ogneliss.dialer.settings.FontScale
import com.ogneliss.dialer.settings.UiScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Масштаб интерфейса и размер шрифта (2.2.1) — те же правила, что
 * в Ogneliss Mail (`UiZoom.auto`, `main.dart`): числа в этих проверках
 * должны совпадать с тем, что почта даёт на том же экране.
 */
class UiScaleMathTest {

    @Test
    fun `авто — экран от 412 dp без изменений`() {
        assertEquals(1f, UiScaleMath.factor(UiScale.AUTO, 412), 0.0001f)
        assertEquals(1f, UiScaleMath.factor(UiScale.AUTO, 515), 0.0001f)
        // 400 / 412 = 0,971 — разница незаметна, не трогаем
        assertEquals(1f, UiScaleMath.factor(UiScale.AUTO, 400), 0.0001f)
    }

    @Test
    fun `авто — узкий экран уменьшается с шагом в процент`() {
        // 393 / 412 = 0,9539 → 95 %
        assertEquals(0.95f, UiScaleMath.factor(UiScale.AUTO, 393), 0.0001f)
        // 360 / 412 = 0,8738 → 87 %
        assertEquals(0.87f, UiScaleMath.factor(UiScale.AUTO, 360), 0.0001f)
    }

    @Test
    fun `авто — не меньше 85 процентов`() {
        assertEquals(UiScaleMath.AUTO_MIN, UiScaleMath.factor(UiScale.AUTO, 320), 0.0001f)
    }

    @Test
    fun `авто — крупный шрифт учитывается наполовину`() {
        // Шрифт ×1,3 → t = 1,15; 412 / (412 × 1,15) = 0,8696 → 86 %
        assertEquals(0.86f, UiScaleMath.factor(UiScale.AUTO, 412, 1.3f), 0.0001f)
        // Мелкий шрифт — целиком: 380 / (412 × 0,85) = 1,085 → без изменений
        assertEquals(1f, UiScaleMath.factor(UiScale.AUTO, 380, 0.85f), 0.0001f)
    }

    @Test
    fun `планшет и раскладушка — свои раскладки, без уменьшения`() {
        assertEquals(1f, UiScaleMath.factor(UiScale.AUTO, 600, 1.6f), 0.0001f)
    }

    @Test
    fun `вручную — как выбрано, на любом экране`() {
        assertEquals(0.85f, UiScaleMath.factor(UiScale.S85, 700), 0.0001f)
        assertEquals(0.95f, UiScaleMath.factor(UiScale.S95, 360), 0.0001f)
        assertEquals(1.1f, UiScaleMath.factor(UiScale.S110, 360, 1.6f), 0.0001f)
    }

    @Test
    fun `по умолчанию авто, прежние значения читаются`() {
        assertEquals(UiScale.AUTO, UiScale.from(null))
        assertEquals(UiScale.S90, UiScale.from("S90"))
        // 125 % нет в почте — ближайшее
        assertEquals(UiScale.S110, UiScale.from("S125"))
    }

    @Test
    fun `шрифт — настройка поверх системного, итог в пределах`() {
        assertEquals(1.3f, UiScaleMath.textScale(1.3f, 1f), 0.0001f)
        assertEquals(1.56f, UiScaleMath.textScale(1.3f, 1.2f), 0.0001f)
        assertEquals(UiScaleMath.TEXT_MAX, UiScaleMath.textScale(2f, 1.6f), 0.0001f)
        assertEquals(UiScaleMath.TEXT_MIN, UiScaleMath.textScale(0.85f, 0.8f), 0.0001f)
    }

    @Test
    fun `размер шрифта — шаг десять процентов в пределах`() {
        assertEquals(1.2f, FontScale.clamp(1.23f), 0.0001f)
        assertEquals(FontScale.MIN, FontScale.clamp(0.1f), 0.0001f)
        assertEquals(FontScale.MAX, FontScale.clamp(3f), 0.0001f)
    }

    // ── Окно постоянной ширины (календарь 360 dp, 2.4.0) ─────────────────────

    @Test
    fun `окно календаря — помещается в масштабе, плотность та же`() {
        val base = Density(2.75f, 1f)
        val scaled = UiScaleMath.scaledDensity(base, 1.1f)
        // 360 × 1,1 = 396 ≤ 412 — без уменьшения
        assertSame(scaled, UiScaleMath.fitted(scaled, base.density, 360.dp, 412))
    }

    @Test
    fun `окно календаря — на узком экране уменьшается до ширины экрана`() {
        val base = Density(3f, 1f)
        val scaled = UiScaleMath.scaledDensity(base, 1.1f)
        // 360 × 1,1 = 396 > 360 — окно уменьшается ровно до экрана
        val fitted = UiScaleMath.fitted(scaled, base.density, 360.dp, 360)
        assertEquals(base.density, fitted.density, 0.0001f)
        // Текст — в той же пропорции, что и размеры
        val ratio = with(fitted) { 16.sp.toPx() } / with(scaled) { 16.sp.toPx() }
        assertEquals(fitted.density / scaled.density, ratio, 0.0001f)
    }

    @Test
    fun `окно календаря — не меньше системного размера`() {
        val base = Density(3f, 1f)
        // Масштаб 85 %: окно и так меньше системного — не трогаем
        val scaled = UiScaleMath.scaledDensity(base, 0.85f)
        assertSame(scaled, UiScaleMath.fitted(scaled, base.density, 360.dp, 320))
    }
}
