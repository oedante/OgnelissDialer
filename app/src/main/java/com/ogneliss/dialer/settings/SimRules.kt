package com.ogneliss.dialer.settings

import com.ogneliss.dialer.data.OperatorMatch
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * КАРТА ПО НОМЕРУ — ПРАВИЛА ВЫБОРА SIM (2.9.11).
 *
 * Автор, 26.11.2026: «Пользователи просят возможность звонков с разных сим-карт по
 * маске номера» — автоматически выбирать карту по коду города, оператора или
 * префиксу, как специализированные менеджеры SIM. И следом: «в список правил
 * добавим возможность выбирать не только маску номера, но и регион из нашей базы.
 * Например „Краснодар*“, куда войдут и Краснодар, и Краснодарский край, или
 * „Билайн“, „МТС“».
 *
 * Правило — одна строка и карта. Строка бывает двух видов, вид — по ней самой:
 *
 * НАЧАЛО НОМЕРА (только цифры и знаки номера): «+7 495 → SIM 1», «8 800 → SIM 2».
 *  - «+7 495», «7495», «8 495» — одно и то же (8 в начале — междугородний префикс
 *    России, сводится к 7); «495», «918» — без кода страны;
 *  - «*», «?», «x», «х» — любая цифра: «+7 9*1»; пробелы, скобки, дефисы не важны.
 *
 * РЕГИОН ИЛИ ОПЕРАТОР (есть буквы): по реестру Россвязи (NumberingPlan) — тот же
 * регион и оператор, что в строке журнала.
 *  - Регион — слово или фраза в названии региона номера: «Москва» — «г. Москва»,
 *    «г. Москва и Московская область», но не «Московская область»; «*» — любые
 *    буквы: «Краснодар*» — и «г. Краснодар», и «Краснодарский край»;
 *  - оператор — по бренду (OperatorMatch): «Билайн», «Beeline» и «Вымпелком» —
 *    один оператор; «МТС», «Tele2», «Т2», «Yota»…; незнакомый — по названию, как регион.
 *  - ⚠️ Реестр знает, кому ВЫДАН номер. Номер мог уйти к другому оператору (MNP)
 *    или уехать с владельцем в другой регион — правило по нему ошибётся. Поэтому
 *    по оператору карта сама не выбирается (OperatorMatch, 1.8.0), а здесь человек
 *    сам решил, что так ему удобнее; оговорка — в «Вопросах и ответах».
 *
 * ЧТО ГЛАВНЕЕ, ЕСЛИ ПОДХОДЯТ НЕСКОЛЬКО. Точнее — главнее: у начала номера точность —
 * число цифр («+7 495 12» точнее «+7 495»), у региона и оператора — как у кода
 * города с «+7» (4 цифры): точнее «+7» и «918», грубее «+7 495 1». Поровну — то,
 * что выше в списке.
 *
 * Где действуют и что главнее правил — разбор у SimRouter. Здесь — только разбор,
 * чистый Kotlin (проверяется SimRulesTest).
 */
data class SimRule(
    /** Строка правила как её написал человек: так и показываем. */
    val mask: String,
    /** Слот карты: 0 — SIM 1, 1 — SIM 2. */
    val slot: Int
)

/** Регион и оператор номера по реестру — для правил с названием. */
data class NumberFacts(val region: String?, val operator: String?)

object SimRules {

    /** Больше правил не храним: список для пальца, а не для базы. */
    const val MAX_RULES = 50

    /** Длина строки правила — с запасом на «Республика Северная Осетия*». */
    const val MAX_MASK_LENGTH = 40

    /** Точность правила с названием — как у кода города с «+7» (см. выше). */
    private const val NAME_WEIGHT = 4

    /** Знак «любая цифра» во внутреннем образце номера. */
    private const val ANY = '?'
    private val WILDCARDS = setOf('*', '?', 'x', 'X', 'х', 'Х')
    private val SEPARATORS = setOf(' ', '-', '(', ')', '.', ' ')

    /**
     * Образец начала номера: цифры и [ANY], без «+» и разделителей; 8 в начале — 7.
     * null — это не начало номера (есть буквы, нет цифр, лишние знаки, слишком длинно).
     */
    fun pattern(mask: String): String? {
        val text = mask.trim()
        if (text.isEmpty() || text.length > MAX_MASK_LENGTH) return null
        val plus = text.startsWith('+')
        val body = StringBuilder()
        for ((i, c) in text.withIndex()) {
            when {
                i == 0 && c == '+' -> Unit
                c.isDigit() -> body.append(c)
                c in WILDCARDS -> body.append(ANY)
                c in SEPARATORS -> Unit
                else -> return null
            }
        }
        if (body.none { it.isDigit() }) return null
        // «8 495» — междугородний префикс России: то же, что «+7 495»
        if (!plus && body.first() == '8') body.setCharAt(0, '7')
        return body.toString()
    }

