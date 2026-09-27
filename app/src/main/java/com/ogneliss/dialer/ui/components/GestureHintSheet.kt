package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R

/**
 * ПОДСКАЗКА О ЖЕСТАХ ПРИ ПЕРВОМ ЗАПУСКЕ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОНА НУЖНА
 * ══════════════════════════════════════════════════════════════════════════
 * Два главных жеста приложения не видны на экране: свайп по строке
 * звонит или пишет, свайп по нижней панели переключает вызовы
 * и контакты.
 *
 * Догадаться о них нельзя — только наткнуться случайно. А без них
 * приложение работает как обычный журнал вызовов, и половина того,
 * ради чего оно сделано, остаётся неоткрытой.
 *
 * ── Почему шторка, а не полноэкранный тур ──────────────────────────────────
 * Тур из пяти экранов пролистывают не читая: он стоит между человеком
 * и приложением, которое он только что установил.
 *
 * Шторка занимает треть экрана, за ней виден настоящий журнал, и она
 * закрывается одним движением. Прочитают её не все — но те, кто
 * прочитает, сделают это осознанно.
 *
 * ── Почему показывается один раз ───────────────────────────────────────────
 * Повторное напоминание о том, что человек уже знает, раздражает
 * сильнее, чем помогает. Забывшие найдут те же сведения в настройках
 * жестов — там каждое действие названо и настраивается.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureHintSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.gesture_hint_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.gesture_hint_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            HintRow(
                icon = Icons.Default.Call,
                text = stringResource(R.string.gesture_hint_swipe_right)
            )
            HintRow(
                icon = Icons.AutoMirrored.Filled.Message,
                text = stringResource(R.string.gesture_hint_swipe_left)
            )
            HintRow(
                icon = Icons.Default.SwipeLeft,
                text = stringResource(R.string.gesture_hint_panel)
            )

            /*
             * Жест клавиатуры — четвёртым.
             *
             * Он менее нужен, чем остальные: клавиатуру можно открыть
             * и кнопкой. Но человек, попробовав свайп по панели,
             * закономерно попробует и вертикальный — лучше сразу
             * сказать, что он есть, чем оставить догадываться.
             */
            HintRow(
                icon = Icons.Default.SwipeUp,
                text = stringResource(R.string.gesture_hint_keyboard)
            )

            Spacer(Modifier.height(16.dp))

            // Оговорка про настройки: человек должен знать, что жесты
            // можно поменять или выключить — иначе непривычный свайп
            // читается как навязанный
            Text(
                stringResource(R.string.gesture_hint_settings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.gesture_hint_got_it))
            }
        }
    }
}

@Composable
private fun HintRow(icon: ImageVector, text: String) {
    Row(
        Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
