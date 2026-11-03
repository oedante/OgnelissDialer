package com.ogneliss.dialer.call

import com.ogneliss.dialer.settings.ContactNameFormat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.IconButtonDefaults
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ogneliss.dialer.concurrency.suspendRunCatching

import com.ogneliss.dialer.ui.adaptive.ProvideWindowLayout
import com.ogneliss.dialer.ui.components.FitText
import com.ogneliss.dialer.ui.components.Motion
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.DeclineRed
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.util.TagCatalog
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.ui.components.Dialpad
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import androidx.compose.ui.platform.LocalDensity
import com.ogneliss.dialer.ui.adaptive.FoldPosture
import com.ogneliss.dialer.settings.CarrierInfoMode
import com.ogneliss.dialer.settings.CallPhotoSize
import com.ogneliss.dialer.settings.AnswerMode
import com.ogneliss.dialer.settings.CallPhotoStyle
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.ogneliss.dialer.data.NoteDisplay
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.ui.components.OgnelissDivider
import com.ogneliss.dialer.util.PhoneFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    private var proximity: ProximityLock? = null
    private var smartSpeaker: SmartSpeakerController? = null

    @Inject lateinit var prefs: com.ogneliss.dialer.settings.Prefs

    /** Нужен для фотографии звонящего: Details её не содержит. */
    @Inject lateinit var contactsRepo: com.ogneliss.dialer.data.ContactsRepository

    /** Заметка о звонящем: самый нужный момент для неё — входящий вызов. */
    @Inject lateinit var notesRepo: com.ogneliss.dialer.data.NotesRepository

    /** Отметки «спам» и «мошенники» — отдельно от заметок с 1.1.1. */
    @Inject lateinit var spamMarksRepo: com.ogneliss.dialer.data.SpamMarksRepository

    /**
     * Реестр нумерации: оператор и регион звонящего.
     *
     * Именно на этом экране сведения нужнее всего. В журнале человек
     * разбирает звонки задним числом, а здесь решает прямо сейчас,
     * брать ли трубку, — и «Тюменская область» у незнакомого номера
     * отвечает на этот вопрос лучше любой оценки риска.
     */
    @Inject lateinit var numberingPlan: com.ogneliss.dialer.data.NumberingPlan

    /**
     * Область приложения для сохранения заметки.
     *
     * Экран вызова закрывается ровно тогда, когда разговор окончен,
     * и область композиции умирает вместе с ним — запись обрывалась
     * на полпути. Эта живёт дольше.
     */
    @Inject @com.ogneliss.dialer.di.ApplicationScope
    lateinit var appScope: kotlinx.coroutines.CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * EDGE-TO-EDGE НУЖЕН И ЗДЕСЬ.
         *
         * С Android 15 система рисует приложение под панелями всегда,
         * независимо от того, просило оно об этом или нет. Экран вызова
         * это касается наравне с остальными: без явного вызова
         * отступы считаются по старым правилам, и кнопка «Завершить»
         * может оказаться под панелью навигации.
         *
         * Без своих подложек: с API 35 методы, которыми они задавались,
         * ничего не делают, а Google Play помечает их использование
         * как неподдерживаемое.
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

        // Показ поверх экрана блокировки — обязателен для входящего вызова
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        /*
         * Датчик приближения — по настройке (по умолчанию включён).
         * Выключить просили в отзыве в Google Play: на части телефонов
         * датчик гасит экран не вовремя. Без него щека может нажать
         * кнопку — об этом сказано в описании настройки.
         *
         * С 1.8.1 — только у уха: держит его LaunchedEffect ниже, по состоянию
         * вызова и звуку. Разбор — ProximityLock.
         */
        proximity = ProximityLock(this)

        // Авто-громкая связь: подписка на датчики только если функция
        // включена, иначе разговор час жёг бы батарею впустую
        lifecycleScope.launch {
            if (prefs.smartSpeaker.first()) {
                smartSpeaker = SmartSpeakerController(this@InCallActivity) { route ->
                    CallManager.setAudioRoute(route)
                }.also { controller ->
                    if (controller.isSupported()) controller.start()
                }
            }
        }

        setContent {
            /*
             * Тема читается из настроек, как и на остальных экранах.
             *
             * Раньше здесь вызывалось OgnelissDialerTheme без параметров,
             * то есть со значениями по умолчанию: экран вызова оставался
             * светлым при выбранной тёмной теме. Заметить это трудно —
             * входящий звонок на эмуляторе не воспроизвести, а на устройстве
             * экран показывается в момент, когда не до разглядывания.
             *
             * Для AMOLED это ещё и вопрос батареи: экран вызова горит
             * дольше остальных, и чёрный фон на нём экономит заметно больше.
             */
            val theme by prefs.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamic by prefs.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
            // Масштаб интерфейса — тот же, что в приложении (1.5.6)
            val uiScale by prefs.uiScale.collectAsStateWithLifecycle(initialValue = com.ogneliss.dialer.settings.UiScale.DEFAULT)
            // Подписи под кнопками — та же настройка, что во всём приложении.
            // Экран вызова — отдельная активность, корня приложения у него
            // нет, поэтому оформление ставится здесь
            val buttonLabels by prefs.buttonLabels.collectAsStateWithLifecycle(initialValue = true)
            val hideContactNumber by prefs.callHideContactNumber.collectAsStateWithLifecycle(initialValue = false)
            // Живой фон (1.5.1): по настройке; в высоком контрасте — нет,
            // там фон ровный ради различимости
            val callAnimation by prefs.callAnimation.collectAsStateWithLifecycle(initialValue = true)
            // Фото звонящего (1.5.5). null — настройка ещё не прочитана:
            // фото не показываем ни так, ни так, чтобы оно не мигнуло
            // на весь экран перед кругом
            val callPhotoStyle by prefs.callPhotoStyle
                .collectAsStateWithLifecycle<CallPhotoStyle?>(initialValue = null)
            // Размер портрета (1.5.7, отзыв valmikk): обычный или крупный
            val callPhotoSize by prefs.callPhotoSize
                .collectAsStateWithLifecycle(initialValue = CallPhotoSize.LARGE)
            // Ответ на вызов: касанием или сдвигом (1.5.8): разбор в AnswerMode
            val answerMode by prefs.answerMode
                .collectAsStateWithLifecycle(initialValue = AnswerMode.TAP)
            // Имя «Фамилия Имя» (1.6.2): разбор в data/ContactNames.kt
            val nameFormat by prefs.contactNameFormat
                .collectAsStateWithLifecycle(initialValue = ContactNameFormat.GIVEN_FIRST)

            val themePreset by prefs.themePreset.collectAsStateWithLifecycle(com.ogneliss.dialer.settings.ThemePreset.CLASSIC)
            OgnelissDialerTheme(themeMode = theme, dynamicColor = dynamic, uiScale = uiScale, themePreset = themePreset) {
                // Раскладка окна: телефон, лёжа, раскладушка, планшет.
                // Разбор — в ui/adaptive/WindowLayout.kt
                ProvideWindowLayout(this@InCallActivity) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.ogneliss.dialer.ui.theme.LocalAppearance provides
                        com.ogneliss.dialer.ui.theme.Appearance(buttonLabels = buttonLabels)
                ) {
                val calls by CallManager.calls.collectAsStateWithLifecycle()
                val revision by CallManager.revision.collectAsStateWithLifecycle()
                val audio by CallManager.audioState.collectAsStateWithLifecycle()

                /*
                 * Режим показа оператора берётся из тех же настроек,
                 * что и журнал: человек задаёт правило один раз,
                 * а не отдельно для каждого экрана.
                 */
                val carrierInfo by prefs.carrierInfo
                    .collectAsStateWithLifecycle(initialValue = CarrierInfoMode.UNKNOWN_ONLY)

                // Автодозвон (1.8.0): сеанс держит экран между попытками
                val redial by AutoRedial.state.collectAsStateWithLifecycle()
                // Памятка о мошенниках (1.8.0): разбор в ScamMemo.kt
                val scamMemo by prefs.scamMemo.collectAsStateWithLifecycle(initialValue = true)

                /*
                 * Экран закрывается, когда нет ни вызовов, ни автодозвона (1.8.0).
                 * Сеанс читается напрямую, а не из состояния: служба создаёт его
                 * раньше, чем опустеет список вызовов, а до композиции новые
                 * значения двух потоков могут дойти в любом порядке.
                 */
                LaunchedEffect(calls, redial) {
                    if (calls.isEmpty() && AutoRedial.state.value == null) finish()
                }
                // «Назад» между попытками — остановить автодозвон; экран закроется сам
                BackHandler(enabled = calls.isEmpty() && redial != null) { AutoRedial.stop() }

                // Датчики должны знать о состоянии вызова и аудиомаршрута
                val proximityOn by prefs.proximityScreenOff.collectAsStateWithLifecycle(initialValue = false)
                LaunchedEffect(revision, audio, proximityOn, redial, calls) {
                    val primary = CallManager.primary
                    smartSpeaker?.updateCallState(primary, audio)
                    @Suppress("DEPRECATION")
                    val callState = primary?.state
                    // Датчик — только у уха (1.8.1): разбор в ProximityLock
                    proximity?.hold(proximityOn && CallScreenRules.proximityWanted(callState, audio?.route))
                    /*
                     * Экран не гаснет, пока звонит и пока идёт отсчёт автодозвона
                     * (1.8.1): на экране блокировки система гасит его через
                     * несколько секунд, и звонящий телефон темнел посреди звонка.
                     * В разговоре — как обычно: у уха гасит датчик, на громкой
                     * связи — тайм-аут экрана.
                     */
                    if (callState == Call.STATE_RINGING || (calls.isEmpty() && redial != null)) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                /*
                 * ЭКРАН БОЛЬШЕ НЕ ПЕРЕСОБИРАЕТСЯ ЗАНОВО.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ЧТО БЫЛО
                 * ══════════════════════════════════════════════════════════
                 * Здесь стоял key(revision) { InCallScreen(...) }. Это
                 * не «перерисовать», а «выбросить и построить заново»:
                 * при каждом изменении revision Compose уничтожал всё
                 * поддерево вместе с его remember.
                 *
                 * А revision растёт на каждое событие вызова: ответ,
                 * удержание, смена аудиомаршрута. То есть при нажатии
                 * «Ответить» экран строился с нуля: фотография
                 * запрашивалась заново, имя и номер на мгновение пустели,
                 * набранный в заметке текст пропадал. Видно это было
                 * как вспышка.
                 *
                 * ── Как теперь ─────────────────────────────────────────────
                 * revision передаётся параметром. Меняется значение —
                 * функция перерисовывается и перечитывает состояние
                 * из объекта Call (он изменяемый, и сам по себе Compose
                 * его изменений не видит — ради этого revision и заведён).
                 * Но remember при этом остаётся жив: портрет, заметка
                 * и имя переживают ответ на вызов.
                 */
                InCallScreen(
                    numberingPlan = numberingPlan,
                    carrierInfo = carrierInfo,
                    call = CallManager.primary,
                    secondary = CallManager.secondary,
                    revision = revision,
                    audioState = audio,
                    contactsRepo = contactsRepo,
                    appScope = appScope,
                    notesRepo = notesRepo,
                    spamMarksRepo = spamMarksRepo,
                    hideContactNumber = hideContactNumber,
                    ambientBackground = callAnimation && theme != ThemeMode.HIGH_CONTRAST,
                    bareControls = theme != ThemeMode.HIGH_CONTRAST,
                    photoStyle = callPhotoStyle,
                    largePortrait = callPhotoSize == CallPhotoSize.LARGE,
                    slideToAnswer = answerMode == AnswerMode.SLIDE,
                    familyFirst = nameFormat == ContactNameFormat.FAMILY_FIRST,
                    redial = redial,
                    scamMemo = scamMemo,
                    // Пользователь переключил звук сам — датчики умолкают
                    // до конца разговора
                    onManualAudioChange = { smartSpeaker?.notifyManualToggle() }
                )
            }
            }
                }
        }
    }

    /*
     * ПОКАЗАН ЛИ ЭКРАН ВЫЗОВА НА САМОМ ДЕЛЕ (1.8.1).
     *
     * Служба вызовов не может знать это по startActivity: прошивка бывает
     * молча не пускает окно из фона. Окно сообщает само — и уведомление
     * входящего становится тихим (на экране свои кнопки) или, если ушли
     * с экрана, пока звонит, снова с кнопками. Разбор — IncomingScreen.
     */
    override fun onStart() {
        super.onStart()
        IncomingScreen.onShown(this)
    }

    override fun onStop() {
        IncomingScreen.onHidden(this)
        super.onStop()
    }

    override fun onDestroy() {
        smartSpeaker?.stop()
        proximity?.release()
        /*
         * Экран убрали сами — смахнули задачу, закрыла система, — а вызовов
         * нет: автодозвон без экрана не продолжаем (1.8.0). Обычное закрытие
         * сюда приходит уже без сеанса: экран закрывается, когда его нет.
         */
        if (isFinishing && CallManager.calls.value.isEmpty()) AutoRedial.stop()
        super.onDestroy()
    }
}

