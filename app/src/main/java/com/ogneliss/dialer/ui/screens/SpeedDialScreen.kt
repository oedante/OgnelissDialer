package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.components.OgnelissDivider
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.data.SpeedDialEntry
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * ЭКРАН БЫСТРОГО НАБОРА.
 *
 * Назначение: назначить контакты на цифры 2–9. Долгое нажатие цифры
 * на клавиатуре сразу звонит назначенному номеру.
 *
 * ── Почему цифры именно с 2 ────────────────────────────────────────────────
 * Единица традиционно занята голосовой почтой — так работает во всех
 * телефонах с восьмидесятых, и ломать эту привычку незачем. Показываем
 * её в списке отдельной строкой, чтобы пользователь не искал, куда делась.
 *
 * ── Почему не сетка, а список ──────────────────────────────────────────────
 * В сетке 3×3 помещаются только цифры, без имён. Пользователь помнит
 * «Любимая на пятёрке», а не «пятёрка — это кто-то». Список даёт цифру
 * и имя рядом, и на нём проще разместить кнопку сброса.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedDialScreen(
    vm: DialerViewModel,
    onBack: () -> Unit
) {
    val entries by vm.speedDial.collectAsState()
    var assigning by remember { mutableStateOf<Int?>(null) }

    // Все цифры 2..9, включая незанятые: пустой слот должен быть виден,
    // иначе непонятно, куда можно назначить
    val slots = remember(entries) {
        (2..9).map { digit -> digit to entries.firstOrNull { it.digit == digit } }
    }

    ScreenScaffold(
        title = stringResource(R.string.speed_dial_title),
        onBack = onBack,
        // Экран настроек: заголовок закреплён
        pinnedTopBar = true
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {

            item {
                Text(
                    stringResource(R.string.speed_dial_hint),
                    Modifier.padding(FlowUi.contentPadding),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Единица — голосовая почта, менять нельзя
            item {
                OgnelissListRow(
                    headline = stringResource(R.string.speed_dial_voicemail),
                    supporting = stringResource(R.string.speed_dial_voicemail_hint),
                    leading = { DigitBadge("1") },
                    trailing = { Icon(Icons.Default.Voicemail, null) }
                )
                OgnelissDivider()
            }

            itemsIndexed(slots, key = { _, slot -> slot.first }) { index, (digit, entry) ->
                SpeedDialRow(
                    digit = digit,
                    entry = entry,
                    onAssign = { assigning = digit },
                    onClear = { vm.clearSpeedDial(digit) }
                )
                // Общий разделитель и только между строками: прежде здесь
                // стоял HorizontalDivider со своей толщиной, и под последней
                // клавишей висела черта, которой нигде больше нет
                if (index < slots.lastIndex) OgnelissDivider()
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    assigning?.let { digit ->
        SpeedDialPickerDialog(
            digit = digit,
            vm = vm,
            onAssign = { number, name ->
                vm.assignSpeedDial(digit, number, name)
                assigning = null
            },
            onDismiss = { assigning = null }
        )
    }
}

@Composable
private fun SpeedDialRow(
    digit: Int,
    entry: SpeedDialEntry?,
    onAssign: () -> Unit,
    onClear: () -> Unit
) {
    OgnelissListRow(
        headline = entry?.name?.takeIf { it.isNotBlank() }
            ?: entry?.number?.let { PhoneFormatter.format(it) }
            ?: stringResource(R.string.speed_dial_empty),
        // Номер второй строкой только когда он есть: у пустого места
        // подпись была бы пустой строкой и раздвигала бы ряд
        supporting = entry?.let { PhoneFormatter.format(it.number) },
        leading = { DigitBadge(digit.toString()) },
        trailing = {
            if (entry != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.speed_dial_clear)
                    )
                }
            } else {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
        onClick = onAssign
    )
}

/** Крупная цифра слева — по ней пользователь и ориентируется. */
@Composable
private fun DigitBadge(digit: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                digit,
                // titleLarge из темы: цифра быстрого набора — заголовок
                // строки, а не произвольно крупный текст
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
