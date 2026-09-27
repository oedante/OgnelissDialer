package com.ogneliss.dialer.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp

/**
 * НАСТРОЙКИ ОФОРМЛЕНИЯ, ДОСТУПНЫЕ ЛЮБОМУ ЭКРАНУ.
 *
 * ── Зачем CompositionLocal, а не параметры ─────────────────────────────────
 * Размер кнопок и показ фото нужны на десятке экранов, включая глубоко
 * вложенные компоненты вроде строки журнала. Пробрасывать их параметрами
 * означало бы протащить пять аргументов через всю иерархию — и забыть
 * один из них в новом компоненте.
 *
 * Именно так и получилось до этого: переключатели «крупные кнопки»,
 * «показывать фото», «виброотклик» и «тоны клавиш» сохранялись
 * в настройках, но ни на что не влияли. Настройка без эффекта хуже
 * её отсутствия: пользователь считает, что приложение сломано.
 *
 * CompositionLocal решает это: значения задаются один раз в корне,
 * компоненты берут их сами.
 */
data class Appearance(
    /** Увеличенные клавиши и кнопки — для слабого зрения и неточных движений. */
    val largeButtons: Boolean = false,
    /** Показывать фотографии контактов в списках. */
    val showPhoto: Boolean = true,
    /** Виброотклик при нажатии клавиш. */
    val haptic: Boolean = true,
    /** Тональные сигналы клавиш, как на кнопочном телефоне. */
    val keyTones: Boolean = true
) {
    /** Высота клавиши с учётом режима крупных кнопок. */
    val dialKeyHeight: Dp
        get() = if (largeButtons) Dimens.dialKeyHeightLarge else Dimens.dialKeyHeight

    val callButtonHeight: Dp
        get() = if (largeButtons) Dimens.callButtonHeightLarge else Dimens.callButtonHeight

    val avatarSize: Dp
        get() = if (largeButtons) Dimens.avatarSizeLarge else Dimens.avatarSize
}

/**
 * Значение по умолчанию совпадает с настройками по умолчанию,
 * поэтому превью и тесты работают без обёртки.
 */
val LocalAppearance = compositionLocalOf { Appearance() }
