package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogneliss.dialer.ui.theme.FlowUi
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
 * ── Почему шторка, а не диалог ─────────────────────────────────────────────
 * Шторка появляется снизу, у большого пальца. Диалог возникает по центру
 * экрана, куда одной рукой на современном телефоне не дотянуться.
 * Для действия, которое совершают на ходу, это важнее эстетики.
 *
 * ── Структура строки номера ────────────────────────────────────────────────
 * Номер целиком, под ним подпись из книги («Рабочий»), а справа — ряд
 * действий: вызов с SIM1, вызов с SIM2, SMS, мессенджер, почта.
 * При одной SIM кнопка вызова одна.
 *
 * Последний использованный номер помечается — обычно звонят на него же.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onDismiss: () -> Unit
) {
    /*
     * Шторка раскрывается сразу полностью и учитывает системные панели.
     *
     * По умолчанию ModalBottomSheet открывается наполовину и не знает
     * о навигационной панели: нижние пункты оказываются под кнопками
     * системы, и нажать их нельзя.
     */
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        // Страница настроек, группы — белыми карточками (1.6.1, SheetGroup)
        containerColor = com.ogneliss.dialer.ui.components.sheetContainerColor(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(Modifier.padding(bottom = FlowUi.sheetBottomPadding)) {
            Text(
                title,
                Modifier.padding(horizontal = FlowUi.sheetPadding).padding(bottom = Space.m),
                style = MaterialTheme.typography.titleLarge
            )

            /*
             * Номера — в скруглённой панели, почта — во второй (1.2.2).
             * Раньше линия стояла и под последним номером, а почта шла
             * сразу за ней без границы — как будто это ещё один номер.
             */
            SheetGroup {
                numbers.forEachIndexed { index, option ->
                    NumberRow(option, sims, onCall, onSms, onMessenger)
                    if (index < numbers.lastIndex) {
                        OgnelissDivider(Modifier.padding(start = Space.l))
                    }
                }
            }

            if (emails.isNotEmpty() && onEmail != null) {
                SheetGroup {
                    emails.forEachIndexed { index, address ->
                        SheetRow(OgnelissIcons.Email, address, onClick = { onEmail(address) })
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
    onMessenger: (String) -> Unit
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
                sims.forEachIndexed { index, sim ->
                    ActionButton(
                        icon = OgnelissIcons.Call,
                        badge = (index + 1).toString(),
                        description = stringResource(
                            R.string.a11y_call_with_sim, sim.label, option.number.takeLast(4)
                        )
                    ) { onCall(option.number, index) }
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
                description = stringResource(R.string.action_sms)
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
    onClick: () -> Unit
) {
    Box {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(Dimens.MIN_TOUCH_TARGET)
        ) {
            Icon(icon, contentDescription = description)
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
