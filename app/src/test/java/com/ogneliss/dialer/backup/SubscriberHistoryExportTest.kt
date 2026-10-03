package com.ogneliss.dialer.backup

import com.ogneliss.dialer.data.CallLogEntry
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.NumberNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * ТЕСТЫ ВЫГРУЗКИ ИСТОРИИ.
 *
 * Выгрузку прикладывают к письму оператору, к отчёту или к заявлению
 * о мошенничестве. Ошибка в цифрах здесь дороже обычной: человек
 * приводит их как довод.
 */
class SubscriberHistoryExportTest {

    private val now = 1_700_000_000_000L
    private val ru = Locale.forLanguageTag("ru")

    private fun entry(
        type: CallType = CallType.INCOMING,
        duration: Long = 0,
        date: Long = now
    ) = CallLogEntry(
        id = date, number = "+79001234567", e164 = "+79001234567",
        cachedName = null, photoUri = null, type = type, date = date,
        durationSec = duration, phoneAccountId = null, contactId = null
    )

    @Test
    fun `сводка считает типы раздельно`() {
        val stats = SubscriberHistoryExport.summarize(
            listOf(
                entry(CallType.INCOMING, 60),
                entry(CallType.OUTGOING, 120),
                entry(CallType.MISSED),
                entry(CallType.MISSED)
            )
        )
        assertEquals(4, stats.total)
        assertEquals(1, stats.incoming)
        assertEquals(1, stats.outgoing)
        assertEquals(2, stats.missed)
        assertEquals(180, stats.totalSeconds)
    }

    @Test
    fun `пустая история не ломает выгрузку`() {
        val text = SubscriberHistoryExport.asText(
            "Андрей", "+79001234567", emptyList(), null, now, ru
        )
        assertTrue(text.contains("нет записей"))
    }

    @Test
    fun `в выгрузке есть имя и номер`() {
        val text = SubscriberHistoryExport.asText(
            "Андрей Клинов", "+79001234567", listOf(entry()), null, now, ru
        )
        assertTrue(text.contains("Андрей Клинов"))
        assertTrue(text.contains("+79001234567"))
    }

    @Test
    fun `заметка попадает в выгрузку`() {
        // Она часть истории общения не меньше самих звонков
        val note = NumberNote(
            "+79001234567", "обещал перезвонить", listOf("работа"),
            // updatedAt именованным: между тегами и временем в модели
            // появилось поле customLabel, и позиционный аргумент
            // подставлялся уже в него
            updatedAt = now
        )
        val text = SubscriberHistoryExport.asText(
            "Андрей", "+79001234567", listOf(entry()), note, now, ru
        )
        assertTrue(text.contains("обещал перезвонить"))
        assertTrue(text.contains("работа"))
    }

    @Test
    fun `есть оговорка об отсутствии записи разговоров`() {
        /*
         * Без неё документ выглядит как полный отчёт, и отсутствие
         * содержания разговоров читается как пропуск, а не как факт.
         */
        val text = SubscriberHistoryExport.asText(
            "Андрей", "+79001234567", listOf(entry()), null, now, ru
        )
        assertTrue(text.contains("не записывается"))
    }

    @Test
    fun `имя файла содержит абонента и дату`() {
        val name = SubscriberHistoryExport.suggestedFileName("Андрей Клинов", "+79001234567", now)
        assertTrue(name.startsWith("ogneliss_"))
        assertTrue(name.endsWith(".txt"))
        assertTrue(name.contains("Андрей_Клинов"))
    }

    @Test
    fun `недопустимые символы убираются из имени файла`() {
        // Иначе сохранение молча не удастся, и причина будет неочевидна
        val name = SubscriberHistoryExport.suggestedFileName("А/Б:В*Г?", "+79001234567", now)
        listOf('/', ':', '*', '?').forEach {
            assertFalse("Символ $it недопустим в имени файла", name.contains(it))
        }
    }

    @Test
    fun `без имени берётся номер`() {
        val name = SubscriberHistoryExport.suggestedFileName(null, "+7 (900) 123-45-67", now)
        assertTrue(name.contains("79001234567"))
    }

    @Test
    fun `csv использует точку с запятой`() {
        // В русской локали Excel ждёт именно её: файл с запятыми
        // открывается одной строкой
        val csv = SubscriberHistoryExport.asCsv(listOf(entry()), ru)
        assertTrue(csv.lines().first().contains(';'))
    }

    @Test
    fun `csv содержит заголовок и строку на каждый вызов`() {
        val csv = SubscriberHistoryExport.asCsv(
            listOf(entry(), entry(date = now - 1000)), ru
        )
        assertEquals("Заголовок плюс две записи", 3, csv.trim().lines().size)
    }
}
