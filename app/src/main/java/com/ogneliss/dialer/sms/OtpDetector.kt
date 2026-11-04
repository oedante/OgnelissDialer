package com.ogneliss.dialer.sms

/**
 * КОДЫ И НОМЕРА В ТЕКСТЕ SMS (2.0.0) — НА УСТРОЙСТВЕ.
 *
 * Разбор идёт только в памяти телефона: приложению не дано выхода
 * в сеть (нет разрешения INTERNET), и текст сообщения никуда не уходит.
 *
 * ── Код подтверждения ([code]) ─────────────────────────────────────────────
 * Ищется только в сообщении, где есть слово-признак: «код», «пароль»,
 * «code», «password», «OTP», «PIN»… Без него сумма покупки или номер
 * заказа выглядели бы так же, как код, — и в буфер ушло бы не то.
 *
 * Кандидаты — 4–8 цифр одним куском («482913», «G-583920») или двумя
 * тройками («123 456»). Не кандидаты: цифры рядом с валютой («1500 р.»),
 * маской карты («*1234»), частью даты, времени, дроби или длинного номера
 * телефона. Из нескольких кандидатов берётся ближайший к слову-признаку,
 * при равенстве — стоящий после него («Код: 5678 … карта *1234»).
 *
 * Пароль из букв и цифр («Ваш пароль: aB3dE9») — только сразу после
 * слова «пароль»/«password»/«код»/«code» и только если в нём есть цифра:
 * иначе это обычное слово.
 *
 * Код отдаётся одними цифрами, без «G-» и пробелов: поле ввода кода
 * почти всегда принимает только цифры.
 *
 * ── Номера телефонов ([phones]) ────────────────────────────────────────────
 * 7–15 цифр с «+», пробелами, скобками и дефисами, как их пишут люди:
 * «+7 900 123-45-67», «8 (800) 555-35-35». Номер отдаётся как написан —
 * копирование кладёт в буфер то, что человек видит.
 *
 * Чистый Kotlin без Android — проверяется модульными тестами.
 */
object OtpDetector {

    /** Слова-признаки кода, в нижнем регистре; поиск — по вхождению. */
    private val KEYWORDS = listOf(
        "код", "пароль", "пин", "одноразов", "подтвержд",
        "code", "password", "passcode", "passwort", "otp", "pin", "verif",
        "código", "codice", "kod", "parol", "mot de passe", "bestätigung", "token", "токен"
    )

    /** Валюта рядом с числом — это сумма, а не код. */
    private val CURRENCY = Regex(
        """^\s*(?:₽|руб|р\.|р\b|rub|usd|eur|€|\$|£|¥|₸|₴|br|сум|тг)""",
        RegexOption.IGNORE_CASE
    )
    private val CURRENCY_BEFORE = Regex("""(?:₽|\$|€|£|¥|руб\.?|rub|usd|eur)\s*$""", RegexOption.IGNORE_CASE)

    /** 4–8 цифр, «G-123456», «123 456» / «123-456». Соседи проверяются отдельно. */
    private val NUMERIC = Regex("""(?:[A-Za-z]{1,2}-)?(\d{3}[ -]\d{3}|\d{4,8})""")

