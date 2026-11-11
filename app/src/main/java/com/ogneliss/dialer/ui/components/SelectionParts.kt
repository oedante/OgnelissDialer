package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.LocalAppearance
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space

/**
 * ОТМЕТКА ВЫБОРА НА МЕСТЕ ПОРТРЕТА (2.1.1).
 *
 * Автор, 09.11.2026: «значок выбора очень крупный». Было: значок-кружок
 * размером с портрет (48–56 dp) — толстые кольца во всю высоту строки.
 * Стало: кружок 24 dp по центру места портрета, как флажки в почте
 * и галерее: место то же (текст строки не сдвигается), а отметка
 * не спорит с именем. Отмеченная — залитый кружок с галочкой.
 */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(LocalAppearance.current.avatarSize), contentAlignment = Alignment.Center) {
        if (selected) {
            Box(
                Modifier
                    .size(MARK_DP.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    OgnelissIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Icon(
                OgnelissIcons.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(MARK_DP.dp)
            )
        }
    }
}

private const val MARK_DP = 24

/**
 * ДЕЙСТВИЯ РЕЖИМА ВЫБОРА — ВНИЗУ (2.1.1, дизайн-система Д2).
 *
 * Автор, 09.11.2026: «из-за того, что панель действий сверху, всё съезжает
 * вниз». В журнале и SMS сверху панели нет (поиск и фильтры — внизу,
 * у большого пальца), и панель выбора, появляясь, сдвигала весь список
 * на свою высоту. Стало: действия встают на место нижней панели
 * разделов — список не двигается, а кнопки — там же, где палец.
 *
 * Слева — «Выйти» и число отмеченного; справа — действия с подписями.
 * «Удалить» — последним и красным: случайное касание при выходе из
 * режима на него не попадает. Действия над пустым выбором неактивны.
 */
@Composable
fun SelectionBottomBar(
    count: Int,
    onClose: () -> Unit,
    /** null — без «Все» (переписка, 2.2.0: место нужнее своим действиям). */
    onSelectAll: (() -> Unit)?,
    onBlock: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
    /** Свои действия экрана перед «Удалить» (2.2.0: переписка — копировать, переслать, избранное). */
    extra: @Composable RowScope.() -> Unit = {},
    onDelete: () -> Unit
) {
    val countText = pluralStringResource(R.plurals.selected_count, count, count)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth()) {
            OgnelissDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(OgnelissIcons.Close, contentDescription = stringResource(R.string.selection_exit))
                }
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .clearAndSetSemantics { contentDescription = countText }
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    if (onSelectAll != null) {
                        SelectionAction(OgnelissIcons.SelectAll, stringResource(R.string.selection_short_all), true, onSelectAll)
                    }
                    if (onBlock != null) {
                        SelectionAction(OgnelissIcons.Block, stringResource(R.string.selection_short_block), count > 0, onBlock)
                    }
                    if (onArchive != null) {
                        SelectionAction(OgnelissIcons.Archive, stringResource(R.string.action_archive), count > 0, onArchive)
                    }
                    if (onUnarchive != null) {
                        SelectionAction(OgnelissIcons.Unarchive, stringResource(R.string.selection_short_unarchive), count > 0, onUnarchive)
                    }
                    extra()
                    SelectionAction(
                        OgnelissIcons.Delete, stringResource(R.string.selection_delete), count > 0, onDelete,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
internal fun RowScope.SelectionAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val color = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(
        Modifier
            .weight(1f, fill = false)
            .widthIn(min = 56.dp, max = 92.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xs, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.xxs)
        )
    }
}
