package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.SettingsCommands
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.swipe.RevealActionFactory
import com.ogneliss.dialer.ui.theme.Dimens

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ДЕЙСТВИЯ СВАЙПА — ДО ТРЁХ (1.2.5)
 * ══════════════════════════════════════════════════════════════════════════
 * Как в почтовых клиентах: на один свайп — несколько действий.
 *
 * • ничего не выбрано — свайпа в эту сторону нет;
 * • одно — срабатывает сразу, как было;
 * • два-три — строка сдвигается и показывает кнопки, действие —
 *   по нажатию. Строка ждёт в сдвинутом виде.
 *
 * Порядок важен: номер у отмеченного действия — его место на панели.
 * Первое — ближе к краю, откуда начат свайп. Хранится как раньше:
 * первое — основное действие свайпа, остальные — «дополнительные»
 * (prefs.swipeRightExtra / swipeLeftExtra — они были заведены давно,
 * но выбрать их было негде).
 */
@Composable
fun SwipeActionsDialog(
    vm: DialerViewModel,
    isRight: Boolean,
    primary: SwipeAction,
    extra: List<SwipeAction>,
    onDismiss: () -> Unit
) {
    val chosen = remember {
        mutableStateListOf<SwipeAction>().apply {
            if (primary != SwipeAction.NONE) add(primary)
            extra.filter { it != SwipeAction.NONE && it != primary }.take(MAX - 1).forEach { add(it) }
        }
    }
    val options = SwipeAction.entries.filter { it != SwipeAction.NONE }

    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (isRight) R.string.settings_swipe_right else R.string.settings_swipe_left),
        content = {
            Column {
                Text(
                    stringResource(R.string.swipe_actions_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.padding(top = Space.s))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(options, key = { it.key }) { action ->
                        val order = chosen.indexOf(action)
                        val selected = order >= 0
                        val full = chosen.size >= MAX
                        ActionRow(
                            action = action,
                            order = order,
                            // Уже три — остальные недоступны, пока не снять одно
                            enabled = selected || !full,
                            onToggle = {
                                if (selected) chosen.remove(action)
                                else if (!full) chosen.add(action)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setChoice(
                    if (isRight) "swipe_right" else "swipe_left",
                    (chosen.firstOrNull() ?: SwipeAction.NONE).key
                )
                vm.setSwipeExtra(isRight, chosen.drop(1))
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_cancel)) }
        }
    )
}

/** Не больше трёх: четвёртая кнопка на панели строки уже не помещается. */
private const val MAX = 3

@Composable
private fun ActionRow(
    action: SwipeAction,
    order: Int,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val visual = RevealActionFactory.iconOf(action)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .toggleable(value = order >= 0, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Номер места на панели вместо галочки: порядок здесь важен
        Surface(
            shape = CircleShape,
            color = if (order >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (order >= 0) {
                    Text(
                        (order + 1).toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.m))
        if (visual != null) {
            Icon(
                visual.first,
                contentDescription = null,
                tint = if (enabled) visual.second else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Space.m))
        }
        Text(
            stringResource(SettingsCommands.swipeActionLabelRes(action)),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
    }
}
