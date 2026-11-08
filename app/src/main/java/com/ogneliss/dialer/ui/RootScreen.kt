package com.ogneliss.dialer.ui

import com.ogneliss.dialer.ui.components.CardsBlurState
import com.ogneliss.dialer.ui.components.LocalCardsBlur
import com.ogneliss.dialer.ui.components.LocalTouchAnchor
import com.ogneliss.dialer.ui.components.trackTouch
import com.ogneliss.dialer.ui.components.rememberTouchAnchor
import com.ogneliss.dialer.ui.components.blurBehindCards
import com.ogneliss.dialer.ui.components.SimChoiceOverlay
import androidx.compose.material3.MaterialTheme
import com.ogneliss.dialer.ui.theme.LocalNavBarStrip
import com.ogneliss.dialer.ui.theme.NavBarStripState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalResources
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
import com.ogneliss.dialer.ui.screens.SettingReveal
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
import com.ogneliss.dialer.ui.components.MainSection
import com.ogneliss.dialer.ui.theme.Appearance
import com.ogneliss.dialer.ui.theme.LocalAppearance
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ogneliss.dialer.ui.settings.SettingsCategory
import com.ogneliss.dialer.ui.settings.SettingsRegistry
import com.ogneliss.dialer.ui.settings.SettingEntry
import com.ogneliss.dialer.ui.settings.SettingKind
import com.ogneliss.dialer.ui.help.FaqLink
import com.ogneliss.dialer.ui.screens.FaqScreen

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
    openBlockLists: Boolean = false,
    /** Открыть переписку (2.0.0): «Написать», ссылка sms:, уведомление. */
    openConversation: com.ogneliss.dialer.ui.conversation.ConversationRequest? = null,
    /** Открыть раздел (2.1.0): касание по значку «SMS» или «Контакты» на главном экране. */
    openSectionRoute: String? = null
) {
    val nav = rememberNavController()

    /*
     * ПЕРЕХОД МЕЖДУ РАЗДЕЛАМИ (1.6.0, вариант «Б»).
     *
     * Журнал, контакты, заметки, напоминания — соседи, а не экраны друг
     * в друге: в стеке под любым из них только журнал. «Назад» из контактов
     * ведёт в журнал, а не в то, что открывали перед ними, и экраны не
     * громоздятся при переключении туда-обратно. Так же ведёт боковая
     * полоса планшета; на телефоне — переключатель на панели и список
     * разделов по долгому нажатию на него.
     */
    val openSection: (MainSection) -> Unit = { section ->
        if (section == MainSection.JOURNAL) {
            nav.popBackStack(MainSection.JOURNAL.route, inclusive = false)
        } else {
            nav.navigate(section.route) {
                popUpTo(MainSection.JOURNAL.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
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
    /*
     * Беда приёма SMS (2.1.0, SmsHealth): уведомления о SMS выключены или на
     * Xiaomi выключен автозапуск, когда диалер — приложение SMS. Критично
     * (SMS приходят молча или теряются) — полоса сверху на любом экране,
     * пересчёт при каждом возвращении в приложение.
     */
    var smsProblem by remember { mutableStateOf<com.ogneliss.dialer.sms.SmsHealth.Problem?>(null) }
    val healthContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onReturnedToForeground()
                smsProblem = runCatching { com.ogneliss.dialer.sms.SmsHealth.problem(healthContext) }.getOrNull()

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

    /*
     * Настройка, найденная поиском, — для раздела, который откроется
     * (1.5.8): если она под «Дополнительно», раздел раскроет группу.
     * Разбор у SettingReveal. Одна на телефон и планшет: поиск и раздел —
     * разные экраны навигации, а на планшете — соседние панели.
     */
    var settingsReveal by remember { mutableStateOf<SettingReveal?>(null) }

    /*
     * «ВОПРОСЫ И ОТВЕТЫ» (1.6.0): кнопка под ответом делает то же, что пункт
     * в настройках, — тем же кодом (runSettingAction).
     *
     * Переключатель и выбор — раздел открывается и подсвечивает пункт, как
     * после поиска по настройкам. Действие и ссылка в системные настройки
     * выполняются сразу: «Работа в фоне» открывает системное окно, а не
     * раздел, где пришлось бы нажимать ещё раз. Кроме действий, которым
     * нужен сам экран раздела (ACTIONS_OF_SETTINGS_SCREEN), и опасных — им
     * нужно его подтверждение: для них раздел открывается, как для
     * переключателя.
     */
    val openFaq: (String?) -> Unit = { question ->
        nav.navigate(if (question == null) "faq" else "faq?q=$question")
    }
    val revealSetting: (SettingEntry) -> Unit = { entry ->
        settingsReveal = SettingReveal(entry.id)
        nav.navigate(entry.category.route)
    }
    val openFaqLink: (FaqLink) -> Unit = { link ->
        when (link) {
            is FaqLink.Setting -> SettingsRegistry.byId(link.entryId)?.let { entry ->
                when (val kind = entry.kind) {
                    is SettingKind.SystemLink -> vm.handleSettingsAction("system:${kind.key}")
                    is SettingKind.Action -> {
                        if (entry.dangerous || kind.key in ACTIONS_OF_SETTINGS_SCREEN) {
                            revealSetting(entry)
                        } else {
                            runSettingAction(kind.key, context, nav, vm, { ownNumberOpen = true }, saveToFile = null)
                        }
                    }
                    else -> revealSetting(entry)
                }
            }
            is FaqLink.Section -> nav.navigate(link.category.route)
            // Оболочка не показала системное окно — тогда системные «Приложения по умолчанию»
            FaqLink.DefaultDialer -> {
                val shown = onRequestDefault()
                if (!shown) vm.handleSettingsAction("system:default_apps")
            }
            // Лицензия — встроенным текстом: ответ не должен уводить на сайт
            is FaqLink.Document -> nav.navigate("legal/${link.document.name}")
            FaqLink.SourceCode -> openDocument(context, com.ogneliss.dialer.util.AppLinks.SOURCE_CODE) {
                nav.navigate("about")
            }
            FaqLink.Mail -> vm.openEmail(com.ogneliss.dialer.util.AppLinks.SUPPORT_EMAIL)
        }
    }

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

    // Значок раздела на главном экране (2.1.0): ключ — и раздел, и счётчик намерений
    LaunchedEffect(openSectionRoute, prefillToken) {
        MainSection.entries.firstOrNull { it.route == openSectionRoute }?.let(openSection)
    }

    LaunchedEffect(openCardForNumber) {
        openCardForNumber?.let { number ->
            nav.navigate("subscriber/" + android.net.Uri.encode("number:$number"))
        }
    }

    /*
     * Переписка (2.0.0). Ключ — и запрос, и счётчик намерений: второе
     * «Написать» тому же человеку после «Назад» — то же намерение,
     * и без счётчика экран бы не открылся. Та же переписка уже открыта —
     * она заменяется, а не копится в стеке.
     */
    LaunchedEffect(openConversation, prefillToken) {
        openConversation?.let { request ->
            nav.navigate(request.route()) {
                popUpTo(com.ogneliss.dialer.ui.conversation.ConversationRequest.ROUTE) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val pendingPostCall by vm.pendingPostCall.collectAsStateWithLifecycle()

    /*
     * ОКНО ПОСЛЕ ЗВОНКА НЕ ПЕРЕЖИВАЕТ УХОД ИЗ ПРИЛОЖЕНИЯ (2.0.2).
     *
     * Жалоба (07.11.2026): поговорил, окно после звонка открылось, человек
     * вышел на рабочий стол, не закрыв его. Потом «Позвонить» на номере
     * в браузере открывал диалер — а вместо набора стояло то самое окно
     * от прошлого звонка.
     *
     * • Окно показано, приложение ушло в фон (ON_STOP) — окно закрыто:
     *   человек решил, что заметка не нужна.
     * • Пришло новое намерение (номер из браузера, «Написать», уведомление
     *   о пропущенном) — человек пришёл за другим, окно закрывается.
     * • Возвращение в приложение — событие старше срока годности (2 мин)
     *   сбрасывается сразу, а не ждёт пересчёта.
     */
    val postCallLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(postCallLifecycle, pendingPostCall != null) {
        val shown = pendingPostCall != null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (shown) vm.dismissPostCall()
                Lifecycle.Event.ON_START -> vm.dropExpiredPostCall()
                else -> Unit
            }
        }
        postCallLifecycle.addObserver(observer)
        onDispose { postCallLifecycle.removeObserver(observer) }
    }
    LaunchedEffect(prefillToken, prefilledNumber, openConversation, openCardForNumber) {
        if (prefillToken > 0 || prefilledNumber != null || openConversation != null || openCardForNumber != null) {
            vm.dismissPostCall()
        }
    }

    /*
     * Здесь было окно «Удалить из журнала?» для удаления свайпом.
     * 2.1.0: свайп удаляет сразу, с «Отменить» (правило автора 07.11.2026:
     * не спрашивать там, где есть отмена) — разбор в handleSwipe.
     */

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
            buttonLabels = settings.buttonLabels,
            listDensity = settings.listDensity
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
     *
     * ── Верх — каждому экрану свой (1.5.8, обзор интерфейса, У3) ───────────
     * Корень отступал и сверху, и под строкой состояния всегда была полоса
     * фона — фото в карточке контакта начиналось под ней. Теперь корень
     * отступает по бокам и снизу, а верхний отступ берёт сам экран: его
     * Scaffold и TopAppBar учитывают строку состояния (contentWindowInsets,
     * TopAppBarDefaults.windowInsets), и фон экрана уходит под строку
     * состояния. Карточка контакта кладёт туда фото.
     *
     * Кто ещё стоит сверху, отступает сам: баннер «сделать основным»
     * (и тогда экраны под ним отступа сверху не получают — он учтён),
     * журнал с карточкой рядом на планшете (ListDetailHost) — как раньше.
     * Окна согласия и мастера разрешений рисуются не здесь (MainActivity).
     */
    // Полоса под системной навигацией — цвета нижней панели (1.5.1):
    // разбор в ui/theme/NavBarStrip.kt
    val navBarStrip = remember { NavBarStripState() }
    val navBarStripColor by animateColorAsState(
        if (navBarStrip.panels > 0) PanelColors.panel() else MaterialTheme.colorScheme.background,
        animationSpec = tween(Motion.NORMAL_MS),
        label = "navBarStrip"
    )
    /*
     * ВЫБОР SIM — ОДИН ХОЗЯИН НА ВСЁ ПРИЛОЖЕНИЕ (1.8.0).
     *
     * Долгое нажатие на любую кнопку звонка или SMS (журнал, контакты,
     * карточка, карточки действий, заметки, напоминания) зовёт
     * vm.chooseSim; карточки выбора показываются здесь, у места нажатия.
     * Место — последнее касание в окне приложения: долгое нажатие — это
     * касание, которое не отпустили (TouchAnchor). Экран под карточками
     * размывается, как под действиями по долгому нажатию.
     */
    val simRequest by vm.simRequest.collectAsStateWithLifecycle()
    val rootAnchor = rememberTouchAnchor()
    // Любое окно карточек (действия, SIM, номер, напоминание, заметка) размывает весь экран
    val cardsBlur = remember { CardsBlurState() }
    // 2.1.0: звонить и писать сейчас можно? Нельзя — кнопки серые (CallAvailability)
    val callAvailability = com.ogneliss.dialer.util.rememberCallAvailability()
    CompositionLocalProvider(
        LocalNavBarStrip provides navBarStrip,
        LocalTouchAnchor provides rootAnchor,
        LocalCardsBlur provides cardsBlur,
        com.ogneliss.dialer.util.LocalCallAvailability provides callAvailability
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .trackTouch(rootAnchor)
            .blurBehindCards(cardsBlur.open > 0)
    ) {
    simRequest?.let { request ->
        SimChoiceOverlay(
            request = request,
            anchorY = rootAnchor.screenY,
            onDismiss = { vm.dismissSimRequest() }
        )
    }
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(navBarStripColor)
    )
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
    ) {
        /*
         * «ВЕРНУТЬСЯ К ВЫЗОВУ» (2.1.0). Было: ушёл с экрана вызова во время
         * разговора — вернуться можно было только из шторки. Стало: пока
         * идёт, удержан, набирается или звонит вызов, сверху — зелёная полоса,
         * касание открывает экран вызова. Вызовов нет — полосы нет.
         */
        val liveCalls by com.ogneliss.dialer.call.CallManager.calls.collectAsStateWithLifecycle()
        val callRevision by com.ogneliss.dialer.call.CallManager.revision.collectAsStateWithLifecycle()
        @Suppress("DEPRECATION")
        val callToReturn = remember(liveCalls, callRevision) {
            liveCalls.any { com.ogneliss.dialer.call.CallScreenRules.returnable(it.state, it.parent != null) }
        }
        /*
         * «Телефон не дал показать экран вызова» (1.8.1) — после входящего,
         * у которого экран вызова так и не появился (IncomingScreen.checkShown).
         * Подсказка — тому, у кого действительно не работает, а не каждому.
         */
        val incomingBlocked by remember {
            com.ogneliss.dialer.util.IncomingCallAccess.blockedNotice(context)
        }.collectAsStateWithLifecycle()
        val blockedShown = isDefaultDialer && incomingBlocked == true
        /*
         * Под строку состояния отступает только верхняя полоса (2.1.0):
         * было — каждая сама, и две полосы подряд давали двойной отступ,
         * а экраны под полосой «не дал показать» отступали ещё раз.
         */
        if (callToReturn) {
            ReturnToCallStrip(padTop = true) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(context, com.ogneliss.dialer.call.InCallActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        if (!isDefaultDialer) {
            // Окно роли не открылось — сразу «Приложения по умолчанию», без
            // объяснений (2.1.0, правило автора «выполнить, а не объяснять»)
            DefaultDialerBanner(padTop = !callToReturn) {
                if (!onRequestDefault()) onOpenSystemSettings()
            }
        }
        if (blockedShown) {
            IncomingBlockedBanner(
                padTop = !callToReturn && isDefaultDialer,
                onFix = {
                    com.ogneliss.dialer.util.IncomingCallAccess.dismissNotice(context)
                    com.ogneliss.dialer.util.IncomingCallAccess.open(context)
                },
                onDismiss = { com.ogneliss.dialer.util.IncomingCallAccess.dismissNotice(context) }
            )
        }
        // В разделе SMS полоса стоит в самом списке — здесь не дублируем
        val stripRoute by nav.currentBackStackEntryAsState()
        val smsStripShown = smsProblem != null &&
            stripRoute?.destination?.route != MainSection.MESSAGES.route
        if (smsStripShown) smsProblem?.let { problem ->
            Box(Modifier.statusBarSpace(!callToReturn && isDefaultDialer && !blockedShown)) {
                com.ogneliss.dialer.ui.conversation.SmsProblemStrip(problem) {
                    com.ogneliss.dialer.sms.SmsHealth.fix(context, problem)
                }
            }
        }
        val topStripShown = callToReturn || !isDefaultDialer || blockedShown || smsStripShown

        /*
         * ЗВОНОК БЕЗ РАЗРЕШЕНИЯ «ТЕЛЕФОН» (2.1.0): системное окно прямо
         * по нажатию «Позвонить», после «Разрешить» — тот же звонок
         * (разбор в util/PermissionPrompt.kt).
         */
        val permissionPrompt = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result -> com.ogneliss.dialer.util.PermissionPrompt.onResult(result) }
        LaunchedEffect(Unit) {
            com.ogneliss.dialer.util.PermissionPrompt.requests.collect { permissions ->
                runCatching { permissionPrompt.launch(permissions) }
                    .onFailure { com.ogneliss.dialer.util.PermissionPrompt.onResult(emptyMap()) }
            }
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
        /*
         * Свой редактор контактов (1.7.0): запросы «создать», «изменить»,
         * «добавить к контакту» из любого экрана — переходом сюда.
         */
        LaunchedEffect(Unit) {
            vm.contactEditRequests.collect { request ->
                when (request) {
                    is DialerViewModel.ContactEditRequest.Create -> nav.navigate(
                        contactEditRoute(number = request.number, name = request.name)
                    )
                    is DialerViewModel.ContactEditRequest.Edit -> nav.navigate(
                        contactEditRoute(id = request.contactId)
                    )
                    is DialerViewModel.ContactEditRequest.Attach -> nav.navigate(
                        "contacts?pick=attach&number=" + android.net.Uri.encode(request.number)
                    )
                }
            }
        }

        val resources = LocalResources.current
        LaunchedEffect(Unit) {
            vm.messages.collect { message ->
                val text = message.formatArg
                    ?.let { resources.getString(message.textRes, it) }
                    ?: resources.getString(message.textRes)

                // 2.1.0: сообщения — только с «Отменить» (разбор у vm.messages);
                // долгого «напоминание может прийти позже» с «Настроить» больше нет
                val result = appSnackbar.showSnackbar(
                    message = text,
                    actionLabel = message.undo?.let { resources.getString(R.string.msg_undo) },
                    duration = SnackbarDuration.Short
                )

                // Погасло без «Отменить» — отложенное действие выполняется
                // (удаление из журнала, 1.5.7: разбор в deleteGroupsWithUndo)
                if (result == SnackbarResult.ActionPerformed) {
                    message.undo?.invoke()
                } else {
                    message.onTimeout?.invoke()
                }
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
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Баннер сверху уже отступил под строку состояния —
                // экранам под ним этот отступ не нужен (1.5.8)
                .then(
                    if (topStripShown) {
                        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    } else {
                        Modifier
                    }
                )
        ) {
            if (showRail) {
                AppNavigationRail(
                    currentRoute = currentRoute,
                    // Один экран каждого вида в стеке: касание по «Контактам»
                    // из заметок не громоздит экраны друг на друга, а переключает
                    // (разбор у openSection)
                    onNavigate = { route ->
                        MainSection.entries.firstOrNull { it.route == route }?.let(openSection)
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
                composable(
                    "dialer",
                    // Журнал и контакты — соседи (1.6.0): между ними —
                    // растворение, без сдвига (разбор у Motion.enterSibling)
                    // 2.1.0: соседи — и SMS (isSectionList)
                    exitTransition = {
                        if (isSectionList(targetState)) Motion.exitSibling(this) else Motion.exitForward(this)
                    },
                    popEnterTransition = {
                        if (isSectionList(initialState)) Motion.enterSibling(this) else Motion.enterBackward(this)
                    }
                ) {
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
                            // Контакты — соседний список (1.6.0): разбор у openSection
                            onOpenContacts = { openSection(MainSection.CONTACTS) },
                            onOpenSection = openSection,
                            onOpenSettings = { nav.navigate("settings") },
                            onOpenHelp = { nav.navigate("faq") },
                            // Ключ группы кодирует ListDetailHost: он содержит
                            // символы, недопустимые в маршруте без экранирования
                            onOpenSubscriber = openCard,
                            onOpenBlockLists = { nav.navigate("blocklists") },
                            onOpenFakeCallPicker = { nav.navigate("contacts?pick=fake") },
                            onOpenNotes = { nav.navigate("notes") },
                            onOpenReminders = { nav.navigate("reminders") },
                            onOpenArchive = { nav.navigate("call_archive") },
                            // Полный список закреплённых — экран «Закреплённые» (2.1.0)
                            onOpenPinned = { nav.navigate("pinned") }
                        )
                    }
                }
                composable(
                    "contacts?pick={pick}&number={number}",
                    arguments = listOf(
                        navArgument("pick") { defaultValue = ""; type = NavType.StringType },
                        // Номер для «Добавить номер к контакту» (1.7.0)
                        navArgument("number") { defaultValue = ""; type = NavType.StringType }
                    ),
                    // Из журнала и обратно — растворением, как соседи (1.6.0);
                    // выбор контакта из настроек — прежним сдвигом
                    enterTransition = {
                        if (initialState.destination.route in SIBLING_ROUTES && isContactsList(targetState)) {
                            Motion.enterSibling(this)
                        } else {
                            Motion.enterForward(this)
                        }
                    },
                    popExitTransition = {
                        if (targetState.destination.route in SIBLING_ROUTES && isContactsList(initialState)) {
                            Motion.exitSibling(this)
                        } else {
                            Motion.exitBackward(this)
                        }
                    },
                    // 2.1.0: из контактов в SMS (свайп по панели) — тоже растворением:
                    // уходящий экран не должен ехать против жеста
                    exitTransition = {
                        if (isContactsList(initialState) && isSectionList(targetState)) Motion.exitSibling(this)
                        else Motion.exitForward(this)
                    },
                    popEnterTransition = {
                        if (isContactsList(targetState) && isSectionList(initialState)) Motion.enterSibling(this)
                        else Motion.enterBackward(this)
                    }
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
                        onOpenNotes = { openSection(MainSection.NOTES) },
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
                        pickForPinning = pickMode == "pin" || pickMode == "fake" || pickMode == "attach",
                        // «Добавить номер к контакту» (1.7.0): выбран контакт —
                        // редактор с номером новой строкой, выбор из стека уходит
                        onPickedContact = if (pickMode == "attach") {
                            { id ->
                                val number = entry.arguments?.getString("number").orEmpty()
                                nav.navigate(contactEditRoute(id = id, number = number)) {
                                    popUpTo("contacts?pick={pick}&number={number}") { inclusive = true }
                                }
                            }
                        } else null,
                        pickTitleRes = if (pickMode == "attach") R.string.contacts_pick_attach_title
                        else R.string.pinned_pick_title,
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
                        onOpenContact = { id -> openCard("contact:$id") },
                        // Из карточек долгого нажатия: блокировка по маске (1.5.7)
                        onOpenBlockLists = { nav.navigate("blocklists") },
                        // Та же панель, что у журнала (1.6.0): переключатель,
                        // список разделов, клавиатура набора — в журнале
                        onOpenSection = openSection,
                        onOpenDialpad = {
                            vm.requestDialpad()
                            openSection(MainSection.JOURNAL)
                        },
                        onOpenSettings = { nav.navigate("settings") },
                        onOpenReminders = { openSection(MainSection.REMINDERS) },
                        onOpenHelp = { nav.navigate("faq") },
                        // Полный список закреплённых — экран «Закреплённые» (2.1.0)
                        onOpenPinned = { nav.navigate("pinned") }
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
                                    onOpenEntry = {
                                        settingsReveal = SettingReveal(it.id)
                                        selected = it.category
                                    },
                                    onOpenFaq = openFaq
                                )
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                SettingsCategoryHost(
                                    category = selected,
                                    vm = vm,
                                    nav = nav,
                                    onBack = { nav.popBackStack() },
                                    onOwnNumber = { ownNumberOpen = true },
                                    showBack = false,
                                    reveal = settingsReveal,
                                    onRevealConsumed = { settingsReveal = null }
                                )
                            }
                        }
                    } else {
                        SettingsHomeScreen(
                            onBack = { nav.popBackStack() },
                            onOpenCategory = { nav.navigate(it.route) },
                            // Из поиска переходим сразу в категорию найденной настройки;
                            // лежит под «Дополнительно» — раздел её покажет (1.5.8)
                            onOpenEntry = {
                                settingsReveal = SettingReveal(it.id)
                                nav.navigate(it.category.route)
                            },
                            onOpenFaq = openFaq
                        )
                    }
                }

                // «Вопросы и ответы» (1.6.0): из меню, настроек и поиска по ним
                composable(
                    "faq?q={q}",
                    arguments = listOf(
                        navArgument("q") { defaultValue = ""; type = NavType.StringType }
                    )
                ) { entry ->
                    FaqScreen(
                        onBack = { nav.popBackStack() },
                        openQuestion = entry.arguments?.getString("q")?.takeIf { it.isNotEmpty() },
                        isDefaultDialer = isDefaultDialer,
                        onOpenLink = openFaqLink
                    )
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

                /*
                 * Свой редактор контакта (1.7.0). id > 0 — правка,
                 * иначе новый контакт с номером и именем.
                 */
                composable(
                    "contact_edit?id={id}&number={number}&name={name}",
                    arguments = listOf(
                        navArgument(com.ogneliss.dialer.ui.contactedit.ARG_ID) { defaultValue = -1L; type = NavType.LongType },
                        navArgument(com.ogneliss.dialer.ui.contactedit.ARG_NUMBER) { defaultValue = ""; type = NavType.StringType },
                        navArgument(com.ogneliss.dialer.ui.contactedit.ARG_NAME) { defaultValue = ""; type = NavType.StringType }
                    )
                ) {
                    com.ogneliss.dialer.ui.contactedit.ContactEditScreen(
                        onClose = { nav.popBackStack() },
                        onSaved = {
                            vm.contactSaved()
                            nav.popBackStack()
                        }
                    )
                }

                /*
                 * Раздел «SMS» (2.0.0): список переписок — кнопкой на нижней
                 * панели рядом с журналом, как соседний раздел.
                 */
                composable(
                    MainSection.MESSAGES.route,
                    enterTransition = { Motion.enterSibling(this) },
                    popExitTransition = { Motion.exitSibling(this) },
                    // 2.1.0: к соседям (журнал, контакты) — растворением; в переписку — сдвигом
                    exitTransition = {
                        if (isSectionList(targetState)) Motion.exitSibling(this) else Motion.exitForward(this)
                    },
                    popEnterTransition = {
                        if (isSectionList(initialState)) Motion.enterSibling(this) else Motion.enterBackward(this)
                    }
                ) {
                    com.ogneliss.dialer.ui.conversation.MessagesScreen(
                        vm = vm,
                        onOpenConversation = { number ->
                            nav.navigate(com.ogneliss.dialer.ui.conversation.ConversationRequest(number, null, null).route())
                        },
                        onOpenSection = openSection,
                        onOpenDialpad = {
                            vm.requestDialpad()
                            openSection(MainSection.JOURNAL)
                        },
                        onOpenSettings = { nav.navigate("settings") },
                        onOpenNotes = { openSection(MainSection.NOTES) },
                        onOpenReminders = { openSection(MainSection.REMINDERS) },
                        onOpenHelp = { nav.navigate("faq") },
                        onFakeCall = { FakeCall.ringNow(context) },
                        onOpenCard = { key -> nav.navigate("subscriber/" + android.net.Uri.encode(key)) },
                        onOpenBlockLists = { nav.navigate("blocklists") }
                    )
                }

                /*
                 * Переписка SMS (2.0.0): из журнала, карточки, уведомления
                 * и ссылок sms: других приложений.
                 */
                composable(
                    com.ogneliss.dialer.ui.conversation.ConversationRequest.ROUTE,
                    arguments = listOf(
                        navArgument(com.ogneliss.dialer.ui.conversation.ConversationRequest.ARG_ADDRESS) { defaultValue = ""; type = NavType.StringType },
                        navArgument(com.ogneliss.dialer.ui.conversation.ConversationRequest.ARG_BODY) { defaultValue = ""; type = NavType.StringType },
                        navArgument(com.ogneliss.dialer.ui.conversation.ConversationRequest.ARG_SUB) { defaultValue = -1; type = NavType.IntType }
                    )
                ) {
                    com.ogneliss.dialer.ui.conversation.ConversationScreen(
                        onBack = { nav.popBackStack() },
                        onOpenCard = { number ->
                            nav.navigate("subscriber/" + android.net.Uri.encode("number:$number"))
                        },
                        onCall = { number -> vm.call(number) }
                    )
                }

                /*
                 * Архив журнала (2.1.0): «Ещё» → «Архив». Архив SMS — внутри
                 * раздела SMS (MessagesScreen), своим видом списка.
                 */
                composable("call_archive") {
                    com.ogneliss.dialer.ui.screens.CallArchiveScreen(vm = vm, onBack = { nav.popBackStack() })
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
                            onOwnNumber = { ownNumberOpen = true },
                            reveal = settingsReveal,
                            onRevealConsumed = { settingsReveal = null }
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
        ) { data ->
            // С кнопкой — кольцо с секундами до конца (1.5.8): разбор в CountdownSnackbar
            com.ogneliss.dialer.ui.components.CountdownSnackbar(data)
        }
    }
    } // LocalNavBarStrip
}
}


/**
 * «Телефон не дал показать экран вызова» (1.8.1): тем же видом, что
 * приглашение стать телефоном по умолчанию, — с кнопкой на нужный экран
 * прошивки и крестиком.
 */
@Composable
private fun IncomingBlockedBanner(padTop: Boolean, onFix: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarSpace(padTop)
                .padding(start = Space.m, top = Space.s, bottom = Space.s),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.incoming_blocked_banner),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onFix) { Text(stringResource(R.string.incoming_blocked_action)) }
            IconButton(onClick = onDismiss) {
                Icon(OgnelissIcons.Close, contentDescription = stringResource(R.string.nav_close))
            }
        }
    }
}

