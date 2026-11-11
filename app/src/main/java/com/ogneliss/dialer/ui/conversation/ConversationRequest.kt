package com.ogneliss.dialer.ui.conversation

import android.content.Intent
import android.net.Uri

/**
 * «ОТКРЫТЬ ПЕРЕПИСКУ» ИЗ НАМЕРЕНИЯ (2.0.0).
 *
 * Приходит отовсюду, где можно написать SMS: кнопки «Написать» в самом
 * диалере (ExternalApps.sms адресует намерение себе), ссылки sms:
 * в других приложениях, уведомление о новом сообщении. Форматы:
 *
 *   smsto:+70000000000            номер
 *   sms:+70000000000?body=Привет  текст в адресе
 *   + sms_body / EXTRA_TEXT        текст в дополнении
 *   + subscription / …SUBSCRIPTION_INDEX — карта (см. ExternalApps.smsFromSim)
 *
 * Несколько адресатов через «,» или «;» — открывается переписка с первым:
 * групповые MMS диалер не ведёт.
 */
data class ConversationRequest(
    val address: String,
    val body: String?,
    val subId: Int?,
    /** Сообщение, к которому прокрутить (2.2.0, найденное поиском): Message.key. */
    val messageKey: String? = null
) {
    /** Маршрут экрана переписки (RootScreen). */
    fun route(): String = buildString {
        append("conversation?address=").append(Uri.encode(address))
        body?.takeIf { it.isNotEmpty() }?.let { append("&body=").append(Uri.encode(it)) }
        subId?.let { append("&sub=").append(it) }
        messageKey?.let { append("&msg=").append(Uri.encode(it)) }
    }

    companion object {
        const val ROUTE = "conversation?address={address}&body={body}&sub={sub}&msg={msg}"
        const val ARG_ADDRESS = "address"
        const val ARG_BODY = "body"
        const val ARG_SUB = "sub"
        const val ARG_MSG = "msg"

        private val SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
        private val ACTIONS = setOf(Intent.ACTION_SENDTO, Intent.ACTION_SEND, Intent.ACTION_VIEW)
        private val SUB_KEYS = listOf("android.telephony.extra.SUBSCRIPTION_INDEX", "subscription", "subscription_id")

        fun from(intent: Intent?): ConversationRequest? {
            if (intent == null || intent.action !in ACTIONS) return null
            val data = intent.data ?: return null
            if (data.scheme?.lowercase() !in SCHEMES) return null
            val part = data.schemeSpecificPart.orEmpty()
            val address = part.substringBefore('?')
                .split(',', ';').firstOrNull()?.trim().orEmpty()
            if (address.isEmpty()) return null
            val body = intent.getStringExtra("sms_body")
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: queryBody(part)
            val subId = SUB_KEYS.firstNotNullOfOrNull { key ->
                intent.getIntExtra(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE && it >= 0 }
            }
            return ConversationRequest(address, body, subId)
        }

        /** «…?body=Текст» — Uri.schemeSpecificPart уже раскодирован. */
        private fun queryBody(part: String): String? =
            part.substringAfter('?', "").split('&')
                .firstOrNull { it.startsWith("body=") }
                ?.removePrefix("body=")
                ?.takeIf { it.isNotEmpty() }
    }
}
