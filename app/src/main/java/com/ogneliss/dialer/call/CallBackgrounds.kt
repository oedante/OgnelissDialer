package com.ogneliss.dialer.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ogneliss.dialer.data.ContactPhotos
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * СВОЙ ФОН ЭКРАНА ВЫЗОВА (2.7.4, решение владельца: «нужно не заменять фото,
 * а именно изменить фон — вместо живого фона выводить изображение,
 * которое добавил пользователь; свой фон только для входящих и исходящих»).
 *
 * Два снимка — для входящих и для исходящих вызовов. Хранятся файлами
 * в filesDir/call_backgrounds/ (JPEG), а не в настройках: DataStore не
 * для картинок. В настройках лежит только «отметка» — время сохранения
 * (Prefs.callBgIncoming / callBgOutgoing): 0 — своего снимка нет, новое
 * значение заставляет открытый экран вызова перечитать файл.
 *
 * Разрешений не нужно: снимок выбирается системным окном выбора фото.
 * Ошибки чтения и записи — только в журнал, человеку ничего не показывается
 * (правило владельца 08.11.2026): экран вызова остаётся с обычным фоном.
 */
object CallBackgrounds {

    enum class Kind(val fileName: String) {
        INCOMING("incoming.jpg"),
        OUTGOING("outgoing.jpg"),

        /*
         * 2.8.0: картинки вместо фото (форум, iKovalsky: «как в TruePhone общие
         * картинки для неизвестных контактов и контактов без фото»). Лежат
         * здесь же: то же сохранение, тот же перенос «телефон на телефон»
         * (папка в data_extraction_rules.xml) и та же уборка при сбросе настроек.
         */
        AVATAR_NO_PHOTO("avatar_no_photo.jpg"),
        AVATAR_UNKNOWN("avatar_unknown.jpg")
    }

    private const val DIR = "call_backgrounds"
    private const val TAG = "CallBackgrounds"

    /** Качество JPEG — как у фото контактов (ContactPhotos). */
    private const val JPEG_QUALITY = 90

    /** Путь к файлу снимка; сам файл может и не существовать. */
    fun file(context: Context, kind: Kind): File = File(File(context.filesDir, DIR), kind.fileName)

    /**
     * Адрес снимка для показа (2.8.0, картинки вместо фото); [mark] 0 — снимка нет.
     * Отметка — в адресе: заменили снимок — у адреса новое значение, и загрузчик
     * картинок не покажет прежний из памяти.
     */
    fun uri(context: Context, kind: Kind, mark: Long): String? =
        if (mark <= 0L) null else android.net.Uri.fromFile(file(context, kind)).buildUpon()
            .appendQueryParameter("v", mark.toString()).build().toString()

    /**
     * Сохранить снимок из [uri] поверх прежнего. true — записан.
     *
     * Читает ContactPhotos.load (поворот по EXIF, уменьшение до 2048 px),
     * пишет во временный файл и переименовывает поверх: оборванная запись
     * не оставит ни обрезанный снимок, ни пропавший прежний.
     */
    suspend fun save(context: Context, kind: Kind, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        var tmp: File? = null
        try {
            val bitmap = ContactPhotos.load(context, uri)
            if (bitmap == null) {
                Logger.w(TAG, "Снимок для фона ${kind.name} не прочитан")
                return@withContext false
            }
            val target = file(context, kind)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, kind.fileName + ".tmp")
            tmp = temp
            temp.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    error("compress=false")
                }
            }
            bitmap.recycle()
            if (!temp.renameTo(target)) {
                // Бывает на файловых системах, где rename не заменяет файл
                target.delete()
                if (!temp.renameTo(target)) error("rename=false")
            }
            tmp = null
            true
        } catch (e: Throwable) {
            Logger.w(TAG, "Снимок для фона ${kind.name} не сохранён", e)
            false
        } finally {
            tmp?.delete()
        }
    }

    /** Удалить снимок (и недописанный временный файл). */
    suspend fun delete(context: Context, kind: Kind) = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(context, kind)
            File(target.parentFile, kind.fileName + ".tmp").delete()
            if (target.exists() && !target.delete()) Logger.w(TAG, "Снимок ${kind.name} не удалился")
        }.onFailure { Logger.w(TAG, "Снимок ${kind.name} не удалён", it) }
        Unit
    }

    /** Без корутин — для сброса всех настроек (Prefs.resetAll уже на IO). */
    fun deleteAllBlocking(context: Context) {
        Kind.entries.forEach { kind ->
            runCatching { file(context, kind).delete() }
                .onFailure { Logger.w(TAG, "Снимок ${kind.name} не удалён", it) }
        }
    }

    /**
     * Снимок для показа; null — файла нет или он не читается (тогда экран
     * остаётся с обычным фоном). Если больше 2048 px — читается с уменьшением,
     * хотя сохранённый уже не больше: файл мог попасть с другого телефона.
     */
    suspend fun load(context: Context, kind: Kind): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(context, kind)
            if (!f.isFile) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ContactPhotos.MAX_SIDE) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.onFailure { Logger.w(TAG, "Снимок ${kind.name} не прочитан", it) }
            .getOrNull()
    }

    /**
     * Яркость снимка под именем и номером (2.7.6, CallBackgroundLayer.bandLuminance) —
     * по копии 24×48: сотни точек достаточно для среднего. Не вышло — 0 (тёмный
     * снимок → белый текст, как было).
     */
    suspend fun textBandLuminance(bitmap: Bitmap): Pair<Double, Double> = withContext(Dispatchers.Default) {
        runCatching {
            val small = Bitmap.createScaledBitmap(bitmap, BAND_W, BAND_H, true)
            val px = IntArray(BAND_W * BAND_H)
            small.getPixels(px, 0, BAND_W, 0, 0, BAND_W, BAND_H)
            if (small !== bitmap) small.recycle()
            // Полоса имени и номера; полоса кнопок разговора (2.8.1)
            CallBackgroundLayer.bandLuminance(px, BAND_W, BAND_H) to
                CallBackgroundLayer.bandLuminance(
                    px, BAND_W, BAND_H,
                    CallBackgroundLayer.CONTROLS_BAND_FROM, CallBackgroundLayer.CONTROLS_BAND_TO
                )
        }.onFailure { Logger.w(TAG, "Яркость снимка не посчитана", it) }
            .getOrDefault(0.0 to 0.0)
    }

    private const val BAND_W = 24
    private const val BAND_H = 48
}
