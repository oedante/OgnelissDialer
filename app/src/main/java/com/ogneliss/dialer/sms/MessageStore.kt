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

    /**
     * ЗАПРОС, КОТОРЫЙ НЕ ПАДАЕТ ОТ ПРОШИВКИ (2.1.0).
     *
     * Было: один запрос со списком столбцов. На части прошивок столбца нет
     * (sub_id у старых, свои имена у Samsung и китайских), провайдер
     * бросает исключение — и раздел SMS был пуст целиком, хотя сообщения
     * на месте. Стало: при сбое — тот же запрос со всеми столбцами
     * (projection = null; отсутствующие столбцы читаются умолчаниями —
     * Cursor.string/int ниже), затем — без LIMIT в сортировке (её
     * принимает не каждый провайдер), а строк берётся не больше [limit].
     *
     * @return строки или null — не ответила ни одна попытка
     */
    private fun <T> rows(
        context: Context,
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        args: Array<String>?,
        order: String?,
        limit: Int?,
        what: String,
        map: (Cursor) -> T?
    ): List<T>? {
        val limited = if (limit != null && order != null) "$order LIMIT $limit" else order
        val attempts = listOf(projection to limited, null to limited, null to order).distinct()
        attempts.forEachIndexed { i, (proj, sort) ->
            val result = runCatching {
                val out = ArrayList<T>()
                context.contentResolver.query(uri, proj, selection, args, sort)?.use { c ->
                    while ((limit == null || out.size < limit) && c.moveToNext()) map(c)?.let { out += it }
                }
                out
            }
            result.getOrNull()?.let { list ->
                if (i > 0) Logger.i(TAG, "$what: прочитано с попытки ${i + 1}")
                return list
            }
            Logger.w(TAG, "$what: попытка ${i + 1} не удалась", result.exceptionOrNull())
        }
        return null
    }

    private fun querySms(context: Context, selection: String?, args: Array<String>?, limit: Int): List<Message> {
        // Черновики — не сообщения: в журнал и переписку не идут
        val sel = listOfNotNull(selection, "type != 3").joinToString(" AND ") { "($it)" }
        return rows(
            context, SMS_URI,
            arrayOf("_id", "thread_id", "address", "body", "date", "type", "status", "read", "sub_id"),
            sel, args, "date DESC", limit, "Чтение SMS"
        ) { c ->
            val type = c.int("type")
            val status = c.int("status")
            Message(
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
        }.orEmpty()
    }

    private fun queryMms(
        context: Context,
        selection: String?,
        args: Array<String>?,
        limit: Int,
        withParts: Boolean
    ): List<Message> {
        // Только сообщения, а не служебные отчёты о доставке и прочтении
        val sel = listOfNotNull(selection, "m_type IN ($MMS_TYPE_SEND_REQ, $MMS_TYPE_NOTIFICATION, $MMS_TYPE_RETRIEVE_CONF)")
            .joinToString(" AND ") { "($it)" }
        // Сначала строки, потом части и адреса: вложенный запрос при открытом курсоре
        // на части прошивок держит базу дольше, чем нужно
        class Row(val id: Long, val thread: Long, val date: Long, val box: Int, val read: Boolean,
                  val type: Int, val sub: Int?, val subject: String?)
        val found = rows(
            context, MMS_URI,
            arrayOf("_id", "thread_id", "date", "msg_box", "read", "sub", "sub_cs", "m_type", "sub_id"),
            sel, args, "date DESC", limit, "Чтение MMS"
        ) { c ->
            Row(
                id = c.long("_id"), thread = c.long("thread_id"), date = c.long("date"),
                box = c.int("msg_box"), read = c.int("read") != 0, type = c.int("m_type"),
                sub = c.intOrNull("sub_id")?.takeIf { it >= 0 },
                subject = c.string("sub")?.takeIf { it.isNotBlank() }
            )
        }.orEmpty()
        return found.map { r ->
            val outgoing = r.box != MMS_INBOX
            val parts = parts(context, r.id, textOnly = !withParts)
            Message(
                id = r.id,
                isMms = true,
                threadId = r.thread,
                address = mmsAddress(context, r.id, outgoing).orEmpty(),
                body = parts.first,
                date = r.date * 1000L,
                outgoing = outgoing,
                status = mmsStatus(r.box, r.type),
                read = r.read,
                subId = r.sub,
                subject = r.subject,
                attachments = parts.second
            )
        }
    }

    /** Текст и вложения MMS. [textOnly] — без вложений (для журнала: быстрее). */
    private fun parts(context: Context, mmsId: Long, textOnly: Boolean): Pair<String, List<Message.Attachment>> {
        val texts = ArrayList<String>()
        val attachments = ArrayList<Message.Attachment>()
        rows(
            context, MMS_PART_URI,
            arrayOf("_id", "ct", "text", "name", "cl"),
            "mid = ?", arrayOf(mmsId.toString()), "seq ASC", null, "Части MMS"
        ) { c ->
            val ct = c.string("ct").orEmpty().lowercase()
            when {
                ct == "text/plain" -> c.string("text")?.takeIf { it.isNotBlank() }?.let { texts += it }
                ct == "application/smil" -> Unit
                textOnly -> attachments += Message.Attachment("", ct, null)
                else -> attachments += Message.Attachment(
                    uri = ContentUris.withAppendedId(MMS_PART_URI, c.long("_id")).toString(),
                    contentType = ct,
                    name = c.string("name") ?: c.string("cl")
                )
            }
            Unit
        }
        return texts.joinToString("\n") to attachments
    }

    /** Собеседник MMS: отправитель входящего, первый получатель исходящего. */
    private fun mmsAddress(context: Context, mmsId: Long, outgoing: Boolean): String? {
        val type = if (outgoing) ADDR_TO else ADDR_FROM
        return rows(
            context, Uri.parse("content://mms/$mmsId/addr"),
            arrayOf("address", "type"),
            "type = ?", arrayOf(type.toString()), null, null, "Адрес MMS"
        ) { c -> c.string("address")?.takeIf { it.isNotBlank() && it != INSERT_ADDRESS_TOKEN } }
            ?.firstOrNull()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // СПИСОК ПЕРЕПИСОК ЦЕЛИКОМ (2.1.0)
    // ═══════════════════════════════════════════════════════════════════════

    /** Переписка по таблице нитей: последнее сообщение — кратко. */
    data class ThreadSummary(
        val threadId: Long,
        val address: String,
        val snippet: String,
        /** Миллисекунды. */
        val date: Long,
        val read: Boolean,
        /** null — прошивка не отдала число сообщений. */
        val messageCount: Int?,
        val hasAttachment: Boolean
    )

    /**
     * ВСЕ ПЕРЕПИСКИ — ПО ТАБЛИЦЕ НИТЕЙ (2.1.0).
     *
     * Было: список SMS собирался только из последних 3000 сообщений —
     * переписки старше выпадали из раздела совсем (у того, кто получает
     * много служебных SMS, — уже через пару месяцев). Стало: нити из
     * content://mms-sms/conversations?simple=true (одна строка на переписку:
     * дата, кратко последнее, прочитано ли), собеседник — по
     * canonical-addresses (recipient_ids — номера в этой таблице).
     * Точные данные последнего сообщения берутся из окна, как раньше
     * (MessagesViewModel), эти — только для переписок вне окна.
     *
     * Пустой список — хранилище не отдало таблицу (тогда раздел — только окно).
     */
    fun threadSummaries(context: Context): List<ThreadSummary> {
        if (!SmsRole.canRead(context)) return emptyList()
        val addresses = rows(
            context, Uri.parse("content://mms-sms/canonical-addresses"),
            arrayOf("_id", "address"), null, null, null, null, "Адреса нитей"
        ) { c -> c.long("_id") to c.string("address").orEmpty() }
            ?.toMap() ?: return emptyList()
        return rows(
            context, Uri.parse("content://mms-sms/conversations?simple=true"),
            arrayOf("_id", "date", "message_count", "recipient_ids", "snippet", "read", "has_attachment"),
            null, null, "date DESC", null, "Нити переписок"
        ) { c ->
            // Групповая переписка — по первому собеседнику: групповые MMS диалер не ведёт
            val first = c.string("recipient_ids").orEmpty().trim()
                .split(' ').firstOrNull()?.toLongOrNull()
            val address = first?.let { addresses[it] }?.takeIf { it.isNotBlank() } ?: return@rows null
            ThreadSummary(
                threadId = c.long("_id"),
                address = address,
                snippet = c.string("snippet").orEmpty(),
                date = c.long("date"),
                read = c.intOrNull("read")?.let { it != 0 } ?: true,
                messageCount = c.intOrNull("message_count"),
                hasAttachment = c.int("has_attachment") != 0
            )
        }.orEmpty()
    }

    /**
     * Непрочитанные входящие по нитям — для счётчиков списка (2.1.0):
     * по всему хранилищу, а не по окну последних сообщений. null — не ответило.
     */
    fun unreadCounts(context: Context): Map<Long, Int>? {
        if (!SmsRole.canRead(context)) return null
        val sms = rows(
            context, SMS_URI, arrayOf("thread_id"),
            "read = 0 AND type = $SMS_INBOX", null, null, null, "Непрочитанные SMS"
        ) { c -> c.long("thread_id") } ?: return null
        val mms = rows(
            context, MMS_URI, arrayOf("thread_id"),
            "read = 0 AND msg_box = $MMS_INBOX AND m_type IN ($MMS_TYPE_NOTIFICATION, $MMS_TYPE_RETRIEVE_CONF)",
            null, null, null, "Непрочитанные MMS"
        ) { c -> c.long("thread_id") } ?: return null
        return (sms + mms).groupingBy { it }.eachCount()
    }

    /** Самое новое сообщение нити, миллисекунды; null — не ответило. */
    private fun newestDate(context: Context, threadId: Long): Long? {
        val args = arrayOf(threadId.toString())
        val sms = rows(context, SMS_URI, arrayOf("date"), "thread_id = ?", args, "date DESC", 1, "Дата нити SMS") { c ->
            c.long("date")
        } ?: return null
        val mms = rows(context, MMS_URI, arrayOf("date"), "thread_id = ?", args, "date DESC", 1, "Дата нити MMS") { c ->
            c.long("date") * 1000L
        } ?: return null
        return (sms + mms).maxOrNull() ?: 0L
    }

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
        // Счётчик на значке «SMS» (2.1.0) — после каждого изменения хранилища
        .also { com.ogneliss.dialer.widget.SectionWidgets.updateSms(context) }

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
        com.ogneliss.dialer.widget.SectionWidgets.updateSms(context)
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
        com.ogneliss.dialer.widget.SectionWidgets.updateSms(context)
        return deleted
    }

    /**
     * Удалить переписку целиком.
     *
     * [upTo] (2.1.0) — дата последнего сообщения, которое человек видел,
     * удаляя. Удаление теперь идёт после «Отменить» (через несколько
     * секунд), и за это время в переписку могло прийти новое SMS — его
     * человек не видел и не удалял. Тогда стираются только сообщения
     * не новее [upTo], а переписка с новым остаётся (false: целиком
     * не удалена — закрепление и архив не снимаются).
     */
    fun deleteThread(context: Context, threadId: Long, upTo: Long? = null): Boolean =
        deleteThreadInner(context, threadId, upTo)
            .also { com.ogneliss.dialer.widget.SectionWidgets.updateSms(context) }

    private fun deleteThreadInner(context: Context, threadId: Long, upTo: Long?): Boolean {
        if (!SmsRole.isDefault(context)) return false
        val newest = if (upTo == null) null else newestDate(context, threadId)
        if (upTo != null && (newest == null || newest > upTo)) {
            val args = arrayOf(threadId.toString())
            val deleted = runCatching {
                context.contentResolver.delete(SMS_URI, "thread_id = ? AND date <= $upTo", args) +
                    // У MMS дата — в секундах
                    context.contentResolver.delete(MMS_URI, "thread_id = ? AND date <= ${upTo / 1000L}", args)
            }.onFailure { Logger.w(TAG, "Не удалось удалить переписку до даты", it) }.getOrDefault(0)
            // Новых не оказалось (дату прочитать не вышло) — переписки больше нет
            val gone = deleted > 0 && newestDate(context, threadId) == 0L
            Logger.i(TAG, "Переписка удалена до даты: сообщений $deleted" + if (gone) "" else ", новые оставлены")
            return gone
        }
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
