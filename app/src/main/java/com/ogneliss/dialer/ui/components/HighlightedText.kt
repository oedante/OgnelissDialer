package com.ogneliss.dialer.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ogneliss.dialer.t9.T9Engine

/**
 * ПОДСВЕТКА СОВПАДЕНИЯ В РЕЗУЛЬТАТАХ ПОИСКА.
 *
 * ── Зачем ──────────────────────────────────────────────────────────────────
 * При T9-поиске связь между введёнными цифрами и найденным именем
 * неочевидна: пользователь набрал «5626», а видит «Клинов Андрей».
 * Почему нашлось именно это — непонятно, и результат выглядит случайным.
 *
 * Подсветка отвечает на вопрос без слов: видно, что «Клин» соответствует
 * набранным цифрам. Это же помогает понять, почему НЕ нашлось нужное:
 * подсвечено не то место — значит, ошибся в цифре.
 *
 * ── Почему подсветка жирным, а не цветом ───────────────────────────────────
 * Цветом уже закодирован смысл: оранжевый — фирменный акцент, красный —
 * пропущенные и опасные действия. Добавлять третье значение цвета
 * означало бы размыть систему.
 *
 * Жирное начертание работает и при цветовой слепоте, и в монохромном
 * режиме доступности.
 */
@Composable
fun HighlightedName(
    name: String,
    /** Введённые цифры. Пусто — подсветки нет. */
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val digits = remember(query) { query.filter { it.isDigit() } }

    val text: AnnotatedString = remember(name, digits) {
        if (digits.isEmpty()) return@remember AnnotatedString(name)

        val start = T9Engine.matchIndex(digits, name)
        if (start < 0) return@remember AnnotatedString(name)

        // Длина совпадения равна числу введённых цифр: каждая цифра
        // соответствует одной букве. Ограничиваем длиной имени,
        // чтобы не выйти за границы при частичном совпадении
        val end = (start + digits.length).coerceAtMost(name.length)

        buildAnnotatedString {
            append(name.substring(0, start))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(name.substring(start, end))
            }
            append(name.substring(end))
        }
    }

    Text(text, modifier = modifier, style = style)
}

/**
 * Подсветка совпадения в номере телефона.
 *
 * Отличается от имени: в номере ищется подстрока цифр, а не соответствие
 * буквам. Пользователь может искать по середине номера — «6605» найдёт
 * 8 (967) 666-05-12, и подсветить нужно именно эту часть.
 */
@Composable
fun HighlightedNumber(
    formatted: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val digits = remember(query) { query.filter { it.isDigit() } }

    val text: AnnotatedString = remember(formatted, digits) {
        if (digits.isEmpty()) return@remember AnnotatedString(formatted)

        // Ищем в «голых» цифрах, а подсвечиваем в отформатированной строке.
        // Поэтому нужно сопоставить позиции: разделители сдвигают индексы
        val bare = StringBuilder()
        val positionMap = ArrayList<Int>(formatted.length)
        formatted.forEachIndexed { index, ch ->
            if (ch.isDigit()) {
                bare.append(ch)
                positionMap.add(index)
            }
        }

        val bareStart = bare.indexOf(digits)
        if (bareStart < 0) return@remember AnnotatedString(formatted)

        val startInFormatted = positionMap[bareStart]
        val endInFormatted = positionMap
            .getOrElse(bareStart + digits.length - 1) { positionMap.last() } + 1

        buildAnnotatedString {
            append(formatted.substring(0, startInFormatted))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(formatted.substring(startInFormatted, endInFormatted))
            }
            append(formatted.substring(endInFormatted))
        }
    }

    Text(text, modifier = modifier, style = style)
}
