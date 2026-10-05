package com.ogneliss.dialer.background

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import com.ogneliss.dialer.util.NotificationPermission
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.CallNotifications
import com.ogneliss.dialer.log.Logger
import java.util.Calendar

/**
 * ПРЕДЛОЖЕНИЕ ПЕРЕЗВОНИТЬ ПОСЛЕ СБРОШЕННОГО ВЫЗОВА.
 *
 * Назначение: сразу после отклонения звонка предложить создать
 * напоминание в одно нажатие.
 *
 * ── Почему уведомление, а не всплывающая кнопка ────────────────────────────
 * Идея «кнопка на экране на 3 секунды» упирается в две проблемы:
 *
 * 1. Окно поверх других приложений требует SYSTEM_ALERT_WINDOW, которое
 *    выдаётся не диалогом, а походом в настройки. Большинство его не даёт.
 * 2. Три секунды — очень мало. Вызов сбрасывают на бегу, за рулём,
 *    на совещании. Ровно в этих ситуациях в экран не смотрят,
 *    и кнопка исчезнет незамеченной. А функция задумана именно
 *    для «сбросил и забыл».
 *
 * Уведомление живёт в шторке, пока его не смахнут, работает без особых
 * разрешений и доступно из любого приложения. Кнопки выбора времени —
 * прямо в нём.
 *
 * ── Почему свои напоминания, а не календарь по умолчанию ───────────────────
 * Запись в календарь требует WRITE_CALENDAR — ещё одно чувствительное
 * разрешение ради функции, которая прекрасно работает на AlarmManager.
 * Календарь остаётся ОПЦИЕЙ: открывается через ACTION_INSERT, где
 * пользователь сам подтверждает создание события. Этот путь разрешений
 * не требует вовсе.
 */
object CallbackSuggestion {

    private const val TAG = "CallbackSuggestion"
    private const val NOTIFICATION_ID_BASE = 3000

    const val ACTION_REMIND = "com.ogneliss.dialer.REMIND"
    const val EXTRA_NUMBER = "number"
    const val EXTRA_NAME = "name"
    const val EXTRA_MINUTES = "minutes"

    /** Варианты отсрочки. «Вечером» считается от текущего времени. */
    enum class Delay(val minutes: Long, val labelRes: Int) {
        IN_30_MIN(30, R.string.callback_in_30),
        IN_2_HOURS(120, R.string.callback_in_2h),
        EVENING(-1, R.string.callback_evening)
    }

    /**
     * Сколько минут до «вечера».
     *
     * Вечер — 19:00. Если уже позже, переносим на завтра: напоминание
     * в прошедшее время сработало бы немедленно, что бессмысленно.
     */
    fun minutesUntilEvening(now: Calendar = Calendar.getInstance()): Long {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now) || equals(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis) / 60_000
    }

    fun resolveMinutes(delay: Delay): Long =
        if (delay == Delay.EVENING) minutesUntilEvening() else delay.minutes

    /**
     * Показывает предложение после отклонённого вызова.
     * Ничего не создаёт само — только предлагает.
     */
    /**
     * @param note заметка о номере, если она есть.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЗАЧЕМ ЗАМЕТКА В УВЕДОМЛЕНИИ
     * ══════════════════════════════════════════════════════════════════════
     * Заметки — то, чего нет у других диалеров, и до сих пор увидеть их
     * можно было, только открыв карточку. То есть ровно тогда, когда
     * человек уже вспомнил, кто это, — а помощь нужна раньше.
     *
     * Момент отклонённого вызова для заметки самый подходящий:
     * «Сантехник — обещал приехать во вторник» отвечает на вопрос
     * «перезванивать или нет» без единого нажатия.
     *
     * Заметка вытесняет общую подпись «выберите, когда напомнить»:
     * та ничего не сообщает, а варианты отсрочки видны кнопками ниже.
     */
    fun show(context: Context, number: String?, name: String?, note: String? = null) {
        if (number.isNullOrBlank()) return
        CallNotifications.ensureChannels(context)

        val title = name ?: number
        val text = note?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.callback_suggestion_text)

        val builder = NotificationCompat.Builder(context, CallNotifications.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_callback)  // 1.3.4: свой значок у каждого типа уведомления
            .setContentTitle(context.getString(R.string.callback_suggestion_title, title))
            .setContentText(text)
            // Длинная заметка иначе обрезается на середине слова:
            // «обещал приехать во…» не помогает никак
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            // Приоритет по умолчанию: всплывать поверх экрана не нужно,
            // пользователь только что сознательно сбросил вызов
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        Delay.entries.forEach { delay ->
            builder.addAction(
                0,
                context.getString(delay.labelRes),
                remindIntent(context, number, name, delay)
            )
        }

        NotificationPermission.notify(
            context,
            NOTIFICATION_ID_BASE + number.hashCode(),
            builder.build(),
            "предложение перезвонить"
        )
    }

    private fun remindIntent(
        context: Context,
        number: String,
        name: String?,
        delay: Delay
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        (number + delay.name).hashCode(),
        Intent(context, Receiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_NUMBER, number)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_MINUTES, resolveMinutes(delay))
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /**
     * Создание события в календаре — по желанию пользователя.
     * ACTION_INSERT открывает приложение календаря с заполненными полями;
     * разрешение WRITE_CALENDAR при этом не нужно, подтверждает сам человек.
     */
    fun openCalendarDraft(context: Context, number: String, name: String?, atMillis: Long) {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(
                CalendarContract.Events.TITLE,
                context.getString(R.string.reminder_title, name ?: number)
            )
            .putExtra(CalendarContract.Events.DESCRIPTION, number)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, atMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, atMillis + 15 * 60 * 1000)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
            .onFailure { Logger.w(TAG, "Приложение календаря не найдено", it) }
    }

    /** Обрабатывает нажатие кнопки в уведомлении. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_REMIND) return

            val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
            val name = intent.getStringExtra(EXTRA_NAME)
            val minutes = intent.getLongExtra(EXTRA_MINUTES, 30)

            CallbackReminder.schedule(
                context, number, name,
                System.currentTimeMillis() + minutes * 60_000
            )
            Logger.i(TAG, "Напоминание через $minutes мин для ${Logger.mask(number)}")

            // Убираем предложение: пользователь уже ответил
            NotificationPermission.cancel(context, NOTIFICATION_ID_BASE + number.hashCode())
        }
    }
}
