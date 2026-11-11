package com.ogneliss.dialer.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Xml
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.sms.SmsRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * РЕЗЕРВНАЯ КОПИЯ SMS В ФАЙЛ (2.2.0).
 *
 * ── Формат ─────────────────────────────────────────────────────────────────
 * XML приложения «SMS Backup & Restore» (`<smses><sms … /></smses>`):
 * самый распространённый формат копий SMS на Android. Копию из диалера
 * можно восстановить в другом приложении и наоборот — человек не
 * привязан к нашему.
 *
 * ── Что входит ─────────────────────────────────────────────────────────────
 * Текстовые SMS всех папок (входящие, отправленные, неотправленные).
 * MMS с картинками — нет: это файлы, а не строки, и копия выросла бы
 * в сотни мегабайт. Черновики хранилища (type 3) — тоже нет.
 *
 * ── Восстановление ─────────────────────────────────────────────────────────
 * Только у приложения SMS по умолчанию (писать в хранилище может только
 * оно). Сообщение, которое уже есть в телефоне (то же время и тот же
 * текст), пропускается: восстановить один файл дважды — не задвоить
 * переписку. Нить хранилище подбирает само по номеру. Файл читается
 * потоком — копия на десятки тысяч сообщений не грузится в память целиком.
 *
 * Сеть не нужна и не используется: файл пишется туда, куда человек
 * выбрал в системном окне.
 */
object SmsBackup {

    private const val TAG = "SmsBackup"
    private val SMS_URI: Uri = Uri.parse("content://sms")

    /** Тип «черновик» хранилища: в копию не идёт. */
    private const val TYPE_DRAFT = 3

    /** Сколько строк вставлять за раз: хранилище SMS медленное на одиночных вставках. */
    private const val BATCH = 200

    /** Имя файла по умолчанию: «sms-2026-11-11.xml». */
    fun fileName(): String = "sms-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".xml"

