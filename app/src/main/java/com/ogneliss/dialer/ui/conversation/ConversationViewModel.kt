package com.ogneliss.dialer.ui.conversation

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsMessage
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogneliss.dialer.data.SimRepository
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.sms.Message
import com.ogneliss.dialer.sms.MessageNotifications
import com.ogneliss.dialer.sms.MessageStore
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
 * ПЕРЕПИСКА (1.10.0) — состояние экрана ConversationScreen.
 *
 * ── Два режима ──────────────────────────────────────────────────────────────
 * • Диалер — приложение SMS по умолчанию: вся история из хранилища,
 *   входящие появляются сами (наблюдение MessageStore.changes), открытая
 *   переписка отмечается прочитанной, можно MMS с картинками.
 * • Не по умолчанию (выбор человека «Писать SMS в диалере»): только
 *   отправка. Историю читать нельзя — на экране видны сообщения,
 *   отправленные отсюда за этот раз, и плашка «сделать приложением SMS».
 *
 * Черновик переживает поворот (SavedStateHandle). Номер карты: из
 * намерения («Написать с SIM 2»), иначе карта последнего сообщения
 * переписки, иначе карта SMS по умолчанию.
 */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    @ApplicationContext private val app: Context,
    private val sims: SimRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private companion object {
        const val TAG = "Conversation"
        const val KEY_DRAFT = "draft"
        const val KEY_DRAFT_SEEDED = "draft_seeded"
        /** Картинок в одном MMS: больше операторы часто не пропускают по размеру. */
        const val MAX_IMAGES = 3
    }

    val address: String = savedState.get<String>(ConversationRequest.ARG_ADDRESS).orEmpty()

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
        val canSend: Boolean = false,
        val sims: List<SimSlot> = emptyList(),
        val subId: Int? = null,
        val draft: String = "",
        val images: List<Uri> = emptyList(),
        val sending: Boolean = false,
        /** «2 SMS · осталось 35» — для длинного текста; null — одна SMS с запасом. */
        val counter: String? = null,
        /** Отправить не удалось — показать ошибку один раз. */
        val sendFailed: Boolean = false
    )

    private val _state = MutableStateFlow(
        UiState(
            title = PhoneFormatter.format(address),
            dialable = address.any { it.isDigit() } && address.none { it.isLetter() },
            draft = savedState.get<String>(KEY_DRAFT).orEmpty()
        )
    )
    val state: StateFlow<UiState> = _state

    /** Отправленные отсюда без роли SMS — истории нет, показываем хотя бы их. */
    private val sentHere = mutableListOf<Message>()
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
            _state.update { it.copy(sims = slots, subId = it.subId ?: requested) }
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
        val canSend = SmsRole.canSend(app)
        val messages = withContext(io) {
            if (!SmsRole.canRead(app)) return@withContext sentHere.toList()
            val thread = threadId ?: MessageStore.threadIdOf(app, address)?.also { threadId = it }
            val list = thread?.let { MessageStore.thread(app, it) }.orEmpty()
            // Открытая переписка — прочитана; уведомление о ней больше не нужно
            if (thread != null && list.any { !it.outgoing && !it.read }) {
                MessageStore.markRead(app, thread)
                MessageNotifications.cancel(app, thread)
            }
            list
        }
        _state.update { s ->
            s.copy(
                messages = messages,
                loading = false,
                isDefaultSms = isDefault,
                canSend = canSend,
                // Карта по умолчанию — карта последнего сообщения переписки
                subId = s.subId ?: messages.lastOrNull { it.subId != null }?.subId
            )
        }
    }

    fun onDraft(text: String) {
        savedState[KEY_DRAFT] = text
        _state.update { it.copy(draft = text, counter = counterFor(text)) }
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(images = (it.images + uris).distinct().take(MAX_IMAGES)) }
    }

    fun removeImage(uri: Uri) = _state.update { it.copy(images = it.images - uri) }

    fun selectSim(subId: Int) = _state.update { it.copy(subId = subId) }

    fun consumeSendFailed() = _state.update { it.copy(sendFailed = false) }

    /** Отправить черновик: с картинками — MMS (только приложение SMS), иначе SMS. */
    fun send() {
        val s = _state.value
        val text = s.draft.trim()
        if (s.sending || (text.isEmpty() && s.images.isEmpty())) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            val ok = withContext(io) {
                if (s.images.isNotEmpty() && SmsRole.isDefault(app)) {
                    MmsTransport.send(app, address, text, s.images, s.subId)
                } else {
                    SmsSender.send(app, address, text, s.subId)
                }
            }
            Logger.i(TAG, if (ok) "Сообщение отправляется" else "Сообщение не отправлено")
            if (ok) {
                if (!SmsRole.canRead(app)) {
                    sentHere += Message(
                        id = -System.nanoTime(), isMms = false, threadId = -1, address = address,
                        body = text, date = System.currentTimeMillis(), outgoing = true,
                        status = Message.Status.SENT, read = true, subId = s.subId
                    )
                }
                savedState[KEY_DRAFT] = ""
                _state.update { it.copy(draft = "", images = emptyList(), counter = null, sending = false) }
            } else {
                _state.update { it.copy(sending = false, sendFailed = true) }
            }
            refresh()
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
            if (!ok) _state.update { it.copy(sendFailed = true) }
            refresh()
        }
    }

    fun delete(message: Message) {
        viewModelScope.launch {
            withContext(io) {
                if (message.id > 0) MessageStore.delete(app, listOf(message.key))
                else sentHere.removeAll { it.id == message.id }
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
