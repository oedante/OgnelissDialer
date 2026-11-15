package com.ogneliss.dialer.backup

import com.ogneliss.dialer.data.CallLogEntry
import com.ogneliss.dialer.data.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Выгрузка и восстановление — обзор безопасности 2.7.0:
 * формулы в CSV журнала и чужие значения в файле SMS.
 */
class BackupSafetyTest {

    @Test
    fun `имя-формула в CSV начинается с апострофа`() {
        assertEquals("'=HYPERLINK(\"x\")", CallLogCsvExport.neutralizeFormula("=HYPERLINK(\"x\")"))
        assertEquals("'@SUM(A1)", CallLogCsvExport.neutralizeFormula("@SUM(A1)"))
        assertEquals("'-Мама-", CallLogCsvExport.neutralizeFormula("-Мама-"))
        assertEquals("'+cmd", CallLogCsvExport.neutralizeFormula("+cmd"))
    }

    @Test
    fun `обычный номер и обычное имя в CSV не меняются`() {
        assertEquals("+7 916 123-45-67", CallLogCsvExport.neutralizeFormula("+7 916 123-45-67"))
        assertEquals("*100#", CallLogCsvExport.neutralizeFormula("*100#"))
        assertEquals("Иван Петров", CallLogCsvExport.neutralizeFormula("Иван Петров"))
        assertEquals("", CallLogCsvExport.neutralizeFormula(""))
    }

    @Test
    fun `формула в имени не проходит в выгрузку журнала`() {
        val entry = CallLogEntry(
            id = 1, number = "+79001112233", e164 = "+79001112233", cachedName = null, photoUri = null,
            type = CallType.INCOMING, date = 1_000, durationSec = 5, phoneAccountId = "sim1", contactId = null
        )
        val csv = CallLogCsvExport.asCsv(listOf(entry), mapOf("+79001112233" to "=1+2;x"))
        assertTrue(csv, csv.contains(";+79001112233;\"'=1+2;x\";"))
    }

    @Test
    fun `вид SMS из файла — только известные значения`() {
        assertEquals(1, restoredSmsType(1))
        assertEquals(2, restoredSmsType(2))
        assertEquals(5, restoredSmsType(5))
        // «Исходящее» и «в очереди» — как «не отправлено», вечного «Отправляется» нет
        assertEquals(5, restoredSmsType(4))
        assertEquals(5, restoredSmsType(6))
        // Черновики, чужие числа и пустое — строка пропускается
        assertNull(restoredSmsType(3))
        assertNull(restoredSmsType(0))
        assertNull(restoredSmsType(99))
        assertNull(restoredSmsType(null))
    }
}
