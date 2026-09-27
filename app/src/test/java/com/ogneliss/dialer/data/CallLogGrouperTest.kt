package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ГРУППИРОВКИ ВЫЗОВОВ.
 *
 * Группировка неочевидна и легко ломается: у контакта бывает несколько
 * номеров, режимов три, а сортировка в режиме «по абоненту» отличается
 * от порядка исходных записей. Проверить это вручную означало бы держать
 * на телефоне подходящую историю звонков — нереально.
 */
class CallLogGrouperTest {

    /**
     * КЛЮЧИ КАРТОЧЕК ОБЯЗАНЫ БЫТЬ УНИКАЛЬНЫМИ.
     *
     * LazyColumn падает с «Key was already used», как только два элемента
     * совпадут. Раньше ключом служил идентификатор абонента, и в режиме
     * «подряд идущие» повторные звонки одного человека давали одинаковые
     * ключи — то есть падение у любого активного пользователя.
     *
     * Проверка вынесена первой: это не деталь, а условие работоспособности
     * списка.
     */
    @Test
    fun `ключи карточек уникальны при повторных звонках одного абонента`() {
        val entries = listOf(
            entry(number = "+7900A", date = 5000),
            entry(number = "+7900B", date = 4000),
            entry(number = "+7900A", date = 3000),
            entry(number = "+7900B", date = 2000),
            entry(number = "+7900A", date = 1000)
        )

        val groups = CallLogGrouper.group(
            /*
             * simLabelResolver передаётся ИМЕНОВАННЫМ аргументом.
             *
             * Здесь стояла хвостовая лямбда `) { null }`. Она подставляется
             * в ПОСЛЕДНИЙ параметр, а последний у group() —
             * contactIdByNumber: Map<String, Long>. Компилятор сообщал
             * «actual type is () -> Nothing?, but Map<String, Long>
             * was expected», и весь набор тестов не собирался: не запускался
             * ни один тест проекта.
             */
            entries,
            CallLogGrouper.Mode.CONSECUTIVE,
            separateBySim = false,
            simLabelResolver = { null }
        )

        val keys = groups.map { it.key }
        assertEquals(
            "Один абонент даёт несколько карточек, и ключи не должны совпадать",
            keys.size, keys.toSet().size
        )
    }

