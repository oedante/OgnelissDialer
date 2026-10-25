package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.ContactNameFormat
import com.ogneliss.dialer.settings.ContactSort
import java.text.Collator

/**
 * ИМЕНА КОНТАКТОВ: «ИМЯ ФАМИЛИЯ» ИЛИ «ФАМИЛИЯ ИМЯ», ПОРЯДОК СПИСКА (1.6.2).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ОТКУДА
 * ══════════════════════════════════════════════════════════════════════════
 * Отзыв в Google Play: «сильно не хватает … сортировки по фамилии».
 * Автор, 23.10: «отображение имени контакта можно сделать настраиваемым
 * и предлагать пользователю выбор, как показывать». Две настройки, как
 * в системных «Контактах»: «Как показывать имя» и «Сортировка контактов».
 *
 * ── Откуда берётся «Фамилия Имя» ───────────────────────────────────────────
 * Не из разбора строки: «Сервис доставки» по пробелу стал бы «доставки
 * Сервис». Книга хранит имя по частям (имя, отчество, фамилия) и сама
 * собирает вторую форму — DISPLAY_NAME_ALTERNATIVE. Её и берём. У записей
 * без частей — организация, почта, одно слово, номер — обе формы
 * совпадают, и ничего не переставляется. Это то же, что делают
 * системные «Контакты» при «Сначала фамилия».
 *
 * Западные имена провайдер пишет с запятой: «Petrov, Andrey». В списке
 * она лишняя — убираем, если запятой нет в самом имени.
 *
 * ── Где применяется ────────────────────────────────────────────────────────
 * Только при показе: в данных Contact.displayName остаётся основной формой.
 * На ней держатся правило «при двух контактах на номер — первый
 * по алфавиту», сопоставление с именами из журнала и индекс T9; меняй
 * их по настройке — разъехались бы фото и имя одного абонента.
 *
 *   • список контактов — [present], порядок — [sortedByFamily];
 *   • журнал — [journalNames] + [inJournal]: имя в журнале пишет система
 *     (CACHED_NAME), подменяем его, только если это имя того же контакта;
 *   • карточка, поиск — [present];
 *   • экран вызова — ContactsRepository.familyFirstName.
 */
object ContactNames {

    /**
     * «Фамилия Имя» из второй формы книги. Пустая или такая же —
     * основная форма [primary].
     */
    fun familyFirst(primary: String, alt: String): String {
        val form = alt.trim()
        if (form.isEmpty() || form == primary) return primary
        return if (',' in primary) form else form.replaceFirst(", ", " ")
    }

    /** Имя для показа в выбранной форме. */
    fun shown(contact: Contact, format: ContactNameFormat): String =
        if (format == ContactNameFormat.FAMILY_FIRST) {
            familyFirst(contact.displayName, contact.nameAlt)
        } else {
            contact.displayName
        }

    /** Имя, по которому сортируется список и выбирается буква раздела. */
    fun sortName(contact: Contact, sort: ContactSort): String =
        if (sort == ContactSort.FAMILY) {
            familyFirst(contact.displayName, contact.nameAlt)
        } else {
            contact.displayName
        }

    /**
     * Список по фамилии — по правилам языка телефона, как COLLATE LOCALIZED
     * у самой книги (по-русски: кириллица, затем латиница; «ё» рядом с «е»).
     *
     * Ключи сравнения считаются один раз на контакт: сравнение строк
     * через Collator в сортировке тысяч записей заметно медленнее.
     */
    fun sortedByFamily(
        contacts: List<Contact>,
        collator: Collator = Collator.getInstance()
    ): List<Contact> =
        contacts
            .map { contact -> collator.getCollationKey(sortName(contact, ContactSort.FAMILY)) to contact }
            .sortedWith(compareBy { it.first })
            .map { it.second }

    /**
     * Копия контакта для показа: displayName — в выбранной форме,
     * остальное (id, номера, фото) — как было. Без перестановки — тот же
     * объект, лишних копий нет.
     */
    fun present(contact: Contact, format: ContactNameFormat): Contact {
        if (format == ContactNameFormat.GIVEN_FIRST) return contact
        val name = shown(contact, format)
        return if (name == contact.displayName) contact else contact.copy(displayName = name)
    }

    /**
     * Имена для журнала: (номер в E.164, имя в книге) → имя для показа.
     * Только у тех, у кого форма отличается; при «Имя Фамилия» — пусто.
     *
     * Ключ — пара, а не один номер: номер бывает записан у двоих, и
     * журнал показывает имя одного из них. Переставляем слова именно
     * в том имени, которое стоит в строке.
     */
    fun journalNames(
        contacts: List<Contact>,
        format: ContactNameFormat
    ): Map<Pair<String, String>, String> {
        if (format == ContactNameFormat.GIVEN_FIRST) return emptyMap()
        return buildMap {
            contacts.forEach { contact ->
                val name = shown(contact, format)
                if (name == contact.displayName) return@forEach
                contact.numbers.forEach { number -> put(number.e164 to contact.displayName, name) }
            }
        }
    }

    /** Имя строки журнала с учётом формата: [cached] — что записала система. */
    fun inJournal(
        cached: String?,
        e164: String,
        names: Map<Pair<String, String>, String>
    ): String? = cached?.let { names[e164 to it] } ?: cached
}
