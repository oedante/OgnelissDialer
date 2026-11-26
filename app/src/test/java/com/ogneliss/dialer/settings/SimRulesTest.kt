package com.ogneliss.dialer.settings

import com.ogneliss.dialer.call.SimRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Карта по номеру» (2.9.11): правила «начало номера → SIM».
 */
class SimRulesTest {

    @Test
    fun `маска пишется как удобно`() {
        assertEquals("7495", SimRules.pattern("+7 495"))
        assertEquals("7495", SimRules.pattern("8 (495)"))
        assertEquals("7495", SimRules.pattern("7-495"))
        assertEquals("918", SimRules.pattern("918"))
        assertEquals("79?1", SimRules.pattern("+7 9*1"))
        assertEquals("79?1", SimRules.pattern("+7 9х1"))
        // С плюсом — как написано: «+8…» — не российский префикс
        assertEquals("86", SimRules.pattern("+86"))
    }

    @Test
    fun `негодная маска`() {
        assertNull(SimRules.pattern(""))
        assertNull(SimRules.pattern("  "))
        assertNull(SimRules.pattern("***"))
        assertNull(SimRules.pattern("abc"))
        assertNull(SimRules.pattern("1|2"))
        assertNull(SimRules.pattern("1".repeat(SimRules.MAX_MASK_LENGTH + 1)))
        assertFalse(SimRules.isValid("+"))
        assertFalse(SimRules.isValid("МТС|1"))
        assertFalse(SimRules.isValid("x*"))
    }

    @Test
    fun `номер в любом виде подходит к маске`() {
        val rules = listOf(SimRule("+7 495", 1))
        assertEquals(1, SimRules.match("+7 495 123-45-67", rules)?.slot)
        assertEquals(1, SimRules.match("84951234567", rules)?.slot)
        assertEquals(1, SimRules.match("(495) 123-45-67", rules)?.slot)
        assertNull(SimRules.match("+7 918 123-45-67", rules))
    }

    @Test
    fun `маска без кода страны`() {
        val rules = listOf(SimRule("918", 0))
        assertEquals(0, SimRules.match("+7 918 123-45-67", rules)?.slot)
        assertEquals(0, SimRules.match("8 918 123 45 67", rules)?.slot)
        assertNull(SimRules.match("+7 495 918-45-67", rules))
    }

    @Test
    fun `любая цифра`() {
        val rules = listOf(SimRule("+7 9*1", 1))
        assertEquals(1, SimRules.match("+7 921 000-00-00", rules)?.slot)
        assertEquals(1, SimRules.match("+7 981 000-00-00", rules)?.slot)
        assertNull(SimRules.match("+7 922 000-00-00", rules))
    }

    @Test
    fun `короткий номер`() {
        val rules = listOf(SimRule("0611", 1))
        assertEquals(1, SimRules.match("0611", rules)?.slot)
    }

    @Test
    fun `главнее правило с большим числом цифр, поровну — верхнее`() {
        val rules = listOf(
            SimRule("+7", 0),
            SimRule("+7 495", 1),
            SimRule("+7 495 1", 0),
            SimRule("+7 800", 1),
            SimRule("8 800", 0)
        )
        assertEquals(0, SimRules.match("+7 495 123-45-67", rules)?.slot)
        assertEquals(1, SimRules.match("+7 495 223-45-67", rules)?.slot)
        assertEquals(0, SimRules.match("+7 918 000-00-00", rules)?.slot)
        // «+7 800» и «8 800» — одна маска; выше в списке — «+7 800»
        assertEquals(1, SimRules.match("8 800 555-35-35", rules)?.slot)
    }

    @Test
    fun `хранение туда и обратно`() {
        val rules = listOf(SimRule("+7 495", 0), SimRule("8 800", 1), SimRule("9*1", 1), SimRule("Краснодар*", 1))
        assertEquals(rules, SimRules.parse(SimRules.format(rules)))
        assertEquals(emptyList<SimRule>(), SimRules.parse(null))
        // Испорченные строки пропускаются, годные остаются
        assertEquals(listOf(SimRule("918", 1)), SimRules.parse("x|495\n5|7495\n1|918\n0|"))
    }

    private val krasnodarMts = NumberFacts(region = "г. Краснодар, Краснодарский край", operator = "МТС")
    private val krai = NumberFacts(region = "р-н Славянский, Краснодарский край", operator = "Билайн")
    private val moscow = NumberFacts(region = "г. Москва и Московская область", operator = "МегаФон")
    private val oblast = NumberFacts(region = "Московская область", operator = "Tele2")

