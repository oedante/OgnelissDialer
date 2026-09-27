package com.ogneliss.dialer.util

import com.ogneliss.dialer.data.NumberCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ПРЕДУПРЕЖДЕНИЯ ПЕРЕД НАБОРОМ.
 *
 * Ошибка в обе стороны стоит дорого. Пропустить платный номер — человек
 * теряет деньги. Предупреждать о каждом звонке — человек перестаёт
 * читать предупреждения и пропустит настоящее.
 */
class PreDialWarningTest {

    @Test
    fun `платный номер требует подтверждения`() {
        // Классическая схема: пропущенный звонок с 8-809 в расчёте
        // на то, что человек перезвонит
        val warning = PreDialWarning.evaluate(NumberCategory.PREMIUM, isInContacts = false)
        assertEquals(PreDialWarning.Level.WARNING, warning.level)
        assertTrue(PreDialWarning.requiresConfirmation(warning))
    }

    @Test
    fun `международный номер требует подтверждения`() {
        val warning = PreDialWarning.evaluate(
            NumberCategory.INTERNATIONAL, isInContacts = false, country = "Латвия"
        )
        assertTrue(PreDialWarning.requiresConfirmation(warning))
        assertEquals("Латвия", warning.country)
    }

    @Test
    fun `короткий номер только информирует`() {
        // Может быть платным, а может быть службой доставки —
        // останавливать набор из-за этого чрезмерно
        val warning = PreDialWarning.evaluate(NumberCategory.SHORT_CODE, isInContacts = false)
        assertEquals(PreDialWarning.Level.INFO, warning.level)
        assertFalse(PreDialWarning.requiresConfirmation(warning))
    }

    @Test
    fun `обычный номер не вызывает предупреждения`() {
        val warning = PreDialWarning.evaluate(NumberCategory.PERSONAL, isInContacts = false)
        assertEquals(PreDialWarning.Level.NONE, warning.level)
    }

    @Test
    fun `знакомый номер не вызывает предупреждения даже если платный`() {
        // Пользователь сам добавил его в книгу и звонил раньше.
        // Предупреждать каждый раз — приучить игнорировать предупреждения
        val warning = PreDialWarning.evaluate(NumberCategory.PREMIUM, isInContacts = true)
        assertEquals(PreDialWarning.Level.NONE, warning.level)
        assertFalse(PreDialWarning.requiresConfirmation(warning))
    }

    @Test
    fun `знакомый международный номер тоже не предупреждает`() {
        // Родственник за границей — обычный сценарий
        val warning = PreDialWarning.evaluate(
            NumberCategory.INTERNATIONAL, isInContacts = true
        )
        assertEquals(PreDialWarning.Level.NONE, warning.level)
    }

    @Test
    fun `бесплатная линия не вызывает предупреждения`() {
        val warning = PreDialWarning.evaluate(NumberCategory.TOLL_FREE, isInContacts = false)
        assertEquals(PreDialWarning.Level.NONE, warning.level)
    }
}
