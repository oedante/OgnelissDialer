package com.ogneliss.dialer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * ЦВЕТА ПОРТРЕТОВ С БУКВАМИ (2.1.2, дизайн-система Д8 — как в Ogneliss Mail).
 *
 * Было: у всех контактов без фото — одна заливка акцента и буквы акцентом;
 * в длинном журнале круги сливались, и человека не узнать по цвету. Стало,
 * как в почте (lib/core/utils/avatar_colors.dart): двенадцать приглушённых
 * средних тонов, у одного имени — всегда один. В светлой теме — светлая
 * заливка тоном человека (16 %) и буква тем же тоном темнее; в тёмной —
 * приглушённая заливка и светлая буква; в высококонтрастной — насыщенный
 * круг и белая буква.
 */
object AvatarPalette {

    /** Палитра Mail (kAvatarColorValues), тот же порядок. */
    private val tones = listOf(
        Color(0xFFB0504B), Color(0xFF467E59), Color(0xFF456EA3),
        Color(0xFF9E6B2A), Color(0xFF7E5A9E), Color(0xFF357F7A),
        Color(0xFFA3536F), Color(0xFF5F7A33), Color(0xFF555FA0),
        Color(0xFF8A7424), Color(0xFF37789A), Color(0xFF7D6456)
    )

    /** Тон для имени (или номера) — постоянный для одного ключа. */
    fun tone(key: String): Color = tones[Math.floorMod(key.trim().lowercase().hashCode(), tones.size)]

    /** Заливка круга и цвет букв. */
    fun colors(key: String, dark: Boolean, highContrast: Boolean, surface: Color): Pair<Color, Color> {
        val base = tone(key)
        return when {
            highContrast -> base to Color.White
            dark -> lerp(surface, base, 0.42f) to lerp(base, Color.White, 0.62f)
            else -> lerp(Color.White, base, 0.16f) to lerp(base, Color.Black, 0.25f)
        }
    }

    /**
     * ЗАЛИВКА ВХОДЯЩЕГО СООБЩЕНИЯ (2.1.3).
     *
     * Автор, 11.11.2026: «в диалогах SMS принятые сообщения белым по белому.
     * Можно ли их как-то раскрасить — по принципу, например, почтовика?».
     * Было: входящие — на surfaceContainerHigh, а он в светлой теме белый,
     * как страница: пузыря не видно, текст висит в воздухе. Стало, как
     * в почте, где у каждого человека свой цвет: входящие — в тоне
     * собеседника, том же, что у его портрета в списке ([key] — имя
     * из книги). Номер не из книги и буквенный отправитель — нейтральная
     * серая заливка [neutral], заметная на странице; она же — когда тон
     * собеседника почти совпал с цветом своих сообщений [own]. Текст остаётся
     * основным цветом: тон — подложка, а не краска букв.
     */
    fun bubble(
        key: String?,
        dark: Boolean,
        highContrast: Boolean,
        surface: Color,
        neutral: Color,
        /** Цвет своих сообщений (акцент): близкий к нему тон не берём. */
        own: Color? = null
    ): Color {
        if (key.isNullOrBlank() || highContrast) return neutral
        val tone = tone(key)
        // Тон собеседника близок к акценту — обе стороны переписки вышли бы
        // одного цвета (синий тон при синем акценте). Тогда — нейтральная
        if (own != null && hueDistance(tone, own) < NEAR_HUE) return neutral
        return if (dark) lerp(surface, tone, 0.30f) else lerp(Color.White, tone, 0.16f)
    }

    /** Ближе этого по цветовому кругу (градусы) — «тот же цвет». */
    private const val NEAR_HUE = 30f

    /** Оттенок 0…360; серый (без оттенка) — −1. */
    private fun hue(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue)
        val min = minOf(c.red, c.green, c.blue)
        val d = max - min
        if (d < 0.02f) return -1f
        val h = when (max) {
            c.red -> ((c.green - c.blue) / d) % 6f
            c.green -> (c.blue - c.red) / d + 2f
            else -> (c.red - c.green) / d + 4f
        } * 60f
        return (h + 360f) % 360f
    }

    /** Расстояние между оттенками по кругу, 0…180; серый далёк от любого. */
    internal fun hueDistance(a: Color, b: Color): Float {
        val ha = hue(a)
        val hb = hue(b)
        if (ha < 0f || hb < 0f) return 180f
        val d = kotlin.math.abs(ha - hb) % 360f
        return if (d > 180f) 360f - d else d
    }
}
