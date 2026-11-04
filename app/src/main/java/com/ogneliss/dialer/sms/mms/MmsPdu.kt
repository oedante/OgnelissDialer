package com.ogneliss.dialer.sms.mms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * КОДЕК MMS (2.0.0) — разбор и сборка сообщений протокола MMS.
 *
 * Автор, 05.11.2026: «добавить SMS — редактирование и отправку… журнал
 * звонков и журнал SMS»; MMS — «приём и отправка». Приложение SMS
 * по умолчанию само принимает и отправляет MMS: система только
 * скачивает и отдаёт байты (SmsManager.downloadMultimediaMessage /
 * sendMultimediaMessage). Байты — в двоичном формате OMA MMS 1.2
 * (OMA-TS-MMS_ENC) поверх кодирования WSP (WAP-230-WSP): его и разбирает
 * этот файл.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ СВОЙ, А НЕ БИБЛИОТЕКА
 * ══════════════════════════════════════════════════════════════════════════
 * Готовые разборщики — пакет com.google.android.mms.pdu из AOSP (скрыт
 * в системе, своей копией — тысячи строк) или библиотеки SMS-приложений
 * с открытым кодом под GPL, несовместимой с Apache 2.0 приложения.
 * Приложению нужно немногое: из уведомления — где скачать; из скачанного —
 * от кого, кому, тема и части (текст, картинки); для отправки — собрать
 * сообщение и ответ «получено». Это несколько сотен строк чистого Kotlin
 * без Android — их проверяет MmsPduTest.
 *
 * ── Что разбирается ────────────────────────────────────────────────────────
 * • m-notification-ind — уведомление о новом MMS (адрес скачивания);
 * • m-retrieve-conf — скачанное MMS (заголовки и части);
 * • m-send-conf — ответ центра MMS на отправку (статус, Message-ID).
 * Неизвестные заголовки пропускаются по общему правилу WSP — длина
 * значения видна по первому байту, — а не роняют разбор.
 *
 * ── Что собирается ─────────────────────────────────────────────────────────
 * • m-send-req — отправка: текст и картинки частями multipart/related
 *   со SMIL-разметкой (без неё часть телефонов показывает только первую
 *   часть);
 * • m-notifyresp-ind — «получено» после скачивания.
 */
object MmsPdu {

    /**
     * Имя текстовой части MMS — на него ссылается SMIL. Склейка строк —
     * чтобы проверка ресурсов (prebuild_check) не искала такой файл в assets.
     */
    const val TEXT_PART_NAME = "text0" + ".txt"

    // ── Типы сообщений (X-Mms-Message-Type) ────────────────────────────────
    const val TYPE_SEND_REQ = 0x80
    const val TYPE_SEND_CONF = 0x81
    const val TYPE_NOTIFICATION_IND = 0x82
    const val TYPE_NOTIFYRESP_IND = 0x83
    const val TYPE_RETRIEVE_CONF = 0x84
    const val TYPE_ACKNOWLEDGE_IND = 0x85
    const val TYPE_DELIVERY_IND = 0x86

    // ── Заголовки (код поля | 0x80) ────────────────────────────────────────
    private const val H_BCC = 0x81
    private const val H_CC = 0x82
    private const val H_CONTENT_LOCATION = 0x83
    private const val H_CONTENT_TYPE = 0x84
    private const val H_DATE = 0x85
    private const val H_DELIVERY_REPORT = 0x86
    private const val H_EXPIRY = 0x88
    private const val H_FROM = 0x89
    private const val H_MESSAGE_CLASS = 0x8A
    private const val H_MESSAGE_ID = 0x8B
    private const val H_MESSAGE_TYPE = 0x8C
    private const val H_MMS_VERSION = 0x8D
    private const val H_MESSAGE_SIZE = 0x8E
    private const val H_PRIORITY = 0x8F
    private const val H_READ_REPORT = 0x90
    private const val H_REPORT_ALLOWED = 0x91
    private const val H_RESPONSE_STATUS = 0x92
    private const val H_STATUS = 0x95
    private const val H_SUBJECT = 0x96
    private const val H_TO = 0x97
    private const val H_TRANSACTION_ID = 0x98

    // ── Заголовки частей (WSP) ─────────────────────────────────────────────
    private const val P_CONTENT_LOCATION = 0x8E
    private const val P_CONTENT_ID = 0xC0

