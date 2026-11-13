package com.ogneliss.dialer.call

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ВТОРОЙ ВЫЗОВ, УДЕРЖАНИЕ, КОНФЕРЕНЦИЯ (2.4.2) — сценариями, без Telecom.
 *
 * Каждый тест — то, что человек видит и нажимает на экране вызова:
 * второй входящий во время разговора, «Ответить и удержать», «Завершить
 * и ответить», «Добавить вызов», «Переключить», «Объединить», конференция.
 * Линии называются по ролям: «мама» говорит, «работа» звонит и т. п.
 */
@Suppress("DEPRECATION")
class CallLinesTest {

    private val hold = Call.Details.CAPABILITY_HOLD
    private val supportHold = Call.Details.CAPABILITY_SUPPORT_HOLD
    private val mergeConf = Call.Details.CAPABILITY_MERGE_CONFERENCE
    private val swapConf = Call.Details.CAPABILITY_SWAP_CONFERENCE

    private fun active(key: String, caps: Int = hold, with: Set<String> = emptySet()) = Line(key, Call.STATE_ACTIVE, capabilities = caps, conferenceable = with)
    private fun held(key: String, caps: Int = hold, with: Set<String> = emptySet()) = Line(key, Call.STATE_HOLDING, capabilities = caps, conferenceable = with)
    private fun ringing(key: String) = Line(key, Call.STATE_RINGING)
    private fun dialing(key: String) = Line(key, Call.STATE_DIALING)

    // ── Один вызов ─────────────────────────────────────────────────────────

    @Test
    fun `один разговор — он текущий, второй линии и ждущего нет`() {
        val lines = listOf(active("мама"))
        assertEquals("мама", CallLines.primary(lines)?.key)
        assertNull(CallLines.secondary(lines))
        assertNull(CallLines.waiting(lines))
        assertFalse(CallLines.hasIncoming(lines))
    }

    @Test
    fun `удержать можно только при CAPABILITY_HOLD — SUPPORT_HOLD значит «сейчас нельзя»`() {
        assertTrue(CallLines.canHold(listOf(active("мама", caps = hold))))
        assertFalse(CallLines.canHold(listOf(active("мама", caps = supportHold))))
        // Удержанный снять можно всегда, иначе с ним не вернуться
        assertTrue(CallLines.canHold(listOf(held("мама", caps = 0))))
    }

    @Test
    fun `кнопка «Удержать» — удерживает идущий и снимает удержанный`() {
        assertEquals(CallLines.HoldAction.Hold("мама"), CallLines.holdAction(listOf(active("мама"))))
        assertEquals(CallLines.HoldAction.Unhold("мама"), CallLines.holdAction(listOf(held("мама"))))
        assertNull(CallLines.holdAction(emptyList<Line<String>>()))
    }

    @Test
    fun `завершённые и завершающиеся вызовы не считаются`() {
        val lines = listOf(
            Line("старый", Call.STATE_DISCONNECTED),
            Line("уходит", Call.STATE_DISCONNECTING),
            held("мама")
        )
        assertEquals("мама", CallLines.primary(lines)?.key)
        assertEquals(listOf("мама"), CallLines.topLevel(lines).map { it.key })
    }

    // ── Второй входящий во время разговора ─────────────────────────────────

    @Test
    fun `второй входящий — ждёт ответа, текущим остаётся разговор`() {
        val lines = listOf(active("мама"), ringing("работа"))
        assertEquals("мама", CallLines.primary(lines)?.key)
        assertEquals("работа", CallLines.waiting(lines)?.key)
        assertTrue(CallLines.hasIncoming(lines))
    }

    @Test
    fun `«Завершить и ответить» — сначала завершить текущий, ответить после`() {
        val lines = listOf(active("мама"), ringing("работа"))
        assertEquals(CallLines.EndAndAnswer.ReleaseFirst("мама", "работа"), CallLines.endAndAnswer(lines))
    }

    @Test
    fun `ответ после завершения — только когда завершился тот самый и второй ещё звонит`() {
        val pending = "мама" to "работа"
        assertTrue(CallLines.answerAfterRelease(pending, "мама", Call.STATE_RINGING))
        // Завершился другой вызов — не наш случай
        assertFalse(CallLines.answerAfterRelease(pending, "сосед", Call.STATE_RINGING))
        // Второй уже сбросили — отвечать не на что
        assertFalse(CallLines.answerAfterRelease(pending, "мама", Call.STATE_DISCONNECTED))
        assertFalse(CallLines.answerAfterRelease(null, "мама", Call.STATE_RINGING))
    }

    @Test
    fun `звонит один вызов — «Завершить и ответить» ничего не ждёт`() {
        // Ждущего нет: единственный звонящий — текущий
        assertNull(CallLines.endAndAnswer(listOf(ringing("работа"))))
        assertNull(CallLines.endAndAnswer(listOf(active("мама"))))
    }

