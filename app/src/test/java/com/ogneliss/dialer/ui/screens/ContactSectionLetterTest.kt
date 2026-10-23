package com.ogneliss.dialer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * БУКВЫ РАЗДЕЛОВ СПИСКА КОНТАКТОВ (1.5.7).
 *
 * Порядок самих разделов берётся из книги (по первому появлению буквы
 * в списке, отсортированном по правилам языка телефона), а здесь —
 * какая буква достаётся имени. Ошибка тут разносит контакты одной буквы
 * по двум разделам или ставит раздел не на своё место.
 */
class ContactSectionLetterTest {

    @Test
    fun `первая буква имени — заглавная`() {
        assertEquals('А', sectionLetter("анна"))
        assertEquals('V', sectionLetter("victor"))
    }

    @Test
    fun `Ё — в разделе Е, как в системной книге`() {
        assertEquals('Е', sectionLetter("Ёлкин Пётр"))
        assertEquals('Е', sectionLetter("ёжик"))
        assertEquals('Е', sectionLetter("Елена"))
    }

    @Test
    fun `имя с цифры или знака — в разделе решётки`() {
        assertEquals('#', sectionLetter("01.Баланс"))
        assertEquals('#', sectionLetter("+7 900 000-00-00"))
        assertEquals('#', sectionLetter("(Работа)"))
    }

    @Test
    fun `пустое имя не роняет список`() {
        assertEquals('#', sectionLetter(""))
    }

    @Test
    fun `разделы идут в порядке книги, а не по коду символа`() {
        // Книга по-русски: цифры, кириллица, потом латиница
        val book = listOf("01.Баланс", "Анна", "Елена", "Ёлкин", "Яна", "Bob", "victor")
        val order = HashMap<Char, Int>()
        book.forEach { order.getOrPut(sectionLetter(it)) { order.size } }

        val sorted = book.groupBy { sectionLetter(it) }
            .toSortedMap(compareBy<Char> { order[it] ?: Int.MAX_VALUE }.thenBy { it })

        assertEquals(listOf('#', 'А', 'Е', 'Я', 'B', 'V'), sorted.keys.toList())
        assertEquals(listOf("Елена", "Ёлкин"), sorted['Е'])
    }
}
