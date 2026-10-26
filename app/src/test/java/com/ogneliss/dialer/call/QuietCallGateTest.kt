package com.ogneliss.dialer.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Метка «этот входящий — тихий» и пропущенный после тихого (1.8.0).
 * Разбор — QuietCallGate в QuietHours.kt. Срок метки и то, что она
 * забирается один раз, проверяет SpamPolicyTest.
 *
 * Объект общий на процесс: у каждого теста свои номера, а отметку
 * о последнем входящем тест ставит сам.
 */
class QuietCallGateTest {

    private val minute = 60_000L

    @Test
    fun проверкаСнимаетМеткуСОбычногоЗвонка() {
        // Тихий звонок оборвался до службы вызовов — метка осталась
        QuietCallGate.mark("+70000000103", now = 1_000L)
        // Перезвонил: проверка решила «со звуком» и сняла её
        QuietCallGate.clear("+70000000103")
        assertFalse(QuietCallGate.take("+70000000103", now = 5_000L))
    }

    @Test
    fun меткиРазныхНомеровНеПутаются() {
        QuietCallGate.mark("+70000000104", now = 1_000L)
        assertFalse(QuietCallGate.take("+70000000105", now = 2_000L))
        assertTrue(QuietCallGate.take("+70000000104", now = 2_000L))
    }

    @Test
    fun пропущенныйПослеТихогоБезЗвука() {
        QuietCallGate.noteIncoming(quiet = true, now = 10 * minute)
        assertTrue(QuietCallGate.missedIsQuiet(now = 11 * minute))
        // Позже окна — это уже не про тот звонок
        assertFalse(QuietCallGate.missedIsQuiet(now = 10 * minute + QuietCallGate.MISSED_WINDOW_MS + 1))
    }

    @Test
    fun обычныйВходящийВозвращаетЗвук() {
        QuietCallGate.noteIncoming(quiet = true, now = 10 * minute)
        // Перезвонил за 15 минут — звонок со звуком, и пропущенный тоже
        QuietCallGate.noteIncoming(quiet = false, now = 12 * minute)
        assertFalse(QuietCallGate.missedIsQuiet(now = 13 * minute))
    }
}
