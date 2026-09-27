package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.components.OgnelissPanel
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.SwipeTuning
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.components.swipe.SwipeRevealRow
import com.ogneliss.dialer.ui.components.swipe.rememberSwipeRevealController

/**
 * НАСТРОЙКА ЧУВСТВИТЕЛЬНОСТИ СВАЙПА.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ГЛАВНОЕ ЗДЕСЬ — ПРОБНАЯ СТРОКА
 * ══════════════════════════════════════════════════════════════════════════
 * Подобрать пороги по цифрам невозможно: «140 dp» ничего не говорит
 * о том, как жест ощущается пальцем. Без возможности попробовать прямо
 * на месте настройка превращается в угадывание, а результат проверяется
 * только выходом на главный экран и обратно — по три раза на каждое
 * значение.
 *
 * Поэтому вверху стоит настоящая строка со свайпом, работающая
 * с текущими значениями ползунков. Подвинул — сразу попробовал.
 *
 * ── Почему параметров четыре, а не один «уровень чувствительности» ─────────
 * Одна шкала «слабее — сильнее» выглядела бы проще, но за ней стоят
 * три независимые величины, и людям мешают разные из них. Кому-то
 * жест срабатывает случайно при прокрутке — ему нужна скорость;
 * кому-то приходится тянуть слишком далеко — ему нужно расстояние.
 * Свести это в один ползунок значит лишить половину пользователей
 * возможности починить именно свою проблему.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Composable
fun SwipeTuningScreen(
    vm: DialerViewModel,
    onBack: () -> Unit
) {
    val saved by vm.swipeTuning.collectAsState()

    /*
     * Черновик правок.
     *
     * Ползунки двигают локальную копию, а сохранение идёт с задержкой:
     * запись в хранилище на каждое движение пальца означала бы десятки
     * обращений к диску за секунду.
     */
    var draft by remember(saved) { mutableStateOf(saved) }

    LaunchedEffect(draft) {
        if (draft != saved) {
            kotlinx.coroutines.delay(SAVE_DELAY_MS)
            vm.setSwipeTuning(draft)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.settings_swipe_tuning),
        onBack = onBack,
        // Экран настроек: заголовок закреплён
        pinnedTopBar = true,
        actions = {
            // Сброс доступен только когда есть что сбрасывать: активная
            // кнопка, которая ничего не делает, сбивает с толку
            if (!draft.isDefault) {
                IconButton(onClick = {
                    draft = SwipeTuning.DEFAULT
                    vm.resetSwipeTuning()
                }) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = stringResource(R.string.swipe_tuning_reset)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            PreviewRow(draft)

            Spacer(Modifier.height(8.dp))

            TuningSlider(
                title = stringResource(R.string.swipe_trigger_title),
                description = stringResource(R.string.swipe_trigger_text),
                value = draft.triggerDistanceDp,
                range = SwipeTuning.TRIGGER_RANGE,
                unit = stringResource(R.string.unit_dp),
                onChange = { draft = draft.copy(triggerDistanceDp = it) }
            )

            TuningSlider(
                title = stringResource(R.string.swipe_positional_title),
                description = stringResource(R.string.swipe_positional_text),
                value = draft.positionalThresholdPercent,
                range = SwipeTuning.POSITIONAL_RANGE,
                unit = "%",
                onChange = { draft = draft.copy(positionalThresholdPercent = it) }
            )

            TuningSlider(
                title = stringResource(R.string.swipe_velocity_title),
                description = stringResource(R.string.swipe_velocity_text),
                value = draft.velocityDpPerSecond,
                range = SwipeTuning.VELOCITY_RANGE,
                unit = stringResource(R.string.unit_dp_per_second),
                step = 25,
                onChange = { draft = draft.copy(velocityDpPerSecond = it) }
            )

            OgnelissListRow(
                headline = stringResource(R.string.swipe_horizontal_title),
                supporting = stringResource(R.string.swipe_horizontal_text),
                trailing = {
                    Switch(
                        checked = draft.requireHorizontalStart,
                        onCheckedChange = { draft = draft.copy(requireHorizontalStart = it) }
                    )
                }
            )

            // Подсказка о том, что настройки сохраняются сами: без неё
            // возникает вопрос «а где кнопка Сохранить»
            Text(
                stringResource(R.string.swipe_tuning_autosave),
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Пробная строка.
 *
 * Настоящий компонент свайпа с текущими значениями — не имитация.
 * Имитация вела бы себя иначе, и подобранные на ней значения
 * не совпали бы с поведением в журнале.
 */
@Composable
private fun PreviewRow(tuning: SwipeTuning) {
    val controller = rememberSwipeRevealController()
    var lastAction by remember { mutableStateOf<String?>(null) }

    val callLabel = stringResource(R.string.action_call)
    val smsLabel = stringResource(R.string.action_sms)

    Column(Modifier.padding(FlowUi.contentPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TouchApp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.swipe_tuning_try),
                style = MaterialTheme.typography.titleSmall
            )
        }

        Spacer(Modifier.height(8.dp))

        OgnelissPanel {
            SwipeRevealRow(
                key = "preview",
                controller = controller,
                tuning = tuning,
                startActions = emptyList(),
                endActions = emptyList(),
                onDirectStart = { lastAction = callLabel },
                onDirectEnd = { lastAction = smsLabel }
            ) {
                OgnelissListRow(
                    headline = stringResource(R.string.swipe_tuning_sample_name),
                    supporting = stringResource(R.string.swipe_tuning_sample_hint),
                    leading = { Icon(Icons.Default.Person, contentDescription = null) }
                )
            }
        }

        // Что сработало — иначе непонятно, засчитался жест или нет
        Spacer(Modifier.height(8.dp))
        Text(
            lastAction?.let { stringResource(R.string.swipe_tuning_fired, it) }
                ?: stringResource(R.string.swipe_tuning_nothing),
            style = MaterialTheme.typography.bodySmall,
            color = if (lastAction != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun TuningSlider(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    unit: String,
    step: Int = 5,
    onChange: (Int) -> Unit
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            // Значение показано числом: по положению ползунка вернуться
            // к прежней настройке невозможно
            Text(
                "$value $unit",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step) - 1
        )

        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Пауза перед сохранением: запись на каждое движение пальца избыточна. */
private const val SAVE_DELAY_MS = 400L
