package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import androidx.compose.foundation.clickable
import com.ogneliss.dialer.ui.theme.PanelColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.ui.theme.Dimens

/**
 * КНОПКА НИЖНЕЙ ПАНЕЛИ: ЗНАЧОК С ПОДПИСЬЮ ПОД НИМ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ОБЩИЙ КОМПОНЕНТ
 * ══════════════════════════════════════════════════════════════════════════
 * Нижняя панель есть на четырёх экранах: журнал, контакты, заметки,
 * напоминания. В журнале под значками появились подписи, а на остальных
 * кнопка фильтра осталась голым значком — и панели разошлись по высоте:
 * значок с подписью выше голого значка на строку текста.
 *
 * Высоту панелей выравнивает общий токен FlowUi.bottomPanelRowHeight,
 * а вид кнопок — этот компонент. Раньше он был приватным ActionIcon
 * внутри панели журнала; второй экран с такой же кнопкой — повод
 * вынести его, а не скопировать.
 *
 * ── Устройство ─────────────────────────────────────────────────────────────
 * Нажимается вся колонка вместе с подписью. Подпись в одну строку,
 * с многоточием: под значком помещается одно слово.
 *
 * Для чтения с экрана описание у всей колонки, а у значка его нет:
 * иначе TalkBack прочёл бы подпись дважды.
 */
@Composable
fun LabeledIconButton(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    /**
     * Показывать ли подпись. По умолчанию — везде, кроме низкого окна
     * (телефон лёжа): там подписи съедали бы четверть высоты экрана.
     * Описание для чтения с экрана остаётся в любом случае.
     */
    showLabel: Boolean = !LocalWindowLayout.current.isShort &&
        com.ogneliss.dialer.ui.theme.LocalAppearance.current.buttonLabels
) {
    val label = stringResource(labelRes)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .padding(horizontal = Space.xs, vertical = 2.dp)
            .widthIn(min = Dimens.MIN_TOUCH_TARGET)
    ) {
        /*
         * ЗНАЧОК — В «ТАБЛЕТКЕ» ТОГО ЖЕ РАЗМЕРА, ЧТО КНОПКА КЛАВИАТУРЫ.
         *
         * Раньше по сторонам стояли голые значки (24 dp), а в центре —
         * крупная кнопка клавиатуры (88 × 52). Из-за разницы в высоте
         * над боковыми значками оставалась пустота, а подписи приходилось
         * выравнивать по низу.
         *
         * Теперь все пять кнопок одной высоты: боковые — таблетки
         * 56 × 40 со светлой подложкой (цвет клавиш набора), кнопка
         * клавиатуры — 72 × 40 и залита основным цветом. Ряд ровный,
         * пустоты нет, а кнопки стали крупнее и проще для пальца.
         *
         * Совмещены оба ваших варианта: боковые кнопки больше,
         * клавиатура — меньше (но всё ещё шире соседей и выделена цветом).
         */
        Box(
            Modifier
                .size(width = PILL_WIDTH, height = PILL_HEIGHT)
                .clip(CircleShape)
                .background(PanelColors.key()),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }

        if (showLabel) {
            // 4 dp, а не 2: подпись прилипала к таблетке кнопки
            Spacer(Modifier.height(Space.xs))

            // FitText (1.3.4): длинная подпись («Напоминания» в боковой
            // колонке) ужимается, а не обрезается многоточием
            FitText(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                minScale = 0.8f
            )
        }
    }
}

/** Размер «таблетки» боковой кнопки панели. */
internal val PILL_WIDTH = 56.dp
internal val PILL_HEIGHT = 40.dp
