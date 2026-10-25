package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import com.ogneliss.dialer.ui.components.SectionBand
import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.components.GroupedRow
import com.ogneliss.dialer.ui.theme.Space

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * СТАТИСТИКА ВЫЗОВОВ.
 *
 * ── Что показываем и почему именно это ─────────────────────────────────────
 * Сводка за загруженный период: сколько всего звонков, сколько времени
 * в разговорах, соотношение входящих и исходящих. Плюс список тех,
 * с кем говорите чаще всего.
 *
 * Диаграмм намеренно нет. Красивый график по месяцам требует полного
 * журнала за годы, которого у нас нет: система хранит ограниченную
 * историю. Рисовать красивую кривую по трём месяцам и называть её
 * «статистикой за год» — обманывать пользователя.
 *
 * ── Честная оговорка о глубине ─────────────────────────────────────────────
 * Внизу указано, за какой период посчитаны цифры. Без этого пользователь
 * решит, что видит статистику за всё время, и удивится, почему давний
 * собеседник не в списке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    vm: DialerViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val stats = remember(state.callLog) { vm.computeStats() }

    ScreenScaffold(
        // Как настройки Ogneliss Mail: серо-голубая страница, группы — карточками (1.5.6)
        tone = com.ogneliss.dialer.ui.theme.PageTone.TINTED,
        title = stringResource(R.string.stats_title),
        onBack = onBack
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {

            item {
                Row(
                    Modifier.fillMaxWidth().padding(FlowUi.contentPadding),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard(stats.totalCalls, R.string.stats_total_calls)
                    StatCard(stats.incoming, R.string.call_incoming)
                    StatCard(stats.outgoing, R.string.call_outgoing)
                    StatCard(stats.missed, R.string.call_missed, highlight = true)
                }
            }

            // Сводка — строками во всю ширину (сплошной список, 1.5.3)
            item {
                val minutes = (stats.totalDurationSec / 60).toInt()
                GroupedRow(GroupPosition.SINGLE) {
                Column {
                OgnelissListRow(
                    headline = pluralStringResource(
                        R.plurals.duration_minutes, minutes, minutes
                    ),
                    supporting = stringResource(R.string.stats_total_time)
                )

                // Средняя длительность считается только по состоявшимся
                // разговорам: пропущенные занизили бы её вдвое
                if (stats.averageDurationSec > 0) {
                    RowDivider(Modifier.padding(start = Space.l))
                    OgnelissListRow(
                        headline = stringResource(
                            R.string.stats_average,
                            stats.averageDurationSec / 60,
                            stats.averageDurationSec % 60
                        ),
                        supporting = stringResource(R.string.stats_average_label)
                    )
                }

                // Доля пропущенных от входящих — показатель, по которому
                // видно, успеваете ли вы отвечать
                if (stats.missedRatePercent > 0) {
                    RowDivider(Modifier.padding(start = Space.l))
                    OgnelissListRow(
                        headline = stringResource(
                            R.string.stats_missed_rate, stats.missedRatePercent
                        ),
                        supporting = stringResource(R.string.stats_missed_rate_label)
                    )
                }
                }
                }
            }

            item {
                // Полоса раздела (сплошной список, 1.5.3)
                SectionBand(stringResource(R.string.stats_top_contacts))
            }

            if (stats.topContacts.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.stats_empty),
                        Modifier.padding(FlowUi.contentPadding),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(stats.topContacts, key = { _, e -> e.key }) { index, entry ->
                GroupedRow(GroupPosition.of(index, stats.topContacts.size)) {
                OgnelissListRow(
                    headline = entry.displayName ?: PhoneFormatter.format(entry.number),
                    supporting = run {
                        val minutes = (entry.totalDurationSec / 60).toInt()
                        // Склонение выбирает Android: «1 вызов», «2 вызова»,
                        // «5 вызовов» — вручную это не собрать
                        pluralStringResource(R.plurals.call_count, entry.count, entry.count) +
                            " · " +
                            pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
                    },
                    leading = {
                        // Место в рейтинге понятнее любой иконки
                        Text(
                            "${stats.topContacts.indexOf(entry) + 1}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = { vm.call(entry.number) }
                )
                }
            }

            // Честная оговорка: без неё пользователь решит, что видит
            // статистику за всё время
            item {
                Text(
                    stringResource(R.string.stats_coverage, stats.coverageDays),
                    Modifier.padding(FlowUi.contentPadding),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(Space.xxl))
            }
        }
    }
}

@Composable
private fun StatCard(value: Int, labelRes: Int, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            // headlineSmall из темы вместо 22 sp числом: те же 22,
            // но при смене типографики поменяется вместе со всем
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = if (highlight && value > 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
