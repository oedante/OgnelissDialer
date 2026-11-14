package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.FolderOrder
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.OgnelissIcons

/**
 * ПАПКИ ЖУРНАЛА И КОНТАКТОВ — ПОРЯДОК И ПОКАЗ (2.5.0).
 *
 * Автор: «почему настройки панели только в SMS?». Экран — как у папок SMS
 * (SmsFoldersScreen): значок и название, стрелки «выше» и «ниже»,
 * переключатель показа. Первую папку («Все») не скрыть. Изменения — сразу,
 * меню перестраивается само. Открывается значком справа от «ПАПКИ» в «Меню».
 */
@Composable
internal fun MenuFoldersScreen(
    title: String,
    /** Все папки раздела: ключ, название, значок — в порядке по умолчанию. */
    folders: List<Triple<String, Int, ImageVector>>,
    raw: String?,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    val keys = folders.map { it.first }
    val fixed = keys.first()
    val order = FolderOrder.parse(raw, keys, fixed)
    ScreenScaffold(title = title, onBack = onBack, pinnedTopBar = true) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            itemsIndexed(order, key = { _, f -> f.first }) { i, (key, visible) ->
                val folder = folders.first { it.first == key }
                ListItem(
                    leadingContent = { Icon(folder.third, contentDescription = null) },
                    headlineContent = { Text(stringResource(folder.second)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onSave(FolderOrder.encode(FolderOrder.move(order, i, -1), fixed)) }, enabled = i > 0) {
                                Icon(OgnelissIcons.ArrowUpward, contentDescription = stringResource(R.string.pinned_move_up))
                            }
                            IconButton(onClick = { onSave(FolderOrder.encode(FolderOrder.move(order, i, 1), fixed)) }, enabled = i < order.lastIndex) {
                                Icon(OgnelissIcons.ArrowDownward, contentDescription = stringResource(R.string.pinned_move_down))
                            }
                            Switch(
                                checked = visible,
                                enabled = key != fixed,
                                onCheckedChange = { on ->
                                    onSave(FolderOrder.encode(order.toMutableList().apply { this[i] = key to on }, fixed))
                                }
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET)
                )
            }
        }
    }
}
