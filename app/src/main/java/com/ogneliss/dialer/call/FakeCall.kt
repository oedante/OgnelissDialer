package com.ogneliss.dialer.call

import com.ogneliss.dialer.concurrency.suspendRunCatching

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ПСЕВДОЗВОНОК.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ЭТО НУЖНО
 * ══════════════════════════════════════════════════════════════════════════
 * Способ выйти из разговора, из которого неудобно или небезопасно уйти
 * словами: телефон звонит, человек извиняется и отходит. Работает
 * потому, что звонок выглядит как настоящий.
 *
 * ⚠️ Это не средство защиты. Оно даёт повод отойти — и только.
 * В опасной ситуации звонить надо по-настоящему.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО ЗДЕСЬ НЕ ПРОИСХОДИТ
 * ══════════════════════════════════════════════════════════════════════════
 * Телефонная подсистема не задействована вовсе. Подделать входящий
 * вызов в системе нельзя, да и не нужно: экран рисуется приложением,
 * звонок играет через обычный проигрыватель рингтона.
 *
 * 2.7.8: псевдозвонок ещё и регистрируется «своим вызовом» приложения
 * (FakeTelecomCall, как у мессенджеров) — ради гарнитуры, машины и часов.
 * Это не вызов сети: никуда не звонит и в журнал не попадает.
 *
 * Из этого следуют три ограничения, и о них честнее сказать вслух:
 *
 * • псевдозвонок НЕ попадает в журнал вызовов — ни в наш, ни в системный;
 * • на нём не работает громкая связь, микрофон и всё, что связано
 *   с реальным разговором: разговаривать не с кем;
 * • если человек рядом смотрит на экран внимательно, он увидит, что
 *   приложение — это Ogneliss, а не системный телефон. На большинстве
 *   устройств они и так одно и то же, потому что приложение работает
 *   диалером по умолчанию.
 */
object FakeCall {

    const val ACTION_RING = "com.ogneliss.dialer.FAKE_CALL_RING"

    /** Идентификатор будильника: псевдозвонок в каждый момент один. */
    private const val REQUEST_CODE = 0x1CA1

    private const val TAG = "FakeCall"

    /** Подпись уведомления в журнале работы (для разработчика, не для экрана). */
    private const val LOG_WHAT = "псевдозвонок"

    /** Показать экран прямо сейчас. */
    /**
     * СООБЩИТЬ ЛАУНЧЕРУ, ЧТО ПСЕВДОЗВОНКОМ ПОЛЬЗУЮТСЯ.
     *
     * Порядок ярлыков в меню долгого нажатия решает лаунчер, а не мы:
     * приложение задаёт только ранг (у псевдозвонка он нулевой, разбор
     * в ContactShortcuts). Многие лаунчеры — особенно у производителей
     * телефонов — ранг не учитывают или учитывают наравне с тем, как
     * часто ярлыком пользуются.
     *
     * reportShortcutUsed — официальный способ сказать «этим пользуются».
     * Google сам рекомендует вызывать его при каждом использовании
     * действия, даже если оно запущено не из ярлыка: лаунчер учится
     * на этом и поднимает ярлык выше.
     *
     * ⚠️ Гарантии это не даёт. Лаунчер может использовать эти данные,
     * а может и нет.
     */
    private fun reportUsed(context: Context) {
        runCatching {
            androidx.core.content.pm.ShortcutManagerCompat.reportShortcutUsed(context, "fake_call")
        }
    }

