package com.ogneliss.dialer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * ЦВЕТА НИЖНЕЙ ПАНЕЛИ И КЛАВИШ НАБОРА.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОТДЕЛЬНЫЕ ЦВЕТА, А НЕ ТОКЕНЫ ТЕМЫ НАПРЯМУЮ
 * ══════════════════════════════════════════════════════════════════════════
 * Клавиши набора были surfaceContainerHighest на панели surfaceContainer.
 * В светлой теме это Grey92 на Grey95 — контраст 1,04 : 1, то есть
 * клавиши почти сливались с панелью (измерено по снимку экрана
 * от пользователя: #E9EDF6 на #F2F2F2).
 *
 * Токены Material 3 здесь и не помогут сами по себе: соседние уровни
 * surfaceContainer отличаются на пару процентов яркости — они задуманы
 * для едва заметных слоёв, а не для кнопок, которые надо видеть с первого
 * взгляда. Нужна пара уровней через один, и в светлой и тёмной теме —
 * разная, потому что «приподнятое» там светлеет в разные стороны.
 *
 * ── Что выбрано ────────────────────────────────────────────────────────────
 * | Тема     | Панель           | Клавиша                 | Контраст |
 * |----------|------------------|-------------------------|----------|
 * | светлая  | surfaceContainerHighest | surfaceContainerLowest | 1,17 : 1 |
 * | тёмная   | surfaceContainer | surfaceContainerHighest | 1,34 : 1 |
 *
 * В светлой клавиши белые на сером — как у клавиатур Gboard и iOS:
 * клавиша читается как выступающая. В тёмной — светлее панели, тоже
 * «выступают». Контраст в обеих темах один порядка, так что вид
 * не меняется при переключении темы.
 *
 * Текст на панели от этого не страдает: основной цвет текста на Grey90 —
 * 12,8 : 1 при норме 4,5.
 *
 * ── Почему по яркости поверхности, а не по настройке темы ──────────────────
 * Тема бывает «как в системе», тёмной, светлой, AMOLED, с динамическими
 * цветами и без. Спросить у самой палитры «ты светлая?» — единственный
 * ответ, который верен во всех этих случаях сразу.
 *
 * ⚠️ Цвет панели один на все четыре экрана с нижней панелью (журнал,
 * контакты, заметки, напоминания): иначе при перелистывании край панели
 * менял бы цвет.
 */
object PanelColors {

    @Composable
    @ReadOnlyComposable
    private fun isLight(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    /** Фон нижней панели на всех экранах. */
    @Composable
    @ReadOnlyComposable
    fun panel(): Color =
        /*
         * В светлой теме — surfaceContainerHighest, а не surfaceVariant.
         *
         * С surfaceVariant (Grey90, #E1E3E1) клавиши и поля белели
         * хорошо — 1,29 : 1, — но сама панель на снимках с устройства
         * оказалась слишком тёмной для светлой темы: тяжёлая серая
         * полоса под светлым журналом. На уровень светлее — Grey92:
         * белое на нём 1,17 : 1, всё ещё заметно больше прежних 1,04,
         * при которых клавиши сливались с панелью.
         */
        if (isLight()) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainer

    /** Фон клавиши набора. */
    @Composable
    @ReadOnlyComposable
    fun key(): Color =
        if (isLight()) MaterialTheme.colorScheme.surfaceContainerLowest
        else MaterialTheme.colorScheme.surfaceContainerHighest
}
