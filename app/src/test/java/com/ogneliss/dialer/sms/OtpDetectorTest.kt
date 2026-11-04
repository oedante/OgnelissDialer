package com.ogneliss.dialer.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Поиск кода и номеров в тексте SMS (2.0.0). Тексты — вымышленные
 * образцы типичных сообщений; номера — из учебных диапазонов.
 */
class OtpDetectorTest {

    @Test fun `код подтверждения по-русски`() =
        assertEquals("482913", OtpDetector.code("Код подтверждения: 482913. Никому не сообщайте его."))

    @Test fun `код по-английски`() =
        assertEquals("7391", OtpDetector.code("Your verification code is 7391"))

    @Test fun `код с буквенной приставкой — только цифры`() =
        assertEquals("583920", OtpDetector.code("G-583920 is your verification code."))

    @Test fun `две тройки через пробел`() =
        assertEquals("123456", OtpDetector.code("Code: 123 456"))

    @Test fun `без слова-признака кода нет`() =
        assertNull(OtpDetector.code("Покупка 1500 р. Карта *1234. Баланс 25000 р."))

    @Test fun `сумма и маска карты не код`() =
        assertEquals("5678", OtpDetector.code("Код 5678 для оплаты 1500 р. по карте *1234"))

    @Test fun `маска карты раньше кода`() =
        assertEquals("9012", OtpDetector.code("Карта *1234: покупка 990 руб. Код для подтверждения 9012"))

    @Test fun `пароль из букв и цифр`() =
        assertEquals("aB3dE9", OtpDetector.code("Ваш пароль: aB3dE9"))

    @Test fun `дата и время не код`() =
        assertNull(OtpDetector.code("Ваш код записи подтверждён: встреча 05.11.2026 в 12:30"))

    @Test fun `пароль со сроком действия`() =
        assertEquals("2468", OtpDetector.code("Пароль для входа 2468. Действует 5 минут"))

    @Test fun `номер телефона рядом с кодом не мешает`() =
        assertEquals("4321", OtpDetector.code("Вопросы: +7 900 000-00-00, код 4321"))

    @Test fun `ближайший к слову-признаку`() =
        assertEquals("1234", OtpDetector.code("Код подтверждения 1234 действует до 2027 года"))

    @Test fun `пустой текст`() = assertNull(OtpDetector.code(""))

    @Test fun `номера телефонов как написаны`() {
        val found = OtpDetector.phones("Звоните +7 900 000-00-00 или 8 (800) 000-00-00, код 4321")
        assertEquals(listOf("+7 900 000-00-00", "8 (800) 000-00-00"), found)
    }

    @Test fun `суммы и даты не номера`() {
        assertTrue(OtpDetector.phones("Списано 1500 2000 3000, дата 05.11.2026 12:30").isEmpty())
    }

    @Test fun `код не номер`() {
        assertTrue(OtpDetector.phones("Код 482913").isEmpty())
    }
}
