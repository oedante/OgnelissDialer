package com.ogneliss.dialer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space

/**
 * ФОТО НА ВЕСЬ ЭКРАН (1.8.4, как в Ogneliss Mail 1.6.49 — «как фото
 * профиля в Telegram»).
 *
 * Нажатие на обложку карточки контакта открывает снимок целиком: на обложке
 * он обрезан до полосы 3 : 2 и уходит под строку заголовка. Пальцами —
 * приблизить до ×6 (вокруг точки между пальцами, 2.0.2), двойное касание — ×2,5 и обратно, касание или «Назад» —
 * закрыть. Фон — чёрный 92 %, появление — растворением с подъёмом
 * от 92 % размера за 200 мс, как в почте.
 */
@Composable
internal fun PhotoViewerDialog(
    uri: String,
    title: String,
    onDismiss: () -> Unit
) {
    // Окно во весь экран, под системными панелями: снимку нужна вся площадь,
    // а «Назад» закрывает его, как любое окно
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Системное «Убрать анимацию» Compose учитывает сам: появление — сразу
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, tween(Motion.QUICK_MS)) }
        // Приближение и сдвиг — состояние окна: закрыли и открыли снова —
        // снимок опять целиком
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        // Размер области снимка: graphicsLayer приближает вокруг её середины,
        // а пальцы могут стоять где угодно
        var area by remember { mutableStateOf(IntSize.Zero) }
        val transform = rememberTransformableState { centroid, zoom, pan, _ ->
            val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
            // Без приближения снимок стоит по центру: сдвигать нечего
            offset = if (next <= 1f) Offset.Zero else {
                // Точка снимка между пальцами остаётся под пальцами: приближение
                // идёт вокруг них, а не вокруг середины экрана
                val fromCenter = centroid - Offset(area.width / 2f, area.height / 2f)
                fromCenter + pan - (fromCenter - offset) * (next / scale)
            }
            scale = next
        }
        /*
         * Касания — у подложки, а не у снимка: касание мимо снимка (по чёрным
         * полям) тоже закрывает, как в почте. Сведение пальцами снимок
         * забирает себе (transformable), и касанием оно не считается.
         */
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value }
                .background(Color.Black.copy(alpha = BACKDROP_ALPHA))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                            offset = Offset.Zero
                        }
                    )
                }
        ) {
            // Снимок целиком (Fit), в пределах безопасной области: вырез
            // камеры и панель навигации его не закрывают
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.a11y_contact_photo, title),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .onSizeChanged { area = it }
                    .transformable(transform)
                    .graphicsLayer {
                        val lift = APPEAR_FROM + (1f - APPEAR_FROM) * appear.value
                        scaleX = scale * lift
                        scaleY = scale * lift
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
            // Сверху — «Закрыть» и имя: белым, фон под ними всегда тёмный
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = Space.xs, end = Space.l, top = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(OgnelissIcons.Close, contentDescription = stringResource(R.string.nav_close), tint = Color.White)
                }
                Spacer(Modifier.width(Space.xs))
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Приближение пальцами — до ×6, как в почте. */
private const val MAX_ZOOM = 6f
/** Двойное касание — сразу ×2,5: лицо крупно, без сведения пальцами. */
private const val DOUBLE_TAP_ZOOM = 2.5f
/** Подложка — чёрный 92 %: экран под ней угадывается, но не мешает. */
private const val BACKDROP_ALPHA = 0.92f
/** Снимок появляется с 92 % размера. */
private const val APPEAR_FROM = 0.92f