/**
 * Экран активного вызова.
 *
 * В ландшафте информация об абоненте и кнопки управления встают рядом:
 * вертикально они не помещаются, и кнопка «Завершить» уезжает за экран —
 * это ровно та жалоба «не могу завершить звонок», что встречается
 * в отзывах на сторонние диалеры.
 */
@Composable
private fun InCallScreen(
    numberingPlan: com.ogneliss.dialer.data.NumberingPlan,
    /** Настройка «Оператор и регион»: та же, что в журнале. */
    carrierInfo: CarrierInfoMode,
    call: Call?,
    secondary: Call?,
    /**
     * Счётчик событий телефонии.
     *
     * Call — изменяемый объект из системного API: его состояние меняется
     * внутри, а Compose таких изменений не видит и перерисовку не назначит.
     * Счётчик растёт на каждое событие и служит признаком «данные вызова
     * могли измениться, перечитай их».
     *
     * Раньше он использовался как key() снаружи — и вместо перечитывания
     * экран пересобирался с нуля, теряя портрет и текст заметки.
     */
    revision: Int,
    audioState: CallAudioState?,
    contactsRepo: com.ogneliss.dialer.data.ContactsRepository,
    /** Область приложения: переживает закрытие экрана вызова. */
    appScope: kotlinx.coroutines.CoroutineScope,
    notesRepo: com.ogneliss.dialer.data.NotesRepository,
    spamMarksRepo: com.ogneliss.dialer.data.SpamMarksRepository,
    /** У сохранённого контакта — тип номера вместо номера. */
    hideContactNumber: Boolean = false,
    /** Живой фон за экраном (CallAmbientBackground.kt); без фото на весь экран. */
    ambientBackground: Boolean = false,
    /** Кнопки без панели и на ровном фоне (1.6.2): всё, кроме высокого контраста. */
    bareControls: Boolean = false,
    /** Фото звонящего: круг, квадрат или на весь экран; null — ещё не прочитано. */
    photoStyle: CallPhotoStyle? = CallPhotoStyle.CIRCLE,
    /** Настройка «Размер фото» — крупный (1.5.7). */
    largePortrait: Boolean = false,
    /** Настройка «Ответ на вызов» — сдвигом (1.5.8): разбор в SlideToAnswer. */
    slideToAnswer: Boolean = false,
    /** Настройка «Как показывать имя» — «Фамилия Имя» (1.6.2). */
    familyFirst: Boolean = false,
    /** Сеанс автодозвона (1.8.0): между попытками вместо кнопок вызова — его кнопки. */
    redial: AutoRedial.Session? = null,
    /** Памятка о мошенниках (1.8.0): разбор в ScamMemo.kt. */
    scamMemo: Boolean = true,
    onManualAudioChange: () -> Unit = {}
) {
    // Сдвиг вместо кнопок ответа — если выбран и не включён TalkBack
    val slideAnswer = useSlideToAnswer(slideToAnswer)
    // Мало места по высоте — раскладка в две колонки. Порог прежний
    // (480 dp), но теперь один на всё приложение: разбор в WindowLayout
    val landscape = LocalWindowLayout.current.isShort
    // Состояние панелей локальное для экрана: при повороте оно должно
    // сбрасываться. Открытая DTMF-панель после поворота перекрыла бы
    // кнопку завершения вызова в ландшафте
    var showDialpad by remember { mutableStateOf(false) }
    var showQuickReply by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var dtmfBuffer by remember { mutableStateOf("") }
    /*
     * Состояние перечитывается по revision.
     *
     * call.state — обычное поле изменяемого объекта, а не состояние
     * Compose: подписаться на него нельзя. Ключ revision заставляет
     * перечитать значение после каждого события телефонии.
     */
    /*
     * Предупреждение «val state is deprecated» — осознанное.
     *
     * Замена, Call.getDetails().getState(), появилась только в API 31,
     * а minSdk у нас 26. Пока он не поднимется, ветка со старым
     * свойством нужна всё равно, и подавлять предупреждение точечно —
     * значит спрятать напоминание о том, что этот код однажды надо
     * переписать.
     */
    @Suppress("DEPRECATION")
    val state = remember(revision, call) { call?.state ?: Call.STATE_DISCONNECTED }
    /*
     * Между попытками автодозвона (1.8.0) вызова нет, а сеанс есть: экран
     * остаётся тем же — портрет и имя на месте, — а вместо «Вызов завершён»
     * и «Завершить» — состояние и кнопки автодозвона. Разбор — RedialPanel.kt.
     */
    val redialSession = redial?.takeIf { call == null }
    // Идёт попытка автодозвона — под плашкой «попытка 2 из 5»
    val redialAttempt = redial?.takeIf { call != null && it.phase == RedialPolicy.Phase.CALLING }
    /*
     * ЛИЧНОСТЬ СОБЕСЕДНИКА «ЗАЛИПАЕТ».
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ФОТО ПРОПАДАЛО ПРИ ЗАВЕРШЕНИИ
     * ══════════════════════════════════════════════════════════════════════
     * Номер, имя и фотография брались напрямую из call. Как только
     * разговор заканчивается, система убирает объект вызова, call
     * становится null — и всё разом обнулялось: имя, номер, портрет.
     *
     * На экране это выглядело так: «Вызов завершён», а вместо человека
     * серый силуэт без имени и номера. Хотя экран ещё секунду виден
     * и должен показывать, с кем говорили.
     *
     * ── Та же причина у мигания при ответе ─────────────────────────────────
     * При нажатии «Ответить» система пересобирает список вызовов,
     * и call на короткое время приходит пустым. Экран успевал
     * перерисоваться пустым и обратно — это и читалось как вспышка.
     *
     * ── Как теперь ─────────────────────────────────────────────────────────
     * Живые значения читаются из call, но в состояние попадают только
     * непустые. Стало известно имя — запомнили; пропал вызов — держим
     * последнее известное. Обратно в «неизвестно» экран уже не падает.
     *
     * Фотографию отдельно чинить не нужно: produceState ниже перезапускается
     * по ключу number, а number больше не обнуляется — значит, и портрет
     * остаётся на месте.
     */
    val liveNumber = remember(revision, call) {
        call?.details?.handle?.schemeSpecificPart.orEmpty()
    }
    val liveName = remember(revision, call) {
        CallDetailsCompat.contactDisplayName(call?.details)
    }

    var number by remember { mutableStateOf(liveNumber) }
    var name by remember { mutableStateOf(liveName) }

    /*
     * ИМЯ ОТ ОПЕРАТОРА (1.8.0, «маркировка звонков»): название организации,
     * которое сеть передала вместе с номером. «Залипает» так же, как имя:
     * пропал вызов — держим последнее. Разбор — CallDetailsCompat.networkName.
     */
    val liveNetworkName = remember(revision, call) { CallDetailsCompat.networkName(call?.details) }
    var networkName by remember { mutableStateOf(liveNetworkName) }
    LaunchedEffect(liveNetworkName) {
        if (!liveNetworkName.isNullOrBlank()) networkName = liveNetworkName
    }

    /*
     * ДО ANDROID 11 ИМЯ КОНТАКТА ИЩЕМ САМИ (1.8.0).
     *
     * Система отдаёт имя контакта в вызове только с Android 11
     * (CallDetailsCompat.contactDisplayName). На Android 8–10 экран вызова
     * показывал контакт номером — запасной путь, обещанный в CallDetailsCompat,
     * так и не был сделан. Поиск по номеру идёт по индексу и занимает
     * миллисекунды; пока он идёт, на экране номер.
     */
    LaunchedEffect(liveNumber) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && liveNumber.isNotBlank() && name.isNullOrBlank()) {
            val found = suspendRunCatching { contactsRepo.lookupByNumber(liveNumber) }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
            name = if (familyFirst) {
                suspendRunCatching { contactsRepo.familyFirstName(liveNumber, found) }.getOrNull() ?: found
            } else found
        }
    }

    /*
     * Ключи здесь — строки, а не объекты.
     *
     * LaunchedEffect сравнивает ключи по equals, поэтому пересозданная
     * строка с тем же содержимым перезапуска не вызывает. Раньше
     * ключом служил объект details, и он менялся при каждом обновлении
     * от системы — чтение фотографии запускалось заново по нескольку
     * раз в секунду, и портрет мигал.
     */
    LaunchedEffect(liveNumber) {
        if (liveNumber.isNotBlank()) number = liveNumber
    }
    LaunchedEffect(liveName, familyFirst) {
        if (!liveName.isNullOrBlank()) {
            name = liveName
            /*
             * «Фамилия Имя» (1.6.2): система даёт имя только в основной
             * форме. Переставляем, если это имя того же контакта, —
             * разбор у ContactsRepository.familyFirstName. Сначала
             * показываем как есть: запрос короткий, но входящий ждать
             * его не должен.
             */
            if (familyFirst) {
                suspendRunCatching {
                    contactsRepo.familyFirstName(liveNumber.ifBlank { number }, liveName)
                }.getOrNull()?.let { name = it }
            }
        }
    }

    /*
     * Фотография звонящего.
     *
     * Details её не содержит — система отдаёт только имя. Запрашиваем
     * сами точечным запросом по номеру; он идёт по индексу и занимает
     * миллисекунды, что допустимо даже на экране входящего вызова.
     *
     * produceState: пока запрос идёт, показывается обобщённая иконка,
     * и экран не ждёт результата.
     */
    val photoUri by produceState<String?>(initialValue = null, number) {
        value = suspendRunCatching { contactsRepo.photoUriForNumber(number) }.getOrNull()
    }
    // Тип номера — только если он понадобится: настройка включена
    // и звонит сохранённый контакт
    val numberType by produceState<String?>(null, number, hideContactNumber, name) {
        value = if (hideContactNumber && !name.isNullOrBlank()) {
            suspendRunCatching { contactsRepo.numberTypeLabel(number) }.getOrNull()
        } else null
    }
    /*
     * ══════════════════════════════════════════════════════════════════════
     * ЧТО ПОКАЗАТЬ ПОД ИМЕНЕМ
     * ══════════════════════════════════════════════════════════════════════
     * Отзыв в Google Play просил дважды: «показывать у сохранённых
     * контактов только имя». Против было одно возражение: у контакта
     * бывает несколько номеров, в том числе зарубежный, и номер
     * показывает, какой именно звонит.
     *
     * Поэтому при включённой настройке вместо номера — его тип из книги:
     * «Мобильный», «Рабочий», своя подпись. Какой номер звонит — видно,
     * цифр нет. Если тип не прочитался — номер, как обычно: лучше лишние
     * цифры, чем гадать. У незнакомых номеров номер показывается всегда.
     */
    val numberLine = if (hideContactNumber && !name.isNullOrBlank() && numberType != null) {
        numberType
    } else null

    /*
     * ЗАМЕТКА О ЗВОНЯЩЕМ.
     *
     * Самый нужный момент для заметки — именно этот: человек решает,
     * брать трубку или нет, и «мошенники» или «доставка мебели» отвечают
     * на вопрос лучше любого номера.
     *
     * Предел длины здесь больше, чем в списке: экран пустой, места хватает.
     */
    /*
     * rememberCoroutineScope здесь больше нет.
     *
     * Он остался от прежней версии сохранения заметки и после перехода
     * на appScope (см. ниже) не использовался ни разу — переменная жила
     * только в предупреждениях компилятора. Область композиции для
     * заметки и не годится: она умирает вместе с экраном вызова,
     * то есть ровно в момент, когда заметку надо дописать.
     */

    /*
     * ЗАМЕТКА ЖИВЁТ ДОЛЬШЕ ЭКРАНА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НЕ rememberCoroutineScope
     * ══════════════════════════════════════════════════════════════════════
     * Она умирает вместе с композицией. Экран вызова закрывается ровно
     * в тот момент, когда разговор окончен, — и сохранение заметки,
     * запущенное за миг до этого, обрывается на полпути.
     *
     * В логе это выглядело так:
     *   W [InCallNote] Не удалось прочитать заметку
     *   Q.T: The coroutine scope left the composition
     *
     * Текст при этом терялся молча: человек написал, разговор
     * закончился, заметки нет.
     *
     * Область приложения переживает закрытие экрана: запись доходит
     * до базы, даже если экран уже пропал.
     */
    val noteState = remember(number) { InCallNoteState(notesRepo, spamMarksRepo, appScope) }

    /*
     * Панель заметки не закрывается сама.
     *
     * AnimatedVisibility скрывает содержимое, но состояние остаётся:
     * текст не теряется, если человек свернул панель посмотреть
     * на экран вызова и открыл снова.
     */
    LaunchedEffect(number) { noteState.load(number) }

    // Завершение вызова: экран вот-вот исчезнет, ждать паузу нельзя
    DisposableEffect(number) { onDispose { noteState.saveNow() } }

    /*
     * ИМЯ, ЗАМЕТКА И НОМЕР ПО ОБЩИМ ПРАВИЛАМ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ЧЕРЕЗ NoteDisplay
     * ══════════════════════════════════════════════════════════════════════
     * Те же правила действуют в журнале вызовов и в карточке абонента:
     *
     * • номер в контактах — имя сверху, заметка курсивом под ним;
     * • номера нет — заметка ВМЕСТО имени: она единственное, что
     *   человек знает об этом номере;
     * • нет ни того, ни другого — только номер.
     *
     * Своя реализация здесь означала бы, что при следующей правке
     * экраны разойдутся: в журнале «Сантехник Иван», а на входящем
     * просто номер. Человек не поймёт, почему приложение узнаёт
     * звонящего в одном месте и не узнаёт в другом.
     *
     * Отдельно важно на экране входящего: решение брать трубку
     * принимается за секунды, и заметка «мошенник» или «врач» —
     * ровно то, ради чего она записывалась.
     */
    /*
     * Ключ — только номер.
     *
     * Имя выводится из тех же данных и меняется вместе с ним; держать
     * его вторым ключом значит перезапускать чтение заметки всякий раз,
     * когда строка имени пересоздалась — а это происходило постоянно.
     */
    /*
     * ОПЕРАТОР И РЕГИОН ЗВОНЯЩЕГО.
     *
     * Читается в фоне: первое обращение к реестру после установки
     * распаковывает файл на четыре мегабайта, и на главном потоке
     * это дало бы заметную задержку ровно в момент звонка.
     *
     * Ключ — номер: пока идёт разговор, он не меняется, и повторных
     * обращений не будет.
     */
    val carrierLine by produceState<String?>(null, number, carrierInfo, name) {
        value = withContext(Dispatchers.IO) {
            val known = !name.isNullOrBlank()
            val show = when (carrierInfo) {
                CarrierInfoMode.HIDDEN -> false
                CarrierInfoMode.ALL -> true
                CarrierInfoMode.UNKNOWN_ONLY -> !known
            }
            if (!show || number.isBlank()) return@withContext null

            numberingPlan.lookup(PhoneFormatter.toE164(number))?.let { entry ->
                listOf(
                    com.ogneliss.dialer.data.NumberingPlan.shortOperatorName(entry.operator),
                    entry.region
                ).filter { it.isNotBlank() }.joinToString(" · ")
            }
        }
    }

    // Метки из заметки — с заглавной: на экране вызова это заголовок
    val fraudLabel = stringResource(R.string.tag_fraud)
        .replaceFirstChar { it.titlecase() }
    val spamLabel = stringResource(R.string.tag_spam)
        .replaceFirstChar { it.titlecase() }

    val callerDisplay by produceState(
        initialValue = NoteDisplay.Result(
            title = PhoneFormatter.format(number),
            subtitle = null,
            titleFromNote = false
        ),
        number,
        /*
         * name — тоже ключ.
         *
         * Имя теперь попадает в состояние через LaunchedEffect, то есть
         * на кадр позже номера. Без этого ключа заголовок считался бы
         * один раз, когда имени ещё нет, и на экране навсегда остался бы
         * номер вместо «Любимая».
         *
         * Ключ — строка, сравнение по содержимому: пока имя не изменилось,
         * перезапуска нет.
         */
        name,
        // Имя от оператора (1.8.0) — тоже: приходит вместе с вызовом или чуть позже
        networkName
    ) {
        value = runCatching {
            val stored = notesRepo.get(PhoneFormatter.toE164(number))
            // Отметка — из своего хранилища, а не из заметки
            val tags = listOfNotNull(
                spamMarksRepo.get(PhoneFormatter.toE164(number))?.type?.key
            )

            /*
             * ══════════════════════════════════════════════════════════════
             * МЕТКА «СПАМ» ИЛИ «МОШЕННИКИ» — ВМЕСТО ИМЕНИ
             * ══════════════════════════════════════════════════════════════
             * Экран вызова — единственное место, где метка нужна по-настоящему:
             * на решение «брать или нет» у человека три секунды, и номер
             * ему ничего не скажет. «Мошенники» крупно и красным — скажет.
             *
             * Сюда вызов попадает, только когда переключатель «Блокировать
             * помеченные» выключен: при включённом SpamPolicy отклоняет
             * помеченный номер ещё до звонка. То есть этот экран — ровно
             * тот случай, когда человек решил сам.
             *
             * ⚠️ Имя из книги метку не вытесняет, а стоит над ней: если
             * номер записан в контактах и при этом помечен, человек сам
             * его записал — и имя важнее. Но и метку показать надо,
             * поэтому она идёт подписью.
             */
            val warning = when {
                "fraud" in tags -> fraudLabel
                "spam" in tags -> spamLabel
                else -> null
            }

            when {
                warning != null && name.isNullOrBlank() -> NoteDisplay.Result(
                    title = warning,
                    subtitle = stored?.note?.takeIf { it.isNotBlank() },
                    titleFromNote = true
                )

                warning != null -> NoteDisplay.Result(
                    title = name.orEmpty(),
                    subtitle = warning,
                    titleFromNote = false
                )

                // Имя от оператора — вместо имени, если нет ни контакта, ни заметки:
                // заметка человека важнее сведений сети (1.8.0)
                else -> NoteDisplay.forCallScreen(
                    displayName = name ?: networkName?.takeIf { stored?.note.isNullOrBlank() },
                    note = stored?.note,
                    formattedNumber = PhoneFormatter.format(number)
                )
            }
        }.getOrElse {
            NoteDisplay.Result(PhoneFormatter.format(number), null, false)
        }
    }

    /*
     * Есть ли у номера опасная метка — для цвета заголовка.
     *
     * Считается отдельно от callerDisplay, потому что тот отвечает
     * на вопрос «что написать», а этот — «каким цветом». Смешать их
     * значило бы добавить в общий NoteDisplay поле, нужное только здесь.
     */
    val isDangerTagged by produceState(false, number) {
        value = runCatching {
            spamMarksRepo.get(PhoneFormatter.toE164(number)) != null
        }.getOrDefault(false)
    }

    /*
     * Чьё имя в заголовке — подписью (1.8.0). Имя от оператора — не контакт
     * и не заметка: «по данным оператора». Заголовок — заметка или метка, —
     * имя сети уходит в подпись целиком: «Банк — по данным оператора».
     * Подменить имя мошенник может так же, как номер, — поэтому оно
     * не красится и не выдаётся за проверенное.
     */
    val operatorLine = when {
        !name.isNullOrBlank() || networkName.isNullOrBlank() -> null
        callerDisplay.titleFromNote -> stringResource(R.string.cnap_source_named, networkName.orEmpty())
        else -> stringResource(R.string.cnap_source)
    }

    // Памятка о мошенниках (1.8.0): незнакомый номер, разговор дольше двух минут
    val memo = rememberScamMemo(
        enabled = scamMemo,
        active = state == Call.STATE_ACTIVE,
        unknown = name.isNullOrBlank(),
        connectedAt = remember(revision, call) { call?.details?.connectTimeMillis ?: 0L },
        key = number
    )

    if (showQuickReply) {
        QuickReplySheet(
            templates = QuickReply.defaultTemplates(context),
            onSend = { message ->
                // Если отклонение с текстом не сработало, вызов всё равно
                // нужно сбросить: пользователь этого и хотел
                if (!QuickReply.rejectWith(message)) CallManager.reject()
                showQuickReply = false
            },
            onDismiss = { showQuickReply = false }
        )
    }

    /*
     * ОТСТУПЫ ОТ СИСТЕМНЫХ ПАНЕЛЕЙ.
     *
     * Экран вызова рисуется во весь экран, включая области под строкой
     * состояния и панелью навигации. Раньше это сходило с рук: система
     * сама держала подложки. С Android 15 она этого не делает, и без
     * safeDrawingPadding кнопка «Завершить» уезжает под панель
     * навигации, а таймер разговора — под часы.
     *
     * Здесь, а не в каждой раскладке: портретная и альбомная ветки
     * рисуются внутри этой Surface, и отступ нужен обеим.
     */
    /*
     * ФОТО ЗВОНЯЩЕГО НА ВЕСЬ ЭКРАН (1.3.4, 04.10.2026, фаза 4; образец —
     * экран вызова Google, скриншот автора 03.10).
     *
     * Было: круглый портрет 132 dp на сплошном фоне. Стало: фото контакта
     * (PHOTO_URI — полноразмерное, не миниатюра) под всем экраном, включая
     * строку состояния, с затемнением сверху для имени и значков; имя
     * и состояние — светлым поверх фото; управление — в нижней панели.
     * Только в обычной портретной раскладке: лёжа и в позе «ноутбук» места
     * по высоте мало, там прежний вид. Нет фото — прежний вид с панелью.
     */
    val windowLayout0 = LocalWindowLayout.current
    val tabletop0 = windowLayout0.posture == FoldPosture.TABLETOP &&
        windowLayout0.hinge?.let { !it.vertical } == true
    /*
     * 1.5.5: фото на весь экран — только если так выбрано в настройках
     * (Настройки → Вызовы → «Фото на экране вызова»). По умолчанию —
     * портрет в круге, и волны живого фона расходятся от него.
     */
    val photoBackground = photoStyle == CallPhotoStyle.FULL_SCREEN &&
        !landscape && !tabletop0 && !photoUri.isNullOrBlank()
    // Портрет над именем: фото есть, настройка прочитана, и не фон
    val showPortrait = photoStyle != null && !photoUri.isNullOrBlank() && !photoBackground
    val squarePortrait = photoStyle == CallPhotoStyle.SQUARE
    // Где портрет на экране — от него расходятся волны (CallAmbientBackground)
    var portraitFocus by remember { mutableStateOf<AmbientFocus?>(null) }
    /*
     * Нет фото — волны от имени (1.8.1). Было: от точки на 13 % высоты,
     * где имя стояло до 1.7.0; имя с тех пор ниже, и волны шли от пустого
     * места над ним (снимок автора). Разбор у AmbientFocus.
     */
    var nameFocus by remember { mutableStateOf<AmbientFocus?>(null) }
    val trackName = Modifier.onGloballyPositioned { coords ->
        val bounds = coords.boundsInRoot()
        nameFocus = AmbientFocus(bounds.center, bounds.height / 2f, edge = false)
    }
    val trackPortrait = Modifier.onGloballyPositioned { coords ->
        val bounds = coords.boundsInRoot()
        // У квадрата волна начинается у углов, а не у середины стороны
        val half = bounds.width / 2f
        portraitFocus = AmbientFocus(bounds.center, if (squarePortrait) half * SQUARE_FOCUS else half)
    }
    LightStatusIconsOnPhoto(photoBackground)
    // Живой фон — когда нет фото на весь экран: фото само и есть фон
    val ambient = ambientBackground && !photoBackground
    // Без панели под кнопками — везде, кроме фото на весь экран и высокого
    // контраста (1.6.2, разбор у LocalControlsOnAmbient)
    val bare = ambient || (bareControls && !photoBackground)
    Box(Modifier.fillMaxSize()) {
    if (photoBackground) CallPhotoBackground(photoUri.orEmpty())
    // Во весь экран, под строкой состояния тоже — как обои
    if (ambient) {
        CallAmbientBackground(
            ambientMoodOf(state),
            // В цветах снимка звонящего (1.8.4), если портрет на экране
            photoUri = photoUri.takeIf { showPortrait },
            focus = { if (showPortrait) portraitFocus else nameFocus }
        )
    }
    // Ровный фон без панели — до краёв экрана (1.6.2)
    if (bare && !ambient) PlainCallBackground()
    // Полоса цвета панели под навигацией — только когда есть панель
    // (разбор у LocalControlsOnAmbient)
    if (!bare) NavBarUnderPanel()
    Surface(
        Modifier.fillMaxSize().safeDrawingPadding(),
        color = if (photoBackground || ambient) Color.Transparent else MaterialTheme.colorScheme.surface
    ) {
        /*
         * Кто звонит — фото, имя, оператор, состояние вызова. Общий блок
         * для раскладок «лёжа» и «ноутбук»: раньше жил только внутри
         * альбомной ветки, и вторая раскладка скопировала бы его целиком.
         */
        val identityBlock: @Composable () -> Unit = {
                    /*
                     * Портрет и в альбомной ориентации.
                     *
                     * Раньше он был только в портретной: при повороте
                     * телефона звонящий превращался в строку текста.
                     * Меньше, чем в портретной — 96 dp против 140:
                     * по высоте места вдвое меньше, и крупный портрет
                     * вытеснил бы имя за край.
                     */
                    // Отступ только вместе с портретом: без фотографии
                    // экран начинается с имени, и зазор сверху был бы
                    // отступом от пустоты
                    if (showPortrait) {
                        // Лёжа — по высоте окна (1.5.6): 30 % высоты,
                        // от 72 до 140 dp; было 88 dp на любом экране
                        CallerAvatar(
                            photoUri = photoUri,
                            size = (windowLayout0.heightDp * if (largePortrait) 0.36f else 0.30f).dp
                                .coerceIn(
                                    FlowUi.callPortraitMin,
                                    if (largePortrait) LANDSCAPE_PORTRAIT_MAX_LARGE else LANDSCAPE_PORTRAIT_MAX
                                ),
                            square = squarePortrait,
                            modifier = trackPortrait
                        )
                        Spacer(Modifier.height(Space.m))
                    }

                    CallerIdentity(
                        display = callerDisplay,
                        number = number,
                        carrierLine = carrierLine,
                        numberLine = numberLine,
                        danger = isDangerTagged,
                        titleSize = FlowUi.callNameSizeLandscape,
                        operatorLine = operatorLine,
                        titleModifier = trackName
                    )

                    Spacer(Modifier.height(Space.s))
                    if (redialSession != null) RedialStatus(redialSession) else {
                        CallStatusLine(state, call)
                        redialAttempt?.let { RedialAttemptLine(it) }
                    }
                }

        /** Главные кнопки: ответить и отклонить, или завершить. */
        /*
         * Кнопки разговора — звук, громкая связь, клавиши, удержание.
         * Один блок на портретную и альбомную раскладки (1.8.1): лёжа их
         * не было вовсе — ни выключить микрофон, ни включить громкую связь.
         */
        val controlsBlock: @Composable () -> Unit = {
            Box(
                Modifier.fillMaxWidth().padding(vertical = Space.s, horizontal = Space.s),
                contentAlignment = Alignment.Center
            ) {
                // Возможности читаются заново на каждое событие
                // телефонии: сеть сообщает их не сразу
                val canHold = remember(revision, call) { CallManager.canHold }
                val canMerge = remember(revision, call, secondary) { CallManager.canMerge }
                val canAddCall = remember(revision, call, secondary) { CallManager.canAddCall }
                val context = androidx.compose.ui.platform.LocalContext.current

                ControlsRow(
                    audioState = audioState,
                    onManualAudioChange = onManualAudioChange,
                    onHold = { CallManager.toggleHold() },
                    onDialpad = { showDialpad = !showDialpad },
                    onSwap = { CallManager.swapCalls() },
                    onMerge = { CallManager.merge() },
                    hasSecondary = secondary != null,
                    isHolding = state == Call.STATE_HOLDING,
                    canHold = canHold,
                    canMerge = canMerge,
                    canAddCall = canAddCall,
                    onAddCall = { openDialerForSecondCall(context) }
                )
            }
        }

        /*
         * Клавиши тонового набора — высота по окну (1.8.1).
         *
         * Панель клавиш со своей строкой и отступами — около 360 dp. На
         * телефоне 16 : 9 вместе с кнопками разговора она не помещалась,
         * и «Завершить» уезжала за край. Теперь при открытых клавишах кнопки
         * разговора убраны (как в системном диалере), а клавиши ниже, если
         * места мало, — не ниже 48 dp, удобной для пальца высоты.
         */
        val dtmfKeyHeight = fittedDtmfKeyHeight(
            windowHeightDp = windowLayout0.heightDp,
            reservedDp = if (landscape) CallScreenRules.DTMF_RESERVED_LANDSCAPE_DP
            else CallScreenRules.DTMF_RESERVED_PORTRAIT_DP
        )
        val dtmfBlock: @Composable () -> Unit = {
            DtmfPanel(
                entered = dtmfBuffer,
                onDigit = { digit ->
                    CallManager.playDtmf(digit)
                    dtmfBuffer += digit
                },
                onBackspace = { dtmfBuffer = dtmfBuffer.dropLast(1) },
                onClose = { showDialpad = false },
                keyHeight = dtmfKeyHeight
            )
        }

        val mainButtons: @Composable () -> Unit = {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Памятка о мошенниках (1.8.0) — над кнопками
                    AnimatedVisibility(memo.visible) {
                        ScamMemoCard(onDismiss = memo::dismiss, modifier = Modifier.padding(bottom = Space.m))
                    }
                    if (redialSession != null) {
                        RedialButtons(redialSession)
                    } else if (state == Call.STATE_RINGING && slideAnswer) {
                        // Ответ сдвигом (1.5.8): от случайного касания
                        SlideToAnswer(
                            onAnswer = { CallManager.answer() },
                            onDecline = { CallManager.reject() }
                        )
                    } else
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterHorizontally)
                    ) {
                        if (state == Call.STATE_RINGING) {
                            BigButton(
                                stringResource(R.string.incall_decline),
                                DeclineRed,
                                OgnelissIcons.CallEnd,
                                modifier = Modifier.weight(1f),
                                shareRow = true
                            ) {
                                CallManager.reject()
                            }
                            BigButton(
                                stringResource(R.string.incall_answer),
                                CallGreen,
                                OgnelissIcons.Call,
                                modifier = Modifier.weight(1f),
                                shareRow = true
                            ) {
                                CallManager.answer()
                            }
                        } else {
                            BigButton(
                                stringResource(R.string.incall_hangup),
                                DeclineRed,
                                OgnelissIcons.CallEnd
                            ) {
                                CallManager.hangup()
                            }
                        }
                    }
                  }
                }

        /*
         * ══════════════════════════════════════════════════════════════════
         * РАСКЛАДУШКА В ПОЛОЖЕНИИ «НОУТБУК»
         * ══════════════════════════════════════════════════════════════════
         * Полураскрытая раскладушка, сгиб горизонтальный: нижняя половина
         * лежит на столе, верхняя стоит. Так её ставят для громкой связи
         * и видеозвонков — и так же делает системное приложение на Pixel
         * Fold и Galaxy Fold: собеседник — наверху, где его видно,
         * кнопки — внизу, где до них достаёт палец.
         *
         * Граница — по сгибу, а не посередине экрана: у разных моделей
         * сгиб не ровно в центре, и ничего не должно лечь на сам сгиб —
         * там изображение искажено.
         *
         * ⚠️ Проверяется только на раскладушке или эмуляторе Pixel Fold:
         * у телефона сгиба нет, и эта ветка не срабатывает.
         */
        val windowLayout = LocalWindowLayout.current
        val hinge = windowLayout.hinge
        if (windowLayout.posture == FoldPosture.TABLETOP && hinge != null && !hinge.vertical) {
            val density = LocalDensity.current
            // Сгиб — в координатах окна, а содержимое начинается ниже
            // строки состояния: вычитаем её
            val topInsetDp = with(density) {
                WindowInsets.safeDrawing.getTop(density).toDp().value
            }
            val aboveHinge = (hinge.startDp - topInsetDp).coerceAtLeast(0f).dp
            val hingeThickness = (hinge.endDp - hinge.startDp).coerceAtLeast(0f).dp

            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(aboveHinge)
                        .padding(FlowUi.contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        identityBlock()
                    }
                }

                Spacer(Modifier.height(hingeThickness))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(FlowUi.contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    mainButtons()
                }
            }
            return@Surface
        }

        if (landscape) {
            /*
             * ЛЁЖА — И КНОПКИ РАЗГОВОРА (1.8.1).
             *
             * Было: слева кто звонит, справа только «Завершить» — повернул
             * телефон, и ни выключить микрофон, ни громкую связь, ни клавиш.
             * Теперь справа над главной кнопкой — те же кнопки разговора,
             * а клавиши открываются слева, на месте имени: по высоте лёжа
             * их больше некуда положить. Обе половины прокручиваются, если
             * места меньше, чем нужно; главные кнопки — всегда на виду.
             */
            val inCall = state != Call.STATE_RINGING && redialSession == null
            Row(
                Modifier.fillMaxSize().padding(FlowUi.contentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showDialpad && inCall) dtmfBlock() else identityBlock()
                }
                Spacer(Modifier.width(FlowUi.contentPadding))
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (inCall) {
                        Column(
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            controlsBlock()
                            Spacer(Modifier.height(Space.m))
                        }
                    }
                    mainButtons()
                }
            }
            return@Surface
        }

        /*
         * ОТСТУПЫ ПО МЕРКАМ ОСТАЛЬНОГО ПРИЛОЖЕНИЯ.
         *
         * Было 24 dp по краям и ещё 40 сверху. В журнале и контактах
         * поля 12 dp, и экран вызова рядом с ними выглядел разреженным:
         * между именем и кнопками оставалась пустота в треть экрана,
         * а сами кнопки уезжали к нижнему краю.
         *
         * Теперь 16 по краям (чуть больше журнальных 12: экран пустой,
         * и совсем прижимать к краю нечего) и 24 сверху вместо 40.
         */
        Column(
            // Поля — у верхнего блока: нижняя панель идёт на всю ширину (1.3.4)
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- Верх: кто звонит ---
            OnPhotoColors(photoBackground) {
            Column(
                Modifier
                    // 1.5.6: всё место над панелью — портрету и подписям;
                    // размер портрета считается от него (AdaptivePortraitColumn)
                    .weight(1f)
                    .fillMaxWidth()
                    // Места меньше, чем подписям, — они обрезаются здесь,
                    // а не ложатся на кнопки под ними (1.8.1)
                    .clipToBounds()
                    .padding(
                        top = Space.xl,
                        start = FlowUi.contentPadding,
                        end = FlowUi.contentPadding,
                        bottom = Space.m
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /*
                 * ПОРТРЕТ АБОНЕНТА.
                 *
                 * ── Почему его не было ─────────────────────────────────────
                 * Фотография запрашивалась, но нигде не рисовалась:
                 * на экране оставались только имя и номер. Узнать
                 * звонящего в лицо было нельзя.
                 *
                 * ── Размер — по экрану (1.5.6) ─────────────────────────────
                 * Было 132 dp на любом телефоне — на широком экране автора
                 * четверть ширины («очень маленький»). Теперь до 46 %
                 * ширины и не выше места над панелью: разбор во FlowUi
                 * (callPortraitMax) и в AdaptivePortraitColumn.
                 *
                 * Если фотографии нет, портрета нет вовсе: серый круг
                 * с силуэтом занимал верхнюю треть экрана и не сообщал
                 * ничего — силуэт одинаков у всех. Без него имя
                 * поднимается наверх, и длинному хватает места.
                 */
                AdaptivePortraitColumn(
                    portrait = if (showPortrait) {
                        {
                            CallerAvatar(
                                photoUri = photoUri,
                                size = FlowUi.callPortraitMaxFor(largePortrait),
                                square = squarePortrait,
                                modifier = trackPortrait
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxSize = FlowUi.callPortraitMaxFor(largePortrait),
                    widthFraction = FlowUi.callPortraitFractionFor(largePortrait),
                    // Ближе к середине экрана (1.7.0, разбор у CALL_IDENTITY_BIAS)
                    verticalBias = CALL_IDENTITY_BIAS
                ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CallerIdentity(
                    display = callerDisplay,
                    number = number,
                    carrierLine = carrierLine,
                    numberLine = numberLine,
                    danger = isDangerTagged,
                    titleSize = FlowUi.callNameSize,
                    operatorLine = operatorLine,
                    titleModifier = trackName
                )
                Spacer(Modifier.height(Space.m))
                if (redialSession != null) RedialStatus(redialSession) else {
                    CallStatusLine(state, call)
                    redialAttempt?.let { RedialAttemptLine(it) }
                }
                secondary?.let {
                    Spacer(Modifier.height(Space.s))
                    Text(
                        stringResource(
                            R.string.incall_secondary,
                            CallDetailsCompat.contactDisplayName(it.details)
                                ?: PhoneFormatter.format(
                                    CallDetailsCompat.number(it.details).orEmpty()
                                )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                }
                }
                // Набранное показывает сама панель: дублировать
                // не нужно, иначе цифры видны в двух местах
            }
            } // OnPhotoColors

            // --- Низ: управление — в нижней панели, как в журнале (1.3.4) ---
            Surface(
                shape = OgnelissShapeTokens.bottomPanel,
                // Цвет нижней панели — общий с журналом (PanelColors, 1.4.0):
                // в светлой теме серая панель с белыми кнопками, как клавиатура.
                // Над живым фоном — без подложки, волны идут и под кнопками
                // (1.6.0); с 1.6.2 и на ровном фоне — разбор у LocalControlsOnAmbient
                color = if (bare) Color.Transparent else PanelColors.panel(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
            CompositionLocalProvider(LocalControlsOnAmbient provides bare) {
            Column(
                // На всю ширину панели: иначе узкое содержимое ушло бы влево
                Modifier.fillMaxWidth().padding(top = Space.l, start = FlowUi.contentPadding, end = FlowUi.contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                /*
                 * ВСЁ, КРОМЕ ГЛАВНЫХ КНОПОК, — ПРОКРУЧИВАЕТСЯ (1.8.1).
                 *
                 * Клавиши, памятка, второй вызов, конференция, кнопки
                 * разговора, заметка с клавиатурой — вместе они бывают выше
                 * экрана, особенно на телефонах 16 : 9 и с крупным шрифтом.
                 * Раньше тогда обрезалось то, что внизу, — «Завершить»
                 * и «Ответить». Теперь эта часть занимает не больше места,
                 * чем есть, и прокручивается, а главные кнопки всегда на виду.
                 */
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(showDialpad && redialSession == null) {
                        dtmfBlock()
                    }

                    /*
                     * ══════════════════════════════════════════════════════════
                     * ВТОРОЙ ВХОДЯЩИЙ ВО ВРЕМЯ РАЗГОВОРА
                     * ══════════════════════════════════════════════════════════
                     * Раньше его не было на экране вовсе: текущим оставался
                     * идущий разговор, и ответить на второй звонок можно было
                     * только из шторки уведомлений. Теперь — плашка с тремя
                     * решениями: отклонить, ответить с удержанием первого,
                     * ответить и завершить первый.
                     */
                    // Памятка о мошенниках (1.8.0) — над управлением, у глаз
                    AnimatedVisibility(memo.visible) {
                        ScamMemoCard(onDismiss = memo::dismiss, modifier = Modifier.padding(bottom = Space.m))
                    }

                    val waitingCall = remember(revision) { CallManager.waiting }
                    waitingCall?.let { incoming ->
                        WaitingCallBanner(incoming, contactsRepo)
                        Spacer(Modifier.height(Space.m))
                    }

                    /*
                     * УЧАСТНИКИ КОНФЕРЕНЦИИ.
                     *
                     * После объединения на экране была одна «конференция»
                     * без людей: кто в ней, не видно, отключить одного
                     * нельзя. Теперь — список участников, у каждого
                     * «поговорить отдельно» и «отключить», если сеть это
                     * позволяет.
                     */
                    val participants = remember(revision, call) { CallManager.conferenceParticipants }
                    if (participants.isNotEmpty()) {
                        ConferencePanel(participants, contactsRepo)
                        Spacer(Modifier.height(Space.m))
                    }

                    /*
                     * Панель управления появляется раскрытием.
                     *
                     * Момент ответа — единственный, когда на экране меняется
                     * всё сразу: две кнопки становятся одной, и снизу
                     * выезжает панель управления. Мгновенная подмена читалась
                     * как переход на другой экран, и палец, летевший
                     * к «Ответить», оказывался над «Удержать».
                     *
                     * Раскрытие занимает те же доли секунды, но показывает,
                     * что это тот же экран, а не новый.
                     */
                    AnimatedVisibility(
                        // Между попытками автодозвона управлять нечем (1.8.0).
                        // Открыты клавиши или заметка — кнопки разговора убраны
                        // (1.8.1): вместе они не помещались на экран 16 : 9
                        visible = state != Call.STATE_RINGING && redialSession == null &&
                            !showDialpad && !showNote,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        /*
                         * УПРАВЛЕНИЕ — ОДНИМ БЛОКОМ, БЕЗ ПОДЛОЖКИ.
                         *
                         * ── Что было ───────────────────────────────────────────
                         * Кнопки висели прямо на фоне экрана. Вместе с именем,
                         * номером, состоянием и кнопкой завершения получалось
                         * пять уровней, ничем не разделённых: глаз не понимал,
                         * где кончается «кто звонит» и начинается «что я могу
                         * сделать».
                         *
                         * ── Как было в 1.0 — 1.2.0 ─────────────────────────────
                         * Те же кнопки на подложке — по общему правилу
                         * приложения: смысловой блок живёт в карточке.
                         *
                         * ── Как теперь (1.2.1) ─────────────────────────────────
                         * Подложка убрана. Первый же отзыв в Google Play
                         * просил убрать «большую квадратную тень вокруг
                         * кнопок»: большой светло-серый прямоугольник занимал
                         * треть экрана и спорил с портретом за внимание.
                         * Круглые кнопки группируются и сами — одинаковой
                         * формой и ровной сеткой; отдельный фон под ними
                         * не нужен. Сами кружки стали заметнее: теперь они
                         * лежат прямо на светлом фоне экрана, а не на сером.
                         *
                         * Полоски-«ручки» сверху, как в макете, здесь нет:
                         * она обещает, что панель тянется пальцем, а она
                         * не тянется. Обещать жест и не дать его — хуже,
                         * чем не обещать.
                         */
                        controlsBlock()
                        Spacer(Modifier.height(Space.l))
                      }
                    }

                    /*
                     * ЗАМЕТКА ВО ВРЕМЯ РАЗГОВОРА.
                     *
                     * Договорённости забываются за минуты: «привезут
                     * во вторник после четырёх» произносится один раз.
                     * Экран после звонка помогает не всегда — разговор
                     * может длиться двадцать минут, и к концу начало
                     * уже забыто.
                     *
                     * Показывается только на активном вызове: во время
                     * гудков записывать нечего.
                     */
                    if (state == Call.STATE_ACTIVE) {
                        // Подпись — по настройке «Подписи под кнопками»
                        // (1.3.2, отзыв в Google Play); без подписи значок
                        // описан для TalkBack
                        val noteLabel = stringResource(
                            if (showNote) R.string.incall_note_hide
                            else R.string.incall_note_add
                        )
                        if (com.ogneliss.dialer.ui.theme.LocalAppearance.current.buttonLabels) {
                            TextButton(onClick = { showNote = !showNote }) {
                                Icon(OgnelissIcons.EditNote, contentDescription = null)
                                Spacer(Modifier.width(Space.s))
                                Text(noteLabel)
                            }
                        } else {
                            IconButton(onClick = { showNote = !showNote }) {
                                Icon(OgnelissIcons.EditNote, contentDescription = noteLabel)
                            }
                        }
                    }

                    AnimatedVisibility(showNote && redialSession == null) {
                        InCallNoteField(
                            text = noteState.text,
                            onTextChange = { noteState.onTextChange(it) },
                            selectedTags = noteState.tags,
                            onToggleTag = { noteState.toggleTag(it) },
                            /*
                             * Клавиатура открывается сразу.
                             *
                             * Человек нажал «Записать заметку» во время
                             * разговора — значит, хочет писать сейчас,
                             * а не искать, куда ещё нажать. Лишнее касание
                             * здесь стоит дорого: собеседник ждёт.
                             */
                            requestFocus = showNote
                        )
                    }

                    // Отклонить с сообщением. Кнопка появляется только если
                    // оператор и прошивка это поддерживают: объяснять
                    // пользователю особенности его оператора бессмысленно
                    if (state == Call.STATE_RINGING && QuickReply.isAvailable()) {
                        /*
                         * Крупнее обычной текстовой кнопки.
                         *
                         * По ней попадают в тех же условиях, что и по ответу:
                         * телефон звонит, человек торопится. Стандартная
                         * высота текстовой кнопки — 40 dp, и промах мимо неё
                         * попадает в «Отклонить» рядом.
                         */
                        /*
                         * Подпись — по настройке «Подписи под кнопками»
                         * (1.3.2, 01.10.2026, отзыв в Google Play).
                         * Было: текст показывался всегда, даже с выключенными
                         * подписями. Стало: подписи выключены — только значок
                         * сообщения той же высоты 56 dp; описание для TalkBack
                         * остаётся.
                         */
                        val replyLabel = stringResource(R.string.quick_reply_action)
                        if (com.ogneliss.dialer.ui.theme.LocalAppearance.current.buttonLabels) {
                            TextButton(
                                onClick = { showQuickReply = true },
                                modifier = Modifier.heightIn(min = 56.dp)
                            ) {
                                // Стиль темы вместо числа: кегль подписи кнопки
                                // задаётся типографикой, а не на месте
                                Text(replyLabel, style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            FilledTonalIconButton(
                                onClick = { showQuickReply = true },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    OgnelissIcons.Message,
                                    contentDescription = replyLabel
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.m))
                    }
                }

                if (redialSession != null) {
                    // Автодозвон (1.8.0): на месте «Завершить» — его кнопки
                    RedialButtons(redialSession, Modifier.padding(horizontal = Space.s))
                } else if (state == Call.STATE_RINGING && slideAnswer) {
                    // Ответ сдвигом (1.5.8): от случайного касания, когда
                    // телефон достают из кармана. Разбор — в SlideToAnswer
                    SlideToAnswer(
                        onAnswer = { CallManager.answer() },
                        onDecline = { CallManager.reject() },
                        modifier = Modifier.padding(horizontal = Space.s)
                    )
                } else
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.s),
                    // По центру: при одной кнопке («Завершить») ряд не должен
                    // прижимать её к левому краю
                    horizontalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterHorizontally)
                ) {
                    if (state == Call.STATE_RINGING) {
                        BigButton(
                            stringResource(R.string.incall_decline),
                            DeclineRed,
                            OgnelissIcons.CallEnd,
                            modifier = Modifier.weight(1f),
                            shareRow = true
                        ) { CallManager.reject() }

                        /*
                         * ПУЛЬСАЦИЯ КНОПКИ ОТВЕТА.
                         *
                         * ══════════════════════════════════════════════════
                         * ЗАЧЕМ АНИМАЦИЯ ИМЕННО ЗДЕСЬ
                         * ══════════════════════════════════════════════════
                         * Экран входящего вызова человек видит доли секунды
                         * и в неудобных условиях: телефон достают из кармана,
                         * смотрят искоса. Двигающийся объект глаз находит
                         * раньше неподвижного — это единственное место
                         * в приложении, где движение экономит время,
                         * а не украшает.
                         *
                         * ── Насколько заметная ─────────────────────────────
                         * Размах 4%: кнопка «дышит», а не прыгает. Больше —
                         * и она начинает мешать целиться, а промах здесь
                         * означает сброшенный звонок.
                         *
                         * Период 1.1 секунды: примерно вдвое медленнее
                         * пульса, спокойный ритм. Быстрее — тревожно,
                         * медленнее — не замечается.
                         *
                         * ── Только у ответа ────────────────────────────────
                         * У «Отклонить» пульсации нет намеренно: анимация
                         * притягивает палец, и притягивать его к отказу
                         * от звонка не нужно.
                         */
                        val pulse = rememberInfiniteTransition(label = "answer")
                        val scale by pulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.04f,
                            animationSpec = infiniteRepeatable(
                                tween(1100, easing = FastOutSlowInEasing),
                                RepeatMode.Reverse
                            ),
                            label = "answerScale"
                        )

                        Box(Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }) {
                            BigButton(
                                stringResource(R.string.incall_answer),
                                CallGreen,
                                OgnelissIcons.Call,
                                modifier = Modifier.fillMaxWidth(),
                                shareRow = true
                            ) { CallManager.answer() }
                        }
                    } else {
                        BigButton(
                            stringResource(R.string.incall_hangup),
                            DeclineRed,
                            OgnelissIcons.CallEnd
                        ) { CallManager.hangup() }
                    }
                }
                Spacer(Modifier.height(Space.xl))
            }
            } // LocalControlsOnAmbient
            } // панель управления
        }
    }
    } // Box с фото
}

