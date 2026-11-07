package com.ogneliss.dialer.call

import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.data.SimRepository
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.SheetDivider
import com.ogneliss.dialer.ui.components.SheetGroup
import com.ogneliss.dialer.ui.components.SheetRow
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ВОПРОСЫ, БЕЗ ОТВЕТА НА КОТОРЫЕ ВЫЗОВ СТОИТ (2.1.0).
 *
 * Telecom в двух случаях ждёт решения от экрана вызова и сам дальше
 * не идёт:
 *
 * • SELECT_PHONE_ACCOUNT — исходящий без выбранной SIM («спрашивать
 *   каждый раз», звонок из другого приложения). Ответ —
 *   Call.phoneAccountSelected.
 * • post-dial wait — в номере «;», остаток ждёт подтверждения.
 *   Ответ — Call.postDialContinue.
 *
 * Было: ни того ни другого экран не показывал, и вызов висел до тех пор,
 * пока человек сам не сбрасывал его. Стало: два диалога поверх экрана
 * вызова. Состояние — в CallManager (awaitingAccount, postDialWait).
 */
@Composable
internal fun SimSelectDialog(call: Call, simRepo: SimRepository) {
    val context = LocalContext.current
    // Список и подписи — в фоне: SimRepository ходит в системные службы
    val options by produceState<List<Pair<PhoneAccountHandle, String>>>(emptyList(), call) {
        value = withContext(Dispatchers.IO) {
            suspendRunCatching {
                val handles = CallManager.suggestedAccounts(call).ifEmpty { simRepo.phoneAccounts() }
                val tm = context.getSystemService(TelecomManager::class.java)
                handles.mapIndexed { index, handle ->
                    val label = simRepo.labelFor(handle.id)
                        ?: runCatching { tm?.getPhoneAccount(handle)?.label?.toString() }.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                        ?: "SIM ${index + 1}"
                    handle to label
                }
            }.getOrDefault(emptyList())
        }
    }

    OgnelissDialog(
        // Касание мимо окна — не отмена: случайно сбросить звонок хуже,
        // чем закрыть окно кнопкой
        onDismissRequest = {},
        title = stringResource(R.string.sim_chooser_title),
        confirmButton = {
            // Отмена выбора — отмена вызова: без SIM ему некуда идти
            TextButton(onClick = { call.disconnect() }) { Text(stringResource(R.string.nav_cancel)) }
        }
    ) {
        if (options.isNotEmpty()) {
            SheetGroup {
                options.forEachIndexed { index, (handle, label) ->
                    SheetRow(
                        OgnelissIcons.SimCard,
                        label,
                        onClick = { CallManager.selectAccount(call, handle) }
                    )
                    if (index < options.lastIndex) SheetDivider()
                }
            }
        }
    }
}

/**
 * Остаток номера после «;»: «Отправить 123» или «Отмена» (остаток
 * отбрасывается, разговор продолжается).
 */
@Composable
internal fun PostDialDialog(wait: CallManager.PostDialWait) {
    OgnelissDialog(
        onDismissRequest = {},
        title = stringResource(R.string.incall_postdial_title),
        confirmButton = {
            TextButton(onClick = { CallManager.continuePostDial(true) }) {
                Text(stringResource(R.string.incall_postdial_send, wait.remaining))
            }
        },
        dismissButton = {
            TextButton(onClick = { CallManager.continuePostDial(false) }) {
                Text(stringResource(R.string.nav_cancel))
            }
        }
    ) {}
}
