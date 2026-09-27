package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.ui.components.OgnelissDialog

/**
 * НАЗВАНИЯ SIM-КАРТ.
 *
 * Назначение: дать пользователю подписать слоты понятными именами —
 * «Рабочая», «Личная», «Для интернета».
 *
 * ── Зачем это нужно ────────────────────────────────────────────────────────
 * Системная подпись SIM — обычно название оператора. Если обе карты
 * одного оператора, обе называются одинаково («MTS», «MTS») и не различают
 * ничего. Номер телефона карты тоже не спасает: он часто не записан
 * в саму карту и Android возвращает пустую строку.
 *
 * Слот же есть всегда, поэтому подписи привязаны к нему: слот 1 и слот 2.
 * Пустое поле означает «использовать системное имя».
 */
@Composable
fun SimLabelsDialog(
    sims: List<SimSlot>,
    currentLabels: Map<Int, String>,
    onSave: (slotIndex: Int, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Локальное состояние: правки применяются при сохранении, а не посимвольно
    val drafts = remember(sims, currentLabels) {
        mutableStateMapOf<Int, String>().apply {
            sims.forEach { put(it.slotIndex, currentLabels[it.slotIndex].orEmpty()) }
        }
    }

    // Общий контейнер приложения вместо AlertDialog
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_sim_labels),
        confirmButton = {
            TextButton(onClick = {
                drafts.forEach { (slot, label) -> onSave(slot, label) }
                onDismiss()
            }) { Text(stringResource(R.string.nav_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_cancel)) }
        },
        content = {
            Column {
                Text(
                    stringResource(R.string.settings_sim_labels_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(12.dp))

                if (sims.isEmpty()) {
                    Text(stringResource(R.string.settings_sim_none))
                }

                sims.forEach { sim ->
                    OutlinedTextField(
                        value = drafts[sim.slotIndex].orEmpty(),
                        onValueChange = { drafts[sim.slotIndex] = it },
                        label = {
                            // Показываем номер слота и системное имя как подсказку:
                            // пользователю нужно понять, какую карту он подписывает
                            Text(
                                stringResource(
                                    R.string.settings_sim_slot,
                                    sim.slotIndex + 1
                                )
                            )
                        },
                        placeholder = { Text(sim.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
        }
    )
}
