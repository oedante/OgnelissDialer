package com.ogneliss.dialer.sms

import androidx.compose.runtime.Immutable

/**
 * СООБЩЕНИЕ ПЕРЕПИСКИ (1.10.0) — SMS или MMS из общего хранилища сообщений
 * телефона (content://sms, content://mms). Своей базы сообщений у приложения
 * нет: переписку видят и другие приложения сообщений, а при смене
 * приложения SMS по умолчанию ничего не теряется.
 */
@Immutable
data class Message(
    /** Номер строки в хранилище (у SMS и MMS — свои). */
    val id: Long,
    val isMms: Boolean,
    val threadId: Long,
    /** Собеседник: отправитель входящего, получатель исходящего. */
    val address: String,
    val body: String,
    /** Миллисекунды (у MMS хранилище держит секунды — переводится при чтении). */
    val date: Long,
    val outgoing: Boolean,
    val status: Status,
    val read: Boolean,
    /** Карта, через которую шло сообщение; null — неизвестно. */
    val subId: Int?,
    val subject: String? = null,
    val attachments: List<Attachment> = emptyList()
) {
    /** Ключ для списков и выбора: номера SMS и MMS пересекаются. */
    val key: String get() = keyOf(isMms, id)

    enum class Status { RECEIVED, SENDING, SENT, DELIVERED, FAILED, NOT_DOWNLOADED }

    /** Вложение MMS: картинка или другой файл части. */
    @Immutable
    data class Attachment(val uri: String, val contentType: String, val name: String?) {
        val isImage: Boolean get() = contentType.startsWith("image/")
    }

    companion object {
        fun keyOf(isMms: Boolean, id: Long) = (if (isMms) "mms:" else "sms:") + id

        /**
         * Номер записи журнала для сообщения — отрицательный: не пересекается
         * с номерами вызовов (они положительные), а MMS — с SMS.
         */
        fun journalId(isMms: Boolean, id: Long): Long = -(if (isMms) MMS_ID_OFFSET + id else id)

        /** Обратно: (MMS ли, номер в хранилище) по номеру записи журнала. */
        fun fromJournalId(journalId: Long): Pair<Boolean, Long>? {
            if (journalId >= 0) return null
            val v = -journalId
            return if (v >= MMS_ID_OFFSET) true to (v - MMS_ID_OFFSET) else false to v
        }

        private const val MMS_ID_OFFSET = 1_000_000_000_000L
    }
}

/**
 * Сообщение в записи журнала (CallLogEntry.message): что показать в строке
 * вместо типа вызова.
 */
@Immutable
data class JournalMessage(
    val key: String,
    val text: String,
    val unread: Boolean,
    val isMms: Boolean,
    val hasAttachment: Boolean,
    val failed: Boolean,
    val threadId: Long
)
