package com.ogneliss.dialer.fake

import com.ogneliss.dialer.data.CallLogEntry
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallLogSource
import com.ogneliss.dialer.data.CallTotals
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.GroupingSettings
import com.ogneliss.dialer.data.SimInfoSource
import com.ogneliss.dialer.data.ContactEditorLauncher
import com.ogneliss.dialer.data.ContactWriter
import com.ogneliss.dialer.data.NumberRule
import com.ogneliss.dialer.data.RuleKind
import com.ogneliss.dialer.data.RulePattern
import com.ogneliss.dialer.data.RulesSource
import com.ogneliss.dialer.data.ShortcutSink
import com.ogneliss.dialer.data.ShortcutTarget
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.settings.GroupingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ЗАГЛУШКИ ИСТОЧНИКОВ ДАННЫХ ДЛЯ ТЕСТОВ.
 *
 * ── Зачем ──────────────────────────────────────────────────────────────────
 * Настоящие репозитории привязаны к Android: `ContentResolver`,
 * системные провайдеры, `TelecomManager`. Проверить с ними, что подгрузка
 * страницы обновляет список, можно только на эмуляторе с заполненным
 * журналом вызовов — такие тесты не пишут, и слой оставался непроверенным.
 *
 * Заглушки заменяют всё это списком в памяти. Тест выполняется
 * за миллисекунды и не зависит от того, что лежит в журнале устройства.
 *
 * ── Почему заглушки простые ────────────────────────────────────────────────
 * Без библиотек подмены. Здесь достаточно вернуть заранее заданный список
 * и посчитать вызовы; мощный инструмент добавил бы зависимость
 * и сделал бы тесты труднее для чтения, чем проверяемый код.
 */

/**
 * Журнал вызовов в памяти.
 *
 * Считает обращения: это позволяет проверить не только результат,
 * но и то, что лишних запросов к провайдеру не происходит —
 * а именно лишние чтения были одной из найденных проблем.
 */
class FakeCallLogSource(
    private val entries: List<CallLogEntry> = emptyList()
) : CallLogSource {

    var loadCallCount = 0
        private set

    var lastRequestedOffset = -1
        private set

    val deletedGroups = mutableListOf<CallLogGroup>()
    var clearAllCalled = false
        private set

    override suspend fun loadEntries(limit: Int, offset: Int): List<CallLogEntry> {
        loadCallCount++
        lastRequestedOffset = offset
        return entries.drop(offset).take(limit)
    }

    override suspend fun historyFor(number: String, limit: Int): List<CallLogEntry> =
        entries.filter { it.number == number }.take(limit)

    /**
     * ИТОГИ ПО НОМЕРАМ.
     *
     * Метода не было вовсе, и из-за этого класс не реализовывал
     * CallLogSource: «Class 'FakeCallLogSource' is not abstract and does
     * not implement abstract member 'totalsByNumber'». Один этот пропуск
     * ронял компиляцию ВСЕХ тестов — падал не отдельный тест, а сборка
     * тестового исходника целиком.
     *
     * Ключ — E.164, как в настоящем CallLogRepository: там счётчики
     * складываются по нормализованному номеру, и тест, считающий иначе,
     * проверял бы не то поведение.
     */
    override suspend fun totalsByNumber(): Map<String, CallTotals> =
        entries.groupBy { it.e164 }
            .mapValues { (_, list) ->
                CallTotals(
                    total = list.size,
                    incoming = list.count { it.type == CallType.INCOMING },
                    outgoing = list.count { it.type == CallType.OUTGOING },
                    missed = list.count { it.type == CallType.MISSED },
                    rejected = list.count { it.type == CallType.REJECTED },
                    durationSec = list.sumOf { it.durationSec }
                )
            }

    override suspend fun deleteGroup(group: CallLogGroup): Boolean {
        deletedGroups += group
        return true
    }

    override suspend fun clearAll(): Boolean {
        clearAllCalled = true
        return true
    }
}

