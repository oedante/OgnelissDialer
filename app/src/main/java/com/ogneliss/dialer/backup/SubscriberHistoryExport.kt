package com.ogneliss.dialer.backup

import com.ogneliss.dialer.data.CallLogEntry
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.NumberNote
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ЭКСПОРТ ИСТОРИИ ОБЩЕНИЯ С АБОНЕНТОМ.
 *
 * Чистая логика без Android — покрыта тестами.
 *
 * ── Зачем это нужно ────────────────────────────────────────────────────────
 * Три реальных случая, и все три про то, что журнал нужен ЗА пределами
 * телефона:
 *
 * • спор с оператором или подрядчиком: «я звонил вам четырежды,
 *   вот когда именно»;
 * • отчёт по времени: сколько ушло на переговоры с конкретным клиентом;
 * • заявление о телефонном мошенничестве — там просят перечень звонков.
 *
 * Во всех случаях нужен текст, который можно приложить к письму
 * или распечатать, а не скриншот.
 *
 * ── Почему текст, а не CSV ─────────────────────────────────────────────────
 * CSV удобен машине, но человеку его нужно открыть в таблице, где он
 * рассыплется из-за разделителей и кодировки. Обычный текст читается
 * везде и вставляется в письмо как есть.
 *
 * Для тех, кому нужна таблица, есть отдельный формат — см. asCsv.
 *
 * ── Что НЕ включаем ────────────────────────────────────────────────────────
 * Содержание разговоров: их не существует, приложение не записывает
 * звонки. Об этом сказано прямо в подписи файла, чтобы не возникало
 * ожиданий.
 */
object SubscriberHistoryExport {

    /**
     * Человекочитаемый отчёт.
     *
     * @param note заметка пользователя — она часть истории общения
     *        не меньше, чем сами звонки: «обещал перезвонить во вторник»
     *        объясняет, почему после звонка была пауза
     */
    fun asText(
        displayName: String?,
        number: String,
        entries: List<CallLogEntry>,
        note: NumberNote?,
        exportedAt: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        /**
         * Отметка номера. С 1.1.1 не внутри заметки, поэтому передаётся
         * отдельно: в отчёте по абоненту она нужна — это то, что человек
         * решил о номере.
         *
         * ⚠️ Последним параметром намеренно: тесты вызывают функцию
         * позиционно, и вставка в середину подставила бы дату отчёта
         * на место отметки.
         */
        spamMark: com.ogneliss.dialer.data.SpamMarkType? = null
    ): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", locale)
        val header = displayName?.takeIf { it.isNotBlank() }?.let { "$it ($number)" } ?: number

        return buildString {
            appendLine("История вызовов")
            appendLine("Абонент: $header")
            appendLine("Выгружено: ${dateFormat.format(Date(exportedAt))}")
            appendLine()

            // Сводка первой: в споре она обычно и есть ответ на вопрос
            val stats = summarize(entries)
            appendLine("Всего вызовов: ${stats.total}")
            appendLine("  входящих: ${stats.incoming}")
            appendLine("  исходящих: ${stats.outgoing}")
            appendLine("  пропущенных: ${stats.missed}")
            appendLine("Общая длительность разговоров: ${formatDuration(stats.totalSeconds)}")
            appendLine()

            spamMark?.let {
                appendLine(
                    when (it) {
                        com.ogneliss.dialer.data.SpamMarkType.FRAUD -> "Отмечен: мошенники"
                        com.ogneliss.dialer.data.SpamMarkType.SPAM -> "Отмечен: спам"
                    }
                )
                appendLine()
            }

            note?.note?.takeIf { it.isNotBlank() }?.let {
                appendLine("Заметка: $it")
                if (note.tags.isNotEmpty()) {
                    appendLine("Метки: ${note.tags.joinToString(", ")}")
                }
                appendLine()
            }

            appendLine("Список вызовов:")
            if (entries.isEmpty()) {
                appendLine("  (нет записей)")
            } else {
                // От новых к старым: так же, как на экране. Обратный
                // порядок заставил бы сверять две разные картины
                entries.sortedByDescending { it.date }.forEach { entry ->
                    append("  ")
                    append(dateFormat.format(Date(entry.date)))
                    append("  ")
                    append(typeLabel(entry.type).padEnd(12))
                    if (entry.durationSec > 0) {
                        append(formatDuration(entry.durationSec))
                    }
                    appendLine()
                }
            }

            appendLine()
            appendLine("—")
            // Оговорка обязательна: без неё документ выглядит как полный
            // отчёт, и отсутствие содержания разговоров читается
            // как пропуск, а не как факт
            appendLine("Выгружено приложением Ogneliss Dialer.")
            appendLine("Содержание разговоров не записывается и не выгружается.")
            appendLine("Данные взяты из системного журнала вызовов устройства.")
        }
    }

    /**
     * Табличный вид для обработки в программах.
     *
     * Разделитель — точка с запятой, а не запятая: в русской локали
     * Excel по умолчанию ждёт именно её, и файл с запятыми открывается
     * одной строкой.
     */
    fun asCsv(
        entries: List<CallLogEntry>,
        locale: Locale = Locale.getDefault()
    ): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", locale)
        return buildString {
            appendLine("Дата;Тип;Длительность, с;Номер")
            entries.sortedByDescending { it.date }.forEach { entry ->
                append(dateFormat.format(Date(entry.date)))
                append(';')
                append(typeLabel(entry.type))
                append(';')
                append(entry.durationSec)
                append(';')
                appendLine(entry.number)
            }
        }
    }

    /** Имя файла: включает абонента и дату, чтобы не путать выгрузки. */
    fun suggestedFileName(
        displayName: String?,
        number: String,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(exportedAt))
        val who = displayName?.takeIf { it.isNotBlank() }
            // Символы, недопустимые в именах файлов, заменяем: иначе
            // сохранение молча не удастся, и причина будет неочевидна
            ?.replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            ?.trim()
            ?.replace(' ', '_')
            ?.take(40)
            ?: number.filter { it.isDigit() }

        return "ogneliss_${who}_$stamp.txt"
    }

    data class Summary(
        val total: Int,
        val incoming: Int,
        val outgoing: Int,
        val missed: Int,
        val totalSeconds: Long
    )

    fun summarize(entries: List<CallLogEntry>): Summary = Summary(
        total = entries.size,
        incoming = entries.count { it.type == CallType.INCOMING },
        outgoing = entries.count { it.type == CallType.OUTGOING },
        missed = entries.count { it.type == CallType.MISSED },
        totalSeconds = entries.sumOf { it.durationSec }
    )

    private fun typeLabel(type: CallType): String = when (type) {
        CallType.INCOMING -> "входящий"
        CallType.OUTGOING -> "исходящий"
        CallType.MISSED -> "пропущенный"
        CallType.REJECTED -> "отклонён"
        CallType.BLOCKED -> "заблокирован"
        CallType.VOICEMAIL -> "голосовая почта"
    }

    /**
     * Длительность в виде «1 ч 05 мин» или «2:15».
     *
     * Часы отделены словами, минуты и секунды двоеточием: «1:05:30»
     * читается неоднозначно — час пять минут или минута пять секунд.
     */
    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "—"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d ч %02d мин", hours, minutes)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, secs)
        }
    }
}
