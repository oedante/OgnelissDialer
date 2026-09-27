package com.ogneliss.dialer.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ЧТЕНИЕ КОНТАКТОВ.
 *
 * ── О стоимости операций ───────────────────────────────────────────────────
 * loadAll() читает всю телефонную книгу: у активного пользователя это
 * тысячи строк курсора плюс отдельный запрос за адресами почты.
 * Операция дорогая, вызывать её на горячих путях нельзя.
 *
 * Для проверки «есть ли номер в контактах» существует lookupByNumber():
 * точечный запрос к PhoneLookup, который система обслуживает по индексу
 * за миллисекунды и с нормализацией номера на своей стороне.
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /**
     * ИДЕНТИФИКАТОРЫ ИЗБРАННЫХ КОНТАКТОВ.
     *
     * ── Почему отдельным запросом ──────────────────────────────────────────
     * В таблице Phone поле STARRED присоединяется из таблицы контактов,
     * и присоединение выполняет провайдер. Часть прошивок делает это
     * неверно и возвращает единицу для всех строк — тогда «избранными»
     * оказываются все контакты подряд, что и наблюдалось.
     *
     * Прямой запрос к таблице контактов такой неоднозначности не имеет:
     * STARRED там собственное поле, а не результат присоединения.
     *
     * Запрос дешёвый: возвращает только идентификаторы и только
     * отмеченных, то есть обычно единицы записей.
     */
    private fun loadStarredIds(): Set<Long> = runCatching {
        // Без разрешения запрос вернул бы SecurityException со стеком
        // в логе. Отсутствие доступа — штатное состояние до его выдачи,
        // и ошибкой в журнале выглядеть не должно
        if (!PermissionGuard.canReadContacts(context)) return@runCatching emptySet()

        val ids = mutableSetOf<Long>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.STARRED} = 1",
            null,
            null
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        ids
    }.getOrElse {
        Logger.w("Contacts", "Не удалось прочитать избранные", it)
        emptySet()
    }

    suspend fun loadAll(): List<Contact> = withContext(io) {
        // Проверка до запроса: без разрешения провайдер бросает
        // SecurityException, а список остаётся пустым без объяснения
        if (!PermissionGuard.canReadContacts(context)) {
            PermissionGuard.logDenied("чтение контактов", "READ_CONTACTS")
            return@withContext emptyList()
        }
        val byId = LinkedHashMap<Long, MutableList<PhoneNumber>>()
        // Пара «имя, фото»: признак избранного теперь берётся отдельным
        // запросом, потому что в таблице Phone он присоединяется провайдером
        // и на части прошивок возвращается неверно
        val meta = LinkedHashMap<Long, Pair<String, String?>>()
        val lookup = HashMap<Long, String>()

        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                lookup[id] = c.getString(1).orEmpty()
                meta.getOrPut(id) { c.getString(2).orEmpty() to c.getString(3) }
                val number = c.getString(4).orEmpty()
                val label = ContactsContract.CommonDataKinds.Phone
                    .getTypeLabel(context.resources, c.getInt(5), c.getString(6)).toString()
                byId.getOrPut(id) { mutableListOf() }
                    .add(PhoneNumber(number, label, PhoneFormatter.toE164(number)))
            }
        }

        val emails = loadEmails()
        val events = loadEvents()

        val starredIds = loadStarredIds()

        meta.map { (id, m) ->
            Contact(
                id = id,
                lookupKey = lookup[id].orEmpty(),
                displayName = m.first,
                photoUri = m.second,
                // Избранное из таблицы контактов, а не из присоединённого
                // поля таблицы данных — см. пояснение в loadStarredIds
                starred = id in starredIds,
                numbers = byId[id]?.distinctBy { it.e164 } ?: emptyList(),
                emails = emails[id].orEmpty(),
                events = events[id].orEmpty()
            )
        }
    }

    private fun loadEmails(): Map<Long, List<String>> {
        val out = HashMap<Long, MutableList<String>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(1) ?: continue
                out.getOrPut(c.getLong(0)) { mutableListOf() }.add(addr)
            }
        }
        return out
    }

    /**
     * ДАТЫ ИЗ КНИГИ: ДЕНЬ РОЖДЕНИЯ И ГОДОВЩИНЫ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПРО ФОРМАТ ХРАНЕНИЯ
     * ══════════════════════════════════════════════════════════════════════
     * Провайдер отдаёт дату строкой, и она бывает двух видов:
     *
     *     1988-12-05   — с годом
     *     --12-05      — без года
     *
     * Второй вид — не порча данных, а обычное дело: люди помнят число
     * и месяц, а год не спрашивают. Поэтому дата не разбирается
     * в календарь ради возраста: показываем то, что записано, приведя
     * к виду локали.
     *
     * ⚠️ Встречается и третий вид — произвольная строка, введённая руками
     * в другом приложении. Она отдаётся как есть: лучше показать «зимой»
     * так, как человек написал, чем не показать ничего.
     */
    private fun loadEvents(): Map<Long, List<ContactEvent>> {
        val out = HashMap<Long, MutableList<ContactEvent>>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Event.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Event.START_DATE,
                    ContactsContract.CommonDataKinds.Event.TYPE,
                    ContactsContract.CommonDataKinds.Event.LABEL
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val raw = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val label = ContactsContract.CommonDataKinds.Event
                        .getTypeLabel(context.resources, c.getInt(2), c.getString(3))
                        .toString()
                    /*
                     * ДУБЛИ ИЗ РАЗНЫХ УЧЁТНЫХ ЗАПИСЕЙ.
                     *
                     * Контакт нередко синхронизирован дважды — например,
                     * с Google и с почтовым сервисом. День рождения
                     * тогда приходит двумя записями, и в карточке
                     * он стоял два раза подряд.
                     *
                     * Сводим по паре «подпись + дата»: одинаковая дата
                     * с одинаковой подписью — это одно событие,
                     * от какого бы источника оно ни пришло. Разные даты
                     * с одной подписью остаются обе: это может быть
                     * годовщина свадьбы и годовщина знакомства.
                     */
                    val event = ContactEvent(label = label, date = formatEventDate(raw))
                    val list = out.getOrPut(c.getLong(0)) { mutableListOf() }
                    if (list.none { it.label == event.label && it.date == event.date }) {
                        list.add(event)
                    }
                }
            }
        }.onFailure { Logger.w("Contacts", "Не удалось прочитать даты контактов", it) }
        return out
    }

    /**
     * Дата события в виде локали.
     *
     * «1988-12-05» → «5 декабря 1988 г.», «--12-05» → «5 декабря».
     * Всё, что не разобралось, возвращается как есть.
     */
    private fun formatEventDate(raw: String): String = runCatching {
        /*
         * ТРЕТИЙ ФОРМАТ: ПОЛНАЯ ОТМЕТКА ВРЕМЕНИ.
         *
         * Часть учётных записей (Exchange, некоторые почтовые сервисы)
         * записывает дату целиком: 1988-12-05T00:00:00.000Z. Разбор
         * этого не знал, и на экран попадала сырая строка — рядом
         * с правильно разобранной датой того же события.
         *
         * Берём первые десять символов: дальше идёт время, которого
         * у дня рождения нет по смыслу, а часовой пояс сдвинул бы
         * дату на сутки у тех, кто восточнее Гринвича.
         */
        val iso = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T").find(raw)
        val normalized = if (iso != null) raw.take(10) else raw

        val withYear = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(normalized)
        val noYear = Regex("^--(\\d{2})-(\\d{2})$").find(normalized)

        when {
            withYear != null -> {
                val (y, m, d) = withYear.destructured
                val cal = java.util.Calendar.getInstance().apply {
                    set(y.toInt(), m.toInt() - 1, d.toInt(), 0, 0, 0)
                }
                java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG)
                    .format(cal.time)
            }

            noYear != null -> {
                val (m, d) = noYear.destructured
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.MONTH, m.toInt() - 1)
                    set(java.util.Calendar.DAY_OF_MONTH, d.toInt())
                }
                /*
                 * Формат «день и месяц» без года берётся у локали:
                 * собирать его строкой вручную нельзя — порядок дня
                 * и месяца в разных языках разный.
                 */
                java.text.SimpleDateFormat(
                    android.text.format.DateFormat.getBestDateTimePattern(
                        java.util.Locale.getDefault(), "d MMMM"
                    ),
                    java.util.Locale.getDefault()
                ).format(cal.time)
            }

            else -> raw
        }
    }.getOrDefault(raw)

    /**
     * ТОЧЕЧНАЯ ПРОВЕРКА НОМЕРА.
     *
     * Используется на входящем вызове, где счёт идёт на миллисекунды:
     * система даёт CallScreeningService всего несколько секунд, и за это
     * время нельзя вычитывать всю книгу.
     *
     * PhoneLookup сам нормализует номер и ищет по индексу, поэтому работает
     * одинаково для "+79001234567", "89001234567" и "8 (900) 123-45-67".
     *
     * @return имя контакта или null, если номер незнаком
     */
    suspend fun lookupByNumber(number: String): String? = withContext(io) {
        if (number.isBlank()) return@withContext null
        if (!PermissionGuard.canReadContacts(context)) return@withContext null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrElse {
            Logger.w("Contacts", "Точечный поиск контакта не удался", it)
            null
        }
    }

    /**
     * ОТМЕТКА ПОСЛЕДНЕГО ИЗМЕНЕНИЯ КНИГИ.
     *
     * Возвращает максимальную метку времени правки среди всех контактов.
     *
     * Зачем: ContentObserver срабатывает на любое касание провайдера,
     * включая служебные операции синхронизации, которые ничего не меняют
     * по существу. Синхронизация Google трогает контакты пачками
     * по несколько раз в час.
     *
     * Сравнивая метку с сохранённой, можно понять, изменилось ли что-то
     * на самом деле, и не перечитывать тысячи контактов вместе
     * с пересборкой T9-индекса впустую.
     *
     * @return метка в миллисекундах или 0, если прочитать не удалось
     */
    suspend fun lastUpdatedTimestamp(): Long = withContext(io) {
        if (!PermissionGuard.canReadContacts(context)) return@withContext 0L
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP),
                null, null,
                "${ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP} DESC LIMIT 1"
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
        }.getOrElse {
            Logger.w("Contacts", "Не удалось прочитать отметку изменения книги", it)
            // При ошибке возвращаем текущее время: пусть лучше перечитаем
            // лишний раз, чем пропустим настоящее изменение
            System.currentTimeMillis()
        }
    }

    /**
     * ФОТОГРАФИЯ КОНТАКТА ПО НОМЕРУ.
     *
     * Нужна экрану вызова: лицо узнаётся быстрее имени, и на экране,
     * где решение принимается за пару секунд, это имеет значение.
     *
     * Запрос точечный, по индексу PhoneLookup — тот же путь, что
     * и для имени. Читать всю книгу ради одной фотографии в момент
     * входящего вызова недопустимо: там счёт идёт на миллисекунды.
     */
    suspend fun photoUriForNumber(number: String): String? = withContext(io) {
        if (number.isBlank()) return@withContext null
        if (!PermissionGuard.canReadContacts(context)) return@withContext null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrElse {
            Logger.w("Contacts", "Не удалось прочитать фото контакта", it)
            null
        }
    }

    /** Быстрая проверка наличия номера в книге. */
    suspend fun isKnownNumber(number: String): Boolean =
        lookupByNumber(number) != null

    /*
     * Изменяющие операции вынесены в ContactQuickActions (безопасные)
     * и ContactEditor (переадресация в системное приложение).
     * Здесь остаётся только чтение — так проще следить за тем,
     * кто и что пишет в системные контакты.
     * Обоснование разделения: docs/CONTACTS_STRATEGY.md
     */
}
