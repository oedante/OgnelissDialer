package com.ogneliss.dialer.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.ogneliss.dialer.settings.UiScale
import kotlin.math.floor

/**
 * МАСШТАБ ИНТЕРФЕЙСА И РАЗМЕР ШРИФТА — КАК В OGNELISS MAIL (2.2.1).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 11.11.2026: «масштаб давай тоже оттуда возьмём и изменение
 * масштаба. Хочется, чтобы эти два приложения были полностью похожи».
 * Токены размеров у приложений общие (docs/OGNELISS_DESIGN_SYSTEM.md),
 * а масштаб считался по-разному — и на одном телефоне Dialer был на 10 %
 * мельче почты. Теперь расчёт тот же, что в почте (`ui_zoom.dart`,
 * `main.dart`): одинаковые токены × одинаковый масштаб = одинаковый вид.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК
 * ══════════════════════════════════════════════════════════════════════════
 * Внутри темы подменяется плотность (LocalDensity): все размеры в dp
 * и текст умножаются на масштаб z — без правки сотен мест, где они заданы.
 *
 * • «Авто» (по умолчанию): вёрстка рассчитана на телефон шириной 412 dp
 *   при обычном шрифте; z = наименьшая сторона / (412 × t), где t —
 *   множитель шрифта (крупный шрифт учитывается наполовину: он выбран ради
 *   крупного текста, и уменьшать его целиком неправильно). z от 0,97 —
 *   без изменений (разница незаметна); не меньше 0,85; шаг 1 %.
 *   Планшеты и раскладушки (от 600 dp) не трогаются — у них свои раскладки.
 * • Вручную («Оформление → Масштаб интерфейса»): 85, 90, 95, 100, 110 %.
 * • Текст масштабируется вместе со всем (в почте — FittedBox всего экрана).
 *
 * ── Размер шрифта ──────────────────────────────────────────────────────────
 * «Оформление → Размер шрифта»: множитель 0,8…1,6 ПОВЕРХ размера шрифта
 * Android (не вместо: человек с крупным системным шрифтом не должен получить
 * мелкий текст). Итог (система × настройка) — не больше 1,6: выше вёрстка
 * разваливается; для слабого зрения есть экранная лупа Android.
 *
 * ── Было (1.5.6–2.2.0) ─────────────────────────────────────────────────────
 * Опорная ширина 460 dp, не меньше 0,88, по умолчанию 90 %, текст при
 * уменьшении — мягче (корень масштаба), «Авто» не уменьшало при увеличенном
 * «Размере экрана» Android. Размеры текста были на 1 sp крупнее почты
 * (Type.kt) — вместе с 90 % это давало тот же текст, но всё остальное мельче.
 *
 * ── Что не затрагивается ──────────────────────────────────────────────────
 * Уведомления, ярлыки, виджеты, плитка быстрых настроек и заставка — их
 * рисует система. Отступы системных панелей — в пикселях, пересчитываются.
 */
val LocalUiScaleFactor = staticCompositionLocalOf { 1f }

/*
 * МАСШТАБ — И В ОТДЕЛЬНЫХ ОКНАХ (2.4.0).
 *
 * Автор, 12.11.2026: «масштаб не всегда действует… в разных окнах, панелях,
 * выпадающих списках разные размеры шрифтов». Причина: шторка
 * (ModalBottomSheet), диалог (Dialog), выпадающее меню и Popup открываются
 * отдельным окном Android, и корень этого окна заново задаёт LocalDensity —
 * системную. Масштаб интерфейса и «Размер шрифта» темы туда не доходили:
 * на экране 85–95 %, в шторке рядом — 100 %, и текст «прыгал» между окнами.
 *
 * Тема кладёт свою плотность сюда, а содержимое каждого окна оборачивается
 * в [ScaledWindow] (или [ScaledSheetContent] для шторок и меню — там нужен
 * ColumnScope). Проверка: scripts/ui_roles_check.py ищет окна без обёртки.
 */
val LocalScaledDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Содержимое отдельного окна (Dialog, Popup) — в масштабе приложения.
 *
 * [fitWidth] — для окон постоянной ширины (календарь и часы Material —
 * 360 dp): при масштабе больше 100 % на узком экране они не поместились
 * бы и обрезались по краям. Тогда окно уменьшается целиком, ровно
 * до ширины экрана, — текст и размеры в той же пропорции.
 */
