package com.ogneliss.dialer.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.R
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.NotificationPermission
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ПРОПУЩЕННЫЕ ВЫЗОВЫ — УВЕДОМЛЕНИЕ И СЧЁТЧИК НА ЗНАЧКЕ ПРИЛОЖЕНИЯ (1.2.4)
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── Откуда берётся число на значке ──────────────────────────────────────────
 * У Android нет способа «нарисовать число на значке» напрямую. Значок
 * получает отметку, пока у приложения есть уведомление в канале, где
 * разрешены отметки (setShowBadge). Число берётся из setNumber — его
 * показывают лаунчеры, которые умеют числа (Samsung, Xiaomi, Honor,
 * многие сторонние); остальные ставят точку, а число видно по долгому
 * нажатию на значок. Библиотеки для «прямой» отметки (ShortcutBadger)
 * держались на закрытых API производителей и перестали работать —
 * путь через уведомление единственный надёжный.
 *
 * ── Откуда берётся счёт ─────────────────────────────────────────────────────
 * Пропущенные считает сама система (Telecom) — по непрочитанным записям
 * журнала. Если у диалера по умолчанию есть приёмник
 * ACTION_SHOW_MISSED_CALLS_NOTIFICATION, система не рисует своё уведомление,
 * а присылает диалеру число и номер (MissedCallsReceiver ниже). Раньше
 * такого приёмника не было — уведомление рисовала система, и отметка
 * доставалась не нашему значку, а системной «Телефонии».
 *
 * ── Сброс ───────────────────────────────────────────────────────────────────
 * При открытии приложения и при исходящем звонке (clear): убираем своё
 * уведомление и просим систему отметить пропущенные прочитанными
 * (cancelMissedCallsNotification — доступно диалеру по умолчанию).
 * Система в ответ пришлёт число 0 — приёмник просто уберёт уведомление.
 */
object MissedCallsNotifier {

    const val CHANNEL = "missed_calls"

    /** Подпись для журнала приложения, не для экрана. */
    private const val LOG_WHAT = "пропущенные вызовы"
    private const val ID = 1003

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.missed_calls_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                // Отметка на значке — ради неё канал и заведён отдельно
                setShowBadge(true)
            }
        )
    }

    /*
     * Сколько пропущенных сейчас на значке. Хранится, чтобы сброс при
     * каждом открытии приложения не дёргал систему зря: сброс помечает
     * записи журнала прочитанными, а это — лишняя перезагрузка журнала
     * при каждом возвращении в приложение.
     */
    private const val PREFS = "missed_calls"
    private const val KEY_COUNT = "count"

    private fun saveCount(context: Context, count: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_COUNT, count).apply()

    private fun savedCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_COUNT, 0)

    fun show(
        context: Context,
        count: Int,
        number: String?,
        /**
         * Без звука (1.8.0): пропущенный — от звонка, заглушённого тихими
         * часами. Разбор — QuietCallGate.missedIsQuiet.
         */
        silent: Boolean = false
    ) {
        saveCount(context, count.coerceAtLeast(0))
        if (count <= 0) {
            cancel(context)
            return
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = context.resources.getQuantityString(R.plurals.missed_calls_title, count, count)
        val text = number?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.missed_calls_last, PhoneFormatter.format(it))
        }
        /*
         * «ПЕРЕЗВОНИТЬ», «НАПИСАТЬ» И СМАХИВАНИЕ (2.1.0).
         *
         * Было: только касание — открыть приложение; смахнутое уведомление
         * оставляло счётчик на значке и непрочитанные в системе до следующего
         * запуска. Стало: две кнопки по последнему номеру (вызов через
         * Telecom — исходящий сам сбросит счётчик; «Написать» — своя
         * переписка или приложение SMS, ExternalApps.smsIntent) и сброс
         * счётчика при смахивании (CallControlReceiver → clear).
         */
        val last = number?.takeIf { it.isNotBlank() }
        val callBack = last?.let {
            PendingIntent.getActivity(
                context, ID + 1,
                Intent(Intent.ACTION_CALL, Uri.fromParts("tel", it, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val write = last?.let {
            PendingIntent.getActivity(
                context, ID + 2,
                ExternalApps.smsIntent(context, it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val dismissed = PendingIntent.getBroadcast(
            context, ID,
            Intent(context, CallControlReceiver::class.java).setAction(CallControlReceiver.ACTION_CLEAR_MISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_missed)  // 1.3.4: свой значок у каждого типа уведомления
            .setContentTitle(title)
            .apply { if (text != null) setContentText(text) }
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .apply {
                callBack?.let { addAction(0, context.getString(R.string.missed_call_back), it) }
                write?.let { addAction(0, context.getString(R.string.action_write), it) }
            }
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .build()
        NotificationPermission.notify(context, ID, notification, LOG_WHAT)
        Logger.i("Missed", "Пропущенных: $count")
    }

    /*
     * ПРОПУЩЕННЫЕ, КОГДА НАШИ УВЕДОМЛЕНИЯ ЗАПРЕЩЕНЫ (2.1.0).
     *
     * Было: приёмник MissedCallsReceiver объявлен всегда — и Telecom,
     * увидев его у диалера по умолчанию, своё уведомление о пропущенном
     * не рисует, а отдаёт число нам. Если человек запретил приложению
     * уведомления (Android 13+: «Не разрешать» в первом же окне), наше
     * уведомление не показывалось — пропущенные исчезали бесследно.
     * Стало: уведомления запрещены — приёмник выключен, и Telecom рисует
     * пропущенный сам (его уведомление от имени системы); разрешили снова —
     * приёмник включается. Проверка — при открытии приложения, загрузке,
     * обновлении и в начале каждого входящего.
     */
    fun syncReceiver(context: Context) {
        runCatching {
            val allowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            val pm = context.packageManager
            val component = android.content.ComponentName(context, MissedCallsReceiver::class.java)
            val wanted = if (allowed) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(component) != wanted) {
                pm.setComponentEnabledSetting(component, wanted, android.content.pm.PackageManager.DONT_KILL_APP)
                Logger.i("Missed", if (allowed) "Пропущенные снова показывает приложение" else "Уведомления запрещены — пропущенные покажет система")
            }
        }.onFailure { Logger.w("Missed", "Приёмник пропущенных не переключён", it) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    /** Сброс счётчика: при открытии приложения и исходящем звонке. */
    fun clear(context: Context) {
        // Нечего сбрасывать — систему не трогаем (разбор у savedCount)
        if (savedCount(context) == 0) return
        saveCount(context, 0)
        cancel(context)
        // В журнал — чтобы сброс было видно так же, как появление счётчика
        Logger.i("Missed", "Счётчик пропущенных сброшен")
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.cancelMissedCallsNotification()
        }.onFailure {
            // Не диалер по умолчанию — система не даст; своё уведомление
            // всё равно убрано
            Logger.w("Missed", "Системный счётчик пропущенных не сброшен", it)
        }
    }
}

/**
 * Приёмник числа пропущенных от системы. Без него уведомление рисует
 * сама система, и отметка на значке нашему приложению не достаётся.
 * Защищён разрешением MODIFY_PHONE_STATE: его держит только система,
 * подделать рассылку стороннее приложение не может.
 */
class MissedCallsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
        // Пропущенный от звонка в тихие часы — без звука: иначе разбудил бы он (1.8.0)
        MissedCallsNotifier.show(context, count, number, silent = QuietCallGate.missedIsQuiet())
    }
}
