package com.ogneliss.dialer.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.R
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
 */
object MessageNotifications {

    private const val TAG = "SmsNotify"
    const val CHANNEL = "messages"
    private const val ID_BASE = 4000
    private const val ID_RANGE = 100_000
    private const val ID_SEND_FAILED = 3999
    private const val ID_DOWNLOAD_FAILED = 3998
    const val ACTION_REPLY = "com.ogneliss.dialer.sms.REPLY"
    const val ACTION_READ = "com.ogneliss.dialer.sms.READ"
    const val ACTION_COPY_CODE = "com.ogneliss.dialer.sms.COPY_CODE"
    const val EXTRA_CODE = "code"
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_THREAD = "thread"
    const val EXTRA_SUB = "sub"
    const val KEY_REPLY = "reply_text"

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

    /** Намерение «открыть переписку с [address]» — в диалере. */
    fun openIntent(context: Context, address: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_SENDTO)
            .setData(Uri.fromParts("smsto", address, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Показать (обновить) уведомление переписки с [address]. */
    fun showForAddress(context: Context, address: String) {
        val thread = MessageStore.threadIdOf(context, address) ?: return
        val unread = MessageStore.unreadInThread(context, thread)
        if (unread.isEmpty()) {
            cancel(context, thread)
            return
        }
        ensureChannel(context)
        val name = contactName(context, address) ?: PhoneFormatter.format(address)
        val me = Person.Builder().setName(context.getString(R.string.sms_me)).build()
        val them = Person.Builder().setName(name).setKey(address).build()
        val style = NotificationCompat.MessagingStyle(me)
        unread.forEach { m ->
            val text = when {
                m.body.isNotBlank() -> m.body
                m.attachments.isNotEmpty() -> context.getString(R.string.sms_picture)
                else -> context.getString(R.string.sms_mms)
            }
            style.addMessage(text, m.date, them)
        }
        val id = idFor(thread)
        val open = PendingIntent.getActivity(
            context, id, openIntent(context, address),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val subId = unread.lastOrNull()?.subId
        val actionIntent = { action: String ->
            Intent(context, ActionReceiver::class.java).setAction(action)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_THREAD, thread)
                .putExtra(EXTRA_SUB, subId ?: -1)
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
        val code = unread.lastOrNull()?.body?.let(OtpDetector::code)
        val copy = code?.let {
            val copyPending = PendingIntent.getBroadcast(
                context, id + 2 * ID_RANGE,
                actionIntent(ACTION_COPY_CODE).putExtra(EXTRA_CODE, it),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(0, context.getString(R.string.sms_copy_code_action, it), copyPending)
                .setShowsUserInterface(false)
                .build()
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .setContentTitle(name)
            .setContentText(unread.last().body)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setNumber(unread.size)
            .apply { if (copy != null) addAction(copy) }
            .addAction(reply)
            .addAction(read)
            .build()
        NotificationPermission.notify(context, id, notification, "новое сообщение")
    }

    fun cancel(context: Context, threadId: Long) {
        runCatching { NotificationManagerCompat.from(context).cancel(idFor(threadId)) }
    }

    /** Сообщение не ушло — человек мог уже закрыть переписку. */
    fun sendFailed(context: Context) = simple(context, ID_SEND_FAILED, R.string.sms_send_failed, "не отправлено")

    /** MMS не скачалось — центр MMS пришлёт его снова или его нужно запросить у отправителя. */
    fun downloadFailed(context: Context) = simple(context, ID_DOWNLOAD_FAILED, R.string.sms_download_failed, "MMS не скачано")

    private fun simple(context: Context, id: Int, textRes: Int, what: String) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(context.getString(textRes))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationPermission.notify(context, id, n, what)
    }

    /** Имя из книги по номеру; null — нет в книге или нет разрешения. */
    fun contactName(context: Context, number: String): String? {
        if (!PermissionGuard.canReadContacts(context) || number.isBlank()) return null
        return runCatching {
            context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    /** «Копировать код», «Ответить» и «Прочитано» из шторки. */
    class ActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
            val thread = intent.getLongExtra(EXTRA_THREAD, -1L)
            val subId = intent.getIntExtra(EXTRA_SUB, -1).takeIf { it >= 0 }
            val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    if (intent.action == ACTION_COPY_CODE) {
                        intent.getStringExtra(EXTRA_CODE)?.let { code ->
                            // Буфер обмена и Toast — с главного потока
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                SmsClipboard.copy(context, code, SmsClipboard.Kind.CODE)
                            }
                        }
                    }
                    if (intent.action == ACTION_REPLY && !text.isNullOrBlank()) {
                        if (!SmsSender.send(context, address, text, subId)) sendFailed(context)
                    }
                    if (thread >= 0) {
                        MessageStore.markRead(context, thread)
                        cancel(context, thread)
                    }
                } finally {
                    pending.finish()
                }
            }
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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val sent = SmsSender.send(applicationContext, number, text, null)
            Logger.i("SmsRespond", if (sent) "Ответ на вызов отправлен" else "Ответ на вызов не отправлен")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
