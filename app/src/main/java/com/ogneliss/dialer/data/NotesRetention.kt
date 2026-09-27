package com.ogneliss.dialer.data

/**
 * СРОК ЖИЗНИ ЗАМЕТОК.
 *
 * ── Зачем удалять заметки автоматически ────────────────────────────────────
 * Заметка «доставка мебели» полезна неделю, пока ждёшь доставку.
 * Через год она мусор: номер давно переиспользован оператором, а строка
 * продолжает всплывать в поиске и мешать.
 *
 * Но заметка «мошенники» полезна всегда, и стирать её по расписанию —
 * прямой вред.
 *
 * ── Отсюда два измерения настройки ─────────────────────────────────────────
 * СРОК — через сколько считать заметку устаревшей.
 * ОБЛАСТЬ — чьи заметки удалять.
 *
 * Область важнее срока. По умолчанию чистятся только заметки к номерам
 * ВНЕ телефонной книги: если человек занесён в контакты, заметка о нём
 * относится к живой связи, а не к разовому делу.
 *
 * ── Что не удаляется никогда ───────────────────────────────────────────────
 * Заметки с метками «спам» и «мошенники». Их писали именно затем,
 * чтобы помнить долго, и срок давности тут не работает: номер
 * мошенников остаётся номером мошенников.
 */
object NotesRetention {

    /** Через сколько заметка считается устаревшей. */
    enum class Period(val days: Int) {
        /** Не удалять никогда — состояние по умолчанию. */
        NEVER(0),
        MONTH(30),
        HALF_YEAR(182),
        YEAR(365);

        companion object {
            fun from(name: String?): Period =
                entries.firstOrNull { it.name == name } ?: NEVER
        }
    }

    /** Чьи заметки чистить. */
    enum class Scope {
        /**
         * Только номера вне телефонной книги.
         *
         * По умолчанию: заметка о человеке из контактов относится
         * к живой связи, а не к разовому делу, и срок давности
         * к ней неприменим.
         */
        UNKNOWN_ONLY,

        /** Все заметки, включая контакты. */
        ALL;

        companion object {
            fun from(name: String?): Scope =
                entries.firstOrNull { it.name == name } ?: UNKNOWN_ONLY
        }
    }

    /**
     * Метки, защищающие заметку от удаления.
     *
     * Их ставят именно затем, чтобы помнить долго: номер мошенников
     * остаётся номером мошенников и через год.
     */
    val PROTECTED_TAGS = setOf("spam", "fraud")

    /**
     * Какие заметки удалить.
     *
     * @param knownNumbers номера из телефонной книги
     * @return номера заметок, подлежащих удалению
     */
    fun selectForDeletion(
        notes: Map<String, NumberNote>,
        period: Period,
        scope: Scope,
        knownNumbers: Set<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): Set<String> {
        if (period == Period.NEVER) return emptySet()

        val threshold = nowMillis - period.days.toLong() * DAY_MILLIS

        return notes.filterValues { note ->
            // Защищённые метки не удаляются ни при каких настройках
            if (note.tags.any { it in PROTECTED_TAGS }) return@filterValues false

            // Область действия
            if (scope == Scope.UNKNOWN_ONLY && note.e164 in knownNumbers) {
                return@filterValues false
            }

            note.updatedAt in 1 until threshold
        }.keys
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
