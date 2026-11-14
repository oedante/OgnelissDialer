package com.ogneliss.dialer.sms

import com.ogneliss.dialer.sms.MessageLinks.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Ссылки, почта, номера и даты в SMS (2.5.0) — на текстах банковских SMS
 * со снимка автора и на том, что выделяться НЕ должно (суммы, проценты,
 * время, «т.е.»).
 */
class MessageLinksTest {

    /** 14.11.2026, полдень UTC — «когда пришло». */
    private val ref = LocalDate.of(2026, 11, 14).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun find(text: String) = MessageLinks.find(text, ref, ZoneOffset.UTC)
    private fun parts(text: String) = find(text).map { it.kind to text.substring(it.start, it.end) }

    @Test
    fun `ссылка со схемой и без — до точки в конце предложения`() {
        val text = "Обновление доступно по ссылке: http://s.gpb.ru/w/ezrerxAJi"
        assertEquals(listOf(Kind.URL to "http://s.gpb.ru/w/ezrerxAJi"), parts(text))

        val bare = "Переведите активы до 31.01.2027 s.gpb.ru/w/veCKymz7j. Банк ГПБ (АО). 18+. Реклама"
        val found = find(bare)
        assertEquals(listOf(Kind.DATE to "31.01.2027", Kind.URL to "s.gpb.ru/w/veCKymz7j"), parts(bare))
        assertEquals("https://s.gpb.ru/w/veCKymz7j", found[1].target)
    }

    @Test
    fun `дата с «г» и ссылка с переносом в пути`() {
        val text = "новыми тарифами с 30.11.2026г. s.gpb.ru/m/ysWjbVXEI Банк ГПБ (АО)"
        val found = find(text)
        assertEquals(listOf(Kind.DATE, Kind.URL), found.map { it.kind })
        assertEquals(LocalDate.of(2026, 11, 30), found[0].date)
        assertEquals("s.gpb.ru/m/ysWjbVXEI", text.substring(found[1].start, found[1].end))
    }

    @Test
    fun `проценты, суммы, время и сокращения — не ссылки и не номера`() {
        assertTrue(find("Вклад \"Особый\" со ставкой до 13,4% и 10 000 000 руб.").isEmpty())
        assertTrue(find("Встреча в 12:20, т.е. через час").isEmpty())
        assertTrue(find("Версия 1.5 доступна").isEmpty())
    }

    @Test
    fun `почта и номер телефона`() {
        val text = "Пишите на help@bank.ru или звоните 8-800-100-07-01, +7 (900) 123-45-67."
        val found = find(text)
        assertEquals(listOf(Kind.EMAIL, Kind.PHONE, Kind.PHONE), found.map { it.kind })
        assertEquals("help@bank.ru", found[0].target)
        assertEquals("88001000701", found[1].target)
        assertEquals("+79001234567", found[2].target)
    }

    @Test
    fun `дата словами — с годом и без`() {
        val found = find("Акция до 31 декабря, оплатить до 5 января 2027")
        assertEquals(listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 5)), found.map { it.date })
        // «1 января» в ноябрьском SMS — это следующий год
        assertEquals(LocalDate.of(2027, 1, 1), find("С 1 января тарифы меняются")[0].date)
        // «1 ноября» в SMS от 14 ноября — этот год, только что прошедшее
        assertEquals(LocalDate.of(2026, 11, 1), find("С 1 Ноября тарифы изменились")[0].date)
    }

    @Test
    fun `несуществующая дата — не дата`() {
        assertTrue(find("код 31.02.2026").none { it.kind == Kind.DATE })
    }

    @Test
    fun `адрес с www и кириллический домен`() {
        assertEquals(listOf(Kind.URL to "www.example.com/a?b=1"), parts("Смотрите www.example.com/a?b=1"))
        assertEquals(listOf(Kind.URL to "госуслуги.рф"), parts("Подробнее на госуслуги.рф"))
    }
}