@Composable
private fun DefaultDialerBanner(padTop: Boolean, onRequest: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                // Подложка — до верха экрана, текст — под строкой состояния (1.5.8)
                .statusBarSpace(padTop)
                .padding(Space.m),
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
 * «ВЕРНУТЬСЯ К ВЫЗОВУ» (2.1.0): зелёная полоса сверху, пока есть вызов.
 * Зелёный в приложении — только звонок; здесь он и есть. Высота — цель
 * касания (48 dp), вся полоса — одна кнопка.
 */
@Composable
private fun ReturnToCallStrip(padTop: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.incall_return)
    Surface(color = com.ogneliss.dialer.ui.theme.CallGreen, contentColor = androidx.compose.ui.graphics.Color.White) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarSpace(padTop)
                .clickable(onClickLabel = label, onClick = onClick)
                .heightIn(min = com.ogneliss.dialer.ui.theme.Dimens.MIN_TOUCH_TARGET)
                .padding(horizontal = Space.l, vertical = Space.xs),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(OgnelissIcons.Call, contentDescription = null, modifier = Modifier.size(com.ogneliss.dialer.ui.theme.FlowUi.iconSizeInline))
            Spacer(Modifier.width(Space.s))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Отступ под строку состояния — только у верхней из полос (2.1.0). */
@Composable
private fun Modifier.statusBarSpace(pad: Boolean): Modifier =
    if (pad) windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else this


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
    showBack: Boolean = true,
    /** Найдено поиском — показать (1.5.8): разбор у SettingReveal. */
    reveal: SettingReveal? = null,
    onRevealConsumed: () -> Unit = {}
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

    val scope = rememberCoroutineScope()

    /*
     * 2.1.0: без «Сохранено» и «Не удалось сохранить файл» (правило автора
     * 07.11.2026: успех не подтверждается, о некритичной неудаче
     * не сообщаем). Неудача — пустой файл, который окно выбора уже
     * создало, удаляется; причина — в журнале разработчика.
     */
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
            if (!ok) vm.discardExport(uri)
        }
    }

    SettingsCategoryScreen(
        category = category,
        vm = vm,
        onBack = onBack,
        showBack = showBack,
        // Просьба — только разделу найденной настройки
        reveal = reveal?.takeIf { r -> SettingsRegistry.byCategory(category).any { it.id == r.entryId } },
        onRevealConsumed = onRevealConsumed,
        onAction = { key ->
            runSettingAction(key, context, nav, vm, onOwnNumber) { build, fileName ->
                pendingText = build
                saveLauncher.launch(fileName)
            }
        }
    )
}


