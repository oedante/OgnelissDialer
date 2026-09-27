package com.ogneliss.dialer.ui.components

import android.content.Context
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.SpamPolicy
import com.ogneliss.dialer.data.BlockedCallLog

/**
 * ПОДПИСЬ О БЛОКИРОВКЕ ДЛЯ СТРОКИ ЖУРНАЛА.
 *
 * ── Почему причина названа своими словами ──────────────────────────────────
 * «Заблокирован» без объяснения оставляет главный вопрос без ответа:
 * человек видит, что звонок не прошёл, но не знает, какое правило
 * сработало и где его менять.
 *
 * «Заблокирован — вы отметили как спам» отвечает сразу: понятно, что
 * произошло и что это его собственное решение, которое можно отменить.
 *
 * ── Почему «Без звука» отличается от «Заблокирован» ────────────────────────
 * При блокировке звонящий слышит короткие гудки — для него это «занято»
 * или «недоступен». При беззвучном пропуске идут обычные гудки, и он
 * решит, что вы просто не берёте трубку.
 *
 * Для человека это разница между «меня отсекли» и «до меня не дозвонились»,
 * и она влияет на то, перезвонит ли он.
 */
object BlockedReasonText {

    fun format(context: Context, entry: BlockedCallLog.Entry): String {
        val prefix = context.getString(
            when (entry.outcome) {
                BlockedCallLog.Outcome.BLOCKED -> R.string.blocked_prefix
                BlockedCallLog.Outcome.SILENCED -> R.string.silenced_prefix
            }
        )

        val reason = reasonRes(entry.reason)?.let { context.getString(it) }

        // Причина не у каждого решения: NO_RULE_MATCHED и подобные
        // означают «правило не сработало», и объяснять там нечего
        return if (reason != null) "$prefix — $reason" else prefix
    }

    /**
     * Причины, о которых есть что сказать.
     *
     * Разрешающие решения (ALLOW_LIST, IN_CONTACTS) сюда не попадают:
     * до этой подписи они не доходят, вызов прошёл.
     */
    fun reasonRes(reason: SpamPolicy.Reason): Int? = when (reason) {
        SpamPolicy.Reason.APP_BLOCK_LIST -> R.string.block_reason_app_list
        SpamPolicy.Reason.SYSTEM_BLOCK_LIST -> R.string.block_reason_system_list
        SpamPolicy.Reason.USER_TAG -> R.string.block_reason_user_tag
        SpamPolicy.Reason.HIDDEN_BLOCKED -> R.string.block_reason_hidden
        SpamPolicy.Reason.NOT_IN_CONTACTS -> R.string.block_reason_not_in_contacts

        SpamPolicy.Reason.ALLOW_LIST,
        SpamPolicy.Reason.HIDDEN_ALLOWED,
        SpamPolicy.Reason.IN_CONTACTS,
        SpamPolicy.Reason.NO_RULE_MATCHED -> null
    }
}
