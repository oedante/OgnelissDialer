package com.ogneliss.dialer.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.sms.SmsRole
import com.ogneliss.dialer.util.RoleAccess

/**
 * ПОДСКАЗКА, КОГДА РОЛЬ ИЛИ РАЗРЕШЕНИЕ SMS НЕ ВЫДАНЫ (2.0.0).
 *
 * Человек нажал «Назначить» или «Сделать» — и ничего не получилось.
 * Без объяснения это тупик, поэтому окно говорит, что именно случилось
 * и куда нажать, одной кнопкой туда и ведёт. Разбор причин — RoleAccess.
 */
@Composable
fun RoleHelpDialog(help: RoleAccess.Help, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val restricted = help == RoleAccess.Help.RESTRICTED
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (restricted) R.string.role_restricted_title else R.string.dialer_role_blocked_title),
        content = {
            Text(stringResource(if (restricted) R.string.role_restricted_text else R.string.dialer_role_blocked_text))
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if (restricted) RoleAccess.openAppInfo(context) else RoleAccess.openDefaultApps(context)
            }) {
                Text(stringResource(if (restricted) R.string.role_restricted_open else R.string.dialer_role_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_close)) }
        }
    )
}

/**
 * Запрос роли SMS с разбором отказа: окно роли (или «Приложения по
 * умолчанию», если роли на телефоне нет в виде окна), после возврата —
 * [onResult] с подсказкой или null (роль выдана либо человек отказался сам).
 */
@Composable
fun rememberSmsRoleRequest(onResult: (RoleAccess.Help?) -> Unit): () -> Unit {
    val context = LocalContext.current
    // Уведомления (Android 13+) — без них входящие SMS приходили бы молча
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // RESULT_OK — система подтвердила роль (в том числе если она уже была у нас)
        val granted = result.resultCode == android.app.Activity.RESULT_OK || SmsRole.isDefault(context)
        com.ogneliss.dialer.log.Logger.i("SmsRole", SmsRole.diagnostics(context))
        // 2.1.0: канал «Сообщения» — сразу при выдаче роли (SmsHealth.onRoleGranted)
        if (granted) com.ogneliss.dialer.sms.SmsHealth.onRoleGranted(context)
        if (granted && SmsRole.needsNotificationPermission(context)) {
            notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        onResult(if (granted) null else RoleAccess.helpAfterDenied(context))
    }
    return {
        val request = SmsRole.request(context)
        if (request != null) {
            RoleAccess.markRequested()
            runCatching { launcher.launch(request) }.onFailure { onResult(RoleAccess.Help.NOT_SHOWN) }
        } else if (!SmsRole.isDefault(context)) {
            onResult(RoleAccess.Help.NOT_SHOWN)
        }
    }
}
