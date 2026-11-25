package com.ogneliss.dialer.call

import com.ogneliss.dialer.ui.theme.PhotoPalette
import com.ogneliss.dialer.ui.theme.ThemePresetMath

/**
 * ЦВЕТА ОБЛИКОВ ЖИВОГО ФОНА (2.9.5 — «Пламя»; 2.9.10 — «Абстракция», «Закат», «Ночь»).
 *
 * Автор, 25.11.2026, о вариантах живого фона: «делаем 1, 2, 4» — облики без
 * картинок (та же анимация полей и волн, другие цвета), мягкий отсвет за
 * эмблемой и бережнее к батарее. Облик — только цвета: страница, образцы полей
 * (как у снимка звонящего, PhotoPalette.Swatch), волны и свечение — каждое
 * для светлой и тёмной темы; эмблема «пламя с лисой» — только у «Пламени».
 * Ни картинок, ни лишней работы в кадре: рисует тот же CallAmbientBackground.
 */
internal class AmbientPalette(
    private val pageLight: Int,
    private val pageDark: Int,
    private val swatchesLight: List<PhotoPalette.Swatch>,
    private val swatchesDark: List<PhotoPalette.Swatch>,
    private val ringLight: Int,
    private val ringDark: Int,
    private val glowLight: Int,
    private val glowDark: Int,
    /** Эмблема «пламя с лисой» над именем, когда нет фото (только «Пламя»). */
    val emblem: Boolean = false
) {
    fun page(dark: Boolean) = if (dark) pageDark else pageLight
    fun swatches(dark: Boolean) = if (dark) swatchesDark else swatchesLight
    fun ring(dark: Boolean) = if (dark) ringDark else ringLight
    fun glow(dark: Boolean) = if (dark) glowDark else glowLight
}

internal object AmbientPalettes {

    /** Цвета облика; null — «Цвета темы и фото» (тоны снимка или темы, как было). */
    fun of(style: AmbientStyle): AmbientPalette? = when (style) {
        AmbientStyle.THEME -> null
        AmbientStyle.FLAME -> FLAME
        AmbientStyle.ABSTRACT -> ABSTRACT
        AmbientStyle.SUNSET -> SUNSET
        AmbientStyle.NIGHT -> NIGHT
    }

    /*
     * «ПЛАМЯ» (2.9.5) — цвета значка (res/values/ic_launcher_background.xml):
     * пламя #CCAA6D, фон #3A4450, лиса #D3D8DC. Страница в тёмной теме — на тон
     * глубже фона значка (подписи читаются лучше), в светлой — тёплая светлая.
     * Волны в светлой теме — темнее (#AD8540): светлое золото на светлом не видно.
     * Поля: в светлой — золото, графит и янтарь; в тёмной — графит значка
     * (золото там бурело на весь экран), золото — в свечении и волнах.
     */
    private val FLAME = AmbientPalette(
        pageLight = 0xFFF8F4EC.toInt(),
        pageDark = 0xFF2B323C.toInt(),
        swatchesLight = swatchesOf(0xFFCCAA6D.toInt(), 0xFF3A4450.toInt(), 0xFFD9A85B.toInt()),
        swatchesDark = swatchesOf(0xFF4C5C72.toInt(), 0xFF3A4450.toInt(), 0xFF5A5249.toInt()),
        ringLight = 0xFFAD8540.toInt(),
        ringDark = 0xFFCCAA6D.toInt(),
        glowLight = 0xFFE9CF98.toInt(),
        glowDark = 0xFF8C7444.toInt(),
        emblem = true
    )

    /*
     * «АБСТРАКЦИЯ» (2.9.10, по макету автора — «плавные линии и световые эффекты»):
     * фиолетовый и синий. Светлая — сиреневая страница и насыщенные поля,
     * тёмная — глубокий индиго, поля приглушены, волны светлее.
     */
    private val ABSTRACT = AmbientPalette(
        pageLight = 0xFFF1F0FA.toInt(),
        pageDark = 0xFF1C1F3A.toInt(),
        swatchesLight = swatchesOf(0xFF6C63FF.toInt(), 0xFF4F7BE8.toInt(), 0xFFB07CE8.toInt()),
        swatchesDark = swatchesOf(0xFF3A3F8F.toInt(), 0xFF2C3A70.toInt(), 0xFF5B3F8A.toInt()),
        ringLight = 0xFF5A55D6.toInt(),
        ringDark = 0xFF9C9BFF.toInt(),
        glowLight = 0xFFCAC4FF.toInt(),
        glowDark = 0xFF4B4A9E.toInt()
    )

    /** «ЗАКАТ» (2.9.10): коралл, персик и роза; в тёмной — сливовые сумерки. */
    private val SUNSET = AmbientPalette(
        pageLight = 0xFFFBF1EC.toInt(),
        pageDark = 0xFF2A1F2A.toInt(),
        swatchesLight = swatchesOf(0xFFF08A5D.toInt(), 0xFFF2B880.toInt(), 0xFFC96C8E.toInt()),
        swatchesDark = swatchesOf(0xFF6E3B4E.toInt(), 0xFF8A4E3A.toInt(), 0xFF4A2F55.toInt()),
        ringLight = 0xFFC9603A.toInt(),
        ringDark = 0xFFF2A07B.toInt(),
        glowLight = 0xFFF6C9A8.toInt(),
        glowDark = 0xFF8A4A4E.toInt()
    )

    /** «НОЧЬ» (2.9.10): синие сумерки; в тёмной — почти чёрная синева, волны лунные. */
    private val NIGHT = AmbientPalette(
        pageLight = 0xFFEEF2F8.toInt(),
        pageDark = 0xFF0F1626.toInt(),
        swatchesLight = swatchesOf(0xFF3B5B8C.toInt(), 0xFF5C7CA8.toInt(), 0xFF8FA9CC.toInt()),
        swatchesDark = swatchesOf(0xFF1F3358.toInt(), 0xFF283F66.toInt(), 0xFF3A2F5C.toInt()),
        ringLight = 0xFF3B5B8C.toInt(),
        ringDark = 0xFF8FB2E6.toInt(),
        glowLight = 0xFFC3D3EA.toInt(),
        glowDark = 0xFF2F4A7A.toInt()
    )

    /** Образцы полей — как у снимка звонящего: первый весомее остальных. */
    private fun swatchesOf(vararg argb: Int): List<PhotoPalette.Swatch> = argb.mapIndexed { i, c ->
        val hsl = ThemePresetMath.toHsl(c)
        PhotoPalette.Swatch(hsl.h, hsl.s, hsl.l, 1.0 - 0.2 * i)
    }
}
