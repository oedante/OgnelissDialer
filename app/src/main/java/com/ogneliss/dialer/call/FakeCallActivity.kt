package com.ogneliss.dialer.call

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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.ContactsRepository
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.DeclineRed
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.Locale
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
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Поверх экрана блокировки: телефон обычно лежит заблокированным,
        // а звонок должен быть виден так же, как настоящий
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        startRinging()

        setContent {
            val theme by prefs.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dynamic by prefs.dynamicColor.collectAsStateWithLifecycle(false)
            // Живой фон — как у настоящего вызова (1.5.1)
            val callAnimation by prefs.callAnimation.collectAsStateWithLifecycle(true)

            /*
             * Кто «звонит».
             *
             * Настройка хранит номер. Пусто — берётся случайный контакт:
             * так звонок выглядит правдоподобно у того, кто не настраивал
             * ничего заранее, а именно этот человек и нажмёт кнопку
             * в неудобный момент.
             */
            var caller by remember { mutableStateOf<Pair<String, String>?>(null) }

            LaunchedEffect(Unit) {
                val chosen = prefs.fakeCallNumber.first()
                caller = if (chosen.isNotBlank()) {
                    val name = suspendRunCatching { contactsRepo.lookupByNumber(chosen) }.getOrNull()
                    (name ?: PhoneFormatter.format(chosen)) to chosen
                } else {
                    val random = suspendRunCatching {
                        contactsRepo.loadAll().filter { it.numbers.isNotEmpty() }.randomOrNull()
                    }.getOrNull()

                    if (random != null) {
                        random.displayName to random.numbers.first().value
                    } else {
                        // Контактов нет вовсе — показываем номер
                        // без имени: это тоже выглядит как обычный звонок
                        getString(R.string.fake_call_default_name) to ""
                    }
                }
            }

            OgnelissDialerTheme(themeMode = theme, dynamicColor = dynamic) {
                // Раскладка окна: телефон, лёжа, раскладушка, планшет.
                // Разбор — в ui/adaptive/WindowLayout.kt
                ProvideWindowLayout(this@FakeCallActivity) {
                // Фото — тем же запросом, что у настоящего вызова (1.3.4)
                val callerNumber = caller?.second.orEmpty()
                val photo by produceState<String?>(initialValue = null, callerNumber) {
                    value = callerNumber.takeIf { it.isNotBlank() }?.let {
                        suspendRunCatching { contactsRepo.photoUriForNumber(it) }.getOrNull()
                    }
                }
                FakeCallScreen(
                    name = caller?.first.orEmpty(),
                    number = callerNumber,
                    photoUri = photo,
                    ambientBackground = callAnimation && theme != ThemeMode.HIGH_CONTRAST,
                    onAnswer = { stopRinging() },
                    onHangUp = { finish() }
                )
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

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}

@Composable
private fun FakeCallScreen(
    name: String,
    number: String,
    photoUri: String?,
    /** Живой фон (CallAmbientBackground.kt); без фото на весь экран. */
    ambientBackground: Boolean = false,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit
) {
    var answered by remember { mutableStateOf(false) }
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
    val photoBackground = !LocalWindowLayout.current.isShort && !photoUri.isNullOrBlank()
    LightStatusIconsOnPhoto(photoBackground)
    // Живой фон — по тем же состояниям, что у настоящего вызова
    val ambient = ambientBackground && !photoBackground
    Box(Modifier.fillMaxSize()) {
    if (photoBackground) CallPhotoBackground(photoUri.orEmpty())
    if (ambient) {
        CallAmbientBackground(
            when {
                !answered -> AmbientMood.INCOMING
                holding -> AmbientMood.HOLDING
                else -> AmbientMood.ACTIVE
            }
        )
    }
    NavBarUnderPanel()
    Surface(
        Modifier.fillMaxSize().safeDrawingPadding(),
        color = if (photoBackground || ambient) Color.Transparent else MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            OnPhotoColors(photoBackground) {
            Column(
                Modifier.padding(top = Space.xl, start = FlowUi.contentPadding, end = FlowUi.contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    name,
                    fontSize = FlowUi.callNameSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                if (number.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        PhoneFormatter.format(number),
                        fontSize = FlowUi.callNumberSize,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(Modifier.height(Space.m))
                CallStatusPill(
                    active = answered && !holding,
                    text = when {
                        !answered -> stringResource(R.string.incall_incoming)
                        holding -> stringResource(R.string.incall_on_hold)
                        // Формат и Locale — как у настоящего таймера (CallStatusLine)
                        else -> String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
                    }
                )
            }
            } // OnPhotoColors

            Surface(
                shape = OgnelissShapeTokens.bottomPanel,
                // Как у настоящего вызова: общий цвет нижней панели (1.4.0)
                color = PanelColors.panel(),
                modifier = Modifier.fillMaxWidth()
            ) {
            Column(
                Modifier.fillMaxWidth().padding(top = Space.l, start = FlowUi.contentPadding, end = FlowUi.contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.animation.AnimatedVisibility(answered && showDialpad) {
                    DtmfPanel(
                        entered = dtmf,
                        onDigit = { dtmf += it },
                        onBackspace = { dtmf = dtmf.dropLast(1) },
                        onClose = { showDialpad = false }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = answered,
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

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.s),
                    horizontalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterHorizontally)
                ) {
                    if (!answered) {
                        BigButton(
                            stringResource(R.string.incall_decline),
                            DeclineRed,
                            OgnelissIcons.CallEnd,
                            modifier = Modifier.weight(1f),
                            shareRow = true,
                            onClick = onHangUp
                        )
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
                    } else {
                        BigButton(
                            stringResource(R.string.incall_hangup),
                            DeclineRed,
                            OgnelissIcons.CallEnd,
                            onClick = onHangUp
                        )
                    }
                }
                Spacer(Modifier.height(Space.xl))
            }
            } // панель
        }
    }
    } // Box с фото
}

/**
 * Кнопки разговора псевдозвонка — те же плитки и та же раскладка, что
 * ControlsRow настоящего вызова (три в ряд, перенос, зазоры 20 dp и 16 dp).
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Space.l),
            maxItemsInEachRow = 3,
            modifier = Modifier.fillMaxWidth()
        ) {
            ToggleTile(muted, OgnelissIcons.MicOff, stringResource(R.string.incall_mute), onMute)
            ToggleTile(speaker, OgnelissIcons.VolumeUp, stringResource(R.string.incall_speaker), onSpeaker)
            ToggleTile(false, OgnelissIcons.Dialpad, stringResource(R.string.incall_keypad), onDialpad)
            ToggleTile(holding, OgnelissIcons.Pause, stringResource(R.string.incall_hold), onHold)
        }
    }
}