@Composable
fun ScaledWindow(fitWidth: Dp = Dp.Unspecified, content: @Composable () -> Unit) {
    val scaled = LocalScaledDensity.current
    if (scaled == null) {
        content()
        return
    }
    // Внутри окна LocalDensity — системная: от неё и считается доля экрана
    val base = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = remember(scaled, base, fitWidth, screenWidthDp) {
        UiScaleMath.fitted(scaled, base.density, fitWidth, screenWidthDp)
    }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

/** Содержимое шторки и выпадающего меню (ColumnScope) — в масштабе приложения. */
@Composable
fun ColumnScope.ScaledSheetContent(content: @Composable ColumnScope.() -> Unit) {
    val scope = this
    ScaledWindow { scope.content() }
}

object UiScaleMath {
    /** Ширина, под которую сверстан интерфейс (dp, обычный шрифт) — как `UiZoom.referenceWidth` почты. */
    const val REFERENCE_WIDTH_DP = 412f

    /** Нижняя граница «Авто»: меньше — текст становится слишком мелким. */
    const val AUTO_MIN = 0.85f

    /** От этого значения «Авто» ничего не меняет: разница незаметна. */
    const val AUTO_KEEP = 0.97f

    /** С этой ширины — раскладушки и планшеты: свои раскладки, масштаб не нужен. */
    const val WIDE_WIDTH_DP = 600

    /** Пределы итогового множителя шрифта (система × настройка) — как `TextScaleLimits.app` почты. */
    const val TEXT_MIN = 0.8f
    const val TEXT_MAX = 1.6f

    /** Итоговый множитель шрифта: системный × «Размер шрифта», в пределах. */
    fun textScale(systemFontScale: Float, setting: Float): Float =
        (systemFontScale * setting).coerceIn(TEXT_MIN, TEXT_MAX)

    /**
     * Масштаб «Авто» — по наименьшей стороне экрана (не меняется при повороте)
     * и итоговому множителю шрифта [textScale].
     */
    fun autoFactor(smallestWidthDp: Int, textScale: Float = 1f): Float {
        if (smallestWidthDp <= 0 || smallestWidthDp >= WIDE_WIDTH_DP) return 1f
        val t = if (textScale <= 1f) textScale else 1f + (textScale.coerceIn(1f, TEXT_MAX) - 1f) / 2f
        val z = smallestWidthDp / (REFERENCE_WIDTH_DP * t)
        if (z >= AUTO_KEEP) return 1f
        // Шаг 1 %: масштаб не «дрожит» от мелких изменений размера окна
        return floor(z.coerceIn(AUTO_MIN, 1f) * 100f) / 100f
    }

    /** Итоговый масштаб по настройке: «Авто» или выбранный вручную. */
    fun factor(scale: UiScale, smallestWidthDp: Int, textScale: Float = 1f): Float =
        scale.fixed ?: autoFactor(smallestWidthDp, textScale)

    /**
     * Плотность с масштабом [layout] и «Размером шрифта» [fontSetting];
     * оба по единице и системный шрифт в пределах — исходная, без обёртки.
     */
    /**
     * Плотность для окна шириной [fitWidth] (dp приложения) на экране
     * [screenWidthDp] (системные dp): поместилось в масштабе — [scaled],
     * нет — уменьшенная до ширины экрана. Меньше системной не бывает:
     * окно, которое не помещается и без масштаба, — забота самого окна.
     */
    fun fitted(scaled: Density, baseDensity: Float, fitWidth: Dp, screenWidthDp: Int): Density {
        if (fitWidth == Dp.Unspecified || screenWidthDp <= 0 || baseDensity <= 0f) return scaled
        val layout = scaled.density / baseDensity
        if (fitWidth.value * layout <= screenWidthDp) return scaled
        val shrink = (screenWidthDp / (fitWidth.value * layout)).coerceAtLeast(1f / layout)
        return if (shrink >= 1f) scaled else ShrunkDensity(scaled, shrink)
    }

    fun scaledDensity(base: Density, layout: Float, fontSetting: Float = 1f): Density {
        // Во сколько раз текст отличается от системного: настройка и предел 1,6
        val ratio = if (base.fontScale > 0f) textScale(base.fontScale, fontSetting) / base.fontScale else fontSetting
        return if (layout == 1f && ratio == 1f) base else ScaledDensity(base, layout, layout * ratio)
    }
}

/**
 * Плотность масштаба, уменьшенная в [shrink] раз целиком — и размеры,
 * и текст (окно постоянной ширины на узком экране, [ScaledWindow]).
 * Перевод sp ↔ dp — как у масштаба: тот же текст в dp, меньше пикселей.
 */
private class ShrunkDensity(private val scaled: Density, shrink: Float) : Density {
    override val density: Float = scaled.density * shrink
    override val fontScale: Float = scaled.fontScale

    override fun TextUnit.toDp(): Dp = with(scaled) { this@toDp.toDp() }

    override fun Dp.toSp(): TextUnit = with(scaled) { this@toSp.toSp() }
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
