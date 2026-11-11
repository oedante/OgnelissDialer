package com.ogneliss.dialer.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.ogneliss.dialer.settings.WidgetShape
import com.ogneliss.dialer.util.IncomingCallAccess
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * ЗНАЧОК ВИДЖЕТА РАЗДЕЛА — КАК ЗНАЧОК ЛАУНЧЕРА (2.1.0).
 *
 * Виджет рисует приложение, а не лаунчер: ни формы значков, ни их размера,
 * ни того, подписаны ли они, лаунчер не сообщает. Поэтому значок рисуется
 * картинкой сам, как можно ближе к соседям:
 *
 * • Размер — [VIEW_DP], во всю картинку (2.1.3; было 58 dp из 64 —
 *   поля под счётчик): лаунчер и так оставляет вокруг виджета свои поля,
 *   и значок выходил мельче соседей. Счётчик лежит на углу значка.
 * • Форма — маска системы (круг, «сквиркл»…), как у значков лаунчера по
 *   умолчанию. Исключение — MIUI/HyperOS, vivo и ColorOS (OPPO, realme,
 *   OnePlus): их лаунчеры рисуют значки скруглённым квадратом, а маска
 *   системы там нередко круг — виджет выходил кругом среди квадратов
 *   (снимок автора, realme). Там, если маска — круг, рисуем скруглённый
 *   квадрат ([ownRoundedSquare]).
 * • Угадать удаётся не всегда (свой лаунчер, набор значков) — форму можно
 *   выбрать в настройках (2.1.3, WidgetShape): круг, три скругления, квадрат.
 * • Счётчик — красный кружок в правом верхнем углу самого значка, как
 *   у лаунчеров (было: отдельная надпись над картинкой).
 * • Подписи нет: где лаунчер подписи прячет, она выделялась бы.
 */
internal object WidgetIcon {

    /** Сторона картинки: значок — во всю её ширину, счётчик — на его углу. */
    private const val VIEW_DP = 64f

    /** Скругление своего квадрата при «Авто» — доля стороны (как у MIUI/ColorOS). */
    private const val AUTO_CORNER = 0.25f

    private const val BADGE_DP = 21f
    private const val BADGE_TEXT_SP = 11.5f
    private const val BADGE_RED = 0xFFD93025.toInt()

    /**
     * @param shape форма из настроек (2.1.3); AUTO — как у системы,
     *        на оболочках со своими лаунчерами — скруглённый квадрат
     */
    fun draw(context: Context, iconRes: Int, unread: Int, shape: WidgetShape = WidgetShape.AUTO): Bitmap {
        val dm = context.resources.displayMetrics
        val view = px(context, VIEW_DP)
        val bitmap = Bitmap.createBitmap(dm, view, view, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val icon = Rect(0, 0, view, view)
        val drawable = ContextCompat.getDrawable(context, iconRes)
        if (drawable is AdaptiveIconDrawable) {
            val corner = shape.corner ?: AUTO_CORNER.takeIf { ownRoundedSquare(drawable) }
            if (corner != null) drawRounded(canvas, drawable, icon, corner)
            else {
                drawable.bounds = icon
                drawable.draw(canvas)
            }
        } else drawable?.apply { bounds = icon; draw(canvas) }
        if (unread > 0) drawBadge(context, canvas, if (unread > 99) "99+" else unread.toString(), view)
        return bitmap
    }

    /** Свой скруглённый квадрат: оболочки, где лаунчер рисует ими, а маска системы — круг. */
    private fun ownRoundedSquare(icon: AdaptiveIconDrawable): Boolean =
        IncomingCallAccess.restrictiveShell && systemMaskRound(icon)

    /**
     * Маска системы — круг? Угол квадрата 100 × 100 (точка 12, 12) кругом
     * не покрыт, а скруглённым квадратом и «сквирклом» — покрыт.
     */
    private fun systemMaskRound(icon: AdaptiveIconDrawable): Boolean = runCatching {
        val saved = Rect(icon.bounds)
        icon.setBounds(0, 0, 100, 100)
        val region = Region().apply { setPath(icon.iconMask, Region(0, 0, 100, 100)) }
        icon.bounds = saved
        !region.contains(12, 12)
    }.getOrDefault(false)

    /**
     * Слои адаптивного значка под своей маской. Слои на четверть стороны
     * шире значка с каждой стороны (108 dp против 72 видимых); край —
     * со сглаживанием: рисуем шейдером, а не обрезкой холста.
     */
    private fun drawRounded(canvas: Canvas, icon: AdaptiveIconDrawable, bounds: Rect, corner: Float) {
        val w = bounds.width()
        val layer = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        val ext = w / 4
        val layerBounds = Rect(-ext, -ext, w + ext, w + ext)
        icon.background?.apply { this.bounds = layerBounds; draw(lc) }
        icon.foreground?.apply { this.bounds = layerBounds; draw(lc) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(layer, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(bounds.left.toFloat(), bounds.top.toFloat()) })
            }
        }
        val r = w * corner
        canvas.drawRoundRect(RectF(bounds), r, r, paint)
    }

    /** Счётчик: красная «таблетка» с белой обводкой в правом верхнем углу картинки. */
    private fun drawBadge(context: Context, canvas: Canvas, text: String, view: Int) {
        val h = px(context, BADGE_DP).toFloat()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, BADGE_TEXT_SP, context.resources.displayMetrics
            )
        }
        val w = max(h, textPaint.measureText(text) + h * 0.55f)
        val stroke = h * 0.07f
        val rect = RectF(view - w + stroke / 2, stroke / 2, view - stroke / 2, h - stroke / 2)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BADGE_RED }
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = Color.WHITE
        }
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, ring)
        val fm = textPaint.fontMetrics
        canvas.drawText(text, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2, textPaint)
    }

    private fun px(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density).roundToInt()
}
