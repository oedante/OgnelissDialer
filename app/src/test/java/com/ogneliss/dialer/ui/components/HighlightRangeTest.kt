package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.t9.T9Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ГРАНИЦ ПОДСВЕТКИ.
 *
 * Подсветка вычисляет позиции в строке. Ошибка на единицу даёт либо
 * обрезанное совпадение, либо `StringIndexOutOfBoundsException`
 * прямо в списке результатов — то есть падение при вводе цифры,
 * самое частое действие в приложении.
 *
 * Здесь проверяется та же арифметика, что применяется в HighlightedName
 * и HighlightedNumber.
 */
class HighlightRangeTest {

    /** Повторяет вычисление диапазона из HighlightedName. */
    private fun nameRange(name: String, digits: String): IntRange? {
        val start = T9Engine.matchIndex(digits, name)
        if (start < 0) return null
        val end = (start + digits.length).coerceAtMost(name.length)
        return start until end
    }

    @Test
    fun `совпадение с начала имени подсвечивается с нуля`() {
        // "Андрей": А=2 Н=5 Д=3
        val range = nameRange("Андрей", "253")
        assertEquals(0, range?.first)
        assertEquals(2, range?.last)
    }

    @Test
    fun `совпадение по фамилии находит второе слово`() {
        val range = nameRange("Хабиров Ренат", "63")
        assertTrue("Ожидалось совпадение", range != null)
        // Подсветка должна начаться на слове «Ренат», а не в начале строки
        assertTrue("Совпадение должно быть после пробела", (range?.first ?: 0) > 0)
    }

    @Test
    fun `запрос длиннее имени не выходит за границы`() {
        // Классическая ошибка на единицу: без coerceAtMost здесь
        // было бы падение в списке результатов
        val name = "Ян"
        val range = nameRange(name, "926666")
        if (range != null) {
            assertTrue("Конец диапазона вышел за строку", range.last < name.length)
        }
    }

    @Test
    fun `отсутствие совпадения не даёт диапазона`() {
        assertEquals(null, nameRange("Андрей", "999"))
    }

    /** Повторяет сопоставление позиций из HighlightedNumber. */
    private fun numberRange(formatted: String, digits: String): IntRange? {
        val bare = StringBuilder()
        val positions = ArrayList<Int>()
        formatted.forEachIndexed { i, ch ->
            if (ch.isDigit()) { bare.append(ch); positions.add(i) }
        }
        val start = bare.indexOf(digits)
        if (start < 0) return null
        val end = positions.getOrElse(start + digits.length - 1) { positions.last() } + 1
        return positions[start] until end
    }

    @Test
    fun `подсветка в номере учитывает разделители`() {
        // Ищем "6605" в "8 (967) 666-05-12": в голых цифрах это позиция 6,
        // но в отформатированной строке — совсем другая
        val formatted = "8 (967) 666-05-12"
        val range = numberRange(formatted, "6605")
        assertTrue(range != null)

        val highlighted = formatted.substring(range!!.first, range.last + 1)
        // Подсвеченный кусок должен содержать искомые цифры
        assertEquals("6605", highlighted.filter { it.isDigit() })
    }

    @Test
    fun `поиск по началу номера подсвечивает начало`() {
        val range = numberRange("8 (967) 666-05-12", "8967")
        assertEquals(0, range?.first)
    }

    @Test
    fun `отсутствие цифр в номере не ломает подсветку`() {
        assertEquals(null, numberRange("8 (967) 666-05-12", "0000"))
    }

    @Test
    fun `совпадение в конце номера не выходит за границы`() {
        val formatted = "8 (967) 666-05-12"
        val range = numberRange(formatted, "12")
        assertTrue(range != null)
        assertTrue("Конец вышел за строку", range!!.last < formatted.length)
    }
}
