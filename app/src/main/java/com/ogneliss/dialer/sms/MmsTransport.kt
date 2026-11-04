package com.ogneliss.dialer.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.ogneliss.dialer.data.BlockingController
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.sms.mms.MmsPdu
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * MMS: ПРИЁМ И ОТПРАВКА (1.10.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК ПРИХОДИТ MMS
 * ══════════════════════════════════════════════════════════════════════════
 * 1. Сеть присылает короткое уведомление (WAP Push, m-notification-ind)
 *    с адресом, где лежит сообщение, — [PushReceiver].
 * 2. Приложение просит систему скачать его (downloadMultimediaMessage):
 *    скачивает служба телефона через точку доступа MMS оператора —
 *    приложению не нужен ни интернет, ни свои настройки APN.
 * 3. Скачанное (m-retrieve-conf) приходит в [DownloadedReceiver]:
 *    разбор (MmsPdu), запись в хранилище MMS, уведомление и ответ
 *    центру MMS «получено» (m-notifyresp-ind).
 *
 * ── Как уходит ─────────────────────────────────────────────────────────────
 * Сообщение собирается (MmsPdu.composeSendReq) и записывается
 * в «Исходящие»; система отправляет файл (sendMultimediaMessage), ответ —
 * в [SentReceiver]: «Отправленные» или «Не отправлено».
 *
 * Файлы обмена со службой телефона — в cache/mms/ через FileProvider
 * (res/xml/file_paths.xml): она пишет скачанное и читает отправляемое
 * по выданному на время доступу. После обработки файл удаляется.
 */
object MmsTransport {

    private const val TAG = "Mms"
    private const val ACTION_DOWNLOADED = "com.ogneliss.dialer.mms.DOWNLOADED"
    private const val ACTION_SENT = "com.ogneliss.dialer.mms.SENT"
    private const val EXTRA_FILE = "file"
    private const val EXTRA_LOCATION = "location"
    private const val EXTRA_TX = "transaction"
    private const val EXTRA_SUB = "sub"
    private const val EXTRA_URI = "message_uri"
    private const val EXTRA_NOTIFY_RESP = "notify_resp"
    private val requestCodes = AtomicInteger((System.currentTimeMillis() % 100_000).toInt() + 50_000)

    /** Картинка в MMS — не больше этого: операторы режут сообщения от 300 КБ. */
    private const val MAX_IMAGE_BYTES = 280 * 1024
    private const val MAX_IMAGE_SIDE = 1280

    private fun dir(context: Context) = File(context.cacheDir, "mms").apply { mkdirs() }

    private fun shareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    private fun mutableFlags() = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)

    /** Служба телефона читает и пишет файл — выдаём ей доступ явно. */
    private fun grant(context: Context, uri: Uri) {
        listOf("com.android.phone", "com.android.mms.service").forEach { pkg ->
            runCatching {
                context.grantUriPermission(
                    pkg, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПРИЁМ
    // ═══════════════════════════════════════════════════════════════════════

    /** Скачать MMS по уведомлению. */
    fun download(context: Context, notification: MmsPdu.Notification, subId: Int?) {
        runCatching {
            val file = File(dir(context), "in-${UUID.randomUUID()}.pdu")
            val uri = shareUri(context, file)
            grant(context, uri)
            val intent = Intent(context, DownloadedReceiver::class.java).setAction(ACTION_DOWNLOADED)
                .putExtra(EXTRA_FILE, file.absolutePath)
                .putExtra(EXTRA_LOCATION, notification.contentLocation)
                .putExtra(EXTRA_TX, notification.transactionId)
                .putExtra(EXTRA_SUB, subId ?: -1)
            val pi = PendingIntent.getBroadcast(context, requestCodes.incrementAndGet(), intent, mutableFlags())
            SmsSender.managerFor(context, subId)
                .downloadMultimediaMessage(context, notification.contentLocation, uri, null, pi)
            Logger.i(TAG, "MMS скачивается, размер ${notification.size ?: "?"} Б")
        }.onFailure { Logger.e(TAG, "Не удалось начать скачивание MMS", it) }
    }

    /** Записать скачанное MMS во «Входящие»; null — не удалось. */
    fun storeIncoming(context: Context, m: MmsPdu.Message, subId: Int?, read: Boolean): Uri? = runCatching {
        val from = m.from ?: return null
        val thread = Telephony.Threads.getOrCreateThreadId(context, from)
        val values = ContentValues().apply {
            put("thread_id", thread)
            put("date", System.currentTimeMillis() / 1000L)
            m.dateSeconds?.let { put("date_sent", it) }
            put("msg_box", MessageStore.MMS_INBOX)
            put("read", if (read) 1 else 0)
            put("seen", if (read) 1 else 0)
            m.messageId?.let { put("m_id", it) }
            m.subject?.let { put("sub", it); put("sub_cs", 106) }
            put("ct_t", m.contentType ?: MmsPdu.CT_MULTIPART_RELATED)
            put("m_type", MessageStore.MMS_TYPE_RETRIEVE_CONF)
            put("v", 0x12)
            m.transactionId?.let { put("tr_id", it) }
            put("m_cls", "personal")
            put("text_only", if (m.parts.all { it.isText || it.isSmil }) 1 else 0)
            subId?.let { put("sub_id", it) }
        }
        val uri = context.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        writeParts(context, id, m.parts)
        writeAddress(context, id, from, MessageStore.ADDR_FROM)
        m.to.forEach { writeAddress(context, id, it, MessageStore.ADDR_TO) }
        m.cc.forEach { writeAddress(context, id, it, MessageStore.ADDR_CC) }
        uri
    }.onFailure { Logger.e(TAG, "Не удалось сохранить MMS", it) }.getOrNull()

    private fun writeParts(context: Context, mmsId: Long, parts: List<MmsPdu.Part>) {
        val partsUri = Uri.parse("content://mms/$mmsId/part")
        parts.forEachIndexed { i, p ->
            val values = ContentValues().apply {
                put("mid", mmsId)
                put("seq", if (p.isSmil) -1 else i)
                put("ct", p.contentType)
                p.name?.let { put("name", it); put("fn", it) }
                p.contentId?.let { put("cid", it) }
                p.contentLocation?.let { put("cl", it) }
                if (p.isText || p.isSmil) {
                    put("chset", 106)
                    put("text", p.decoded())
                } else {
                    p.charset?.let { put("chset", it) }
                }
            }
            val partUri = context.contentResolver.insert(partsUri, values) ?: return@forEachIndexed
            if (!p.isText && !p.isSmil) {
                runCatching {
                    context.contentResolver.openOutputStream(partUri)?.use { it.write(p.data) }
                }.onFailure { Logger.w(TAG, "Не удалось записать вложение MMS", it) }
            }
        }
    }

    private fun writeAddress(context: Context, mmsId: Long, address: String, type: Int) {
        runCatching {
            context.contentResolver.insert(
                Uri.parse("content://mms/$mmsId/addr"),
                ContentValues().apply {
                    put("msg_id", mmsId)
                    put("address", address)
                    put("type", type)
                    put("charset", 106)
                }
            )
        }
    }

    /** Ответ центру MMS «получено». */
    private fun sendNotifyResp(context: Context, transactionId: String, subId: Int?) {
        if (transactionId.isBlank()) return
        runCatching {
            val file = File(dir(context), "resp-${UUID.randomUUID()}.pdu")
            file.writeBytes(MmsPdu.composeNotifyResp(transactionId))
            val uri = shareUri(context, file)
            grant(context, uri)
            val intent = Intent(context, SentReceiver::class.java).setAction(ACTION_SENT)
                .putExtra(EXTRA_FILE, file.absolutePath)
                .putExtra(EXTRA_NOTIFY_RESP, true)
            val pi = PendingIntent.getBroadcast(context, requestCodes.incrementAndGet(), intent, mutableFlags())
            SmsSender.managerFor(context, subId).sendMultimediaMessage(context, uri, null, null, pi)
        }.onFailure { Logger.w(TAG, "Не удалось ответить центру MMS", it) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ОТПРАВКА
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Отправить MMS: [text] и картинки [images] (адреса content://),
     * на [address]. Картинки ужимаются до [MAX_IMAGE_BYTES].
     */
    fun send(context: Context, address: String, text: String, images: List<Uri>, subId: Int?): Boolean {
        if (!SmsRole.isDefault(context)) {
            Logger.w(TAG, "MMS отправляет только приложение SMS по умолчанию")
            return false
        }
        return runCatching {
            val parts = images.mapIndexedNotNull { i, uri -> imagePart(context, uri, i) }
            if (parts.isEmpty() && text.isBlank()) return false
            val tx = "T" + java.lang.Long.toHexString(System.currentTimeMillis())
            val pdu = MmsPdu.composeSendReq(
                to = listOf(address), text = text, attachments = parts, transactionId = tx,
                deliveryReport = SmsSettings.deliveryReports(context)
            )
            val stored = storeOutgoing(context, address, text, parts, subId, tx)
            val file = File(dir(context), "out-${UUID.randomUUID()}.pdu")
            file.writeBytes(pdu)
            val uri = shareUri(context, file)
            grant(context, uri)
            val intent = Intent(context, SentReceiver::class.java).setAction(ACTION_SENT)
                .putExtra(EXTRA_FILE, file.absolutePath)
                .apply { stored?.let { putExtra(EXTRA_URI, it.toString()) } }
            val pi = PendingIntent.getBroadcast(context, requestCodes.incrementAndGet(), intent, mutableFlags())
            managerFor(context, subId).sendMultimediaMessage(context, uri, null, null, pi)
            Logger.i(TAG, "MMS отправляется: картинок ${parts.size}, ${pdu.size} Б")
            true
        }.getOrElse {
            Logger.e(TAG, "Не удалось отправить MMS", it)
            false
        }
    }

    private fun managerFor(context: Context, subId: Int?): SmsManager = SmsSender.managerFor(context, subId)

    private fun storeOutgoing(
        context: Context,
        address: String,
        text: String,
        images: List<MmsPdu.Part>,
        subId: Int?,
        transactionId: String
    ): Uri? = runCatching {
        val thread = Telephony.Threads.getOrCreateThreadId(context, address)
        val values = ContentValues().apply {
            put("thread_id", thread)
            put("date", System.currentTimeMillis() / 1000L)
            put("msg_box", MessageStore.MMS_OUTBOX)
            put("read", 1)
            put("seen", 1)
            put("ct_t", MmsPdu.CT_MULTIPART_RELATED)
            put("m_type", MessageStore.MMS_TYPE_SEND_REQ)
            put("v", 0x12)
            put("tr_id", transactionId)
            put("m_cls", "personal")
            put("text_only", if (images.isEmpty()) 1 else 0)
            subId?.let { put("sub_id", it) }
        }
        val uri = context.contentResolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        val parts = ArrayList<MmsPdu.Part>()
        images.forEachIndexed { i, p ->
            parts += MmsPdu.Part(p.contentType, p.data, name = p.name, contentId = "<image$i>",
                contentLocation = p.contentLocation ?: "image$i")
        }
        if (text.isNotBlank()) {
            parts += MmsPdu.Part(MmsPdu.CT_TEXT_PLAIN, text.toByteArray(), contentId = "<text0>",
                contentLocation = MmsPdu.TEXT_PART_NAME, charset = 106)
        }
        writeParts(context, id, parts)
        writeAddress(context, id, MessageStore.INSERT_ADDRESS_TOKEN, MessageStore.ADDR_FROM)
        writeAddress(context, id, address, MessageStore.ADDR_TO)
        uri
    }.onFailure { Logger.w(TAG, "Не удалось сохранить исходящее MMS", it) }.getOrNull()

    /**
     * Картинка для MMS: уменьшить до [MAX_IMAGE_SIDE] по большей стороне,
     * повернуть по EXIF и сжать в JPEG, пока не уложится в [MAX_IMAGE_BYTES].
     */
    private fun imagePart(context: Context, uri: Uri, index: Int): MmsPdu.Part? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (android.media.ExifInterface(stream).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
            )) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        var bitmap = decoded
        val k = MAX_IMAGE_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (k < 1f || rotation != 0f) {
            val m = Matrix().apply {
                if (k < 1f) postScale(k, k)
                if (rotation != 0f) postRotate(rotation)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }
        var quality = 85
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            quality -= 10
        } while (bytes.size > MAX_IMAGE_BYTES && quality >= 35)
        if (bytes.size > MAX_IMAGE_BYTES) {
            // Всё ещё велико — вдвое меньше по сторонам
            val half = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
            bytes = ByteArrayOutputStream().also { half.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        }
        MmsPdu.Part("image/jpeg", bytes, name = "image$index.jpg", contentLocation = "image$index.jpg")
    }.onFailure { Logger.w(TAG, "Не удалось подготовить картинку для MMS", it) }.getOrNull()

    // ═══════════════════════════════════════════════════════════════════════
    // ПРИЁМНИКИ
    // ═══════════════════════════════════════════════════════════════════════

    /** Уведомление о новом MMS (WAP_PUSH_DELIVER) — только приложению SMS по умолчанию. */
    class PushReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
            val data = intent.getByteArrayExtra("data") ?: return
            val notification = MmsPdu.parseNotification(data) ?: run {
                Logger.w(TAG, "WAP Push — не уведомление MMS")
                return
            }
            download(context, notification, SmsDeliverReceiver.subscriptionOf(intent))
        }
    }

    /** Скачанное MMS: разбор, запись, уведомление, «получено». */
    @AndroidEntryPoint
    class DownloadedReceiver : BroadcastReceiver() {

        @Inject lateinit var blocking: BlockingController

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_DOWNLOADED) return
            val path = intent.getStringExtra(EXTRA_FILE) ?: return
            val tx = intent.getStringExtra(EXTRA_TX).orEmpty()
            val subId = intent.getIntExtra(EXTRA_SUB, -1).takeIf { it >= 0 }
            // Код — до goAsync: после него приёмник его уже не знает
            val code = resultCode
            val ok = code == Activity.RESULT_OK
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val file = File(path)
                try {
                    if (!ok || !file.exists() || file.length() == 0L) {
                        Logger.w(TAG, "MMS не скачано: код $code")
                        MessageNotifications.downloadFailed(context)
                        return@launch
                    }
                    val message = MmsPdu.parseMessage(file.readBytes())
                    if (message == null || message.type != MmsPdu.TYPE_RETRIEVE_CONF) {
                        Logger.w(TAG, "Скачанное MMS не разобрано")
                        MessageNotifications.downloadFailed(context)
                        return@launch
                    }
                    val from = message.from.orEmpty()
                    val blocked = from.isNotBlank() &&
                        suspendRunCatching { blocking.isBlocked(from) }.getOrDefault(false)
                    val uri = storeIncoming(context, message, subId, read = blocked)
                    sendNotifyResp(context, tx.ifBlank { message.transactionId.orEmpty() }, subId)
                    Logger.i(TAG, "MMS сохранено: частей ${message.parts.size}")
                    if (uri != null && !blocked && from.isNotBlank()) {
                        MessageNotifications.showForAddress(context, from)
                    }
                } finally {
                    file.delete()
                    pending.finish()
                }
            }
        }
    }

    /** Ответ на отправку MMS (и на «получено» — его только убираем). */
    class SentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_SENT) return
            intent.getStringExtra(EXTRA_FILE)?.let { File(it).delete() }
            if (intent.getBooleanExtra(EXTRA_NOTIFY_RESP, false)) return
            val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: return
            val ok = resultCode == Activity.RESULT_OK
            val conf = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)?.let { MmsPdu.parseMessage(it) }
            val accepted = ok && (conf?.responseStatus == null || conf.responseStatus == MmsPdu.RESPONSE_OK)
            runCatching {
                val values = ContentValues().apply {
                    put("msg_box", if (accepted) MessageStore.MMS_SENT else MessageStore.MMS_FAILED)
                    conf?.messageId?.let { put("m_id", it) }
                    conf?.responseStatus?.let { put("resp_st", it) }
                }
                context.contentResolver.update(uri, values, null, null)
            }.onFailure { Logger.w(TAG, "Не удалось обновить исходящее MMS", it) }
            if (!accepted) {
                Logger.w(TAG, "MMS не отправлено: код $resultCode, ответ ${conf?.responseStatus}")
                MessageNotifications.sendFailed(context)
            }
        }
    }
}
