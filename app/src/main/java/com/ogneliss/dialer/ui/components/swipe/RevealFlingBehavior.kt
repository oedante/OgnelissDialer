package com.ogneliss.dialer.ui.components.swipe

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import kotlin.math.abs

/**
 * Бросок строки свайпа с НАСТРАИВАЕМЫМ порогом скорости (1.3.3, 04.10.2026).
 *
 * Было: конструктор AnchoredDraggableState(…, positionalThreshold,
 * velocityThreshold, snapAnimationSpec, decayAnimationSpec) — Compose 1.12
 * пометил его устаревшим. Его замена, AnchoredDraggableDefaults.flingBehavior,
 * принимает только порог положения: порог скорости в ней зашит (125 dp/с).
 * У нас он настраивается (SwipeTuning.velocityDpPerSecond, по умолчанию 450):
 * при 120 dp/с обычная прокрутка журнала сдвигала строки вбок.
 *
 * Стало: тот же механизм, что у стандартного flingBehavior, — snapFlingBehavior
 * и SnapLayoutInfoProvider (оба — публичный API foundation), — но порог
 * скорости передаётся снаружи. Выбор цели повторяет правило прежнего
 * конструктора, чтобы жест под пальцем не изменился:
 *  - строка стоит на якоре — остаётся на нём;
 *  - бросок быстрее порога скорости — к ближайшему якорю по направлению броска;
 *  - медленнее — к соседнему якорю, только если пройдено больше доли
 *    positionalThreshold пути между ними, иначе обратно.
 * Отсчёт — от якоря, на котором строка стояла до жеста (settledValue).
 *
 * Обобщена по типу якоря: RevealState объявлен private в SwipeRevealRow.kt,
 * а выбору цели его значения не нужны (сборка 04.10.2026 упала на этом).
 */
internal fun <T> revealFlingBehavior(
    state: AnchoredDraggableState<T>,
    positionalThreshold: (distance: Float) -> Float,
    velocityThreshold: () -> Float
): TargetedFlingBehavior = snapFlingBehavior(
    snapLayoutInfoProvider = RevealSnapLayoutInfo(state, positionalThreshold, velocityThreshold),
    decayAnimationSpec = exponentialDecay(),
    snapAnimationSpec = spring()
)

private class RevealSnapLayoutInfo<T>(
    private val state: AnchoredDraggableState<T>,
    private val positionalThreshold: (distance: Float) -> Float,
    private val velocityThreshold: () -> Float
) : SnapLayoutInfoProvider {

    // Без «подлёта» по инерции: строка сразу доводится до выбранного якоря
    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f

    override fun calculateSnapOffset(velocity: Float): Float {
        val anchors = state.anchors
        val offset = state.requireOffset()
        val settled = state.settledValue
        val from = anchors.positionOf(settled)

        val target = when {
            from.isNaN() || from == offset -> settled
            abs(velocity) >= abs(velocityThreshold()) ->
                anchors.closestAnchor(offset, searchUpwards = velocity > 0) ?: settled
            else -> {
                val neighbor = anchors.closestAnchor(offset, searchUpwards = offset > from) ?: settled
                val distance = abs(anchors.positionOf(neighbor) - from)
                if (abs(offset - from) <= abs(positionalThreshold(distance))) settled else neighbor
            }
        }
        val targetPosition = anchors.positionOf(target)
        return if (targetPosition.isNaN()) 0f else targetPosition - offset
    }
}
