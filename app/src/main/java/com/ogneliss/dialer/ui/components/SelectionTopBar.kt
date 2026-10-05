package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R

/**
 * ПАНЕЛЬ РЕЖИМА ВЫБОРА.
 *
 * Заменяет обычную панель сверху, пока идёт выделение. Показывает
 * количество выбранного и действия над ним.
 *
 * ── Почему счётчик, а не просто «Выбрано» ──────────────────────────────────
 * Пользователь должен видеть, сколько записей попадёт под удаление,
 * ДО нажатия. «Удалить 47 записей» и «удалить 3» — разные решения,
 * и цифра здесь важнее любой другой информации.
 *
 * ── Порядок действий ───────────────────────────────────────────────────────
 * Разрушительное действие стоит последним и справа: случайное касание
 * при закрытии режима не должно попадать на «Удалить».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onBlock: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = {
            // Число выбранного со склонением: «1 запись», «2 записи», «5 записей»
            Text(pluralStringResource(R.plurals.selected_count, count, count))
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(OgnelissIcons.Close, contentDescription = stringResource(R.string.nav_close))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    OgnelissIcons.SelectAll,
                    contentDescription = stringResource(R.string.selection_select_all)
                )
            }
            if (onBlock != null) {
                IconButton(onClick = onBlock) {
                    Icon(
                        OgnelissIcons.Block,
                        contentDescription = stringResource(R.string.action_block)
                    )
                }
            }
            // Удаление последним и справа: случайное касание при закрытии
            // режима не должно попадать на разрушительное действие
            IconButton(onClick = onDelete) {
                Icon(
                    OgnelissIcons.Delete,
                    contentDescription = stringResource(R.string.selection_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}
