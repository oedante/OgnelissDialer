package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ РАСКЛАДКИ КНОПОК ВЫЗОВА.
 *
 * «Две SIM» — не одно состояние, а несколько разных, и наивная проверка
 * «карт больше одной → две кнопки» ошибается в каждом из них.
 *
 * Проверить это вручную означало бы держать под рукой телефоны со всеми
 * сочетаниями: два слота с одной картой, отключённая карта, карта только
 * для интернета, eSIM плюс физическая.
 */
class SimCallPlanTest {

    private fun sim(subId: Int, slot: Int, label: String = "SIM ${slot + 1}") =
        SimSlot(subscriptionId = subId, slotIndex = slot, label = label, number = null)

    // ── Одна кнопка ────────────────────────────────────────────────────────

    @Test
    fun `телефон без SIM показывает одну кнопку`() {
        // Выбор делает система: лучше позвонить с карты по умолчанию,
        // чем не позвонить вовсе
        assertEquals(
            SimCallPlan.Layout.Single,
            SimCallPlan.decide(emptyList(), emptySet())
        )
    }

    @Test
    fun `одна карта показывает одну кнопку без номера слота`() {
        val sims = listOf(sim(1, 0))
        assertEquals(
            SimCallPlan.Layout.Single,
            SimCallPlan.decide(sims, setOf(1))
        )
    }

    @Test
    fun `список недоступен из-за разрешения — одна кнопка`() {
        // Без READ_PHONE_STATE список пуст, но звонить пользователь
        // должен иметь возможность
        assertEquals(
            SimCallPlan.Layout.Single,
            SimCallPlan.decide(emptyList(), setOf(1, 2))
        )
    }

    // ── Две кнопки ─────────────────────────────────────────────────────────

    @Test
    fun `две голосовые карты дают две кнопки`() {
        val sims = listOf(sim(1, 0), sim(2, 1))
        val layout = SimCallPlan.decide(sims, setOf(1, 2))

        assertTrue(layout is SimCallPlan.Layout.PerSim)
        assertEquals(2, (layout as SimCallPlan.Layout.PerSim).sims.size)
    }

    // ── Ключевые случаи, которые ломали наивную проверку ───────────────────

    @Test
    fun `карта только для интернета не получает кнопку`() {
        // Тариф для данных и eSIM-профили операторов часто без голоса.
        // Подписка активна, в списке есть — но звонок с неё не пройдёт
        val sims = listOf(sim(1, 0), sim(2, 1))
        val layout = SimCallPlan.decide(sims, voiceCapableSubscriptionIds = setOf(1))

        assertEquals(
            "Кнопка обещала бы то, чего не будет",
            SimCallPlan.Layout.Single, layout
        )
    }

    @Test
    fun `единственная активная карта во втором слоте сохраняет свой слот`() {
        // Два слота, карта только во втором. Позиция в списке 0,
        // а слот 1 — если перепутать, звонок уйдёт не с той карты
        val sims = listOf(sim(subId = 5, slot = 1))
        val callable = SimCallPlan.callableSims(sims, setOf(5))

        assertEquals(1, callable.first().slotIndex)
    }

    @Test
    fun `отключённой карты нет в списке и она не влияет на раскладку`() {
        // Система не отдаёт отключённые подписки: до нас доходит
        // только активная
        val sims = listOf(sim(1, 0))
        assertEquals(
            SimCallPlan.Layout.Single,
            SimCallPlan.decide(sims, setOf(1))
        )
    }

    // ── Три и более карт ───────────────────────────────────────────────────

    @Test
    fun `три голосовые карты не помещаются в ряд`() {
        // Физическая плюс два eSIM-профиля: кнопки стали бы уже 100 dp,
        // что ниже комфортного порога для главного действия
        val sims = listOf(sim(1, 0), sim(2, 1), sim(3, 2))
        assertEquals(
            SimCallPlan.Layout.Single,
            SimCallPlan.decide(sims, setOf(1, 2, 3))
        )
    }

    @Test
    fun `три карты требуют отдельного выбора`() {
        val sims = listOf(sim(1, 0), sim(2, 1), sim(3, 2))
        assertTrue(SimCallPlan.requiresSimPicker(sims, setOf(1, 2, 3)))
    }

    @Test
    fun `две карты отдельного выбора не требуют`() {
        val sims = listOf(sim(1, 0), sim(2, 1))
        assertFalse(SimCallPlan.requiresSimPicker(sims, setOf(1, 2)))
    }

    // ── Фильтрация ─────────────────────────────────────────────────────────

    @Test
    fun `в список попадают только голосовые карты`() {
        val sims = listOf(sim(1, 0), sim(2, 1), sim(3, 2))
        val callable = SimCallPlan.callableSims(sims, setOf(1, 3))

        assertEquals(2, callable.size)
        assertEquals(listOf(1, 3), callable.map { it.subscriptionId })
    }

    @Test
    fun `пустой набор голосовых карт даёт пустой список`() {
        val sims = listOf(sim(1, 0), sim(2, 1))
        assertTrue(SimCallPlan.callableSims(sims, emptySet()).isEmpty())
    }

    @Test
    fun `предел кнопок в ряд равен двум`() {
        // Фиксируем константу: увеличение потребует пересмотра ширины
        assertEquals(2, SimCallPlan.MAX_INLINE_BUTTONS)
    }
}