/**
 * ДЕЙСТВИЕ ПУНКТА НАСТРОЕК — ОДНО НА РАЗДЕЛЫ НАСТРОЕК И «ВОПРОСЫ И ОТВЕТЫ».
 *
 * До 1.6.0 этот разбор жил внутри SettingsCategoryHost. Кнопки под ответами
 * (FaqScreen) делают то же, что пункты в настройках, — и разбор вынесен,
 * чтобы два места не разошлись: поправили переход здесь — поправился
 * и там.
 *
 * @param saveToFile выгрузка в файл: окно выбора файла есть только у раздела
 *   настроек. null — у вызывающего его нет; такие пункты «Вопросы и ответы»
 *   не выполняют, а открывают их раздел (ACTIONS_OF_SETTINGS_SCREEN).
 */
private fun runSettingAction(
    key: String,
    context: android.content.Context,
    nav: androidx.navigation.NavController,
    vm: DialerViewModel,
    onOwnNumber: () -> Unit,
    saveToFile: ((build: suspend () -> String?, fileName: String) -> Unit)?
) {
    when (key) {
        "allow_list", "block_list" -> nav.navigate("blocklists")
        "spam_overview" -> nav.navigate("spam")
        "speed_dial" -> nav.navigate("speeddial")
        "backup_export", "backup_import" -> nav.navigate("backup")

        "calllog_export" -> saveToFile?.invoke({ vm.buildCallLogCsv() }, vm.callLogCsvFileName())

        "log_save" -> saveToFile?.invoke({ vm.buildLogText() }, "ogneliss.log")
        "stats" -> nav.navigate("stats")
        /*
         * Приложение SMS по умолчанию (2.0.0). Уже по умолчанию — системный
         * выбор приложений: отказаться от роли можно только там.
         */
        "sms_default_app" -> {
            val activity = context as? android.app.Activity
            val request = com.ogneliss.dialer.sms.SmsRole.request(context)
            when {
                activity == null -> Unit
                request != null -> runCatching {
                    // Ответ и разбор отказа — MainActivity.onActivityResult (RoleAccess)
                    com.ogneliss.dialer.util.RoleAccess.markRequested()
                    @Suppress("DEPRECATION")
                    activity.startActivityForResult(request, com.ogneliss.dialer.sms.SmsRole.REQUEST_CODE)
                }
                else -> com.ogneliss.dialer.util.DefaultDialer.openSystemDefaultApps(activity)
            }
        }
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
            context, com.ogneliss.dialer.util.AppLinks.PRIVACY
        ) { nav.navigate("about") }

        "terms" -> openDocument(
            context, com.ogneliss.dialer.util.AppLinks.TERMS
        ) { nav.navigate("about") }

        // Лицензия Apache 2.0 и исходный код (1.5.7)
        "license" -> openDocument(
            context, com.ogneliss.dialer.util.AppLinks.LICENSE
        ) { nav.navigate("about") }

        "source_code" -> openDocument(
            context, com.ogneliss.dialer.util.AppLinks.SOURCE_CODE
        ) { nav.navigate("about") }

        "licenses" -> openDocument(
            context, com.ogneliss.dialer.util.AppLinks.LICENSES
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

        /*
         * 2.1.0: магазин — только у установленного из Google Play
         * (InstallSource). «Оценить» без него не показывается вовсе
         * (SettingsRegistry.shown); было — сообщение «Google Play
         * недоступен». «Версия» без магазина — сразу «О приложении».
         */
        "rate_app" -> openDocument(
            context,
            "https://play.google.com/store/apps/details" +
                "?id=com.ogneliss.dialer"
        ) { nav.navigate("about") }

        "version" -> {
            if (com.ogneliss.dialer.util.InstallSource.isGooglePlay(context)) {
                openDocument(
                    context,
                    "https://play.google.com/store/apps/details" +
                        "?id=com.ogneliss.dialer"
                ) { nav.navigate("about") }
            } else {
                nav.navigate("about")
            }
        }

        else -> vm.handleSettingsAction(key)
    }
}

