package com.ogneliss.dialer.util

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import com.ogneliss.dialer.log.Logger

/**
 * ВЫБОР ПЕРСОНАЛЬНОЙ МЕЛОДИИ КОНТАКТА.
 *
 * ── Почему через системный выбор, а не свой список ─────────────────────────
 * Системный диалог показывает всё сразу: встроенные мелодии, звуки
 * производителя, файлы пользователя из папки Ringtones и то, что добавили
 * другие приложения. Свой список пришлось бы читать из MediaStore,
 * что требует разрешения на доступ к аудиофайлам — ради задачи,
 * которую система решает без единого разрешения.
 *
 * ── Как это работает вместе с системным звонком ────────────────────────────
 * Выбранная мелодия записывается в колонку CUSTOM_RINGTONE контакта.
 * Дальше её проигрывает сама телефония при входящем вызове — нам ничего
 * делать не нужно. Именно поэтому мы и отказались от собственного
 * проигрывателя: система уже умеет всё, включая персональные мелодии.
 *
 * Побочная польза: мелодия видна и работает в штатном приложении
 * «Телефон» тоже. Пользователь не привязан к нам своей настройкой.
 */
object RingtonePicker {

    private const val TAG = "RingtonePicker"

    /**
     * Intent системного выбора мелодии.
     *
     * @param current текущая мелодия контакта, чтобы отметить её в списке
     * @param title заголовок диалога
     */
    fun createIntent(current: Uri?, title: String): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            // Показать пункт «По умолчанию» — сброс к общей мелодии
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            // «Без звука» тоже нужен: беззвучный контакт — рабочий сценарий
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        }

    /**
     * Извлекает выбранную мелодию из результата.
     * @return URI мелодии; null означает «по умолчанию» или «без звука»
     */
    fun extractResult(data: Intent?): Uri? {
        // IntentCompat: типизированный getParcelableExtra появился
        // только в API 33, а старый объявлен устаревшим. Совместимая
        // обёртка сама выбирает нужный путь
        val uri = data?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(
                it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
            )
        }
        Logger.d(TAG, "Выбрана мелодия: ${if (uri != null) "своя" else "по умолчанию"}")
        return uri
    }

    /** Название мелодии для показа в карточке контакта. */
    fun titleFor(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrElse {
            Logger.w(TAG, "Не удалось прочитать название мелодии", it)
            null
        }
    }
}
