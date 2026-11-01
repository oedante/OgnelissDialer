package com.ogneliss.dialer.util

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ЭКРАН ВХОДЯЩЕГО НА ПРОШИВКАХ ПРОИЗВОДИТЕЛЕЙ (1.8.1).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Чистый Android разрешает диалеру по умолчанию открыть экран вызова
 * из фона: InCallService, к которому подключилась система, — исключение
 * из запрета на запуск окон в фоне. Но поверх этого у производителей свои
 * запреты, и по умолчанию они выключены для всех сторонних приложений:
 *
 * • Xiaomi, Redmi, POCO (MIUI, HyperOS) — «Другие разрешения»:
 *   «Экран блокировки» (показ поверх блокировки) и «Всплывающие окна,
 *   когда приложение работает в фоне». Состояние читается — это коды
 *   AppOps прошивки 10020 и 10021, ими же проверяет экран звонка Telegram.
 * • vivo, iQOO (Funtouch OS, OriginOS) — «Всплывающие окна в фоне»
 *   и «Показ на экране блокировки» в разрешениях приложения.
 *   Состояние наружу не отдаётся.
 * • OPPO, realme, OnePlus (ColorOS, realme UI, OxygenOS 12+) — «Плавающие
 *   окна» / «Поверх других приложений»: без него окно из фона не всегда
 *   поднимается. Читается стандартно (Settings.canDrawOverlays).
 * • Android 14+ у всех — «Полноэкранные уведомления»
 *   (USE_FULL_SCREEN_INTENT). Google Play оставляет его приложениям для
 *   звонков и будильников, но пользователь может отозвать; без него
 *   входящий на заблокированном экране — только плашка на 60 секунд
 *   (source.android.com/docs/core/permissions/fsi-limits). Читается:
 *   NotificationManager.canUseFullScreenIntent.
 *
 * Huawei, Honor, Tecno, Infinix — запуск из фона в их «Запуске
 * приложений» / «Автозапуске» (AutoStart), отдельного разрешения нет.
 * Samsung, Motorola, Lenovo, Pixel — без своих запретов.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО ДЕЛАЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * [open] ведёт на нужный экран прошивки, а нет его — в свойства
 * приложения. [state] говорит, что известно: выдано, не выдано или
 * проверить нельзя — тогда шаг остаётся подсказкой, а не галочкой
 * (как автозапуск: галочка, которая ничего не проверяет, обманывает).
 *
 * Экран вызова не показался (IncomingScreen, 1,5 с) — [reportBlocked]:
 * в приложении появляется полоса «Телефон не дал показать экран вызова»
 * с кнопкой сюда же. Так подсказку видит тот, у кого действительно
 * не работает, а не каждый.
 */
object IncomingCallAccess {

    private const val TAG = "IncomingAccess"

    /** Что известно о разрешениях для экрана входящего. */
    enum class State {
        /** Всё, что можно проверить, выдано, и проверять больше нечего. */
        GRANTED,

        /** Проверяемое разрешение не выдано. */
        MISSING,

        /** Прошивка не отдаёт состояние — только подсказка. */
        UNKNOWN,

        /** На этом телефоне своих запретов нет — пункт не нужен. */
        NOT_NEEDED
    }

    private enum class Shell { MIUI, VIVO, COLOR_OS, OTHER }

    private val shell: Shell by lazy {
        val maker = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        when {
            listOf("xiaomi", "redmi", "poco").any { it in maker } -> Shell.MIUI
            listOf("vivo", "iqoo").any { it in maker } -> Shell.VIVO
            listOf("oppo", "realme", "oneplus").any { it in maker } -> Shell.COLOR_OS
            else -> Shell.OTHER
        }
    }

    // Коды AppOps MIUI/HyperOS — не из SDK, из прошивки
    private const val MIUI_OP_SHOW_WHEN_LOCKED = 10020
    private const val MIUI_OP_BACKGROUND_START_ACTIVITY = 10021

