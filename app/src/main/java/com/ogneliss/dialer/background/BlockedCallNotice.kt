package com.ogneliss.dialer.background

import com.ogneliss.dialer.concurrency.suspendRunCatching

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.CallNotifications
import com.ogneliss.dialer.data.BlockingController
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.NotificationPermission
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * УВЕДОМЛЕНИЕ О ЗАБЛОКИРОВАННОМ ЗВОНКЕ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОНО НУЖНО
 * ══════════════════════════════════════════════════════════════════════════
 * У блокировки несимметричная цена ошибки. Пропущенный спам — это
 * раздражение на минуту. Заблокированный звонок из школы, от курьера
 * или из поликлиники — это упущенное дело, о котором человек узнаёт
 * через неделю и не связывает с приложением.
 *
 * До сих пор заблокированный вызов был виден, только если специально
 * открыть журнал заблокированных. То есть узнать об ошибке мог тот,
 * кто и так подозревал, что она есть.
 *
 * Теперь о каждой блокировке приходит тихое уведомление с кнопкой
 * «Разблокировать». Ошибка исправляется одним касанием, не открывая
 * приложение и не разбираясь, каким правилом номер был отсечён.
 *
 * ── Почему тихое ───────────────────────────────────────────────────────────
 * Канал с низкой важностью: без звука, без вибрации, без всплывающей
 * карточки. Смысл блокировки в том, чтобы телефон молчал, — уведомление
 * со звуком отменяло бы саму функцию. Оно ждёт в шторке, пока человек
 * сам туда заглянет.
 *
 * ── Почему не «отменить блокировку навсегда» одной кнопкой ─────────────────
 * Номер мог быть отсечён маской (8800*) или настройкой «блокировать
 * незнакомых». Снимать маску из уведомления нельзя: вместе с ней
 * разблокируется весь диапазон, а человек просил про один номер.
 * Поэтому кнопка появляется только у номера с собственным правилом,
 * а в остальных случаях уведомление открывает списки блокировки,
 * где видно всю картину.
 */
object BlockedCallNotice {

    const val ACTION_UNBLOCK = "com.ogneliss.dialer.UNBLOCK_FROM_NOTIFICATION"

    private const val EXTRA_NUMBER = "number"
    private const val TAG = "BlockedCallNotice"

    /**
     * Идентификатор уведомления считается от номера.
     *
     * Повторный звонок с того же номера заменяет прежнее уведомление,
     * а не копит их: настойчивый робот иначе за вечер забил бы шторку
     * двумя десятками одинаковых карточек.
     */
    private fun idFor(e164: String) = "blocked:$e164".hashCode()

    /**
     * @param hasOwnRule есть ли у номера собственное правило. От этого
     *   зависит, будет ли кнопка отмены: маску снимать из уведомления
     *   нельзя.
     */
    fun show(context: Context, e164: String, hasOwnRule: Boolean) {
        val id = idFor(e164)

        // Нажатие на само уведомление ведёт в списки блокировки:
        // там видно, каким правилом номер отсечён, и что с этим делать
        val openIntent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_BLOCK_LISTS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CallNotifications.CHANNEL_BLOCKED)
            .setSmallIcon(R.drawable.ic_stat_blocked)  // 1.3.4: свой значок у каждого типа уведомления
            .setContentTitle(context.getString(R.string.blocked_notice_title))
            .setContentText(PhoneFormatter.format(e164))
            .setAutoCancel(true)
            .setContentIntent(openIntent)

        if (hasOwnRule) {
            val undoIntent = PendingIntent.getBroadcast(
                context, id + 1,
                Intent(context, Receiver::class.java)
                    .setAction(ACTION_UNBLOCK)
                    .putExtra(EXTRA_NUMBER, e164),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, context.getString(R.string.action_unblock), undoIntent)
        }

        NotificationPermission.notify(context, id, builder.build(), "заблокированный вызов")
    }

    /** Отмена блокировки прямо из шторки. */
    @AndroidEntryPoint
    class Receiver : BroadcastReceiver() {

        @Inject lateinit var blocking: BlockingController

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_UNBLOCK) return
            val number = intent.getStringExtra(EXTRA_NUMBER) ?: return

            /*
             * goAsync: снятие правила идёт в базу, а onReceive обязан
             * вернуться сразу. Без этого система вправе убить процесс
             * до того, как запись дойдёт до диска, и блокировка
             * останется на месте — при том что уведомление уже исчезло.
             */
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                suspendRunCatching { blocking.removeRule(number) }
                    .onFailure { Logger.w(TAG, "Не удалось снять правило: ${it.message}") }

                androidx.core.app.NotificationManagerCompat.from(context).cancel(idFor(number))
                pending.finish()
            }
        }
    }
}
