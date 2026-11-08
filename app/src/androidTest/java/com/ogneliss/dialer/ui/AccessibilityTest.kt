package com.ogneliss.dialer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.ui.components.CallButtons
import com.ogneliss.dialer.ui.components.Dialpad
import com.ogneliss.dialer.ui.components.SelectionTopBar
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * ТЕСТЫ ДОСТУПНОСТИ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ЭТО ПРОВЕРЯЕТСЯ АВТОМАТИКОЙ, А НЕ ЧЕК-ЛИСТОМ
 * ══════════════════════════════════════════════════════════════════════════
 * Доступность легко сделать один раз и незаметно потерять. Добавили
 * иконку без описания, ужали кнопку ради вёрстки — и незрячий пользователь
 * слышит «кнопка», не понимая какая, а человек с тремором промахивается.
 *
 * Ручной чек-лист от этого не спасает: его проходят перед релизом,
 * а ломается всё в промежутке. Тест ломает сборку сразу.
 *
 * Для диалера это важнее, чем для среднего приложения: им пользуются
 * пожилые и люди со слабым зрением, и звонок — не то действие,
 * которое можно отложить до удобного момента.
 * ══════════════════════════════════════════════════════════════════════════
 */
class AccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    /** Требование Material Design и рекомендаций по доступности. */
    private val minTouchTarget = Dimens.MIN_TOUCH_TARGET

    @Test
    fun каждаяКлавишаНаборуДостаточноКрупная() {
        // Цель меньше 48 dp промахивается при тряске, в перчатках
        // и при треморе
        compose.setContent {
            OgnelissDialerTheme {
                Dialpad(onDigit = {}, onLongDigit = {})
            }
        }

        listOf("1", "5", "9", "*", "0", "#").forEach { key ->
            compose.onNodeWithContentDescription("Клавиша $key")
                .assertHeightIsAtLeast(minTouchTarget)
                .assertWidthIsAtLeast(minTouchTarget)
        }
    }

    @Test
    fun клавишиОзвучиваютсяОсмысленно() {
        // TalkBack должен читать «Клавиша 5», а не «5 JKL МНОП»:
        // перечисление букв на слух бесполезно и мешает
        compose.setContent {
            OgnelissDialerTheme {
                Dialpad(onDigit = {}, onLongDigit = {})
            }
        }

        val tree = compose.onRoot().printToString(maxDepth = 20)

        assertTrue("Описание клавиши должно быть коротким", tree.contains("Клавиша 5"))
        assertTrue("Буквы не должны попадать в описание", !tree.contains("Клавиша 5 JKL"))
    }

    @Test
    fun кнопкаВызоваИмеетОписаниеСНомеромКарты() {
        // Незрячий пользователь должен понимать, какую SIM выбирает,
        // а не слышать две одинаковые «кнопка вызова»
        compose.setContent {
            OgnelissDialerTheme {
                CallButtons(
                    sims = listOf(
                        SimSlot(1, 0, "Рабочая", null),
                        SimSlot(2, 1, "Личная", null)
                    ),
                    enabled = true,
                    onCall = {}
                )
            }
        }

        compose.onNodeWithContentDescription("Позвонить с Рабочая").assertExists()
        compose.onNodeWithContentDescription("Позвонить с Личная").assertExists()
    }

    @Test
    fun всеИконочныеКнопкиИмеютОписание() {
        // Кнопка без описания озвучивается как «кнопка» — пользователь
        // не узнает, что она делает, пока не нажмёт
        compose.setContent {
            OgnelissDialerTheme {
                Column {
                    SelectionTopBar(
                        count = 3,
                        onClose = {}, onSelectAll = {}, onBlock = {}, onDelete = {}
                    )
                }
            }
        }

        val clickableWithoutDescription = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Role) and
                SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription) and
                SemanticsMatcher.keyNotDefined(SemanticsProperties.Text)
        ).fetchSemanticsNodes()

        assertTrue(
            "Найдены кнопки без описания: ${clickableWithoutDescription.size}",
            clickableWithoutDescription.isEmpty()
        )
    }

    @Test
    fun кнопкиВРежимеВыбораДостаточноКрупные() {
        compose.setContent {
            OgnelissDialerTheme {
                SelectionTopBar(
                    count = 1,
                    onClose = {}, onSelectAll = {}, onBlock = {}, onDelete = {}
                )
            }
        }

        // Удаление стоит рядом с закрытием: промах здесь стирает записи
        compose.onNodeWithContentDescription("Удалить")
            .assertHeightIsAtLeast(minTouchTarget)
            .assertWidthIsAtLeast(minTouchTarget)
    }
}
