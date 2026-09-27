package com.ogneliss.dialer.data

import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PhoneFormatter
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * УПРАВЛЕНИЕ БЛОКИРОВКОЙ НОМЕРОВ.
 *
 * ── Зачем отдельный класс ──────────────────────────────────────────────────
 * Блокировка складывается из трёх источников: наши правила в Room,
 * системный список Android и метки «спам» на номерах. Разбросанные
 * по ViewModel, они выглядели независимыми — и однажды это привело
 * к тому, что свайп «Заблокировать» писал в четвёртое, давно мёртвое
 * хранилище, а номер нигде не появлялся.
 *
 * Здесь все пути блокировки собраны вместе, и добавить новый мимо них
 * уже не получится незаметно.
 *
 * ── Почему наши правила, а не системный список ─────────────────────────────
 * Системный список надёжнее: он блокирует до того, как телефон зазвонит.
 * Но добавление в него на части прошивок требует подтверждения на каждый
 * номер, и массовая блокировка превратилась бы в череду диалогов.
 *
 * Поэтому по умолчанию пишем в свои правила, а системный список остаётся
 * доступен отдельной кнопкой — для тех номеров, которые нужно отсечь
 * совсем жёстко.
 */
@Singleton
class BlockingController @Inject constructor(
    private val rules: RulesSource
) {

    /** Все правила: чёрный и белый списки вместе. */
    val allRules: Flow<List<NumberRule>> = rules.all

    /**
     * Блокировка одного номера.
     *
     * @return false, если номер пустой — например, скрытый вызов.
     *         Такой блокируется отдельной настройкой, а не правилом:
     *         правило по пустой строке совпало бы со всем подряд
     */
    suspend fun block(e164: String, comment: String? = null): Boolean {
        if (e164.isBlank()) {
            Logger.d(TAG, "Пустой номер: блокировать нечего")
            return false
        }
        rules.add(e164, RuleKind.BLOCK, comment)
        Logger.i(TAG, "Заблокирован ${Logger.mask(e164)}")
        return true
    }

    /** Блокировка нескольких номеров разом. */
    suspend fun blockAll(groups: Collection<CallLogGroup>): Int {
        var blocked = 0
        groups.forEach { group ->
            if (block(group.e164)) blocked++
        }
        Logger.i(TAG, "Массовая блокировка: $blocked из ${groups.size}")
        return blocked
    }

    /**
     * Разрешение номера.
     *
     * Белый список имеет приоритет над чёрным, поэтому добавление сюда
     * автоматически отменяет блокировку — отдельно снимать её не нужно
     * и было бы источником путаницы.
     */
    suspend fun allow(e164: String, comment: String? = null) {
        if (e164.isBlank()) return
        rules.add(e164, RuleKind.ALLOW, comment)
        Logger.i(TAG, "Разрешён ${Logger.mask(e164)}")
    }

    suspend fun removeRule(pattern: String) = rules.remove(pattern)

    suspend fun removeRules(patterns: Collection<String>) {
        patterns.forEach { rules.remove(it) }
    }

    /**
     * Номера с метками спама.
     *
     * Нужны фильтру журнала: метка — это не правило блокировки,
     * но по ней тоже отбирают записи, и источник должен быть один.
     */
    fun spamNumbers(marks: Map<String, SpamMark>): Set<String> =
        // С 1.1.1 отметки живут отдельно от заметок; любая отметка —
        // и спам, и мошенники — попадает в фильтр «Спам»
        marks.keys

    /** Заблокирован ли номер нашими правилами. */
    suspend fun isBlocked(number: String): Boolean {
        val e164 = PhoneFormatter.toE164(number)
        return rules.decide(e164) == RuleKind.BLOCK
    }

    companion object {

        private const val TAG = "Blocking"
        /**
         * Метки, означающие нежелательный номер.
         * Ключи, а не переводы: смена языка не должна ломать фильтр.
         */
        val SPAM_TAGS = setOf("spam", "fraud")
    }
}
