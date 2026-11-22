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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * КНОПКИ ВЫЗОВА.
 *
 * ── Одна SIM: одна кнопка «Позвонить» ──────────────────────────────────────
 * Главное действие экрана: зелёная кнопка под клавиатурой. Форма: до 2.7.5 —
 * пилюля во всю ширину, 2.7.5–2.8.8 — круг, с 2.8.9 — прямоугольник
 * со скруглением из настройки «Скругление углов» и подписью внутри.
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
     * ПРЯМОУГОЛЬНЫЕ КНОПКИ С ПОДПИСЬЮ ВНУТРИ (2.8.9, автор по снимку клавиатуры:
     * «давай кнопки на клавиатуре сделаем прямоугольными с подписями в них.
     * А то не красиво»). В 2.7.5 были круги с подписью карты под ними: под
     * бледными кругами висели «SIM 1 · Beeline2292» — подпись отрывалась
     * от кнопки, а ряд занимал две строки. Стало: кнопки делят ряд поровну
     * между «скрыть клавиатуру» и «Контактами». Не совсем прямоугольные (автор:
     * «со скруглениями, зависящими от настроек, которые уже есть»): форма темы
     * MaterialTheme.shapes.medium — 14 dp, при «Скруглении углов» «Малое» 7,
     * «Большое» ≈ 20 (shapesFor, CornerSize). Внутри —
     * трубка и «SIM 1» с именем карты строкой ниже (FitText: лёжа имя
     * не обрезается — урок 1.3.2). Одна карта — одна кнопка «Позвонить».
     */
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (layout is SimCallPlan.Layout.Single) {
            val label = stringResource(R.string.action_call)
            CallButton(
                enabled = enabled,
                colors = callColors,
                description = label,
                title = label,
                subtitle = null,
                onClick = { onCall(-1) },
                modifier = Modifier.weight(1f)
            )
        } else {
            (layout as SimCallPlan.Layout.PerSim).sims.forEach { sim ->
                val label = stringResource(R.string.a11y_call_with_sim_only, sim.label)
                val slot = "SIM ${sim.slotIndex + 1}"
                // Имя карты — строкой ниже, если пользователь или система его задали
                val named = sim.label.isNotBlank() && sim.label != slot
                CallButton(
                    enabled = enabled,
                    colors = callColors,
                    description = label,
                    title = slot,
                    subtitle = sim.label.takeIf { named },
                    // Передаём индекс СЛОТА, а не позицию в списке:
                    // при одной активной карте во втором слоте позиция 0,
                    // а слот 1 — и звонок ушёл бы не с той карты
                    onClick = { onCall(sim.slotIndex) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Кнопка вызова: трубка и подпись внутри, белые на зелёном (2.8.9). */
@Composable
private fun CallButton(
    enabled: Boolean,
    colors: IconButtonColors,
    description: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    val content = if (enabled) colors.contentColor else colors.disabledContentColor
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        modifier = modifier
            .height(callButtonHeight())
            .semantics { contentDescription = description }
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = Space.s)
        ) {
            Icon(OgnelissIcons.Call, contentDescription = null, modifier = Modifier.size(CALL_ICON))
            Spacer(Modifier.width(Space.s))
            Column(verticalArrangement = Arrangement.Center) {
                FitText(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    minScale = 0.7f
                )
                if (subtitle != null) {
                    FitText(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = content,
                        maxLines = 1,
                        minScale = 0.7f
                    )
                }
            }
        }
    }
}

/**
 * Высота кнопки вызова — высота кнопок приложения (56 dp, с крупными
 * кнопками 72 dp). Нужна и панели: кнопки «скрыть клавиатуру»
 * и «Контакты» стоят по её середине.
 */
@Composable
internal fun callButtonHeight(): Dp = LocalAppearance.current.callButtonHeight

/** Значок трубки в кнопке. */
private val CALL_ICON = 24.dp
