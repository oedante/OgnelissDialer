package com.ogneliss.dialer.ui.conversation

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.ogneliss.dialer.ui.components.MenuFolder
import com.ogneliss.dialer.ui.components.MenuFolderRows
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space

/**
 * ПАПКИ SMS В ШТОРКЕ «ЕЩЁ» (2.2.0) — как «Папки» в меню Ogneliss Mail
 * (автор, 11.11.2026: «за референс UI возьмём Ogneliss Mail»).
 *
 * Было в первой версии: вкладки строкой над панелью. В почте папки — список
 * над «Избранным» в меню, которое открывается нижней кнопкой: там же,
 * у пальца, и без лишней строки над списком всегда. Строка папки — значок,
 * название и число непрочитанных; открытая — заливкой, как выбранный
 * раздел. Значок справа от подписи — порядок и показ (SmsFoldersScreen).
 */
@Composable
internal fun SmsFolderRows(
    folders: List<SmsFolder>,
    current: SmsFolder,
    unread: Map<SmsFolder, Int>,
    onSelect: (SmsFolder) -> Unit,
    onEdit: () -> Unit
) {
    MenuFolderRows(
        title = stringResource(R.string.settings_sms_folders),
        folders = folders.map { folder ->
            MenuFolder(
                icon = smsFolderIcon(folder),
                label = stringResource(smsFolderLabel(folder)),
                count = unread[folder] ?: 0,
                selected = folder == current,
                onClick = { onSelect(folder) }
            )
        },
        onEdit = onEdit,
        editLabel = stringResource(R.string.sms_folders_edit)
    )
}

/** Значок папки — по смыслу, как значки папок почты. */
internal fun smsFolderIcon(folder: SmsFolder): ImageVector = when (folder) {
    SmsFolder.ALL -> OgnelissIcons.Forum
    SmsFolder.PERSONAL -> OgnelissIcons.Person
    SmsFolder.UNKNOWN -> OgnelissIcons.PersonOff
    SmsFolder.SERVICE -> OgnelissIcons.Article
    SmsFolder.UNREAD -> OgnelissIcons.Textsms
    SmsFolder.STARRED -> OgnelissIcons.Star
}

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
    // Пусто — пустой список без надписи (тихий интерфейс): вкладка и так подписана
    LazyColumn(Modifier.fillMaxSize()) {
        items(starred, key = { it.msgKey }) { e ->
            // Дата полностью: избранное хранится долго, «вчера» здесь ни о чём не скажет
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
                        // Своё — «Вы: имя», как последнее сообщение в списке переписок
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
                    // Четыре строки: избранное читают здесь же, не открывая переписку
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
    // Совпадение — цветом действия: глаз находит слово в куске текста сразу
    val accent = MaterialTheme.colorScheme.primary
    val snippet = remember(message.key, query, accent) { snippetWithMatch(message.body, query, accent) }
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
private fun snippetWithMatch(body: String, query: String, color: Color): AnnotatedString {
    // Переводы строк — пробелами: в строке результата две строки текста, не абзацы
    val text = body.replace('\n', ' ')
    val q = query.trim()
    val at = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    // Не нашлось (совпадение было в другом написании регистра) — текст как есть
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
