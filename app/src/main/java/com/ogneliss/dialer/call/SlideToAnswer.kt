package com.ogneliss.dialer.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import android.view.accessibility.AccessibilityManager
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.DeclineRed
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ОТВЕТ НА ВЫЗОВ СДВИГОМ (1.5.8, обзор интерфейса, У6).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Телефон звонит в кармане, его достают — и ладонь касается экрана.
 * С кнопками «Ответить» и «Отклонить» такое касание отвечает на вызов
 * или сбрасывает его. Сдвиг случайно не получается: нужно провести
 * пальцем почти до края.
 *
 * Включается настройкой «Ответ на вызов» (AnswerMode); по умолчанию —
 * кнопки, как было.
 *
 * ── Как устроено ───────────────────────────────────────────────────────────
 * Дорожка во всю ширину, по краям подсказки: слева «Отклонить», справа
 * «Ответить». Посередине — круглая ручка с трубкой, она «дышит», как
 * прежняя кнопка ответа. Вправо — ручка зелёная, дорожка зеленеет;
 * влево — ручка краснеет и трубка опускается. Дотянули до 70 % пути —
 * короткий отклик вибрацией: «сработает», отпустили — сработало.
 * Не дотянули — ручка пружиной возвращается на место.
 *
 * ── Справа налево ───────────────────────────────────────────────────────────
 * В раскладке справа налево (арабский, иврит в системе) дорожка
 * зеркальна: «Ответить» слева, и ответ —
 * сдвигом влево, как любой ползунок в такой раскладке. Ручку двигает
 * offset — он сам зеркалит сдвиг, а жест приходит в экранных
 * координатах; поэтому знак жеста в RTL меняется (toward).
 *
 * ── Доступность ────────────────────────────────────────────────────────────
 * С TalkBack экран вызова показывает обычные кнопки (useSlideToAnswer):
 * у него случайных касаний и так нет — нажатие требует двойного касания.
 * Для остальных способов управления (Switch Access, голос) у ручки два
 * действия — «Ответить» и «Отклонить» — и описание жеста.
 */
@Composable
internal fun SlideToAnswer(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Сдвиг «к ответу» — вправо, а справа налево — влево (разбор выше)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val drag = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    val answerLabel = stringResource(R.string.incall_answer)
    val declineLabel = stringResource(R.string.incall_decline)
    // Подсказка называет стороны — в зеркальной раскладке они другие
    val hint = stringResource(if (rtl) R.string.incall_slide_hint_rtl else R.string.incall_slide_hint)

    // «Дыхание» ручки — пока её не тронули: тот же ритм, что у кнопки ответа
    val pulse = rememberInfiniteTransition(label = "slideKnob")
    val breathe by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "slideKnobScale"
    )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            // Над живым фоном без панели — тень и ободок (1.6.0)
            .ambientEdge(LocalControlsOnAmbient.current, CircleShape, MaterialTheme.colorScheme.outlineVariant)
            .clip(CircleShape)
            .background(PanelColors.key())
    ) {
        val density = LocalDensity.current
        val knob = TRACK_HEIGHT - KNOB_INSET * 2
        val maxTravel = with(density) { ((maxWidth - knob) / 2 - KNOB_INSET).toPx() }.coerceAtLeast(1f)
        val progress = (drag.value / maxTravel).coerceIn(-1f, 1f)

        // Дорожка наливается цветом стороны, к которой тянут
        val fill = when {
            progress > 0f -> CallGreen.copy(alpha = FILL_ALPHA * progress)
            progress < 0f -> DeclineRed.copy(alpha = FILL_ALPHA * -progress)
            else -> Color.Transparent
        }
        Box(Modifier.matchParentSize().background(fill))

        // Подсказки по краям: гаснут по мере сдвига
        val hintsAlpha = 1f - abs(progress)
        Row(
            Modifier
                .matchParentSize()
                .padding(horizontal = Space.l)
                .graphicsLayer { alpha = hintsAlpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(OgnelissIcons.CallEnd, contentDescription = null, tint = DeclineRed)
            Spacer(Modifier.width(Space.s))
            Text(
                declineLabel,
                style = MaterialTheme.typography.labelLarge,
                color = DeclineRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(knob))
            Text(
                answerLabel,
                style = MaterialTheme.typography.labelLarge,
                color = CallGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Spacer(Modifier.width(Space.s))
            Icon(OgnelissIcons.Call, contentDescription = null, tint = CallGreen)
        }

        // Ручка
        val knobColor = if (progress >= 0f) CallGreen
        else lerp(CallGreen, DeclineRed, (-progress * 2f).coerceAtMost(1f))
        val idle = drag.value == 0f && !done
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(drag.value.roundToInt(), 0) }
                .size(knob)
                .graphicsLayer {
                    val s = if (idle) breathe else 1f
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(knobColor)
                .semantics {
                    contentDescription = hint
                    customActions = listOf(
                        CustomAccessibilityAction(answerLabel) { done = true; onAnswer(); true },
                        CustomAccessibilityAction(declineLabel) { done = true; onDecline(); true }
                    )
                }
                .pointerInput(maxTravel, rtl) {
                    // Положение ручки считаем здесь, а не по drag.value:
                    // snapTo выполнится позже, и несколько событий одного
                    // кадра сложились бы с устаревшим значением
                    var current = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { current = drag.value },
                        onDragEnd = end@{
                            if (done) return@end
                            val p = current / maxTravel
                            scope.launch {
                                when {
                                    p >= TRIGGER -> {
                                        done = true
                                        drag.animateTo(maxTravel, tween(Q_MS))
                                        onAnswer()
                                    }
                                    p <= -TRIGGER -> {
                                        done = true
                                        drag.animateTo(-maxTravel, tween(Q_MS))
                                        onDecline()
                                    }
                                    else -> drag.animateTo(0f, spring())
                                }
                                armed = false
                            }
                        },
                        onDragCancel = {
                            if (!done) scope.launch { drag.animateTo(0f, spring()) }
                            armed = false
                        },
                        onHorizontalDrag = move@{ change, delta ->
                            if (done) return@move
                            change.consume()
                            val toward = if (rtl) -delta else delta
                            current = (current + toward).coerceIn(-maxTravel, maxTravel)
                            val target = current
                            scope.launch { drag.snapTo(target) }
                            val nowArmed = abs(target) >= TRIGGER * maxTravel
                            if (nowArmed && !armed) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            armed = nowArmed
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (progress < -0.15f) OgnelissIcons.CallEnd else OgnelissIcons.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Показывать ли сдвиг вместо кнопок: выбран в настройках и не включён
 * TalkBack (разбор — у SlideToAnswer).
 */
@Composable
internal fun useSlideToAnswer(slideChosen: Boolean): Boolean {
    val context = LocalContext.current
    val accessibility = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
    // TalkBack включили, пока телефон звонит, — кнопки появляются сразу
    var touchExploration by remember(accessibility) {
        mutableStateOf(accessibility?.isTouchExplorationEnabled == true)
    }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            touchExploration = enabled
        }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    return slideChosen && !touchExploration
}

/** Высота дорожки — как у кнопок ответа (BigButton). */
private val TRACK_HEIGHT = FlowUi.primaryActionHeight + 16.dp
private val KNOB_INSET = 6.dp
/** Доля пути, после которой отпускание срабатывает. */
private const val TRIGGER = 0.7f
private const val FILL_ALPHA = 0.35f
private const val Q_MS = 150
