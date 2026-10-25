package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.ContactNameFormat
import com.ogneliss.dialer.settings.ContactSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.text.Collator
import java.util.Locale

/**
 * ИМЕНА КОНТАКТОВ (1.6.2): «Фамилия Имя» и сортировка по фамилии.
 * Имена и номера вымышленные.
 */
class ContactNamesTest {

    private fun contact(id: Long, name: String, alt: String = "", number: String = "+7900123456$id") = Contact(
        id = id, lookupKey = "k$id", displayName = name, photoUri = null, starred = false,
        numbers = listOf(PhoneNumber(number, "Mobile", number)),
        nameAlt = alt
    )

    private val ru = Collator.getInstance(Locale.forLanguageTag("ru"))

    @Test
    fun запятаяПослеФамилииУбирается() {
        assertEquals("Петров Андрей", ContactNames.familyFirst("Андрей Петров", "Петров, Андрей"))
        assertEquals("Petrov Andrey", ContactNames.familyFirst("Andrey Petrov", "Petrov, Andrey"))
    }

    @Test
    fun безВторойФормыИмяНеМеняется() {
        assertEquals("Сервис доставки", ContactNames.familyFirst("Сервис доставки", ""))
        assertEquals("Борис", ContactNames.familyFirst("Борис", "Борис"))
    }

    @Test
    fun запятуюВСамомИмениНеТрогаем() {
        assertEquals("Иванов, бухгалтер", ContactNames.familyFirst("бухгалтер, Иванов", "Иванов, бухгалтер"))
    }

    @Test
    fun имяФамилияОтдаётТотЖеОбъект() {
        val anna = contact(1, "Анна Смирнова", "Смирнова, Анна")
        assertSame(anna, ContactNames.present(anna, ContactNameFormat.GIVEN_FIRST))
        assertEquals("Смирнова Анна", ContactNames.present(anna, ContactNameFormat.FAMILY_FIRST).displayName)
        // Номер и id остаются — карточка открывается та же
        assertEquals(anna.id, ContactNames.present(anna, ContactNameFormat.FAMILY_FIRST).id)
    }

    @Test
    fun сортировкаПоФамилии() {
        val list = listOf(
            contact(1, "Андрей Петров", "Петров, Андрей"),
            contact(2, "Борис Алексеев", "Алексеев, Борис"),
            contact(3, "Сервис доставки"),
            contact(4, "Виктор Орлов", "Орлов, Виктор")
        )
        val sorted = ContactNames.sortedByFamily(list, ru).map { it.id }
        assertEquals(listOf(2L, 4L, 1L, 3L), sorted)
        assertEquals("Алексеев Борис", ContactNames.sortName(list[1], ContactSort.FAMILY))
        assertEquals("Борис Алексеев", ContactNames.sortName(list[1], ContactSort.GIVEN))
    }

    @Test
    fun журналПодменяетТолькоИмяТогоЖеКонтакта() {
        val anna = contact(1, "Анна Смирнова", "Смирнова, Анна", number = "+79001112233")
        val boss = contact(2, "Начальник", number = "+79001112233")
        val names = ContactNames.journalNames(listOf(anna, boss), ContactNameFormat.FAMILY_FIRST)

        assertEquals("Смирнова Анна", ContactNames.inJournal("Анна Смирнова", "+79001112233", names))
        // Тот же номер, но журнал показывает другого — не трогаем
        assertEquals("Начальник", ContactNames.inJournal("Начальник", "+79001112233", names))
        assertEquals(null, ContactNames.inJournal(null, "+79001112233", names))
        assertEquals(
            emptyMap<Pair<String, String>, String>(),
            ContactNames.journalNames(listOf(anna), ContactNameFormat.GIVEN_FIRST)
        )
    }
}
