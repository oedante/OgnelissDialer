package com.ogneliss.dialer.ui.conversation

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsMessage
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import com.ogneliss.dialer.data.CallLogSource
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.SimRepository
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.data.db.SmsDraftDao
import com.ogneliss.dialer.data.db.SmsDraftEntity
import com.ogneliss.dialer.data.db.StarredSmsDao
import com.ogneliss.dialer.data.db.StarredSmsEntity
import com.ogneliss.dialer.di.ApplicationScope
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.sms.SmsQueue
import com.ogneliss.dialer.sms.SmsSettings
import com.ogneliss.dialer.sms.smsPinKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.sms.Message
import com.ogneliss.dialer.sms.MessageNotifications
import com.ogneliss.dialer.sms.MessageStore
import com.ogneliss.dialer.sms.MmsDownloads
import com.ogneliss.dialer.sms.MmsTransport
import com.ogneliss.dialer.sms.SmsRole
import com.ogneliss.dialer.sms.SmsSender
import com.ogneliss.dialer.util.PermissionGuard
import com.ogneliss.dialer.util.PhoneFormatter
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
 * ПЕРЕПИСКА (2.0.0) — состояние экрана ConversationScreen.
 *
 * Диалер — приложение SMS по умолчанию: вся история из хранилища,
 * входящие появляются сами (наблюдение MessageStore.changes), открытая
 * переписка отмечается прочитанной, можно MMS с картинками.
 *
 * 2.1.0: режима «только отправка» без роли SMS больше нет (было:
 * «Писать SMS в диалере» — сообщения, отправленные за этот раз, плашка
 * «сделать приложением SMS», окно SEND_SMS). Без роли экран передаёт
 * переписку приложению SMS человека и закрывается (ConversationScreen).
 *
 * Черновик переживает поворот (SavedStateHandle). Номер карты: из
 * намерения («Написать с SIM 2»), иначе карта последнего сообщения
 * переписки, иначе карта SMS по умолчанию.
 *
 * ── 2.2.0 ──────────────────────────────────────────────────────────────────
 * • Черновик — ещё и в базе (sms_drafts): переживает закрытие экрана,
 *   в списке SMS — «Черновик: …». Пишется с задержкой после набора,
 *   в области приложения: закрытый экран запись не обрывает.
 * • Отправка через очередь (SmsQueue): с «Отменить» на несколько секунд
 *   (настройка «Отмена отправки») или на выбранное время («Отправить
 *   позже» — долгое нажатие на «Отправить»). MMS с картинками — сразу.
 * • Избранное — копии отмеченных сообщений (sms_starred).
 * • Звонки с собеседником — в ленте между сообщениями (настройка
 *   «Звонки в переписке»), с номером SIM; не старше первого сообщения
 *   ленты, иначе давняя история звонков вытеснила бы переписку.
 */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    @ApplicationContext private val app: Context,
    private val sims: SimRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val callLog: CallLogSource,
    private val prefs: Prefs,
    private val drafts: SmsDraftDao,
    private val starredDao: StarredSmsDao,
    private val queue: SmsQueue,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private companion object {
        const val TAG = "Conversation"
        const val KEY_DRAFT = "draft"
        const val KEY_DRAFT_SEEDED = "draft_seeded"
        /** Картинок в одном MMS: больше операторы часто не пропускают по размеру. */
        const val MAX_IMAGES = 3
        /** Черновик в базу — после паузы в наборе, не на каждую букву. */
        const val DRAFT_SAVE_DELAY_MS = 600L
        /** Звонков в ленте — последние; дальше ленту листают ради сообщений. */
        const val CALLS_LIMIT = 100
    }

    /** Звонок в ленте переписки (2.2.0). */
    @Immutable
    data class CallEvent(
        val id: Long,
        val type: CallType,
        val date: Long,
        val durationSec: Long,
        /** Слот SIM при двух картах; null — одна карта или неизвестно. */
        val simSlot: Int?
    ) {
        val key: String get() = "call:$id"
    }

    /** SMS в очереди (2.2.0): ждёт окна «Отменить» или выбранного времени. */
    @Immutable
    data class Queued(val id: Long, val body: String, val atMillis: Long, val scheduled: Boolean)



    val address: String = savedState.get<String>(ConversationRequest.ARG_ADDRESS).orEmpty()

    /** Текст и карта из намерения — их получит приложение SMS, если роли нет (2.1.0). */
    val initialBody: String? = savedState.get<String>(ConversationRequest.ARG_BODY)?.takeIf { it.isNotEmpty() }
    val requestedSubId: Int? = savedState.get<Int>(ConversationRequest.ARG_SUB)?.takeIf { it >= 0 }

    /** Сообщение, к которому прокрутить (2.2.0: найдено поиском, из избранного). */
    val focusKey: String? = savedState.get<String>(ConversationRequest.ARG_MSG)?.takeIf { it.isNotEmpty() }

    /** Ключ переписки для черновика и очереди: «8 912…» и «+7 912…» — одна. */
    private val draftKey: String = smsPinKey(address)
    private var draftJob: Job? = null

    data class UiState(
        val title: String,
        /** Номер под именем; null — имени нет, заголовок и есть номер. */
        val subtitle: String? = null,
        val photoUri: String? = null,
        /** Номер можно набрать — у буквенного отправителя («Bank») нельзя. */
        val dialable: Boolean = true,
        /** От старых к новым. */
        val messages: List<Message> = emptyList(),
        val loading: Boolean = true,
        val isDefaultSms: Boolean = false,
        /** Оператор разрешает MMS на выбранной карте — можно прикрепить картинку (2.1.0). */
        val mmsEnabled: Boolean = true,
        val sims: List<SimSlot> = emptyList(),
        val subId: Int? = null,
        val draft: String = "",
        val images: List<Uri> = emptyList(),
        val sending: Boolean = false,
        /** «2 SMS · осталось 35» — для длинного текста; null — одна SMS с запасом. */
        val counter: String? = null,
        /** Звонки в ленте (2.2.0): от старых к новым; пусто — настройка выключена. */
        val calls: List<CallEvent> = emptyList(),
        /** Очередь отправки этой переписки (2.2.0). */
        val queued: List<Queued> = emptyList(),
        /** Ключи избранных сообщений (2.2.0). */
        val starred: Set<String> = emptySet()
    )

    private val _state = MutableStateFlow(
        UiState(
            title = PhoneFormatter.format(address),
            dialable = address.any { it.isDigit() } && address.none { it.isLetter() },
            draft = savedState.get<String>(KEY_DRAFT).orEmpty()
        )
    )
    val state: StateFlow<UiState> = _state

    private var threadId: Long? = null

    init {
        // Текст из намерения («Поделиться», sms:…?body=) — один раз, не поверх черновика
        if (savedState.get<Boolean>(KEY_DRAFT_SEEDED) != true) {
            savedState[KEY_DRAFT_SEEDED] = true
            savedState.get<String>(ConversationRequest.ARG_BODY)?.takeIf { it.isNotEmpty() }?.let(::onDraft)
        }
        viewModelScope.launch { loadContact() }
        viewModelScope.launch {
            val slots = runCatching { sims.slotsAsync() }.getOrDefault(emptyList())
            val requested = savedState.get<Int>(ConversationRequest.ARG_SUB)?.takeIf { it >= 0 }
            _state.update {
                // Переписка ещё читается — карту последнего сообщения выберет refresh()
                val fallback = if (!it.loading && slots.size > 1) SmsSender.resolveSubId(app, null) else null
                it.copy(sims = slots, subId = it.subId ?: requested ?: fallback)
            }
        }
        // Черновик из базы (2.2.0) — если экран открыт заново, а не повёрнут, и текста из намерения нет
        if (savedState.get<String>(KEY_DRAFT) == null && initialBody == null) {
            viewModelScope.launch {
                val saved = withContext(io) { runCatching { drafts.get(draftKey) }.getOrNull() }?.body
                if (!saved.isNullOrEmpty() && _state.value.draft.isEmpty()) {
                    savedState[KEY_DRAFT] = saved
                    _state.update { it.copy(draft = saved, counter = counterFor(saved)) }
                }
            }
        }
        viewModelScope.launch {
            queue.all().map { list ->
                list.filter { it.addressKey == draftKey }.map { Queued(it.id, it.body, it.atMillis, it.scheduled) }
            }.collect { q -> _state.update { it.copy(queued = q) } }
        }
        viewModelScope.launch {
            starredDao.all().map { list -> list.mapTo(HashSet()) { it.msgKey } }
                .collect { keys -> _state.update { it.copy(starred = keys) } }
        }
        refresh()
        observe()
    }

    @OptIn(FlowPreview::class)
    private fun observe() = viewModelScope.launch {
        MessageStore.changes(app).debounce(250).collect { refresh() }
    }

    /** Перечитать переписку и права; отметить прочитанным. */
    fun refresh() = viewModelScope.launch {
        val isDefault = SmsRole.isDefault(app)
        val mms = withContext(io) { MmsTransport.isEnabled(app, _state.value.subId) }
        val messages = withContext(io) {
            if (!SmsRole.canRead(app)) return@withContext emptyList()
            val thread = threadId ?: MessageStore.threadIdOf(app, address)?.also { threadId = it }
            val list = thread?.let { MessageStore.thread(app, it) }.orEmpty()
            // Открытая переписка — прочитана; уведомление о ней больше не нужно
            if (thread != null && list.any { !it.outgoing && !it.read }) {
                MessageStore.markRead(app, thread)
                MessageNotifications.cancel(app, thread)
            }
            list
        }
        val calls = loadCalls(messages)
        _state.update { s ->
            s.copy(
                messages = messages,
                calls = calls,
                loading = false,
                isDefaultSms = isDefault,
                mmsEnabled = mms,
                // Карта по умолчанию — карта последнего сообщения переписки, иначе
                // карта SMS из настроек Android (при двух SIM — видно в строке ввода)
                subId = s.subId ?: messages.lastOrNull { it.subId != null }?.subId
                    ?: if (s.sims.size > 1) SmsSender.resolveSubId(app, null) else null
            )
        }
    }

    fun onDraft(text: String) {
        savedState[KEY_DRAFT] = text
        _state.update { it.copy(draft = text, counter = counterFor(text)) }
        persistDraft(text, DRAFT_SAVE_DELAY_MS)
    }

    /** Черновик в базу (2.2.0): пустой — удалить. Область приложения — экран может закрыться. */
    private fun persistDraft(text: String, wait: Long) {
        draftJob?.cancel()
        draftJob = appScope.launch {
            if (wait > 0) delay(wait)
            withContext(io) {
                runCatching {
                    if (text.isBlank()) drafts.delete(draftKey)
                    else drafts.upsert(SmsDraftEntity(draftKey, text, System.currentTimeMillis()))
                }.onFailure { Logger.w(TAG, "Черновик не сохранён", it) }
            }
        }
    }

    /** Поле ввода очищено после отправки — и черновик в базе. */
    private fun clearDraft() {
        savedState[KEY_DRAFT] = ""
        _state.update { it.copy(draft = "", images = emptyList(), counter = null, sending = false) }
        persistDraft("", 0)
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(images = (it.images + uris).distinct().take(MAX_IMAGES)) }
    }

    fun removeImage(uri: Uri) = _state.update { it.copy(images = it.images - uri) }

    fun selectSim(subId: Int) {
        _state.update { it.copy(subId = subId) }
        // MMS разрешает оператор карты — другая карта, другой ответ
        viewModelScope.launch {
            val mms = withContext(io) { MmsTransport.isEnabled(app, subId) }
            _state.update { it.copy(mmsEnabled = mms) }
        }
    }

    /**
     * Отправить черновик: с картинками — MMS (только приложение SMS), иначе SMS.
     *
     * 2.1.0: не ушло — без сообщения «Не отправлено» внизу экрана (правило
     * автора: не показывать ошибок): черновик остаётся в поле, записанное
     * в хранилище неотправленное — пузырём с повтором. Без роли SMS
     * не отправляет вовсе: экран передаёт переписку приложению SMS.
     */
    fun send() {
        val s = _state.value
        val text = s.draft.trim()
        if (s.sending || (text.isEmpty() && s.images.isEmpty())) return
        if (!SmsRole.isDefault(app)) {
            Logger.w(TAG, "Отправка без роли SMS пропущена")
            return
        }
        /*
         * ОТМЕНА ОТПРАВКИ (2.2.0): текст — в очередь на несколько секунд,
         * поле сразу пустое, под пузырём «Отменить». MMS — сразу: картинки
         * в очередь не кладутся (это ссылки на файлы, они могут исчезнуть).
         */
        val undo = SmsSettings.undoSeconds(app)
        if (s.images.isEmpty() && undo > 0) {
            clearDraft()
            viewModelScope.launch {
                queue.enqueue(address, text, s.subId, System.currentTimeMillis() + undo * 1000L, scheduled = false)
            }
            return
        }
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            val ok = withContext(io) {
                if (s.images.isNotEmpty() && s.mmsEnabled) {
                    MmsTransport.send(app, address, text, s.images, s.subId)
                } else {
                    SmsSender.send(app, address, text, s.subId)
                }
            }
            Logger.i(TAG, if (ok) "Сообщение отправляется" else "Сообщение не отправлено — черновик оставлен")
            if (ok) {
                clearDraft()
            } else {
                _state.update { it.copy(sending = false) }
            }
            refresh()
        }
    }

    /**
     * ОТПРАВИТЬ ПОЗЖЕ (2.2.0): текст — в очередь на [atMillis]. Картинки
     * не откладываются (MMS — сразу), поэтому с ними кнопка не предлагается.
     */
    fun sendLater(atMillis: Long) {
        val s = _state.value
        val text = s.draft.trim()
        if (text.isEmpty() || s.images.isNotEmpty() || !SmsRole.isDefault(app)) return
        clearDraft()
        viewModelScope.launch { queue.enqueue(address, text, s.subId, atMillis, scheduled = true) }
    }

    /** «Отменить»: убрать из очереди, текст — обратно в поле (к уже набранному). */
    fun cancelQueued(id: Long) {
        viewModelScope.launch {
            val entry = queue.cancel(id) ?: return@launch
            val current = _state.value.draft
            onDraft(if (current.isBlank()) entry.body else entry.body + "\n" + current)
        }
    }

    /** «Отправить сейчас» — отложенное, не дожидаясь срока. */
    fun sendQueuedNow(id: Long) {
        viewModelScope.launch { queue.sendNow(id) }
    }

    /** Отметить или снять избранное (2.2.0). */
    fun star(messages: List<Message>, on: Boolean) {
        if (messages.isEmpty()) return
        appScope.launch(io) {
            runCatching {
                if (on) {
                    val now = System.currentTimeMillis()
                    starredDao.upsert(messages.map {
                        StarredSmsEntity(it.key, it.address.ifBlank { address }, it.body, it.date, it.outgoing, now)
                    })
                } else {
                    starredDao.delete(messages.map { it.key })
                }
            }.onFailure { Logger.w(TAG, "Избранное не изменено", it) }
        }
    }

    /** Удалить выбранные (2.2.0). */
    fun deleteMany(messages: List<Message>) {
        val keys = messages.filter { it.id > 0 }.map { it.key }
        if (keys.isEmpty()) return
        viewModelScope.launch {
            withContext(io) { MessageStore.delete(app, keys) }
            refresh()
        }
    }

    /** Звонки с собеседником в ленту (2.2.0): настройка, номер, не старше первого сообщения. */
    private suspend fun loadCalls(messages: List<Message>): List<CallEvent> {
        if (messages.isEmpty() || !_state.value.dialable) return emptyList()
        if (!runCatching { prefs.smsCallsInThread.first() }.getOrDefault(true)) return emptyList()
        val oldest = messages.first().date
        return withContext(io) {
            runCatching { callLog.historyFor(address, CALLS_LIMIT) }.getOrDefault(emptyList())
                .filter { it.date >= oldest }
                .map { CallEvent(it.id, it.type, it.date, it.durationSec, sims.slotForAccountId(it.phoneAccountId)) }
                .sortedBy { it.date }
        }
    }

    /**
     * «MMS не загружено — нажмите, чтобы загрузить» (2.1.0): скачать сейчас.
     * Скачается — строка сама сменится сообщением (наблюдение за хранилищем).
     */
    fun downloadMms(message: Message) {
        if (!message.isMms || message.status != Message.Status.NOT_DOWNLOADED || message.id <= 0) return
        viewModelScope.launch {
            val started = withContext(io) { MmsDownloads.downloadFromInbox(app, message.id) }
            Logger.i(TAG, if (started) "MMS скачивается по касанию" else "MMS по касанию не скачать — нет адреса")
        }
    }

    /** Повторить неотправленное: старое удаляется, уходит новое. */
    fun retry(message: Message) {
        if (message.isMms || message.body.isBlank()) return
        viewModelScope.launch {
            val ok = withContext(io) {
                SmsSender.send(app, address, message.body, message.subId ?: _state.value.subId).also { sent ->
                    if (sent && message.id > 0) MessageStore.delete(app, listOf(message.key))
                }
            }
            if (!ok) Logger.w(TAG, "Повтор не ушёл")
            refresh()
        }
    }

    fun delete(message: Message) {
        viewModelScope.launch {
            withContext(io) {
                if (message.id > 0) MessageStore.delete(app, listOf(message.key))
            }
            refresh()
        }
    }

    /**
     * «2 SMS · осталось 35»: длинный текст делится на части, каждая —
     * отдельное SMS у оператора. Кириллица — 70 знаков на SMS, латиница — 160.
     */
    private fun counterFor(text: String): String? {
        if (text.isEmpty()) return null
        val r = runCatching { SmsMessage.calculateLength(text, false) }.getOrNull() ?: return null
        val parts = r[0]
        val left = r[2]
        return if (parts > 1 || left <= 20) "$parts · $left" else null
    }

    /** Имя и фото из книги — заголовок экрана. */
    private suspend fun loadContact() {
        if (!PermissionGuard.canReadContacts(app) || address.isBlank()) return
        val found = withContext(io) {
            runCatching {
                app.contentResolver.query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                    null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null }
            }.getOrNull()
        } ?: return
        _state.update {
            it.copy(
                title = found.first ?: it.title,
                subtitle = if (found.first != null) PhoneFormatter.format(address) else null,
                photoUri = found.second
            )
        }
    }
}
