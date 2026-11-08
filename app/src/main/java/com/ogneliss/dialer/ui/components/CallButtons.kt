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
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SimCallPlan
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.ui.theme.LocalAppearance
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens

/**
 * КНОПКИ ВЫЗОВА.
 *
 * ── Одна SIM: одна широкая кнопка ──────────────────────────────────────────
 * Главное действие экрана. Занимает почти всю ширину, скруглена полностью,
 * окрашена в основной цвет — промахнуться невозможно.
 *
 * ── Две SIM: две кнопки рядом ──────────────────────────────────────────────
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
    val appearance = LocalAppearance.current
    val height = appearance.callButtonHeight

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
    val callColors = if (!canCall) ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContentColor = MaterialTheme.colorScheme.outline
    ) else ButtonDefaults.buttonColors(
        containerColor = CallGreen,
        contentColor = Color.White,
        /*
         * Номер не набран (1.3.6, 07.10.2026, автор: клавиатура «из другого
         * приложения»). Было: системный серый «недоступно» — такого серого
         * больше нигде нет. Стало: тоновая зелёная — светлый фон (16%)
         * и зелёные подпись и значок: видно, что это кнопка вызова, просто
         * пока не активна. Белый текст на бледно-зелёном не читался бы.
         */
        disabledContainerColor = CallGreen.copy(alpha = 0.16f),
        disabledContentColor = CallGreen.copy(alpha = 0.75f)
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        if (layout is SimCallPlan.Layout.Single) {
            val label = stringResource(R.string.action_call)
            Button(
                onClick = { onCall(-1) },
                enabled = enabled,
                shape = OgnelissShapeTokens.callButton,
                colors = callColors,
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .semantics { contentDescription = label }
            ) {
                Icon(OgnelissIcons.Call, contentDescription = null)
            }
        } else {
            (layout as SimCallPlan.Layout.PerSim).sims.forEachIndexed { index, sim ->
                val label = stringResource(R.string.a11y_call_with_sim_only, sim.label)
                Button(
                    // Передаём индекс СЛОТА, а не позицию в списке:
                    // при одной активной карте во втором слоте позиция 0,
                    // а слот 1 — и звонок ушёл бы не с той карты
                    onClick = { onCall(sim.slotIndex) },
                    enabled = enabled,
                    shape = OgnelissShapeTokens.callButton,
                    colors = callColors,
                    modifier = Modifier
                        .weight(1f)
                        .height(height)
                        .semantics { contentDescription = label }
                ) {
                    Icon(OgnelissIcons.Call, contentDescription = null)
                    Spacer(Modifier.width(Space.s))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            "SIM ${sim.slotIndex + 1}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        // Имя карты мелко под номером: если пользователь
                        // подписал слоты, выбор становится осмысленным,
                        // а не «первая или вторая»
                        if (sim.label.isNotBlank() && sim.label != "SIM ${sim.slotIndex + 1}") {
                            // FitText (1.3.2): лёжа имя обрезалось до
                            // «Beeline2» / «Beeline0» — карты не различить
                            FitText(
                                sim.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                minScale = 0.7f
                            )
                        }
                    }
                }
            }
        }
    }
}
