package com.ogneliss.dialer.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.ogneliss.dialer.R

/**
 * Разрешения Ogneliss Dialer, сгруппированные по назначению.
 * Для каждой группы — понятное объяснение, которое показывается пользователю
 * ДО системного диалога. Google Play требует такого обоснования для
 * чувствительных групп CALL_LOG и PHONE.
 */
object AppPermissions {

    data class Group(
        val id: String,
        val titleRes: Int,
        val rationaleRes: Int,
        val permissions: List<String>,
        val required: Boolean
    )

    /**
     * Группы разрешений в порядке важности.
     *
     * Порядок не случаен: обязательные идут первыми, и пользователь
     * доходит до необязательных, уже согласившись на основное.
     * Обратный порядок приводил бы к отказам на первом же экране.
     */
    val groups: List<Group> by lazy {
        listOf(
            Group(
                id = "phone",
                titleRes = R.string.perm_phone_title,
                rationaleRes = R.string.perm_phone_text,
                permissions = buildList {
                    add(Manifest.permission.CALL_PHONE)
                    add(Manifest.permission.READ_PHONE_STATE)
                    add(Manifest.permission.ANSWER_PHONE_CALLS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        add(Manifest.permission.READ_PHONE_NUMBERS)
                },
                required = true
            ),
            Group(
                id = "call_log",
                titleRes = R.string.perm_calllog_title,
                rationaleRes = R.string.perm_calllog_text,
                permissions = listOf(
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG
                ),
                required = true
            ),
            Group(
                id = "contacts",
                titleRes = R.string.perm_contacts_title,
                rationaleRes = R.string.perm_contacts_text,
                permissions = listOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
                ),
                required = true
            ),
            Group(
                id = "notifications",
                titleRes = R.string.perm_notifications_title,
                rationaleRes = R.string.perm_notifications_text,
                permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
                required = false
            ),
            Group(
                id = "audio",
                titleRes = R.string.perm_audio_title,
                rationaleRes = R.string.perm_audio_text,
                permissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                },
                required = false
            )
        )
    }

    fun runtimePermissions(): Array<String> =
        groups.flatMap { it.permissions }.distinct().toTypedArray()

    fun missing(context: Context): List<Group> = groups.filter { g ->
        g.permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Специальные разрешения: выдаются только через настройки ---

    /**
     * Системные настройки приложения.
     *
     * Нужны, когда система перестала показывать диалог запроса: после
     * двух отказов она игнорирует запрос молча, и единственный путь
     * выдать разрешение — системный экран. Без этой ссылки пользователь
     * оказывается в тупике и не понимает, почему кнопка не работает.
     */
    fun openAppSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun requestOverlay(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(android.app.AlarmManager::class.java)
        return am?.canScheduleExactAlarms() ?: false
    }

    fun requestExactAlarms(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Батарея: без исключения из оптимизаций система может убить процесс вызова. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
