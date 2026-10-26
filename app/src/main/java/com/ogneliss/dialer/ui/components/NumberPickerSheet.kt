package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * ВЫБОР НОМЕРА И SIM ПЕРЕД ДЕЙСТВИЕМ.
 *
 * Назначение: когда у абонента несколько номеров, нужно выбрать,
 * на какой звонить или писать — и с какой SIM.
 *
 * ── С 1.8.0 — карточками, а не шторкой ─────────────────────────────────────
 * Автор: «панели выбора SIM лучше сделать не выезжающими снизу, а как при
 * долгом нажатии на карточку в журнале или контакт. Пользователям такое
 * нравится». Здесь тоже выбирают SIM, поэтому вид тот же: список размыт,
 * у места нажатия — карточка «кому» и карточка номеров (CardsOverlay).
 * Было (1.2.1–1.7.0): шторка снизу; довод «у большого пальца» карточки
 * сохраняют — они встают там, где палец только что нажал.
 *
 * ── Структура строки номера ────────────────────────────────────────────────
 * Номер целиком, под ним подпись из книги («Рабочий»), а справа — ряд
 * действий: вызов с SIM1, вызов с SIM2, SMS, мессенджер, почта.
 * При одной SIM кнопка вызова одна. Долгое нажатие на SMS при двух
 * картах — выбор SIM для сообщения ([onChooseSmsSim]).
 *
 * Последний использованный номер помечается — обычно звонят на него же.
 */
