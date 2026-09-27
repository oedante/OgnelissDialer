package com.ogneliss.dialer.ui.theme

import com.ogneliss.dialer.ui.theme.AdaptiveLayout.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ВЫБОРА РАСКЛАДКИ.
 *
 * Раскладка зависит и от ширины, и от высоты, а не только от признака
 * «телефон или планшет». Ошибка здесь означает нечитаемый экран
 * на конкретном устройстве, причём заметят её пользователи,
 * а не разработчик со своим телефоном в портрете.
 *
 * Размеры взяты у реальных устройств.
 */
class AdaptiveLayoutTest {

    // ── Телефон ────────────────────────────────────────────────────────────

    @Test
    fun `телефон в портрете складывает панели вертикально`() {
        // Pixel 7: 412 × 915
        assertEquals(Mode.STACKED, AdaptiveLayout.modeFor(412, 915))
    }

    @Test
    fun `телефон в ландшафте раскладывает панели в два столбца`() {
        // Тот же Pixel 7 повёрнутый: 915 × 412.
        // Вертикально клавиатура и список не уживаются
        assertEquals(Mode.SIDE_BY_SIDE, AdaptiveLayout.modeFor(915, 412))
    }

    @Test
    fun `невысокий экран всегда даёт две панели`() {
        // Старый компактный телефон в ландшафте
        assertEquals(Mode.SIDE_BY_SIDE, AdaptiveLayout.modeFor(568, 320))
    }

    // ── Планшет ────────────────────────────────────────────────────────────

    @Test
    fun `планшет в портрете показывает журнал и клавиатуру рядом`() {
        // Pixel Tablet в портрете: 800 × 1280
        assertEquals(Mode.TABLET_DIALPAD, AdaptiveLayout.modeFor(800, 1280))
    }

    @Test
    fun `широкий экран без выбранного абонента показывает клавиатуру`() {
        assertEquals(
            Mode.TABLET_DIALPAD,
            AdaptiveLayout.modeFor(1280, 800, hasSelection = false)
        )
    }

    @Test
    fun `широкий экран с выбранным абонентом показывает две панели`() {
        assertEquals(
            Mode.TWO_PANE,
            AdaptiveLayout.modeFor(1280, 800, hasSelection = true)
        )
    }

    // ── Раскладушка ────────────────────────────────────────────────────────

    @Test
    fun `раскладушка в сложенном виде ведёт себя как телефон`() {
        // Pixel Fold закрытый: 320 × 840 — узкий и высокий
        assertEquals(Mode.STACKED, AdaptiveLayout.modeFor(320, 840))
    }

    @Test
    fun `раскладушка в раскрытом виде переходит на планшетную раскладку`() {
        // Pixel Fold открытый: 840 × 900, почти квадратный
        assertEquals(Mode.TABLET_DIALPAD, AdaptiveLayout.modeFor(840, 900))
    }

    // ── Ширина клавиатуры ──────────────────────────────────────────────────

    @Test
    fun `на телефоне клавиатура занимает всю ширину`() {
        assertEquals(412, AdaptiveLayout.dialpadMaxWidthDp(412))
    }

    @Test
    fun `на планшете клавиатура ограничена по ширине`() {
        // Иначе клавиши по 200 dp: до дальних не дотянуться
        val width = AdaptiveLayout.dialpadMaxWidthDp(1280)
        assertTrue("Клавиатура должна быть ограничена", width < 1280)
        assertEquals(400, width)
    }

    @Test
    fun `на широком экране клавиатура прижимается к краю`() {
        // Планшет держат за край, центр экрана недосягаем одной рукой
        assertTrue(AdaptiveLayout.shouldAlignDialpadToEdge(800))
        assertFalse(AdaptiveLayout.shouldAlignDialpadToEdge(412))
    }

    // ── Признак двух панелей ───────────────────────────────────────────────

    @Test
    fun `только вертикальная раскладка не считается двухпанельной`() {
        assertFalse(AdaptiveLayout.showsBothPanes(Mode.STACKED))
        assertTrue(AdaptiveLayout.showsBothPanes(Mode.SIDE_BY_SIDE))
        assertTrue(AdaptiveLayout.showsBothPanes(Mode.TABLET_DIALPAD))
        assertTrue(AdaptiveLayout.showsBothPanes(Mode.TWO_PANE))
    }

    // ── Оценка числа видимых строк ─────────────────────────────────────────

    @Test
    fun `в вертикальной раскладке помещается меньше строк`() {
        // Половину экрана занимает клавиатура
        val stacked = AdaptiveLayout.estimatedVisibleRows(Mode.STACKED, 915)
        val side = AdaptiveLayout.estimatedVisibleRows(Mode.SIDE_BY_SIDE, 915)
        assertTrue("В две панели должно помещаться больше", side > stacked)
    }

    @Test
    fun `на крошечном экране число строк не уходит в минус`() {
        assertTrue(AdaptiveLayout.estimatedVisibleRows(Mode.SIDE_BY_SIDE, 40) >= 0)
    }
}
