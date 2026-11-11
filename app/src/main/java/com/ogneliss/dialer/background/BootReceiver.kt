package com.ogneliss.dialer.background

import com.ogneliss.dialer.concurrency.suspendRunCatching

import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ogneliss.dialer.call.CallNotifications

/** После перезагрузки и обновления восстанавливаем каналы уведомлений и напоминания. */
@dagger.hilt.android.AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @javax.inject.Inject
    lateinit var reminders: com.ogneliss.dialer.data.RemindersRepository

    @javax.inject.Inject
    lateinit var smsQueue: com.ogneliss.dialer.sms.SmsQueue

    override fun onReceive(context: Context, intent: Intent) {
        // 2.1.0: и после обновления приложения — состояние приёмника
        // пропущенных сверяется с разрешением уведомлений
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        CallNotifications.ensureChannels(context)
        // Виджет «Закреплённые» (2.2.0): после обновления приложения — новая разметка
        com.ogneliss.dialer.widget.PinnedWidget.update(context)
        // Уведомления запрещены — пропущенные рисует система (MissedCallsNotifier.syncReceiver)
        com.ogneliss.dialer.call.MissedCallsNotifier.syncReceiver(context)

        /*
         * ВОССТАНОВЛЕНИЕ НАПОМИНАНИЙ.
         *
         * Система стирает все будильники при выключении телефона.
         * Раньше напоминания на этом и заканчивались — молча: человек
         * ставил «перезвонить через два часа», перезагружал телефон
         * и не получал ничего, не понимая, что произошло.
         *
         * Просроченные за время выключения не переставляются: показывать
         * «перезвоните» через сутки после назначенного хуже, чем
         * не показывать. Разбор — в RemindersRepository.
         */
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        ).launch {
            suspendRunCatching { reminders.restoreAfterBoot() }
            // Отложенные SMS (2.2.0): пришедшее за время выключения — сейчас, остальное — будильником
            suspendRunCatching { smsQueue.sendDue() }
            pending.finish()
        }
    }
}