    /** Буквенно-цифровой пароль сразу после слова-признака. */
    private val ALNUM_AFTER_WORD = Regex(
        """(?:пароль|password|passcode|код|code)\s*(?:[:：—–-]|is|—)?\s*([A-Za-z0-9]{4,12})(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE
    )

    /** Номер телефона: «+», цифры, пробелы, скобки, дефисы; 7–15 цифр. */
    private val PHONE = Regex("""(?<![\w*+])\+?\d[\d ()\-]{5,20}\d(?!\w)""")

    /**
     * Код подтверждения в [text] или null. Только цифры для числового
     * кода, как есть — для буквенно-цифрового пароля.
     */
    fun code(text: String): String? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val keywordAt = KEYWORDS.flatMap { word -> indicesOf(lower, word).map { it to word.length } }
        if (keywordAt.isEmpty()) return null

        // Буквенно-цифровой пароль: «пароль: aB3dE9» — только если не одни цифры
        ALNUM_AFTER_WORD.findAll(text).forEach { m ->
            val token = m.groupValues[1]
            if (token.any { it.isDigit() } && token.any { it.isLetter() } && !looksLikeWord(token)) {
                return token
            }
        }

        val candidates = NUMERIC.findAll(text).mapNotNull { m ->
            val start = m.range.first
            val end = m.range.last + 1
            val digits = m.groupValues[1].filter { it.isDigit() }
            if (digits.length !in 4..8) return@mapNotNull null
            if (!isolated(text, start, end)) return@mapNotNull null
            if (CURRENCY.containsMatchIn(text.substring(end).take(6))) return@mapNotNull null
            if (CURRENCY_BEFORE.containsMatchIn(text.substring(0, start).takeLast(6))) return@mapNotNull null
            if (partOfPhone(text, start, end)) return@mapNotNull null
            // Расстояние до ближайшего слова-признака; после слова — чуть ближе
            val distance = keywordAt.minOf { (at, len) ->
                if (start >= at + len) (start - (at + len)) * 2 else (at - end).coerceAtLeast(0) * 2 + 1
            }
            Triple(digits, distance, start)
        }.toList()
        return candidates.minWithOrNull(compareBy({ it.second }, { it.third }))?.first
    }

    /** Номера телефонов в [text], как написаны, без повторов. */
    fun phones(text: String): List<String> =
        PHONE.findAll(text)
            .map { it.value.trim() }
            .filter { it.count(Char::isDigit) in 7..15 }
            // «05.11.2026 12:30» сюда не попадает (точки и двоеточия),
            // а «1500 2000 3000» — три суммы через пробел — отсекает
            // требование: группы цифр без «+» и скобок длиннее двух
            .filter { candidate -> candidate.startsWith("+") || candidate.contains('(') || candidate.contains('-') || looksLikeSolidNumber(candidate) }
            .distinct()
            .toList()

    // ── Внутреннее ─────────────────────────────────────────────────────────

    private fun indicesOf(text: String, word: String): List<Int> {
        val out = ArrayList<Int>()
        var i = text.indexOf(word)
        while (i >= 0) {
            out += i
            i = text.indexOf(word, i + 1)
        }
        return out
    }

    /**
     * Число стоит отдельно: не прилеплено к буквам и цифрам, не часть
     * даты, времени, дроби, маски карты («*1234») или «+7…».
     */
    private fun isolated(text: String, start: Int, end: Int): Boolean {
        val before = text.getOrNull(start - 1)
        val before2 = text.getOrNull(start - 2)
        val after = text.getOrNull(end)
        val after2 = text.getOrNull(end + 1)
        if (before != null && (before.isLetterOrDigit() || before == '*' || before == '+' || before == '#')) return false
        if (after != null && after.isLetterOrDigit()) return false
        // 05.11.2026, 12:30, 3/4, 1,5 — цифра по другую сторону разделителя
        if (before != null && before in ".,:/" && before2?.isDigit() == true) return false
        if (after != null && after in ".,:/" && after2?.isDigit() == true) return false
        return true
    }

    /** Кандидат — кусок номера телефона вокруг него. */
    private fun partOfPhone(text: String, start: Int, end: Int): Boolean =
        PHONE.findAll(text).any { m ->
            m.range.first <= start && m.range.last + 1 >= end &&
                m.value.count(Char::isDigit) >= 9 &&
                m.value.count(Char::isDigit) > text.substring(start, end).count(Char::isDigit)
        }

    /** «aB3dE9» — пароль; «Sber1» — тоже; «vk2025» — пароль. Слово — только буквы. */
    private fun looksLikeWord(token: String): Boolean = token.all { it.isLetter() }

    /**
     * Цифры подряд без пробелов — номер. Через пробелы — только 10–12 цифр
     * с короткой первой группой: «8 800 000 00 00» — номер,
     * «1500 2000 3000» — три суммы.
     */
    private fun looksLikeSolidNumber(candidate: String): Boolean {
        if (candidate.none { it == ' ' }) return true
        val groups = candidate.split(' ').filter { it.isNotEmpty() }
        return groups.first().length <= 3 && candidate.count(Char::isDigit) in 10..12
    }
}
