package com.ogneliss.dialer.background

import com.ogneliss.dialer.concurrency.suspendRunCatching

import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ogneliss.dialer.call.CallNotifications

/** После перезагрузки восстанавливаем каналы уведомлений. */
@dagger.hilt.android.AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @javax.inject.Inject
    lateinit var reminders: com.ogneliss.dialer.data.RemindersRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CallNotifications.ensureChannels(context)

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
            pending.finish()
        }
    }
}
