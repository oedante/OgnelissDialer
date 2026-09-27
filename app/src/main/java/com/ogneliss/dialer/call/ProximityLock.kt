package com.ogneliss.dialer.call

import android.content.Context
import android.os.PowerManager

/**
 * Гасит экран, когда телефон поднесён к уху.
 * Без этого щека нажимает кнопки во время разговора — классическая жалоба
 * в отзывах на сторонние диалеры.
 */
class ProximityLock(context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var wakeLock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    fun acquire() {
        val pm = powerManager ?: return
        if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        wakeLock = pm.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "OgnelissDialer:proximity"
        ).apply { runCatching { acquire(60 * 60 * 1000L) } }
    }

    fun release() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }
}
