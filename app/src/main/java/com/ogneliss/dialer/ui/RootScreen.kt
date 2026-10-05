package com.ogneliss.dialer.ui

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import com.ogneliss.dialer.ui.components.OwnNumberDialog
import com.ogneliss.dialer.util.PhoneFormatter
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.style.TextAlign
import com.ogneliss.dialer.ui.adaptive.ProvidePaneLayout
import com.ogneliss.dialer.ui.adaptive.LocalNavigationRailShown
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.adaptive.LayoutMode
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.ui.screens.ContactsScreen
import com.ogneliss.dialer.ui.screens.NotesScreen
import com.ogneliss.dialer.ui.screens.RemindersScreen
import com.ogneliss.dialer.call.FakeCall
import com.ogneliss.dialer.ui.screens.MainDialerScreen
import com.ogneliss.dialer.ui.screens.SettingsCategoryScreen
import com.ogneliss.dialer.ui.screens.SettingsHomeScreen
import com.ogneliss.dialer.ui.screens.SubscriberScreen
import com.ogneliss.dialer.ui.screens.BlockListScreen
import com.ogneliss.dialer.ui.screens.SpamProtectionScreen
import com.ogneliss.dialer.ui.screens.SpeedDialScreen
import com.ogneliss.dialer.ui.screens.BackupScreen
import com.ogneliss.dialer.ui.screens.StatsScreen
import com.ogneliss.dialer.ui.screens.SwipeTuningScreen
import com.ogneliss.dialer.ui.screens.AboutScreen
import com.ogneliss.dialer.ui.screens.PermissionsSettingsScreen
import com.ogneliss.dialer.ui.screens.PinnedContactsScreen
import com.ogneliss.dialer.ui.screens.LegalDocument
import com.ogneliss.dialer.ui.screens.LegalScreen
import com.ogneliss.dialer.ui.screens.ExternalLookupScreen
import com.ogneliss.dialer.ui.screens.PostCallSheet
import com.ogneliss.dialer.ui.components.Motion
import com.ogneliss.dialer.ui.theme.Appearance
import com.ogneliss.dialer.ui.theme.LocalAppearance
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ogneliss.dialer.ui.settings.SettingsCategory

/**
 * КОРНЕВОЙ ЭКРАН ПРИЛОЖЕНИЯ.
 *
 * Держит навигацию между всеми экранами и то, что должно быть видно
 * поверх любого из них: баннер «назначьте телефоном по умолчанию»
 * и экран заметки после звонка.
 *
 * ── Почему настройки оформления задаются здесь ─────────────────────────────
 * Размер кнопок, показ фото и отклик клавиш нужны глубоко вложенным
 * компонентам. Пробрасывать их параметрами через всю иерархию означало бы
 * протащить пять аргументов до строки журнала и забыть один из них
 * в новом компоненте — именно так эти настройки и оказались нерабочими
 * до v22.0. CompositionLocal решает это в одном месте.
 *
 * ── Почему экран после звонка вне NavHost ──────────────────────────────────
 * Разговор мог закончиться, пока пользователь был в настройках.
 * Показывать заметку только на главном экране значило бы терять её
 * в половине случаев.
 */
