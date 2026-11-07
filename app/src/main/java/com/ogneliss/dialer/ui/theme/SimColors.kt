package com.ogneliss.dialer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * ЦВЕТА SIM-КАРТ (2.1.0).
 *
 * Было: «▯1» и «▯2» одним серым — с двумя картами глазом их не различить,
 * пока не вчитаешься в цифру. Автор, 07.11.2026: «можно взять отдельные
 * цвета для разных SIM». Так же Ogneliss Mail красит ящики
 * (account_color_indicator): цвет — второй признак, цифра остаётся.
 *
 * Палитра — без зелёного (он только у звонка) и без синего акцента
 * (управление): фиолетовый у первой карты, янтарный у второй, бирюзовый
 * и розовый у третьей-четвёртой (редкие телефоны с тремя-четырьмя).
 * Тёмной теме — светлые варианты: контраст с фоном не ниже 4.5.
 */
object SimColors {
    private val light = listOf(Color(0xFF6E56CF), Color(0xFFB45309), Color(0xFF0E7490), Color(0xFFBE185D))
    private val dark = listOf(Color(0xFFB4A5F5), Color(0xFFF6B85C), Color(0xFF67D0E4), Color(0xFFF59AC4))

    /** Цвет карты по слоту (0 — SIM 1). */
    @Composable
    fun of(slotIndex: Int): Color {
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val palette = if (isDark) dark else light
        return palette[Math.floorMod(slotIndex, palette.size)]
    }

    /** Подложка плашки — тот же цвет, приглушённый. */
    @Composable
    fun container(slotIndex: Int): Color = of(slotIndex).copy(alpha = 0.14f)
}
