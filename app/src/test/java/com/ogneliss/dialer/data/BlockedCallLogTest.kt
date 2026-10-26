package com.ogneliss.dialer.data

import com.ogneliss.dialer.call.SpamPolicy
import com.ogneliss.dialer.data.db.BlockedCallDao
import com.ogneliss.dialer.data.db.BlockedCallEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ЖУРНАЛА ЗАБЛОКИРОВАННЫХ ВЫЗОВОВ.
 *
 * Заблокированный вызов выглядит в системном журнале как обычный
 * пропущенный. Если причина потеряется, человек решит, что приложение
 * съело звонок — и будет прав по факту, хотя правило включил он сам.
 */
class BlockedCallLogTest {

    /**
     * Хранилище в памяти вместо базы.
     *
     * Room в модульных тестах требует Robolectric и запуска реальной
     * SQLite: для проверки логики это лишние секунды на каждый прогон,
     * а поведение DAO здесь тривиально.
     */
    private class FakeDao : BlockedCallDao {
        val rows = linkedMapOf<String, BlockedCallEntity>()

        override suspend fun recent(limit: Int): List<BlockedCallEntity> =
            rows.values.sortedByDescending { it.at }.take(limit)

        override suspend fun upsert(entry: BlockedCallEntity) {
            rows[entry.e164] = entry
        }

        override suspend fun delete(e164: String) {
            rows.remove(e164)
        }

        override suspend fun get(e164: String): BlockedCallEntity? = rows[e164]

        override suspend fun deleteOlderThan(before: Long) {
            rows.values.removeAll { it.at < before }
        }

        override suspend fun clear() = rows.clear()
    }

    private val dao = FakeDao()
    private val log = BlockedCallLog(dao)

    @Test
    fun `причина сохраняется и читается`() = runTest {
        log.record("+79001112233", SpamPolicy.Reason.USER_TAG, BlockedCallLog.Outcome.BLOCKED)

        val entry = log.snapshot()["+79001112233"]
        assertEquals(SpamPolicy.Reason.USER_TAG, entry?.reason)
        assertEquals(BlockedCallLog.Outcome.BLOCKED, entry?.outcome)
    }

    @Test
    fun `сброшенный и беззвучный различаются`() = runTest {
        /*
         * Для звонящего это разные вещи: при блокировке он слышит
         * короткие гудки, при беззвучном пропуске — обычные. От этого
         * зависит, перезвонит ли он.
         */
        log.record("+7900A", SpamPolicy.Reason.APP_BLOCK_LIST, BlockedCallLog.Outcome.BLOCKED)
        log.record("+7900B", SpamPolicy.Reason.APP_BLOCK_LIST, BlockedCallLog.Outcome.SILENCED)

        val snapshot = log.snapshot()
        assertEquals(BlockedCallLog.Outcome.BLOCKED, snapshot["+7900A"]?.outcome)
        assertEquals(BlockedCallLog.Outcome.SILENCED, snapshot["+7900B"]?.outcome)
    }

    @Test
    fun `повторная блокировка обновляет причину`() = runTest {
        // Правило могло смениться: сначала сработала оценка риска,
        // потом человек добавил номер в чёрный список
        log.record("+7900A", SpamPolicy.Reason.APP_BLOCK_LIST, BlockedCallLog.Outcome.SILENCED)
        log.record("+7900A", SpamPolicy.Reason.APP_BLOCK_LIST, BlockedCallLog.Outcome.BLOCKED)

        assertEquals(SpamPolicy.Reason.APP_BLOCK_LIST, log.snapshot()["+7900A"]?.reason)
        assertEquals("Дубликата быть не должно", 1, dao.rows.size)
    }

    @Test
    fun `снятие блокировки убирает пометку`() = runTest {
        /*
         * Иначе запись продолжала бы говорить «в вашем чёрном списке»
         * после того, как номер оттуда убрали: человек исправил правило,
         * а приложение утверждает обратное.
         */
        log.record("+7900A", SpamPolicy.Reason.APP_BLOCK_LIST, BlockedCallLog.Outcome.BLOCKED)
        log.forget("+7900A")

        assertNull(log.snapshot()["+7900A"])
    }

    @Test
    fun `пустой номер не записывается`() = runTest {
        // Скрытый вызов приходит без номера: записывать нечего,
        // а пустой ключ затирал бы себя при каждом таком звонке
        log.record("", SpamPolicy.Reason.HIDDEN_BLOCKED, BlockedCallLog.Outcome.BLOCKED)

        assertTrue(log.snapshot().isEmpty())
    }

    @Test
    fun `неизвестная причина не роняет чтение`() = runTest {
        /*
         * Имя значения могло исчезнуть при обновлении приложения:
         * причину переименовали или убрали. Показать запись без
         * объяснения лучше, чем уронить весь журнал.
         */
        dao.rows["+7900A"] = BlockedCallEntity("+7900A", "НЕСУЩЕСТВУЮЩАЯ", "BLOCKED", 1L)

        val entry = log.snapshot()["+7900A"]
        assertEquals(SpamPolicy.Reason.NO_RULE_MATCHED, entry?.reason)
    }

