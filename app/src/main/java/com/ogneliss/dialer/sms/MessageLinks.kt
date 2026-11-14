package com.ogneliss.dialer.sms

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * ССЫЛКИ, ПОЧТА, НОМЕРА И ДАТЫ В ТЕКСТЕ SMS (2.5.0).
 *
 * Автор, по снимку переписки с банком: «И в SMS не выделены ссылки и даты».
 * Было: текст пузыря — сплошной; «s.gpb.ru/m/qSCybIiTU» и «30.11.2026»
 * не нажимались — ссылку приходилось копировать, дату — переписывать
 * в календарь руками.
 *
 * Здесь — только поиск, без Android (проверки — MessageLinksTest); что
 * делать по касанию, решает пузырь (ConversationScreen): ссылка —
 * в браузер, почта — в почту, номер — в наш набор, дата — в календарь.
 *
 * ── Почему свои шаблоны, а не android.util.Patterns ────────────────────────
 * Patterns.WEB_URL принимает за адрес почти любое «слово.слово»
 * («т.е», «13.4» в части прошивок), а Patterns.PHONE — суммы с пробелами
 * («10 000 000») и даты («30.11.2026»): в банковских SMS половина текста
 * окрашивалась бы. Свои шаблоны строже: адрес — со схемой, «www.» или
 * известной зоной (.ru, .рф, .com…); номер — российский или международный
 * с «+», 10–11 цифр; дата — день, месяц и (не обязательно) год, с проверкой,
 * что такая дата существует.
 */
object MessageLinks {

    enum class Kind { URL, EMAIL, PHONE, DATE }

    /**
     * Найденное: [start] включительно, [end] не включительно; [target] —
     * адрес для открытия (со схемой), почта, номер цифрами или дата
     * в формате ISO; [date] — для дат.
     */
    data class Span(val start: Int, val end: Int, val kind: Kind, val target: String, val date: LocalDate? = null)

    /** Зоны, по которым адрес без «http» и «www» узнаётся как адрес. */
    private const val ZONES =
        "ru|рф|su|com|net|org|info|biz|io|me|app|online|site|shop|store|pro|cc|tv|co|ly|link|gl|to|by|kz|ua|uz|am|ge|de|uk|eu|us|ink|club|top|xyz|tech|dev|page|cloud"

    private val URL = Regex(
        "(?<![\\w@./-])(" +
            "(?:https?://|www\\.)[^\\s<>\"«»]+" +
            "|(?:[a-zа-яё0-9](?:[a-zа-яё0-9-]*[a-zа-яё0-9])?\\.)+(?:$ZONES)(?::\\d{2,5})?(?:/[^\\s<>\"«»]*)?(?![\\w.-]*\\w)" +
            ")",
        RegexOption.IGNORE_CASE
    )

    private val EMAIL = Regex("(?<![\\w.+-])[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", RegexOption.IGNORE_CASE)

    /** +7 (900) 123-45-67, 8 900 123 45 67, 89001234567, 8-800-555-35-35, +44 20 7946 0958 (как 10–11 цифр после кода). */
    private val PHONE = Regex("(?<![\\w+])(?:\\+\\d{1,3}|8)[\\s(-]*\\d{3}[\\s)-]*\\d{3}[\\s-]*\\d{2}[\\s-]*\\d{2}(?!\\d)")

    private val NUMERIC_DATE = Regex("(?<![\\d.,/])(\\d{1,2})([./])(\\d{1,2})\\2(\\d{4}|\\d{2})(?![\\d.,/]\\d)")

