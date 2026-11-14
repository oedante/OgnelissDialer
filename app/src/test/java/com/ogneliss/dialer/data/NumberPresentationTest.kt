package com.ogneliss.dialer.data

import com.ogneliss.dialer.backup.CallLogCsvExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ВЫЗОВ БЕЗ НОМЕРА (2.6.0) — автор, 14.11.2026: «Неизвестный номер никак
 * не определяется». Признак журнала NUMBER_PRESENTATION, «-1/-2/-3» старых
 * прошивок, группировка без чужого контакта и подпись в экспорте.
 */
class NumberPresentationTest {

    @Test
    fun `признак журнала и номера-заглушки`() {
        assertEquals(NumberPresentation.ALLOWED, NumberPresentation.of(1, "+79001112233"))
        assertEquals(NumberPresentation.RESTRICTED, NumberPresentation.of(2, ""))
        assertEquals(NumberPresentation.UNKNOWN, NumberPresentation.of(3, ""))
        assertEquals(NumberPresentation.PAYPHONE, NumberPresentation.of(4, ""))
        // Android 13: «недоступен» — тоже «не определён»
        assertEquals(NumberPresentation.UNKNOWN, NumberPresentation.of(5, ""))
        // Старые прошивки: признака нет, в номере — заглушка
        assertEquals(NumberPresentation.RESTRICTED, NumberPresentation.of(1, "-2"))
        assertEquals(NumberPresentation.UNKNOWN, NumberPresentation.of(null, "-1"))
        assertEquals(NumberPresentation.PAYPHONE, NumberPresentation.of(null, "-3"))
        // Пустой номер без признака — не определён
        assertEquals(NumberPresentation.UNKNOWN, NumberPresentation.of(1, "  "))
        assertTrue(NumberPresentation.RESTRICTED.hidden)
        assertFalse(NumberPresentation.ALLOWED.hidden)
    }

    private var nextId = 1L
    private fun entry(number: String, date: Long, presentation: NumberPresentation = NumberPresentation.ALLOWED, type: CallType = CallType.MISSED) =
        CallLogEntry(
            id = nextId++, number = number, e164 = number, cachedName = null, photoUri = null,
            type = type, date = date, durationSec = 15, phoneAccountId = "sim1", contactId = null,
            presentation = presentation
        )

    @Test
    fun `скрытые вызовы — своей группой, без строки «Номера» и без чужого контакта`() {
        val hidden1 = entry("", 300, NumberPresentation.RESTRICTED)
        val hidden2 = entry("", 200, NumberPresentation.RESTRICTED)
        val known = entry("+79001112233", 100, type = CallType.INCOMING)
        // У контакта номер, который не разобрался в E.164, — ключ «»: скрытый к нему не пристаёт
        val book = mapOf("" to 77L, "+79001112233" to 5L)
        val groups = CallLogGrouper.group(listOf(hidden1, hidden2, known), CallLogGrouper.Mode.CONSECUTIVE, contactIdByNumber = book)
        assertEquals(2, groups.size)
        val hidden = groups[0]
        assertEquals(2, hidden.count)
        assertEquals(NumberPresentation.RESTRICTED, hidden.presentation)
        assertEquals(null, hidden.contactId)
        assertTrue(hidden.numbers.isEmpty())
        assertTrue(hidden.key.startsWith("hidden:RESTRICTED"))
        assertEquals(5L, groups[1].contactId)
    }

    @Test
    fun `в экспорте журнала — пометка вместо пустого имени`() {
        val csv = CallLogCsvExport.asCsv(listOf(entry("", 1_000, NumberPresentation.RESTRICTED)))
        assertTrue(csv.lines()[1].contains(";;номер скрыт;пропущенный;"))
    }
}
