package com.ogneliss.dialer.sms

/**
 * ПАПКИ SMS (2.2.0) — как папки в Telegram (просьба автора: «с возможностью
 * менять их порядок, переключение лучше ориентировать снизу, как во всём
 * приложении»). Вид — как «Папки» в меню Ogneliss Mail (автор, 11.11.2026:
 * «за референс UI возьмём Ogneliss Mail»): списком в шторке «Ещё», которая
 * открывается нижней кнопкой, открытая папка — названием над списком
 * (MessagesFolders.kt).
 *
 * ── Какие ──────────────────────────────────────────────────────────────────
 * Папки собираются сами, по отправителю — разбирать переписки вручную
 * никто не станет:
 * • Все;
 * • Личные — собеседник есть в книге;
 * • Незнакомые — обычный номер, которого нет в книге;
 * • Сервисы — буквенный отправитель («Bank», «Ozon») или короткий номер
 *   (до шести цифр, «900»): банки, магазины, коды;
 * • Непрочитанные;
 * • Избранное — не переписки, а отмеченные сообщения (sms_starred).
 *
 * ── Порядок и показ ────────────────────────────────────────────────────────
 * Строкой в настройках: «ALL,PERSONAL,-UNKNOWN,…» — порядок вкладок,
 * минус — скрыта. «Все» скрыть нельзя: иначе часть переписок пропала бы
 * из виду. Папки новых версий, которых нет в строке, добавляются в конец
 * видимыми. Чистый Kotlin — проверяется модульными тестами.
 */
enum class SmsFolder { ALL, PERSONAL, UNKNOWN, SERVICE, UNREAD, STARRED }

object SmsFolders {

    /** Порядок по умолчанию: все папки видны. */
    val DEFAULT: List<Pair<SmsFolder, Boolean>> = SmsFolder.entries.map { it to true }

    /** Строка настройки → порядок и видимость. Пустая или битая — по умолчанию. */
    fun parse(raw: String?): List<Pair<SmsFolder, Boolean>> {
        if (raw.isNullOrBlank()) return DEFAULT
        val seen = LinkedHashMap<SmsFolder, Boolean>()
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            val hidden = token.startsWith("-")
            val folder = SmsFolder.entries.firstOrNull { it.name == token.removePrefix("-") } ?: return@forEach
            if (folder !in seen) seen[folder] = !hidden || folder == SmsFolder.ALL
        }
        SmsFolder.entries.filter { it !in seen }.forEach { seen[it] = true }
        return seen.toList()
    }

    fun encode(list: List<Pair<SmsFolder, Boolean>>): String =
        list.joinToString(",") { (folder, visible) -> if (visible || folder == SmsFolder.ALL) folder.name else "-" + folder.name }

    /** Видимые папки по порядку. */
    fun visible(raw: String?): List<SmsFolder> = parse(raw).filter { it.second }.map { it.first }

    /** Буквенный отправитель или короткий номер — сервис, а не человек. */
    fun isService(address: String): Boolean {
        if (address.any { it.isLetter() }) return true
        val digits = address.count { it.isDigit() }
        return digits in 1..SHORT_NUMBER_MAX
    }

    /**
     * Переписка в папке. [hasContact] — собеседник есть в книге,
     * [unread] — непрочитанных. STARRED переписок не содержит.
     */
    fun matches(folder: SmsFolder, address: String, hasContact: Boolean, unread: Int): Boolean = when (folder) {
        SmsFolder.ALL -> true
        SmsFolder.PERSONAL -> hasContact
        SmsFolder.UNKNOWN -> !hasContact && !isService(address)
        SmsFolder.SERVICE -> !hasContact && isService(address)
        SmsFolder.UNREAD -> unread > 0
        SmsFolder.STARRED -> false
    }

    /** Короткие номера сервисов: «900», «4444», «88002» — до шести цифр. */
    private const val SHORT_NUMBER_MAX = 6
}
