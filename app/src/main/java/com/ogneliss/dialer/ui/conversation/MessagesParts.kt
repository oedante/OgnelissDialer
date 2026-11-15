package com.ogneliss.dialer.ui.conversation

import com.ogneliss.dialer.ui.components.EmptyStateLayout
import com.ogneliss.dialer.ui.components.EmptyState
import com.ogneliss.dialer.ui.components.EmptyReason
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.sms.SmsHealth
import com.ogneliss.dialer.sms.SmsRole
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space

/*
 * ЧАСТИ РАЗДЕЛА «SMS» (2.1.0) — вынесены из MessagesScreen при правке
 * (правило: большие функции экранов делим, выносим в *Parts.kt).
 */

/**
 * БЕЗ РОЛИ SMS (2.1.0) — РЕЖИМ СПУТНИКА.
 *
 * Было: абзац «чтобы видеть переписки… сделайте диалер приложением SMS
 * по умолчанию» и кнопка «Приложение SMS по умолчанию». Стало (правило
 * автора: человек не должен думать и выбирать — всё работает и так):
 * главная кнопка — «Открыть «Сообщения»» (SMS ведёт его приложение,
 * «Написать» по всему диалеру открывает переписку там), рядом тихая
 * «Вести SMS в Ogneliss» — роль по просьбе. Одна строка — только если
 * у приложения есть чаты RCS: взяв роль, их выключишь.
 */
@Composable
internal fun CompanionBlock(
    companion: SmsRole.CompanionApp?,
    onOpenCompanion: () -> Unit,
    onUseHere: () -> Unit
) {
    // Общий вид пустого экрана (2.7.0, EmptyStateLayout): был синий значок и кнопки по центру
    EmptyStateLayout(icon = OgnelissIcons.Textsms, title = null) {
        if (companion != null) {
            FilledTonalButton(onClick = onOpenCompanion) {
                Text(stringResource(R.string.messages_open_app, companion.label))
            }
            Spacer(Modifier.height(Space.s))
        }
        TextButton(onClick = onUseHere) { Text(stringResource(R.string.messages_use_here)) }
        if (companion?.rcs == true) {
            Text(
                stringResource(R.string.messages_rcs_note, companion.label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Пусто (2.1.0): значок и одна строка — как пустое состояние дизайн-системы,
 * но без пояснения и кнопки (правило автора: не объяснять).
 */
@Composable
internal fun MessagesEmpty(archive: Boolean) {
    // 2.7.0: общий EmptyState (был свой блок по центру экрана)
    EmptyState(EmptyReason.NoMessages(archive))
}

/**
 * Критичная беда приёма SMS (SmsHealth.Problem) — одной строкой с кнопкой
 * туда, где исправить. Без крестика: пока беда есть, SMS приходят молча
 * или не приходят; исправили — полоса пропадает сама при возвращении.
 */
@Composable
internal fun SmsProblemStrip(problem: SmsHealth.Problem, onFix: () -> Unit) {
    val text = when (problem) {
        SmsHealth.Problem.NOTIFICATIONS_OFF -> R.string.sms_notifications_off
        SmsHealth.Problem.AUTOSTART_OFF -> R.string.sms_autostart_off
    }
    val icon = when (problem) {
        SmsHealth.Problem.NOTIFICATIONS_OFF -> OgnelissIcons.NotificationsOff
        SmsHealth.Problem.AUTOSTART_OFF -> OgnelissIcons.Warning
    }
    Strip(
        text = stringResource(text),
        icon = icon,
        action = stringResource(R.string.confirm_enable),
        onAction = onFix,
        critical = true
    )
}

/**
 * «Разрешите автозапуск для SMS» — один раз, на прошивках, где автозапуск
 * не проверить (SmsHealth.autostartHintNeeded). Было (2.0.0): в переписке,
 * на всех таких прошивках, Xiaomi включительно.
 */
@Composable
internal fun AutostartHintStrip(onOpen: () -> Unit, onDismiss: () -> Unit) {
    Strip(
        text = stringResource(R.string.sms_autostart_hint),
        icon = OgnelissIcons.Info,
        action = stringResource(R.string.sms_autostart_action),
        onAction = onOpen,
        critical = false,
        onDismiss = onDismiss
    )
}

/** Полоса во всю ширину над списком: значок, строка, кнопка, крестик (по желанию). */
@Composable
internal fun Strip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: String,
    onAction: () -> Unit,
    critical: Boolean,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        color = if (critical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (critical) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        /*
         * Крупный шрифт (2.7.0, галерея): кнопка справа съедала половину ширины,
         * и текст без предела строк вставал столбиком в 7–8 строк по слову.
         * От 130 % кнопки уходят под текст, текст — во всю ширину полосы.
         */
        val stacked = LocalDensity.current.fontScale >= STRIP_STACK_FONT_SCALE
        val message: @Composable () -> Unit = {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                // Без предела строк (2.7.0). Было: maxLines = 2 с многоточием —
                // подсказка свайпов обрывалась на «…Доведите до …», а при шрифте
                // 160 % оставалось «Проведите по строке вправо …». Полоса
                // растёт по высоте; все её тексты короткие и важные
            )
        }
        val buttons: @Composable () -> Unit = {
            TextButton(onClick = onAction) { Text(action) }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(OgnelissIcons.Close, contentDescription = stringResource(R.string.nav_close))
                }
            }
        }
        val padding = Modifier.padding(
            start = Space.l, end = if (onDismiss == null) Space.s else Space.xs,
            top = Space.xs, bottom = Space.xs
        )
        if (stacked) {
            Column(Modifier.fillMaxWidth().then(padding)) {
                Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(STRIP_ICON))
                    Spacer(Modifier.width(Space.m))
                    Box(Modifier.weight(1f)) { message() }
                }
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) { buttons() }
            }
        } else {
            Row(padding, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(STRIP_ICON))
                Spacer(Modifier.width(Space.m))
                Box(Modifier.weight(1f)) { message() }
                buttons()
            }
        }
    }
}

/** С какого размера шрифта кнопки полосы — под текстом, а не справа. */
private const val STRIP_STACK_FONT_SCALE = 1.3f

/** Значок пустого состояния — 72 dp (дизайн-система, «Пустое состояние»). */

/** Значок в строке — 20 dp (дизайн-система, «Значки»). */
private val STRIP_ICON = 20.dp
