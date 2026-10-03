package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSp

/**
 * Текст, который уменьшается, чтобы не рваться посреди слова
 * (1.3.2, 01.10.2026).
 *
 * Было: в карточке лёжа подпись «Поделиться» переносилась как
 * «Поделитьс / я» — слово длиннее кнопки, а maxLines = 2 разрешал
 * перенос внутри слова. SIM-кнопки лёжа обрезали имя карты
 * до «Beeline2» и «Beeline0» — их легко перепутать.
 *
 * Стало: перед показом меряем самое широкое слово (при maxLines > 1)
 * или весь текст (при maxLines = 1). Не помещается — уменьшаем шрифт,
 * но не меньше minScale от исходного: дальше текст становится
 * нечитаемым, и тогда — многоточие.
 *
 * Мерить — дёшево: результат запоминается по тексту, стилю и ширине.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minScale: Float = 0.75f,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val fitted = remember(text, style, maxWidth, maxLines, minScale) {
            if (maxWidth == Constraints.Infinity || !style.fontSize.isSp) {
                style
            } else {
                val parts = if (maxLines == 1) listOf(text) else text.split(' ', '\u00A0')
                val widest = parts.maxOfOrNull { part ->
                    measurer.measure(part, style, softWrap = false, maxLines = 1).size.width
                } ?: 0
                if (widest <= maxWidth || widest == 0) {
                    style
                } else {
                    val scale = (maxWidth.toFloat() / widest).coerceAtLeast(minScale)
                    style.copy(
                        fontSize = style.fontSize * scale,
                        lineHeight = if (style.lineHeight.isSp) style.lineHeight * scale else style.lineHeight
                    )
                }
            }
        }
        Text(
            text,
            style = fitted,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            color = color,
            textAlign = textAlign
        )
    }
}
