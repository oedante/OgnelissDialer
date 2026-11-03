package com.ogneliss.dialer.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ЦВЕТОВЫЕ ТЕМЫ КАК В OGNELISS MAIL — РАСЧЁТ (1.8.4).
 *
 * Автор, 03.11.2026: «Сравни оформление экранов. Они отличаются от Ogneliss
 * Mail. Давай попробуем подогнать». Разница оказалась в теме: почта стояла
 * на «Графите» (Оформление → Цветовая тема), а в «Классике» она синяя, как
 * диалер. Поэтому диалер получает те же темы и тот же расчёт, что почта
 * (lib/core/theme/theme_palette.dart, appearance.dart — PresetSpec,
 * AccentFit): выбрав одну тему в обоих приложениях, получаешь одни цвета.
 *
 * ── Как строится тема ──────────────────────────────────────────────────────
 * • Серые поверхности и текст — те же ступени яркости, что у «Классики»,
 *   повёрнутые к тону темы: тон [Spec.neutralHue], насыщенность × [Spec.neutralSat]
 *   («Графит» — 0, чистый серый).
 * • Акцент — тон и насыщенность цвета темы, яркость подобрана так, чтобы
 *   и белый текст на акценте, и акцент как текст на самой тёмной (в тёмной
 *   теме — самой светлой) поверхности проходили AA с запасом ([TARGET]).
 * • Текст темнеет (в тёмной теме — светлеет), пока не пройдёт AA на всех
 *   фонах: у зелёного и бирюзового тона та же «светлота» HSL ярче.
 * • Зелёный звонка, красный ошибок — свои роли, темы их не трогают.
 *
 * Чистая математика на ARGB-целых, без Android и Compose: проверяет
 * ThemePresetMathTest. HSL и яркость — как во Flutter (HSLColor,
 * Color.computeLuminance), чтобы цвета совпадали с почтой цвет в цвет.
 */
internal object ThemePresetMath {

    /** Тон серых, множитель их насыщенности, акцент темы; тёмная — чёрная (AMOLED). */
    data class Spec(val neutralHue: Double, val neutralSat: Double, val accent: Int, val blackDark: Boolean = false)

    /** Темы — как PresetSpec почты, в том же порядке. */
    val SPECS: Map<String, Spec> = linkedMapOf(
        "CLASSIC" to Spec(214.0, 1.0, 0xFF1D4F91.toInt()),
        // «Небо» — светлее и голубее
        "SKY" to Spec(206.0, 1.35, 0xFF1C6FC0.toInt()),
        "OCEAN" to Spec(188.0, 1.05, 0xFF00737E.toInt()),
        "FOREST" to Spec(140.0, 0.65, 0xFF2B7A4B.toInt()),
        "SUNSET" to Spec(28.0, 0.85, 0xFFB4501E.toInt()),
        // «Графит» — чистый серый без синевы и графитовый акцент
        "GRAPHITE" to Spec(0.0, 0.0, 0xFF4F5866.toInt()),
        // «Полночь» — индиго, тёмный вариант чисто чёрный
        "MIDNIGHT" to Spec(238.0, 0.9, 0xFF5155C9.toInt(), blackDark = true)
    )

    /** Запас над нормой AA (4,5 : 1): расчёт яркости округляет. */
    const val TARGET = 4.6

    data class Hsl(val h: Double, val s: Double, val l: Double)

    fun toHsl(argb: Int): Hsl {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val delta = mx - mn
        var hue = when {
            mx == 0.0 || delta == 0.0 -> 0.0
            mx == r -> 60.0 * (((g - b) / delta).mod(6.0))
            mx == g -> 60.0 * ((b - r) / delta + 2)
            else -> 60.0 * ((r - g) / delta + 4)
        }
        if (hue.isNaN()) hue = 0.0
        val l = (mx + mn) / 2
        val s = if (l == 1.0) 0.0 else (delta / (1.0 - abs(2.0 * l - 1.0))).let { if (it.isNaN()) 0.0 else it.coerceIn(0.0, 1.0) }
        return Hsl(hue, s, l)
    }

