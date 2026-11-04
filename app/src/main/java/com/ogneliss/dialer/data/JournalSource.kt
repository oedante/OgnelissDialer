package com.ogneliss.dialer.data

import android.content.Context
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.sms.JournalMessage
import com.ogneliss.dialer.sms.Message
import com.ogneliss.dialer.sms.MessageStore
import com.ogneliss.dialer.sms.SmsRole
import com.ogneliss.dialer.sms.SmsSettings
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ЖУРНАЛ: ВЫЗОВЫ И СООБЩЕНИЯ ОДНОЙ ЛЕНТОЙ (2.0.0).
 *
 * Выбор автора: SMS — в том же журнале, что вызовы, с фильтром
 * «Вызовы / Сообщения» (CallLogFilter.Kind). Сообщения видны, только
 * когда диалер — приложение SMS по умолчанию (иначе читать их нельзя)
 * и в настройках не выключено «Сообщения в журнале».
 *
 * ── Страницы ────────────────────────────────────────────────────────────────
 * У двух хранилищ нет общей выборки, поэтому страница собирается слиянием:
 * из каждого берутся первые offset + limit записей, сливаются по времени,
 * и от общего списка отрезается нужный кусок. Дороже постраничного чтения
 * одного источника, но страницы журнала короткие, а порядок — точный:
 * запись не «перепрыгнет» на соседнюю страницу.
 *
 * Сообщение в журнале — CallLogEntry с полем message: направление вместо
 * типа вызова, длительность 0, отрицательный id (Message.journalId) —
 * по нему удаление отличает сообщение от вызова.
 *
 * Итоги, история карточки и очистка журнала — только по вызовам:
 * карточка считает звонки, а «Очистить журнал» не должно молча стирать
 * переписку.
 */
@Singleton
class JournalSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calls: CallLogRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : CallLogSource {

    override suspend fun loadEntries(limit: Int, offset: Int): List<CallLogEntry> {
        if (!messagesShown()) return calls.loadEntries(limit, offset)
        if (limit <= 0) return emptyList()
        val window = offset + limit
        val callPage = calls.loadEntries(window, 0)
        val messages = withContext(io) { MessageStore.latest(context, window) }.map(::toEntry)
        return (callPage + messages)
            .sortedByDescending { it.date }
            .drop(offset)
            .take(limit)
    }

    override suspend fun totalsByNumber(): Map<String, CallTotals> = calls.totalsByNumber()

    override suspend fun historyFor(number: String, limit: Int): List<CallLogEntry> =
        calls.historyFor(number, limit)

    /** Удаление группы: вызовы — из журнала, сообщения — из хранилища SMS. */
    override suspend fun deleteGroup(group: CallLogGroup): Boolean = calls.deleteGroup(group)

    override suspend fun clearAll(): Boolean = calls.clearAll()

    private fun messagesShown(): Boolean =
        SmsRole.canRead(context) && SmsSettings.inJournal(context)

    private fun toEntry(m: Message): CallLogEntry = CallLogEntry(
        id = Message.journalId(m.isMms, m.id),
        number = m.address,
        // Буквенный отправитель («Bank») в E.164 не переводится — группа по нему самому
        e164 = PhoneFormatter.toE164(m.address).ifBlank { m.address },
        cachedName = null,
        photoUri = null,
        type = if (m.outgoing) CallType.OUTGOING else CallType.INCOMING,
        date = m.date,
        durationSec = 0,
        phoneAccountId = m.subId?.toString(),
        contactId = null,
        message = JournalMessage(
            key = m.key,
            text = m.body,
            unread = !m.outgoing && !m.read,
            isMms = m.isMms,
            hasAttachment = m.attachments.isNotEmpty(),
            failed = m.status == Message.Status.FAILED,
            threadId = m.threadId
        )
    )
}