    @Test
    fun `второй входящий при удержанном — текущим становится звонящий, отвечать сразу`() {
        // Говорили с мамой, удержали, и звонит работа: активного нет
        val lines = listOf(held("мама"), ringing("работа"))
        assertEquals("работа", CallLines.primary(lines)?.key)
        assertNull(CallLines.waiting(lines))
        assertEquals("мама", CallLines.secondary(lines)?.key)
    }

    // ── Добавить вызов ─────────────────────────────────────────────────────

    @Test
    fun `«Добавить вызов» — при идущем разговоре, если его можно удержать и Telecom не против`() {
        assertTrue(CallLines.canAddCall(listOf(active("мама")), telecomAllows = true))
        assertFalse(CallLines.canAddCall(listOf(active("мама")), telecomAllows = false))
        assertFalse(CallLines.canAddCall(listOf(active("мама", caps = supportHold)), telecomAllows = true))
        assertFalse(CallLines.canAddCall(listOf(dialing("мама")), telecomAllows = true))
        // Вторая линия уже есть или кто-то звонит — третью не даём
        assertFalse(CallLines.canAddCall(listOf(active("мама"), held("работа")), telecomAllows = true))
        assertFalse(CallLines.canAddCall(listOf(active("мама"), ringing("работа")), telecomAllows = true))
    }

    @Test
    fun `набор второго номера — экран показывает набираемый, первый на удержании`() {
        val lines = listOf(held("мама"), dialing("работа"))
        assertEquals("работа", CallLines.primary(lines)?.key)
        assertEquals("мама", CallLines.secondary(lines)?.key)
    }

    // ── Переключить ────────────────────────────────────────────────────────

    @Test
    fun `«Переключить» — только снять удержанный, текущий удержит Telecom`() {
        val lines = listOf(active("мама"), held("работа"))
        assertEquals(CallLines.Swap.Unhold("работа"), CallLines.swap(lines))
    }

    @Test
    fun `«Переключить» у конференции, которая умеет сама, — просим её`() {
        val lines = listOf(active("конференция", caps = hold or swapConf), held("работа"))
        assertEquals(CallLines.Swap.Conference("конференция"), CallLines.swap(lines))
    }

    @Test
    fun `«Переключить» без второй линии — ничего`() {
        assertNull(CallLines.swap(listOf(active("мама"))))
    }

    // ── Объединить ─────────────────────────────────────────────────────────

    @Test
    fun `объединить — указаны друг у друга (GSM)`() {
        val lines = listOf(active("мама", with = setOf("работа")), held("работа"))
        assertTrue(CallLines.canMerge(lines))
        assertEquals(CallLines.Merge.Conference("мама", "работа"), CallLines.merge(lines))
    }

    @Test
    fun `объединить — вызов сам собирает конференцию (CDMA, часть IMS)`() {
        val a = listOf(active("мама", caps = hold or mergeConf), held("работа"))
        assertEquals(CallLines.Merge.MergeConference("мама"), CallLines.merge(a))
        val b = listOf(active("мама"), held("работа", caps = hold or mergeConf))
        assertEquals(CallLines.Merge.MergeConference("работа"), CallLines.merge(b))
    }

    @Test
    fun `объединить — сеть ничего не сказала, кнопки нет, но попытка conference`() {
        val lines = listOf(active("мама"), held("работа"))
        assertFalse(CallLines.canMerge(lines))
        assertEquals(CallLines.Merge.Conference("мама", "работа"), CallLines.merge(lines))
    }

    @Test
    fun `объединить одну линию нельзя`() {
        assertFalse(CallLines.canMerge(listOf(active("мама"))))
        assertNull(CallLines.merge(listOf(active("мама"))))
    }

    // ── Конференция ────────────────────────────────────────────────────────

    @Test
    fun `конференция — текущая она, а не участник`() {
        val lines = listOf(
            Line("мама", Call.STATE_ACTIVE, hasParent = true),
            Line("работа", Call.STATE_ACTIVE, hasParent = true),
            active("конференция")
        )
        assertEquals("конференция", CallLines.primary(lines)?.key)
        assertNull(CallLines.secondary(lines))
        assertEquals(listOf("конференция"), CallLines.topLevel(lines).map { it.key })
    }

    @Test
    fun `конференция на удержании и новый разговор — вторая линия конференция`() {
        val lines = listOf(
            Line("мама", Call.STATE_HOLDING, hasParent = true),
            Line("работа", Call.STATE_HOLDING, hasParent = true),
            held("конференция"),
            active("сосед")
        )
        assertEquals("сосед", CallLines.primary(lines)?.key)
        assertEquals("конференция", CallLines.secondary(lines)?.key)
        assertEquals(CallLines.Swap.Unhold("конференция"), CallLines.swap(lines))
    }
}
