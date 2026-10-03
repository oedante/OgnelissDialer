package com.ogneliss.dialer.call

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
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

        // Поверх экрана блокировки: телефон обычно лежит заблокированным,
        // а звонок должен быть виден так же, как настоящий
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        startRinging()

        setContent {
            val theme by prefs.theme.collectAsState(ThemeMode.SYSTEM)
            val dynamic by prefs.dynamicColor.collectAsState(false)

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
                FakeCallScreen(
                    name = caller?.first.orEmpty(),
                    number = caller?.second.orEmpty(),
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
    onAnswer: () -> Unit,
    onHangUp: () -> Unit
) {
    var answered by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(answered) {
        if (!answered) return@LaunchedEffect
        while (true) {
            delay(1000)
            seconds++
        }
    }

    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().padding(FlowUi.contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                Modifier.padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    name,
                    fontSize = FlowUi.callNameSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                if (number.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        PhoneFormatter.format(number),
                        fontSize = FlowUi.callNumberSize,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    if (answered) {
                        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
                    } else {
                        stringResource(R.string.incall_incoming)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                if (!answered) {
                    FakeCallButton(
                        label = stringResource(R.string.incall_decline),
                        color = DeclineRed,
                        onClick = onHangUp
                    )
                    FakeCallButton(
                        label = stringResource(R.string.incall_answer),
                        color = CallGreen,
                        onClick = { answered = true; onAnswer() }
                    )
                } else {
                    FakeCallButton(
                        label = stringResource(R.string.incall_hangup),
                        color = DeclineRed,
                        onClick = onHangUp
                    )
                }
            }
        }
    }
}

@Composable
private fun FakeCallButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier
            .heightIn(min = FlowUi.primaryActionHeight)
            .widthIn(min = FlowUi.callActionMinWidth)
    ) {
        Icon(
            if (color == CallGreen) Icons.Default.Call else Icons.Default.CallEnd,
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
