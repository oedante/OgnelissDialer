package com.ogneliss.dialer.call

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Правила защиты от спама — для выбранной SIM (2.9.8, форум, Bublikov34:
 * «разделение настроек спама на разные сим-карты»). Разбор — SpamSimScope.
 */
class SpamSimScopeTest {

    private val strictOnSim1 = SpamPolicy.Settings(
        blockHidden = true,
        blockNotInContacts = true,
        simScope = SpamSimScope.SIM1
    )

    private fun stranger(slot: Int?, inBlockList: Boolean = false) = SpamPolicy.SpamInput(
        number = "+79001234567",
        e164 = "+79001234567",
        isInContacts = false,
        inBlockList = inBlockList,
        settings = strictOnSim1,
        simSlot = slot
    )

    @Test
    fun `правила действуют на свою карту`() {
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.evaluate(stranger(0)).action)
    }

    @Test
    fun `на другую карту звонят все`() {
        assertEquals(SpamPolicy.Action.ALLOW, SpamPolicy.evaluate(stranger(1)).action)
    }

    @Test
    fun `скрытый номер на другую карту - тоже пропущен`() {
        val hidden = stranger(1).copy(number = null, e164 = "")
        assertEquals(SpamPolicy.Action.ALLOW, SpamPolicy.evaluate(hidden).action)
    }

    @Test
    fun `чёрный список - для обеих карт`() {
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.evaluate(stranger(1, inBlockList = true)).action)
    }

    @Test
    fun `карта неизвестна - правила действуют`() {
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.evaluate(stranger(null)).action)
    }

    @Test
    fun `по умолчанию - все карты, как было`() {
        val all = stranger(1).copy(settings = strictOnSim1.copy(simScope = SpamSimScope.ALL))
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.evaluate(all).action)
        assertEquals(SpamSimScope.ALL, SpamSimScope.from(null))
        assertEquals(SpamSimScope.ALL, SpamSimScope.from("нет такого"))
    }
}
