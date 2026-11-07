package com.ogneliss.dialer.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * ХРАНИЛИЩЕ СООБЩЕНИЙ ТЕЛЕФОНА (2.0.0): чтение и запись SMS и MMS.
 *
 * Всё — через провайдер Telephony (content://sms, content://mms): он общий
 * для всех приложений сообщений. Читать можно, только пока диалер —
 * приложение SMS по умолчанию (SmsRole.canRead — проверяется при каждом
 * чтении: перестал быть — сразу пусто, как требует политика Google Play).
 * Записывать входящие и отправленные обязано само приложение SMS
 * по умолчанию — система этого за него не делает.
 *
 * Все функции блокирующие — вызывать вне главного потока.
 * Имена столбцов — строками: часть констант Telephony появилась только
 * в новых Android, а имена столбцов не менялись с Android 4.4.
 */
object MessageStore {

    private const val TAG = "Messages"

    val SMS_URI: Uri = Uri.parse("content://sms")
    val MMS_URI: Uri = Uri.parse("content://mms")
    private val MMS_PART_URI: Uri = Uri.parse("content://mms/part")
    private val ALL_URI: Uri = Uri.parse("content://mms-sms/")

    // Типы SMS (Telephony.Sms.MESSAGE_TYPE_*)
    const val SMS_INBOX = 1
    const val SMS_SENT = 2
    const val SMS_OUTBOX = 4
    const val SMS_FAILED = 5
    const val SMS_QUEUED = 6

    // Ящики MMS (Telephony.Mms.MESSAGE_BOX_*); «не отправлено» — 5
    const val MMS_INBOX = 1
    const val MMS_SENT = 2
    const val MMS_OUTBOX = 4
    const val MMS_FAILED = 5

    // Типы MMS (X-Mms-Message-Type) и роли адресов (PduHeaders)
    const val MMS_TYPE_SEND_REQ = 128
    const val MMS_TYPE_NOTIFICATION = 130
    const val MMS_TYPE_RETRIEVE_CONF = 132
    const val ADDR_FROM = 137
    const val ADDR_TO = 151
    const val ADDR_CC = 130

    // Статус доставки SMS (Telephony.Sms.STATUS_*)
    const val STATUS_NONE = -1
    const val STATUS_COMPLETE = 0
    const val STATUS_PENDING = 32
    const val STATUS_FAILED = 64

    // ═══════════════════════════════════════════════════════════════════════
    // ЧТЕНИЕ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Последние [limit] сообщений всех переписок, от новых к старым —
     * для журнала. MMS — с адресом собеседника и признаком вложений.
     */
    fun latest(context: Context, limit: Int): List<Message> {
        if (!SmsRole.canRead(context) || limit <= 0) return emptyList()
        val sms = querySms(context, null, null, limit)
        val mms = queryMms(context, null, null, limit, withParts = false)
        return (sms + mms).sortedByDescending { it.date }.take(limit)
    }

    /** Переписка с номером [address]: последние [limit] сообщений, от старых к новым. */
    fun conversation(context: Context, address: String, limit: Int = 500): List<Message> {
        if (!SmsRole.canRead(context)) return emptyList()
        val thread = threadIdOf(context, address) ?: return emptyList()
        return thread(context, thread, limit)
    }

    /** Переписка по номеру нити [threadId]. */
    fun thread(context: Context, threadId: Long, limit: Int = 500): List<Message> {
        if (!SmsRole.canRead(context)) return emptyList()
        val sel = "thread_id = ?"
        val args = arrayOf(threadId.toString())
        val sms = querySms(context, sel, args, limit)
        val mms = queryMms(context, sel, args, limit, withParts = true)
        return (sms + mms).sortedByDescending { it.date }.take(limit).reversed()
    }

    /** Непрочитанные входящие нити — для уведомления. */
    fun unreadInThread(context: Context, threadId: Long, limit: Int = 8): List<Message> {
        if (!SmsRole.canRead(context)) return emptyList()
        val sel = "thread_id = ? AND read = 0"
        val args = arrayOf(threadId.toString())
        return (querySms(context, sel, args, limit) + queryMms(context, sel, args, limit, withParts = true))
            .filter { !it.outgoing }
            .sortedBy { it.date }
            .takeLast(limit)
    }

    /**
     * Сообщения с любым из [numbers] — для истории карточки (2.1.0).
     *
     * Не через threadIdOf: тот создаёт нить, если её нет, и карточка
     * человека, с которым не переписывались, плодила бы пустые переписки.
     * Берутся последние [window] сообщений и отбираются по номеру
     * (E.164 и последние десять цифр — «8 912…» и «+7 912…» одно и то же).
     */
    fun withNumbers(context: Context, numbers: Collection<String>, window: Int = 3000): List<Message> {
        if (!SmsRole.canRead(context) || numbers.isEmpty()) return emptyList()
        fun key(n: String): String {
            val e164 = com.ogneliss.dialer.util.PhoneFormatter.toE164(n)
            return com.ogneliss.dialer.data.CallLogGrouper.tailKey(e164) ?: e164.ifBlank { n.trim() }
        }
        val keys = numbers.map(::key).filter { it.isNotBlank() }.toSet()
        return latest(context, window).filter { key(it.address) in keys }
    }

    /**
     * Нить переписки с номером: существующая или новая. Создаёт строку нити
     * в хранилище — только для приложения SMS по умолчанию.
     */
    fun threadIdOf(context: Context, address: String): Long? = runCatching {
        if (!SmsRole.isDefault(context) || address.isBlank()) return null
        Telephony.Threads.getOrCreateThreadId(context, address)
    }.onFailure { Logger.w(TAG, "Нить переписки не найдена", it) }.getOrNull()

    private fun querySms(context: Context, selection: String?, args: Array<String>?, limit: Int): List<Message> {
        val out = ArrayList<Message>()
        // Черновики — не сообщения: в журнал и переписку не идут
        val sel = listOfNotNull(selection, "type != 3").joinToString(" AND ") { "($it)" }
        runCatching {
            context.contentResolver.query(
                SMS_URI,
                arrayOf("_id", "thread_id", "address", "body", "date", "type", "status", "read", "sub_id"),
                sel, args, "date DESC LIMIT $limit"
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.int("type")
                    val status = c.int("status")
                    out += Message(
                        id = c.long("_id"),
                        isMms = false,
                        threadId = c.long("thread_id"),
                        address = c.string("address").orEmpty(),
                        body = c.string("body").orEmpty(),
                        date = c.long("date"),
                        outgoing = type != SMS_INBOX,
                        status = smsStatus(type, status),
                        read = c.int("read") != 0,
                        subId = c.intOrNull("sub_id")?.takeIf { it >= 0 }
                    )
                }
            }
        }.onFailure { Logger.w(TAG, "Не удалось прочитать SMS", it) }
        return out
    }

    private fun queryMms(
        context: Context,
        selection: String?,
        args: Array<String>?,
        limit: Int,
        withParts: Boolean
    ): List<Message> {
        val out = ArrayList<Message>()
        // Только сообщения, а не служебные отчёты о доставке и прочтении
        val sel = listOfNotNull(selection, "m_type IN ($MMS_TYPE_SEND_REQ, $MMS_TYPE_NOTIFICATION, $MMS_TYPE_RETRIEVE_CONF)")
            .joinToString(" AND ") { "($it)" }
        runCatching {
            context.contentResolver.query(
                MMS_URI,
                arrayOf("_id", "thread_id", "date", "msg_box", "read", "sub", "sub_cs", "m_type", "sub_id"),
                sel, args, "date DESC LIMIT $limit"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.long("_id")
                    val box = c.int("msg_box")
                    val outgoing = box != MMS_INBOX
                    val parts = parts(context, id, textOnly = !withParts)
                    out += Message(
                        id = id,
                        isMms = true,
                        threadId = c.long("thread_id"),
                        address = mmsAddress(context, id, outgoing).orEmpty(),
                        body = parts.first,
                        date = c.long("date") * 1000L,
                        outgoing = outgoing,
                        status = mmsStatus(box, c.int("m_type")),
                        read = c.int("read") != 0,
                        subId = c.intOrNull("sub_id")?.takeIf { it >= 0 },
                        subject = c.string("sub")?.takeIf { it.isNotBlank() },
                        attachments = parts.second
                    )
                }
            }
        }.onFailure { Logger.w(TAG, "Не удалось прочитать MMS", it) }
        return out
    }

    /** Текст и вложения MMS. [textOnly] — без вложений (для журнала: быстрее). */
    private fun parts(context: Context, mmsId: Long, textOnly: Boolean): Pair<String, List<Message.Attachment>> {
        val texts = ArrayList<String>()
        val attachments = ArrayList<Message.Attachment>()
        runCatching {
            context.contentResolver.query(
                MMS_PART_URI,
                arrayOf("_id", "ct", "text", "name", "cl"),
                "mid = ?", arrayOf(mmsId.toString()), "seq ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val ct = c.string("ct").orEmpty().lowercase()
                    when {
                        ct == "text/plain" -> c.string("text")?.takeIf { it.isNotBlank() }?.let { texts += it }
                        ct == "application/smil" -> Unit
                        else -> {
                            val partId = c.long("_id")
                            if (textOnly) {
                                attachments += Message.Attachment("", ct, null)
                            } else {
                                attachments += Message.Attachment(
                                    uri = ContentUris.withAppendedId(MMS_PART_URI, partId).toString(),
                                    contentType = ct,
                                    name = c.string("name") ?: c.string("cl")
                                )
                            }
                        }
                    }
                }
            }
        }.onFailure { Logger.w(TAG, "Не удалось прочитать части MMS", it) }
        return texts.joinToString("\n") to attachments
    }

    /** Собеседник MMS: отправитель входящего, первый получатель исходящего. */
    private fun mmsAddress(context: Context, mmsId: Long, outgoing: Boolean): String? = runCatching {
        val type = if (outgoing) ADDR_TO else ADDR_FROM
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"),
            arrayOf("address", "type"),
            "type = ?", arrayOf(type.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val a = c.string("address")
                if (!a.isNullOrBlank() && a != INSERT_ADDRESS_TOKEN) return@use a
            }
            null
        }
    }.getOrNull()

    /** Отправитель исходящего MMS в хранилище: «подставит сеть». */
    const val INSERT_ADDRESS_TOKEN = "insert-address-token"

    private fun smsStatus(type: Int, status: Int): Message.Status = when (type) {
        SMS_INBOX -> Message.Status.RECEIVED
        SMS_OUTBOX, SMS_QUEUED -> Message.Status.SENDING
        SMS_FAILED -> Message.Status.FAILED
        else -> when (status) {
            STATUS_COMPLETE -> Message.Status.DELIVERED
            STATUS_FAILED -> Message.Status.FAILED
            else -> Message.Status.SENT
        }
    }

    private fun mmsStatus(box: Int, type: Int): Message.Status = when {
        box == MMS_INBOX && type == MMS_TYPE_NOTIFICATION -> Message.Status.NOT_DOWNLOADED
        box == MMS_INBOX -> Message.Status.RECEIVED
        box == MMS_OUTBOX -> Message.Status.SENDING
        box == MMS_FAILED -> Message.Status.FAILED
        else -> Message.Status.SENT
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЗАПИСЬ
    // ═══════════════════════════════════════════════════════════════════════

    /** Входящее SMS — в «Входящие»; нить хранилище подберёт по номеру. */
    fun insertIncomingSms(
        context: Context,
        address: String,
        body: String,
        dateSent: Long,
        subId: Int?,
        serviceCenter: String?,
        read: Boolean
    ): Uri? = runCatching {
        val values = ContentValues().apply {
            put("address", address)
            put("body", body)
            put("date", System.currentTimeMillis())
            put("date_sent", dateSent)
            put("type", SMS_INBOX)
            put("read", if (read) 1 else 0)
            put("seen", if (read) 1 else 0)
            serviceCenter?.let { put("service_center", it) }
            subId?.let { put("sub_id", it) }
        }
        context.contentResolver.insert(Uri.parse("content://sms/inbox"), values)
    }.onFailure { Logger.e(TAG, "Не удалось сохранить входящее SMS", it) }.getOrNull()

    /** Исходящее SMS — в «Исходящие» до ответа сети. */
    fun insertOutgoingSms(context: Context, address: String, body: String, subId: Int?, threadId: Long?): Uri? = runCatching {
        val values = ContentValues().apply {
            put("address", address)
            put("body", body)
            put("date", System.currentTimeMillis())
            put("type", SMS_OUTBOX)
            put("read", 1)
            put("seen", 1)
            put("status", STATUS_NONE)
            subId?.let { put("sub_id", it) }
            threadId?.let { put("thread_id", it) }
        }
        context.contentResolver.insert(Uri.parse("content://sms/outbox"), values)
    }.onFailure { Logger.e(TAG, "Не удалось сохранить исходящее SMS", it) }.getOrNull()

    /** Тип и статус доставки SMS после ответа сети. */
    fun updateSms(context: Context, uri: Uri, type: Int? = null, status: Int? = null, errorCode: Int? = null) {
        runCatching {
            val values = ContentValues().apply {
                type?.let { put("type", it) }
                status?.let { put("status", it) }
                errorCode?.let { put("error_code", it) }
            }
            if (values.size() > 0) context.contentResolver.update(uri, values, null, null)
        }.onFailure { Logger.w(TAG, "Не удалось обновить SMS", it) }
    }

    /** Тип SMS сейчас — чтобы не затереть «не отправлено» поздним «отправлено». */
    fun smsType(context: Context, uri: Uri): Int? = runCatching {
        context.contentResolver.query(uri, arrayOf("type"), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.int("type") else null
        }
    }.getOrNull()

    /** Нить прочитана: и SMS, и MMS. */
    fun markRead(context: Context, threadId: Long) {
        if (!SmsRole.isDefault(context)) return
        val values = ContentValues().apply { put("read", 1); put("seen", 1) }
        val args = arrayOf(threadId.toString())
        runCatching {
            context.contentResolver.update(SMS_URI, values, "thread_id = ? AND read = 0", args)
            context.contentResolver.update(MMS_URI, values, "thread_id = ? AND read = 0", args)
        }.onFailure { Logger.w(TAG, "Не удалось отметить прочитанным", it) }
    }

    /** Удалить сообщения по ключам «sms:12», «mms:7». */
    fun delete(context: Context, keys: Collection<String>): Int {
        if (!SmsRole.isDefault(context) || keys.isEmpty()) return 0
        var deleted = 0
        keys.forEach { key ->
            val id = key.substringAfter(':').toLongOrNull() ?: return@forEach
            val uri = if (key.startsWith("mms:")) ContentUris.withAppendedId(MMS_URI, id)
            else ContentUris.withAppendedId(SMS_URI, id)
            deleted += runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0)
        }
        Logger.i(TAG, "Удалено сообщений: $deleted")
        return deleted
    }

    /** Удалить переписку целиком. */
    fun deleteThread(context: Context, threadId: Long): Boolean {
        if (!SmsRole.isDefault(context)) return false
        return runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(Uri.parse("content://mms-sms/conversations"), threadId),
                null, null
            ) > 0
        }.onFailure { Logger.w(TAG, "Не удалось удалить переписку", it) }.getOrDefault(false)
    }

    /**
     * Изменения хранилища: пришло, ушло, прочитано, удалено. Сигнал
     * без данных — журнал и переписка перечитывают сами.
     */
    fun changes(context: Context): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(ALL_URI, true, observer)
            context.contentResolver.registerContentObserver(SMS_URI, true, observer)
            context.contentResolver.registerContentObserver(MMS_URI, true, observer)
        }.isSuccess
        if (!registered) Logger.w(TAG, "Не удалось следить за сообщениями")
        awaitClose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }

    // ── Курсор ─────────────────────────────────────────────────────────────

    private fun Cursor.index(name: String) = getColumnIndex(name)
    private fun Cursor.string(name: String): String? = index(name).takeIf { it >= 0 }?.let { getString(it) }
    private fun Cursor.long(name: String): Long = index(name).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L
    private fun Cursor.int(name: String): Int = index(name).takeIf { it >= 0 }?.let { getInt(it) } ?: 0
    private fun Cursor.intOrNull(name: String): Int? =
        index(name).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) }
}
