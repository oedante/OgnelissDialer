package com.ogneliss.dialer.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.data.BlockingController
import com.ogneliss.dialer.log.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ВХОДЯЩЕЕ SMS (2.0.0) — SMS_DELIVER, только приложению SMS по умолчанию.
 *
 * Система отдаёт SMS одному получателю — приложению по умолчанию —
 * и больше нигде его не сохраняет: не записали в хранилище — сообщение
 * потеряно. Поэтому сначала запись, потом всё остальное.
 *
 * • Части длинного SMS склеиваются (getMessagesFromIntent).
 * • Номер в нашем списке блокировки — сообщение сохраняется прочитанным
 *   и без уведомления: удалять чужое SMS молча нельзя, а беспокоить
 *   человек просил не надо. Системный список блокировки система
 *   применяет сама — такие SMS сюда не доходят.
 * • goAsync: запись и уведомление идут не в главном потоке, а система
 *   не убивает процесс, пока они не закончены.
 */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {

    @Inject lateinit var blocking: BlockingController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()
        if (parts.isNullOrEmpty()) return
        val first = parts[0] ?: return
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = parts.joinToString("") { it?.displayMessageBody ?: it?.messageBody.orEmpty() }
        val subId = subscriptionOf(intent)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val blocked = suspendRunCatching { blocking.isBlocked(address) }.getOrDefault(false)
                val uri = MessageStore.insertIncomingSms(
                    context, address, body,
                    dateSent = first.timestampMillis,
                    subId = subId,
                    serviceCenter = first.serviceCenterAddress,
                    read = blocked
                )
                Logger.i(TAG, "Входящее SMS сохранено" + if (blocked) " (номер в списке блокировки)" else "")
                if (uri != null && !blocked) {
                    MessageNotifications.showForAddress(context, address)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsIn"

        /** Номер подписки (карты) из намерения: ключи у прошивок разные. */
        fun subscriptionOf(intent: Intent): Int? = listOf(
            "android.telephony.extra.SUBSCRIPTION_INDEX", "subscription", "sub_id"
        ).firstNotNullOfOrNull { key ->
            intent.getIntExtra(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE && it >= 0 }
        }
    }
}
