package com.ogneliss.dialer.util

import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * СРАВНЕНИЕ НАБРАННЫХ ЦИФР С НОМЕРОМ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ЭТО НУЖНО
 * ══════════════════════════════════════════════════════════════════════════
 * Один и тот же телефон человек набирает по-разному. В России —
 * «8 961 777-88-99» или «+7 961 777-88-99»; в книге он может лежать
 * в третьем виде. Поиск сравнивал голые цифры подстрокой, и «8961…»
 * не находил номер, записанный как «+7961…»: строка «8961777…»
 * не встречается внутри «7961777…».
 *
 * Для человека это одно и то же число, и поиск обязан так считать.
 *
 * ── Как приводим ───────────────────────────────────────────────────────────
 * К «значащей части» — тому, что остаётся без кода страны и без
 * междугородного префикса:
 *
 *     +7 961 777-88-99  →  9617778899
 *     8 961 777-88-99   →  9617778899
 *     961 777-88-99     →  9617778899
 *
 * ── Почему не просто «отрезать первую цифру» ───────────────────────────────
 * Междугородный префикс разный: в России «8», в Германии и Франции
 * «0», в США его нет вовсе. Спрашиваем его у libphonenumber
 * для страны SIM-карты — getNddPrefixForRegion. Захардкоженная
 * восьмёрка сломала бы немецкий номер 030…, у которого ноль —
 * часть местной записи.
 */
object DialDigits {

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /** Только цифры. */
    fun digits(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Значащая часть номера: без кода страны и междугородного префикса.
     *
     * @param raw номер или часть номера — годится и неполный набор
     *
     * Полный номер разбирается libphonenumber: он знает длину кода
     * страны для каждого плана нумерации. Неполный разобрать нельзя,
     * поэтому у него снимается только междугородный префикс —
     * этого хватает, потому что дальше сравнение идёт подстрокой.
     */
    fun significant(raw: String, region: String = PhoneFormatter.defaultRegion): String {
        val digits = digits(raw)
        if (digits.isEmpty()) return digits

        val parsed = runCatching { util.parse(raw, region) }.getOrNull()
        if (parsed != null && util.isValidNumber(parsed)) {
            return parsed.nationalNumber.toString()
        }

        val ndd = runCatching { util.getNddPrefixForRegion(region, true) }.getOrNull()
        return if (!ndd.isNullOrEmpty() && digits.length > ndd.length && digits.startsWith(ndd)) {
            digits.removePrefix(ndd)
        } else {
            digits
        }
    }

    /**
     * Совпадает ли набранное с номером.
     *
     * Сравниваются обе формы: сырые цифры (человек мог набрать номер
     * с кодом страны, и тогда совпадение точнее) и значащие части.
     */
    fun matches(number: String, queryDigits: String): Boolean {
        if (queryDigits.isEmpty()) return false

        if (digits(number).contains(queryDigits)) return true

        val significantQuery = significant(queryDigits)
        if (significantQuery.isEmpty()) return false

        return significant(number).contains(significantQuery)
    }
}
