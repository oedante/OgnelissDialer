package com.ogneliss.dialer.settings

import com.ogneliss.dialer.ui.components.MainSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Раздел при запуске (2.7.0, форум, oleg_p). Чистая функция StartSection.resolve —
 * без Android и Robolectric.
 */
class StartSectionTest {

    private fun cold(
        choice: StartChoice,
        last: String? = null,
        explicit: String? = null,
        icons: Boolean = false
    ) = StartSection.resolve(choice, last, explicit, icons, coldLauncherStart = true)

    @Test
    fun `маршруты совпадают с главными разделами`() {
        assertEquals(MainSection.JOURNAL.route, StartSection.JOURNAL)
        assertEquals(MainSection.MESSAGES.route, StartSection.MESSAGES)
        assertEquals(MainSection.CONTACTS.route, StartSection.CONTACTS)
    }

    @Test
    fun `по умолчанию журнал`() {
        assertEquals(StartChoice.JOURNAL, StartChoice.from(null))
        assertEquals(StartChoice.JOURNAL, StartChoice.from("неизвестное"))
        assertEquals(StartSection.JOURNAL, cold(StartChoice.from(null)))
    }

    @Test
    fun `выбран журнал`() {
        assertEquals(StartSection.JOURNAL, cold(StartChoice.JOURNAL, last = StartSection.CONTACTS))
    }

    @Test
    fun `выбраны SMS`() {
        assertEquals(StartSection.MESSAGES, cold(StartChoice.MESSAGES))
    }

    @Test
    fun `выбраны контакты`() {
        assertEquals(StartSection.CONTACTS, cold(StartChoice.CONTACTS))
    }

    @Test
    fun `последний раздел открывает сохранённый`() {
        assertEquals(StartSection.MESSAGES, cold(StartChoice.LAST, last = StartSection.MESSAGES))
        assertEquals(StartSection.CONTACTS, cold(StartChoice.LAST, last = StartSection.CONTACTS))
        assertEquals(StartSection.JOURNAL, cold(StartChoice.LAST, last = StartSection.JOURNAL))
    }

    @Test
    fun `последний раздел без сохранённого значения открывает журнал`() {
        assertEquals(StartSection.JOURNAL, cold(StartChoice.LAST, last = null))
    }

    @Test
    fun `последний раздел с чужим значением открывает журнал`() {
        assertEquals(StartSection.JOURNAL, cold(StartChoice.LAST, last = "notes"))
        assertEquals(StartSection.JOURNAL, cold(StartChoice.LAST, last = ""))
    }

    @Test
    fun `явное намерение важнее настройки`() {
        assertEquals(
            StartSection.CONTACTS,
            cold(StartChoice.MESSAGES, last = StartSection.JOURNAL, explicit = StartSection.CONTACTS)
        )
        assertEquals(
            StartSection.MESSAGES,
            cold(StartChoice.LAST, last = StartSection.CONTACTS, explicit = StartSection.MESSAGES)
        )
    }

    @Test
    fun `явное намерение важнее отдельных значков`() {
        assertEquals(
            StartSection.MESSAGES,
            cold(StartChoice.JOURNAL, explicit = StartSection.MESSAGES, icons = true)
        )
    }

    @Test
    fun `явное намерение действует и не при запуске значком`() {
        assertEquals(
            StartSection.MESSAGES,
            StartSection.resolve(StartChoice.CONTACTS, null, StartSection.MESSAGES, false, coldLauncherStart = false)
        )
    }

    @Test
    fun `отдельные значки главнее настройки и открывают журнал`() {
        StartChoice.entries.forEach { choice ->
            assertEquals(
                "выбор $choice",
                StartSection.JOURNAL,
                cold(choice, last = StartSection.CONTACTS, icons = true)
            )
        }
    }

    @Test
    fun `возврат из фона остаётся где был`() {
        StartChoice.entries.forEach { choice ->
            assertNull(
                "выбор $choice",
                StartSection.resolve(choice, StartSection.CONTACTS, null, false, coldLauncherStart = false)
            )
        }
    }

    @Test
    fun `возврат из фона с отдельными значками тоже остаётся где был`() {
        assertNull(StartSection.resolve(StartChoice.MESSAGES, null, null, true, coldLauncherStart = false))
    }

    @Test
    fun `запоминаются только главные разделы`() {
        assertEquals(setOf("dialer", "messages", "contacts"), StartSection.MAIN_ROUTES)
        assertTrue(MainSection.NOTES.route !in StartSection.MAIN_ROUTES)
        assertTrue(MainSection.REMINDERS.route !in StartSection.MAIN_ROUTES)
    }
}
