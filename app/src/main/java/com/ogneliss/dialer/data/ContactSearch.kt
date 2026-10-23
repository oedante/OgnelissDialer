package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.SortOrder
import com.ogneliss.dialer.t9.T9Engine
import com.ogneliss.dialer.util.DialDigits
import com.ogneliss.dialer.util.TextFold

/**
 * ПОИСК ПО КОНТАКТАМ И ЖУРНАЛУ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧИСТАЯ ФУНКЦИЯ БЕЗ ЗАВИСИМОСТЕЙ
 * ══════════════════════════════════════════════════════════════════════════
 * Принимает списки, возвращает списки. Ни Android, ни репозиториев,
 * ни состояния — поэтому проверяется тестами полностью.
 *
 * Пока поиск жил во ViewModel, протестировать его было нельзя: пришлось бы
 * поднимать весь граф зависимостей ради проверки, что «5546» находит
 * «Клинова». А это самая часто используемая функция приложения —
 * она срабатывает на каждое нажатие цифры.
 *
 * ── Два вида результатов ───────────────────────────────────────────────────
 * Контакты и записи журнала ищутся отдельно и показываются раздельно.
 * Смешивать их нельзя: один человек встречался бы дважды — как контакт
 * и как запись о звонке, — и пользователь не понял бы, почему.
 *
 * ── Совпадение по номеру ищется как подстрока ──────────────────────────────
 * Человек часто помнит середину или окончание номера: «который на 0512».
 * Поиск только по началу отсёк бы этот сценарий, хотя он не реже,
 * чем набор с первой цифры.
 * ══════════════════════════════════════════════════════════════════════════
 */
object ContactSearch {

    /** Результат поиска: два независимых списка. */
    data class Result(
        val contacts: List<Contact>,
        val callLog: List<CallLogGroup>
    ) {
        val isEmpty: Boolean get() = contacts.isEmpty() && callLog.isEmpty()

        companion object {
            val EMPTY = Result(emptyList(), emptyList())
        }
    }

    /**
     * @param query введённые цифры — поиск по T9 и номеру; если в запросе
     *   есть буквы — поиск по тексту имени и заметки (1.6.0, разбор
     *   у searchText). Знаки набора без цифр («+», «*») не ищут ничего
     * @param order порядок выдачи контактов
     */
    /**
     * @param notes заметки пользователя — по ним тоже ищем
     */
    fun search(
        contacts: List<Contact>,
        callLog: List<CallLogGroup>,
        query: String,
        order: SortOrder,
        notes: Map<String, NumberNote> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): Result {
        if (TextFold.isTextQuery(query)) return searchText(contacts, callLog, query.trim(), notes)

        val digits = query.filter { it.isDigit() }
        if (digits.isEmpty()) return Result.EMPTY

        val matchedContacts = contacts.filter { contact -> matches(contact, digits, notes) }

        // Порядок выдачи важен не меньше самого поиска: если нужный
        // контакт не первый, пользователь набирает ещё цифры,
        // то есть поиск не сэкономил ни одного нажатия
        val ranked = rank(matchedContacts, callLog, digits, order, nowMillis)

        val matchedLog = callLog.filter { group ->
            val nameMatches = group.displayName
                ?.let { T9Engine.matchIndex(digits, it) >= 0 } ?: false

            /*
             * ПОИСК ПО ЗАМЕТКАМ.
             *
             * Тот же T9-механизм, что для имён: заметка — обычный текст,
             * и набор «34275» находит «доставка» так же, как нашёл бы
             * фамилию.
             *
             * Это делает заметки полноценной заменой контактам: человек
             * помнит «я записывал что-то про доставку», набирает несколько
             * цифр и находит номер, не заводя контакт в книге.
             */
            val noteMatches = notes[group.e164]?.note
                ?.let { T9Engine.matchIndex(digits, it) >= 0 } ?: false

            /*
             * Сравнение через DialDigits: «8961…» и «+7961…» — один
             * и тот же номер, и поиск обязан так считать. Разбор —
             * в самом DialDigits.
             */
            nameMatches || noteMatches || DialDigits.matches(group.number, digits)
        }

        return Result(ranked, matchedLog)
    }

