package com.ogneliss.dialer.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.Space

/**
 * СООБЩЕНИЕ С ОБРАТНЫМ ОТСЧЁТОМ (1.5.8, обзор интерфейса, У5).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * «Удалено из журнала · Отменить» гасло без предупреждения: сколько
 * времени осталось, чтобы передумать, видно не было. Удаление из журнала
 * при этом окончательное — после того как сообщение погаснет, запись
 * уходит из системного журнала (разбор в deleteGroupsWithUndo).
 *
 * Как в Telegram: слева кольцо, которое убывает, и в нём — секунды.
 * Видно, что действие ещё можно отменить и сколько на это есть.
 *
 * ── Сколько ждать ──────────────────────────────────────────────────────────
 * Тот же срок, что у SnackbarHost: 4 с у короткого сообщения, 10 с
 * у длинного, и столько, сколько попросил человек в специальных
 * возможностях Android («Время на выполнение действия»), — тот же расчёт
 * через LocalAccessibilityManager. Иначе кольцо кончалось бы раньше
 * или позже, чем гаснет сообщение.
 *
 * ── Время — по часам, а не анимацией ──────────────────────────────────────
 * При выключенной в Android анимации анимированное кольцо опустело бы
 * сразу, хотя сообщение ещё висит. Кольцо считает по часам на каждом
 * кадре — четыре секунды по шестьдесят кадров ничего не стоят.
 *
 * ── TalkBack ───────────────────────────────────────────────────────────────
 * Кольцо и цифры скрыты от чтения с экрана: сообщение объявляется
 * вслух, и меняющаяся цифра внутри него читалась бы каждую секунду заново.
 *
 * Сообщения без кнопки — обычные, без кольца: отменять нечего.
 */
@Composable
fun CountdownSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    val actionLabel = visuals.actionLabel
    if (actionLabel == null) {
        Snackbar(data)
        return
    }

    val accessibility = LocalAccessibilityManager.current
    val total = remember(data) {
        val base = when (visuals.duration) {
            SnackbarDuration.Short -> SHORT_MS
            SnackbarDuration.Long -> LONG_MS
            SnackbarDuration.Indefinite -> Long.MAX_VALUE
        }
        if (base == Long.MAX_VALUE) base
        else accessibility?.calculateRecommendedTimeoutMillis(
            base,
            containsIcons = true,
            containsText = true,
            containsControls = true
        ) ?: base
    }
    val timed = total != Long.MAX_VALUE
    var left by remember(data) { mutableLongStateOf(total) }
    if (timed) {
        LaunchedEffect(data) {
            val start = SystemClock.uptimeMillis()
            while (left > 0) {
                withFrameMillis { }
                left = (total - (SystemClock.uptimeMillis() - start)).coerceAtLeast(0L)
            }
        }
    }

    val actionColor = SnackbarDefaults.actionColor
    Snackbar(
        modifier = Modifier.padding(SNACKBAR_MARGIN),
        action = {
            TextButton(
                onClick = { data.performAction() },
                colors = ButtonDefaults.textButtonColors(contentColor = actionColor)
            ) {
                Text(actionLabel)
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (timed) {
                CountdownRing(
                    fraction = left.toFloat() / total,
                    seconds = ((left + 999) / 1000).toInt(),
                    color = actionColor
                )
                Spacer(Modifier.width(Space.m))
            }
            Text(visuals.message, maxLines = 2)
        }
    }
}

/** Кольцо, которое убывает по часовой стрелке, и оставшиеся секунды в нём. */
@Composable
private fun CountdownRing(fraction: Float, seconds: Int, color: Color) {
    Box(
        Modifier
            .size(RING_SIZE)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(RING_SIZE)) {
            val stroke = RING_STROKE.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = color.copy(alpha = TRACK_ALPHA),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke)
            )
            drawArc(
                color = color,
                startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            seconds.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private const val SHORT_MS = 4_000L
private const val LONG_MS = 10_000L
private const val TRACK_ALPHA = 0.25f
private val RING_SIZE = 28.dp
private val RING_STROKE = 2.5.dp
/** Поле вокруг сообщения — как у стандартного Snackbar(data) в Material 3. */
private val SNACKBAR_MARGIN = 12.dp
