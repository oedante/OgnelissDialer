package com.ogneliss.dialer.data

/**
 * ФИЛЬТРЫ СПИСКА КОНТАКТОВ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ФИЛЬТРЫ, А НЕ ОТДЕЛЬНЫЙ ЭКРАН АУДИТА
 * ══════════════════════════════════════════════════════════════════════════
 * Прежний вариант — переключатель «предлагать раз в месяц» — плох тем,
 * что до результата нельзя добраться по своей воле. Человек включает
 * настройку и ждёт неизвестно чего; когда предложение наконец приходит,
 * он уже забыл, что его заказывал.
 *
 * Фильтр в списке контактов работает иначе: результат доступен в любой
 * момент, ничего не нужно включать заранее, и находится он там,
 * где человек и так разбирает контакты.
 *
 * ── Про порог «давно» ──────────────────────────────────────────────────────
 * Полгода и год — не круглые числа ради красоты. Полгода это срок,
 * за который «временный» контакт вроде мастера по ремонту точно перестал
 * быть нужным. Год — срок, после которого номер мог быть переиспользован
 * оператором, и контакт стал не просто лишним, а неверным.
 *
 * ⚠️ ВАЖНО: отсутствие ЗВОНКОВ не означает отсутствия общения. С человеком
 * можно ежедневно переписываться и ни разу не позвонить. Поэтому фильтр
 * называется «давно не звонили», а не «ненужные», и никакого массового
 * удаления по нему нет.
 * ══════════════════════════════════════════════════════════════════════════
 */
enum class ContactFilter {

    /** Все контакты. */
    ALL,

    /** Отмеченные пользователем. */
    FAVOURITES,

    /**
     * Те, кому звонят чаще всего.
     *
     * Порог не фиксирован: у одного человека «часто» это десять звонков,
     * у другого два. Берём верхнюю часть распределения, а не абсолютное
     * число.
     */
    FREQUENT,

    /** Без звонков больше полугода. */
    STALE_HALF_YEAR,

    /** Без звонков больше года. */
    STALE_YEAR,

    /**
     * С заметкой.
     *
     * Заметку человек писал сам, и ищет он её обычно тогда, когда забыл,
     * к кому она относилась: «где-то записал, когда привезут мебель».
     * Перебирать ради этого весь список — то же самое, что не иметь
     * заметок вовсе.
     */
    WITH_NOTE,

    /** С назначенным напоминанием перезвонить. */
    WITH_REMINDER;

    companion object {

        /** Доля контактов, считающихся частыми. */
        const val FREQUENT_TOP_FRACTION = 0.2

        /** Минимум звонков, чтобы контакт вообще считался частым. */
        const val FREQUENT_MIN_CALLS = 3

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        fun daysFor(filter: ContactFilter): Int? = when (filter) {
            STALE_HALF_YEAR -> 182
            STALE_YEAR -> 365
            else -> null
        }

        /**
         * Применение фильтра.
         *
         * @param lastCallByE164 время последнего вызова по каждому номеру
         * @param callCountByE164 число вызовов
         */
        fun apply(
            contacts: List<Contact>,
            filter: ContactFilter,
            lastCallByE164: Map<String, Long>,
            callCountByE164: Map<String, Int>,
            nowMillis: Long = System.currentTimeMillis(),
            /** Номера, у которых есть заметка. */
            notedNumbers: Set<String> = emptySet(),
            /** Номера с назначенным напоминанием. */
            remindedNumbers: Set<String> = emptySet()
        ): List<Contact> = when (filter) {

            ALL -> contacts

            FAVOURITES -> contacts.filter { it.starred }

            // Достаточно одного номера с заметкой: у контакта их бывает
            // несколько, а запись обычно привязана к тому, по которому
            // звонили
            WITH_NOTE -> contacts.filter { contact ->
                contact.numbers.any { it.e164 in notedNumbers }
            }

            WITH_REMINDER -> contacts.filter { contact ->
                contact.numbers.any { it.e164 in remindedNumbers }
            }

            FREQUENT -> {
                val counts = contacts.associateWith { contact ->
                    contact.numbers.sumOf { callCountByE164[it.e164] ?: 0 }
                }.filterValues { it >= FREQUENT_MIN_CALLS }

                // Верхняя пятая часть по числу звонков: абсолютный порог
                // не годится, у разных людей разная интенсивность общения
                val takeCount = (counts.size * FREQUENT_TOP_FRACTION)
                    .toInt().coerceAtLeast(if (counts.isEmpty()) 0 else 1)

                counts.entries
                    .sortedByDescending { it.value }
                    .take(takeCount)
                    .map { it.key }
            }

            STALE_HALF_YEAR, STALE_YEAR -> {
                val days = daysFor(filter) ?: return contacts
                val threshold = nowMillis - days.toLong() * DAY_MILLIS

                contacts.filter { contact ->
                    // Избранные не попадают: пользователь уже сказал,
                    // что человек важен, и предлагать его к пересмотру
                    // значит спорить с ним
                    if (contact.starred) return@filter false

                    val lastCall = contact.numbers
                        .mapNotNull { lastCallByE164[it.e164] }
                        .maxOrNull()

                    // Звонков нет в загруженной истории — это НЕ значит,
                    // что их не было: журнал ограничен. Такие контакты
                    // тоже показываем, но интерфейс подписывает их иначе
                    lastCall == null || lastCall < threshold
                }
            }
        }
    }
}
