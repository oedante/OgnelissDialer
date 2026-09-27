package com.ogneliss.dialer.call

import com.ogneliss.dialer.data.CallType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ РЕШЕНИЯ О ПОКАЗЕ ЭКРАНА ПОСЛЕ ЗВОНКА.
 *
 * ── Почему тут столько условий ─────────────────────────────────────────────
 * Экран после звонка одновременно и в списке желаемых функций, и в списке
 * раздражителей: всплывающее окно после каждого разговора с родными
 * бесит быстрее, чем радует. Отсюда четыре независимые проверки,
 * и любая ошибка в них сразу заметна пользователю — либо экран
 * не появляется, когда нужен, либо лезет, когда не звали.
 */
class PostCallCoordinatorTest {

    private val coordinator = PostCallCoordinator()

    private fun call(
        durationSec: Long = 60,
        completedAt: Long = System.currentTimeMillis()
    ) = PostCallCoordinator.CompletedCall(
        number = "+79001234567",
        displayName = null,
        durationSec = durationSec,
        type = CallType.INCOMING,
        wasAnswered = durationSec >= 3,
        completedAt = completedAt
    )

    // ── Базовые случаи ─────────────────────────────────────────────────────

    @Test
    fun `состоявшийся разговор с незнакомым номером показывает экран`() {
        assertTrue(
            coordinator.shouldShow(
                call(), enabled = true, unknownOnly = true, isKnownContact = false
            )
        )
    }

    @Test
    fun `выключенная настройка отменяет показ`() {
        assertFalse(
            coordinator.shouldShow(
                call(), enabled = false, unknownOnly = true, isKnownContact = false
            )
        )
    }

    @Test
    fun `отсутствие события не показывает ничего`() {
        assertFalse(
            coordinator.shouldShow(
                null, enabled = true, unknownOnly = false, isKnownContact = false
            )
        )
    }

    // ── Знакомые номера ────────────────────────────────────────────────────

    @Test
    fun `знакомый номер не показывает экран при опции только незнакомые`() {
        // Разговор с женой не должен каждый раз просить заметку
        assertFalse(
            coordinator.shouldShow(
                call(), enabled = true, unknownOnly = true, isKnownContact = true
            )
        )
    }

    @Test
    fun `знакомый номер показывает экран если опция выключена`() {
        assertTrue(
            coordinator.shouldShow(
                call(), enabled = true, unknownOnly = false, isKnownContact = true
            )
        )
    }

    // ── Несостоявшийся разговор ────────────────────────────────────────────

    @Test
    fun `пропущенный вызов не показывает экран`() {
        // Писать заметку о разговоре, которого не было, не о чем
        assertFalse(
            coordinator.shouldShow(
                call(durationSec = 0), enabled = true,
                unknownOnly = false, isKnownContact = false
            )
        )
    }

    @Test
    fun `слишком короткий разговор не показывает экран`() {
        // Две секунды — никто не успел ничего сказать
        assertFalse(
            coordinator.shouldShow(
                call(durationSec = 2), enabled = true,
                unknownOnly = false, isKnownContact = false
            )
        )
    }

    @Test
    fun `три секунды уже считаются разговором`() {
        assertTrue(
            coordinator.shouldShow(
                call(durationSec = 3), enabled = true,
                unknownOnly = false, isKnownContact = false
            )
        )
    }

    // ── Срок годности события ──────────────────────────────────────────────

    @Test
    fun `устаревшее событие не показывает экран`() {
        // Пользователь открыл приложение через час после разговора —
        // предложение сделать заметку выглядело бы нелепо
        val old = call(completedAt = System.currentTimeMillis() - 60 * 60 * 1000)
        assertTrue("Событие должно считаться протухшим", old.isExpired)
        assertFalse(
            coordinator.shouldShow(
                old, enabled = true, unknownOnly = false, isKnownContact = false
            )
        )
    }

    @Test
    fun `свежее событие не считается устаревшим`() {
        assertFalse(call().isExpired)
    }

    // ── Жизненный цикл события ─────────────────────────────────────────────

    @Test
    fun `завершение вызова публикует событие`() {
        coordinator.onCallCompleted("+79001234567", "Андрей", 30, CallType.INCOMING)
        assertNotNull(coordinator.pending.value)
    }

    @Test
    fun `пустой номер не публикует событие`() {
        // Скрытый номер: заметку писать не к чему, ключа для неё нет
        coordinator.onCallCompleted(null, null, 30, CallType.INCOMING)
        assertNull(coordinator.pending.value)
    }

    @Test
    fun `обработка события очищает его`() {
        coordinator.onCallCompleted("+79001234567", null, 30, CallType.INCOMING)
        coordinator.consume()
        assertNull(coordinator.pending.value)
    }

    @Test
    fun `новый вызов заменяет предыдущее необработанное событие`() {
        coordinator.onCallCompleted("+79001111111", null, 30, CallType.INCOMING)
        coordinator.onCallCompleted("+79002222222", null, 30, CallType.OUTGOING)
        // Показываем последний разговор, а не копим очередь окон
        assertTrue(coordinator.pending.value?.number == "+79002222222")
    }
}