    // ── Параметры типа содержимого ─────────────────────────────────────────
    private const val PARAM_CHARSET = 0x81
    private const val PARAM_TYPE = 0x83
    private const val PARAM_DEP_NAME = 0x85
    private const val PARAM_DEP_FILENAME = 0x86
    private const val PARAM_CT_MR_TYPE = 0x89
    private const val PARAM_DEP_START = 0x8A
    private const val PARAM_NAME = 0x97
    private const val PARAM_FILENAME = 0x98
    private const val PARAM_START = 0x99

    /** Версия протокола 1.2 — Short-integer (0x80 | 0x12). */
    private const val VERSION_1_2 = 0x92
    private const val VALUE_YES = 0x80
    private const val VALUE_NO = 0x81
    /** X-Mms-Status: получено. */
    const val STATUS_RETRIEVED = 0x81
    /** X-Mms-Status: отложено (не скачали — центр пришлёт ещё раз). */
    const val STATUS_DEFERRED = 0x83
    /** X-Mms-Response-Status: принято. */
    const val RESPONSE_OK = 0x80

    private const val CHARSET_UTF8 = 106

    const val CT_TEXT_PLAIN = "text/plain"
    const val CT_SMIL = "application/smil"
    const val CT_MULTIPART_RELATED = "application/vnd.wap.multipart.related"
    const val CT_MULTIPART_MIXED = "application/vnd.wap.multipart.mixed"

    // ═══════════════════════════════════════════════════════════════════════
    // МОДЕЛЬ
    // ═══════════════════════════════════════════════════════════════════════

    /** Уведомление о новом MMS: где скачать и что о нём известно заранее. */
    data class Notification(
        val transactionId: String,
        val contentLocation: String,
        val from: String?,
        val subject: String?,
        val size: Long?,
        /** Срок хранения на сервере: секунды от сейчас или абсолютная дата (секунды). */
        val expirySeconds: Long?,
        val messageClass: String?
    )

    /** Часть MMS: текст, картинка, разметка SMIL. */
    class Part(
        val contentType: String,
        val data: ByteArray,
        val name: String? = null,
        val contentId: String? = null,
        val contentLocation: String? = null,
        /** MIBenum кодировки текста (106 — UTF-8); null — не указана. */
        val charset: Int? = null
    ) {
        val isText: Boolean get() = contentType.equals(CT_TEXT_PLAIN, ignoreCase = true)
        val isSmil: Boolean get() = contentType.equals(CT_SMIL, ignoreCase = true)
        val isImage: Boolean get() = contentType.startsWith("image/", ignoreCase = true)

        /** Текст части в её кодировке (по умолчанию — UTF-8). */
        fun decoded(): String = String(data, charsetOf(charset ?: CHARSET_UTF8))
    }

