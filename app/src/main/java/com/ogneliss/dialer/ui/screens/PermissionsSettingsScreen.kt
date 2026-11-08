package com.ogneliss.dialer.ui.screens

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.components.GroupedRow
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.util.AppPermissions
import com.ogneliss.dialer.util.AutoStart
import com.ogneliss.dialer.util.IncomingCallAccess

/**
 * ПЕРЕЧЕНЬ РАЗРЕШЕНИЙ В НАСТРОЙКАХ.
 *
 * ── Зачем нужен отдельный экран ────────────────────────────────────────────
 * Мастер при первом запуске показывает только роль «Телефон» (2.1.0),
 * остальное выдаётся само или одним системным окном. Здесь видно всё
 * сразу и можно доправить, не переустанавливая приложение.
 *
 * ── Что показываем (2.1.0) ─────────────────────────────────────────────────
 * Было: у каждой строки — пояснение, «(необязательно)», строка-подсказка
 * под системными настройками. Правило автора — не объяснять, а выполнять.
 * Стало: название, состояние (отметка) и кнопка там, где есть что
 * сделать. Работа в фоне и автозапуск — здесь, а не в мастере.
 * Системное окно разрешения не появилось (уже дважды отказали — Android
 * отвечает отказом сразу) — открываются свойства приложения, без слов.
 */
@Composable
fun PermissionsSettingsScreen(
    isDefaultDialer: Boolean,
    /** Запрос роли телефона. false — окно роли не открылось. */
    onRequestDefaultDialer: () -> Boolean,
    /** «Приложения по умолчанию» — когда окно роли не открылось. */
    onOpenSystemSettings: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val missing = remember(refreshTrigger, isDefaultDialer) {
        AppPermissions.missing(context).map { it.id }.toSet()
    }

    // Окно разрешения: ответ быстрее человеческого — окна не было
    var askedAt by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        refreshTrigger++
        val quick = SystemClock.elapsedRealtime() - askedAt < QUICK_ANSWER_MS
        if (quick && result.values.any { !it }) AppPermissions.openAppSettings(context)
    }

    // Разрешения прошивки выдают в её настройках — после возврата
    // состояние перечитывается (1.8.1)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val incoming = remember(refreshTrigger) { IncomingCallAccess.state(context) }
    val battery = remember(refreshTrigger) { AppPermissions.isIgnoringBatteryOptimizations(context) }

    /*
     * Роль SMS (2.0.0) — по желанию: строка показывает состояние и ведёт
     * в системное окно; отказ разбирает RoleAccess (ограниченные настройки
     * Android 15 для установленного из файла, окно не показалось).
     */
    var smsRoleHelp by remember { mutableStateOf<com.ogneliss.dialer.util.RoleAccess.Help?>(null) }
    val requestSmsRole = com.ogneliss.dialer.ui.components.rememberSmsRoleRequest { help ->
        refreshTrigger++
        smsRoleHelp = help
    }
    smsRoleHelp?.let { help ->
        com.ogneliss.dialer.ui.components.RoleHelpDialog(help) { smsRoleHelp = null }
    }
    val isDefaultSms = remember(refreshTrigger) { com.ogneliss.dialer.sms.SmsRole.isDefault(context) }

    val grant = stringResource(R.string.onboarding_grant)
    val assign = stringResource(R.string.onboarding_default_action)
    val open = stringResource(R.string.permissions_open)

    ScreenScaffold(
        // Как настройки Ogneliss Mail: серо-голубая страница, группы — карточками (1.5.6)
        tone = com.ogneliss.dialer.ui.theme.PageTone.TINTED,
        title = stringResource(R.string.settings_permissions),
        onBack = onBack,
        pinnedTopBar = true
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {

            // Роль диалера и разрешения — одной группой: без них звонки не работают
            item(key = "dialer_role") {
                GroupedRow(GroupPosition.FIRST) {
                    PermissionRow(
                        title = stringResource(R.string.onboarding_default_title),
                        status = if (isDefaultDialer) Status.GRANTED else Status.MISSING,
                        required = true,
                        action = assign,
                        // Окно роли не открылось — сразу «Приложения по умолчанию»
                        onAction = { if (!onRequestDefaultDialer()) onOpenSystemSettings() }
                    )
                }
            }

            val permissionGroups = AppPermissions.groups.filter { it.permissions.isNotEmpty() }
            itemsIndexed(permissionGroups, key = { _, g -> g.id }) { index, group ->
                GroupedRow(
                    if (index == permissionGroups.lastIndex) GroupPosition.LAST else GroupPosition.MIDDLE
                ) {
                    PermissionRow(
                        title = stringResource(group.titleRes),
                        status = if (group.id in missing) Status.MISSING else Status.GRANTED,
                        required = group.required,
                        action = grant,
                        onAction = {
                            askedAt = SystemClock.elapsedRealtime()
                            launcher.launch(group.toRequest.toTypedArray())
                        }
                    )
                }
            }

            /*
             * Работа телефона в фоне (2.1.0 — из мастера сюда): экран входящего
             * на прошивках со своими запретами, исключение из энергосбережения,
             * автозапуск. Разбор — IncomingCallAccess, AutoStart.
             */
            item(key = "background") {
                Spacer(Modifier.height(Space.l))
                val showIncoming = incoming != IncomingCallAccess.State.NOT_NEEDED
                val showAutostart = AutoStart.isSupported()
                if (showIncoming) {
                    GroupedRow(GroupPosition.FIRST) {
                        PermissionRow(
                            title = stringResource(R.string.incoming_access_title),
                            status = when (incoming) {
                                IncomingCallAccess.State.GRANTED -> Status.GRANTED
                                IncomingCallAccess.State.MISSING -> Status.MISSING
                                else -> Status.UNKNOWN
                            },
                            required = false,
                            action = if (incoming == IncomingCallAccess.State.MISSING) grant else open,
                            onAction = {
                                IncomingCallAccess.dismissNotice(context)
                                IncomingCallAccess.open(context)
                            }
                        )
                    }
                }
                GroupedRow(
                    when {
                        !showIncoming && !showAutostart -> GroupPosition.SINGLE
                        !showIncoming -> GroupPosition.FIRST
                        !showAutostart -> GroupPosition.LAST
                        else -> GroupPosition.MIDDLE
                    }
                ) {
                    PermissionRow(
                        title = stringResource(R.string.settings_battery),
                        status = if (battery) Status.GRANTED else Status.MISSING,
                        required = false,
                        action = grant,
                        onAction = { AppPermissions.requestIgnoreBatteryOptimizations(context) }
                    )
                }
                if (showAutostart) {
                    GroupedRow(GroupPosition.LAST) {
                        PermissionRow(
                            title = stringResource(R.string.wizard_autostart_title),
                            // Состояние прошивки не отдают — отметки не ставим
                            status = Status.UNKNOWN,
                            required = false,
                            action = open,
                            onAction = { AutoStart.open(context) }
                        )
                    }
                }
            }

            item(key = "sms_role") {
                Spacer(Modifier.height(Space.l))
                GroupedRow(GroupPosition.SINGLE) {
                    PermissionRow(
                        title = stringResource(R.string.settings_sms_default_app),
                        status = if (isDefaultSms) Status.GRANTED else Status.MISSING,
                        required = false,
                        action = assign,
                        onAction = { requestSmsRole() }
                    )
                }
            }

            // Свойства приложения в Android: всё остальное — там
            item(key = "system") {
                Spacer(Modifier.height(Space.l))
                GroupedRow(GroupPosition.SINGLE) {
                    PermissionRow(
                        title = stringResource(R.string.permissions_open_system),
                        status = null,
                        required = false,
                        action = open,
                        onAction = { AppPermissions.openAppSettings(context) }
                    )
                }
            }
        }
    }
}

