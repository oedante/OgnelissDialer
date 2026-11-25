package com.ogneliss.dialer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Колонка справа — порядок и показ (2.9.9, автор: «частое — ближе к нижнему
 * правому углу: журнал звонков, SMS, контакты и остальное»). Разбор — RailItem.
 */
class RailItemTest {

    @Test
    fun `по умолчанию - журнал, SMS, контакты, затем остальное`() {
        assertEquals(
            listOf(
                RailItem.JOURNAL, RailItem.MESSAGES, RailItem.CONTACTS,
                RailItem.NOTES, RailItem.REMINDERS, RailItem.OWN_CONTACT, RailItem.FAKE_CALL
            ),
            RailItem.visible(null)
        )
    }

    @Test
    fun `свой порядок и скрытые пункты`() {
        assertEquals(
            listOf(RailItem.CONTACTS, RailItem.JOURNAL, RailItem.FAKE_CALL, RailItem.MESSAGES, RailItem.REMINDERS),
            RailItem.visible("CONTACTS,JOURNAL,FAKE_CALL,-NOTES,MESSAGES,-OWN_CONTACT")
        )
    }

    @Test
    fun `журнал не скрыть`() {
        assertTrue(RailItem.JOURNAL in RailItem.visible("-JOURNAL,MESSAGES"))
    }

    @Test
    fun `у каждого пункта-раздела свой раздел`() {
        assertEquals(5, RailItem.entries.count { it.section != null })
    }
}
