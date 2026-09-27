package com.ogneliss.dialer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ogneliss.dialer.log.Logger

/**
 * ПРОВЕРКА ПРАВА ПОКАЗЫВАТЬ УВЕДОМЛЕНИЯ.
 *
 * ── Почему одного runCatching мало ─────────────────────────────────────────
 * С Android 13 показ уведомлений требует разрешения POST_NOTIFICATIONS,
 * и вызов `notify()` без него бросает SecurityException.
 *
 * Обернуть его в runCatching недостаточно по двум причинам:
 *
 * 1. Ошибка проглатывается молча. Пользователь не получает напоминание
 *    и не понимает, почему — а причина в отозванном разрешении,
 *    и её можно исправить.
 * 2. Lint справедливо помечает такой вызов ошибкой: он не отличает
 *    «мы обработали» от «мы понадеялись».
 *
 * Здесь проверка явная, а неудача попадает в лог с понятной причиной.
 */
object NotificationPermission {

    private const val TAG = "Notifications"

    /** Есть ли разрешение показывать уведомления. */
    fun isGranted(context: Context): Boolean {
        // До Android 13 разрешение не требовалось
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * МОЖНО ЛИ ПОКАЗАТЬ УВЕДОМЛЕНИЕ.
     *
     * Разрешения мало: пользователь мог выключить уведомления
     * приложения в системных настройках, не отзывая разрешение.
     * Тогда вызов notify() пройдёт без ошибки, но уведомления
     * человек не увидит — и будет считать, что напоминания не работают.
     */
    fun canNotify(context: Context): Boolean =
        isGranted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Безопасный показ уведомления.
     *
     * @param what что показываем — попадёт в лог, если не получилось
     * @return true, если уведомление показано
     */
    /*
     * @SuppressLint("MissingPermission") — с объяснением, а не «чтобы молчал».
     *
     * Разрешение проверяется первой же строкой тела: isGranted(context),
     * и при отказе функция возвращает false, ничего не показывая. Сверх
     * того вызов обёрнут в runCatching, который ловит SecurityException —
     * он возможен и при выданном разрешении, например в рабочем профиле.
     *
     * lint через вспомогательную функцию заглянуть не умеет и считал
     * это ошибкой, из-за которой падала сборка release.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun notify(
        context: Context,
        id: Int,
        notification: android.app.Notification,
        what: String
    ): Boolean {
        if (!isGranted(context)) {
            Logger.w(TAG, "Нет разрешения на уведомления, пропускаю: $what")
            return false
        }
        return runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        }.getOrElse {
            // SecurityException возможен и при выданном разрешении:
            // например, при ограничениях рабочего профиля
            Logger.w(TAG, "Не удалось показать уведомление: $what", it)
            false
        }
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }
}
