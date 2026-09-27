package com.ogneliss.dialer.t9

/**
 * Двухалфавитный T9 (RU + EN) по раскладке, привычной пользователям ExDialer:
 * 2 ABC/АБВГ, 3 DEF/ДЕЖЗ, 4 GHI/ИЙКЛ, 5 JKL/МНОП,
 * 6 MNO/РСТУ, 7 PQRS/ФХЦЧ, 8 TUV/ШЩЪЫ, 9 WXYZ/ЬЭЮЯ.
 */
object T9Engine {

    private val map: Map<Char, Char> = buildMap {
        fun bind(digit: Char, letters: String) = letters.forEach { put(it, digit) }
        bind('2', "abcабвг")
        bind('3', "defдежзё")
        bind('4', "ghiийкл")
        bind('5', "jklмноп")
        bind('6', "mnoрсту")
        bind('7', "pqrsфхцч")
        bind('8', "tuvшщъы")
        bind('9', "wxyzьэюя")
        put('0', '0'); put(' ', '0')
    }

    /** Переводит имя в цифровую строку: "Андрей" -> "236337" */
    fun toDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            val d = map[ch]
            if (d != null) sb.append(d) else if (ch.isDigit()) sb.append(ch)
        }
        return sb.toString()
    }

    fun normalizeNumber(number: String): String = number.filter { it.isDigit() }

    /**
     * Возвращает индекс начала совпадения в исходном тексте (для подсветки) или -1.
     * Приоритет — совпадение с началом любого слова.
     */
    /**
     * КЭШ ПЕРЕВОДА ИМЁН В ЦИФРЫ.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Перевод имени в последовательность цифр — посимвольный обход
     * с поиском по таблице раскладки. Сам по себе он дёшев, но при
     * поиске выполняется для КАЖДОГО контакта на КАЖДУЮ введённую цифру.
     *
     * При тысяче контактов и наборе из пяти цифр это пять тысяч
     * переводов одних и тех же имён — а имена между нажатиями
     * не меняются.
     *
     * ── Почему ограничен ───────────────────────────────────────────────────
     * Телефонная книга редко превышает пару тысяч записей, но полагаться
     * на это нельзя: у людей с корпоративными аккаунтами бывает
     * и больше. 2048 покрывает подавляющее большинство книг целиком,
     * а при переполнении вытесняется самая давняя запись — она
     * с наибольшей вероятностью не участвует в текущем поиске.
     *
     * ── Про потокобезопасность ─────────────────────────────────────────────
     * Поиск идёт в фоновом потоке, индексация T9 — в другом. Доступ
     * синхронизирован: блокировка короткая, а чтение из map несопоставимо
     * дешевле повторного перевода.
     */
    private const val DIGITS_CACHE_SIZE = 2048

    private val digitsCache = object : LinkedHashMap<String, String>(
        DIGITS_CACHE_SIZE, 0.75f, /* accessOrder = */ true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
            size > DIGITS_CACHE_SIZE
    }

    private val cacheLock = Any()

    /** Перевод с кэшированием. Для разовых вызовов используйте toDigits. */
    fun toDigitsCached(text: String): String {
        if (text.length > MAX_CACHED_LENGTH) return toDigits(text)

        synchronized(cacheLock) { digitsCache[text] }?.let { return it }
        val result = toDigits(text)
        synchronized(cacheLock) { digitsCache[text] = result }
        return result
    }

    /**
     * Длиннее этого не кэшируем: длинные строки встречаются редко
     * и занимали бы место в кэше без пользы.
     */
    private const val MAX_CACHED_LENGTH = 64

    /** Сброс кэша. Нужен при смене языка: раскладка может отличаться. */
    fun clearCache() {
        synchronized(cacheLock) { digitsCache.clear() }
    }

    fun matchIndex(query: String, name: String): Int {
        if (query.isEmpty()) return 0
        val words = mutableListOf<Pair<Int, String>>()
        var start = -1
        name.forEachIndexed { i, c ->
            if (c.isLetterOrDigit()) { if (start == -1) start = i }
            else if (start != -1) { words += start to name.substring(start, i); start = -1 }
        }
        if (start != -1) words += start to name.substring(start)

        for ((offset, word) in words) {
            if (toDigitsCached(word).startsWith(query)) return offset
        }
        val idx = toDigitsCached(name).indexOf(query)
        return if (idx >= 0) idx else -1
    }
}
