package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Метка «тот же оператор» (1.8.0): названия карт — как их отдают SIM
 * российских операторов, названия номеров — как их сокращает
 * NumberingPlan.shortOperatorName.
 */
class OperatorMatchTest {

    @Test
    fun картыОператоровУзнаются() {
        assertEquals("mts", OperatorMatch.brandKey("MTS RUS"))
        assertEquals("beeline", OperatorMatch.brandKey("Beeline"))
        assertEquals("megafon", OperatorMatch.brandKey("MegaFon"))
        assertEquals("t2", OperatorMatch.brandKey("Tele2"))
        assertEquals("t2", OperatorMatch.brandKey("T2"))
        assertEquals("yota", OperatorMatch.brandKey("Yota"))
        assertEquals("tmobile", OperatorMatch.brandKey("Т-Мобайл"))
    }

    @Test
    fun названияИзРеестраУзнаются() {
        assertEquals("mts", OperatorMatch.brandKey("МТС"))
        assertEquals("beeline", OperatorMatch.brandKey("Билайн"))
        assertEquals("megafon", OperatorMatch.brandKey("МегаФон"))
        assertEquals("beeline", OperatorMatch.brandKey("Вымпелком"))
        assertEquals("mts", OperatorMatch.brandKey("Мобильные ТелеСистемы"))
    }

    @Test
    fun незнакомыйОператорБезКлюча() {
        assertNull(OperatorMatch.brandKey("Связь сервис"))
        assertNull(OperatorMatch.brandKey(""))
        assertNull(OperatorMatch.brandKey(null))
        // «т2» — только целым словом
        assertNull(OperatorMatch.brandKey("Т2000 Телеком"))
    }

    @Test
    fun тотЖеОператор() {
        assertTrue(OperatorMatch.same("MTS RUS", "SIM 1", "МТС"))
        assertTrue(OperatorMatch.same(null, "Билайн", "Билайн"))
        assertTrue(OperatorMatch.same("Tele2", null, "Tele2"))
    }

    @Test
    fun другойИлиНеизвестныйОператорБезМетки() {
        assertFalse(OperatorMatch.same("Beeline", "SIM 2", "МТС"))
        assertFalse(OperatorMatch.same("MTS RUS", "SIM 1", null))
        assertFalse(OperatorMatch.same(null, "SIM 1", "МТС"))
        assertFalse(OperatorMatch.same("Т-Мобайл", null, "Tele2"))
    }
}
