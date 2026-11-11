package com.ogneliss.dialer.ui.conversation

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.db.StarredSmsEntity
import com.ogneliss.dialer.sms.Message
import com.ogneliss.dialer.sms.SmsFolder
import com.ogneliss.dialer.ui.components.OgnelissDivider
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space

/**
 * ВКЛАДКИ ПАПОК SMS (2.2.0) — строкой над нижней панелью раздела.
 *
 * Внизу, у пальца, как переключение разделов во всём приложении (просьба
 * автора). Выбранная — заливкой, у остальных — число непрочитанных.
 * Не помещаются — строка листается вбок. Долгое нажатие — порядок и показ.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderTabs(
    folders: List<SmsFolder>,
    current: SmsFolder,
    unread: Map<SmsFolder, Int>,
    onSelect: (SmsFolder) -> Unit,
    onEdit: () -> Unit
) {
    val editLabel = stringResource(R.string.settings_sms_folders)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth()) {
            OgnelissDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.s, vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                folders.forEach { folder ->
                    val on = folder == current
                    val count = unread[folder] ?: 0
                    Surface(
                        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (on) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .combinedClickable(
                                role = Role.Tab,
                                onClick = { onSelect(folder) },
                                onLongClick = onEdit,
                                onLongClickLabel = editLabel
                            )
                            .semantics { selected = on }
                    ) {
                        Row(
                            Modifier
                                .heightIn(min = TAB_HEIGHT)
                                .padding(horizontal = Space.m),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (folder == SmsFolder.STARRED) {
                                Icon(
                                    OgnelissIcons.Star, contentDescription = null,
                                    modifier = Modifier.padding(end = Space.xxs).size(FlowUi.iconSizeInline)
                                )
                            }
                            Text(
                                stringResource(smsFolderLabel(folder)),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (on) FontWeight.SemiBold else null
                            )
                            if (count > 0) {
                                Text(
                                    count.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = Space.xs)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Высота вкладки — не меньше цели касания. */
private val TAB_HEIGHT = 48.dp

/**
 * ИЗБРАННОЕ (2.2.0) — отмеченные сообщения, новые первыми: от кого, когда,
 * текст. Касание — переписка у этого сообщения; звёздочка — снять отметку.
 * Хранятся копии (sms_starred): удалённое из переписки здесь остаётся.
 */
@Composable
internal fun StarredList(
    starred: List<StarredSmsEntity>,
    titleOf: (String) -> String,
    onOpen: (StarredSmsEntity) -> Unit,
    onUnstar: (StarredSmsEntity) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        items(starred, key = { it.msgKey }) { e ->
            val date = remember(e.date) {
                DateUtils.formatDateTime(
                    context, e.date,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(e) }
                    .padding(start = FlowUi.screenPadding, top = Space.s, bottom = Space.s),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (e.outgoing) stringResource(R.string.sms_you, titleOf(e.address)) else titleOf(e.address),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(Space.s))
                        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        e.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onUnstar(e) }) {
                    Icon(
                        OgnelissIcons.Star,
                        contentDescription = stringResource(R.string.sms_unstar),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Найденное поиском сообщение (2.2.0): собеседник, дата и кусок текста
 * вокруг совпадения, совпадение — цветом. Касание — переписка у него.
 */
@Composable
internal fun FoundMessageRow(message: Message, title: String, query: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val date = remember(message.date) {
        DateUtils.formatDateTime(context, message.date, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
    val accent = MaterialTheme.colorScheme.primary
    val snippet = remember(message.key, query, accent) { highlight(message.body, query, accent) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = FlowUi.screenPadding, vertical = Space.s)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            snippet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Кусок текста с совпадением ближе к началу строки; совпадение — цветом и жирным. */
private fun highlight(body: String, query: String, color: androidx.compose.ui.graphics.Color): AnnotatedString {
    val text = body.replace('\n', ' ')
    val q = query.trim()
    val at = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (at < 0) return AnnotatedString(text)
    val from = if (at > SNIPPET_LEAD) at - SNIPPET_LEAD else 0
    val shown = (if (from > 0) "…" else "") + text.substring(from)
    val start = at - from + (if (from > 0) 1 else 0)
    return buildAnnotatedString {
        append(shown)
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), start, (start + q.length).coerceAtMost(shown.length))
    }
}

/** Знаков до совпадения в куске текста: чтобы было видно, о чём речь. */
private const val SNIPPET_LEAD = 24
