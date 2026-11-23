package com.ogneliss.dialer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Конструктор нижней панели (2.9.2, PanelSlots). */
class PanelSlotsTest {

    private val j = PanelSlot.JOURNAL
    private val s = PanelSlot.MESSAGES
    private val c = PanelSlot.CONTACTS
    private val n = PanelSlot.NOTES
    private val r = PanelSlot.REMINDERS
    private val m = PanelSlot.MENU

    @Test
    fun `по умолчанию — по роли SMS, как решил автор`() {
        assertEquals(listOf(j, s, c, m), PanelSlots.defaults(smsDefault = true))
        assertEquals(listOf(j, c, n, m), PanelSlots.defaults(smsDefault = false))
    }

    @Test
    fun `сохранённое читается и пишется`() {
        val slots = listOf(c, j, r, m)
        assertEquals(slots, PanelSlots.parse(PanelSlots.encode(slots)))
        assertEquals(slots, PanelSlots.resolve("CONTACTS,JOURNAL,REMINDERS,MENU", smsDefault = true))
    }

    @Test
    fun `битое, без журнала, с повтором или не четыре — по умолчанию`() {
        listOf(null, "", "JOURNAL,SMS,CONTACTS,MENU", "CONTACTS,NOTES,REMINDERS,MENU", "JOURNAL,JOURNAL,NOTES,MENU", "JOURNAL,NOTES,MENU")
            .forEach { assertNull(it, PanelSlots.parse(it)) }
        assertEquals(listOf(j, c, n, m), PanelSlots.resolve("мусор", smsDefault = false))
    }

    @Test
    fun `раздел с панели меняется местами`() {
        assertEquals(listOf(c, s, j, m), PanelSlots.place(listOf(j, s, c, m), 0, c))
    }

    @Test
    fun `новый раздел встаёт на место, прежний уходит в меню`() {
        val out = PanelSlots.place(listOf(j, s, c, m), 3, r)
        assertEquals(listOf(j, s, c, r), out)
        assertEquals(setOf(m, n), PanelSlots.offPanel(out).toSet())
    }

    @Test
    fun `журнал не убирается — только переставляется`() {
        assertEquals(listOf(j, s, c, m), PanelSlots.place(listOf(j, s, c, m), 0, n))
        assertEquals(listOf(s, j, c, m), PanelSlots.place(listOf(j, s, c, m), 0, s))
    }
}
