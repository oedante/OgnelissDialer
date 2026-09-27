package com.ogneliss.dialer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ РЕЖИМА МНОЖЕСТВЕННОГО ВЫБОРА.
 *
 * Логика простая, но у неё есть неочевидный случай: снятие последнего
 * выделения. Если оставить режим включённым, пользователь видит панель
 * действий над пустым выбором — нажимает «Удалить» и не понимает,
 * почему ничего не произошло.
 */
class SelectionStateTest {

    @Test
    fun `изначально режим выключен`() {
        val state = SelectionState<String>()
        assertFalse(state.isActive)
        assertEquals(0, state.count)
    }

    @Test
    fun `запуск включает режим и выделяет элемент`() {
        val state = SelectionState<String>()
        state.start("a")
        assertTrue(state.isActive)
        assertTrue(state.isSelected("a"))
        assertEquals(1, state.count)
    }

    @Test
    fun `переключение добавляет и убирает элементы`() {
        val state = SelectionState<String>()
        state.start("a")
        state.toggle("b")
        assertEquals(2, state.count)

        state.toggle("b")
        assertEquals(1, state.count)
        assertFalse(state.isSelected("b"))
    }

    @Test
    fun `снятие последнего выделения оставляет режим включённым`() {
        /*
         * ПОВЕДЕНИЕ ИЗМЕНИЛОСЬ ОСОЗНАННО.
         *
         * Тест требовал обратного: пустой выбор выключает режим. Это
         * перестало работать, когда появился startEmpty() — включение
         * режима кнопкой, до того как человек что-то отметил. С прежним
         * правилом режим выключался бы сразу после включения.
         *
         * Проверять теперь надо именно новое правило: выбор пуст,
         * но режим держится, пока человек сам не выйдет из него.
         */
        val state = SelectionState<String>()
        state.start("a")
        state.toggle("a")

        assertTrue("Режим держится до явного выхода", state.isActive)
        assertEquals(0, state.count)
    }

    @Test
    fun `выбор всех включает режим`() {
        val state = SelectionState<String>()
        state.selectAll(listOf("a", "b", "c"))
        assertTrue(state.isActive)
        assertEquals(3, state.count)
    }

    @Test
    fun `выбор всех из пустого списка не включает режим`() {
        // Пустой журнал: включать режим выбора не над чем
        val state = SelectionState<String>()
        state.selectAll(emptyList())
        assertFalse(state.isActive)
    }

    @Test
    fun `сброс очищает выбор и выключает режим`() {
        val state = SelectionState<String>()
        state.selectAll(listOf("a", "b"))
        state.clear()
        assertFalse(state.isActive)
        assertEquals(0, state.count)
    }

    @Test
    fun `повторный выбор всех не удваивает элементы`() {
        val state = SelectionState<String>()
        state.selectAll(listOf("a", "b"))
        state.selectAll(listOf("a", "b"))
        assertEquals(2, state.count)
    }
}