@Composable
fun NumberPickerSheet(
    title: String,
    numbers: List<NumberOption>,
    sims: List<SimSlot>,
    onCall: (number: String, slotIndex: Int) -> Unit,
    onSms: (number: String) -> Unit,
    onMessenger: (number: String) -> Unit,
    onEmail: ((String) -> Unit)? = null,
    emails: List<String> = emptyList(),
    /** Долгое нажатие на SMS при двух картах — выбор SIM (1.8.0); null — нет. */
    onChooseSmsSim: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    CardsOverlay(anchorY = LocalTouchAnchor.current?.screenY, onDismiss = onDismiss) { close ->
        ActionCard {
            Column(Modifier.padding(Space.l)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.number_picker_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ActionCard {
            Column(Modifier.padding(vertical = Space.xs)) {
                numbers.forEachIndexed { index, option ->
                    if (index > 0) RowDivider(Modifier.padding(start = Space.l))
                    // Действие — сразу, карточки гаснут после: звонок не ждёт анимации
                    NumberRow(
                        option, sims,
                        onCall = { number, slot -> onCall(number, slot); close() },
                        onSms = { number -> onSms(number); close() },
                        onMessenger = { number -> onMessenger(number); close() },
                        onChooseSmsSim = onChooseSmsSim?.let { choose -> { number -> choose(number); close() } }
                    )
                }
            }
        }
        if (emails.isNotEmpty() && onEmail != null) {
            ActionCard {
                Column(Modifier.padding(vertical = Space.xs)) {
                    emails.forEachIndexed { index, address ->
                        SheetRow(OgnelissIcons.Email, address, onClick = { onEmail(address); close() })
                        if (index < emails.lastIndex) SheetDivider()
                    }
                }
            }
        }
    }
}

/** Один номер абонента со всеми доступными действиями. */
data class NumberOption(
    val number: String,
    val label: String?,
    /** Последний вызов на этот номер был исходящим (иначе входящим). */
    val lastWasOutgoing: Boolean? = null,
    /** Слот SIM, с которого звонили в прошлый раз или который запомнен. */
    val preferredSlot: Int? = null,
    /** Самый свежий номер абонента — подсвечивается. */
    val isMostRecent: Boolean = false
)

@Composable
private fun NumberRow(
    option: NumberOption,
    sims: List<SimSlot>,
    onCall: (String, Int) -> Unit,
    onSms: (String) -> Unit,
    onMessenger: (String) -> Unit,
    onChooseSmsSim: ((String) -> Unit)? = null
) {
    // Поля — внутри панели: её собственный отступ от края уже есть
    Column(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    PhoneFormatter.format(option.number),
                    // bodyLarge из темы вместо 18 sp: номер в списке
                    // выбора — обычный текст, а не заголовок
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (option.isMostRecent) FontWeight.Medium else FontWeight.Normal
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    option.label?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    // Направление последнего вызова: сразу видно,
                    // я звонил или мне звонили
                    option.lastWasOutgoing?.let { outgoing ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (outgoing) "↗" else "↙",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    // С какой SIM звонили в прошлый раз
                    option.preferredSlot?.let { slot ->
                        Spacer(Modifier.width(6.dp))
                        SimBadge(slot)
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.s))

        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            if (sims.size > 1) {
                // Две SIM — две кнопки вызова с номером карты
                sims.forEach { sim ->
                    ActionButton(
                        icon = OgnelissIcons.Call,
                        badge = (sim.slotIndex + 1).toString(),
                        description = stringResource(
                            R.string.a11y_call_with_sim, sim.label, option.number.takeLast(4)
                        )
                    // Индекс СЛОТА, а не место в списке (как в CallButtons): при картах
                    // во втором слоте место 0 и слот 1 — звонок ушёл бы не с той карты
                    ) { onCall(option.number, sim.slotIndex) }
                }
            } else {
                ActionButton(
                    icon = OgnelissIcons.Call,
                    badge = null,
                    description = stringResource(R.string.action_call)
                ) { onCall(option.number, -1) }
            }

            ActionButton(
                icon = OgnelissIcons.Message,
                badge = null,
                description = stringResource(R.string.action_sms),
                onLongClick = if (sims.size > 1) onChooseSmsSim?.let { choose -> { choose(option.number) } } else null,
                onLongClickLabel = stringResource(R.string.a11y_choose_sim_sms)
            ) { onSms(option.number) }

            ActionButton(
                icon = OgnelissIcons.Chat,
                badge = null,
                description = stringResource(R.string.action_whatsapp)
            ) { onMessenger(option.number) }
        }
    }
}

/** Кнопка действия с необязательным номером SIM в углу. */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String?,
    description: String,
    /** Долгое нажатие (1.8.0) — выбор SIM у SMS; null — только касание. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit
) {
    Box {
        // Круг цвета кнопки FilledIconButton, но с долгим нажатием: у Material его нет
        Box(
            Modifier
                .size(Dimens.MIN_TOUCH_TARGET)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickableCompat(
                            onClick = onClick,
                            onLongClick = onLongClick,
                            onLongClickLabel = onLongClickLabel
                        )
                    } else {
                        Modifier.clickable(onClick = onClick)
                    }
                )
                .semantics { role = Role.Button; contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        if (badge != null) {
            Badge(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
            ) { Text(badge) }
        }
    }
}

/** Компактный индикатор SIM: «1» или «2» в рамке. */
@Composable
fun SimBadge(slotIndex: Int) {
    // Описание для TalkBack (1.3.2): без него плашка читалась как «1»,
    // похоже на счётчик звонков
    val description = "SIM ${slotIndex + 1}"
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
    ) {
        /*
         * Значок SIM перед цифрой (1.3.4, 04.10.2026, дизайн-система).
         * Было: одна цифра в плашке — глазом её принимали за число звонков
         * (у строки журнала рядом и правда есть счётчик). Стало: «▯1» —
         * значок SIM-карты 12 dp и номер слота; читается однозначно.
         */
        Row(
            Modifier.padding(start = Space.xxs, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                OgnelissIcons.SimCard,
                contentDescription = null,
                // Серым, как подписи-метки писем в Ogneliss Mail (1.5.6)
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(1.dp))
            Text(
                (slotIndex + 1).toString(),
                // labelSmall: подпись под номером — самый тихий текст
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