/** Ответ окна разрешения быстрее этого — окна не было (Android больше не спрашивает). */
private const val QUICK_ANSWER_MS = 800L

/** Что известно о строке: выдано, не выдано или проверить нельзя. */
private enum class Status { GRANTED, MISSING, UNKNOWN }

/**
 * Строка: отметка состояния, название и кнопка. Выдано — кнопки нет;
 * проверить нельзя (автозапуск, vivo) — кнопка всегда, без отметки.
 */
@Composable
private fun PermissionRow(
    title: String,
    /** null — у строки нет состояния (системные настройки). */
    status: Status?,
    required: Boolean,
    action: String,
    onAction: () -> Unit
) {
    ListItem(
        leadingContent = {
            when (status) {
                Status.GRANTED -> Icon(
                    OgnelissIcons.CheckCircle,
                    contentDescription = stringResource(R.string.wizard_granted),
                    tint = MaterialTheme.colorScheme.primary
                )
                Status.MISSING, Status.UNKNOWN -> Icon(
                    OgnelissIcons.RadioButtonUnchecked,
                    contentDescription = null,
                    // Обязательное и не выданное — красным: без него звонки не работают
                    tint = if (status == Status.MISSING && required) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline
                )
                null -> Icon(OgnelissIcons.Settings, contentDescription = null)
            }
        },
        headlineContent = { Text(title) },
        trailingContent = {
            if (status != Status.GRANTED) {
                TextButton(onClick = onAction) { Text(action) }
            }
        },
        modifier = Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET)
    )
}
