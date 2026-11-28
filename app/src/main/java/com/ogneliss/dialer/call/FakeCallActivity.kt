package com.ogneliss.dialer.call

import com.ogneliss.dialer.settings.SwitchSetting
import com.ogneliss.dialer.settings.ContactNameFormat
import com.ogneliss.dialer.data.ContactNames
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import androidx.compose.ui.graphics.Color
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ogneliss.dialer.concurrency.suspendRunCatching

import com.ogneliss.dialer.ui.adaptive.ProvideWindowLayout
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.ContactsRepository
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.DeclineRed
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.settings.CallPhotoSize
import com.ogneliss.dialer.settings.AnswerMode
import com.ogneliss.dialer.settings.CallPhotoStyle
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * ЭКРАН ПСЕВДОЗВОНКА.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ОТДЕЛЬНЫЙ ЭКРАН, А НЕ РЕЖИМ InCallActivity
 * ══════════════════════════════════════════════════════════════════════════
 * Настоящий экран вызова насквозь завязан на CallManager: состояние,
 * счётчик событий, ответ, удержание, аудиомаршруты. Добавить туда ветку
 * «а сейчас звонка на самом деле нет» значит поставить условие в каждое
 * из этих мест — и однажды ошибиться в том, которое обрабатывает
 * настоящий вызов.
 *
 * Ошибка на экране псевдозвонка — неловкость. Ошибка на экране
 * настоящего вызова — пропущенный звонок. Поэтому они разделены,
 * и повторение части разметки здесь оправдано.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО ДЕЛАЕТ ЭКРАН
 * ══════════════════════════════════════════════════════════════════════════
 * Звонит и вибрирует, показывает имя и номер, даёт ответить или
 * отклонить. После ответа идёт счётчик времени, как в настоящем
 * разговоре. Разговаривать не с кем: микрофон не включается, звук
 * не передаётся.
 */
@AndroidEntryPoint
class FakeCallActivity : ComponentActivity() {

    @Inject lateinit var prefs: Prefs
    @Inject lateinit var contactsRepo: ContactsRepository

    private var ringtone: Ringtone? = null

    /**
     * Датчик приближения (2.4.4, проверка датчика): «ответили» и поднесли
     * к уху — экран гаснет, как у настоящего вызова. Было: не гас, и щека
     * могла нажать «Завершить» посреди «разговора» — розыгрыш раскрывался.
     */
    private var proximity: ProximityLock? = null
    private var vibrator: Vibrator? = null

    /** Предпросмотр экрана вызова из настроек (2.9.12, FakeCall.preview): тихо, без вызова в системе. */
    private val preview: Boolean by lazy { intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true }

