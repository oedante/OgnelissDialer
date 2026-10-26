package com.ogneliss.dialer.data

import com.ogneliss.dialer.ui.components.CallLogRowState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * ГОТОВЫЕ СТРОКИ ЖУРНАЛА БЕРУТСЯ ПОВТОРНО — И ТОЛЬКО ПОКА ВЕРНЫ (1.8.0).
 *
 * До 1.8.0 отпечаток для сравнения и отпечаток в строке считались
 * по-разному, и переиспользование не срабатывало никогда: журнал
 * собирался целиком при каждом звонке. Здесь проверяется и что оно
 * работает, и что изменившаяся строка собирается заново — устаревшая
 * строка хуже лишней работы.
 *
 * Часовой пояс задаётся явно: подписи «Сегодня» и «Вчера» зависят
 * от календаря, и тест не должен зависеть от того, где его запускают.
 */
class CallLogRowMapperTest {

    private val originalZone: TimeZone = TimeZone.getDefault()

    @Before
    fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))

    @After
    fun restoreZone() = TimeZone.setDefault(originalZone)

    private val labels = object : CallLogRowMapper.Labels {
        override fun infoSummary(info: NumberInfo?) = info?.operatorHint
        override fun accessibility(title: String, type: CallType, date: String) = "$title, $date"
        override fun today(time: String) = "Сегодня, $time"
        override fun yesterday(time: String) = "Вчера, $time"
        override val fingerprint = 1
    }

    private val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.US)

    /** Время по часам телефона — в поясе теста. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun group(id: Long, e164: String, lastDate: Long, name: String? = null) = CallLogGroup(
        key = "number:$e164#$id", lastCallId = id, number = e164, e164 = e164,
        displayName = name, photoUri = null, contactId = null,
        lastType = CallType.INCOMING, lastDate = lastDate, durationSec = 10,
        count = 1, incomingCount = 1, outgoingCount = 0, missedCount = 0,
        rejectedCount = 0, totalDurationSec = 10, numbers = listOf(e164),
        subscriptionLabel = null, subscriptionId = null
    )

    private fun build(
        groups: List<CallLogGroup>,
        previous: List<CallLogRowState> = emptyList(),
        notes: Map<String, NumberNote> = emptyMap(),
        known: Set<String> = emptySet(),
        blocked: Map<String, String> = emptyMap(),
        now: Long = at(2026, 10, 26, 12, 0)
    ) = CallLogRowMapper.mapIncremental(
        groups = groups,
        previous = previous,
        numberInfo = emptyMap(),
        knownNumbers = known,
        simSlots = emptyMap(),
        contactPhotos = emptyMap(),
        blockedLabels = blocked,
        notes = notes,
        dateFormat = dateFormat,
        labels = labels,
        days = CallLogRowMapper.Days(now)
    )

    private fun note(e164: String, text: String) =
        NumberNote(e164 = e164, note = text, tags = emptyList(), updatedAt = 1L)

    // Звонки: сегодня утром, вчера вечером и неделю назад. Лениво — время
    // считается уже в поясе теста, а не в поясе машины при создании класса
    private val today by lazy { group(1, "+70000000201", at(2026, 10, 26, 9, 15)) }
    private val yesterday by lazy { group(2, "+70000000202", at(2026, 10, 25, 21, 47)) }
    private val older by lazy { group(3, "+70000000203", at(2026, 10, 19, 18, 2)) }

    @Test
    fun `неизменившиеся строки берутся готовыми`() {
        val first = build(listOf(today, yesterday, older))
        val second = build(listOf(today, yesterday, older), previous = first)
        first.indices.forEach { assertSame(first[it], second[it]) }
    }

    @Test
    fun `журнал перечитан теми же данными — строки берутся готовыми`() {
        // После каждого звонка журнал читается заново: те же записи — новые объекты
        val first = build(listOf(today, yesterday))
        val second = build(listOf(today.copy(), yesterday.copy()), previous = first)
        assertSame(first[0], second[0])
        assertSame(first[1], second[1])
    }

    @Test
    fun `новый звонок собирается, остальные берутся готовыми`() {
        val first = build(listOf(today, yesterday, older))
        val fresh = group(4, "+70000000204", at(2026, 10, 26, 11, 30))
        val second = build(listOf(fresh, today, yesterday, older), previous = first)
        assertEquals(fresh.key, second[0].key)
        assertSame(first[0], second[1])
        assertSame(first[1], second[2])
        assertSame(first[2], second[3])
    }

    @Test
    fun `изменилась заметка — строка собрана заново`() {
        // Число заметок то же, сменился только текст
        val first = build(listOf(today, yesterday), notes = mapOf(today.e164 to note(today.e164, "курьер")))
        val second = build(
            listOf(today, yesterday), previous = first,
            notes = mapOf(today.e164 to note(today.e164, "доставка мебели"))
        )
        assertNotSame(first[0], second[0])
        assertEquals("доставка мебели", second[0].title)
        assertSame(first[1], second[1])
    }

    @Test
    fun `номер сохранили в контакты — строка собрана заново`() {
        val first = build(listOf(today))
        val second = build(listOf(today), previous = first, known = setOf(today.e164))
        assertNotSame(first[0], second[0])
        assertTrue(second[0].isContact)
    }

    @Test
    fun `сменилось имя в журнале — строка собрана заново`() {
        val first = build(listOf(today))
        val second = build(listOf(today.copy(displayName = "Сервис доставки")), previous = first)
        assertEquals("Сервис доставки", second[0].title)
    }

    @Test
    fun `после полуночи сегодня становится вчера, старые строки не трогаются`() {
        val first = build(listOf(today, yesterday, older))
        assertEquals("Сегодня, 09:15", first[0].dateLabel)
        assertEquals("Вчера, 21:47", first[1].dateLabel)

        val nextDay = build(listOf(today, yesterday, older), previous = first, now = at(2026, 10, 27, 0, 5))
        assertEquals("Вчера, 09:15", nextDay[0].dateLabel)
        assertEquals("25 Oct, 21:47", nextDay[1].dateLabel)
        // Неделя назад — дата и была, строка та же
        assertSame(first[2], nextDay[2])
    }

    @Test
    fun `вчера — по календарю, и в день перевода часов тоже`() {
        // Берлин: 29 марта 2026 года в 02:00 часы переводят на 03:00 — в сутках 23 часа.
        // Прежний подсчёт делил разницу полуночей на 24 часа и получал «Сегодня»
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val days = CallLogRowMapper.Days(at(2026, 3, 30, 12, 0))
        assertEquals(CallLogRowMapper.Day.YESTERDAY, days.of(at(2026, 3, 29, 10, 0)))
        assertEquals(CallLogRowMapper.Day.TODAY, days.of(at(2026, 3, 30, 0, 30)))
        assertEquals(CallLogRowMapper.Day.EARLIER, days.of(at(2026, 3, 28, 23, 59)))
        // Часы телефона отвели назад — звонок «из завтра» подписан датой
        assertEquals(CallLogRowMapper.Day.EARLIER, days.of(at(2026, 3, 31, 0, 1)))
    }

    @Test
    fun `пометка по ключу строки важнее пометки по номеру`() {
        // По ключу строки — «без звука» у одного звонка (тихие часы), по номеру — блокировка
        val rows = build(
            listOf(today, yesterday),
            blocked = mapOf(today.key to "Без звука", yesterday.e164 to "Заблокирован")
        )
        assertEquals("Без звука", rows[0].blockedLabel)
        assertEquals("Заблокирован", rows[1].blockedLabel)
    }

    @Test
    fun `обстановка сборки — по подписям и формату даты`() {
        val one = CallLogRowMapper.contextOf(labels, SimpleDateFormat("d MMM, HH:mm", Locale.US))
        val two = CallLogRowMapper.contextOf(labels, SimpleDateFormat("d MMM, HH:mm", Locale.US))
        assertEquals("Те же подписи и формат — готовые строки годятся", one, two)

        val french = CallLogRowMapper.contextOf(labels, SimpleDateFormat("d MMM, HH:mm", Locale.FRENCH))
        assertNotEquals("Другой язык — всё заново", one, french)

        val unknown = object : CallLogRowMapper.Labels {
            override fun infoSummary(info: NumberInfo?): String? = null
            override fun accessibility(title: String, type: CallType, date: String) = title
            override fun today(time: String) = time
            override fun yesterday(time: String) = time
        }
        assertNull("Без отпечатка подписей готовые строки не берутся", CallLogRowMapper.contextOf(unknown, dateFormat))
    }
}
