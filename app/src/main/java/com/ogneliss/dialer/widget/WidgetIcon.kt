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
 * • Размер — [VIEW_DP] на значок со счётчиком, сам значок — [ICON_DP]:
 *   как у значков на главном экране большинства телефонов (было 48 dp —
 *   заметно мельче соседей).
 * • Форма — маска системы (круг, «сквиркл»…), как у значков лаунчера по
 *   умолчанию. Исключение — MIUI/HyperOS, vivo и ColorOS (OPPO, realme,
 *   OnePlus): их лаунчеры рисуют значки скруглённым квадратом, а маска
 *   системы там нередко круг — виджет выходил кругом среди квадратов
 *   (снимок автора, realme). Там, если маска — круг, рисуем скруглённый
 *   квадрат ([ownRoundedSquare]).
 * • Счётчик — красный кружок в правом верхнем углу самого значка, как
 *   у лаунчеров (было: отдельная надпись над картинкой).
 * • Подписи нет: где лаунчер подписи прячет, она выделялась бы.
 */
internal object WidgetIcon {

    /** Сторона картинки со счётчиком. */
    private const val VIEW_DP = 64f

    /** Сторона самого значка внутри неё (остальное — место счётчику). */
    private const val ICON_DP = 58f

    /** Радиус скругления своего квадрата — доля стороны (как у MIUI/ColorOS). */
    private const val CORNER = 0.25f

    private const val BADGE_DP = 21f
    private const val BADGE_TEXT_SP = 11.5f
    private const val BADGE_RED = 0xFFD93025.toInt()

    fun draw(context: Context, iconRes: Int, unread: Int): Bitmap {
        val dm = context.resources.displayMetrics
        val view = px(context, VIEW_DP)
        val side = px(context, ICON_DP)
        val bitmap = Bitmap.createBitmap(dm, view, view, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Значок — по центру, как соседи; счётчик ляжет на его правый верхний угол
        val inset = (view - side) / 2
        val icon = Rect(inset, inset, inset + side, inset + side)
        val drawable = ContextCompat.getDrawable(context, iconRes)
        if (drawable is AdaptiveIconDrawable) {
            if (ownRoundedSquare(drawable)) drawRounded(canvas, drawable, icon)
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
    private fun drawRounded(canvas: Canvas, icon: AdaptiveIconDrawable, bounds: Rect) {
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
        val r = w * CORNER
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
