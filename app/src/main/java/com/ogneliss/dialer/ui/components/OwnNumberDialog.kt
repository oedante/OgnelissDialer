package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.ogneliss.dialer.R
import com.ogneliss.dialer.util.PhoneFormatter
import kotlinx.coroutines.launch

/**
 * ОКНО «МОЙ НОМЕР».
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ НОМЕР ПРИХОДИТСЯ СПРАШИВАТЬ
 * ══════════════════════════════════════════════════════════════════════════
 * Кажется, что телефон свой номер знает. На деле — далеко не всегда:
 * он хранится на SIM-карте в поле, которое оператор заполняет
 * по желанию, и многие не заполняют.
 *
 * Поэтому порядок такой: сперва пробуем узнать у системы, и только
 * если не вышло — просим ввести. Не наоборот: заставлять человека
 * набирать то, что телефон может знать сам, невежливо.
 *
 * ── Почему это не мешает при первом запуске ────────────────────────────────
 * Окно открывается только по нажатию в меню. Номер нужен ровно для
 * одного — поделиться своей карточкой, — и требовать его у всех
 * при установке значило бы задерживать людей ради возможности,
 * которой многие не воспользуются.
 */
@Composable
fun OwnNumberDialog(
    current: String,
    onDetect: suspend () -> Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(current) { mutableStateOf(PhoneFormatter.format(current)) }
    var detectFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.my_number_title),
        content = {
                Text(
                    stringResource(R.string.my_number_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Space.l))

                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        detectFailed = false
                    },
                    label = { Text(stringResource(R.string.my_number_title)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (detectFailed) {
                    Spacer(Modifier.height(Space.s))
                    Text(
                        stringResource(R.string.my_number_not_detected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(Space.s))

                // Попытка определения — рядом с полем, а не вместо него:
                // она может не сработать, и тогда поле уже под рукой
                TextButton(
                    onClick = {
                        scope.launch { detectFailed = !onDetect() }
                    }
                ) {
                    Text(stringResource(R.string.my_number_detect))
                }
        },
        confirmButton = {
            TextButton(
                /*
                 * КНОПКА ВСЕГДА ДОСТУПНА.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ПОЧЕМУ УБРАНО УСЛОВИЕ enabled
                 * ══════════════════════════════════════════════════════════
                 * Было: enabled = value != PhoneFormatter.format(current) —
                 * «сохранять нечего, значение не изменилось».
                 *
                 * Ломалось это на определении номера. «Попробовать
                 * определить» не просто заполняет поле: detectOwnNumber
                 * сразу записывает найденный номер в настройки. current
                 * меняется, remember(current) заново кладёт его в value,
                 * value становится равным current — и кнопка гаснет.
                 *
                 * Со стороны человека выглядит как поломка: номер в поле
                 * есть, «Сохранить» серая. То, что номер уже сохранён,
                 * ниоткуда не видно.
                 *
                 * Повторное сохранение того же значения ничего не портит:
                 * запись идёт в те же настройки тем же текстом. Лучше
                 * лишний раз записать, чем показать неработающую кнопку.
                 */
                onClick = {
                    onSave(value)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                /*
                 * Удаление номера.
                 *
                 * Появляется, только когда номер задан: пустая кнопка
                 * сбивала бы с толку.
                 *
                 * Очищает поле, а не сохраняет пустоту сразу —
                 * подтверждение по «Сохранить», как и при изменении.
                 * Передумав, человек закрывает окно, и номер остаётся.
                 */
                if (current.isNotBlank()) {
                    TextButton(onClick = { value = "" }) {
                        Text(stringResource(R.string.action_clear))
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.nav_close))
                }
            }
        }
    )
}
