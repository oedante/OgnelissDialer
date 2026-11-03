package com.ogneliss.dialer.ui.theme

import kotlin.math.abs
import kotlin.math.min

/**
 * ЦВЕТА СНИМКА (1.8.4).
 *
 * Автор, 03.11.2026, снимок карточки контакта: «не нравится, как фото
 * переходит в панель — дёшево и топорно»; про экран вызова: «живой фон,
 * наверное, можно улучшить». Обоим нужны цвета самого снимка:
 *
 * • карточке — тёмный тон под именем на обложке. Чёрное затемнение
 *   превращало низ любого снимка в серую дымку; тон самого снимка
 *   держит белое имя так же, но остаётся цветом фото;
 * • живому фону — три тона, которыми он окрашен: у звонящего с фото фон
 *   в цветах его снимка, без фото — в цветах темы.
 *
 * Чистая математика на ARGB-целых, без Android: проверяет PhotoPaletteTest.
 * Снимок приходит уменьшенным (около 48 × 48 — PhotoColors.kt), поэтому
 * k-средних по всем точкам — доли миллисекунды.
 */
internal object PhotoPalette {

    /** Цвет снимка: тон (°), насыщенность и яркость HSL, вес (доля × заметность). */
    data class Swatch(val hue: Double, val sat: Double, val light: Double, val weight: Double)

    /**
     * Главные цвета [argb] — k-средних, отсортированы по весу.
     *
     * Вес — доля точек × (0,25 + насыщенность) × близость яркости к середине:
     * большое серое небо или пересвеченный фон не перекрашивают фон звонка
     * в серый, а глаза и одежда — заметнее своей площади.
     * Начальные центры — точки, равномерно взятые по снимку: результат
     * одинаков при каждом запуске.
     */
    fun swatches(argb: IntArray, k: Int = 6, iterations: Int = 10): List<Swatch> {
        val px = argb.filter { (it ushr 24) >= 128 }
        if (px.isEmpty()) return emptyList()
        val n = px.size
        val r = DoubleArray(n) { ((px[it] shr 16) and 0xFF) / 255.0 }
        val g = DoubleArray(n) { ((px[it] shr 8) and 0xFF) / 255.0 }
        val b = DoubleArray(n) { (px[it] and 0xFF) / 255.0 }
        val count = min(k, n)
        val cr = DoubleArray(count) { r[it * n / count] }
        val cg = DoubleArray(count) { g[it * n / count] }
        val cb = DoubleArray(count) { b[it * n / count] }
        val label = IntArray(n)
        repeat(iterations) {
            for (i in 0 until n) {
                var best = 0
                var bestD = Double.MAX_VALUE
                for (j in 0 until count) {
                    val dr = r[i] - cr[j]
                    val dg = g[i] - cg[j]
                    val db = b[i] - cb[j]
                    val d = dr * dr + dg * dg + db * db
                    if (d < bestD) {
                        bestD = d
                        best = j
                    }
                }
                label[i] = best
            }
            val sr = DoubleArray(count)
            val sg = DoubleArray(count)
            val sb = DoubleArray(count)
            val sn = IntArray(count)
            for (i in 0 until n) {
                val j = label[i]
                sr[j] += r[i]
                sg[j] += g[i]
                sb[j] += b[i]
                sn[j]++
            }
            for (j in 0 until count) if (sn[j] > 0) {
                cr[j] = sr[j] / sn[j]
                cg[j] = sg[j] / sn[j]
                cb[j] = sb[j] / sn[j]
            }
        }
        val sizes = IntArray(count).also { s -> label.forEach { s[it]++ } }
        return (0 until count).filter { sizes[it] > 0 }.map { j ->
            val h = ThemePresetMath.toHsl(argbOf(cr[j], cg[j], cb[j]))
            val share = sizes[j].toDouble() / n
            Swatch(h.h, h.s, h.l, share * (0.25 + h.s) * (1.0 - abs(h.l - 0.5)))
        }.sortedByDescending { it.weight }
    }

    /**
     * Тёмный тон снимка — подложка белого имени на обложке карточки.
     * Средний цвет строк [fromRow]…[toRow] (доли высоты) — той части, что
     * у имени; тон и насыщенность его (не выше 45 %, иначе подложка
     * кричит), яркость [light]. Серый снимок даёт серый тон, не цветной.
     */
    fun scrimTone(argb: IntArray, width: Int, fromRow: Float, toRow: Float, light: Double = SCRIM_LIGHT): Int {
        if (width <= 0 || argb.isEmpty()) return ThemePresetMath.hsl(0.0, 0.0, light)
        val rows = argb.size / width
        val from = (rows * fromRow).toInt().coerceIn(0, rows - 1)
        val to = (rows * toRow).toInt().coerceIn(from + 1, rows)
        var sr = 0.0
        var sg = 0.0
        var sb = 0.0
        var cnt = 0
        for (y in from until to) for (x in 0 until width) {
            val c = argb[y * width + x]
            if ((c ushr 24) < 128) continue
            sr += (c shr 16) and 0xFF
            sg += (c shr 8) and 0xFF
            sb += c and 0xFF
            cnt++
        }
        if (cnt == 0) return ThemePresetMath.hsl(0.0, 0.0, light)
        val h = ThemePresetMath.toHsl(argbOf(sr / cnt / 255.0, sg / cnt / 255.0, sb / cnt / 255.0))
        return ThemePresetMath.hsl(h.h, min(h.s, SCRIM_MAX_SAT), light)
    }

