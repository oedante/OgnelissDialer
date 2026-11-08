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
    /*
     * ВХОДЯЩИЕ — КАНАЛ БЕЗ ЗВУКА (1.8.1).
     *
     * Мелодию и вибрацию входящего играет система (Telecom) — с мелодиями
     * контактов и «Не беспокоить». У прежнего канала «call_incoming» был
     * звук уведомления по умолчанию: пока уведомление входящего ставилось
     * редко (только если экран вызова не открылся), этого не замечали.
     * С 1.8.1 оно ставится при каждом входящем на заблокированном экране —
     * и поверх мелодии звякало бы уведомление. Так же и в системном
     * диалере Android: канал входящих без звука.
     *
     * Звук канала после создания приложению не изменить, поэтому канал
     * новый, а прежний удаляется (ensureChannels).
     */
    const val CHANNEL_INCOMING = "call_incoming_silent"
    private const val CHANNEL_INCOMING_OLD = "call_incoming"
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

    /*
     * ВСПЛЫВАЮЩЕЕ УВЕДОМЛЕНИЕ ВХОДЯЩЕГО — ПОД СВОИМ НОМЕРОМ (2.1.0).
     *
     * Было: одно уведомление службы (ID_ONGOING) на весь вызов, и запасное
     * «окно вызова не показалось» лишь обновляло его — с тихого на
     * всплывающее. Но системная оболочка запускает полноэкранное намерение
     * только у НОВОГО уведомления (SystemUI: onEntryAdded), а у обновлённого —
     * нет: на заблокированном телефоне запасной путь не будил экран.
     * Стало: всплывающее входящее ставится под ID_INCOMING. Служба
     * переходит на него (startForeground с другим номером снимает прежнее),
     * и система видит новое уведомление — с полноэкранным показом и всплытием.
     */
    const val ID_INCOMING = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.incall_incoming), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                }
        )
        runCatching { nm.deleteNotificationChannel(CHANNEL_INCOMING_OLD) }
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

    /**
     * ВХОДЯЩИЙ — ВСЕГДА С «ОТВЕТИТЬ» И «ОТКЛОНИТЬ» (2.1.0).
     *
     * Было: пока звонит, а экран вызова открыт, служба показывала «Текущий
     * вызов» с таймером и кнопкой «Завершить» (ongoing). Не показался экран
     * вызова, а запасное уведомление не сработало (экран погас, «Не
     * беспокоить») — телефон звонил, а ответить было нечем. Стало: пока
     * вызов звонит, уведомление всегда входящее (CallStyle.forIncomingCall).
     *
     * @param alert всплывающее: канал входящих, полноэкранный показ, всплытие
     *        поверх. Иначе — тихое: канал текущего вызова (без звука
     *        и всплытия), без полноэкранного показа, но с теми же кнопками.
     * @param number номер звонящего: по нему «Не беспокоить» узнаёт
     *        контакт и избранных (исключения режима) — [caller].
     */
    fun incoming(
        context: Context,
        callerName: String,
        number: String?,
        alert: Boolean,
        text: String = context.getString(R.string.incall_incoming)
    ): Notification {
        val open = pending(context, InCallActivity::class.java, 0)
        val person = caller(callerName, number)
        val builder = NotificationCompat.Builder(context, if (alert) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(callerName)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            // Повторная постановка того же уведомления (пересборка, второй
            // вызов) не всплывает и не звучит заново (2.1.0)
            .setOnlyAlertOnce(true)
            // Касание по плашке — экран вызова на любой прошивке (2.1.0):
            // одной полноэкранной части мало, часть оболочек её не запускает
            // по касанию
            .setContentIntent(open)
            .addPerson(person)
        if (alert) {
            builder.setFullScreenIntent(open, true).setPriority(NotificationCompat.PRIORITY_MAX)
        } else {
            builder.setSilent(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person,
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
     * Тихий вариант входящего ([incoming] без alert): без звука, без всплытия
     * поверх экрана и без полноэкранного показа. Иначе уведомление само
     * разбудило бы экран посреди ночи — ровно то, от чего тихие часы
     * защищают. Кнопки «Ответить» и «Отклонить» остаются: кто не спит
     * и смотрит в телефон, ответит из шторки.
     */
    fun quietIncoming(context: Context, callerName: String, number: String?): Notification =
        incoming(context, callerName, number, alert = false, text = context.getString(R.string.quiet_call_notification))

    /*
     * ЗВОНЯЩИЙ В УВЕДОМЛЕНИИ — С НОМЕРОМ (2.1.0).
     *
     * Было: Person только с именем и не добавлен в уведомление. «Не
     * беспокоить» с исключениями («звонки от контактов», «от избранных»,
     * «повторные») сверяет звонящего по адресу tel: из уведомления —
     * без него наш входящий скрывался даже для разрешённых. Стало: адрес
     * tel: в E.164, «важный» и addPerson — как у системного диалера.
     */
    private fun caller(callerName: String, number: String?): androidx.core.app.Person {
        val e164 = number?.let { com.ogneliss.dialer.util.PhoneFormatter.toE164(it) }?.takeIf { it.isNotBlank() }
        return androidx.core.app.Person.Builder()
            .setName(callerName)
            .apply { if (e164 != null) setUri(android.net.Uri.fromParts("tel", e164, null).toString()) }
            .setImportant(true)
            .build()
    }

    fun ongoing(context: Context, callerName: String, number: String?, startedAt: Long): Notification {
        val person = caller(callerName, number)
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(callerName)
            .setContentText(context.getString(R.string.incall_ongoing))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(startedAt)
            // Уведомление вызова обновляется несколько раз за звонок
            // (входящий → разговор): не озвучивать повторно, даже если
            // звук каналу включили в настройках (1.8.3)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending(context, InCallActivity::class.java, 1))
            .addPerson(person)
        val hangup = actionIntent(context, CallControlReceiver.ACTION_HANGUP)
        /*
         * «ДИНАМИК» И «МИКРОФОН» В ШТОРКЕ (2.1.0).
         *
         * Было: в уведомлении разговора одна кнопка «Завершить» — чтобы
         * включить громкую связь или выключить микрофон, приходилось
         * возвращаться на экран вызова. Стало: обе кнопки-переключателя
         * рядом (CallControlReceiver). С Android 12 — CallStyle разговора,
         * как у входящего: система рисует «Завершить» красной кнопкой
         * и не урезает уведомление.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    person,
                    hangup
                )
            )
        } else {
            builder.addAction(0, context.getString(R.string.incall_hangup), hangup)
        }
        builder.addAction(0, context.getString(R.string.incall_speaker), actionIntent(context, CallControlReceiver.ACTION_SPEAKER))
        builder.addAction(0, context.getString(R.string.incall_notif_mic), actionIntent(context, CallControlReceiver.ACTION_MUTE))
        return builder.build()
    }

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
