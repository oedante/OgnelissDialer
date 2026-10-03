package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ЗАКРЕПЛЁННЫХ АБОНЕНТОВ.
 *
 * Главное здесь — порядок. Он задаётся временем закрепления, а не датой
 * звонка: иначе карточки прыгали бы местами при каждом входящем,
 * и мышечная память «мастер второй сверху» переставала бы работать —
 * а ради неё закрепление и делается.
 */
class PinnedContactsTest {

    private fun group(e164: String, lastDate: Long = 0) = CallLogGroup(
        key = "k$e164", lastCallId = 1, number = e164, e164 = e164,
        displayName = null, photoUri = null, contactId = null,
        lastType = CallType.INCOMING, lastDate = lastDate, durationSec = 0,
        count = 1, incomingCount = 1, outgoingCount = 0, missedCount = 0,
        rejectedCount = 0, totalDurationSec = 0, numbers = listOf(e164),
        subscriptionLabel = null, subscriptionId = null
    )

    // ── Порядок ────────────────────────────────────────────────────────────

    @Test
    fun `порядок закреплённых задаётся закреплением, а не датой звонка`() {
        val groups = listOf(
            group("+7900B", lastDate = 5000),   // звонил позже
            group("+7900A", lastDate = 1000)
        )
        // Но закреплён первым был A
        val (pinned, _) = PinnedContacts.split(groups, listOf("+7900A", "+7900B"))

        assertEquals(
            "Карточки не должны меняться местами при новом звонке",
            listOf("+7900A", "+7900B"), pinned.map { it.e164 }
        )
    }

    @Test
    fun `незакреплённые сохраняют исходный порядок`() {
        val groups = listOf(group("+7900A"), group("+7900B"), group("+7900C"))
        val (_, rest) = PinnedContacts.split(groups, listOf("+7900B"))

        assertEquals(listOf("+7900A", "+7900C"), rest.map { it.e164 })
    }

    @Test
    fun `без закреплённых список возвращается целиком`() {
        val groups = listOf(group("+7900A"), group("+7900B"))
        val (pinned, rest) = PinnedContacts.split(groups, emptyList())

        assertTrue(pinned.isEmpty())
        assertEquals(2, rest.size)
    }

    @Test
    fun `закреплённый номер, которого нет в журнале, не ломает разбиение`() {
        // Записи могли уйти за пределы загруженной страницы
        val groups = listOf(group("+7900A"))
        val (pinned, rest) = PinnedContacts.split(groups, listOf("+7900A", "+7900Z"))

        assertEquals(1, pinned.size)
        assertTrue(rest.isEmpty())
    }

    // ── Добавление ─────────────────────────────────────────────────────────

    @Test
    fun `новый номер добавляется в конец`() {
        // Закреплённые раньше остаются на своих местах
        val result = PinnedContacts.pin(listOf("+7900A"), "+7900B")
        assertEquals(listOf("+7900A", "+7900B"), result)
    }

    @Test
    fun `повторное закрепление не меняет список`() {
        val current = listOf("+7900A", "+7900B")
        assertEquals(current, PinnedContacts.pin(current, "+7900A"))
    }

    @Test
    fun `при достижении предела закрепить нельзя`() {
        // null означает «покажи причину», а не «сделай вид, что получилось»
        val full = (1..PinnedContacts.MAX_PINNED).map { "+790000$it" }
        assertNull(PinnedContacts.pin(full, "+7900NEW"))
    }

    @Test
    fun `пустой номер не закрепляется`() {
        // Скрытый вызов: закреплять нечего
        assertNull(PinnedContacts.pin(emptyList(), ""))
    }

    // ── Снятие ─────────────────────────────────────────────────────────────

    @Test
    fun `открепление убирает только указанный номер`() {
        val result = PinnedContacts.unpin(listOf("+7900A", "+7900B"), "+7900A")
        assertEquals(listOf("+7900B"), result)
    }

    @Test
    fun `открепление отсутствующего номера безопасно`() {
        val current = listOf("+7900A")
        assertEquals(current, PinnedContacts.unpin(current, "+7900Z"))
    }

    // ── Состояние меню ─────────────────────────────────────────────────────

    @Test
    fun `признак заполненности отражает предел`() {
        assertTrue(PinnedContacts.canPinMore(emptyList()))

        val full = (1..PinnedContacts.MAX_PINNED).map { "+790000$it" }
        assertFalse(PinnedContacts.canPinMore(full))
    }

    // ── Перемещение ────────────────────────────────────────────────────────

    @Test
    fun `перемещение вверх меняет местами с предыдущим`() {
        val result = PinnedContacts.move(listOf("A", "B", "C"), "B", up = true)
        assertEquals(listOf("B", "A", "C"), result)
    }

    @Test
    fun `перемещение вниз меняет местами со следующим`() {
        val result = PinnedContacts.move(listOf("A", "B", "C"), "B", up = false)
        assertEquals(listOf("A", "C", "B"), result)
    }

