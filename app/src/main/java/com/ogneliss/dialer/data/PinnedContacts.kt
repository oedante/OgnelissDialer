package com.ogneliss.dialer.data

/**
 * ЗАКРЕПЛЁННЫЕ АБОНЕНТЫ ВВЕРХУ ЖУРНАЛА.
 *
 * Чистая логика без Android — покрыта тестами.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧЕМ ЭТО ОТЛИЧАЕТСЯ ОТ ИЗБРАННОГО
 * ══════════════════════════════════════════════════════════════════════════
 * Избранное — свойство КОНТАКТА, оно хранится в телефонной книге,
 * синхронизируется с Google и видно всем приложениям.
 *
 * Закрепление — свойство ЖУРНАЛА у нас: «держи этот номер наверху,
 * пока я с ним разбираюсь». Курьер, мастер, разовый клиент —
 * их не заносят в избранное, потому что через неделю они там будут
 * мешать.
 *
 * Поэтому закреплять можно и номера, которых нет в книге вовсе,
 * а избранное для них недоступно в принципе.
 *
 * ── Почему ограничение по числу ────────────────────────────────────────────
 * Закреплённые занимают верх экрана — тот, что виден без прокрутки.
 * Если их окажется десять, журнал начнётся ниже видимой области,
 * и функция превратится в помеху. Пять — примерно половина экрана,
 * дальше начинается вред.
 * ══════════════════════════════════════════════════════════════════════════
 */
object PinnedContacts {

    /**
     * Сколько номеров можно закрепить.
     *
     * Пять занимают примерно половину экрана. Больше — журнал уходит
     * за пределы видимой области, и приложение открывается «пустым».
     */
    const val MAX_PINNED = 5

    /**
     * Разделение журнала на закреплённые и обычные записи.
     *
     * @return пара «закреплённые в порядке закрепления» и «остальные»
     */
    fun split(
        groups: List<CallLogGroup>,
        pinnedE164: List<String>
    ): Pair<List<CallLogGroup>, List<CallLogGroup>> {
        if (pinnedE164.isEmpty()) return emptyList<CallLogGroup>() to groups

        val pinnedSet = pinnedE164.toSet()
        val pinned = mutableListOf<CallLogGroup>()
        val rest = mutableListOf<CallLogGroup>()

        groups.forEach { group ->
            if (group.e164 in pinnedSet) pinned += group else rest += group
        }

        /*
         * Порядок закреплённых — тот, в котором их закрепляли,
         * а не по дате последнего звонка.
         *
         * Иначе карточки прыгали бы местами при каждом входящем,
         * и мышечная память «мастер второй сверху» переставала бы
         * работать — а ради неё закрепление и делается.
         */
        val orderIndex = pinnedE164.withIndex().associate { (i, e164) -> e164 to i }
        pinned.sortBy { orderIndex[it.e164] ?: Int.MAX_VALUE }

        return pinned to rest
    }

    /**
     * Добавление номера к закреплённым.
     *
     * @return новый список или null, если добавить нельзя —
     *         вызывающий код тогда покажет причину, а не сделает вид,
     *         что всё получилось
     */
    fun pin(current: List<String>, e164: String): List<String>? = when {
        e164.isBlank() -> null

        // Повторное закрепление — не ошибка, но и не изменение:
        // возвращаем прежний список, чтобы не дёргать хранилище
        e164 in current -> current

        current.size >= MAX_PINNED -> null

        // Новый номер добавляется в конец: закреплённые раньше остаются
        // на своих местах, и привычный порядок не ломается
        else -> current + e164
    }

    fun unpin(current: List<String>, e164: String): List<String> =
        current.filterNot { it == e164 }

    /** Можно ли закрепить ещё один номер. Для состояния пункта меню. */
    fun canPinMore(current: List<String>): Boolean = current.size < MAX_PINNED

    fun isPinned(current: List<String>, e164: String): Boolean = e164 in current

    /**
     * ПЕРЕМЕЩЕНИЕ НА ОДНУ ПОЗИЦИЮ.
     *
     * ── Почему стрелками, а не перетаскиванием ─────────────────────────────
     * Перетаскивание в списке на телефоне ненадёжно: длинное нажатие
     * конфликтует с прокруткой, палец перекрывает перемещаемый элемент,
     * а промах возвращает всё на место без объяснения.
     *
     * Две стрелки решают ту же задачу однозначно. Закреплённых
     * не больше пяти — переставить нужное на любое место занимает
     * четыре нажатия в худшем случае, и каждое из них видно.
     *
     * @return новый список или прежний, если двигать некуда
     */
    fun move(current: List<String>, e164: String, up: Boolean): List<String> {
        val index = current.indexOf(e164)
        if (index < 0) return current

        val target = if (up) index - 1 else index + 1
        // Границы: у крайних элементов кнопка неактивна, но проверка
        // нужна и здесь — состояние интерфейса может отстать
        if (target !in current.indices) return current

        return current.toMutableList().apply {
            this[index] = this[target].also { this[target] = this[index] }
        }
    }

