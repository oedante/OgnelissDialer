package com.ogneliss.dialer.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ МАСКИРОВАНИЯ ПЕРСОНАЛЬНЫХ ДАННЫХ В ЛОГЕ.
 *
 * Это тест не функциональности, а СООТВЕТСТВИЯ ТРЕБОВАНИЯМ.
 * Файл лога пользователь отправляет наружу; попадание туда полного номера
 * означает утечку персональных данных и нарушение правил Google Play.
 * Поэтому проверка вынесена в автотест: она должна падать сборку,
 * а не обнаруживаться на ревью в магазине.
 */
class LoggerMaskTest {

    @Test
    fun `полный номер не сохраняется в маске`() {
        val original = "+79671112233"
        val masked = Logger.mask(original)

        assertFalse("В маске не должно быть исходного номера", masked.contains("9671112"))
        assertTrue("Должны остаться последние 4 цифры", masked.endsWith("2233"))
        assertTrue("Должен остаться код страны", masked.startsWith("+7"))
    }

    @Test
    fun `короткий номер маскируется полностью`() {
        assertEquals("****", Logger.mask("1234"))
    }

    @Test
    fun `пустой номер не ломает маскирование`() {
        assertEquals("<пусто>", Logger.mask(null))
        assertEquals("<пусто>", Logger.mask(""))
    }

    @Test
    fun `имя сокращается до инициалов`() {
        assertEquals("А. К.", Logger.maskName("Андрей Клинов"))
    }

    @Test
    fun `отсутствие имени обрабатывается`() {
        assertEquals("<без имени>", Logger.maskName(null))
    }
}
