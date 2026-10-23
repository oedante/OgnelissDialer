package com.ogneliss.dialer.call

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * МЯГКИЙ КРАЙ ПОРТРЕТА (1.5.7).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 22.10: «границы портретов на экране звонка и в карточке слишком
 * резкие и выбиваются из общего оформления». Портрет был обрезан по кругу
 * с тенью 4 dp: чёткий тёмный край посреди светлых волн — единственная
 * жёсткая линия на экране.
 *
 * Выбран набросок F «растворение 4 % + ободок»: край фото растворяется
 * на 4 % поперечника, а под ним — светлый ореол цвета страницы. Фото
 * переходит в ореол, ореол — в волны; тени нет.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК РИСУЕТСЯ
 * ══════════════════════════════════════════════════════════════════════════
 * В отдельном слое сначала рисуется маска — та же форма (круг или
 * скруглённый квадрат), непрозрачная внутри и тающая к краю, — а поверх
 * неё фото с режимом SrcIn: фото остаётся там, где есть маска, и ровно
 * настолько, насколько она плотна.
 *
 * Мягкая форма собрана из девяти частей, как растягиваемая картинка:
 * четыре угла — радиальные градиенты от центров скруглений, четыре
 * стороны — линейные, середина — заливка. У круга сторон и середины нет:
 * четыре четверти. Так край одинаково мягкий по всей длине и на любой
 * версии Android — размытие маски (BlurMaskFilter) аппаратно рисуется
 * только с Android 9, а приложение работает с 8.
 */
internal fun Modifier.softPortrait(
    shape: Shape,
    /** Ширина растворения — доля меньшей стороны. */
    featherFraction: Float,
    /** Цвет под фото, пока оно грузится. */
    placeholder: Color
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawSoftShape(shape, Color.Black, feather = size.minDimension * featherFraction, grow = 0f)
        val paint = Paint().apply { blendMode = BlendMode.SrcIn }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(size.toRect(), paint)
            drawRect(placeholder)
            this@drawWithContent.drawContent()
            canvas.restore()
        }
    }

/**
 * Светлый ореол вокруг портрета: плотный у края фото, тает наружу
 * на [widthFraction] меньшей стороны. Рисуется за пределами размера —
 * место под него не нужно.
 */
internal fun Modifier.softHalo(
    shape: Shape,
    color: Color,
    widthFraction: Float
): Modifier = this.drawBehind {
    val width = size.minDimension * widthFraction
    drawSoftShape(shape, color, feather = width, grow = width)
}

/**
 * Форма [shape], растянутая на [grow] во все стороны, плотная внутри
 * и тающая к краю на ширине [feather].
 */
private fun DrawScope.drawSoftShape(shape: Shape, color: Color, feather: Float, grow: Float) {
    // Целые пиксели: на дробных границах части накладывались бы
    // сглаживанием, и на стыках проступали бы тонкие линии
    val g = grow.roundToInt().toFloat()
    val w = (size.width + 2 * g).roundToInt().toFloat()
    val h = (size.height + 2 * g).roundToInt().toFloat()
    if (w <= 0f || h <= 0f) return
    val left = -g
    val top = -g

    // Скругление берётся у исходного размера и растёт вместе с формой:
    // ореол вокруг скруглённого квадрата повторяет его углы, а не
    // скругляется тем же радиусом на большем квадрате
    val outline = shape.createOutline(size, layoutDirection, this)
    val corner = ((outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f) + g
    val half = minOf(w, h) / 2f
    val featherPx = feather.coerceIn(1f, half)
    // Вниз до целого: два радиуса не должны быть больше стороны — иначе
    // углы наложатся, и на стыке выйдет полоса плотнее
    val r = floor(corner.coerceIn(featherPx, half)).coerceAtLeast(1f)
    val f = minOf(featherPx, r)
    val clear = color.copy(alpha = 0f)
    // Доля радиуса, до которой угол ещё плотный
    val solid = ((r - f) / r).coerceIn(0f, 1f)

    fun quarter(cx: Float, cy: Float, x: Float, y: Float) = drawRect(
        Brush.radialGradient(
            0f to color, solid to color, 1f to clear,
            center = Offset(cx, cy), radius = r
        ),
        topLeft = Offset(x, y),
        size = Size(r, r)
    )
    quarter(left + r, top + r, left, top)
    quarter(left + w - r, top + r, left + w - r, top)
    quarter(left + r, top + h - r, left, top + h - r)
    quarter(left + w - r, top + h - r, left + w - r, top + h - r)

    val midW = w - 2 * r
    val midH = h - 2 * r
    if (midW > 0f) {
        drawRect(
            Brush.verticalGradient(0f to clear, 1f to color, startY = top, endY = top + f),
            topLeft = Offset(left + r, top), size = Size(midW, r)
        )
        drawRect(
            Brush.verticalGradient(0f to color, 1f to clear, startY = top + h - f, endY = top + h),
            topLeft = Offset(left + r, top + h - r), size = Size(midW, r)
        )
    }
    if (midH > 0f) {
        drawRect(
            Brush.horizontalGradient(0f to clear, 1f to color, startX = left, endX = left + f),
            topLeft = Offset(left, top + r), size = Size(r, midH)
        )
        drawRect(
            Brush.horizontalGradient(0f to color, 1f to clear, startX = left + w - f, endX = left + w),
            topLeft = Offset(left + w - r, top + r), size = Size(r, midH)
        )
    }
    if (midW > 0f && midH > 0f) {
        drawRect(color, topLeft = Offset(left + r, top + r), size = Size(midW, midH))
    }
}
