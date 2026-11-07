package com.ogneliss.dialer.ui.conversation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.sms.Message
import com.ogneliss.dialer.sms.MessageNotifications
import com.ogneliss.dialer.sms.MessageStore
import com.ogneliss.dialer.sms.SmsRole
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * РАЗДЕЛ «SMS» — СПИСОК ПЕРЕПИСОК (2.0.0).
 *
 * Решение автора: на нижней панели — Журнал · SMS · клавиатура ·
 * Контакты. Здесь — по одной строке на переписку: последнее сообщение,
 * время, число непрочитанных. Касание — экран переписки.
 *
 * Переписки собираются из последних [WINDOW] сообщений хранилища —
 * группировкой по разговору (thread_id). Окно с запасом: у человека
 * редко больше нескольких сотен переписок, а старые, выпавшие из окна,
 * найдутся поиском по журналу и откроются из карточки.
 *
 * Без роли SMS читать нельзя — список пуст, экран объясняет, как
 * сделать диалер приложением SMS (написать можно и так).
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private companion object {
        const val TAG = "Messages"
        const val WINDOW = 3000
    }

    /** Одна переписка в списке. */
    data class Thread(
        val threadId: Long,
        val address: String,
        val last: Message,
        val unread: Int
    )

    data class UiState(
        val loading: Boolean = true,
        val isDefaultSms: Boolean = false,
        val threads: List<Thread> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        refresh()
        observe()
    }

    @OptIn(FlowPreview::class)
    private fun observe() = viewModelScope.launch {
        MessageStore.changes(app).debounce(300).collect { refresh() }
    }

    fun refresh() = viewModelScope.launch {
        val isDefault = SmsRole.isDefault(app)
        val threads = withContext(io) {
            if (!SmsRole.canRead(app)) return@withContext emptyList()
            MessageStore.latest(app, WINDOW)
                // Сообщение без разговора (редко, у старых прошивок) — по адресу
                .groupBy { if (it.threadId > 0) "t:${it.threadId}" else "a:${it.address}" }
                .map { (_, list) ->
                    val newest = list.maxBy { it.date }
                    Thread(
                        threadId = newest.threadId,
                        address = newest.address,
                        last = newest,
                        unread = list.count { !it.outgoing && !it.read }
                    )
                }
                .sortedByDescending { it.last.date }
        }
        _state.update { it.copy(loading = false, isDefaultSms = isDefault, threads = threads) }
    }

    /** Удалить переписку целиком (после подтверждения на экране). */
    fun deleteThread(thread: Thread, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val ok = withContext(io) {
            val ok = thread.threadId > 0 && MessageStore.deleteThread(app, thread.threadId)
            if (ok) MessageNotifications.cancel(app, thread.threadId)
            Logger.i(TAG, if (ok) "Переписка удалена" else "Переписку удалить не удалось")
            ok
        }
        onDone(ok)
        refresh()
    }

    /**
     * Удалить несколько переписок (режим выбора, 2.1.0) — по очереди,
     * одним перечитыванием в конце. onDone получает удалённые: их
     * закрепление и место в архиве снимаются одной записью.
     */
    fun deleteThreads(threads: Collection<Thread>, onDone: (List<Thread>) -> Unit = {}) = viewModelScope.launch {
        val deleted = withContext(io) {
            threads.filter { thread ->
                val ok = thread.threadId > 0 && MessageStore.deleteThread(app, thread.threadId)
                if (ok) MessageNotifications.cancel(app, thread.threadId)
                ok
            }
        }
        Logger.i(TAG, "Удалено переписок: ${deleted.size} из ${threads.size}")
        onDone(deleted)
        refresh()
    }
}