    /** Все SMS телефона в XML; null — читать нельзя или сбой. */
    suspend fun export(context: Context): String? = withContext(Dispatchers.IO) {
        if (!SmsRole.canRead(context)) return@withContext null
        runCatching {
            val rows = ArrayList<Map<String, String?>>()
            context.contentResolver.query(
                SMS_URI,
                arrayOf("address", "date", "date_sent", "type", "body", "read", "status", "sub_id", "service_center", "subject"),
                "type != $TYPE_DRAFT", null, "date ASC"
            )?.use { c ->
                val names = c.columnNames
                while (c.moveToNext()) {
                    rows += names.indices.associate { i -> names[i] to if (c.isNull(i)) null else c.getString(i) }
                }
            }
            val readable = SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.getDefault())
            val out = StringWriter()
            val xml = Xml.newSerializer()
            xml.setOutput(out)
            xml.startDocument("UTF-8", true)
            xml.text("\n")
            xml.startTag(null, "smses")
            xml.attribute(null, "count", rows.size.toString())
            xml.attribute(null, "backup_date", System.currentTimeMillis().toString())
            xml.attribute(null, "type", "full")
            rows.forEach { r ->
                xml.text("\n  ")
                xml.startTag(null, "sms")
                xml.attribute(null, "protocol", "0")
                xml.attribute(null, "address", clean(r["address"]).ifEmpty { "null" })
                xml.attribute(null, "date", r["date"] ?: "0")
                xml.attribute(null, "type", r["type"] ?: "1")
                xml.attribute(null, "subject", clean(r["subject"]).ifEmpty { "null" })
                xml.attribute(null, "body", clean(r["body"]))
                xml.attribute(null, "toa", "null")
                xml.attribute(null, "sc_toa", "null")
                xml.attribute(null, "service_center", clean(r["service_center"]).ifEmpty { "null" })
                xml.attribute(null, "read", r["read"] ?: "1")
                xml.attribute(null, "status", r["status"] ?: "-1")
                xml.attribute(null, "locked", "0")
                xml.attribute(null, "date_sent", r["date_sent"] ?: "0")
                r["sub_id"]?.let { xml.attribute(null, "sub_id", it) }
                xml.attribute(null, "readable_date", readable.format(Date(r["date"]?.toLongOrNull() ?: 0L)))
                xml.attribute(null, "contact_name", "(Unknown)")
                xml.endTag(null, "sms")
            }
            xml.text("\n")
            xml.endTag(null, "smses")
            xml.endDocument()
            Logger.i(TAG, "Копия SMS собрана: ${rows.size}")
            out.toString()
        }.onFailure { Logger.e(TAG, "Копия SMS не собрана", it) }.getOrNull()
    }

    /**
     * Восстановить SMS из файла [uri]. Возвращает, сколько добавлено;
     * null — не приложение SMS по умолчанию или файл не прочитан.
     */
    suspend fun restore(context: Context, uri: Uri): Int? = withContext(Dispatchers.IO) {
        if (!SmsRole.isDefault(context)) {
            Logger.w(TAG, "Восстановление SMS без роли SMS пропущено")
            return@withContext null
        }
        runCatching {
            val existing = existingKeys(context)
            var added = 0
            var skipped = 0
            val batch = ArrayList<ContentValues>(BATCH)
            fun flush() {
                if (batch.isEmpty()) return
                added += context.contentResolver.bulkInsert(SMS_URI, batch.toTypedArray())
                batch.clear()
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(input, null)
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG || parser.name != "sms") continue
                    val values = valuesOf(parser) ?: continue
                    val key = keyOf(values.getAsLong("date") ?: 0L, values.getAsString("body").orEmpty())
                    if (!existing.add(key)) {
                        skipped++
                        continue
                    }
                    batch += values
                    if (batch.size >= BATCH) flush()
                }
            } ?: return@runCatching null
            flush()
            Logger.i(TAG, "SMS восстановлены: добавлено $added, уже были $skipped")
            com.ogneliss.dialer.widget.SectionWidgets.updateSms(context)
            added
        }.onFailure { Logger.e(TAG, "SMS из файла не восстановлены", it) }.getOrNull()
    }

    /** Строка `<sms …/>` → значения хранилища; без адреса или даты — пропуск. */
    private fun valuesOf(p: XmlPullParser): ContentValues? {
        fun attr(name: String): String? = p.getAttributeValue(null, name)?.takeIf { it != "null" }
        val address = attr("address")?.takeIf { it.isNotBlank() } ?: return null
        val date = attr("date")?.toLongOrNull() ?: return null
        val type = attr("type")?.toIntOrNull()?.takeIf { it != TYPE_DRAFT } ?: return null
        return ContentValues().apply {
            put("address", address)
            put("date", date)
            put("type", type)
            put("body", attr("body").orEmpty())
            put("read", attr("read")?.toIntOrNull() ?: 1)
            put("seen", 1)
            attr("status")?.toIntOrNull()?.let { put("status", it) }
            attr("date_sent")?.toLongOrNull()?.let { put("date_sent", it) }
            attr("subject")?.let { put("subject", it) }
            attr("service_center")?.let { put("service_center", it) }
            attr("sub_id")?.toIntOrNull()?.let { put("sub_id", it) }
        }
    }

    /** Уже лежащие в хранилище: время + текст. Номер не берётся — «8…» и «+7…» записаны по-разному. */
    private fun existingKeys(context: Context): HashSet<String> {
        val keys = HashSet<String>()
        context.contentResolver.query(SMS_URI, arrayOf("date", "body"), null, null, null)?.use { c ->
            while (c.moveToNext()) keys += keyOf(c.getLong(0), c.getString(1).orEmpty())
        }
        return keys
    }

    private fun keyOf(date: Long, body: String): String = date.toString() + "|" + body.hashCode()

    /** Без управляющих знаков: XML 1.0 их не допускает, и файл не прочитался бы. */
    private fun clean(text: String?): String =
        text.orEmpty().filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }
}
