package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ПОСТРАНИЧНОЙ ЗАГРУЗКИ.
 *
 * Главное, что здесь проверяется, — корректность группировки НА СТЫКЕ
 * СТРАНИЦ. Это тот случай, ради которого пришлось отказаться от Paging 3:
 * последняя карточка страницы и первая карточка следующей могут оказаться
 * вызовами одного абонента, и их нужно склеить.
 *
 * Наивная постраничная реализация даёт видимый артефакт: «Любимая (3)»
 * и сразу под ней «Любимая (2)» вместо «Любимая (5)». Поймать это вручную
 * трудно — нужна подходящая история звонков ровно на границе страницы.
 */
class CallLogPagerTest {

    private var nextId = 1L

    private fun entry(
        number: String = "+79001112233",
        date: Long,
        type: CallType = CallType.INCOMING,
        contactId: Long? = null
    ) = CallLogEntry(
        id = nextId++,
        number = number,
        e164 = number,
        cachedName = null,
        photoUri = null,
        type = type,
        date = date,
        durationSec = 10,
        phoneAccountId = "sim1",
        contactId = contactId
    )

    private fun page(count: Int, number: String, startDate: Long) =
        (0 until count).map { entry(number = number, date = startDate - it) }

    // ── Главный случай: склейка на границе страниц ─────────────────────────

    @Test
    fun `вызовы одного абонента на стыке страниц склеиваются`() {
        val pager = CallLogPager(pageSize = 3)

        // Страница 1: три вызова от одного номера
        pager.append(page(3, "+79001112233", 1000))
        // Страница 2: ещё два вызова от него же
        pager.append(page(2, "+79001112233", 997))

        val groups = pager.group(
            CallLogGrouper.Mode.CONSECUTIVE, separateBySim = false
        ) { null }

        assertEquals("Должна получиться ОДНА карточка, а не две", 1, groups.size)
        assertEquals(5, groups[0].count)
    }

    @Test
    fun `разные абоненты на стыке остаются раздельными`() {
        val pager = CallLogPager(pageSize = 3)
        pager.append(page(3, "+79001112233", 1000))
        pager.append(page(3, "+79004445566", 997))

        val groups = pager.group(
            CallLogGrouper.Mode.CONSECUTIVE, separateBySim = false
        ) { null }

        assertEquals(2, groups.size)
    }

    @Test
    fun `режим по абоненту собирает вызовы из разных страниц`() {
        val pager = CallLogPager(pageSize = 2)
        // Абонент A попадает в первую и третью страницы
        pager.append(listOf(entry(number = "+7900A", date = 1000), entry(number = "+7900B", date = 999)))
        pager.append(listOf(entry(number = "+7900B", date = 998), entry(number = "+7900B", date = 997)))
        pager.append(listOf(entry(number = "+7900A", date = 996), entry(number = "+7900A", date = 995)))

        val groups = pager.group(
            CallLogGrouper.Mode.BY_CONTACT, separateBySim = false
        ) { null }

        assertEquals(2, groups.size)
        assertEquals(3, groups.first { it.e164 == "+7900A" }.count)
        assertEquals(3, groups.first { it.e164 == "+7900B" }.count)
    }

    // ── Определение конца ──────────────────────────────────────────────────

    @Test
    fun `неполная страница означает конец журнала`() {
        val pager = CallLogPager(pageSize = 10)
        pager.append(page(4, "+79001112233", 1000))

        assertTrue(pager.reachedEnd)
        assertFalse(pager.canLoadMore)
    }

    @Test
    fun `полная страница не считается концом`() {
        val pager = CallLogPager(pageSize = 4)
        pager.append(page(4, "+79001112233", 1000))

        assertFalse(pager.reachedEnd)
        assertTrue(pager.canLoadMore)
    }

    @Test
    fun `пустая страница завершает загрузку`() {
        val pager = CallLogPager(pageSize = 4)
        pager.append(page(4, "+79001112233", 1000))
        assertFalse(pager.append(emptyList()))
        assertTrue(pager.reachedEnd)
    }

    // ── Защита от дублей ───────────────────────────────────────────────────

    @Test
    fun `повторно пришедшие записи не удваивают счётчики`() {
        // Между запросами журнал мог измениться, и смещение «съехало»:
        // провайдер вернёт записи, которые мы уже загрузили
        val pager = CallLogPager(pageSize = 3)
        val first = page(3, "+79001112233", 1000)
        pager.append(first)
        pager.append(first)   // тот же набор повторно

        assertEquals("Дубли должны отсекаться по id", 3, pager.loadedCount)

        val groups = pager.group(
            CallLogGrouper.Mode.CONSECUTIVE, separateBySim = false
        ) { null }
        assertEquals(3, groups[0].count)
    }

    @Test
    fun `частичное пересечение страниц обрабатывается корректно`() {
        val pager = CallLogPager(pageSize = 4)
        val entries = page(4, "+79001112233", 1000)
        pager.append(entries)
        // Вторая страница содержит две последние записи первой и две новые
        pager.append(entries.takeLast(2) + page(2, "+79001112233", 990))

        assertEquals(6, pager.loadedCount)
    }

    // ── Потолок накопления ─────────────────────────────────────────────────

    @Test
    fun `накопление ограничено потолком`() {
        val pager = CallLogPager(pageSize = 10, maxEntries = 25)
        repeat(5) { i -> pager.append(page(10, "+79001112233", 1000L - i * 10)) }

        assertTrue("Потолок должен остановить загрузку", pager.reachedLimit)
        assertFalse(pager.canLoadMore)
    }

    @Test
    fun `у потолка запрашивается только остаток`() {
        val pager = CallLogPager(pageSize = 10, maxEntries = 25)
        pager.append(page(10, "+7900A", 1000))
        pager.append(page(10, "+7900B", 900))

        // Загружено 20 из 25 — следующий запрос должен быть на 5, а не на 10
        assertEquals(5, pager.nextLimit)
    }

    @Test
    fun `смещение равно числу загруженных записей`() {
        val pager = CallLogPager(pageSize = 10)
        pager.append(page(10, "+7900A", 1000))
        assertEquals(10, pager.nextOffset)
    }

    // ── Сброс ──────────────────────────────────────────────────────────────

    @Test
    fun `сброс очищает накопленное и снимает флаг конца`() {
        val pager = CallLogPager(pageSize = 10)
        pager.append(page(3, "+7900A", 1000))
        assertTrue(pager.reachedEnd)

        pager.reset()

        assertEquals(0, pager.loadedCount)
        assertFalse(pager.reachedEnd)
        assertTrue(pager.canLoadMore)
    }
}
