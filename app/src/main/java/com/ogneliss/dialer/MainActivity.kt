package com.ogneliss.dialer

import com.ogneliss.dialer.settings.SwitchSetting
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import com.ogneliss.dialer.ui.adaptive.ProvideWindowLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleEventObserver
import com.ogneliss.dialer.ui.RootScreen
import com.ogneliss.dialer.ui.screens.ConsentScreen
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.ui.screens.LegalDocument
import com.ogneliss.dialer.ui.screens.LegalScreen
import com.ogneliss.dialer.ui.screens.PermissionWizardScreen
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import com.ogneliss.dialer.util.AppPermissions
import com.ogneliss.dialer.util.DefaultDialer
import com.ogneliss.dialer.util.LocaleManager
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.ui.DialerViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Номер текущей редакции документов. Увеличивается при их существенном
 * изменении — тогда согласие спрашивается заново.
 * 2 — политика 1.3 (2.7.2): данные переезжают при переносе «телефон на
 * телефон»; решение автора 16.11.2026 — спросить согласие снова, с кратким
 * резюме изменений ([CONSENT_CHANGES]).
 */
private const val CONSENT_VERSION = 2

/**
 * Что изменилось в редакции — для тех, кто принимал прежнюю. Показывается
 * резюме текущей редакции: кто пропустил несколько, всё равно видит
 * документы целиком по ссылкам на том же экране.
 */