    /**
     * Образец названия (регион или оператор): строчные, «ё» — «е», пробелы
     * схлопнуты. null — не название: нет букв, есть «|» или перевод строки
     * (ими разделены правила при хранении), слишком длинно.
     */
    fun namePattern(mask: String): String? {
        val text = mask.trim()
        if (text.isEmpty() || text.length > MAX_MASK_LENGTH) return null
        if ('|' in text || '\n' in text || '\r' in text) return null
        if (text.none { it.isLetter() && it !in WILDCARDS_LETTERS }) return null
        return normalizeName(text)
    }

    /** «x» и «х» — знаки «любая цифра» в номере, а не буквы названия. */
    private val WILDCARDS_LETTERS = setOf('x', 'X', 'х', 'Х')

    fun isValid(mask: String): Boolean = pattern(mask) != null || namePattern(mask) != null

    /** Правило с названием (регион, оператор), а не начало номера. */
    fun isName(mask: String): Boolean = pattern(mask) == null && namePattern(mask) != null

    private fun normalizeName(text: String): String =
        text.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ").trim()

    /**
     * Чем номер можно сравнить с маской: цифры как набраны, международный вид
     * (79181234567) и, у российских, — без кода страны (9181234567).
     */
    internal fun forms(number: String): Set<String> {
        val raw = number.filter(Char::isDigit)
        val e164 = runCatching { PhoneFormatter.toE164(number) }.getOrDefault(number).filter(Char::isDigit)
        return buildSet {
            if (raw.isNotEmpty()) {
                add(raw)
                // «8 918…» как набрано — тоже российский номер
                if (raw.length == 11 && raw.first() == '8') add("7" + raw.drop(1))
            }
            if (e164.isNotEmpty()) {
                add(e164)
                if (e164.length == 11 && e164.first() == '7') add(e164.drop(1))
            }
        }
    }

    private fun matches(form: String, pattern: String): Boolean =
        form.length >= pattern.length &&
            pattern.indices.all { i -> pattern[i] == ANY || pattern[i] == form[i] }

    /**
     * Слово или фраза [name] (образец [namePattern]) — в строке [text] с границы слова
     * до границы слова; «*» — любые буквы, «?» — одна.
     */
    internal fun nameMatches(name: String, text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val body = buildString {
            for (c in name) when (c) {
                '*' -> append("[^,]*?")
                '?' -> append('.')
                else -> append(Regex.escape(c.toString()))
            }
        }
        val rx = Regex("(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])")
        return rx.containsMatchIn(normalizeName(text))
    }

    /** Название подходит к оператору: по бренду, если он известен, иначе по тексту. */
    internal fun operatorMatches(name: String, operator: String?): Boolean {
        if (operator.isNullOrBlank()) return false
        val ruleBrand = OperatorMatch.brandKey(name.replace("*", " ").replace("?", " "))
        if (ruleBrand != null) return ruleBrand == OperatorMatch.brandKey(operator)
        return nameMatches(name, operator)
    }

    /**
     * Подходящее правило для [number]; null — ни одно. [facts] — регион и оператор
     * номера; спрашиваются, только если есть правила с названием, и один раз.
     */
    fun match(number: String, rules: List<SimRule>, facts: () -> NumberFacts? = { null }): SimRule? {
        if (rules.isEmpty() || number.isBlank()) return null
        val forms = forms(number)
        if (forms.isEmpty()) return null
        val known by lazy { runCatching(facts).getOrNull() }
        return rules.withIndex()
            .mapNotNull { (index, rule) ->
                val digits = pattern(rule.mask)
                val weight = when {
                    digits != null ->
                        if (forms.any { matches(it, digits) }) digits.count(Char::isDigit) else null
                    else -> namePattern(rule.mask)?.let { name ->
                        val f = known
                        val hit = f != null && (nameMatches(name, f.region) || operatorMatches(name, f.operator))
                        if (hit) NAME_WEIGHT else null
                    }
                } ?: return@mapNotNull null
                Triple(index, rule, weight)
            }
            // Точнее — главнее; поровну — что выше в списке
            .minWithOrNull(compareByDescending<Triple<Int, SimRule, Int>> { it.third }.thenBy { it.first })
            ?.second
    }

    /*
     * ХРАНЕНИЕ: строка правил, по одному в строке, «слот|маска». В правиле нет ни «|»,
     * ни перевода строки — их не пропускают [pattern] и [namePattern].
     */
    fun format(rules: List<SimRule>): String =
        rules.filter { isValid(it.mask) && it.slot in 0..1 }
            .take(MAX_RULES)
            .joinToString("\n") { "${it.slot}|${it.mask.trim()}" }

    fun parse(raw: String?): List<SimRule> =
        raw.orEmpty().lineSequence()
            .mapNotNull { line ->
                val slot = line.substringBefore('|', "").toIntOrNull() ?: return@mapNotNull null
                val mask = line.substringAfter('|', "")
                SimRule(mask, slot).takeIf { slot in 0..1 && isValid(mask) }
            }
            .take(MAX_RULES)
            .toList()
}
