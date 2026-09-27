package com.ogneliss.dialer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ СТРОКИ НАБОРА СО СПЕЦСИМВОЛАМИ.
 *
 * Ошибка здесь означает набор неверного номера. Классическая ловушка:
 * прогнать строку с запятыми через форматтер номеров — libphonenumber
 * их не понимает и либо вернёт мусор, либо молча отрежет хвост донабора,
 * и пользователь окажется в голосовом меню без автоматического ввода.
 */
class DialStringTest {

    @Test
    fun `обычный номер не содержит спецсимволов`() {
        assertFalse(DialString.hasSpecialCharacters("+79001234567"))
    }

    @Test
    fun `пауза распознаётся`() {
        assertTrue(DialString.hasSpecialCharacters("+74951234567,1"))
    }

    @Test
    fun `ожидание распознаётся`() {
        assertTrue(DialString.hasSpecialCharacters("+74951234567;1234"))
    }

    @Test
    fun `основной номер отделяется от хвоста`() {
        assertEquals("+74951234567", DialString.primaryNumber("+74951234567,,1,2;1234"))
    }

    @Test
    fun `хвост донабора сохраняется целиком`() {
        assertEquals(",,1,2;1234", DialString.postDialSequence("+74951234567,,1,2;1234"))
    }

    @Test
    fun `у обычного номера хвост пуст`() {
        assertEquals("", DialString.postDialSequence("+79001234567"))
    }

    @Test
    fun `форматирование не теряет хвост`() {
        // Главная проверка: прогон всей строки через форматтер номеров
        // отрезал бы донабор, и звонок в банк перестал бы работать
        val result = DialString.formatForDisplay("+74951234567,1,2")
        assertTrue("Хвост донабора потерян", result.endsWith(",1,2"))
    }

    @Test
    fun `последовательность расшифровывается по шагам`() {
        val steps = DialString.describeSequence("+74951234567,1;5678")
        assertEquals(
            listOf(
                DialString.Step.Pause,
                DialString.Step.Digits("1"),
                DialString.Step.Wait,
                DialString.Step.Digits("5678")
            ),
            steps
        )
    }

    @Test
    fun `несколько пауз подряд дают несколько шагов`() {
        val steps = DialString.describeSequence("+74951234567,,1")
        assertEquals(3, steps.size)
        assertEquals(DialString.Step.Pause, steps[0])
        assertEquals(DialString.Step.Pause, steps[1])
    }

    @Test
    fun `у обычного номера шагов нет`() {
        assertTrue(DialString.describeSequence("+79001234567").isEmpty())
    }
}
