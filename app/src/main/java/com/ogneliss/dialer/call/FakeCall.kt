package com.ogneliss.dialer.call

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ogneliss.dialer.log.Logger
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
            ringNow(context)

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
                suspendRunCatching { prefs.setFakeCallAt(0L) }
                pending.finish()
            }
        }
    }
}
