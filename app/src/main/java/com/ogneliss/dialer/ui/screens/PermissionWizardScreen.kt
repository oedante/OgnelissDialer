package com.ogneliss.dialer.ui.screens

import androidx.compose.runtime.Immutable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ogneliss.dialer.util.AutoStart
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.OgnelissPanel
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ReadableWidth
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.util.AppPermissions

/**
 * ПОШАГОВЫЙ МАСТЕР РАЗРЕШЕНИЙ ПРИ ПЕРВОМ ЗАПУСКЕ.
 *
 * ── Что было не так со старым экраном ──────────────────────────────────────
 * Все разрешения показывались одним списком карточек. Понять по нему,
 * что уже выдано, что осталось и что из этого обязательно, было нельзя:
 * галочки терялись среди текста, а кнопка «Продолжить» становилась
 * активной по неочевидному условию.
 *
 * ── Как устроено сейчас ────────────────────────────────────────────────────
 * Один экран — одно разрешение. На каждом видно:
 *   • зачем оно нужно, человеческим языком;
 *   • что перестанет работать без него;
 *   • обязательное оно или нет;
 *   • выдано уже или нет — крупно и однозначно.
 *
 * Кнопка «Далее» есть всегда: пропустить можно любой шаг, включая
 * обязательный. Запрещать это неправильно — пользователь вправе
 * не давать доступ. Но последствия он должен увидеть заранее,
 * а не обнаружить пустой журнал и решить, что приложение сломано.
 *
 * ── Порядок шагов ──────────────────────────────────────────────────────────
 * Сначала роль диалера: без неё Android не передаёт приложению управление
 * вызовами, и остальные разрешения бесполезны. Дальше по убыванию
 * важности, необязательные — в конце, когда пользователь уже втянулся.
 */
