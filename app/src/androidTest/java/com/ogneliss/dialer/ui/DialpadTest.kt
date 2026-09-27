package com.ogneliss.dialer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ogneliss.dialer.ui.components.Dialpad
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * ТЕСТЫ КЛАВИАТУРЫ НАБОРА.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ЭТИ ТЕСТЫ ВАЖНЕЕ ПРОЧИХ
 * ══════════════════════════════════════════════════════════════════════════
 * В проекте 28 наборов тестов, и все они проверяют логику. При этом
 * ломался чаще всего именно интерфейс: свайп не срабатывал при одном
 * номере, пять настроек не применялись, содержимое уезжало под системные
 * панели, кнопка удаления обрабатывала нажатие дважды.
 *
 * Ни одна из этих ошибок не была бы поймана тестом логики: логика
 * работала верно, не работала её связь с экраном.
 *
 * ── Почему androidTest, а не обычные тесты ─────────────────────────────────
 * Compose можно тестировать и на JVM через Robolectric, но там своя
 * эмуляция отрисовки, и часть поведения — жесты, размеры, insets —
 * воспроизводится приблизительно. Для интерфейса, который ломался
 * именно в мелочах, приблизительно недостаточно.
 *
 * Запуск: ./gradlew connectedDebugAndroidTest
 * ══════════════════════════════════════════════════════════════════════════
 */
class DialpadTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun нажатиеЦифрыПередаётЕёОбработчику() {
        val pressed = mutableListOf<Char>()

        compose.setContent {
            OgnelissDialerTheme {
                Dialpad(onDigit = { pressed += it }, onLongDigit = {})
            }
        }

        // Ищем по описанию для TalkBack: оно же служит стабильным
        // идентификатором и не зависит от вёрстки
        compose.onNodeWithContentDescription("Клавиша 5").performClick()

        assertEquals(listOf('5'), pressed)
    }

    @Test
    fun всеДвенадцатьКлавишОтображаются() {
        compose.setContent {
            OgnelissDialerTheme {
                Dialpad(onDigit = {}, onLongDigit = {})
            }
        }

        // Раскладка фиксирована телефонным стандартом: пропажа клавиши
        // сломала бы мышечную память, которой пользуются десятилетиями
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#").forEach { key ->
            compose.onNodeWithContentDescription("Клавиша $key").assertIsDisplayed()
        }
    }

    @Test
    fun долгоеНажатиеПередаётсяОтдельнымОбработчиком() {
        // На долгом нажатии держатся сразу три функции: «+» на нуле,
        // пауза на звёздочке и быстрый набор на цифрах 2–9
        val longPressed = mutableListOf<Char>()

        compose.setContent {
            OgnelissDialerTheme {
                Dialpad(onDigit = {}, onLongDigit = { longPressed += it })
            }
        }

        compose.onNodeWithContentDescription("Клавиша 0")
            .performTouchInput { longClick() }

        assertEquals(listOf('0'), longPressed)
    }

    @Test
    fun короткоеНажатиеНеСчитаетсяДолгим() {
        // Кнопка удаления однажды обрабатывала нажатие дважды из-за
        // вложенных обработчиков. Здесь проверяется, что такого нет
        val short = mutableListOf<Char>()
        val long = mutableListOf<Char>()

        compose.setContent {
            OgnelissDialerTheme {
                Dialpad(onDigit = { short += it }, onLongDigit = { long += it })
            }
        }

        compose.onNodeWithContentDescription("Клавиша 7").performClick()

        assertEquals(listOf('7'), short)
        assertEquals("Долгое нажатие не должно срабатывать", emptyList<Char>(), long)
    }
}
