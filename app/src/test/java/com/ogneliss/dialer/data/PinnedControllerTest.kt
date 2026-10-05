package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Чистые функции PinnedController (1.3.4): перевод закреплений на контакты
 * и строки экрана «Закреплённые». Раньше это было внутри DialerViewModel
 * и без телефона не проверялось.
 */
class PinnedControllerTest {

    private val lk = "0r12-ABC"

    private fun contact(id: Long, name: String, vararg e164: String, photo: String? = null) = Contact(
        id = id, lookupKey = if (id == 1L) lk else "lk$id", displayName = name,
        photoUri = photo, starred = false,
        numbers = e164.map { PhoneNumber(it, "Mobile", it) }
    )

    private fun group(e164: String, contactId: Long?, name: String? = null) = CallLogGroup(
        key = e164, lastCallId = 1, number = e164, e164 = e164,
        displayName = name, photoUri = null, contactId = contactId,
        lastType = CallType.INCOMING, lastDate = 0, durationSec = 0,
        count = 1, incomingCount = 1, outgoingCount = 0, missedCount = 0,
        rejectedCount = 0, totalDurationSec = 0, numbers = listOf(e164),
        subscriptionLabel = null, subscriptionId = null
    )

    private val love = contact(1, "Любимая", "+79105214092", "+79676660512", photo = "content://photo/1")

    @Test
    fun `перевод — номер из книги становится контактом`() {
        val migrated = PinnedController.migrate(listOf("+79105214092", "+79001112233"), listOf(love))!!
        assertEquals(lk, PinnedContacts.parse(migrated[0]).lookupKey)
        assertEquals("+79001112233", migrated[1])
    }

    @Test
    fun `перевод — менять нечего — null, в настройки не пишем`() {
        val already = listOf(PinnedContacts.contactKey(lk, "+79105214092"))
        assertNull(PinnedController.migrate(already, listOf(love)))
        assertNull(PinnedController.migrate(emptyList(), listOf(love)))
        assertNull(PinnedController.migrate(listOf("+79105214092"), emptyList()))
    }

    @Test
    fun `строка экрана — имя и фото из книги, номер последнего звонка с другого номера`() {
        val pins = listOf(PinnedContacts.contactKey(lk, "+79105214092"))
        val log = listOf(group("+79676660512", contactId = 1))
        val entry = PinnedController.entries(pins, listOf(love), log).single()
        assertEquals("Любимая", entry.displayName)
        assertEquals("content://photo/1", entry.photoUri)
        assertEquals("+79676660512", entry.e164)
        assertEquals(pins[0], entry.key)
    }

    @Test
    fun `строка экрана — журнал пуст — первый номер контакта`() {
        val pins = listOf(PinnedContacts.contactKey(lk, "+79105214092"))
        val entry = PinnedController.entries(pins, listOf(love), emptyList()).single()
        assertEquals("+79105214092", entry.e164)
        assertEquals("Любимая", entry.displayName)
    }

    @Test
    fun `строка экрана — контакт удалён — держится за запасной номер`() {
        val pins = listOf(PinnedContacts.contactKey(lk, "+79105214092"))
        val entry = PinnedController.entries(pins, emptyList(), emptyList()).single()
        assertEquals("+79105214092", entry.e164)
        assertNull(entry.displayName)
    }

    // ── Удаление контакта снимает его закрепления (1.3.4) ─────────────────

    @Test
    fun `удаление — снимается закрепление по ключу контакта`() {
        val pins = listOf(PinnedContacts.contactKey(lk, "+79105214092"), "+79001112233")
        assertEquals(listOf("+79001112233"), PinnedController.withoutContact(pins, love))
        org.junit.Assert.assertTrue(PinnedController.isContactPinned(pins, love))
    }

    @Test
    fun `удаление — снимаются и старые закрепления по любому номеру контакта`() {
        val pins = listOf("+79676660512", "+79001112233", "+79105214092")
        assertEquals(listOf("+79001112233"), PinnedController.withoutContact(pins, love))
    }

    @Test
    fun `удаление — чужие закрепления не трогаются`() {
        val pins = listOf("+79001112233")
        assertEquals(pins, PinnedController.withoutContact(pins, love))
        org.junit.Assert.assertFalse(PinnedController.isContactPinned(pins, love))
    }
}
