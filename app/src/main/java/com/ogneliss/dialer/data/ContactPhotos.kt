package com.ogneliss.dialer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ФОТО КОНТАКТА ДЛЯ СВОЕГО РЕДАКТОРА (1.7.0).
 *
 * Снимок из галереи бывает 50 мегапикселей и повёрнут меткой EXIF, а не
 * пикселями: камера пишет «повернуть на 90°» и не поворачивает. Поэтому:
 *   1. [load] читает с уменьшением (inSampleSize) до [MAX_SIDE] по большей
 *      стороне и сразу поворачивает по EXIF — дальше работаем с тем, что
 *      человек видит в галерее;
 *   2. [crop] вырезает квадрат, выбранный на экране обрезки, и сводит его
 *      к [OUTPUT_SIDE] — столько хранит книга для экрана вызова;
 *   3. JPEG с качеством 90: книга всё равно пережмёт миниатюру сама.
 *
 * Разрешений не нужно: снимок выбирается системным окном выбора фото,
 * доступ — только к выбранному.
 */
object ContactPhotos {

    /** Сторона снимка для экрана обрезки — с запасом под приближение. */
    const val MAX_SIDE = 2048

    /** Сторона сохраняемого фото: столько книга отдаёт как «большое». */
    const val OUTPUT_SIDE = 720

    /** Снимок из [uri], уменьшенный и повёрнутый по EXIF; null — не прочитался. */
    fun load(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null

        val degrees = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scaled = fitInside(decoded, MAX_SIDE)
        if (degrees == 0f) scaled else {
            val rotated = Bitmap.createBitmap(
                scaled, 0, 0, scaled.width, scaled.height, Matrix().apply { postRotate(degrees) }, true
            )
            if (rotated !== scaled) scaled.recycle()
            rotated
        }
    }.getOrNull()

    /**
     * Квадрат [square] (в пикселях [source]) → JPEG [OUTPUT_SIDE]×[OUTPUT_SIDE].
     * Квадрат подрезается до границ снимка: экран обрезки так и не даёт
     * выйти за край, но округление может дать лишний пиксель.
     */
    fun crop(source: Bitmap, square: Rect): ByteArray {
        val side = min(min(square.width(), square.height()), min(source.width, source.height)).coerceAtLeast(1)
        val left = square.left.coerceIn(0, source.width - side)
        val top = square.top.coerceIn(0, source.height - side)
        val piece = Bitmap.createBitmap(source, left, top, side, side)
        val out = if (side == OUTPUT_SIDE) piece else Bitmap.createScaledBitmap(piece, OUTPUT_SIDE, OUTPUT_SIDE, true)
        return jpeg(out).also {
            if (out !== piece) out.recycle()
            if (piece !== source) piece.recycle()
        }
    }

    /** Уменьшенная копия JPEG — для запасного пути записи (строка Photo). */
    fun downscale(jpeg: ByteArray, side: Int): ByteArray? = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return@runCatching null
        val small = Bitmap.createScaledBitmap(bitmap, side, side, true)
        jpeg(small).also { if (small !== bitmap) small.recycle(); bitmap.recycle() }
    }.getOrNull()

    private fun jpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }

    private fun fitInside(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val k = maxSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * k).roundToInt(), (bitmap.height * k).roundToInt(), true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