@Composable
fun RootScreen(
    /** Разрешения только что выданы: нужно переподписаться на провайдеры. */
    permissionsJustGranted: Boolean = false,
    onPermissionsHandled: () -> Unit = {},
    isDefaultDialer: Boolean,
    /**
     * Запрос роли телефона.
     *
     * Возвращает false, если оболочка заблокировала системное окно —
     * тогда мастер показывает объяснение и путь в настройки.
     */
    onRequestDefault: () -> Boolean,
    onOpenSystemSettings: () -> Unit = {},
    prefilledNumber: String?,
    /** Счётчик намерений: см. MainDialerScreen.prefillToken. */
    prefillToken: Int = 0,
    /** Открыть клавиатуру: запрос с экрана вызова, «Добавить вызов». */
    openDialpad: Boolean = false,
    /**
     * Открыть карточку этого номера сразу при запуске.
     *
     * Приходит из уведомления напоминания: человек вернулся
     * к отложенному разговору, и ему нужна история общения,
     * а не пустой набор.
     */
    openCardForNumber: String? = null,
    /** Открыть списки блокировки сразу после запуска: из уведомления. */
    openBlockLists: Boolean = false
) {
    val nav = rememberNavController()
    val vm: DialerViewModel = hiltViewModel()

    // Экран после звонка показывается поверх ЛЮБОГО экрана приложения:
    // пользователь мог уйти в настройки, пока шёл разговор
    LaunchedEffect(permissionsJustGranted) {
        if (permissionsJustGranted) {
            vm.onPermissionsChanged()
            onPermissionsHandled()
        }
    }

    /*
     * Обновление при возвращении на передний план.
     *
     * Звонок происходит вне списка, и возвращение к нему — естественный
     * момент, чтобы показать свежие данные. Подписка на провайдер этого
     * не покрывает: во время разговора приложение в фоне, и часть
     * прошивок придерживает уведомления.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onReturnedToForeground()

                /*
                 * Строки возвращаются на место при возврате в приложение.
                 *
                 * Действие свайпа могло открыть SMS или мессенджер:
                 * процесс ушёл в фон, анимация возврата прервалась,
                 * и строка осталась сдвинутой.
                 *
                 * Подписка здесь одна на весь экран. Раньше такая же
                 * стояла в КАЖДОЙ строке: при прокрутке строки создаются
                 * и уничтожаются постоянно, и каждая добавляла и снимала
                 * подписку — десятки операций в секунду ради случая,
                 * который бывает раз в несколько минут.
                 */
                vm.closeAllSwipes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
    }

    /*
     * Пересоздание после смены языка.
     *
     * setApplicationLocales меняет конфигурацию, но композиция читает
     * строки один раз при первой отрисовке. Без пересоздания интерфейс
     * остаётся на прежнем языке до перезапуска приложения — именно это
     * и наблюдалось.
     */
    val localeChanged by vm.localeChanged.collectAsStateWithLifecycle()
    /*
     * Контекст нужен и сам по себе: документы открываются в браузере,
     * а для этого требуется startActivity. Приводить его к Activity
     * для этого незачем — хватает обычного контекста.
     */
    val context = LocalContext.current

    /** Поле коротких сообщений: одно на всё приложение. */
    val appSnackbar = remember { SnackbarHostState() }

    /*
     * Диалог своего номера.
     *
     * Открывается из настроек. В меню журнала тот же номер открывает
     * карточку — там это действие, здесь настройка.
     */
    var ownNumberOpen by remember { mutableStateOf(false) }
    val ownNumber by vm.ownNumber.collectAsStateWithLifecycle()

    if (ownNumberOpen) {
        OwnNumberDialog(
            current = ownNumber,
            onDetect = { vm.detectOwnNumber() },
            onSave = { vm.setOwnNumber(it) },
            onDismiss = { ownNumberOpen = false }
        )
    }
    val activity = context as? android.app.Activity
    LaunchedEffect(localeChanged) {
        if (localeChanged) {
            vm.onLocaleChangeHandled()
            activity?.recreate()
        }
    }

    /*
     * Переход на карточку из уведомления.
     *
     * Однократно: LaunchedEffect с ключом-номером сработает ещё раз
     * только если придёт другое уведомление, а не при каждом повороте
     * экрана.
     */
    LaunchedEffect(openBlockLists) {
        // Уведомление о заблокированном звонке: человек хочет увидеть,
        // каким правилом номер отсечён
        if (openBlockLists) nav.navigate("blocklists")
    }

    LaunchedEffect(openCardForNumber) {
        openCardForNumber?.let { number ->
            nav.navigate("subscriber/" + android.net.Uri.encode("number:$number"))
        }
    }

    val pendingPostCall by vm.pendingPostCall.collectAsStateWithLifecycle()

    // Удаление записи свайпом — после подтверждения (разбор в handleSwipe)
    val deleteRequest by vm.deleteRequest.collectAsStateWithLifecycle()
    deleteRequest?.let { group ->
        com.ogneliss.dialer.ui.components.OgnelissDialog(
            onDismissRequest = { vm.dismissDeleteRequest() },
            title = androidx.compose.ui.res.stringResource(R.string.delete_entry_confirm_title),
            content = {
                androidx.compose.material3.Text(
                    androidx.compose.ui.res.stringResource(
                        R.string.delete_entry_confirm_text,
                        group.displayName ?: com.ogneliss.dialer.util.PhoneFormatter.format(group.number)
                    )
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.confirmDeleteRequest() }) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(R.string.action_delete),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { vm.dismissDeleteRequest() }) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.nav_cancel))
                }
            }
        )
    }

    // Нужно окну после звонка: показать уже поставленную отметку

    val uiState by vm.state.collectAsStateWithLifecycle()

    // Настройки оформления задаются один раз в корне: компоненты берут
    // их сами. Раньше эти переключатели сохранялись, но ни на что
    // не влияли — их некуда было передать
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val appearance = remember(settings) {
        Appearance(
            largeButtons = settings.largeButtons,
            showPhoto = settings.showPhoto,
            haptic = settings.haptic,
            keyTones = settings.keyTones,
            buttonLabels = settings.buttonLabels
        )
    }

    pendingPostCall?.let { completed ->
        PostCallSheet(
            number = completed.number,
            displayName = completed.displayName,
            durationSec = completed.durationSec,
            isKnownContact = completed.displayName != null,
            onSaveNote = { note, tags ->
                vm.savePostCallNote(completed.number, note, tags)
            },
            // Отметка — из общего состояния, чтобы фишка показывала
            // уже поставленную раньше, а не всегда пустую
            currentMark = uiState.spamMarks[PhoneFormatter.toE164(completed.number)]?.type,
            onMark = { type ->
                vm.setSpamMark(PhoneFormatter.toE164(completed.number), type)
            },
            onAddContact = {
                vm.createContact(completed.number, completed.displayName)
                vm.dismissPostCall()
            },
            onRemind = { minutes ->
                vm.remindLater(completed.number, completed.displayName, minutes)
                vm.dismissPostCall()
            },
            onBlock = {
                vm.blockNumber(PhoneFormatter.toE164(completed.number))
                vm.dismissPostCall()
            },
            onDismiss = { vm.dismissPostCall() }
        )
    }

    CompositionLocalProvider(LocalAppearance provides appearance) {
    /*
     * ОТСТУПЫ ПОД СИСТЕМНЫЕ ПАНЕЛИ.
     *
     * Приложение работает в режиме edge-to-edge: система больше не рисует
     * подложку под строкой состояния и кнопками навигации, и содержимое
     * уезжает под них. Верхние чипы прятались под часами, нижняя кнопка
     * вызова — под кнопками навигации.
     *
     * safeDrawing учитывает всё сразу: строку состояния, навигацию,
     * вырез камеры и жестовую зону. Отдельные statusBars/navigationBars
     * покрыли бы не все случаи — например, вырез в ландшафте.
     */
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (!isDefaultDialer) {
            // Баннеру результат не нужен: он только приглашает,
            // а объяснение при отказе покажет мастер
            DefaultDialerBanner { onRequestDefault() }
        }
        /*
         * СООБЩЕНИЯ О ДЕЙСТВИЯХ — ОДНО ПОЛЕ НА ВСЁ ПРИЛОЖЕНИЕ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ИХ БЫЛО НЕ ВИДНО
         * ══════════════════════════════════════════════════════════════════
         * Сборщик жил внутри экрана журнала. Пока человек там, всё
         * работало; стоило открыть карточку или настройки — сообщения
         * уходили в пустоту. А блокируют, ставят напоминание и снимают
         * правило как раз чаще всего из карточки.
         *
         * Хуже того, сообщение с кнопкой «Отменить» пропадало вместе
         * с экраном: отменить удаление записи, уйдя в карточку,
         * было нельзя.
         *
         * Теперь поле одно и стоит над всей навигацией — сообщение
         * видно на любом экране, и отмена доступна там же.
         *
         * ⚠️ Поток сообщений один (vm.messages), и собирать его дважды
         * нельзя: канал отдаёт каждое сообщение одному получателю,
         * и половина терялась бы случайным образом.
         */
        LaunchedEffect(Unit) {
            vm.messages.collect { message ->
                val text = message.formatArg
                    ?.let { context.getString(message.textRes, it) }
                    ?: context.getString(message.textRes)

                val actionText = when (message.textRes) {
                    R.string.msg_reminder_set_inexact ->
                        context.getString(R.string.msg_reminder_exact_fix)
                    else -> context.getString(R.string.msg_undo)
                }

                val result = appSnackbar.showSnackbar(
                    message = text,
                    actionLabel = message.undo?.let { actionText },
                    duration = if (message.textRes == R.string.msg_reminder_set_inexact) {
                        SnackbarDuration.Long
                    } else {
                        SnackbarDuration.Short
                    }
                )

                if (result == SnackbarResult.ActionPerformed) message.undo?.invoke()
            }
        }

        /*
         * ══════════════════════════════════════════════════════════════════
         * БОКОВАЯ НАВИГАЦИЯ НА ШИРОКОМ ОКНЕ
         * ══════════════════════════════════════════════════════════════════
         * Этап 5 плана ADAPTIVE_PLAN.md. На планшете и раскладушке лёжа
         * до нижней кромки экрана палец тянется неудобно, а по бокам места
         * в избытке — Material советует для широких окон боковую полосу
         * навигации вместо нижней.
         *
         * Четыре главных экрана — журнал, контакты, заметки, напоминания —
         * всегда под рукой слева, одним касанием, без перелистывания.
         *
         * ⚠️ Первая версия: нижние панели экранов остаются на месте —
         * в них клавиатура набора, поиск и фильтры. Кнопки «Контакты»
         * и «Напоминания» на панели журнала на широком окне дублируют
         * полосу; убрать их — следующим шагом, когда полоса проверена
         * на устройстве.
         */
        val railLayout = LocalWindowLayout.current
        val showRail = railLayout.mode == LayoutMode.TABLET
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route.orEmpty()

        CompositionLocalProvider(LocalNavigationRailShown provides showRail) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (showRail) {
                AppNavigationRail(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (route == "dialer") {
                            nav.popBackStack("dialer", inclusive = false)
                        } else {
                            nav.navigate(route) {
                                // Один экран каждого вида в стеке: касание
                                // по «Контактам» из заметок не громоздит
                                // экраны друг на друга, а переключает
                                popUpTo("dialer") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }

            NavHost(
                nav,
                startDestination = "dialer",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                // Переходы построены на смещении и прозрачности — система
                // умеет анимировать их в обратную сторону, что нужно
                // для предпросмотра возврата на Android 14+
                enterTransition = Motion.enterForward,
                exitTransition = Motion.exitForward,
                popEnterTransition = Motion.enterBackward,
                popExitTransition = Motion.exitBackward
            ) {
                composable("dialer") {
                    /*
                     * ══════════════════════════════════════════════════════
                     * ЖУРНАЛ И КАРТОЧКА РЯДОМ НА ПЛАНШЕТЕ
                     * ══════════════════════════════════════════════════════
                     * Этап 6 плана ADAPTIVE_PLAN.md. На планшете касание
                     * записи открывает карточку справа, а не вместо журнала:
                     * журнал остаётся на месте, по нему можно идти дальше,
                     * и карточка следующего абонента — одно касание.
                     *
                     * Каждая половина получает свою раскладку по своей
                     * ширине (ProvidePaneLayout): для журнала половина
                     * планшета — это раскладушка, и клавиатура открывается
                     * внизу, а не сбоку; карточка — одной колонкой.
                     *
                     * Выбранная запись переживает поворот и складывание
                     * (rememberSaveable). На телефоне и раскладушке
                     * в портрете всё как было — карточка открывается
                     * отдельным экраном.
                     */
                    ListDetailHost(vm = vm, nav = nav) { openCard ->
                        MainDialerScreen(
                            vm = vm,
                            prefilledNumber = prefilledNumber,
                            prefillToken = prefillToken,
                            openDialpad = openDialpad,
                            onOpenContacts = { nav.navigate("contacts") },
                            onOpenSettings = { nav.navigate("settings") },
                            // Ключ группы кодирует ListDetailHost: он содержит
                            // символы, недопустимые в маршруте без экранирования
                            onOpenSubscriber = openCard,
                            onOpenBlockLists = { nav.navigate("blocklists") },
                            onOpenFakeCallPicker = { nav.navigate("contacts?pick=fake") },
                            onOpenNotes = { nav.navigate("notes") },
                            onOpenReminders = { nav.navigate("reminders") }
                        )
                    }
                }
                composable(
                    "contacts?pick={pick}",
                    arguments = listOf(
                        navArgument("pick") { defaultValue = ""; type = NavType.StringType }
                    )
                ) { entry ->
                    /*
                     * Режим выбора для закрепления.
                     *
                     * Экран контактов один и тот же: заводить второй список
                     * ради выбора означало бы поддерживать два поиска,
                     * две группировки и два набора свайпов — и они разошлись
                     * бы при первой же правке.
                     */
                    val pickMode = entry.arguments?.getString("pick").orEmpty()
                    // В режиме выбора (закрепить, псевдозвонок) касание выбирает
                    // контакт, а не открывает карточку — правая панель не нужна
                    ListDetailHost(vm = vm, nav = nav, enabled = pickMode.isEmpty()) { openCard ->
                    ContactsScreen(
                        vm = vm,
                        onOpenNotes = { nav.navigate("notes") },
                        // Псевдозвонок с экрана контактов запускается сразу,
                        // без выбора времени: окно с вариантами живёт
                        // в журнале, а здесь нужна та же кнопка «сейчас»
                        onFakeCall = { FakeCall.ringNow(context) },
                        /*
                         * Режим выбора один, а применений два: закрепление
                         * номера и выбор «звонящего» для псевдозвонка.
                         * Экран об этом не знает — он просто возвращает
                         * номер, а что с ним делать, решает маршрут.
                         */
                        pickForPinning = pickMode == "pin" || pickMode == "fake",
                        onPicked = { e164 ->
                            when (pickMode) {
                                "fake" -> vm.setFakeCallNumber(e164)
                                else -> vm.pinNumber(e164)
                            }
                            nav.popBackStack()
                        },
                        onBack = { nav.popBackStack() },
                        // Карточка открывается по ключу группы журнала;
                        // для контакта без звонков ключ строится по его id
                        onOpenContact = { id -> openCard("contact:$id") }
                    )
                    }
                }


                // Карточка абонента: вся история общения с одним человеком
                composable(
                    "subscriber/{key}",
                    arguments = listOf(navArgument("key") { type = NavType.StringType })
                ) { entry ->
                    val key = android.net.Uri.decode(entry.arguments?.getString("key").orEmpty())
                    SubscriberScreen(
                        groupKey = key,
                        vm = vm,
                        onOpenBlockLists = { nav.navigate("blocklists") },
                        onBack = { nav.popBackStack() }
                    )
                }
                // Главный экран настроек: семь категорий + поиск
                composable("settings") {
                    /*
                     * ══════════════════════════════════════════════════════
                     * НАСТРОЙКИ В ДВЕ ПАНЕЛИ НА ПЛАНШЕТЕ
                     * ══════════════════════════════════════════════════════
                     * Этап 7 плана ADAPTIVE_PLAN.md. На планшете список из
                     * десяти разделов растягивался на всю ширину, а раздел
                     * открывался отдельным экраном поверх — и чтобы
                     * заглянуть в соседний, приходилось возвращаться.
                     *
                     * Теперь слева — разделы, справа — содержимое
                     * выбранного. Переход между разделами — одно касание,
                     * без возврата. Справа нет стрелки «назад»: она
                     * закрыла бы настройки целиком, а «назад» здесь —
                     * это выбрать другой раздел.
                     *
                     * Обработка действий раздела (выгрузка журнала,
                     * переходы в списки, открытие документов) вынесена
                     * в SettingsCategoryHost — одна и та же для отдельного
                     * экрана раздела и для правой панели.
                     */
                    val settingsLayout = LocalWindowLayout.current
                    if (settingsLayout.mode == LayoutMode.TABLET) {
                        var selected by rememberSaveable {
                            mutableStateOf(SettingsCategory.entries.first())
                        }
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.width(SETTINGS_LIST_WIDTH).fillMaxHeight()) {
                                SettingsHomeScreen(
                                    onBack = { nav.popBackStack() },
                                    onOpenCategory = { selected = it },
                                    onOpenEntry = { selected = it.category }
                                )
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                SettingsCategoryHost(
                                    category = selected,
                                    vm = vm,
                                    nav = nav,
                                    onBack = { nav.popBackStack() },
                                    onOwnNumber = { ownNumberOpen = true },
                                    showBack = false
                                )
                            }
                        }
                    } else {
                        SettingsHomeScreen(
                            onBack = { nav.popBackStack() },
                            onOpenCategory = { nav.navigate(it.route) },
                            // Из поиска переходим сразу в категорию найденной настройки
                            onOpenEntry = { nav.navigate(it.category.route) }
                        )
                    }
                }

                composable("permissions") {
                    PermissionsSettingsScreen(
                        isDefaultDialer = isDefaultDialer,
                        onRequestDefaultDialer = onRequestDefault,
                        onOpenSystemSettings = onOpenSystemSettings,
                        onBack = { nav.popBackStack() }
                    )
                }

                composable("about") {
                    AboutScreen(
                        onBack = { nav.popBackStack() },
                        onOpenLegal = { doc -> nav.navigate("legal/${doc.name}") }
                    )
                }

                composable("legal/{doc}") { entry ->
                    val doc = runCatching {
                        LegalDocument.valueOf(entry.arguments?.getString("doc").orEmpty())
                    }.getOrDefault(LegalDocument.PRIVACY)
                    LegalScreen(document = doc, onBack = { nav.popBackStack() })
                }

                composable("external_lookup") {
                    ExternalLookupScreen(vm = vm, onBack = { nav.popBackStack() })
                }

                composable("pinned") {
                    PinnedContactsScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        // Выбор идёт через тот же экран контактов: заводить
                        // второй список ради одной задачи означало бы
                        // поддерживать два поиска и две группировки
                        onAddFromContacts = { nav.navigate("contacts?pick=pin") }
                    )
                }

                composable("swipe_tuning") {
                    SwipeTuningScreen(vm = vm, onBack = { nav.popBackStack() })
                }

                composable("backup") {
                    BackupScreen(vm = vm, onBack = { nav.popBackStack() })
                }

                composable("stats") {
                    StatsScreen(vm = vm, onBack = { nav.popBackStack() })
                }

                composable("speeddial") {
                    SpeedDialScreen(vm = vm, onBack = { nav.popBackStack() })
                }

                /*
                 * Заметки и напоминания.
                 *
                 * Отдельные экраны, а не фильтры списков: заметку ищут,
                 * когда забыли, к кому она относится, а напоминания нужны
                 * по времени, а не по человеку. Разбор — в шапках самих
                 * экранов.
                 */
                composable("notes") {
                    /*
                     * ══════════════════════════════════════════════════════════
                     * ВОТ ПОЧЕМУ СВАЙП НЕ РАБОТАЛ — И НИКОГДА НЕ РАБОТАЛ
                     * ══════════════════════════════════════════════════════════
                     * Экран заметок умеет листаться, но колбэки перелистывания
                     * сюда просто не передавались: onSwipeNext и onSwipePrevious
                     * оставались по умолчанию null, а при null жест выключен
                     * (enabled = false). Палец шёл вбок — и касание уходило полю
                     * поиска или кнопке фильтра, потому что жеста не было вовсе.
                     *
                     * Три захода я чинил распознавание жеста — фазу доставки
                     * событий, площадь касания, перехват у поля ввода. Всё это
                     * было не о том. Подсказка была в словах «в контактах
                     * работает, наполнение панели то же»: раз панель одна,
                     * разница должна быть не в ней, а в том, что ей передают.
                     *
                     * Передача — как в контактах: с учётом той же настройки
                     * panelSwipeNav и в том же порядке карусели
                     * журнал → контакты → заметки → напоминания.
                     */
                    ListDetailHost(vm = vm, nav = nav) { openCard ->
                    NotesScreen(
                        vm = vm,
                        onOpenCard = openCard,
                        onBack = { nav.popBackStack() },
                        // Вперёд — к напоминаниям, назад — туда, откуда пришли
                        // (из карусели это контакты), как «назад» в контактах
                        onSwipeNext = if (settings.panelSwipeNav) {
                            { nav.navigate("reminders") }
                        } else null,
                        onSwipePrevious = if (settings.panelSwipeNav) {
                            { nav.popBackStack() }
                        } else null
                    )
                    }
                }

                composable("reminders") {
                    ListDetailHost(vm = vm, nav = nav) { openCard ->
                    RemindersScreen(
                        vm = vm,
                        onOpenCard = openCard,
                        onBack = { nav.popBackStack() },
                        // Напоминания — последний экран карусели: вперёд листать
                        // некуда, назад — к заметкам. Разбор — у NotesScreen выше
                        onSwipeNext = null,
                        onSwipePrevious = if (settings.panelSwipeNav) {
                            { nav.popBackStack() }
                        } else null
                    )
                    }
                }

                // Списки блокировки и разрешений
                composable("blocklists") {
                    BlockListScreen(vm = vm, onBack = { nav.popBackStack() })
                }

                // Сводка по слоям защиты от спама
                composable("spam") {
                    SpamProtectionScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onOpenLists = { nav.navigate("blocklists") }
                    )
                }

                // По экрану на каждую категорию. Маршруты берутся из самого enum,
                // поэтому новая категория не требует правки навигации здесь.
                SettingsCategory.entries.forEach { category ->
                    composable(category.route) {
                        SettingsCategoryHost(
                            category = category,
                            vm = vm,
                            nav = nav,
                            onBack = { nav.popBackStack() },
                            onOwnNumber = { ownNumberOpen = true }
                        )
                    }
                }
            }
        }
        }
    }

        /*
         * Поле сообщений поверх всего.
         *
         * Оно должно быть видно на любом экране, включая карточку
         * и настройки, где своего Scaffold с полем нет. Поэтому стоит
         * не внутри навигации, а поверх неё.
         *
         * Отступ снизу — под панелью навигации: иначе сообщение
         * наезжает на кнопки системы.
         */
        SnackbarHost(
            appSnackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}
}


