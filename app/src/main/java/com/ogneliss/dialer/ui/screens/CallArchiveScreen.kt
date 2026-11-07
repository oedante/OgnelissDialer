package com.ogneliss.dialer.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.ArchiveRules
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.RowAvatar
import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.components.SelectionTopBar
import com.ogneliss.dialer.ui.components.rememberSelectionState
import com.ogneliss.dialer.ui.theme.LocalAppearance
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.ui.theme.listName
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * АРХИВ ЖУРНАЛА (2.1.0) — «Ещё» → «Архив».
 *
 * Просьба автора (07.11.2026): в режиме выбора журнала — «удалить
 * и архивировать». Было: убрать запись можно было только удалением
 * навсегда; стало — «В архив», а здесь архив можно разобрать: «Вернуть
 * из архива» или «Удалить» (то же удаление с «Отменить», что в журнале).
 *
 * Список нарочно простой: портрет, имя или номер, дата последнего вызова.
 * Касание отмечает строку — других действий у архива нет, и открывать
 * карточку отсюда незачем (она открывается из поиска журнала).
 *
 * Показываются архивные карточки из прочитанной части журнала (страницы
 * CallLogPager): очень старые появятся, когда журнал догрузится.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallArchiveScreen(vm: DialerViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val archivedIds by vm.callArchivedIds.collectAsStateWithLifecycle()
    val pendingDeletion by vm.pendingDeletion.collectAsStateWithLifecycle()
    // Удалённые с «Отменить» пропадают сразу, как в журнале
    val archived = remember(state.callLog, archivedIds, pendingDeletion) {
        state.callLog.filter { it.key !in pendingDeletion && ArchiveRules.isGroupArchived(it, archivedIds) }
    }
    val selection = rememberSelectionState<String>()
    var confirmDelete by remember { mutableStateOf(false) }
    fun selectedGroups(): List<CallLogGroup> = archived.filter { it.key in selection.selected }

    // «Назад» сперва снимает выбор, потом закрывает экран
    BackHandler(enabled = selection.isActive) { selection.clear() }

    /*
     * Свой Scaffold, а не ScreenScaffold: при выборе верхнюю строку
     * заменяет панель выбора (как в журнале), а ScreenScaffold строку
     * заголовка подменять не умеет. Отступ от часов — safeDrawing.
     */
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            if (selection.isActive) {
                SelectionTopBar(
                    count = selection.count,
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(archived.map { it.key }) },
                    onUnarchive = {
                        vm.unarchiveGroups(selectedGroups())
                        selection.clear()
                    },
                    onDelete = { if (selection.count > 0) confirmDelete = true }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.archive_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(OgnelissIcons.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                        }
                    }
                )
            }
        }
    ) { pad ->
        if (archived.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(Space.xl), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.archive_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(bottom = Space.l)
            ) {
                item(key = "note", contentType = "note") {
                    // Как вернуть без кнопки: новый звонок сам возвращает запись
                    Text(
                        stringResource(R.string.archive_calls_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s)
                    )
                }
                items(archived, key = { it.key }, contentType = { "archived_call" }) { group ->
                    ArchivedCallRow(
                        group = group,
                        selectionActive = selection.isActive,
                        selected = selection.isSelected(group.key),
                        onToggle = {
                            if (selection.isActive) selection.toggle(group.key) else selection.start(group.key)
                        }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        val count = selection.count
        OgnelissDialog(
            onDismissRequest = { confirmDelete = false },
            title = stringResource(R.string.selection_delete_title),
            content = { Text(pluralStringResource(R.plurals.selection_delete_text, count, count)) },
            confirmButton = {
                TextButton(onClick = {
                    // То же удаление с «Отменить», что в журнале: id остаются
                    // в архиве, и отмена возвращает запись сюда же
                    vm.deleteGroupsWithUndo(selectedGroups())
                    selection.clear()
                    confirmDelete = false
                }) { Text(stringResource(R.string.selection_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.nav_cancel)) }
            }
        )
    }
}

/** Строка архива: портрет (или кружок выбора), имя, номер и дата последнего вызова. */
@Composable
private fun ArchivedCallRow(
    group: CallLogGroup,
    selectionActive: Boolean,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val title = group.displayName?.takeIf { it.isNotBlank() } ?: PhoneFormatter.format(group.number)
    val date = remember(group.lastDate) {
        DateUtils.formatDateTime(
            context, group.lastDate,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        )
    }
    // У контакта номер — во второй строке с датой; у номера без имени он уже в заголовке
    val subtitle = if (group.displayName.isNullOrBlank()) date else "${PhoneFormatter.format(group.number)} · $date"
    Column {
        RowDivider(Modifier.padding(start = Space.l + LocalAppearance.current.avatarSize + Space.m))
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                // Долгое нажатие — тоже выбор: в архиве оно не занято карточками
                .combinedClickable(onClick = onToggle, onLongClick = onToggle)
                .padding(
                    horizontal = Space.l,
                    vertical = LocalAppearance.current.rowPadding(OgnelissTheme.surfaces.outlined)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionActive) {
                Icon(
                    if (selected) OgnelissIcons.CheckCircle else OgnelissIcons.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(LocalAppearance.current.avatarSize)
                )
            } else {
                RowAvatar(group.photoUri, name = group.displayName?.takeIf { group.contactId != null })
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.listName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