    /** Можно ли двигать вверх. Для состояния кнопки. */
    fun canMoveUp(current: List<String>, e164: String): Boolean =
        current.indexOf(e164) > 0

    /** Можно ли двигать вниз. */
    fun canMoveDown(current: List<String>, e164: String): Boolean {
        val index = current.indexOf(e164)
        return index >= 0 && index < current.lastIndex
    }

    // ── Закрепление контакта, а не номера (1.3.2, 03.10.2026) ──────────────
    /*
     * Было: в настройках хранились номера E.164. Контакт подбирался
     * к номеру каждый раз заново, поэтому звонок с другого номера того же
     * человека или очистка журнала «теряли» закреплённого: строка без
     * звонков показывалась без фото и могла не открыть карточку.
     *
     * Стало: для номера из книги хранится ключ контакта —
     *   contact:<LOOKUP_KEY>|<e164>
     * LOOKUP_KEY — постоянный ключ контакта в книге Android (id контакта
     * меняется при синхронизации, ключ — нет). Номер после «|» — запасной:
     * если контакт удалили или ключ сменился, закреплённый остаётся
     * по номеру, а не пропадает. Номер не из книги хранится как раньше —
     * голым E.164. Старые записи читаются без изменений и переводятся
     * на контакт функцией migrate(), как только контакты загружены.
     *
     * Ключ экранируется: «,» разделяет записи в настройках, «|» — части.
     */

    private const val CONTACT_PREFIX = "contact:"

    /** Разобранная запись: lookupKey == null — закреплён номер не из книги. */
    data class Pin(val raw: String, val lookupKey: String?, val e164: String)

    private fun escape(s: String) = s.replace("%", "%25").replace(",", "%2C").replace("|", "%7C")
    private fun unescape(s: String) = s.replace("%7C", "|").replace("%2C", ",").replace("%25", "%")

    fun contactKey(lookupKey: String, e164: String): String =
        CONTACT_PREFIX + escape(lookupKey) + "|" + e164

    /** Ключ для закрепления: контакт, если номер есть в книге, иначе номер. */
    fun keyFor(e164: String, lookupKey: String?): String =
        if (lookupKey.isNullOrBlank()) e164 else contactKey(lookupKey, e164)

    fun parse(raw: String): Pin {
        if (!raw.startsWith(CONTACT_PREFIX)) return Pin(raw, null, raw)
        val body = raw.removePrefix(CONTACT_PREFIX)
        val bar = body.lastIndexOf('|')
        return if (bar < 0) Pin(raw, unescape(body), "")
        else Pin(raw, unescape(body.substring(0, bar)), body.substring(bar + 1))
    }

    /**
     * Позиция закреплённого, к которому относится номер (или контакт).
     * Контакт совпадает по ключу — значит, подходит звонок с любого его
     * номера. Номер совпадает напрямую — для номеров не из книги и как
     * запасной вариант, если ключ контакта не найден.
     */
    fun indexOf(pins: List<Pin>, e164: String, lookupKey: String?): Int =
        pins.indexOfFirst { pin ->
            (pin.lookupKey != null && pin.lookupKey == lookupKey) ||
                (pin.e164.isNotEmpty() && pin.e164 == e164)
        }

    fun isSubscriberPinned(current: List<String>, e164: String, lookupKey: String?): Boolean =
        indexOf(current.map(::parse), e164, lookupKey) >= 0

    /** Закрепить номер или его контакт; null — предел или пустой номер. */
    fun pinSubscriber(current: List<String>, e164: String, lookupKey: String?): List<String>? = when {
        e164.isBlank() -> null
        isSubscriberPinned(current, e164, lookupKey) -> current
        current.size >= MAX_PINNED -> null
        else -> current + keyFor(e164, lookupKey)
    }

    /** Открепить всё, что относится к этому номеру или контакту. */
    fun unpinSubscriber(current: List<String>, e164: String, lookupKey: String?): List<String> =
        current.filterNot { raw ->
            val pin = parse(raw)
            (pin.lookupKey != null && pin.lookupKey == lookupKey) ||
                (pin.e164.isNotEmpty() && pin.e164 == e164)
        }

    /**
     * Перевод закреплённых номеров на контакты. Номер, который есть
     * в книге, становится ключом контакта; два закреплённых номера одного
     * человека сливаются в один (остаётся тот, что выше). Порядок
     * сохраняется. Возвращает тот же список, если менять нечего, —
     * по этому признаку вызывающий решает, писать ли в настройки.
     */
    fun migrate(current: List<String>, lookupKeyOf: (String) -> String?): List<String> {
        val result = mutableListOf<String>()
        val seenContacts = HashSet<String>()
        val seenNumbers = HashSet<String>()
        current.forEach { raw ->
            val pin = parse(raw)
            val key = pin.lookupKey ?: lookupKeyOf(pin.e164)
            when {
                key != null -> if (seenContacts.add(key)) {
                    result += if (pin.lookupKey != null) raw else contactKey(key, pin.e164)
                }
                seenNumbers.add(pin.e164) -> result += raw
            }
        }
        return if (result == current) current else result
    }
}
