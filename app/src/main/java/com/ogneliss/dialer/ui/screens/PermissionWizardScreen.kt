package com.ogneliss.dialer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ReadableWidth
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.PanelReachesNavBar
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.util.AppPermissions
import com.ogneliss.dialer.util.IncomingCallAccess
import kotlinx.coroutines.delay

/**
 * МАСТЕР ПЕРВОГО ЗАПУСКА — ТОЛЬКО ТО, ЧТО МОЖЕТ СДЕЛАТЬ ЛИШЬ ЧЕЛОВЕК (2.1.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * БЫЛО — СТАЛО — ПОЧЕМУ
 * ══════════════════════════════════════════════════════════════════════════
 * Было: страница на каждое разрешение (телефон, журнал, контакты,
 * уведомления, звук), работа в фоне, автозапуск, экран входящего;
 * на каждой — пояснение, «что перестанет работать», счётчик шагов.
 *
 * Правило автора (07.11.2026): «Пользователь не должен думать и выбирать,
 * чтобы приложение работало как нужно, на любой прошивке, с сервисами
 * Google и без. Не показывать ошибки и объяснения — выполнять или
 * пропускать».
 *
 * Стало:
 * • «Телефон по умолчанию» — страница остаётся: роль выдаёт только
 *   системное окно. Окно не открылось — сразу «Приложения по умолчанию».
 * • Разрешения — без страниц. С Android 10 роль «Телефон» сама выдаёт
 *   телефон, журнал, контакты, уведомления (13+), «поверх других»
 *   и работу в фоне; чего не хватило — одно системное окно на всё
 *   ([MissingPermissionsRequest]). На Android 8–9 — то же одно окно.
 * • Работа в фоне и автозапуск — не в мастере, а строками в «Настройки →
 *   Разрешения»: проверить их нельзя, а без них звонки работают.
 * • «Экран входящего вызова» — только где запрет известен заранее
 *   (IncomingCallAccess.stepBeforeCalls): MIUI/HyperOS с подтверждённым
 *   запретом, ColorOS без «поверх других». Один раз за всё время.
 */
