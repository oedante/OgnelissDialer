package com.ogneliss.dialer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ РАЗБОРА ГОЛОСОВОГО ЗАПРОСА.
 *
 * Распознаватель отдаёт не цифры, а слова: «восемь девятьсот сто двадцать
 * три». Логика сборки числа из числительных неочевидна — составные
 * («девятьсот двадцать три» = 923) складываются, простые идут подряд.
 *
 * Проверять это вручную означало бы каждый раз диктовать в микрофон
 * и надеяться, что распознаватель услышал так же.
 */
class VoiceQueryParserTest {

    // ── Имена ──────────────────────────────────────────────────────────────

    @Test
    fun `командное слово отбрасывается перед именем`() {
        val q = VoiceQueryParser.parse("позвони Андрею")
        assertTrue(q is VoiceQueryParser.Query.Name)
        assertEquals("андрею", (q as VoiceQueryParser.Query.Name).text)
    }

    @Test
    fun `имя без команды распознаётся как имя`() {
        val q = VoiceQueryParser.parse("Хабиров Ренат")
        assertEquals("хабиров ренат", (q as VoiceQueryParser.Query.Name).text)
    }

    @Test
    fun `английская команда тоже отбрасывается`() {
        val q = VoiceQueryParser.parse("call John Smith")
        assertEquals("john smith", (q as VoiceQueryParser.Query.Name).text)
    }

    // ── Числа словами ──────────────────────────────────────────────────────

    @Test
    fun `простые числительные собираются в цифры`() {
        val q = VoiceQueryParser.parse("восемь девять ноль ноль один два три")
        assertEquals("8900123", (q as VoiceQueryParser.Query.Number).digits)
    }

    @Test
    fun `составные числительные складываются`() {
        // «девятьсот двадцать три» — одно число 923, а не 900 20 3
        val q = VoiceQueryParser.parse("девятьсот двадцать три")
        assertEquals("923", (q as VoiceQueryParser.Query.Number).digits)
    }

    @Test
    fun `смесь составных и простых разбирается корректно`() {
        val q = VoiceQueryParser.parse("восемь девятьсот шестьдесят семь")
        assertEquals("8967", (q as VoiceQueryParser.Query.Number).digits)
    }

    @Test
    fun `готовые цифры в тексте берутся как есть`() {
        val q = VoiceQueryParser.parse("набери 89001234567")
        assertEquals("89001234567", (q as VoiceQueryParser.Query.Number).digits)
    }

    @Test
    fun `цифры вперемешку со словами объединяются`() {
        val q = VoiceQueryParser.parse("8 900 сто двадцать три")
        assertEquals("8900123", (q as VoiceQueryParser.Query.Number).digits)
    }

    @Test
    fun `команда перед номером отбрасывается`() {
        val q = VoiceQueryParser.parse("позвони по номеру восемь девятьсот")
        assertEquals("8900", (q as VoiceQueryParser.Query.Number).digits)
    }

    // ── Граничные случаи ───────────────────────────────────────────────────

    @Test
    fun `слишком короткая цифровая последовательность считается именем`() {
        // "два" — скорее часть фразы, чем номер
        val q = VoiceQueryParser.parse("два")
        assertTrue(q is VoiceQueryParser.Query.Name)
    }

    @Test
    fun `пустой ввод даёт пустой запрос`() {
        assertTrue(VoiceQueryParser.parse(null) is VoiceQueryParser.Query.Empty)
        assertTrue(VoiceQueryParser.parse("") is VoiceQueryParser.Query.Empty)
        assertTrue(VoiceQueryParser.parse("   ") is VoiceQueryParser.Query.Empty)
    }

    @Test
    fun `только командное слово даёт пустой запрос`() {
        assertTrue(VoiceQueryParser.parse("позвони") is VoiceQueryParser.Query.Empty)
    }

    @Test
    fun `знаки препинания не мешают разбору`() {
        val q = VoiceQueryParser.parse("позвони, Андрею.")
        assertEquals("андрею", (q as VoiceQueryParser.Query.Name).text)
    }

    // ── Проверка длины номера ──────────────────────────────────────────────

    @Test
    fun `полный российский номер опознаётся как телефонный`() {
        assertTrue(VoiceQueryParser.looksLikePhoneNumber("89001234567"))
        assertTrue(VoiceQueryParser.looksLikePhoneNumber("9001234567"))
    }

    @Test
    fun `короткий сервисный номер опознаётся`() {
        assertTrue(VoiceQueryParser.looksLikePhoneNumber("112"))
        assertTrue(VoiceQueryParser.looksLikePhoneNumber("0900"))
    }

    @Test
    fun `случайная длина не считается номером`() {
        assertTrue(!VoiceQueryParser.looksLikePhoneNumber("12345678"))
    }
}