    companion object {
        /** Признак предпросмотра в намерении (FakeCall.preview). */
        const val EXTRA_PREVIEW = "com.ogneliss.dialer.extra.CALL_PREVIEW"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 2.7.7: открыт из уведомления по времени — его мелодию сменяет своя (FakeCall)
        FakeCall.clearNotification(this)
        /*
         * 2.7.8: псевдозвонок — «своим вызовом» в системе (FakeTelecomCall): так
         * он звонит и в Bluetooth-гарнитуре. Открыли кнопкой, плиткой или
         * ярлыком — вызова ещё нет, создаём; открыла система (по времени) —
         * он уже есть, start ничего не делает. При повороте экрана — не трогаем.
         */
        // Предпросмотр (2.9.12) — не вызов: в системе его нет, гарнитура о нём не узнает
        if (!preview) {
            FakeTelecomCall.activityVisible = true
            if (savedInstanceState == null) FakeTelecomCall.start(this)
        }

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

        // Поверх экрана блокировки: телефон обычно лежит заблокированным,
        // а звонок должен быть виден так же, как настоящий
        if (!preview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // Предпросмотр — без мелодии и вибрации: смотрят в настройках, а не ждут звонка
        if (!preview) startRinging()

        proximity = if (preview) null else ProximityLock(this)

        // Вид экрана — до первого кадра, как у настоящего вызова (2.9.7, CallScreenStart);
        // пробный вызов — входящий
        val start = CallScreenStart.read(this, prefs, incoming = true)

        setContent {
            val theme by prefs.theme.collectAsStateWithLifecycle(start.theme)
            // Гасить экран у уха — та же настройка, что у настоящего вызова
            val proximityOn by prefs.flow(SwitchSetting.PROXIMITY_SCREEN_OFF).collectAsStateWithLifecycle(true)
            var atEar by remember { mutableStateOf(false) }
            LaunchedEffect(atEar, proximityOn) { proximity?.hold(proximityOn && atEar) }
            val dynamic by prefs.flow(SwitchSetting.DYNAMIC_COLOR).collectAsStateWithLifecycle(start.dynamicColor)
            val uiScale by prefs.uiScale.collectAsStateWithLifecycle(start.uiScale)
            // Живой фон — как у настоящего вызова (1.5.1)
            val callAnimation by prefs.flow(SwitchSetting.CALL_ANIMATION).collectAsStateWithLifecycle(start.callAnimation)
            // Свой фон вызова (2.7.4): пробный вызов — входящий, берёт снимок входящих
            val callBgIncoming by prefs.callBgIncoming.collectAsStateWithLifecycle(start.callBgIncoming)
            // Фото звонящего — как у настоящего вызова (1.5.5); null — ещё не прочитано
            val callPhotoStyle by prefs.callPhotoStyle.collectAsStateWithLifecycle<CallPhotoStyle?>(start.callPhotoStyle)
            // Облик живого фона — как у настоящего вызова (2.9.5)
            val ambientStyle by prefs.callAmbientStyle.collectAsStateWithLifecycle(start.ambientStyle)
            // Размер портрета — как у настоящего вызова (1.5.7)
            val callPhotoSize by prefs.callPhotoSize.collectAsStateWithLifecycle(start.callPhotoSize)
            // Ответ касанием или сдвигом — как у настоящего вызова (1.5.8)
            val answerMode by prefs.answerMode.collectAsStateWithLifecycle(start.answerMode)
            // «Ответить» слева — как у настоящего вызова (2.1.1)
            val swapAnswer by prefs.flow(SwitchSetting.SWAP_ANSWER).collectAsStateWithLifecycle(start.swapAnswer)
            // Подпись «Входящий вызов» — как у настоящего вызова (2.9.7, CallStatusLabels)
            val statusLabels by prefs.flow(SwitchSetting.CALL_STATUS_LABELS).collectAsStateWithLifecycle(start.statusLabels)

            /*
             * Кто «звонит».
             *
             * Настройка хранит номер. Пусто — берётся случайный контакт:
             * так звонок выглядит правдоподобно у того, кто не настраивал
             * ничего заранее, а именно этот человек и нажмёт кнопку
             * в неудобный момент.
             */
            var caller by remember { mutableStateOf<Pair<String, String>?>(null) }
            // Звонит сохранённый контакт (2.8.0): для картинки вместо фото — буквы или силуэт
            var callerKnown by remember { mutableStateOf(true) }
            val avatarNoPhoto by prefs.avatarNoPhoto.collectAsStateWithLifecycle(start.avatarNoPhoto)
            val avatarUnknown by prefs.avatarUnknown.collectAsStateWithLifecycle(start.avatarUnknown)
            val avatarScope by prefs.avatarScope.collectAsStateWithLifecycle(start.avatarScope)
            // Имя и номер — гарнитуре и машине, как только выбраны
            LaunchedEffect(caller) {
                if (!preview) caller?.let { (n, num) -> FakeTelecomCall.updateCaller(n, num) }
            }
            /*
             * Ответили или сбросили в гарнитуре (2.7.8): ответ — экран переходит
             * в разговор, мелодия смолкает; сброс — экран закрывается. «Был
             * вызов» — чтобы не закрыться до того, как система его создала.
             */
            val telecom by FakeTelecomCall.state.collectAsStateWithLifecycle()
            var hadTelecomCall by remember { mutableStateOf(false) }
            LaunchedEffect(telecom) {
                // Предпросмотр не связан с «вызовом в системе» — чужой псевдозвонок его не закроет
                if (preview) return@LaunchedEffect
                when (telecom) {
                    FakeTelecomCall.State.RINGING -> hadTelecomCall = true
                    FakeTelecomCall.State.ACTIVE -> { hadTelecomCall = true; stopRinging() }
                    FakeTelecomCall.State.IDLE -> if (hadTelecomCall) finish()
                }
            }

            LaunchedEffect(Unit) {
                // Предпросмотр: контакт с фото, если есть, — видно, как ляжет фото звонящего
                val chosen = if (preview) "" else prefs.fakeCallNumber.first()
                // Имя — в той же форме, что у настоящего вызова (1.6.2)
                val nameFormat = prefs.contactNameFormat.first()
                caller = if (chosen.isNotBlank()) {
                    val name = suspendRunCatching { contactsRepo.lookupByNumber(chosen) }.getOrNull()
                        ?.let { found ->
                            if (nameFormat == ContactNameFormat.FAMILY_FIRST) {
                                suspendRunCatching { contactsRepo.familyFirstName(chosen, found) }.getOrNull() ?: found
                            } else {
                                found
                            }
                        }
                    callerKnown = name != null
                    (name ?: PhoneFormatter.format(chosen)) to chosen
                } else {
                    val random = suspendRunCatching {
                        val withNumbers = contactsRepo.loadAll().filter { it.numbers.isNotEmpty() }
                        // Предпросмотр (2.9.12): контакт с фото, если такие есть
                        (if (preview) withNumbers.filter { !it.photoUri.isNullOrBlank() }.randomOrNull() else null)
                            ?: withNumbers.randomOrNull()
                    }.getOrNull()

                    if (random != null) {
                        ContactNames.shown(random, nameFormat) to random.numbers.first().value
                    } else {
                        // Контактов нет вовсе — показываем номер
                        // без имени: это тоже выглядит как обычный звонок
                        getString(R.string.fake_call_default_name) to ""
                    }
                }
            }

            // Тема, акцент, скругление, шрифт (1.8.4–1.9.0)
            val themeLook by prefs.themeLook.collectAsStateWithLifecycle(start.themeLook)
            val fontScale by prefs.fontScale.collectAsStateWithLifecycle(start.fontScale)
            OgnelissDialerTheme(themeMode = theme, dynamicColor = dynamic, uiScale = uiScale, fontScale = fontScale, look = themeLook) {
                // Раскладка окна: телефон, лёжа, раскладушка, планшет.
                // Разбор — в ui/adaptive/WindowLayout.kt
                ProvideWindowLayout(this@FakeCallActivity) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalAmbientStyle provides ambientStyle,
                    LocalCallStatusLabels provides statusLabels,
                    LocalStartBackground provides start.background
                ) {
                // Фото — тем же запросом, что у настоящего вызова (1.3.4)
                val callerNumber = caller?.second.orEmpty()
                val contactPhoto by produceState<String?>(initialValue = null, callerNumber) {
                    value = callerNumber.takeIf { it.isNotBlank() }?.let {
                        suspendRunCatching { contactsRepo.photoUriForNumber(it) }.getOrNull()
                    }
                }
                // Фото нет — своя картинка (2.8.0, AvatarPictures), как у настоящего вызова
                val photo = contactPhoto?.takeIf { it.isNotBlank() } ?: if (caller == null) null else
                    com.ogneliss.dialer.ui.components.AvatarPictures.of(
                        applicationContext, avatarNoPhoto, avatarUnknown, avatarScope
                    ).forCallScreen(known = callerKnown)
                FakeCallScreen(
                    name = caller?.first.orEmpty(),
                    number = callerNumber,
                    photoUri = photo,
                    ambientBackground = callAnimation && theme != ThemeMode.HIGH_CONTRAST,
                    bareControls = theme != ThemeMode.HIGH_CONTRAST,
                    // В высоком контрасте свой снимок не показываем — как и живой фон
                    incomingBgMark = if (theme != ThemeMode.HIGH_CONTRAST) callBgIncoming else 0L,
                    photoStyle = callPhotoStyle,
                    largePortrait = callPhotoSize == CallPhotoSize.LARGE,
                    slideToAnswer = answerMode != AnswerMode.TAP,
                    verticalAnswer = answerMode == AnswerMode.SWIPE_VERTICAL,
                    swapAnswer = swapAnswer,
                    onAnswer = { stopRinging(); if (!preview) FakeTelecomCall.answer() },
                    onHangUp = { if (!preview) FakeTelecomCall.end(); finish() },
                    answeredOutside = !preview && telecom == FakeTelecomCall.State.ACTIVE,
                    onAtEarChanged = { atEar = it }
                )
                }
            }
            }
        }
    }

    /**
     * Звонок и вибрация.
     *
     * Проигрывается системный рингтон, а не свой звук: человек рядом
     * слышит ровно то, что слышал бы при настоящем звонке этого
     * телефона.
     *
     * Тихий режим не обходится: если владелец выключил звук, включать
     * его самим — предавать его же настройку. Вибрация в этом случае
     * остаётся, и её достаточно, чтобы взять телефон в руки.
     */
    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(
                this, RingtoneManager.TYPE_RINGTONE
            ) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }

        runCatching {
            vibrator = getSystemService(Vibrator::class.java)
            val pattern = longArrayOf(0, 800, 900)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
        ringtone = null
    }

    override fun onStart() {
        super.onStart()
        if (!preview) FakeTelecomCall.activityVisible = true
    }

    override fun onResume() {
        super.onResume()
        // Открыли касанием плашки (2.7.8) — её мелодию сменяет своя
        FakeCall.clearNotification(this)
    }

    override fun onStop() {
        if (!preview) FakeTelecomCall.activityVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        // Экран закрыли насовсем — и вызов в системе завершается (гарнитура, машина)
        if (isFinishing && !preview) FakeTelecomCall.end()
        stopRinging()
        // Ещё у уха — экран загорится, когда отнимут (как у настоящего вызова)
        proximity?.release(waitForFar = true)
        super.onDestroy()
    }
}

