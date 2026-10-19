package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.lazy.itemsIndexed
import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.components.GroupedRow
import com.ogneliss.dialer.ui.components.SectionBand
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.data.RemindersRepository
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.EmptyReason
import com.ogneliss.dialer.ui.components.CallLogSkeleton
import com.ogneliss.dialer.ui.components.EmptyState
import com.ogneliss.dialer.ui.components.ReminderTimeDialog
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.components.LabeledIconButton
import com.ogneliss.dialer.ui.components.SearchBottomBar
import com.ogneliss.dialer.ui.components.SingleChoiceSheet
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.util.PhoneFormatter
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * ЭКРАН НАПОМИНАНИЙ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ПОРЯДОК ЗДЕСЬ ДРУГОЙ, ЧЕМ В ЗАМЕТКАХ
 * ══════════════════════════════════════════════════════════════════════════
 * Заметки живут месяцами и ищутся по человеку — там алфавит. Напоминание
 * существует ради момента: важно не «кому», а «когда» и «что раньше».
 * Поэтому список идёт по времени срабатывания, а заголовки групп — дни.
 *
 * Ближайшие два дня названы словами: «Сегодня» и «Завтра». Дальше дата —
 * «через 4 дня» человек всё равно переводит в число, чтобы сверить
 * с планами.
 *
 * ── Чего здесь нет ─────────────────────────────────────────────────────────
 * Просроченных. Сработавшее напоминание удаляет свою запись, а те,
 * что сработали при выключенном телефоне, убираются при восстановлении.
 * Список показывает только предстоящее — иначе он копил бы историю,
 * которой никто не пользуется.
 */
