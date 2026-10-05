package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.OgnelissDivider
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.EmptyReason
import com.ogneliss.dialer.ui.components.CallLogSkeleton
import com.ogneliss.dialer.ui.components.EmptyState
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.components.SearchBottomBar
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.NoteTeal
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * ЭКРАН ЗАМЕТОК.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОТДЕЛЬНЫЙ ЭКРАН, ЕСЛИ ЕСТЬ ФИЛЬТР
 * ══════════════════════════════════════════════════════════════════════════
 * Фильтр «с заметкой» в журнале отвечает на вопрос «кому я звонил
 * и оставил запись». А заметки нужны иначе: человек помнит, что где-то
 * записал, когда привезут мебель, но не помнит, чей это был номер
 * и когда был звонок. Искать через журнал — значит сначала вспомнить
 * то, ради чего заметка и писалась.
 *
 * Здесь заметки — сами по себе: список по алфавиту, как в контактах,
 * без привязки ко времени звонка.
 *
 * ── Почему по первой букве, а не по дате изменения ─────────────────────────
 * По дате удобно тому, кто ищет последнее. Но заметки живут месяцами
 * («не отвечает после 18:00»), и по дате нужная уходит вниз тем дальше,
 * чем дольше она верна. Алфавит устойчив: человек помнит, к кому
 * относится запись, чаще, чем когда её сделал.
 */
