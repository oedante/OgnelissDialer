package com.ogneliss.dialer.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ogneliss.dialer.ui.components.MainSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Разделы и «Назад» (2.9.9, автор: «по уведомлению открывают SMS, отвечают, „Назад“ —
 * в список SMS, ещё „Назад“ — ожидают скрытие приложения, а оно переходит в журнал»).
 * «Свернуть» — значит под экраном в стеке ничего нет: previousBackStackEntry == null,
 * и «Назад» выполняет система. Разбор — SectionNavigator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SectionNavigatorTest {

    @get:Rule val rule = androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController
    private lateinit var sections: SectionNavigator

    @Before
    fun setUp() {
        val root = mutableStateOf(MainSection.JOURNAL.name)
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = MainSection.JOURNAL.route) {
                composable(MainSection.JOURNAL.route) { Text("journal") }
                composable(SectionNavigator.CONTACTS_ROUTE) { Text("contacts") }
                composable(MainSection.MESSAGES.route) { Text("messages") }
                composable(MainSection.NOTES.route) { Text("notes") }
                composable(MainSection.REMINDERS.route) { Text("reminders") }
                composable("conversation/{a}") { Text("conversation") }
                composable("subscriber/{k}") { Text("card") }
            }
        }
        rule.runOnIdle { sections = SectionNavigator(nav, root) }
    }

    private fun route() = nav.currentDestination?.route

    /** «Назад» свернёт приложение: под текущим экраном ничего нет. */
    private fun backLeavesApp() = assertNull(nav.previousBackStackEntry)

    @Test
    fun `журнал при запуске - Назад сворачивает`() = rule.runOnIdle {
        assertEquals(MainSection.JOURNAL.route, route())
        backLeavesApp()
    }

    @Test
    fun `перешли в SMS с панели - Назад сворачивает, а не ведёт в журнал`() = rule.runOnIdle {
        sections.open(MainSection.MESSAGES)
        assertEquals(MainSection.MESSAGES.route, route())
        backLeavesApp()
    }

    @Test
    fun `переписка из уведомления - Назад в список SMS, ещё Назад сворачивает`() {
        rule.runOnIdle {
            sections.ensureRoot(MainSection.MESSAGES)
            nav.navigate("conversation/1")
        }
        rule.runOnIdle {
            assertEquals("conversation/{a}", route())
            nav.popBackStack()
        }
        rule.runOnIdle {
            assertEquals(MainSection.MESSAGES.route, route())
            backLeavesApp()
        }
    }

    @Test
    fun `контакты - тоже основание`() = rule.runOnIdle {
        sections.open(MainSection.CONTACTS)
        assertEquals(SectionNavigator.CONTACTS_ROUTE, route())
        backLeavesApp()
    }

    @Test
    fun `раздел помнит открытый экран при возврате`() {
        rule.runOnIdle {
            sections.open(MainSection.MESSAGES)
            nav.navigate("conversation/7")
        }
        rule.runOnIdle { sections.open(MainSection.CONTACTS) }
        rule.runOnIdle { sections.open(MainSection.MESSAGES) }
        rule.runOnIdle {
            // Возврат в SMS — та же переписка; «Назад» — список, ещё «Назад» — свернуть
            assertEquals("conversation/{a}", route())
            nav.popBackStack()
        }
        rule.runOnIdle {
            assertEquals(MainSection.MESSAGES.route, route())
            backLeavesApp()
        }
    }

    @Test
    fun `открыт снаружи - сразу список раздела`() {
        rule.runOnIdle {
            sections.open(MainSection.MESSAGES)
            nav.navigate("conversation/3")
        }
        rule.runOnIdle { sections.open(MainSection.JOURNAL) }
        rule.runOnIdle { sections.openFromOutside(MainSection.MESSAGES) }
        rule.runOnIdle {
            assertEquals(MainSection.MESSAGES.route, route())
            backLeavesApp()
        }
    }

    @Test
    fun `тот же раздел - к его началу`() {
        rule.runOnIdle { nav.navigate("subscriber/x") }
        rule.runOnIdle { sections.open(MainSection.JOURNAL) }
        rule.runOnIdle {
            assertEquals(MainSection.JOURNAL.route, route())
            backLeavesApp()
        }
    }
}
