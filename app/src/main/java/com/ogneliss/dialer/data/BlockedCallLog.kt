package com.ogneliss.dialer.data

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

    private companion object {
        const val TAG = "BlockedCalls"

        /**
         * Сколько записей читать для показа в журнале.
         *
         * Пятьсот покрывает несколько недель при обычной частоте спама.
         * Больше не нужно: системный журнал вызовов сам хранит
         * ограниченную историю, и пометке не к чему будет привязаться.
         */
        const val DEFAULT_LIMIT = 500

        /**
         * Срок хранения причин.
         *
         * Через два месяца правило либо давно исправлено, либо оставлено
         * намеренно — объяснять нечего.
         */
        const val RETENTION_DAYS = 60

        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