@Composable
private fun DefaultDialerBanner(onRequest: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(Space.m),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.onboarding_default_text),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onRequest) { Text(stringResource(R.string.onboarding_default_action)) }
        }
    }

}


/**
 * Открытие документа на сайте с запасным вариантом.
 *
 * Браузера может не быть вовсе — на некоторых прошивках его удаляют,
 * — а ссылка может не открыться из-за отсутствия сети. Оставлять
 * человека без документа нельзя: он имеет право их прочитать
 * в любой момент.
 */
private fun openDocument(
    context: android.content.Context,
    url: String,
    fallback: () -> Unit
) {
    val opened = runCatching {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(context.packageManager) == null) return@runCatching false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    if (!opened) fallback()
}


/**
 * Боковая полоса навигации: четыре главных экрана.
 *
 * Выбранный пункт определяется по маршруту: у контактов он с параметром
 * («contacts?pick={pick}»), поэтому сравнение по началу строки.
 */
@Composable
private fun AppNavigationRail(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        Triple("dialer", OgnelissIcons.History, R.string.nav_journal),
        Triple("contacts", OgnelissIcons.Person, R.string.nav_contacts),
        Triple("notes", OgnelissIcons.StickyNote2, R.string.nav_notes),
        Triple("reminders", OgnelissIcons.Alarm, R.string.nav_reminders)
    )

    NavigationRail(
        containerColor = PanelColors.panel()
    ) {
        Spacer(Modifier.weight(1f))
        items.forEach { (route, icon, label) ->
            val selected = currentRoute == route || currentRoute.startsWith("$route?")
            NavigationRailItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(route) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label)) }
            )
        }
        Spacer(Modifier.weight(1f))
    }
}


