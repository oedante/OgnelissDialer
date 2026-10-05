package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.FlowUi
import java.text.DateFormat as JavaDateFormat
import java.util.Calendar
import java.util.Date

/**
 * ВЫБОР ДАТЫ И ВРЕМЕНИ НАПОМИНАНИЯ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОКНО, ЕСЛИ ВАРИАНТ УЖЕ ВЫБРАН
 * ══════════════════════════════════════════════════════════════════════════
 * Раньше нажатие на «через час» ставило напоминание немедленно.
 * Быстро, но неисправимо: промахнулся по строке — напоминание уже
 * стоит, и снять его можно было только из карточки.
 *
 * И главное, набор вариантов ограничивал сутками: «через два часа»
 * есть, «в четверг» нет. Человеку, которому нужно перезвонить
 * послезавтра, приложение отвечало «такого не бывает».
 *
 * Теперь вариант — это предзаполнение, а не действие. «Через час»
 * подставляет время через час, «завтра» — девять утра следующего дня,
 * и то и другое можно поправить до сохранения. Лишнее нажатие
 * окупается тем, что промах виден до, а не после.
 *
 * ── Почему системные окна выбора, а не свои ────────────────────────────────
 * Календарь и часы Material 3 занимают почти весь экран и требуют
 * согласия на экспериментальный API. Системные окна человек уже видел
 * в будильнике и календаре, они учитывают 12- или 24-часовой формат
 * и работают с чтением с экрана без нашей помощи.
 */
@Composable
fun ReminderTimeDialog(
    displayName: String,
    /** Предзаполнение: время из выбранного варианта или существующего напоминания. */
    initialMillis: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var target by remember(initialMillis) { mutableLongStateOf(initialMillis) }

    val dateFormat = remember { JavaDateFormat.getDateInstance(JavaDateFormat.FULL) }
    val timeFormat = remember { JavaDateFormat.getTimeInstance(JavaDateFormat.SHORT) }

    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.reminder_dialog_title),
        content = {
            Text(
                displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PickerRow(
                icon = OgnelissIcons.CalendarMonth,
                value = dateFormat.format(Date(target))
            ) {
                val c = Calendar.getInstance().apply { timeInMillis = target }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        target = Calendar.getInstance().apply {
                            timeInMillis = target
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                        }.timeInMillis
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    // Прошедшую дату выбрать нельзя: напоминание в прошлом
                    // не сработает никогда, а понять это по молчанию телефона
                    // невозможно
                    datePicker.minDate = System.currentTimeMillis()
                }.show()
            }

            PickerRow(
                icon = OgnelissIcons.Schedule,
                value = timeFormat.format(Date(target))
            ) {
                val c = Calendar.getInstance().apply { timeInMillis = target }
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        target = Calendar.getInstance().apply {
                            timeInMillis = target
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    },
                    c.get(Calendar.HOUR_OF_DAY),
                    c.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context)
                ).show()
            }

            /*
             * Предупреждение о прошедшем времени.
             *
             * Дату в прошлое выбрать нельзя, а время — можно: сегодняшнее
             * утро при выборе «сегодня». Молча отказать нельзя, молча
             * поставить тоже: напоминание не сработает.
             */
            if (target <= System.currentTimeMillis()) {
                Text(
                    stringResource(R.string.reminder_in_past),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(target) },
                enabled = target > System.currentTimeMillis()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nav_cancel))
            }
        }
    )
}

/** Строка «значок — значение», нажимается целиком. */
@Composable
private fun PickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
    ) {
        Row(
            modifier = Modifier.padding(FlowUi.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
