package com.ogneliss.dialer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Буквы для аватара контакта без фото (1.3.5). */
class InitialsTest {

    @Test
    fun `два слова — две буквы`() {
        assertEquals("ТК", Initials.of("Тестовый Контакт"))
        assertEquals("SM", Initials.of("Sankras, Montazhnaya Kompaniya"))
        assertEquals("ММ", Initials.of("Мастер Мебель Монтаж"))
    }

    @Test
    fun `одно слово, слово с цифрами — одна буква`() {
        assertEquals("Т", Initials.of("Тест1"))
        assertEquals("R", Initials.of("RusTobaco"))
        assertEquals("С", Initials.of("Слесарь 2"))
    }

    @Test
    fun `строчные — заглавными`() {
        assertEquals("АК", Initials.of("андрей клинов"))
    }

    @Test
    fun `без слов с буквой — нет букв, остаётся значок`() {
        assertNull(Initials.of("8 (495) 488-61-66"))
        assertNull(Initials.of("911"))
        assertNull(Initials.of("  "))
        assertNull(Initials.of(null))
    }
}
