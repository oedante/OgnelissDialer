package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.*
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
 * Назначение: сказать, почему список пуст, — значком и коротким
 * заголовком.
 *
 * ── 2.1.0: без пояснений ───────────────────────────────────────────────────
 * Правило автора 07.11.2026 (дизайн-система, «Пустое состояние»): значок
 * и короткий заголовок; без пояснений; кнопка — только если нужна для
 * работы. Было: под заголовком — абзац («Звонки появятся здесь»,
 * «Попробуйте меньше цифр…», «Без доступа к контактам…»), у поиска —
 * кнопка «Сбросить поиск». Стало: абзацев нет, кнопка осталась одна —
 * «Разрешить», когда нет доступа к журналу или контактам: без неё
 * приложение не работает. Запрос разрешения — системным окном
 * (rememberPermissionRequest у экранов).
 *
 * ── Почему это не мелочь ───────────────────────────────────────────────────
 * Пустой белый экран пользователь трактует как поломку. Особенно
 * при первом запуске: он только что выдал приложению доступ к журналу
 * вызовов, увидел пустоту — и первый вывод будет «разрешения не сработали».
 *
 * Причин пустоты несколько, и заголовки у них РАЗНЫЕ:
 *  • журнал действительно пуст — «звонков пока нет»
 *  • нет разрешения — заголовок и кнопка выдачи
 *  • поиск ничего не нашёл — «ничего не найдено»
 *
 * Одна общая заглушка «ничего нет» на все случаи оставляет пользователя
 * в тупике: без доступа к журналу он решил бы, что звонков нет.
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
    val actionLabel = actionFor(reason)?.let { stringResource(it) }

    if (compact) {
        // Одна строка: в ландшафте и на узкой панели места под иллюстрацию нет
        Row(
            modifier.fillMaxWidth().padding(Space.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(Space.m))
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
            .padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon, null,
            Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(Space.l))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun iconFor(reason: EmptyReason): ImageVector = when (reason) {
    EmptyReason.NoCalls -> OgnelissIcons.PhoneDisabled
    EmptyReason.NoCallLogPermission, EmptyReason.NoContactsPermission -> OgnelissIcons.Lock
    is EmptyReason.NoSearchResults -> OgnelissIcons.SearchOff
    EmptyReason.NoContacts -> OgnelissIcons.PersonOff
    EmptyReason.NoFavourites -> OgnelissIcons.StarBorder
    EmptyReason.NoNotes -> OgnelissIcons.StickyNote2
    EmptyReason.NoReminders -> OgnelissIcons.Alarm
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

/**
 * Кнопка — только если без неё приложение не работает: выдать доступ.
 * «Сбросить поиск» снята в 2.1.0 — у поля поиска свой крестик.
 */
private fun actionFor(reason: EmptyReason): Int? = when (reason) {
    EmptyReason.NoCallLogPermission, EmptyReason.NoContactsPermission ->
        R.string.onboarding_grant
    else -> null
}
