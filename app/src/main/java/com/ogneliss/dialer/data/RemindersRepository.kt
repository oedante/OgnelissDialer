package com.ogneliss.dialer.data

import android.content.Context
import com.ogneliss.dialer.background.CallbackReminder
import com.ogneliss.dialer.data.db.ReminderDao
import com.ogneliss.dialer.data.db.ReminderEntity
import com.ogneliss.dialer.log.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * НАПОМИНАНИЯ ПЕРЕЗВОНИТЬ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ПОСРЕДНИК МЕЖДУ БУДИЛЬНИКОМ И БАЗОЙ
 * ══════════════════════════════════════════════════════════════════════════
 * Напоминание живёт в двух местах сразу: будильник в системе заставляет
 * телефон зазвонить, запись в базе позволяет его увидеть, отменить
 * и восстановить после перезагрузки.
 *
 * Разъехаться им нельзя. Запись без будильника — напоминание, которое
 * не сработает; будильник без записи — тот, который не отменить.
 * Поэтому обе стороны меняются только здесь, и ни один экран
 * не обращается к AlarmManager напрямую.
 *
 * ── Порядок действий ───────────────────────────────────────────────────────
 * Сначала база, потом будильник. Если процесс убьют между ними,
 * останется запись без будильника: её видно, её можно снять и поставить
 * заново. Обратный порядок дал бы невидимый будильник — он бы сработал,
 * а человек не понял бы, откуда.
 */
@Singleton
class RemindersRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ReminderDao
) {

    /** Одно напоминание. */
    data class Reminder(
        val e164: String,
        val displayName: String?,
        val atMillis: Long
    ) {
        /** Сколько осталось. Отрицательное значит «уже должно было сработать». */
        val remainingMillis: Long get() = atMillis - System.currentTimeMillis()
    }

    /**
     * Активные напоминания, по времени срабатывания.
     *
     * Сработавшие отсеиваются на чтении, а не только уборкой в базе:
     * между срабатыванием и удалением записи проходит доля секунды,
     * и в этот момент список не должен показывать несуществующее.
     */
    val active: Flow<List<Reminder>> = dao.all().map { rows ->
        val now = System.currentTimeMillis()
        rows.filter { it.atMillis > now }
            .map { Reminder(it.e164, it.displayName, it.atMillis) }
    }

    /** Напоминание для конкретного номера или null. */
    fun forNumber(e164: String): Flow<Reminder?> =
        active.map { list -> list.firstOrNull { it.e164 == e164 } }

    suspend fun schedule(number: String, displayName: String?, atMillis: Long) {
        val e164 = com.ogneliss.dialer.util.PhoneFormatter.toE164(number)
        if (e164.isBlank()) return

        dao.upsert(
            ReminderEntity(
                e164 = e164,
                displayName = displayName,
                atMillis = atMillis,
                createdAt = System.currentTimeMillis()
            )
        )
        CallbackReminder.schedule(context, e164, displayName, atMillis)
    }

    suspend fun cancel(number: String) {
        val e164 = com.ogneliss.dialer.util.PhoneFormatter.toE164(number)
        // Будильник снимается первым: запись без будильника безобидна,
        // будильник без записи — нет
        CallbackReminder.cancel(context, e164)
        dao.delete(e164)
    }

    /** Вызывается самим напоминанием после срабатывания. */
    suspend fun markFired(e164: String) {
        dao.delete(e164)
    }

    /**
     * ВОССТАНОВЛЕНИЕ ПОСЛЕ ПЕРЕЗАГРУЗКИ.
     *
     * Система стирает все будильники при выключении телефона. Раньше
     * напоминания на этом и заканчивались — молча, и человек узнавал
     * об этом, не дождавшись звонка.
     *
     * Просроченные во время выключения не переставляются: показывать
     * «перезвоните Андрею» через сутки после назначенного времени
     * хуже, чем не показывать. Они просто убираются.
     */
    suspend fun restoreAfterBoot() {
        val now = System.currentTimeMillis()
        dao.deleteExpired(now)

        val restored = dao.snapshot()
        restored.forEach {
            CallbackReminder.schedule(context, it.e164, it.displayName, it.atMillis)
        }
        if (restored.isNotEmpty()) {
            Logger.i("Reminders", "Восстановлено напоминаний: ${restored.size}")
        }
    }
}
