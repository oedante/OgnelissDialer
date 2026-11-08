package com.ogneliss.dialer.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
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
 * ── Вид — карточки (1.8.0) ──────────────────────────────────────────────────
 * Было: окно посередине с серыми плитками даты и времени. Стало — стопка
 * карточек, как у всех окон выбора (CardsOverlay, CardSheetParts.kt).
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
    onDismiss: () -> Unit,
    /**
     * Заголовок шапки (1.8.0): по умолчанию «Напомнить перезвонить»;
     * псевдозвонок передаёт своё название — окно то же, дело другое.
     */
    title: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector = OgnelissIcons.Alarm
) {
    val context = LocalContext.current

    // Прошедшее предзаполнение — сразу на завтра (2.1.0, разбор у CardButtons ниже)
    var target by remember(initialMillis) { mutableLongStateOf(notInPast(initialMillis)) }

    val dateFormat = remember { JavaDateFormat.getDateInstance(JavaDateFormat.FULL) }
    val timeFormat = remember { JavaDateFormat.getTimeInstance(JavaDateFormat.SHORT) }

    /*
     * 1.8.0 — КАРТОЧКАМИ, как выбор варианта перед ним и действия по долгому
     * нажатию (автор: «привести окна напоминаний и заметок к одному UI»).
     * Шапка — что и с кем; дата и время — строками, нажатие открывает
     * системный календарь или часы; «Сохранить» — заливкой справа внизу,
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
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = target }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                // Без сдвига на завтра: следом обычно выбирают время,
                                // и «сегодня в 18:00» не должно стать завтрашним
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
                )
                CardChoiceRow(
                    label = timeFormat.format(Date(target)),
                    trailing = null,
                    icon = OgnelissIcons.Schedule,
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = target }
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                target = notInPast(Calendar.getInstance().apply {
                                    timeInMillis = target
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis)
                            },
                            c.get(Calendar.HOUR_OF_DAY),
                            c.get(Calendar.MINUTE),
                            DateFormat.is24HourFormat(context)
                        ).show()
                    }
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