private val CONSENT_CHANGES = mapOf(2 to R.string.consent_changes_v2)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Первый осмысленный кадр готов.
     *
     * Ставится, как только прочитана версия согласия: до этого
     * неизвестно, что рисовать, и заставка честнее пустого экрана.
     */
    private var firstFrameReady = false

    /** Для заставки: согласие читается здесь, а не в разметке (2.7.0, разбор у setKeepOnScreenCondition). */
    @javax.inject.Inject lateinit var startPrefs: com.ogneliss.dialer.settings.Prefs

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        /**
         * Экран запуска.
         *
         * Устанавливается ДО super.onCreate — иначе система успеет
         * показать свой вариант, и произойдёт видимая подмена.
         *
         * Держим его на экране, пока грузится первая порция журнала:
         * пустой список на долю секунды с последующим наполнением
         * выглядит как подвисание. Но не дольше — если данные почему-то
         * не приходят, пользователь должен увидеть интерфейс,
         * а не бесконечную заставку.
         */
        /*
         * ЗАСТАВКА СКРЫВАЕТСЯ, КАК ТОЛЬКО ГОТОВ ПЕРВЫЙ КАДР.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО БЫЛО НЕ ТАК
         * ══════════════════════════════════════════════════════════════════
         * Заставка держалась 700 мс по таймеру — безусловно, даже когда
         * интерфейс был готов через пятьдесят.
         *
         * Приём этот придуман для другого: чтобы заставка не мелькала,
         * появившись и тут же исчезнув. Но диалер открывают, чтобы
         * позвонить прямо сейчас, часто на ходу, и три четверти секунды
         * ожидания там заметны — особенно когда за ними ничего нет.
         *
         * ── Что вместо ─────────────────────────────────────────────────────
         * Убираем условие вовсе. Система показывает заставку ровно
         * столько, сколько идёт запуск: готов первый кадр — она уходит.
         * Мелькания при этом не будет, потому что запуск Compose-экрана
         * заведомо дольше кадра.
         *
         * ── Почему нельзя «держать приложение в фоне» ───────────────────────
         * Ожидание, что приложение всегда останется в памяти, Android
         * не гарантирует: система выгружает фоновые процессы под нужды
         * переднего плана, и повлиять на это приложение не может.
         *
         * Единственный способ ускорить холодный старт — сократить сам
         * запуск. Для этого есть Baseline Profile: он даёт 20–30 %,
         * и его ещё предстоит сгенерировать.
         */
        val splash = installSplashScreen()

        /*
         * Заставка держится, пока не прочитано согласие.
         *
         * Это единственное, чего мы ждём перед первым кадром: до чтения
         * неизвестно, показывать экран согласия или журнал, и пустой
         * экран в этот момент выглядел бы как подвисание.
         *
         * Чтение одного значения из DataStore занимает считаные
         * миллисекунды — заставка уйдёт почти сразу. Прежний таймер
         * на 700 мс держал её и тогда, когда всё давно готово.
         */
        splash.setKeepOnScreenCondition { !firstFrameReady }
        /*
         * ЗАСТАВКУ СНИМАЕТ ЧТЕНИЕ НАСТРОЕК, А НЕ РАЗМЕТКА (2.7.0).
         *
         * Было: флаг ставился только из разметки (LaunchedEffect ниже). Пока
         * заставка держится, кадры не рисуются; а разметке, чтобы дойти до
         * флага, нужен кадр. На телефоне это сходилось, потому что разметка
         * собирается до первого кадра, — но заставка зависела от того, в каком
         * порядке система вызовет сборку и отрисовку, а в тестах экранов
         * (ScreenGalleryTest) запуск не завершался вовсе. Теперь согласие
         * читается отдельно от разметки; ошибка чтения заставку тоже снимает —
         * человек должен увидеть интерфейс, а не вечную заставку.
         */
        /*
         * С КАКОГО РАЗДЕЛА НАЧАТЬ (2.7.0, форум, oleg_p: «выбор экрана при
         * запуске: конкретный экран (по умолчанию) либо последний
         * использованный»). Настройки читаются в той же сопрограмме, что и
         * согласие, — до снятия заставки, не на главном потоке и без ожидания
         * в разметке: человек не видит прыжка с журнала на другой раздел.
         * Решает чистая StartSection.resolve; здесь — только чтение настроек.
         * Холодный запуск — это новая активность (savedInstanceState == null),
         * открытая значком приложения (MAIN + LAUNCHER). Возврат из фона
         * приходит в onNewIntent или с сохранённым состоянием — тогда
         * человек остаётся там, где был. Ошибка чтения настроек — журнал.
         */
        // Само чтение — ниже, после super.onCreate (2.7.3): см. там

        /*
         * EDGE-TO-EDGE БЕЗ СВОИХ ПОДЛОЖЕК.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ УБРАНЫ SystemBarStyle
         * ══════════════════════════════════════════════════════════════════
         * Здесь стояли свои полупрозрачные подложки под системные панели:
         * enableEdgeToEdge(statusBarStyle = …, navigationBarStyle = …).
         *
         * На Android 15 это перестало работать и стало вредным.
         * Под капотом такой вызов задаёт Window.statusBarColor
         * и navigationBarColor, а с API 35 оба метода объявлены
         * устаревшими и НИЧЕГО НЕ ДЕЛАЮТ. Google Play помечает такое
         * приложение как «использует неподдерживаемые API отображения
         * от края до края» — это и было замечание в консоли.
         *
         * То есть подложки не рисовались ни на одном Android 15,
         * а предупреждение мы получали на каждом выпуске.
         *
         * ── Что вместо них ─────────────────────────────────────────────────
         * Вызов без параметров: система сама делает панели прозрачными
         * и сама решает вопрос контраста значков. Задача «содержимое
         * не должно уезжать под панели» решается отступами —
         * их считает Scaffold, и они уже расставлены по экранам.
         *
         * Контрастность значков (тёмные на светлой теме, светлые
         * на тёмной) задаётся в Theme.kt через WindowInsetsController —
         * этот путь поддерживается и в Android 15.
         */
        enableEdgeToEdge()

        /*
         * БЕЗ БЕЛОЙ ПОДЛОЖКИ ПОД КНОПКАМИ НАВИГАЦИИ (1.5.1, снимки автора
         * 11.10: под нижней панелью — почти белая полоса с ◁ ○ □).
         * enableEdgeToEdge() по умолчанию оставляет включённым «контраст
         * панели навигации», и при трёх кнопках система кладёт под них
         * полупрозрачную белую (в тёмной — чёрную) подложку поверх нашей
         * полосы цвета панели. Контраст значков задаёт Theme.kt
         * (isAppearanceLightNavigationBars), подложка не нужна.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        // Намерение, с которым приложение открыли: дальше его меняет
        // onNewIntent
        currentIntent = intent
        // ?: — раздел запуска (выше) мог быть уже выбран: не затирать его пустым
        sectionRoute = sectionRouteOf(intent) ?: sectionRoute

        /*
         * ЧТЕНИЕ СОГЛАСИЯ И РАЗДЕЛА ЗАПУСКА — ПОСЛЕ super.onCreate (2.7.3).
         * Было (2.7.0–2.7.2): сопрограмма запускалась до super.onCreate, а
         * startPrefs подставляет Hilt именно в super.onCreate. lifecycleScope
         * начинает сопрограмму сразу (Main.immediate) — обращение к ещё пустому
         * полю падало, ошибка ловилась, и «При запуске открывать» не
         * срабатывало никогда (журнал пользователя: «lateinit property
         * startPrefs has not been initialized» при каждом запуске). Заставка
         * от переноса не страдает: она держится, пока firstFrameReady ложь.
         */
        val coldLauncherStart = savedInstanceState == null &&
            com.ogneliss.dialer.widget.SectionIcons.isAppIconLaunch(intent)
        lifecycleScope.launch {
            runCatching { startPrefs.consentVersion.first() }
            if (coldLauncherStart) {
                runCatching {
                    val route = com.ogneliss.dialer.settings.StartSection.resolve(
                        choice = startPrefs.startSection.first(),
                        last = startPrefs.lastSection.first(),
                        explicitRoute = sectionRouteOf(intent),
                        // Пока включены отдельные значки «SMS» и «Контакты» (2.6.0),
                        // значок приложения — «Телефон» и открывает журнал: это правило
                        // главнее настройки
                        sectionIconsEnabled = com.ogneliss.dialer.widget.SectionIcons.isEnabled(this@MainActivity),
                        coldLauncherStart = true
                    )
                    if (route != null) sectionRoute = route
                }.onFailure {
                    com.ogneliss.dialer.log.Logger.w("MainActivity", "Раздел запуска не прочитан", it)
                }
            }
            firstFrameReady = true
        }

        setContent {
            val vm: DialerViewModel = hiltViewModel()
            val theme by vm.prefs.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dynamic by vm.prefs.flow(SwitchSetting.DYNAMIC_COLOR).collectAsStateWithLifecycle(false)
            // Масштаб интерфейса под экран (1.5.6): разбор в ui/theme/UiScale.kt
            val uiScale by vm.prefs.uiScale.collectAsStateWithLifecycle(com.ogneliss.dialer.settings.UiScale.DEFAULT)

            // Тема, акцент, скругление, шрифт (1.8.4–1.9.0)
            val themeLook by vm.prefs.themeLook.collectAsStateWithLifecycle(com.ogneliss.dialer.settings.ThemeLook())
            val fontScale by vm.prefs.fontScale.collectAsStateWithLifecycle(1f)
            OgnelissDialerTheme(themeMode = theme, dynamicColor = dynamic, uiScale = uiScale, fontScale = fontScale, look = themeLook) {
                // Раскладка окна: телефон, лёжа, раскладушка, планшет.
                // Разбор — в ui/adaptive/WindowLayout.kt
                ProvideWindowLayout(this@MainActivity) {
                var isDefault by remember { mutableStateOf(DefaultDialer.isDefault(this)) }
                var permsOk by remember {
                    mutableStateOf(AppPermissions.missing(this).none { it.required })
                }
                /*
                 * МАСТЕР — ТОЛЬКО КОГДА ЕСТЬ ЧТО ПОКАЗАТЬ (2.1.0): роли «Телефон»
                 * нет или на MIUI/ColorOS есть шаг экрана входящего, который ещё
                 * не предлагали. Было: мастер открывался и при любом невыданном
                 * разрешении — страницами с пояснениями. Стало: разрешения
                 * спрашиваются одним системным окном поверх журнала
                 * (MissingPermissionsRequest). Решение — один раз за открытие:
                 * после выдачи роли мастер доводит свои шаги до конца сам.
                 */
                var wizardDone by remember {
                    mutableStateOf(!com.ogneliss.dialer.ui.screens.wizardNeeded(this@MainActivity, isDefault))
                }

                // Роль телефона или SMS не выдана после окна — подсказка поверх любого экрана (2.0.0)
                roleHelp?.let { help ->
                    com.ogneliss.dialer.ui.components.RoleHelpDialog(help) { roleHelp = null }
                }

                /*
                 * СОГЛАСИЕ С ДОКУМЕНТАМИ.
                 *
                 * Читается из настроек, а не задаётся заново каждый запуск:
                 * иначе экран показывался бы всякий раз.
                 *
                 * Хранится номер редакции: при существенном изменении
                 * документов согласие спрашивается заново, при исправлении
                 * опечаток — нет.
                 */
                val consentVersion by vm.prefs.consentVersion
                    .collectAsStateWithLifecycle(initialValue = -1)

                /*
                 * ЯЗЫК ПРИ ПЕРВОМ ЗАПУСКЕ.
                 *
                 * Если язык системы нам знаком — берём его, иначе
                 * английский. Делается один раз: дальше действует выбор
                 * пользователя, и перезаписывать его нельзя — человек мог
                 * сознательно поставить английский при русской системе.
                 */
                LaunchedEffect(consentVersion) {
                    // Значение прочитано — можно убирать заставку
                    if (consentVersion >= 0) firstFrameReady = true

                    if (consentVersion == 0) {
                        LocaleManager.applySystemDefault(this@MainActivity)
                    }
                }
                var permissionsJustGranted by remember { mutableStateOf(false) }

                // Состояние разрешений меняется вне приложения — перечитываем при возврате
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val obs = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isDefault = DefaultDialer.isDefault(this@MainActivity)
                            // Обновившимся до 2.0.0: телефон уже наш — один раз предложить SMS
                            if (isDefault && consentVersion > 0 &&
                                AppPermissions.missing(this@MainActivity).none { it.required }
                            ) {
                                requestSmsRoleOnce()
                            }
                            val wasOk = permsOk
                            permsOk = AppPermissions.missing(this@MainActivity)
                                .none { it.required }
                            // Разрешения могли появиться, пока пользователь
                            // был в системных настройках: подписку
                            // на провайдеры нужно установить заново
                            if (!wasOk && permsOk) permissionsJustGranted = true
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(obs)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
                }

                /*
                 * Документ, открытый для чтения с экрана согласия.
                 * Держится здесь, а не в ConsentScreen: экран согласия
                 * не должен знать о навигации.
                 */
                var legalToShow by remember { mutableStateOf<LegalDocument?>(null) }
                val consentScope = rememberCoroutineScope()

                when {
                    // Версия ещё не прочитана из хранилища. Показываем
                    // пустой экран: иначе у принявших согласие оно мелькнёт
                    // на долю секунды при каждом запуске
                    // Версия ещё не прочитана: держим заставку, а не
                    // показываем пустой экран — иначе у принявших согласие
                    // оно мелькнёт на долю секунды при каждом запуске
                    consentVersion < 0 -> Unit

                    // Чтение документа поверх экрана согласия
                    legalToShow != null -> LegalScreen(
                        document = legalToShow!!,
                        onBack = { legalToShow = null }
                    )

                    consentVersion < CONSENT_VERSION -> ConsentScreen(
                        // Принимал прежнюю редакцию (не первый запуск) — что изменилось
                        changes = CONSENT_CHANGES[CONSENT_VERSION]
                            ?.takeIf { consentVersion > 0 }
                            ?.let { stringResource(it) },
                        onAccept = {
                            /*
                             * rememberCoroutineScope, а не lifecycleScope:
                             * внутри composable его нет, а запись
                             * настройки — suspend-функция.
                             */
                            consentScope.launch {
                                vm.prefs.setConsentVersion(CONSENT_VERSION)
                            }
                        },
                        // Отказ закрывает приложение: пользоваться диалером,
                        // не приняв условия обработки журнала и контактов,
                        // невозможно по смыслу
                        onDecline = { finishAndRemoveTask() },
                        onOpenDocument = { doc -> legalToShow = doc }
                    )

                    !wizardDone -> {
                    /*
                     * Пошаговый мастер вместо общего списка карточек.
                     *
                     * Прежний экран показывал все разрешения сразу, и понять
                     * по нему, что выдано, а что нет, было невозможно.
                     * Из-за этого разрешения оставались невыданными,
                     * а приложение получало SecurityException при каждом
                     * обращении к журналу и контактам.
                     */
                        // Метка экрана — для проверки в замерах (1.3.3), см. screenTag
                        Box(Modifier.screenTag("permission_wizard")) {
                            PermissionWizardScreen(
                                isDefaultDialer = isDefault,
                                onRequestDefaultDialer = { DefaultDialer.request(this@MainActivity) },
                                onOpenSystemSettings = {
                                    DefaultDialer.openSystemDefaultApps(this@MainActivity)
                                },
                                onFinished = { wizardDone = true }
                            )
                        }
                    }

                    else -> Box(Modifier.screenTag("root_screen")) { RootScreen(
                        permissionsJustGranted = permissionsJustGranted,
                        onPermissionsHandled = { permissionsJustGranted = false },
                        isDefaultDialer = isDefault,
                        onRequestDefault = { DefaultDialer.request(this@MainActivity) },
                        onOpenSystemSettings = {
                            DefaultDialer.openSystemDefaultApps(this@MainActivity)
                        },
                        prefilledNumber = numberFromIntent(currentIntent),
                        prefillToken = intentToken,
                        openDialpad = currentIntent
                            ?.getBooleanExtra(EXTRA_OPEN_DIALPAD, false) == true,
                        // Карточка из уведомления напоминания
                        openCardForNumber = cardNumberFromIntent(currentIntent),
                        // Списки блокировки из уведомления о заблокированном
                        openBlockLists = currentIntent
                            ?.getBooleanExtra(EXTRA_OPEN_BLOCK_LISTS, false) == true,
                        // «Написать» и ссылки sms: — переписка в диалере (2.0.0)
                        openConversation = com.ogneliss.dialer.ui.conversation.ConversationRequest.from(currentIntent),
                        // Значок раздела на главном экране (2.1.0, widget/SectionWidgets);
                        // 2.6.0 — и значок приложения при отдельных значках разделов
                        openSectionRoute = sectionRoute
                    )
                        // Чего не выдала роль — одним системным окном, без своих страниц (2.1.0)
                        com.ogneliss.dialer.ui.screens.MissingPermissionsRequest(isDefaultDialer = isDefault)
                    }
                }
            }
            }
        }
    }

    /**
     * ПОСЛЕДНЕЕ ПРИШЕДШЕЕ НАМЕРЕНИЕ — КАК СОСТОЯНИЕ COMPOSE.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НОМЕР ИЗ ДРУГОГО ПРИЛОЖЕНИЯ НЕ ПОДСТАВЛЯЛСЯ
     * ══════════════════════════════════════════════════════════════════════
     * launchMode="singleTask": когда приложение уже запущено, новое
     * намерение приходит в onNewIntent, а не в onCreate. Экран при этом
     * не пересоздаётся.
     *
     * Раньше onNewIntent вызывал setIntent(intent) — и на этом всё.
     * Разметка читала номер выражением numberFromIntent(intent) внутри
     * setContent, то есть ОДИН раз при создании композиции. Compose
     * не знает, что поле intent у активности изменилось: это обычное
     * поле, а не состояние. Перерисовки не происходило, и человек
     * видел пустое поле набора.
     *
     * Работало это только при холодном запуске — когда приложение
     * было закрыто и onCreate читал намерение сам. Отсюда
     * «иногда работает»: зависит от того, висит ли приложение
     * в памяти.
     *
     * Теперь намерение живёт в mutableStateOf: onNewIntent кладёт туда
     * новое, композиция читает его оттуда и перечитывает при изменении.
     */
    private var currentIntent by mutableStateOf<Intent?>(null)

    /** Сколько намерений пришло: см. MainDialerScreen.prefillToken. */
    private var intentToken by mutableStateOf(0)

    /*
     * Открыли приложение — пропущенные увидены: счётчик на значке
     * и уведомление убираются (MissedCallsNotifier.clear).
     */
    override fun onResume() {
        super.onResume()
        // Значки разделов: роль SMS могла смениться, SMS — прочитаться (2.1.0)
        com.ogneliss.dialer.widget.SectionWidgets.updateAll(this)
        com.ogneliss.dialer.call.MissedCallsNotifier.clear(this)
        /*
         * ПРИЛОЖЕНИЕ ОТКРЫЛИ, ПОКА ЗВОНИТ (2.1.0) — сразу экран вызова.
         * Было: открывался журнал, а ответить можно было только из шторки —
         * если экран вызова не показался (запрет прошивки) или его свернули.
         * Ушли с экрана вызова сами («Назад») — не возвращаем: наверху
         * полоса «Вернуться к вызову» (RootScreen).
         */
        if (com.ogneliss.dialer.call.IncomingScreen.shouldReturnToCall()) {
            runCatching {
                startActivity(
                    Intent(this, com.ogneliss.dialer.call.InCallActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { com.ogneliss.dialer.log.Logger.w("MainActivity", "Экран вызова не открылся", it) }
        }
        // Уведомления могли запретить или разрешить в настройках — пропущенные
        // показывает тот, кому можно (2.1.0, MissedCallsNotifier.syncReceiver)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.ogneliss.dialer.call.MissedCallsNotifier.syncReceiver(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent = intent
        sectionRoute = sectionRouteOf(intent)
        intentToken++
    }

    /** Какой раздел открыть по намерению; null — остаться, где был. */
    private var sectionRoute by mutableStateOf<String?>(null)

    /*
     * РАЗДЕЛ ПО НАМЕРЕНИЮ.
     * • Значок раздела (виджет 2.1.0, значок в списке приложений 2.6.0) —
     *   раздел из намерения (SectionLaunchActivity кладёт его в EXTRA_OPEN_SECTION).
     * • Значок самого приложения, пока включены отдельные значки «SMS»
     *   и «Контакты» (2.6.0, SectionIcons), — журнал: значок приложения
     *   тогда «Телефон». Без них — как было: туда, где человек был.
     * Состояние значков спрашивается у Android здесь, а не в разметке:
     * один раз на намерение.
     */
    private fun sectionRouteOf(intent: Intent?): String? {
        intent?.getStringExtra(com.ogneliss.dialer.widget.SectionWidgets.EXTRA_OPEN_SECTION)?.let { return it }
        return if (com.ogneliss.dialer.widget.SectionIcons.isAppIconLaunch(intent) &&
            com.ogneliss.dialer.widget.SectionIcons.isEnabled(this)
        ) com.ogneliss.dialer.ui.components.MainSection.JOURNAL.route else null
    }

    /*
     * Один companion object на класс.
     *
     * ACTION_OPEN_CARD и EXTRA_CARD_NUMBER объявлены здесь же:
     * второй companion Kotlin не допускает, а они нужны снаружи —
     * уведомление напоминания формирует по ним намерение.
     *
     * Поэтому объект не private: константы читает CallbackReminder.
     */
    companion object {
        /** Запрос уведомлений после выдачи роли SMS (2.0.0). */
        private const val NOTIFICATIONS_REQUEST = 4203

        /** Открыть карточку абонента: намерение из уведомления. */
        const val ACTION_OPEN_CARD = "com.ogneliss.dialer.OPEN_CARD"
        const val EXTRA_CARD_NUMBER = "card_number"

        /**
         * Открыть списки блокировки: намерение из уведомления
         * о заблокированном звонке.
         *
         * Отдельный признак, а не действие: уведомление открывает
         * приложение обычным способом, и различать нужно только,
         * куда перейти после запуска.
         */
        const val EXTRA_OPEN_BLOCK_LISTS = "open_block_lists"

        /**
         * Открыть клавиатуру набора: намерение с экрана вызова,
         * кнопка «Добавить вызов». Человек пришёл набрать второй номер,
         * а не смотреть журнал.
         */
        const val EXTRA_OPEN_DIALPAD = "open_dialpad"
    }

    /**
     * Что подсказать после окна роли «Телефон» или «SMS» (2.0.0): Android 15
     * ограничил настройку для установленного из файла или окно не показалось.
     * Разбор — util/RoleAccess.kt.
     */
    private var roleHelp by mutableStateOf<com.ogneliss.dialer.util.RoleAccess.Help?>(null)

    /*
     * Окна ролей запускаются через startActivityForResult из разных мест
     * (мастер, «Разрешения», полоса в журнале, настройки сообщений) —
     * ответ приходит сюда, и разбор отказа один на всех.
     */
    @Deprecated("Окна ролей запускаются startActivityForResult — ответ здесь")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val smsRequest = requestCode == com.ogneliss.dialer.sms.SmsRole.REQUEST_CODE ||
            requestCode == com.ogneliss.dialer.sms.SmsRole.AUTO_REQUEST_CODE
        // RESULT_OK — система подтвердила роль, даже если её проверка отстаёт
        val granted = resultCode == RESULT_OK || when {
            requestCode == DefaultDialer.REQUEST_CODE -> DefaultDialer.isDefault(this)
            smsRequest -> com.ogneliss.dialer.sms.SmsRole.isDefault(this)
            else -> return
        }
        if (smsRequest) {
            com.ogneliss.dialer.log.Logger.i("SmsRole", com.ogneliss.dialer.sms.SmsRole.diagnostics(this))
        }
        when {
            // Сразу за ролью телефона — роль SMS (решение автора, 2.0.0; 2.1.0 — не при RCS)
            granted && requestCode == DefaultDialer.REQUEST_CODE -> requestSmsRoleOnce()
            /*
             * 2.1.0: отказ в роли, которую человек не просил (окно после роли
             * телефона), — не объясняется окном: человек сам отказался или
             * система не показала — пропустить и записать (правило автора:
             * не показывать объяснений). Объяснение — только когда просил он сам.
             */
            !granted && requestCode == com.ogneliss.dialer.sms.SmsRole.AUTO_REQUEST_CODE ->
                com.ogneliss.dialer.log.Logger.i("SmsRole", "Роль SMS без просьбы человека не выдана — пропущено")
            !granted -> roleHelp = com.ogneliss.dialer.util.RoleAccess.helpAfterDenied(this)
            smsRequest -> {
                // Канал «Сообщения» — сразу, до первого SMS (SmsHealth). Проверка роли:
                // код 4202 занят ещё и окном роли определителя (call/ScreeningRoleInfo)
                if (com.ogneliss.dialer.sms.SmsRole.isDefault(this)) com.ogneliss.dialer.sms.SmsHealth.onRoleGranted(this)
                // Приложение SMS без уведомлений — входящие пришли бы молча
                if (com.ogneliss.dialer.sms.SmsRole.needsNotificationPermission(this)) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATIONS_REQUEST)
                }
            }
        }
    }

    /**
     * РОЛЬ SMS СРАЗУ ПОСЛЕ РОЛИ ТЕЛЕФОНА (2.0.0).
     *
     * Решение автора: человек, сделавший диалер телефоном, тут же видит
     * системное окно «сделать приложением SMS». Отказ уважается: окно
     * показывается один раз (SmsSettings.rolePromptDone), дальше роль —
     * в «Разрешениях», настройках сообщений и в переписке.
     *
     * Тот же вызов — при первом запуске 2.0.0 у тех, кто стал телефоном
     * по умолчанию ещё в прежних версиях.
     *
     * 2.1.0: только если приложение SMS по умолчанию сейчас — не с чатами RCS
     * (SmsRole.autoOfferAllowed): взяв роль у Google Сообщений или Samsung
     * Messages, диалер молча выключил бы человеку RCS. Там окно не
     * показывается и не отмечается показанным — сменит человек приложение
     * SMS на другое, предложим тогда. Роль по просьбе — «Вести SMS
     * в Ogneliss» в разделе SMS. Код запроса свой (AUTO_REQUEST_CODE):
     * отказ здесь не объясняется окном.
     */
    private fun requestSmsRoleOnce() {
        if (com.ogneliss.dialer.sms.SmsRole.isDefault(this)) return
        if (com.ogneliss.dialer.sms.SmsSettings.rolePromptDone(this)) return
        if (!com.ogneliss.dialer.sms.SmsRole.autoOfferAllowed(this)) return
        val request = com.ogneliss.dialer.sms.SmsRole.request(this) ?: return
        com.ogneliss.dialer.sms.SmsSettings.markRolePromptDone(this)
        runCatching {
            com.ogneliss.dialer.util.RoleAccess.markRequested()
            @Suppress("DEPRECATION")
            startActivityForResult(request, com.ogneliss.dialer.sms.SmsRole.AUTO_REQUEST_CODE)
        }.onFailure { com.ogneliss.dialer.log.Logger.w("SmsRole", "Окно роли SMS не открылось — пропущено", it) }
    }

    private fun numberFromIntent(intent: Intent?): String? =
        intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart

    /**
     * НОМЕР, ЧЬЮ КАРТОЧКУ НУЖНО ОТКРЫТЬ.
     *
     * ── Откуда приходит ────────────────────────────────────────────────────
     * Из уведомления напоминания. Раньше оно открывало системный набор
     * через ACTION_DIAL — и часть прошивок это намерение молча
     * игнорировала: человек нажимал, ничего не происходило.
     *
     * Карточка полезнее набора: в ней видна история общения, заметка
     * и кнопки звонка, сообщения и повторного напоминания. Именно эти
     * сведения и нужны, когда возвращаешься к отложенному разговору.
     */
    private fun cardNumberFromIntent(intent: Intent?): String? =
        intent?.takeIf { it.action == ACTION_OPEN_CARD }?.getStringExtra(EXTRA_CARD_NUMBER)

}

/*
 * Метка экрана для замеров (1.3.3, 03.10.2026).
 *
 * Замеры трижды мерили не тот экран: мастер разрешений, экран согласия,
 * снова мастер — и это было видно только по сгенерированному профилю.
 * Теперь DeviceSetup в модуле benchmark после подготовки запускает
 * приложение и проверяет, что на экране root_screen, а не
 * permission_wizard или экран согласия. testTagsAsResourceId делает
 * метку видимой для UI Automator; на пользователя не влияет.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.screenTag(tag: String): Modifier = this
    .fillMaxSize()
    .semantics { testTagsAsResourceId = true }
    .testTag(tag)
