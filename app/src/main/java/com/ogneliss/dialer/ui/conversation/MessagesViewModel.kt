package com.ogneliss.dialer.ui.conversation

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.sms.Message
import com.ogneliss.dialer.sms.MessageNotifications
import com.ogneliss.dialer.sms.MessageStore
import com.ogneliss.dialer.sms.SmsHealth
import com.ogneliss.dialer.sms.SmsRole
import com.ogneliss.dialer.sms.SmsSettings
import com.ogneliss.dialer.data.db.SmsDraftDao
import com.ogneliss.dialer.data.db.StarredSmsDao
import com.ogneliss.dialer.data.db.StarredSmsEntity
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.sms.SmsFolder
import com.ogneliss.dialer.sms.SmsFolders
import com.ogneliss.dialer.sms.OtpSweeper
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * РАЗДЕЛ «SMS» — СПИСОК ПЕРЕПИСОК (2.0.0).
 *
 * Решение автора: на нижней панели — Журнал · SMS · клавиатура ·
 * Контакты. Здесь — по одной строке на переписку: последнее сообщение,
 * время, число непрочитанных. Касание — экран переписки.
 *
 * ── 2.1.0: все переписки, а не только свежие ──────────────────────────────
 * Было: переписки собирались из последних [WINDOW] сообщений хранилища —
 * старые выпадали из раздела совсем. Стало: окно даёт точные данные
 * последнего сообщения (текст, карта, «Вы:»), а переписки вне окна
 * добавляются из таблицы нитей (MessageStore.threadSummaries: дата,
 * кратко последнее, прочитано ли). Непрочитанные считаются по всему
 * хранилищу (MessageStore.unreadCounts), а не по окну.
 *
 * Без роли SMS читать нельзя — список пуст; экран предлагает открыть
 * приложение SMS человека и (по просьбе) вести SMS здесь.
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val prefs: Prefs,
    private val draftsDao: SmsDraftDao,
    private val starredDao: StarredSmsDao,
    private val otpCleanup: OtpSweeper
) : ViewModel() {

    private companion object {
        const val TAG = "Messages"
        const val WINDOW = 3000

        /**
         * Удаление с «Отменить»: запасной срок, если сообщение так и не
         * показалось (то же, что у журнала, DialerViewModel.UNDO_FALLBACK_MS).
         */
        const val UNDO_FALLBACK_MS = 12_000L

        /** Сколько ждать перечитанного списка после удаления. */
        const val REFRESH_WAIT_MS = 3_000L

        /** Поиск по тексту — после паузы в наборе (2.2.0). */
        const val SEARCH_DELAY_MS = 300L

        /**
         * Переписки, удалённые с «Отменить» (2.1.0): ключ → дата последнего
         * сообщения, которое видел человек. На весь процесс, а не на экран:
         * ушёл из раздела в окно отмены — строки не должны вернуться.
         */
        val hidden = MutableStateFlow<Map<String, Long>>(emptyMap())

        /**
         * Удаление из хранилища — не областью экрана: человек мог уйти
         * из приложения сразу после «Удалить», и переписка вернулась бы.
         */
        val deletionScope = CoroutineScope(SupervisorJob())
    }

    /** Одна переписка в списке. */
    @Immutable
    data class Thread(
        val threadId: Long,
        val address: String,
        val last: Message,
        val unread: Int
    ) {
        /** Ключ строки: разговор и адрес (у старых прошивок разговора может не быть). */
        val key: String get() = "thread:$threadId:$address"
    }

    @Immutable
    data class UiState(
        val loading: Boolean = true,
        val isDefaultSms: Boolean = false,
        val threads: List<Thread> = emptyList(),
        /** Без роли SMS — приложение SMS человека (кнопка «Открыть «…»»), 2.1.0. */
        val companion: SmsRole.CompanionApp? = null,
        /** С ролью — критичная беда приёма (полоса сверху), 2.1.0. */
        val problem: SmsHealth.Problem? = null,
        /** Полоса «разрешите автозапуск» — один раз (прошивки без проверки), 2.1.0. */
        val autostartHint: Boolean = false,
        /** Черновики (2.2.0): smsPinKey(адрес) → текст. */
        val drafts: Map<String, String> = emptyMap(),
        /** Избранные сообщения (2.2.0), новые первыми. */
        val starred: List<StarredSmsEntity> = emptyList(),
        /** Папки (2.2.0): порядок и видимость. */
        val folders: List<Pair<SmsFolder, Boolean>> = SmsFolders.DEFAULT,
        /** Найденные по тексту SMS (2.2.0), новые первыми. */
        val found: List<Message> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Скрытые до удаления переписки — экран их не показывает. */
    val pendingDeletion: StateFlow<Map<String, Long>> = hidden

    init {
        refresh()
        observe()
        viewModelScope.launch {
            draftsDao.all().map { list -> list.associate { it.address to it.body } }
                .collect { d -> _state.update { it.copy(drafts = d) } }
        }
        viewModelScope.launch {
            starredDao.all().collect { list -> _state.update { it.copy(starred = list) } }
        }
        viewModelScope.launch {
            prefs.smsFolders.map(SmsFolders::parse).collect { f -> _state.update { it.copy(folders = f) } }
        }
        // Автоудаление кодов (2.2.0): при открытии раздела — то, чему пришёл срок
        viewModelScope.launch { suspendRunCatching { otpCleanup.sweep() } }
    }

    private var searchJob: Job? = null

    /**
     * Поиск по тексту всех SMS (2.2.0): после паузы в наборе, от двух
     * знаков. Переписки по имени и номеру ищет экран сам — здесь сообщения.
     */
    fun search(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(found = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DELAY_MS)
            val found = withContext(io) { suspendRunCatching { MessageStore.search(app, query) }.getOrDefault(emptyList()) }
            _state.update { it.copy(found = found) }
        }
    }

    /** Порядок и видимость папок (2.2.0). */
    fun setFolders(list: List<Pair<SmsFolder, Boolean>>) {
        viewModelScope.launch { prefs.setSmsFolders(SmsFolders.encode(list)) }
    }

    /** Снять отметку «избранное» (2.2.0). */
    fun unstar(key: String) {
        viewModelScope.launch(io) { suspendRunCatching { starredDao.delete(listOf(key)) } }
    }

    @OptIn(FlowPreview::class)
    private fun observe() = viewModelScope.launch {
        MessageStore.changes(app).debounce(300).collect { refresh() }
    }

    fun refresh() = viewModelScope.launch {
        val isDefault = SmsRole.isDefault(app)
        val loaded = withContext(io) {
            val threads = if (SmsRole.canRead(app)) loadThreads() else emptyList()
            Triple(
                threads,
                if (isDefault) null else SmsRole.companionApp(app),
                if (isDefault) SmsHealth.problem(app) else null
            )
        }
        val (threads, companion, problem) = loaded
        _state.update {
            it.copy(
                loading = false,
                isDefaultSms = isDefault,
                threads = threads,
                companion = companion,
                problem = problem,
                autostartHint = problem == null && SmsHealth.autostartHintNeeded(app)
            )
        }
    }

    /** Окно последних сообщений — точно; переписки старше — по таблице нитей. */
    private fun loadThreads(): List<Thread> {
        val unread = MessageStore.unreadCounts(app)
        val windowed = MessageStore.latest(app, WINDOW)
            // Сообщение без разговора (редко, у старых прошивок) — по адресу
            .groupBy { if (it.threadId > 0) "t:${it.threadId}" else "a:${it.address}" }
            .map { (_, list) ->
                val newest = list.maxBy { it.date }
                Thread(
                    threadId = newest.threadId,
                    address = newest.address,
                    last = newest,
                    unread = unread?.get(newest.threadId) ?: list.count { !it.outgoing && !it.read }
                )
            }
        val known = windowed.mapTo(HashSet()) { it.threadId }
        val older = MessageStore.threadSummaries(app)
            .filter { it.threadId > 0 && it.threadId !in known && it.messageCount != 0 }
            .map { s ->
                Thread(
                    threadId = s.threadId,
                    address = s.address,
                    last = Message(
                        id = -s.threadId,
                        isMms = false,
                        threadId = s.threadId,
                        address = s.address,
                        body = s.snippet,
                        date = s.date,
                        outgoing = false,
                        status = Message.Status.RECEIVED,
                        read = s.read,
                        subId = null,
                        attachments = if (s.hasAttachment) listOf(Message.Attachment("", "image/*", null)) else emptyList()
                    ),
                    unread = unread?.get(s.threadId) ?: if (s.read) 0 else 1
                )
            }
        if (older.isNotEmpty()) Logger.i(TAG, "Переписок вне окна последних сообщений: ${older.size}")
        return (windowed + older).sortedByDescending { it.last.date }
    }

    /** Полоса «разрешите автозапуск»: открыли экран или закрыли — больше не показывать. */
    fun dismissAutostartHint() {
        SmsSettings.markAutostartHintDone(app)
        _state.update { it.copy(autostartHint = false) }
    }

    /**
     * УДАЛЕНИЕ С «ОТМЕНИТЬ» (2.1.0).
     *
     * Было: окно «Удалить переписку?» с подтверждением — и сразу из хранилища.
     * Стало (правило автора: не объяснять, а выполнять; «Отменить» —
     * не сообщение об ошибке): строки пропадают сразу, внизу — «Переписка
     * удалена · Отменить» с кольцом (как удаление из журнала), из хранилища —
     * когда сообщение погаснет. «Отменить» — строки возвращаются.
     *
     * Удалено из хранилища — [onDeleted] со списком удалённых целиком:
     * только с них снимается закрепление и место в архиве (раньше
     * удаления — нельзя: «Отменить» вернул бы переписку без них).
     *
     * @return что выполнить по исходу сообщения: (погасло, «Отменить»)
     */
    fun deleteWithUndo(
        threads: Collection<Thread>,
        onDeleted: (List<Thread>) -> Unit
    ): Pair<() -> Unit, () -> Unit> {
        val batch = threads.filter { it.key !in hidden.value }
        val marks = batch.associate { it.key to it.last.date }
        hidden.update { it + marks }
        val settled = AtomicBoolean(false)
        var fallback: Job? = null
        val commit: () -> Unit = {
            if (settled.compareAndSet(false, true)) {
                fallback?.cancel()
                deletionScope.launch(io) {
                    val deleted = batch.filter { thread ->
                        val ok = thread.threadId > 0 &&
                            suspendRunCatching { MessageStore.deleteThread(app, thread.threadId, upTo = thread.last.date) }
                                .getOrDefault(false)
                        if (ok) MessageNotifications.cancel(app, thread.threadId)
                        ok
                    }
                    Logger.i(TAG, "Удаление переписок с отменой: ${deleted.size} из ${batch.size}")
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onDeleted(deleted) }
                    // Ключи снимаются, когда список уже перечитан без этих строк — без мелькания
                    refresh().join()
                    withTimeoutOrNull(REFRESH_WAIT_MS) {
                        state.first { s -> s.threads.none { it.key in marks.keys && it.last.date <= marks.getValue(it.key) } }
                    }
                    hidden.update { it - marks.keys }
                }
            }
        }
        val undo: () -> Unit = {
            if (settled.compareAndSet(false, true)) {
                fallback?.cancel()
                hidden.update { it - marks.keys }
            }
        }
        fallback = deletionScope.launch {
            delay(UNDO_FALLBACK_MS)
            commit()
        }
        return commit to undo
    }
}
