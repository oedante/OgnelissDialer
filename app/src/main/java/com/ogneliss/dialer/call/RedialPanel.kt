package com.ogneliss.dialer.call

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.RedialPolicy.Phase
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.DeclineRed
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
import kotlinx.coroutines.delay

/*
 * ЭКРАН ВЫЗОВА МЕЖДУ ПОПЫТКАМИ АВТОДОЗВОНА (1.8.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ НА ЭКРАНЕ ВЫЗОВА, А НЕ ОТДЕЛЬНЫМ ОКНОМ
 * ══════════════════════════════════════════════════════════════════════════
 * Человек только что видел «Соединение…» с портретом и именем. Если после
 * «занято» экран закроется и откроется другое окно, связь «я звоню этому
 * человеку» рвётся. Здесь экран остаётся тем же: портрет, имя, номер —
 * на месте, меняются только плашка состояния и кнопки внизу.
 *
 * ── Кнопки — у большого пальца ─────────────────────────────────────────────
 * На месте «Завершить», той же высоты (BigButton): палец, который только что
 * был у красной кнопки, находит их не глядя. Главное действие — справа
 * и зелёным: «Дозваниваться», «Позвонить сейчас», «Ещё раз». Остановка —
 * слева: красная, когда идёт автодозвон, серая, когда просто закрыть.
 *
 * ── Отсчёт — вслух один раз ────────────────────────────────────────────────
 * Плашка с «Повтор через 7 с» меняется каждую секунду; TalkBack произносит
 * её только при смене этапа (CallStatusPill, параметр spoken): иначе диктор
 * говорил бы без остановки.
 */

/** Плашка состояния вместо «Вызов завершён» и строка под ней. */
@Composable
internal fun RedialStatus(session: AutoRedial.Session) {
    val reason = stringResource(reasonRes(session.reason))
    val remaining by produceState(secondsLeft(session.dialAt), session.dialAt, session.phase) {
        while (session.phase == Phase.WAITING) {
            value = secondsLeft(session.dialAt)
            delay(TICK_MS)
        }
    }
    val attemptLine = stringResource(R.string.redial_attempt, session.attempt.coerceAtLeast(1), session.maxAttempts)

    val (pill, caption) = when (session.phase) {
        Phase.OFFER -> reason to
            stringResource(R.string.redial_offer_hint, session.maxAttempts, session.intervalSec)
        Phase.WAITING -> stringResource(R.string.redial_waiting, remaining) to "$reason · $attemptLine"
        Phase.CALLING -> stringResource(R.string.redial_calling) to attemptLine
        Phase.GAVE_UP -> stringResource(R.string.redial_gave_up) to
            "$reason · " + stringResource(R.string.redial_gave_up_detail, session.maxAttempts)
    }
    // Вслух — этап, а не секунды: разбор в шапке файла
    val spoken = when (session.phase) {
        Phase.WAITING, Phase.CALLING -> stringResource(
            R.string.redial_spoken, reason, session.attempt.coerceAtLeast(1), session.maxAttempts
        )
        else -> "$pill. $caption"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CallStatusPill(active = false, text = pill, spoken = spoken)
        Spacer(Modifier.height(Space.s))
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Подпись под плашкой, пока идёт попытка: «Автодозвон · попытка 2 из 5». */
@Composable
internal fun RedialAttemptLine(session: AutoRedial.Session) {
    Spacer(Modifier.height(Space.s))
    Text(
        stringResource(R.string.redial_attempt, session.attempt.coerceAtLeast(1), session.maxAttempts),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** Кнопки автодозвона — на месте «Завершить». */
@Composable
internal fun RedialButtons(session: AutoRedial.Session, modifier: Modifier = Modifier) {
    val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
    val onNeutral = MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterHorizontally)
    ) {
        when (session.phase) {
            Phase.OFFER -> {
                BigButton(
                    stringResource(R.string.nav_close), neutral, OgnelissIcons.Close,
                    modifier = Modifier.weight(1f), shareRow = true, contentColor = onNeutral
                ) { AutoRedial.stop() }
                BigButton(
                    stringResource(R.string.redial_action_start), CallGreen, OgnelissIcons.RestartAlt,
                    modifier = Modifier.weight(1f), shareRow = true
                ) { AutoRedial.accept() }
            }
            Phase.WAITING -> {
                BigButton(
                    stringResource(R.string.redial_action_stop), DeclineRed, OgnelissIcons.Close,
                    modifier = Modifier.weight(1f), shareRow = true
                ) { AutoRedial.stop() }
                BigButton(
                    stringResource(R.string.redial_action_now), CallGreen, OgnelissIcons.Call,
                    modifier = Modifier.weight(1f), shareRow = true
                ) { AutoRedial.redialNow() }
            }
            // Попытка набирается — остановить можно, ускорить нечего
            Phase.CALLING -> BigButton(
                stringResource(R.string.redial_action_stop), DeclineRed, OgnelissIcons.Close
            ) { AutoRedial.stop() }
            Phase.GAVE_UP -> {
                BigButton(
                    stringResource(R.string.nav_close), neutral, OgnelissIcons.Close,
                    modifier = Modifier.weight(1f), shareRow = true, contentColor = onNeutral
                ) { AutoRedial.stop() }
                BigButton(
                    stringResource(R.string.redial_action_again), CallGreen, OgnelissIcons.RestartAlt,
                    modifier = Modifier.weight(1f), shareRow = true
                ) { AutoRedial.retry() }
            }
        }
    }
}

private fun reasonRes(reason: RedialPolicy.Reason): Int = when (reason) {
    RedialPolicy.Reason.BUSY -> R.string.redial_reason_busy
    RedialPolicy.Reason.NO_ANSWER -> R.string.redial_reason_no_answer
    RedialPolicy.Reason.FAILED -> R.string.redial_reason_failed
}

/** Сколько целых секунд до набора, с округлением вверх: «через 1 с», а не «через 0». */
private fun secondsLeft(dialAt: Long): Int {
    if (dialAt <= 0L) return 0
    val ms = (dialAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    return ((ms + 999L) / 1000L).toInt()
}

/** Чаще секунды — чтобы цифра менялась ровно, а не с запаздыванием до секунды. */
private const val TICK_MS = 250L
