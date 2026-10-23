package com.ogneliss.dialer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ СРАВНЕНИЯ ТЕКСТА ДЛЯ ПОИСКА БУКВАМИ (1.6.0).
 *
 * Позиция совпадения нужна подсветке имени: ошибка в ней — падение
 * в списке результатов на каждую букву. Порядок (rank) решает, кто
 * окажется первым в выдаче.
 */
class TextFoldTest {

    @Test
    fun `регистр и ё не важны`() {
        assertEquals(0, TextFold.indexOf("Семён", "СЕМЕН"))
        assertEquals(0, TextFold.indexOf("Семен", "семён"))
    }

    @Test
    fun `позиция — в исходной строке`() {
        assertEquals(5, TextFold.indexOf("Иван Петров", "пет"))
        assertEquals(-1, TextFold.indexOf("Иван", "Иванов"))
        assertEquals(-1, TextFold.indexOf("Иван", ""))
    }

    @Test
    fun `поиск с позиции`() {
        assertEquals(5, TextFold.indexOf("Иван Ванин", "ван", from = 2))
    }

    @Test
    fun `текст — когда есть буквы`() {
        assertTrue(TextFold.isTextQuery("ольга"))
        assertTrue(TextFold.isTextQuery("Иван 2"))
        assertFalse(TextFold.isTextQuery("8912"))
        assertFalse(TextFold.isTextQuery("+7*#"))
    }

    @Test
    fun `порядок совпадений в имени`() {
        assertEquals(0, TextFold.rank("Анна", "ан"))
        // Первое вхождение — внутри «Иван», но «Анохин» начинается с запроса
        assertEquals(1, TextFold.rank("Иван Анохин", "ан"))
        assertEquals(2, TextFold.rank("Светлана", "ан"))
        assertEquals(3, TextFold.rank("Борис", "ан"))
    }

    @Test
    fun `contains принимает null`() {
        assertFalse(TextFold.contains(null, "а"))
        assertTrue(TextFold.contains("Заметка", "мет"))
    }
}
