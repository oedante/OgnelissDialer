package com.ogneliss.dialer.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.R
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.NotificationPermission
import com.ogneliss.dialer.util.PermissionGuard
import com.ogneliss.dialer.util.PhoneFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * УВЕДОМЛЕНИЯ О СООБЩЕНИЯХ (2.0.0).
 *
 * Одно уведомление на переписку — стилем «переписка» (MessagingStyle):
 * последние непрочитанные сообщения, имя из книги. Кнопки — «Ответить»
 * (поле ввода прямо в шторке, ответ уходит SMS с той же карты)
 * и «Прочитано». Касание — переписка в диалере.
 *
 * В последнем сообщении есть код подтверждения — первой кнопкой
 * «Копировать 482913» (OtpDetector, разбор на устройстве): код уходит
 * в буфер с пометкой «секретно», уведомление закрывается, переписка
 * отмечается прочитанной. На Android 15+ система сама прячет такой код
 * от сторонних читателей уведомлений.
 *
 * Канал «Сообщения» — со звуком (у SMS он ожидаем), настраивается
 * в системных настройках уведомлений приложения.
 *
 * ── 2.1.0: уведомление — из самого сообщения ───────────────────────────────
 * Было: уведомление собиралось перечитыванием хранилища (непрочитанные
 * нити). Не прочиталось (сбой записи, MIUI прячет «служебные SMS» от
 * сторонних читателей) — уведомления не было вовсе, и SMS оставалось
 * незамеченным. Стало: только что пришедшее ([Incoming]) показывается
 * всегда, хранилище лишь добавляет к нему прежние непрочитанные.
 *
 * ── 2.1.0: «переписка» Android 11+ ────────────────────────────────────────
 * Собеседник — Person с адресом tel:номер (режим «Не беспокоить»
 * «сообщения от избранных контактов» узнаёт его по номеру), уведомление
 * связано с долгоживущим ярлыком переписки (ShortcutManagerCompat):
 * без ярлыка Android 11+ не показывает уведомление в разделе
 * «Разговоры» и не даёт сделать его приоритетным.
 */
object MessageNotifications {

    private const val TAG = "SmsNotify"
    const val CHANNEL = "messages"
    private const val ID_BASE = 4000
    private const val ID_RANGE = 100_000
    private const val ID_SEND_FAILED = 3999
    /** Не скачанные MMS (2.1.0) — своё окно номеров, по одному на сообщение. */
    private const val ID_MMS_BASE = 900_000
    private const val ID_MMS_RANGE = 10_000
    /** Ярлыки переписок — после ярлыков звонков в меню значка (ContactShortcuts). */
    private const val SHORTCUT_RANK = 100
    const val ACTION_REPLY = "com.ogneliss.dialer.sms.REPLY"
    const val ACTION_READ = "com.ogneliss.dialer.sms.READ"
    const val ACTION_COPY_CODE = "com.ogneliss.dialer.sms.COPY_CODE"
    /** «Удалить» из шторки (2.1.0): удаляет показанные сообщения. */
    const val ACTION_DELETE = "com.ogneliss.dialer.sms.DELETE"
    /** «Загрузить» не скачанное MMS (2.1.0). */
    const val ACTION_MMS_DOWNLOAD = "com.ogneliss.dialer.sms.MMS_DOWNLOAD"
    private const val EXTRA_KEYS = "keys"
    private const val EXTRA_NOTIFY_ID = "notify_id"
    private const val EXTRA_MMS_KEY = "mms_key"
    const val EXTRA_CODE = "code"
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_THREAD = "thread"
    const val EXTRA_SUB = "sub"
    const val KEY_REPLY = "reply_text"

