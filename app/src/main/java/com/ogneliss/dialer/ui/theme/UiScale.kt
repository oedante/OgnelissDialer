package com.ogneliss.dialer.ui.theme

import android.util.DisplayMetrics
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.ogneliss.dialer.settings.UiScale
import kotlin.math.sqrt

/**
 * МАСШТАБ ИНТЕРФЕЙСА ПОД ЭКРАН (1.5.6, 18.10.2026).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор: «отлично смотрится при разрешении 2480 × 1116, но на меньших
 * разрешениях может восприниматься по-другому». Размеры в приложении —
 * в dp, а ширина экрана в dp у телефонов разная: у автора ≈ 515 dp
 * (по снимкам — плотность ≈ 2,17), у большинства телефонов 360–412 dp.
 * На узком экране те же кнопки, поля и портреты занимают на четверть-треть
 * большую долю ширины — интерфейс выглядит крупным и тесным.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК
 * ══════════════════════════════════════════════════════════════════════════
 * Внутри темы подменяется плотность (LocalDensity): все размеры в dp
 * умножаются на коэффициент k, — без правки сотен мест, где они заданы.
 *
 * • «Авто» (по умолчанию): k = наименьшая ширина экрана / 460 dp,
 *   но не меньше 0,88 и не больше 1. Экран от 460 dp (как у автора) —
 *   без изменений; обычный телефон 393–412 dp — 0,88–0,9. Раскладушки
 *   и планшеты (от 600 dp) не трогаются: у них свои раскладки.
 * • Если человек сам увеличил «Размер экрана» в настройках Android
 *   (плотность выше заводской), «Авто» ничего не уменьшает: это его
 *   осознанный выбор, и спорить с ним нельзя.
 * • Ручной выбор (Настройки → Оформление → «Масштаб интерфейса»):
 *   90, 100, 110, 125 %.
 *
 * ── Текст мягче, чем всё остальное ─────────────────────────────────────────
 * При уменьшении текст умножается на √k, а не на k: при k = 0,88 текст —
 * 0,94 (14 sp → 13,2), тогда как поля и кнопки — 0,88. Иначе на узком
 * экране текст стал бы мелким. При увеличении (110, 125 %) текст растёт
 * вместе со всем: увеличивают, чтобы лучше видеть. Настройка размера
 * шрифта Android действует как раньше — поверх.
 *
 * ── Что не затрагивается ──────────────────────────────────────────────────
 * Уведомления, ярлыки, плитка быстрых настроек и заставка — их рисует
 * система. Отступы системных панелей — в пикселях, пересчитываются верно.
 */
val LocalUiScaleFactor = staticCompositionLocalOf { 1f }

object UiScaleMath {
    /** Ширина, с которой «Авто» ничего не меняет: экран автора ≈ 515 dp, с запасом. */
    const val REFERENCE_WIDTH_DP = 460f

    /** Нижняя граница «Авто»: кнопки 48 dp → 42, текст 14 sp → 13,2. */
    const val AUTO_MIN = 0.88f

    /** С этой ширины — раскладушки и планшеты: свои раскладки, масштаб не нужен. */
    const val WIDE_WIDTH_DP = 600

    /**
     * Коэффициент «Авто».
     *
     * [densityDpi] — текущая плотность (с учётом «Размера экрана» Android),
     * [stableDensityDpi] — заводская. Текущая выше заводской — человек
     * увеличил интерфейс сам: не уменьшаем.
     */
    fun autoFactor(
        smallestWidthDp: Int,
        densityDpi: Int,
        stableDensityDpi: Int = DisplayMetrics.DENSITY_DEVICE_STABLE
    ): Float {
        if (smallestWidthDp <= 0 || smallestWidthDp >= WIDE_WIDTH_DP) return 1f
        if (stableDensityDpi > 0 && densityDpi > stableDensityDpi) return 1f
        return (smallestWidthDp / REFERENCE_WIDTH_DP).coerceIn(AUTO_MIN, 1f)
    }

    /**
     * Итоговый коэффициент по настройке.
     *
     * ══════════════════════════════════════════════════════════════════════
     * 90 % — НЕ ПОВЕРХ УВЕЛИЧЕННОГО И НЕ НА ПЛАНШЕТЕ (1.8.1)
     * ══════════════════════════════════════════════════════════════════════
     * С 1.8.0 по умолчанию 90 % вместо «Авто». Но «Авто» умело две вещи,
     * которых у постоянного множителя нет: не уменьшать, если человек сам
     * увеличил «Размер экрана» в Android (обычно — чтобы лучше видеть),
     * и не трогать раскладушки и планшеты — у них свои раскладки. 90 %
     * поверх увеличенного размера сводили настройку Android на нет.
     * Теперь 90 % в этих двух случаях — как 100 %. Остальные значения
     * действуют как выбраны.
     */
    fun factor(
        scale: UiScale,
        smallestWidthDp: Int,
        densityDpi: Int,
        stableDensityDpi: Int = DisplayMetrics.DENSITY_DEVICE_STABLE
    ): Float {
        val fixed = scale.fixed ?: return autoFactor(smallestWidthDp, densityDpi, stableDensityDpi)
        if (scale == UiScale.DEFAULT && fixed < 1f) {
            val enlarged = stableDensityDpi > 0 && densityDpi > stableDensityDpi
            if (enlarged || smallestWidthDp >= WIDE_WIDTH_DP) return 1f
        }
        return fixed
    }

    /** Множитель текста: при уменьшении мягче (√k), при увеличении — как всё. */
    fun textFactor(layout: Float): Float = if (layout < 1f) sqrt(layout) else layout

    /** Плотность с масштабом; при k = 1 — исходная, без обёртки. */
    fun scaledDensity(base: Density, layout: Float): Density =
        if (layout == 1f) base else ScaledDensity(base, layout, textFactor(layout))
}

/**
 * Плотность с отдельными множителями для размеров ([layout]) и текста ([text]).
 *
 * Перевод sp ↔ dp делегируется исходной плотности: на Android 14+ крупный
 * системный шрифт растёт нелинейно (FontScaleConverter), и простая
 * Density(density, fontScale) эту нелинейность потеряла бы.
 *
 * Проверка: текст в пикселях = исходные пиксели × text —
 * toDp(sp) × text / layout × (density × layout).
 */
private class ScaledDensity(
    private val base: Density,
    private val layout: Float,
    private val text: Float
) : Density {
    override val density: Float = base.density * layout
    override val fontScale: Float = base.fontScale * text / layout

    override fun TextUnit.toDp(): Dp {
        val unscaled = with(base) { this@toDp.toDp() }
        return unscaled * (text / layout)
    }

    override fun Dp.toSp(): TextUnit = with(base) { (this@toSp * (layout / text)).toSp() }
}
