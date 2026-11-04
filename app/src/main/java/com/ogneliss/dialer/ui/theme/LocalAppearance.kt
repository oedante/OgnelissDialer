package com.ogneliss.dialer.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.settings.ListDensity

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
    val keyTones: Boolean = true,
    /**
     * ПОДПИСИ ПОД КНОПКАМИ.
     *
     * Подписи нужны, пока человек учит приложение: «что делает эта
     * кнопка». Опытному они уже не нужны — значки узнаются с первого
     * взгляда, а подписи занимают место. Просили в отзыве в Google Play
     * про экран вызова; сделано сразу для всего приложения: нижние
     * панели, круглые кнопки карточки, кнопки экрана вызова.
     *
     * Без подписи описание кнопки для чтения с экрана (TalkBack)
     * остаётся: подпись прячется только с экрана, не из доступности.
     *
     * Не касается кнопок, где текст — сама кнопка («Завершить»,
     * «Ответить»): без текста там осталась бы пустая таблетка.
     */
    val buttonLabels: Boolean = true,
    /**
     * ПЛОТНОСТЬ ЖУРНАЛА И КОНТАКТОВ (1.9.0, как «Плотность ленты» Ogneliss
     * Mail): поле строки сверху и снизу и аватар — как DensitySpec почты.
     * Компактная — 2 dp и 36 dp (строка не ниже 48 dp), обычная — 10 и 48
     * (прежний вид), просторная — 14 и 52.
     */
    val listDensity: ListDensity = ListDensity.NORMAL
) {
    /** Высота клавиши с учётом режима крупных кнопок. */
    val dialKeyHeight: Dp
        get() = if (largeButtons) Dimens.dialKeyHeightLarge else Dimens.dialKeyHeight

    val callButtonHeight: Dp
        get() = if (largeButtons) Dimens.callButtonHeightLarge else Dimens.callButtonHeight

    /** Аватар в строке списка: крупные кнопки — крупный, иначе по плотности. */
    val avatarSize: Dp
        get() = when {
            largeButtons -> Dimens.avatarSizeLarge
            listDensity == ListDensity.COMPACT -> 36.dp
            listDensity == ListDensity.COMFORTABLE -> 52.dp
            else -> Dimens.avatarSize
        }

    /**
     * Поле строки журнала и контактов сверху и снизу. [outlined] —
     * высококонтрастная тема: там строки делит линия, и поле на 4 dp
     * меньше (как было: 6 вместо 10), но не меньше 2.
     */
    fun rowPadding(outlined: Boolean): Dp {
        val base = when (listDensity) {
            ListDensity.COMPACT -> 2.dp
            ListDensity.NORMAL -> 10.dp
            ListDensity.COMFORTABLE -> 14.dp
        }
        return if (outlined) (base - 4.dp).coerceAtLeast(2.dp) else base
    }
}

/**
 * Значение по умолчанию совпадает с настройками по умолчанию,
 * поэтому превью и тесты работают без обёртки.
 */
val LocalAppearance = compositionLocalOf { Appearance() }
