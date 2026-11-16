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
        OUTGOING("outgoing.jpg")
    }

    private const val DIR = "call_backgrounds"
    private const val TAG = "CallBackgrounds"

    /** Качество JPEG — как у фото контактов (ContactPhotos). */
    private const val JPEG_QUALITY = 90

    /** Путь к файлу снимка; сам файл может и не существовать. */
    fun file(context: Context, kind: Kind): File = File(File(context.filesDir, DIR), kind.fileName)

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
}