    /**
     * Цвета темы для живого фона без фото: акцент и два соседних тона
     * (+32° и −28°) — поле одного цвета выглядело плоским.
     */
    fun themeSwatches(accent: Int): List<Swatch> {
        val h = ThemePresetMath.toHsl(accent)
        return listOf(
            Swatch(h.h, h.s, h.l, 1.0),
            Swatch((h.h + 32.0).mod(360.0), h.s * 0.9, h.l, 0.8),
            Swatch((h.h - 28.0).mod(360.0), h.s * 0.85, h.l, 0.6)
        )
    }

    /**
     * Три тона живого фона из [swatches]: в светлой теме — пастель
     * (яркость 84–90 %), в тёмной — глубокие (22–30 %). Насыщенность —
     * от снимка: серый снимок даёт серый фон, а не выдуманный цвет.
     * Цветов меньше трёх — недостающие — соседние тона первого.
     * Текст экрана вызова ([texts]: имя и подписи) на каждом тоне проходит
     * AA — [fitToText].
     */
    fun ambientTones(swatches: List<Swatch>, dark: Boolean, texts: IntArray): IntArray {
        val base = swatches.ifEmpty { listOf(Swatch(0.0, 0.0, 0.5, 1.0)) }
        val picked = List(3) { i ->
            base.getOrNull(i) ?: base[0].let { it.copy(hue = (it.hue + if (i == 1) 32.0 else -28.0).mod(360.0)) }
        }
        return IntArray(3) { i ->
            val s = picked[i]
            val tone = if (dark) ThemePresetMath.hsl(s.hue, min(s.sat * 1.2, 0.55), DARK_LIGHT[i])
            else ThemePresetMath.hsl(s.hue, min(s.sat * 1.5, 0.70), LIGHT_LIGHT[i])
            fitToText(tone, texts, dark)
        }
    }

    /**
     * Тон, на котором [texts] проходят AA: в светлой теме светлее, в тёмной
     * темнее, пока контраст с каждым не станет не ниже TARGET. Жёлтый
     * и зелёный при той же «светлоте» HSL ярче синего — без подгонки серые
     * подписи на них не читались бы (3,3 : 1 в светлой, 2,8 : 1 в тёмной).
     */
    fun fitToText(tone: Int, texts: IntArray, dark: Boolean): Int {
        var h = ThemePresetMath.toHsl(tone)
        fun ok() = texts.all { ThemePresetMath.contrast(it, ThemePresetMath.hsl(h.h, h.s, h.l)) >= ThemePresetMath.TARGET }
        while (!ok() && h.l > 0.04 && h.l < 0.98) h = h.copy(l = h.l + if (dark) -0.01 else 0.01)
        return ThemePresetMath.hsl(h.h, h.s, h.l)
    }

    /**
     * Тоны по состоянию вызова: разговор без фото — на 45 % к зелёному
     * звонка ([greenHue]), с фото — те же цвета, приглушённые на 20 %
     * (зелёный поверх цветов снимка давал болотный); удержание — почти
     * серые. Тон сдвигается по короткой дуге круга.
     */
    fun moodTones(
        tones: IntArray,
        active: Boolean,
        holding: Boolean,
        fromPhoto: Boolean,
        greenHue: Double,
        dark: Boolean,
        texts: IntArray
    ): IntArray =
        IntArray(tones.size) { i ->
            val h = ThemePresetMath.toHsl(tones[i])
            val tone = when {
                holding -> ThemePresetMath.hsl(h.h, h.s * 0.15, h.l)
                active && fromPhoto -> ThemePresetMath.hsl(h.h, h.s * 0.8, h.l)
                active -> {
                    val diff = (greenHue - h.h + 540.0).mod(360.0) - 180.0
                    ThemePresetMath.hsl((h.h + diff * 0.45).mod(360.0), h.s * 0.85, h.l)
                }
                else -> tones[i]
            }
            fitToText(tone, texts, dark)
        }

    /**
     * Цвет волн: тон первого цвета фона, насыщенность не выше 55 %;
     * в светлой теме — средний (55 %), в тёмной — светлый (72 %).
     */
    fun ringTone(tone: Int, dark: Boolean): Int {
        val h = ThemePresetMath.toHsl(tone)
        return ThemePresetMath.hsl(h.h, min(h.s, 0.55), if (dark) 0.72 else 0.55)
    }

    /**
     * Свечение вокруг портрета: светлая дымка тона фона (95 %) в светлой
     * теме, приглушённый свет (40 %) в тёмной; имя под портретом на нём
     * читается — [fitToText].
     */
    fun haloTone(tone: Int, dark: Boolean, texts: IntArray): Int {
        val h = ThemePresetMath.toHsl(tone)
        val c = if (dark) ThemePresetMath.hsl(h.h, min(h.s, 0.5), 0.40)
        else ThemePresetMath.hsl(h.h, h.s * 0.8, 0.95)
        return fitToText(c, texts, dark)
    }

    private fun argbOf(r: Double, g: Double, b: Double): Int {
        fun ch(v: Double) = (v * 255.0 + 0.5).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Подложка имени: тёмная, но своего тона. */
    const val SCRIM_LIGHT = 0.15
    private const val SCRIM_MAX_SAT = 0.45
    private val LIGHT_LIGHT = doubleArrayOf(0.84, 0.88, 0.90)
    private val DARK_LIGHT = doubleArrayOf(0.30, 0.25, 0.22)
}
