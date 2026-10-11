package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import com.ogneliss.dialer.ui.theme.FlowUi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens

/**
 * РАМКА ГРУППЫ ИЗ ОТДЕЛЬНЫХ СТРОК (1.5.1, строгий стиль).
 *
 * Группа в журнале и контактах — не один блок, а строки списка, лежащие
 * вплотную (GroupPosition). Рамку целиком на каждую строку дать нельзя:
 * между строками вышли бы двойные линии поверх разделителя.
 *
 * Поэтому каждая строка рисует свою часть общей рамки: средняя — только
 * боковые стороны, первая — ещё верх со скруглёнными углами, последняя —
 * низ, одиночная — всё. Приём: рисуется скруглённый прямоугольник,
 * продолженный за край строки там, где группа идёт дальше, и обрезается
 * по строке — остаются ровно нужные стороны и углы. Стыки строк сходятся
 * без зазоров и без двойных линий.
 *
 * Рисуется поверх содержимого: фон строки (цвет выбора, подсветка
 * нажатия) рамку не перекрывает.
 */
@Composable
fun Modifier.groupOutline(position: GroupPosition): Modifier {
    val color = MaterialTheme.colorScheme.outlineVariant
    return drawWithContent {
        drawContent()
        val w = OgnelissShapeTokens.groupBorderWidth.toPx()
        val r = GROUP_RADIUS.toPx()
        val above = if (position == GroupPosition.MIDDLE || position == GroupPosition.LAST) r + w else 0f
        val below = if (position == GroupPosition.MIDDLE || position == GroupPosition.FIRST) r + w else 0f
        clipRect {
            drawRoundRect(
                color = color,
                topLeft = Offset(w / 2f, w / 2f - above),
                size = Size(size.width - w, size.height - w + above + below),
                cornerRadius = CornerRadius(r - w / 2f),
                style = Stroke(width = w)
            )
        }
    }
}

/** Радиус группы — как shapes.medium у строк (8 dp). */
private val GROUP_RADIUS = 8.dp

/**
 * СТРОКА В ГРУППЕ (1.5.1, строгий стиль).
 *
 * Для экранов, где строки списка лежали прямо на странице с разделителями
 * во всю ширину (быстрый набор, разрешения, закреплённые, статистика,
 * списки блокировки, поиск по настройкам): та же белая группа с рамкой,
 * что в журнале и настройках, но собранная из отдельных элементов
 * LazyColumn — по месту строки в группе (GroupPosition).
 *
 * Разделитель между строками — внутри группы, от начала текста
 * ([dividerStart]); под первой строкой его нет.
 *
 * ── Почему подменяется surface ─────────────────────────────────────────────
 * ListItem по умолчанию красится в surface — а это теперь цвет страницы
 * (серый): строка вышла бы серой полосой внутри белой группы. Внутри
 * группы surface = цвет группы, и любые ListItem в ней — белые без
 * правки каждого вызова.
 */
@Composable
fun GroupedRow(
    position: GroupPosition,
    dividerStart: androidx.compose.ui.unit.Dp = 56.dp,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val corner = MaterialTheme.shapes.medium
    val zero = androidx.compose.foundation.shape.CornerSize(0.dp)
    val shape = when (position) {
        GroupPosition.SINGLE -> corner
        GroupPosition.FIRST -> corner.copy(bottomStart = zero, bottomEnd = zero)
        GroupPosition.MIDDLE -> androidx.compose.ui.graphics.RectangleShape
        GroupPosition.LAST -> corner.copy(topStart = zero, topEnd = zero)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.screenPadding)
            .clip(shape)
            .background(scheme.surfaceContainerLow)
            .groupOutline(position)
    ) {
        if (position == GroupPosition.MIDDLE || position == GroupPosition.LAST) {
            OgnelissDivider(Modifier.padding(start = dividerStart))
        }
        MaterialTheme(
            colorScheme = scheme.copy(surface = scheme.surfaceContainerLow),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