@Composable
fun NotesScreen(
    vm: DialerViewModel,
    onOpenCard: (String) -> Unit,
    /** Следующий экран карусели: напоминания. */
    onSwipeNext: (() -> Unit)? = null,
    /** Предыдущий: контакты. */
    onSwipePrevious: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()

    /** Поиск идёт по имени, номеру и тексту самой заметки. */
    var search by remember { mutableStateOf("") }


    /** Номер, заметку которого правят. */
    var editing by remember { mutableStateOf<String?>(null) }

    /*
     * Заметки с именами и группировкой.
     *
     * Имя ищется среди контактов по номеру: заметка привязана к номеру,
     * а не к контакту, и у номера без имени заголовком становится
     * сам номер — по нему и сортируется.
     */
    val grouped = remember(state.notes, state.contacts, search) {
        val nameByNumber = buildMap {
            state.contacts.forEach { contact ->
                contact.numbers.forEach { put(it.e164, contact.displayName) }
            }
        }

        state.notes
            .filterValues { it.note.isNotBlank() }
            .map { (e164, note) ->
                NoteRow(
                    e164 = e164,
                    title = nameByNumber[e164] ?: PhoneFormatter.format(e164),
                    note = note.note,
                    tags = note.tags
                )
            }
            /*
             * Поиск по трём полям сразу.
             *
             * По имени ищут, когда помнят, к кому запись. По тексту —
             * когда помнят только её содержание: «мебель», «вторник».
             * Второе и есть главная причина, по которой этот экран
             * существует, поэтому текст заметки в поиске обязателен.
             */
            .filter { row ->
                search.isBlank() ||
                    row.title.contains(search, ignoreCase = true) ||
                    row.note.contains(search, ignoreCase = true) ||
                    row.e164.contains(search)
            }
            /*
             * ПОРЯДОК ПО ТЕКСТУ ЗАМЕТКИ, А НЕ ПО ИМЕНИ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ
             * ══════════════════════════════════════════════════════════════
             * Сначала список был устроен как контакты: буква — имя.
             * Но сюда приходят не за человеком, а за записью: «где-то
             * я записал, когда привезут мебель». Имени человек в этот
             * момент не помнит — он помнит слово из заметки.
             *
             * Поэтому и буква группы, и порядок берутся из текста
             * заметки: «Замер мебели» ищется на «З», а не на «8 (918)».
             * Имя при этом никуда не девается, оно второй строкой.
             *
             * Знак «#» — для заметок, начинающихся не с буквы: цифры,
             * кавычки, смайлы. Он в конце списка: такие записи редки,
             * и держать их первыми значит отодвигать всё остальное.
             */
            .sortedBy { it.note.lowercase() }
            /*
             * Группы помеченных спамом больше нет: с 1.1.1 отметка живёт
             * отдельно от заметки (разбор в SpamMarkEntity), и на этом
             * экране только то, что человек написал себе. Помеченные
             * номера ищутся фильтром «Спам» в журнале.
             */
            .groupBy { row ->
                row.note.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
            }
            .toSortedMap(compareBy({ it == '#' }, { it }))
    }


    editing?.let { e164 ->
        val note = state.notes[e164]
        NoteEditorDialog(
            initialNote = note?.note.orEmpty(),
            initialTags = note?.tags.orEmpty().toSet(),
            onSave = { text, tags ->
                vm.saveNote(e164, text, tags.toList())
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    ScreenScaffold(
        title = stringResource(R.string.nav_notes),
        onBack = onBack,
        bottomBar = {
            SearchBottomBar(
                query = search,
                onQueryChange = { search = it },
                placeholder = stringResource(R.string.notes_search_hint),
                onSwipeNext = onSwipeNext,
                onSwipePrevious = onSwipePrevious
            )
        }
    ) { padding ->
        /*
         * ══════════════════════════════════════════════════════════════════
         * ПОКА ГРУЗИТСЯ — ЗАГЛУШКА, А НЕ «ЗАМЕТОК НЕТ»
         * ══════════════════════════════════════════════════════════════════
         * Заметки приходят из базы потоком, и первые кадры список пуст
         * всегда. Показывать в этот момент «Заметок пока нет» —
         * говорить неправду: они, скорее всего, есть, просто ещё
         * не прочитаны.
         *
         * Та же ошибка была в журнале и контактах, и там она уже
         * исправлена тем же способом.
         *
         * ⚠️ state.loading здесь — про загрузку журнала и книги,
         * а не заметок: своего признака у них нет. Это приближение,
         * но оно работает, потому что обе загрузки идут одновременно
         * и заканчиваются за доли секунды.
         */
        if (grouped.isEmpty() && state.loading && search.isBlank()) {
            CallLogSkeleton()
            return@ScreenScaffold
        }

        if (grouped.isEmpty()) {
            EmptyState(
                reason = if (search.isNotBlank()) {
                    EmptyReason.NoSearchResults(search)
                } else {
                    EmptyReason.NoNotes
                },
                onAction = { search = "" },
                // Отступы каркаса: иначе значок уходит под строку заголовка
                modifier = Modifier.padding(padding)
            )
            return@ScreenScaffold
        }

        /*
         * В ландшафте ширина ограничена.
         *
         * Строка «имя — заметка — две кнопки» на 900 dp растягивается
         * так, что кнопки уезжают к дальнему краю, а между ними
         * и текстом остаётся полэкрана пустоты. 560 dp — примерно
         * ширина телефона в портрете, то есть привычная длина строки.
         */
        // Шире телефона — список не растягивается во всю ширину.
        // Раньше смотрели на ориентацию, и раскрытая раскладушка
        // в портрете получала строки на всю ширину: разбор в WindowLayout
        val landscape = LocalWindowLayout.current.isWide

        LazyColumn(
            Modifier
                .padding(padding)
                .then(if (landscape) Modifier.widthIn(max = 560.dp) else Modifier)
        ) {
            grouped.forEach { (letter, rows) ->
                item(key = "letter-$letter") {
                    Text(
                        letter.toString(),
                        Modifier.padding(
                            start = FlowUi.screenPadding + Space.xs,
                            top = FlowUi.sheetPadding,
                            bottom = Space.xs
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OgnelissDivider()
                }

                items(rows, key = { it.e164 }) { row ->
                    NoteListRow(
                        row = row,
                        onOpen = { onOpenCard(row.e164) },
                        onCall = { vm.call(row.e164) },
                        onEdit = { editing = row.e164 }
                    )
                }
            }
        }
    }
}

/** Строка списка заметок. */
private data class NoteRow(
    val e164: String,
    val title: String,
    val note: String,
    val tags: List<String>
)

@Composable
private fun NoteListRow(
    row: NoteRow,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.screenPadding, vertical = FlowUi.cardSpacing)
    ) {
        Row(
            Modifier.padding(start = Space.l, end = Space.xs, top = Space.s, bottom = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                OgnelissIcons.StickyNote2,
                contentDescription = null,
                tint = NoteTeal,
                modifier = Modifier.heightIn(min = FlowUi.iconSizeDefault)
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Space.m)
            ) {
                /*
                 * Заметка — первой строкой, имя — второй.
                 *
                 * Раз список упорядочен по тексту заметки, глаз идёт
                 * по нему: имя над заметкой заставляло бы читать через
                 * строку, чтобы понять, в том ли месте искать.
                 */
                Text(
                    row.note,
                    style = MaterialTheme.typography.titleMedium,
                    // Две строки: заметка бывает длинной, но список
                    // должен оставаться списком
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.tags.isNotEmpty()) {
                    Text(
                        row.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                IconButton(onClick = onCall, modifier = Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET)) {
                    Icon(
                        OgnelissIcons.Call,
                        contentDescription = stringResource(R.string.action_call),
                        tint = com.ogneliss.dialer.ui.theme.CallGreen
                    )
                }
                /*
                 * Правка прямо из списка.
                 *
                 * Заметку правят чаще, чем звонят по ней: «привезут
                 * во вторник» становится «привезли». Заставлять ради
                 * этого открывать карточку значит просить лишних два
                 * нажатия на каждое уточнение.
                 */
                IconButton(onClick = onEdit, modifier = Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET)) {
                    Icon(
                        OgnelissIcons.Edit,
                        contentDescription = stringResource(R.string.action_edit)
                    )
                }
            }
        }
    }
}
