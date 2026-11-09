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
}