@Composable
internal fun FakeCallScreen(
    name: String,
    number: String,
    photoUri: String?,
    /** Живой фон (CallAmbientBackground.kt); без фото на весь экран. */
    ambientBackground: Boolean = false,
    /** Кнопки без панели и на ровном фоне (1.6.2), как у настоящего вызова. */
    bareControls: Boolean = false,
    /** Отметка своего фона входящих (2.7.4, CallBackgrounds); 0 — нет. */
    incomingBgMark: Long = 0L,
    /** Фото: круг, квадрат или на весь экран (1.5.5); null — ещё не прочитано. */
    photoStyle: CallPhotoStyle? = CallPhotoStyle.CIRCLE,
    /** Настройка «Размер фото» — крупный (1.5.7). */
    largePortrait: Boolean = false,
    /** Настройка «Ответ на вызов» — сдвигом (1.5.8), как у настоящего. */
    slideToAnswer: Boolean = false,
    /** Ответ свайпом вверх и вниз (2.8.5), как у настоящего вызова. */
    verticalAnswer: Boolean = false,
    /** «Ответить» слева (2.1.1), как у настоящего. */
    swapAnswer: Boolean = false,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    /** «Трубка у уха» (2.4.4): ответили, не на удержании, без громкой связи — датчик гасит экран. */
    onAtEarChanged: (Boolean) -> Unit = {},
    /** Ответили в гарнитуре, машине или на часах (2.7.8, FakeTelecomCall). */
    answeredOutside: Boolean = false
) {
    val slideAnswer = useSlideToAnswer(slideToAnswer)
    var answered by remember { mutableStateOf(false) }
    LaunchedEffect(answeredOutside) { if (answeredOutside) answered = true }
    var seconds by remember { mutableIntStateOf(0) }

    /*
     * КАК НАСТОЯЩИЙ ВЫЗОВ — И ПОСЛЕ ОТВЕТА (1.5.1, автор: «псевдозвонок
     * должен быть похож на обычный вызов»).
     *
     * Было: после «Ответить» — таймер простой строкой («0:05») и одна
     * кнопка «Завершить». Настоящий вызов в это время показывает зелёную
     * плашку «00:05» и панель «Звук · Динамик · Клавиши · Удержать» —
     * кто стоит рядом, видел разницу.
     *
     * Теперь — те же плашка, кнопки и клавиатура, что у настоящего вызова
     * (CallStatusPill, ToggleTile, DtmfPanel, BigButton из InCallActivity).
     * Кнопки переключаются, но ничего не делают: разговора нет. «Удержать»
     * ставит плашку «На удержании», останавливает таймер и делает фон серым —
     * как у настоящего.
     */
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var showDialpad by remember { mutableStateOf(false) }
    var dtmf by remember { mutableStateOf("") }

    // Правило — как у настоящего вызова (CallScreenRules.proximityWanted)
    LaunchedEffect(answered, holding, speaker) {
        @Suppress("DEPRECATION")
        val state = when {
            !answered -> android.telecom.Call.STATE_RINGING
            holding -> android.telecom.Call.STATE_HOLDING
            else -> android.telecom.Call.STATE_ACTIVE
        }
        val route = if (speaker) android.telecom.CallAudioState.ROUTE_SPEAKER else android.telecom.CallAudioState.ROUTE_EARPIECE
        onAtEarChanged(CallScreenRules.proximityWanted(state, route))
    }

    LaunchedEffect(answered, holding) {
        if (!answered || holding) return@LaunchedEffect
        while (true) {
            delay(1000)
            seconds++
        }
    }

    /*
     * Вид — как у настоящего вызова (1.3.4): фото на весь экран в портретной
     * раскладке, светлый текст поверх, кнопки в нижней панели.
     */
    val short = LocalWindowLayout.current.isShort
    val photoBackground = photoStyle == CallPhotoStyle.FULL_SCREEN && !short && !photoUri.isNullOrBlank()
    // Фото по ширине экрана — как у настоящего вызова (2.6.0, CallPhotoTop)
    val photoTop = photoStyle == CallPhotoStyle.WIDTH && !short && !photoUri.isNullOrBlank()
    // Портрет над именем и волны от него — как у настоящего вызова (1.5.5)
    val showPortrait = photoStyle != null && !photoUri.isNullOrBlank() && !photoBackground && !photoTop
    val squarePortrait = photoStyle == CallPhotoStyle.SQUARE || photoStyle == CallPhotoStyle.WIDTH
    var portraitFocus by remember { mutableStateOf<AmbientFocus?>(null) }
    // Нет фото — волны от имени (1.8.1, разбор у AmbientFocus)
    var nameFocus by remember { mutableStateOf<AmbientFocus?>(null) }
    // 2.7.4: свой снимок входящих вместо живого фона — как у настоящего вызова
    val customBg = rememberCustomCallBackground(CallBackgrounds.Kind.INCOMING, incomingBgMark)
    val bgLayer = CallBackgroundLayer.decide(photoBackground, photoTop, customBg != null, ambientBackground)
    val customLayer = bgLayer == CallBackgroundLayer.CUSTOM
    // 2.7.6: цвет текста над снимком и значки строки состояния — как у настоящего
    val customText = if (customLayer) customCallTextColor(customBg) else null
    // 2.8.1: подписи под кнопками разговора — по яркости низа снимка
    val customControls = if (customLayer) customCallControlsColor(customBg) else null
    LightStatusIconsOnPhoto(photoBackground || photoTop || customText == Color.White)
    // Живой фон — по тем же состояниям, что у настоящего вызова
    val ambient = bgLayer == CallBackgroundLayer.AMBIENT
    val bare = ambient || customLayer || (bareControls && !photoBackground)
    // Сторона фото по ширине — как у настоящего вызова (photoTopSide)
    var topAreaPx by remember { mutableIntStateOf(0) }
    var identityPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val callDensity = androidx.compose.ui.platform.LocalDensity.current
    val statusBarTop = with(callDensity) { WindowInsets.safeDrawing.getTop(callDensity).toDp() }
    val photoSide = if (!photoTop) 0.dp else with(callDensity) {
        photoTopSide(
            screenWidth = maxWidth,
            topArea = topAreaPx.toDp(),
            statusBar = statusBarTop,
            identity = identityPx.toDp(),
            gap = Space.m,
            min = PHOTO_TOP_MIN
        )
    }
    if (photoBackground) CallPhotoBackground(photoUri.orEmpty())
    if (ambient) {
        CallAmbientBackground(
            when {
                !answered -> AmbientMood.INCOMING
                holding -> AmbientMood.HOLDING
                else -> AmbientMood.ACTIVE
            },
            photoUri = photoUri.takeIf { showPortrait },
            focus = { if (showPortrait) portraitFocus else nameFocus }
        )
    }
    // Как у настоящего вызова: панели нет над живым и ровным фоном (1.6.2)
    if (customLayer) customBg?.let { CallCustomBackground(it.image) }
    else if ((bare && !ambient) || photoTop) PlainCallBackground()
    if (photoTop) CallPhotoTop(photoUri.orEmpty(), photoSide)
    if (!bare) NavBarUnderPanel()
    Surface(
        Modifier.fillMaxSize().safeDrawingPadding(),
        color = if (photoBackground || ambient || photoTop || customLayer) Color.Transparent else MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            OnPhotoColors(photoBackground || customText != null, customText ?: Color.White) {
            Column(
                Modifier
                    // 1.5.6: всё место над панелью — портрету и подписям,
                    // размер портрета по нему (как у настоящего вызова)
                    .weight(1f)
                    .onSizeChanged { topAreaPx = it.height }
                    .fillMaxWidth()
                    .padding(top = Space.xl, start = FlowUi.contentPadding, end = FlowUi.contentPadding, bottom = Space.m),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val trackPortrait = Modifier.onGloballyPositioned { coords ->
                    val bounds = coords.boundsInRoot()
                    val half = bounds.width / 2f
                    // У квадрата волна — от углов (как SQUARE_FOCUS в InCallActivity)
                    portraitFocus = AmbientFocus(bounds.center, if (squarePortrait) half * 1.25f else half)
                }
                // Место под фото по ширине — как у настоящего вызова (2.6.0)
                if (photoTop && photoSide > 0.dp) {
                    Spacer(Modifier.height((photoSide - statusBarTop - Space.xl - PHOTO_TOP_OVERLAP).coerceAtLeast(0.dp)))
                }
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
                    gap = if (short) Space.m else Space.l,
                    maxSize = FlowUi.callPortraitMaxFor(largePortrait),
                    widthFraction = FlowUi.callPortraitFractionFor(largePortrait),
                    // Как у настоящего вызова — ближе к середине (1.7.0); под фото по ширине — сразу под ним
                    verticalBias = if (photoTop) 0f else CALL_IDENTITY_BIAS,
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.onSizeChanged { identityPx = it.height }
                ) {
                // Как у настоящего вызова (2.6.0): слово не рвётся, имя ужимается
                com.ogneliss.dialer.ui.components.FitText(
                    name,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = FlowUi.callNameSize,
                        fontWeight = FontWeight.Medium,
                        lineHeight = FlowUi.callNameSize * 1.15f,
                        shadow = callTextShadow()
                    ),
                    maxLines = 3,
                    minScale = 0.5f,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val bounds = coords.boundsInRoot()
                        nameFocus = AmbientFocus(bounds.center, bounds.height / 2f, edge = false)
                    }
                )

                if (number.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    CallNumberText(PhoneFormatter.format(number))
                }

                Spacer(Modifier.height(Space.m))
                CallStatusPill(
                    active = answered && !holding,
                    text = when {
                        !answered -> stringResource(R.string.incall_incoming)
                        holding -> stringResource(R.string.incall_on_hold)
                        // Формат — как у настоящего таймера (CallScreenRules.timer): с часа — «1:05:07» (2.4.3)
                        else -> CallScreenRules.timer(seconds.toLong())
                    },
                    // «Входящий вызов» — по настройке, как у настоящего вызова (2.9.7)
                    shown = answered || LocalCallStatusLabels.current
                )
                }
                }
            }
            } // OnPhotoColors

            Surface(
                shape = OgnelissShapeTokens.bottomPanel,
                // Как у настоящего вызова: общий цвет нижней панели (1.4.0),
                // над живым фоном — без подложки (1.6.0, разбор у LocalControlsOnAmbient)
                color = if (bare) Color.Transparent else PanelColors.panel(),
                // Над своим снимком — подписи кнопок по яркости его низа (2.8.1)
                contentColor = customControls ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
            CompositionLocalProvider(LocalControlsOnAmbient provides bare) {
            Column(
                Modifier.fillMaxWidth().padding(top = Space.l, start = FlowUi.contentPadding, end = FlowUi.contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.animation.AnimatedVisibility(answered && showDialpad) {
                    DtmfPanel(
                        entered = dtmf,
                        onDigit = { dtmf += it },
                        onBackspace = { dtmf = dtmf.dropLast(1) },
                        onClose = { showDialpad = false },
                        keyHeight = fittedDtmfKeyHeight(
                            LocalWindowLayout.current.heightDp, CallScreenRules.DTMF_RESERVED_PORTRAIT_DP
                        )
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    // Клавиши открыты — кнопки разговора убраны, как у настоящего
                    // вызова (1.8.1): вместе не помещались на экран 16 : 9
                    visible = answered && !showDialpad,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FakeControlsRow(
                            muted = muted,
                            speaker = speaker,
                            holding = holding,
                            onMute = { muted = !muted },
                            onSpeaker = { speaker = !speaker },
                            onDialpad = { showDialpad = !showDialpad },
                            onHold = { holding = !holding }
                        )
                        Spacer(Modifier.height(Space.l))
                    }
                }

                if (!answered && slideAnswer) {
                    // Сдвиг вместо кнопок — как у настоящего вызова (1.5.8)
                    if (verticalAnswer && !com.ogneliss.dialer.ui.adaptive.LocalWindowLayout.current.isShort) VerticalSwipeToAnswer(
                        onAnswer = { answered = true; onAnswer() },
                        onDecline = onHangUp
                    ) else SlideToAnswer(
                        onAnswer = { answered = true; onAnswer() },
                        onDecline = onHangUp,
                        modifier = Modifier.padding(horizontal = Space.s),
                        swapped = swapAnswer
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.s),
                        horizontalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterHorizontally)
                    ) {
                        if (!answered) {
                            val decline: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                                BigButton(
                                    stringResource(R.string.incall_decline),
                                    DeclineRed,
                                    OgnelissIcons.CallEnd,
                                    modifier = Modifier.weight(1f),
                                    shareRow = true,
                                    onClick = onHangUp
                                )
                            }
                            // Та же «дышащая» кнопка ответа, что у настоящего входящего
                            val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "fakeAnswer")
                            val scale by pulse.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.04f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    androidx.compose.animation.core.tween(
                                        1100, easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    ),
                                    androidx.compose.animation.core.RepeatMode.Reverse
                                ),
                                label = "fakeAnswerScale"
                            )
                            val answer: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                                Box(
                                    Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                                ) {
                                    BigButton(
                                        stringResource(R.string.incall_answer),
                                        CallGreen,
                                        OgnelissIcons.Call,
                                        modifier = Modifier.fillMaxWidth(),
                                        shareRow = true
                                    ) { answered = true; onAnswer() }
                                }
                            }
                            if (swapAnswer) { answer(); decline() } else { decline(); answer() }
                        } else {
                            BigButton(
                                stringResource(R.string.incall_hangup),
                                DeclineRed,
                                OgnelissIcons.CallEnd,
                                onClick = onHangUp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.xl))
            }
            } // LocalControlsOnAmbient
            } // панель
        }
    }
    } // Box с фото
}