/**
 * СОСТОЯНИЕ ВЫЗОВА И ТАЙМЕР.
 *
 * Таймер тикает раз в секунду, только когда вызов активен.
 *
 * ── Почему плашка, а не просто строка ──────────────────────────────────────
 * Голый текст между именем и кнопками терялся: на пустом экране глазу
 * не за что зацепиться, и «Соединение…» читалось как часть имени.
 * Плашка отделяет состояние от того, кому звонишь.
 *
 * ── Почему у разговора зелёный, а у остального нет ─────────────────────────
 * Зелёный в приложении означает ровно одно — «связь есть, разговор идёт».
 * Гудки, удержание и завершение зелёными быть не должны: человек читает
 * цвет раньше текста, и зелёный на «Соединение…» сообщал бы, что
 * соединились, когда ещё нет.
 */
@Composable
private fun CallStatusLine(state: Int, call: Call?) {
    val active = state == Call.STATE_ACTIVE

    val text = if (active) {
        val connectTime = call?.details?.connectTimeMillis ?: System.currentTimeMillis()
        var elapsed by remember { mutableLongStateOf(0L) }
        LaunchedEffect(connectTime) {
            while (true) {
                elapsed = (System.currentTimeMillis() - connectTime) / 1000
                delay(1000)
            }
        }
        /*
         * Locale указывается явно.
         *
         * Без него формат берёт язык системы, а в арабской и некоторых
         * индийских локалях %02d рисуется местными цифрами: таймер
         * разговора превращался в набор незнакомых знаков.
         * ROOT — потому что это часы и минуты, а не текст.
         */
        String.format(java.util.Locale.ROOT, "%02d:%02d", elapsed / 60, elapsed % 60)
    } else {
        when (state) {
            Call.STATE_RINGING -> stringResource(R.string.incall_incoming)
            Call.STATE_DIALING, Call.STATE_CONNECTING -> stringResource(R.string.incall_connecting)
            Call.STATE_HOLDING -> stringResource(R.string.incall_on_hold)
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED ->
                stringResource(R.string.incall_ended)
            else -> ""
        }
    }

    // Пустое состояние плашки не рисуем: пустая пилюля выглядит
    // как незагрузившийся элемент
    if (text.isBlank()) return

    CallStatusPill(active, text)
}

