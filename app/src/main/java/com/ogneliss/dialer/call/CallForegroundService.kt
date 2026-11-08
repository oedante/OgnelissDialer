package com.ogneliss.dialer.call

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.ogneliss.dialer.log.Logger

/**
 * Foreground-сервис на время вызова.
 *
 * Зачем: Android агрессивно убивает процессы в фоне, а диалер обязан
 * держать состояние вызова живым. Тип FOREGROUND_SERVICE_TYPE_PHONE_CALL
 * обязателен с Android 14, иначе система бросит исключение при старте.
 *
 * ── Что изменилось в 2.1.0 ─────────────────────────────────────────────────
 * • Пока вызов звонит, уведомление всегда входящее, с «Ответить»
 *   и «Отклонить»: всплывающее ([EXTRA_ALERT]) или тихое. «Текущий вызов»
 *   с таймером — только после ответа и у исходящих (CallNotifications).
 * • [start] и [stop] не бросают исключений: сбой службы не должен
 *   оборвать onCallAdded раньше, чем откроется экран вызова.
 * • Остановка — командой внутри службы, после startForeground ([stop]).
 */
class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallService"

        /**
         * Служба запущена или запуск запрошен (2.1.0). Нет — [stop] её
         * не будит: иначе ради остановки она поднималась бы с уведомлением.
         */
        @Volatile
        private var requested = false

        /**
         * @param incoming вызов ещё звонит. Тогда уведомление сервиса
         *        показывает кнопки ответа и отклонения — иначе система
         *        урежет его до строчки, и на заблокированном экране
         *        ответить будет нечем.
         * @param alert входящее всплывает и разворачивается на весь экран
         *        (2.1.0); без него — тихое входящее с теми же кнопками.
         * @param number номер звонящего: по нему «Не беспокоить» узнаёт
         *        контакт (CallNotifications.caller).
         * @return false — служба не запустилась (причина в журнале).
         */
        fun start(
            context: Context,
            callerName: String,
            number: String? = null,
            incoming: Boolean = false,
            alert: Boolean = false,
            /** Тихий входящий (1.8.0, тихие часы): без всплытия и полноэкранного показа. */
            quiet: Boolean = false,
            /**
             * Начало разговора — Call.Details.connectTimeMillis (2.1.0); 0 — «сейчас».
             * Было: таймер уведомления всегда шёл от момента показа, и после
             * пересборки (сняли второй входящий, ушёл участник конференции)
             * обнулялся посреди разговора.
             */
            connectedAt: Long = 0L
        ): Boolean {
            val i = Intent(context, CallForegroundService::class.java)
                .putExtra("name", callerName)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_INCOMING, incoming)
                .putExtra(EXTRA_ALERT, alert)
                .putExtra(EXTRA_QUIET, quiet)
                .putExtra(EXTRA_CONNECTED_AT, connectedAt)
            /*
             * БЕЗ ИСКЛЮЧЕНИЙ НАРУЖУ (2.1.0).
             *
             * Было: startForegroundService вызывался из onCallAdded без
             * защиты. Ошибка запуска (запрет фонового старта на прошивке,
             * нехватка памяти) обрывала onCallAdded раньше startActivity —
             * экран вызова не открывался вовсе. Стало: ошибка — в журнал,
             * вызывающий идёт дальше.
             */
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else context.startService(i)
                requested = true
                true
            }.getOrElse {
                Logger.w(TAG, "Служба вызова не запустилась", it)
                false
            }
        }

        /** Признак входящего: от него зависит вид уведомления. */
        const val EXTRA_INCOMING = "incoming"

        /** Входящее всплывающее, с полноэкранным показом (2.1.0). */
        const val EXTRA_ALERT = "alert"

        /** Тихий входящий: уведомление без звука и без показа поверх экрана. */
        const val EXTRA_QUIET = "quiet"

        /** Время соединения для таймера уведомления (2.1.0). */
        const val EXTRA_CONNECTED_AT = "connectedAt"

        /** Номер звонящего для уведомления (2.1.0). */
        const val EXTRA_NUMBER = "number"

        private const val ACTION_STOP = "com.ogneliss.dialer.CALL_SERVICE_STOP"

        /*
         * ОСТАНОВКА — КОМАНДОЙ, А НЕ stopService (2.1.0).
         *
         * Было: stopService сразу после startForegroundService. Короткий
         * вызов (USSD/MMI-код, мгновенный сброс) успевал закончиться раньше,
         * чем служба дошла до onStartCommand и startForeground, — и система
         * роняла процесс: «Context.startForegroundService() did not then call
         * Service.startForeground()». Вместе с процессом падала и служба
         * вызовов. Стало: остановка идёт той же очередью команд — после
         * команды запуска, когда startForeground уже вызван.
         */
        fun stop(context: Context) {
            if (!requested) return
            val i = Intent(context, CallForegroundService::class.java).setAction(ACTION_STOP)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else context.startService(i)
            }.onFailure {
                Logger.w(TAG, "Команда остановки не принята — останавливаю напрямую", it)
                runCatching { context.stopService(Intent(context, CallForegroundService::class.java)) }
            }
        }
    }

    /** startForeground уже вызывался в этой службе. */
    private var inForeground = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopNow(startId)
            return START_NOT_STICKY
        }
        // Прежний экземпляр мог закончиться (onDestroy) уже после нового start()
        requested = true
        runCatching { CallNotifications.ensureChannels(this) }
        // Запасное имя из ресурсов: уведомление видит пользователь,
        // а не разработчик, и оно обязано быть на его языке
        val name = intent?.getStringExtra("name")
            ?: getString(com.ogneliss.dialer.R.string.incall_ongoing)
        val number = intent?.getStringExtra(EXTRA_NUMBER)

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
         * Теперь сервис показывает то уведомление, которое соответствует
         * состоянию: пока звонит — входящее с кнопками (всплывающее или
         * тихое, 2.1.0), при разговоре — обычное.
         */
        val incoming = intent?.getBooleanExtra(EXTRA_INCOMING, false) == true
        val alert = intent?.getBooleanExtra(EXTRA_ALERT, false) == true
        val quiet = intent?.getBooleanExtra(EXTRA_QUIET, false) == true

        val notification = runCatching {
            when {
                incoming && quiet -> CallNotifications.quietIncoming(this, name, number)
                incoming -> CallNotifications.incoming(this, name, number, alert)
                else -> {
                    val connectedAt = intent?.getLongExtra(EXTRA_CONNECTED_AT, 0L) ?: 0L
                    CallNotifications.ongoing(
                        this, name, number,
                        connectedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                }
            }
        }.getOrElse {
            Logger.w(TAG, "Уведомление вызова не собрано — ставлю простое", it)
            fallbackNotification(name)
        }
        // Всплывающее входящее — под своим номером: система видит новое
        // уведомление и запускает полноэкранный показ (CallNotifications.ID_INCOMING)
        val id = if (incoming && alert && !quiet) CallNotifications.ID_INCOMING else CallNotifications.ID_ONGOING
        showForeground(id, notification)
        return START_NOT_STICKY
    }

    /*
     * ТИП PHONE_CALL — И НА ANDROID 10–13 (2.1.0).
     *
     * Было: тип передавался только с Android 14, раньше — 0. Система при
     * этом не считала службу службой звонка: CallStyle и приоритет
     * уведомления на части прошивок Android 10–13 урезались. Тип
     * объявлен в манифесте, а передавать его можно с Android 10.
     */
    private fun showForeground(id: Int, notification: Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                id,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL else 0
            )
            inForeground = true
        }.onFailure { Logger.w(TAG, "startForeground не выполнен", it) }
    }

    /**
     * Остановка по команде [stop]. Если startForeground почему-то ещё не
     * вызывался — сначала он: система требует его от службы, запущенной
     * через startForegroundService, даже если её тут же останавливают.
     */
    private fun stopNow(startId: Int) {
        if (!inForeground) {
            showForeground(CallNotifications.ID_ONGOING, fallbackNotification(getString(com.ogneliss.dialer.R.string.incall_ongoing)))
        }
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        inForeground = false
        // Пришла новая команда запуска (следующий вызов) — служба остаётся
        stopSelf(startId)
    }

    /** Простое уведомление вызова — когда собрать обычное не вышло. */
    private fun fallbackNotification(name: String): Notification =
        androidx.core.app.NotificationCompat.Builder(this, CallNotifications.CHANNEL_ONGOING)
            .setSmallIcon(com.ogneliss.dialer.R.drawable.ic_call)
            .setContentTitle(name)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setSilent(true)
            .build()

    override fun onDestroy() {
        requested = false
        inForeground = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
