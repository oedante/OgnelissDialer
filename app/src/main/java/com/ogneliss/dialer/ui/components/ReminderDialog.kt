package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
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
 * ВИД — КАРТОЧКИ (1.8.0)
 * ══════════════════════════════════════════════════════════════════════════
 * 1.2–1.7: окно посередине (сначала AlertDialog, потом OgnelissDialog)
 * с серыми плитками. С 1.8.0 — стопка карточек у места нажатия, как
 * действия по долгому нажатию (CardsOverlay, части — CardSheetParts.kt).
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
    /*
     * 1.8.0 — КАРТОЧКАМИ, КАК ДЕЙСТВИЯ ПО ДОЛГОМУ НАЖАТИЮ (автор: «привести
     * окна напоминаний и заметок к одному UI с остальным приложением»).
     * Было: окно посередине с серыми плитками-строками. Стало: шапка с синим
     * будильником и именем, под ней варианты строками без линий; время —
     * справа, той же колонкой. «Отмены» нет — касание мимо карточек и «Назад»
     * закрывают, как у действий.
     *
     * reminder_dialog_title, а не reminder_title: второй занят заголовком
     * УВЕДОМЛЕНИЯ («Перезвонить: Андрей») — здесь имя стоит своей строкой.
     */
    val now = remember { System.currentTimeMillis() }
    CardsOverlay(anchorY = LocalTouchAnchor.current?.screenY, onDismiss = onDismiss) { close ->
        ActionCard {
            CardHeader(
                icon = OgnelissIcons.Alarm,
                accent = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.reminder_dialog_title),
                subtitle = displayName
            )
        }
        ActionCard {
            Column(Modifier.padding(vertical = Space.s)) {
                PRESETS.forEach { (minutes, labelRes) ->
                    val at = now + minutes * 60_000L
                    // Точное время рядом с интервалом: «через два часа»
                    // и «в 17:40» человек сверяет с планами по-разному,
                    // и второе нагляднее
                    CardChoiceRow(
                        label = stringResource(labelRes),
                        trailing = formatTime(at),
                        onClick = { onPick(at); close() }
                    )
                }

                /*
                 * ЗАВТРА В ДЕВЯТЬ.
                 *
                 * Набор интервалов упирался в сутки: «через два часа» есть,
                 * «завтра» нет. А половина причин перезвонить — рабочие,
                 * и звонок в 21:40 «через два часа» бессмысленен: нужно утро.
                 * Девять часов — начало рабочего дня в большинстве мест;
                 * точное время можно поправить в следующем окне.
                 */
                val tomorrow = remember {
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                CardChoiceRow(
                    label = stringResource(R.string.reminder_tomorrow),
                    trailing = formatTime(tomorrow),
                    onClick = { onPick(tomorrow); close() }
                )

                // «Задать время…» — то же окно, что у вариантов, с ближайшим часом
                CardChoiceRow(
                    label = stringResource(R.string.reminder_custom),
                    trailing = null,
                    icon = OgnelissIcons.CalendarMonth,
                    onClick = { onPick(now + 60 * 60_000L); close() }
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
