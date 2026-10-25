package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.LocalPageTone
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.PageTone

/**
 * СТРОКА ГРУППЫ (быстрый набор, разрешения, закреплённые, статистика,
 * списки блокировки, поиск по настройкам, внешние определители, заметки,
 * напоминания) — строки собраны из отдельных элементов LazyColumn,
 * а выглядеть должны одной группой.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * 1.5.6 — ПО ВИДУ СТРАНИЦЫ (как в Ogneliss Mail)
 * ══════════════════════════════════════════════════════════════════════════
 * • TINTED (настройки и похожие экраны): строка — часть белой карточки
 *   с рамкой. Первая строка скругляет верх карточки, последняя — низ,
 *   одиночная — всё; рамка рисуется по частям: у первой — верх и бока,
 *   у средних — бока, у последней — бока и низ. Вместе получается одна
 *   карточка, хотя строки — отдельные элементы списка (иначе пришлось
 *   бы собирать все строки в один элемент и терять переиспользование).
 * • LIST (заметки, напоминания): строка во всю ширину на белом, как в
 *   списке писем Mail.
 *
 * Между строками группы — линия от начала текста ([dividerStart]),
 * над первой — нет (там полоса раздела или край карточки).
 *
 * ── Почему подменяется surface ─────────────────────────────────────────────
 * ListItem красится в surface. Внутри строки surface = цвет строки,
 * и любой ListItem в ней одного цвета с подложкой без правки каждого
 * вызова — в тёмной теме это важно.
 */
@Composable
fun GroupedRow(
    position: GroupPosition,
    dividerStart: Dp = 56.dp,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tinted = LocalPageTone.current == PageTone.TINTED
    val surfaces = OgnelissTheme.surfaces
    val rowColor = if (tinted) surfaces.card else scheme.surfaceContainerLow

    val outer = if (tinted) {
        val shape = segmentShape(position)
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.groupInset)
            .clip(shape)
            .background(rowColor)
            .segmentBorder(position, surfaces.cardBorder)
    } else {
        Modifier
            .fillMaxWidth()
            .background(rowColor)
    }

    Column(outer) {
        if (position == GroupPosition.MIDDLE || position == GroupPosition.LAST) {
            RowDivider(Modifier.padding(start = dividerStart))
        }
        MaterialTheme(
            colorScheme = scheme.copy(surface = rowColor, surfaceContainerLow = rowColor),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}

/** Форма части карточки по месту строки в группе. */
private fun segmentShape(position: GroupPosition) = when (position) {
    GroupPosition.SINGLE -> OgnelissShapeTokens.card
    GroupPosition.FIRST -> RoundedCornerShape(
        topStart = OgnelissShapeTokens.cardRadius,
        topEnd = OgnelissShapeTokens.cardRadius
    )
    GroupPosition.LAST -> RoundedCornerShape(
        bottomStart = OgnelissShapeTokens.cardRadius,
        bottomEnd = OgnelissShapeTokens.cardRadius
    )
    GroupPosition.MIDDLE -> RectangleShape
}

/**
 * Рамка части карточки: только те стороны, что у неё снаружи.
 *
 * Линия рисуется поверх содержимого и на полтолщины внутрь края, чтобы
 * не обрезаться клипом; у соседних строк бока продолжают друг друга.
 */
private fun Modifier.segmentBorder(position: GroupPosition, color: Color): Modifier =
    drawWithContent {
        drawContent()
        val stroke = CARD_BORDER.toPx()
        val half = stroke / 2f
        val r = OgnelissShapeTokens.cardRadius.toPx()
        val w = size.width
        val h = size.height
        when (position) {
            GroupPosition.SINGLE -> drawRoundRect(
                color = color,
                topLeft = Offset(half, half),
                size = Size(w - stroke, h - stroke),
                cornerRadius = CornerRadius(r - half),
                style = Stroke(stroke)
            )
            GroupPosition.MIDDLE -> {
                drawLine(color, Offset(half, 0f), Offset(half, h), stroke)
                drawLine(color, Offset(w - half, 0f), Offset(w - half, h), stroke)
            }
            GroupPosition.FIRST -> {
                val path = Path().apply {
                    moveTo(half, h)
                    lineTo(half, r)
                    arcTo(Rect(half, half, half + 2 * (r - half), half + 2 * (r - half)), 180f, 90f, false)
                    lineTo(w - r, half)
                    arcTo(Rect(w - half - 2 * (r - half), half, w - half, half + 2 * (r - half)), 270f, 90f, false)
                    lineTo(w - half, h)
                }
                drawPath(path, color, style = Stroke(stroke))
            }
            GroupPosition.LAST -> {
                val path = Path().apply {
                    moveTo(half, 0f)
                    lineTo(half, h - r)
                    arcTo(Rect(half, h - half - 2 * (r - half), half + 2 * (r - half), h - half), 180f, -90f, false)
                    lineTo(w - r, h - half)
                    arcTo(Rect(w - half - 2 * (r - half), h - half - 2 * (r - half), w - half, h - half), 90f, -90f, false)
                    lineTo(w - half, 0f)
                }
                drawPath(path, color, style = Stroke(stroke))
            }
        }
    }
