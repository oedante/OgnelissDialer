package com.ogneliss.dialer.data

import com.ogneliss.dialer.util.NumberDisplay
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ТЕСТЫ КОРОТКИХ ПОДПИСЕЙ НОМЕРОВ.
 *
 * Подписи используются в чипах свайпа и кнопках выбора, где места мало.
 * Ошибка здесь означает, что пользователь позвонит не на тот номер —
 * а у контакта с тремя номерами это рабочий, домашний и мобильный.
 */
class NumberDisplayTest {

    @Test
    fun `последние четыре цифры извлекаются из форматированного номера`() {
        assertEquals("2233", NumberDisplay.lastFour("8 (967) 111-22-33"))
        assertEquals("2233", NumberDisplay.lastFour("+79671112233"))
    }

    @Test
    fun `короткая подпись читается как окончание номера`() {
        assertEquals("…22-33", NumberDisplay.shortLabel("8 (967) 111-22-33"))
        assertEquals("…55-66", NumberDisplay.shortLabel("8 (910) 444-55-66"))
    }

    @Test
    fun `короткий номер не ломает подпись`() {
        assertEquals("112", NumberDisplay.shortLabel("112"))
    }

    @Test
    fun `осмысленная метка предпочитается цифрам`() {
        val label = NumberDisplay.chipLabel(
            number = "8 (484) 222-33-44",
            label = "Рабочий",
            allLabels = listOf("Рабочий", "Домашний", "Мобильный")
        )
        assertEquals("Рабочий", label)
    }

    @Test
    fun `одинаковые метки не различают номера и заменяются цифрами`() {
        // Три «Мобильный» ничего не различают — нужны цифры
        val label = NumberDisplay.chipLabel(
            number = "8 (967) 111-22-33",
            label = "Мобильный",
            allLabels = listOf("Мобильный", "Мобильный", "Мобильный")
        )
        assertEquals("…22-33", label)
    }

    @Test
    fun `отсутствие метки заменяется цифрами`() {
        val label = NumberDisplay.chipLabel(
            number = "8 (910) 444-55-66",
            label = null,
            allLabels = emptyList()
        )
        assertEquals("…55-66", label)
    }
}