    /**
     * ПРЕДПРОСМОТР ЭКРАНА ВЫЗОВА (2.9.11, форум, Bublikov34: «возможно ли настройки
     * отображения вызовов как-то просматривать при выборе, до применения и звонка
     * кому-то?»; автор: «можно, надо подумать, как лучше реализовать предпросмотр»).
     *
     * Тот же экран псевдозвонка с текущими настройками вида, но тихо: без мелодии
     * и вибрации, без «вызова в системе» (гарнитура и машина о нём не узнают), без
     * показа поверх экрана блокировки и без датчика приближения. «Ответить» — вид
     * разговора, «Отклонить» и «Назад» — обратно в настройки. Открывается из
     * «Экрана вызова» (строка «Посмотреть экран вызова») и из окон выбора его
     * настроек (SettingsChoiceDialog, CALL_PREVIEW_KEYS).
     */
    fun preview(context: Context) {
        context.startActivity(
            Intent(context, FakeCallActivity::class.java)
                .putExtra(FakeCallActivity.EXTRA_PREVIEW, true)
                .apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }

    fun ringNow(context: Context) {
        reportUsed(context)
        context.startActivity(
            Intent(context, FakeCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Отложенный псевдозвонок.
     *
     * ── Почему setAlarmClock, а не setExact ────────────────────────────────
     * Это единственный вид будильника, который система не откладывает
     * ради экономии батареи ни в каком режиме, включая глубокий сон.
     * Для напоминания задержка в пять минут терпима, для псевдозвонка —
     * нет: он назначен на минуту, когда человек рассчитывает выйти
     * из разговора.
     *
     * Побочный эффект: система показывает значок будильника в строке
     * состояния. Это цена за точность, и её стоит знать —
     * внимательный собеседник значок увидит.
     */
    fun schedule(context: Context, atMillis: Long) {
        reportUsed(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context)

        runCatching {
            alarms.setAlarmClock(
                AlarmManager.AlarmClockInfo(atMillis, intent),
                intent
            )
        }.onFailure { Logger.w(TAG, "Не удалось назначить псевдозвонок: ${it.message}") }
    }

    /** Уведомление отложенного псевдозвонка (2.7.7): одно на всё приложение. */
    const val NOTIFICATION_ID = 0x1CA2

    /** Канал со звуком мелодии звонка — только для псевдозвонка по времени. */
    private const val CHANNEL_RING = "fake_call_ring"

    /** Сколько звонит плашка, если её не открыли: как неотвеченный вызов. */
    private const val RING_TIMEOUT_MS = 45_000L

    /**
     * ПСЕВДОЗВОНОК ПО ВРЕМЕНИ — УВЕДОМЛЕНИЕМ ВО ВЕСЬ ЭКРАН (2.7.7).
     *
     * Форум, valer4ik123 (Motorola Edge 50 Fusion): «Через минуту» и «Выбрать
     * время» не срабатывали вовсе, «Позвонить сейчас» — да.
     *
     * Было: будильник → этот приёмник → startActivity. С Android 10 приложение,
     * которого нет на экране, само открыть экран не может (запуск из фона
     * запрещён), и система молча ничего не показывала. «Сейчас» работало:
     * приложение в этот момент на экране.
     *
     * Стало — как у настоящего входящего: уведомление категории «вызов»
     * с переходом во весь экран (USE_FULL_SCREEN_INTENT уже есть ради входящих).
     * Экран выключен или заблокирован — система сама открывает экран
     * псевдозвонка. Телефоном пользуются — плашка сверху с мелодией звонка,
     * касание открывает экран. Экран псевдозвонка снимает уведомление сам
     * (FakeCallActivity). Разрешено «поверх других приложений» — экран
     * открывается сразу, как раньше.
     */
    internal fun ringFromBackground(context: Context) {
        if (com.ogneliss.dialer.util.AppPermissions.canDrawOverlays(context)) {
            ringNow(context)
            return
        }
        runCatching {
            ensureRingChannel(context)
            val open = PendingIntent.getActivity(
                context, REQUEST_CODE,
                Intent(context, FakeCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_RING)
                .setSmallIcon(com.ogneliss.dialer.R.drawable.ic_call)
                // 2.9.10: в шапке — «Ogneliss • Звонок» (автор: «в уведомлении писать на локальном языке звонок или SMS»)
                .setSubText(context.getString(com.ogneliss.dialer.R.string.notif_source_call))
                .setContentTitle(context.getString(com.ogneliss.dialer.R.string.incall_incoming))
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(open, true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setTimeoutAfter(RING_TIMEOUT_MS)
                .build()
                // Мелодия повторяется, пока плашку не открыли или не смахнули
                .apply { flags = flags or android.app.Notification.FLAG_INSISTENT }
            com.ogneliss.dialer.util.NotificationPermission.notify(context, NOTIFICATION_ID, notification, LOG_WHAT)
        }.onFailure { Logger.w(TAG, "Уведомление псевдозвонка не показано: ${it.message}") }
        // Приложение на экране — откроется и так (уведомление тогда снимет сам экран)
        runCatching { ringNow(context) }
    }

    private fun ensureRingChannel(context: Context) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_RING) != null) return
        val channel = android.app.NotificationChannel(
            CHANNEL_RING,
            context.getString(com.ogneliss.dialer.R.string.fake_call_title),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    /** Снять уведомление псевдозвонка — экран открыт, звонит уже он. */
    fun clearNotification(context: Context) {
        runCatching { androidx.core.app.NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarms.cancel(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, Receiver::class.java).setAction(ACTION_RING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Срабатывание отложенного псевдозвонка. */
    @dagger.hilt.android.AndroidEntryPoint
    class Receiver : BroadcastReceiver() {

        @javax.inject.Inject
        lateinit var prefs: com.ogneliss.dialer.settings.Prefs

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_RING) return

            /*
             * Отметка о назначенном снимается сразу после срабатывания.
             *
             * Иначе окно псевдозвонка продолжало бы показывать «назначен
             * на 19:40» после того, как он уже прозвонил, и предлагать
             * отменить несуществующее.
             */
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            ).launch {
                /*
                 * 2.7.8: сначала — своим вызовом в системе (FakeTelecomCall): так
                 * псевдозвонок звонит и в гарнитуре, а экран покажет система
                 * (onShowIncomingCallUi). Система не приняла — по-старому.
                 */
                val number = suspendRunCatching { prefs.fakeCallNumber.first() }.getOrNull().orEmpty()
                if (!FakeTelecomCall.start(context, number)) ringFromBackground(context)
                suspendRunCatching { prefs.setFakeCallAt(0L) }
                pending.finish()
            }
        }
    }
}
