package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Журнал по дням (1.8.1): разбор в JournalDays. */
class JournalDaysTest {

    private val zone = TimeZone.getTimeZone("Europe/Moscow")
    private val ru = Locale.forLanguageTag("ru")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    // Суббота, 31 октября 2026, 23:00
    private val now = at(2026, 10, 31, 23)

    private fun label(millis: Long, nowMillis: Long = now, locale: Locale = ru, dayMonth: String = "d MMMM") =
        JournalDays.label(
            JournalDays.dayStart(millis, nowMillis, zone), nowMillis, locale,
            today = "Сегодня", yesterday = "Вчера",
            dayMonth = dayMonth, dayMonthYear = "d MMMM y", zone = zone
        )

    @Test
    fun `сегодня и вчера — прописными, как в почте`() {
        assertEquals("СЕГОДНЯ", label(at(2026, 10, 31, 0, 5)))
        assertEquals("ВЧЕРА", label(at(2026, 10, 30, 23, 50)))
    }

    @Test
    fun `вчера — календарное, а не 24 часа назад`() {
        // Час ночи: звонок в 23:50 — вчерашний, хотя прошло 40 минут
        val night = at(2026, 11, 1, 0, 30)
        assertEquals("ВЧЕРА", label(at(2026, 10, 31, 23, 50), nowMillis = night))
        assertEquals("СЕГОДНЯ", label(at(2026, 11, 1, 0, 10), nowMillis = night))
    }

    @Test
    fun `этот год — число, месяц и день недели`() {
        assertEquals("29 ОКТЯБРЯ · ЧЕТВЕРГ", label(at(2026, 10, 29, 12)))
        assertEquals("1 ЯНВАРЯ · ЧЕТВЕРГ", label(at(2026, 1, 1, 9)))
    }

    @Test
    fun `прошлый год — с годом и без дня недели`() {
        assertEquals("29 ОКТЯБРЯ 2025", label(at(2025, 10, 29, 12)))
    }

    @Test
    fun `звонок из будущего — сегодняшний`() {
        assertEquals("СЕГОДНЯ", label(at(2026, 11, 2, 10)))
    }

    @Test
    fun `порядок числа и месяца — по языку`() {
        assertEquals(
            "OCTOBER 29 · THURSDAY",
            label(at(2026, 10, 29, 12), locale = Locale.US, dayMonth = "MMMM d")
        )
    }

    @Test
    fun `звонки одного дня — одна группа`() {
        val morning = JournalDays.dayStart(at(2026, 10, 29, 0, 1), now, zone)
        val evening = JournalDays.dayStart(at(2026, 10, 29, 23, 59), now, zone)
        val nextDay = JournalDays.dayStart(at(2026, 10, 30, 0, 0), now, zone)
        assertEquals(morning, evening)
        assertEquals(true, nextDay > evening)
    }
}
