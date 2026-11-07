package com.ogneliss.dialer.data

/**
 * КАКОЙ КОНТАКТ КНИГИ ОТНОСИТСЯ К НОМЕРУ (2.1.0).
 *
 * Было: карточка по номеру брала первый по алфавиту контакт с этим E.164.
 * Свой номер (и номер любого человека) часто записан в нескольких
 * контактах: полный — с двумя номерами, почтой и фото — и короткие дубли
 * с SIM-карты, из мессенджера. Открывался дубль с одним номером (автор,
 * 07.11.2026: «Мой контакт — только один распознанный телефон, а не вся
 * информация»). К тому же номер «8 912…» в книге и «+7 912…» в журнале
 * по E.164 не сходились, если код страны не восстановился.
 *
 * Стало: совпадение — по E.164 или по последним десяти цифрам; из
 * совпавших выбирается самый полный — с фото выше, затем больше номеров
 * и почты, затем по алфавиту (тот же порядок, что у фото «Моего контакта»
 * в меню с 2.0.1).
 */
object ContactMatch {

    fun matches(contact: Contact, e164: String): Boolean {
        if (e164.isBlank()) return false
        val tail = CallLogGrouper.tailKey(e164)
        return contact.numbers.any { n ->
            n.e164 == e164 || (tail != null && CallLogGrouper.tailKey(n.e164) == tail)
        }
    }

    /** Все контакты с этим номером, самый полный — первым. */
    fun allFor(contacts: List<Contact>, e164: String): List<Contact> =
        contacts.filter { matches(it, e164) }.sortedWith(FULLEST_FIRST)

    /** Самый полный контакт с этим номером или null. */
    fun bestFor(contacts: List<Contact>, e164: String): Contact? = allFor(contacts, e164).firstOrNull()

    val FULLEST_FIRST: Comparator<Contact> =
        compareByDescending<Contact> { !it.photoUri.isNullOrBlank() }
            .thenByDescending { it.numbers.size + it.emails.size + it.events.size }
            .thenBy { it.displayName.lowercase() }

    /**
     * «МОЙ КОНТАКТ» — ВСЁ О СЕБЕ ОДНОЙ КАРТОЧКОЙ.
     *
     * Собирается из профиля «Я» системных контактов (ContactsContract.Profile —
     * его не было вовсе: в общий список книги он не входит), из всех контактов
     * со своими номерами и из номеров SIM-карт. Номера — без повторов по E.164
     * и последним десяти цифрам, почта — по адресу; имя и id — у самого
     * полного контакта книги (через него правка и мессенджеры), фото —
     * у профиля или у самого полного.
     *
     * @return null — о себе ничего не известно (нет ни номера, ни профиля)
     */
    fun ownContact(
        contacts: List<Contact>,
        profile: Contact?,
        ownNumbers: List<String>
    ): Contact? {
        val seeds = (ownNumbers + profile?.numbers.orEmpty().map { it.e164 })
            .filter { it.isNotBlank() }
            .distinct()
        val matched = seeds.flatMap { allFor(contacts, it) }.distinctBy { it.id }.sortedWith(FULLEST_FIRST)
        val best = matched.firstOrNull()
        if (best == null && profile == null && seeds.isEmpty()) return null

        val numbers = mutableListOf<PhoneNumber>()
        val seen = HashSet<String>()
        fun add(n: PhoneNumber) {
            val key = CallLogGrouper.tailKey(n.e164) ?: n.e164.ifBlank { n.value }
            if (key.isNotBlank() && seen.add(key)) numbers += n
        }
        (listOfNotNull(best) + matched.drop(1) + listOfNotNull(profile)).forEach { c -> c.numbers.forEach(::add) }
        ownNumbers.filter { it.isNotBlank() }.forEach { add(PhoneNumber(it, "", it)) }

        val emails = (listOfNotNull(best, profile) + matched).flatMap { it.emails }
            .distinctBy { it.trim().lowercase() }
        val events = (listOfNotNull(best, profile) + matched).flatMap { it.events }.distinct()
        return Contact(
            // id — только контакта книги: профиль «Я» своим редактором не правится
            id = best?.id ?: -1L,
            lookupKey = best?.lookupKey ?: profile?.lookupKey.orEmpty(),
            displayName = best?.displayName?.takeIf { it.isNotBlank() }
                ?: profile?.displayName.orEmpty(),
            photoUri = profile?.photoUri?.takeIf { it.isNotBlank() } ?: matched.firstOrNull { !it.photoUri.isNullOrBlank() }?.photoUri,
            starred = best?.starred ?: false,
            numbers = numbers,
            emails = emails,
            events = events,
            nameAlt = best?.nameAlt.orEmpty()
        )
    }
}
