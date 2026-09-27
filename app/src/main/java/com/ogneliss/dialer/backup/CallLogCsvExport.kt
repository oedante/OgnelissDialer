package com.ogneliss.dialer.backup

import com.ogneliss.dialer.data.CallLogEntry
import com.ogneliss.dialer.data.CallType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ВЫГРУЗКА ВСЕГО ЖУРНАЛА В CSV.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧЕМ ОТЛИЧАЕТСЯ ОТ ВЫГРУЗКИ ПО АБОНЕНТУ
 * ══════════════════════════════════════════════════════════════════════════
 * SubscriberHistoryExport делает отчёт для человека: сводка, заметка,
 * читаемые даты. Его открывают и читают глазами, обычно в споре
 * с одним собеседником.
 *
 * Здесь задача другая: выгрузить всё и обработать в таблице —
 * посчитать время разговоров за месяц, свести по номерам, приложить
 * к отчёту о работе. Значит нужен не текст, а CSV, который открывается
 * в Excel и LibreOffice без бубна.
 *
 * ── Разделитель: точка с запятой ───────────────────────────────────────────
 * Не запятая. В русской, немецкой и французской локалях Excel считает
 * разделителем именно её, а файл с запятыми открывает одним столбцом.
 * Человек при этом видит кашу и винит приложение — и будет прав.
 *
 * ── Кодировка: UTF-8 с BOM ─────────────────────────────────────────────────
 * Без BOM Excel читает файл в системной кодировке, и кириллические имена
 * превращаются в «Ð˜Ð²Ð°Ð½». BOM — три байта, которые эту беду снимают;
 * LibreOffice и любые скрипты их игнорируют.
 */
object CallLogCsvExport {

    /** Три байта, по которым Excel узнаёт UTF-8. */
    const val BOM = "\uFEFF"

    fun asCsv(
        entries: List<CallLogEntry>,
        nameByNumber: Map<String, String> = emptyMap(),
        locale: Locale = Locale.getDefault()
    ): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", locale)
        val timeFormat = SimpleDateFormat("HH:mm:ss", locale)

        return buildString {
            append(BOM)
            appendLine(
                listOf(
                    "Дата", "Время", "Номер", "Имя", "Тип",
                    "Длительность, с", "Длительность"
                ).joinToString(";")
            )

            entries.sortedByDescending { it.date }.forEach { entry ->
                val moment = Date(entry.date)
                appendLine(
                    listOf(
                        dateFormat.format(moment),
                        timeFormat.format(moment),
                        entry.number,
                        nameByNumber[entry.number].orEmpty(),
                        typeLabel(entry.type),
                        entry.durationSec.toString(),
                        formatDuration(entry.durationSec)
                    ).joinToString(";") { escape(it) }
                )
            }
        }
    }

    /**
     * Экранирование по правилам CSV.
     *
     * Поле с разделителем, кавычкой или переносом строки берётся
     * в кавычки, а кавычки внутри удваиваются. Имя вида
     * «Иванов И.И., бухгалтерия» иначе разъедет файл по столбцам.
     */
    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else {
            value
        }

    private fun typeLabel(type: CallType): String = when (type) {
        CallType.INCOMING -> "входящий"
        CallType.OUTGOING -> "исходящий"
        CallType.MISSED -> "пропущенный"
        CallType.REJECTED -> "отклонённый"
        CallType.BLOCKED -> "заблокированный"
        CallType.VOICEMAIL -> "голосовая почта"
    }

    /**
     * Длительность в виде ч:мм:сс.
     *
     * Секунды отдельным столбцом уже есть — этот для чтения глазами,
     * когда таблицу просто просматривают.
     */
    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "%d:%02d", m, s)
        }
    }

    fun suggestedFileName(exportedAt: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(exportedAt))
        return "ogneliss-calllog-$stamp.csv"
    }
}
