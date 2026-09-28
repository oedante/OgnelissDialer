package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * ВЫБОР КОНТАКТА ДЛЯ БЫСТРОГО НАБОРА.
 *
 * Список контактов с поиском по имени. Если у контакта несколько номеров,
 * показывается каждый отдельной строкой: назначать нужно конкретный номер,
 * а не контакт — иначе при вызове пришлось бы спрашивать, какой из трёх,
 * что убивает саму идею быстрого набора.
 */
@Composable
fun SpeedDialPickerDialog(
    digit: Int,
    vm: DialerViewModel,
    onAssign: (number: String, name: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val state by vm.state.collectAsState()
    var search by remember { mutableStateOf("") }

    // Разворачиваем контакты в плоский список «номер + имя»
    val options = remember(state.contacts, search) {
        state.contacts
            .filter { search.isBlank() || it.displayName.contains(search, ignoreCase = true) }
            .flatMap { contact ->
                contact.numbers.map { number ->
                    Triple(number.value, contact.displayName, number.label)
                }
            }
            .take(200)
    }

    /*
     * Общий контейнер приложения.
     *
     * «Отмена» переехала из dismissButton в confirmButton: у AlertDialog
     * тут стоял пустой confirmButton = {}, то есть окно рисовало место
     * под кнопку, которой нет. Выбор здесь делается нажатием на строку,
     * подтверждать нечего — единственная кнопка закрывает окно.
     */
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.speed_dial_assign, digit),
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_cancel)) }
        },
        content = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(R.string.speed_dial_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                if (options.isEmpty()) {
                    Text(
                        stringResource(R.string.speed_dial_no_contacts),
                        Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(options, key = { it.first + it.second }) { (number, name, label) ->
                        OgnelissListRow(
                            headline = name,
                            supporting = buildString {
                                append(PhoneFormatter.format(number))
                                if (label.isNotBlank()) append(" · ").append(label)
                            },
                            onClick = { onAssign(number, name) }
                        )
                    }
                }
            }
        }
    )
}
