package com.ogneliss.dialer.data

import com.ogneliss.dialer.util.PhoneFormatter

/**
 * РАЗБОР И ПРОВЕРКА ШАБЛОНОВ БЛОКИРОВКИ.
 *
 * Назначение: превратить то, что ввёл пользователь, в корректный шаблон
 * и объяснить, если ввод бессмысленный.
 *
 * ── Почему это отдельный класс ─────────────────────────────────────────────
 * Пользователь вводит шаблон руками, и ошибиться легко. Опасны два случая:
 *
 * 1. СЛИШКОМ ШИРОКИЙ ШАБЛОН. «*» или «+7*» заблокируют вообще всё,
 *    включая школу и работу. Человек не сразу поймёт, почему телефон
 *    замолчал, и решит, что приложение сломалось.
 *
 * 2. ШАБЛОН, КОТОРЫЙ НИКОГДА НЕ СРАБОТАЕТ. Номера сравниваются в формате
 *    E.164 («+7900…»), поэтому введённое «8 (900)…» не совпадёт ни с чем.
 *    Правило будет в списке, выглядеть рабочим и молча не работать.
 *
 * Оба случая ловятся здесь, до сохранения.
 */
object RulePattern {

    /** Минимум значащих цифр, чтобы шаблон не был слишком широким. */
    const val MIN_SIGNIFICANT_DIGITS = 3

    sealed interface Result {
        /** Шаблон корректен. [pattern] — нормализованный вид для хранения. */
        data class Valid(val pattern: String, val isMask: Boolean) : Result

        /** Шаблон опасно широкий: заблокирует почти всё. */
        data class TooBroad(val matchedDigits: Int) : Result

        /** Пустой ввод или только служебные символы. */
        data object Empty : Result

        /** Символы, которых в номере быть не может. */
        data class InvalidCharacters(val characters: String) : Result
    }

    private val ALLOWED = Regex("^[0-9+*?()\\-\\s]+$")

    /**
     * Нормализует ввод пользователя.
     *
     * Обычный номер приводится к E.164 — именно в этом виде происходит
     * сравнение при входящем вызове. Шаблон с маской нормализуется
     * бережно: символы «*» и «?» libphonenumber не понимает, поэтому
     * убираются только разделители.
     */
    fun parse(input: String, region: String = PhoneFormatter.defaultRegion): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.Empty

        if (!ALLOWED.matches(trimmed)) {
            val bad = trimmed.filterNot { ALLOWED.matches(it.toString()) }.toSet()
                .joinToString("")
            return Result.InvalidCharacters(bad)
        }

        val isMask = trimmed.contains('*') || trimmed.contains('?')

        // Значащие цифры — те, что реально сужают шаблон
        val significantDigits = trimmed.count { it.isDigit() }
        if (significantDigits < MIN_SIGNIFICANT_DIGITS) {
            return Result.TooBroad(significantDigits)
        }

        val normalized = if (isMask) {
            normalizeMask(trimmed)
        } else {
            PhoneFormatter.toE164(trimmed, region)
        }

        return Result.Valid(normalized, isMask)
    }

    /**
     * Нормализация шаблона с маской.
     *
     * Убираются скобки, дефисы и пробелы: сравнение идёт с E.164,
     * где их нет. Ведущая «8» заменяется на «+7» по тем же причинам —
     * иначе привычное «8800*» не совпало бы ни с одним номером.
     */
    private fun normalizeMask(input: String): String {
        var s = input.filter { it.isDigit() || it == '+' || it == '*' || it == '?' }

        if (s.startsWith("8") && !s.startsWith("+")) {
            s = "+7" + s.substring(1)
        } else if (!s.startsWith("+") && s.firstOrNull()?.isDigit() == true) {
            // Номер без кода страны: считаем российским
            s = "+7$s"
        }
        return s
    }

    /**
     * Человекочитаемое объяснение, что именно попадёт под шаблон.
     * Показывается до сохранения, чтобы пользователь увидел последствия.
     */
    fun describe(pattern: String): String = when {
        !pattern.contains('*') && !pattern.contains('?') ->
            PhoneFormatter.format(pattern)
        pattern.endsWith("*") ->
            pattern.dropLast(1) + "…"
        else -> pattern
    }

    /**
     * ПРОВЕРКА СОВПАДЕНИЯ ШАБЛОНА С НОМЕРОМ.
     *
     * Единственное место, где это делается: RulesRepository вызывает
     * этот же метод. Раньше он держал свою копию, и копии разошлись бы
     * при первой же правке — а разойтись им нельзя, от них зависит,
     * зазвонит телефон или нет.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЧТО ЗДЕСЬ БЫЛО СЛОМАНО
     * ══════════════════════════════════════════════════════════════════════
     * Прежний код экранировал ВЕСЬ шаблон целиком, а потом пытался
     * вернуть звёздочке смысл заменой подстроки «\*»:
     *
     *     Regex.escape(pattern).replace("\\*", "\\E.*\\Q")
     *
     * Regex.escape заворачивает строку в «\Q…\E» и не ставит обратных
     * слэшей перед отдельными символами — звёздочка внутри остаётся
     * просто звёздочкой. Искомой подстроки «\*» там нет никогда, замена
     * не срабатывает, и «+7800*» превращается в регулярное выражение
     * ^\Q+7800*\E$ — то есть совпадает только с номером, в котором
     * буквально стоит звёздочка.
     *
     * Итог: маски не блокировали НИЧЕГО. Правило сохранялось, было видно
     * в списке, выглядело рабочим — ровно тот случай, от которого
     * предостерегает комментарий в начале этого файла.
     *
     * ── Как теперь ─────────────────────────────────────────────────────────
     * Экранируются только куски между подстановочными знаками, а сами
     * знаки превращаются в «.*» и «.». Проверено на «+7800*»,
     * «+7900123456?» и на номерах, которые совпадать не должны.
     *
     * ⚠️ Тест «нормализованная маска действительно совпадает с номерами»
     * в RulePatternTest на прежнем коде обязан был падать. Значит,
     * набор тестов давно не прогонялся целиком — это стоит проверить
     * отдельно.
     */
    fun matches(pattern: String, e164: String): Boolean {
        if (!pattern.contains('*') && !pattern.contains('?')) return pattern == e164
        return toRegex(pattern).matches(e164)
    }

    /** Шаблон с масками → регулярное выражение. */
    private fun toRegex(pattern: String): Regex {
        val out = StringBuilder("^")
        val literal = StringBuilder()

        fun flush() {
            if (literal.isNotEmpty()) {
                out.append(Regex.escape(literal.toString()))
                literal.clear()
            }
        }

        pattern.forEach { ch ->
            when (ch) {
                '*' -> { flush(); out.append(".*") }
                '?' -> { flush(); out.append(".") }
                else -> literal.append(ch)
            }
        }
        flush()
        return Regex(out.append("$").toString())
    }
}
