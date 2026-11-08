package com.ogneliss.dialer.ui.contactedit

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.Space
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * ОБРЕЗКА ФОТО КОНТАКТА (1.7.0).
 *
 * Квадратная рамка посередине; снимок под ней двигается пальцем
 * и приближается щипком. Рамку снимок всегда закрывает целиком:
 * пустых полос в фото не бывает. Внутри рамки — круг: так фото будет
 * видно в журнале и на экране вызова (квадратный портрет — по настройке).
 *
 * Фон чёрный в любой теме, как у редакторов фото: цвет снимка не должен
 * спорить с цветом страницы.
 */
@Composable
fun PhotoCropDialog(
    source: Bitmap,
    onDone: (Rect) -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 2.1.0: без подсказки «Сдвиньте и увеличьте снимок…» — правило автора
            // 07.11.2026, пояснений не показываем; рамка обрезки говорит сама
            Spacer(Modifier.height(Space.l))
            var crop by remember { mutableStateOf<Rect?>(null) }
            CropArea(source, Modifier.weight(1f).fillMaxWidth()) { crop = it }
            Row(
                Modifier.fillMaxWidth().padding(Space.m),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.nav_cancel), color = Color.White)
                }
                TextButton(onClick = { crop?.let(onDone) }) {
                    Text(stringResource(R.string.contact_edit_crop_done), color = Color.White)
                }
            }
        }
    }
}

/**
 * Поле обрезки. Математика в пикселях экрана: снимок нарисован
 * в масштабе [scale] с левым верхним углом в [tx], [ty]; рамка — квадрат
 * [side] посередине. Квадрат в пикселях снимка — то, что уходит в [onCrop]
 * после каждого движения.
 */
@Composable
private fun CropArea(source: Bitmap, modifier: Modifier, onCrop: (Rect) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
        val side = (minOf(w, h) - 2 * margin).coerceAtLeast(1f)
        val left0 = (w - side) / 2f
        val top0 = (h - side) / 2f
        // Наименьший масштаб — снимок закрывает рамку целиком
        val minScale = max(side / source.width, side / source.height)
        val maxScale = minScale * 6f

        var scale by remember(source, side) { mutableFloatStateOf(minScale) }
        var tx by remember(source, side) { mutableFloatStateOf(w / 2f - source.width * minScale / 2f) }
        var ty by remember(source, side) { mutableFloatStateOf(h / 2f - source.height * minScale / 2f) }

        fun clampAndReport() {
            tx = tx.coerceIn(left0 + side - source.width * scale, left0)
            ty = ty.coerceIn(top0 + side - source.height * scale, top0)
            val l = ((left0 - tx) / scale).roundToInt()
            val t = ((top0 - ty) / scale).roundToInt()
            val s = (side / scale).roundToInt()
            onCrop(Rect(l, t, l + s, t + s))
        }
        // Первая рамка — без касания: «Готово» сразу даёт снимок по центру
        LaunchedEffect(source, side) { clampAndReport() }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(source, side) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        val k = newScale / scale
                        tx = centroid.x - (centroid.x - tx) * k + pan.x
                        ty = centroid.y - (centroid.y - ty) * k + pan.y
                        scale = newScale
                        clampAndReport()
                    }
                }
        ) {
            drawImage(
                image,
                dstOffset = IntOffset(tx.roundToInt(), ty.roundToInt()),
                dstSize = IntSize(
                    (source.width * scale).roundToInt(),
                    (source.height * scale).roundToInt()
                )
            )
            // Затемнение вокруг рамки
            val frame = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(Offset(left0, top0), Size(side, side)))
            }
            clipPath(frame, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }
            drawRect(
                Color.White, topLeft = Offset(left0, top0), size = Size(side, side),
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Круг — как фото будет видно в журнале
            drawCircle(
                Color.White.copy(alpha = 0.55f),
                radius = side / 2f,
                center = Offset(left0 + side / 2f, top0 + side / 2f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
