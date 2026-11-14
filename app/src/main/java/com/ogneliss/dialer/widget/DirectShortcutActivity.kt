package com.ogneliss.dialer.widget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.ShortcutManagerCompat
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.ContactShortcuts

/**
 * «ПРЯМОЙ ВЫЗОВ» И «ПРЯМОЕ SMS» ИЗ ЛАУНЧЕРА (2.5.0).
 *
 * Форум: «не хватает ярлыков прямого вызова и SMS». Как в стандартных
 * «Контактах» Android: в списке виджетов и ярлыков главного экрана —
 * «Прямой вызов» и «Прямое SMS» (два входа в манифесте — activity-alias
 * с действием CREATE_SHORTCUT). Человек тянет пункт на экран, выбирает
 * номер в системном выборе контакта — на экране значок: касание звонит
 * нашим приложением или открывает переписку (ContactShortcuts.direct).
 *
 * Выбор номера — системный (ACTION_PICK по номерам телефонов): он отдаёт
 * ровно одну строку номера с временным доступом, своё окно выбора здесь
 * ничего бы не добавило. Нет приложения выбора — тихо отменяем (журнал):
 * лаунчер просто не поставит значок.
 */
class DirectShortcutActivity : ComponentActivity() {

    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        finishWith(result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK })
    }

    /** Вход «Прямое SMS» (alias); иначе — «Прямой вызов». */
    private val sms: Boolean
        get() = intent?.component?.className?.endsWith(SMS_ALIAS) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        runCatching { pick.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) }
            .onFailure {
                Logger.w(TAG, "Нет выбора контакта — прямой ярлык не создан", it)
                setResult(RESULT_CANCELED)
                finish()
            }
    }

    private fun finishWith(uri: Uri?) {
        val row = uri?.let { picked ->
            runCatching {
                contentResolver.query(picked, arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME, Phone.PHOTO_URI), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) Triple(c.getString(0), c.getString(1), c.getString(2)) else null }
            }.onFailure { Logger.w(TAG, "Выбранный номер не прочитался", it) }.getOrNull()
        }
        val number = row?.first
        if (number.isNullOrBlank()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val info = ContactShortcuts.direct(this, row.second.orEmpty(), number, row.third, sms)
        setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, info))
        finish()
    }

    private companion object {
        const val TAG = "DirectShortcut"
        /** Имя входа «Прямое SMS» в манифесте (activity-alias). */
        const val SMS_ALIAS = ".DirectSms"
    }
}
