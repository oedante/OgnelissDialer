package com.ogneliss.dialer.widget

import android.content.Context
import android.graphics.Color
import com.ogneliss.dialer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Значок виджета раздела (2.1.0, WidgetIcon): картинка 64 dp, значок из
 * адаптивного ресурса (слои — PNG make_section_icons.py), счётчик — красный
 * в правом верхнем углу и только когда есть непрочитанные.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class WidgetIconTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun red(pixel: Int) = Color.red(pixel) > 180 && Color.green(pixel) < 110 && Color.blue(pixel) < 110

    @Test
    fun `значок SMS со счётчиком и без`() {
        for (unread in listOf(0, 3, 27, 120)) {
            val b = WidgetIcon.draw(context, R.drawable.ic_shortcut_sms, unread)
            assertEquals(192, b.width)                 // 64 dp на xxhdpi
            assertEquals(192, b.height)
            assertEquals(255, Color.alpha(b.getPixel(b.width / 2, b.height / 2)))
            val corner = b.getPixel(b.width - 18, 18)
            assertEquals("счётчик при $unread", unread > 0, red(corner))
        }
    }

    @Test
    fun `значок контактов — без счётчика`() {
        val b = WidgetIcon.draw(context, R.drawable.ic_shortcut_contacts, 0)
        assertEquals(255, Color.alpha(b.getPixel(b.width / 2, b.height / 2)))
        assertTrue(!red(b.getPixel(b.width - 18, 18)))
    }

    // ── 2.1.3: форма из настроек (WidgetShape) ─────────────────────────────

    @Test
    fun `форма из настроек — круг, скругление, квадрат`() {
        val res = R.drawable.ic_shortcut_contacts
        fun cornerAlpha(shape: com.ogneliss.dialer.settings.WidgetShape, at: Int) =
            Color.alpha(WidgetIcon.draw(context, res, 0, shape).getPixel(at, at))
        // Квадрат закрашен до самого угла, круг — нет
        assertEquals(255, cornerAlpha(com.ogneliss.dialer.settings.WidgetShape.SQUARE, 2))
        assertEquals(0, cornerAlpha(com.ogneliss.dialer.settings.WidgetShape.CIRCLE, 2))
        // Точка (20, 20) из 192: у круга снаружи, у слабого скругления — внутри
        assertEquals(0, cornerAlpha(com.ogneliss.dialer.settings.WidgetShape.CIRCLE, 20))
        assertEquals(255, cornerAlpha(com.ogneliss.dialer.settings.WidgetShape.ROUND_SMALL, 20))
        // Сильное скругление срезает больше слабого
        assertEquals(0, cornerAlpha(com.ogneliss.dialer.settings.WidgetShape.ROUND_LARGE, 8))
    }
}
