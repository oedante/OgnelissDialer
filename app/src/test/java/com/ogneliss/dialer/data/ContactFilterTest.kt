package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ФИЛЬТРОВ КОНТАКТОВ.
 *
 * Фильтры давности показывают людей, которых человек может решить
 * удалить. Ошибка здесь приводит к потере нужного контакта — поэтому
 * проверяется прежде всего то, что в список НЕ должно попадать.
 */
class ContactFilterTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun contact(
        id: Long,
        name: String,
        number: String = "+7900$id",
        starred: Boolean = false
    ) = Contact(
        id = id, lookupKey = "k$id", displayName = name, photoUri = null,
        starred = starred, numbers = listOf(PhoneNumber(number, "Mobile", number))
    )

    // ── Избранные ──────────────────────────────────────────────────────────

    @Test
    fun `фильтр избранных отбирает только отмеченные`() {
        val contacts = listOf(
            contact(1, "Обычный"),
            contact(2, "Важный", starred = true)
        )
        val result = ContactFilter.apply(
            contacts, ContactFilter.FAVOURITES, emptyMap(), emptyMap(), now
        )
        assertEquals(1, result.size)
        assertEquals("Важный", result.first().displayName)
    }

    // ── Давность ───────────────────────────────────────────────────────────

    @Test
    fun `избранные не попадают в фильтр давности`() {
        // Пользователь уже сказал, что человек важен. Предлагать его
        // к пересмотру значит спорить с ним
        val contacts = listOf(contact(1, "Любимая", "+7900A", starred = true))
        val lastCall = mapOf("+7900A" to (now - 500 * day))

        val result = ContactFilter.apply(
            contacts, ContactFilter.STALE_YEAR, lastCall, emptyMap(), now
        )
        assertTrue("Избранный не должен попадать в список", result.isEmpty())
    }

    @Test
    fun `недавний звонок исключает контакт из давних`() {
        val contacts = listOf(contact(1, "Активный", "+7900A"))
        val lastCall = mapOf("+7900A" to (now - 10 * day))

        val result = ContactFilter.apply(
            contacts, ContactFilter.STALE_HALF_YEAR, lastCall, emptyMap(), now
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `контакт без звонков в истории попадает в давние`() {
        // Звонков может не быть в загруженной части журнала — интерфейс
        // подписывает такие случаи осторожнее, но показать их нужно
        val contacts = listOf(contact(1, "Неизвестно", "+7900A"))

        val result = ContactFilter.apply(
            contacts, ContactFilter.STALE_YEAR, emptyMap(), emptyMap(), now
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `полугодовой и годовой пороги различаются`() {
        val contacts = listOf(contact(1, "Восемь месяцев", "+7900A"))
        val lastCall = mapOf("+7900A" to (now - 240 * day))

        val halfYear = ContactFilter.apply(
            contacts, ContactFilter.STALE_HALF_YEAR, lastCall, emptyMap(), now
        )
        val year = ContactFilter.apply(
            contacts, ContactFilter.STALE_YEAR, lastCall, emptyMap(), now
        )

        assertEquals("Восемь месяцев больше полугода", 1, halfYear.size)
        assertTrue("Но меньше года", year.isEmpty())
    }

    // ── Частые ─────────────────────────────────────────────────────────────

    @Test
    fun `частые берутся долей, а не абсолютным числом`() {
        // У одного человека «часто» это десять звонков, у другого два.
        // Абсолютный порог был бы верен только для одного из них
        val contacts = (1L..10L).map { contact(it, "Контакт $it", "+7900$it") }
        val counts = contacts.mapIndexed { index, c ->
            c.numbers.first().e164 to (index + 1) * 5
        }.toMap()

        val result = ContactFilter.apply(
            contacts, ContactFilter.FREQUENT, emptyMap(), counts, now
        )

        assertEquals("Верхняя пятая часть от десяти", 2, result.size)
        assertEquals("Контакт 10", result.first().displayName)
    }

    @Test
    fun `контакты с одним-двумя звонками не считаются частыми`() {
        val contacts = listOf(contact(1, "Редкий", "+7900A"))
        val counts = mapOf("+7900A" to 2)

        val result = ContactFilter.apply(
            contacts, ContactFilter.FREQUENT, emptyMap(), counts, now
        )
        assertTrue("Два звонка — это не «часто»", result.isEmpty())
    }

    @Test
    fun `при отсутствии статистики список частых пуст`() {
        val contacts = listOf(contact(1, "Кто-то"))
        val result = ContactFilter.apply(
            contacts, ContactFilter.FREQUENT, emptyMap(), emptyMap(), now
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `фильтр «все» ничего не отсекает`() {
        val contacts = listOf(contact(1, "А"), contact(2, "Б"))
        assertEquals(
            contacts,
            ContactFilter.apply(contacts, ContactFilter.ALL, emptyMap(), emptyMap(), now)
        )
    }
}
