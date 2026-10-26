package com.ogneliss.dialer.call

import com.ogneliss.dialer.data.NumberCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ПОЛИТИКИ ФИЛЬТРАЦИИ ВЫЗОВОВ.
 *
 * Самая ответственная логика приложения: ошибка здесь означает либо
 * пропущенный важный звонок, либо прошедший спам. Тесты фиксируют
 * ПОРЯДОК приоритетов правил — именно он ломается при добавлении
 * новых условий, и именно его невозможно проверить вручную.
 */
class SpamPolicyTest {

    /** Заготовка входных данных: незнакомый номер, все настройки по умолчанию. */
    private fun input(
        number: String? = "+79001234567",
        isInContacts: Boolean = false,
        inAllowList: Boolean = false,
        inBlockList: Boolean = false,
        inSystemBlockList: Boolean = false,
        tags: List<String> = emptyList(),
        category: NumberCategory = NumberCategory.PERSONAL,
        settings: SpamPolicy.Settings = SpamPolicy.Settings(),
        minuteOfDay: Int = -1,
        repeatCall: Boolean = false
    ) = SpamPolicy.SpamInput(
        number = number,
        e164 = number ?: "",
        isInContacts = isInContacts,
        inAllowList = inAllowList,
        inBlockList = inBlockList,
        inSystemBlockList = inSystemBlockList,
        tags = tags,
        category = category,
        settings = settings,
        minuteOfDay = minuteOfDay,
        repeatCall = repeatCall
    )

    // ── Базовое поведение ──────────────────────────────────────────────────

    @Test
    fun `обычный незнакомый номер пропускается`() {
        val d = SpamPolicy.evaluate(input())
        assertFalse(d.block)
        assertEquals(SpamPolicy.Reason.NO_RULE_MATCHED, d.reason)
    }

    @Test
    fun `номер из контактов пропускается`() {
        val d = SpamPolicy.evaluate(input(isInContacts = true))
        assertFalse(d.block)
        assertEquals(SpamPolicy.Reason.IN_CONTACTS, d.reason)
    }

    // ── Приоритет белого списка ────────────────────────────────────────────

    @Test
    fun `белый список отменяет чёрный список`() {
        val d = SpamPolicy.evaluate(input(inAllowList = true, inBlockList = true))
        assertFalse("Белый список должен побеждать", d.block)
        assertEquals(SpamPolicy.Reason.ALLOW_LIST, d.reason)
    }

    @Test
    fun `белый список отменяет системную блокировку`() {
        val d = SpamPolicy.evaluate(input(inAllowList = true, inSystemBlockList = true))
        assertFalse(d.block)
    }

    @Test
    fun `белый список отменяет метку спама`() {
        val d = SpamPolicy.evaluate(input(inAllowList = true, tags = listOf("spam")))
        assertFalse(d.block)
    }

    @Test
    fun `белый список отменяет блокировку всех незнакомых`() {
        val d = SpamPolicy.evaluate(
            input(
                inAllowList = true,
                settings = SpamPolicy.Settings(blockNotInContacts = true)
            )
        )
        assertFalse("Курьер из белого списка должен дозвониться", d.block)
    }

    // ── Списки блокировки ──────────────────────────────────────────────────

    @Test
    fun `номер из чёрного списка блокируется`() {
        val d = SpamPolicy.evaluate(input(inBlockList = true))
        assertTrue(d.block)
        assertEquals(SpamPolicy.Reason.APP_BLOCK_LIST, d.reason)
    }

    @Test
    fun `номер из системного списка блокируется`() {
        val d = SpamPolicy.evaluate(input(inSystemBlockList = true))
        assertTrue(d.block)
        assertEquals(SpamPolicy.Reason.SYSTEM_BLOCK_LIST, d.reason)
    }

    // ── Метки пользователя ─────────────────────────────────────────────────

    @Test
    fun `метка спам блокирует при включённой настройке`() {
        val d = SpamPolicy.evaluate(input(tags = listOf("spam")))
        assertTrue(d.block)
        assertEquals(SpamPolicy.Reason.USER_TAG, d.reason)
    }

    @Test
    fun `метка спам не блокирует при выключенной настройке`() {
        val d = SpamPolicy.evaluate(
            input(
                tags = listOf("spam"),
                settings = SpamPolicy.Settings(blockTaggedSpam = false)
            )
        )
        assertFalse(d.block)
    }

    @Test
    fun `безобидная метка не блокирует`() {
        val d = SpamPolicy.evaluate(input(tags = listOf("bank", "delivery")))
        assertFalse("Метка «банк» не повод блокировать", d.block)
    }

    // ── Скрытые номера ─────────────────────────────────────────────────────

    @Test
    fun `скрытый номер пропускается по умолчанию`() {
        val d = SpamPolicy.evaluate(input(number = null))
        assertFalse(d.block)
        assertEquals(SpamPolicy.Reason.HIDDEN_ALLOWED, d.reason)
    }

    @Test
    fun `скрытый номер блокируется при включённой настройке`() {
        val d = SpamPolicy.evaluate(
            input(number = null, settings = SpamPolicy.Settings(blockHidden = true))
        )
        assertTrue(d.block)
    }

    // ── Блокировка незнакомых ──────────────────────────────────────────────

    @Test
    fun `блокировка незнакомых не трогает контакты`() {
        val d = SpamPolicy.evaluate(
            input(
                isInContacts = true,
                settings = SpamPolicy.Settings(blockNotInContacts = true)
            )
        )
        assertFalse(d.block)
    }

    @Test
    fun `блокировка незнакомых блокирует незнакомого`() {
        val d = SpamPolicy.evaluate(
            input(settings = SpamPolicy.Settings(blockNotInContacts = true))
        )
        assertTrue(d.block)
        assertEquals(SpamPolicy.Reason.NOT_IN_CONTACTS, d.reason)
    }

