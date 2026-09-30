package com.ogneliss.dialer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.OgnelissDivider
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.util.AppPermissions

/**
 * ПЕРЕЧЕНЬ РАЗРЕШЕНИЙ В НАСТРОЙКАХ.
 *
 * ── Зачем нужен отдельный экран ────────────────────────────────────────────
 * Мастер при первом запуске проходят один раз, и часть разрешений там
 * пропускают. Позже человек обнаруживает, что чего-то не хватает —
 * и должен иметь возможность посмотреть текущее состояние и доправить,
 * не переустанавливая приложение.
 *
 * Системный экран разрешений Android для этого не годится: он показывает
 * технические названия групп и ничего не говорит о том, что именно
 * перестанет работать в этом приложении.
 *
 * ── Что показываем ─────────────────────────────────────────────────────────
 * Состояние каждого разрешения, обязательность и последствия отказа.
 * Нажатие на невыданное запрашивает его заново; если система больше
 * не показывает диалог (пользователь отказал дважды), открываются
 * системные настройки приложения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSettingsScreen(
    isDefaultDialer: Boolean,
    /**
     * Запрос роли телефона.
     *
     * Возвращает false, если оболочка заблокировала системное окно.
     * Тогда показываем объяснение и путь в настройки — молча уводить
     * туда нельзя, человек не поймёт, куда попал.
     */
    onRequestDefaultDialer: () -> Boolean,
    onOpenSystemSettings: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val missing = remember(refreshTrigger, isDefaultDialer) {
        AppPermissions.missing(context).map { it.id }.toSet()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshTrigger++ }

    /*
     * Оболочка заблокировала выбор звонилки по умолчанию.
     *
     * Xiaomi, Samsung, Huawei и nubia требуют сперва разрешить
     * изменение системных настроек. Без объяснения человек упирается
     * в стену: кнопка нажата, ничего не произошло.
     */
    var roleBlocked by remember { mutableStateOf(false) }

    if (roleBlocked) {
        // Общий контейнер приложения вместо AlertDialog
        OgnelissDialog(
            onDismissRequest = { roleBlocked = false },
            title = stringResource(R.string.dialer_role_blocked_title),
            content = { Text(stringResource(R.string.dialer_role_blocked_text)) },
            confirmButton = {
                TextButton(onClick = {
                    roleBlocked = false
                    onOpenSystemSettings()
                }) {
                    Text(stringResource(R.string.dialer_role_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { roleBlocked = false }) {
                    Text(stringResource(R.string.nav_close))
                }
            }
        )
    }

    ScreenScaffold(
        title = stringResource(R.string.settings_permissions),
        onBack = onBack,
        pinnedTopBar = true
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {

            // Роль диалера отдельной строкой: это не разрешение,
            // а системная роль, и запрашивается иначе
            item {
                PermissionRow(
                    title = stringResource(R.string.onboarding_default_title),
                    description = stringResource(R.string.wizard_dialer_rationale),
                    granted = isDefaultDialer,
                    required = true,
                    onClick = {
                        if (!isDefaultDialer && !onRequestDefaultDialer()) {
                            roleBlocked = true
                        }
                    }
                )
                OgnelissDivider()
            }

            items(
                AppPermissions.groups.filter { it.permissions.isNotEmpty() },
                key = { it.id }
            ) { group ->
                PermissionRow(
                    title = stringResource(group.titleRes),
                    description = stringResource(group.rationaleRes),
                    granted = group.id !in missing,
                    required = group.required,
                    onClick = {
                        if (group.id in missing) {
                            launcher.launch(group.toRequest.toTypedArray())
                        }
                    }
                )
                OgnelissDivider()
            }

            // Путь для случая, когда система больше не показывает диалог:
            // после двух отказов запрос игнорируется молча, и без этой
            // строки пользователь оказывается в тупике
            item {
                // Общая строка списка: высота и подложка одни на всё
                // приложение. Разбор — в OgnelissListRow
                OgnelissListRow(
                    headline = stringResource(R.string.permissions_open_system),
                    supporting = stringResource(R.string.permissions_open_system_hint),
                    leading = { Icon(Icons.Default.Settings, null) },
                    onClick = { AppPermissions.openAppSettings(context) }
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    required: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = when {
                    granted -> MaterialTheme.colorScheme.primary
                    required -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
            )
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title)
                if (!required) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.onboarding_optional),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        supportingContent = { Text(description) },
        modifier = Modifier
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .clickable(onClick = onClick)
    )
}