/**
 * Действия, которым нужен сам экран раздела: подписи SIM — его окно,
 * выгрузки — его выбор файла. Кнопка в «Вопросах и ответах» для них
 * открывает раздел и подсвечивает пункт, а не выполняет действие.
 */
private val ACTIONS_OF_SETTINGS_SCREEN = setOf("sim_labels", "calllog_export", "log_save")


/**
 * Правая половина, пока запись не выбрана: значок на месте будущей
 * карточки. Пустая панель выглядела бы недогруженным экраном.
 *
 * 2.1.0: без подписи «Выберите вызов, чтобы увидеть здесь карточку» —
 * правило автора 07.11.2026, пояснений не показываем; значок остался.
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
        // Две панели — под строкой состояния, как до 1.5.8: фото карточки
        // справа не уходит под часы, которые стоят над журналом слева
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
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

/**
 * Контакты как соседний список, а не выбор контакта для настройки
 * (закрепить, псевдозвонок): по этому признаку переход между журналом
 * и контактами — растворением (1.6.0).
 */
private fun isContactsList(entry: androidx.navigation.NavBackStackEntry): Boolean =
    entry.destination.route?.startsWith(MainSection.CONTACTS.route) == true &&
        entry.arguments?.getString("pick").isNullOrEmpty()

/** Маршрут своего редактора контакта (1.7.0): правка по id или новый с номером и именем. */
private fun contactEditRoute(id: Long = -1L, number: String = "", name: String? = null): String =
    "contact_edit?id=$id&number=" + android.net.Uri.encode(number) +
        "&name=" + android.net.Uri.encode(name.orEmpty())

/** Соседи контактов в карусели (2.1.0): переход растворением, а не сдвигом. */
private val SIBLING_ROUTES = setOf(MainSection.JOURNAL.route, MainSection.MESSAGES.route)

/** Разделы-соседи карусели: журнал, SMS, список контактов (2.1.0). */
private fun isSectionList(entry: androidx.navigation.NavBackStackEntry): Boolean =
    entry.destination.route in SIBLING_ROUTES || isContactsList(entry)
