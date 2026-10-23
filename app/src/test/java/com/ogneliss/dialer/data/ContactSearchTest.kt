package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ПОИСКА.
 *
 * Поиск срабатывает на каждое нажатие цифры — чаще любой другой функции
 * приложения. При этом до выноса из ViewModel он был непроверяем:
 * пришлось бы поднимать весь граф зависимостей ради проверки, что «5546»
 * находит «Клинова».
 */
class ContactSearchTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun contact(
        id: Long,
        name: String,
        number: String = "+7900111$id",
        starred: Boolean = false
    ) = Contact(
        id = id, lookupKey = "k$id", displayName = name,
        photoUri = null, starred = starred,
        numbers = listOf(PhoneNumber(number, "Mobile", number))
    )

    private fun group(
        number: String,
        name: String? = null,
        count: Int = 1,
        lastDate: Long = now
    ) = CallLogGroup(
        key = "g$number", lastCallId = 1, number = number, e164 = number,
        displayName = name, photoUri = null, contactId = null,
        lastType = CallType.INCOMING, lastDate = lastDate, durationSec = 10,
        count = count, incomingCount = count, outgoingCount = 0,
        missedCount = 0, rejectedCount = 0, totalDurationSec = 10,
        numbers = listOf(number), subscriptionLabel = null, subscriptionId = null
    )

    // ── Базовое поведение ──────────────────────────────────────────────────

    @Test
    fun `пустой запрос ничего не ищет`() {
        val result = ContactSearch.search(
            listOf(contact(1, "Андрей")), emptyList(), "", SortOrder.RELEVANCE, nowMillis = now
        )
        assertTrue(result.isEmpty)
    }

    @Test
    fun `запрос без цифр ничего не ищет`() {
        // В поле набора могут оказаться «+», «*» и «,» — сами по себе
        // они искать не должны
        val result = ContactSearch.search(
            listOf(contact(1, "Андрей")), emptyList(), "+*,", SortOrder.RELEVANCE, nowMillis = now
        )
        assertTrue(result.isEmpty)
    }

    @Test
    fun `контакт находится по T9 имени`() {
        /*
         * 4445 — это «Клин» на клавиатуре.
         *
         * Раскладка в T9Engine: 4 — ИЙКЛ, 5 — МНОП. Значит «Клинов» —
         * это 444552, а не 5545, как считал этот тест: он был написан
         * под другую раскладку и все годы не запускался, потому что
         * тестовый исходник не собирался.
         */
        val result = ContactSearch.search(
            listOf(contact(1, "Клинов"), contact(2, "Петров")),
            emptyList(), "4445", SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals(1, result.contacts.size)
        assertEquals("Клинов", result.contacts.first().displayName)
    }

    @Test
    fun `контакт находится по середине номера`() {
        // Человек помнит окончание: «который на 2233».
        // Поиск только по началу отсёк бы этот сценарий
        val result = ContactSearch.search(
            listOf(contact(1, "Кто-то", number = "+79671112233")),
            emptyList(), "2233", SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals(1, result.contacts.size)
    }

    // ── Раздельные результаты ──────────────────────────────────────────────

    @Test
    fun `контакты и журнал возвращаются отдельно`() {
        // Смешивать нельзя: один человек встречался бы дважды —
        // как контакт и как запись о звонке
        val result = ContactSearch.search(
            contacts = listOf(contact(1, "Клинов", number = "+79001112233")),
            callLog = listOf(group("+79001112233", "Клинов")),
            query = "4445", order = SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals(1, result.contacts.size)
        assertEquals(1, result.callLog.size)
    }

    @Test
    fun `запись журнала находится по номеру без имени`() {
        val result = ContactSearch.search(
            contacts = emptyList(),
            callLog = listOf(group("+79001234567")),
            query = "1234", order = SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals(1, result.callLog.size)
    }

    // ── Порядок выдачи ─────────────────────────────────────────────────────

    @Test
    fun `частый собеседник выше редкого`() {
        // Если нужный контакт не первый, пользователь набирает ещё цифры,
        // и поиск не сэкономил ни одного нажатия
        val contacts = listOf(
            contact(1, "Абрамов", number = "+7900111A"),
            contact(2, "Аверин", number = "+7900111B")
        )
        val log = listOf(group("+7900111B", count = 50, lastDate = now - day))

        val result = ContactSearch.search(
            contacts, log, "2", SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals("Аверин", result.contacts.first().displayName)
    }

    @Test
    fun `контакт без звонков не выпадает из выдачи`() {
        // За него говорит качество совпадения имени
        val result = ContactSearch.search(
            listOf(contact(1, "Клинов")), emptyList(), "4445", SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals(1, result.contacts.size)
    }

    @Test
    fun `алфавитный порядок соблюдается когда выбран`() {
        val contacts = listOf(
            contact(1, "Яковлев", number = "+7900111A"),
            contact(2, "Абрамов", number = "+7900111B")
        )
        val log = listOf(group("+7900111A", count = 100))

        val result = ContactSearch.search(contacts, log, "2", SortOrder.ALPHABET, nowMillis = now)
        assertEquals("Абрамов", result.contacts.first().displayName)
    }

    @Test
    fun `один результат не требует ранжирования`() {
        val result = ContactSearch.search(
            listOf(contact(1, "Клинов")), emptyList(), "4445", SortOrder.FREQUENCY, nowMillis = now
        )
        assertEquals(1, result.contacts.size)
    }

    @Test
    fun `ничего не найдено — оба списка пусты`() {
        val result = ContactSearch.search(
            listOf(contact(1, "Андрей")),
            listOf(group("+79001234567")),
            "99999", SortOrder.RELEVANCE, nowMillis = now
        )
        assertTrue(result.isEmpty)
    }

    // ── Поиск по заметкам ──────────────────────────────────────────────────

    @Test
    fun `контакт находится по заметке`() {
        /*
         * Ради этого случая всё и делалось: человек помнит «я записывал
         * что-то про сантехника», а имя «Иван» через полгода ничего
         * не говорит.
         *
         * Раньше по заметкам искали только в журнале. Если человек
         * заведён контактом, поиск его не находил — именно тогда,
         * когда заметка нужнее всего.
         */
        val ivan = contact(1, "Иван", "+79001112233")
        val notes = mapOf(
            "+79001112233" to NumberNote(
                e164 = "+79001112233",
                note = "сантехник",
                tags = emptyList(),
                customLabel = null
            )
        )

        // 625637544 — «сантехник» на клавиатуре (7 — ФХЦЧ, 6 — РСТУ)
        val result = ContactSearch.search(
            listOf(ivan), emptyList(), "625637544", SortOrder.RELEVANCE, notes, now
        )

        assertTrue("Контакт должен найтись по заметке", result.contacts.contains(ivan))
    }

    @Test
    fun `контакт по-прежнему находится по имени и номеру`() {
        // Заметки не должны подменить прежние способы: человек ищет
        // и по имени, и по цифрам номера
        val ivan = contact(1, "Иван", "+79001112233")

        val byName = ContactSearch.search(
            listOf(ivan), emptyList(), "4225", SortOrder.RELEVANCE, nowMillis = now
        )
        val byNumber = ContactSearch.search(
            listOf(ivan), emptyList(), "1112233", SortOrder.RELEVANCE, nowMillis = now
        )

        assertTrue("По имени", byName.contacts.contains(ivan))
        assertTrue("По номеру", byNumber.contacts.contains(ivan))
    }

    @Test
    fun `заметка другого номера не находит контакт`() {
        // Заметки привязаны к номерам: чужая заметка не должна
        // вытаскивать посторонний контакт
        val ivan = contact(1, "Иван", "+79001112233")
        val notes = mapOf(
            "+79009998877" to NumberNote(
                e164 = "+79009998877",
                note = "сантехник",
                tags = emptyList(),
                customLabel = null
            )
        )

        val result = ContactSearch.search(
            listOf(ivan), emptyList(), "625637544", SortOrder.RELEVANCE, notes, now
        )

        assertTrue("Чужая заметка не должна находить контакт", result.contacts.isEmpty())
    }

    // ── Поиск буквами (1.6.0, кнопка «Поиск» на панели) ────────────────────

    @Test
    fun `буквы ищут по имени, без учёта регистра`() {
        val olga = contact(1, "Ольга Сергеевна")
        val boris = contact(2, "Борис")
        val result = ContactSearch.search(
            listOf(olga, boris), emptyList(), "ольга", SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals(listOf(olga), result.contacts)
    }

    @Test
    fun `ё и е при поиске буквами — одно и то же`() {
        // «Семён» находится по «семен», и наоборот: «ё» пишут не все
        val semyon = contact(1, "Семён")
        val fedor = contact(2, "Федор")
        val bySemen = ContactSearch.search(
            listOf(semyon, fedor), emptyList(), "семен", SortOrder.RELEVANCE, nowMillis = now
        )
        val byFyodor = ContactSearch.search(
            listOf(semyon, fedor), emptyList(), "фёдор", SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals(listOf(semyon), bySemen.contacts)
        assertEquals(listOf(fedor), byFyodor.contacts)
    }

    @Test
    fun `буквы находят заметку — и в журнале, и у контакта`() {
        // Номер не в книге, но с заметкой «сантехник»: находится запись
        // журнала. Тот же номер у контакта — находится и контакт
        val number = "+79001234500"
        val notes = mapOf(
            number to NumberNote(e164 = number, note = "Сантехник, после 18:00", tags = emptyList())
        )
        val ivan = contact(1, "Иван", number)
        val result = ContactSearch.search(
            listOf(ivan), listOf(group(number)), "сантех", SortOrder.RELEVANCE, notes, now
        )
        assertEquals(listOf(ivan), result.contacts)
        assertEquals(1, result.callLog.size)
    }

    @Test
    fun `буквами имя, которое с запроса начинается, — выше`() {
        // «ан»: «Анна» начинается с запроса, у «Иван Анохин» с него
        // начинается второе слово (хотя первое вхождение — внутри «Иван»),
        // у «Светлана» — совпадение внутри слова
        val svetlana = contact(1, "Светлана")
        val ivan = contact(2, "Иван Анохин")
        val anna = contact(3, "Анна")
        val result = ContactSearch.search(
            listOf(svetlana, ivan, anna), emptyList(), "ан", SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals(listOf(anna, ivan, svetlana), result.contacts)
    }

    @Test
    fun `цифры по-прежнему ищут по T9, даже если поле поиска текстовое`() {
        // В поле «Поиск» можно набрать и цифры: это тот же поиск, что
        // на клавиатуре набора
        val ivan = contact(1, "Иван", "+79001112233")
        val result = ContactSearch.search(
            listOf(ivan), emptyList(), "1112233", SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals(listOf(ivan), result.contacts)
    }

    @Test
    fun `запрос из пробелов ничего не ищет`() {
        val result = ContactSearch.search(
            listOf(contact(1, "Андрей")), emptyList(), "   ", SortOrder.RELEVANCE, nowMillis = now
        )
        assertTrue(result.isEmpty)
    }

}
