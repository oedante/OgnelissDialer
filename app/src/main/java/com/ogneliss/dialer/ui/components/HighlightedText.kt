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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.ogneliss.dialer.t9.T9Engine
import com.ogneliss.dialer.util.TextFold

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
    /**
     * Запрос: цифры — совпадение по T9, буквы — подстрокой (1.6.0, поиск
     * в панели и в контактах). Пусто — подсветки нет.
     */
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    /*
     * ИМЯ — В ОДНУ СТРОКУ (1.5.7).
     *
     * Ограничения не было, и длинное имя переносилось: «Служба /
     * спасения», «Константин Сергеевич / Иванов» — строки списка выходили
     * разной высоты; при крупном шрифте это видно на снимке автора.
     * В журнале комментарий так и обещал: «длинное имя обрежется, значок
     * останется виден», — а оно переносилось. Полное имя — в карточке.
     */
    maxLines: Int = 1
) {
    val text: AnnotatedString = remember(name, query) {
        val range = highlightRange(name, query) ?: return@remember AnnotatedString(name)
        buildAnnotatedString {
            append(name.substring(0, range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(name.substring(range.first, range.last + 1))
            }
            append(name.substring(range.last + 1))
        }
    }

    Text(
        text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Какую часть имени подсветить; null — ничего.
 *
 * Цифры — совпадение по T9: длина равна числу цифр (каждая цифра —
 * одна буква), не дальше конца имени — иначе выход за границы строки
 * при частичном совпадении. Буквы (1.6.0) — подстрока без учёта
 * регистра и с «ё» = «е», как ищет поиск (TextFold): подсвечено то же,
 * по чему нашлось.
 */
internal fun highlightRange(name: String, query: String): IntRange? {
    if (TextFold.isTextQuery(query)) {
        val text = query.trim()
        val start = TextFold.indexOf(name, text)
        if (start < 0) return null
        return start until (start + text.length).coerceAtMost(name.length)
    }
    val digits = query.filter { it.isDigit() }
    if (digits.isEmpty()) return null
    val start = T9Engine.matchIndex(digits, name)
    if (start < 0) return null
    val end = (start + digits.length).coerceAtMost(name.length)
    return if (end > start) start until end else null
}

/**
 * Подсветка совпадения в номере телефона.
 *
 * Отличается от имени: в номере ищется подстрока цифр, а не соответствие
 * буквам. Пользователь может искать по середине номера — «1122» найдёт
 * 8 (967) 111-22-33, и подсветить нужно именно эту часть.
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