    fun state(context: Context): State {
        if (!fullScreenAllowed(context)) return State.MISSING
        return when (shell) {
            Shell.MIUI -> when (miuiAllowed(context)) {
                true -> State.GRANTED
                false -> State.MISSING
                null -> State.UNKNOWN
            }
            Shell.COLOR_OS ->
                if (AppPermissions.canDrawOverlays(context)) State.GRANTED else State.MISSING
            Shell.VIVO -> State.UNKNOWN
            Shell.OTHER -> State.NOT_NEEDED
        }
    }

    /** Полноэкранные уведомления (Android 14+); раньше — всегда можно. */
    fun fullScreenAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService(NotificationManager::class.java)
            ?.canUseFullScreenIntent() ?: true
    }

    /**
     * MIUI/HyperOS: оба разрешения прошивки. null — проверить не вышло
     * (метод скрыт или прошивка другая): не выдумываем ответ.
     */
    private fun miuiAllowed(context: Context): Boolean? = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return@runCatching null
        val check = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
        )
        listOf(MIUI_OP_SHOW_WHEN_LOCKED, MIUI_OP_BACKGROUND_START_ACTIVITY).all { op ->
            check.invoke(ops, op, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }
    }.getOrNull()

    /** Для журнала разбора: что известно на этом телефоне. */
    fun describe(context: Context): String =
        "полноэкранные ${if (fullScreenAllowed(context)) "да" else "НЕТ"}, " +
            "оболочка $shell: ${state(context)}"

    /**
     * Открыть экран, где это разрешают. Порядок — от того, что точно
     * не выдано, к общему: полноэкранные уведомления, разрешения прошивки,
     * свойства приложения.
     */
    fun open(context: Context) {
        val pkg = context.packageName
        val packageUri = Uri.fromParts("package", pkg, null)
        val candidates = buildList {
            if (!fullScreenAllowed(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))
            }
            when (shell) {
                Shell.MIUI -> {
                    // «Другие разрешения» приложения: действие, а не имя
                    // экрана — его переименовывали между версиями
                    add(miuiEditor(Intent("miui.intent.action.APP_PERM_EDITOR"), pkg))
                    add(miuiEditor(component("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"), pkg))
                    add(miuiEditor(component("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity"), pkg))
                }
                Shell.VIVO -> {
                    add(component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity").putExtra("packagename", pkg))
                    add(component("com.iqoo.secure", "com.iqoo.secure.safeguard.SoftPermissionDetailActivity").putExtra("packagename", pkg))
                }
                Shell.COLOR_OS ->
                    if (!AppPermissions.canDrawOverlays(context)) {
                        add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
                    }
                Shell.OTHER -> Unit
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }

        for (intent in candidates) {
            val opened = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) return@runCatching false
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (opened) {
                Logger.i(TAG, "Открыт экран: ${intent.action ?: intent.component?.className}")
                return
            }
        }
        Logger.w(TAG, "Экран разрешений не найден на ${Build.MANUFACTURER}")
    }

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    private fun miuiEditor(intent: Intent, pkg: String): Intent =
        intent.putExtra("extra_pkgname", pkg).putExtra("extra_package_name", pkg)

    // ── «Телефон не дал показать экран вызова» ─────────────────────────────

    private const val PREFS = "incoming_access"
    private const val KEY_BLOCKED = "blocked"

    private val blocked = MutableStateFlow<Boolean?>(null)

    /** Показывать ли полосу «Телефон не дал показать экран вызова». */
    fun blockedNotice(context: Context): StateFlow<Boolean?> {
        if (blocked.value == null) {
            blocked.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_BLOCKED, false)
        }
        return blocked.asStateFlow()
    }

    /** Экран вызова не показался — запомнить до следующего открытия приложения. */
    fun reportBlocked(context: Context) = setBlocked(context, true)

    /** Человек открыл настройки или закрыл полосу. */
    fun dismissNotice(context: Context) = setBlocked(context, false)

    private fun setBlocked(context: Context, value: Boolean) {
        blocked.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BLOCKED, value).apply()
    }
}
