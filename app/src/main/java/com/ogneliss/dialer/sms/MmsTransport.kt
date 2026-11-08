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
 * MMS: ПРИЁМ И ОТПРАВКА (2.0.0).
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
 * 2.1.0: «получено» — только после записи в хранилище (было — сразу после
 * разбора: не записалось — центр уже считал сообщение доставленным
 * и удалял его). Не скачалось — повторы и одно уведомление «Загрузить»
 * (MmsDownloads); пока идёт скачивание, в переписке — строка
 * «MMS не загружено» ([storeNotificationInd]).
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
    private const val EXTRA_FAILED_ADDRESS = "failed_address"
    private const val EXTRA_NOTIFY_RESP = "notify_resp"
    private const val EXTRA_KEY = "pending_key"
    private val requestCodes = AtomicInteger((System.currentTimeMillis() % 100_000).toInt() + 50_000)

    /*
     * РАЗМЕР MMS — ПО НАСТРОЙКАМ ОПЕРАТОРА (2.1.0).
     *
     * Было: каждая картинка ужималась до 280 КБ, а картинок до трёх — до
     * 840 КБ в одном MMS при пределе большинства операторов 300 КБ: такое
     * сообщение центр MMS отклонял. Стало: предел всего сообщения — из
     * настроек оператора для карты (SmsManager.getCarrierConfigValues,
     * MMS_CONFIG_MAX_MESSAGE_SIZE; не сказал — 300 КБ, как в AOSP),
     * картинки делят его поровну за вычетом текста и заголовков.
     * Оператор выключил MMS (MMS_CONFIG_MMS_ENABLED) — картинку
     * не прикрепить вовсе ([isEnabled]).
     */
    private const val DEFAULT_MAX_MESSAGE = 300 * 1024
    /** Заголовки, SMIL и заголовки частей — с запасом. */
    private const val PDU_OVERHEAD = 4 * 1024
    /** Меньше этого картинку не ужимаем — пусть лучше центр решит сам. */
    private const val MIN_IMAGE_BYTES = 16 * 1024
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

    /**
     * Скачать MMS по запомненному уведомлению (MmsDownloads.Pending).
     * Не началось — сразу неудача: повтор поставит MmsDownloads.
     */
    fun download(context: Context, pending: MmsDownloads.Pending) {
        runCatching {
            val file = File(dir(context), "in-${UUID.randomUUID()}.pdu")
            val uri = shareUri(context, file)
            grant(context, uri)
            val intent = Intent(context, DownloadedReceiver::class.java).setAction(ACTION_DOWNLOADED)
                .putExtra(EXTRA_FILE, file.absolutePath)
                .putExtra(EXTRA_LOCATION, pending.contentLocation)
                .putExtra(EXTRA_TX, pending.transactionId)
                .putExtra(EXTRA_SUB, pending.subId ?: -1)
                .putExtra(EXTRA_KEY, pending.key)
            val pi = PendingIntent.getBroadcast(context, requestCodes.incrementAndGet(), intent, mutableFlags())
            SmsSender.managerFor(context, pending.subId)
                .downloadMultimediaMessage(context, pending.contentLocation, uri, null, pi)
            Logger.i(TAG, "MMS скачивается, попытка ${pending.attempts + 1}")
        }.onFailure {
            Logger.e(TAG, "Не удалось начать скачивание MMS", it)
            MmsDownloads.onFailed(context, pending.key, "не началось", blocked = false)
        }
    }

    /**
     * СТРОКА «MMS НЕ ЗАГРУЖЕНО» (2.1.0) — уведомление сети (m-notification-ind)
     * в хранилище, как у AOSP «Сообщений»: переписка сразу показывает,
     * что сообщение есть, а касание скачивает его (MmsDownloads.downloadFromInbox).
     * Прочитанной: пока идут повторы, счётчик непрочитанных не дёргается;
     * кончились попытки — станет непрочитанной (MmsDownloads.onFailed).
     * Скачалось — строка удаляется, на её месте — само сообщение.
     *
     * @return номер строки; null — отправитель неизвестен или не записалось
     */
    fun storeNotificationInd(context: Context, n: MmsPdu.Notification, subId: Int?): Long? = runCatching {
        val from = n.from?.takeIf { it.isNotBlank() } ?: return null
        val thread = Telephony.Threads.getOrCreateThreadId(context, from)
        val values = ContentValues().apply {
            put("thread_id", thread)
            put("date", System.currentTimeMillis() / 1000L)
            put("msg_box", MessageStore.MMS_INBOX)
            put("read", 1)
            put("seen", 1)
            put("m_type", MessageStore.MMS_TYPE_NOTIFICATION)
            put("ct_l", n.contentLocation)
            put("tr_id", n.transactionId)
            n.size?.let { put("m_size", it) }
            put("m_cls", n.messageClass ?: "personal")
            put("v", 0x12)
            n.subject?.takeIf { it.isNotBlank() }?.let { put("sub", it); put("sub_cs", 106) }
            subId?.let { put("sub_id", it) }
        }
        val uri = context.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        writeAddress(context, id, from, MessageStore.ADDR_FROM)
        id
    }.onFailure { Logger.w(TAG, "Строка «MMS не загружено» не записана", it) }.getOrNull()

    /** Отправитель MMS по строке хранилища. */
    fun senderOf(context: Context, mmsId: Long): String? = runCatching {
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"), arrayOf("address"),
            "type = ?", arrayOf(MessageStore.ADDR_FROM.toString()), null
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
    }.getOrNull()

    /**
     * Это MMS уже записано (центр прислал уведомление второй раз, и оно
     * скачалось дважды) — его строка; иначе null.
     */
    private fun storedAlready(context: Context, messageId: String?): Uri? {
        if (messageId.isNullOrBlank()) return null
        return runCatching {
            context.contentResolver.query(
                MessageStore.MMS_URI, arrayOf("_id"),
                "m_id = ? AND m_type = ${MessageStore.MMS_TYPE_RETRIEVE_CONF}", arrayOf(messageId), null
            )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(MessageStore.MMS_URI, c.getLong(0)) else null }
        }.getOrNull()
    }

    /** Записать скачанное MMS во «Входящие» от [from]; null — не удалось. */
    fun storeIncoming(context: Context, m: MmsPdu.Message, from: String, subId: Int?, read: Boolean): Uri? = runCatching {
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
        // Счётчик на значке «SMS» (2.1.0): пришло MMS
        com.ogneliss.dialer.widget.SectionWidgets.updateSms(context)
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

    /** Настройки MMS оператора карты: разрешены ли и предел сообщения, байт. */
    private class CarrierMms(val enabled: Boolean, val maxBytes: Int)

    private fun carrierMms(context: Context, subId: Int?): CarrierMms {
        val config = runCatching { SmsSender.managerFor(context, subId).carrierConfigValues }
            .onFailure { Logger.w(TAG, "Настройки MMS оператора не прочитаны", it) }
            .getOrNull()
        val enabled = config?.getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, true) ?: true
        val max = config?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, DEFAULT_MAX_MESSAGE)
            ?.takeIf { it > 0 } ?: DEFAULT_MAX_MESSAGE
        return CarrierMms(enabled, max)
    }

    /** Оператор разрешает MMS на этой карте — иначе картинку не прикрепить (2.1.0). */
    fun isEnabled(context: Context, subId: Int?): Boolean = carrierMms(context, subId).enabled

    /**
     * Отправить MMS: [text] и картинки [images] (адреса content://),
     * на [address]. Картинки делят предел MMS оператора поровну.
     */
    fun send(context: Context, address: String, text: String, images: List<Uri>, subId: Int?): Boolean {
        if (!SmsRole.isDefault(context)) {
            Logger.w(TAG, "MMS отправляет только приложение SMS по умолчанию")
            return false
        }
        val carrier = carrierMms(context, subId)
        if (!carrier.enabled) {
            Logger.w(TAG, "MMS выключено оператором — не отправляется")
            return false
        }
        return runCatching {
            val textBytes = text.toByteArray(Charsets.UTF_8).size
            val budget = (carrier.maxBytes - PDU_OVERHEAD - textBytes) / images.size.coerceAtLeast(1)
            val perImage = budget.coerceAtLeast(MIN_IMAGE_BYTES)
            val parts = images.mapIndexedNotNull { i, uri -> imagePart(context, uri, i, perImage) }
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
                // Адрес — чтобы уведомление о неотправленном открыло переписку (2.1.0)
                .putExtra(EXTRA_FAILED_ADDRESS, address)
                .apply { stored?.let { putExtra(EXTRA_URI, it.toString()) } }
            val pi = PendingIntent.getBroadcast(context, requestCodes.incrementAndGet(), intent, mutableFlags())
            managerFor(context, subId).sendMultimediaMessage(context, uri, null, null, pi)
            Logger.i(TAG, "MMS отправляется: картинок ${parts.size}, ${pdu.size} Б из ${carrier.maxBytes} Б оператора")
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
     * повернуть по EXIF и сжать в JPEG, пока не уложится в [maxBytes]
     * (2.1.0: доля предела оператора, было — 280 КБ на каждую).
     */
    private fun imagePart(context: Context, uri: Uri, index: Int, maxBytes: Int): MmsPdu.Part? = runCatching {
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
        /*
         * Сначала качество (85 → 35), потом размер: не уложилось — стороны
         * ×0,7 и снова. Было: одно уменьшение вдвое при 70 % и отправка
         * как есть, даже если и это не уложилось.
         */
        var bytes = ByteArray(0)
        var scaled = bitmap
        for (round in 0 until 6) {
            var quality = 85
            while (quality >= 35) {
                bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                if (bytes.size <= maxBytes) break
                quality -= 10
            }
            if (bytes.size <= maxBytes || minOf(scaled.width, scaled.height) < 160) break
            scaled = Bitmap.createScaledBitmap(
                scaled, (scaled.width * 0.7f).toInt().coerceAtLeast(1), (scaled.height * 0.7f).toInt().coerceAtLeast(1), true
            )
        }
        if (bytes.size > maxBytes) Logger.w(TAG, "Картинка MMS больше доли предела: ${bytes.size} из $maxBytes Б")
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
            val subId = SmsDeliverReceiver.subscriptionOf(intent)
            /*
             * 2.1.0: запись в хранилище — не в главном потоке (было: скачивание
             * начиналось прямо здесь), и уведомление сети запоминается до
             * успешной записи сообщения (MmsDownloads) — повторить есть чем.
             */
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    suspendRunCatching {
                        val inbox = MmsDownloads.knownInboxId(context, notification)
                            ?: storeNotificationInd(context, notification, subId)
                        MmsDownloads.start(context, notification, subId, inbox)
                    }.onFailure { Logger.e(TAG, "Уведомление о MMS не обработано", it) }
                } finally {
                    pending.finish()
                }
            }
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
            val key = intent.getStringExtra(EXTRA_KEY)
            // Код — до goAsync: после него приёмник его уже не знает
            val code = resultCode
            val ok = code == Activity.RESULT_OK
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val file = File(path)
                try {
                    suspendRunCatching { handle(context, file, ok, code, tx, subId, key) }
                        .onFailure {
                            Logger.e(TAG, "Скачанное MMS не обработано", it)
                            key?.let { k -> MmsDownloads.onFailed(context, k, "сбой обработки", blocked = false) }
                        }
                } finally {
                    file.delete()
                    pending.finish()
                }
            }
        }

        private suspend fun handle(
            context: Context,
            file: File,
            ok: Boolean,
            code: Int,
            tx: String,
            subId: Int?,
            key: String?
        ) {
            val known = key?.let { MmsDownloads.get(context, it) }
            val expectedFrom = known?.from.orEmpty()
            suspend fun blocked(from: String) =
                from.isNotBlank() && suspendRunCatching { blocking.isBlocked(from) }.getOrDefault(false)
            fun failed(reason: String, isBlocked: Boolean) {
                Logger.w(TAG, "MMS не скачано: $reason")
                key?.let { MmsDownloads.onFailed(context, it, reason, isBlocked) }
            }
            if (!ok || !file.exists() || file.length() == 0L) {
                failed("код $code", blocked(expectedFrom))
                return
            }
            val message = MmsPdu.parseMessage(file.readBytes())
            if (message == null || message.type != MmsPdu.TYPE_RETRIEVE_CONF) {
                failed("не разобрано", blocked(expectedFrom))
                return
            }
            // Отправитель неизвестен и сети, и уведомлению — «Неизвестный», как у SMS
            val from = message.from?.takeIf { it.isNotBlank() } ?: expectedFrom.ifBlank {
                context.getString(com.ogneliss.dialer.R.string.sms_unknown_sender)
            }
            val isBlocked = blocked(from)
            val uri = storedAlready(context, message.messageId)
                ?: storeIncoming(context, message, from, subId, read = isBlocked)
            if (uri == null) {
                // Не записано — «получено» не говорим: центр MMS сохранит сообщение для повтора
                failed("не записано", isBlocked)
                return
            }
            sendNotifyResp(context, tx.ifBlank { message.transactionId.orEmpty() }, subId)
            key?.let { MmsDownloads.onDone(context, it) }
            Logger.i(TAG, "MMS сохранено: частей ${message.parts.size}")
            if (!isBlocked) {
                MessageNotifications.showForAddress(
                    context, from,
                    MessageNotifications.Incoming(
                        body = message.bodyText(),
                        date = System.currentTimeMillis(),
                        subId = subId,
                        key = runCatching { Message.keyOf(true, ContentUris.parseId(uri)) }.getOrNull(),
                        isMms = true,
                        hasPicture = message.parts.any { it.isImage }
                    )
                )
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
                MessageNotifications.sendFailed(context, intent.getStringExtra(EXTRA_FAILED_ADDRESS))
            }
        }
    }
}
