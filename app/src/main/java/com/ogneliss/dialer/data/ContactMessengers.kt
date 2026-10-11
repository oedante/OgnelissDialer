package com.ogneliss.dialer.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.util.PermissionGuard

/**
 * ══════════════════════════════════════════════════════════════════════════
 * МЕССЕНДЖЕРЫ КОНТАКТА — ПО ТЕЛЕФОННОЙ КНИГЕ (1.2.5)
 * ══════════════════════════════════════════════════════════════════════════
 * Отзыв на форуме: «планируете в карточке связь через другое приложение,
 * Telegram например?». Кнопка «SMS» в карточке стала «Написать»: если
 * у контакта есть мессенджеры — выбор, если нет — сразу SMS, как было.
 *
 * ── Откуда знаем, что мессенджер у контакта ЕСТЬ ─────────────────────────
 * WhatsApp, Telegram и Viber при синхронизации кладут в телефонную книгу
 * у каждого своего пользователя строку данных своего типа («Написать
 * в Telegram», «Сообщение +7…»). Наличие такой строки — надёжный признак,
 * что человек в мессенджере зарегистрирован, а не просто что мессенджер
 * установлен у нас. Интернет не нужен: это чтение книги, доступ к ней
 * у диалера уже есть.
 *
 * Max — по тому же признаку, если его строки в книге есть (тип содержит
 * «ru.oneme»). Если Max строк не кладёт, он в выборе не появится:
 * лучше не предложить, чем предложить мессенджер, которого у человека нет.
 *
 * ── Как открываем ─────────────────────────────────────────────────────────
 * Той же строкой книги: ACTION_VIEW на её адрес с её типом. Мессенджер
 * сам знает, какой чат открыть, — даже когда у контакта несколько номеров.
 * Номер и подпись берём из той же строки — для второй строки в выборе.
 */
object ContactMessengers {

    data class Link(
        val kind: SwipeAction,
        val dataId: Long,
        val mimeType: String,
        /** Подпись из строки книги или номер — чтобы различать номера. */
        val summary: String?,
        /** Номер в виде +7…, если его удалось достать из строки, — для сравнения. */
        val e164: String? = null
    )

    private fun kindOf(mime: String): SwipeAction? = when {
        mime.contains("com.whatsapp.profile") -> SwipeAction.WHATSAPP
        mime.contains("org.telegram.messenger") && mime.endsWith(".profile") -> SwipeAction.TELEGRAM
        mime.contains("com.viber.voip.viber_number_message") -> SwipeAction.VIBER
        mime.contains("ru.oneme") -> SwipeAction.MAX
        else -> null
    }

    /** Порядок в выборе: как в России ими пользуются чаще. */
    private val ORDER = listOf(SwipeAction.TELEGRAM, SwipeAction.WHATSAPP, SwipeAction.MAX, SwipeAction.VIBER)

    fun linksFor(context: Context, contactId: Long): List<Link> {
        if (!PermissionGuard.canReadContacts(context)) return emptyList()
        return runCatching {
            val out = mutableListOf<Link>()
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data._ID,
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA3
                ),
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(1) ?: continue
                    val kind = kindOf(mime) ?: continue
                    val data1 = c.getString(2)
                    val data3 = c.getString(3)
                    /*
                     * Номер из строки. У WhatsApp DATA1 — «79101234567@s.whatsapp.net»,
                     * у Viber — сам номер. А у Telegram DATA1 — внутренний ID
                     * пользователя: раньше он показывался как «странный номер»
                     * (с телефона, 30.09.2026). Для Telegram номер берём только
                     * из подписи DATA3 («Написать +7 910 …»), если он там есть.
                     */
                    val raw = if (kind == SwipeAction.TELEGRAM) data3 else data1?.substringBefore('@')
                    val number = raw
                        ?.let { PHONE_IN_TEXT.find(it)?.value }
                        ?.filter { it.isDigit() || it == '+' }
                        ?.takeIf { it.count(Char::isDigit) >= 7 }
                        ?.let { if (it.startsWith("+")) it else "+$it" }
                    out += Link(kind, c.getLong(0), mime, number, number)
                }
            }
            // Один и тот же номер в одном мессенджере — одна строка
            val links = out.distinctBy { it.kind to (it.summary ?: it.dataId.toString()) }
                .sortedBy { ORDER.indexOf(it.kind) }
            // В журнал — только какие мессенджеры нашлись, без номеров:
            // если в выборе чего-то не хватит, по логу будет видно почему
            Logger.i(
                "Messengers",
                "У контакта: " + (links.map { it.kind.name }.distinct().joinToString().ifEmpty { "нет" })
            )
            links
        }.getOrElse {
            Logger.w("Messengers", "Не удалось прочитать мессенджеры контакта", it)
            emptyList()
        }
    }

    fun open(context: Context, link: Link): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(
                    ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, link.dataId),
                    link.mimeType
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrElse {
        Logger.w("Messengers", "Мессенджер не открылся по строке книги", it)
        false
    }
}

/** Номер внутри текста: «+7 910 123-45-67», «79101234567». */
private val PHONE_IN_TEXT = Regex("""\+?\d[\d\s()\-]{6,}\d""")
