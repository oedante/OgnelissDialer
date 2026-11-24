package com.ogneliss.dialer.call

import androidx.test.core.app.ApplicationProvider
import com.ogneliss.dialer.settings.CallPhotoStyle
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Вид экрана вызова до первого кадра (2.9.7, письмо пользователя: «экран
 * открывается с мельканием классической темы»). Разбор — CallScreenStart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallScreenStartTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = Prefs(context)

    @Test
    fun `сохранённые значения приходят к первому кадру`() {
        runBlocking {
            prefs.setTheme(ThemeMode.DARK)
            prefs.setCallAmbientStyle(AmbientStyle.FLAME)
            prefs.setCallStatusLabels(false)
        }
        val start = CallScreenStart.read(context, prefs, incoming = null)
        assertEquals(ThemeMode.DARK, start.theme)
        assertEquals(AmbientStyle.FLAME, start.ambientStyle)
        assertFalse(start.statusLabels)
    }

    @Test
    fun `фото звонящего прочитано - не null, как до чтения`() {
        val start = CallScreenStart.read(context, prefs, incoming = null)
        assertNotNull(start.callPhotoStyle)
        assertEquals(runBlocking { prefs.callPhotoStyle.first() }, start.callPhotoStyle)
    }

    @Test
    fun `без своего снимка заготовки фона нет`() {
        val start = CallScreenStart.read(context, prefs, incoming = true)
        assertNull(start.background)
    }

    @Test
    fun `значения по умолчанию - те же, что были у потоков`() {
        val defaults = CallScreenStart()
        assertEquals(ThemeMode.SYSTEM, defaults.theme)
        assertEquals(AmbientStyle.THEME, defaults.ambientStyle)
        assertEquals(null as CallPhotoStyle?, defaults.callPhotoStyle)
        assertEquals(true, defaults.statusLabels)
    }
}
