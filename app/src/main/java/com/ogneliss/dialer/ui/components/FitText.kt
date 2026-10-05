package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
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
    /*
     * Мерить тем же стилем, каким рисует Text (1.3.4, 04.10.2026).
     * Было: замер по style, а Text рисует LocalTextStyle.current.merge(style).
     * У «Поделиться» лёжа ширина была впритык: по замеру слово влезало,
     * масштаб не включался, а при отрисовке не хватало пары пикселей —
     * снова «Поделитьс / я» (скриншот автора, 1.3.3).
     */
    val merged = LocalTextStyle.current.merge(style)
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val fitted = remember(text, merged, maxWidth, maxLines, minScale) {
            if (maxWidth == Constraints.Infinity || !merged.fontSize.isSp) {
                merged
            } else {
                val parts = if (maxLines == 1) listOf(text) else text.split(' ', '\u00A0')
                val widest = parts.maxOfOrNull { part ->
                    measurer.measure(part, merged, softWrap = false, maxLines = 1).size.width
                } ?: 0
                // Запас 4%: округление и сглаживание при отрисовке
                // не должны решать, переносить ли последнюю букву
                val target = maxWidth * FIT_SAFETY
                if (widest <= target || widest == 0) {
                    merged
                } else {
                    val scale = (target / widest).coerceAtLeast(minScale)
                    merged.copy(
                        fontSize = merged.fontSize * scale,
                        lineHeight = if (merged.lineHeight.isSp) merged.lineHeight * scale else merged.lineHeight
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

/** Доля ширины, которую может занять самое широкое слово. */
private const val FIT_SAFETY = 0.96f
