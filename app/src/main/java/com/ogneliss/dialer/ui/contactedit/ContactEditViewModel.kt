package com.ogneliss.dialer.ui.contactedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogneliss.dialer.data.ContactAccount
import com.ogneliss.dialer.data.ContactDraft
import com.ogneliss.dialer.data.ContactEditPlan
import com.ogneliss.dialer.data.ContactEditRepository
import com.ogneliss.dialer.data.ContactEditorLauncher
import com.ogneliss.dialer.data.ContactPhotos
import com.ogneliss.dialer.data.EditOp
import com.ogneliss.dialer.data.EditableValue
import com.ogneliss.dialer.data.LoadedContact
import com.ogneliss.dialer.data.PhotoChange
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.settings.ContactNameFormat
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.util.PermissionGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * СОСТОЯНИЕ СВОЕГО РЕДАКТОРА КОНТАКТА (1.7.0).
 *
 * Отдельная ViewModel, а не ещё одна часть DialerViewModel: у редактора
 * своё время жизни (открыт — черновик, закрыт — забыт) и свои аргументы
 * маршрута: id контакта для правки или номер и имя для нового.
 * Правила записи — ContactEditModel.kt, сама запись — ContactEditRepository.
 */
@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val app: Context,
    private val repo: ContactEditRepository,
    private val prefs: Prefs,
    private val systemEditor: ContactEditorLauncher
) : ViewModel() {

    private companion object {
        const val TAG = "ContactEdit"
        /** Ключи новых строк — подальше от ключей прочитанных. */
        const val NEW_KEY_START = 100_000L
    }

    /** id контакта для правки; не больше нуля — новый контакт. */
    val contactId: Long = savedState.get<Long>(ARG_ID) ?: -1L
    val isNew: Boolean get() = contactId <= 0

    data class UiState(
        val loading: Boolean = true,
        val notFound: Boolean = false,
        val draft: ContactDraft = ContactDraft(),
        /** С чем сравнивать «есть ли изменения». */
        val original: ContactDraft = ContactDraft(),
        val loaded: LoadedContact? = null,
        val accounts: List<ContactAccount> = emptyList(),
        val account: ContactAccount? = null,
        /** Фото из книги — для показа, пока его не сменили. */
        val photoUri: String? = null,
        /** Новое фото (JPEG) — после обрезки. */
        val newPhoto: ByteArray? = null,
        val photoRemoved: Boolean = false,
        val saving: Boolean = false
    ) {
        val dirty: Boolean
            get() = draft != original || newPhoto != null || photoRemoved

        /**
         * Есть что сохранить (2.1.0): без имени, номера, почты, организации
         * и фото «Сохранить» неактивна. Было — сообщение «Введите имя,
         * номер или почту» после нажатия.
         */
        val canSave: Boolean
            get() = !draft.isEmpty || newPhoto != null

        /** Есть ли что показать в круге: новое фото или прежнее, если не убрано. */
        val hasPhoto: Boolean
            get() = newPhoto != null || (!photoRemoved && !photoUri.isNullOrBlank())
    }

    /*
     * События экрану. 2.1.0 (правило автора 07.11.2026 «не объяснять —
     * выполнить или пропустить»): событий-сообщений больше нет. Было:
     * Failed, Empty, NoPermission, PhotoUnreadable — каждое со своей
     * строкой внизу экрана. Стало: не сохранилось — та же форма
     * в системных «Контактах» (openDraftInSystem); пусто — «Сохранить»
     * неактивна (UiState.canSave); нет разрешения — системный запрос
     * (AskWritePermission), отказ — снова системные «Контакты»; снимок
     * не прочитался — остаётся прежнее фото, причина — в журнал.
     */
    sealed interface Event {
        data class Saved(val contactId: Long?) : Event
        data object OpenedInSystem : Event
        /** Нет WRITE_CONTACTS — экран спрашивает систему (ответ — writePermissionResult). */
        data object AskWritePermission : Event
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Порядок полей имени — как «Как показывать имя»: «Фамилия Имя» — фамилия первой. */
    val familyFirst: StateFlow<Boolean> = prefs.contactNameFormat
        .map { it == ContactNameFormat.FAMILY_FIRST }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /*
     * Channel, а не SharedFlow (2.1.0): «открыть в системных «Контактах»»
     * для контакта только из мессенджеров или SIM решается сразу после
     * загрузки, и событие не должно пропасть, если экран ещё не начал
     * слушать. Каждое событие — одному получателю, один раз.
     */
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /** Снимок на экране обрезки; null — экрана нет. */
    private val _cropSource = MutableStateFlow<Bitmap?>(null)
    val cropSource: StateFlow<Bitmap?> = _cropSource

    private var nextKey = NEW_KEY_START

    init {
        val number = savedState.get<String>(ARG_NUMBER).orEmpty()
        val name = savedState.get<String>(ARG_NAME).orEmpty()
        viewModelScope.launch { if (isNew) startNew(number, name) else startEdit(number) }
    }

    private suspend fun startNew(number: String, name: String) {
        val draft = ContactDraft(
            given = name.trim(),
            phones = listOf(EditableValue(nextKey++, number.trim(), ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE))
        )
        val accounts = repo.accounts()
        val remembered = prefs.contactAccount.first()
        _state.value = UiState(
            loading = false,
            draft = draft,
            // Пустой черновик — без вопроса «не сохранять?» при выходе
            original = draft,
            accounts = accounts,
            account = accounts.firstOrNull { it.key == remembered } ?: accounts.firstOrNull()
        )
    }

    /**
     * [attachNumber] — «Добавить номер к контакту» (1.7.0): контакт выбран
     * в списке, номер встаёт новой строкой. Он уже есть — ничего не
     * добавляется: второй такой же номер в контакте никому не нужен.
     */
    private suspend fun startEdit(attachNumber: String) {
        val loaded = repo.load(contactId)
        if (loaded == null) {
            // 2.1.0: экран закрывается сам, без «Контакт не найден» (ContactEditScreen)
            Logger.i(TAG, "Контакт для правки не найден — редактор закрывается")
            _state.value = UiState(loading = false, notFound = true)
            return
        }
        val digits = attachNumber.filter { it.isDigit() }
        val known = loaded.draft.phones.any { phone ->
            val own = phone.value.filter { it.isDigit() }
            digits.isNotEmpty() && (own.endsWith(digits.takeLast(10)) || digits.endsWith(own.takeLast(10)))
        }
        val draft = if (attachNumber.isBlank() || known) loaded.draft else loaded.draft.copy(
            phones = loaded.draft.phones +
                EditableValue(nextKey++, attachNumber.trim(), ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
        )
        _state.value = UiState(
            loading = false,
            draft = draft,
            original = loaded.draft,
            loaded = loaded,
            photoUri = loaded.photoUri
        )
    }

    // ── Правка полей ─────────────────────────────────────────────────────────

    fun edit(change: (ContactDraft) -> ContactDraft) = _state.update { it.copy(draft = change(it.draft)) }

    fun addPhone() = edit {
        it.copy(phones = it.phones + EditableValue(nextKey++, "", ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE))
    }

    fun addEmail() = edit {
        it.copy(emails = it.emails + EditableValue(nextKey++, "", ContactsContract.CommonDataKinds.Email.TYPE_HOME))
    }

    fun chooseAccount(account: ContactAccount) = _state.update { it.copy(account = account) }

    // ── Фото ─────────────────────────────────────────────────────────────────

    /** Снимок выбран — читаем в фоне и открываем обрезку. */
    fun photoPicked(uri: Uri) = viewModelScope.launch {
        val bitmap = withContext(Dispatchers.IO) { ContactPhotos.load(app, uri) }
        if (bitmap == null) {
            // 2.1.0: без сообщения «Не удалось открыть снимок» — остаётся прежнее фото
            Logger.w(TAG, "Снимок не прочитался — фото не меняется")
        } else {
            _cropSource.value = bitmap
        }
    }

    /** Квадрат выбран: режем, сжимаем, показываем в круге. */
    fun cropDone(square: Rect) = viewModelScope.launch {
        val source = _cropSource.value ?: return@launch
        val jpeg = withContext(Dispatchers.Default) { ContactPhotos.crop(source, square) }
        _cropSource.value = null
        _state.update { it.copy(newPhoto = jpeg, photoRemoved = false) }
    }

    fun cropCancelled() {
        _cropSource.value = null
    }

    fun removePhoto() = _state.update { it.copy(newPhoto = null, photoRemoved = true) }

    // ── Сохранение ───────────────────────────────────────────────────────────

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        if (!s.canSave) {
            // Кнопка в этом случае неактивна — сюда попадает только гонка нажатий
            Logger.d(TAG, "Сохранять нечего")
            return
        }
        if (!PermissionGuard.canWriteContacts(app)) {
            _events.trySend(Event.AskWritePermission)
            return
        }
        val photo = when {
            s.newPhoto != null -> PhotoChange.Set(s.newPhoto)
            s.photoRemoved -> PhotoChange.Remove
            else -> PhotoChange.Keep
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val event = if (isNew) {
                val account = s.account
                val id = if (account != null) repo.saveNew(account, s.draft, photo) else null
                if (account != null && id != null) {
                    prefs.setContactAccount(account.key)
                    Event.Saved(id)
                } else {
                    null
                }
            } else {
                val loaded = s.loaded
                if (loaded != null && repo.saveEdit(loaded, s.draft, photo)) Event.Saved(contactId) else null
            }
            _state.update { it.copy(saving = false) }
            if (event != null) _events.trySend(event) else openDraftInSystem()
        }
    }

    /**
     * Ответ системы на запрос WRITE_CONTACTS (2.1.0): выдано — сохранить
     * своим редактором; отказ — та же форма в системных «Контактах»,
     * у них своё разрешение. Было: сообщение «Нет разрешения… разрешите
     * в настройках приложения».
     */
    fun writePermissionResult(granted: Boolean) {
        if (granted) save() else {
            Logger.i(TAG, "Запись контактов не разрешена — черновик уходит в системные «Контакты»")
            openDraftInSystem()
        }
    }

    /*
     * Свой редактор не сохранил — запасной путь вместо сообщения
     * «Не удалось сохранить контакт» (2.1.0): системная форма с набранным
     * (ContactEditorLauncher.openDraft). Новый контакт — все строки;
     * правка — только новые (вставки из плана правки). Открыть некому —
     * черновик остаётся на экране, причина — в журнале.
     */
    private fun openDraftInSystem() {
        val s = _state.value
        val loaded = s.loaded
        val plan = when {
            isNew -> ContactEditPlan.forNew(s.draft)
            loaded != null -> ContactEditPlan.forEdit(loaded, s.draft)
            else -> emptyList()
        }
        val rows = plan.filterIsInstance<EditOp.Insert>().map { it.row }
        if (systemEditor.openDraft(contactId.takeIf { !isNew }, rows)) {
            _events.trySend(Event.OpenedInSystem)
        } else {
            Logger.w(TAG, "Черновик некуда передать — остаётся в редакторе")
        }
    }

    /**
     * «Открыть в системных «Контактах»»: для фото с эффектами, адресов,
     * групп. Свой черновик при этом закрывается — иначе, вернувшись,
     * человек сохранил бы поверх правок системного приложения старое.
     */
    fun openInSystem() {
        if (isNew) return
        systemEditor.edit(contactId)
        _events.trySend(Event.OpenedInSystem)
    }
}

/** Аргументы маршрута редактора: «contact_edit?id=…&number=…&name=…». */
const val ARG_ID = "id"
const val ARG_NUMBER = "number"
const val ARG_NAME = "name"
