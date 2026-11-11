package com.ogneliss.dialer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Буквенные отправители SMS (2.1.3). Отзыв с форума: всем SMS с буквенным
 * именем («RSCHS», «MegaFon») подставлялся контакт, у которого в поле номера
 * стояло «BRAVOOPTICA»: у всех таких адресов ключ номера был пустой строкой.
 */
class PhoneFormatterAlphaTest {

    @Test
    fun `буквенный адрес остаётся собой, а не пустой строкой`() {
        assertEquals("RSCHS", PhoneFormatter.toE164("RSCHS", "RU"))
        assertEquals("MegaFon", PhoneFormatter.toE164(" MegaFon ", "RU"))
        assertTrue(PhoneFormatter.toE164("BRAVOOPTICA", "RU").isNotBlank())
    }

    @Test
    fun `разные буквенные отправители — разные ключи`() {
        assertNotEquals(PhoneFormatter.toE164("RSCHS", "RU"), PhoneFormatter.toE164("BRAVOOPTICA", "RU"))
        assertNotEquals(PhoneFormatter.toE164("MegaFon", "RU"), PhoneFormatter.toE164("Tele2", "RU"))
    }

    @Test
    fun `обычные номера — как раньше`() {
        assertEquals("+79211222565", PhoneFormatter.toE164("+7 921 122-25-65", "RU"))
        assertEquals("+79211222565", PhoneFormatter.toE164("8 (921) 122-25-65", "RU"))
        // короткий номер без букв буквенным не считается — в нём нет букв
        assertTrue(PhoneFormatter.toE164("900", "RU").all { it.isDigit() || it == '+' })
    }
}