    @Test
    fun `правило с названием — регион или оператор`() {
        assertTrue(SimRules.isName("Краснодар*"))
        assertTrue(SimRules.isName("МТС"))
        assertTrue(SimRules.isName("Т2"))
        assertFalse(SimRules.isName("+7 495"))
        assertEquals("краснодар*", SimRules.namePattern("  Краснодар* "))
        assertEquals("республика северная осетия*", SimRules.namePattern("Республика  Северная Осетия*"))
    }

    @Test
    fun `регион со звёздочкой — и город, и край`() {
        val rules = listOf(SimRule("Краснодар*", 1))
        assertEquals(1, SimRules.match("+7 918 000-00-00", rules) { krasnodarMts }?.slot)
        assertEquals(1, SimRules.match("+7 918 000-00-00", rules) { krai }?.slot)
        assertNull(SimRules.match("+7 918 000-00-00", rules) { moscow })
    }

    @Test
    fun `регион без звёздочки — целым словом`() {
        val rules = listOf(SimRule("Москва", 0))
        assertEquals(0, SimRules.match("+7 495 000-00-00", rules) { moscow }?.slot)
        assertNull(SimRules.match("+7 496 000-00-00", rules) { oblast })
        // «Краснодар» без звёздочки — город, но не край
        val city = listOf(SimRule("Краснодар", 1))
        assertEquals(1, SimRules.match("+7 918 000-00-00", city) { krasnodarMts }?.slot)
        assertNull(SimRules.match("+7 918 000-00-00", city) { krai })
    }

    @Test
    fun `оператор по бренду в любом написании`() {
        assertEquals(1, SimRules.match("+7 903 000-00-00", listOf(SimRule("Билайн", 1))) { krai }?.slot)
        assertEquals(1, SimRules.match("+7 903 000-00-00", listOf(SimRule("Beeline", 1))) { krai }?.slot)
        assertEquals(0, SimRules.match("+7 977 000-00-00", listOf(SimRule("Т2", 0))) { oblast }?.slot)
        assertEquals(0, SimRules.match("+7 977 000-00-00", listOf(SimRule("Теле2", 0))) { oblast }?.slot)
        assertNull(SimRules.match("+7 918 000-00-00", listOf(SimRule("МТС", 0))) { krai })
        // Незнакомый оператор — по названию
        val local = NumberFacts(region = null, operator = "Связь сервис")
        assertEquals(1, SimRules.match("+7 900 000-00-00", listOf(SimRule("Связь*", 1))) { local }?.slot)
    }

    @Test
    fun `реестр не знает номера — правило с названием не подходит`() {
        assertNull(SimRules.match("+1 212 000 0000", listOf(SimRule("МТС", 0))) { null })
    }

    @Test
    fun `название точнее кода страны, но грубее кода города`() {
        val rules = listOf(SimRule("+7", 0), SimRule("МТС", 1), SimRule("+7 918 0", 0))
        // «МТС» точнее «+7»
        assertEquals(1, SimRules.match("+7 918 100-00-00", rules) { krasnodarMts }?.slot)
        // «+7 918 0» точнее «МТС»
        assertEquals(0, SimRules.match("+7 918 000-00-00", rules) { krasnodarMts }?.slot)
        // Регион и оператор поровну — выше в списке
        val tie = listOf(SimRule("Краснодар*", 0), SimRule("МТС", 1))
        assertEquals(0, SimRules.match("+7 918 000-00-00", tie) { krasnodarMts }?.slot)
    }

    @Test
    fun `реестр спрашивается, только если есть правила с названием`() {
        var asked = 0
        SimRules.match("+7 495 000-00-00", listOf(SimRule("+7 495", 0), SimRule("8 800", 1))) { asked++; moscow }
        assertEquals(0, asked)
        SimRules.match("+7 495 000-00-00", listOf(SimRule("Москва", 0), SimRule("МТС", 1))) { asked++; moscow }
        assertEquals(1, asked)
    }

    @Test
    fun `карта для звонков и SMS главнее правил`() {
        val rules = listOf(SimRule("+7 495", 1))
        assertEquals(0, SimRouter.decide("+7 495 123-45-67", outgoingSlot = 0, rules = rules))
        assertEquals(1, SimRouter.decide("+7 495 123-45-67", outgoingSlot = -1, rules = rules))
        assertNull(SimRouter.decide("+7 918 123-45-67", outgoingSlot = -1, rules = rules))
        assertTrue(SimRules.match("", rules) == null)
    }
}