@Composable
fun PermissionWizardScreen(
    isDefaultDialer: Boolean,
    /**
     * Запрос роли телефона.
     *
     * Возвращает false, если системное окно не открылось: оболочка
     * его заблокировала. Тогда показываем объяснение — молча уводить
     * в настройки нельзя, человек не поймёт, куда попал.
     */
    onRequestDefaultDialer: () -> Boolean,
    onOpenSystemSettings: () -> Unit = {},
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var stepIndex by remember { mutableIntStateOf(0) }

    // Пересчитывается после каждого запроса: результат должен быть виден сразу
    var refreshTrigger by remember { mutableIntStateOf(0) }

    /*
     * Оболочка заблокировала выбор звонилки по умолчанию.
     *
     * Xiaomi, Samsung, Huawei, nubia требуют сперва разрешить изменение
     * системных настроек. Без объяснения человек упирается в стену:
     * кнопка нажата, ничего не произошло.
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

    val steps = remember(isDefaultDialer, refreshTrigger) {
        buildSteps(context, isDefaultDialer)
    }
    val step = steps.getOrNull(stepIndex)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Результат придёт в следующем кадре: обновляем состояние экрана
        refreshTrigger++
    }

    if (step == null) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    Scaffold(
        // Insets обрабатывает Scaffold: без него содержимое уезжает
        // под строку состояния и под кнопки навигации
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            WizardButtons(
                isLastStep = stepIndex == steps.lastIndex,
                isGranted = step.isGranted,
                isRequired = step.required,
                onGrant = {
                    when {
                        step.id == STEP_DIALER -> {
                            // false означает, что оболочка заблокировала
                            // системное окно: показываем объяснение
                            if (!onRequestDefaultDialer()) roleBlocked = true
                        }
                        step.permissions.isNotEmpty() ->
                            permissionLauncher.launch(step.permissions.toTypedArray())
                        else -> step.openSettings?.invoke()
                    }
                },
                onNext = {
                    if (stepIndex == steps.lastIndex) onFinished() else stepIndex++
                }
            )
        }
    ) { padding ->
        // Не шире 640 dp по центру на широком окне: разбор в ReadableWidth
        ReadableWidth {
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // Индикатор прогресса: видно, сколько осталось, и что процесс
            // конечен. Без него длинная череда запросов утомляет
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            Text(
                stringResource(R.string.wizard_step_of, stepIndex + 1, steps.size),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(32.dp))

            AnimatedContent(targetState = step, label = "step") { current ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusIcon(current.icon, current.isGranted)

                    Spacer(Modifier.height(24.dp))

                    Text(
                        stringResource(current.titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    // Обязательное или нет — сразу под заголовком,
                    // чтобы решение принималось осознанно
                    RequirementBadge(current.required, current.isGranted)

                    Spacer(Modifier.height(24.dp))

                    Text(
                        stringResource(current.rationaleRes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    // Что перестанет работать: главное отличие от старого
                    // экрана. Пользователь должен понимать цену отказа
                    // до того, как откажется
                    if (!current.isGranted) {
                        Spacer(Modifier.height(20.dp))
                        OgnelissPanel(
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.padding(FlowUi.contentPadding), verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.Info, null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(current.consequenceRes),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
        }
    }
}

/** Крупная отметка состояния: выдано или нет. Понятно с одного взгляда. */
@Composable
private fun StatusIcon(icon: ImageVector, granted: Boolean) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = if (granted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(48.dp))
            }
        }
        if (granted) {
            Icon(
                Icons.Default.CheckCircle, null,
                Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RequirementBadge(required: Boolean, granted: Boolean) {
    val (textRes, color) = when {
        granted -> R.string.wizard_granted to MaterialTheme.colorScheme.primary
        required -> R.string.wizard_required to MaterialTheme.colorScheme.error
        else -> R.string.wizard_optional to MaterialTheme.colorScheme.outline
    }
    Text(
        stringResource(textRes),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = color
    )
}

/**
 * Кнопки внизу.
 *
 * «Далее» доступна всегда, даже если разрешение не выдано: запрещать
 * пользователю отказываться неправильно. Но текст кнопки меняется —
 * «Пропустить» вместо «Далее», чтобы отказ был осознанным действием,
 * а не машинальным нажатием.
 */
@Composable
private fun WizardButtons(
    isLastStep: Boolean,
    isGranted: Boolean,
    isRequired: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp)
        ) {
            if (!isGranted) {
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wizard_grant))
                }
                Spacer(Modifier.height(8.dp))
            }

            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        when {
                            isLastStep -> R.string.wizard_finish
                            isGranted -> R.string.wizard_next
                            else -> R.string.wizard_skip
                        }
                    )
                )
            }
        }
    }
}

// ── Описание шагов ─────────────────────────────────────────────────────────

private const val STEP_DIALER = "dialer"
private const val STEP_BATTERY = "battery"
private const val STEP_AUTOSTART = "autostart"

private /**
 * @Immutable: шаг мастера перерисовывается при каждом изменении
 * состояния разрешений, а поле permissions типа List делало бы
 * его нестабильным.
 */
@Immutable
data class WizardStep(
    val id: String,
    val icon: ImageVector,
    val titleRes: Int,
    val rationaleRes: Int,
    /** Что перестанет работать без этого разрешения. */
    val consequenceRes: Int,
    val required: Boolean,
    val isGranted: Boolean,
    val permissions: List<String> = emptyList(),
    val openSettings: (() -> Unit)? = null
)

