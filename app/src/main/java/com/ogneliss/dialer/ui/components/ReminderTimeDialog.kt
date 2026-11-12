package com.ogneliss.dialer.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
import java.text.DateFormat as JavaDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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
 * ── Вид — карточки (1.8.0) ──────────────────────────────────────────────────
 * Было: окно посередине с серыми плитками даты и времени. Стало — стопка
 * карточек, как у всех окон выбора (CardsOverlay, CardSheetParts.kt).
 *
 * ── Календарь и часы — окна приложения (2.4.0) ─────────────────────────────
 * Было: системные окна Android — «их человек уже видел в будильнике».
 * Но они живут по теме системы, а не приложения (тёмное приложение —
 * светлый календарь), со своим акцентом, и масштаб приложения на них
 * не действует вовсе: это не Compose. Автор, 12.11.2026: «не на все
 * действует масштаб», «как будто приложение сшито из разных кусков».
 * Стало: календарь и часы Material в нашем окне (PickerDialog) — тема,
 * акцент и масштаб как везде. 12- или 24-часовой формат — по системе,
 * как и было; чтение с экрана у них своё, Material.
 */
@Composable
fun ReminderTimeDialog(
    displayName: String,
    /** Предзаполнение: время из выбранного варианта или существующего напоминания. */
    initialMillis: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Заголовок шапки (1.8.0): по умолчанию «Напомнить перезвонить»;
     * псевдозвонок передаёт своё название — окно то же, дело другое.
     */
    title: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector = OgnelissIcons.Alarm
) {
    // Прошедшее предзаполнение — сразу на завтра (2.1.0, разбор у CardButtons ниже)
    var target by remember(initialMillis) { mutableLongStateOf(notInPast(initialMillis)) }

    // Открытое окно выбора: календарь или часы; null — ни одного
    var picking by remember { mutableStateOf<Picking?>(null) }

    val dateFormat = remember { JavaDateFormat.getDateInstance(JavaDateFormat.FULL) }
    val timeFormat = remember { JavaDateFormat.getTimeInstance(JavaDateFormat.SHORT) }

    /*
     * 1.8.0 — КАРТОЧКАМИ, как выбор варианта перед ним и действия по долгому
     * нажатию (автор: «привести окна напоминаний и заметок к одному UI»).
     * Шапка — что и с кем; дата и время — строками, нажатие открывает
     * календарь или часы (2.4.0 — окна приложения, PickerDialog); «Сохранить» — заливкой справа внизу,
     * у большого пальца.
     */
    CardsOverlay(anchorY = LocalTouchAnchor.current?.screenY, onDismiss = onDismiss) { close ->
        ActionCard {
            CardHeader(
                icon = icon,
                accent = MaterialTheme.colorScheme.primary,
                title = title ?: stringResource(R.string.reminder_dialog_title),
                subtitle = displayName
            )
        }
        ActionCard {
            Column(Modifier.padding(top = Space.s)) {
                CardChoiceRow(
                    label = dateFormat.format(Date(target)),
                    trailing = null,
                    icon = OgnelissIcons.CalendarMonth,
                    onClick = { picking = Picking.DATE }
                )
                CardChoiceRow(
                    label = timeFormat.format(Date(target)),
                    trailing = null,
                    icon = OgnelissIcons.Schedule,
                    onClick = { picking = Picking.TIME }
                )

                /*
                 * Прошедшее время. Дату в прошлое выбрать нельзя, а время —
                 * можно: сегодняшнее утро при выборе «сегодня».
                 *
                 * Было: красное «Это время уже прошло» и неактивная
                 * «Сохранить». Стало (2.1.0, правило автора 07.11.2026
                 * «выполнить, а не объяснять»): то же время завтра — после
                 * выбора времени дата в строке выше меняется сама
                 * (notInPast). Окно могло простоять открытым, пока время
                 * прошло, а дату могли выбрать после времени, — поэтому
                 * проверка и при сохранении.
                 */
                CardButtons(
                    confirmLabel = stringResource(R.string.action_save),
                    onConfirm = { onSave(notInPast(target)); close() },
                    dismissLabel = stringResource(R.string.nav_cancel),
                    onDismiss = close
                )
            }
        }
    }
    when (picking) {
        Picking.DATE -> ReminderDatePicker(
            target = target,
            onPick = { target = it; picking = null },
            onDismiss = { picking = null }
        )
        Picking.TIME -> ReminderTimePicker(
            target = target,
            onPick = { target = it; picking = null },
            onDismiss = { picking = null }
        )
        null -> Unit
    }
}

/** Какое окно выбора открыто поверх карточек. */
private enum class Picking { DATE, TIME }

/**
 * Календарь (2.4.0). Календарь Material считает в UTC: день из него —
 * по UTC, время суток остаётся прежним, местным. Прошедшие дни
 * не выбираются: напоминание в прошлом не сработает никогда.
 * Без сдвига на завтра: следом обычно выбирают время, и «сегодня
 * в 18:00» не должно стать завтрашним.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDatePicker(target: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = remember { LocalDate.now(zone) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = Instant.ofEpochMilli(target).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            }
        }
    )
    PickerDialog(
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.contact_edit_pick),
        onConfirm = {
            val millis = state.selectedDateMillis
            if (millis == null) onDismiss() else {
                val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                onPick(
                    Calendar.getInstance().apply {
                        timeInMillis = target
                        set(Calendar.YEAR, day.year)
                        set(Calendar.MONTH, day.monthValue - 1)
                        set(Calendar.DAY_OF_MONTH, day.dayOfMonth)
                    }.timeInMillis
                )
            }
        },
        dismissLabel = stringResource(R.string.nav_cancel)
    ) {
        DatePicker(state = state, title = null)
    }
}

/**
 * Часы (2.4.0): 12- или 24-часовой формат — как в системе. Прошедшее
 * время сегодня — то же время завтра (notInPast), как и было.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePicker(target: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val start = remember(target) { Calendar.getInstance().apply { timeInMillis = target } }
    val state = rememberTimePickerState(
        initialHour = start.get(Calendar.HOUR_OF_DAY),
        initialMinute = start.get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(context)
    )
    PickerDialog(
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.contact_edit_pick),
        onConfirm = {
            onPick(
                notInPast(
                    Calendar.getInstance().apply {
                        timeInMillis = target
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                )
            )
        },
        dismissLabel = stringResource(R.string.nav_cancel)
    ) {
        // Часы уже календаря: по краям — воздух, как в окне часов Material
        TimePicker(state = state, modifier = Modifier.padding(start = Space.l, end = Space.l, top = Space.l))
    }
}

/**
 * Время напоминания не в прошлом (2.1.0): прошедшее — тот же час
 * и минута на следующий день (через Calendar: переход на летнее время
 * не сдвигает час).
 */
private fun notInPast(millis: Long, now: Long = System.currentTimeMillis()): Long {
    if (millis > now) return millis
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    while (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
    return c.timeInMillis
}
