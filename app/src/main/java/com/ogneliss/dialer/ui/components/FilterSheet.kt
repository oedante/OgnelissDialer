package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.ui.res.pluralStringResource
import com.ogneliss.dialer.ui.theme.NoteTeal
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.CallLogFilter
import com.ogneliss.dialer.data.CallType

/**
 * ФИЛЬТРЫ ЖУРНАЛА В ШТОРКЕ.
 *
 * ── Что было не так с рядом чипов ──────────────────────────────────────────
 * Восемь чипов в одну строку не помещались и уезжали за край экрана.
 * Часть фильтров была попросту недоступна: о существовании «Спам»
 * и «Незнакомые» можно было узнать, только догадавшись прокрутить
 * ряд вбок, чего никто не делает.
 *
 * Плюс ряд занимал место над списком постоянно, хотя фильтрами
 * пользуются изредка.
 *
 * ── Как сейчас ─────────────────────────────────────────────────────────────
 * Одна кнопка «Фильтры» со счётчиком активных условий. Нажатие открывает
 * шторку, где все фильтры видны разом и сгруппированы по смыслу.
 *
 * Счётчик на кнопке принципиален: без него активный фильтр не виден,
 * и пользователь недоумевает, почему журнал наполовину пуст.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filter: CallLogFilter,
    simCount: Int,
    onFilterChange: (CallLogFilter) -> Unit,
    /** Сколько записей проходит текущий отбор. */
    matchCount: Int = 0,
    /** Показывать выбор «Вызовы / Сообщения» (2.0.0): SMS есть в журнале. */
    showKind: Boolean = false,
    onDismiss: () -> Unit
) {
    /*
     * Шторка раскрывается сразу полностью и учитывает системные панели.
     *
     * По умолчанию ModalBottomSheet открывается наполовину и не знает
     * о навигационной панели: нижние пункты оказываются под кнопками
     * системы, и нажать их нельзя.
     */
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        // Белая, как журнал: фильтры отбирают его записи (1.6.1)
        containerColor = listSheetContainerColor(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = FlowUi.sheetBottomPadding)
        ) {
            Row(
                Modifier.padding(horizontal = FlowUi.sheetPadding),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.filter_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
                if (filter.isActive) {
                    TextButton(onClick = { onFilterChange(CallLogFilter()) }) {
                        Text(stringResource(R.string.filter_reset))
                    }
                }
            }

            /*
             * Группы — как разделы журнала (1.6.1, автор: «фильтры сделаем
             * как в журнале»): скруглённая полоса с подписью, как «СЕГОДНЯ»
             * над записями, фишки — под ней на белой странице.
             */
            /*
             * Вызовы или сообщения (2.0.0) — только когда сообщения есть
             * в журнале: без роли SMS переключатель был бы мёртвым.
             */
            if (showKind) {
                FilterGroup(stringResource(R.string.filter_group_kind)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        CallLogFilter.Kind.entries.forEach { kind ->
                            FilterChip(
                                selected = filter.kind == kind,
                                onClick = { onFilterChange(filter.copy(kind = kind)) },
                                label = { Text(stringResource(kindLabel(kind))) },
                                leadingIcon = if (filter.kind == kind) {
                                    {
                                        Icon(
                                            OgnelissIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FlowUi.iconSizeInline)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            FilterGroup(stringResource(R.string.filter_group_type)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    TypeChip(filter, CallType.MISSED, R.string.call_missed, onFilterChange)
                    TypeChip(filter, CallType.INCOMING, R.string.call_incoming, onFilterChange)
                    TypeChip(filter, CallType.OUTGOING, R.string.call_outgoing, onFilterChange)
                }
            }

            FilterGroup(stringResource(R.string.filter_group_period)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    CallLogFilter.Period.entries.forEach { period ->
                        FilterChip(
                            selected = filter.period == period,
                            onClick = { onFilterChange(filter.copy(period = period)) },
                            label = { Text(stringResource(periodLabel(period))) },
                            // Галочка у выбранного: период — выбор одного
                            // из нескольких, и видно, какой действует
                            leadingIcon = if (filter.period == period) {
                                {
                                    Icon(
                                        OgnelissIcons.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FlowUi.iconSizeInline)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            // SIM показываем только при двух картах: мёртвый фильтр
            // занимает место и сбивает с толку
            if (simCount > 1) {
                FilterGroup(stringResource(R.string.filter_group_sim)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        repeat(simCount) { slot ->
                            FilterChip(
                                selected = filter.simSlot == slot,
                                onClick = {
                                    onFilterChange(
                                        filter.copy(
                                            simSlot = if (filter.simSlot == slot) null else slot
                                        )
                                    )
                                },
                                label = { Text("SIM ${slot + 1}") }
                            )
                        }
                    }
                }
            }

            FilterGroup(stringResource(R.string.filter_group_other)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FilterChip(
                        selected = filter.unknownOnly,
                        onClick = { onFilterChange(filter.copy(unknownOnly = !filter.unknownOnly)) },
                        label = { Text(stringResource(R.string.filter_unknown)) },
                        leadingIcon = {
                            Icon(
                                OgnelissIcons.PersonOff,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeInline)
                            )
                        }
                    )
                    /*
                     * «Ожидают ответа» — первой фишкой в ряду.
                     *
                     * Это единственный отбор, который отвечает на вопрос
                     * «что я должен сделать», а не «что было». Им пользуются
                     * чаще остальных, и ставить его после «спама» значило бы
                     * прятать нужное за редким.
                     */
                    FilterChip(
                        selected = filter.awaitingCallbackOnly,
                        onClick = {
                            onFilterChange(
                                filter.copy(
                                    awaitingCallbackOnly = !filter.awaitingCallbackOnly
                                )
                            )
                        },
                        label = { Text(stringResource(R.string.filter_awaiting)) },
                        leadingIcon = {
                            Icon(
                                OgnelissIcons.CallMissed,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeInline),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                    FilterChip(
                        selected = filter.withNoteOnly,
                        onClick = {
                            onFilterChange(filter.copy(withNoteOnly = !filter.withNoteOnly))
                        },
                        label = { Text(stringResource(R.string.filter_with_note)) },
                        leadingIcon = {
                            Icon(
                                OgnelissIcons.StickyNote2,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeInline),
                                tint = NoteTeal
                            )
                        }
                    )
                    FilterChip(
                        selected = filter.withReminderOnly,
                        onClick = {
                            onFilterChange(
                                filter.copy(withReminderOnly = !filter.withReminderOnly)
                            )
                        },
                        label = { Text(stringResource(R.string.filter_with_reminder)) },
                        leadingIcon = {
                            Icon(
                                OgnelissIcons.Alarm,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeInline)
                            )
                        }
                    )
                    FilterChip(
                        selected = filter.spamOnly,
                        onClick = { onFilterChange(filter.copy(spamOnly = !filter.spamOnly)) },
                        label = { Text(stringResource(R.string.filter_spam)) },
                        leadingIcon = {
                            Icon(
                                OgnelissIcons.Block,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeInline),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }

            /*
             * СКОЛЬКО ЗАПИСЕЙ ОТОБРАНО.
             *
             * ── Зачем ──────────────────────────────────────────────────────
             * Шторка закрывает список, и результат отбора не виден:
             * человек выбирает условия вслепую и закрывает её, чтобы
             * проверить. Если ничего не нашлось — открывает снова.
             *
             * Счётчик показывает итог сразу. Ноль при этом не ошибка,
             * а ответ: с такими условиями записей нет, и менять надо
             * условия, а не искать поломку.
             */
            if (filter.isActive) {
                Text(
                    if (matchCount == 0) {
                        stringResource(R.string.filter_no_matches)
                    } else {
                        pluralStringResource(
                            R.plurals.filter_match_count, matchCount, matchCount
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = FlowUi.sheetPadding, vertical = Space.m),
                    color = if (matchCount == 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Кнопка открытия фильтров со счётчиком.
 * Счётчик обязателен: иначе активный фильтр невидим, и наполовину
 * пустой журнал выглядит как потеря данных.
 */
@Composable
fun FilterButton(filter: CallLogFilter, onClick: () -> Unit) {
    if (filter.isActive) {
        FilledTonalButton(onClick = onClick) {
            Icon(OgnelissIcons.FilterList, null, Modifier.size(FlowUi.iconSizeInline))
            Spacer(Modifier.width(Space.s))
            Text(stringResource(R.string.filter_active_count, filter.activeCount))
        }
    } else {
        TextButton(onClick = onClick) {
            Icon(OgnelissIcons.FilterList, null, Modifier.size(FlowUi.iconSizeInline))
            Spacer(Modifier.width(Space.s))
            Text(stringResource(R.string.filter_title))
        }
    }
}

/**
 * Группа фильтров — раздел журнала (1.6.1): полоса с подписью и фишки
 * под ней. Фишки начинаются там же, где аватары в журнале (16 dp);
 * сверху и снизу у фишки и так 8 dp невидимой области нажатия
 * (48 dp при видимых 32), поэтому полей по вертикали почти нет.
 */
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    ListSheetGroup(title = title) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.l, end = Space.l, bottom = Space.xs)
        ) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    horizontalArrangement: Arrangement.Horizontal,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = horizontalArrangement
    ) { content() }
}

@Composable
private fun TypeChip(
    filter: CallLogFilter,
    type: CallType,
    labelRes: Int,
    onChange: (CallLogFilter) -> Unit
) {
    /*
     * ИКОНКА РЯДОМ С ПОДПИСЬЮ.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Три фишки «Пропущенный», «Входящий», «Исходящий» отличаются
     * только текстом, и различать их приходится чтением. А те же стрелки
     * человек уже видит в каждой строке журнала: связь между фильтром
     * и тем, что он отбирает, становится очевидной без слов.
     *
     * Цвет у пропущенного красный — тот же, что в списке. Одинаковый
     * признак должен выглядеть одинаково везде, иначе приходится
     * запоминать два разных обозначения одного и того же.
     */
    FilterChip(
        selected = type in filter.types,
        leadingIcon = {
            Icon(
                when (type) {
                    CallType.MISSED -> OgnelissIcons.CallMissed
                    CallType.INCOMING -> OgnelissIcons.CallReceived
                    else -> OgnelissIcons.CallMade
                },
                contentDescription = null,
                modifier = Modifier.size(FlowUi.iconSizeInline),
                tint = if (type == CallType.MISSED) {
                    MaterialTheme.colorScheme.error
                } else {
                    LocalContentColor.current
                }
            )
        },
        onClick = {
            onChange(
                filter.copy(
                    types = if (type in filter.types) filter.types - type
                    else filter.types + type
                )
            )
        },
        label = { Text(stringResource(labelRes)) }
    )
}

private fun kindLabel(kind: CallLogFilter.Kind): Int = when (kind) {
    CallLogFilter.Kind.ALL -> R.string.filter_kind_all
    CallLogFilter.Kind.CALLS -> R.string.filter_kind_calls
    CallLogFilter.Kind.MESSAGES -> R.string.filter_kind_messages
}

private fun periodLabel(period: CallLogFilter.Period): Int = when (period) {
    CallLogFilter.Period.ALL -> R.string.filter_period_all
    CallLogFilter.Period.TODAY -> R.string.filter_period_today
    CallLogFilter.Period.WEEK -> R.string.filter_period_week
    CallLogFilter.Period.MONTH -> R.string.filter_period_month
}
