package com.ogneliss.dialer.ui

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.ogneliss.dialer.data.ContactNames
import com.ogneliss.dialer.data.PinnedController
import com.ogneliss.dialer.data.PinnedEntry
import com.ogneliss.dialer.concurrency.suspendRunCatching

import android.os.Build
import androidx.compose.runtime.Immutable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ogneliss.dialer.util.AppPermissions
import com.ogneliss.dialer.ui.components.BlockedReasonText
import com.ogneliss.dialer.call.CallActions
import com.ogneliss.dialer.call.ExternalApps
import com.ogneliss.dialer.background.ContentChangeObserver
import com.ogneliss.dialer.background.CallbackReminder
import com.ogneliss.dialer.background.T9IndexWorker
import com.ogneliss.dialer.data.*
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.settings.SpeedDialStore
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.ui.theme.MessageBlue
import com.ogneliss.dialer.settings.SwipeSimMode
import com.ogneliss.dialer.settings.SwipeTuning
import com.ogneliss.dialer.data.CallLogLoader
import com.ogneliss.dialer.data.CallLogRowMapper
import com.ogneliss.dialer.ui.components.CallLogRowState
import com.ogneliss.dialer.di.DefaultDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.ogneliss.dialer.data.CallLogFilter
import com.ogneliss.dialer.data.ContactSearch
import com.ogneliss.dialer.data.SimCallPlan
import com.ogneliss.dialer.data.CallStatsCalculator
import com.ogneliss.dialer.settings.SettingsStateSource
import com.ogneliss.dialer.settings.SettingsCommands
import com.ogneliss.dialer.util.PreDialWarning
import com.ogneliss.dialer.util.PermissionGuard
import com.ogneliss.dialer.ui.components.NumberOption
import com.ogneliss.dialer.ui.components.swipe.RevealAction
import com.ogneliss.dialer.ui.components.swipe.RevealActionFactory
import com.ogneliss.dialer.ui.components.swipe.AndroidRevealStrings
import com.ogneliss.dialer.data.SimSlot
import android.content.Intent
import com.ogneliss.dialer.util.SystemLinks
import com.ogneliss.dialer.data.BlockingController
import com.ogneliss.dialer.data.PinnedContacts
import com.ogneliss.dialer.data.RuleKind
import com.ogneliss.dialer.call.PostCallCoordinator
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.DirectoryLookupRepository
import com.ogneliss.dialer.data.DirectoryProvider
import com.ogneliss.dialer.data.ExternalCallerIdController
import com.ogneliss.dialer.util.VoiceInput
import com.ogneliss.dialer.util.VoiceQueryParser
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.log.Stage
import com.ogneliss.dialer.util.DialDigits
import com.ogneliss.dialer.util.PhoneFormatter
import com.ogneliss.dialer.concurrency.debounceContentChanges
import android.content.Context
import kotlinx.coroutines.flow.combine
import com.ogneliss.dialer.t9.T9Engine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Помечено @Immutable: без этого Compose считает состояние
 * изменившимся при каждом обновлении и перерисовывает всё,
 * что от него зависит, — из-за полей типа List и Map.
 */
@Immutable
data class UiState(
    val query: String = "",
    val callLog: List<CallLogGroup> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val filteredLog: List<CallLogGroup> = emptyList(),
    val filteredContacts: List<Contact> = emptyList(),
    val sims: List<SimSlot> = emptyList(),
    val numberInfo: Map<String, NumberInfo> = emptyMap(),
    val notes: Map<String, NumberNote> = emptyMap(),
    /**
     * Отметки «спам» и «мошенники» по номеру.
     *
     * С 1.1.1 отдельно от заметок — разбор в SpamMarkEntity.
     */
    val spamMarks: Map<String, SpamMark> = emptyMap(),
    val loading: Boolean = true,

    /**
     * Состояние разрешений.
     *
     * Нужно интерфейсу для честного пустого состояния: «звонков нет»
     * и «нет доступа к журналу» — разные причины, и подсказки к ним
     * разные. Раньше оба случая выглядели одинаково, и пользователь
     * после отказа считал, что журнал действительно пуст.
     */
    val hasCallLogPermission: Boolean = true,
    val hasContactsPermission: Boolean = true
)

