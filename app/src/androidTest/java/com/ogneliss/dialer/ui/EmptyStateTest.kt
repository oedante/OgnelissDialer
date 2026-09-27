package com.ogneliss.dialer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.EmptyReason
import com.ogneliss.dialer.ui.components.EmptyState
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * ТЕСТЫ ПУСТЫХ СОСТОЯНИЙ.
 *
 * Пустой экран пользователь трактует как поломку. Особенно при первом
 * запуске: он только что выдал доступ к журналу, видит пустоту — и первый
 * вывод «разрешения не сработали».
 *
 * Причин пустоты четыре, и подсказки к ним разные. Эти тесты сторожат
 * то, что разные причины не схлопнутся однажды в одну общую заглушку
 * «ничего нет», оставляющую человека в тупике.
 */
class EmptyStateTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun пустойЖурналОбъясняетПричину() {
        compose.setContent {
            OgnelissDialerTheme { EmptyState(reason = EmptyReason.NoCalls) }
        }

        compose.onNodeWithText(context.getString(R.string.empty_no_calls)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.empty_no_calls_hint))
            .assertIsDisplayed()
    }

    @Test
    fun отсутствиеРазрешенияОтличаетсяОтПустогоЖурнала() {
        // Разные причины — разные подсказки. Общая заглушка оставила бы
        // пользователя в тупике: он решил бы, что звонков действительно нет
        compose.setContent {
            OgnelissDialerTheme { EmptyState(reason = EmptyReason.NoCallLogPermission) }
        }

        compose.onNodeWithText(context.getString(R.string.empty_no_calllog_permission))
            .assertIsDisplayed()
    }

    @Test
    fun приОтсутствииРазрешенияЕстьКнопкаДействия() {
        // Кнопка нужна там, где пользователь реально может что-то
        // исправить. Без неё сообщение только констатирует проблему
        var actionCalled = false

        compose.setContent {
            OgnelissDialerTheme {
                EmptyState(
                    reason = EmptyReason.NoCallLogPermission,
                    onAction = { actionCalled = true }
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.onboarding_grant)).performClick()

        assertTrue(actionCalled)
    }

    @Test
    fun ненайденныйПоискПредлагаетСброс() {
        var cleared = false

        compose.setContent {
            OgnelissDialerTheme {
                EmptyState(
                    reason = EmptyReason.NoSearchResults("9999"),
                    onAction = { cleared = true }
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.empty_clear_search)).performClick()

        assertTrue(cleared)
    }

    @Test
    fun компактныйВариантПоказываетТолькоЗаголовок() {
        // В ландшафте и на узкой панели высоты под иллюстрацию
        // и подсказку нет
        compose.setContent {
            OgnelissDialerTheme {
                EmptyState(reason = EmptyReason.NoCalls, compact = true)
            }
        }

        compose.onNodeWithText(context.getString(R.string.empty_no_calls)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.empty_no_calls_hint))
            .assertDoesNotExist()
    }
}