/**
 * Ширина колонки разделов настроек на планшете — ширина телефона:
 * названия разделов с пояснениями помещаются в строку, как в портрете.
 */
private val SETTINGS_LIST_WIDTH = 360.dp

/**
 * РАЗДЕЛ НАСТРОЕК ВМЕСТЕ С ОБРАБОТКОЙ ЕГО ДЕЙСТВИЙ.
 *
 * Раньше всё это жило прямо внутри перехода на экран раздела: запуск
 * окна сохранения файла, сообщения о выгрузке, переходы в списки
 * блокировки, быстрый набор, документы. Для настроек в две панели
 * (планшет) тот же раздел показывается справа от списка — и обработку
 * пришлось бы копировать. Вынесена сюда, одна на оба случая.
 */
@Composable
private fun SettingsCategoryHost(
    category: SettingsCategory,
    vm: DialerViewModel,
    nav: androidx.navigation.NavController,
    onBack: () -> Unit,
    onOwnNumber: () -> Unit,
    showBack: Boolean = true
) {
    val context = LocalContext.current

    /*
     * ЗАПИСЬ ФАЙЛОВ ИЗ НАСТРОЕК.
     *
     * Выгрузка журнала и сохранение технического журнала
     * пишут в файл, который человек выбирает системным
     * окном: приложение не решает за него, куда класть,
     * и не оставляет файлов, о которых владелец не знает.
     *
     * Текст собирается ПОСЛЕ выбора файла, а не до:
     * на десяти тысячах записей это секунда работы,
     * и делать её впустую, если человек передумает,
     * незачем.
     */
    var pendingText by remember {
        mutableStateOf<(suspend () -> String?)?>(null)
    }

    val settingsSnackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportDoneText = stringResource(R.string.export_done)
    val exportFailedText = stringResource(R.string.export_failed)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/*")
    ) { uri ->
        val build = pendingText
        pendingText = null
        if (uri == null || build == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val text = build()
            val ok = text != null && vm.writeTextToUri(uri, text)
            settingsSnackbar.showSnackbar(
                if (ok) exportDoneText else exportFailedText
            )
        }
    }

    SettingsCategoryScreen(
        category = category,
        vm = vm,
        onBack = onBack,
        snackbar = settingsSnackbar,
        showBack = showBack,
        onAction = { key ->
            when (key) {
                "allow_list", "block_list" -> nav.navigate("blocklists")
                "spam_overview" -> nav.navigate("spam")
                "speed_dial" -> nav.navigate("speeddial")
                "backup_export", "backup_import" -> nav.navigate("backup")

                "calllog_export" -> {
                    pendingText = { vm.buildCallLogCsv() }
                    saveLauncher.launch(vm.callLogCsvFileName())
                }

                "log_save" -> {
                    pendingText = { vm.buildLogText() }
                    saveLauncher.launch("ogneliss.log")
                }
                "stats" -> nav.navigate("stats")
                "swipe_tuning" -> nav.navigate("swipe_tuning")
                "pinned" -> nav.navigate("pinned")
                /*
                 * Свой номер: диалог поверх настроек.
                 *
                 * Отдельный экран ради одного поля был бы
                 * лишним переходом — человек пришёл
                 * изменить номер, а не изучать раздел.
                 */
                "own_number" -> onOwnNumber()

                "external_lookup" -> nav.navigate("external_lookup")
                "permissions" -> nav.navigate("permissions")
                /*
                 * ДОКУМЕНТЫ ОТКРЫВАЮТСЯ НА САЙТЕ.
                 *
                 * ── Почему не встроенный экран ────────────
                 * Встроенная копия устаревает: редакцию
                 * правят, а у людей на телефонах остаётся
                 * та, что была в их версии приложения.
                 * Для политики конфиденциальности это
                 * важно — она обязана быть актуальной.
                 *
                 * На сайте лежит та же редакция, что
                 * в репозитории: страницы собираются
                 * из тех же файлов.
                 *
                 * Встроенный экран остаётся запасным:
                 * если браузера нет или ссылка
                 * не открылась, документ показывается
                 * из приложения. Без сети человек всё
                 * равно должен их прочитать.
                 */
                "privacy" -> openDocument(
                    context, "https://ogneliss.ru/dialer/privacy"
                ) { nav.navigate("about") }

                "terms" -> openDocument(
                    context, "https://ogneliss.ru/dialer/terms"
                ) { nav.navigate("about") }

                "licenses" -> openDocument(
                    context, "https://ogneliss.ru/dialer/licenses"
                ) { nav.navigate("about") }

                /*
                 * «Версия» ведёт в магазин.
                 *
                 * Сам номер версии уже виден в подписи
                 * пункта, а нажимают на него обычно
                 * за обновлением или чтобы оставить отзыв.
                 *
                 * Если Google Play на устройстве нет
                 * (а его нет на части прошивок), открывается
                 * прежний экран «О приложении» —
                 * openDocument сам вызовет запасной путь.
                 */
                /*
                 * Выбор «звонящего» — тот же экран,
                 * что и выбор номера для закрепления:
                 * список контактов с поиском. Заводить
                 * ради этого второй такой экран
                 * не за чем.
                 */
                "fake_call_number" -> nav.navigate("contacts?pick=fake")

                "rate_app" -> openDocument(
                    context,
                    "https://play.google.com/store/apps/details" +
                        "?id=com.ogneliss.dialer"
                ) {
                    // Магазина на устройстве может не быть:
                    // тогда сообщаем, а не молчим
                    vm.notifyNoStore()
                }

                "version" -> openDocument(
                    context,
                    "https://play.google.com/store/apps/details" +
                        "?id=com.ogneliss.dialer"
                ) { nav.navigate("about") }

                else -> vm.handleSettingsAction(key)
            }
        }
    )
}


