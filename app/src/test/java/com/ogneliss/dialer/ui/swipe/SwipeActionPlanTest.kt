package com.ogneliss.dialer.ui.swipe

import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.ui.components.swipe.SwipeActionPlan
import com.ogneliss.dialer.ui.components.swipe.SwipeActionPlan.Behaviour
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ТЕСТЫ ПОВЕДЕНИЯ СВАЙПА.
 *
 * Правило «сколько номеров — такое поведение» неочевидно и зависит сразу
 * от трёх величин: действия, числа номеров и числа SIM. Ошибка приведёт
 * либо к панели из шести кнопок под пальцем, либо к лишней шторке там,
 * где хватило бы прямого действия.
 */
class SwipeActionPlanTest {

    // ── Прямое действие ────────────────────────────────────────────────────

    @Test
    fun `один номер и одна SIM дают прямое действие`() {
        assertEquals(
            Behaviour.DIRECT,
            SwipeActionPlan.decide(SwipeAction.CALL, numberCount = 1, simCount = 1)
        )
    }

    @Test
    fun `действия не зависящие от номера всегда прямые`() {
        // Удалять запись или открывать карточку по номерам незачем
        listOf(
            SwipeAction.DELETE_FROM_LOG,
            SwipeAction.BLOCK_NUMBER,
            SwipeAction.VIEW_CONTACT,
            SwipeAction.REMIND_CALLBACK
        ).forEach { action ->
            assertEquals(
                "Действие $action не должно требовать выбора номера",
                Behaviour.DIRECT,
                SwipeActionPlan.decide(action, numberCount = 5, simCount = 2)
            )
        }
    }

    @Test
    fun `выключенное действие отключает свайп`() {
        assertEquals(
            Behaviour.DISABLED,
            SwipeActionPlan.decide(SwipeAction.NONE, numberCount = 3, simCount = 2)
        )
    }

    // ── Панель ─────────────────────────────────────────────────────────────

    @Test
    fun `два номера и одна SIM раскрывают панель`() {
        assertEquals(
            Behaviour.REVEAL,
            SwipeActionPlan.decide(SwipeAction.CALL, numberCount = 2, simCount = 1)
        )
    }

    @Test
    fun `три номера и одна SIM ещё помещаются в панель`() {
        assertEquals(
            Behaviour.REVEAL,
            SwipeActionPlan.decide(SwipeAction.CALL, numberCount = 3, simCount = 1)
        )
    }

    @Test
    fun `один номер и две SIM дают панель из двух кнопок`() {
        // Выбор карты без промежуточного диалога
        assertEquals(
            Behaviour.REVEAL,
            SwipeActionPlan.decide(SwipeAction.CALL, numberCount = 1, simCount = 2)
        )
    }

    // ── Шторка ─────────────────────────────────────────────────────────────

    @Test
    fun `четыре номера уходят в шторку`() {
        assertEquals(
            Behaviour.SHEET,
            SwipeActionPlan.decide(SwipeAction.CALL, numberCount = 4, simCount = 1)
        )
    }

    @Test
    fun `два номера и две SIM дают четыре цели и уходят в шторку`() {
        // Это тот случай, ради которого считается произведение:
        // 2 × 2 = 4 кнопки в панель уже не помещаются
        assertEquals(
            Behaviour.SHEET,
            SwipeActionPlan.decide(SwipeAction.CALL, numberCount = 2, simCount = 2)
        )
    }

    @Test
    fun `три номера с двумя SIM тем более в шторку`() {
        assertEquals(
            Behaviour.SHEET,
            SwipeActionPlan.decide(SwipeAction.CALL, numberCount = 3, simCount = 2)
        )
    }

    // ── SMS не умножается на SIM ───────────────────────────────────────────

    @Test
    fun `SMS не зависит от числа SIM`() {
        // SMS уходит в системное приложение, выбор карты делает оно само
        assertEquals(
            Behaviour.REVEAL,
            SwipeActionPlan.decide(SwipeAction.SMS, numberCount = 3, simCount = 2)
        )
    }

    @Test
    fun `мессенджер не зависит от числа SIM`() {
        assertEquals(
            Behaviour.REVEAL,
            SwipeActionPlan.decide(SwipeAction.WHATSAPP, numberCount = 2, simCount = 2)
        )
    }

    // ── Подсчёт целей ──────────────────────────────────────────────────────

    @Test
    fun `число целей для вызова умножается на число SIM`() {
        assertEquals(4, SwipeActionPlan.targetCount(SwipeAction.CALL, 2, 2))
        assertEquals(2, SwipeActionPlan.targetCount(SwipeAction.CALL, 2, 1))
        assertEquals(6, SwipeActionPlan.targetCount(SwipeAction.CALL, 3, 2))
    }

    @Test
    fun `ноль номеров трактуется как один`() {
        // Защита от пустого списка: свайп не должен ломаться
        assertEquals(1, SwipeActionPlan.targetCount(SwipeAction.CALL, 0, 1))
        assertEquals(
            Behaviour.DIRECT,
            SwipeActionPlan.decide(SwipeAction.CALL, numberCount = 0, simCount = 1)
        )
    }

    @Test
    fun `предел панели равен трём кнопкам`() {
        // Фиксируем константу: увеличение потребует пересмотра ширины кнопок
        assertEquals(3, SwipeActionPlan.MAX_REVEAL_ACTIONS)
    }
}
