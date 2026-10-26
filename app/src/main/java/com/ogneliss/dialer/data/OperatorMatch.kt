package com.ogneliss.dialer.data

/**
 * «ТОТ ЖЕ ОПЕРАТОР» — МЕТКА В ВЫБОРЕ SIM (1.8.0).
 *
 * Автор выбрал метку в окне выбора карты: звонок внутри сети у многих
 * тарифов дешевле, и человек с двумя картами обычно выбирает «ту, что того
 * же оператора». Сравниваем две строки из разных источников:
 *
 *   карта  — как её называет сама SIM: «MTS RUS», «Beeline», «MegaFon»,
 *            «Tele2», «T2», «Yota»; иногда — по-русски («МТС»);
 *   номер  — короткое имя из реестра Россвязи (NumberingPlan.shortOperatorName):
 *            «МТС», «Билайн», «МегаФон», «Tele2», «Yota».
 *
 * Обе строки сводятся к ключу бренда по словарю. Неизвестный оператор —
 * ключа нет, метки нет: лучше промолчать, чем подсказать неверно.
 *
 * ⚠️ Это подсказка, а не выбор. Реестр знает, КОМУ выдан диапазон номеров;
 * номер мог уйти к другому оператору с сохранением номера (MNP), и тогда
 * метка ошибётся. Поэтому карта сама по метке не выбирается никогда,
 * а под меткой стоит пояснение «по плану нумерации».
 *
 * Чистый Kotlin без Android — проверяется тестом OperatorMatchTest.
 */
object OperatorMatch {

    /**
     * Ключи брендов и их написания. Короткие (до четырёх знаков) сравниваются
     * с целым словом — «т2» не должно находиться внутри «т2000»; длинные —
     * как начало слова («вымпел» в «вымпелком») или вхождение фразы.
     */
    private val BRANDS: List<Pair<String, List<String>>> = listOf(
        "mts" to listOf("мтс", "mts", "мобильные телесистемы", "мобильные теле-системы"),
        "megafon" to listOf("мегафон", "megafon"),
        "beeline" to listOf("билайн", "beeline", "вымпел", "вымпелком"),
        "t2" to listOf("t2", "т2", "tele2", "теле2"),
        "yota" to listOf("yota", "йота", "скартел"),
        "tmobile" to listOf("т-мобайл", "t-mobile", "тинькофф", "tinkoff"),
        "sber" to listOf("сбермобайл", "sbermobile"),
        "rostelecom" to listOf("ростелеком", "rostelecom"),
        "motiv" to listOf("мотив", "motiv")
    )

    /** Ключ бренда по названию; null — оператор не из словаря или строки нет. */
    fun brandKey(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val text = name.lowercase().replace('ё', 'е')
        val words = text.split(Regex("[^\\p{L}\\p{N}-]+")).filter { it.isNotEmpty() }
        return BRANDS.firstOrNull { (_, aliases) ->
            aliases.any { alias ->
                when {
                    ' ' in alias -> text.contains(alias)
                    alias.length <= 4 -> words.any { it == alias }
                    else -> words.any { it.startsWith(alias) }
                }
            }
        }?.first
    }

    /**
     * Та же сеть у карты и у номера.
     *
     * У карты смотрим и имя оператора, и подпись (подпись — то, как человек
     * назвал карту сам или как назвала система: часто это и есть оператор).
     */
    fun same(simCarrier: String?, simLabel: String?, numberOperator: String?): Boolean {
        val number = brandKey(numberOperator) ?: return false
        val sim = brandKey(simCarrier) ?: brandKey(simLabel) ?: return false
        return sim == number
    }
}
