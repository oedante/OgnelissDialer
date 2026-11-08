package com.ogneliss.dialer.util

import android.content.Context
import android.os.Build
import com.ogneliss.dialer.log.Logger

/**
 * ОТКУДА УСТАНОВЛЕНО ПРИЛОЖЕНИЕ (2.1.0).
 *
 * Было: «Оценить» и «Версия» в «О приложении» всегда вели на страницу
 * Google Play, а без магазина (телефоны без сервисов Google, установка
 * из файла или другого магазина) «Оценить» отвечало сообщением «Google
 * Play на этом устройстве недоступен» — мёртвая ссылка с объяснением.
 *
 * Стало (правило автора 07.11.2026 «не объяснять — выполнить или
 * пропустить»): ссылки на магазин — только у установленного из Google
 * Play. Иначе «Оценить» не показывается, а «Версия» открывает
 * «О приложении». Признак — установщик пакета: com.android.vending.
 *
 * Установщик за время жизни процесса не меняется (обновление из другого
 * источника перезапускает процесс), поэтому ответ запоминается.
 */
object InstallSource {

    private const val TAG = "InstallSource"

    /** Пакет Google Play — установщик, который он записывает себе. */
    const val GOOGLE_PLAY = "com.android.vending"

    @Volatile
    private var fromPlay: Boolean? = null

    /** Установлено из Google Play — ссылки на страницу в магазине рабочие. */
    fun isGooglePlay(context: Context): Boolean =
        fromPlay ?: (installer(context) == GOOGLE_PLAY).also { fromPlay = it }

    /**
     * Пакет, установивший приложение; null — неизвестно (установка
     * из файла, adb) или система не ответила.
     */
    private fun installer(context: Context): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            // До Android 11 другого способа нет; на 11+ — getInstallSourceInfo выше
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
    }.onFailure { Logger.w(TAG, "Установщик не определён", it) }.getOrNull()
}
