package com.ogneliss.dialer.data

/**
 * «ПАПКИ» ЖУРНАЛА (2.2.2) — как папки в меню Ogneliss Mail.
 *
 * В почте «Меню» открывает список папок; у журнала вызовов папок нет,
 * но есть то, что люди открывают так же часто: пропущенные, входящие,
 * исходящие, незнакомые. Это готовые наборы того же фильтра журнала
 * ([CallLogFilter]) — папка лишь ставит фильтр одним касанием. Сложный
 * отбор (карта, период, с заметкой) — по-прежнему в «Фильтрах» шапки;
 * он папкой не считается ([of] вернёт null), и в меню ничего не выделено.
 *
 * «Ждут ответа» — пропущенные, на которые не перезвонили: ради них
 * журнал чаще всего и открывают.
 *
 * Чистый Kotlin — проверяется модульными тестами.
 */
enum class JournalFolder {
    ALL, MISSED, AWAITING, INCOMING, OUTGOING, UNKNOWN;

    /** Фильтр журнала для этой папки. */
    fun filter(): CallLogFilter = when (this) {
        ALL -> CallLogFilter()
        MISSED -> CallLogFilter(types = setOf(CallType.MISSED))
        AWAITING -> CallLogFilter(awaitingCallbackOnly = true)
        INCOMING -> CallLogFilter(types = setOf(CallType.INCOMING))
        OUTGOING -> CallLogFilter(types = setOf(CallType.OUTGOING))
        UNKNOWN -> CallLogFilter(unknownOnly = true)
    }

    companion object {
        /** Папка, которой соответствует фильтр; null — свой набор условий. */
        fun of(filter: CallLogFilter): JournalFolder? = entries.firstOrNull { it.filter() == filter }
    }
}