/**
 * Кнопки разговора псевдозвонка — те же плитки и та же раскладка, что
 * ControlsRow настоящего вызова: четыре кнопки — одной строкой (1.5.7),
 * зазоры из controlsGap.
 * Без «Добавить вызов», Bluetooth и «Объединить»: у настоящего они видны
 * только когда доступны, а чаще — нет.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FakeControlsRow(
    muted: Boolean,
    speaker: Boolean,
    holding: Boolean,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onDialpad: () -> Unit,
    onHold: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = Space.s, horizontal = Space.s),
        contentAlignment = Alignment.Center
    ) {
        val perRow = controlsPerRow(4)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(controlsGap(perRow), Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Space.l),
            maxItemsInEachRow = perRow,
            modifier = Modifier.fillMaxWidth()
        ) {
            ToggleTile(muted, OgnelissIcons.MicOff, stringResource(R.string.incall_mute), onMute)
            ToggleTile(speaker, OgnelissIcons.VolumeUp, stringResource(R.string.incall_speaker), onSpeaker)
            ToggleTile(false, OgnelissIcons.Dialpad, stringResource(R.string.incall_keypad), onDialpad)
            ToggleTile(holding, OgnelissIcons.Pause, stringResource(R.string.incall_hold), onHold)
        }
    }
}