/**
 * Плашка состояния: «Соединение…», «Входящий вызов», таймер на зелёном.
 * Общая с псевдозвонком (1.5.1): он должен выглядеть как настоящий вызов,
 * а своя строка у него отличалась и видом, и форматом таймера (0:05 вместо 00:05).
 */
@Composable
internal fun CallStatusPill(
    active: Boolean,
    text: String,
    /**
     * Что произнесёт TalkBack (1.8.0): у отсчёта автодозвона текст меняется
     * каждую секунду, а вслух нужен только этап. null — то же, что на экране.
     */
    spoken: String? = null
) {
    /*
     * ЦВЕТ ПЛАШКИ МЕНЯЕТСЯ ПЛАВНО.
     *
     * Переход «Соединение…» → таймер — момент, когда собеседник взял
     * трубку. Мгновенная смена серого на зелёный читалась как подмена
     * элемента: глаз замечал вспышку, но не связывал её с событием.
     *
     * 300 мс: достаточно, чтобы движение заметили, и не настолько
     * долго, чтобы плашка «догоняла» уже идущий разговор.
     */
    val background by animateColorAsState(
        if (active) CallGreen else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(Motion.NORMAL_MS),
        label = "statusBackground"
    )
    val foreground by animateColorAsState(
        if (active) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(Motion.NORMAL_MS),
        label = "statusForeground"
    )

    /*
     * СМЕНА СОСТОЯНИЯ — ВСЛУХ (1.5.1, проход по TalkBack).
     *
     * Человек с экранным диктором не видел, что вызов соединился, встал
     * на удержание или завершился: строка менялась молча. Теперь плашка —
     * «живая область»: TalkBack сам произносит новое состояние.
     *
     * Произносится только состояние («Идёт разговор», «На удержании»),
     * а не таймер: он внутри, отдельным текстом, и меняется каждую
     * секунду — живой областью он бы говорил без остановки. Время
     * разговора слышно, если коснуться плашки.
     */
    val stateLabel = spoken ?: if (active) stringResource(R.string.incall_ongoing) else text
    Surface(
        // Пилюля (1.5.3 — снова, как до 1.5.1)
        shape = CircleShape,
        color = background,
        contentColor = foreground,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = stateLabel
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (active) OgnelissIcons.Call else OgnelissIcons.Schedule,
                contentDescription = null,
                // 20 dp вместо 18: значок рядом с titleMedium,
                // и размер по сетке смотрится ровнее с текстом
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Space.s))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/*
 * @OptIn нужен из-за FlowRow: раскладка с переносом до сих пор помечена
 * экспериментальной, и без согласия компилятор не собирает файл вовсе.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsRow(
    audioState: CallAudioState?,
    /**
     * Уведомление о ручном переключении звука.
     *
     * Без него автоматика продолжала бы командовать динамиком после того,
     * как пользователь переключил его сам: он включает громкую связь,
     * подносит телефон к уху что-то сказать — и связь выключается
     * против его воли. Спорить с пользователем недопустимо.
     */
    onManualAudioChange: () -> Unit,
    onHold: () -> Unit,
    onDialpad: () -> Unit,
    onSwap: () -> Unit,
    onMerge: () -> Unit,
    hasSecondary: Boolean,
    isHolding: Boolean,
    /** Сеть позволяет удержание: разбор в CallManager.canHold. */
    canHold: Boolean = true,
    /** Две линии можно объединить: разбор в CallManager.canMerge. */
    canMerge: Boolean = true,
    /** Можно набрать ещё номер, не прерывая разговор. */
    canAddCall: Boolean = false,
    onAddCall: () -> Unit = {}
) {
    val muted = audioState?.isMuted == true
    val speaker = audioState?.route == CallAudioState.ROUTE_SPEAKER
    val btAvailable = (audioState?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH != 0
    val btActive = audioState?.route == CallAudioState.ROUTE_BLUETOOTH

    /*
     * СЕТКА КНОПОК, А НЕ ДВА РЯДА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЧТО БЫЛО НЕ ТАК
     * ══════════════════════════════════════════════════════════════════════
     * Кнопки стояли двумя жёсткими рядами по три. Но вторая строка
     * заполняется по обстоятельствам: Bluetooth есть не всегда,
     * переключение и объединение — только при втором вызове.
     *
     * В обычном разговоре во второй строке оставалась одна кнопка
     * «Удержать», прижатая к центру, и половина строки пустовала.
     *
     * ── Как теперь ─────────────────────────────────────────────────────────
     * FlowRow раскладывает кнопки сам: три в строке, перенос по мере
     * появления. Пустых мест не остаётся при любом наборе.
     *
     * maxItemsInEachRow = 3 — чтобы на широком экране они не вытянулись
     * в одну строку и не стали мелкими мишенями.
     *
     * ── Ряды поровну (1.5.7) ───────────────────────────────────────────────
     * По три в строке обычный разговор давал 3 + 1: «Удержать» одна во втором
     * ряду (снимок автора, 22.10). Раскладку теперь выбирает controlsPerRow:
     * четыре кнопки — одной строкой, семь-восемь — по четыре, пять и шесть —
     * по три (3 + 2, 3 + 3).
     */
    val buttonCount = 3 +
        (if (canHold) 1 else 0) +
        (if (canAddCall) 1 else 0) +
        (if (btAvailable) 1 else 0) +
        (if (hasSecondary) 1 + (if (canMerge) 1 else 0) else 0)
    val perRow = controlsPerRow(buttonCount)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(controlsGap(perRow), Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Space.l),
        maxItemsInEachRow = perRow,
        modifier = Modifier.fillMaxWidth()
    ) {
            ToggleTile(muted, OgnelissIcons.MicOff, stringResource(R.string.incall_mute)) {
                CallManager.setMuted(!muted)
            }
            ToggleTile(speaker, OgnelissIcons.VolumeUp, stringResource(R.string.incall_speaker)) {
                onManualAudioChange()
                CallManager.setAudioRoute(
                    if (speaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
                )
            }
            ToggleTile(false, OgnelissIcons.Dialpad, stringResource(R.string.incall_keypad), onDialpad)

            // Только если сеть умеет: иначе кнопка молча не работала бы
            if (canHold) {
                ToggleTile(isHolding, OgnelissIcons.Pause, stringResource(R.string.incall_hold), onHold)
            }

            /*
             * «Добавить вызов» — набрать ещё номер, не кладя трубку.
             *
             * Раньше вторая линия появлялась только одним путём: кто-то
             * звонил сам. Позвонить третьему можно было, только выйдя
             * из экрана вызова в журнал, — и догадаться об этом нельзя.
             */
            if (canAddCall) {
                ToggleTile(false, OgnelissIcons.AddIcCall, stringResource(R.string.incall_add_call), onAddCall)
            }
            if (btAvailable) {
                ToggleTile(btActive, OgnelissIcons.Bluetooth, stringResource(R.string.incall_bluetooth)) {
                    onManualAudioChange()
                    CallManager.setAudioRoute(
                        if (btActive) CallAudioState.ROUTE_EARPIECE
                        else CallAudioState.ROUTE_BLUETOOTH
                    )
                }
            }
            if (hasSecondary) {
                ToggleTile(false, OgnelissIcons.SwapCalls, stringResource(R.string.incall_swap), onSwap)
                if (canMerge) {
                    ToggleTile(false, OgnelissIcons.MergeType, stringResource(R.string.incall_merge), onMerge)
                }
        }
    }
}

/**
 * Сколько кнопок разговора ставить в строку (1.5.7).
 *
 * Четыре — одной строкой, а не 3 + 1 с одинокой кнопкой во втором ряду;
 * семь-восемь — по четыре; остальное — по три, как раньше.
 */
internal fun controlsPerRow(count: Int): Int = if (count == 4 || count >= 7) 4 else 3

/**
 * Зазор между кнопками в строке.
 *
 * Плитка — 60 dp (FlowUi.quickActionSize + 16 dp). Четыре плитки с зазором
 * 16 dp — 288 dp, а на телефоне шириной 360 dp под кнопки остаётся 312 dp:
 * ряд помещается даже с подписью шире плитки («Lautsprecher», «Haut-parleur»).
 * С зазором 20 dp, как у трёх, немецкий и французский ряды на таком телефоне
 * уже не влезали. Не поместится и так (крупный шрифт) — FlowRow перенесёт
 * кнопку сам, как раньше: хуже не станет.
 */
internal fun controlsGap(perRow: Int): Dp = if (perRow == 4) Space.l else 20.dp

@Composable
internal fun ToggleTile(
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    /*
     * КНОПКИ УПРАВЛЕНИЯ КРУПНЕЕ.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Стандартный размер FilledIconToggleButton — 40 dp, а подпись
     * была 11 sp. Во время разговора телефон часто держат у уха
     * и нажимают вслепую, глядя одним глазом: промах означает
     * включённый динамик посреди личного разговора.
     *
     * 64 dp и подпись 13 sp дают запас, который заметно снижает число
     * промахов. Места хватает: экран вызова почти пуст.
     */
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconToggleButton(
            checked = active,
            onCheckedChange = { onClick() },
            /*
             * Цвета кнопки (1.4.0). По умолчанию Material красит значок
             * выключенной кнопки синим — на панели было шесть синих значков,
             * и включённый динамик не выделялся среди выключенных. Теперь
             * выключенная — клавиша панели с нейтральным значком, включённая —
             * залита синим: цвет означает «включено», а не «кнопка».
             */
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = PanelColors.key(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary
            ),
            // Размер из правил: тот же, что у быстрых действий
            // в карточке абонента — они и по смыслу одно и то же.
            // Над живым фоном — тень и ободок (1.6.0): белая кнопка
            // на светлом фоне иначе не видна
            modifier = Modifier
                .ambientEdge(LocalControlsOnAmbient.current, CircleShape, MaterialTheme.colorScheme.outlineVariant)
                .size(FlowUi.quickActionSize + 16.dp)
        ) {
            Icon(icon, label, Modifier.size(28.dp))
        }
        // Подпись — если не выключена в настройках оформления.
        // Описание для TalkBack у значка есть и без неё
        if (com.ogneliss.dialer.ui.theme.LocalAppearance.current.buttonLabels) {
            Spacer(Modifier.height(Space.s))
            // labelMedium: подпись под значком — тот же стиль,
            // что у подписей в нижней панели журнала
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * ВЫБОР ШАБЛОНА БЫСТРОГО ОТВЕТА.
 *
 * ── Почему шторка, а не диалог ─────────────────────────────────────────────
 * Телефон звонит, и до кнопок по центру экрана не дотянуться одной рукой.
 * Шторка выезжает снизу — прямо под большой палец.
 *
 * ── Про отправку ───────────────────────────────────────────────────────────
 * Сообщение отправляет система через SMS-приложение по умолчанию.
 * Разрешение SEND_SMS приложению не требуется, и запрашивать его
 * в диалере значило бы вызвать те же подозрения, что и микрофон.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplySheet(
    templates: List<String>,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.ogneliss.dialer.ui.components.sheetContainerColor()) {
        Column(Modifier.padding(bottom = Space.xxl)) {
            Text(
                stringResource(R.string.quick_reply_title),
                Modifier.padding(horizontal = Space.xl, vertical = Space.s),
                style = MaterialTheme.typography.titleLarge
            )
            // Ответы — в скруглённой панели, как строки остальных шторок (1.2.2)
            com.ogneliss.dialer.ui.components.SheetGroup {
                templates.forEachIndexed { index, template ->
                    com.ogneliss.dialer.ui.components.SheetRow(
                        OgnelissIcons.Message,
                        template,
                        onClick = { onSend(template) }
                    )
                    if (index < templates.lastIndex) com.ogneliss.dialer.ui.components.SheetDivider()
                }
            }
        }
    }
}

/**
 * ПОЛЕ ЗАМЕТКИ НА ЭКРАНЕ ВЫЗОВА.
 *
 * ── Почему без кнопки «Сохранить» ──────────────────────────────────────────
 * Во время разговора внимание занято, и требовать подтверждения —
 * верный способ потерять текст: человек допишет мысль, положит трубку,
 * и заметка исчезнет вместе с экраном.
 *
 * Текст сохраняется сам после паузы в наборе и ещё раз при завершении
 * вызова. Подпись под полем сообщает об этом прямо, чтобы не возникало
 * тревоги «а сохранилось ли».
 *
 * ── Про размер поля ────────────────────────────────────────────────────────
 * Три строки: достаточно для «привезут во вторник после четырёх, звонить
 * Марине», но не настолько много, чтобы поле вытеснило кнопку завершения
 * вызова за пределы экрана.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InCallNoteField(
    text: String,
    onTextChange: (String) -> Unit,
    /** Уже выбранные метки: они могли быть поставлены раньше. */
    selectedTags: Set<String> = emptySet(),
    onToggleTag: (String) -> Unit = {},
    requestFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            // Небольшая пауза: панель ещё выезжает, и запрос фокуса
            // до конца анимации система игнорирует
            kotlinx.coroutines.delay(150)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.s)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.incall_note_hint)) },
            /*
             * Поле выше: четыре строки вместо двух.
             *
             * Заметку во время разговора пишут не глядя — собеседник
             * говорит, а взгляд на экране лишь мельком. В двух строках
             * текст начинал прокручиваться внутри поля уже на первой
             * фразе, и написанное уходило из виду.
             *
             * Места на экране вызова достаточно: там кроме портрета
             * и кнопок ничего нет.
             */
            minLines = 4,
            maxLines = 6,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        /*
         * МЕТКИ ДОСТУПНЫ И ВО ВРЕМЯ РАЗГОВОРА.
         *
         * ── Зачем ──────────────────────────────────────────────────────────
         * Раньше их можно было поставить только после звонка или
         * из карточки абонента. А самый частый случай — как раз во время:
         * поднял трубку, услышал предложение кредита, отметил «спам»
         * и положил.
         *
         * Одно нажатие вместо «дождаться конца, найти запись, открыть
         * карточку, поставить метку».
         *
         * ── Почему с переносом по строкам ──────────────────────────────────
         * Ряд с прокруткой прятал бы часть меток за краем, а искать
         * их пальцем во время разговора неудобно вдвойне.
         */
        Spacer(Modifier.height(Space.s))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xxs)
        ) {
            TagCatalog.suggested.forEach { tag ->
                FilterChip(
                    selected = tag.key in selectedTags,
                    onClick = { onToggleTag(tag.key) },
                    // С заглавной — как в карточке, журнале и после звонка
                    label = { Text(stringResource(tag.labelRes).replaceFirstChar { it.titlecase() }) }
                )
            }
        }

        Text(
            stringResource(R.string.incall_note_autosave),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = Space.s, start = Space.s)
        )
    }
}

