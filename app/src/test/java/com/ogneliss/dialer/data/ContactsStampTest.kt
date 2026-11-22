package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * КОГДА ПЕРЕЧИТЫВАТЬ КНИГУ (2.8.8, ContactsStamp).
 *
 * Лог автора: книга из 488 контактов читалась целиком по два-три раза после
 * каждого звонка. Теперь — только когда изменилась отметка книги; правило
 * должно не пропускать настоящих изменений.
 */
class ContactsStampTest {

    private val stamp = ContactsStamp.of(count = 488, starred = 12, lastUpdated = 1_000L, lastDeleted = 500L)

    @Test
    fun `та же отметка и контакты на месте — книгу не читаем`() {
        assertFalse(ContactsStamp.mustRead(stamp, stamp, haveContacts = true))
    }

    @Test
    fun `первое чтение — читаем`() {
        assertTrue(ContactsStamp.mustRead(stamp, lastRead = null, haveContacts = true))
    }

    @Test
    fun `контактов нет — читаем всегда, пустота бывает сбоем провайдера`() {
        assertTrue(ContactsStamp.mustRead(stamp, stamp, haveContacts = false))
    }

    @Test
    fun `отметка не прочиталась — читаем, лучше лишний раз`() {
        assertTrue(ContactsStamp.mustRead(current = null, lastRead = stamp, haveContacts = true))
    }

    @Test
    fun `добавление, удаление, звёздочка и правка меняют отметку`() {
        val added = ContactsStamp.of(489, 12, 2_000L, 500L)
        val deleted = ContactsStamp.of(487, 12, 1_000L, 3_000L)
        val starred = ContactsStamp.of(488, 13, 1_000L, 500L)
        val edited = ContactsStamp.of(488, 12, 4_000L, 500L)
        listOf(added, deleted, starred, edited).forEach { changed ->
            assertNotEquals(stamp, changed)
            assertTrue(ContactsStamp.mustRead(changed, stamp, haveContacts = true))
        }
    }

    @Test
    fun `удалили один и добавили другой — время удаления всё равно меняет отметку`() {
        // Число то же, время правки могло не сдвинуться, если новый контакт старше
        val swapped = ContactsStamp.of(488, 12, 1_000L, 9_000L)
        assertTrue(ContactsStamp.mustRead(swapped, stamp, haveContacts = true))
    }

    @Test
    fun `выдали доступ к контактам — отметка сменилась`() {
        assertTrue(ContactsStamp.mustRead(stamp, ContactsStamp.NO_ACCESS, haveContacts = true))
        assertEquals("488/12/1000/500", stamp)
    }
}
