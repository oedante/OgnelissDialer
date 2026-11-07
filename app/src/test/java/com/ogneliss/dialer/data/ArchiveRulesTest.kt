package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Архив журнала и SMS (2.1.0): разбор в ArchiveRules. Номера вымышленные. */
class ArchiveRulesTest {

    private fun entry(id: Long) = CallLogEntry(
        id = id, number = "+70000000001", e164 = "+70000000001", cachedName = null, photoUri = null,
        type = CallType.INCOMING, date = id * 1000, durationSec = 0, phoneAccountId = null, contactId = null
    )

    private fun group(key: String, vararg ids: Long) = CallLogGroup(
        key = key, lastCallId = ids.maxOrNull() ?: -1, number = "+70000000001", e164 = "+70000000001",
        displayName = null, photoUri = null, contactId = null,
        lastType = CallType.INCOMING, lastDate = 0, durationSec = 0, count = ids.size,
        incomingCount = ids.size, outgoingCount = 0, missedCount = 0, rejectedCount = 0,
        totalDurationSec = 0, numbers = listOf("+70000000001"),
        subscriptionLabel = null, subscriptionId = null,
        entries = ids.map { entry(it) }
    )

    @Test
    fun `sms archive survives a round trip`() {
        val map = mapOf("+70000000001" to 100L, "bank" to 200L)
        assertEquals(map, ArchiveRules.parseSms(ArchiveRules.formatSms(map)))
    }

    @Test
    fun `broken sms lines are skipped`() {
        assertEquals(mapOf("ok" to 5L), ArchiveRules.parseSms("ok|5\n|7\nnope\nx|y"))
        assertTrue(ArchiveRules.parseSms(null).isEmpty())
    }

    @Test
    fun `new message brings a thread back`() {
        val archive = mapOf("k" to 1_000L)
        assertTrue(ArchiveRules.isSmsArchived("k", 1_000L, archive))
        assertFalse(ArchiveRules.isSmsArchived("k", 1_001L, archive))
        assertFalse(ArchiveRules.isSmsArchived("other", 1L, archive))
    }

    @Test
    fun `archive time is never before the last message`() {
        assertEquals(5_000L, ArchiveRules.smsArchiveTime(now = 4_000L, lastDate = 5_000L))
        assertEquals(6_000L, ArchiveRules.smsArchiveTime(now = 6_000L, lastDate = 5_000L))
    }

    @Test
    fun `group is hidden only when all its calls are archived`() {
        val archived = setOf(1L, 2L)
        assertTrue(ArchiveRules.isGroupArchived(group("a", 1, 2), archived))
        // Новый звонок (id 3) возвращает карточку в журнал
        assertFalse(ArchiveRules.isGroupArchived(group("a", 1, 2, 3), archived))
        assertEquals(setOf("a"), ArchiveRules.archivedGroupKeys(listOf(group("a", 1), group("b", 9)), archived))
    }

    @Test
    fun `call ids survive a round trip`() {
        val ids = setOf(3L, 1L, 2L)
        assertEquals(ids, ArchiveRules.parseCallIds(ArchiveRules.formatCallIds(ids)))
        assertTrue(ArchiveRules.parseCallIds("").isEmpty())
    }
}
