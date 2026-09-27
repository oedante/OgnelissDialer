package com.ogneliss.dialer.util

/**
 * СПЕЦИАЛЬНЫЕ СИМВОЛЫ В НАБИРАЕМОМ НОМЕРЕ.
 *
 * ── Что это и зачем ────────────────────────────────────────────────────────
 * Голосовые меню банков и операторов требуют донабора: «нажмите 1
 * для баланса, затем введите код». Каждый раз слушать меню и тыкать
 * цифры вручную утомительно.
 *
 * Два стандартных символа телефонии решают это:
 *
 *   ЗАПЯТАЯ (,) — ПАУЗА. Телефония ждёт около двух секунд, затем
 *     автоматически набирает следующие цифры. Годится, когда меню
 *     отвечает предсказуемо быстро.
 *
 *   ТОЧКА С ЗАПЯТОЙ (;) — ОЖИДАНИЕ. Набор останавливается, телефон
 *     показывает запрос «отправить тоны?». Пользователь нажимает,
 *     когда услышит нужный пункт меню. Надёжнее паузы, когда время
 *     ответа непредсказуемо.
 *
 * Пример: `+74951234567,,1,2;1234` — дозвон, две паузы, пункт 1,
 * пункт 2, затем ожидание подтверждения и ввод кода.
 *
 * ── Почему это отдельный класс ─────────────────────────────────────────────
 * Разбор строки набора влияет на то, что показывается пользователю
 * и что уходит в телефонию. Ошибка здесь приводит к набору неверного
 * номера — форматировать номер с запятыми как обычный нельзя.
 */
object DialString {

    const val PAUSE = ','
    const val WAIT = ';'

    /** Есть ли в строке специальные символы. */
    fun hasSpecialCharacters(input: String): Boolean =
        input.any { it == PAUSE || it == WAIT }

    /**
     * Основной номер — часть до первого специального символа.
     * Именно её нужно искать в контактах и форматировать.
     */
    fun primaryNumber(input: String): String =
        input.takeWhile { it != PAUSE && it != WAIT }

    /** Хвост донабора после основного номера. */
    fun postDialSequence(input: String): String =
        input.dropWhile { it != PAUSE && it != WAIT }

    /**
     * Отображение строки набора.
     *
     * Основной номер форматируется как обычно, хвост показывается
     * как есть. Форматировать всю строку целиком нельзя: libphonenumber
     * не понимает запятых и вернёт мусор либо потеряет хвост.
     */
    fun formatForDisplay(input: String, region: String = PhoneFormatter.defaultRegion): String {
        if (!hasSpecialCharacters(input)) return PhoneFormatter.format(input, region)

        val primary = primaryNumber(input)
        val tail = postDialSequence(input)
        return PhoneFormatter.format(primary, region) + tail
    }

    /**
     * Человекочитаемое пояснение хвоста.
     * Показывается под полем ввода: символы «,» и «;» ничего не говорят
     * тому, кто их не знает, а знают их немногие.
     */
    fun describeSequence(input: String): List<Step> {
        val tail = postDialSequence(input)
        if (tail.isEmpty()) return emptyList()

        val steps = mutableListOf<Step>()
        val digits = StringBuilder()

        fun flush() {
            if (digits.isNotEmpty()) {
                steps += Step.Digits(digits.toString())
                digits.clear()
            }
        }

        tail.forEach { ch ->
            when (ch) {
                PAUSE -> { flush(); steps += Step.Pause }
                WAIT -> { flush(); steps += Step.Wait }
                else -> digits.append(ch)
            }
        }
        flush()
        return steps
    }

    sealed interface Step {
        /** Пауза около двух секунд. */
        data object Pause : Step
        /** Ожидание подтверждения пользователя. */
        data object Wait : Step
        /** Цифры, которые будут отправлены тонами. */
        data class Digits(val value: String) : Step
    }
}
