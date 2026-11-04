package com.ogneliss.dialer.data

import com.ogneliss.dialer.util.PhoneFormatter

/**
 * ГРУППИРОВКА ВЫЗОВОВ.
 *
 * Назначение: превращает плоский список вызовов из системы в карточки
 * журнала. Вынесено в отдельный объект без зависимостей от Android,
 * чтобы покрыть тестами: логика группировки неочевидна и легко ломается
 * при добавлении новых условий.
 *
 * ── Два режима ─────────────────────────────────────────────────────────────
 *
 * CONSECUTIVE (по умолчанию, как в штатных диалерах):
 *   объединяются только ПОДРЯД ИДУЩИЕ вызовы одного абонента.
 *   Звонок в понедельник и звонок в пятницу останутся разными карточками,
 *   если между ними были другие звонки. Так журнал читается как лента
 *   событий в хронологии.
 *
 * BY_CONTACT:
 *   ВСЕ вызовы одного абонента сливаются в одну карточку, независимо
 *   от того, что было между ними. Журнал превращается в список людей,
 *   отсортированный по времени последнего контакта. Удобно, когда важно
 *   «с кем я общался недавно», а не «что происходило по порядку».
 *
 * ── Что считается одним абонентом ──────────────────────────────────────────
 * Если номер привязан к контакту — группируем по КОНТАКТУ. У человека
 * может быть три номера, и звонки с них должны попадать в одну карточку.
 * Для номеров вне книги ключ — сам номер в E.164.
 *
 * ── Сортировка ─────────────────────────────────────────────────────────────
 * Всегда по времени ПОСЛЕДНЕГО вызова, по убыванию. В режиме BY_CONTACT
 * это принципиально: карточка поднимается наверх при новом звонке,
 * даже если предыдущий был месяц назад.
 */
object CallLogGrouper {

    enum class Mode {
        /** Только подряд идущие вызовы. */
        CONSECUTIVE,
        /** Все вызовы абонента в одной карточке. */
        BY_CONTACT,
        /** Без группировки: каждый вызов отдельной строкой. */
        NONE
    }

    /**
     * @param entries записи журнала, ОТСОРТИРОВАННЫЕ по дате убыванию
     * @param mode режим группировки
     * @param separateBySim не объединять вызовы, сделанные с разных SIM-карт.
     *        При двух активных SIM пользователю обычно важно, с какой именно
     *        шёл разговор, поэтому по умолчанию включено.
     */
    /**
     * @param contactIdByNumber соответствие «номер в E.164 → контакт»
     *        из телефонной книги. Нужно, чтобы человек с несколькими
     *        номерами давал ОДНУ карточку: полагаться на contactId
     *        из журнала нельзя, он часто пуст
     */
    fun group(
        entries: List<CallLogEntry>,
        mode: Mode = Mode.CONSECUTIVE,
        separateBySim: Boolean = true,
        simLabelResolver: (String?) -> String? = { null },
        contactIdByNumber: Map<String, Long> = emptyMap()
    ): List<CallLogGroup> = when (mode) {
        Mode.NONE -> entries.map { toGroup(listOf(it), simLabelResolver) }
        Mode.CONSECUTIVE ->
            groupConsecutive(entries, separateBySim, simLabelResolver, contactIdByNumber)
        Mode.BY_CONTACT -> groupByContact(entries, simLabelResolver, contactIdByNumber)
    }

    /**
     * Ключ абонента для ОБЪЕДИНЕНИЯ записей.
     *
     * Контакт важнее номера: три номера одного человека попадают
     * в одну карточку.
     */
    /**
     * КЛЮЧ АБОНЕНТА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НЕДОСТАТОЧНО entry.contactId
     * ══════════════════════════════════════════════════════════════════════
     * Он разбирается из CACHED_LOOKUP_URI журнала, а на многих прошивках
     * последний сегмент этой строки — lookup-ключ, а не число: разбор
     * возвращает null.
     *
     * Следствие для группировки: у человека с рабочим и личным номером
     * оба вызова получают ключи «number:…» — разные, — и он появляется
     * в журнале дважды. Хотя это один человек, и карточка должна быть
     * одна.
     *
     * ── Как решено ─────────────────────────────────────────────────────────
     * Снаружи передаётся соответствие «номер → идентификатор контакта»,
     * построенное по загруженной телефонной книге. Номер в формате E.164
     * есть у каждой записи и от кэша журнала не зависит.
     *
     * contactId из журнала остаётся запасным вариантом: книга могла
     * ещё не загрузиться, и тогда лучше сгруппировать хоть как-то,
     * чем не сгруппировать вовсе.
     */
    private fun subscriberKey(
        entry: CallLogEntry,
        contactIdByNumber: Map<String, Long>
    ): String {
        val resolved = contactIdByNumber[entry.e164] ?: entry.contactId
        return resolved?.let { "contact:$it" } ?: "number:${entry.e164}"
    }