    @Test
    fun `ключи уникальны и в режиме по абоненту`() {
        val entries = listOf(
            entry(number = "+7900A", date = 5000),
            entry(number = "+7900B", date = 4000),
            entry(number = "+7900A", date = 3000)
        )

        val groups = CallLogGrouper.group(
            entries,
            CallLogGrouper.Mode.BY_CONTACT,
            separateBySim = false,
            simLabelResolver = { null }
        )

        val keys = groups.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `ключи уникальны без группировки`() {
        val entries = (1..10).map { entry(number = "+7900A", date = it * 1000L) }

        val groups = CallLogGrouper.group(
            entries,
            CallLogGrouper.Mode.NONE,
            separateBySim = false,
            simLabelResolver = { null }
        )

        val keys = groups.map { it.key }
        assertEquals("Каждый вызов — своя карточка со своим ключом", 10, keys.toSet().size)
    }

    private var nextId = 1L

    private fun entry(
        number: String = "+79001112233",
        type: CallType = CallType.INCOMING,
        date: Long,
        duration: Long = 30,
        contactId: Long? = null,
        name: String? = null,
        sim: String? = "sim1"
    ) = CallLogEntry(
        id = nextId++,
        number = number,
        e164 = number,
        cachedName = name,
        photoUri = null,
        type = type,
        date = date,
        durationSec = duration,
        phoneAccountId = sim,
        contactId = contactId
    )

    // ── Режим «подряд идущие» ──────────────────────────────────────────────

    @Test
    fun `подряд идущие вызовы одного номера объединяются`() {
        val entries = listOf(entry(date = 300), entry(date = 200), entry(date = 100))
        val groups = CallLogGrouper.group(entries, CallLogGrouper.Mode.CONSECUTIVE)
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].count)
    }

    @Test
    fun `вызов другого абонента разрывает группу`() {
        val entries = listOf(
            entry(number = "+79001112233", date = 300),
            entry(number = "+79004445566", date = 200),
            entry(number = "+79001112233", date = 100)
        )
        val groups = CallLogGrouper.group(entries, CallLogGrouper.Mode.CONSECUTIVE)
        assertEquals("Должно получиться три отдельные карточки", 3, groups.size)
    }

    @Test
    fun `вызовы с разных SIM не объединяются при двух картах`() {
        val entries = listOf(entry(date = 300, sim = "sim1"), entry(date = 200, sim = "sim2"))
        val groups = CallLogGrouper.group(
            entries, CallLogGrouper.Mode.CONSECUTIVE, separateBySim = true
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun `вызовы с разных SIM объединяются если разделение выключено`() {
        val entries = listOf(entry(date = 300, sim = "sim1"), entry(date = 200, sim = "sim2"))
        val groups = CallLogGrouper.group(
            entries, CallLogGrouper.Mode.CONSECUTIVE, separateBySim = false
        )
        assertEquals(1, groups.size)
    }

    // ── Режим «все вызовы абонента» ────────────────────────────────────────

    @Test
    fun `все вызовы абонента сливаются в одну карточку`() {
        val entries = listOf(
            entry(number = "+79001112233", date = 500),
            entry(number = "+79004445566", date = 400),
            entry(number = "+79001112233", date = 300),
            entry(number = "+79004445566", date = 200),
            entry(number = "+79001112233", date = 100)
        )
        val groups = CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT)
        assertEquals("Два абонента — две карточки", 2, groups.size)
        assertEquals(3, groups.first { it.e164 == "+79001112233" }.count)
        assertEquals(2, groups.first { it.e164 == "+79004445566" }.count)
    }

    @Test
    fun `карточки сортируются по времени последнего вызова`() {
        val entries = listOf(
            entry(number = "+79001112233", date = 500),
            entry(number = "+79004445566", date = 400),
            entry(number = "+79004445566", date = 350),
            entry(number = "+79001112233", date = 100)
        )
        val groups = CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT)
        assertEquals("+79001112233", groups[0].e164)
        assertEquals("+79004445566", groups[1].e164)
    }

    @Test
    fun `старый абонент поднимается наверх при новом звонке`() {
        val entries = listOf(
            entry(number = "+7900A", date = 1000),
            entry(number = "+7900B", date = 500),
            entry(number = "+7900B", date = 400),
            entry(number = "+7900B", date = 300)
        )
        val groups = CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT)
        assertEquals(
            "Сортировка идёт по последнему вызову, а не по количеству",
            "+7900A", groups[0].e164
        )
    }

    // ── Несколько номеров одного контакта ──────────────────────────────────

    @Test
    fun `разные номера одного контакта попадают в одну карточку`() {
        val entries = listOf(
            entry(number = "+79671111111", contactId = 42, name = "Любимая", date = 300),
            entry(number = "+79102222222", contactId = 42, name = "Любимая", date = 200),
            entry(number = "+74843333333", contactId = 42, name = "Любимая", date = 100)
        )
        val groups = CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT)
        assertEquals("Один человек — одна карточка", 1, groups.size)
        assertEquals(3, groups[0].count)
        assertEquals("Все три номера должны быть в карточке", 3, groups[0].numbers.size)
    }

    @Test
    fun `номера без контакта не сливаются между собой`() {
        val entries = listOf(
            entry(number = "+79001111111", contactId = null, date = 300),
            entry(number = "+79002222222", contactId = null, date = 200)
        )
        assertEquals(2, CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT).size)
    }

    // ── Счётчики по типам ──────────────────────────────────────────────────

    @Test
    fun `счётчики входящих исходящих и пропущенных считаются раздельно`() {
        val entries = listOf(
            entry(type = CallType.INCOMING, date = 600),
            entry(type = CallType.INCOMING, date = 500),
            entry(type = CallType.OUTGOING, date = 400),
            entry(type = CallType.MISSED, date = 300, duration = 0),
            entry(type = CallType.MISSED, date = 200, duration = 0),
            entry(type = CallType.REJECTED, date = 100, duration = 0)
        )
        val group = CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT).single()
        assertEquals(6, group.count)
        assertEquals(2, group.incomingCount)
        assertEquals(1, group.outgoingCount)
        assertEquals(2, group.missedCount)
        assertEquals(1, group.rejectedCount)
        // Свойство группы называется hasAnyMissed; hasMissed есть
        // у строки журнала (CallLogRowState), а не у группы
        assertTrue(group.hasAnyMissed)
    }

    @Test
    fun `суммарная длительность складывается по всем вызовам`() {
        val entries = listOf(
            entry(date = 300, duration = 60),
            entry(date = 200, duration = 30),
            entry(date = 100, duration = 10)
        )
        val group = CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT).single()
        assertEquals(100L, group.totalDurationSec)
        assertEquals("Длительность карточки — последнего вызова", 60L, group.durationSec)
    }

    @Test
    fun `односторонняя связь определяется корректно`() {
        val onlyIncoming = listOf(
            entry(type = CallType.INCOMING, date = 300),
            entry(type = CallType.INCOMING, date = 200),
            entry(type = CallType.MISSED, date = 100)
        )
        assertTrue(
            CallLogGrouper.group(onlyIncoming, CallLogGrouper.Mode.BY_CONTACT).single().isOneWay
        )

        val both = listOf(
            entry(type = CallType.INCOMING, date = 300),
            entry(type = CallType.OUTGOING, date = 200)
        )
        assertFalse(
            CallLogGrouper.group(both, CallLogGrouper.Mode.BY_CONTACT).single().isOneWay
        )
    }

    // ── Прочее ─────────────────────────────────────────────────────────────

    @Test
    fun `режим без группировки оставляет каждую запись отдельно`() {
        val entries = listOf(entry(date = 300), entry(date = 200), entry(date = 100))
        assertEquals(3, CallLogGrouper.group(entries, CallLogGrouper.Mode.NONE).size)
    }

    @Test
    fun `имя берётся из самой свежей записи где оно есть`() {
        val entries = listOf(
            entry(date = 300, name = null),
            entry(date = 200, name = "Новое имя"),
            entry(date = 100, name = "Старое имя")
        )
        val group = CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT).single()
        assertEquals("Новое имя", group.displayName)
    }

    @Test
    fun `пустой журнал не ломает группировку`() {
        assertTrue(CallLogGrouper.group(emptyList(), CallLogGrouper.Mode.BY_CONTACT).isEmpty())
        assertTrue(CallLogGrouper.group(emptyList(), CallLogGrouper.Mode.CONSECUTIVE).isEmpty())
    }

    @Test
    fun `записи в неверном порядке всё равно группируются правильно`() {
        val entries = listOf(entry(date = 100), entry(date = 300), entry(date = 200))
        val group = CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT).single()
        assertEquals("Последним считается самый свежий", 300L, group.lastDate)
    }

    // ── Группировка при подгрузке страниц ──────────────────────────────────

    @Test
    fun `номера одного контакта сходятся в одну карточку`() {
        /*
         * Главная проверка для постраничной загрузки.
         *
         * Человек звонил с рабочего номера сегодня, а с личного —
         * полгода назад. Первая страница содержит только рабочий,
         * второй номер приходит с подгрузкой.
         *
         * Если ключ группировки строится по номеру, а не по контакту,
         * получатся две карточки одного человека — и вторая всплывёт
         * внезапно посреди списка.
         */
        val entries = listOf(
            entry(number = "+79001112233", date = 1_000, contactId = 42),
            entry(number = "+79005556677", date = 500, contactId = 42)
        )

        val contactIdByNumber = mapOf(
            "+79001112233" to 42L,
            "+79005556677" to 42L
        )

        val groups = CallLogGrouper.group(
            entries,
            CallLogGrouper.Mode.BY_CONTACT,
            contactIdByNumber = contactIdByNumber
        )

        assertEquals("Оба номера — один человек", 1, groups.size)
        assertEquals("В карточке оба номера", 2, groups.first().numbers.size)
    }

    @Test
    fun `без карты контактов номера остаются разными карточками`() {
        /*
         * Обратный случай: карта ещё не пришла — книга читается
         * параллельно с журналом.
         *
         * Тогда группировка идёт по номеру, и это верно: связать
         * их пока нечем. После загрузки книги вызывается regroup,
         * и карточки сливаются.
         */
        val entries = listOf(
            entry(number = "+79001112233", date = 1_000),
            entry(number = "+79005556677", date = 500)
        )

        val groups = CallLogGrouper.group(entries, CallLogGrouper.Mode.BY_CONTACT)

        assertEquals("Разные номера без карты — разные карточки", 2, groups.size)
    }
}
