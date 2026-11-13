package com.ogneliss.dialer.call

import com.ogneliss.dialer.call.SmartSpeakerPolicy.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ АВТО-ГРОМКОЙ СВЯЗИ.
 *
 * Проверять вручную означало бы звонить кому-то и класть телефон
 * на стол в разных позах, каждый раз заново.
 *
 * Главное, что здесь фиксируется, — случаи, когда функция обязана
 * МОЛЧАТЬ. Ошибка в них хуже отсутствия функции: внезапно заоравший
 * динамик на совещании или спор с пользователем, который сам
 * переключил звук.
 */
class SmartSpeakerPolicyTest {

    private fun state(
        accelerationZ: Float = 9.8f,
        proximityNear: Boolean = false,
        stableForMs: Long = 1000,
        speakerOn: Boolean = false,
        headsetConnected: Boolean = false,
        manualOverride: Boolean = false,
        callActive: Boolean = true
    ) = SmartSpeakerPolicy.SensorState(
        accelerationZ, proximityNear, stableForMs,
        speakerOn, headsetConnected, manualOverride, callActive
    )

    // ── Основной сценарий ──────────────────────────────────────────────────

    @Test
    fun `телефон положили на стол — включается динамик`() {
        assertEquals(Route.SPEAKER, SmartSpeakerPolicy.decide(state()))
    }

    @Test
    fun `телефон подняли — возвращается разговорный динамик`() {
        assertEquals(
            Route.EARPIECE,
            SmartSpeakerPolicy.decide(state(accelerationZ = 1.0f, speakerOn = true))
        )
    }

    @Test
    fun `телефон у уха — разговорный динамик`() {
        assertEquals(
            Route.EARPIECE,
            SmartSpeakerPolicy.decide(state(proximityNear = true, speakerOn = true))
        )
    }

    @Test
    fun `телефон уже лежит и динамик включён — ничего не меняем`() {
        assertEquals(Route.UNCHANGED, SmartSpeakerPolicy.decide(state(speakerOn = true)))
    }

    // ── Когда функция обязана молчать ──────────────────────────────────────

    @Test
    fun `гарнитура отменяет автоматику`() {
        // Звук уже идёт куда надо, вмешиваться нельзя
        assertEquals(
            Route.UNCHANGED,
            SmartSpeakerPolicy.decide(state(headsetConnected = true))
        )
    }

    @Test
    fun `ручное переключение отменяет автоматику до конца вызова`() {
        // Иначе приложение спорит с пользователем: он включил громкую связь,
        // поднёс телефон к уху что-то сказать — и связь выключилась
        assertEquals(
            Route.UNCHANGED,
            SmartSpeakerPolicy.decide(state(manualOverride = true))
        )
    }

    @Test
    fun `до соединения переключать нечего`() {
        assertEquals(Route.UNCHANGED, SmartSpeakerPolicy.decide(state(callActive = false)))
    }

    @Test
    fun `неустоявшееся состояние не вызывает переключения`() {
        // Защита от дребезга: рука дрогнула, датчик мигнул
        assertEquals(Route.UNCHANGED, SmartSpeakerPolicy.decide(state(stableForMs = 100)))
    }

    // ── Гистерезис ─────────────────────────────────────────────────────────

    @Test
    fun `промежуточный наклон не переключает ничего`() {
        // Между порогами включения и выключения — мёртвая зона.
        // Без неё звук скакал бы между динамиками при малейшем движении
        val middle = (SmartSpeakerPolicy.FLAT_THRESHOLD +
            SmartSpeakerPolicy.UPRIGHT_THRESHOLD) / 2

        assertEquals(
            Route.UNCHANGED,
            SmartSpeakerPolicy.decide(state(accelerationZ = middle, speakerOn = false))
        )
        assertEquals(
            Route.UNCHANGED,
            SmartSpeakerPolicy.decide(state(accelerationZ = middle, speakerOn = true))
        )
    }

    @Test
    fun `порог выключения строго ниже порога включения`() {
        // Иначе гистерезиса нет и переключение начнёт дребезжать
        assertTrue(
            SmartSpeakerPolicy.UPRIGHT_THRESHOLD < SmartSpeakerPolicy.FLAT_THRESHOLD
        )
    }

    @Test
    fun `телефон экраном вниз не включает динамик`() {
        // z около -9.8: телефон перевёрнут, класть его так специально
        // для громкой связи никто не станет
        assertEquals(
            Route.UNCHANGED,
            SmartSpeakerPolicy.decide(state(accelerationZ = -9.8f, speakerOn = false))
        )
    }

    // ── Экономия батареи ───────────────────────────────────────────────────

    @Test
    fun `при выключенной функции датчики не слушаем`() {
        assertFalse(SmartSpeakerPolicy.shouldObserveSensors(false, false))
    }

    @Test
    fun `с гарнитурой датчики не слушаем`() {
        // Разговор может длиться час, лишний расход ни к чему
        assertFalse(SmartSpeakerPolicy.shouldObserveSensors(true, true))
    }

    @Test
    fun `при включённой функции без гарнитуры датчики нужны`() {
        assertTrue(SmartSpeakerPolicy.shouldObserveSensors(true, false))
    }

    // ── «Близко» — как у самого Android (2.4.4) ────────────────────────────

    @Test
    fun `датчик с максимумом 5 см — 0 близко, 5 далеко`() {
        assertTrue(SmartSpeakerPolicy.isNear(0f, 5f))
        assertFalse(SmartSpeakerPolicy.isNear(5f, 5f))
    }

    @Test
    fun `датчик с максимумом больше 5 см — «далеко» 5 см не считается близко`() {
        // Было: 5 < 8 — «близко» всегда, авто-динамик не включался
        assertFalse(SmartSpeakerPolicy.isNear(5f, 8f))
        assertFalse(SmartSpeakerPolicy.isNear(8f, 8f))
        assertTrue(SmartSpeakerPolicy.isNear(0f, 8f))
        assertTrue(SmartSpeakerPolicy.isNear(3f, 10f))
    }

    @Test
    fun `датчик с максимумом 1 см — близко только 0`() {
        assertTrue(SmartSpeakerPolicy.isNear(0f, 1f))
        assertFalse(SmartSpeakerPolicy.isNear(1f, 1f))
        // Отрицательное значение — сбой датчика, не «близко»
        assertFalse(SmartSpeakerPolicy.isNear(-1f, 5f))
    }
}
