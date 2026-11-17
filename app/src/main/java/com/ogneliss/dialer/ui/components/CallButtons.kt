package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.CallGreen
import androidx.compose.ui.graphics.Color
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SimCallPlan
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.ui.theme.LocalAppearance
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * КНОПКИ ВЫЗОВА.
 *
 * ── Одна SIM: один круг по центру ──────────────────────────────────────────
 * Главное действие экрана: крупный зелёный круг под клавиатурой (2.7.5;
 * до того — широкая пилюля во всю ширину).
 *
 * ── Две SIM: два круга рядом ───────────────────────────────────────────────
 * Не одна кнопка с диалогом выбора после нажатия: это лишний шаг
 * на самом частом действии. Две кнопки убирают его целиком.
 *
 * Каждая подписана номером карты и её именем, если пользователь его задал.
 * Без подписи две одинаковые кнопки заставляли бы гадать, какая какая.
 *
 * ── Почему кнопки, а не сегментированный переключатель ─────────────────────
 * Переключатель подразумевает выбор режима, а не действие. Здесь каждое
 * нажатие — немедленный звонок, и форма должна об этом говорить.
 */
@Composable
fun CallButtons(
    /** Карты, с которых МОЖНО звонить: уже отфильтрованы по голосовым. */
    sims: List<SimSlot>,
    enabled: Boolean,
    onCall: (slotIndex: Int) -> Unit
) {
    /*
     * Раскладка определяется планом, а не размером списка.
     *
     * «Карт больше одной → две кнопки» ошибается в трёх реальных случаях:
     * два слота при одной карте, карта отключена в настройках,
     * карта только для интернета. Подробности — в SimCallPlan.
     */
    val layout = SimCallPlan.decide(sims, sims.map { it.subscriptionId }.toSet())

    /*
     * ЗЕЛЁНЫЕ, А НЕ СИНИЕ (1.3.5, 06.10.2026). Кнопки вызова брали цвета
     * Button по умолчанию — primary (синий), хотя по дизайн-системе зелёный
     * только у звонка (CallGreen), как в журнале и карточке. Пустое поле —
     * тональный цвет панели (surfaceContainerHighest) вместо грязно-серого
     * «отключено» по умолчанию: кнопка видна, но ясно, что номера нет.
     */
    /*
     * Звонить нельзя (2.1.0: нет SIM, режим полёта) — кнопка серая, но
     * нажимается: экстренный номер наберётся и так (разбор в CallAvailability).
     */
    val canCall = com.ogneliss.dialer.util.LocalCallAvailability.current.canCall
    val callColors = if (!canCall) IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContentColor = MaterialTheme.colorScheme.outline
    ) else IconButtonDefaults.filledIconButtonColors(
        containerColor = CallGreen,
        contentColor = Color.White,
        /*
         * Номер не набран (1.3.6, 07.10.2026, автор: клавиатура «из другого
         * приложения»). Было: системный серый «недоступно» — такого серого
         * больше нигде нет. Стало: тоновая зелёная — светлый фон (16%)
         * и зелёный значок: видно, что это кнопка вызова, просто
         * пока не активна. Белый значок на бледно-зелёном не читался бы.
         */
        disabledContainerColor = CallGreen.copy(alpha = 0.16f),
        disabledContentColor = CallGreen.copy(alpha = 0.75f)
    )

    /*
     * КРУГЛЫЕ КНОПКИ, ПОДПИСЬ КАРТЫ ПОД НИМИ (2.7.5, классическая клавиатура —
     * вариант «А», выбранный автором). Было: широкие пилюли во всю ширину
     * с «SIM 1» внутри. Круги стоят равномерно по ширине ряда; подпись —
     * номер слота и имя карты, если его задали (FitText: лёжа имя
     * не обрезается до «Beeline2» / «Beeline0» — урок 1.3.2).
     */
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        if (layout is SimCallPlan.Layout.Single) {
            val label = stringResource(R.string.action_call)
            CallCircle(
                enabled = enabled,
                colors = callColors,
                description = label,
                onClick = { onCall(-1) }
            )
        } else {
            (layout as SimCallPlan.Layout.PerSim).sims.forEach { sim ->
                val label = stringResource(R.string.a11y_call_with_sim_only, sim.label)
                val slot = "SIM ${sim.slotIndex + 1}"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = callCircleSize() + CAPTION_EXTRA)
                ) {
                    CallCircle(
                        enabled = enabled,
                        colors = callColors,
                        description = label,
                        // Передаём индекс СЛОТА, а не позицию в списке:
                        // при одной активной карте во втором слоте позиция 0,
                        // а слот 1 — и звонок ушёл бы не с той карты
                        onClick = { onCall(sim.slotIndex) }
                    )
                    Spacer(Modifier.height(Space.xxs))
                    // Имя карты рядом с номером: если пользователь подписал
                    // слоты, выбор становится осмысленным, а не «первая или вторая»
                    val named = sim.label.isNotBlank() && sim.label != slot
                    FitText(
                        if (named) "$slot · ${sim.label}" else slot,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        minScale = 0.7f
                    )
                }
            }
        }
    }
}

/** Круг вызова: значок трубки, белый на зелёном. */
@Composable
private fun CallCircle(
    enabled: Boolean,
    colors: IconButtonColors,
    description: String,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = colors,
        modifier = Modifier
            .size(callCircleSize())
            .semantics { contentDescription = description }
    ) {
        Icon(OgnelissIcons.Call, contentDescription = null, modifier = Modifier.size(CALL_ICON))
    }
}

/**
 * Диаметр круга вызова: высота прежней кнопки + 8 dp — 64 dp, с крупными
 * кнопками 80 dp. Нужен и панели: кнопку «скрыть клавиатуру» ставят
 * по центру круга, а не по центру круга с подписью.
 */
@Composable
internal fun callCircleSize(): Dp = LocalAppearance.current.callButtonHeight + 8.dp

/** Значок трубки в круге. */
private val CALL_ICON = 28.dp

/** Подпись карты может быть шире круга — на столько. */
private val CAPTION_EXTRA = 56.dp
