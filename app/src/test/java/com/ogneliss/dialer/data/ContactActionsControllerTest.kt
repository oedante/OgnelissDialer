package com.ogneliss.dialer.data

import com.ogneliss.dialer.fake.FakeContactEditorLauncher
import com.ogneliss.dialer.fake.FakeContactWriter
import com.ogneliss.dialer.fake.FakeShortcutSink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ДЕЙСТВИЙ НАД КОНТАКТАМИ.
 *
 * Главное здесь — порядок ярлыков. Система показывает не больше четырёх,
 * поэтому важен именно порядок, а не полнота списка: если наверх попадут
 * случайные номера, функция потеряет смысл, но выглядеть будет рабочей.
 *
 * Раньше проверить это можно было только на устройстве с лаунчером.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactActionsControllerTest {

    private fun contact(
        id: Long,
        name: String,
        number: String = "+7900$id",
        starred: Boolean = false
    ) = Contact(
        id = id, lookupKey = "k$id", displayName = name, photoUri = null,
        starred = starred, numbers = listOf(PhoneNumber(number, "Mobile", number))
    )

    private fun group(number: String, name: String?, count: Int) = CallLogGroup(
        key = number, lastCallId = 1, number = number, e164 = number,
        displayName = name, photoUri = null, contactId = null,
        lastType = CallType.INCOMING, lastDate = 0, durationSec = 0,
        count = count, incomingCount = count, outgoingCount = 0,
        missedCount = 0, rejectedCount = 0, totalDurationSec = 0,
        numbers = listOf(number), subscriptionLabel = null, subscriptionId = null
    )

    private fun controller(): Triple<
        ContactActionsController, FakeContactWriter, FakeShortcutSink
        > {
        val writer = FakeContactWriter()
        val sink = FakeShortcutSink()
        return Triple(
            ContactActionsController(writer, FakeContactEditorLauncher(), sink),
            writer, sink
        )
    }

    // ── Порядок ярлыков ────────────────────────────────────────────────────

    @Test
    fun `избранные идут раньше частых`() {
        // Пользователь отметил их сам — это сигнал сильнее статистики
        val (controller, _, sink) = controller()

        controller.updateShortcuts(
            contacts = listOf(contact(1, "Избранный", "+7900A", starred = true)),
            callLog = listOf(group("+7900B", "Частый", count = 100))
        )

        assertEquals("Избранный", sink.lastTargets.first().name)
    }

    @Test
    fun `частые сортируются по числу вызовов`() {
        val (controller, _, sink) = controller()

        controller.updateShortcuts(
            contacts = emptyList(),
            callLog = listOf(
                group("+7900A", "Редкий", count = 1),
                group("+7900B", "Частый", count = 50)
            )
        )

        assertEquals("Частый", sink.lastTargets.first().name)
    }

    @Test
    fun `один человек не попадает в ярлыки дважды`() {
        // Он и в избранном, и в частых: дубль занял бы одно
        // из четырёх мест впустую
        val (controller, _, sink) = controller()

        controller.updateShortcuts(
            contacts = listOf(contact(1, "Любимая", "+7900A", starred = true)),
            callLog = listOf(group("+7900A", "Любимая", count = 100))
        )

        assertEquals(1, sink.lastTargets.size)
    }

    @Test
    fun `записи без имени в ярлыки не попадают`() {
        // Ярлык с голым номером на рабочем столе бесполезен:
        // непонятно, кому он звонит
        val (controller, _, sink) = controller()

        controller.updateShortcuts(
            contacts = emptyList(),
            callLog = listOf(group("+7900A", null, count = 100))
        )

        assertTrue(sink.lastTargets.isEmpty())
    }

    @Test
    fun `контакт без номера пропускается`() {
        val (controller, _, sink) = controller()
        val noNumber = Contact(
            id = 1, lookupKey = "k", displayName = "Только почта",
            photoUri = null, starred = true, numbers = emptyList()
        )

        controller.updateShortcuts(listOf(noNumber), emptyList())

        assertTrue("Звонить некуда — ярлык бессмыслен", sink.lastTargets.isEmpty())
    }

    // ── Быстрые операции ───────────────────────────────────────────────────

    @Test
    fun `переключение избранного инвертирует текущее состояние`() = runTest {
        val (controller, writer, _) = controller()

        controller.toggleStar(contact(1, "Андрей", starred = false))
        assertEquals(true, writer.starred[1])

        controller.toggleStar(contact(1, "Андрей", starred = true))
        assertEquals(false, writer.starred[1])
    }

    @Test
    fun `тихий режим передаётся как есть`() = runTest {
        val (controller, writer, _) = controller()

        controller.setSilent(contactId = 7, silent = true)
        assertEquals(true, writer.silenced[7])
    }

    @Test
    fun `удаление доходит до записи`() = runTest {
        val (controller, writer, _) = controller()

        controller.delete(42)
        assertEquals(listOf(42L), writer.deleted)
    }

    // ── Проверка наличия номера ────────────────────────────────────────────

    @Test
    fun `номер в книге распознаётся независимо от формата`() {
        val (controller, _, _) = controller()
        val contacts = listOf(contact(1, "Андрей", "+79001234567"))

        assertTrue(controller.isKnown(contacts, "+79001234567"))
        assertTrue("Формат записи не должен влиять", controller.isKnown(contacts, "89001234567"))
    }

    @Test
    fun `незнакомый номер не находится`() {
        val (controller, _, _) = controller()
        val contacts = listOf(contact(1, "Андрей", "+79001234567"))

        assertTrue(!controller.isKnown(contacts, "+79009999999"))
    }
}