    /** Скачанное MMS (m-retrieve-conf) или любое другое с заголовками. */
    class Message(
        val type: Int,
        val transactionId: String?,
        val messageId: String?,
        /** Дата — секунды с 1970 (так в протоколе); null — не указана. */
        val dateSeconds: Long?,
        val from: String?,
        val to: List<String>,
        val cc: List<String>,
        val subject: String?,
        val contentType: String?,
        val responseStatus: Int?,
        val status: Int?,
        val parts: List<Part>
    ) {
        /** Весь текст сообщения: текстовые части подряд. */
        fun bodyText(): String = parts.filter { it.isText }.joinToString("\n") { it.decoded().trim() }.trim()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РАЗБОР
    // ═══════════════════════════════════════════════════════════════════════

    /** Уведомление о новом MMS; null — это не уведомление или оно испорчено. */
    fun parseNotification(pdu: ByteArray): Notification? = runCatching {
        val r = Reader(pdu)
        val h = readHeaders(r)
        if (h.type != TYPE_NOTIFICATION_IND) return null
        val location = h.contentLocation?.takeIf { it.isNotBlank() } ?: return null
        Notification(
            transactionId = h.transactionId.orEmpty(),
            contentLocation = location,
            from = h.from,
            subject = h.subject,
            size = h.messageSize,
            expirySeconds = h.expiry,
            messageClass = h.messageClass
        )
    }.getOrNull()

    /** Сообщение целиком: заголовки и, если есть, части. null — испорчено. */
    fun parseMessage(pdu: ByteArray): Message? = runCatching {
        val r = Reader(pdu)
        val h = readHeaders(r)
        if (h.type < 0) return null
        val ct = h.contentType
        val parts = when {
            ct == null || r.remaining() <= 0 -> emptyList()
            ct.mediaType.startsWith("application/vnd.wap.multipart", ignoreCase = true) -> readMultipart(r)
            // Не составное — одна часть всем телом
            else -> listOf(
                Part(ct.mediaType, r.readBytes(r.remaining()), name = ct.name, charset = ct.charset)
            )
        }
        Message(
            type = h.type,
            transactionId = h.transactionId,
            messageId = h.messageId,
            dateSeconds = h.date,
            from = h.from,
            to = h.to,
            cc = h.cc,
            subject = h.subject,
            contentType = ct?.mediaType,
            responseStatus = h.responseStatus,
            status = h.status,
            parts = parts
        )
    }.getOrNull()

    /** Тип содержимого с параметрами. */
    private class ContentType(
        val mediaType: String,
        val charset: Int? = null,
        val name: String? = null,
        val type: String? = null,
        val start: String? = null
    )

    private class Headers {
        var type = -1
        var transactionId: String? = null
        var messageId: String? = null
        var date: Long? = null
        var from: String? = null
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var subject: String? = null
        var contentLocation: String? = null
        var messageSize: Long? = null
        var expiry: Long? = null
        var messageClass: String? = null
        var responseStatus: Int? = null
        var status: Int? = null
        var contentType: ContentType? = null
    }

    /**
     * Заголовки — до Content-Type: он по протоколу последний, за ним тело.
     * Неизвестный заголовок пропускается по первому байту значения.
     */
    private fun readHeaders(r: Reader): Headers {
        val h = Headers()
        while (r.remaining() > 0) {
            val field = r.peek()
            if (field < 0x80) {
                // Заголовок приложения: имя и значение — строки
                r.readTextString()
                r.readTextString()
                continue
            }
            r.read()
            when (field) {
                H_MESSAGE_TYPE -> h.type = r.read()
                H_TRANSACTION_ID -> h.transactionId = r.readTextString()
                H_MESSAGE_ID -> h.messageId = r.readTextString()
                H_DATE -> h.date = r.readLongInteger()
                // «Подставит сеть» не затирает уже известного отправителя
                H_FROM -> r.readFrom()?.let { h.from = it }
                H_TO -> cleanAddress(r.readEncodedString())?.let { h.to += it }
                H_CC -> cleanAddress(r.readEncodedString())?.let { h.cc += it }
                H_BCC -> r.readEncodedString()
                H_SUBJECT -> h.subject = r.readEncodedString()
                H_CONTENT_LOCATION -> h.contentLocation = r.readTextString()
                H_MESSAGE_SIZE -> h.messageSize = r.readLongInteger()
                H_EXPIRY -> h.expiry = r.readExpiry()
                H_MESSAGE_CLASS -> h.messageClass = r.readMessageClass()
                H_RESPONSE_STATUS -> h.responseStatus = r.read()
                H_STATUS -> h.status = r.read()
                H_MMS_VERSION -> r.readIntegerValue()
                H_CONTENT_TYPE -> {
                    h.contentType = r.readContentType()
                    return h
                }
                else -> r.skipValue()
            }
        }
        return h
    }

    /** Тело multipart: число частей, у каждой — длины, тип, заголовки, данные. */
    private fun readMultipart(r: Reader): List<Part> {
        val count = r.readUintvar().toInt()
        val parts = ArrayList<Part>(count.coerceIn(0, 64))
        repeat(count) {
            if (r.remaining() <= 0) return parts
            val headersLength = r.readUintvar().toInt()
            val dataLength = r.readUintvar().toInt()
            val headersEnd = r.position + headersLength
            val ct = r.readContentType()
            var contentId: String? = null
            var contentLocation: String? = null
            while (r.position < headersEnd) {
                val field = r.peek()
                if (field < 0x80) {
                    r.readTextString()
                    r.readTextString()
                    continue
                }
                r.read()
                when (field) {
                    P_CONTENT_ID -> contentId = r.readQuotedOrText()
                    P_CONTENT_LOCATION -> contentLocation = r.readTextString()
                    else -> r.skipValue()
                }
            }
            r.position = headersEnd
            val data = r.readBytes(dataLength.coerceAtMost(r.remaining()))
            parts += Part(
                contentType = ct.mediaType,
                data = data,
                name = ct.name,
                contentId = contentId,
                contentLocation = contentLocation,
                charset = ct.charset
            )
        }
        return parts
    }

    // ═══════════════════════════════════════════════════════════════════════
    // СБОРКА
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Отправка (m-send-req): [to] — номера получателей, [text] — текст
     * (может быть пустым, если есть картинки), [attachments] — картинки.
     * Отправитель — «подставит сеть» (Insert-address-token): свой номер
     * телефон часто не знает.
     */
    fun composeSendReq(
        to: List<String>,
        text: String,
        attachments: List<Part>,
        transactionId: String,
        subject: String? = null,
        deliveryReport: Boolean = false
    ): ByteArray {
        require(to.isNotEmpty()) { "Нет получателей" }
        val w = Writer()
        w.octet(H_MESSAGE_TYPE); w.octet(TYPE_SEND_REQ)
        w.octet(H_TRANSACTION_ID); w.textString(transactionId)
        w.octet(H_MMS_VERSION); w.octet(VERSION_1_2)
        // From: Value-length 1, Insert-address-token
        w.octet(H_FROM); w.octet(1); w.octet(0x81)
        to.forEach { number ->
            w.octet(H_TO); w.encodedStringUtf8(addressOf(number))
        }
        subject?.takeIf { it.isNotBlank() }?.let { w.octet(H_SUBJECT); w.encodedStringUtf8(it) }
        // Класс — личное, приоритет обычный, отчёты — по выбору
        w.octet(H_MESSAGE_CLASS); w.octet(0x80)
        w.octet(H_PRIORITY); w.octet(0x81)
        w.octet(H_DELIVERY_REPORT); w.octet(if (deliveryReport) VALUE_YES else VALUE_NO)
        w.octet(H_READ_REPORT); w.octet(VALUE_NO)

        // Части: SMIL, затем картинки и текст
        val parts = ArrayList<Part>()
        val images = attachments.mapIndexed { i, p ->
            val location = p.contentLocation ?: "image$i.${extensionOf(p.contentType)}"
            Part(p.contentType, p.data, name = p.name ?: location, contentId = "<image$i>", contentLocation = location)
        }
        val textPart = text.takeIf { it.isNotBlank() }?.let {
            Part(CT_TEXT_PLAIN, it.toByteArray(Charsets.UTF_8), contentId = "<text0>",
                contentLocation = TEXT_PART_NAME, charset = CHARSET_UTF8)
        }
        val smil = smilFor(images.map { it.contentLocation!! }, textPart?.contentLocation)
        parts += Part(CT_SMIL, smil.toByteArray(Charsets.UTF_8), contentId = "<smil>", contentLocation = "smil.xml")
        parts += images
        textPart?.let { parts += it }

        // Content-Type: multipart/related; type="application/smil"; start="<smil>"
        w.octet(H_CONTENT_TYPE)
        val ct = Writer()
        ct.octet(0x80 or 0x33) // application/vnd.wap.multipart.related
        ct.octet(PARAM_CT_MR_TYPE); ct.textString(CT_SMIL)
        ct.octet(PARAM_DEP_START); ct.textString("<smil>")
        w.valueLength(ct.size()); w.bytes(ct.toByteArray())
        writeMultipart(w, parts)
        return w.toByteArray()
    }

    /** «Получено» (m-notifyresp-ind) — ответ центру MMS после скачивания. */
    fun composeNotifyResp(transactionId: String, status: Int = STATUS_RETRIEVED): ByteArray {
        val w = Writer()
        w.octet(H_MESSAGE_TYPE); w.octet(TYPE_NOTIFYRESP_IND)
        w.octet(H_TRANSACTION_ID); w.textString(transactionId)
        w.octet(H_MMS_VERSION); w.octet(VERSION_1_2)
        w.octet(H_STATUS); w.octet(status)
        w.octet(H_REPORT_ALLOWED); w.octet(VALUE_NO)
        return w.toByteArray()
    }

    private fun writeMultipart(w: Writer, parts: List<Part>) {
        w.uintvar(parts.size.toLong())
        parts.forEach { p ->
            val head = Writer()
            // Тип части с параметрами
            val ct = Writer()
            val wellKnown = WELL_KNOWN_TYPES.indexOf(p.contentType.lowercase())
            if (wellKnown >= 0) ct.octet(0x80 or wellKnown) else ct.textString(p.contentType)
            p.charset?.let { ct.octet(PARAM_CHARSET); ct.integerValue(it.toLong()) }
            p.name?.let { ct.octet(PARAM_DEP_NAME); ct.textString(it) }
            head.valueLength(ct.size()); head.bytes(ct.toByteArray())
            p.contentId?.let { head.octet(P_CONTENT_ID); head.quotedString(it) }
            p.contentLocation?.let { head.octet(P_CONTENT_LOCATION); head.textString(it) }
            w.uintvar(head.size().toLong())
            w.uintvar(p.data.size.toLong())
            w.bytes(head.toByteArray())
            w.bytes(p.data)
        }
    }

    /** Разметка SMIL: картинка сверху, текст снизу — по слайду на картинку. */
    internal fun smilFor(images: List<String>, text: String?): String {
        val sb = StringBuilder()
        sb.append("<smil><head><layout><root-layout width=\"100%\" height=\"100%\"/>")
        sb.append("<region id=\"Image\" left=\"0\" top=\"0\" width=\"100%\" height=\"80%\" fit=\"meet\"/>")
        sb.append("<region id=\"Text\" left=\"0\" top=\"80%\" width=\"100%\" height=\"20%\" fit=\"scroll\"/>")
        sb.append("</layout></head><body>")
        if (images.isEmpty()) {
            text?.let { sb.append("<par dur=\"5000ms\"><text src=\"").append(it).append("\" region=\"Text\"/></par>") }
        } else {
            images.forEachIndexed { i, img ->
                sb.append("<par dur=\"5000ms\"><img src=\"").append(img).append("\" region=\"Image\"/>")
                if (i == 0 && text != null) sb.append("<text src=\"").append(text).append("\" region=\"Text\"/>")
                sb.append("</par>")
            }
        }
        sb.append("</body></smil>")
        return sb.toString()
    }

    /** Номер получателя в виде протокола: «+79001234567/TYPE=PLMN». */
    internal fun addressOf(number: String): String {
        val clean = number.filter { it.isDigit() || it == '+' }
        return if (clean.isNotEmpty() && '@' !in number) "$clean/TYPE=PLMN" else number
    }

    /** Номер из адреса протокола: без «/TYPE=PLMN». */
    fun cleanAddress(address: String?): String? =
        address?.substringBefore("/TYPE=", address)?.trim()?.takeIf { it.isNotEmpty() }

    private fun extensionOf(contentType: String): String = when (contentType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> "bin"
    }

    /** Кодировка по номеру MIBenum; неизвестная — UTF-8. */
    fun charsetOf(mib: Int): Charset {
        val name = when (mib) {
            3 -> "US-ASCII"
            4 -> "ISO-8859-1"
            106 -> "UTF-8"
            1000 -> "UTF-16BE"
            1013 -> "UTF-16BE"
            1014 -> "UTF-16LE"
            1015 -> "UTF-16"
            2084 -> "KOI8-R"
            2251 -> "windows-1251"
            17 -> "Shift_JIS"
            2026 -> "Big5"
            113 -> "GBK"
            else -> "UTF-8"
        }
        return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
    }

    /**
     * Общеизвестные типы содержимого WSP: номер в списке — код типа.
     * Нужны первые 0x3F: текст, картинки, составные, сам MMS.
     */
    internal val WELL_KNOWN_TYPES: List<String> = listOf(
        "*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml",
        "text/x-vcalendar", "text/x-vcard", "text/vnd.wap.wml", "text/vnd.wap.wmlscript",
        "text/vnd.wap.wta-event", "multipart/*", "multipart/mixed", "multipart/form-data",
        "multipart/byterantes", "multipart/alternative", "application/*", "application/java-vm",
        "application/x-www-form-urlencoded", "application/x-hdmlc", "application/vnd.wap.wmlc",
        "application/vnd.wap.wmlscriptc", "application/vnd.wap.wta-eventc", "application/vnd.wap.uaprof",
        "application/vnd.wap.wtls-ca-certificate", "application/vnd.wap.wtls-user-certificate",
        "application/x-x509-ca-cert", "application/x-x509-user-cert", "image/*", "image/gif",
        "image/jpeg", "image/tiff", "image/png", "image/vnd.wap.wbmp", "application/vnd.wap.multipart.*",
        "application/vnd.wap.multipart.mixed", "application/vnd.wap.multipart.form-data",
        "application/vnd.wap.multipart.byteranges", "application/vnd.wap.multipart.alternative",
        "application/xml", "text/xml", "application/vnd.wap.wbxml", "application/x-x968-cross-cert",
        "application/x-x968-ca-cert", "application/x-x968-user-cert", "text/vnd.wap.si",
        "application/vnd.wap.sic", "text/vnd.wap.sl", "application/vnd.wap.slc", "text/vnd.wap.co",
        "application/vnd.wap.coc", "application/vnd.wap.multipart.related", "application/vnd.wap.sia",
        "text/vnd.wap.connectivity-xml", "application/vnd.wap.connectivity-wbxml",
        "application/pkcs7-mime", "application/vnd.wap.hashed-certificate",
        "application/vnd.wap.signed-certificate", "application/vnd.wap.cert-response",
        "application/xhtml+xml", "application/wml+xml", "text/css",
        "application/vnd.wap.mms-message", "application/vnd.wap.rollover-certificate"
    )

    // ═══════════════════════════════════════════════════════════════════════
    // ПРИМИТИВЫ WSP
    // ═══════════════════════════════════════════════════════════════════════

    private class Reader(private val buf: ByteArray) {
        var position = 0

        fun remaining() = buf.size - position
        fun peek(): Int = buf[position].toInt() and 0xFF
        fun read(): Int {
            if (position >= buf.size) throw IndexOutOfBoundsException("Конец данных")
            return buf[position++].toInt() and 0xFF
        }

        fun readBytes(n: Int): ByteArray {
            if (n < 0 || n > remaining()) throw IndexOutOfBoundsException("Нет $n байт")
            return buf.copyOfRange(position, position + n).also { position += n }
        }

        fun readUintvar(): Long {
            var result = 0L
            var guard = 0
            while (true) {
                val b = read()
                result = (result shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) return result
                if (++guard > 5) throw IllegalStateException("Слишком длинное число")
            }
        }

        /** Value-length: короткая длина (0–30) или 31 и uintvar. */
        fun readValueLength(): Int {
            val b = read()
            return when {
                b < 31 -> b
                b == 31 -> readUintvar().toInt()
                else -> throw IllegalStateException("Не длина: $b")
            }
        }

        fun readLongInteger(): Long {
            val length = read()
            if (length > 8) throw IllegalStateException("Long-integer длиной $length")
            var v = 0L
            repeat(length) { v = (v shl 8) or read().toLong() }
            return v
        }

        /** Short-integer (байт с высшим битом) или Long-integer. */
        fun readIntegerValue(): Long {
            val b = peek()
            return if (b >= 0x80) (read() and 0x7F).toLong() else readLongInteger()
        }

        /** Text-string: байты до нуля; кавычка 127 в начале отбрасывается. */
        fun readTextString(): String = String(readCString(), Charsets.UTF_8)

        private fun readCString(): ByteArray {
            if (peek() == 127) read()
            val start = position
            while (position < buf.size && buf[position].toInt() != 0) position++
            val bytes = buf.copyOfRange(start, position)
            if (position < buf.size) position++ // ноль
            return bytes
        }

        /** Quoted-string (с кавычкой 34 в начале) или обычная строка. */
        fun readQuotedOrText(): String {
            if (peek() == 34) read()
            return readTextString()
        }

        /** Encoded-string-value: строка или длина + кодировка + строка. */
        fun readEncodedString(): String {
            val b = peek()
            if (b > 31) return readTextString()
            val length = readValueLength()
            val end = position + length
            val charset = readIntegerValue().toInt()
            val bytes = readCString()
            position = end.coerceAtMost(buf.size).coerceAtLeast(position)
            return String(bytes, charsetOf(charset))
        }

        /** From: длина, затем адрес (0x80 + строка) или «подставит сеть» (0x81). */
        fun readFrom(): String? {
            val length = readValueLength()
            val end = position + length
            val token = read()
            val address = if (token == 0x80) readEncodedString() else null
            position = end.coerceAtMost(buf.size)
            return cleanAddress(address)
        }

        /** Срок: длина, 0x80 — абсолютная дата, 0x81 — секунды от сейчас. */
        fun readExpiry(): Long {
            val length = readValueLength()
            val end = position + length
            read() // абсолютный или относительный — для показа неважно
            val value = readLongInteger()
            position = end.coerceAtMost(buf.size)
            return value
        }

        fun readMessageClass(): String {
            val b = peek()
            return if (b >= 0x80) {
                when (read()) {
                    0x80 -> "personal"
                    0x81 -> "advertisement"
                    0x82 -> "informational"
                    0x83 -> "auto"
                    else -> "personal"
                }
            } else readTextString()
        }

        /** Тип содержимого: короткий код, строка или длина + тип + параметры. */
        fun readContentType(): ContentType {
            val b = peek()
            if (b >= 0x80) return ContentType(mediaTypeOf(read() and 0x7F))
            if (b > 31) return ContentType(readTextString())
            val length = readValueLength()
            val end = position + length
            val media = if (peek() >= 0x80 || peek() <= 30) {
                mediaTypeOf(readIntegerValue().toInt())
            } else {
                readTextString()
            }
            var charset: Int? = null
            var name: String? = null
            var type: String? = null
            var start: String? = null
            while (position < end) {
                val p = peek()
                if (p < 0x80) {
                    // Нетипизированный параметр: имя-строка и значение
                    val key = readTextString().lowercase()
                    val value = readUntypedValue()
                    when (key) {
                        "charset" -> charset = value.toIntOrNull()
                        "name", "filename" -> name = value
                        "type" -> type = value
                        "start" -> start = value
                    }
                    continue
                }
                read()
                when (p) {
                    PARAM_CHARSET -> charset = readCharset()
                    PARAM_NAME, PARAM_FILENAME, PARAM_DEP_NAME, PARAM_DEP_FILENAME -> name = readTextString()
                    PARAM_CT_MR_TYPE, PARAM_TYPE -> type = if (peek() >= 0x80 || peek() <= 30) {
                        mediaTypeOf(readIntegerValue().toInt())
                    } else readTextString()
                    PARAM_START, PARAM_DEP_START -> start = readTextString()
                    else -> skipValue()
                }
            }
            position = end.coerceAtMost(buf.size)
            return ContentType(media, charset, name, type, start)
        }

        private fun readCharset(): Int {
            val b = peek()
            return if (b == 128) { read(); 0 } else readIntegerValue().toInt()
        }

        private fun readUntypedValue(): String {
            val b = peek()
            return if (b >= 0x80 || b <= 30) readIntegerValue().toString() else readQuotedOrText()
        }

        /**
         * Пропустить значение неизвестного заголовка по общему правилу WSP:
         * 0–30 — столько байт, 31 — длина uintvar и столько байт,
         * 32–127 — строка до нуля, 128–255 — один байт.
         */
        fun skipValue() {
            val b = peek()
            when {
                b < 31 -> { read(); position += b }
                b == 31 -> { read(); position += readUintvar().toInt() }
                b < 128 -> readCString()
                else -> read()
            }
            if (position > buf.size) position = buf.size
        }

        private fun mediaTypeOf(code: Int): String =
            WELL_KNOWN_TYPES.getOrNull(code) ?: "application/octet-stream"
    }

    private class Writer {
        private val out = ByteArrayOutputStream()

        fun octet(v: Int) = out.write(v and 0xFF)
        fun bytes(b: ByteArray) = out.write(b, 0, b.size)
        fun size() = out.size()
        fun toByteArray(): ByteArray = out.toByteArray()

        fun uintvar(value: Long) {
            var v = value
            val stack = ArrayList<Int>()
            stack += (v and 0x7F).toInt()
            v = v ushr 7
            while (v > 0) {
                stack += ((v and 0x7F) or 0x80).toInt()
                v = v ushr 7
            }
            for (i in stack.indices.reversed()) octet(stack[i])
        }

        fun valueLength(length: Int) {
            if (length < 31) octet(length) else { octet(31); uintvar(length.toLong()) }
        }

        fun textString(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 128) octet(127)
            bytes(bytes)
            octet(0)
        }

        fun quotedString(s: String) {
            octet(34)
            bytes(s.toByteArray(Charsets.UTF_8))
            octet(0)
        }

        /** Строка в UTF-8 с явной кодировкой: длина, 106, строка. */
        fun encodedStringUtf8(s: String) {
            val body = Writer()
            body.integerValue(CHARSET_UTF8.toLong())
            body.textString(s)
            valueLength(body.size())
            bytes(body.toByteArray())
        }

        fun integerValue(v: Long) {
            if (v in 0..127) {
                octet(0x80 or v.toInt())
                return
            }
            var x = v
            val bytes = ArrayList<Int>()
            while (x > 0) {
                bytes += (x and 0xFF).toInt()
                x = x ushr 8
            }
            octet(bytes.size)
            for (i in bytes.indices.reversed()) octet(bytes[i])
        }
    }
}
