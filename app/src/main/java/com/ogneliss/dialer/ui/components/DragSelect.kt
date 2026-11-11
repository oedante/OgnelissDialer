package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.LocalAppearance
import com.ogneliss.dialer.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * ВЫБОР ПРОТЯГИВАНИЕМ (2.1.3).
 *
 * Автор, 11.11.2026: «выбирать несколько можно, только нажимая на каждый,
 * а протягивание не работает». Стало, как в почте и галерее: в режиме
 * выбора палец ведут по столбцу кружков (место портретов) — и строки
 * отмечаются подряд. Начали с неотмеченной — отмечает, с отмеченной —
 * снимает. У верхнего и нижнего края список сам подкручивается.
 *
 * Работает и продолжением долгого нажатия на портрет: оно включает выбор,
 * а палец, не отрываясь, ведут дальше.
 *
 * ── Как уживается с остальным ──────────────────────────────────────────────
 * Жест начинается только в столбце кружков и только движением по вертикали:
 * касание без движения остаётся строке (отметить одну), движение вбок —
 * свайпу строки, вертикаль правее столбца — прокрутке. События
 * перехватываются на спуске (Initial) и гасятся — список в это время
 * не прокручивается.
 *
 * @param keyOf ключ элемента списка → ключ выбора; null — не строка
 *        (заголовок дня)
 */
fun <T> Modifier.dragSelect(
    selection: SelectionState<T>,
    listState: LazyListState,
    keyOf: (Any) -> T?
): Modifier = composed {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val columnPx = with(density) { (Space.l + LocalAppearance.current.avatarSize + Space.s).toPx() }
    val edgePx = with(density) { EDGE.toPx() }
    val stepPx = with(density) { SCROLL_STEP.toPx() }
    val currentKeyOf by rememberUpdatedState(keyOf)

    var dragging by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }
    // Что делает жест (отмечает или снимает) и где он был в прошлый раз
    val session = remember { object { var select = true; var lastIndex = -1 } }

    /** Строка под пальцем и все между ней и прошлой — в состояние жеста. */
    fun applyAt(y: Float) {
        val info = listState.layoutInfo
        val local = logical(info, y)
        val hit = info.visibleItemsInfo.firstOrNull { local >= it.offset && local < it.offset + it.size } ?: return
        val from = if (session.lastIndex < 0) hit.index else session.lastIndex
        val range = minOf(from, hit.index)..maxOf(from, hit.index)
        info.visibleItemsInfo.forEach { item ->
            if (item.index in range) currentKeyOf(item.key)?.let { selection.set(it, session.select) }
        }
        session.lastIndex = hit.index
    }

    // У края — подкрутка, пока палец там стоит
    LaunchedEffect(dragging) {
        while (dragging && isActive) {
            if (autoScroll != 0f) {
                listState.scrollBy(autoScroll)
                applyAt(lastY)
            }
            delay(FRAME_MS)
        }
    }

    pointerInput(selection, listState, rtl, columnPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val inColumn = if (rtl) down.position.x > size.width - columnPx else down.position.x < columnPx
            if (!inColumn) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            // Ждём движения: по вертикали и в режиме выбора — наш жест
            var started = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (abs(dx) > slop && abs(dx) >= abs(dy)) break
                if (abs(dy) > slop) {
                    started = selection.isActive
                    if (started) change.consume()
                    break
                }
            }
            if (!started) return@awaitEachGesture

            val info = listState.layoutInfo
            val startLocal = logical(info, down.position.y)
            val first = info.visibleItemsInfo.firstOrNull { startLocal >= it.offset && startLocal < it.offset + it.size }
            // С отмеченной строки жест снимает, с остальных — отмечает. После
            // долгого нажатия строка только что отмечена им же — тогда отмечаем
            val firstKey = first?.let { currentKeyOf(it.key) }
            session.select = firstKey == null || !selection.isSelected(firstKey) || selection.count == 1
            session.lastIndex = -1
            dragging = true
            applyAt(down.position.y)
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    lastY = change.position.y
                    // Перевёрнутый список (переписка, 2.2.0): вперёд — это вверх
                    val toward = if (listState.layoutInfo.reverseLayout) -1f else 1f
                    autoScroll = when {
                        lastY < edgePx -> -stepPx * toward
                        lastY > size.height - edgePx -> stepPx * toward
                        else -> 0f
                    }
                    applyAt(lastY)
                }
            } finally {
                dragging = false
                autoScroll = 0f
            }
        }
    }
}

/**
 * Точка касания → отступ в координатах списка (offset элементов).
 * У перевёрнутого списка (reverseLayout, переписка — 2.2.0) отступы
 * считаются от нижнего края.
 */
private fun logical(info: androidx.compose.foundation.lazy.LazyListLayoutInfo, y: Float): Float =
    if (info.reverseLayout) info.viewportSize.height - y + info.viewportStartOffset
    else y + info.viewportStartOffset

/** Ближе этого к краю списка — подкрутка. */
private val EDGE = 56.dp

/** Подкрутка за кадр: строка (~68 dp) проходит под пальцем за несколько кадров, без пропусков. */
private val SCROLL_STEP = 12.dp

private const val FRAME_MS = 16L