/** Высота клавиш тонового набора по месту (1.8.1): разбор в CallScreenRules. */
internal fun fittedDtmfKeyHeight(windowHeightDp: Int, reservedDp: Int): Dp =
    CallScreenRules.dtmfKeyHeightDp(windowHeightDp, reservedDp).dp

@Composable
internal fun DtmfPanel(
    entered: String,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    /** Высота клавиш по месту на экране (1.8.1); null — обычная. */
    keyHeight: Dp? = null
) {
    Surface(
        // Клавиши лежат на той же панели, что и кнопки разговора (1.4.0):
        // своя подложка с тоном поверх панели давала третий оттенок серого
        color = PanelColors.key(),
        shape = MaterialTheme.shapes.extraLarge,
        // Над живым фоном — тень и ободок (1.6.0)
        modifier = Modifier
            .fillMaxWidth()
            .ambientEdge(
                LocalControlsOnAmbient.current,
                MaterialTheme.shapes.extraLarge,
                MaterialTheme.colorScheme.outlineVariant
            )
    ) {
        Column(Modifier.padding(vertical = Space.m)) {

            // Поле ввода отделено от клавиш чертой: без разделения
            // непонятно, где заканчивается результат и начинается набор
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entered.ifEmpty { stringResource(R.string.incall_dtmf_hint) },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (entered.isEmpty()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                if (entered.isNotEmpty()) {
                    IconButton(onClick = onBackspace) {
                        Icon(
                            OgnelissIcons.Backspace,
                            contentDescription = stringResource(R.string.a11y_backspace)
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(
                        OgnelissIcons.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.dialpad_hide)
                    )
                }
            }

            // Тот же разделитель, что везде: своя толщина здесь была
            // заметнее соседних линий на других экранах
            OgnelissDivider()
            Spacer(Modifier.height(Space.s))

            // Та же клавиатура, что на главном экране: одинаковые размеры
            // и одинаковый отклик — переучиваться не приходится
            Dialpad(onDigit = onDigit, onLongDigit = {}, keyHeight = keyHeight)
        }
    }
}


/**
 * КТО ЗВОНИТ: ИМЯ ИЛИ ЗАМЕТКА, НОМЕР, ПОМЕТКА.
 *
 * ── Три случая ─────────────────────────────────────────────────────────────
 * • Номер в контактах — имя крупно, заметка курсивом под ним,
 *   номер третьей строкой.
 * • Номера нет, но есть заметка — она ВМЕСТО имени: единственное,
 *   что человек знает об этом номере.
 * • Нет ни того, ни другого — только номер, и он же заголовок.
 *
 * Правила общие с журналом вызовов и карточкой абонента: их задаёт
 * NoteDisplay. Своя реализация здесь означала бы, что экраны разойдутся
 * при следующей правке.
 *
 * ── Почему номер показывается даже при известном имени ─────────────────────
 * По нему решают, брать ли трубку: один человек звонит с рабочего
 * и личного, и это меняет ответ. Исключение — когда номер уже стоит
 * заголовком, дублировать его незачем.
 *
 * ── Почему заметка курсивом ────────────────────────────────────────────────
 * Курсив отличает пометку человека от данных телефонии. «Мошенник» —
 * это то, что записал он сам, а не то, что сообщила сеть, и путать
 * одно с другим нельзя.
 */
@Composable
private fun CallerIdentity(
    display: NoteDisplay.Result,
    number: String,
    /** «Билайн · Москва и Московская область» или null. */
    carrierLine: String?,
    titleSize: TextUnit,
    /** Вместо номера под именем — например, тип номера. null — номер. */
    numberLine: String? = null,
    /**
     * У номера метка «спам» или «мошенники».
     *
     * Заголовок тогда красный: цвет доходит до человека раньше текста,
     * а на экране вызова счёт идёт на секунды.
     */
    danger: Boolean = false,
    /** «по данным оператора» (1.8.0): чьё имя в заголовке. null — не нужно. */
    operatorLine: String? = null,
    /** Для заголовка: от него идут волны, когда фото нет (1.8.1). */
    titleModifier: Modifier = Modifier
) {
    Text(
        display.title,
        modifier = titleModifier,
        fontSize = titleSize,
        color = if (danger) BlockRed else androidx.compose.ui.graphics.Color.Unspecified,
        /*
         * МЕЖСТРОЧНЫЙ ИНТЕРВАЛ ЗАДАЁТСЯ ВМЕСТЕ С РАЗМЕРОМ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО БЫЛО НЕ ТАК
         * ══════════════════════════════════════════════════════════════════
         * Указан был только fontSize. Интервал при этом остаётся
         * от стиля по умолчанию — он рассчитан на обычный текст,
         * а не на 36 sp.
         *
         * Пока имя помещалось в строку, разницы не было. Длинное —
         * «Дмитрий Плиточник Краснодар» — переносилось на две,
         * и строки наезжали друг на друга.
         *
         * ── Почему 1.15 ────────────────────────────────────────────────────
         * Обычный для заголовков множитель — 1.2–1.3, но здесь текст
         * крупный и в одну-две строки: широкий интервал разрывал бы
         * имя на два несвязанных куска. 1.15 достаточно, чтобы буквы
         * не задевали друг друга, и достаточно тесно, чтобы имя
         * читалось целиком.
         */
        lineHeight = titleSize * 1.15f,
        textAlign = TextAlign.Center,
        /*
         * Три строки — предел.
         *
         * Совсем длинное имя иначе вытеснит номер и кнопки за край
         * экрана. Обрыв многоточием честнее: видно, что имя длиннее,
         * и остальное на месте.
         */
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )

    // Источник имени — сразу под ним, мелко и прямым начертанием:
    // это сведения сети, а не пометка человека (та — курсивом ниже)
    operatorLine?.let { line ->
        Spacer(Modifier.height(Space.xxs))
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }

    display.subtitle?.let { note ->
        Spacer(Modifier.height(Space.xs))
        Text(
            note,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    // Номер не дублируется, если он уже стоит заголовком
    if (display.title != PhoneFormatter.format(number)) {
        Spacer(Modifier.height(Space.xs))
        Text(
            numberLine ?: PhoneFormatter.format(number),
            fontSize = FlowUi.callNumberSize,
            color = MaterialTheme.colorScheme.outline
        )
    }

    /*
     * Оператор и регион — мельче номера.
     *
     * Порядок сверху вниз: кто звонит, с какого номера, откуда номер.
     * Последнее — уточнение, и набирать его тем же кеглем значит
     * спорить с именем за внимание в те три секунды, что есть
     * на решение.
     */
    carrierLine?.let {
        Spacer(Modifier.height(Space.xs))
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * ПОРТРЕТ ЗВОНЯЩЕГО.
 *
 * ── Почему отдельным компонентом ───────────────────────────────────────────
 * Рисуется в двух ориентациях с разным размером. Держать две копии
 * означало бы, что при следующей правке одну забудут — и при повороте
 * телефона портрет вёл бы себя иначе.
 *
 * ── Без фотографии портрета нет вовсе ──────────────────────────────────────
 * Раньше при отсутствии снимка рисовался серый круг с силуэтом. На экране
 * вызова он занимал верхнюю треть и не сообщал ничего: силуэт одинаков
 * у всех, кто не завёл фотографию, а таких большинство. Имя при этом
 * уезжало вниз, и у длинного имени в три строки — «Екатерина
 * Ставропольская Квартиросъемщик» — на него оставалась узкая полоса.
 *
 * Теперь: есть снимок — есть портрет, нет снимка — экран начинается
 * с имени.
 *
 * ⚠️ Компонент возвращает пустоту, поэтому отступ ПОСЛЕ него ставится
 * только вместе с портретом: иначе на экране без фотографии остался бы
 * висеть зазор от несуществующего элемента.
 */
@Composable
internal fun CallerAvatar(
    photoUri: String?,
    size: Dp,
    /** Скруглённый квадрат вместо круга (настройка «Фото на экране вызова», 1.5.5). */
    square: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Пустой путь и путь на удалённый файл — одно и то же: показывать
    // нечего. Проверка на пустую строку нужна, потому что часть
    // прошивок отдаёт "" вместо null
    val uri = photoUri?.takeIf { it.isNotBlank() } ?: return

    /*
     * КРУГ, А НЕ СКРУГЛЁННЫЙ КВАДРАТ.
     *
     * ── Почему здесь исключение из общего правила ──────────────────────────
     * По правилам приложения аватары — shapes.extraLarge. На экране
     * вызова портрет остаётся единственным крупным пятном, и квадрат
     * с закруглениями спорил с круглыми кнопками управления
     * и с кнопкой завершения: три разные формы на пустом экране.
     *
     * 1.5.5: круг — по умолчанию; квадрат — по выбору пользователя
     * (отзыв в магазине), со скруглением шторок и диалогов: в мягком стиле
     * острый угол выбивался бы.
     *
     * ── Мягкий край вместо тени (1.5.7) ────────────────────────────────────
     * Было: обрезка по форме и тень 4 dp — чёткий тёмный край посреди
     * светлых волн («слишком резкие границы», автор, 22.10). Стало: край
     * фото растворяется на 4 % поперечника, под ним — светлый ореол цвета
     * страницы; тени нет. Разбор — SoftPortrait.kt.
     */
    val shape = if (square) MaterialTheme.shapes.extraLarge else CircleShape
    Box(
        modifier
            .size(size)
            .softHalo(
                shape,
                MaterialTheme.colorScheme.surfaceBright.copy(alpha = PORTRAIT_HALO_ALPHA),
                PORTRAIT_HALO
            )
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Во весь портрет: размер может задать и раскладка (1.5.6)
            modifier = Modifier
                .fillMaxSize()
                .softPortrait(
                    shape,
                    PORTRAIT_FEATHER,
                    placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
                )
        )
    }
}

/** Растворение края портрета — доля поперечника (набросок F, 1.5.7). */
private const val PORTRAIT_FEATHER = 0.04f
/** Ореол вокруг портрета: ширина — доля поперечника, плотность у края. */
private const val PORTRAIT_HALO = 0.06f
private const val PORTRAIT_HALO_ALPHA = 0.85f

/**
 * КНОПКА ОТВЕТА И ОТКЛОНЕНИЯ.
 *
 * ── Почему такая крупная ───────────────────────────────────────────────────
 * По ней попадают в худших условиях, какие бывают: телефон звонит,
 * человек достаёт его из кармана на ходу, часто одной рукой и не глядя
 * толком. Промах здесь означает либо сброшенный нужный звонок, либо
 * принятый нежелательный — и то и другое исправить нельзя.
 *
 * 72 dp против прежних 56 и ширина от 150 dp: это заметно больше
 * рекомендованного минимума в 48 dp, и здесь запас оправдан. Места
 * на экране вызова достаточно — там кроме портрета и двух кнопок
 * ничего нет.
 *
 * Текст крупнее по той же причине: подпись читают боковым зрением,
 * не вглядываясь.
 */
@Composable
internal fun BigButton(
    text: String,
    color: Color,
    /**
     * Значок рядом с подписью.
     *
     * ── Зачем он ───────────────────────────────────────────────────────────
     * Подпись читают, значок узнают. На звонящем телефоне вторая
     * операция быстрее, и трубка вниз/трубка вверх различаются
     * боковым зрением, когда слово ещё не прочитано.
     *
     * ── Почему подпись всё-таки осталась ───────────────────────────────────
     * В макете у кнопки только значок. Но значок трубки означает
     * «звонить» столько же, сколько «завершить», — эти две трубки
     * различает наклон, а он мелкий. Ошибка стоит сорванного
     * разговора, поэтому слово оставлено.
     */
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    /**
     * Кнопка делит ряд поровну с соседкой — «Отклонить» и «Ответить».
     *
     * ══════════════════════════════════════════════════════════════════
     * ЗАЧЕМ
     * ══════════════════════════════════════════════════════════════════
     * Отзыв в Google Play (Samsung Galaxy A04s, английский): «поправьте
     * немного подписи на кнопках ответа». У каждой кнопки была
     * наименьшая ширина 152 dp и зазор 40 dp: вместе 344 dp — а экран
     * A04s шириной 360 dp, из которых ещё уходят поля. Кнопки не
     * помещались, вторая сжималась, и подпись ломалась посреди слова
     * или обрезалась.
     *
     * Теперь в ряду из двух кнопки делят ширину поровну, поля внутри
     * кнопки меньше, а подпись при нехватке места переносится на вторую
     * строку целым словом, а не обрезается.
     */
    shareRow: Boolean = false,
    /** Цвет подписи и значка (1.8.0): белый на цветной кнопке, тёмный — на серой «Закрыть». */
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Button(
        shape = OgnelissShapeTokens.callButton,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(horizontal = Space.l),
        /*
         * Высота из правил FlowUI.
         *
         * Кнопка ответа — то же по смыслу, что «Позвонить» в карточке:
         * главное действие экрана. Разные числа здесь означали бы,
         * что одно и то же действие выглядит по-разному в зависимости
         * от того, откуда пришёл вызов.
         *
         * Плюс запас: по кнопке ответа попадают в худших условиях —
         * телефон достают из кармана на ходу.
         */
        modifier = modifier
            .height(FlowUi.primaryActionHeight + 16.dp)
            // Одна кнопка («Завершить») — не уже 152 dp, чтобы в неё легко
            // попасть; две в ряду — сколько даст ряд
            .then(if (shareRow) Modifier else Modifier.widthIn(min = FlowUi.callActionMinWidth))
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Spacer(Modifier.width(Space.s))
        /*
         * FitText (1.8.0): длинное слово уменьшается, а не рвётся посреди.
         * У автодозвона подписи длиннее прежних — «Дозваниваться» на узком
         * телефоне в половину ряда не помещалось и ломалось на «Дозванивать /
         * ся». Короткие подписи («Ответить», «Завершить») не меняются:
         * пока слово влезает, размер прежний.
         */
        FitText(
            text,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = FlowUi.callNumberSize,
                fontWeight = FontWeight.Medium
            ),
            color = contentColor,
            // Две строки и по центру: при крупном шрифте системы подпись
            // переносится целым словом, а не обрезается
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}


/**
 * Открыть набор номера для второго вызова, не завершая текущий.
 *
 * Приложение запускается с флагом «открыть клавиатуру»: человек пришёл
 * набирать номер, а не смотреть журнал. Дальше всё обычным путём —
 * набранный номер уходит в систему, и Telecom сам ставит текущий вызов
 * на удержание (мы проверили заранее, что сеть это умеет, — иначе
 * плитки не было бы).
 *
 * Экран вызова при этом не закрывается: он вернётся, как только
 * новый вызов начнётся.
 */
private fun openDialerForSecondCall(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(context, com.ogneliss.dialer.MainActivity::class.java)
                .putExtra(com.ogneliss.dialer.MainActivity.EXTRA_OPEN_DIALPAD, true)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { Logger.w("InCall", "Не удалось открыть набор для второго вызова", it) }
}

/** Имя по номеру из книги, иначе номер — для плашек второго вызова и участников. */
@Composable
private fun rememberCallerLabel(
    call: Call,
    contactsRepo: com.ogneliss.dialer.data.ContactsRepository
): String {
    val raw = call.details?.handle?.schemeSpecificPart.orEmpty()
    val fallback = if (raw.isBlank()) stringResource(R.string.incall_hidden_number)
    else PhoneFormatter.format(raw)

    val name by produceState<String?>(null, raw) {
        value = if (raw.isBlank()) null
        else suspendRunCatching { contactsRepo.lookupByNumber(raw) }.getOrNull()
    }
    return name?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * ПЛАШКА ВТОРОГО ВХОДЯЩЕГО.
 *
 * Три кнопки, а не две: при втором звонке выбор не «ответить или нет»,
 * а «что станет с тем, с кем говорю сейчас». Удержание — первым,
 * как в системном приложении: это самый частый и самый безопасный
 * выбор — никого не теряешь.
 */
@Composable
private fun WaitingCallBanner(
    call: Call,
    contactsRepo: com.ogneliss.dialer.data.ContactsRepository
) {
    val label = rememberCallerLabel(call, contactsRepo)

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        // В нижней панели — цвет клавиши (1.4.0): secondaryContainer
        // в светлой теме совпадал с панелью по яркости. «Второй вызов» —
        // синим заголовком: событие видно и без цветной заливки
        color = PanelColors.key(),
        // Над живым фоном — тень и ободок (1.6.0)
        modifier = Modifier
            .fillMaxWidth()
            .ambientEdge(
                LocalControlsOnAmbient.current,
                MaterialTheme.shapes.extraLarge,
                MaterialTheme.colorScheme.outlineVariant
            )
    ) {
        Column(Modifier.padding(Space.l)) {
            Text(
                stringResource(R.string.incall_waiting_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(Space.m))

            Button(
                onClick = { CallManager.answerWaitingHoldCurrent() },
                colors = ButtonDefaults.buttonColors(containerColor = CallGreen),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.incall_waiting_hold_answer)) }

            Spacer(Modifier.height(Space.s))

            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedButton(
                    onClick = { CallManager.answerWaitingEndCurrent() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.incall_waiting_end_answer),
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
                OutlinedButton(
                    onClick = { CallManager.rejectWaiting() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DeclineRed),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.incall_decline)) }
            }
        }
    }
}

/**
 * УЧАСТНИКИ КОНФЕРЕНЦИИ.
 *
 * Кнопки у участника — только те, что сеть разрешила: «отключить»
 * без разрешения на части сетей кладёт трубку всей конференции,
 * а «поговорить отдельно» без разрешения просто ничего не делает.
 */
@Composable
private fun ConferencePanel(
    participants: List<Call>,
    contactsRepo: com.ogneliss.dialer.data.ContactsRepository
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        // Лежит в нижней панели: цвет клавиши, а не панели — иначе сливался бы
        color = PanelColors.key(),
        // Над живым фоном — тень и ободок (1.6.0)
        modifier = Modifier
            .fillMaxWidth()
            .ambientEdge(
                LocalControlsOnAmbient.current,
                MaterialTheme.shapes.extraLarge,
                MaterialTheme.colorScheme.outlineVariant
            )
    ) {
        Column(Modifier.padding(vertical = Space.s)) {
            Text(
                stringResource(R.string.incall_conference_title, participants.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s)
            )

            participants.forEach { child ->
                val label = rememberCallerLabel(child, contactsRepo)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = Space.l, end = Space.xs)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (CallManager.canSplitParticipant(child)) {
                        IconButton(onClick = { CallManager.splitParticipant(child) }) {
                            Icon(
                                OgnelissIcons.CallSplit,
                                contentDescription = stringResource(R.string.incall_conference_split)
                            )
                        }
                    }

                    if (CallManager.canDropParticipant(child)) {
                        IconButton(onClick = { CallManager.dropParticipant(child) }) {
                            Icon(
                                OgnelissIcons.CallEnd,
                                contentDescription = stringResource(R.string.incall_conference_drop),
                                tint = DeclineRed
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Квадратный портрет: волна рождается у скруглённых углов, а не у середины
 * стороны — радиус до угла с учётом скругления 24 dp у портрета 132 dp
 * (≈ 1,25 половины стороны).
 */
private const val SQUARE_FOCUS = 1.25f

/** Портрет лёжа — не крупнее 140 dp: по высоте места вдвое меньше (1.5.6). */
private val LANDSCAPE_PORTRAIT_MAX = 140.dp
private val LANDSCAPE_PORTRAIT_MAX_LARGE = 170.dp
