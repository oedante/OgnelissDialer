package com.ogneliss.dialer.call

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
import kotlinx.coroutines.delay

/*
 * ПАМЯТКА О МОШЕННИКАХ (1.8.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, в списке функций для 1.8.0: «Памятка о мошенниках». Телефонный
 * обман устроен одинаково: незнакомый номер, «служба безопасности банка»,
 * долгий разговор, в конце — код из SMS. Решающая минута — когда человек
 * уже полчаса на линии и не может посмотреть на ситуацию со стороны.
 *
 * Памятка — одно напоминание за разговор, на экране вызова, без
 * блокировок и без оценок: «не называйте коды из SMS». Приложение не знает,
 * мошенник ли на линии, и не делает вид, что знает, — оно напоминает
 * правило, которое верно для любого звонка.
 *
 * ── Когда ──────────────────────────────────────────────────────────────────
 * Номер не из книги, разговор идёт дольше [ScamMemoPolicy.THRESHOLD_MS]
 * (две минуты). Курьер и доставка до неё не доживают; обзвон «службы
 * безопасности» длится десятки минут. Телефон коротко вздрагивает: трубка
 * у уха, экран погашен датчиком, без отклика памятку никто не увидит.
 *
 * ── Почему один раз ────────────────────────────────────────────────────────
 * Повторяющееся предупреждение перестают читать на третьем показе.
 * «Понятно» убирает памятку до конца разговора.
 */

/** Правило показа памятки — чистое, без Android. */
object ScamMemoPolicy {

    /** Сколько должен длиться разговор с незнакомым номером. */
    const val THRESHOLD_MS = 2 * 60 * 1000L

    /**
     * Через сколько миллисекунд показать памятку; null — не показывать.
     *
     * @param connectedAt когда соединились (Call.Details.connectTimeMillis), 0 — ещё нет
     */
    fun delayMs(
        enabled: Boolean,
        active: Boolean,
        unknown: Boolean,
        alreadyShown: Boolean,
        connectedAt: Long,
        now: Long
    ): Long? {
        if (!enabled || !active || !unknown || alreadyShown || connectedAt <= 0L) return null
        return (connectedAt + THRESHOLD_MS - now).coerceAtLeast(0L)
    }
}

/** Видна ли памятка и показывалась ли уже в этом разговоре. */
@Stable
internal class ScamMemoState {
    var visible by mutableStateOf(false)
        private set
    var shown by mutableStateOf(false)
        private set

    fun show() {
        visible = true
        shown = true
    }

    fun dismiss() {
        visible = false
    }
}

/**
 * Состояние памятки для текущего разговора.
 *
 * Ключ — номер: новый собеседник — новая памятка. Попытки автодозвона
 * на тот же номер — тот же разговор, повторять её незачем.
 */
@Composable
internal fun rememberScamMemo(
    enabled: Boolean,
    active: Boolean,
    unknown: Boolean,
    connectedAt: Long,
    key: String
): ScamMemoState {
    val state = remember(key) { ScamMemoState() }
    val context = LocalContext.current
    LaunchedEffect(enabled, active, unknown, connectedAt, state.shown) {
        val wait = ScamMemoPolicy.delayMs(
            enabled, active, unknown, state.shown, connectedAt, System.currentTimeMillis()
        ) ?: return@LaunchedEffect
        delay(wait)
        state.show()
        nudge(context)
    }
    return state
}

/** Карточка памятки над кнопками управления. */
@Composable
internal fun ScamMemoCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        // TalkBack прочтёт памятку сам, как только она появится
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(Modifier.padding(start = Space.l, end = Space.s, top = Space.m, bottom = Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = Space.s)) {
                Icon(OgnelissIcons.Shield, contentDescription = null)
                Spacer(Modifier.width(Space.m))
                Text(stringResource(R.string.scam_memo_title), style = MaterialTheme.typography.titleSmall)
            }
            Text(
                stringResource(R.string.scam_memo_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Space.s, end = Space.s)
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.scam_memo_ok))
            }
        }
    }
}

/**
 * Короткий двойной отклик: трубка у уха, экран погашен — без него
 * памятку не заметят. Нет вибромотора или запрещено — молча обходимся.
 */
private fun nudge(context: Context) {
    runCatching {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(NUDGE_PATTERN, -1))
    }
}

/** Пауза, отклик, пауза, отклик — миллисекунды. */
private val NUDGE_PATTERN = longArrayOf(0L, 60L, 90L, 60L)