    /**
     * Только что пришедшее сообщение (2.1.0) — то, что показать в любом
     * случае, даже если хранилище его не отдало. [key] — ключ записи
     * в хранилище («sms:12»), null — записать не удалось.
     */
    data class Incoming(
        val body: String,
        val date: Long,
        val subId: Int?,
        val key: String?,
        val isMms: Boolean = false,
        val hasPicture: Boolean = false
    )

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.sms_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.sms_channel_text)
                setShowBadge(true)
            }
        )
    }

    private fun idFor(threadId: Long) = ID_BASE + (threadId % ID_RANGE).toInt()

    /** Нити не знаем (хранилище не ответило) — номер по адресу, в том же окне. */
    private fun idForAddress(address: String) = ID_BASE + Math.floorMod(smsPinKey(address).hashCode(), ID_RANGE)

    /** Намерение «открыть переписку с [address]» — в диалере. */
    fun openIntent(context: Context, address: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_SENDTO)
            .setData(Uri.fromParts("smsto", address, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    // ── Какая переписка на экране ──────────────────────────────────────────

    /*
     * 2.1.0: о неотправленном в открытой переписке уведомлять незачем —
     * пузырь «Не отправлено» с повтором виден и так (правило автора:
     * не показывать лишних сообщений). Экран переписки отмечает себя
     * здесь при показе и уходе (ConversationScreen, ON_RESUME / ON_PAUSE).
     */
    @Volatile private var visibleKey: String? = null

    fun setConversationVisible(address: String, visible: Boolean) {
        val key = smsPinKey(address)
        visibleKey = if (visible) key else visibleKey.takeIf { it != key }
    }

    private fun isConversationVisible(address: String?): Boolean =
        !address.isNullOrBlank() && visibleKey == smsPinKey(address)

    // ── Входящие ───────────────────────────────────────────────────────────

    /**
     * Показать (обновить) уведомление переписки с [address].
     *
     * [incoming] — только что пришедшее (2.1.0): оно в уведомлении всегда.
     * Без него (обновление после «Прочитано» в другом месте) — только то,
     * что отдало хранилище; пусто — уведомление снимается.
     */
    fun showForAddress(context: Context, address: String, incoming: Incoming? = null) {
        val thread = MessageStore.threadIdOf(context, address)
        val stored = thread?.let { MessageStore.unreadInThread(context, it) }.orEmpty()
        val unread = withIncoming(stored, incoming, address, thread)
        val id = thread?.let(::idFor) ?: idForAddress(address)
        if (unread.isEmpty()) {
            cancelId(context, id)
            return
        }
        ensureChannel(context)
        val contact = contactInfo(context, address)
        val name = contact?.name ?: PhoneFormatter.format(address)
        val photo = contact?.photo?.let { loadIcon(context, it) }
        val dialable = address.any { it.isDigit() } && address.none { it.isLetter() }
        val me = Person.Builder().setName(context.getString(R.string.sms_me)).build()
        val them = Person.Builder()
            .setName(name)
            .setKey(address)
            // tel: — по нему «Не беспокоить» узнаёт избранного (2.1.0)
            .apply { if (dialable) setUri("tel:$address") }
            .setImportant(contact?.starred == true)
            .apply { photo?.let { setIcon(it) } }
            .build()
        val style = NotificationCompat.MessagingStyle(me)
        unread.forEach { m ->
            style.addMessage(textOf(context, m), m.date, them)
        }
        val open = PendingIntent.getActivity(
            context, id, openIntent(context, address),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val subId = unread.lastOrNull()?.subId
        val actionIntent = { action: String ->
            Intent(context, ActionReceiver::class.java).setAction(action)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_THREAD, thread ?: -1L)
                .putExtra(EXTRA_SUB, subId ?: -1)
                .putExtra(EXTRA_NOTIFY_ID, id)
        }
        // Ответ из шторки: изменяемое намерение — в него система кладёт текст
        val replyFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val replyPending = PendingIntent.getBroadcast(context, id, actionIntent(ACTION_REPLY), replyFlags)
        val readPending = PendingIntent.getBroadcast(
            context, id + ID_RANGE, actionIntent(ACTION_READ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val remote = RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.sms_reply_hint)).build()
        val reply = NotificationCompat.Action.Builder(0, context.getString(R.string.sms_reply), replyPending)
            .addRemoteInput(remote)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
        val read = NotificationCompat.Action.Builder(0, context.getString(R.string.sms_mark_read), readPending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        /*
         * «УДАЛИТЬ» (2.1.0, автор: «в оповещении о SMS должно быть ещё
         * „Удалить“»). Как в стандартном приложении сообщений: удаляются
         * сообщения, показанные в уведомлении, а не вся переписка. Без
         * подтверждения — с экрана блокировки только после разблокировки,
         * чтобы чужая рука не стёрла сообщение. Не записанное в хранилище
         * удалять нечем — кнопка тогда только снимает уведомление.
         */
        val deletePending = PendingIntent.getBroadcast(
            context, id + 3 * ID_RANGE,
            actionIntent(ACTION_DELETE).putExtra(EXTRA_KEYS, unread.mapNotNull { m -> m.key.takeIf { m.id > 0 } }.toTypedArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val delete = NotificationCompat.Action.Builder(0, context.getString(R.string.sms_delete), deletePending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_DELETE)
            .setShowsUserInterface(false)
            .setAuthenticationRequired(true)
            .build()
        val code = unread.lastOrNull()?.body?.let(OtpDetector::code)
        val copy = code?.let {
            val copyPending = PendingIntent.getBroadcast(
                context, id + 2 * ID_RANGE,
                actionIntent(ACTION_COPY_CODE).putExtra(EXTRA_CODE, it),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(0, context.getString(R.string.sms_copy_code_action, it), copyPending)
                .setShowsUserInterface(false)
                // Код с заблокированного экрана — только после разблокировки (Android 12+)
                .setAuthenticationRequired(true)
                .build()
        }
        val shortcut = conversationShortcut(context, address, name, them, photo)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .setContentTitle(name)
            .setContentText(textOf(context, unread.last()))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setNumber(unread.size)
            .apply { shortcut?.let { setShortcutInfo(it) } }
            /*
             * Экран блокировки: если человек скрыл там содержимое уведомлений,
             * видно только «Новое сообщение» — без имени, текста и кода.
             */
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setContentTitle(context.getString(R.string.sms_new_message))
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setNumber(unread.size)
                    .build()
            )
            .apply { if (copy != null) addAction(copy) }
            .addAction(reply)
            /*
             * Видно не больше трёх кнопок. С кодом — «Копировать код»,
             * «Ответить», «Удалить»: «Прочитано» уступает место (смахнуть
             * уведомление — то же «прочитано» для глаза). Без кода — все три.
             */
            .apply { if (copy == null) addAction(read) }
            .addAction(delete)
            .build()
        NotificationPermission.notify(context, id, notification, "новое сообщение")
    }

    /**
     * Непрочитанные из хранилища и только что пришедшее — одним списком.
     * Пришедшее уже записано и прочитано вместе со всеми — не повторяется;
     * не записано или хранилище его не отдало (MIUI) — добавляется само.
     */
    private fun withIncoming(stored: List<Message>, incoming: Incoming?, address: String, thread: Long?): List<Message> {
        if (incoming == null) return stored
        if (incoming.key != null && stored.any { it.key == incoming.key }) return stored
        val id = incoming.key?.substringAfter(':')?.toLongOrNull() ?: -1L
        val own = Message(
            id = id,
            isMms = incoming.isMms,
            threadId = thread ?: -1L,
            address = address,
            body = incoming.body,
            date = incoming.date,
            outgoing = false,
            status = Message.Status.RECEIVED,
            read = false,
            subId = incoming.subId,
            attachments = if (incoming.hasPicture) listOf(Message.Attachment("", "image/*", null)) else emptyList()
        )
        return (stored + own).sortedBy { it.date }.takeLast(8)
    }

    private fun textOf(context: Context, m: Message): String = when {
        m.body.isNotBlank() -> m.body
        m.attachments.isNotEmpty() -> context.getString(R.string.sms_picture)
        else -> context.getString(R.string.sms_mms)
    }

    /**
     * Ярлык переписки для «Разговоров» Android 11+: долгоживущий, с собеседником.
     * В меню значка приложения его нет (Android 13+), там — ярлыки звонков.
     * Не вышло — уведомление всё равно показывается, просто обычным.
     */
    private fun conversationShortcut(
        context: Context,
        address: String,
        name: String,
        person: Person,
        icon: IconCompat?
    ): ShortcutInfoCompat? = runCatching {
        val shortcut = ShortcutInfoCompat.Builder(context, "sms:" + smsPinKey(address))
            .setShortLabel(name)
            .setLongLived(true)
            .setIsConversation()
            .setPerson(person)
            .setIntent(openIntent(context, address))
            .setRank(SHORTCUT_RANK)
            .setExcludedFromSurfaces(ShortcutInfoCompat.SURFACE_LAUNCHER)
            .apply { icon?.let { setIcon(it) } }
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        shortcut
    }.onFailure { Logger.w(TAG, "Ярлык переписки не создан", it) }.getOrNull()

    fun cancel(context: Context, threadId: Long) = cancelId(context, idFor(threadId))

    private fun cancelId(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    // ── Не отправлено ──────────────────────────────────────────────────────

    /**
     * Сообщение не ушло — человек мог уже закрыть переписку.
     *
     * 2.1.0: было — касание открывало главный экран, и переписку с неотправленным
     * приходилось искать. Стало: если адрес известен ([address]), открывается
     * сразу она (openIntent); без адреса — как раньше, главный экран.
     * Переписка с этим адресом на экране — не показывается: пузырь
     * «Не отправлено» с повтором уже перед глазами.
     */
    fun sendFailed(context: Context, address: String? = null) {
        if (isConversationVisible(address)) {
            Logger.i(TAG, "Не отправлено — переписка открыта, без уведомления")
            return
        }
        ensureChannel(context)
        val target = address?.takeIf { it.isNotBlank() }?.let { openIntent(context, it) }
            ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(
            context, ID_SEND_FAILED, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(context.getString(R.string.sms_send_failed))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationPermission.notify(context, ID_SEND_FAILED, n, "не отправлено")
    }

    // ── MMS не скачано ─────────────────────────────────────────────────────

    private fun idForMms(key: String) = ID_MMS_BASE + Math.floorMod(key.hashCode(), ID_MMS_RANGE)

    /**
     * MMS не скачалось и после всех повторов (MmsDownloads, 2.1.0) — одно
     * уведомление: от кого и «Загрузить». Критично по правилу автора:
     * без действия человека сообщение потеряется (центр MMS хранит его
     * ограниченный срок). Было: «MMS не скачано» без отправителя и кнопки
     * после первой же неудачи.
     */
    fun mmsNotDownloaded(context: Context, key: String, from: String?) {
        ensureChannel(context)
        val id = idForMms(key)
        val who = from?.takeIf { it.isNotBlank() }
            ?.let { contactInfo(context, it)?.name ?: PhoneFormatter.format(it) }
            ?: context.getString(R.string.sms_unknown_sender)
        val open = PendingIntent.getActivity(
            context, id,
            from?.takeIf { it.isNotBlank() }?.let { openIntent(context, it) }
                ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val download = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ActionReceiver::class.java).setAction(ACTION_MMS_DOWNLOAD)
                .putExtra(EXTRA_MMS_KEY, key)
                .putExtra(EXTRA_NOTIFY_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(who)
            .setContentText(context.getString(R.string.sms_mms_not_downloaded))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(
                NotificationCompat.Action.Builder(0, context.getString(R.string.sms_mms_download), download)
                    .setShowsUserInterface(false)
                    .build()
            )
            .build()
        NotificationPermission.notify(context, id, n, "MMS не скачано")
    }

    fun cancelMmsNotDownloaded(context: Context, key: String) = cancelId(context, idForMms(key))

    // ── Книга ──────────────────────────────────────────────────────────────

    /** Кто это по книге: имя, снимок, избранный. */
    private class ContactInfo(val name: String?, val photo: String?, val starred: Boolean)

    private fun contactInfo(context: Context, number: String): ContactInfo? {
        if (!PermissionGuard.canReadContacts(context) || number.isBlank()) return null
        return runCatching {
            context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                    ContactsContract.PhoneLookup.STARRED
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) ContactInfo(c.getString(0), c.getString(1), c.getInt(2) != 0) else null
            }
        }.getOrNull()
    }

    /** Имя из книги по номеру; null — нет в книге или нет разрешения. */
    fun contactName(context: Context, number: String): String? = contactInfo(context, number)?.name

    /** Снимок контакта — значком собеседника и ярлыка; не открылся — без него. */
    private fun loadIcon(context: Context, photoUri: String): IconCompat? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
            BitmapFactory.decodeStream(stream)?.let { IconCompat.createWithBitmap(it) }
        }
    }.getOrNull()

    /** «Копировать код», «Ответить», «Прочитано», «Удалить», «Загрузить» из шторки. */
    class ActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Текст ответа — до goAsync: намерение читается здесь же
            val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    suspendRunCatching { handle(context, intent, text) }
                        .onFailure { Logger.e(TAG, "Кнопка уведомления не сработала: ${intent.action}", it) }
                } finally {
                    pending.finish()
                }
            }
        }

        private suspend fun handle(context: Context, intent: Intent, text: String?) {
            val notifyId = intent.getIntExtra(EXTRA_NOTIFY_ID, -1)
            if (intent.action == ACTION_MMS_DOWNLOAD) {
                intent.getStringExtra(EXTRA_MMS_KEY)?.let { MmsDownloads.retryNow(context, it) }
                if (notifyId >= 0) cancelId(context, notifyId)
                return
            }
            val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
            val thread = intent.getLongExtra(EXTRA_THREAD, -1L)
            val subId = intent.getIntExtra(EXTRA_SUB, -1).takeIf { it >= 0 }
            when (intent.action) {
                ACTION_COPY_CODE -> intent.getStringExtra(EXTRA_CODE)?.let { code ->
                    // Буфер обмена — с главного потока
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        SmsClipboard.copy(context, code, SmsClipboard.Kind.CODE)
                    }
                }
                ACTION_DELETE -> {
                    val keys = intent.getStringArrayExtra(EXTRA_KEYS).orEmpty().toList()
                    if (keys.isNotEmpty()) MessageStore.delete(context, keys)
                    if (notifyId >= 0) cancelId(context, notifyId)
                    return
                }
                ACTION_REPLY -> if (!text.isNullOrBlank() && !SmsSender.send(context, address, text, subId)) {
                    sendFailed(context, address)
                }
            }
            if (thread >= 0) MessageStore.markRead(context, thread)
            if (notifyId >= 0) cancelId(context, notifyId)
        }
    }
}

/**
 * «ОТВЕТИТЬ СООБЩЕНИЕМ» НА ВХОДЯЩИЙ ВЫЗОВ (2.0.0).
 *
 * Обязательная часть приложения SMS по умолчанию: шаблон ответа
 * на экране вызова (своём и системном) отправляет SMS через эту службу.
 * Номер — в адресе намерения (smsto:…), текст — в EXTRA_TEXT.
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.data?.schemeSpecificPart?.substringBefore('?')?.takeIf { it.isNotBlank() }
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE || number == null || text.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        /*
         * 2.1.0: было — карта null (SIM по умолчанию для SMS). При двух SIM
         * ответ на звонок, пришедший на вторую карту, уходил с первой — с
         * другого номера. Стало: подписка из намерения (Telecom кладёт её
         * в «subscription»/SUBSCRIPTION_INDEX), иначе — по умолчанию.
         */
        val subId = SmsDeliverReceiver.subscriptionOf(intent)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val sent = SmsSender.send(applicationContext, number, text, subId)
            Logger.i("SmsRespond", if (sent) "Ответ на вызов отправлен" else "Ответ на вызов не отправлен")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
