package com.ogneliss.dialer

import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import com.ogneliss.dialer.ui.adaptive.ProvideWindowLayout
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

/** Номер текущей редакции документов. Увеличивается при их изменении. */
private const val CONSENT_VERSION = 1

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Первый осмысленный кадр готов.
     *
     * Ставится, как только прочитана версия согласия: до этого
     * неизвестно, что рисовать, и заставка честнее пустого экрана.
     */
    private var firstFrameReady = false

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

        setContent {
            val vm: DialerViewModel = hiltViewModel()
            val theme by vm.prefs.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dynamic by vm.prefs.dynamicColor.collectAsStateWithLifecycle(false)
            // Масштаб интерфейса под экран (1.5.6): разбор в ui/theme/UiScale.kt
            val uiScale by vm.prefs.uiScale.collectAsStateWithLifecycle(com.ogneliss.dialer.settings.UiScale.DEFAULT)

            OgnelissDialerTheme(themeMode = theme, dynamicColor = dynamic, uiScale = uiScale) {
                // Раскладка окна: телефон, лёжа, раскладушка, планшет.
                // Разбор — в ui/adaptive/WindowLayout.kt
                ProvideWindowLayout(this@MainActivity) {
                var isDefault by remember { mutableStateOf(DefaultDialer.isDefault(this)) }
                var permsOk by remember {
                    mutableStateOf(AppPermissions.missing(this).none { it.required })
                }
                var onboardingDone by remember { mutableStateOf(false) }

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

                    !onboardingDone && (!isDefault || !permsOk) -> {
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
                                onFinished = { onboardingDone = true }
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
                            ?.getBooleanExtra(EXTRA_OPEN_BLOCK_LISTS, false) == true
                    ) }
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
        com.ogneliss.dialer.call.MissedCallsNotifier.clear(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent = intent
        intentToken++
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
