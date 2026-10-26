package com.ogneliss.dialer.data

import com.ogneliss.dialer.concurrency.suspendRunCatching

import com.ogneliss.dialer.call.SpamPolicy
import com.ogneliss.dialer.data.db.BlockedCallDao
import com.ogneliss.dialer.data.db.BlockedCallEntity
import com.ogneliss.dialer.log.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ЖУРНАЛ ЗАБЛОКИРОВАННЫХ ВЫЗОВОВ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ЗАПОМИНАТЬ ПРИЧИНУ
 * ══════════════════════════════════════════════════════════════════════════
 * Заблокированный вызов попадает в системный журнал, но выглядит там
 * как обычный пропущенный. Почему телефон не зазвонил — узнать неоткуда,
 * и со стороны это неотличимо от поломки.
 *
 * Блокировка при этом ошибается: оценка риска — эвристика, метка «спам»
 * могла быть поставлена случайно, номер в чёрном списке мог сменить
 * владельца. Без объяснения человек не может это исправить — он
 * не знает, какое правило сработало.
 *
 * ── Почему в базе, а не в памяти ───────────────────────────────────────────
 * Первая версия хранила причины в памяти, и они терялись при перезапуске
 * процесса. Ломался самый частый случай: человек утром открывает телефон,
 * видит пропущенные за ночь и хочет понять, почему они не прошли —
 * а объяснять уже нечем.
 *
 * ── Про чистку по времени, а не по количеству ──────────────────────────────
 * Ограничение числом отбрасывало бы свежие записи в день активного
 * спама — ровно тогда, когда они нужнее всего. Причина же годичной
 * давности бесполезна независимо от того, сколько их накопилось.
 */
@Singleton
class BlockedCallLog @Inject constructor(
    private val dao: BlockedCallDao
) {

    /**
     * Как поступили с вызовом.
     *
     * Отличать важно: при блокировке звонящий слышит короткие гудки —
     * для него это «занято» или «недоступен». При беззвучном пропуске
     * идут обычные гудки, и он решит, что вы не берёте трубку.
     *
     * Для человека это разница между «меня отсекли» и «до меня
     * не дозвонились», и она влияет на то, перезвонит ли он.
     */
    enum class Outcome { BLOCKED, SILENCED }

    data class Entry(
        val e164: String,
        val reason: SpamPolicy.Reason,
        val outcome: Outcome,
        val at: Long
    )

    suspend fun record(e164: String, reason: SpamPolicy.Reason, outcome: Outcome) {
        if (e164.isBlank()) return

        runCatching {
            dao.upsert(
                BlockedCallEntity(
                    e164 = e164,
                    // Имя значения, а не порядковый номер: перестановка
                    // элементов enum не должна менять смысл записей
                    reason = reason.name,
                    outcome = outcome.name
                )
            )
        }.onFailure { Logger.w(TAG, "Не удалось записать причину блокировки", it) }
    }

    /**
     * Последние записи по номерам.
     *
     * @param limit сколько читать. Пятьсот покрывает несколько недель
     *        при обычной частоте спама и остаётся дешёвым: выборка идёт
     *        по индексу времени
     */
    suspend fun snapshot(limit: Int = DEFAULT_LIMIT): Map<String, Entry> =
        runCatching {
            dao.recent(limit).associate { row ->
                row.e164 to Entry(
                    e164 = row.e164,
                    reason = runCatching { SpamPolicy.Reason.valueOf(row.reason) }
                        .getOrDefault(SpamPolicy.Reason.NO_RULE_MATCHED),
                    outcome = runCatching { Outcome.valueOf(row.outcome) }
                        .getOrDefault(Outcome.BLOCKED),
                    at = row.at
                )
            }
        }.getOrElse {
            Logger.w(TAG, "Не удалось прочитать причины блокировки", it)
            emptyMap()
        }

    /**
     * Этот номер звонил без звука в тихие часы не раньше [since] (1.8.0).
     *
     * Так распознаётся «перезвонил — значит срочно»: второй звонок
     * за 15 минут проходит со звуком. Ошибка чтения — «нет»: тогда
     * второй звонок тоже пройдёт без звука, но останется в журнале.
     */
    suspend fun silencedQuietlySince(e164: String, since: Long): Boolean = suspendRunCatching {
        val row = dao.get(e164) ?: return@suspendRunCatching false
        row.outcome == Outcome.SILENCED.name &&
            row.reason == SpamPolicy.Reason.QUIET_HOURS.name &&
            row.at >= since
    }.getOrDefault(false)

    /** Снятие пометки: вызывается вместе со снятием правила. */
    suspend fun forget(e164: String) {
        runCatching { dao.delete(e164) }
    }

    /**
     * Очистка записей старше срока.
     *
     * Вызывается фоновой задачей вместе с очисткой заметок: отдельная
     * задача ради одной таблицы не окупается.
     */
    suspend fun purgeOlderThan(days: Int = RETENTION_DAYS) {
        val before = System.currentTimeMillis() - days * DAY_MILLIS
        suspendRunCatching { dao.deleteOlderThan(before) }
            .onSuccess { Logger.d(TAG, "Очищены причины блокировки старше $days дней") }
    }

    suspend fun clear() {
        runCatching { dao.clear() }
    }

    companion object {
        private const val TAG = "BlockedCalls"

        /**
         * Сколько записей читать для показа в журнале.
         *
         * Пятьсот покрывает несколько недель при обычной частоте спама.
         * Больше не нужно: системный журнал вызовов сам хранит
         * ограниченную историю, и пометке не к чему будет привязаться.
         */
        private const val DEFAULT_LIMIT = 500

        /**
         * Срок хранения причин.
         *
         * Через два месяца правило либо давно исправлено, либо оставлено
         * намеренно — объяснять нечего.
         */
        private const val RETENTION_DAYS = 60

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Насколько звонок в журнале может быть раньше записи о нём — проверка
         * идёт после начала звонка — и позже, на сдвиг часов (1.8.0).
         */
        const val SILENCED_MATCH_BEFORE_MS = 30_000L
        const val SILENCED_MATCH_AFTER_MS = 5_000L

        /**
         * СТРОКИ ЖУРНАЛА С ТИХИМ ЗВОНКОМ (1.8.0): ключ строки → запись.
         *
         * Блокировка — свойство номера: пока правило стоит, его звонки
         * не проходят, и пометка верна для любой его строки. Беззвучный
         * пропуск (тихие часы) — про один звонок. Перезвонил днём,
         * поговорили — та строка уже не «без звука», а запись живёт 60 дней:
         * по номеру пометка висела бы на каждом его звонке.
         *
         * Поэтому пометку получает только строка, чей последний звонок —
         * тот самый тихий. В журнале время звонка — его начало, а запись
         * делается чуть позже, при проверке: окно — [SILENCED_MATCH_BEFORE_MS]
         * до записи и [SILENCED_MATCH_AFTER_MS] после.
         */
        fun silencedRows(groups: List<CallLogGroup>, entries: Map<String, Entry>): Map<String, Entry> {
            val result = HashMap<String, Entry>()
            for (group in groups) {
                val entry = entries[group.e164]?.takeIf { it.outcome == Outcome.SILENCED } ?: continue
                val window = (entry.at - SILENCED_MATCH_BEFORE_MS)..(entry.at + SILENCED_MATCH_AFTER_MS)
                if (group.lastDate in window) result[group.key] = entry
            }
            return result
        }
    }
}