    // ── Три уровня ответа ──────────────────────────────────────────────────

    @Test
    fun `явные правила блокируют, а не заглушают`() {
        // Пользователь сам добавил номер в чёрный список: сомнений нет,
        // и смягчать его решение неправильно
        assertEquals(
            SpamPolicy.Action.BLOCK,
            SpamPolicy.evaluate(input(inBlockList = true)).action
        )
        assertEquals(
            SpamPolicy.Action.BLOCK,
            SpamPolicy.evaluate(input(tags = listOf("spam"))).action
        )
    }

    // ── Защита от регрессий ────────────────────────────────────────────────

    @Test
    fun `контакт не блокируется никакими настройками кроме явных списков`() {
        val aggressive = SpamPolicy.Settings(
            blockHidden = true,
            blockNotInContacts = true
        )
        val d = SpamPolicy.evaluate(input(isInContacts = true, settings = aggressive))
        assertFalse("Знакомый номер должен дозваниваться всегда", d.block)
    }

    @Test
    fun `явная блокировка сильнее наличия в контактах`() {
        // Пользователь заблокировал номер, который есть в контактах —
        // например, бывшего коллегу. Его воля важнее факта наличия в книге.
        val d = SpamPolicy.evaluate(input(isInContacts = true, inBlockList = true))
        assertTrue(d.block)
    }

    // ── Тихие часы (1.8.0) ─────────────────────────────────────────────────

    private val night = SpamPolicy.Settings(quietHours = QuietHours.NIGHT_22_08)

    @Test
    fun `тихие часы глушат незнакомого ночью`() {
        val d = SpamPolicy.evaluate(input(settings = night, minuteOfDay = 23 * 60))
        assertEquals(SpamPolicy.Action.SILENCE, d.action)
        assertEquals(SpamPolicy.Reason.QUIET_HOURS, d.reason)
    }

    @Test
    fun `тихие часы не трогают день`() {
        val d = SpamPolicy.evaluate(input(settings = night, minuteOfDay = 12 * 60))
        assertEquals(SpamPolicy.Action.ALLOW, d.action)
    }

    @Test
    fun `перезвонивший за пятнадцать минут звонит`() {
        val d = SpamPolicy.evaluate(input(settings = night, minuteOfDay = 3 * 60, repeatCall = true))
        assertEquals(SpamPolicy.Action.ALLOW, d.action)
        assertEquals(SpamPolicy.Reason.QUIET_REPEAT, d.reason)
    }

    @Test
    fun `контакт и белый список звонят и в тихие часы`() {
        val contact = SpamPolicy.evaluate(input(isInContacts = true, settings = night, minuteOfDay = 2 * 60))
        assertEquals(SpamPolicy.Action.ALLOW, contact.action)
        val allowed = SpamPolicy.evaluate(input(inAllowList = true, settings = night, minuteOfDay = 2 * 60))
        assertEquals(SpamPolicy.Action.ALLOW, allowed.action)
    }

    @Test
    fun `блокировка сильнее тихих часов`() {
        val strict = night.copy(blockNotInContacts = true)
        val d = SpamPolicy.evaluate(input(settings = strict, minuteOfDay = 1 * 60))
        assertEquals(SpamPolicy.Action.BLOCK, d.action)
    }

    @Test
    fun `скрытый номер ночью без звука`() {
        val d = SpamPolicy.evaluate(input(number = null, settings = night, minuteOfDay = 5 * 60))
        assertEquals(SpamPolicy.Action.SILENCE, d.action)
        val blocked = SpamPolicy.evaluate(
            input(number = null, settings = night.copy(blockHidden = true), minuteOfDay = 5 * 60)
        )
        assertEquals(SpamPolicy.Action.BLOCK, blocked.action)
    }

    @Test
    fun `без времени тихие часы не действуют`() {
        val d = SpamPolicy.evaluate(input(settings = SpamPolicy.Settings(quietHours = QuietHours.ALWAYS)))
        assertEquals(SpamPolicy.Action.ALLOW, d.action)
    }

    @Test
    fun `границы промежутков`() {
        assertTrue(QuietHours.NIGHT_22_08.contains(22 * 60))
        assertTrue(QuietHours.NIGHT_22_08.contains(0))
        assertTrue(QuietHours.NIGHT_22_08.contains(8 * 60 - 1))
        assertFalse(QuietHours.NIGHT_22_08.contains(8 * 60))
        assertFalse(QuietHours.NIGHT_22_08.contains(22 * 60 - 1))
        assertTrue(QuietHours.NIGHT_23_07.contains(6 * 60 + 59))
        assertFalse(QuietHours.NIGHT_23_07.contains(7 * 60))
        assertTrue(QuietHours.ALWAYS.contains(0))
        assertTrue(QuietHours.ALWAYS.contains(24 * 60 - 1))
        assertFalse(QuietHours.OFF.contains(3 * 60))
        assertEquals(QuietHours.OFF, QuietHours.from(null))
        assertEquals(QuietHours.NIGHT_23_07, QuietHours.from("NIGHT_23_07"))
    }

    @Test
    fun `метка тихого вызова живёт минуту и забирается один раз`() {
        QuietCallGate.mark("+79000000001", now = 1_000L)
        assertTrue(QuietCallGate.take("+79000000001", now = 30_000L))
        // Забрана — второй раз нет
        assertFalse(QuietCallGate.take("+79000000001", now = 31_000L))
        QuietCallGate.mark("+79000000002", now = 1_000L)
        assertFalse(QuietCallGate.take("+79000000002", now = 120_000L))
    }
}
