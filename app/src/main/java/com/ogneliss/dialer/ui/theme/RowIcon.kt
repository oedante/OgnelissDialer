package com.ogneliss.dialer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ТОН ЗНАЧКА, КОТОРЫЙ ЛИШЬ ОБОЗНАЧАЕТ СТРОКУ — ОДИН НА ВСЁ ПРИЛОЖЕНИЕ
 * (2.7.0, обзор вида 15.11.2026).
 *
 * Было: значок слева в строке настроек — синий (primary), в «Меню» и в шторках —
 * серый (onSurfaceVariant), в карточке абонента — вперёд и назад. Владелец:
 * «приложение выглядит сделанным из разных кусков».
 *
 * ПРАВИЛО.
 *  • Значок, который лишь называет строку (заметка, почта, дата, «Язык»,
 *    «Блокировка»), — [tint]: onSurfaceVariant, тот же, что у значков шторок.
 *  • Цвет — только со смыслом:
 *      – зелёный у вызова (CallGreen);
 *      – красный у опасного (BlockRed, error);
 *      – фирменный цвет у мессенджеров, цвет SIM;
 *      – primary у ВКЛЮЧЁННОГО или выбранного состояния (закреплено,
 *        избранное, активный фильтр, выбран режим выбора) и у кнопок-действий
 *        («Добавить», «Изменить», «Наверх»).
 *
 * Новая строка со значком берёт [tint], а не пишет MaterialTheme.colorScheme.*
 * на месте: иначе через месяц тонов снова станет три.
 */
object RowIcon {
    @Composable
    fun tint(): Color = MaterialTheme.colorScheme.onSurfaceVariant
}