/** Состав SIM задаётся тестом. */
class FakeSimInfoSource(
    private val slots: List<SimSlot> = emptyList()
) : SimInfoSource {

    override suspend fun availableSlots(): List<SimSlot> = slots

    override fun labelForAccount(accountId: String?): String? =
        slots.firstOrNull { it.subscriptionId.toString() == accountId }?.label
}

/** Режим группировки меняется прямо в тесте. */
class FakeGroupingSettings(
    initial: GroupingMode = GroupingMode.CONSECUTIVE
) : GroupingSettings {

    private val mode = MutableStateFlow(initial)

    override val groupingMode: Flow<GroupingMode> = mode

    fun setMode(value: GroupingMode) {
        mode.value = value
    }
}


/**
 * Правила блокировки в памяти.
 *
 * Совпадение по маске повторяет боевую логику: без этого тест
 * контроллера проверял бы не то, что работает в приложении.
 */
class FakeRulesSource(
    initial: List<NumberRule> = emptyList()
) : RulesSource {

    private val rules = MutableStateFlow(initial)

    override val all: Flow<List<NumberRule>> = rules

    override suspend fun add(pattern: String, kind: RuleKind, comment: String?) {
        // Повторное добавление заменяет правило, а не плодит дубли:
        // так же ведёт себя Room с OnConflictStrategy.REPLACE
        rules.value = rules.value.filterNot { it.pattern == pattern } +
            NumberRule(pattern, kind.key, comment)
    }

    override suspend fun remove(pattern: String) {
        rules.value = rules.value.filterNot { it.pattern == pattern }
    }

    override suspend fun decide(e164: String): RuleKind? {
        // Белый список проверяется первым: он приоритетнее чёрного
        val allow = rules.value.firstOrNull { it.isAllow && matches(it.pattern, e164) }
        if (allow != null) return RuleKind.ALLOW

        val block = rules.value.firstOrNull { it.isBlock && matches(it.pattern, e164) }
        return if (block != null) RuleKind.BLOCK else null
    }

    private fun matches(pattern: String, e164: String): Boolean =
        RulePattern.matches(pattern, e164)

    fun current(): List<NumberRule> = rules.value
}

/** Запись контактов: считает вызовы вместо обращения к провайдеру. */
class FakeContactWriter : ContactWriter {

    val starred = mutableMapOf<Long, Boolean>()
    val silenced = mutableMapOf<Long, Boolean>()
    val deleted = mutableListOf<Long>()

    override suspend fun setStarred(contactId: Long, starred: Boolean): Boolean {
        this.starred[contactId] = starred
        return true
    }

    override suspend fun setSendToVoicemail(contactId: Long, enabled: Boolean): Boolean {
        silenced[contactId] = enabled
        return true
    }

    override suspend fun isSendToVoicemail(contactId: Long): Boolean = silenced[contactId] == true

    override suspend fun setCustomRingtone(contactId: Long, uri: android.net.Uri?): Boolean = true

    override suspend fun delete(contactId: Long): Boolean {
        deleted += contactId
        return true
    }
}

/** Переходы в системное приложение: запоминаются, но не выполняются. */
class FakeContactEditorLauncher : ContactEditorLauncher {
    val created = mutableListOf<String>()
    val edited = mutableListOf<Long>()

    override fun createWithNumber(number: String, name: String?) { created += number }
    override fun addNumberToExisting(number: String) { created += number }
    override fun edit(contactId: Long) { edited += contactId }
    override fun openDraft(contactId: Long?, rows: List<com.ogneliss.dialer.data.RowValue>): Boolean {
        contactId?.let { edited += it }
        return true
    }
}

/** Ярлыки: сохраняем последний переданный список для проверки порядка. */
class FakeShortcutSink : ShortcutSink {
    var lastTargets: List<ShortcutTarget> = emptyList()
        private set

    override fun updateDynamic(targets: List<ShortcutTarget>) { lastTargets = targets }
    override fun requestPin(target: ShortcutTarget): Boolean = true
}
