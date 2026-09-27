package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.RuleKind
import com.ogneliss.dialer.data.RulePattern
import com.ogneliss.dialer.ui.components.OgnelissDialog

/**
 * ДОБАВЛЕНИЕ ПРАВИЛА БЛОКИРОВКИ ИЛИ РАЗРЕШЕНИЯ.
 *
 * ── Проверка идёт по мере ввода, а не по нажатию «Сохранить» ───────────────
 * Пользователь должен видеть последствия до того, как правило начнёт
 * действовать. Два опасных случая, которые ловятся сразу:
 *
 * • Слишком широкий шаблон («+7*») заблокирует вообще всё. Человек потом
 *   неделю не может понять, почему телефон молчит.
 * • Шаблон, который никогда не сработает: номера сравниваются в формате
 *   E.164, поэтому введённое «8 (900)…» не совпадёт ни с чем. Правило
 *   будет в списке, выглядеть рабочим и молча не работать.
 *
 * Поэтому под полем всегда показывается, ЧТО именно попадёт под правило
 * в нормализованном виде.
 */
@Composable
fun AddRuleDialog(
    kind: RuleKind,
    onAdd: (pattern: String, comment: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    val parsed = remember(input) { RulePattern.parse(input) }
    val isValid = parsed is RulePattern.Result.Valid

    /*
     * Окно в общем стиле приложения: тот же контейнер, что у заметки,
     * выбора SIM и настроек. AlertDialog задавал свои 24 dp по краям
     * и выбивался из остального.
     *
     * Порядок аргументов изменился: у OgnelissDialog заголовок —
     * строка, а не composable, и содержимое передаётся в content.
     */
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            if (kind == RuleKind.ALLOW) R.string.rule_add_allow
            else R.string.rule_add_block
        ),
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    (parsed as? RulePattern.Result.Valid)?.let {
                        onAdd(it.pattern, comment.takeIf { c -> c.isNotBlank() })
                    }
                    onDismiss()
                }
            ) { Text(stringResource(R.string.nav_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_cancel)) }
        },
        content = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.rule_number_or_mask)) },
                    placeholder = { Text("+7 800 *") },
                    singleLine = true,
                    isError = input.isNotBlank() && !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                // Обратная связь по мере ввода
                when (val p = parsed) {
                    is RulePattern.Result.Valid -> {
                        Text(
                            stringResource(
                                if (p.isMask) R.string.rule_preview_mask
                                else R.string.rule_preview_number,
                                RulePattern.describe(p.pattern)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is RulePattern.Result.TooBroad -> {
                        Text(
                            stringResource(R.string.rule_error_too_broad),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is RulePattern.Result.InvalidCharacters -> {
                        Text(
                            stringResource(R.string.rule_error_characters, p.characters),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    RulePattern.Result.Empty -> {
                        Text(
                            stringResource(R.string.rule_mask_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.rule_comment)) },
                    placeholder = { Text(stringResource(R.string.rule_comment_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
