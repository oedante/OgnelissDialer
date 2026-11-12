package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.ScaledSheetContent
import com.ogneliss.dialer.ui.theme.FlowUi

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.*
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
        // Шторка — цвета страницы, группы на ней белые (строгий стиль, 1.5.1)
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        ScaledSheetContent {
            Column(Modifier.padding(horizontal = Space.xl).padding(bottom = Space.xl)) {
                Text(
                    stringResource(R.string.gesture_hint_title),
                    // Заголовок окна — titleMedium, как у всех окон и шторок (2.4.0)
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(Space.s))

                Text(
                    stringResource(R.string.gesture_hint_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(FlowUi.sheetPadding))

                HintRow(
                    icon = OgnelissIcons.Call,
                    text = stringResource(R.string.gesture_hint_swipe_right)
                )
                HintRow(
                    icon = OgnelissIcons.Message,
                    text = stringResource(R.string.gesture_hint_swipe_left)
                )
                HintRow(
                    icon = OgnelissIcons.SwipeLeft,
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
                    icon = OgnelissIcons.SwipeUp,
                    text = stringResource(R.string.gesture_hint_keyboard)
                )

                Spacer(Modifier.height(Space.l))

                // Оговорка про настройки: человек должен знать, что жесты
                // можно поменять или выключить — иначе непривычный свайп
                // читается как навязанный
                Text(
                    stringResource(R.string.gesture_hint_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.height(FlowUi.sheetPadding))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.gesture_hint_got_it))
                }
            }
        }
    }
}

@Composable
private fun HintRow(icon: ImageVector, text: String) {
    Row(
        Modifier.padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(Space.l))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
