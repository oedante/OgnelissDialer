package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ПОРЯДКА РЕЗУЛЬТАТОВ ПОИСКА.
 *
 * При T9-поиске по трём цифрам находятся десятки контактов. Нужный обычно
 * один, и он должен быть первым — иначе поиск не сэкономил пользователю
 * ни одного нажатия, а только добавил работы глазам.
 *
 * До появления ранжирования выдача шла по алфавиту: «Абрамов», которому
 * не звонили никогда, оказывался выше «Любимой», которой звонят ежедневно.
 */
class SearchRankerTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun candidate(
        name: String,
        number: String = "+79001111111",
        calls: Int = 0,
        lastCall: Long = 0,
        starred: Boolean = false
    ) = SearchRanker.Candidate(name, number, number, calls, lastCall, starred)

    // ── Релевантность ──────────────────────────────────────────────────────

    @Test
    fun `частый собеседник выше редкого при равном совпадении`() {
        val result = SearchRanker.rank(
            listOf(
                candidate("Абрамов", calls = 0),
                candidate("Аверин", calls = 50, lastCall = now - day)
            ),
            query = "2", order = SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals("Аверин", result.first().displayName)
    }

    @Test
    fun `совпадение с начала имени сильнее совпадения с середины`() {
        // Набирая «4445» (Клин), человек имеет в виду «Клинов»,
        // а не «Александр Клин»
        val result = SearchRanker.rank(
            listOf(
                candidate("Александр Клинов"),
                candidate("Клинов Андрей")
            ),
            query = "4445", order = SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals("Клинов Андрей", result.first().displayName)
    }

    @Test
    fun `избранный поднимается выше при прочих равных`() {
        val result = SearchRanker.rank(
            listOf(
                candidate("Андрей", calls = 5),
                candidate("Анна", calls = 5, starred = true)
            ),
            query = "2", order = SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals("Анна", result.first().displayName)
    }

    @Test
    fun `недавний собеседник выше давнего при равной частоте`() {
        val result = SearchRanker.rank(
            listOf(
                candidate("Старый", calls = 10, lastCall = now - 300 * day),
                candidate("Свежий", calls = 10, lastCall = now - 2 * day)
            ),
            query = "", order = SortOrder.RELEVANCE, nowMillis = now
        )
        assertEquals("Свежий", result.first().displayName)
    }

    @Test
    fun `контакт без звонков не выпадает из выдачи`() {
        // Сигналы складываются, а не перемножаются: перемножение обнулило бы
        // вклад качества совпадения для контакта без единого звонка,
        // а именно ему поиск и нужнее всего
        val score = SearchRanker.score(
            candidate("Клинов", calls = 0, lastCall = 0), "4445", now
        )
        assertTrue("Совпадение имени должно давать вес", score > 0)
    }

    @Test
    fun `совпадение с начала номера тоже учитывается`() {
        // Человек часто помнит первые цифры, а не имя
        val withNumberMatch = SearchRanker.score(
            candidate("Кто-то", number = "+79671234567"), "7967", now
        )
        val withoutMatch = SearchRanker.score(
            candidate("Кто-то", number = "+79101234567"), "7967", now
        )
        assertTrue(withNumberMatch > withoutMatch)
    }

    // ── Явные режимы сортировки ────────────────────────────────────────────

    @Test
    fun `алфавитный порядок игнорирует частоту`() {
        val result = SearchRanker.rank(
            listOf(
                candidate("Яков", calls = 100),
                candidate("Андрей", calls = 1)
            ),
            query = "", order = SortOrder.ALPHABET, nowMillis = now
        )
        assertEquals("Андрей", result.first().displayName)
    }

    @Test
    fun `сортировка по частоте не смотрит на давность`() {
        val result = SearchRanker.rank(
            listOf(
                candidate("Редкий", calls = 1, lastCall = now),
                candidate("Частый", calls = 99, lastCall = now - 300 * day)
            ),
            query = "", order = SortOrder.FREQUENCY, nowMillis = now
        )
        assertEquals("Частый", result.first().displayName)
    }

    @Test
    fun `сортировка по давности не смотрит на частоту`() {
        val result = SearchRanker.rank(
            listOf(
                candidate("Частый", calls = 99, lastCall = now - 300 * day),
                candidate("Свежий", calls = 1, lastCall = now)
            ),
            query = "", order = SortOrder.RECENCY, nowMillis = now
        )
        assertEquals("Свежий", result.first().displayName)
    }

    @Test
    fun `один результат не требует сортировки`() {
        val single = listOf(candidate("Один"))
        assertEquals(single, SearchRanker.rank(single, "", SortOrder.RELEVANCE, now))
    }
}
