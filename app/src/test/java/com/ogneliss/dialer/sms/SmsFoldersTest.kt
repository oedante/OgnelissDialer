package com.ogneliss.dialer.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Папки SMS (2.2.0): разбор строки настройки и раскладка переписок. */
class SmsFoldersTest {

    @Test
    fun пустаяСтрока_всеПапкиПоПорядку() {
        assertEquals(SmsFolders.DEFAULT, SmsFolders.parse(null))
        assertEquals(SmsFolders.DEFAULT, SmsFolders.parse(""))
    }

    @Test
    fun порядокИСкрытыеСохраняются() {
        val list = SmsFolders.parse("UNREAD,ALL,-SERVICE")
        assertEquals(SmsFolder.UNREAD, list[0].first)
        assertEquals(SmsFolder.ALL, list[1].first)
        assertEquals(SmsFolder.SERVICE to false, list[2])
        // Не упомянутые — в конце, видимыми
        assertEquals(SmsFolder.entries.size, list.size)
        assertTrue(list.drop(3).all { it.second })
        assertEquals(list, SmsFolders.parse(SmsFolders.encode(list)))
    }

    @Test
    fun всеНельзяСкрыть() {
        assertTrue(SmsFolders.parse("-ALL,PERSONAL").first { it.first == SmsFolder.ALL }.second)
        assertFalse(SmsFolders.encode(listOf(SmsFolder.ALL to false)).startsWith("-"))
    }

    @Test
    fun мусорВСтрокеПропускается() {
        val list = SmsFolders.parse("FOO,,-BAR,PERSONAL,PERSONAL")
        assertEquals(SmsFolder.PERSONAL, list[0].first)
        assertEquals(SmsFolder.entries.size, list.size)
    }

    @Test
    fun сервисы_буквенныеИКороткие() {
        assertTrue(SmsFolders.isService("Sberbank"))
        assertTrue(SmsFolders.isService("900"))
        assertTrue(SmsFolders.isService("4444"))
        assertFalse(SmsFolders.isService("+7 912 345-67-89"))
    }

    @Test
    fun раскладкаПереписок() {
        assertTrue(SmsFolders.matches(SmsFolder.PERSONAL, "+79123456789", hasContact = true, unread = 0))
        assertTrue(SmsFolders.matches(SmsFolder.UNKNOWN, "+79123456789", hasContact = false, unread = 0))
        assertFalse(SmsFolders.matches(SmsFolder.UNKNOWN, "Bank", hasContact = false, unread = 0))
        assertTrue(SmsFolders.matches(SmsFolder.SERVICE, "Bank", hasContact = false, unread = 0))
        assertTrue(SmsFolders.matches(SmsFolder.UNREAD, "Bank", hasContact = false, unread = 2))
        assertFalse(SmsFolders.matches(SmsFolder.STARRED, "Bank", hasContact = false, unread = 2))
    }
}
