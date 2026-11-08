package com.ogneliss.dialer.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import com.ogneliss.dialer.log.Logger
import java.util.concurrent.atomic.AtomicInteger

/**
 * ОТПРАВКА SMS (2.0.0).
 *
 * • С нужной карты: SmsManager для подписки ([subId]); null — карта
 *   по умолчанию для SMS.
 * • Длинный текст — частями (divideMessage), получатель увидит одно
 *   сообщение.
 * • Приложение SMS по умолчанию само записывает отправленное в хранилище
 *   («Исходящие», после ответа сети — «Отправленные» или «Не отправлено»):
 *   система делает это только за остальные приложения. Не приложение SMS
 *   по умолчанию — запись делает система, сразу.
 * • Ответ сети и отчёт о доставке приходят в [StatusReceiver].
 *
 * Разрешение SEND_SMS проверяет вызывающий экран; без него — false.
 */
object SmsSender {

    private const val TAG = "SmsSend"
    const val ACTION_SENT = "com.ogneliss.dialer.sms.SENT"
    const val ACTION_DELIVERED = "com.ogneliss.dialer.sms.DELIVERED"
    private const val EXTRA_URI = "message_uri"
    private const val EXTRA_ADDRESS = "address"
    private const val EXTRA_PART = "part"
    private const val EXTRA_PARTS = "parts"
    private val requestCodes = AtomicInteger((System.currentTimeMillis() % 100_000).toInt())

    /** SmsManager карты [subId]; null — карта SMS по умолчанию (см. [resolveSubId]). */
    @Suppress("DEPRECATION")
    fun managerFor(context: Context, subId: Int?): SmsManager {
        val sub = resolveSubId(context, subId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (sub != null) base.createForSubscriptionId(sub) else base
        } else {
            if (sub != null) SmsManager.getSmsManagerForSubscriptionId(sub) else SmsManager.getDefault()
        }
    }

    /**
     * КАРТА ДЛЯ ОТПРАВКИ.
     *
     * Карта выбрана — она. Нет — карта SMS по умолчанию из настроек
     * Android. При двух SIM и «Спрашивать каждый раз» карты по умолчанию
     * нет, и часть прошивок без неё SMS не отправляет вовсе (ошибка
     * без объяснения) — тогда первая активная карта. Переписка показывает
     * выбранную карту явно, её можно сменить до отправки.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun resolveSubId(context: Context, subId: Int?): Int? {
        if (subId != null && subId >= 0) return subId
        val default = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (default != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return default
        return runCatching {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
        }.getOrNull()
    }

    /**
     * Отправить [text] на [address]. Возвращает, ушло ли сообщение в сеть
     * (доставку подтверждает [StatusReceiver]).
     */
    fun send(context: Context, address: String, text: String, subId: Int?): Boolean {
        if (address.isBlank() || text.isBlank()) return false
        if (!SmsRole.canSend(context)) {
            Logger.w(TAG, "Нет разрешения на отправку SMS")
            return false
        }
        return runCatching {
            val manager = managerFor(context, subId)
            val parts = manager.divideMessage(text)
            val stored = if (SmsRole.isDefault(context)) {
                MessageStore.insertOutgoingSms(context, address, text, subId, MessageStore.threadIdOf(context, address))
            } else null
            val report = SmsSettings.deliveryReports(context)
            val sent = ArrayList<PendingIntent>(parts.size)
            val delivered = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { i ->
                sent += pending(context, ACTION_SENT, stored, i, parts.size, address)
                if (report) delivered += pending(context, ACTION_DELIVERED, stored, i, parts.size, address)
            }
            if (parts.size == 1) {
                manager.sendTextMessage(address, null, parts[0], sent[0], delivered.firstOrNull())
            } else {
                manager.sendMultipartTextMessage(
                    address, null, parts, sent, if (report) delivered else null
                )
            }
            Logger.i(TAG, "SMS отправляется: частей ${parts.size}, карта ${subId ?: "по умолчанию"}")
            true
        }.getOrElse {
            Logger.e(TAG, "Не удалось отправить SMS", it)
            false
        }
    }

    private fun pending(context: Context, action: String, uri: Uri?, part: Int, parts: Int, address: String): PendingIntent {
        val intent = Intent(context, StatusReceiver::class.java).setAction(action).apply {
            uri?.let { putExtra(EXTRA_URI, it.toString()) }
            // Адрес — чтобы уведомление о неотправленном открыло переписку (2.1.0)
            putExtra(EXTRA_ADDRESS, address)
            putExtra(EXTRA_PART, part)
            putExtra(EXTRA_PARTS, parts)
        }
        // Изменяемое: в отчёт о доставке система вкладывает PDU статуса
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, requestCodes.incrementAndGet(), intent, flags)
    }

    /**
     * Ответ сети на отправку и отчёт о доставке. Запись в хранилище —
     * только у приложения SMS по умолчанию (иначе её ведёт система).
     */
    class StatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
            val part = intent.getIntExtra(EXTRA_PART, 0)
            val parts = intent.getIntExtra(EXTRA_PARTS, 1)
            when (intent.action) {
                ACTION_SENT -> {
                    val ok = resultCode == Activity.RESULT_OK
                    if (!ok) Logger.w(TAG, "SMS не отправлено: код $resultCode, часть ${part + 1} из $parts")
                    if (uri == null) return
                    if (!ok) {
                        /*
                         * Одно уведомление на сообщение (2.1.0): было — на каждую
                         * неотправленную часть, у длинного SMS — по три подряд.
                         * Уже «не отправлено» — значит, о нём сказала прежняя часть.
                         */
                        val already = MessageStore.smsType(context, uri) == MessageStore.SMS_FAILED
                        MessageStore.updateSms(context, uri, type = MessageStore.SMS_FAILED, errorCode = resultCode)
                        if (!already) MessageNotifications.sendFailed(context, intent.getStringExtra(EXTRA_ADDRESS))
                    } else if (part == parts - 1 && MessageStore.smsType(context, uri) != MessageStore.SMS_FAILED) {
                        MessageStore.updateSms(context, uri, type = MessageStore.SMS_SENT)
                    }
                }
                ACTION_DELIVERED -> {
                    if (uri == null) return
                    val pdu = intent.getByteArrayExtra("pdu") ?: return
                    val format = intent.getStringExtra("format")
                    val status = runCatching { SmsMessage.createFromPdu(pdu, format).status }.getOrNull() ?: return
                    // TP-Status: 0x00–0x1F — доставлено, 0x20–0x3F — ещё пытаются, дальше — нет
                    val stored = when {
                        status < 0x20 -> MessageStore.STATUS_COMPLETE
                        status < 0x40 -> MessageStore.STATUS_PENDING
                        else -> MessageStore.STATUS_FAILED
                    }
                    if (part == parts - 1 || stored == MessageStore.STATUS_FAILED) {
                        MessageStore.updateSms(context, uri, status = stored)
                    }
                }
            }
        }
    }
}
