package com.ogneliss.dialer.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ЖУРНАЛ ПО ДНЯМ — КАК ЛЕНТА OGNELISS MAIL (1.8.1).
 *
 * Чистая логика без Android — покрыта тестами (JournalDaysTest).
 *
 * ── Было ───────────────────────────────────────────────────────────────────
 * С 1.0 журнал делился на «Сегодня», «Вчера», «На этой неделе»,
 * «На прошлой неделе» и «Раньше» (CallLogDateGroup — он остался у истории
 * в карточке). Кто листал далеко, видел под «Раньше» месяцы звонков
 * без единой подписи дня.
 *
 * ── Стало (автор: «добавим плашку с датой в журнал… можно сделать UI
 * одинаковыми» с Ogneliss Mail) ─────────────────────────────────────────────
 * Каждый день — своя подпись, как в почте: «СЕГОДНЯ», «ВЧЕРА»,
 * «29 октября · ЧЕТВЕРГ»; прошлый год — «29 октября 2025» (день недели
 * давнего дня бесполезен, а год без подписи не узнать). При прокрутке
 * подпись верхнего дня висит плашкой вверху (FloatingDatePill).
 *
 * ── Границы календарные ────────────────────────────────────────────────────
 * Звонок вчера в 23:50 — «Вчера», даже если сейчас час ночи. Сутки
 * отсчитываются календарём, а не вычитанием 24 часов: в день перевода
 * часов в них 23 или 25 часов.
 *
 * Звонок «из будущего» (сбитые часы, запись из другого часового пояса) —
 * сегодняшний, как и прежде: прятать его в завтрашний день незачем.
 */
object JournalDays {

    /** Начало календарного дня, в который попал [millis]; будущее — сегодня. */
    fun dayStart(millis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        startOfDay(minOf(millis, nowMillis), zone)

    /**
     * Подпись дня.
     *
     * @param dayStart начало дня (из [dayStart])
     * @param dayMonth шаблон «число и месяц» для языка: на Android —
     *        DateFormat.getBestDateTimePattern(locale, "dMMMM"): «d MMMM»
     *        по-русски, «MMMM d» по-английски
     * @param dayMonthYear то же с годом («dMMMMy»)
     */
    fun label(
        dayStart: Long,
        nowMillis: Long,
        locale: Locale,
        today: String,
        yesterday: String,
        dayMonth: String,
        dayMonthYear: String,
        zone: TimeZone = TimeZone.getDefault()
    ): String {
        val todayStart = startOfDay(nowMillis, zone)
        if (dayStart >= todayStart) return today.uppercase(locale)
        val yesterdayStart = Calendar.getInstance(zone).apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis
        if (dayStart >= yesterdayStart) return yesterday.uppercase(locale)

        val date = Date(dayStart)
        val sameYear = year(dayStart, zone) == year(nowMillis, zone)
        // Вся подпись прописными (2.4.0): было «29 октября · ЧЕТВЕРГ» — два
        // регистра в одной подписи, рядом с «СЕГОДНЯ» и «ВЧЕРА»
        if (!sameYear) return format(dayMonthYear, locale, zone, date).uppercase(locale)
        val weekday = format("EEEE", locale, zone, date)
        return (format(dayMonth, locale, zone, date) + " · " + weekday).uppercase(locale)
    }

    private fun format(pattern: String, locale: Locale, zone: TimeZone, date: Date): String =
        SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(date)

    private fun year(millis: Long, zone: TimeZone): Int =
        Calendar.getInstance(zone).apply { timeInMillis = millis }.get(Calendar.YEAR)

    private fun startOfDay(millis: Long, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
