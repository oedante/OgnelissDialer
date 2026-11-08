package com.ogneliss.dialer.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard
import java.util.concurrent.Executors

/**
 * ФОТО И ТИП НОМЕРА ЗВОНЯЩЕГО — ДЛЯ УВЕДОМЛЕНИЯ ВЫЗОВА (2.1.0).
 *
 * Автор, 08.11.2026, по вопросу пользователя («а такое — плашка сверху
 * с фото и „мобильный“ — вы не делали?»): плашка входящего — с фото
 * контакта и типом номера, как в стандартных звонилках. Было: в уведомлении
 * значок-силуэт и «Входящий вызов».
 *
 * ── Почему не сразу в уведомлении ──────────────────────────────────────────
 * Запрос к книге и разбор снимка — десятки миллисекунд, а при холодном
 * провайдере контактов и больше. Звонок ждать не должен: уведомление
 * ставится сразу, без фото, а когда фото готово — обновляется тем же
 * номером уведомления (без нового звука и всплытия: setOnlyAlertOnce).
 * Повторные показы того же звонка берут готовое из памяти.
 */
object CallerCard {

    /** Фото (миниатюра книги) и подпись номера («Мобильный»); любое может отсутствовать. */
    data class Info(val photo: Bitmap?, val typeLabel: String?)

    private const val TAG = "CallerCard"
    /**
     * Сторона снимка. Книга хранит миниатюры 96 px, а уведомление рисует
     * звонящего кругом в 40–48 dp; больше не нужно — снимок едет в уведомление
     * трижды (значок, звонящий CallStyle, список людей).
     */
    private const val MAX_SIDE_PX = 128
    private const val CACHE_SIZE = 6

    private val cache = object : LinkedHashMap<String, Info>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Info>?) = size > CACHE_SIZE
    }
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun key(number: String) = number.filter { it.isDigit() || it == '+' }

    /** Уже загруженное для этого номера; null — ещё не загружали. */
    fun cached(number: String?): Info? {
        if (number.isNullOrBlank()) return null
        return synchronized(cache) { cache[key(number)] }
    }

    /**
     * Загрузить в фоне; [onLoaded] — в главном потоке и только если нашлось
     * что показать (фото или тип номера). Без разрешения на контакты — ничего.
     */
    fun load(context: Context, number: String?, onLoaded: (Info) -> Unit) {
        if (number.isNullOrBlank() || cached(number) != null) return
        if (!PermissionGuard.canReadContacts(context)) return
        val app = context.applicationContext
        io.execute {
            val info = runCatching { query(app, number) }
                .onFailure { Logger.w(TAG, "Фото звонящего не загрузилось", it) }
                .getOrNull() ?: Info(null, null)
            synchronized(cache) { cache[key(number)] = info }
            if (info.photo != null || info.typeLabel != null) main.post { onLoaded(info) }
        }
    }

    private fun query(context: Context, number: String): Info? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.TYPE,
            ContactsContract.PhoneLookup.LABEL
        )
        return context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val photoUri = c.getString(0) ?: c.getString(1)
            val label = if (c.isNull(2)) null else ContactsContract.CommonDataKinds.Phone
                .getTypeLabel(context.resources, c.getInt(2), c.getString(3))
                ?.toString()?.takeIf { it.isNotBlank() }
            Info(photoUri?.let { decode(context, Uri.parse(it)) }, label)
        }
    }

    /** Снимок не больше [MAX_SIDE_PX] по стороне: уведомлению больше не нужно, а память — не лишняя. */
    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        while (side / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        // Прореживание — только степенями двойки: остаток — точным уменьшением
        val big = maxOf(raw.width, raw.height)
        if (big <= MAX_SIDE_PX) return@runCatching raw
        val k = MAX_SIDE_PX.toFloat() / big
        Bitmap.createScaledBitmap(
            raw,
            (raw.width * k).toInt().coerceAtLeast(1),
            (raw.height * k).toInt().coerceAtLeast(1),
            true
        ).also { if (it !== raw) raw.recycle() }
    }.getOrNull()
}
