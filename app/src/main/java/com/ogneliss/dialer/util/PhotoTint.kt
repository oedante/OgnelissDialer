package com.ogneliss.dialer.util

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Преобладающий цвет фото контакта — для заливки шапки меню записи
 * (1.3.5, 05.10.2026, предложение автора; настройка «Цвет шапки меню
 * из фото», по умолчанию выключена).
 *
 * Дёшево по устройству: берётся миниатюра контакта (PHOTO_THUMBNAIL_URI,
 * ~96 px), декодируется с уменьшением до ≤ 48 px — около 2 тыс. пикселей;
 * вызывается вне главного потока, один раз на открытие меню.
 *
 * Как выбирается цвет: пиксели раскладываются по корзинам 4 × 4 × 4 бита;
 * серые, почти белые и почти чёрные пропускаются — иначе на портретах
 * побеждали бы фон и тени; ответ — средний цвет самой частой корзины.
 * Цветного нет (ч/б фото) — null, шапка остаётся обычной.
 */
object PhotoTint {

    private const val TARGET_PX = 48

    fun dominantColor(resolver: ContentResolver, uri: String): Int? = runCatching {
        val parsed = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxSide <= 0) return@runCatching null
        var sample = 1
        while (maxSide / (sample * 2) >= TARGET_PX) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@runCatching null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        dominantOf(pixels)
    }.getOrNull()

    /** Чистая часть — без Android, проверяется модульным тестом. */
    fun dominantOf(pixels: IntArray): Int? {
        val count = IntArray(4096)
        val sumR = LongArray(4096)
        val sumG = LongArray(4096)
        val sumB = LongArray(4096)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue // прозрачные
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            // Яркость и насыщенность по HSV: пропускаем тёмное, белое и серое
            if (max < 40 || (max > 240 && min > 225)) continue
            if (max - min < max * 0.18f) continue
            val bin = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            count[bin]++
            sumR[bin] += r.toLong(); sumG[bin] += g.toLong(); sumB[bin] += b.toLong()
        }
        var best = -1
        for (i in count.indices) if (count[i] > 0 && (best < 0 || count[i] > count[best])) best = i
        if (best < 0) return null
        val n = count[best]
        val r = (sumR[best] / n).toInt()
        val g = (sumG[best] / n).toInt()
        val b = (sumB[best] / n).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