    /**
     * СОВПАДЕНИЕ КОНТАКТА: ИМЯ, НОМЕР ИЛИ ЗАМЕТКА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ЗАМЕТКА ЗДЕСЬ ТОЖЕ НУЖНА
     * ══════════════════════════════════════════════════════════════════════
     * По заметкам искали только в журнале. Но человек не различает,
     * где лежит номер — в книге или в истории звонков: он помнит
     * «я записывал что-то про сантехника» и набирает.
     *
     * Если сантехник заведён контактом, но заметка привязана к номеру,
     * поиск его не находил. Причём именно тогда, когда заметка нужнее
     * всего: имя вроде «Иван» ничего не говорит через полгода,
     * а «сантехник» говорит.
     *
     * ── Как ищем ───────────────────────────────────────────────────────────
     * Заметка привязана к номеру, у контакта их может быть несколько —
     * проверяем каждый. Совпадение по любому означает совпадение
     * контакта: это один человек.
     */
    private fun matches(
        contact: Contact,
        digits: String,
        notes: Map<String, NumberNote>
    ): Boolean {
        if (T9Engine.matchIndex(digits, contact.displayName) >= 0) return true

        return contact.numbers.any { number ->
            if (DialDigits.matches(number.value, digits)) return@any true

            val note = notes[number.e164]?.note ?: return@any false
            T9Engine.matchIndex(digits, note) >= 0
        }
    }

    /**
     * Ранжирование через SearchRanker.
     *
     * Сигналы берутся из журнала: сколько раз звонили и когда в последний.
     * Контакт без единого звонка не выпадает из выдачи — за него говорит
     * качество совпадения имени.
     */
    private fun rank(
        matched: List<Contact>,
        callLog: List<CallLogGroup>,
        digits: String,
        order: SortOrder,
        nowMillis: Long
    ): List<Contact> {
        if (matched.size <= 1) return matched

        val statsByE164 = callLog.associateBy { it.e164 }

        val candidates = matched.mapNotNull { contact ->
            val primary = contact.numbers.firstOrNull() ?: return@mapNotNull null
            val group = statsByE164[primary.e164]
            SearchRanker.Candidate(
                displayName = contact.displayName,
                number = primary.value,
                e164 = primary.e164,
                callCount = group?.count ?: 0,
                lastCallDate = group?.lastDate ?: 0,
                starred = contact.starred
            )
        }

        val ranked = SearchRanker.rank(candidates, digits, order, nowMillis)
        val positions = ranked.withIndex().associate { (index, c) -> c.e164 to index }

        // Контакты без номера в конец: сортировать их не по чему,
        // но и терять нельзя — у контакта может быть только почта
        return matched.sortedBy { contact ->
            contact.numbers.firstOrNull()?.let { positions[it.e164] } ?: Int.MAX_VALUE
        }
    }

    /**
     * ПОИСК БУКВАМИ (1.6.0).
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЗАЧЕМ
     * ══════════════════════════════════════════════════════════════════════
     * С 1.6.0 у журнала есть кнопка «Поиск» с обычной клавиатурой
     * (вариант «Б» обзора интерфейса). T9 на ней бессмыслен: человек
     * печатает «сантехник» буквами и ждёт найти и контакт, и заметку
     * к номеру, который в книгу не заносили.
     *
     * ── Что сравнивается ───────────────────────────────────────────────────
     * Имя и заметка — подстрокой, без учёта регистра, «ё» = «е»
     * (TextFold). Номер — нет: в запросе с буквами цифры номера не ищут,
     * для номера есть клавиатура набора.
     *
     * ── Порядок ────────────────────────────────────────────────────────────
     * Сначала имена, которые начинаются с запроса, затем — где с него
     * начинается слово («петр» → «Иван Петров»), затем остальное. Внутри —
     * избранные, потом те, кому звонят чаще; при равенстве — порядок
     * книги (сортировка устойчивая).
     */
    private fun searchText(
        contacts: List<Contact>,
        callLog: List<CallLogGroup>,
        query: String,
        notes: Map<String, NumberNote>
    ): Result {
        if (query.isEmpty()) return Result.EMPTY

        val matched = contacts.filter { contact ->
            TextFold.contains(contact.displayName, query) ||
                contact.numbers.any { TextFold.contains(notes[it.e164]?.note, query) }
        }
        val matchedLog = callLog.filter { group ->
            TextFold.contains(group.displayName, query) ||
                TextFold.contains(notes[group.e164]?.note, query)
        }
        return Result(rankText(matched, callLog, query), matchedLog)
    }

    /** Порядок выдачи при поиске буквами: разбор у searchText. */
    private fun rankText(
        matched: List<Contact>,
        callLog: List<CallLogGroup>,
        query: String
    ): List<Contact> {
        if (matched.size <= 1) return matched
        val callsByE164 = callLog.associate { it.e164 to it.count }
        return matched.sortedWith(
            compareBy<Contact> { TextFold.rank(it.displayName, query) }
                .thenByDescending { it.starred }
                .thenByDescending { contact -> contact.numbers.sumOf { callsByE164[it.e164] ?: 0 } }
        )
    }

}
