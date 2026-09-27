package com.ogneliss.dialer.call

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * ПЛИТКА БЫСТРЫХ НАСТРОЕК: ПСЕВДОЗВОНОК.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Самый быстрый способ вызвать псевдозвонок, не открывая приложение:
 * потянуть шторку и нажать. Два движения — и телефон звонит.
 *
 * Это ответ на просьбу «запускать сочетанием клавиш громкости».
 * Перехватывать клавиши при свёрнутом приложении может только служба
 * специальных возможностей, а она даёт доступ ко всему, что происходит
 * на экране. Ради одной кнопки это несоразмерно, и в приложении,
 * которое обещает не трогать данные, выглядело бы противоречиво.
 * Плитка решает ту же задачу штатным способом.
 *
 * ── Плитку нужно добавить руками ───────────────────────────────────────────
 * Android не разрешает приложению самому положить плитку в шторку:
 * человек добавляет её через «изменить» в панели быстрых настроек.
 * Это осознанное ограничение системы, и обойти его нечем.
 */
class FakeCallTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, FakeCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /*
         * С Android 14 запуск из плитки идёт только через PendingIntent.
         *
         * Прежний startActivityAndCollapse(Intent) там бросает исключение,
         * а не просто ничего не делает: плитка, собранная по старому
         * образцу, роняет системный интерфейс шторки.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
