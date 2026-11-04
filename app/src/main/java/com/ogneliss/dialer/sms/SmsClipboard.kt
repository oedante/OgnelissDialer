package com.ogneliss.dialer.sms

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.ogneliss.dialer.R
import com.ogneliss.dialer.log.Logger

/**
 * КОПИРОВАНИЕ ИЗ SMS В БУФЕР ОБМЕНА (2.0.0) — только на устройстве.
 *
 * • Код подтверждения кладётся с пометкой «секретно»
 *   (ClipDescription.EXTRA_IS_SENSITIVE, Android 13+): система не покажет
 *   его во всплывающем превью буфера, а клавиатуры — в истории. Вставить
 *   его можно как обычно: клавиатура предлагает свежее содержимое буфера
 *   одним касанием.
 * • На Android 13+ система сама показывает «Скопировано» — своё сообщение
 *   только до 13, иначе было бы два.
 * • Текст и номер — без пометки: это не секрет, и человеку полезно видеть,
 *   что именно скопировалось.
 */
object SmsClipboard {

    private const val TAG = "SmsClip"

    enum class Kind(val toastRes: Int) {
        CODE(R.string.sms_copied_code),
        TEXT(R.string.sms_copied_text),
        NUMBER(R.string.sms_copied_number)
    }

    fun copy(context: Context, value: String, kind: Kind) {
        runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
            val clip = ClipData.newPlainText(context.getString(kind.toastRes), value)
            if (kind == Kind.CODE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, kind.toastRes, Toast.LENGTH_SHORT).show()
            }
            // Само содержимое в журнал не пишется — только что скопировано
            Logger.i(TAG, "Скопировано из SMS: ${kind.name.lowercase()}")
        }.onFailure { Logger.w(TAG, "Не удалось скопировать", it) }
    }
}
