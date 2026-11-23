package com.ogneliss.dialer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * «Инструменты» меню (2.9.3): по умолчанию всё и по порядку; скрытое и
 * переставленное — из строки FolderOrder; обязательной строки нет.
 */
class MenuToolTest {

    @Test
    fun `по умолчанию видно всё по порядку`() {
        assertEquals(MenuTool.entries.toList(), MenuTool.visible(null))
        assertEquals(MenuTool.entries.toList(), MenuTool.visible(""))
    }

    @Test
    fun `скрытое не показывается, порядок из строки`() {
        val raw = "NOTES,-OWN_CONTACT,FAKE_CALL,MESSAGES,CONTACTS,-REMINDERS"
        assertEquals(
            listOf(MenuTool.NOTES, MenuTool.FAKE_CALL, MenuTool.MESSAGES, MenuTool.CONTACTS),
            MenuTool.visible(raw)
        )
    }

    @Test
    fun `можно скрыть всё — обязательной строки нет`() {
        val raw = MenuTool.keys.joinToString(",") { "-$it" }
        assertEquals(emptyList<MenuTool>(), MenuTool.visible(raw))
    }

    @Test
    fun `новая строка из обновления появляется включённой в конце`() {
        // Строка, сохранённая до появления «Напоминаний» в списке: их ключа в ней нет
        val raw = "OWN_CONTACT,FAKE_CALL,MESSAGES,CONTACTS,NOTES"
        assertEquals(MenuTool.REMINDERS, MenuTool.visible(raw).last())
    }

    @Test
    fun `незнакомые и повторённые ключи пропускаются`() {
        val raw = "NOTES,NOTES,UNKNOWN,-FAKE_CALL"
        assertEquals(
            listOf(MenuTool.NOTES, MenuTool.OWN_CONTACT, MenuTool.MESSAGES, MenuTool.CONTACTS, MenuTool.REMINDERS),
            MenuTool.visible(raw)
        )
    }
}
