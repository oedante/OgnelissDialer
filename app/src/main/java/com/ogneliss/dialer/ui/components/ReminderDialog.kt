package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.FlowUi
import java.util.Calendar

/**
 * ВЫБОР ВРЕМЕНИ НАПОМИНАНИЯ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ВЫБОР, А НЕ ФИКСИРОВАННЫЙ ЧАС
 * ══════════════════════════════════════════════════════════════════════════
 * Раньше напоминание ставилось молча на час вперёд. Две беды сразу:
 * человек не знал, сработало ли вообще, и не мог задать нужный срок.
 *
 * А срок зависит от дела. «Перезвонить, когда освободится» — это пять
 * минут. «Позвонить в банк» — это завтра в рабочее время. Один час
 * не подходит ни тому, ни другому.
 *
 * ── Почему именно такие интервалы ──────────────────────────────────────────
 * 5 и 15 минут — «сейчас занят, освобожусь». 30 минут и час — «доделаю
 * дело». Два часа — «после обеда». Дальше начинается «завтра», и для
 * него нужен выбор времени, а не интервал: «через 18 часов» никто
 * не мыслит.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ КОНТЕЙНЕР ОБЩИЙ, А НЕ AlertDialog
 * ══════════════════════════════════════════════════════════════════════════
 * Здесь шесть строк выбора. С отступами AlertDialog (24 dp по краям)
 * и ListItem (16 внутри каждой строки) список занимал почти весь экран,
 * а на мелких телефонах уезжал под кнопку «Отмена».
 *
 * Строки теперь такие же карточки, как в журнале, и нажимается
 * карточка целиком. Значок часов из шапки убран: он ничего
 * не добавлял к заголовку, но занимал строку в окне, которое
 * и так длинное.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Composable
fun ReminderDialog(
    displayName: String,
    /**
     * Выбран вариант.
     *
     * Передаётся ВРЕМЯ СРАБАТЫВАНИЯ, а не задержка: у варианта «завтра»
     * задержки в минутах нет — она зависит от того, который сейчас час.
     *
     * Напоминание при этом ещё не ставится: экран показывает окно
     * выбора даты и времени с этим значением, и человек может
     * его поправить.
     */
    onPick: (atMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    OgnelissDialog(
        onDismissRequest = onDismiss,
        /*
         * reminder_dialog_title, а не reminder_title.
         *
         * Второй уже занят заголовком УВЕДОМЛЕНИЯ о напоминании,
         * и у него есть аргумент с именем абонента: «Перезвонить: Андрей».
         * Здесь имя показано отдельной строкой ниже, и подставлять его
         * в заголовок незачем.
         */
        title = stringResource(R.string.reminder_dialog_title),
        content = {
            Text(
                displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PRESETS.forEach { (minutes, labelRes) ->
                val at = System.currentTimeMillis() + minutes * 60_000L
                ReminderRow(
                    label = stringResource(labelRes),
                    // Точное время рядом с интервалом: «через два часа»
                    // и «в 17:40» человек сверяет с планами по-разному,
                    // и второе нагляднее
                    trailing = formatTime(at),
                    onClick = { onPick(at) }
                )
            }

            /*
             * ЗАВТРА В ДЕВЯТЬ.
             *
             * Набор интервалов упирался в сутки: «через два часа» есть,
             * «завтра» нет. А половина причин перезвонить — рабочие,
             * и звонок в 21:40 «через два часа» бессмысленен: нужно утро.
             *
             * Девять часов — начало рабочего дня в большинстве мест.
             * Точное время всё равно можно поправить в следующем окне.
             */
            val tomorrow = remember {
                java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 9)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            ReminderRow(
                label = stringResource(R.string.reminder_tomorrow),
                trailing = formatTime(tomorrow),
                onClick = { onPick(tomorrow) }
            )

            /*
             * «Другое время» ведёт в то же окно, что и остальные варианты,
             * только предзаполненное ближайшим часом. Отдельного пути
             * для него больше нет: любой вариант теперь можно поправить.
             */
            ReminderRow(
                label = stringResource(R.string.reminder_custom),
                trailing = null,
                onClick = { onPick(System.currentTimeMillis() + 60 * 60_000L) }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nav_cancel))
            }
        }
    )
}

/**
 * Строка выбора.
 *
 * Время справа, а не второй строкой снизу: так шесть строк занимают
 * шесть высот, а не двенадцать, и колонка времени читается сверху вниз
 * одним взглядом.
 */
@Composable
private fun ReminderRow(label: String, trailing: String?, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(FlowUi.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (trailing != null) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Интервалы напоминания.
 *
 * Порядок от короткого к длинному: чаще нужен ближайший, и он должен
 * быть первым под пальцем.
 */
private val PRESETS = listOf(
    5L to R.string.reminder_5min,
    15L to R.string.reminder_15min,
    30L to R.string.reminder_30min,
    60L to R.string.reminder_1hour,
    120L to R.string.reminder_2hours
)

/** Во сколько сработает напоминание. */
private fun formatTime(atMillis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = atMillis }
    return String.format(
        java.util.Locale.getDefault(),
        "%02d:%02d",
        c.get(Calendar.HOUR_OF_DAY),
        c.get(Calendar.MINUTE)
    )
}
