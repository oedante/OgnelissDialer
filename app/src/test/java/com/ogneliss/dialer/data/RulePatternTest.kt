package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ШАБЛОНОВ БЛОКИРОВКИ.
 *
 * Два класса ошибок, которые эти тесты обязаны ловить:
 *
 * 1. ПРАВИЛО, КОТОРОЕ МОЛЧА НЕ РАБОТАЕТ. Номера сравниваются в E.164,
 *    поэтому «8 (900) 123-45-67» не совпадёт ни с чем. Пользователь
 *    увидит правило в списке, решит что защищён, и получит звонок.
 *    Это худший вид бага: приложение выглядит работающим.
 *
 * 2. СЛИШКОМ ШИРОКОЕ ПРАВИЛО. «+7*» заблокирует всё. Человек неделю
 *    не поймёт, почему телефон молчит.
 */
class RulePatternTest {

    // ── Нормализация обычных номеров ───────────────────────────────────────

    @Test
    fun `российский номер с восьмёркой приводится к E164`() {
        val result = RulePattern.parse("8 (900) 123-45-67")
        assertTrue(result is RulePattern.Result.Valid)
        assertEquals("+79001234567", (result as RulePattern.Result.Valid).pattern)
    }

    @Test
    fun `номер в международном формате остаётся как есть`() {
        val result = RulePattern.parse("+79001234567")
        assertEquals("+79001234567", (result as RulePattern.Result.Valid).pattern)
    }

    @Test
    fun `разные записи одного номера дают один шаблон`() {
        // Иначе пользователь добавит номер дважды и удивится
        val a = (RulePattern.parse("8 900 123 45 67") as RulePattern.Result.Valid).pattern
        val b = (RulePattern.parse("+7 (900) 123-45-67") as RulePattern.Result.Valid).pattern
        assertEquals(a, b)
    }

    // ── Маски ──────────────────────────────────────────────────────────────

    @Test
    fun `маска с восьмёркой нормализуется к плюс семь`() {
        // Пользователь пишет привычное 8800*, а сравнение идёт с +7800…
        val result = RulePattern.parse("8800*")
        assertTrue(result is RulePattern.Result.Valid)
        assertEquals("+7800*", (result as RulePattern.Result.Valid).pattern)
        assertTrue(result.isMask)
    }

    @Test
    fun `из маски убираются разделители`() {
        val result = RulePattern.parse("+7 (800) *") as RulePattern.Result.Valid
        assertEquals("+7800*", result.pattern)
    }

    @Test
    fun `нормализованная маска действительно совпадает с номерами`() {
        // Главная проверка: правило должно РАБОТАТЬ, а не просто сохраниться
        val pattern = (RulePattern.parse("8800*") as RulePattern.Result.Valid).pattern
        assertTrue(
            "Маска 8-800 обязана ловить бесплатные линии",
            RulePattern.matches(pattern, "+78005553535")
        )
        assertFalse(
            "Маска 8-800 не должна ловить обычные мобильные",
            RulePattern.matches(pattern, "+79001234567")
        )
    }

    @Test
    fun `вопросительный знак заменяет один символ`() {
        val pattern = (RulePattern.parse("+7900123456?") as RulePattern.Result.Valid).pattern
        assertTrue(RulePattern.matches(pattern, "+79001234567"))
        assertFalse(RulePattern.matches(pattern, "+790012345678"))
    }

    // ── Защита от опасных шаблонов ─────────────────────────────────────────

    @Test
    fun `звёздочка одна отклоняется`() {
        assertTrue(RulePattern.parse("*") is RulePattern.Result.TooBroad)
    }

    @Test
    fun `слишком короткий шаблон отклоняется`() {
        // "+7*" поймает вообще все российские номера
        assertTrue(RulePattern.parse("+7*") is RulePattern.Result.TooBroad)
        assertTrue(RulePattern.parse("79*") is RulePattern.Result.TooBroad)
    }

    @Test
    fun `три значащие цифры уже допустимы`() {
        assertTrue(RulePattern.parse("800*") is RulePattern.Result.Valid)
    }

    @Test
    fun `пустой ввод не считается ошибкой символов`() {
        assertTrue(RulePattern.parse("") is RulePattern.Result.Empty)
        assertTrue(RulePattern.parse("   ") is RulePattern.Result.Empty)
    }

    @Test
    fun `буквы в номере отклоняются с указанием символов`() {
        val result = RulePattern.parse("+7900abc")
        assertTrue(result is RulePattern.Result.InvalidCharacters)
        assertTrue((result as RulePattern.Result.InvalidCharacters).characters.contains("a"))
    }

    // ── Точное совпадение ──────────────────────────────────────────────────

    @Test
    fun `шаблон без маски совпадает только с точным номером`() {
        assertTrue(RulePattern.matches("+79001234567", "+79001234567"))
        assertFalse(RulePattern.matches("+79001234567", "+79001234568"))
    }

    @Test
    fun `описание шаблона читаемо`() {
        assertEquals("+7800…", RulePattern.describe("+7800*"))
    }

    // ── Ввод без недопустимых символов (2.1.0) ─────────────────────────────

    @Test
    fun `недопустимые символы отбрасываются при вводе`() {
        // Поле правила не объясняет ошибку, а не пускает лишнее:
        // после очистки шаблон должен разбираться как годный
        val cleaned = RulePattern.sanitize("8 (800) abc-12*")
        assertEquals("8 (800) -12*", cleaned)
        assertTrue(RulePattern.parse(cleaned) is RulePattern.Result.Valid)
    }

    @Test
    fun `очистка не трогает допустимый ввод`() {
        assertEquals("+7 900 123-45-6?", RulePattern.sanitize("+7 900 123-45-6?"))
    }
}
