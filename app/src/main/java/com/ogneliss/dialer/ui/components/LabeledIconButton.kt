package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
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
    showLabel: Boolean = !LocalWindowLayout.current.isShort
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
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .widthIn(min = Dimens.MIN_TOUCH_TARGET)
    ) {
        Icon(icon, contentDescription = null, tint = tint)

        if (showLabel) {
            Spacer(Modifier.height(2.dp))

            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
