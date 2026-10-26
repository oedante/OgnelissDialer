package com.ogneliss.dialer.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ogneliss.dialer.R

/**
 * УВЕДОМЛЕНИЯ О ВЫЗОВАХ.
 *
 * ── Про CallStyle ──────────────────────────────────────────────────────────
 * С Android 12 уведомление о звонке обязано использовать стиль CallStyle.
 * Без него система понижает приоритет: уведомление не всплывает поверх
 * экрана, и пользователь пропускает звонок, не поняв почему.
 *
 * ── Четыре канала, а не один ───────────────────────────────────────────────
 * Каналы разделены, потому что пользователь должен иметь возможность
 * настроить их по отдельности: отключить напоминания, но оставить
 * входящие. Один общий канал лишил бы его этого выбора.
 *
 * • Входящие — максимальный приоритет, обходят «Не беспокоить»
 * • Текущий вызов — низкий: он не должен отвлекать во время разговора
 * • Пропущенные — обычный
 * • Напоминания — высокий, иначе теряют смысл
 *
 * ── Почему PendingIntent immutable ─────────────────────────────────────────
 * С Android 12 изменяемые PendingIntent запрещены без явного флага:
 * стороннее приложение могло бы подменить их содержимое. Для кнопок
 * «Ответить» и «Отклонить» это означало бы управление чужими звонками.
 */
object CallNotifications {

    const val CHANNEL_ONGOING = "call_ongoing"
    const val CHANNEL_INCOMING = "call_incoming"
    const val CHANNEL_MISSED = "call_missed"
    const val CHANNEL_REMINDER = "call_reminder"

    /**
     * Заблокированные вызовы.
     *
     * Важность низкая: без звука, вибрации и всплывающей карточки.
     * Смысл блокировки в том, чтобы телефон молчал, — уведомление
     * со звуком отменяло бы саму функцию. Оно ждёт в шторке.
     *
     * Отдельный канал, а не общий с пропущенными: так человек может
     * выключить его системными средствами, не трогая всё остальное,
     * и приложению не нужна для этого своя настройка.
     */
    const val CHANNEL_BLOCKED = "call_blocked"

    const val ID_ONGOING = 1001
    const val ID_INCOMING = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.incall_incoming), NotificationManager.IMPORTANCE_HIGH)
                .apply { setBypassDnd(true); lockscreenVisibility = Notification.VISIBILITY_PUBLIC }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.incall_ongoing), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MISSED, context.getString(R.string.call_missed), NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.action_remind), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BLOCKED,
                context.getString(R.string.blocked_notice_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    fun incoming(context: Context, callerName: String): Notification {
        val fullScreen = pending(context, InCallActivity::class.java, 0)
        val builder = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(callerName)
            .setContentText(context.getString(R.string.incall_incoming))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    androidx.core.app.Person.Builder().setName(callerName).build(),
                    actionIntent(context, CallControlReceiver.ACTION_REJECT),
                    actionIntent(context, CallControlReceiver.ACTION_ANSWER)
                )
            )
        } else {
            builder.addAction(0, context.getString(R.string.incall_decline), actionIntent(context, CallControlReceiver.ACTION_REJECT))
            builder.addAction(0, context.getString(R.string.incall_answer), actionIntent(context, CallControlReceiver.ACTION_ANSWER))
        }
        return builder.build()
    }

    /**
     * ТИХИЙ ВХОДЯЩИЙ (1.8.0, тихие часы для незнакомых).
     *
     * Канал «текущий вызов» — с низкой важностью: без звука, без всплытия
     * поверх экрана и без полноэкранного показа. Иначе уведомление само
     * разбудило бы экран посреди ночи — ровно то, от чего тихие часы
     * защищают. Кнопки «Ответить» и «Отклонить» остаются: кто не спит
     * и смотрит в телефон, ответит из шторки.
     */
    fun quietIncoming(context: Context, callerName: String): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(callerName)
            .setContentText(context.getString(R.string.quiet_call_notification))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pending(context, InCallActivity::class.java, 2))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    androidx.core.app.Person.Builder().setName(callerName).build(),
                    actionIntent(context, CallControlReceiver.ACTION_REJECT),
                    actionIntent(context, CallControlReceiver.ACTION_ANSWER)
                )
            )
        } else {
            builder.addAction(0, context.getString(R.string.incall_decline), actionIntent(context, CallControlReceiver.ACTION_REJECT))
            builder.addAction(0, context.getString(R.string.incall_answer), actionIntent(context, CallControlReceiver.ACTION_ANSWER))
        }
        return builder.build()
    }

    fun ongoing(context: Context, callerName: String, startedAt: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(callerName)
            .setContentText(context.getString(R.string.incall_ongoing))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(startedAt)
            .setContentIntent(pending(context, InCallActivity::class.java, 1))
            .addAction(0, context.getString(R.string.incall_hangup), actionIntent(context, CallControlReceiver.ACTION_HANGUP))
            .build()

    private fun pending(context: Context, cls: Class<*>, req: Int): PendingIntent =
        PendingIntent.getActivity(
            context, req,
            Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun actionIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, CallControlReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