    fun fromHsl(h: Double, s: Double, l: Double): Int {
        val hue = h.mod(360.0)
        val sat = s.coerceIn(0.0, 1.0)
        val light = l.coerceIn(0.0, 1.0)
        val chroma = (1.0 - abs(2.0 * light - 1.0)) * sat
        val secondary = chroma * (1.0 - abs((hue / 60.0).mod(2.0) - 1.0))
        val match = light - chroma / 2.0
        val (r, g, b) = when {
            hue < 60 -> Triple(chroma, secondary, 0.0)
            hue < 120 -> Triple(secondary, chroma, 0.0)
            hue < 180 -> Triple(0.0, chroma, secondary)
            hue < 240 -> Triple(0.0, secondary, chroma)
            hue < 300 -> Triple(secondary, 0.0, chroma)
            else -> Triple(chroma, 0.0, secondary)
        }
        fun ch(v: Double) = ((v + match) * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Цвет тона [hue] с насыщенностью [sat] и яркостью [light] (0…1). */
    fun hsl(hue: Double, sat: Double, light: Double): Int = fromHsl(hue, sat, light)

    /** Относительная яркость, как Color.computeLuminance во Flutter. */
    fun luminance(argb: Int): Double {
        fun lin(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin((argb shr 16) and 0xFF) +
            0.7152 * lin((argb shr 8) and 0xFF) +
            0.0722 * lin(argb and 0xFF)
    }

    /** Отношение контраста двух непрозрачных цветов: 1…21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * Акцент светлой темы: тон и насыщенность [base], яркость — не выше 45 %
     * и такая, чтобы и белый текст на акценте, и акцент как текст на [on]
     * проходили AA (AccentFit.light почты).
     */
    fun accentLight(base: Int, on: Int): Int {
        var h = toHsl(base)
        if (h.l > 0.45) h = h.copy(l = 0.45)
        while (h.l > 0.06) {
            val c = fromHsl(h.h, h.s, h.l)
            if (contrast(c, on) >= TARGET && contrast(WHITE, c) >= TARGET) break
            h = h.copy(l = h.l - 0.01)
        }
        return fromHsl(h.h, h.s, h.l)
    }

    /**
     * Акцент тёмной темы: светлый тон [base] (насыщенность — не выше 75 %,
     * яркость — от 70 %), светлее, пока акцент как текст на [on] не пройдёт
     * AA (AccentFit.dark почты).
     */
    fun accentDark(base: Int, on: Int): Int {
        var h = toHsl(base)
        h = h.copy(s = min(h.s, 0.75), l = max(h.l, 0.70))
        while (h.l < 0.95 && contrast(fromHsl(h.h, h.s, h.l), on) < TARGET) {
            h = h.copy(l = h.l + 0.01)
        }
        return fromHsl(h.h, h.s, h.l)
    }

    /** Серый «Классики» — к тону темы: тон [Spec.neutralHue], насыщенность × [Spec.neutralSat]. */
    fun neutral(c: Int, spec: Spec): Int {
        val h = toHsl(c)
        return fromHsl(spec.neutralHue, (h.s * spec.neutralSat).coerceIn(0.0, 1.0), h.l)
    }

    /** Текст — темнее (в тёмной теме светлее), пока не пройдёт AA на всех [backs]. */
    fun text(c: Int, backs: List<Int>, dark: Boolean): Int {
        var h = toHsl(c)
        while (backs.any { contrast(fromHsl(h.h, h.s, h.l), it) < TARGET } && h.l > 0.02 && h.l < 0.98) {
            h = h.copy(l = h.l + if (dark) 0.01 else -0.01)
        }
        return fromHsl(h.h, h.s, h.l)
    }

    /** Производные акцента: тон темы, насыщенность не выше [maxSat], яркость [light]. */
    fun accentTone(spec: Spec, maxSat: Double, light: Double): Int {
        val base = toHsl(spec.accent)
        return hsl(base.h, min(base.s, maxSat), light)
    }
}
