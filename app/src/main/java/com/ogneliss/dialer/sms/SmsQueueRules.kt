package com.ogneliss.dialer.sms

import com.ogneliss.dialer.data.db.ScheduledSmsEntity

/**
 * ПРАВИЛА ОЧЕРЕДИ SMS (2.4.2) — без Android, ради проверок (SmsQueueRulesTest).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОМЕТКА «ОТПРАВЛЯЕТСЯ»
 * ══════════════════════════════════════════════════════════════════════════
 * Было (2.2.0–2.4.1): строка удалялась ДО отправки — так второй будильник
 * не отправлял её повторно. Но если Android закрывал процесс между
 * удалением и отправкой, SMS пропадало без следа: ни в сети, ни в очереди,
 * ни в черновике (совет из обзора надёжности, автор взял его 13.11.2026).
 *
 * Стало: перед отправкой срок строки меняется на пометку — минус время,
 * когда её взяли ([claimMark]); удаляется строка после отправки. Второй
 * будильник помеченную не возьмёт (claim только у ждущих), отмена её
 * не удалит. Схема базы не меняется: пометка живёт в том же atMillis.
 *
 * ── Прерванная отправка ────────────────────────────────────────────────────
 * Пометка старше [RECOVER_MS] — процесс закрыли посреди отправки. Ушло
 * SMS или нет, узнать нельзя: текст возвращается в черновик и приходит
 * одно уведомление «могло не уйти» — дважды отправить хуже, чем дать
 * человеку решить. Будильник на эту проверку ставится вместе с отправкой
 * ([nextWake] учитывает помеченные): иначе при пустой очереди проверка
 * ждала бы перезагрузки.
 */
object SmsQueueRules {

    /** Пометка старше — отправка прервана (закрыт процесс). */
    const val RECOVER_MS = 2 * 60_000L

    /** Неточный будильник может прийти чуть раньше — не ждать ради секунд. */
    const val EARLY_MS = 15_000L

    /** Просрочено больше чем на сутки — не отправлять, вернуть в черновик. */
    const val STALE_MS = 24 * 60 * 60_000L

    /** Пометка «отправляется с [now]»: всегда меньше нуля. */
    fun claimMark(now: Long): Long = -now.coerceAtLeast(1L)

    /** Строка в отправке (взята, ещё не удалена). */
    fun isClaimed(entry: ScheduledSmsEntity): Boolean = entry.atMillis < 0

    /** С какого времени отправляется; для ждущей — null. */
    fun claimedAt(entry: ScheduledSmsEntity): Long? = if (isClaimed(entry)) -entry.atMillis else null

    /**
     * Пришёл срок. Запас «чуть раньше» — только отложенным: неточный
     * будильник приходит с разбросом. Окно «Отменить» выдерживается
     * целиком — иначе второе сообщение подряд ушло бы вместе с первым,
     * не дав себя отменить.
     */
    fun due(rows: List<ScheduledSmsEntity>, now: Long): List<ScheduledSmsEntity> =
        rows.filter { !isClaimed(it) && it.atMillis <= now + (if (it.scheduled) EARLY_MS else 0L) }
            .sortedBy { it.atMillis }

    /** Просрочено больше суток (телефон был выключен) — в черновик, а не в сеть. */
    fun isStale(entry: ScheduledSmsEntity, now: Long): Boolean =
        !isClaimed(entry) && now - entry.atMillis > STALE_MS

    /** Прерванные отправки: пометка старше [RECOVER_MS]. */
    fun interrupted(rows: List<ScheduledSmsEntity>, now: Long): List<ScheduledSmsEntity> =
        rows.filter { e -> claimedAt(e)?.let { now - it > RECOVER_MS } == true }

    /**
     * Когда будить: ближайший срок ждущих или проверка помеченных
     * (пометка + [RECOVER_MS] + секунда запаса). null — будить незачем.
     */
    fun nextWake(rows: List<ScheduledSmsEntity>): Long? =
        rows.minOfOrNull { e -> claimedAt(e)?.let { it + RECOVER_MS + 1_000L } ?: e.atMillis }

    /** Ждущие — то, что показывает переписка (помеченные уже уходят). */
    fun waiting(rows: List<ScheduledSmsEntity>): List<ScheduledSmsEntity> = rows.filterNot { isClaimed(it) }
}
