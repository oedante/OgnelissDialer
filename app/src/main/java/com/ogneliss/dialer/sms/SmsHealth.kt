package com.ogneliss.dialer.sms

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.AutoStart

/**
 * ДОХОДЯТ ЛИ SMS (2.1.0) — то, без чего роль SMS у нас есть, а сообщения
 * человек не увидит.
 *
 * Правило автора (07.11.2026): «Не показывать пользователю ошибки
 * и объяснения — выполнять или пропускать действия. Пользователю нужно
 * знать только о критичных ошибках, где требуется его действие».
 * Здесь как раз такие — исправить их может только человек:
 *
 * • УВЕДОМЛЕНИЯ ВЫКЛЮЧЕНЫ — приложению целиком (или нет разрешения
 *   на Android 13+) или каналу «Сообщения» (важность «нет»). SMS
 *   принимаются и записываются, но приходят молча — человек о них
 *   не узнает.
 * • АВТОЗАПУСК ВЫКЛЮЧЕН на Xiaomi / HyperOS — закрытое приложение
 *   не получает SMS. MIUI отдаёт состояние кодом AppOps прошивки 10008
 *   (тем же способом, что «Экран блокировки» 10020 и «Всплывающие окна
 *   в фоне» 10021 в util/IncomingCallAccess.kt).
 *
 * Обе беды — полосой сверху списка SMS с кнопкой туда, где исправить
 * ([problem], [fix]); для журнала — тот же [problem] (интегратору).
 *
 * На других прошивках с закрытым автозапуском (Huawei, Honor, OPPO,
 * realme, OnePlus, vivo, Tecno, Infinix) состояние наружу не отдаётся —
 * там полоса «разрешите автозапуск» один раз после выдачи роли, со
 * значком закрыть ([autostartHintNeeded]). Было (2.0.0): одна и та же
 * подсказка в переписке на всех таких прошивках, Xiaomi включительно,
 * даже когда автозапуск уже разрешён.
 */
object SmsHealth {

    private const val TAG = "SmsHealth"

    /** Код AppOps MIUI «Автозапуск» — не из SDK, из прошивки. */
    private const val MIUI_OP_AUTOSTART = 10008

    enum class Problem { NOTIFICATIONS_OFF, AUTOSTART_OFF }

    private val isMiui: Boolean by lazy {
        val maker = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        listOf("xiaomi", "redmi", "poco").any { it in maker }
    }

    /**
     * Прошивки, где автозапуск для сторонних закрыт, а проверить его нельзя.
     * Samsung, Meizu и другие из AutoStart здесь не названы: у Samsung
     * «спящие приложения» — не автозапуск, а приложение SMS по умолчанию
     * система будит сама.
     */
    private val autostartOem: Boolean by lazy {
        val maker = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        listOf("huawei", "honor", "oppo", "realme", "oneplus", "vivo", "iqoo", "tecno", "infinix", "itel")
            .any { it in maker }
    }

    /**
     * Уведомления о SMS не покажутся: выключены приложению (Android 13+ —
     * и без разрешения) или каналу «Сообщения».
     */
    fun notificationsBlocked(context: Context): Boolean = runCatching {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return@runCatching true
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(MessageNotifications.CHANNEL)
        channel?.importance == NotificationManager.IMPORTANCE_NONE
    }.getOrDefault(false)

    /**
     * Автозапуск MIUI/HyperOS: true — разрешён, false — запрещён, null —
     * не MIUI или прошивка не ответила (метод скрыт, ответ незнакомый):
     * ответ не выдумываем.
     */
    fun miuiAutostart(context: Context): Boolean? {
        if (!isMiui) return null
        return runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return@runCatching null
            val check = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            )
            when (check.invoke(ops, MIUI_OP_AUTOSTART, Process.myUid(), context.packageName)) {
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_IGNORED -> false
                else -> null
            }
        }.getOrNull()
    }

    /** Критичная беда приёма SMS, пока роль у нас; null — всё в порядке или роли нет. */
    fun problem(context: Context): Problem? {
        if (!SmsRole.isDefault(context)) return null
        return when {
            notificationsBlocked(context) -> Problem.NOTIFICATIONS_OFF
            miuiAutostart(context) == false -> Problem.AUTOSTART_OFF
            else -> null
        }
    }

    /**
     * Полоса «разрешите автозапуск» — один раз, пока роль у нас, на прошивках,
     * где автозапуск не проверить (и на MIUI, если прошивка не ответила).
     */
    fun autostartHintNeeded(context: Context): Boolean =
        SmsRole.isDefault(context) &&
            (autostartOem || (isMiui && miuiAutostart(context) == null)) &&
            !SmsSettings.autostartHintDone(context)

    /** Открыть экран, где беду исправляют. */
    fun fix(context: Context, problem: Problem) {
        when (problem) {
            Problem.NOTIFICATIONS_OFF -> openNotificationSettings(context)
            Problem.AUTOSTART_OFF -> AutoStart.open(context)
        }
        Logger.i(TAG, "Открыт экран исправления: ${problem.name}")
    }

    /**
     * Роль SMS только что выдана (MainActivity, rememberSmsRoleRequest): канал
     * «Сообщения» создаётся сразу — он виден в настройках уведомлений
     * и проверяется [notificationsBlocked] ещё до первого SMS.
     */
    fun onRoleGranted(context: Context) {
        runCatching { MessageNotifications.ensureChannel(context) }
            .onFailure { Logger.w(TAG, "Канал сообщений не создан", it) }
        Logger.i(TAG, "Роль SMS выдана; ${describe(context)}")
    }

    /** Для журнала разработчика. */
    fun describe(context: Context): String =
        "уведомления ${if (notificationsBlocked(context)) "ВЫКЛЮЧЕНЫ" else "да"}, " +
            "автозапуск MIUI ${miuiAutostart(context) ?: "—"}"

    /** Приложению выключены все уведомления — его экран; иначе экран канала «Сообщения». */
    private fun openNotificationSettings(context: Context) {
        val appOff = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        val candidates = listOfNotNull(
            if (appOff) null else Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, MessageNotifications.CHANNEL),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
        for (intent in candidates) {
            val opened = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) return@runCatching false
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (opened) return
        }
        Logger.w(TAG, "Экран уведомлений не открылся")
    }
}
