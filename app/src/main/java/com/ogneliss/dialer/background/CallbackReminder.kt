package com.ogneliss.dialer.background

import android.app.AlarmManager
import android.app.PendingIntent
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.util.NotificationPermission
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.CallNotifications

/**
 * «Напомнить перезвонить» — одна из самых частых просьб в отзывах
 * на сторонние диалеры. Работает через AlarmManager, полностью локально.
 */
object CallbackReminder {

    /** Действие «отложить на 15 минут» из уведомления. */
    const val ACTION_SNOOZE = "com.ogneliss.dialer.REMINDER_SNOOZE"

    /**
     * На сколько откладывается напоминание.
     *
     * Достаточно, чтобы закончить начатое, и не настолько долго,
     * чтобы забыть, о чём речь.
     */
    const val SNOOZE_MINUTES = 15L

    private const val EXTRA_NUMBER = "number"
    private const val EXTRA_NAME = "name"

    /**
     * Планирует напоминание.
     *
     * Идентификатор PendingIntent строится из номера: повторный вызов
     * для того же номера заменяет предыдущее напоминание, а не создаёт
     * второе. Иначе три нажатия «напомнить» дали бы три уведомления.
     */
    fun schedule(context: Context, number: String, name: String?, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, number.hashCode(),
            Intent(context, Receiver::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_NAME, name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Android 12+: точные будильники требуют разрешения, поэтому
        // при его отсутствии откатываемся на неточный — напоминание
        // не критично ко времени до секунды.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    /**
     * Можно ли ставить точные будильники.
     *
     * С Android 12 это отдельное разрешение. Без него система вправе
     * сдвинуть срабатывание ради экономии батареи — иногда на десятки
     * минут, и «напомнить через 5 минут» превращается в «через сорок».
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    fun cancel(context: Context, number: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, number.hashCode(),
            Intent(context, Receiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pi?.let { am.cancel(it) }
    }

    @dagger.hilt.android.AndroidEntryPoint
    class Receiver : BroadcastReceiver() {

        @javax.inject.Inject
        lateinit var reminders: com.ogneliss.dialer.data.RemindersRepository

        override fun onReceive(context: Context, intent: Intent) {
            val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
            val name = intent.getStringExtra(EXTRA_NAME) ?: number

            /*
             * Запись удаляется сразу после срабатывания.
             *
             * Иначе значок «напоминание установлено» остался бы висеть
             * в журнале и контактах у номера, о котором уже напомнили,
             * а в карточке предлагалось бы отменить несуществующее.
             *
             * goAsync: удаление идёт в базу, onReceive обязан вернуться
             * сразу.
             */
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            ).launch {
                runCatching { reminders.markFired(number) }
                pending.finish()
            }

            /*
             * Отложение: ставим напоминание заново и убираем текущее.
             *
             * Уведомление снимаем явно: setAutoCancel срабатывает
             * только при нажатии на само уведомление, а не на кнопку
             * действия — иначе старое висело бы рядом с новым.
             */
            if (intent.action == ACTION_SNOOZE) {
                NotificationPermission.cancel(context, number.hashCode())
                schedule(
                    context, number, name,
                    System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
                )
                return
            }

            CallNotifications.ensureChannels(context)

            /*
             * НАЖАТИЕ ОТКРЫВАЕТ КАРТОЧКУ АБОНЕНТА.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ НЕ НАБОР НОМЕРА
             * ══════════════════════════════════════════════════════════════
             * Раньше здесь стоял ACTION_DIAL — намерение открыть системный
             * набор. Часть прошивок его молча игнорирует: человек
             * нажимает на уведомление, и не происходит ничего.
             *
             * Карточка полезнее и надёжнее. В ней видна история общения,
             * заметка и кнопки звонка, сообщения, повторного напоминания —
             * ровно то, что нужно, когда возвращаешься к отложенному
             * разговору. И открывает её наше приложение, а не чужое.
             */
            val cardIntent = PendingIntent.getActivity(
                context, number.hashCode(),
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_CARD)
                    .putExtra(MainActivity.EXTRA_CARD_NUMBER, number)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            /*
             * Звонок сразу, минуя приложение.
             *
             * ACTION_CALL требует разрешения CALL_PHONE, которое у нас
             * есть как у звонилки по умолчанию. Открывать ради звонка
             * приложение незачем: человек уже решил позвонить, нажав
             * именно эту кнопку.
             */
            val callIntent = PendingIntent.getActivity(
                context, number.hashCode() + 1,
                Intent(Intent.ACTION_CALL, android.net.Uri.fromParts("tel", number, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )

            /*
             * Отложить ещё на 15 минут.
             *
             * Самая частая причина, по которой напоминание приходит
             * не вовремя: человек снова занят. Без этой кнопки ему
             * пришлось бы заходить в приложение и ставить напоминание
             * заново — а он в этот момент за рулём или на встрече,
             * то есть как раз не может.
             *
             * Пятнадцать минут: достаточно, чтобы закончить начатое,
             * и не настолько долго, чтобы забыть, о чём речь.
             */
            val snoozeIntent = PendingIntent.getBroadcast(
                context, number.hashCode() + 2,
                Intent(context, Receiver::class.java)
                    .setAction(ACTION_SNOOZE)
                    .putExtra(EXTRA_NUMBER, number)
                    .putExtra(EXTRA_NAME, name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val n = NotificationCompat.Builder(context, CallNotifications.CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(context.getString(R.string.reminder_title, name))
                .setContentText(number)
                .setAutoCancel(true)
                .setContentIntent(cardIntent)
                .addAction(0, context.getString(R.string.action_call), callIntent)
                .addAction(0, context.getString(R.string.reminder_snooze), snoozeIntent)
                .build()

            // Явная проверка разрешения: молча проглоченная ошибка
            // означала бы, что напоминание не пришло, а почему —
            // непонятно ни пользователю, ни разработчику
            NotificationPermission.notify(
                context, number.hashCode(), n, "напоминание перезвонить"
            )
        }
    }
}
