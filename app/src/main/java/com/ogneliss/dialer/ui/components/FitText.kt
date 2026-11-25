package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
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
 *
 * ── Один размер в ряду (2.9.11, [peers]) ───────────────────────────────────
 * Было: каждая подпись ряда ужималась сама, и при крупном шрифте в одном ряду
 * выходили разные размеры — «Контакты» мельче «Журнала» и «SMS», «Поделиться»
 * мельче «Ещё». Стало: подпись знает подписи соседей по ряду и считает масштаб
 * по самой широкой из всех. Ячейки ряда одной ширины — каждая получает тот же
 * масштаб сама, без второго прохода и без перерисовки. Мерим полужирным: так
 * выбранная (полужирная) подпись не меняет размер ряда при смене раздела.
 *
 * ── Перенос по слогам (2.9.11, [hyphenate]) ────────────────────────────────
 * Колонка справа узкая: одно длинное слово («Напоминания», «Псевдозвонок»)
 * ужималось до 60 %. Теперь сначала пробуем две строки с переносом по слогам
 * (Hyphens.Auto, правила переноса Android) в полный размер. Если перенос не
 * вышел (у языка нет правил — система рвёт слово где придётся), — как раньше,
 * ужимаем: разрыв без дефиса хуже мелкого текста.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minScale: Float = 0.75f,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    /** Подписи соседей по ряду равной ширины (2.9.11): масштаб — общий на ряд. */
    peers: List<String> = emptyList(),
    /** Длинное слово — переносом по слогам, а не ужатием (2.9.11, при maxLines > 1). */
    hyphenate: Boolean = false
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
        val fitted = remember(text, merged, maxWidth, maxLines, minScale, peers, hyphenate) {
            if (maxWidth == Constraints.Infinity || !merged.fontSize.isSp) {
                merged
            } else {
                val all = if (peers.isEmpty()) listOf(text) else peers + text
                val parts = if (maxLines == 1) all else all.flatMap { it.split(' ', '\u00A0') }
                // С соседями — полужирным: одна мерка для всех ячеек ряда
                val measureStyle = if (peers.isEmpty()) merged else merged.copy(fontWeight = FontWeight.SemiBold)
                val widest = parts.maxOfOrNull { part ->
                    measurer.measure(part, measureStyle, softWrap = false, maxLines = 1).size.width
                } ?: 0
                // Запас 4%: округление и сглаживание при отрисовке
                // не должны решать, переносить ли последнюю букву
                val target = maxWidth * FIT_SAFETY
                // Чуть шире места — чуть ужать («Контакты»); намного — перенести по слогам
                val hyphenated = if (hyphenate && maxLines > 1 && widest > target / HYPHENATE_BELOW) {
                    merged.copy(hyphens = Hyphens.Auto).takeIf {
                        hyphenatesCleanly(measurer, text, it, maxWidth, maxLines)
                    }
                } else null
                if (widest <= target || widest == 0) {
                    merged
                } else if (hyphenated != null) {
                    hyphenated
                } else {
                    val scale = (target / widest).coerceAtLeast(minScale)
                    merged.copy(
                        fontSize = merged.fontSize * scale,
                        lineHeight = if (merged.lineHeight.isSp) merged.lineHeight * scale else merged.lineHeight,
                        // 2.7.0: разрядка ужимается вместе с шрифтом. Было: 0,5 sp
                        // оставалась как есть, ужатое слово выходило шире замера и
                        // всё равно рвалось («Напомнит / ь» при шрифте 160 %)
                        letterSpacing = if (merged.letterSpacing.isSp) merged.letterSpacing * scale else merged.letterSpacing
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

/**
 * Помещается ли [text] в [maxLines] строк шириной [width] с переносом по слогам,
 * и каждый разрыв внутри слова — с дефисом (2.9.11). Дефиса нет — система
 * разорвала слово как пришлось (у языка нет правил переноса): так нельзя.
 *
 * Дефис в текст не входит — Android дорисовывает его в конце строки, и строка
 * выходит шире своих букв; по этой разнице его и видно.
 */
internal fun hyphenatesCleanly(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    width: Int,
    maxLines: Int
): Boolean {
    val layout = measurer.measure(
        text, style,
        constraints = Constraints(maxWidth = width),
        maxLines = maxLines
    )
    if (layout.hasVisualOverflow) return false
    for (line in 0 until layout.lineCount - 1) {
        val end = layout.getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        if (!text[end - 1].isLetter() || !text[end].isLetter()) continue
        val start = layout.getLineStart(line)
        val letters = measurer.measure(text.substring(start, end), style, softWrap = false, maxLines = 1).size.width
        val drawn = layout.getLineRight(line) - layout.getLineLeft(line)
        if (drawn <= letters + HYPHEN_MIN_PX) return false
    }
    return true
}

/** Ужать меньше чем до этой доли не хватает — переносим по слогам (иначе ужимаем). */
private const val HYPHENATE_BELOW = 0.85f

/** Шире букв хотя бы на столько — значит, в конце строки дефис (он шире пикселя). */
private const val HYPHEN_MIN_PX = 1f
