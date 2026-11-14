package com.ogneliss.dialer.ui.components

/**
 * ПОРЯДОК И ПОКАЗ ПАПОК МЕНЮ — ОДНО ПРАВИЛО ДЛЯ ЖУРНАЛА И КОНТАКТОВ (2.5.0).
 *
 * Автор, по снимкам меню: «почему настройки панели только в SMS?». У SMS
 * порядок и показ папок настраивались с 2.2.0 (SmsFolders), у журнала
 * и контактов — нет. Теперь у всех трёх значок «Настроить папки» справа
 * от «ПАПКИ»; журнал и контакты хранят порядок здесь, тем же форматом,
 * что SMS: «ALL,MISSED,-UNKNOWN,…» — порядок, минус — скрыта.
 *
 * Первая папка («Все») не скрывается: иначе часть списка пропала бы из виду.
 * Папки новых версий, которых нет в строке, добавляются в конец видимыми;
 * незнакомые имена (папку убрали) пропускаются. Чистый Kotlin — проверки
 * в FolderOrderTest.
 */
object FolderOrder {

    /** Строка настройки → порядок и показ; [all] — все папки по умолчанию, [fixed] — не скрывается. */
    fun parse(raw: String?, all: List<String>, fixed: String): List<Pair<String, Boolean>> {
        if (raw.isNullOrBlank()) return all.map { it to true }
        val seen = LinkedHashMap<String, Boolean>()
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            val hidden = token.startsWith("-")
            val name = token.removePrefix("-")
            if (name !in all || name in seen) return@forEach
            seen[name] = !hidden || name == fixed
        }
        all.filter { it !in seen }.forEach { seen[it] = true }
        return seen.toList()
    }

    fun encode(list: List<Pair<String, Boolean>>, fixed: String): String =
        list.joinToString(",") { (name, visible) -> if (visible || name == fixed) name else "-$name" }

    /** Видимые по порядку. */
    fun visible(raw: String?, all: List<String>, fixed: String): List<String> =
        parse(raw, all, fixed).filter { it.second }.map { it.first }

    /** Сдвинуть на [delta] (−1 — выше, +1 — ниже); за край — как было. */
    fun move(list: List<Pair<String, Boolean>>, index: Int, delta: Int): List<Pair<String, Boolean>> {
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return list
        return list.toMutableList().apply { add(target, removeAt(index)) }
    }
}
