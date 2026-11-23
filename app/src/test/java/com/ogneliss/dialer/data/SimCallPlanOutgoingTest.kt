package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** «Карта для звонков и SMS» (2.9.1, SimCallPlan.outgoing): какие карты предлагать для исходящих. */
class SimCallPlanOutgoingTest {

    private val sim1 = SimSlot(11, 0, "SIM 1", null, "Beeline")
    private val sim2 = SimSlot(12, 1, "SIM 2", null, "МТС")

    @Test
    fun `не выбрана — обе карты, как было`() {
        assertEquals(listOf(sim1, sim2), SimCallPlan.outgoing(listOf(sim1, sim2), -1))
    }

    @Test
    fun `выбрана вторая — только она`() {
        assertEquals(listOf(sim2), SimCallPlan.outgoing(listOf(sim1, sim2), 1))
    }

    @Test
    fun `выбранной карты нет — все, звонок важнее настройки`() {
        assertEquals(listOf(sim1), SimCallPlan.outgoing(listOf(sim1), 1))
        assertEquals(emptyList<SimSlot>(), SimCallPlan.outgoing(emptyList(), 0))
    }
}
