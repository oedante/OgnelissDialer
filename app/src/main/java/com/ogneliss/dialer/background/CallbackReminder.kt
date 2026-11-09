package com.ogneliss.dialer.background

import com.ogneliss.dialer.concurrency.suspendRunCatching

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

    /** Срок напоминания — в будильнике (2.1.1): по нему побудка решает, пора ли. */
    private const val EXTRA_AT = "at"

    /** Без точных будильников: ближе этого к сроку ставим сам срок (разбор у [schedule]). */
    private const val FINAL_STEP_MS = 10 * 60_000L

    /** Побудка не раньше чем за минуту до срока — уже срок. */
    private const val EARLY_MS = 60_000L

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
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_AT, atMillis),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            return
        }
        /*
         * БЕЗ ТОЧНЫХ БУДИЛЬНИКОВ — К СРОКУ СТУПЕНЯМИ (2.1.1).
         *
         * Отзыв с форума (Samsung S23, 09.11.2026): «если время напоминания
         * выбирать не из пресетов, а вручную, оно не срабатывает». С Android 14
         * разрешения на точные будильники по умолчанию нет, и напоминание
         * ставилось неточным будильником. Система вправе сдвинуть такой на
         * три четверти оставшегося срока: «через 5 минут» опаздывало на минуты,
         * а «сегодня в 18:00», поставленное в обед, — на несколько часов,
         * то есть на деле не приходило.
         *
         * Стало: при сроке дальше [FINAL_STEP_MS] — промежуточная побудка
         * на середине оставшегося. Даже сдвинутая на три четверти, она
         * приходит раньше срока, и следующая ставится ближе; последняя —
         * на сам срок, с опозданием в считанные минуты. Побудок немного
         * (на сутки — около восьми), каждая — короткий приём без окон.
         * Разрешение на точные будильники по-прежнему можно дать
         * в «Разрешениях» — тогда срок ставится сразу.
         */
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, inexactWake(System.currentTimeMillis(), atMillis), pi)
    }

    /**
     * Когда будить неточным будильником (2.1.1, разбор у [schedule]): близко
     * к сроку — сам срок, иначе середина оставшегося.
     */
    internal fun inexactWake(now: Long, atMillis: Long): Long {
        val remaining = atMillis - now
        return if (remaining <= FINAL_STEP_MS) atMillis else now + remaining / 2
    }

    /** Побудка в [now] при сроке [atMillis] — уже срок, а не промежуточная. */
    internal fun isDue(now: Long, atMillis: Long): Boolean = atMillis <= 0L || now >= atMillis - EARLY_MS

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

            // Промежуточная побудка (2.1.1, разбор у schedule): до срока ещё
            // далеко — ставим следующую, ближе. Без срока в намерении — будильник
            // прежней версии: срабатывает как раньше
            val at = intent.getLongExtra(EXTRA_AT, 0L)
            if (intent.action != ACTION_SNOOZE && !isDue(System.currentTimeMillis(), at)) {
                schedule(context, number, intent.getStringExtra(EXTRA_NAME), at)
                return
            }

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
                suspendRunCatching { reminders.markFired(number) }
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
             * Звонок сразу — своим приложением (2.1.1).
             *
             * Было: общее намерение ACTION_CALL. На Samsung его перехватывало
             * окно выбора приложения, где Ogneliss Dialer нет (отзыв с форума,
             * S23). Стало: своё окно без интерфейса звонит через Telecom
             * и снимает это уведомление (CallNumberActivity).
             */
            val callIntent = PendingIntent.getActivity(
                context, number.hashCode() + 1,
                com.ogneliss.dialer.call.CallNumberActivity.intent(context, number, number.hashCode()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
                .setSmallIcon(R.drawable.ic_stat_reminder)  // 1.3.4: свой значок у каждого типа уведомления
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
