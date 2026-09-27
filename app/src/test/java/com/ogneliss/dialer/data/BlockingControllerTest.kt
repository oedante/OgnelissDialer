package com.ogneliss.dialer.data

import com.ogneliss.dialer.fake.FakeRulesSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ЕДИНОЙ ТОЧКИ БЛОКИРОВКИ.
 *
 * Блокировка складывается из трёх источников — правил, системного списка
 * и меток «спам». Разбросанные по коду, они однажды привели к тому,
 * что свайп «Заблокировать» писал в четвёртое, давно мёртвое хранилище,
 * и номер нигде не появлялся.
 *
 * Эти тесты фиксируют, что все пути ведут в одно место.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockingControllerTest {

    private fun controller(rules: FakeRulesSource = FakeRulesSource()) =
        BlockingController(rules) to rules

    private fun group(number: String) = CallLogGroup(
        key = number, lastCallId = 1, number = number, e164 = number,
        displayName = null, photoUri = null, contactId = null,
        lastType = CallType.INCOMING, lastDate = 0, durationSec = 0,
        count = 1, incomingCount = 1, outgoingCount = 0, missedCount = 0,
        rejectedCount = 0, totalDurationSec = 0, numbers = listOf(number),
        subscriptionLabel = null, subscriptionId = null
    )

    @Test
    fun `блокировка добавляет правило`() = runTest {
        val (controller, rules) = controller()
        assertTrue(controller.block("+79001234567"))

        assertEquals(1, rules.current().size)
        assertTrue(rules.current().first().isBlock)
    }

    @Test
    fun `пустой номер не блокируется`() = runTest {
        // Правило по пустой строке совпало бы со всем подряд.
        // Скрытые вызовы отсекаются отдельной настройкой
        val (controller, rules) = controller()

        assertFalse(controller.block(""))
        assertTrue("Правило создаваться не должно", rules.current().isEmpty())
    }

    @Test
    fun `массовая блокировка возвращает число успешных`() = runTest {
        val (controller, rules) = controller()
        val blocked = controller.blockAll(
            listOf(group("+79001111111"), group("+79002222222"), group(""))
        )

        assertEquals("Пустой номер не считается", 2, blocked)
        assertEquals(2, rules.current().size)
    }

    @Test
    fun `повторная блокировка не плодит дубли`() = runTest {
        val (controller, rules) = controller()
        controller.block("+79001234567")
        controller.block("+79001234567", comment = "ещё раз")

        assertEquals(1, rules.current().size)
    }

    // ── Приоритет белого списка ────────────────────────────────────────────

    @Test
    fun `разрешение отменяет блокировку без отдельного снятия`() = runTest {
        // Белый список приоритетнее чёрного. Заставлять делать два
        // действия вместо одного было бы источником путаницы
        val (controller, _) = controller()
        controller.block("+79001234567")
        controller.allow("+79001234567")

        assertFalse(
            "Номер должен считаться разрешённым",
            controller.isBlocked("+79001234567")
        )
    }

    @Test
    fun `маска блокирует диапазон номеров`() = runTest {
        val (controller, _) = controller()
        controller.block("+7800*")

        assertTrue(controller.isBlocked("+78005553535"))
        assertFalse(controller.isBlocked("+79001234567"))
    }

    // ── Отметки о спаме ─────────────────────────────────────────────────────

    @Test
    fun `в фильтр «Спам» попадают все отмеченные номера`() {
        // С 1.1.1 отметки живут отдельно от заметок: и спам, и мошенники
        // попадают в фильтр, а заметки на него не влияют вовсе
        val (controller, _) = controller()
        val marks = mapOf(
            "+7900A" to SpamMark("+7900A", SpamMarkType.SPAM, 1L),
            "+7900C" to SpamMark("+7900C", SpamMarkType.FRAUD, 2L)
        )

        assertEquals(setOf("+7900A", "+7900C"), controller.spamNumbers(marks))
    }

    @Test
    fun `без отметок фильтр «Спам» пуст`() {
        val (controller, _) = controller()
        assertTrue(controller.spamNumbers(emptyMap()).isEmpty())
    }

    @Test
    fun `удаление правил работает пачкой`() = runTest {
        val (controller, rules) = controller()
        controller.block("+7900A")
        controller.block("+7900B")
        controller.block("+7900C")

        controller.removeRules(listOf("+7900A", "+7900C"))

        assertEquals(1, rules.current().size)
        assertEquals("+7900B", rules.current().first().pattern)
    }
}