@Composable
fun PermissionWizardScreen(
    isDefaultDialer: Boolean,
    /**
     * Запрос роли телефона. false — системное окно не открылось
     * (оболочка его заблокировала): тогда сразу [onOpenSystemSettings].
     */
    onRequestDefaultDialer: () -> Boolean,
    /** «Приложения по умолчанию» — когда окно роли не открылось. */
    onOpenSystemSettings: () -> Unit = {},
    onFinished: () -> Unit
) {
    val context = LocalContext.current

    // Разрешения прошивки выдают в её настройках — после возврата
    // состояние перечитывается
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var dialerSkipped by rememberSaveable { mutableStateOf(false) }
    var incomingSkipped by rememberSaveable { mutableStateOf(false) }
    // Шаг экрана входящего — один раз за всё время: запомнено при показе
    val incomingOffered = rememberSaveable { !IncomingCallAccess.stepShownBefore(context) }
    val incomingStep = remember(refresh) { IncomingCallAccess.stepBeforeCalls(context) }

    val page: WizardPage? = when {
        !isDefaultDialer && !dialerSkipped -> WizardPage.Dialer
        // Без роли «Телефон» экран вызова не наш — спрашивать о нём незачем
        isDefaultDialer && incomingOffered && !incomingSkipped && incomingStep != null ->
            WizardPage.Incoming(incomingStep)
        else -> null
    }

    if (page == null) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    if (page is WizardPage.Incoming) {
        LaunchedEffect(Unit) { IncomingCallAccess.markStepShown(context) }
    }

    CompositionLocalProvider(
        com.ogneliss.dialer.ui.theme.LocalPageTone provides com.ogneliss.dialer.ui.theme.PageTone.TINTED
    ) {
        Scaffold(
            // Как настройки Ogneliss Mail: серо-голубая страница (1.5.6)
            containerColor = com.ogneliss.dialer.ui.theme.OgnelissTheme.pageColor(com.ogneliss.dialer.ui.theme.PageTone.TINTED),
            // Insets обрабатывает Scaffold: без него содержимое уезжает
            // под строку состояния и под кнопки навигации
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                WizardButtons(
                    actionRes = page.actionRes,
                    onAction = {
                        when (page) {
                            // Окно роли не открылось — сразу системный выбор, без объяснений
                            WizardPage.Dialer -> if (!onRequestDefaultDialer()) onOpenSystemSettings()
                            is WizardPage.Incoming -> IncomingCallAccess.open(context)
                        }
                    },
                    onSkip = {
                        when (page) {
                            WizardPage.Dialer -> dialerSkipped = true
                            is WizardPage.Incoming -> incomingSkipped = true
                        }
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
                        .padding(horizontal = Space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(targetState = page, label = "step") { current ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PageIcon(current.icon)
                            Spacer(Modifier.height(Space.xl))
                            Text(
                                stringResource(current.titleRes),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(Space.m))
                            // Одна короткая строка: что сделать, без «что будет, если нет»
                            Text(
                                stringResource(current.textRes),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Страница мастера: что показать и какая кнопка. */
private sealed class WizardPage(
    val icon: ImageVector,
    val titleRes: Int,
    val textRes: Int,
    val actionRes: Int
) {
    /** Роль «Телефон по умолчанию». */
    data object Dialer : WizardPage(
        OgnelissIcons.Smartphone,
        R.string.onboarding_default_title,
        R.string.onboarding_default_text,
        R.string.onboarding_default_action
    )

    /** Экран входящего: что разрешить на этой прошивке. */
    data class Incoming(val step: IncomingCallAccess.Step) : WizardPage(
        OgnelissIcons.PhonelinkLock,
        R.string.incoming_access_title,
        if (step == IncomingCallAccess.Step.MIUI) R.string.incoming_step_miui else R.string.incoming_step_overlay,
        R.string.onboarding_grant
    )
}

/**
 * Нужен ли мастер при запуске: роли «Телефон» нет или есть шаг экрана
 * входящего, который ещё не предлагали (2.1.0). Иначе — сразу журнал.
 */
fun wizardNeeded(context: android.content.Context, isDefaultDialer: Boolean): Boolean =
    !isDefaultDialer ||
        (IncomingCallAccess.stepBeforeCalls(context) != null && !IncomingCallAccess.stepShownBefore(context))

/** Крупный значок шага. */
@Composable
private fun PageIcon(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(96.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(48.dp))
        }
    }
}

/** Кнопки внизу: действие и «Пропустить» — шаг не обязателен. */
@Composable
private fun WizardButtons(actionRes: Int, onAction: () -> Unit, onSkip: () -> Unit) {
    // Цвет нижней панели, как на остальных экранах (1.4.0)
    Surface(color = PanelColors.panel()) {
        PanelReachesNavBar()
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Space.xl)
        ) {
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(actionRes))
            }
            Spacer(Modifier.height(Space.s))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wizard_skip))
            }
        }
    }
}

/** Пауза после возврата на экран: следом может открываться окно роли SMS. */
private const val PROMPT_SETTLE_MS = 700L

/**
 * НЕДОСТАЮЩИЕ РАЗРЕШЕНИЯ — ОДНИМ СИСТЕМНЫМ ОКНОМ, БЕЗ СВОИХ СТРАНИЦ (2.1.0).
 *
 * Роль «Телефон» с Android 10 выдаёт почти всё сама; чего не хватило
 * (Android 8–9, Bluetooth, отозванное потом) — одно окно на все, раз за
 * запуск приложения и только у телефона по умолчанию. Окно ждёт, пока
 * приложение на экране: сразу после роли телефона открывается окно роли
 * SMS, и два окна поверх друг друга сбивали бы.
 */
@Composable
fun MissingPermissionsRequest(isDefaultDialer: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* ответ разберёт MainActivity при возврате (permissionsJustGranted) */ }
    LaunchedEffect(isDefaultDialer) {
        if (!isDefaultDialer) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(PROMPT_SETTLE_MS)
            if (AppPermissions.promptedThisRun) return@repeatOnLifecycle
            val missing = AppPermissions.missingToRequest(context)
            if (missing.isEmpty()) return@repeatOnLifecycle
            AppPermissions.promptedThisRun = true
            runCatching { launcher.launch(missing) }
        }
    }
}
