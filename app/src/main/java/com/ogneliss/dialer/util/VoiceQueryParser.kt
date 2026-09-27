package com.ogneliss.dialer.util

/**
 * РАЗБОР ГОЛОСОВОГО ЗАПРОСА.
 *
 * Назначение: превратить распознанную фразу в намерение — набрать номер
 * или найти контакт по имени.
 *
 * ── Что распознаватель отдаёт на самом деле ────────────────────────────────
 * Не «+79001234567», а фразу вида «позвони Андрею», «набери восемь девятьсот
 * сто двадцать три сорок пять шестьдесят семь» или просто «Андрей Клинов».
 * Причём числа приходят словами, а не цифрами: распознаватель не знает,
 * что человек диктует телефон.
 *
 * ── Почему это отдельный класс без зависимостей ────────────────────────────
 * Логика неочевидна (числительные, командные слова, смешанный ввод),
 * а проверять её вручную означало бы каждый раз диктовать в микрофон.
 * Здесь чистые функции, покрытые тестами.
 */
object VoiceQueryParser {

    /** Что пользователь хотел. */
    sealed interface Query {
        /** Продиктован номер. */
        data class Number(val digits: String) : Query

        /** Продиктовано имя — нужно искать по контактам. */
        data class Name(val text: String) : Query

        /** Ничего осмысленного. */
        data object Empty : Query
    }

    /**
     * Командные слова, которые нужно отбросить перед поиском имени.
     * «Позвони Андрею» должно искать «Андрею», а не всю фразу целиком.
     */
    private val COMMAND_WORDS = setOf(
        // русские
        "позвони", "позвонить", "набери", "набрать", "вызов", "вызови",
        "звонок", "телефон", "номер", "соедини",
        // английские
        "call", "dial", "phone", "ring"
    )

    /**
     * Числительные словами. Распознаватель часто выдаёт именно их,
     * особенно когда человек диктует медленно, по одной цифре.
     */
    private val DIGIT_WORDS: Map<String, String> = mapOf(
        "ноль" to "0", "нуль" to "0", "zero" to "0", "oh" to "0",
        "один" to "1", "одна" to "1", "раз" to "1", "one" to "1",
        "два" to "2", "две" to "2", "two" to "2",
        "три" to "3", "three" to "3",
        "четыре" to "4", "four" to "4",
        "пять" to "5", "five" to "5",
        "шесть" to "6", "six" to "6",
        "семь" to "7", "seven" to "7",
        "восемь" to "8", "eight" to "8",
        "девять" to "9", "nine" to "9"
    )

    /** Составные числительные: «двадцать», «сто» и т. п. */
    private val COMPOSITE_WORDS: Map<String, String> = mapOf(
        "десять" to "10", "одиннадцать" to "11", "двенадцать" to "12",
        "тринадцать" to "13", "четырнадцать" to "14", "пятнадцать" to "15",
        "шестнадцать" to "16", "семнадцать" to "17", "восемнадцать" to "18",
        "девятнадцать" to "19", "двадцать" to "20", "тридцать" to "30",
        "сорок" to "40", "пятьдесят" to "50", "шестьдесят" to "60",
        "семьдесят" to "70", "восемьдесят" to "80", "девяносто" to "90",
        "сто" to "100", "двести" to "200", "триста" to "300",
        "четыреста" to "400", "пятьсот" to "500", "шестьсот" to "600",
        "семьсот" to "700", "восемьсот" to "800", "девятьсот" to "900"
    )

    /**
     * Минимум цифр, чтобы считать ввод номером.
     * Короткие последовательности — скорее часть имени («Т-34»)
     * или ошибка распознавания.
     */
    private const val MIN_NUMBER_DIGITS = 3

    fun parse(spoken: String?): Query {
        if (spoken.isNullOrBlank()) return Query.Empty

        val words = spoken.lowercase()
            .replace(Regex("[.,!?]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .filterNot { it in COMMAND_WORDS }

        if (words.isEmpty()) return Query.Empty

        val digits = extractDigits(words)
        if (digits.length >= MIN_NUMBER_DIGITS) return Query.Number(digits)

        val name = words.joinToString(" ")
        return if (name.isBlank()) Query.Empty else Query.Name(name)
    }

    /**
     * Сборка цифр из слов.
     *
     * Обрабатывает три случая одновременно:
     *  • готовые цифры в тексте («8 900 1234567»)
     *  • простые числительные («восемь девять ноль ноль»)
     *  • составные («девятьсот двадцать три» → «923»)
     *
     * Составные складываются, пока идут подряд: «девятьсот двадцать три»
     * это одно число 923, а не три отдельных.
     */
    private fun extractDigits(words: List<String>): String {
        val result = StringBuilder()
        var pendingSum = 0
        var hasPending = false

        fun flushPending() {
            if (hasPending) {
                result.append(pendingSum)
                pendingSum = 0
                hasPending = false
            }
        }

        for (word in words) {
            val plainDigits = word.filter { it.isDigit() }

            when {
                // Слово целиком из цифр
                plainDigits.length == word.length && plainDigits.isNotEmpty() -> {
                    flushPending()
                    result.append(plainDigits)
                }
                // Составное числительное: копим сумму
                COMPOSITE_WORDS.containsKey(word) -> {
                    pendingSum += COMPOSITE_WORDS.getValue(word).toInt()
                    hasPending = true
                }
                // Простое числительное
                DIGIT_WORDS.containsKey(word) -> {
                    val value = DIGIT_WORDS.getValue(word).toInt()
                    if (hasPending) {
                        pendingSum += value
                    } else {
                        result.append(value)
                    }
                }
                // Слово с цифрами внутри — берём цифры
                plainDigits.isNotEmpty() -> {
                    flushPending()
                    result.append(plainDigits)
                }
                // Обычное слово прерывает накопление числа
                else -> flushPending()
            }
        }
        flushPending()
        return result.toString()
    }

    /**
     * Похоже ли на телефонный номер по длине.
     * Российский мобильный — 10 или 11 цифр; короткие сервисные — 3–6.
     */
    fun looksLikePhoneNumber(digits: String): Boolean =
        digits.length in 3..6 || digits.length in 10..15
}
