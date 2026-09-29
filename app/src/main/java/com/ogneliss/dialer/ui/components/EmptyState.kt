package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R

/**
 * ПУСТЫЕ СОСТОЯНИЯ.
 *
 * Назначение: объяснить, почему список пуст и что делать дальше.
 *
 * ── Почему это не мелочь ───────────────────────────────────────────────────
 * Пустой белый экран пользователь трактует как поломку. Особенно
 * при первом запуске: он только что выдал приложению доступ к журналу
 * вызовов, увидел пустоту — и первый вывод будет «разрешения не сработали».
 *
 * Причин пустоты несколько, и они требуют РАЗНЫХ подсказок:
 *  • журнал действительно пуст — «звонков пока нет»
 *  • нет разрешения — нужна кнопка выдачи, а не совет позвонить
 *  • поиск ничего не нашёл — предложить сбросить запрос
 *  • всё отфильтровано — сказать, что фильтр активен
 *
 * Одна общая заглушка «ничего нет» на все случаи оставляет пользователя
 * в тупике в трёх случаях из четырёх.
 *
 * ── Компактный вариант ─────────────────────────────────────────────────────
 * В ландшафте и на разделённом экране высоты под иллюстрацию нет.
 * Поэтому есть сокращённая версия в одну строку.
 */

sealed interface EmptyReason {
    /** Журнал вызовов пуст — первый запуск или всё удалено. */
    data object NoCalls : EmptyReason

    /** Нет разрешения на чтение журнала. */
    data object NoCallLogPermission : EmptyReason

    /** Нет разрешения на чтение контактов. */
    data object NoContactsPermission : EmptyReason

    /** Поиск не дал результатов. */
    data class NoSearchResults(val query: String) : EmptyReason

    /** Контактов нет вовсе. */
    data object NoContacts : EmptyReason

    /** Нет избранных. */
    data object NoFavourites : EmptyReason

    /** Заметок нет ни к одному номеру. */
    data object NoNotes : EmptyReason

    /** Напоминаний не назначено. */
    data object NoReminders : EmptyReason
}

@Composable
fun EmptyState(
    reason: EmptyReason,
    compact: Boolean = false,
    onAction: (() -> Unit)? = null,
    /**
     * Внешние отступы — обычно отступы каркаса экрана.
     *
     * Заметки и напоминания рисовали пустое состояние без них, и значок
     * с заголовком «Заметок пока нет» частично уходил под строку
     * заголовка экрана — видно на снимках из тестирования 1.1.3.
     */
    modifier: Modifier = Modifier
) {
    val icon = iconFor(reason)
    val title = stringResource(titleFor(reason))
    val hint = hintFor(reason)?.let { stringResource(it) }
    val actionLabel = actionFor(reason)?.let { stringResource(it) }

    if (compact) {
        // Одна строка: в ландшафте и на узкой панели места под иллюстрацию нет
        Row(
            modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    /*
     * Появление проявлением, а не мгновенно.
     *
     * Пустое состояние возникает в двух случаях: список дочитан
     * до конца поиска и ничего не нашлось, либо данные ещё грузятся
     * и оказались пустыми. Во втором случае мгновенно возникшая
     * надпись «звонков нет» на долю секунды выглядит как приговор,
     * даже если через кадр список наполнится.
     *
     * 220 мс проявления делают эту долю секунды незаметной: пока
     * надпись набирает плотность, данные обычно уже приходят,
     * и человек не успевает прочитать ложное сообщение.
     */
    val alpha by animateFloatAsState(1f, tween(Motion.QUICK_MS), label = "emptyState")

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon, null,
            Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun iconFor(reason: EmptyReason): ImageVector = when (reason) {
    EmptyReason.NoCalls -> Icons.Default.PhoneDisabled
    EmptyReason.NoCallLogPermission, EmptyReason.NoContactsPermission -> Icons.Default.Lock
    is EmptyReason.NoSearchResults -> Icons.Default.SearchOff
    EmptyReason.NoContacts -> Icons.Default.PersonOff
    EmptyReason.NoFavourites -> Icons.Default.StarBorder
    EmptyReason.NoNotes -> Icons.AutoMirrored.Filled.StickyNote2
    EmptyReason.NoReminders -> Icons.Default.Alarm
}

private fun titleFor(reason: EmptyReason): Int = when (reason) {
    EmptyReason.NoCalls -> R.string.empty_no_calls
    EmptyReason.NoCallLogPermission -> R.string.empty_no_calllog_permission
    EmptyReason.NoContactsPermission -> R.string.empty_no_contacts_permission
    is EmptyReason.NoSearchResults -> R.string.empty_no_results
    EmptyReason.NoContacts -> R.string.empty_no_contacts
    EmptyReason.NoFavourites -> R.string.empty_no_favourites
    EmptyReason.NoNotes -> R.string.empty_no_notes
    EmptyReason.NoReminders -> R.string.empty_no_reminders
}

private fun hintFor(reason: EmptyReason): Int? = when (reason) {
    EmptyReason.NoCalls -> R.string.empty_no_calls_hint
    EmptyReason.NoCallLogPermission -> R.string.empty_no_calllog_permission_hint
    EmptyReason.NoContactsPermission -> R.string.empty_no_contacts_permission_hint
    is EmptyReason.NoSearchResults -> R.string.empty_no_results_hint
    EmptyReason.NoContacts -> R.string.empty_no_contacts_hint
    EmptyReason.NoFavourites -> R.string.empty_no_favourites_hint
    EmptyReason.NoNotes -> R.string.empty_no_notes_hint
    EmptyReason.NoReminders -> R.string.empty_no_reminders_hint
}

/** Кнопка нужна только там, где пользователь реально может что-то исправить. */
private fun actionFor(reason: EmptyReason): Int? = when (reason) {
    EmptyReason.NoCallLogPermission, EmptyReason.NoContactsPermission ->
        R.string.onboarding_grant
    is EmptyReason.NoSearchResults -> R.string.empty_clear_search
    else -> null
}
