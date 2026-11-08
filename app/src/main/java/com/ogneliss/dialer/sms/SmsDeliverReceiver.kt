package com.ogneliss.dialer.sms

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ogneliss.dialer.R
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.data.BlockingController
import com.ogneliss.dialer.log.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 *
 * ── 2.1.0: SMS не теряется ни при каком сбое ───────────────────────────────
 * Было: запись не удалась — уведомления не было (оно строилось
 * перечитыванием хранилища), а в журнале всё равно «сохранено»; пустой
 * адрес отправителя — сообщение отбрасывалось молча; сбой где угодно —
 * без следа. Стало:
 * • запись не удалась — ещё одна попытка, а уведомление показывается
 *   из самого сообщения в любом случае (MessageNotifications.Incoming):
 *   текст хотя бы виден человеку;
 * • пустой адрес — «Неизвестный» (как AOSP «Сообщения»): у таких SMS
 *   одна общая переписка;
 * • вся работа — под перехватом с записью в журнал; «сохранено»
 *   пишется, только когда сохранено.
 */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {

    @Inject lateinit var blocking: BlockingController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .onFailure { Logger.e(TAG, "Входящее SMS не разобрано", it) }
            .getOrNull()
        val first = parts?.firstOrNull { it != null }
        if (parts == null || first == null) {
            Logger.w(TAG, "Входящее SMS без частей")
            return
        }
        val address = (first.displayOriginatingAddress ?: first.originatingAddress)
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.sms_unknown_sender)
        val body = parts.joinToString("") { it?.displayMessageBody ?: it?.messageBody.orEmpty() }
        val subId = subscriptionOf(intent)
        val sentAt = runCatching { first.timestampMillis }.getOrDefault(0L)
        val serviceCenter = runCatching { first.serviceCenterAddress }.getOrNull()
        val receivedAt = System.currentTimeMillis()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                suspendRunCatching {
                    deliver(context, address, body, subId, sentAt, serviceCenter, receivedAt)
                }.onFailure {
                    Logger.e(TAG, "Входящее SMS: сбой обработки от ${Logger.mask(address)}", it)
                    // Последняя страховка: хотя бы показать — ничего, кроме уведомления
                    runCatching {
                        MessageNotifications.showForAddress(
                            context, address,
                            MessageNotifications.Incoming(body, receivedAt, subId, key = null)
                        )
                    }.onFailure { e -> Logger.e(TAG, "Уведомление о входящем не показано", e) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun deliver(
        context: Context,
        address: String,
        body: String,
        subId: Int?,
        sentAt: Long,
        serviceCenter: String?,
        receivedAt: Long
    ) {
        val blocked = suspendRunCatching { blocking.isBlocked(address) }.getOrDefault(false)
        val insert = {
            MessageStore.insertIncomingSms(
                context, address, body,
                dateSent = sentAt,
                subId = subId,
                serviceCenter = serviceCenter,
                read = blocked
            )
        }
        // Хранилище бывает занято (база заперта другим приложением) — повтор через миг
        val uri = insert() ?: run {
            delay(RETRY_DELAY_MS)
            insert()
        }
        val what = if (blocked) " (номер в списке блокировки)" else ""
        if (uri != null) Logger.i(TAG, "Входящее SMS сохранено$what")
        else Logger.e(TAG, "Входящее SMS НЕ сохранено и после повтора$what — показано уведомлением")
        if (blocked) return
        val key = uri?.let { runCatching { Message.keyOf(false, ContentUris.parseId(it)) }.getOrNull() }
        MessageNotifications.showForAddress(
            context, address,
            MessageNotifications.Incoming(body = body, date = receivedAt, subId = subId, key = key)
        )
    }

    companion object {
        private const val TAG = "SmsIn"
        private const val RETRY_DELAY_MS = 400L

        /** Номер подписки (карты) из намерения: ключи у прошивок разные. */
        fun subscriptionOf(intent: Intent): Int? = listOf(
            "android.telephony.extra.SUBSCRIPTION_INDEX", "subscription", "sub_id"
        ).firstNotNullOfOrNull { key ->
            intent.getIntExtra(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE && it >= 0 }
        }
    }
}
