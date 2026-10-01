package com.ogneliss.dialer.ui.theme

import android.app.Activity
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ogneliss.dialer.log.Logger

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ОБОИ РАБОЧЕГО СТОЛА КАК ФОН (1.3.0)
 * ══════════════════════════════════════════════════════════════════════════
 * Предложение автора: под журналом и списками — обои рабочего стола,
 * приглушённые и размытые; панели с кнопками и надписями — непрозрачные.
 * Приглушение: 0, 20, 35, 50, 70, 85 % (по умолчанию 35), размытие:
 * 0, 10, 20, 35, 50, 80 px (по умолчанию 20).
 *
 * ── Почему не картинка обоев ──────────────────────────────────────────────
 * WallpaperManager.getDrawable() с Android 13–14 требует закрытых
 * разрешений, а «доступ ко всем файлам» Google Play звонилке не одобрит.
 * Поэтому картинку мы не читаем вовсе: просим систему рисовать обои
 * ПОД окном приложения (FLAG_SHOW_WALLPAPER) и делаем окно прозрачным.
 * Никаких разрешений, обои всегда актуальные, живые обои — тоже.
 *
 * ── Размытие ──────────────────────────────────────────────────────────────
 * Окно размывает то, что под ним (setBackgroundBlurRadius) — Android 12+.
 * Система может размытие выключить (энергосбережение, часть прошивок,
 * isCrossWindowBlurEnabled = false) — тогда обои видны чёткими, но
 * приглушение остаётся, и текст читается. На Android 8–11 — только
 * приглушение.
 *
 * ── Приглушение ───────────────────────────────────────────────────────────
 * Слой цвета фона темы поверх обоев (WallpaperOverlay в теме): в тёмной
 * теме это затемнение, в светлой — осветление. Тёмный текст светлой темы
 * на затемнённых обоях не читался бы, поэтому слой — цвета фона темы,
 * а не всегда чёрный.
 *
 * ── Где не работает ───────────────────────────────────────────────────────
 * В темах AMOLED (чёрный фон — смысл темы) и «Высокий контраст» (обои
 * под текстом контраст и снижают) — обои не показываются, даже если
 * настройка включена. Экран вызова — без обоев: там своё оформление.
 */
object WallpaperBackground {

    /** Применить к окну активности. background — непрозрачный фон темы. */
    fun apply(activity: Activity, enabled: Boolean, blurPx: Int, background: Color) {
        val window = activity.window ?: return
        if (enabled) {
            window.setFormat(PixelFormat.TRANSLUCENT)
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { window.setBackgroundBlurRadius(blurPx.coerceAtLeast(0)) }
                    .onFailure { Logger.w("Wallpaper", "Размытие не применилось", it) }
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { window.setBackgroundBlurRadius(0) }
            }
            window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
        }
    }
}
