package com.ogneliss.dialer.ui.conversation

import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.data.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Новое сообщение: подсказки «Кому» (2.1.3, NewMessagePicker.kt). Номера вымышленные. */
class NewMessageTest {

    private fun contact(id: Long, name: String, vararg numbers: String) = Contact(
        id = id, lookupKey = "k$id", displayName = name, photoUri = null, starred = false,
        numbers = numbers.map { PhoneNumber(it, "Мобильный", it.filter { c -> c.isDigit() || c == '+' }) }
    )

    private val book = listOf(
        contact(1, "Анна Петрова", "+79180000001"),
        contact(2, "Иван Анненков", "+79180000002", "+79280000003"),
        contact(3, "Сервис доставки", "+78005550000"),
        contact(4, "Марианна", "+79180000004"),
        contact(5, "Без номера")
    )

    @Test
    fun `по имени — сначала начало имени, потом начало слова, потом середина`() {
        val names = NewMessage.suggestions("анн", book).map { it.name }
        assertEquals(listOf("Анна Петрова", "Иван Анненков", "Иван Анненков", "Марианна"), names)
    }

    @Test
    fun `у совпавшего по имени — все номера, без номеров контакт не предлагается`() {
        assertEquals(2, NewMessage.suggestions("иван", book).size)
        assertTrue(NewMessage.suggestions("без", book).isEmpty())
    }

    @Test
    fun `по номеру — от двух цифр, восьмёрка находит плюс семь`() {
        assertTrue(NewMessage.suggestions("9", book).isEmpty())
        assertEquals(listOf("Иван Анненков"), NewMessage.suggestions("928", book).map { it.name })
        // «8 800…» — это «+7 800…»
        assertEquals(listOf("Сервис доставки"), NewMessage.suggestions("8800", book).map { it.name })
        // по номеру — только совпавший номер контакта
        assertEquals(listOf("+79280000003"), NewMessage.suggestions("928", book).map { it.number })
    }

    @Test
    fun `пустой запрос — подсказок нет`() {
        assertTrue(NewMessage.suggestions("  ", book).isEmpty())
    }

    @Test
    fun `набранный номер — от трёх цифр и только знаки номера`() {
        assertEquals("+7 918 000", NewMessage.typedNumber(" +7 918 000 "))
        assertNull(NewMessage.typedNumber("12"))
        assertNull(NewMessage.typedNumber("152 Дарья"))
    }

    @Test
    fun `дальше — набранный номер или единственная подсказка`() {
        val one = NewMessage.suggestions("марианна", book)
        assertEquals("+79180000004", NewMessage.target("марианна", one))
        assertNull(NewMessage.target("анн", NewMessage.suggestions("анн", book)))
        assertEquals("8918", NewMessage.target("8918", emptyList()))
    }
}
