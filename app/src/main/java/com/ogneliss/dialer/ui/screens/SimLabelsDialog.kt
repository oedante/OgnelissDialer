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
                        placeholder = { Text(sim.systemName ?: sim.label) },
                        /*
                         * ЧТО ЭТО ЗА КАРТА — ПОД ПОЛЕМ, ВСЕГДА НА ВИДУ.
                         *
                         * Вопрос от пользователя: «Слот 1», «Слот 2» —
                         * а где у меня какой номер? Системное имя стояло
                         * только подсказкой внутри пустого поля, и её
                         * закрывала подпись «Слот 1»: пока в поле не нажмёшь,
                         * не видно.
                         *
                         * Теперь под каждым полем — имя карты в системе,
                         * оператор и номер, что из этого известно. Номера
                         * часто нет (операторы редко пишут его на карту),
                         * но системное имя есть всегда — и оно то же, что
                         * в настройках телефона, где карты подписаны.
                         */
                        supportingText = { Text(simIdentity(sim)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
        }
    )
}


/**
 * Строка «что это за карта»: системное имя, оператор, номер — без повторов.
 *
 * Оператор не повторяется, если системное имя с него начинается
 * («Beeline2292» и «Beeline»). Вид карты — SIM или eSIM — есть всегда.
 */
@Composable
private fun simIdentity(sim: SimSlot): String {
    val kind = stringResource(if (sim.embedded) R.string.sim_kind_esim else R.string.sim_kind_physical)
    val parts = buildList {
        // Вид карты — первым: «eSIM» сразу отвечает на «где какая»,
        // если физический слот у телефона один
        add(kind)
        sim.systemName?.let { add(it) }
        sim.carrier?.let { carrier ->
            val dup = sim.systemName?.startsWith(carrier, ignoreCase = true) == true
            if (!dup) add(carrier)
        }
        sim.number?.let { add(com.ogneliss.dialer.util.PhoneFormatter.format(it)) }
    }
    return parts.joinToString(" · ")
}
