package com.ogneliss.dialer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Переходы в системные настройки Android, относящиеся к звонкам. */
object SystemLinks {

    /**
     * Экран приложений по умолчанию.
     *
     * Запасной путь для выбора определителя: до Android 10 роли
     * не существует, и запросить её нельзя — остаётся привести
     * пользователя туда, где выбор всё же есть.
     */
    fun defaultAppsIntent(): android.content.Intent =
        android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)


    private fun open(context: Context, action: String, uri: Uri? = null) {
        runCatching {
            val i = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (uri != null) data = uri
            }
            context.startActivity(i)
        }
    }

    fun simCards(context: Context) {
        // На большинстве прошивок открывается через ACTION_WIRELESS_SETTINGS
        open(context, Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
    }

    fun doNotDisturb(context: Context) =
        open(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun sound(context: Context) = open(context, Settings.ACTION_SOUND_SETTINGS)

    fun appNotifications(context: Context) = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun defaultApps(context: Context) = open(context, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}
