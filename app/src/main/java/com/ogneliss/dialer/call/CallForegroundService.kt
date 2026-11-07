package com.ogneliss.dialer.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Foreground-сервис на время вызова.
 *
 * Зачем: Android агрессивно убивает процессы в фоне, а диалер обязан
 * держать состояние вызова живым. Тип FOREGROUND_SERVICE_TYPE_PHONE_CALL
 * обязателен с Android 14, иначе система бросит исключение при старте.
 */
class CallForegroundService : Service() {

    companion object {
        /**
         * @param incoming вызов ещё звонит. Тогда уведомление сервиса
         *        показывает кнопки ответа и отклонения — иначе система
         *        урежет его до строчки, и на заблокированном экране
         *        ответить будет нечем.
         */
        fun start(
            context: Context,
            callerName: String,
            incoming: Boolean = false,
            /** Тихий входящий (1.8.0, тихие часы): без всплытия и полноэкранного показа. */
            quiet: Boolean = false,
            /**
             * Начало разговора — Call.Details.connectTimeMillis (2.1.0); 0 — «сейчас».
             * Было: таймер уведомления всегда шёл от момента показа, и после
             * пересборки (сняли второй входящий, ушёл участник конференции)
             * обнулялся посреди разговора.
             */
            connectedAt: Long = 0L
        ) {
            val i = Intent(context, CallForegroundService::class.java)
                .putExtra("name", callerName)
                .putExtra(EXTRA_INCOMING, incoming)
                .putExtra(EXTRA_QUIET, quiet)
                .putExtra(EXTRA_CONNECTED_AT, connectedAt)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else context.startService(i)
        }

        /** Признак входящего: от него зависит вид уведомления. */
        const val EXTRA_INCOMING = "incoming"

        /** Тихий входящий: уведомление без звука и без показа поверх экрана. */
        const val EXTRA_QUIET = "quiet"

        /** Время соединения для таймера уведомления (2.1.0). */
        const val EXTRA_CONNECTED_AT = "connectedAt"

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CallNotifications.ensureChannels(this)
        // Запасное имя из ресурсов: уведомление видит пользователь,
        // а не разработчик, и оно обязано быть на его языке
        val name = intent?.getStringExtra("name")
            ?: getString(com.ogneliss.dialer.R.string.incall_ongoing)

        /*
         * ОДНО УВЕДОМЛЕНИЕ НА СОСТОЯНИЕ ВЫЗОВА.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО БЫЛО НЕ ТАК
         * ══════════════════════════════════════════════════════════════════
         * Сервис всегда показывал «Текущий вызов», а уведомление
         * входящего с кнопками «Ответить» и «Отклонить» жило отдельно.
         *
         * Система при этом урезала входящее: CallStyle разворачивается
         * в полноценную карточку только у уведомления переднего сервиса
         * с типом PHONE_CALL. Обычное показывалось строчкой без кнопок —
         * ровно как на скриншоте.
         *
         * И висели они парой: «Входящий вызов» и «Текущий вызов» —
         * человек видел два уведомления об одном звонке.
         *
         * ── Что важнее ─────────────────────────────────────────────────────
         * На заблокированном экране ответить было НЕЧЕМ: полноэкранное
         * намерение система придерживает, если уведомление не выглядит
         * звонком, а кнопок в нём нет.
         *
         * Теперь сервис показывает то уведомление, которое соответствует
         * состоянию: при входящем — с кнопками, при разговоре — обычное.
         */
        val incoming = intent?.getBooleanExtra(EXTRA_INCOMING, false) == true
        val quiet = intent?.getBooleanExtra(EXTRA_QUIET, false) == true

        val notification = if (incoming && quiet) {
            CallNotifications.quietIncoming(this, name)
        } else if (incoming) {
            CallNotifications.incoming(this, name)
        } else {
            val connectedAt = intent?.getLongExtra(EXTRA_CONNECTED_AT, 0L) ?: 0L
            CallNotifications.ongoing(
                this, name,
                connectedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        }

        ServiceCompat.startForeground(
            this,
            // Идентификатор один: смена уведомления заменяет прежнее,
            // а не добавляет второе
            CallNotifications.ID_ONGOING,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL else 0
        )
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