private fun buildSteps(
    context: android.content.Context,
    isDefaultDialer: Boolean
): List<WizardStep> = buildList {

    // Роль диалера первой: без неё остальное бесполезно
    add(
        WizardStep(
            id = STEP_DIALER,
            icon = Icons.Default.Smartphone,
            titleRes = R.string.onboarding_default_title,
            rationaleRes = R.string.wizard_dialer_rationale,
            consequenceRes = R.string.wizard_dialer_consequence,
            required = true,
            isGranted = isDefaultDialer
        )
    )

    val icons = mapOf(
        "phone" to Icons.Default.Call,
        "call_log" to Icons.Default.History,
        "contacts" to Icons.Default.Contacts,
        "notifications" to Icons.Default.Notifications,
        "audio" to Icons.Default.Headphones
    )
    val consequences = mapOf(
        "phone" to R.string.wizard_phone_consequence,
        "call_log" to R.string.wizard_calllog_consequence,
        "contacts" to R.string.wizard_contacts_consequence,
        "notifications" to R.string.wizard_notifications_consequence,
        "audio" to R.string.wizard_audio_consequence
    )

    // Обязательные раньше необязательных: пользователь соглашается
    // на важное, пока внимание ещё свежее
    AppPermissions.groups
        .filter { it.permissions.isNotEmpty() }
        .sortedByDescending { it.required }
        .forEach { group ->
            add(
                WizardStep(
                    id = group.id,
                    icon = icons[group.id] ?: Icons.Default.Lock,
                    titleRes = group.titleRes,
                    rationaleRes = group.rationaleRes,
                    consequenceRes = consequences[group.id] ?: R.string.wizard_generic_consequence,
                    required = group.required,
                    isGranted = AppPermissions.missing(context).none { it.id == group.id },
                    permissions = group.permissions
                )
            )
        }

    /*
     * РАБОТА В ФОНЕ — ШАГ МАСТЕРА, А НЕ ПУНКТ НАСТРОЕК.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ЗДЕСЬ
     * ══════════════════════════════════════════════════════════════════════
     * Я сначала спрятал это в настройки, рассуждая, что пятая просьба
     * при установке выглядит навязчиво. Это была ошибка.
     *
     * Человек не пойдёт разбираться, почему пропущенные не появляются
     * в журнале. Он решит, что приложение сломано, и удалит его —
     * и будет прав по факту: без исключения из энергосбережения
     * наблюдатель за журналом не просыпается, и вызовы действительно
     * теряются до следующего открытия.
     *
     * Лучше спросить один раз при установке, чем потерять человека
     * через неделю.
     *
     * ── Почему не обязательный ─────────────────────────────────────────────
     * Приложение без этого работает — просто журнал обновляется
     * только когда его открывают. Помечать шаг обязательным значило бы
     * не пустить дальше того, кто отказался: хуже, чем работать
     * с оговоркой.
     */
    add(
        WizardStep(
            id = STEP_BATTERY,
            icon = Icons.Default.BatteryAlert,
            titleRes = R.string.settings_battery,
            rationaleRes = R.string.wizard_battery_rationale,
            consequenceRes = R.string.wizard_battery_consequence,
            required = false,
            isGranted = AppPermissions.isIgnoringBatteryOptimizations(context),
            openSettings = { AppPermissions.requestIgnoreBatteryOptimizations(context) }
        )
    )

    /*
     * АВТОЗАПУСК — ТОЛЬКО НА ОБОЛОЧКАХ, ГДЕ ОН ЕСТЬ.
     *
     * ── Почему нельзя проверить, выдан ли ──────────────────────────────────
     * Общего способа нет: у Xiaomi, Huawei, Oppo это собственные экраны
     * в собственных оболочках, и состояние они наружу не отдают.
     *
     * Поэтому шаг не отмечается выполненным никогда — он остаётся
     * подсказкой, а не проверкой. Это честнее, чем показывать галочку,
     * которая ничего не значит.
     *
     * ── Почему показываем не всем ──────────────────────────────────────────
     * На чистом Android такого экрана нет, и пункт вёл бы в никуда.
     * Показываем только там, где производитель известен своими
     * ограничениями.
     */
    if (AutoStart.isSupported()) {
        add(
            WizardStep(
                id = STEP_AUTOSTART,
                icon = Icons.Default.RestartAlt,
                titleRes = R.string.wizard_autostart_title,
                rationaleRes = R.string.wizard_autostart_rationale,
                consequenceRes = R.string.wizard_autostart_consequence,
                required = false,
                isGranted = false,
                openSettings = { AutoStart.open(context) }
            )
        )
    }
}