@HiltViewModel
class DialerViewModel @Inject constructor(
    private val app: Application,
    private val callLogRepo: CallLogRepository,
    private val callLogLoader: CallLogLoader,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val contactsRepo: ContactsRepository,
    private val simRepo: SimRepository,
    private val speedDialStore: SpeedDialStore,
    private val numberInfoRepo: NumberInfoRepository,
    private val statsRepo: CallStatsRepository,
    private val observer: ContentChangeObserver,
    private val notesRepo: NotesRepository,
    private val spamMarksRepo: SpamMarksRepository,
    private val blocking: BlockingController,
    /**
     * Журнал причин блокировки.
     *
     * Заблокированный вызов выглядит в системном журнале как обычный
     * пропущенный. Отсюда берутся сведения, почему телефон не зазвонил.
     */
    private val blockedCallLog: com.ogneliss.dialer.data.BlockedCallLog,
    /**
     * Кэш телефонной книги.
     *
     * Даёт фотографии и группировку сразу при запуске, пока идёт
     * настоящее чтение провайдера — оно занимает секунды.
     */
    private val contactCache: com.ogneliss.dialer.data.ContactCache,
    /** Напоминания: список, постановка и отмена. */
    private val reminders: com.ogneliss.dialer.data.RemindersRepository,
    /** Определение собственного номера — по возможности. */
    private val ownNumberSource: com.ogneliss.dialer.data.OwnNumber,
    private val systemBlock: SystemBlockRepository,
    private val simPrefs: SimPreferenceRepository,
    private val settingsSource: SettingsStateSource,
    private val settingsCommands: SettingsCommands,
    private val contactActions: ContactActionsController,
    private val postCallCoordinator: PostCallCoordinator,
    private val exporter: com.ogneliss.dialer.backup.ExportController,
    private val pinned: com.ogneliss.dialer.data.PinnedController,
    private val directoryRepo: DirectoryLookupRepository,
    private val externalCallerId: ExternalCallerIdController,
    val prefs: Prefs
) : AndroidViewModel(app) {

    val actions = CallActions(app, simRepo)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val swipeRight = prefs.swipeRight.stateIn(viewModelScope, SharingStarted.Eagerly, SwipeAction.CALL)
    val swipeLeft = prefs.swipeLeft.stateIn(viewModelScope, SharingStarted.Eagerly, SwipeAction.SMS)

    /**
     * Дополнительные действия свайпа.
     *
     * Основное срабатывает сразу, дополнительные показываются кнопками
     * при более длинном движении: быстрый путь остаётся быстрым,
     * а выбор — доступным.
     */
    /** Параметры чувствительности свайпа. */
    val swipeTuning = prefs.swipeTuning
        .stateIn(viewModelScope, SharingStarted.Eagerly, SwipeTuning.DEFAULT)

    fun setSwipeTuning(tuning: SwipeTuning) = viewModelScope.launch {
        prefs.setSwipeTuning(tuning)
    }

    fun resetSwipeTuning() = viewModelScope.launch { prefs.resetSwipeTuning() }

    val swipeRightExtra = prefs.swipeRightExtra
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val swipeLeftExtra = prefs.swipeLeftExtra
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSwipeExtra(isRight: Boolean, actions: List<SwipeAction>) = viewModelScope.launch {
        if (isRight) prefs.setSwipeRightExtra(actions) else prefs.setSwipeLeftExtra(actions)
    }

    /*
     * КЛАВИАТУРА ИЗ КОНТАКТОВ (1.6.0, вариант «Б»).
     *
     * У контактов та же панель, что у журнала, и кнопка клавиатуры на ней
     * ведёт в журнал: набор номера живёт там. Журнал открывается заново,
     * и просьба «открыть клавиатуру» ждёт его здесь — экран забирает её
     * один раз, при появлении (разбор в MainDialerScreen, ON_START).
     */
    @Volatile
    private var dialpadRequested = false

    fun requestDialpad() {
        dialpadRequested = true
    }

    /** Была просьба открыть клавиатуру — true, и просьба снимается. */
    fun takeDialpadRequest(): Boolean {
        val requested = dialpadRequested
        dialpadRequested = false
        return requested
    }

    /**
     * Кнопки панели для набора действий.
     *
     * Если дополнительных нет, поведение прежнее: одно действие,
     * прямое срабатывание. Если есть — панель показывает и основное,
     * и дополнительные, чтобы выбор был явным.
     */
    /**
     * Вид прямого действия для подсказки при свайпе.
     *
     * Строится один раз на набор настроек: подсказка одинакова
     * для всех строк и зависит только от выбранного действия.
     */
    fun directActionAppearance(action: SwipeAction): RevealAction? =
        if (action == SwipeAction.NONE) null
        else RevealActionFactory.appearanceOnly(action, revealStrings)

    fun revealActionsFor(
        primary: SwipeAction,
        extra: List<SwipeAction>,
        group: CallLogGroup,
        sims: List<SimSlot>
    ): List<RevealAction> {
        // Свайп выключен — выключен целиком, вместе с дополнительными
        // действиями: иначе панель раскрывалась бы с одними «дополнительными»
        if (primary == SwipeAction.NONE) return emptyList()
        if (extra.isEmpty()) return revealActions(primary, group, sims)

        /*
         * НЕСКОЛЬКО ДЕЙСТВИЙ — ПО КНОПКЕ НА ДЕЙСТВИЕ (1.2.5).
         *
         * Первая версия брала первую кнопку из «панели по номерам» каждого
         * действия. Такие кнопки рассчитаны на выбор номера: одинаковый
         * синий цвет, общий значок сообщения, подпись — номер. В итоге
         * SMS, Telegram и WhatsApp на панели выглядели одинаково, а под
         * значком стоял номер (с телефона, 30.09.2026).
         *
         * Теперь у каждого действия своя кнопка: значок и цвет — те же,
         * что в настройках свайпа, подпись — название действия. Номер
         * действие выбирает само (handleSwipe): для мессенджеров — тот,
         * на котором мессенджер у контакта есть.
         */
        return (listOf(primary) + extra)
            .filter { it != SwipeAction.NONE }
            .distinct()
            .map { action ->
                val visual = RevealActionFactory.iconOf(action)
                val label = app.getString(SettingsCommands.swipeActionLabelRes(action))
                RevealAction(
                    label = label,
                    description = label,
                    color = visual?.second ?: MessageBlue,
                    icon = visual?.first,
                    onClick = { performSwipe(action, group) }
                )
            }
    }

    init {
        /*
         * КЭШ ПОКАЗЫВАЕТСЯ ДО ЧТЕНИЯ ПРОВАЙДЕРА.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЗАЧЕМ
         * ══════════════════════════════════════════════════════════════════
         * Чтение книги занимает секунды — на большой до десяти. Всё это
         * время журнал показывался без фотографий, а человек
         * с несколькими номерами выглядел несколькими карточками.
         *
         * Кэш — одна выборка из локальной базы, десятки миллисекунд.
         * Он подставляется первым, а настоящее чтение приходит следом
         * и заменяет его.
         *
         * ── Почему до refresh, а не внутри ─────────────────────────────────
         * refresh читает журнал, контакты и SIM параллельно и обновляет
         * состояние разом. Кэш должен успеть раньше — иначе он бесполезен.
         *
         * ── Почему проверяем, что контакты пусты ───────────────────────────
         * Если настоящее чтение уже успело — оно точнее, и затирать его
         * кэшем нельзя.
         */
        viewModelScope.launch {
            val cached = contactCache.load()
            if (cached.isNotEmpty() && state.value.contacts.isEmpty()) {
                Logger.d(TAG, "Контакты из кэша: ${cached.size}")
                _state.update { it.copy(contacts = cached) }
            }
        }

        refresh()
        observer.register()

        /*
         * СПИСОК КАРТ ИЗ ПРОШЛОГО ЗАПУСКА — СРАЗУ НА ЭКРАН.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ КЭШ, А НЕ ОЖИДАНИЕ
         * ══════════════════════════════════════════════════════════════════
         * Сразу после старта процесса телефонная служба иногда ещё
         * не поднялась, и SubscriptionManager отдаёт пустой список.
         * Мы уже перестали кэшировать эту пустоту и затирать ею
         * состояние, но само ожидание никуда не делось: первые доли
         * секунды показывать было нечего.
         *
         * Карты вставляют и вынимают редко — раз в год, а приложение
         * открывают десятки раз в день. Поэтому список из прошлого
         * запуска показывается сразу, а настоящее чтение его поправит.
         *
         * ⚠️ Риск ошибки здесь мал и обратим: если карту всё-таки
         * вынули, кнопка выбора укажет на исчезнувшую. Звонок при этом
         * не сорвётся — accountForSlot вернёт null, и вызов уйдёт через
         * карту по умолчанию, как при обычном наборе.
         */
        viewModelScope.launch {
            val cached = runCatching { parseSimCache(prefs.simCache.first()) }
                .getOrDefault(emptyList())

            if (cached.isNotEmpty() && state.value.sims.isEmpty()) {
                _state.update { it.copy(sims = cached) }
            }
        }

        // Карту могут вынуть или отключить при работающем приложении:
        // без подписки список остался бы в кэше прежним
        /*
         * ПОДПИСКА НА СМЕНУ SIM — С ПОВТОРНЫМ ЧТЕНИЕМ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ОДНОГО ЧТЕНИЯ МАЛО
         * ══════════════════════════════════════════════════════════════════
         * Подписка у нас была и раньше: SubscriptionManager присылает
         * onSubscriptionsChanged, мы сбрасываем кэш и перечитываем.
         * Но событие приходит В НАЧАЛЕ переключения, а не в конце:
         * модем ещё отключает карту, и список в этот момент бывает
         * прежним — с двумя картами. Мы его читали, клали в кэш,
         * и дальше жили с устаревшим до следующего события или
         * следующего обновления журнала.
         *
         * Поэтому после события — ещё два чтения: через 2 и через
         * 6 секунд. Переключение карты на большинстве телефонов
         * укладывается в первые две; вторая — для медленных модемов
         * и двух карт одного оператора, где система переназначает
         * подписки дольше.
         *
         * Задания отменяют друг друга: несколько событий подряд
         * (их приходит пачка на одно переключение) дают одну серию
         * перечитываний, а не по серии на каждое.
         */
        runCatching {
            simRepo.startWatching {
                refresh()
                simSettleJob?.cancel()
                simSettleJob = viewModelScope.launch {
                    for (wait in SIM_SETTLE_DELAYS_MS) {
                        kotlinx.coroutines.delay(wait)
                        simRepo.invalidateCache()
                        refresh()
                    }
                }
            }
        }
            .onFailure { Logger.w(TAG, "Не удалось подписаться на смену SIM", it) }
        // Пользовательские имена SIM нужны репозиторию для подписей в журнале
        viewModelScope.launch {
            prefs.simLabels.collect { labels ->
                simRepo.setCustomLabels(labels)
                // Подписи SIM — это вид, а не данные: перечитывать
                // журнал ради них незачем
                regroupOnly()
            }
        }
        viewModelScope.launch { notesRepo.all.collect { n -> _state.update { it.copy(notes = n) } } }
        viewModelScope.launch {
            spamMarksRepo.all.collect { m -> _state.update { it.copy(spamMarks = m) } }
        }
        T9IndexWorker.enqueue(app)
        // ── Реакция на изменения системных данных ──────────────────────────
        //
        // Журнал и контакты обрабатываются РАЗДЕЛЬНО: это операции разной
        // стоимости. Перечитать страницу журнала дёшево, а перечитать книгу
        // из тысяч контактов вместе с пересборкой T9-индекса — дорого.

        // Журнал: изменения приходят пачками. Система создаёт запись,
        // потом дописывает длительность, имя и фото — без задержки это
        // четыре-пять полных перечитываний подряд за одну секунду
        viewModelScope.launch {
            observer.callLogChanges.debounceContentChanges().collect {
                /*
                 * Порог по времени здесь соблюдается.
                 *
                 * Система шлёт уведомления пачкой: создала запись,
                 * дописала длительность, потом имя, потом фото. Без
                 * порога это четыре полных чтения журнала подряд
                 * за одну секунду.
                 *
                 * Действия пользователя порог обходят — там ждать
                 * нечего, изменение уже произошло.
                 */
                refresh(force = false)
            }
        }

        // Контакты: перед перезагрузкой проверяем, изменилось ли что-то
        // на самом деле. Синхронизация аккаунтов трогает провайдер
        // по несколько раз в час, ничего при этом не меняя по существу
        viewModelScope.launch {
            observer.contactsChanges.debounceContentChanges().collect {
                onContactsPossiblyChanged()
            }
        }
    }

    override fun onCleared() {
        simRepo.stopWatching()
        super.onCleared()
        observer.unregister()
    }

    /**
     * Отметка времени последней известной правки книги.
     * Служит для отсечения ложных уведомлений от синхронизации.
     */
    private var lastContactsTimestamp: Long = 0L

    /**
     * Реакция на уведомление об изменении контактов.
     *
     * Провайдер уведомляет и о служебных операциях синхронизации,
     * которые не меняют данные. Сравнение отметки времени отсекает их
     * и экономит перечитывание книги вместе с пересборкой T9-индекса.
     */
    private suspend fun onContactsPossiblyChanged() {
        val timestamp = contactsRepo.lastUpdatedTimestamp()
        if (timestamp == lastContactsTimestamp && timestamp != 0L) {
            Logger.d("Contacts", "Уведомление без реальных изменений, пропускаю")
            return
        }
        lastContactsTimestamp = timestamp

        Logger.i("Contacts", "Книга изменилась, перечитываю и пересобираю T9-индекс")
        refresh()
        // Индекс поиска устарел: имена могли измениться, контакты добавиться
        T9IndexWorker.enqueue(app)
    }

    /**
     * Текущее обновление списка. Новый запрос отменяет предыдущий:
     * если пользователь быстро переключил режим группировки, старая
     * загрузка становится бесполезной, а её результат ещё и перезатрёт
     * новый, придя позже.
     */
    /**
     * Есть ли совпадения в загруженном.
     *
     * Дешёвая проверка перед подгрузкой: полный поиск с ранжированием
     * стоит дороже, а здесь нужен только ответ «да или нет».
     */
    private fun hasMatches(query: String): Boolean {
        val state = _state.value

        // Буквами (1.6.0) — то же сравнение, что в поиске: имя и заметка
        if (com.ogneliss.dialer.util.TextFold.isTextQuery(query)) {
            val text = query.trim()
            return state.contacts.any { com.ogneliss.dialer.util.TextFold.contains(it.displayName, text) } ||
                state.callLog.any { group ->
                    com.ogneliss.dialer.util.TextFold.contains(group.displayName, text) ||
                        com.ogneliss.dialer.util.TextFold.contains(state.notes[group.e164]?.note, text)
                }
        }

        val digits = query.filter { it.isDigit() }
        if (digits.isEmpty()) return true

        /*
         * Сравнение то же, что в поиске: «8961…» и «+7961…» — один
         * номер. Здесь это важно не меньше: по ответу решается,
         * догружать ли журнал глубже, и расхождение с поиском дало бы
         * «ничего не найдено» там, где совпадение есть.
         */
        return state.contacts.any { contact ->
            T9Engine.matchIndex(digits, contact.displayName) >= 0 ||
                contact.numbers.any { DialDigits.matches(it.value, digits) }
        } || state.callLog.any { group ->
            DialDigits.matches(group.number, digits)
        }
    }

    private companion object {
        const val TAG = "DialerVM"

        /**
         * Удаление с «Отменить» (1.5.7): запасной срок, если сообщение
         * так и не показалось. Сообщение гаснет через 4 с, но может ждать
         * в очереди за другим — срок с запасом.
         */
        const val UNDO_FALLBACK_MS = 12_000L

        /** Сколько ждать перечитанного журнала после удаления. */
        const val REFRESH_WAIT_MS = 3_000L

        /**
         * Пауза перед выполнением поиска.
         *
         * 120 мс: между цифрами при быстром наборе проходит 60–100 мс,
         * то есть пауза склеивает их в один поиск. Больше — человек
         * успевает заметить задержку; меньше — склейки не происходит.
         */
        /**
         * Сколько страниц догружать при поиске.
         *
         * Пять по 200 — тысяча записей сверх первых пятисот. Дальше
         * ждать дольше, чем человек готов: он скорее решит, что
         * приложение зависло.
         */
        const val MAX_SEARCH_PAGES = 5

        const val SEARCH_DEBOUNCE_MS = 120L

        /**
         * Пауза перед обновлением данных.
         *
         * Склеивает срабатывания разных подписок: без неё одно изменение
         * журнала вызывало три полных перечитывания подряд.
         */
        const val REFRESH_DEBOUNCE_MS = 150L

        /**
         * Паузы повторного чтения SIM после события смены карт:
         * 2 с для обычного переключения, ещё 4 — для медленных модемов.
         * Разбор — у подписки на SubscriptionManager.
         */
        val SIM_SETTLE_DELAYS_MS = longArrayOf(2_000L, 4_000L)

        /**
         * Пауза перед повторным чтением контактов.
         *
         * Секунды хватает провайдеру, чтобы закончить синхронизацию
         * с аккаунтом после запуска телефона.
         */
        const val CONTACTS_RETRY_DELAY_MS = 1000L

        /**
         * Сколько раз пробовать перечитать контакты.
         *
         * Четыре попытки с удвоением паузы дают около семи секунд
         * ожидания — этого хватает даже большой книге на медленном
         * телефоне.
         */
        const val MAX_CONTACTS_RETRIES = 4
    }

    private var refreshJob: Job? = null

    /**
     * Повторное чтение контактов уже выполнялось.
     *
     * Одной попытки достаточно: если и она вернула пустоту, книга
     * действительно пуста, а повторять бесконечно значило бы читать
     * провайдер вхолостую при каждом обновлении.
     */
    /**
     * Сколько раз уже пытались перечитать контакты.
     *
     * Ограничение нужно: пустая книга — допустимое состояние, и читать
     * провайдер бесконечно значило бы тратить его силы впустую.
     */
    private var contactsRetries = 0

    /**
     * ОБЛАСТЬ ДЛЯ ЧТЕНИЯ ТЕЛЕФОННОЙ КНИГИ.
     *
     * Не viewModelScope: обновление журнала отменяет предыдущее,
     * и чтение книги вместе с ним. В логе это выглядело как три
     * подряд «Не удалось прочитать контакты» с JobCancellationException,
     * а на экране — как отсутствие фотографий и группировки.
     *
     * Книга меняется редко и читается быстро: отменять её ради
     * обновления журнала бессмысленно.
     *
     * SupervisorJob: сбой одного чтения не отменяет остальные.
     */
    private val appScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

    /**
     * Постраничная загрузка вынесена в CallLogLoader: пейджер, режим
     * группировки и правило разделения по SIM всегда меняются вместе,
     * и держать их порознь означало забывать одно из трёх.
     */

    /** Идёт ли подгрузка следующей страницы. */
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private var loadMoreJob: Job? = null

    /**
     * Повторная подписка на провайдеры.
     *
     * Нужна после выдачи разрешений: при первом запуске подписка
     * не устанавливается, и без повторного вызова список не обновлялся бы
     * сам до перезапуска приложения.
     */
    /**
     * ВОЗВРАЩЕНИЕ ПРИЛОЖЕНИЯ НА ПЕРЕДНИЙ ПЛАН.
     *
     * Подписки на провайдер недостаточно по двум причинам.
     *
     * Первая: если разрешение выдали позже создания ViewModel, подписка
     * не установилась, а повторная попытка происходит не во всех
     * сценариях возврата.
     *
     * Вторая: во время разговора приложение в фоне, и часть прошивок
     * придерживает уведомления от провайдеров до возвращения процесса
     * на передний план. Именно поэтому новый звонок не появлялся
     * в списке до перезапуска.
     *
     * Для диалера возвращение к списку после звонка — самый частый
     * сценарий вообще, и обновление в этот момент решает задачу
     * надёжнее любой подписки.
     */
    /**
     * Признак того, что список нужно промотать к началу.
     *
     * Взводится при возвращении в приложение: человек мог прокрутить
     * журнал вниз, позвонить и вернуться — и свежий звонок оказался бы
     * выше видимой области.
     *
     * Экран сбрасывает признак после прокрутки, иначе список дёргался бы
     * вверх при каждой перерисовке.
     */
    private val _scrollToTop = MutableStateFlow(false)
    val scrollToTop: StateFlow<Boolean> = _scrollToTop

    fun onScrolledToTop() { _scrollToTop.value = false }

    /**
     * Принудительное обновление по воле пользователя.
     *
     * Например, потянул список вниз. Здесь читаем всегда: человек
     * попросил явно, и отвечать «данные и так свежие» невежливо —
     * он этого не видит.
     */
    /**
     * ПЕРЕСБОРКА БЕЗ ЧТЕНИЯ.
     *
     * Для изменений, которые не трогают данные: сменилась тема,
     * подписи SIM, режим группировки. Записи уже прочитаны, и повторное
     * чтение шестисот строк из провайдера ничего бы не изменило.
     *
     * Именно ради этого случая и вводилась проверка, которая ломала
     * удаление: теперь она выражена явно и применяется только там,
     * где уместна.
     */
    /**
     * ПОЛОЖЕНИЕ СПИСКА ЖУРНАЛА.
     *
     * Хранится здесь, а не в экране: rememberLazyListState теряет
     * запись при уходе в карточку абонента, и список прыгал наверх
     * при возврате.
     *
     * Обычные поля, а не поток: значение читается один раз при сборке
     * экрана, и подписка на него вызывала бы лишние перерисовки при
     * каждом движении пальца.
     */
    var logScrollIndex: Int = 0
        private set

    var logScrollOffset: Int = 0
        private set

    fun rememberLogScroll(index: Int, offset: Int) {
        logScrollIndex = index
        logScrollOffset = offset
    }

    /**
     * Положение списка контактов — по той же причине, что и журнала.
     *
     * Отдельные поля, а не одни на оба списка: человек может уйти
     * из середины контактов в карточку, вернуться и перейти в журнал —
     * общее хранилище перенесло бы чужую позицию.
     */
    var contactsScrollIndex: Int = 0
        private set

    var contactsScrollOffset: Int = 0
        private set

    fun rememberContactsScroll(index: Int, offset: Int) {
        contactsScrollIndex = index
        contactsScrollOffset = offset
    }

    fun regroupOnly() = viewModelScope.launch {
        suspendRunCatching {
            val groups = callLogLoader.regroup()
            _state.update { it.copy(callLog = groups) }
        }.onFailure { Logger.w(TAG, "Не удалось перегруппировать журнал", it) }
    }

    /**
     * Счётчик команд «закрыть все свайпы».
     *
     * Через ViewModel, а не напрямую: команда приходит из RootScreen,
     * а исполняют её координаторы на разных экранах — журнала
     * и контактов. Общего родителя у них нет.
     */
    private val _closeSwipes = MutableStateFlow(0)
    val closeSwipes: StateFlow<Int> = _closeSwipes

    fun closeAllSwipes() { _closeSwipes.value++ }

    /**
     * Подсказка о жестах уже показана.
     *
     * Eagerly, а не по подписке: подсказка решается в первые кадры
     * после запуска, и ленивое начало давало бы её мелькание —
     * появилась и тут же скрылась, когда пришло настоящее значение.
     */
    val gestureHintShown = prefs.gestureHintShown
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * СОБСТВЕННЫЙ НОМЕР.
     *
     * Пусто — не указан. Показывается в меню, чтобы человек видел,
     * знает ли приложение его номер, и мог поправить.
     */
    val ownNumber = prefs.ownNumber
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * Попытка определить номер у системы.
     *
     * @return false, если система его не знает — тогда интерфейс просит
     *         ввести вручную. Это не ошибка: у большинства операторов
     *         поле на SIM пустое.
     */
    suspend fun detectOwnNumber(): Boolean {
        val detected = ownNumberSource.detect(app) ?: return false
        prefs.setOwnNumber(detected)
        return true
    }

    fun setOwnNumber(value: String) = viewModelScope.launch {
        prefs.setOwnNumber(PhoneFormatter.toE164(value))
    }

    fun markGestureHintShown() = viewModelScope.launch {
        prefs.setGestureHintShown(true)
    }

    /**
     * Системный экран разрешения на точные будильники.
     *
     * Открывается по кнопке в сообщении о неточном напоминании:
     * искать этот экран в настройках самостоятельно почти невозможно —
     * он спрятан в разделе особых разрешений приложения.
     */
    fun openExactAlarmSettings() {
        runCatching {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.fromParts("package", app.packageName, null)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }.onFailure {
            Logger.w(TAG, "Не удалось открыть настройки точных будильников", it)
        }
    }

    /**
     * ПОВТОРНОЕ ЧТЕНИЕ КОНТАКТОВ.
     *
     * Вызывается экраном контактов при открытии, если список пуст.
     * Читает только книгу, не трогая журнал: журнал мог быть загружен
     * страницами, и перечитывать его ради контактов расточительно.
     *
     * Пустой результат не записывается: он либо подтверждает пустую
     * книгу — тогда в состоянии и так пусто, — либо означает очередной
     * сбой чтения, и стирать им прежние данные незачем.
     */
    fun reloadContacts() = viewModelScope.launch {
        suspendRunCatching { contactsRepo.loadAll() }
            .onFailure { Logger.w(TAG, "Повторное чтение контактов не удалось", it) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { loaded -> _state.update { it.copy(contacts = loaded) } }
    }

    fun onReturnedToForeground() {
        _scrollToTop.value = true
        runCatching { observer.register() }
            .onFailure { Logger.w(TAG, "Не удалось подписаться на провайдеры", it) }

        /*
         * СПИСОК SIM ПЕРЕЧИТЫВАЕТСЯ ПРИ КАЖДОМ ВОЗВРАЩЕНИИ.
         *
         * Карту отключают в системных настройках — то есть ровно тогда,
         * когда приложение в фоне. Вернулся — и refresh() брал список
         * из кэша, где карт по-прежнему две. Кэш сбрасывался только
         * событием от SubscriptionManager, а оно приходит не сразу
         * (разбор — у подписки ниже). Отсюда «отобразилось через минуту».
         *
         * Чтение дешёвое — один вызов системной службы, — а возвращение
         * в приложение случается не так часто, чтобы это было заметно.
         */
        runCatching { simRepo.invalidateCache() }
            .onFailure { Logger.w(TAG, "Не удалось сбросить кэш SIM", it) }

        /*
         * ВОЗВРАЩЕНИЕ В ПРИЛОЖЕНИЕ — ВСЕГДА ЧИТАЕМ ЗАНОВО.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ЗДЕСЬ НЕЛЬЗЯ ЭКОНОМИТЬ
         * ══════════════════════════════════════════════════════════════════
         * Проверка «данные не менялись» была введена, чтобы смена темы
         * не перечитывала журнал. Но она задела и этот случай — а он
         * прямо противоположный.
         *
         * Возврат в приложение почти всегда означает, что человек только
         * что звонил: экран вызова закрылся, и мы снова на переднем
         * плане. Именно тогда журнал изменился наверняка.
         *
         * Подписка на провайдер это не покрывает: часть прошивок
         * придерживает уведомления, пока процесс в фоне, и сообщение
         * приходит с задержкой в несколько секунд. Всё это время
         * человек смотрит на список без своего звонка и делает вывод,
         * что приложение не работает.
         *
         * Чтение одной страницы стоит десятки миллисекунд — за них
         * не жалко.
         *
         * Отдельная пометка здесь больше не нужна: refresh() читает
         * всегда, а для изменений вида есть regroupOnly().
         */
        refresh()
    }

    fun onPermissionsChanged() {
        /*
         * Каждый шаг обёрнут отдельно.
         *
         * Это путь сразу после выдачи разрешений: приложение впервые
         * получает доступ к журналу и контактам, и здесь же впервые
         * выполняется код, который до этого молча возвращал пустые списки.
         * Сбой любого шага не должен уносить приложение целиком —
         * пользователь только что дал доступ и ждёт, что всё заработает.
         */
        Stage.set("permissions_changed")

        /*
         * Кэш списка SIM сбрасывается принудительно.
         *
         * До выдачи разрешения система не отдаёт карты, и в кэш попадал
         * ПУСТОЙ список. Дальше он жил до перезапуска приложения: кнопки
         * выбора SIM не появлялись, пока что-нибудь не пересоздавало
         * экран — например, смена темы. Отсюда и «две кнопки появляются
         * только после изменения темы».
         */
        runCatching { simRepo.invalidateCache() }
            .onFailure { Logger.w(TAG, "Не удалось сбросить кэш SIM", it) }

        runCatching { observer.register() }
            .onFailure { Logger.e(TAG, "Не удалось подписаться на провайдеры", it) }

        runCatching { refresh() }
            .onFailure { Logger.e(TAG, "Не удалось обновить данные", it) }
    }

    /**
     * ОБНОВЛЕНИЕ С ЧТЕНИЕМ ЖУРНАЛА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ЧИТАЕМ ВСЕГДА
     * ══════════════════════════════════════════════════════════════════════
     * Раньше чтение пропускалось, если провайдер не сообщал об изменении.
     * Проверка вводилась против лишних чтений при смене темы, но задела
     * всё остальное: удаление записи, блокировка, закрепление,
     * добавление в чёрный список.
     *
     * Все они меняют журнал сами, не дожидаясь провайдера, — и вызывали
     * refresh() впустую. Удалённая запись оставалась на экране до тех
     * пор, пока система не пришлёт своё уведомление, а оно может
     * задержаться на секунды.
     *
     * Для человека это выглядит как «удаление не работает»: он нажал,
     * запись на месте. Хуже того, он нажимает ещё раз.
     *
     * ── Что вместо проверки ────────────────────────────────────────────────
     * refresh() читает всегда. Косметические изменения — смена темы,
     * подписей SIM — вызывают regroupOnly(): он работает с уже
     * прочитанными записями и к провайдеру не обращается.
     *
     * Разделение по смыслу, а не по догадке: вызывающий код знает,
     * менял он данные или только их вид.
     */
    fun refresh(force: Boolean = true) {

        /*
         * Подгрузка страницы имеет приоритет.
         *
         * В логе видно пару: «прочитано 150 (offset=300)», а через
         * 150 мс «прочитано 450 (offset=0)». Человек прокрутил вниз,
         * подгрузилась страница — и тут же сработало обновление
         * от возврата на передний план, перечитав всё заново.
         *
         * Двойная работа приходится ровно на момент прокрутки, когда
         * плавность нужнее всего. Обновление подождёт: подгрузка
         * закончится через доли секунды, и данные будут свежими.
         */
        if (loadMoreJob?.isActive == true) return

        /*
         * Предыдущее обновление отменяется.
         *
         * В логе видно три одновременных чтения одной страницы:
         * подписка на журнал, подписка на контакты и возврат
         * на передний план сработали разом. Каждое читало по 150 записей
         * и запускало пересборку строк — тройная работа впустую,
         * и одна из корутин падала с JobCancellationException.
         */
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            /*
             * Короткая пауза склеивает обновления из разных источников.
             *
             * Подписка на журнал, подписка на контакты и возврат
             * на передний план часто срабатывают в одну миллисекунду —
             * в логе видно три чтения подряд с одинаковым offset=0.
             *
             * 150 мс достаточно, чтобы все три попали в одно обновление,
             * и незаметно на глаз.
             */
            delay(REFRESH_DEBOUNCE_MS)
            doRefresh(forceRead = force)
        }
    }

    /**
     * Загрузка данных для списка.
     *
     * Журнал, контакты и список SIM читаются ПАРАЛЛЕЛЬНО: они независимы,
     * и последовательное чтение давало бы сумму задержек вместо максимума.
     * На книге в 2000 контактов и журнале в 500 записей разница заметна
     * при каждом открытии приложения.
     */
    private suspend fun doRefresh(
        /**
         * Читать, не глядя на порог по времени.
         *
         * true для действий пользователя: удаление, блокировка, открытие
         * экрана. false для потока уведомлений от провайдера — там порог
         * и нужен.
         */
        forceRead: Boolean = true
    ) = coroutineScope {
        Stage.set("refresh")
        // Состояние разрешений нужно интерфейсу: пустой журнал
        // из-за отказа и пустой журнал у нового телефона — разные вещи,
        // и подсказки должны быть разными
        _state.update {
            it.copy(
                hasCallLogPermission = PermissionGuard.canReadCallLog(app),
                hasContactsPermission = PermissionGuard.canReadContacts(app)
            )
        }

        // async внутри coroutineScope, а не viewModelScope: при отмене
        // refreshJob дочерние загрузки тоже отменяются, иначе они
        // продолжали бы жечь батарею и потоки впустую
        /*
         * Обновление СОХРАНЯЕТ прокрутку.
         *
         * loadFirstPage сбрасывал пейджер: человек прокрутил журнал
         * на 750 записей, вернулся в приложение — и список схлопнулся
         * до 150, а остальное пришлось подгружать заново. В логе это
         * видно как «offset=600», а следом «offset=0».
         */
        val logDeferred = async { callLogLoader.refreshKeepingPosition(force = forceRead) }
        /*
         * СБОЙ ЧТЕНИЯ НЕ ОЗНАЧАЕТ «КОНТАКТОВ НЕТ».
         *
         * ── Что было не так ────────────────────────────────────────────────
         * runCatching молча подменял ошибку пустым списком, и он попадал
         * в состояние как полноценный результат. Экран показывал
         * «Контактов нет» — хотя книга полна, просто чтение не удалось.
         *
         * Провайдер контактов отдаёт ошибку не только при отсутствии
         * разрешения: он может быть занят синхронизацией с аккаунтом,
         * особенно сразу после запуска телефона. Через секунду то же
         * чтение проходит.
         *
         * Отсюда и «после поворота экрана контакты появляются»: поворот
         * вызывает повторное чтение, и оно удаётся.
         *
         * ── Что делаем ─────────────────────────────────────────────────────
         * null означает «прочитать не удалось» и отличается от пустого
         * списка. Прежние контакты при этом сохраняются: показать
         * устаревший список лучше, чем пустой.
         */
        /*
         * КОНТАКТЫ ЧИТАЮТСЯ ВНЕ ОТМЕНЯЕМОЙ ЦЕПОЧКИ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО ПОКАЗАЛ ЛОГ
         * ══════════════════════════════════════════════════════════════════
         *   13:28:08.870 W [DialerVM] Не удалось прочитать контакты
         *   B3.i0: y0 was cancelled
         *   13:28:09.061 W [DialerVM] Не удалось прочитать контакты
         *   13:28:09.245 W [DialerVM] Не удалось прочитать контакты
         *
         * Подписка на провайдер срабатывает несколько раз подряд, каждое
         * срабатывание отменяет предыдущее обновление — и чтение книги
         * не доходит до конца НИ РАЗУ.
         *
         * runCatching при этом ловил CancellationException наравне
         * с настоящими ошибками и возвращал null. Дальше подставлялись
         * прежние контакты, то есть пустой список.
         *
         * Отсюда всё разом: нет фотографий в журнале, не работает
         * группировка по нескольким номерам, «Добавить в контакты»
         * у тех, кто в книге. И появлялось это только после захода
         * в контакты — там своё, неотменяемое чтение.
         *
         * ── Почему отдельная область ───────────────────────────────────────
         * Книга меняется редко и читается быстро. Отменять её чтение
         * ради обновления журнала бессмысленно: результат тот же,
         * а работа теряется.
         */
        val contactsDeferred = appScope.async {
            suspendRunCatching { contactsRepo.loadAll() }
                .onFailure { error ->
                    /*
                     * Отмена — не ошибка.
                     *
                     * CancellationException означает, что нас прервали
                     * штатно. Писать её в лог как сбой значит засорять
                     * журнал и прятать настоящие ошибки среди шума.
                     */
                    if (error !is kotlinx.coroutines.CancellationException) {
                        Logger.w(TAG, "Не удалось прочитать контакты", error)
                    }
                }
                .getOrNull()
        }
        val simsDeferred = async {
            suspendRunCatching {
                // В интерфейс попадают только карты, с которых можно
                // звонить: активная карта не обязательно умеет голос
                val all = simRepo.slotsAsync()
                val voice = simRepo.voiceCapableSubscriptionIds()
                SimCallPlan.callableSims(all, voice)
            }.getOrDefault(emptyList())
        }

        /*
         * КАРТЫ ПОКАЗЫВАЮТСЯ, КАК ТОЛЬКО ПРОЧИТАНЫ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ИХ ПРИХОДИЛОСЬ ЖДАТЬ
         * ══════════════════════════════════════════════════════════════════
         * Всё состояние публиковалось одним обновлением в конце: журнал,
         * контакты, карты. А до конца доходит долго — прочитать журнал,
         * прочитать книгу, перегруппировать, пересчитать статистику.
         * Список SIM при этом готов через миллисекунды.
         *
         * Заметнее всего после смены языка: экран пересоздаётся, кнопки
         * «SIM 1» и «SIM 2» под клавиатурой исчезают и возвращаются
         * через секунду — выглядит как сбой, хотя карты никуда
         * не девались.
         *
         * Отдельная публикация: карты появляются сразу, остальное
         * догоняет.
         */
        launch {
            val early = simsDeferred.await()
            if (early.isNotEmpty()) {
                _state.update { it.copy(sims = early) }
                // Запоминаем для следующего запуска: показать сразу
                suspendRunCatching { prefs.setSimCache(encodeSimCache(early)) }
            }
        }

        val rawLog = logDeferred.await()
        /*
         * При неудаче оставляем прежние контакты.
         *
         * Пустой список стёр бы имена у всех записей журнала: входящий
         * от знакомого показался бы неизвестным номером — ровно то,
         * ради чего диалер и нужен.
         */
        /*
         * Результат чтения книги отдельно от подставленного.
         *
         * freshContacts — то, что действительно прочитано: null означает
         * сбой или отмену. contacts — что показываем: при сбое остаются
         * прежние, чтобы журнал не потерял фотографии.
         *
         * Различать важно для кэша: перезаписывать его подставленными
         * данными значит записать в него то, что оттуда же и взято.
         */
        val freshContacts = contactsDeferred.await()

        /*
         * ══════════════════════════════════════════════════════════════════
         * ПУСТОЙ ОТВЕТ КНИГИ НЕ СТИРАЕТ ИЗВЕСТНЫЕ КОНТАКТЫ
         * ══════════════════════════════════════════════════════════════════
         * Жалоба: у закреплённых контактов периодически пропадают
         * остальные номера, почта, дни рождения — остаётся один номер,
         * по которому контакт закреплён.
         *
         * Причина — провайдер контактов, занятый синхронизацией. Журнал
         * приложения это подтверждает: «Контакты с попытки 4» — первые
         * три чтения вернули ПУСТОЙ список. Не ошибку, а пустоту.
         *
         * Сбой (null) и раньше не трогал прежние контакты. А пустой список
         * считался правдой: он заменял всю книгу в состоянии. Карточка
         * строилась уже без контакта — из одной записи журнала, у которой
         * один номер и никаких сведений. Через несколько секунд повторное
         * чтение всё возвращало, отсюда «периодически».
         *
         * Теперь пустой ответ при уже известных контактах — как сбой:
         * прежние остаются (из кэша или прошлого чтения), а книга
         * перечитывается повторно. Кэш и так не перезаписывался пустотой —
         * не хватало того же правила для состояния.
         *
         * ── Если книгу правда очистили ────────────────────────────────────
         * Повторные чтения (до четырёх, за ~15 секунд) тоже вернут пустоту,
         * и тогда пустота принимается как правда — ниже, в конце серии
         * попыток.
         */
        val previousContacts = state.value.contacts
        val providerAnsweredEmpty = freshContacts != null && freshContacts.isEmpty()
        val contacts = when {
            freshContacts == null -> previousContacts
            providerAnsweredEmpty && previousContacts.isNotEmpty() -> previousContacts
            // freshContacts здесь уже не null — это проверила первая ветка.
            // 1.3.3: запасное «?: previousContacts» убрано — Kotlin 2.4
            // предупреждал, что оно никогда не срабатывает
            else -> freshContacts
        }

        /*
         * СООТВЕТСТВИЕ «НОМЕР → КОНТАКТ» И ПЕРЕГРУППИРОВКА.
         *
         * ── Почему перегруппировка нужна ───────────────────────────────────
         * Журнал и контакты читаются параллельно — так быстрее. Но это
         * значит, что группировка могла завершиться раньше, чем пришла
         * телефонная книга, и выполниться без карты номеров.
         *
         * Тогда человек с рабочим и личным номером дал бы две карточки
         * до следующего обновления.
         *
         * Перегруппировка работает с уже прочитанными записями и стоит
         * доли миллисекунды: к провайдеру она не обращается.
         */
        val contactNumbers = withContext(defaultDispatcher) {
            /*
             * ОДИН НОМЕР — НЕСКОЛЬКО КОНТАКТОВ.
             *
             * ── Когда так бывает ───────────────────────────────────────────
             * Рабочий телефон записан и на человека, и на организацию.
             * Или контакт задвоился после синхронизации с двумя
             * аккаунтами. Или номер общий — семейный, дежурный.
             *
             * ── Почему нужен предсказуемый выбор ───────────────────────────
             * Порядок контактов из провайдера не гарантирован: при разных
             * чтениях он может отличаться. Если брать «какой попался»,
             * карточка будет менять имя между обновлениями — то Иванов,
             * то «Такси».
             *
             * Выбираем первого по алфавиту: это стабильно и не зависит
             * от того, как провайдер отдал записи. Человек видит одно
             * и то же имя всегда.
             */
            buildMap {
                contacts
                    .sortedBy { it.displayName.lowercase() }
                    .forEach { contact ->
                        contact.numbers.forEach { number ->
                            // putIfAbsent: первый по алфавиту побеждает,
                            // остальные не затирают его
                            putIfAbsent(number.e164, contact.id)
                        }
                    }
            }
        }
        callLogLoader.setContactNumbers(contactNumbers)

        /*
         * Кэш обновляется после каждого успешного чтения.
         *
         * Не только при изменении: сравнивать сотню контактов дороже,
         * чем перезаписать их. И надёжнее — сравнение легко ошибается
         * на порядке номеров или регистре имени.
         */
        /*
         * КЭШ ОБНОВЛЯЕТСЯ ПРИ КАЖДОМ УСПЕШНОМ ЧТЕНИИ.
         *
         * ── Где это происходит ─────────────────────────────────────────────
         * Здесь, в doRefresh, — а он выполняется после всех действий,
         * меняющих журнал: удаление, блокировка, снятие блокировки,
         * добавление правила, избранное, открытие экрана журнала,
         * возврат в приложение, уведомление провайдера.
         *
         * Отдельно вызывать сохранение в каждом из них не нужно
         * и вредно: забыть одно место — значит получить расхождение,
         * которое проявится не сразу.
         *
         * ── Почему только freshContacts ────────────────────────────────────
         * При сбое чтения contacts берутся из состояния, а туда они
         * попали из кэша. Записывать их обратно значит переписать кэш
         * тем же содержимым — работа впустую, а при частых обновлениях
         * ещё и лишние обращения к диску.
         */
        if (!freshContacts.isNullOrEmpty()) {
            appScope.launch { contactCache.save(freshContacts) }
        }

        val log = if (contactNumbers.isNotEmpty()) callLogLoader.regroup() else rawLog

        val sims = simsDeferred.await()

        // Пересчёт статистики: обход всего журнала с суммированием.
        // На главном потоке это заметная пауза при каждом обновлении
        withContext(defaultDispatcher) { statsRepo.rebuild(log) }

        // Сначала показываем список, потом досчитываем определитель.
        // Разбор номеров занимает десятки миллисекунд; заставлять
        // пользователя ждать их ради подписи «возможен спам» незачем.
        _state.update {
            /*
             * Пустой список карт не затирает прежний.
             *
             * Сразу после запуска телефонная служба иногда отвечает
             * пустотой (разбор — в SimRepository.slotsAsync). Если
             * записать эту пустоту в состояние, кнопки «SIM 1»
             * и «SIM 2» под клавиатурой пропадут и вернутся только
             * со следующим обновлением.
             *
             * Настоящее исчезновение карты сюда тоже приходит пустым
             * списком — но его приносит подписка на смену SIM, которая
             * сбрасывает кэш и перечитывает. Разница в том, что там
             * чтение уже удалось, а здесь оно не состоялось.
             */
            it.copy(
                callLog = log,
                contacts = contacts,
                sims = sims.ifEmpty { it.sims },
                loading = false
            )
        }

        /*
         * ПОВТОРНАЯ ПОПЫТКА, ЕСЛИ КОНТАКТОВ НЕТ.
         *
         * Пустая книга бывает — у нового телефона, — но чаще это признак
         * того, что провайдер был занят: синхронизация с аккаунтом идёт
         * первые секунды после запуска, и чтение в это время возвращает
         * пустоту без ошибки.
         *
         * Отличить одно от другого нельзя, поэтому просто пробуем ещё раз
         * через секунду. Для пустой книги это лишнее чтение раз
         * за запуск — незаметно; для занятого провайдера это разница
         * между «контакты есть» и «неизвестный номер».
         */
        /*
         * НЕСКОЛЬКО ПОПЫТОК ПРОЧИТАТЬ КОНТАКТЫ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ОДНОЙ ПОПЫТКИ МАЛО
         * ══════════════════════════════════════════════════════════════════
         * Провайдер контактов бывает занят синхронизацией с аккаунтом
         * первые секунды после запуска телефона. Сколько именно —
         * зависит от числа контактов и скорости сети; одной секунды
         * хватает не всегда.
         *
         * Проявлялось это так: журнал открывался без фотографий, а после
         * захода в контакты и обратно они появлялись. Заход туда вызывал
         * повторное чтение — уже удачное.
         *
         * ── Почему пауза растёт ────────────────────────────────────────────
         * Если провайдер занят, частые попытки только добавляют ему
         * работы. Секунда, две, четыре: к четвёртой попытке проходит
         * семь секунд — этого хватает даже большой книге.
         *
         * ── Почему не бесконечно ───────────────────────────────────────────
         * Пустая книга — тоже допустимое состояние: новый телефон,
         * очищенные данные. Бесконечные попытки читали бы провайдер
         * вхолостую при каждом обновлении.
         */
        // Перечитываем и когда показывать нечего, и когда книга ответила
        // пустотой, а показаны прежние контакты: их надо освежить
        if ((contacts.isEmpty() || providerAnsweredEmpty) && contactsRetries < MAX_CONTACTS_RETRIES) {
            viewModelScope.launch {
                var delayMs = CONTACTS_RETRY_DELAY_MS

                while (contactsRetries < MAX_CONTACTS_RETRIES) {
                    contactsRetries++
                    delay(delayMs)

                    val loaded = suspendRunCatching { contactsRepo.loadAll() }.getOrNull()
                    if (!loaded.isNullOrEmpty()) {
                        Logger.i(TAG, "Контакты с попытки $contactsRetries: ${loaded.size}")
                        _state.update { it.copy(contacts = loaded) }
                        return@launch
                    }

                    delayMs *= 2
                }

                Logger.d(TAG, "Контакты остались пустыми после всех попыток")

                /*
                 * Четыре пустых ответа подряд за ~15 секунд — это уже
                 * не занятый провайдер, а правда: книгу очистили.
                 * Показываем пустоту, чтобы не держать удалённые контакты.
                 */
                if (providerAnsweredEmpty) {
                    _state.update { it.copy(contacts = emptyList()) }
                }
            }
        }

        /*
         * Сборка множества номеров и разбор определителя — в фоне.
         *
         * flatMap по всем контактам создаёт список на тысячи элементов,
         * а mapKeys прогоняет каждый номер через libphonenumber.
         * doRefresh выполняется в viewModelScope, а его диспетчер —
         * ГЛАВНЫЙ ПОТОК: без переключения эта работа шла между кадрами.
         */
        val info = withContext(defaultDispatcher) {
            val knownE164 = contacts.flatMapTo(HashSet()) { c -> c.numbers.map { it.e164 } }
            numberInfoRepo.describeAll(
                log.map { g -> g.number to knownE164.contains(g.e164) }
            ).mapKeys { (number, _) -> PhoneFormatter.toE164(number) }
        }

        _state.update { it.copy(numberInfo = info) }

        // Ярлыки обновляются после загрузки: важные контакты могли измениться.
        // Отдельная защита: системный лаунчер бывает капризен к содержимому
        // ярлыков, и его отказ не должен ломать загрузку журнала
        /*
         * Ярлыки строятся в фоне: сортировка журнала по числу вызовов
         * и обход контактов на список из четырёх элементов — работа,
         * результат которой пользователь увидит не сейчас, а в меню
         * значка приложения.
         */
        Stage.set("shortcuts")
        /*
         * Ярлыки обновляются на IO, а не на Default.
         *
         * Раньше здесь стоял счётный диспетчер: работа была чисто
         * вычислительной — обход списков и сортировка.
         *
         * Теперь значок каждого ярлыка — это фотография контакта,
         * которую нужно прочитать с диска и декодировать. Чтение файлов
         * на счётном пуле занимает потоки, которых там ровно по числу
         * ядер, и блокирует настоящие вычисления.
         */
        suspendRunCatching { withContext(Dispatchers.IO) { contactActions.updateShortcuts(contacts, log) } }
            .onFailure { Logger.w(TAG, "Не удалось обновить ярлыки", it) }

        Stage.set("idle")
        viewModelScope.launch { applyFilter(_state.value.query) }
    }

    /**
     * Подгрузка следующей страницы при прокрутке к концу списка.
     *
     * Повторные вызовы игнорируются: LazyColumn при быстрой прокрутке
     * дёргает обработчик много раз подряд, и без защиты запустилось бы
     * несколько параллельных загрузок одной и той же страницы.
     */
    fun loadMore() {
        if (!callLogLoader.canLoadMore) return
        // Повторные вызовы игнорируются: LazyColumn при быстрой прокрутке
        // дёргает обработчик много раз подряд, и без защиты запустилось бы
        // несколько параллельных загрузок одной страницы
        if (loadMoreJob?.isActive == true) return

        // Обновление во время подгрузки отменяется: иначе пейджер
        // сбросится посреди чтения, и страница уйдёт впустую
        refreshJob?.cancel()

        loadMoreJob = viewModelScope.launch {
            _loadingMore.value = true
            try {
                callLogLoader.loadNextPage()?.let { groups ->
                    _state.update { it.copy(callLog = groups) }
                    enrichNumberInfo(groups)
                }
            } finally {
                _loadingMore.value = false
            }
        }
    }

    /**
     * Досчёт подписей к номерам, не блокируя показ списка.
     *
     * Два источника, и порядок между ними важен:
     *
     * 1. ОФЛАЙН-РАЗБОР — регион, оператор, оценка риска. Работает всегда
     *    и мгновенно, данные не покидают устройство.
     *
     * 2. ВНЕШНИЙ ОПРЕДЕЛИТЕЛЬ — только если пользователь включил его
     *    поимённо. Запрашивается ТОЛЬКО для незнакомых номеров: для тех,
     *    кто есть в книге, имя уже известно, и передавать их номера
     *    наружу было бы бессмысленной утечкой.
     */
    private suspend fun enrichNumberInfo(groups: List<CallLogGroup>) {
        val known = state.value.contacts
            .flatMap { c -> c.numbers.map { it.e164 } }.toHashSet()
        val info = numberInfoRepo.describeAll(
            groups.map { g -> g.number to known.contains(g.e164) }
        ).mapKeys { (number, _) -> PhoneFormatter.toE164(number) }
        _state.update { it.copy(numberInfo = it.numberInfo + info) }

        // Внешний определитель — отдельным проходом, после офлайн-разбора.
        // Он может уйти в сеть на своей стороне, и держать из-за него
        // весь список незаполненным неправильно
        enrichExternalNames(groups, known)
    }

    /**
     * Имена от стороннего определителя.
     *
     * Спрашиваем только незнакомые номера и небольшими порциями:
     * каждый запрос уходит в чужое приложение, и лавина обращений
     * посадила бы батарею — причём ему, а припишут это нам.
     */
    private suspend fun enrichExternalNames(
        groups: List<CallLogGroup>,
        knownNumbers: Set<String>
    ) {
        val unknown = groups
            .filter { it.contactId == null && it.e164 !in knownNumbers }
            .map { it.number }
            .distinct()

        val names = suspendRunCatching { externalCallerId.lookupBatch(unknown) }
            .getOrDefault(emptyMap())
        if (names.isEmpty()) return

        // Имя подставляется только там, где своего нет: данные книги
        // всегда важнее подсказки стороннего сервиса
        val updated = _state.value.callLog.map { group ->
            if (group.displayName == null) {
                names[group.number]?.let { group.copy(displayName = it) } ?: group
            } else group
        }
        _state.update { it.copy(callLog = updated) }
    }

    /**
     * Задача текущего поиска.
     *
     * Хранится, чтобы отменить её при следующем нажатии: продолжать
     * искать по «5» после ввода «55» бессмысленно, а результат первого
     * поиска мог бы прийти позже второго и перезаписать его.
     */
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        // Поле ввода обновляется мгновенно и синхронно: задержка здесь
        // означала бы, что цифра появляется с запозданием после нажатия,
        // а это ощущается как подвисание клавиатуры
        _state.update { it.copy(query = q) }

        searchJob?.cancel()

        // Пустой запрос очищаем сразу, без похода в фон: ждать нечего,
        // а мигание прежних результатов заметно
        if (q.isEmpty()) {
            _state.update { it.copy(filteredContacts = emptyList(), filteredLog = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch { applyFilter(q) }
    }

    /**
     * ПРИМЕНЕНИЕ ПОИСКОВОГО ЗАПРОСА.
     *
     * ── Почему в фоновом потоке ────────────────────────────────────────────
     * Поиск перебирает все контакты и все загруженные записи журнала,
     * для каждого имени считает совпадение по T9 и ранжирует результат.
     * На тысяче контактов это десятки миллисекунд.
     *
     * Раньше он выполнялся синхронно в главном потоке прямо в обработчике
     * нажатия — то есть между нажатием на цифру и её появлением
     * на экране. При бюджете 16 мс на кадр это гарантированный пропуск
     * кадров, который ощущается как «клавиатура залипает».
     *
     * ── Короткая пауза перед началом ───────────────────────────────────────
     * Между цифрами при быстром наборе проходит 60–100 мс. Пауза
     * в 120 мс означает, что при наборе «495» поиск выполнится один раз
     * вместо трёх: два промежуточных результата пользователь всё равно
     * не успел бы прочитать.
     *
     * Пауза не задерживает отображение самой цифры — та появляется сразу,
     * см. onQueryChange.
     */
    private suspend fun applyFilter(query: String) {
        delay(SEARCH_DEBOUNCE_MS)

        /*
         * ПОИСК ДОГРУЖАЕТ ЖУРНАЛ, ЕСЛИ НУЖНО.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЗАЧЕМ
         * ══════════════════════════════════════════════════════════════════
         * Журнал читается страницами: при открытии загружены последние
         * 500 записей. Поиск работал по ним — и звонок годичной давности
         * не находился, хотя он есть.
         *
         * Человек при этом не понимает, почему: он помнит, что звонил,
         * а приложение говорит «ничего не найдено».
         *
         * ── Как решено ─────────────────────────────────────────────────────
         * Если по загруженному ничего не нашлось, подгружаем следующую
         * страницу и пробуем снова. И так, пока не найдём или пока
         * журнал не кончится.
         *
         * ── Почему не грузим всё сразу ─────────────────────────────────────
         * Большинство запросов находят ответ на первой же странице:
         * ищут тех, с кем общались недавно. Читать десять тысяч записей
         * ради этого — платить за редкий случай на каждом поиске.
         *
         * ── Про предел ─────────────────────────────────────────────────────
         * MAX_SEARCH_PAGES ограничивает глубину: без него поиск
         * несуществующего номера прочёл бы весь журнал, и человек ждал бы
         * секунды ради «не найдено».
         */
        var attempts = 0
        while (
            attempts < MAX_SEARCH_PAGES &&
            query.isNotEmpty() &&
            callLogLoader.canLoadMore &&
            !hasMatches(query)
        ) {
            attempts++

            val more = suspendRunCatching { callLogLoader.loadNextPage() }.getOrNull()
                ?: break

            _state.update { it.copy(callLog = more) }

            // Запрос мог измениться, пока грузили: тогда результат
            // уже не нужен
            if (_state.value.query != query) return
        }

        val current = _state.value
        val order = settingsState.value.sortOrder

        val result = withContext(defaultDispatcher) {
            ContactSearch.search(
                contacts = current.contacts,
                callLog = current.callLog,
                query = query,
                order = order,
                notes = current.notes
            )
        }

        // Запрос мог измениться, пока шёл поиск: тогда результат уже
        // не нужен, и показывать его — значит показать ответ на вопрос,
        // которого больше нет
        if (_state.value.query != query) return

        // Имена найденных — в выбранной форме (1.6.2, ContactNames)
        val nameFormat = settingsState.value.contactNameFormat
        val shownContacts = result.contacts.map { ContactNames.present(it, nameFormat) }

        _state.update {
            it.copy(filteredContacts = shownContacts, filteredLog = result.callLog)
        }
    }

    /**
     * ВЫЗОВ С ВЫБОРОМ SIM.
     *
     * Приоритет:
     *  1. Явно указанный слот (пользователь нажал кнопку конкретной SIM)
     *     — используем и ЗАПОМИНАЕМ для этого номера.
     *  2. Запомненное предпочтение для номера.
     *  3. Глобальная SIM по умолчанию из настроек.
     *  4. Ничего — отдаём выбор системе.
     *
     * Автоматически SIM по истории НЕ угадываем: молчаливая смена карты
     * означает звонок с другого номера, а это вопрос денег и того,
     * какой номер увидит собеседник.
     */
    fun call(number: String, slotIndex: Int = -1) = viewModelScope.launch {
        val e164 = com.ogneliss.dialer.util.PhoneFormatter.toE164(number)

        val slot = when {
            slotIndex >= 0 -> {
                if (prefs.rememberSim.first()) simPrefs.remember(e164, slotIndex)
                slotIndex
            }
            else -> simPrefs.slotFor(e164)
                ?: prefs.defaultSim.first().takeIf { it >= 0 }
                ?: -1
        }
        actions.placeCall(number, slot)
    }

    /**
     * Нажатие кнопки вызова на клавиатуре.
     * Если у введённого номера есть совпадение с контактом, у которого
     * несколько номеров, показываем выбор — иначе звоним сразу.
     */
    fun callFromDialpad(query: String, slotIndex: Int) {
        if (query.isBlank()) return
        call(query, slotIndex)

        /*
         * ПОЛЕ ОЧИЩАЕТСЯ ПОСЛЕ НАБОРА.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ
         * ══════════════════════════════════════════════════════════════════
         * Номер набран, звонок ушёл — держать его в поле незачем.
         * Хуже того: пока он там, журнал отфильтрован по нему, и после
         * возврата с экрана вызова человек видит список из одной-двух
         * записей вместо своей истории.
         *
         * Именно так выглядит беда со снимка: поле пустое (клавиатура
         * успела свернуться), а список остался коротким.
         */
        onQueryChange("")
    }

    /**
     * Собирает варианты номеров абонента для шторки выбора.
     * Возвращает пустой список, если номер один — тогда шторку показывать
     * не нужно и звонок идёт сразу.
     */
    suspend fun numberOptionsFor(group: CallLogGroup): List<NumberOption> {
        val contact = group.contactId?.let { id ->
            state.value.contacts.firstOrNull { it.id == id }
        }
        val numbers = contact?.numbers?.map { it.value to it.label }
            ?: group.numbers.map { it to null }

        if (numbers.size <= 1) return emptyList()

        // Последний вызов на каждый номер — чтобы показать направление и SIM
        val lastByNumber = group.entries.groupBy { it.number }

        return numbers.map { (number, label) ->
            val e164 = com.ogneliss.dialer.util.PhoneFormatter.toE164(number)
            val last = lastByNumber[number]?.maxByOrNull { it.date }
            NumberOption(
                number = number,
                label = label,
                lastWasOutgoing = last?.let { it.type == CallType.OUTGOING },
                preferredSlot = simPrefs.slotFor(e164),
                isMostRecent = number == group.number
            )
        }
    }

    /** Мессенджер для группы журнала — разбор у handleSwipe. */
    private suspend fun openMessenger(action: SwipeAction, group: CallLogGroup) {
        val contactId = group.contactId ?: state.value.contacts
            .firstOrNull { c -> c.numbers.any { it.e164 == group.e164 } }?.id
        val links = contactId?.let { id ->
            withContext(Dispatchers.IO) { ContactMessengers.linksFor(app, id) }
        }.orEmpty().filter { it.kind == action }
        // Номер строки журнала, если на нём мессенджер есть; иначе — любой,
        // на котором есть; иначе — по номеру, как было
        val link = links.firstOrNull { it.e164 == group.e164 } ?: links.firstOrNull()
        if (link != null && ContactMessengers.open(app, link)) return
        when (action) {
            SwipeAction.WHATSAPP -> actions.openWhatsApp(group.number)
            SwipeAction.TELEGRAM -> ExternalApps.telegram(app, group.number)
            SwipeAction.VIBER -> ExternalApps.viber(app, group.number)
            SwipeAction.MAX -> ExternalApps.max(app, group.number)
            else -> Unit
        }
    }

    fun handleSwipe(action: SwipeAction, group: CallLogGroup) = viewModelScope.launch {
        when (action) {
            SwipeAction.CALL -> actions.placeCall(group.number)
            SwipeAction.SMS -> actions.openSms(group.number)
            /*
             * Мессенджеры — через телефонную книгу (1.2.5): открывается чат
             * на том номере, где мессенджер у контакта есть. Раньше свайп
             * открывал мессенджер по номеру строки журнала — а это мог быть
             * городской номер или номер без этого мессенджера. Если строки
             * книги нет (номер не в контактах), — по номеру, как было.
             */
            SwipeAction.WHATSAPP, SwipeAction.TELEGRAM,
            SwipeAction.VIBER, SwipeAction.MAX -> openMessenger(action, group)

            /*
             * Заметка открывается редактором, а не сохраняется молча:
             * текст пишет человек, и подставить за него нечего.
             */
            SwipeAction.ADD_NOTE -> _noteEditorFor.value = group
            SwipeAction.SHARE_NUMBER -> ExternalApps.share(app, group.number)
            /*
             * Напоминание спрашивает срок, а не ставится молча.
             *
             * Раньше оно уходило на час вперёд без подтверждения: человек
             * не знал, сработало ли, и не мог задать нужное время.
             * А срок зависит от дела — «освободится через пять минут»
             * и «позвонить завтра» требуют разного.
             */
            SwipeAction.REMIND_CALLBACK -> _reminderRequest.value = group
            SwipeAction.EDIT_BEFORE_CALL -> actions.editBeforeCall(group.number)
            SwipeAction.VIEW_CONTACT -> group.contactId?.let { actions.viewContact(it) }
            /*
             * Удаление свайпом — после подтверждения (1.3.0). Свайп легко
             * сделать случайно, а запись удаляется из системного журнала,
             * и восстановить её невозможно. Окно — в RootScreen (deleteRequest).
             * Удаление из меню записи — явный выбор красного пункта после
             * долгого нажатия, там вопроса нет.
             *
             * 1.5.7: и там, и там — «Отменить» после удаления: из системного
             * журнала запись уходит, когда сообщение погаснет
             * (deleteGroupsWithUndo).
             */
            SwipeAction.DELETE_FROM_LOG -> _deleteRequest.value = group
            /*
             * Блокировка идёт в общие правила, а не в отдельное хранилище.
             *
             * Раньше свайп писал в BlockListStore — легаси-хранилище
             * на DataStore, которое нигде больше не читалось. Номер,
             * заблокированный свайпом, не появлялся в списке блокировки
             * и не участвовал в фильтрации входящих: функция выглядела
             * рабочей и не работала.
             */
            // Блокировка идёт через единый контроллер: раньше свайп писал
            // в отдельное хранилище, которое нигде не читалось, и номер
            // не появлялся ни в списке, ни в фильтрации
            SwipeAction.BLOCK_NUMBER -> viewModelScope.launch {
                blocking.block(group.e164)
                // Блокировка обратима, и человек должен об этом узнать
                // сразу: искать способ разблокировать в настройках
                // через минуту — уже поздно
                notify(
                    R.string.msg_blocked,
                    group.displayName ?: PhoneFormatter.format(group.number)
                ) {
                    viewModelScope.launch {
                        blockedCallLog.forget(group.e164)
                        blocking.removeRule(group.e164)
                        // Пометка о блокировке снимается с записи сразу
                        refresh()
                    }
                }
            }
            SwipeAction.EMAIL -> {
                val email = state.value.contacts
                    .firstOrNull { c -> c.numbers.any { it.e164 == group.e164 } }
                    ?.emails?.firstOrNull()
                email?.let { openEmail(it) }
            }
            // Копирование делает то же, что пункт в шторке: обе точки
            // ведут в один метод, чтобы не разойтись при правках
            SwipeAction.COPY_NUMBER -> copyNumber(group.number)

            SwipeAction.NONE -> Unit
        }
    }

    /*
     * Постановка идёт через репозиторий, а не напрямую в AlarmManager:
     * напоминание должно попасть и в базу, иначе его не увидеть,
     * не отменить и не восстановить после перезагрузки.
     */
    fun remindLater(number: String, name: String?, delayMinutes: Long) =
        viewModelScope.launch {
            reminders.schedule(
                number, name, System.currentTimeMillis() + delayMinutes * 60_000L
            )
        }

    /** Отмена напоминания: из карточки абонента. */
    fun cancelReminder(number: String) = viewModelScope.launch {
        reminders.cancel(number)
    }

    /**
     * Активные напоминания.
     *
     * Список нужен трём местам сразу: значок в строке журнала, значок
     * в контактах и блок в карточке. Один поток на всех, чтобы значок
     * и карточка не расходились.
     */
    val activeReminders = reminders.active
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun topContacts() = statsRepo.topContacts()

    fun saveNote(e164: String, note: String, tags: List<String>, label: String? = null) =
        viewModelScope.launch { notesRepo.save(e164, note, tags, label) }

    /**
     * Поставить отметку «спам» или «мошенники», либо снять (type = null).
     *
     * Сообщение и отклик обязательны: действие меняет судьбу звонков
     * с этого номера, если включена блокировка помеченных, — и человек
     * должен увидеть, что нажатие засчитано.
     *
     * Отмена доступна сразу: отметку ставят часто в сердцах, сразу после
     * неприятного звонка, и ошибиться номером легко.
     */
    fun setSpamMark(e164: String, type: SpamMarkType?) = viewModelScope.launch {
        val previous = spamMarksRepo.get(e164)

        if (type == null) spamMarksRepo.unmark(e164) else spamMarksRepo.mark(e164, type)

        val text = when (type) {
            null -> R.string.msg_spam_mark_removed
            SpamMarkType.SPAM -> R.string.msg_marked_spam
            SpamMarkType.FRAUD -> R.string.msg_marked_fraud
        }

        notify(text, undo = {
            viewModelScope.launch {
                if (previous == null) spamMarksRepo.unmark(e164) else spamMarksRepo.restore(previous)
            }
        })
    }

    fun blockNumber(e164: String, comment: String? = null) =
        viewModelScope.launch {
            /*
             * Отклик обязателен.
             *
             * Кнопка блокировки меняет вид по потоку правил, но если
             * правило не добавилось — а block() возвращает false
             * на пустом номере, — не происходит вообще ничего.
             * Человек нажимает второй раз и решает, что сломано.
             */
            val ok = blocking.block(e164, comment)

            Logger.i("Block", "Блокировка ${Logger.mask(e164)}: ${if (ok) "ок" else "не удалась"}")
            notify(if (ok) R.string.msg_blocked else R.string.msg_block_failed)

            // Пометка о блокировке появляется в журнале сразу,
            // а не после следующего звонка с этого номера
            refresh()
        }

    // ── Настройки для декларативных экранов ────────────────────────────────

    /**
     * Единый снимок всех настроек. Экран категории подписывается на него
     * и рисует переключатели по ключам из реестра.
     */
    /**
     * Снимок настроек.
     *
     * Сборка вынесена в SettingsStateSource: прежняя цепочка combine
     * передавала значения списком и разбирала их по индексу, из-за чего
     * перестановка любой строки молча меняла смысл соседних.
     */
    val settingsState: StateFlow<SettingsState> = settingsSource.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    /*
     * ── РАБОТА С НАСТРОЙКАМИ ДЕЛЕГИРУЕТСЯ SettingsCommands ─────────────────
     *
     * Экраны обращаются сюда по строковым ключам из декларативного реестра,
     * а соответствие «ключ → сеттер» — механическая таблица на две сотни
     * строк, которой не место рядом с журналом вызовов и контактами.
     *
     * Здесь остаются только тонкие обёртки: они переводят вызов в корутину
     * и добавляют то, что известно лишь ViewModel — например, что смена
     * группировки требует перестроения списка.
     */

    fun setSwitch(key: String, value: Boolean) = viewModelScope.launch {
        settingsCommands.setSwitch(key, value)
    }

    /** Язык изменён — активность нужно пересоздать, см. SettingsCommands. */
    val localeChanged = settingsCommands.localeChanged

    fun onLocaleChangeHandled() = settingsCommands.onLocaleChangeHandled()

    fun setChoice(key: String, value: String) = viewModelScope.launch {
        settingsCommands.setChoice(key, value)
        when (key) {
            // Группировка меняет только способ объединения уже загруженных
            // записей. Перечитывать журнал незачем — это лишний запрос
            // к провайдеру ради данных, которые уже в памяти
            "grouping" -> {
                val groups = callLogLoader.regroup()
                _state.update { it.copy(callLog = groups) }
            }
            // Сортировка влияет на выдачу поиска: пересобираем её
            "sort_order" -> viewModelScope.launch { applyFilter(_state.value.query) }
        }
    }

    fun setSimLabel(slotIndex: Int, label: String) = viewModelScope.launch {
        settingsCommands.setSimLabel(slotIndex, label)
    }

    fun choiceValue(key: String): String =
        settingsCommands.currentChoice(key, settingsState.value)

    /**
     * Значение варианта в данном снимке настроек — в том числе в исходном,
     * SettingsState(): по нему экран раздела видит, что изменено (1.5.8).
     */
    fun choiceValueOf(key: String, settings: SettingsState): String =
        settingsCommands.currentChoice(key, settings)

    /**
     * Вернуть вариант к исходному («Вернуть как было», 1.5.8). У свайпа —
     * и дополнительные действия: исходно их нет, а оставшись, они
     * показывались бы и после сброса («SMS, WhatsApp»).
     */
    fun resetChoice(key: String) {
        setChoice(key, settingsCommands.currentChoice(key, SettingsState()))
        when (key) {
            "swipe_right" -> setSwipeExtra(isRight = true, actions = emptyList())
            "swipe_left" -> setSwipeExtra(isRight = false, actions = emptyList())
        }
    }

    fun choiceLabel(context: Context, key: String): String {
        // Свайп с несколькими действиями — все через запятую: «SMS, WhatsApp»
        val extra = when (key) {
            "swipe_right" -> swipeRightExtra.value
            "swipe_left" -> swipeLeftExtra.value
            else -> emptyList()
        }
        val base = settingsCommands.choiceLabel(context, key, settingsState.value)
        if (extra.isEmpty()) return base
        return (listOf(base) + extra.map { context.getString(SettingsCommands.swipeActionLabelRes(it)) })
            .joinToString(", ")
    }

    fun choiceTitle(context: Context, key: String): String =
        settingsCommands.choiceTitle(context, key)

    /**
     * Обработка действий с экранов настроек.
     * Ключи совпадают с SettingKind.Action и SettingKind.SystemLink в реестре.
     */
    fun handleSettingsAction(key: String) {
        when {
            key.startsWith("system:") -> openSystemLink(key.removePrefix("system:"))
            key == "log_share" -> com.ogneliss.dialer.log.LogExporter.share(app)
            key == "log_clear" -> com.ogneliss.dialer.log.Logger.clear()
            key == "reset" -> viewModelScope.launch { settingsCommands.resetAll() }
            key == "blocked_numbers" -> openSystemBlockList()
            /*
             * Выбор приложения-определителя.
             *
             * Системный диалог показывает все установленные кандидаты
             * и предупреждает о последствиях. Молча забрать роль нельзя,
             * и это правильно: роль влияет на то, какое приложение
             * видит все входящие номера.
             */
            key == "battery" -> {
                /*
                 * Исключение из энергосбережения.
                 *
                 * Без него система усыпляет процесс, и наблюдатель
                 * за журналом не просыпается: пропущенный звонок
                 * не появится, пока человек сам не откроет приложение.
                 */
                if (AppPermissions.isIgnoringBatteryOptimizations(app)) {
                    notify(R.string.settings_battery_already)
                } else {
                    AppPermissions.requestIgnoreBatteryOptimizations(app)
                }
            }
            key == "sim_labels" -> Unit   // диалог открывает экран настроек
            // Остальные действия обрабатывает навигация в RootScreen:
            // им нужен доступ к NavController, которого у ViewModel нет
            else -> Unit
        }
    }

    private fun openSystemLink(which: String) {
        val links = com.ogneliss.dialer.util.SystemLinks
        when (which) {
            "sim" -> links.simCards(app)
            "dnd" -> links.doNotDisturb(app)
            "sound" -> links.sound(app)
            "notifications" -> links.appNotifications(app)
            "default_apps" -> links.defaultApps(app)
            "blocked_numbers" -> openSystemBlockList()
        }
    }

    /**
     * Кнопки для раскрывающейся панели свайпа.
     * Строки берутся из ресурсов через контекст приложения: фабрика
     * не зависит от Compose и потому тестируема.
     */
    fun revealActions(
        action: SwipeAction,
        group: CallLogGroup,
        sims: List<SimSlot>
    ): List<RevealAction> {
        val contact = group.contactId?.let { id ->
            state.value.contacts.firstOrNull { it.id == id }
        }
        val numbers = contact?.numbers?.map { it.value } ?: group.numbers
        val labels = contact?.numbers?.map { it.label } ?: numbers.map { null }

        return RevealActionFactory.build(
            action = action,
            numbers = numbers,
            labels = labels,
            sims = sims,
            strings = revealStrings,
            onCall = { number, slot -> call(number, slot) },
            onSms = { openSms(it) },
            onMessenger = { ExternalApps.whatsapp(app, it) },
            onSimple = { handleSwipe(it, group) }
        )
    }

    /** Локализованные строки для панели свайпа. */
    private val revealStrings = AndroidRevealStrings(app)

    fun openSystemBlockList() = systemBlock.openSystemScreen()

    /** Открыть SMS-приложение по умолчанию. Своих SMS приложение не отправляет. */
    fun openSms(number: String) = ExternalApps.sms(app, number)

    /** SMS с выбранной SIM — подсказкой приложению сообщений (1.7.0, ExternalApps.smsFromSim). */
    fun openSmsFromSim(number: String, sim: com.ogneliss.dialer.data.SimSlot) =
        ExternalApps.smsFromSim(app, number, sim.subscriptionId, sim.slotIndex)

    /**
     * История вызовов по номеру для карточки абонента.
     *
     * Нужна, когда карточка открыта из списка контактов: в загруженной
     * странице журнала вызовов этого человека может не быть вовсе,
     * а история у него есть — просто глубже. Запрос идёт с условием
     * по номеру, а не выборкой всего журнала.
     */
    suspend fun historyFor(number: String) = callLogRepo.historyFor(number)

    // ── Массовые операции ──────────────────────────────────────────────────

    /**
     * Удаление нескольких карточек журнала разом.
     *
     * Удаляется по конкретным идентификаторам записей, а не по номерам:
     * при группировке по абоненту одна карточка содержит вызовы
     * с нескольких номеров, и удаление по номеру снесло бы не то.
     *
     * @return сколько карточек удалось удалить
     */
    fun deleteGroups(groups: Collection<CallLogGroup>) = viewModelScope.launch {
        var deleted = 0
        groups.forEach { group ->
            if (callLogRepo.deleteGroup(group)) deleted++
        }
        Logger.i("CallLog", "Массовое удаление: $deleted из ${groups.size}")
        refresh()
    }

    /**
     * Блокировка нескольких номеров разом.
     *
     * Идёт в наши правила, а не в системный список: системный требует
     * отдельного подтверждения на каждый номер на части прошивок,
     * и массовая операция превратилась бы в череду диалогов.
     */
    fun blockGroups(groups: Collection<CallLogGroup>) = viewModelScope.launch {
        blocking.blockAll(groups)
        refresh()
    }

    /** Удаление нескольких правил из списков блокировки. */
    /**
     * Снятие правил убирает и пометки в журнале.
     *
     * Иначе запись продолжала бы говорить «Заблокирован — в вашем чёрном
     * списке» после того, как номер оттуда убрали: человек исправил
     * правило, а приложение утверждает обратное.
     */
    fun removeRules(patterns: Collection<String>) = viewModelScope.launch {
        patterns.forEach { blockedCallLog.forget(it) }
        blocking.removeRules(patterns)
    }

    /** Снятие избранного или удаление нескольких контактов сразу. */
    fun deleteContacts(contactIds: Collection<Long>) = viewModelScope.launch {
        // Тот же путь, что у одного контакта: с закреплёнными (1.3.4)
        contactIds.forEach { deleteContactNow(it) }
        refresh()
    }

    fun clearLog() = viewModelScope.launch { callLogRepo.clearAll(); refresh() }

    /*
     * ══════════════════════════════════════════════════════════════════════
     * УДАЛЕНИЕ С «ОТМЕНИТЬ», КАК В TELEGRAM (1.5.7)
     * ══════════════════════════════════════════════════════════════════════
     * Из системного журнала запись удаляется навсегда, поэтому раньше
     * отмены не было вовсе (разбор у handleSwipe). Теперь удаление
     * откладывается: строки пропадают с экрана сразу, внизу — «Удалено
     * из журнала · Отменить», а из системного журнала записи уходят,
     * когда сообщение погаснет. «Отменить» — строки возвращаются,
     * журнал не тронут.
     *
     * ── Если сообщение не показалось ───────────────────────────────────────
     * Экран могли закрыть, а очередь сообщений — отстать. Запасной таймер
     * удаляет и без сообщения, но позже: отмена всегда успевает раньше.
     * Удаление выполняется один раз — кто бы ни сработал первым.
     *
     * ── Почему appScope ────────────────────────────────────────────────────
     * Человек мог уйти из приложения сразу после удаления: viewModelScope
     * отменился бы вместе с экраном, и записи вернулись бы в журнал.
     */
    private val _pendingDeletion = MutableStateFlow<Set<String>>(emptySet())
    /** Ключи записей, удалённых с «Отменить», — экран их уже не показывает. */
    val pendingDeletion: StateFlow<Set<String>> = _pendingDeletion

    fun deleteGroupsWithUndo(groups: Collection<CallLogGroup>) {
        val batch = groups.filter { it.key !in _pendingDeletion.value }
        if (batch.isEmpty()) return
        val keys = batch.mapTo(HashSet()) { it.key }
        _pendingDeletion.update { it + keys }

        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        var fallback: Job? = null
        val commit: () -> Unit = {
            if (settled.compareAndSet(false, true)) {
                fallback?.cancel()
                appScope.launch {
                    var deleted = 0
                    batch.forEach { group ->
                        if (runCatching { callLogRepo.deleteGroup(group) }.getOrDefault(false)) deleted++
                    }
                    Logger.i("CallLog", "Удаление с отменой: $deleted из ${batch.size}")
                    withContext(Dispatchers.Main) { refresh() }
                    // Ключи снимаются, когда журнал уже перечитан без этих
                    // записей: иначе строки мелькнули бы на миг между снятием
                    // и перечитыванием
                    withTimeoutOrNull(REFRESH_WAIT_MS) {
                        state.first { s -> s.callLog.none { it.key in keys } }
                        rows.first { r -> r.none { it.key in keys } }
                    }
                    _pendingDeletion.update { it - keys }
                }
            }
        }
        fallback = appScope.launch {
            delay(UNDO_FALLBACK_MS)
            commit()
        }
        val single = batch.singleOrNull()
        notify(
            textRes = if (single != null) R.string.msg_deleted_from_log else R.string.msg_deleted_from_log_many,
            arg = if (single != null) null else batch.size.toString(),
            onTimeout = commit
        ) {
            if (settled.compareAndSet(false, true)) {
                // Таймер к этому времени уже заведён (присвоен выше) — без «?.»
                fallback.cancel()
                _pendingDeletion.update { it - keys }
            }
        }
    }

    /**
     * Номер, для которого открыт редактор заметки.
     *
     * Живёт во ViewModel, а не в экране: заметку вызывают из журнала,
     * из шторки действий и свайпом, и держать состояние в каждом месте
     * означало бы три разных поведения.
     */
    private val _noteEditorFor = MutableStateFlow<CallLogGroup?>(null)
    val noteEditorFor: StateFlow<CallLogGroup?> = _noteEditorFor

    fun openNoteEditor(group: CallLogGroup) { _noteEditorFor.value = group }
    fun closeNoteEditor() { _noteEditorFor.value = null }

    // ── Экран после звонка ─────────────────────────────────────────────────

    /**
     * Завершённый вызов, о котором стоит предложить сделать заметку.
     * null — показывать нечего.
     *
     * Решение принимает координатор: проверяются настройки, срок годности
     * события, факт состоявшегося разговора и наличие номера в контактах.
     */
    val pendingPostCall: StateFlow<PostCallCoordinator.CompletedCall?> =
        combine(
            postCallCoordinator.pending,
            prefs.postCallEnabled,
            prefs.postCallUnknownOnly,
            state
        ) { event, enabled, unknownOnly, uiState ->
            val known = event != null && uiState.contacts.any { contact ->
                contact.numbers.any { it.e164 == PhoneFormatter.toE164(event.number) }
            }
            if (postCallCoordinator.shouldShow(event, enabled, unknownOnly, known)) event
            else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun dismissPostCall() = postCallCoordinator.consume()

    /** Сохранение заметки с экрана после звонка. */
    fun savePostCallNote(number: String, note: String, tags: List<String>) {
        val e164 = PhoneFormatter.toE164(number)
        saveNote(e164, note, tags)
        postCallCoordinator.consume()
    }

    // ── Готовые строки списка ──────────────────────────────────────────────

    /** Серия повторных чтений SIM после смены карт — разбор у подписки. */
    private var simSettleJob: kotlinx.coroutines.Job? = null

    private val _rows = MutableStateFlow<List<CallLogRowState>>(emptyList())

    /**
     * ГОТОВЫЕ К ОТРИСОВКЕ СТРОКИ.
     *
     * ── Почему подготовка ушла из экрана во ViewModel ──────────────────────
     * Раньше она выполнялась внутри `remember` в composable, то есть
     * **в главном потоке**. На трёх тысячах записей это разбор трёх тысяч
     * номеров через libphonenumber прямо во время отрисовки — заметная
     * пауза при каждой подгрузке страницы.
     *
     * Здесь работа идёт на Default: счётная задача, пул по числу ядер.
     * Экран получает готовый список и только рисует.
     *
     * ── Инкрементально ─────────────────────────────────────────────────────
     * Подгрузка добавляет полторы сотни записей, а пересобирался список
     * целиком. Теперь строки, чьи данные не изменились, переиспользуются:
     * сравнивается целочисленный отпечаток.
     */
    val rows: StateFlow<List<CallLogRowState>> = _rows

    /**
     * Пересборка строк.
     *
     * @param labels локализованные подписи — их знает только слой
     *        интерфейса, поэтому передаются снаружи
     */
    fun rebuildRows(
        groups: List<CallLogGroup>,
        labels: CallLogRowMapper.Labels,
        dateFormat: java.text.SimpleDateFormat
    ) {
        viewModelScope.launch {
            val current = state.value
            /*
             * Подписи о блокировке читаются ДО перехода в фон.
             *
             * snapshot обращается к базе и потому suspend, а внутри
             * withContext мы уже в счётном диспетчере: запрос к диску
             * оттуда занял бы поток, предназначенный для вычислений.
             */
            /*
             * ПОМЕТКА О БЛОКИРОВКЕ — ИЗ ДВУХ ИСТОЧНИКОВ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ ОДНОГО МАЛО
             * ══════════════════════════════════════════════════════════════
             * blockedCallLog хранит вызовы, которые МЫ отклонили: причина
             * записывается в момент срабатывания.
             *
             * Но человек блокирует номер заранее — из карточки или
             * свайпом. Пока заблокированный не позвонит, записи там нет,
             * и в журнале ничего не менялось: номер выглядел обычным,
             * хотя звонки от него уже не пройдут.
             *
             * Именно это вы и заметили.
             *
             * ── Что добавлено ──────────────────────────────────────────────
             * Правила блокировки. Если номер в чёрном списке, пометка
             * стоит сразу — независимо от того, звонил он после этого
             * или нет.
             *
             * ── Почему записи о вызове важнее ──────────────────────────────
             * Она точнее: говорит не только «заблокирован», но и почему
             * именно — правило, оценка риска, системный список.
             * Поэтому она и перекрывает общую пометку.
             */
            val activeRules = runCatching {
                blocking.allRules.first()
                    .filter { it.kind == "block" }
                    .map { it.pattern }
                    .toSet()
            }.getOrDefault(emptySet())

            val ruleLabel = app.getString(R.string.blocked_by_rule)

            val blockedLabels = buildMap {
                activeRules.forEach { pattern -> put(pattern, ruleLabel) }

                /*
                 * ПОМЕТКА ДЛЯ ПРАВИЛ-МАСОК.
                 *
                 * Строка выше кладёт в карту сам шаблон, а ищут в ней
                 * потом по номеру: blockedLabels[group.e164]. Для точного
                 * правила это одно и то же, а для маски «+7800*» —
                 * нет: ключ «+7800*» не совпадёт ни с одним номером,
                 * и в журнале заблокированные по маске выглядели
                 * обычными записями.
                 *
                 * Поэтому маски раскрываем по номерам, которые реально
                 * есть в журнале. Их немного, и сравнение идёт только
                 * с масками, а не со всеми правилами.
                 *
                 * putIfAbsent: точное правило точнее маски и уже стоит.
                 */
                val masks = activeRules.filter { it.contains('*') || it.contains('?') }
                if (masks.isNotEmpty()) {
                    groups.forEach { group ->
                        if (group.e164.isNotBlank() &&
                            masks.any { RulePattern.matches(it, group.e164) }
                        ) {
                            putIfAbsent(group.e164, ruleLabel)
                        }
                    }
                }

                blockedCallLog.snapshot().forEach { (number, entry) ->
                    put(number, BlockedReasonText.format(app, entry))
                }
            }

            val prepared = withContext(defaultDispatcher) {
                CallLogRowMapper.mapIncremental(
                    groups = groups,
                    previous = _rows.value,
                    numberInfo = current.numberInfo,
                    knownNumbers = current.contacts
                        .flatMapTo(HashSet()) { c -> c.numbers.map { it.e164 } },
                    /*
                     * ЗНАЧОК SIM — ТОЛЬКО КОГДА КАРТ ДВЕ.
                     *
                     * ══════════════════════════════════════════════════════
                     * ЗАЧЕМ ЭТО УСЛОВИЕ
                     * ══════════════════════════════════════════════════════
                     * На одной карте значок «1» стоял у каждой строки
                     * журнала и не сообщал ничего: выбора всё равно нет.
                     * Хуже того, его принимали за счётчик повторных
                     * звонков — так его прочитал сторонний разбор
                     * интерфейса, и это разумное прочтение: маленькая
                     * цифра рядом со стрелкой выглядит как количество.
                     *
                     * Когда карт две, значок необходим: без него нельзя
                     * понять, с какой ушёл вызов.
                     */
                    simSlots = if (current.sims.size > 1) {
                        current.sims.associate {
                            it.subscriptionId.toString() to it.slotIndex
                        }
                    } else {
                        emptyMap()
                    },
                    /*
                     * Карта по НОМЕРУ, а не по contactId.
                     *
                     * contactId разбирается из CACHED_LOOKUP_URI журнала,
                     * а на многих прошивках последний сегмент этой строки —
                     * lookup-ключ, а не число: разбор возвращает null,
                     * и фотография не находится никогда.
                     *
                     * Номер в E.164 есть у каждой записи и от кэша
                     * журнала не зависит.
                     */
                    /*
                     * Порядок тот же, что при выборе имени: первый
                     * по алфавиту. Иначе карточка могла показать имя
                     * одного контакта с фотографией другого
                     */
                    /*
                     * Подписи о блокировке.
                     *
                     * Формируются здесь, а не в маппере: нужен контекст
                     * для чтения строк, а маппер работает в фоне
                     * и о ресурсах не знает.
                     */
                    blockedLabels = blockedLabels,
                    contactPhotos = buildMap {
                        current.contacts
                            .sortedBy { it.displayName.lowercase() }
                            .forEach { contact ->
                                val photo = contact.photoUri ?: return@forEach
                                contact.numbers.forEach { number ->
                                    putIfAbsent(number.e164, photo)
                                }
                            }
                    },
                    notes = current.notes,
                    dateFormat = dateFormat,
                    labels = labels,
                    // Настройка «Оператор и регион»: у всех, только
                    // у незнакомых или не показывать
                    carrierInfo = settingsState.value.carrierInfo,
                    // Значок напоминания в строке
                    remindedNumbers = activeReminders.value.mapTo(HashSet()) { it.e164 },
                    // «Фамилия Имя» (1.6.2): имя из журнала переставляется,
                    // только если это имя того же контакта — ContactNames
                    contactNames = ContactNames.journalNames(
                        current.contacts, settingsState.value.contactNameFormat
                    ),
                    // Имя — из книги, а не из журнала (1.7.0): разбор у CallLogRowMapper.nameOf.
                    // Тот же порядок, что у фото: первый по алфавиту
                    bookNames = buildMap {
                        current.contacts
                            .sortedBy { it.displayName.lowercase() }
                            .forEach { contact ->
                                if (contact.displayName.isBlank()) return@forEach
                                contact.numbers.forEach { number -> putIfAbsent(number.e164, contact.displayName) }
                            }
                    }
                )
            }
            _rows.value = prepared
        }
    }

    // ── Экспорт истории по абоненту ────────────────────────────────────────

    /**
     * Текст выгрузки по абоненту.
     *
     * Собирается в фоне: чтение истории из провайдера плюс форматирование
     * дат для каждой записи. На абоненте с сотней звонков это заметно.
     *
     * @return текст или null, если историю не удалось прочитать
     */
    // ── Выгрузки — ExportController (1.3.4) ────────────────────────────────

    suspend fun buildHistoryExport(group: CallLogGroup): String? =
        exporter.historyText(group, state.value.notes[group.e164], state.value.spamMarks[group.e164]?.type)

    suspend fun buildCallLogCsv(): String? = exporter.callLogCsv(state.value.contacts)

    fun callLogCsvFileName(): String = exporter.callLogCsvFileName()

    fun exportFileName(group: CallLogGroup): String = exporter.historyFileName(group)

    // ── Выбор SIM для действия свайпа ──────────────────────────────────────

    val swipeSimMode = prefs.swipeSimMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SwipeSimMode.ASK)

    fun setSwipeSimMode(mode: SwipeSimMode) = viewModelScope.launch {
        prefs.setSwipeSimMode(mode)
    }

    /**
     * Запрос выбора SIM.
     *
     * Живёт во ViewModel, а не в экране: диалог вызывается и из журнала,
     * и из контактов, и держать состояние в двух местах означало бы
     * два разных поведения.
     */
    private val _simRequest = MutableStateFlow<SimRequest?>(null)
    val simRequest: StateFlow<SimRequest?> = _simRequest

    /** Что сделать после выбора карты. */
    data class SimRequest(
        val displayName: String,
        val number: String,
        val sims: List<SimSlot>,
        val onChosen: (slotIndex: Int) -> Unit
    )

    fun dismissSimRequest() { _simRequest.value = null }

    /**
     * ВЫПОЛНЕНИЕ ДЕЙСТВИЯ СВАЙПА.
     *
     * Номер берётся от ПОСЛЕДНЕГО вызова в карточке: свайп по записи
     * журнала продолжает разговор, который в ней записан, а не открывает
     * выбор из всех номеров абонента.
     *
     * Карта определяется настройкой. При «спрашивать» и двух активных
     * картах поднимается диалог — но только для действий, которым карта
     * нужна: SMS отправляется приложением сообщений, и выбор карты
     * делает оно.
     */
    /**
     * @param onBeforeDialog вызывается перед показом диалога выбора SIM
     *        Нужен, чтобы строка вернулась на место: диалог перекрывает
     *        экран, и сдвинутая под ним карточка остаётся сдвинутой,
     *        когда диалог закроется.
     */
    fun performSwipe(
        action: SwipeAction,
        group: CallLogGroup,
        onBeforeDialog: () -> Unit = {}
    ) {
        val needsSim = action == SwipeAction.CALL
        val sims = state.value.sims

        if (!needsSim || sims.size <= 1) {
            handleSwipe(action, group)
            return
        }

        when (swipeSimMode.value) {
            SwipeSimMode.FIRST -> call(group.number, sims.getOrNull(0)?.slotIndex ?: -1)
            SwipeSimMode.SECOND -> call(group.number, sims.getOrNull(1)?.slotIndex ?: -1)
            SwipeSimMode.ASK -> {
                // Сначала возвращаем строку, потом показываем диалог
                onBeforeDialog()
                _simRequest.value = SimRequest(
                    displayName = group.displayName
                        ?: PhoneFormatter.format(group.number),
                    number = group.number,
                    sims = sims,
                    onChosen = { slot ->
                        call(group.number, slot)
                        _simRequest.value = null
                    }
                )
            }
        }
    }

    // ── Напоминание перезвонить ────────────────────────────────────────────

    /**
     * Запрос напоминания.
     *
     * Раньше напоминание ставилось молча на час вперёд: человек
     * не знал, сработало ли, и не мог задать нужный срок. А срок зависит
     * от дела — «освободится через пять минут» и «позвонить в банк
     * завтра» требуют разного.
     */
    /** Запись, которую свайп просит удалить, — ждёт подтверждения. */
    private val _deleteRequest = MutableStateFlow<CallLogGroup?>(null)
    val deleteRequest: StateFlow<CallLogGroup?> = _deleteRequest

    fun confirmDeleteRequest() {
        val group = _deleteRequest.value ?: return
        _deleteRequest.value = null
        // С «Отменить» (1.5.7): подтверждение и отмена вместе, как в Telegram
        deleteGroupsWithUndo(listOf(group))
    }

    fun dismissDeleteRequest() { _deleteRequest.value = null }

    private val _reminderRequest = MutableStateFlow<CallLogGroup?>(null)
    val reminderRequest: StateFlow<CallLogGroup?> = _reminderRequest

    fun requestReminder(group: CallLogGroup) { _reminderRequest.value = group }
    fun dismissReminderRequest() { _reminderRequest.value = null }

    /** Постановка напоминания с подтверждением. */
    /**
     * @param atMillis точное время срабатывания.
     *
     * Раньше сюда приходила задержка в минутах, и это ограничивало
     * сутками: у варианта «завтра в девять» задержки нет — она зависит
     * от того, который сейчас час. Теперь время выбирается в окне
     * и приходит готовым.
     */
    fun scheduleReminderAt(number: String, displayName: String?, atMillis: Long) {
        viewModelScope.launch { reminders.schedule(number, displayName, atMillis) }
        _reminderRequest.value = null

        /*
         * Подтверждение обязательно: напоминание сработает через час,
         * и без отклика человек не узнает, поставилось ли оно вообще.
         *
         * ── Про точность ───────────────────────────────────────────────────
         * Начиная с Android 12 точные будильники требуют отдельного
         * разрешения. Без него система вправе сдвинуть срабатывание
         * ради экономии батареи — иногда на десятки минут.
         *
         * Приложение при этом работает: напоминание придёт, просто
         * позже. Но человек, поставивший «через 5 минут» и получивший
         * через сорок, решит, что функция сломана. Поэтому предупреждаем
         * сразу и подсказываем, где включить точность.
         */
        val exact = CallbackReminder.canScheduleExact(app)
        notify(
            if (exact) R.string.msg_reminder_set else R.string.msg_reminder_set_inexact,
            displayName ?: PhoneFormatter.format(number),
            undo = if (exact) null else {
                { openExactAlarmSettings() }
            }
        )
    }

    // ── Сообщения пользователю ─────────────────────────────────────────────

    /**
     * ОДНОРАЗОВОЕ СООБЩЕНИЕ ДЛЯ SNACKBAR.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Действия свайпа выполняются молча: строка уехала обратно, и всё.
     * При блокировке или удалении это плохо вдвойне — человек не уверен,
     * что действие вообще сработало, и повторяет его.
     *
     * Хуже того, ошибиться свайпом легко: жест быстрый, а отменить
     * заблокированный номер или удалённую запись без подсказки негде.
     *
     * ── Почему Channel, а не StateFlow ─────────────────────────────────────
     * Сообщение должно показаться ОДИН раз. StateFlow хранит последнее
     * значение и отдаст его снова при повороте экрана или возвращении
     * из другого экрана — человек увидел бы «Номер заблокирован» ещё раз
     * через пять минут.
     *
     * Channel отдаёт значение единственному получателю и забывает его.
     */
    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /**
     * Сообщение с возможностью отмены.
     *
     * @param undo действие отмены или null, если отменять нечего
     */
    data class UserMessage(
        val textRes: Int,
        val formatArg: String? = null,
        val undo: (() -> Unit)? = null,
        /**
         * Сообщение погасло без «Отменить» (1.5.7): так отложенное
         * удаление узнаёт, что пора удалять.
         */
        val onTimeout: (() -> Unit)? = null
    )

    /** Магазина на устройстве нет — сказать об этом, а не молчать. */
    fun notifyNoStore() = notify(R.string.settings_rate_no_store)

    private fun notify(
        textRes: Int,
        arg: String? = null,
        onTimeout: (() -> Unit)? = null,
        undo: (() -> Unit)? = null
    ) {
        _messages.trySend(UserMessage(textRes, arg, undo, onTimeout))
    }

    // ── Закреплённые абоненты ──────────────────────────────────────────────

    val pinnedNumbers = prefs.pinnedNumbers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /*
     * Закрепление контакта, а не номера (1.3.2) — устройство в PinnedContacts,
     * запись и сборка списка — в PinnedController (1.3.4). Снаружи
     * по-прежнему передаётся номер: ViewModel сама находит, есть ли он в книге.
     */
    private fun lookupKeyFor(e164: String): String? =
        state.value.contacts.firstOrNull { c -> c.numbers.any { it.e164 == e164 } }?.lookupKey

    /**
     * Закрепить номер вверху журнала.
     *
     * @return false, если достигнут предел — интерфейс тогда объяснит
     *         причину, а не сделает вид, что всё получилось
     */
    fun pinNumber(e164: String): Boolean {
        val lookupKey = lookupKeyFor(e164)
        // Предел проверяется сразу: ответ нужен интерфейсу до записи
        if (PinnedContacts.pinSubscriber(pinnedNumbers.value, e164, lookupKey) == null) return false
        viewModelScope.launch {
            pinned.pin(pinnedNumbers.value, e164, lookupKey)
            /*
             * Полное обновление, а не только перестановка групп: закреплённый
             * мог не попасть в загруженную страницу журнала
             */
            refresh()
        }
        return true
    }

    fun unpinNumber(e164: String) = viewModelScope.launch {
        pinned.unpin(pinnedNumbers.value, e164, lookupKeyFor(e164))
        // Как и закрепление: обновляем всё, а не только порядок групп
        refresh()
    }

    /** Открепление из списка «Закреплённые» — по записи, а не по номеру. */
    fun unpinKey(key: String) = viewModelScope.launch {
        pinned.unpinKey(pinnedNumbers.value, key)
        refresh()
    }

    fun isPinned(e164: String): Boolean =
        PinnedContacts.isSubscriberPinned(pinnedNumbers.value, e164, lookupKeyFor(e164))

    fun movePinned(key: String, up: Boolean) = viewModelScope.launch {
        pinned.move(pinnedNumbers.value, key, up)
    }

    /*
     * Перевод старых закреплений по номеру на контакты — как только
     * загружены контакты, и после каждого их изменения (номер могли
     * добавить в книгу).
     */
    init {
        viewModelScope.launch {
            combine(
                prefs.pinnedNumbers,
                _state.map { it.contacts }.distinctUntilChanged()
            ) { pins, contacts -> pins to contacts }
                .collect { (pins, contacts) ->
                    pinned.migrateIfNeeded(pins, contacts)?.let { (before, after) ->
                        Logger.i(TAG, "Закреплённые переведены на контакты: $before → $after")
                    }
                }
        }
    }

    fun pinnedEntries(): List<PinnedEntry> =
        PinnedController.entries(pinnedNumbers.value, state.value.contacts, state.value.callLog)

    // ── Фильтры журнала ────────────────────────────────────────────────────

    private val _filter = MutableStateFlow(CallLogFilter())
    val filter: StateFlow<CallLogFilter> = _filter

    fun setFilter(value: CallLogFilter) {
        _filter.value = value
    }

    /**
     * Журнал с применённым фильтром.
     *
     * Фильтрация идёт по уже загруженным карточкам, а не запросом
     * к провайдеру: при постраничной загрузке запрос с условием
     * сбил бы смещения, и подгрузка сломалась бы.
     */
    val filteredLog: StateFlow<List<CallLogGroup>> = combine(
        state, _filter
    ) { uiState, activeFilter ->
        uiState to activeFilter
    }
        /*
         * Пропускаем повторы с одинаковым результатом.
         *
         * `state` меняется часто: при загрузке контактов, при появлении
         * данных определителя, при обновлении заметок. Фильтр журнала
         * зависит только от трёх его частей, и пересчитывать выборку
         * при изменении остальных незачем.
         *
         * distinctUntilChanged по этим трём частям отсекает лишние
         * пересчёты — а каждый из них перебирает весь журнал.
         */
        .distinctUntilChangedBy { (uiState, activeFilter) ->
            /*
             * Отметки о спаме — тоже часть ключа.
             *
             * Фильтр «Спам» отбирает по ним, и без этого после новой
             * отметки отфильтрованный журнал остался бы прежним до любого
             * другого изменения. Берётся сам набор номеров, а не размер:
             * снять одну отметку и поставить другую — размер тот же,
             * а выдача другая.
             */
            listOf(uiState.callLog, uiState.notes.size, uiState.spamMarks.keys, activeFilter)
        }
        .map { (uiState, activeFilter) ->
            /*
             * Фильтрация в фоне: перебор всех записей журнала
             * с проверкой типа, периода, SIM и принадлежности контактам.
             * На трёх тысячах записей это десятки миллисекунд, а поток
             * собирается в viewModelScope — то есть в главном потоке.
             */
            withContext(defaultDispatcher) {
                val known = uiState.contacts.flatMapTo(HashSet()) { c ->
                    c.numbers.map { it.e164 }
                }
                // Набор меток спама живёт в одном месте: продублировав его
                // здесь, мы получили бы фильтр, расходящийся с фактической
                // блокировкой
                val spam = blocking.spamNumbers(uiState.spamMarks)

                CallLogFilter.apply(
                    groups = uiState.callLog,
                    filter = activeFilter,
                    slotResolver = { simRepo.slotForAccountId(it) },
                    spamNumbers = spam,
                    knownNumbers = known,
                    // Заметки и напоминания уже загружены для строк
                    // журнала: отдельного чтения фильтр не требует
                    notedNumbers = uiState.notes.keys,
                    remindedNumbers = activeReminders.value.mapTo(HashSet()) { it.e164 }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Статистика ─────────────────────────────────────────────────────────

    /**
     * Сводка по загруженному журналу.
     * Расчёт вынесен в CallStatsCalculator — чистую функцию с тестами.
     */
    fun computeStats(): CallStatsCalculator.CallStats =
        CallStatsCalculator.calculate(state.value.callLog)

    // ── Внешние определители номера ────────────────────────────────────────

    /**
     * Провайдеры, включённые пользователем поимённо.
     * Пустой набор — состояние по умолчанию: ни один не включён.
     */
    val enabledLookupProviders: StateFlow<Set<String>> = prefs.externalLookupIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    suspend fun availableLookupProviders(): List<DirectoryProvider> =
        suspendRunCatching { directoryRepo.availableProviders() }.getOrDefault(emptyList())

    fun setLookupProvider(id: Long, enabled: Boolean) = viewModelScope.launch {
        val current = prefs.externalLookupIds.first()
        val updated = if (enabled) current + id.toString() else current - id.toString()
        prefs.setExternalLookupIds(updated)
        Logger.i("Lookup", "Внешний определитель: ${if (enabled) "включён" else "выключен"}")
    }

    // ── Резервное копирование ──────────────────────────────────────────────

    fun suggestedBackupFileName(): String = exporter.backupFileName()

    suspend fun exportBackup(uri: android.net.Uri): Boolean = exporter.exportBackup(uri)

    suspend fun writeTextToUri(uri: android.net.Uri, text: String): Boolean = exporter.writeText(uri, text)

    suspend fun buildLogText(): String? = exporter.logText()

    suspend fun importBackup(uri: android.net.Uri): Boolean {
        val ok = exporter.importBackup(uri)
        if (ok) refresh()
        return ok
    }

    // ── Правила фильтрации ─────────────────────────────────────────────────

    /** Чёрный и белый списки. Поток из базы: экран обновится сам. */
    val rules = blocking.allRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(pattern: String, kind: RuleKind, comment: String?) = viewModelScope.launch {
        when (kind) {
            RuleKind.BLOCK -> blocking.block(pattern, comment)
            RuleKind.ALLOW -> blocking.allow(pattern, comment)
        }
        // Правило меняет вид записей в журнале: пометка о блокировке
        // появляется или исчезает
        refresh()
    }

    /**
     * КОПИРОВАНИЕ НОМЕРА В БУФЕР.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ЭТО ПОНАДОБИЛОСЬ ОТДЕЛЬНО
     * ══════════════════════════════════════════════════════════════════════
     * Действие COPY_NUMBER было объявлено в списке свайпов и в шторке,
     * но обрабатывалось как `Unit` — то есть не делало ничего.
     * Человек выбирал «Копировать номер», и ничего не происходило.
     *
     * ── Зачем номер копируют ───────────────────────────────────────────────
     * Чаще не для звонка: продиктовать, вставить в заявку, отправить
     * коллеге. Без кнопки его переписывают с экрана — и там ошибаются.
     *
     * ── Про отклик ─────────────────────────────────────────────────────────
     * С Android 13 система сама показывает, что скопировано, и своё
     * сообщение стало бы вторым. На старых версиях показываем своё:
     * иначе непонятно, сработало ли.
     */
    /**
     * Написать на адрес из карточки.
     *
     * Тот же путь, что у свайпа «письмо»: один метод на обе точки входа,
     * чтобы поведение не разошлось при правках.
     */
    fun openEmail(address: String) {
        if (!actions.openEmail(address)) notify(R.string.msg_no_email_app)
    }

    /**
     * СПИСОК КАРТ СТРОКОЙ.
     *
     * Простой разделительный формат, а не JSON: полей четыре, из них
     * два числа, и тянуть разбор JSON ради этого незачем.
     *
     * Строки — метка и номер — экранируются, потому что метку задаёт
     * человек и в ней может оказаться что угодно, включая сам
     * разделитель.
     */
    private fun encodeSimCache(sims: List<SimSlot>): String =
        sims.joinToString("\n") { sim ->
            listOf(
                sim.subscriptionId.toString(),
                sim.slotIndex.toString(),
                sim.label.replace("\\", "\\\\").replace("|", "\\|"),
                sim.number.orEmpty().replace("\\", "\\\\").replace("|", "\\|")
            ).joinToString("|")
        }

    private fun parseSimCache(raw: String): List<SimSlot> {
        if (raw.isBlank()) return emptyList()

        return raw.lineSequence().mapNotNull { line ->
            val parts = splitEscaped(line)
            if (parts.size != 4) return@mapNotNull null

            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            val slot = parts[1].toIntOrNull() ?: return@mapNotNull null

            SimSlot(
                subscriptionId = id,
                slotIndex = slot,
                label = parts[2],
                number = parts[3].takeIf { it.isNotBlank() }
            )
        }.toList()
    }

    /** Разбор строки с экранированными разделителями. */
    private fun splitEscaped(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false

        line.forEach { ch ->
            when {
                escaped -> { current.append(ch); escaped = false }
                ch == '\\' -> escaped = true
                ch == '|' -> { out.add(current.toString()); current.clear() }
                else -> current.append(ch)
            }
        }
        out.add(current.toString())
        return out
    }

    /** Кто «звонит» при псевдозвонке. Пусто — случайный контакт. */
    fun setFakeCallNumber(number: String) = viewModelScope.launch {
        prefs.setFakeCallNumber(number)
    }

    /**
     * Назначенный псевдозвонок: время и имя «звонящего».
     *
     * Окно псевдозвонка показывает и то, и другое. Без времени кнопка
     * «Отменить» нажимается вслепую: человек не знает, назначено ли
     * что-нибудь вообще.
     */
    val fakeCallAt = prefs.fakeCallAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Имя или номер того, кто «позвонит». Пусто — случайный контакт. */
    val fakeCallCaller = prefs.fakeCallNumber
        .map { number ->
            if (number.isBlank()) "" else {
                suspendRunCatching { contactsRepo.lookupByNumber(number) }.getOrNull()
                    ?: PhoneFormatter.format(number)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Запомнить время назначенного псевдозвонка. */
    fun rememberFakeCallAt(atMillis: Long) = viewModelScope.launch {
        prefs.setFakeCallAt(atMillis)
    }

    fun forgetFakeCall() = viewModelScope.launch { prefs.setFakeCallAt(0L) }

    fun copyNumber(number: String) {
        runCatching {
            val clipboard = app.getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    app.getString(R.string.action_copy_number),
                    number
                )
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notify(R.string.msg_number_copied)
            }
        }.onFailure { Logger.w(TAG, "Не удалось скопировать номер", it) }
    }

    /**
     * Копирование адреса почты из карточки (1.3.2, 01.10.2026) —
     * так же, как номера: на Android 13+ система сама показывает,
     * что скопировано, поэтому своё сообщение — только до 13.
     */
    fun copyEmail(address: String) {
        runCatching {
            val clipboard = app.getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    app.getString(R.string.action_copy_email),
                    address
                )
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notify(R.string.msg_email_copied)
            }
        }.onFailure { Logger.w(TAG, "Не удалось скопировать адрес", it) }
    }

    fun removeRule(pattern: String) = viewModelScope.launch {
        // Пометка в журнале снимается вместе с правилом
        blockedCallLog.forget(pattern)
        blocking.removeRule(pattern)

        Logger.i("Block", "Блокировка снята: ${Logger.mask(pattern)}")
        notify(R.string.msg_unblocked)

        refresh()
    }

    // ── Контакты ───────────────────────────────────────────────────────────
    // Действия вынесены в ContactActionsController: там граница проходит
    // по смыслу — репозитории читают и пишут, контроллер решает, что делать

    fun toggleStar(contact: Contact) = viewModelScope.launch {
        contactActions.toggleStar(contact)
        refresh()
    }

    suspend fun isSilentContact(contactId: Long): Boolean = contactActions.isSilent(contactId)

    fun setSilentContact(contactId: Long, silent: Boolean) = viewModelScope.launch {
        contactActions.setSilent(contactId, silent)
        refresh()
    }

    fun setCustomRingtone(contactId: Long, uri: android.net.Uri?) = viewModelScope.launch {
        contactActions.setRingtone(contactId, uri)
    }

    /**
     * Удаление контакта. Разрушительно: удаляется агрегат вместе
     * с записями из всех аккаунтов. Вызывать только после подтверждения.
     */
    /** Закреплён ли контакт — для предупреждения в подтверждении удаления. */
    fun isContactPinned(contactId: Long): Boolean =
        state.value.contacts.firstOrNull { it.id == contactId }
            ?.let { PinnedController.isContactPinned(pinnedNumbers.value, it) } == true

    /*
     * Удаление контакта (1.3.4, 04.10.2026, задача автора).
     * Было: контакт удалялся из книги, а закрепление оставалось — в журнале
     * висел закреплённый «по запасному номеру» без имени и фото.
     * Стало: сначала удаление; только если оно прошло — снимаются все
     * закрепления контакта (по ключу и по каждому номеру); сообщение
     * об итоге — удаление необратимо, человек должен знать, получилось ли.
     */
    fun deleteContact(contactId: Long) = viewModelScope.launch {
        deleteContactNow(contactId)
        refresh()
    }

    private suspend fun deleteContactNow(contactId: Long): Boolean {
        val contact = state.value.contacts.firstOrNull { it.id == contactId }
        if (!contactActions.delete(contactId)) {
            notify(R.string.msg_contact_delete_failed)
            return false
        }
        if (contact != null && pinned.unpinContact(pinnedNumbers.value, contact)) {
            Logger.i(TAG, "Удалённый контакт снят с закреплённых")
        }
        notify(R.string.msg_contact_deleted)
        return true
    }

    /**
     * Фотография контакта по номеру.
     *
     * Точечный запрос к книге: кэш журнала часто пуст, а книга —
     * источник правды. Идёт по индексу PhoneLookup и занимает
     * миллисекунды.
     */
    suspend fun photoForNumber(number: String): String? =
        suspendRunCatching { contactsRepo.photoUriForNumber(number) }.getOrNull()

    /*
     * СВОЙ РЕДАКТОР КОНТАКТОВ (1.7.0).
     *
     * «Создать», «Изменить», «Добавить к контакту» больше не уходят
     * в системное приложение: на OPPO, OnePlus, realme оно закрыто, пока
     * основной — не системный «Телефон» (письмо с OPPO Reno 14), и автор:
     * «очень много запросов на редактор контакта». Здесь — только запрос
     * на переход; маршрут открывает RootScreen, сам редактор —
     * ui/contactedit. Системное приложение — пунктом в меню редактора.
     */
    sealed interface ContactEditRequest {
        data class Create(val number: String, val name: String?) : ContactEditRequest
        data class Edit(val contactId: Long) : ContactEditRequest
        /** Выбрать контакт и открыть его с этим номером в новой строке. */
        data class Attach(val number: String) : ContactEditRequest
    }

    private val _contactEditRequests = MutableSharedFlow<ContactEditRequest>(extraBufferCapacity = 2)
    val contactEditRequests: SharedFlow<ContactEditRequest> = _contactEditRequests

    fun createContact(number: String, name: String? = null) {
        _contactEditRequests.tryEmit(ContactEditRequest.Create(number, name))
    }

    fun addNumberToExistingContact(number: String) {
        _contactEditRequests.tryEmit(ContactEditRequest.Attach(number))
    }

    fun editContact(contactId: Long) {
        _contactEditRequests.tryEmit(ContactEditRequest.Edit(contactId))
    }

    /** Редактор сохранил контакт: перечитать книгу и сказать об этом. */
    fun contactSaved() {
        reloadContacts()
        notify(R.string.contact_edit_saved)
    }

    fun pinContactShortcut(name: String, number: String, e164: String): Boolean =
        contactActions.pinShortcut(name, number, e164)

    // ── Быстрый набор ──────────────────────────────────────────────────────

    val speedDial = speedDialStore.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun speedDialFor(digit: Int): SpeedDialEntry? = speedDialStore.get(digit)

    fun assignSpeedDial(digit: Int, number: String, name: String?) = viewModelScope.launch {
        speedDialStore.assign(digit, number, name)
        Logger.i("SpeedDial", "Цифра $digit назначена на ${Logger.mask(number)}")
    }

    fun clearSpeedDial(digit: Int) = viewModelScope.launch { speedDialStore.clear(digit) }

    /**
     * Долгое нажатие цифры на клавиатуре.
     *
     * @return true, если вызов начат; false — слот пуст и нужно предложить
     *         назначить контакт
     */
    suspend fun dialSpeedDial(digit: Int): Boolean {
        val entry = speedDialStore.get(digit) ?: return false
        call(entry.number)
        return true
    }

    // ── Голосовой ввод ─────────────────────────────────────────────────────

    /**
     * Обработка распознанной фразы.
     *
     * Номер набирается сразу, имя подставляется в строку поиска —
     * звонить по совпадению имени автоматически опасно: распознавание
     * ошибается, и «позвони Ане» легко превратится в «позвони Ване».
     * Пусть пользователь подтвердит нажатием.
     */
    fun handleVoiceResult(variants: List<String>) {
        when (val query = VoiceInput.pickBest(variants)) {
            is VoiceQueryParser.Query.Number -> {
                Logger.i("Voice", "Распознан номер, цифр: ${query.digits.length}")
                onQueryChange(query.digits)
            }
            is VoiceQueryParser.Query.Name -> {
                Logger.i("Voice", "Распознано имя, ищу по контактам")
                searchByName(query.text)
            }
            VoiceQueryParser.Query.Empty ->
                Logger.d("Voice", "Распознать не удалось")
        }
    }

    // ── Предупреждение перед набором ───────────────────────────────────────

    /**
     * Проверка номера до начала вызова.
     * Возвращает null, если предупреждать не о чем.
     */
    fun checkBeforeDial(number: String): PreDialWarning.Warning? {
        // Экстренный номер — без подсказок (1.3.2): «может оказаться
        // платным» под 112 неверно и отвлекает в худший момент
        if (com.ogneliss.dialer.util.EmergencyNumbers.isEmergency(app, number)) return null

        val e164 = PhoneFormatter.toE164(number)
        val known = state.value.contacts.any { c -> c.numbers.any { it.e164 == e164 } }
        val info = state.value.numberInfo[e164]
            ?: numberInfoRepo.describe(number, known)

        val warning = PreDialWarning.evaluate(
            category = info.category,
            isInContacts = known,
            country = info.country
        )
        return warning.takeIf { it.level != PreDialWarning.Level.NONE }
    }

    /** Поиск контакта по произнесённому имени через T9-индекс. */
    private fun searchByName(name: String) {
        val digits = com.ogneliss.dialer.t9.T9Engine.toDigits(name)
        if (digits.isNotEmpty()) onQueryChange(digits)
    }
}
