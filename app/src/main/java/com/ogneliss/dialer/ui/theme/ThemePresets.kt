package com.ogneliss.dialer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ogneliss.dialer.settings.ThemePreset

/**
 * ЦВЕТОВЫЕ ТЕМЫ КАК В OGNELISS MAIL — ПРИМЕНЕНИЕ (1.8.4).
 *
 * Берёт палитру «Классики» диалера (LightColors / DarkColors / Amoled)
 * и поворачивает её к теме: серые — к тону темы, акцент и его производные —
 * от акцента темы. Расчёт и причины — ThemePresetMath.
 *
 * «Классика» остаётся прежней палитрой диалера цвет в цвет: тема
 * по умолчанию ничего не меняет у тех, кто её не трогал.
 *
 * Не применяется: к высокой контрастности (там свои цвета ради чтения)
 * и к цветам из обоев (Material You заменяет и тему, и акцент) — как в почте.
 *
 * ── Что из диалера сохраняется ─────────────────────────────────────────────
 * Вторичная роль (выделение строк) остаётся нейтральной, как в «Классике»
 * диалера, — только поворачивается к тону темы. Зелёный звонка
 * и красный ошибок — свои роли.
 */
internal object ThemePresets {

    fun spec(preset: ThemePreset): ThemePresetMath.Spec = ThemePresetMath.SPECS.getValue(preset.name)

    /**
     * Цветовая схема [base] («Классика» диалера) в теме [preset] со своим
     * акцентом [accent] (1.9.0; null — акцент темы).
     *
     * «Классика» со своим акцентом — серые прежние, меняются только акцент
     * и его производные: тот, кто выбрал «зелёный», получает прежний
     * диалер с зелёным акцентом, а не другую тему (как в почте).
     */
    fun scheme(base: ColorScheme, preset: ThemePreset, dark: Boolean, accent: Int? = null): ColorScheme {
        if (preset == ThemePreset.CLASSIC && accent == null) return base
        val spec = spec(preset)
        val keepNeutrals = preset == ThemePreset.CLASSIC
        val n = { c: Color -> if (keepNeutrals) c else neutralOf(c, spec) }
        val source = accent ?: spec.accent
        fun tone(maxSat: Double, light: Double) = Color(ThemePresetMath.accentToneOf(source, maxSat, light))

        val fitOn = n(base.surfaceContainerHighest).toArgb()
        val primary = Color(
            if (dark) ThemePresetMath.accentDark(source, fitOn)
            else ThemePresetMath.accentLight(source, fitOn)
        )
        // Обратный акцент — для светлых подписей на тёмном и наоборот
        val inverse = Color(
            if (dark) ThemePresetMath.accentLight(source, n(Color(0xFFE3E8EF)).toArgb())
            else ThemePresetMath.accentDark(source, n(Color(0xFF33363C)).toArgb())
        )
        val backs = listOf(base.surfaceContainerHighest, base.surfaceContainerLow, base.background)
            .map { n(it).toArgb() }
        fun text(c: Color) = if (keepNeutrals) c else Color(ThemePresetMath.text(n(c).toArgb(), backs, dark))

        return base.copy(
            primary = primary,
            onPrimary = if (dark) tone(0.72, 0.14) else Color.White,
            primaryContainer = if (dark) tone(0.48, 0.26) else tone(0.65, 0.91),
            onPrimaryContainer = if (dark) tone(0.65, 0.91) else tone(0.72, 0.16),
            inversePrimary = inverse,
            secondary = n(base.secondary),
            onSecondary = n(base.onSecondary),
            secondaryContainer = n(base.secondaryContainer),
            onSecondaryContainer = text(base.onSecondaryContainer),
            background = n(base.background),
            onBackground = text(base.onBackground),
            surface = n(base.surface),
            onSurface = text(base.onSurface),
            surfaceVariant = n(base.surfaceVariant),
            onSurfaceVariant = text(base.onSurfaceVariant),
            surfaceContainerLowest = n(base.surfaceContainerLowest),
            surfaceContainerLow = n(base.surfaceContainerLow),
            surfaceContainer = n(base.surfaceContainer),
            surfaceContainerHigh = n(base.surfaceContainerHigh),
            surfaceContainerHighest = n(base.surfaceContainerHighest),
            surfaceBright = n(base.surfaceBright),
            surfaceDim = n(base.surfaceDim),
            outline = n(base.outline),
            outlineVariant = n(base.outlineVariant),
            inverseSurface = n(base.inverseSurface),
            inverseOnSurface = n(base.inverseOnSurface)
        )
    }

    /**
     * Поверхности диалера [base] в теме [preset]; выбранное — цветом акцента
     * ([scheme] уже со своим акцентом, если он задан, — [customAccent]).
     */
    fun surfaces(
        base: OgnelissSurfaces,
        preset: ThemePreset,
        scheme: ColorScheme,
        customAccent: Boolean = false
    ): OgnelissSurfaces {
        if (preset == ThemePreset.CLASSIC) {
            return if (customAccent) base.copy(navIndicator = scheme.primaryContainer) else base
        }
        val spec = spec(preset)
        val n = { c: Color -> neutralOf(c, spec) }
        return base.copy(
            tintedPage = n(base.tintedPage),
            listBand = n(base.listBand),
            tintBand = n(base.tintBand),
            card = n(base.card),
            cardBorder = n(base.cardBorder),
            avatarRing = n(base.avatarRing),
            avatarGlyph = n(base.avatarGlyph),
            divider = n(base.divider),
            navIndicator = scheme.primaryContainer
        )
    }

    /** Серый к тону темы; прозрачное (рамки «без рамки») — как есть. */
    private fun neutralOf(c: Color, spec: ThemePresetMath.Spec): Color =
        if (c.alpha < 1f) c else Color(ThemePresetMath.neutral(c.toArgb(), spec))

    /** «Полночь» в тёмной теме — чистый чёрный, как AMOLED. */
    fun blackDark(preset: ThemePreset): Boolean = spec(preset).blackDark
}
