package com.ogneliss.dialer.sms

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.ogneliss.dialer.util.PermissionGuard

/**
 * SMS В ДИАЛЕРЕ (1.10.0) — ЧТО МОЖНО И КОГДА.
 *
 * Автор, 05.11.2026: «добавить SMS — редактирование и отправку…
 * ориентировать на журнал звонков и журнал SMS с поддержкой свайпов»;
 * выбрано: «по выбору человека», SMS «в одном журнале с вызовами», MMS —
 * «приём и отправка».
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ДВА РЕЖИМА — ПРАВИЛАМИ GOOGLE PLAY
 * ══════════════════════════════════════════════════════════════════════════
 * Политика «SMS and Call Log permissions» (support.google.com/googleplay/
 * android-developer/answer/10208820): приложению для звонков
 * по умолчанию разрешено SEND_SMS, а читать SMS (READ_SMS) — только
 * приложению SMS по умолчанию. Поэтому:
 *
 * • ОТПРАВКА — всегда: диалер по умолчанию просит SEND_SMS при первой
 *   отправке. Отправленное система сама записывает в общее хранилище
 *   SMS — оно видно и в приложении сообщений человека.
 * • ЖУРНАЛ, ПЕРЕПИСКИ, ПРИЁМ, MMS — только когда человек сам сделал диалер
 *   приложением SMS по умолчанию ([request]). Тогда приложение обязано
 *   само принимать SMS и MMS и записывать их в хранилище (SmsDeliverReceiver,
 *   MmsReceivers) — иначе сообщения потеряются. Перестал быть приложением
 *   SMS по умолчанию — чтение прекращается сразу ([canRead] проверяется
 *   при каждом чтении), как требует политика.
 *
 * Минус роли — чаты RCS в Google Messages: они работают только там.
 * Об этом — в пояснении настройки.
 */
object SmsRole {

    /** Код запроса роли из настроек (у роли телефона — 4201). */
    const val REQUEST_CODE = 4202

    /** Диалер — приложение SMS по умолчанию. */
    fun isDefault(context: Context): Boolean =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName }
            .getOrDefault(false)

    /** Можно читать переписку: роль и разрешение (роль выдаёт его сама). */
    fun canRead(context: Context): Boolean =
        isDefault(context) && PermissionGuard.has(context, Manifest.permission.READ_SMS)

    /** Можно отправлять SMS из приложения. */
    fun canSend(context: Context): Boolean =
        PermissionGuard.has(context, Manifest.permission.SEND_SMS)

    /**
     * Намерение «сделать приложением SMS по умолчанию»; null — роль уже
     * у нас или на телефоне её нет (планшет без SIM).
     */
    fun request(context: Context): Intent? {
        if (isDefault(context)) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java) ?: return null
            if (!rm.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }
}

/**
 * Настройки SMS — копия для мест без корутин (1.10.0).
 *
 * Приёмники SMS, сервис «ответить сообщением» и кнопки «Написать»
 * по всему приложению решают сразу, без чтения DataStore. Prefs при каждой
 * записи этих настроек повторяет их здесь (Prefs.setSms…).
 */
object SmsSettings {
    private const val FILE = "sms_settings"
    const val KEY_IN_APP = "sms_in_app"
    const val KEY_IN_JOURNAL = "sms_in_journal"
    const val KEY_DELIVERY_REPORTS = "sms_delivery_reports"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Писать SMS в диалере, а не в приложении сообщений (по умолчанию — да). */
    fun inApp(context: Context): Boolean = prefs(context).getBoolean(KEY_IN_APP, true)

    /** Показывать SMS в журнале (когда диалер — приложение SMS по умолчанию). */
    fun inJournal(context: Context): Boolean = prefs(context).getBoolean(KEY_IN_JOURNAL, true)

    /** Просить отчёт о доставке (по умолчанию — нет: часть операторов берёт плату). */
    fun deliveryReports(context: Context): Boolean = prefs(context).getBoolean(KEY_DELIVERY_REPORTS, false)

    fun put(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    /**
     * SMS открываются в диалере: он — приложение SMS по умолчанию, или он —
     * телефон по умолчанию и так выбрано в настройках. Отправлять SMS
     * без одной из двух ролей правила Google Play не разрешают — тогда
     * «Написать» открывает приложение сообщений, как раньше.
     */
    fun composeHere(context: Context): Boolean =
        SmsRole.isDefault(context) ||
            (inApp(context) && com.ogneliss.dialer.util.DefaultDialer.isDefault(context))
}
