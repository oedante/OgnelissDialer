package com.ogneliss.dialer.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.ogneliss.dialer.log.Logger

/**
 * РОЛИ «ТЕЛЕФОН» И «SMS»: ПОЧЕМУ ОКНО НЕ ВЫДАЛО РОЛЬ (2.0.0).
 *
 * ── Ограниченные настройки (Android 15+) ───────────────────────────────────
 * Приложение, установленное из файла APK (браузер, мессенджер, файловый
 * менеджер — выпуск с GitHub), а не из магазина, Android 15 не пускает
 * к «опасным» настройкам: роли телефона и SMS, разрешения SMS. Вместо окна
 * выбора система показывает «Ограниченная настройка», и роль не выдаётся.
 * Снять ограничение может только человек: «О приложении» → ⋮ →
 * «Разрешить ограниченные настройки» (пункт появляется после первой
 * попытки). Из Google Play и других магазинов (установка сеансом
 * PackageInstaller) ограничения нет.
 *
 * Откуда приложение установлено, Android 13+ говорит прямо:
 * InstallSourceInfo.packageSource — LOCAL_FILE или DOWNLOADED_FILE.
 *
 * ── Окно не показалось вовсе ───────────────────────────────────────────────
 * Выбор уже дважды отклоняли, или оболочка подменила окно: система
 * сразу возвращает отказ. Отличаем от сознательного отказа по времени:
 * ответ быстрее [QUICK_MS] человек дать не успел бы.
 */
object RoleAccess {

    private const val TAG = "RoleAccess"
    private const val QUICK_MS = 800L

    enum class Help { RESTRICTED, NOT_SHOWN }

    @Volatile private var requestedAt = 0L

    /** Отметить момент запроса роли — перед запуском системного окна. */
    fun markRequested() {
        requestedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Установлено из файла, и Android ограничивает роли и SMS (Android 15+).
     * На 13–14 ограничение касается других настроек (спецвозможности,
     * доступ к уведомлениям), которыми диалер не пользуется.
     */
    fun isRestrictedSource(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        return runCatching {
            val source = context.packageManager.getInstallSourceInfo(context.packageName).packageSource
            source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
                source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        }.getOrDefault(false)
    }

    /** Роль не выдана после окна — что подсказать; null — человек отказался сам. */
    fun helpAfterDenied(context: Context): Help? {
        val quick = SystemClock.elapsedRealtime() - requestedAt < QUICK_MS
        val help = when {
            isRestrictedSource(context) -> Help.RESTRICTED
            quick -> Help.NOT_SHOWN
            else -> null
        }
        if (help != null) Logger.w(TAG, "Роль не выдана: ${help.name}")
        return help
    }

    /** «О приложении» — там пункт «Разрешить ограниченные настройки». */
    fun openAppInfo(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Logger.w(TAG, "Не удалось открыть «О приложении»", it) }
    }

    /** «Приложения по умолчанию» — когда окно роли не показалось. */
    fun openDefaultApps(context: Context) {
        val opened = runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) openAppInfo(context)
    }
}
