package com.ogneliss.dialer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Max убран из свайпов и «Написать» (2.9.8, автор: «у них так и не заработал
 * выбор абонента по номеру»). Разбор — SwipeAction.from.
 */
class SwipeActionRetiredTest {

    @Test
    fun `сохранённый свайп Max становится SMS`() {
        assertEquals(SwipeAction.SMS, SwipeAction.from("max"))
    }

    @Test
    fun `Max нет среди действий`() {
        assertTrue(SwipeAction.entries.none { it.key == "max" })
    }

    @Test
    fun `остальные ключи читаются как были`() {
        SwipeAction.entries.forEach { assertEquals(it, SwipeAction.from(it.key)) }
        assertEquals(SwipeAction.NONE, SwipeAction.from("нет такого"))
    }
}
