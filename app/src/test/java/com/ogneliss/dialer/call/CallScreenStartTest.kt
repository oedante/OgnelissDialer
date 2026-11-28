package com.ogneliss.dialer.call

import com.ogneliss.dialer.settings.SwitchSetting
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
            prefs.set(SwitchSetting.CALL_STATUS_LABELS, false)
        }
        val start = CallScreenStart.read(context, prefs, incoming = null)
        assertEquals(ThemeMode.DARK, start.theme)
        assertEquals(AmbientStyle.FLAME, start.ambientStyle)
        assertFalse(start.statusLabels)
    }

    /*
     * 2.9.11 (Windows, автор: тест падал на assertNotNull). Было: read() — первое
     * обращение к настройкам в тесте. Robolectric создаёт контекст заново в каждом
     * тесте, а с ним и DataStore: чтение шло с диска «холодным», и на Windows не
     * укладывалось в бюджет read() (PREFS_BUDGET_MS, 250 мс) — приходили значения по
     * умолчанию, у фото это null. Так read() и задуман (главный поток ждать дольше
     * не может), а тест проверяет обычный случай: настройки уже в памяти. Поэтому
     * ожидаемое значение читаем первым — оно же и прогревает DataStore.
     */
    @Test
    fun `фото звонящего прочитано - не null, как до чтения`() {
        val expected = runBlocking { prefs.callPhotoStyle.first() }
        val start = CallScreenStart.read(context, prefs, incoming = null)
        assertNotNull(start.callPhotoStyle)
        assertEquals(expected, start.callPhotoStyle)
    }

    @Test
    fun `без своего снимка заготовки фона нет`() {
        // Настройки — в память до read(), как в тесте выше: проверяем снимок, а не бюджет
        runBlocking { prefs.callPhotoStyle.first() }
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