    @Test
    fun `первый элемент не двигается вверх`() {
        // Кнопка неактивна, но проверка нужна и здесь: состояние
        // интерфейса может отстать от данных
        val current = listOf("A", "B")
        assertEquals(current, PinnedContacts.move(current, "A", up = true))
    }

    @Test
    fun `последний элемент не двигается вниз`() {
        val current = listOf("A", "B")
        assertEquals(current, PinnedContacts.move(current, "B", up = false))
    }

    @Test
    fun `перемещение отсутствующего номера безопасно`() {
        val current = listOf("A", "B")
        assertEquals(current, PinnedContacts.move(current, "Z", up = true))
    }

    @Test
    fun `признаки возможности перемещения соответствуют позиции`() {
        val current = listOf("A", "B", "C")

        assertFalse(PinnedContacts.canMoveUp(current, "A"))
        assertTrue(PinnedContacts.canMoveDown(current, "A"))

        assertTrue(PinnedContacts.canMoveUp(current, "B"))
        assertTrue(PinnedContacts.canMoveDown(current, "B"))

        assertTrue(PinnedContacts.canMoveUp(current, "C"))
        assertFalse(PinnedContacts.canMoveDown(current, "C"))
    }

    @Test
    fun `четырёх перемещений хватает на любую перестановку`() {
        /*
         * Обоснование выбора стрелок вместо перетаскивания: при пяти
         * элементах перенести последний на первое место — четыре
         * нажатия, и результат каждого виден.
         */
        var list = listOf("A", "B", "C", "D", "E")
        repeat(4) { list = PinnedContacts.move(list, "E", up = true) }

        assertEquals(listOf("E", "A", "B", "C", "D"), list)
    }

    @Test
    fun `предел не превышает половины экрана`() {
        // Пять карточек занимают примерно половину экрана; больше —
        // журнал начинается ниже видимой области, и приложение
        // открывается «пустым»
        assertTrue(PinnedContacts.MAX_PINNED in 3..6)
    }

    // ── Закрепление контакта (1.3.2) ─────────────────────────────────────────

    private val lk = "0r12-ABC,x|y%z"   // нарочно с «,», «|», «%»

    @Test
    fun `ключ контакта со служебными символами разбирается обратно`() {
        val pin = PinnedContacts.parse(PinnedContacts.contactKey(lk, "+79105214092"))
        assertEquals(lk, pin.lookupKey)
        assertEquals("+79105214092", pin.e164)
    }

    @Test
    fun `голый номер читается как номер`() {
        val pin = PinnedContacts.parse("+79001112233")
        assertNull(pin.lookupKey)
        assertEquals("+79001112233", pin.e164)
    }

    @Test
    fun `звонок с другого номера того же контакта относится к закреплённому`() {
        val pins = listOf(PinnedContacts.contactKey(lk, "+79105214092")).map(PinnedContacts::parse)
        assertEquals(0, PinnedContacts.indexOf(pins, "+79676660512", lk))
    }

    @Test
    fun `контакт не найден — закреплённый держится за запасной номер`() {
        val pins = listOf(PinnedContacts.contactKey(lk, "+79105214092")).map(PinnedContacts::parse)
        assertEquals(0, PinnedContacts.indexOf(pins, "+79105214092", null))
        assertEquals(-1, PinnedContacts.indexOf(pins, "+70000000000", "other"))
    }

    @Test
    fun `миграция сливает номера одного контакта и сохраняет порядок`() {
        val old = listOf("+79105214092", "+79676660512", "+79001112233")
        val migrated = PinnedContacts.migrate(old) {
            if (it == "+79105214092" || it == "+79676660512") lk else null
        }
        assertEquals(2, migrated.size)
        assertEquals(lk, PinnedContacts.parse(migrated[0]).lookupKey)
        assertEquals("+79001112233", migrated[1])
    }

    @Test
    fun `повторная миграция возвращает тот же список`() {
        val migrated = listOf(PinnedContacts.contactKey(lk, "+79105214092"), "+79001112233")
        assertTrue(PinnedContacts.migrate(migrated) { lk } === migrated)
    }

    @Test
    fun `открепление по любому номеру контакта`() {
        val current = listOf(PinnedContacts.contactKey(lk, "+79105214092"), "+79001112233")
        assertEquals(listOf("+79001112233"), PinnedContacts.unpinSubscriber(current, "+79676660512", lk))
    }

    @Test
    fun `номер из книги закрепляется как контакт и не дублируется`() {
        val pinned = PinnedContacts.pinSubscriber(emptyList(), "+79105214092", lk)!!
        assertEquals(lk, PinnedContacts.parse(pinned.single()).lookupKey)
        assertEquals(pinned, PinnedContacts.pinSubscriber(pinned, "+79676660512", lk))
    }
}