/**
 * Правая половина, пока запись не выбрана: подсказка, что сюда встанет
 * карточка. Пустая панель выглядела бы недогруженным экраном.
 */
@Composable
private fun DetailPlaceholder() {
    Column(
        Modifier.fillMaxSize().padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            OgnelissIcons.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(Space.m))
        Text(
            stringResource(R.string.detail_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}


/**
 * «СПИСОК — ПОДРОБНОСТИ» НА ПЛАНШЕТЕ ДЛЯ ЛЮБОГО СПИСКА С КАРТОЧКАМИ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОБЩИЙ
 * ══════════════════════════════════════════════════════════════════════════
 * Журнал получил раскладку «список слева, карточка справа» первым.
 * Контакты, заметки и напоминания — такие же списки, из которых
 * открывается та же карточка абонента. Копировать раскладку четыре раза
 * значило бы четыре места, где она однажды разойдётся.
 *
 * Экран-список получает функцию openCard и зовёт её, как раньше звал
 * переход на карточку. Что она сделает — решает этот компонент:
 * на планшете покажет карточку справа, на телефоне — перейдёт на экран
 * карточки, как всегда.
 *
 * Каждая половина — со своей раскладкой по своей ширине
 * (ProvidePaneLayout): для списка половина планшета — это раскладушка.
 *
 * ── enabled ────────────────────────────────────────────────────────────────
 * false — всегда как на телефоне. Нужен контактам в режиме выбора:
 * там касание выбирает контакт, и правая панель стояла бы пустой.
 */
@Composable
private fun ListDetailHost(
    vm: DialerViewModel,
    nav: androidx.navigation.NavController,
    enabled: Boolean = true,
    list: @Composable (openCard: (String) -> Unit) -> Unit
) {
    val listDetail = enabled && LocalWindowLayout.current.mode == LayoutMode.TABLET
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val openCard: (String) -> Unit = { key ->
        if (listDetail) selectedKey = key
        // Ключ кодируется: в нём бывают символы, недопустимые в маршруте
        else nav.navigate("subscriber/" + android.net.Uri.encode(key))
    }

    if (!listDetail) {
        list(openCard)
    } else {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ProvidePaneLayout { list(openCard) }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ProvidePaneLayout {
                    val key = selectedKey
                    if (key == null) {
                        DetailPlaceholder()
                    } else {
                        // key(): у каждого абонента своё состояние карточки —
                        // прокрутка, открытые диалоги
                        androidx.compose.runtime.key(key) {
                            SubscriberScreen(
                                groupKey = key,
                                vm = vm,
                                onOpenBlockLists = { nav.navigate("blocklists") },
                                onBack = { selectedKey = null },
                                showBack = false
                            )
                        }
                    }
                }
            }
        }
    }
}
