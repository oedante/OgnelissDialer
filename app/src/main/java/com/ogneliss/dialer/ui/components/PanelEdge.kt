package com.ogneliss.dialer.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ВЕРХНИЙ КРАЙ НИЖНЕЙ ПАНЕЛИ — ТОНКАЯ ЛИНИЯ ВМЕСТО ТЕНИ (1.7.0).
 *
 * «Воздушный» вид (автор: «сейчас он тяжёлый и монументальный»): панели
 * журнала, контактов, поиска и карточки — плоские, без тени и скруглений,
 * светлее (PanelColors.panel). Тень в 6 dp над каждой панелью была самой
 * тяжёлой границей экрана; линию в 1 dp глаз читает так же — «список
 * уходит под панель», — но не замечает её отдельно.
 *
 * Рисуется поверх содержимого: подложка панели и её строки линию не
 * закрывают. Цвет — OgnelissSurfaces.divider, тот же, что у линий
 * между частями экрана.
 */
fun Modifier.panelTopLine(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = PANEL_LINE.toPx()
    drawLine(color, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), stroke)
}

/** Толщина линии — как у OgnelissDivider. */
private val PANEL_LINE = 1.dp
