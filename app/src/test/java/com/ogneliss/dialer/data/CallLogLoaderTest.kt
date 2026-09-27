package com.ogneliss.dialer.data

import com.ogneliss.dialer.fake.FakeCallLogSource
import com.ogneliss.dialer.fake.FakeGroupingSettings
import com.ogneliss.dialer.fake.FakeSimInfoSource
import com.ogneliss.dialer.settings.GroupingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ПОСТРАНИЧНОЙ ЗАГРУЗКИ.
 *
 * Первые тесты слоя, связывающего данные с интерфейсом. До появления
 * интерфейсов источников проверить его было нельзя: загрузчик зависел
 * от репозитория, привязанного к системным провайдерам Android.
 *
 * Именно в этом слое находилась половина ошибок проекта — включая
 * лишнее перечитывание журнала при смене группировки.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallLogLoaderTest {

    private var nextId = 1L
    private val now = 1_700_000_000_000L

    private fun entry(
        number: String = "+79001112233",
        date: Long = now,
        contactId: Long? = null
    ) = CallLogEntry(
        id = nextId++, number = number, e164 = number, cachedName = null,
        photoUri = null, type = CallType.INCOMING, date = date,
        durationSec = 10, phoneAccountId = "sim1", contactId = contactId
    )

    private fun entries(count: Int, number: String = "+79001112233") =
        (0 until count).map { entry(number = number, date = now - it * 1000L) }

    private fun loader(
        source: FakeCallLogSource,
        sims: FakeSimInfoSource = FakeSimInfoSource(),
        settings: FakeGroupingSettings = FakeGroupingSettings()
    ) = CallLogLoader(
        source, sims, settings,
        // В тестах группировка идёт в том же потоке: ждать переключения
        // диспетчера ради проверки логики незачем
        UnconfinedTestDispatcher()
    )

    // ── Первая страница ────────────────────────────────────────────────────

    @Test
    fun `первая страница загружается и группируется`() = runTest {
        val source = FakeCallLogSource(entries(5))
        val groups = loader(source).loadFirstPage()

        assertEquals("Подряд идущие вызовы одного номера — одна карточка", 1, groups.size)
        assertEquals(5, groups.first().count)
    }

    @Test
    fun `пустой журнал не ломает загрузку`() = runTest {
        val groups = loader(FakeCallLogSource()).loadFirstPage()
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `повторная загрузка первой страницы сбрасывает накопленное`() = runTest {
        // Режим группировки мог измениться, и старый набор больше
        // не соответствует тому, что нужно показать
        val source = FakeCallLogSource(entries(5))
        val loader = loader(source)

        loader.loadFirstPage()
        val second = loader.loadFirstPage()

        assertEquals("Записи не должны удвоиться", 5, second.first().count)
    }

    // ── Подгрузка ──────────────────────────────────────────────────────────

    @Test
    fun `подгрузка запрашивает следующее смещение`() = runTest {
        val source = FakeCallLogSource(entries(CallLogPager.DEFAULT_PAGE_SIZE + 10))
        val loader = loader(source)

        loader.loadFirstPage()
        loader.loadNextPage()

        assertEquals(
            "Смещение должно равняться числу уже загруженных записей",
            CallLogPager.DEFAULT_PAGE_SIZE, source.lastRequestedOffset
        )
    }

    @Test
    fun `подгрузка возвращает null когда добавить нечего`() = runTest {
        // Тогда вызывающему коду не нужно трогать состояние интерфейса
        val source = FakeCallLogSource(entries(5))
        val loader = loader(source)

        loader.loadFirstPage()
        assertNull(loader.loadNextPage())
    }

    @Test
    fun `конец журнала прекращает подгрузку`() = runTest {
        val source = FakeCallLogSource(entries(5))
        val loader = loader(source)

        loader.loadFirstPage()
        assertFalse("Неполная страница означает конец", loader.canLoadMore)
    }

    @Test
    fun `подгруженные записи попадают в общий список`() = runTest {
        val total = CallLogPager.DEFAULT_PAGE_SIZE + 20
        val source = FakeCallLogSource(entries(total))
        val loader = loader(source)

        loader.loadFirstPage()
        val groups = loader.loadNextPage()

        assertNotNull(groups)
        assertEquals("Все записи одного номера склеились", total, groups!!.first().count)
    }

    // ── Перегруппировка без чтения ─────────────────────────────────────────

    @Test
    fun `смена группировки не обращается к провайдеру повторно`() = runTest {
        // Записи уже в памяти: меняется лишь способ их объединения.
        // Лишний запрос к провайдеру был реальной проблемой до вынесения
        // загрузчика в отдельный класс
        val source = FakeCallLogSource(entries(5))
        val settings = FakeGroupingSettings(GroupingMode.CONSECUTIVE)
        val loader = loader(source, settings = settings)

        loader.loadFirstPage()
        val callsAfterLoad = source.loadCallCount

        settings.setMode(GroupingMode.BY_CONTACT)
        loader.regroup()

        assertEquals(
            "Перегруппировка не должна читать журнал заново",
            callsAfterLoad, source.loadCallCount
        )
    }

    @Test
    fun `режим без группировки даёт карточку на каждый вызов`() = runTest {
        val source = FakeCallLogSource(entries(5))
        val settings = FakeGroupingSettings(GroupingMode.NONE)
        val groups = loader(source, settings = settings).loadFirstPage()

        assertEquals(5, groups.size)
    }

    // ── Разделение по SIM ──────────────────────────────────────────────────

    @Test
    fun `при одной карте разделения по SIM нет`() = runTest {
        val mixed = listOf(
            entry(date = now).copy(phoneAccountId = "sim1"),
            entry(date = now - 1000).copy(phoneAccountId = "sim2")
        )
        val source = FakeCallLogSource(mixed)
        val sims = FakeSimInfoSource(
            listOf(SimSlot(subscriptionId = 1, slotIndex = 0, label = "SIM 1", number = null))
        )

        val groups = loader(source, sims = sims).loadFirstPage()

        assertEquals(
            "С одной картой бейдж SIM не несёт информации, дробить журнал незачем",
            1, groups.size
        )
    }

    @Test
    fun `при двух картах вызовы с разных SIM разделяются`() = runTest {
        val mixed = listOf(
            entry(date = now).copy(phoneAccountId = "sim1"),
            entry(date = now - 1000).copy(phoneAccountId = "sim2")
        )
        val source = FakeCallLogSource(mixed)
        val sims = FakeSimInfoSource(
            listOf(
                SimSlot(subscriptionId = 1, slotIndex = 0, label = "SIM 1", number = null),
                SimSlot(subscriptionId = 2, slotIndex = 1, label = "SIM 2", number = null)
            )
        )

        val groups = loader(source, sims = sims).loadFirstPage()

        assertEquals("С какой карты шёл разговор — вопрос денег", 2, groups.size)
    }
}
