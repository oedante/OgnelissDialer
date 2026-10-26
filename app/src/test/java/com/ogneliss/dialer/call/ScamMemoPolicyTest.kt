package com.ogneliss.dialer.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Памятка о мошенниках (1.8.0): когда показывать. Разбор — в ScamMemo.kt. */
class ScamMemoPolicyTest {

    private val minute = 60_000L

    @Test
    fun незнакомыйДолгийРазговорПолучаетПамятку() {
        // Соединились минуту назад — ждать ещё минуту
        assertEquals(minute, ScamMemoPolicy.delayMs(true, true, true, false, connectedAt = 0L + 1, now = minute + 1))
        // Уже дольше двух минут — сразу
        assertEquals(0L, ScamMemoPolicy.delayMs(true, true, true, false, connectedAt = 1L, now = 5 * minute))
    }

    @Test
    fun безПоводаПамяткиНет() {
        // Выключена в настройках
        assertNull(ScamMemoPolicy.delayMs(false, true, true, false, 1L, 5 * minute))
        // Разговор не идёт
        assertNull(ScamMemoPolicy.delayMs(true, false, true, false, 1L, 5 * minute))
        // Номер из книги
        assertNull(ScamMemoPolicy.delayMs(true, true, false, false, 1L, 5 * minute))
        // Уже показана в этом разговоре
        assertNull(ScamMemoPolicy.delayMs(true, true, true, true, 1L, 5 * minute))
        // Ещё не соединились
        assertNull(ScamMemoPolicy.delayMs(true, true, true, false, 0L, 5 * minute))
    }

    @Test
    fun порогДвеМинуты() {
        assertEquals(2 * minute, ScamMemoPolicy.THRESHOLD_MS)
    }
}
