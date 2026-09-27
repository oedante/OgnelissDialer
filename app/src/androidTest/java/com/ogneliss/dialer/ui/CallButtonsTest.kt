package com.ogneliss.dialer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.ui.components.CallButtons
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * ТЕСТЫ КНОПОК ВЫЗОВА.
 *
 * Здесь проверяется поведение, которое уже один раз было ошибочным:
 * кнопка передавала позицию в списке вместо индекса слота, и при одной
 * активной карте во втором слоте звонок ушёл бы не с той SIM.
 *
 * Логику раскладки проверяет `SimCallPlanTest` на JVM. Здесь — то,
 * что логика правильно доходит до экрана.
 */
class CallButtonsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sim(subId: Int, slot: Int, label: String) =
        SimSlot(subscriptionId = subId, slotIndex = slot, label = label, number = null)

    @Test
    fun приОднойКартеПоказываетсяОднаКнопка() {
        compose.setContent {
            OgnelissDialerTheme {
                CallButtons(sims = listOf(sim(1, 0, "SIM 1")), enabled = true, onCall = {})
            }
        }

        val buttons = compose.onAllNodesWithContentDescription("Позвонить")
        assertEquals(1, buttons.fetchSemanticsNodes().size)
    }

    @Test
    fun приДвухКартахПоказываютсяДвеКнопкиСНомерамиСлотов() {
        compose.setContent {
            OgnelissDialerTheme {
                CallButtons(
                    sims = listOf(sim(1, 0, "Рабочая"), sim(2, 1, "Личная")),
                    enabled = true,
                    onCall = {}
                )
            }
        }

        compose.onNodeWithText("SIM 1").assertIsDisplayed()
        compose.onNodeWithText("SIM 2").assertIsDisplayed()
        // Имена слотов делают выбор осмысленным: без них две одинаковые
        // кнопки заставляли бы гадать, какая какая
        compose.onNodeWithText("Рабочая").assertIsDisplayed()
        compose.onNodeWithText("Личная").assertIsDisplayed()
    }

    @Test
    fun кнопкаПередаётИндексСлотаАНеПозициюВСписке() {
        // Ровно та ошибка, что была найдена анализатором: при одной
        // активной карте во ВТОРОМ слоте позиция равна нулю, а слот —
        // единице, и звонок ушёл бы не с той карты
        var calledSlot = -99

        compose.setContent {
            OgnelissDialerTheme {
                CallButtons(
                    sims = listOf(sim(5, 1, "Единственная"), sim(6, 2, "Третий слот")),
                    enabled = true,
                    onCall = { calledSlot = it }
                )
            }
        }

        compose.onNodeWithText("SIM 2").performClick()

        assertEquals("Должен передаваться slotIndex, а не позиция", 1, calledSlot)
    }

    @Test
    fun приПустомНомереКнопкаНеактивна() {
        compose.setContent {
            OgnelissDialerTheme {
                CallButtons(sims = listOf(sim(1, 0, "SIM 1")), enabled = false, onCall = {})
            }
        }

        compose.onAllNodesWithContentDescription("Позвонить")[0].assertIsNotEnabled()
    }
}
