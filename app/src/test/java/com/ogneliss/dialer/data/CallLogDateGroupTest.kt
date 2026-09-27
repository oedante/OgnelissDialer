package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * ТЕСТЫ ГРУППИРОВКИ ПО ДАВНОСТИ.
 *
 * Границы календарные, а не по числу часов, и именно на этом легче
 * всего ошибиться: «вчера в 23:50» при текущем времени «сегодня 00:30»
 * отстоит на сорок минут, но относится ко вчерашнему дню.
 */
class CallLogDateGroupTest {

    private val day = 24L * 60 * 60 * 1000

    /** Фиксированный момент: среда, 12:00. */
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.AUGUST, 12, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun group(millis: Long) = CallLogDateGroup.of(millis, now)

    @Test
    fun `звонок час назад — сегодня`() {
        assertEquals(CallLogDateGroup.TODAY, group(now - 60 * 60 * 1000))
    }

    @Test
    fun `звонок сегодня ночью — сегодня`() {
        // 00:30 того же дня: прошло 11 с половиной часов, но день тот же
        assertEquals(CallLogDateGroup.TODAY, group(now - 11.5.toLong() * 60 * 60 * 1000))
    }

    @Test
    fun `вчера поздно вечером — вчера, а не сегодня`() {
        // Ключевой случай: по числу часов близко, по календарю — вчера
        val yesterdayEvening = now - 13 * 60 * 60 * 1000
        assertEquals(CallLogDateGroup.YESTERDAY, group(yesterdayEvening))
    }

    @Test
    fun `позавчера — на этой неделе`() {
        // Среда минус два дня — понедельник, неделя началась с него
        assertEquals(CallLogDateGroup.THIS_WEEK, group(now - 2 * day))
    }

    @Test
    fun `прошлая среда — на прошлой неделе`() {
        assertEquals(CallLogDateGroup.LAST_WEEK, group(now - 7 * day))
    }

    @Test
    fun `три недели назад — раньше`() {
        assertEquals(CallLogDateGroup.EARLIER, group(now - 21 * day))
    }

    @Test
    fun `будущее показывается как сегодня`() {
        // Сбитые часы устройства или запись из другого часового пояса.
        // Прятать её в «Раньше» значило бы потерять из виду
        assertEquals(CallLogDateGroup.TODAY, group(now + day))
    }

    @Test
    fun `порядок групп соответствует давности`() {
        // От него зависит сортировка заголовков на экране
        assertEquals(0, CallLogDateGroup.TODAY.ordinal)
        assertEquals(4, CallLogDateGroup.EARLIER.ordinal)
    }
}
