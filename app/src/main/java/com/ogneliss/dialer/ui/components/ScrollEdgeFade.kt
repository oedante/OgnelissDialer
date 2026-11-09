package com.ogneliss.dialer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * ТЕНЬ У КРАЯ ПРОКРУТКИ (2.1.2, дизайн-система Д7 — как в Ogneliss Mail).
 *
 * Было: у края списка строки просто обрезались — не видно, что выше или
 * ниже есть ещё. Стало, как в почте (scroll_edge_fade.dart): у края, за
 * которым есть содержимое, — мягкая тень 8 dp; появляется и гаснет за
 * 150 мс. Маленьким областям (ниже 120 dp) не нужна.
 */
fun Modifier.scrollEdgeFade(state: LazyListState): Modifier = composed {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val shade = Color.Black.copy(alpha = if (dark) 0.32f else 0.10f)
    val before by remember(state) { derivedStateOf { state.canScrollBackward } }
    val after by remember(state) { derivedStateOf { state.canScrollForward } }
    val topAlpha by animateFloatAsState(if (before) 1f else 0f, tween(FADE_MS), label = "edgeTop")
    val bottomAlpha by animateFloatAsState(if (after) 1f else 0f, tween(FADE_MS), label = "edgeBottom")
    drawWithContent {
        drawContent()
        if (size.height < MIN_HEIGHT.toPx()) return@drawWithContent
        val h = EDGE.toPx()
        if (topAlpha > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(shade, Color.Transparent), startY = 0f, endY = h),
                size = Size(size.width, h),
                alpha = topAlpha
            )
        }
        if (bottomAlpha > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, shade), startY = size.height - h, endY = size.height),
                topLeft = Offset(0f, size.height - h),
                size = Size(size.width, h),
                alpha = bottomAlpha
            )
        }
    }
}

private val EDGE = 8.dp
private val MIN_HEIGHT = 120.dp
private const val FADE_MS = 150
