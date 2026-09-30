package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(Modifier.padding(bottom = FlowUi.sheetBottomPadding)) {
            Text(
                title,
                Modifier.padding(horizontal = FlowUi.sheetPadding).padding(bottom = 12.dp),
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
                        OgnelissDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }

            if (emails.isNotEmpty() && onEmail != null) {
                SheetGroup {
                    emails.forEachIndexed { index, address ->
                        SheetRow(Icons.Default.Email, address, onClick = { onEmail(address) })
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
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

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

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sims.size > 1) {
                // Две SIM — две кнопки вызова с номером карты
                sims.forEachIndexed { index, sim ->
                    ActionButton(
                        icon = Icons.Default.Call,
                        badge = (index + 1).toString(),
                        description = stringResource(
                            R.string.a11y_call_with_sim, sim.label, option.number.takeLast(4)
                        )
                    ) { onCall(option.number, index) }
                }
            } else {
                ActionButton(
                    icon = Icons.Default.Call,
                    badge = null,
                    description = stringResource(R.string.action_call)
                ) { onCall(option.number, -1) }
            }

            ActionButton(
                icon = Icons.AutoMirrored.Filled.Message,
                badge = null,
                description = stringResource(R.string.action_sms)
            ) { onSms(option.number) }

            ActionButton(
                icon = Icons.AutoMirrored.Filled.Chat,
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
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            (slotIndex + 1).toString(),
            Modifier.padding(horizontal = 4.dp),
            // labelSmall: подпись под номером — самый тихий текст
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
