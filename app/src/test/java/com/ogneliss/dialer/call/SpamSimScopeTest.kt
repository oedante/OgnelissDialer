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

    /*
     * 2.9.12 — свои правила для каждой SIM (форум, Bublikov34: «на личной — запрет на
     * номера не из контактов, на рабочей — только спам блокировать»).
     */
    private val personalAndWork = SpamPolicy.Settings(
        blockNotInContacts = true,
        blockTaggedSpam = true,
        simScope = SpamSimScope.SEPARATE,
        sim2 = SpamPolicy.Settings(blockNotInContacts = false, blockTaggedSpam = true)
    )

    private fun call(slot: Int?, tags: List<String> = emptyList()) =
        stranger(slot).copy(settings = personalAndWork, tags = tags)

    @Test
    fun `свои правила - незнакомый на личной SIM 1 отклонён`() {
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.evaluate(call(0)).action)
    }

    @Test
    fun `свои правила - незнакомый на рабочей SIM 2 проходит`() {
        assertEquals(SpamPolicy.Action.ALLOW, SpamPolicy.evaluate(call(1)).action)
    }

    @Test
    fun `свои правила - метка спам на SIM 2 по её правилу`() {
        val spam = call(1, tags = SpamPolicy.BLOCKING_TAGS.take(1).toList())
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.evaluate(spam).action)
    }

    @Test
    fun `свои правила - карта неизвестна, действуют общие`() {
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.evaluate(call(null)).action)
    }

    @Test
    fun `свои правила не заданы - для SIM 2 общие`() {
        val noOwn = call(1).copy(settings = personalAndWork.copy(sim2 = null))
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.evaluate(noOwn).action)
    }

    @Test
    fun `по умолчанию - все карты, как было`() {
        val all = stranger(1).copy(settings = strictOnSim1.copy(simScope = SpamSimScope.ALL))
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.evaluate(all).action)
        assertEquals(SpamSimScope.ALL, SpamSimScope.from(null))
        assertEquals(SpamSimScope.ALL, SpamSimScope.from("нет такого"))
    }
}
