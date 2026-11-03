package com.ogneliss.dialer.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.ui.theme.PhotoPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * УМЕНЬШЕННЫЙ СНИМОК ДЛЯ ЦВЕТОВ (1.8.4).
 *
 * Цвета снимка (ui/theme/PhotoPalette.kt) считаются по копии не больше
 * [PALETTE_PX] точек по большей стороне: подложка имени на обложке
 * карточки и тоны живого фона экрана вызова. Снимок грузит тот же Coil,
 * что показывает фото, — любые адреса фото (контакты, файлы) читаются
 * одинаково, а повторное открытие берёт копию из памяти.
 *
 * Не удалось прочитать — null: обложка остаётся с нейтральным тоном,
 * фон — в цветах темы. Пока снимок читается, то же самое.
 */
@Immutable
internal class PhotoPixels(val width: Int, val height: Int, val argb: IntArray)

internal suspend fun loadPhotoPixels(context: Context, uri: String): PhotoPixels? {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(PALETTE_PX)
        // Точки читаются из памяти — аппаратная картинка этого не позволяет
        .allowHardware(false)
        // Свой ключ: в памяти не путается с крупным фото того же адреса
        .memoryCacheKey("palette:$uri")
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    return withContext(Dispatchers.Default) {
        val source = result.drawable.toBitmap()
        val k = PALETTE_PX.toFloat() / max(source.width, source.height)
        val bitmap = if (k < 1f) {
            Bitmap.createScaledBitmap(
                source,
                max(1, (source.width * k).roundToInt()),
                max(1, (source.height * k).roundToInt()),
                true
            )
        } else {
            source
        }
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        PhotoPixels(bitmap.width, bitmap.height, px)
    }
}

/** Уменьшенный снимок [uri]; null — фото нет, оно ещё читается или не читается. */
@Composable
internal fun rememberPhotoPixels(uri: String?): PhotoPixels? {
    val context = LocalContext.current
    val pixels by produceState<PhotoPixels?>(initialValue = null, uri) {
        value = uri?.takeIf { it.isNotBlank() }?.let { u ->
            suspendRunCatching { loadPhotoPixels(context, u) }.getOrNull()
        }
    }
    return pixels
}

/**
 * Главные цвета снимка [uri] (PhotoPalette.swatches) — для живого фона
 * экрана вызова. Считаются не в главном потоке; null — фото нет, оно ещё
 * читается или в нём нет ни одной непрозрачной точки.
 */
@Composable
internal fun rememberPhotoSwatches(uri: String?): List<PhotoPalette.Swatch>? {
    val context = LocalContext.current
    val swatches by produceState<List<PhotoPalette.Swatch>?>(initialValue = null, uri) {
        value = uri?.takeIf { it.isNotBlank() }?.let { u ->
            suspendRunCatching {
                loadPhotoPixels(context, u)?.let { px ->
                    withContext(Dispatchers.Default) { PhotoPalette.swatches(px.argb) }
                }
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
    }
    return swatches
}

/** Сторона уменьшенной копии: цветам хватает, k-средних — доли миллисекунды. */
private const val PALETTE_PX = 48