    private val MONTHS_RU = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )
    private val MONTHS_EN = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )
    private val WORD_DATE_RU = Regex(
        "(?<!\\d)(\\d{1,2})\\s+(${MONTHS_RU.joinToString("|")})(?:\\s+(\\d{4}))?(?!\\d)",
        RegexOption.IGNORE_CASE
    )
    private val WORD_DATE_EN = Regex(
        "(?<!\\d)(\\d{1,2})\\s+(${MONTHS_EN.joinToString("|")})(?:\\s+(\\d{4}))?(?!\\d)" +
            "|\\b(${MONTHS_EN.joinToString("|")})\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?(?!\\d)",
        RegexOption.IGNORE_CASE
    )

    /** Хвост, который не часть адреса: точка в конце предложения, запятая, скобка, кавычка. */
    private const val TRAILING = ".,;:!?)»\"'…"

    /**
     * Всё найденное в [text], без пересечений, по порядку. [reference] —
     * когда пришло сообщение: дата без года — ближайшая к нему.
     */
    fun find(text: String, reference: Long, zone: ZoneId = ZoneId.systemDefault()): List<Span> {
        if (text.isBlank()) return emptyList()
        val refDate = Instant.ofEpochMilli(reference).atZone(zone).toLocalDate()
        val found = mutableListOf<Span>()

        URL.findAll(text).forEach { m ->
            val raw = trimTrailing(m.value)
            if (raw.isEmpty()) return@forEach
            val target = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
            found += Span(m.range.first, m.range.first + raw.length, Kind.URL, target)
        }
        EMAIL.findAll(text).forEach { m ->
            val raw = m.value.trimEnd('.')
            found += Span(m.range.first, m.range.first + raw.length, Kind.EMAIL, raw)
        }
        NUMERIC_DATE.findAll(text).forEach { m ->
            val (d, _, mo, y) = m.destructured
            val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
            dateOf(year, mo.toInt(), d.toInt())?.let { found += Span(m.range.first, m.range.last + 1, Kind.DATE, it.toString(), it) }
        }
        WORD_DATE_RU.findAll(text).forEach { m ->
            val day = m.groupValues[1].toInt()
            val month = MONTHS_RU.indexOf(m.groupValues[2].lowercase()) + 1
            val date = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { dateOf(it.toInt(), month, day) }
                ?: nearest(refDate, month, day)
            date?.let { found += Span(m.range.first, m.range.last + 1, Kind.DATE, it.toString(), it) }
        }
        WORD_DATE_EN.findAll(text).forEach { m ->
            val g = m.groupValues
            val (day, monthName, year) = if (g[1].isNotEmpty()) Triple(g[1], g[2], g[3]) else Triple(g[5], g[4], g[6])
            val month = MONTHS_EN.indexOf(monthName.lowercase()) + 1
            val date = year.takeIf { it.isNotEmpty() }?.let { dateOf(it.toInt(), month, day.toInt()) }
                ?: nearest(refDate, month, day.toInt())
            date?.let { found += Span(m.range.first, m.range.last + 1, Kind.DATE, it.toString(), it) }
        }
        PHONE.findAll(text).forEach { m ->
            val digits = m.value.filter { it.isDigit() }
            if (digits.length !in 10..13) return@forEach
            val target = if (m.value.startsWith("+")) "+$digits" else digits
            found += Span(m.range.first, m.range.last + 1, Kind.PHONE, target)
        }

        // Пересечения: раньше начало; при равном — по важности (адрес, почта, дата, номер)
        val ordered = found.sortedWith(compareBy<Span> { it.start }.thenBy { it.kind.ordinal })
        val result = mutableListOf<Span>()
        for (span in ordered) {
            if (result.any { span.start < it.end && it.start < span.end }) continue
            result += span
        }
        return result
    }

    private fun trimTrailing(raw: String): String {
        var s = raw
        while (s.isNotEmpty() && s.last() in TRAILING) {
            // Закрывающая скобка — часть адреса, если открывающая в нём же
            if (s.last() == ')' && s.count { it == '(' } >= s.count { it == ')' }) break
            s = s.dropLast(1)
        }
        return s
    }

    private fun dateOf(year: Int, month: Int, day: Int): LocalDate? =
        if (year !in 1900..2200) null else runCatching { LocalDate.of(year, month, day) }.getOrNull()

    /**
     * Дата без года — в году сообщения; если так она больше чем на месяц
     * раньше сообщения («1 января» в декабрьском SMS) — в следующем году.
     */
    private fun nearest(reference: LocalDate, month: Int, day: Int): LocalDate? {
        val same = dateOf(reference.year, month, day) ?: return dateOf(reference.year + 1, month, day)
        return if (same.isBefore(reference.minusDays(31))) dateOf(reference.year + 1, month, day) ?: same else same
    }
}
