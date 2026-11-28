package com.ogneliss.dialer.settings

import android.app.Application
import com.ogneliss.dialer.ui.SettingsState
import com.ogneliss.dialer.ui.settings.SettingKind
import com.ogneliss.dialer.ui.settings.SettingsRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * НАСТРОЙКИ С ВЫБОРОМ — ОДНО ОПИСАНИЕ (2.9.15, техдолг, этап 2б).
 *
 * Выбор из шторки проходит путь: имя варианта → разбор → запись в Prefs →
 * хранение → снимок SettingsState → «выбрано сейчас» в шторке. До 2.9.15 каждое
 * звено было своей веткой `when`, и ветки могли разойтись. Здесь — каждый вариант
 * каждого описания по кругу, и сверка описаний с реестром.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChoiceSettingTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs = Prefs(app)

    @After
    fun tearDown() = runBlocking { prefs.resetAll() }

    @Test
    fun `каждый вариант записывается и читается обратно`() = runBlocking {
        val source = SettingsStateSource(prefs)
        Choices.ALL.forEach { c ->
            c.optionNames.forEach { name ->
                c.set(prefs, app, name)
                val state = source.state.first()
                assertEquals("${c.key}: записали $name", name, c.currentName(state))
            }
        }
    }

    @Test
    fun `у каждого описания — пункт на экране с тем же заголовком`() {
        val entries = SettingsRegistry.entries.filter { it.kind is SettingKind.Choice }
        Choices.ALL.forEach { c ->
            val shown = entries.filter { (it.kind as SettingKind.Choice).key == c.key }
            assertEquals("${c.key}: пунктов в реестре", 1, shown.size)
            assertEquals("${c.key}: заголовок шторки и пункта", shown.single().titleRes, c.titleRes)
        }
    }

    @Test
    fun `варианты не повторяются, у каждого подпись`() {
        Choices.ALL.forEach { c ->
            assertEquals(c.key, c.optionNames.size, c.optionNames.toSet().size)
            assertTrue(c.key, c.optionNames.isNotEmpty())
            c.optionNames.forEach { assertTrue("${c.key}/$it", c.labelResOf(it) != 0) }
        }
    }

    @Test
    fun `умолчания снимка — среди вариантов`() {
        val defaults = SettingsState()
        Choices.ALL.forEach { c -> assertTrue(c.key, c.currentName(defaults) in c.optionNames) }
    }

    @Test
    fun `пояснения — только у тех вариантов, где были`() {
        // Как в шторке до 2.9.15 (SettingsChoiceDialog.choiceHint)
        val withHints = Choices.ALL.associate { c -> c.key to c.optionNames.filter { c.hintResOf(it) != null } }
            .filterValues { it.isNotEmpty() }
        assertEquals(
            mapOf(
                "list_density" to listOf("COMPACT", "COMFORTABLE"),
                "ui_font" to listOf("SYSTEM", "ROBOTO"),
                "sms_text_mode" to listOf("UNTIL_EXPANDED", "HIDE")
            ).mapValues { it.value.toSet() },
            withHints.mapValues { it.value.toSet() }
        )
    }
}