    @Test
    fun `старые записи чистятся по времени`() = runTest {
        /*
         * По времени, а не по количеству: ограничение числом отбрасывало
         * бы свежие записи в день активного спама — ровно тогда, когда
         * они нужнее всего.
         */
        val old = System.currentTimeMillis() - 100L * 24 * 60 * 60 * 1000
        dao.rows["+7900OLD"] = BlockedCallEntity("+7900OLD", "USER_TAG", "BLOCKED", old)
        log.record("+7900NEW", SpamPolicy.Reason.USER_TAG, BlockedCallLog.Outcome.BLOCKED)

        log.purgeOlderThan(days = 60)

        val snapshot = log.snapshot()
        assertNull("Старая запись должна уйти", snapshot["+7900OLD"])
        assertTrue("Свежая должна остаться", snapshot.containsKey("+7900NEW"))
    }

    @Test
    fun `предел выборки соблюдается`() = runTest {
        repeat(600) { i ->
            log.record("+7900$i", SpamPolicy.Reason.APP_BLOCK_LIST, BlockedCallLog.Outcome.BLOCKED)
        }

        assertTrue("Выборка ограничена", log.snapshot().size <= 500)
    }

    @Test
    fun `тихий звонок распознаётся как недавний только в своём окне`() = runTest {
        // Тихие часы (1.8.0): перезвонил за 15 минут — значит, срочно
        dao.upsert(
            BlockedCallEntity(
                e164 = "+7900Q",
                reason = SpamPolicy.Reason.QUIET_HOURS.name,
                outcome = BlockedCallLog.Outcome.SILENCED.name,
                at = 10_000L
            )
        )
        assertTrue(log.silencedQuietlySince("+7900Q", since = 5_000L))
        assertEquals(false, log.silencedQuietlySince("+7900Q", since = 20_000L))
        // Заблокированный по другой причине — не «тихий»
        log.record("+7900B", SpamPolicy.Reason.APP_BLOCK_LIST, BlockedCallLog.Outcome.BLOCKED)
        assertEquals(false, log.silencedQuietlySince("+7900B", since = 0L))
        assertEquals(false, log.silencedQuietlySince("+7900Z", since = 0L))
    }

    // ── «Без звука — тихие часы» у звонка, а не у номера (1.8.0) ───────────

    private fun row(key: String, e164: String, lastDate: Long) = CallLogGroup(
        key = key, lastCallId = 1, number = e164, e164 = e164,
        displayName = null, photoUri = null, contactId = null,
        lastType = CallType.MISSED, lastDate = lastDate, durationSec = 0,
        count = 1, incomingCount = 0, outgoingCount = 0, missedCount = 1,
        rejectedCount = 0, totalDurationSec = 0, numbers = listOf(e164),
        subscriptionLabel = null, subscriptionId = null
    )

    private fun entry(e164: String, at: Long, outcome: BlockedCallLog.Outcome) = BlockedCallLog.Entry(
        e164 = e164, reason = SpamPolicy.Reason.QUIET_HOURS, outcome = outcome, at = at
    )

    @Test
    fun `пометку тихого звонка получает только его строка`() {
        val silencedAt = 3_600_000L
        val rows = listOf(
            // Перезвонил через 8 минут — зазвонило: строка уже не «без звука»
            row("number:+7900Q#3", "+7900Q", silencedAt + 8 * 60_000L),
            // Тот самый тихий звонок: в журнале — начало, на секунду раньше записи
            row("number:+7900Q#2", "+7900Q", silencedAt - 1_000L),
            // Вечером, до тихих часов — звонил со звуком
            row("number:+7900Q#1", "+7900Q", silencedAt - 3 * 3_600_000L)
        )
        val result = BlockedCallLog.silencedRows(
            rows, mapOf("+7900Q" to entry("+7900Q", silencedAt, BlockedCallLog.Outcome.SILENCED))
        )
        assertEquals(setOf("number:+7900Q#2"), result.keys)
    }

    @Test
    fun `строка после повторного звонка пометки не получает`() {
        // Журнал по умолчанию собирает звонки номера в одну строку: последний
        // в ней — повторный, со звуком, и пометка о тихом была бы неправдой
        val silencedAt = 3_600_000L
        val merged = row("number:+7900R#5", "+7900R", silencedAt + 10 * 60_000L)
        val result = BlockedCallLog.silencedRows(
            listOf(merged), mapOf("+7900R" to entry("+7900R", silencedAt, BlockedCallLog.Outcome.SILENCED))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `заблокированные остаются пометкой номера`() {
        // Блокировка — свойство номера: её строки здесь не выбираются
        val result = BlockedCallLog.silencedRows(
            listOf(row("number:+7900S#1", "+7900S", 1_000L)),
            mapOf("+7900S" to entry("+7900S", 1_000L, BlockedCallLog.Outcome.BLOCKED))
        )
        assertTrue(result.isEmpty())
    }
}