    /**
     * УНИКАЛЬНЫЙ КЛЮЧ КАРТОЧКИ для списка.
     *
     * ── Почему нельзя использовать ключ абонента ───────────────────────────
     * В режиме «подряд идущие» один и тот же человек даёт НЕСКОЛЬКО
     * карточек: позвонил утром, потом кто-то другой, потом он же вечером.
     * Ключ абонента у них одинаковый.
     *
     * LazyColumn требует уникальности ключей и падает с ошибкой
     * «Key was already used», как только два элемента совпадут.
     * Именно это и происходило при прокрутке журнала с повторными
     * звонками — то есть у любого активного пользователя.
     *
     * Идентификатор последнего вызова уникален в пределах журнала
     * и решает задачу без изменения логики группировки.
     */
    private fun cardKey(entry: CallLogEntry, contactIdByNumber: Map<String, Long>): String =
        "${subscriberKey(entry, contactIdByNumber)}#${entry.id}"

    // ── Режим «подряд идущие» ──────────────────────────────────────────────

    private fun groupConsecutive(
        entries: List<CallLogEntry>,
        separateBySim: Boolean,
        simLabelResolver: (String?) -> String?,
        contactIdByNumber: Map<String, Long>
    ): List<CallLogGroup> {
        val result = mutableListOf<CallLogGroup>()
        val bucket = mutableListOf<CallLogEntry>()

        fun flush() {
            if (bucket.isNotEmpty()) {
                result += toGroup(bucket.toList(), simLabelResolver, contactIdByNumber)
                bucket.clear()
            }
        }

        for (entry in entries) {
            val last = bucket.lastOrNull()
            val sameSubscriber = last != null &&
                subscriberKey(last, contactIdByNumber) == subscriberKey(entry, contactIdByNumber)
            val sameSim = !separateBySim || last?.phoneAccountId == entry.phoneAccountId

            if (sameSubscriber && sameSim) bucket += entry
            else { flush(); bucket += entry }
        }
        flush()
        return result
    }

    // ── Режим «все вызовы абонента» ────────────────────────────────────────

    private fun groupByContact(
        entries: List<CallLogEntry>,
        simLabelResolver: (String?) -> String?,
        contactIdByNumber: Map<String, Long>
    ): List<CallLogGroup> = entries
        .groupBy { subscriberKey(it, contactIdByNumber) }
        .map { (_, list) -> toGroup(list, simLabelResolver, contactIdByNumber) }
        // Сортировка по последнему вызову: карточка поднимается наверх
        // при новом звонке, даже если предыдущий был давно
        .sortedByDescending { it.lastDate }

    // ── Сборка карточки ────────────────────────────────────────────────────

    /**
     * @param entries записи одной группы, первая — самая свежая
     */
    private fun toGroup(
        entries: List<CallLogEntry>,
        simLabelResolver: (String?) -> String?,
        contactIdByNumber: Map<String, Long> = emptyMap()
    ): CallLogGroup {
        // На случай, если вызывающий код передал записи не по порядку
        val sorted = entries.sortedByDescending { it.date }
        val latest = sorted.first()
        // Счётчики и длительность — только по вызовам: SMS (1.10.0) — не звонки
        val calls = sorted.filter { !it.isMessage }

        return CallLogGroup(
            key = cardKey(latest, contactIdByNumber),
            lastCallId = latest.id,
            number = latest.number,
            e164 = latest.e164,
            // Имя берём из самой свежей записи, где оно вообще есть:
            // старые записи журнала могут хранить устаревшее кэшированное имя
            displayName = sorted.firstNotNullOfOrNull { it.cachedName },
            photoUri = sorted.firstNotNullOfOrNull { it.photoUri },
            contactId = latest.contactId,
            lastType = latest.type,
            lastDate = latest.date,
            durationSec = latest.durationSec,
            count = sorted.size,
            incomingCount = calls.count { it.type == CallType.INCOMING },
            outgoingCount = calls.count { it.type == CallType.OUTGOING },
            missedCount = calls.count { it.type == CallType.MISSED },
            rejectedCount = calls.count {
                it.type == CallType.REJECTED || it.type == CallType.BLOCKED
            },
            totalDurationSec = calls.sumOf { it.durationSec },
            /*
             * ОДИН НОМЕР — ОДНА СТРОКА.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ distinct ПО СЫРОЙ СТРОКЕ НЕ РАБОТАЛ
             * ══════════════════════════════════════════════════════════════
             * Система пишет в журнал номер ровно так, как он был набран
             * или пришёл от сети. Один и тот же телефон попадает туда
             * как «+79671112233», «89671112233» и «8 (967) 111-22-33» —
             * это три разные строки, и distinct их не сводил.
             *
             * В карточке это выглядело как дубль: один и тот же номер
             * дважды подряд, с одинаковым форматированием, потому что
             * PhoneFormatter приводил их к одному виду уже при выводе.
             *
             * Сводим по нормализованному виду, а показываем первый
             * из встреченных: он ближе всего к тому, как номер набирали.
             */
            numbers = sorted.map { it.number }
                .distinctBy { PhoneFormatter.toE164(it).ifBlank { it } },
            subscriptionId = latest.phoneAccountId,
            subscriptionLabel = simLabelResolver(latest.phoneAccountId),
            entries = sorted,
            lastMessage = latest.message,
            unreadMessages = sorted.count { it.message?.unread == true }
        )
    }
}