@Composable
fun RemindersScreen(
    vm: DialerViewModel,
    onOpenCard: (String) -> Unit,
    /** Следующий экран карусели: снова журнал, круг замыкается. */
    onSwipeNext: (() -> Unit)? = null,
    /** Предыдущий: заметки. */
    onSwipePrevious: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val reminders by vm.activeReminders.collectAsStateWithLifecycle()

    /** Поиск по имени и номеру: текста, как у заметки, здесь нет. */
    var search by remember { mutableStateOf("") }

    /**
     * Отбор по сроку: всё, сегодня, эта неделя.
     *
     * Дни здесь и так заголовками, но при двух десятках напоминаний
     * до «сегодня» приходится прокручивать. Отбор оставляет на экране
     * только то, что горит.
     */
    var days by remember { mutableStateOf(0) }
    var filterOpen by remember { mutableStateOf(false) }

    /** Напоминание, время которого правят. */
    var editing by remember { mutableStateOf<RemindersRepository.Reminder?>(null) }

    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dayFormat = remember { DateFormat.getDateInstance(DateFormat.FULL) }

    val today = stringResource(R.string.reminders_today)
    val tomorrow = stringResource(R.string.reminders_tomorrow)

    /*
     * Группировка по дням.
     *
     * Ключ — номер календарного дня, а не разница в миллисекундах:
     * напоминание в 23:50 сегодня и в 00:10 завтра разделяют двадцать
     * минут, но это разные дни, и человек их так и делит.
     */
    val grouped = remember(reminders, today, tomorrow, search, days) {
        reminders
            .filter { days == 0 || daysUntil(it.atMillis) < days }
            .filter { reminder ->
                search.isBlank() ||
                    reminder.displayName?.contains(search, ignoreCase = true) == true ||
                    reminder.e164.contains(search)
            }
            .sortedBy { it.atMillis }.groupBy { reminder ->
            when (daysUntil(reminder.atMillis)) {
                0 -> today
                1 -> tomorrow
                else -> dayFormat.format(Date(reminder.atMillis))
            }
        }
    }

    if (filterOpen) {
        SingleChoiceSheet(
            options = listOf(
                0 to R.string.contacts_all,
                1 to R.string.reminders_filter_today,
                7 to R.string.reminders_filter_week
            ),
            current = days,
            onSelect = { days = it },
            onDismiss = { filterOpen = false }
        )
    }

    editing?.let { reminder ->
        ReminderTimeDialog(
            displayName = reminder.displayName ?: PhoneFormatter.format(reminder.e164),
            initialMillis = reminder.atMillis,
            onSave = { chosen ->
                vm.scheduleReminderAt(reminder.e164, reminder.displayName, chosen)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    ScreenScaffold(
        title = stringResource(R.string.nav_reminders),
        onBack = onBack,
        bottomBar = {
            SearchBottomBar(
                query = search,
                onQueryChange = { search = it },
                placeholder = stringResource(R.string.reminders_search_hint),
                onSwipeNext = onSwipeNext,
                onSwipePrevious = onSwipePrevious,
                filterButton = {
                    // Та же шторка, что в контактах и заметках:
                    // отбор одиночный. Разбор — в SingleChoiceSheet
                    LabeledIconButton(
                        icon = OgnelissIcons.FilterList,
                        labelRes = R.string.filter_title,
                        onClick = { filterOpen = true },
                        tint = if (days > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        selected = days > 0
                    )
                }
            )
        }
    ) { padding ->
        // Пока идёт первая загрузка — заглушка, а не «напоминаний нет»:
        // разбор в NotesScreen, здесь то же самое
        if (grouped.isEmpty() && state.loading && search.isBlank()) {
            CallLogSkeleton()
            return@ScreenScaffold
        }

        if (grouped.isEmpty()) {
            EmptyState(
                reason = if (search.isNotBlank()) {
                    EmptyReason.NoSearchResults(search)
                } else {
                    EmptyReason.NoReminders
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
            grouped.forEach { (day, items) ->
                item(key = "day-$day") {
                    // Полоса раздела (сплошной список, 1.5.3)
                    SectionBand(day)
                }

                itemsIndexed(items, key = { _, r -> r.e164 }) { index, reminder ->
                    GroupedRow(GroupPosition.of(index, items.size)) {
                        ReminderListRow(
                            title = reminder.displayName?.takeIf { it.isNotBlank() }
                                ?: PhoneFormatter.format(reminder.e164),
                            time = timeFormat.format(Date(reminder.atMillis)),
                            onOpen = { onOpenCard(reminder.e164) },
                            onCall = { vm.call(reminder.e164) },
                            onEdit = { editing = reminder },
                            onDelete = { vm.cancelReminder(reminder.e164) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderListRow(
    title: String,
    time: String,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onOpen,
        // Строка во всю ширину (сплошной список, 1.5.3)
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = Space.l, end = Space.xs, top = Space.s, bottom = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                OgnelissIcons.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Space.m)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                // Время без даты: дата уже написана заголовком группы,
                // повторять её в каждой строке значит тратить ширину
                // на известное
                Text(
                    time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCall,
                modifier = Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET)
            ) {
                Icon(
                    OgnelissIcons.Call,
                    contentDescription = stringResource(R.string.action_call),
                    tint = CallGreen
                )
            }

            /*
             * Правка и удаление прямо в списке.
             *
             * Напоминание чаще переносят, чем звонят по нему: «перезвоню
             * не в три, а в пять». Ради этого открывать карточку значит
             * просить три лишних нажатия на каждый перенос.
             *
             * Удаление без подтверждения: потеря напоминания
             * не необратима — его ставят заново в два касания,
             * а лишнее окно на каждое снятие раздражает сильнее,
             * чем случайное удаление раз в месяц.
             */
            IconButton(
                onClick = onEdit,
                modifier = Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET)
            ) {
                Icon(
                    OgnelissIcons.Edit,
                    contentDescription = stringResource(R.string.action_edit)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET)
            ) {
                Icon(
                    OgnelissIcons.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Сколько календарных дней до момента. */
private fun daysUntil(atMillis: Long): Int {
    fun startOfDay(millis: Long) = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val diff = startOfDay(atMillis) - startOfDay(System.currentTimeMillis())
    return (diff / (24L * 60 * 60 * 1000)).toInt()
}
