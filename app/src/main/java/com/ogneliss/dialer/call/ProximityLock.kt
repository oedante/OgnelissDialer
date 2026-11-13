package com.ogneliss.dialer.call

import android.content.Context
import android.os.PowerManager

/**
 * Гасит экран, когда телефон поднесён к уху.
 * Без этого щека нажимает кнопки во время разговора — классическая жалоба
 * в отзывах на сторонние диалеры.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ТОЛЬКО У УХА, А НЕ ВСЁ ВРЕМЯ ЭКРАНА ВЫЗОВА (1.8.1)
 * ══════════════════════════════════════════════════════════════════════════
 * Было: датчик включался при открытии экрана вызова и держался до его
 * закрытия — и пока телефон звонит, и на громкой связи. Звонящий телефон
 * экраном вниз, в чехле-книжке, в кармане или с капризным датчиком (жалоба
 * «при звонке не загорается экран», Realme) оставался тёмным: датчик
 * «видел» близость и гасил экран, который окно вызова только что включило.
 *
 * Теперь — как в системном диалере: датчик держит экран погашенным, только
 * когда трубку держат у уха — идёт набор или разговор и звук в разговорном
 * динамике (или в проводной гарнитуре). Звонит, громкая связь, Bluetooth —
 * экран не гасится. Правило — CallScreenRules.proximityWanted.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * БЕЗ ЧАСОВОГО ПРЕДЕЛА (2.4.4, проверка датчика по просьбе автора)
 * ══════════════════════════════════════════════════════════════════════════
 * Было: acquire(60 мин). Блокировка снималась сама через час, а заново
 * её брали только при событии вызова или смене звука: в разговоре дольше
 * часа без таких событий экран у уха больше не гас, и щека нажимала кнопки.
 * Стало: без предела, как в системном диалере. Отпускается всегда: экран
 * вызова отпускает её, когда она не нужна, и при закрытии; процесс
 * закрыт — система снимает её сама.
 *
 * ── Отпустить — когда отнимут от уха ───────────────────────────────────────
 * И при закрытии экрана вызова (2.4.4): собеседник положил трубку, а телефон
 * ещё у уха — экран загорится, когда его отнимут, а не у щеки. Было: при
 * закрытии — сразу.
 */
class ProximityLock(context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var wakeLock: PowerManager.WakeLock? = null

    /** Держать или отпустить. Повторный вызов с тем же значением ничего не делает. */
    fun hold(wanted: Boolean) {
        if (wanted) acquire() else release(waitForFar = true)
    }

    // Без предела — намеренно (разбор в шапке): отпускается в hold(false) и release()
    @android.annotation.SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun acquire() {
        if (wakeLock?.isHeld == true) return
        val pm = powerManager ?: return
        if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        wakeLock = pm.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "OgnelissDialer:proximity"
        ).apply { runCatching { acquire() } }
    }

    /**
     * @param waitForFar экран вернётся, когда телефон отнимут от уха, а не сразу:
     *        включили громкую связь, не отнимая трубку, — экран не вспыхнет у щеки
     */
    fun release(waitForFar: Boolean = false) {
        runCatching {
            if (wakeLock?.isHeld == true) {
                if (waitForFar) wakeLock?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                else wakeLock?.release()
            }
        }
        wakeLock = null
    }
}
