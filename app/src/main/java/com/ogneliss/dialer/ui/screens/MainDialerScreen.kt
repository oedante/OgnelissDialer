package com.ogneliss.dialer.ui.screens


import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.settings.ThemeMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PhoneCallback
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.NoteTeal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.ogneliss.dialer.R
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.call.FakeCall
import com.ogneliss.dialer.ui.theme.Dimens
import androidx.compose.foundation.lazy.itemsIndexed
import com.ogneliss.dialer.settings.JournalStyle
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.ui.components.ReminderTimeDialog
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.data.CallLogDateGroup
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.NumberCategory
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.OwnNumberDialog
import com.ogneliss.dialer.ui.components.GestureHintSheet
import com.ogneliss.dialer.ui.components.*
import com.ogneliss.dialer.ui.components.CallLogRowState
import com.ogneliss.dialer.ui.components.swipe.rememberSwipeRevealController
import com.ogneliss.dialer.util.DialString
import com.ogneliss.dialer.data.RulePattern
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.util.PhoneFormatter
import com.ogneliss.dialer.util.PreDialWarning
import com.ogneliss.dialer.util.VoiceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale


/**
 * ГЛАВНЫЙ ЭКРАН.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КОМПОНОВКА ПОД ОДНУ РУКУ
 * ══════════════════════════════════════════════════════════════════════════
 * ВЕРХ — карточки вызовов и результаты поиска. Их читают и по ним свайпают:
 *   свайп работает на любой высоте, потому что это движение, а не попадание
 *   в цель. Точных нажатий здесь почти нет.
 *
 * НИЗ — всё управление: клавиатура, кнопка вызова, контакты, фильтры,
 *   поиск, меню. Большой палец уверенно достаёт нижнюю треть экрана;
 *   всё выше требует перехвата телефона второй рукой.
 *
 * Раньше строка набранного номера была сверху, а фильтры — рядом с ней.
 * Это заставляло тянуться через весь экран к тому, чем пользуются чаще
 * всего.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Composable
fun MainDialerScreen(
    vm: DialerViewModel,
    prefilledNumber: String?,
    /**
     * Счётчик намерений с номером.
     *
     * Меняется на каждое новое намерение, даже если номер тот же.
     * Нужен, чтобы подстановка сработала повторно.
     */
    prefillToken: Int = 0,
    /**
     * Открыть клавиатуру без номера — «Добавить вызов» на экране вызова.
     *
     * Срабатывает вместе с prefillToken: каждое новое намерение
     * с этим признаком открывает клавиатуру, даже если перед этим
     * её закрыли.
     */
    openDialpad: Boolean = false,
    onOpenContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Экран заметок: из меню и свайпом с контактов. */
    /** Выбор того, кто «звонит»: тот же список контактов. */
    onOpenFakeCallPicker: () -> Unit,
    onOpenNotes: () -> Unit,
    /** Экран напоминаний. */
    onOpenReminders: () -> Unit,
    onOpenSubscriber: (String) -> Unit,
    /** Чёрный и белый списки: туда ведёт разбор блокировки по маске. */
    onOpenBlockLists: () -> Unit
) {
    val state by vm.state.collectAsState()
    val settings by vm.settingsState.collectAsState()

    // Правила блокировки: от них зависит подпись «заблокирован»
    // в строке журнала, поэтому экран на них подписан
    val rules by vm.rules.collectAsState()

    // Напоминания: значок в строке и фильтр «с напоминанием»
    val reminders by vm.activeReminders.collectAsState()
    val activeFilter by vm.filter.collectAsState()
    val swipeTuning by vm.swipeTuning.collectAsState()
    val rightExtra by vm.swipeRightExtra.collectAsState()
    val leftExtra by vm.swipeLeftExtra.collectAsState()
    val filteredByChips by vm.filteredLog.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current


    // ── Состояние экрана ───────────────────────────────────────────────────
    var dialpadExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.dialpadOpenAtStart) {
        if (settings.dialpadOpenAtStart) dialpadExpanded = true
    }

    /*
     * ══════════════════════════════════════════════════════════════════════
     * КЛАВИАТУРА ПРИ КАЖДОМ ОТКРЫТИИ — ПО НАСТРОЙКЕ, А НЕ ПО ПАМЯТИ
     * ══════════════════════════════════════════════════════════════════════
     * Состояние клавиатуры хранится в rememberSaveable — чтобы при повороте
     * экрана она не закрывалась посреди набора. Но у этого есть обратная
     * сторона: вышел из приложения с открытой клавиатурой — вернулся,
     * а она всё ещё открыта, и журнала почти не видно. Настройка «Открывать
     * клавиатуру при запуске» при этом выключена, то есть приложение
     * поступает не так, как попросили.
     *
     * Теперь при каждом возвращении на экран (ON_START) клавиатура приходит
     * в состояние из настройки.
     *
     * ── Исключение: номер уже набран ─────────────────────────────────────
     * Если в поле есть цифры, человек отошёл посреди набора — например,
     * скопировать номер из сообщения. Закрыть клавиатуру значило бы
     * стереть набранное (закрытие очищает поле). Поэтому с цифрами
     * клавиатура остаётся как была.
     *
     * Номер, пришедший из другого приложения, этим не затрагивается:
     * он подставляется ПОСЛЕ ON_START (сначала onStart, потом onNewIntent)
     * и открывает клавиатуру сам.
     */
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Настройку читаем через rememberUpdatedState: наблюдатель создаётся
    // один раз, и без этого он помнил бы значение на момент создания
    val openAtStart by rememberUpdatedState(settings.dialpadOpenAtStart)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START &&
                vm.state.value.query.isEmpty()
            ) {
                dialpadExpanded = openAtStart
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var menuOpen by remember { mutableStateOf(false) }

    /*
     * ══════════════════════════════════════════════════════════════════════
     * ОЧИСТКА ЖУРНАЛА — С ПОДТВЕРЖДЕНИЕМ (1.3.0)
     * ══════════════════════════════════════════════════════════════════════
     * С 1.2.1 («Ещё» — шторка) пункт «Очистить журнал» стирал журнал
     * сразу, без вопроса: окно подтверждения потерялось при переделке
     * меню. Случайное нажатие удаляло весь журнал — и в приложении,
     * и в системном журнале вызовов, — без возможности вернуть
     * (найдено пользователем 30.09.2026).
     */
    var confirmClearLog by remember { mutableStateOf(false) }
    if (confirmClearLog) {
        OgnelissDialog(
            onDismissRequest = { confirmClearLog = false },
            title = stringResource(R.string.log_clear_confirm_title),
            content = { Text(stringResource(R.string.log_clear_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearLog = false
                    vm.clearLog()
                }) {
                    Text(
                        stringResource(R.string.log_clear_confirm_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLog = false }) {
                    Text(stringResource(R.string.nav_cancel))
                }
            }
        )
    }

    // Собственный номер: нужен, чтобы делиться своей карточкой
    val ownNumber by vm.ownNumber.collectAsState()
    var showOwnNumber by remember { mutableStateOf(false) }

    if (showOwnNumber) {
        OwnNumberDialog(
            current = ownNumber,
            onDetect = { vm.detectOwnNumber() },
            onSave = { vm.setOwnNumber(it) },
            onDismiss = { showOwnNumber = false }
        )
    }
    var filtersOpen by remember { mutableStateOf(false) }
    /** Выбранный вариант напоминания: показывается окно выбора времени. */
    var pendingReminder by remember { mutableStateOf<Pair<CallLogGroup, Long>?>(null) }

    /** Открыто окно псевдозвонка. */
    var fakeCallOpen by remember { mutableStateOf(false) }

    /** Выбрано время псевдозвонка: показывается окно выбора даты и времени. */
    var fakeCallAt by remember { mutableStateOf<Long?>(null) }
    var selectedGroup by remember { mutableStateOf<CallLogGroup?>(null) }
    var pickerFor by remember { mutableStateOf<CallLogGroup?>(null) }
    var pickerOptions by remember { mutableStateOf<List<NumberOption>>(emptyList()) }
    var assigningSpeedDial by remember { mutableStateOf<Int?>(null) }
    var pendingCall by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var pendingWarning by remember { mutableStateOf<PreDialWarning.Warning?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }


    /*
     * Множественный выбор и координатор свайпов.
     *
     * Оба живут на уровне экрана, а не строки: выбранное должно
     * пережить прокрутку, а открытой может быть только одна строка —
     * иначе жест по второй оставлял бы первую сдвинутой.
     */
    val selection = rememberSelectionState<String>()
    val swipeController = rememberSwipeRevealController()


    /*
     * ПОЛОЖЕНИЕ СПИСКА ПЕРЕЖИВАЕТ УХОД С ЭКРАНА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ СПИСОК ПРЫГАЛ НАВЕРХ
     * ══════════════════════════════════════════════════════════════════════
     * rememberLazyListState помнит положение, пока экран в композиции.
     * Открывая карточку абонента, мы уходим с журнала — состояние
     * пропадает, и при возврате список оказывается в начале.
     *
     * Для человека это заметно: он прокрутил к разговору недельной
     * давности, открыл карточку, вернулся — и снова листает с самого
     * верха.
     *
     * ── Как решено ─────────────────────────────────────────────────────────
     * Положение хранится вне экрана, во ViewModel: она живёт, пока
     * жив весь экранный слой, и переход в карточку её не задевает.
     *
     * ── Почему не rememberSaveable ─────────────────────────────────────────
     * Он спасает от поворота экрана, но не от смены места назначения:
     * при уходе на другой экран запись всё равно теряется.
     */
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = vm.logScrollIndex,
        initialFirstVisibleItemScrollOffset = vm.logScrollOffset
    )

    /*
     * Сохранение при уходе.
     *
     * DisposableEffect срабатывает, когда экран покидает композицию, —
     * то есть ровно в момент перехода в карточку или настройки.
     */
    DisposableEffect(Unit) {
        onDispose {
            vm.rememberLogScroll(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }


    /*
     * СИСТЕМНАЯ КНОПКА «НАЗАД» ВОЗВРАЩАЕТ К НАЧАЛУ СПИСКА.
     *
     * Журнал — начальный экран: выходить из приложения по «назад»
     * отсюда правильно, но только когда список в начале. Если человек
     * прокрутил на неделю назад, первое нажатие возвращает наверх,
     * а второе уже закрывает приложение.
     *
     * Обработчик включён только в прокрутанном состоянии: иначе он
     * перехватывал бы выход из приложения всегда.
     */
    val canScrollUp by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val scrollScope = rememberCoroutineScope()
    BackHandler(enabled = canScrollUp) {
        scrollScope.launch { listState.animateScrollToItem(0) }
    }


    /*
     * Признак прокрутки для свайпа.
     *
     * derivedStateOf, а не прямая подписка: isScrollInProgress меняется
     * дважды за жест, и чтение его в теле экрана перекомпоновывало бы
     * весь список на каждом начале и конце прокрутки.
     */
    val listScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }


    val list = when {
        state.query.isNotEmpty() -> state.filteredLog
        activeFilter.isActive -> filteredByChips
        else -> state.callLog
    }


    // ── Формат даты, зависящий от локали ───────────────────────────────────
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
    val dateFormat = remember(locale) { SimpleDateFormat("d MMM, HH:mm", locale) }


    val infoLabels = rememberNumberInfoLabels()
    val simSlots = remember(state.sims) {
        state.sims.associate { it.subscriptionId.toString() to it.slotIndex }
    }
    val knownNumbers = remember(state.contacts) {
        state.contacts.flatMapTo(HashSet()) { c -> c.numbers.map { it.e164 } }
    }


    /*
     * Фотографии контактов по номеру.
     *
     * Порядок при дублях тот же, что при выборе имени: первый
     * по алфавиту. Иначе карточка могла показать имя одного контакта
     * с фотографией другого — «Иванов» с логотипом такси.
     */
    val contactPhotos = remember(state.contacts) {
        buildMap<String, String> {
            state.contacts
                .sortedBy { it.displayName.lowercase() }
                .forEach { contact ->
                    val photo = contact.photoUri ?: return@forEach
                    contact.numbers.forEach { number ->
                        putIfAbsent(number.e164, photo)
                    }
                }
        }
    }


    /*
     * ОБНОВЛЕНИЕ ПРИ ОТКРЫТИИ ЖУРНАЛА.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Возврат из контактов, из карточки абонента, из настроек — всё это
     * не проходит через onReturnedToForeground: приложение с переднего
     * плана не уходило.
     *
     * А журнал за это время мог измениться: из карточки удалили запись,
     * из контактов позвонили. Без чтения человек возвращается к тому,
     * что уже неверно.
     *
     * Стоимость — одно чтение страницы при переходе между экранами.
     */
    LaunchedEffect(Unit) { vm.refresh() }

    val allRows by vm.rows.collectAsState()

    /*
     * СТРОКИ, КОТОРЫЕ ДЕЙСТВИТЕЛЬНО ПОКАЗЫВАЮТСЯ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ФИЛЬТРЫ НЕ РАБОТАЛИ
     * ══════════════════════════════════════════════════════════════════════
     * Фильтр применялся к `list`, а на экран шли `rows` — полный набор
     * из модели. Отбор происходил и никуда не девался.
     *
     * Выглядело это так: пустое состояние появлялось сверху (потому
     * что отфильтрованный `list` пуст), а под ним оставался прежний
     * список целиком. Человек видел одновременно «звонков нет»
     * и сами звонки.
     *
     * Здесь строки отбираются по тем же ключам, что и список: у записи
     * журнала и у строки один ключ, и сопоставление точное.
     */
    /*
     * ФИЛЬТР ВХОДИТ В КЛЮЧ ПЕРЕСЧЁТА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ СБРОС НЕ РАБОТАЛ
     * ══════════════════════════════════════════════════════════════════════
     * Ключи были только allRows и list. При сбросе фильтра меняется
     * activeFilter, а list пересчитывается своим потоком — с задержкой
     * в кадр.
     *
     * В этот момент remember видел прежние allRows и прежний list,
     * ключ не менялся — и возвращал старый отбор. Дальше list приходил
     * новый, но пересчёт уже не запускался: ключ снова совпадал.
     *
     * Список так и оставался отфильтрованным до перезапуска приложения.
     *
     * ── Почему добавление фильтра решает ───────────────────────────────────
     * activeFilter меняется в тот же миг, что и нажатие. Ключ становится
     * другим сразу, и ветка «фильтра нет» отдаёт полный список,
     * не дожидаясь потока.
     */
    val rows = remember(allRows, list, activeFilter, state.query) {
        if (!activeFilter.isActive && state.query.isEmpty()) {
            allRows
        } else {
            val visible = list.mapTo(HashSet()) { it.key }
            allRows.filter { it.key in visible }
        }
    }

    /*
     * ВОССТАНОВЛЕНИЕ, КОГДА СТРОКИ УЖЕ ПРИШЛИ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ОДНОГО initialFirstVisibleItemIndex НЕ ХВАТИЛО
     * ══════════════════════════════════════════════════════════════════════
     * Начальное положение применяется в момент первого измерения списка.
     * А при возврате из карточки список в этот момент ещё пуст: строки
     * собираются в фоне и приходят кадром позже. Пустому списку нечего
     * прокручивать, положение обрезается до нуля — и человек снова
     * оказывается в начале, хотя значение во ViewModel сохранилось
     * правильно.
     *
     * Поэтому положение восстанавливается ещё раз, когда строки
     * появились.
     *
     * ── Один раз за заход на экран ─────────────────────────────────────────
     * Признак живёт в remember: при возврате композиция создаётся заново,
     * и восстановление срабатывает снова. Но пока человек остаётся
     * на экране, список больше не дёргается — иначе любое обновление
     * журнала (новый звонок) возвращало бы его к сохранённой позиции.
     */



    /*
     * Подсказки прямого действия: иконка и цвет под строкой.
     *
     * Зависят только от настроек жестов, поэтому считаются один раз
     * на экран, а не для каждой строки — иначе одна и та же работа
     * повторялась бы десятки раз при прокрутке.
     */
    val startHint = remember(settings.swipeRight) {
        vm.directActionAppearance(settings.swipeRight)
    }
    val endHint = remember(settings.swipeLeft) {
        vm.directActionAppearance(settings.swipeLeft)
    }


    /*
     * Найденные контакты как строки журнала.
     *
     * Контакт превращается в CallLogGroup: у него есть номер, имя
     * и фотография — всё, что нужно карточке. Счётчики нулевые,
     * и карточка их не показывает.
     *
     * Так найденный контакт получает тот же свайп, что и запись
     * журнала: поиск нужен, чтобы быстро позвонить, и жест должен
     * работать одинаково.
     */
    val contactRows = remember(state.filteredContacts, contactPhotos, state.query) {
        state.filteredContacts.mapNotNull { contact ->
            val primary = contact.numbers.firstOrNull() ?: return@mapNotNull null
            CallLogRowState(
                key = "contact:${contact.id}",
                number = primary.value,
                e164 = primary.e164,
                contactId = contact.id,
                title = contact.displayName,
                formattedNumber = PhoneFormatter.format(primary.value),
                dateLabel = "",
                noteLine = null,
                titleFromNote = false,
                infoLabel = null,
                infoIsWarning = false,
                photoUri = contact.photoUri,
                lastType = CallType.INCOMING,
                count = 0,
                incomingCount = 0,
                outgoingCount = 0,
                missedCount = 0,
                hasMissed = false,
                simLabel = null,
                simSlotIndex = null,
                numbersCount = contact.numbers.size,
                accessibilityLabel = contact.displayName,
                signature = contact.id.hashCode(),
                group = CallLogGroup(
                    key = "contact:${contact.id}",
                    lastCallId = -1,
                    number = primary.value,
                    e164 = primary.e164,
                    displayName = contact.displayName,
                    photoUri = contact.photoUri,
                    contactId = contact.id,
                    lastType = CallType.INCOMING,
                    lastDate = 0,
                    durationSec = 0,
                    count = 0,
                    incomingCount = 0,
                    outgoingCount = 0,
                    missedCount = 0,
                    rejectedCount = 0,
                    totalDurationSec = 0,
                    numbers = contact.numbers.map { it.value },
                    subscriptionLabel = null,
                    subscriptionId = null
                )
            )
        }
    }


    val gestureHintShown by vm.gestureHintShown.collectAsState()
    if (!gestureHintShown && list.isNotEmpty()) {
        GestureHintSheet(onDismiss = { vm.markGestureHintShown() })
    }


    val pinnedNumbers by vm.pinnedNumbers.collectAsState()


    /*
     * Закреплённые вынесены в отдельную группу над датами.
     *
     * Ключи remember выбраны так, чтобы пересчёт шёл при настоящих
     * изменениях: размер списка, первая запись, состав закреплённых
     * и запрос. Ссылка на сам список для этого не годится — она
     * меняется при каждом обновлении журнала.
     *
     * При поиске закреплённые не показываются: человек ищет
     * конкретного абонента, и лишняя группа сверху ему мешает.
     */
    // Ключ — сами строки, а не их число и первая: иначе изменение записи
    // в середине журнала (ключ карточки меняется после каждого звонка)
    // оставляло здесь старые строки со старыми ключами
    /*
     * ══════════════════════════════════════════════════════════════════════
     * ЗАКРЕПЛЁННЫЕ — ПО КОНТАКТУ, А НЕ ТОЛЬКО ПО НОМЕРУ (1.2.4)
     * ══════════════════════════════════════════════════════════════════════
     * Закрепляется номер. Но группа журнала у контакта одна на все его
     * номера, и её «номер» — номер последнего звонка. Позвонили с другого
     * номера того же контакта — группа сменила номер, перестала совпадать
     * с закреплённым, и закреплённая карточка превращалась в «без звонков»:
     * без портрета, а нажатие открывало карточку по голому номеру, которую
     * карточка не умела искать, — и тут же закрывалась (лог 30.09.2026:
     * «Группа не найдена» на каждое нажатие).
     *
     * Теперь строка закреплена, если её номер закреплён ИЛИ закреплён любой
     * другой номер того же контакта. Порядок — по первому закреплённому
     * номеру контакта; на каждый закреплённый номер — одна строка.
     */
    val pinOrder = remember(pinnedNumbers) {
        pinnedNumbers.withIndex().associate { (i, n) -> n to i }
    }
    val contactPinIndex = remember(pinOrder, state.contacts) {
        buildMap<Long, Int> {
            state.contacts.forEach { contact ->
                contact.numbers.mapNotNull { pinOrder[it.e164] }.minOrNull()
                    ?.let { put(contact.id, it) }
            }
        }
    }
    fun pinIndexOf(row: CallLogRowState): Int? =
        pinOrder[row.e164] ?: row.group.contactId?.let { contactPinIndex[it] }

    val pinnedRows = remember(rows, pinOrder, contactPinIndex, state.query) {
        if (pinOrder.isEmpty() || state.query.isNotEmpty()) emptyList()
        else {
            rows.mapNotNull { row -> pinIndexOf(row)?.let { it to row } }
                // Строки идут от новых к старым: у каждого закреплённого
                // остаётся самая свежая
                .distinctBy { it.first }
                .sortedBy { it.first }
                .map { it.second }
        }
    }

    /*
     * ЗАКРЕПЛЁННЫЕ, ПО КОТОРЫМ НЕ БЫЛО ЗВОНКОВ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЧТО БЫЛО НЕ ТАК
     * ══════════════════════════════════════════════════════════════════════
     * Закреплённые собирались фильтрацией строк журнала. Значит номер,
     * по которому ещё не звонили, не появлялся вовсе: в настройках
     * он числился закреплённым, а в журнале его не было.
     *
     * Заметнее всего это на городских номерах из книги — их закрепляют
     * заранее, до первого звонка, ровно ради того, чтобы не искать
     * потом. То есть функция не работала именно в том случае, ради
     * которого её и открывают.
     *
     * ── Как теперь ─────────────────────────────────────────────────────────
     * Для закреплённых без истории строка собирается из книги: имя,
     * фотография, номер. Позвонить по ней можно, а истории в ней нет —
     * потому что её и правда нет.
     */
    val pinnedWithoutCalls = remember(pinnedNumbers, pinnedRows, state.contacts, state.query) {
        if (pinnedNumbers.isEmpty() || state.query.isNotEmpty()) emptyList()
        else {
            // «Со звонками» — закреплённые, для которых нашлась строка:
            // по своему номеру или по другому номеру того же контакта
            val coveredIndexes = pinnedRows.mapNotNullTo(HashSet()) { pinIndexOf(it) }
            val withCalls = pinnedNumbers.filterIndexed { i, _ -> i in coveredIndexes }.toHashSet()
            val byNumber = buildMap {
                state.contacts.forEach { contact ->
                    contact.numbers.forEach { number ->
                        putIfAbsent(number.e164, contact)
                    }
                }
            }

            pinnedNumbers.filterNot { it in withCalls }.map { e164 ->
                val contact = byNumber[e164]
                PinnedOnlyRow(
                    e164 = e164,
                    title = contact?.displayName ?: PhoneFormatter.format(e164),
                    photoUri = contact?.photoUri
                )
            }
        }
    }


    val unpinnedRows = remember(rows, pinnedRows) {
        if (pinnedRows.isEmpty()) rows
        else {
            val pinnedKeys = pinnedRows.mapTo(HashSet()) { it.key }
            rows.filterNot { it.key in pinnedKeys }
        }
    }


    /*
     * Разбивка по датам считается в фоне.
     *
     * На тысяче записей она занимает десятки миллисекунд — в главном
     * потоке это заметный рывок при каждом обновлении журнала.
     */
    val dateGroups by produceState(
        initialValue = emptyList<Pair<CallLogDateGroup, List<CallLogRowState>>>(),
        unpinnedRows
    ) {
        value = withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            unpinnedRows.groupBy { CallLogDateGroup.of(it.group.lastDate, now) }
                .toList()
                .sortedBy { it.first.ordinal }
        }
    }

    /*
     * ВОССТАНОВЛЕНИЕ ЖДЁТ ИМЕННО ТО, ЧТО РИСУЕТСЯ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ПРОШЛАЯ ПОПЫТКА НЕ ПОМОГЛА
     * ══════════════════════════════════════════════════════════════════════
     * Первый раз положение восстанавливалось по начальному значению
     * LazyListState — не сработало, потому что при возврате список ещё
     * пуст. Второй раз стало ждать появления rows — тоже мало: rows
     * это плоский список строк, а LazyColumn рисует dateGroups —
     * строки, разложенные по датам, с заголовками между ними.
     * Собирается это в фоне и приходит ещё кадром позже.
     *
     * То есть индекс применялся к списку, в котором нужного элемента
     * ещё нет, и снова обрезался до нуля.
     *
     * В контактах ровно эта правка уже сделана и работает — там ждём
     * grouped. Здесь ждём dateGroups.
     *
     * ⚠️ Признак того, что впредь ждать надо именно нарисованное:
     * промежуточные списки готовы раньше, и по ним восстанавливать
     * положение нельзя.
     */
    var scrollRestored by remember { mutableStateOf(false) }
    LaunchedEffect(dateGroups.isEmpty()) {
        if (!scrollRestored && dateGroups.isNotEmpty()) {
            scrollRestored = true
            if (vm.logScrollIndex > 0 || vm.logScrollOffset > 0) {
                runCatching {
                    listState.scrollToItem(vm.logScrollIndex, vm.logScrollOffset)
                }
            }
        }
    }


    /*
     * Ключ пересборки строк — целое число, а не строка.
     *
     * Строковый вариант собирался заново при каждой перерисовке экрана:
     * несколько подстановок и выделение памяти под результат, который
     * тут же отбрасывался как неизменившийся.
     *
     * derivedStateOf вдобавок сообщает об изменении, только когда
     * меняется результат, а не при каждом чтении зависимостей.
     */
    /*
     * ══════════════════════════════════════════════════════════════════════
     * ОШИБКА 1.0 — 1.2.2: КЛЮЧ СМОТРЕЛ НА СПИСОК ПЕРВОГО ПОКАЗА ЭКРАНА
     * ══════════════════════════════════════════════════════════════════════
     * Здесь стоял `list` — обычная переменная этой функции. derivedStateOf
     * внутри remember { } создаётся один раз и запоминает `list` таким,
     * каким он был при первом показе экрана, — и дальше считал размер
     * и первую запись ЭТОГО, давно устаревшего списка.
     *
     * Пересборка строк случалась, только если менялось что-то ещё
     * из ключа: число заметок, контактов, сведений о номерах. После
     * обычного звонка ничего из этого не менялось — строки журнала
     * оставались старыми, со старыми ключами карточек. А ключ карточки
     * включает номер последней записи вызова («абонент#id»), и после
     * звонка он другой. Нажатие открывало карточку по старому ключу,
     * карточка не находила такой группы и тут же закрывалась — со стороны
     * «ничего не происходит». Сообщено пользователем по логу 30.09.2026.
     *
     * Теперь список берётся через rememberUpdatedState — всегда текущий,
     * а в ключ входят ключи всех групп, а не только первой: слияние
     * вызовов в середине журнала тоже меняет ключ.
     */
    val currentList by rememberUpdatedState(list)
    val rowsKey by remember {
        derivedStateOf {
            var h = currentList.size
            h = 31 * h + currentList.fold(0) { acc, g -> 31 * acc + g.key.hashCode() }

            /*
             * ПОИСКОВЫЙ ЗАПРОС — ЧАСТЬ КЛЮЧА.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ СПИСОК ОСТАВАЛСЯ ОТФИЛЬТРОВАННЫМ
             * ══════════════════════════════════════════════════════════════
             * Строки собираются из `list`, а он при непустом запросе —
             * это результат поиска. Ключ считался по длине списка
             * и ключу первой записи: если после очистки запроса
             * длина совпала с длиной выдачи, а первая запись осталась
             * той же — а так и бывает, когда искали свежий номер, —
             * ключ не менялся. Пересборка не запускалась, и на экране
             * висел список из поиска при пустом поле ввода.
             *
             * Длина запроса делает ключ разным для «искали» и «не ищем»
             * в любом случае.
             */
            h = 31 * h + state.query.length
            h = 31 * h + state.numberInfo.size
            h = 31 * h + state.notes.size
            h = 31 * h + state.contacts.size

            /*
             * Фотографии и группировка — тоже часть ключа.
             *
             * ── Почему размера контактов мало ──────────────────────────────
             * Книга читается параллельно с журналом. Когда она приходит,
             * СОСТАВ контактов не меняется — их число то же, что было
             * при прошлом чтении. Ключ оставался прежним, пересборка
             * не запускалась, и строки жили без фотографий и без общей
             * группировки по номерам одного человека.
             *
             * Появлялось это только после захода в контакты: там свой
             * пересчёт с другими ключами.
             *
             * Карта фотографий отражает и наличие книги, и её содержимое:
             * пока контактов нет, она пуста, и любое их появление
             * меняет ключ.
             */
            h = 31 * h + contactPhotos.size
            h = 31 * h + state.sims.size
            /*
             * Режим показа оператора — тоже часть ключа.
             *
             * Иначе переключение настройки «Оператор и регион»
             * не перестраивало бы уже собранные строки: список
             * менялся бы только после следующего звонка.
             */
            h = 31 * h + settings.carrierInfo.ordinal

            /*
             * ПРАВИЛА БЛОКИРОВКИ — ТОЖЕ ЧАСТЬ КЛЮЧА.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ ПОМЕТКА ПОЯВЛЯЛАСЬ ТОЛЬКО ПОСЛЕ ПЕРЕОТКРЫТИЯ
             * ══════════════════════════════════════════════════════════════
             * Блокировка меняет правила, но не журнал: те же записи,
             * те же даты, то же число контактов. Ключ оставался прежним,
             * пересборка строк не запускалась, и подпись «заблокирован»
             * не появлялась, пока экран не пересоздадут.
             *
             * Для номера из книги это иногда срабатывало с первого раза —
             * но не потому, что работало: просто чтение контактов после
             * refresh() успевало поменять карту фотографий, и ключ менялся
             * заодно. У номера не из книги меняться было нечему, отсюда
             * и «со второго раза».
             *
             * Считаем по количеству правил и содержимому: удаление одного
             * правила и добавление другого оставляют количество прежним.
             */
            /*
             * ПРАВИЛА И НАПОМИНАНИЯ — ПО СОДЕРЖИМОМУ, А НЕ ПО КОЛИЧЕСТВУ.
             *
             * Размера мало: заменил одно правило другим — количество
             * то же, ключ прежний, строка берётся из прошлого списка
             * без пометки. С напоминанием ещё хуже: перенёс его
             * на другое время, и количество не менялось вовсе.
             *
             * Считаем по содержимому: шаблон и вид правила, номер
             * и время срабатывания.
             */
            h = 31 * h + rules.sumOf { it.pattern.hashCode() + it.kind.hashCode() }
            h = 31 * h + reminders.sumOf { it.e164.hashCode() + it.atMillis.hashCode() }
            h
        }
    }


    LaunchedEffect(rowsKey) {
        vm.rebuildRows(list, infoLabels, dateFormat)
    }

    val blockedLabelText = stringResource(R.string.blocked_by_rule)
    /*
     * Мошенники важнее спама: если стоят обе метки, показывается эта.
     *
     * С заглавной: в заметке метки пишутся строчными, как фишки
     * («спам»), а в строке журнала это начало фразы — строчная буква
     * там читается как обрезанный текст.
     */
    val fraudLabelText = stringResource(R.string.tag_fraud)
        .replaceFirstChar { it.titlecase() }
    val spamLabelText = stringResource(R.string.tag_spam)
        .replaceFirstChar { it.titlecase() }

    /*
     * ПОМЕТКИ БЛОКИРОВКИ И НАПОМИНАНИЯ — ПРИ ОТРИСОВКЕ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НЕ ЧЕРЕЗ ПЕРЕСБОРКУ СТРОК
     * ══════════════════════════════════════════════════════════════════════
     * Обе пометки лежали внутри готовой строки, а строки собираются
     * в фоне по ключу. Значит между «человек нажал заблокировать»
     * и «пометка появилась» стоит цепочка: правило записалось →
     * обновился поток правил → изменился ключ → перезапустился эффект →
     * пересборка дошла до конца → строки заменились.
     *
     * Любое звено этой цепочки, задержавшееся на секунду, выглядит
     * как «не работает». Мы уже чинили её дважды — добавляли правила
     * в ключ, считали хеш по содержимому, — и каждый раз оставалась
     * задержка в непредсказуемый момент.
     *
     * Здесь цепочки нет: наборы заблокированных и напоминаний приходят
     * из потоков напрямую, и пометка считается в момент отрисовки
     * строки. Поток эмитит — Compose перерисовывает. Пересборка строк
     * к этому больше не причастна.
     *
     * ── Цена ───────────────────────────────────────────────────────────────
     * Копия объекта строки на каждую видимую запись при изменении
     * наборов. Записей на экране десяток, копия — это десяток ссылок;
     * дешевле, чем повторная сборка всего списка.
     */
    val blockedByRule = remember(rules, reminders) {
        rules.filter { it.isBlock }
    }

    val liveRow: (CallLogRowState) -> CallLogRowState = { row ->
        val blocked = blockedByRule.any { rule ->
            if (rule.isMask) RulePattern.matches(rule.pattern, row.e164)
            else rule.pattern == row.e164
        }

        val hasReminder = reminders.any { it.e164 == row.e164 }

        /*
         * ══════════════════════════════════════════════════════════════════
         * МЕТКА «СПАМ» ВИДНА В ЖУРНАЛЕ, НО НЕ ВЫДАЁТ СЕБЯ ЗА БЛОКИРОВКУ
         * ══════════════════════════════════════════════════════════════════
         * Метку ставят, чтобы в следующий раз узнать номер ещё до ответа.
         * Раньше она жила только в карточке: в журнале номер с меткой
         * «спам» ничем не отличался от соседей, и узнать его можно было,
         * только открыв.
         *
         * ⚠️ Метка — не блокировка, и показывать её надо иначе. Номер
         * с меткой звонит как обычно; если нарисовать у него значок
         * блокировки, человек решит, что звонки отрезаны, и пропустит
         * настоящий. Поэтому своя строка со значком предупреждения,
         * а не общая с пометкой «Заблокирован».
         *
         * Когда номер и помечен, и заблокирован, показывается только
         * блокировка: она сильнее и уже сообщает главное.
         */
        // Отметка из своего хранилища, а не из заметки: с 1.1.1 это
        // разные вещи — разбор в SpamMarkEntity
        val mark = state.spamMarks[row.e164]?.type
        val dangerTag = mark != null

        /*
         * ПЕРЕКЛЮЧАТЕЛЬ «БЛОКИРОВАТЬ ПОМЕЧЕННЫЕ» РЕШАЕТ, ЧТО ПОКАЗАТЬ.
         *
         * Включён — номер с меткой действительно блокируется: SpamPolicy
         * отклоняет его до звонка (правило 5, USER_TAG). Значит в журнале
         * он должен выглядеть заблокированным, а не «помеченным»:
         * иначе человек видит предупреждение и думает, что звонки
         * проходят.
         *
         * Выключен — звонки проходят, и строка предупреждает меткой.
         * Это тот же расчёт, что в SpamPolicy, и держать его надо
         * в согласии с ним: разойдутся — журнал начнёт врать о том,
         * что происходит со звонками.
         */
        val blockedByTag = dangerTag && settings.blockTaggedSpam
        val isBlocked = blocked || blockedByTag

        val warning = when {
            isBlocked || row.blockedLabel != null -> null
            mark == SpamMarkType.FRAUD -> fraudLabelText
            mark == SpamMarkType.SPAM -> spamLabelText
            else -> null
        }

        if (isBlocked == (row.blockedLabel != null) &&
            hasReminder == row.hasReminder &&
            warning == row.warningTag
        ) {
            row
        } else {
            row.copy(
                // Подпись из журнала заблокированных важнее: там указана
                // причина. Правило даёт общую пометку, когда причины нет
                blockedLabel = row.blockedLabel ?: blockedLabelText.takeIf { isBlocked },
                hasReminder = hasReminder,
                warningTag = warning
            )
        }
    }


    val pauseLabel = stringResource(R.string.dial_pause)
    val waitLabel = stringResource(R.string.dial_wait)


    // ── Голосовой ввод ─────────────────────────────────────────────────────
    val voiceAvailable = remember { VoiceInput.isAvailable(context) }
    val voicePrompt = stringResource(R.string.voice_prompt)
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> vm.handleVoiceResult(VoiceInput.extractResults(result.data)) }


    /*
     * НОМЕР ИЗ ДРУГОГО ПРИЛОЖЕНИЯ.
     *
     * Клавиатура раскрывается принудительно, даже если в настройках
     * она закрыта при запуске: человек нажал «Позвонить» в сообщении,
     * и показать ему журнал вместо набранного номера — значит потерять
     * то, ради чего он пришёл.
     *
     * ⚠️ Ключ включает и сам номер, и счётчик намерений: если нажать
     * «Позвонить» на ТОМ ЖЕ номере второй раз, номер не изменится,
     * и без счётчика эффект бы не перезапустился — а поле к тому
     * времени человек мог уже очистить.
     */
    LaunchedEffect(openDialpad, prefillToken) {
        if (openDialpad) dialpadExpanded = true
    }

    LaunchedEffect(prefilledNumber, prefillToken) {
        prefilledNumber?.takeIf { it.isNotBlank() }?.let {
            vm.onQueryChange(it)
            dialpadExpanded = true
        }
    }


    LaunchedEffect(state.query) {
        if (listState.firstVisibleItemIndex > 0) {
            listState.animateScrollToItem(0)
        }
    }


    val scrollToTop by vm.scrollToTop.collectAsState()
    LaunchedEffect(scrollToTop, rows.size) {
        if (scrollToTop && rows.isNotEmpty()) {
            if (listState.firstVisibleItemIndex > 0) listState.animateScrollToItem(0)
            vm.onScrolledToTop()
        }
    }


    LaunchedEffect(activeFilter) {
        if (listState.firstVisibleItemIndex > 0) {
            listState.animateScrollToItem(0)
        }
    }


    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { if (it) swipeController.close() }
    }


    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }


    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.query.isEmpty()) vm.loadMore()
    }


    BackHandler(
        enabled = selection.isActive || state.query.isNotEmpty() || dialpadExpanded
    ) {
        when {
            selection.isActive -> selection.clear()
            // Сначала очищаем набранное, потом закрываем клавиатуру:
            // два нажатия «назад» вместо одного, зато шаги обратимы
            state.query.isNotEmpty() -> vm.onQueryChange("")
            else -> dialpadExpanded = false
        }
    }




    /*
     * ══════════════════════════════════════════════════════════════════════
     * КЛАВИАТУРА СБОКУ НА ШИРОКОМ ОКНЕ
     * ══════════════════════════════════════════════════════════════════════
     * На телефоне лёжа открытая клавиатура занимала почти всю высоту
     * экрана: журнала над ней не было видно вовсе. А ведь набирают номер
     * чаще всего, глядя в журнал, — перезвонить тому, кто звонил.
     *
     * Теперь на любом окне шире телефона (телефон лёжа, раскладушка,
     * планшет — разбор в WindowLayout) открытая клавиатура встаёт справа
     * колонкой фиксированной ширины, а журнал занимает остальное.
     *
     * Закрытая — остаётся внизу рядом значков, как на телефоне: она
     * низкая и места не отнимает.
     *
     * ── Почему справа ─────────────────────────────────────────────────────
     * Большинство держит телефон лёжа двумя руками и набирает большим
     * пальцем правой — клавиши под ним. Журнал слева читается как начало
     * строки: сначала кто звонил, потом набор.
     *
     * ── Высота клавиш ─────────────────────────────────────────────────────
     * По высоте у телефона лёжа ~360 dp на всё: строку номера, четыре ряда
     * клавиш, кнопки вызова. С обычными 60 dp клавиатура не влезает.
     * Ряды клавиш делят между собой всё, что осталось, — разбор
     * в Dialpad.fillHeight.
     */
    val windowLayout = LocalWindowLayout.current

    /*
     * Сбоку — когда журналу после клавиатуры остаётся хотя бы ширина
     * телефона. Телефон лёжа — всегда: у него беда не с шириной,
     * а с высотой. Раскрытая раскладушка в портрете (~673 dp) — нет:
     * журналу осталось бы ~310 dp, уже самого узкого телефона, а высоты
     * у неё хватает и на клавиатуру снизу.
     */
    val sideDialpad = dialpadExpanded && windowLayout.hasRoomForSidePanel
    /*
     * Телефон лёжа: панель журнала — вертикальной полосой справа, а не
     * внизу (1.2.2). Разбор — у ActionRail в BottomControlPanel.
     * Только телефон: на планшете слева полоса навигации приложения,
     * и вторая полоса справа была бы лишней.
     */
    val landscapeRail = windowLayout.mode == com.ogneliss.dialer.ui.adaptive.LayoutMode.PHONE_LANDSCAPE &&
        !com.ogneliss.dialer.ui.adaptive.LocalNavigationRailShown.current

    val controlPanel: @Composable (Modifier, Boolean, Dp?, Boolean) -> Unit = { panelModifier, side, keyHeight, rail ->
        BottomControlPanel(
            modifier = panelModifier,
            side = side,
            rail = rail,
            dialKeyHeight = keyHeight,
            expanded = dialpadExpanded,
            query = state.query,
            /*
             * Заметка о набранном номере.
             *
             * Ищется по нормализованному номеру: в заметках ключ —
             * E.164, а в поле набора человек печатает как удобно,
             * с восьмёркой или без.
             *
             * remember по запросу и карте заметок: нормализация
             * номера идёт через libphonenumber, и делать её
             * на каждое нажатие клавиши незачем.
             */
            noteHint = remember(state.query, state.notes) {
                state.query.takeIf { it.length >= 4 }
                    ?.let { state.notes[PhoneFormatter.toE164(it)]?.note }
            },
            sims = state.sims,
            filter = activeFilter,
            voiceAvailable = voiceAvailable,
            pauseLabel = pauseLabel,
            waitLabel = waitLabel,
            /*
             * ЗАКРЫТИЕ КЛАВИАТУРЫ ОЧИЩАЕТ НАБРАННОЕ.
             *
             * ══════════════════════════════════════════════════════════
             * ПОЧЕМУ
             * ══════════════════════════════════════════════════════════
             * Набранные цифры фильтруют журнал: набрал «918» — видны
             * только звонки с таких номеров. Пока клавиатура открыта,
             * это очевидно: цифры на виду.
             *
             * Свернул клавиатуру — цифры исчезли вместе с ней,
             * а список остался отфильтрованным. Человек видит журнал,
             * в котором половины звонков нет, и причины этому
             * на экране не осталось.
             *
             * Поэтому закрытие клавиатуры очищает и поле, и фильтр
             * по нему. Открытие — нет: там очищать нечего.
             */
            onToggleExpanded = {
                if (dialpadExpanded && state.query.isNotEmpty()) vm.onQueryChange("")
                dialpadExpanded = !dialpadExpanded
            },
            onDigit = { vm.onQueryChange(state.query + it) },
            onLongDigit = { digit ->
                scope.launch { handleLongDigit(vm, state.query, digit) { slot ->
                    assigningSpeedDial = slot
                } }
            },
            onBackspace = { vm.onQueryChange(state.query.dropLast(1)) },
            onClear = { vm.onQueryChange("") },
            onCall = { slot ->
                state.query.takeIf { it.isNotEmpty() }?.let { number ->
                    val warning = vm.checkBeforeDial(number)
                    if (warning != null && PreDialWarning.requiresConfirmation(warning)) {
                        pendingCall = number to slot
                        pendingWarning = warning
                    } else vm.callFromDialpad(number, slot)
                }
            },
            onAddContact = { vm.createContact(it) },
            // Вставка из буфера: номер попадает в поле набора,
            // как будто его набрали руками — дальше работают
            // и подсказка с заметкой, и предупреждение перед вызовом
            onPaste = { vm.onQueryChange(it) },
            onContacts = onOpenContacts,
            onFilters = { filtersOpen = true },
            onVoice = { voiceLauncher.launch(VoiceInput.createIntent(context, voicePrompt)) },
            onStartSelection = {
                if (selection.isActive) selection.clear() else selection.startEmpty()
            },
            selectionActive = selection.isActive,
            // Напоминания — с панели, а не только из меню:
            // разбор в ActionRow
            onReminders = onOpenReminders,
            onMenu = { menuOpen = true },
            onSwipeToContacts = if (settings.panelSwipeNav) onOpenContacts else null,
            menuContent = {
                OverflowMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    settings = settings,
                    // Свой номер: пункт показывает его или «не указан»,
                    // чтобы было видно, готово ли приложение делиться
                    // карточкой
                    ownNumber = ownNumber,
                    onOwnNumber = {
                        menuOpen = false

                        /*
                         * ЕСТЬ НОМЕР — ОТКРЫВАЕМ КАРТОЧКУ.
                         *
                         * ══════════════════════════════════════════════
                         * ПОЧЕМУ НЕ ОТДЕЛЬНЫЙ ЭКРАН
                         * ══════════════════════════════════════════════
                         * Своя карточка ничем не отличается от чужой:
                         * имя, номера, заметка, история звонков.
                         * Строить для неё второй экран значило бы
                         * поддерживать два одинаковых.
                         *
                         * Карточка уже умеет открываться по номеру —
                         * тем же путём, что запись журнала, которой
                         * нет в книге. Если контакт с этим номером
                         * есть, покажется он; если нет — карточка
                         * из одного номера.
                         *
                         * ── Пока номер не задан ────────────────────────
                         * Открывается диалог: показывать пустую
                         * карточку бессмысленно, а спросить номер
                         * надо один раз.
                         */
                        if (ownNumber.isBlank()) {
                            showOwnNumber = true
                        } else {
                            onOpenSubscriber("number:$ownNumber")
                        }
                    },
                    onThemeToggle = {
                        vm.setChoice(
                            "theme",
                            if (settings.theme == ThemeMode.DARK) ThemeMode.LIGHT.name
                            else ThemeMode.DARK.name
                        )
                        menuOpen = false
                    },
                    // Только после подтверждения: очистка необратима и стирает
                    // и системный журнал (разбор у confirmClearLog)
                    onClearLog = { menuOpen = false; confirmClearLog = true },
                    onSettings = { menuOpen = false; onOpenSettings() },
                    onFakeCall = { menuOpen = false; fakeCallOpen = true },
                    onNotes = { menuOpen = false; onOpenNotes() },
                    onReminders = { menuOpen = false; onOpenReminders() },
                    onStartSelection = {
                        menuOpen = false
                        if (selection.isActive) selection.clear()
                        else selection.startEmpty()
                    },
                    selectionActive = selection.isActive
                )
            }
        )
    }

    Scaffold(
        /*
         * Своего поля сообщений у экрана больше нет: оно одно на всё
         * приложение и живёт в RootScreen. Разбор — там же.
         */
        floatingActionButton = {
            /*
             * КРУГЛАЯ КНОПКА ПСЕВДОЗВОНКА.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ МАЛЕНЬКАЯ И БЕЗ ПОДПИСИ
             * ══════════════════════════════════════════════════════════════
             * Обычная FAB — крупный цветной круг, который первым бросается
             * в глаза. Здесь это вредно дважды: главное действие
             * приложения — звонок, а не псевдозвонок; и человек рядом
             * не должен понимать, что за кнопку нажали.
             *
             * Малый размер и приглушённый цвет: кнопка на месте, но
             * не спорит за внимание и не объявляет о себе.
             *
             * Описание для чтения с экрана есть — незрячему человеку
             * маскировка не нужна, ему нужна кнопка.
             */
            // Кнопку можно выключить: она видна всем, кто смотрит
            // на экран, а нужна не каждому
            // При клавиатуре сбоку кнопка легла бы поверх неё — прячем
            if (settings.fakeCallButton && !sideDialpad) SmallFloatingActionButton(
                onClick = { fakeCallOpen = true },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                /*
                 * ЗНАЧОК ПСЕВДОЗВОНКА — ТРУБКА СО СТРЕЛКОЙ К НЕЙ.
                 *
                 * История: сначала был залитый PhoneCallback, и на 24 dp
                 * его стрелка срасталась с трубкой в пятно. Его заменили
                 * на RingVolume (трубка с лучами). Но смысл «входящий
                 * вызов» у трубки со стрелкой понятнее, и этот знак
                 * больше нигде в приложении не занят.
                 *
                 * Решение — КОНТУРНЫЙ вариант, а не залитый: сравнили оба
                 * в трёх размерах, и у контурного между стрелкой и трубкой
                 * остаётся зазор даже на 24 dp. Тот же знак — на ярлыке,
                 * в плитке быстрых настроек и в меню.
                 */
                Icon(
                    Icons.AutoMirrored.Outlined.PhoneCallback,
                    contentDescription = stringResource(R.string.fake_call_title)
                )
            }
        },
        topBar = {
            if (selection.isActive) {
                SelectionTopBar(
                    count = selection.count,
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(list.map { it.key }) },
                    onBlock = {
                        vm.blockGroups(list.filter { it.key in selection.selected })
                        selection.clear()
                    },
                    onDelete = { confirmBulkDelete = true }
                )
            }
            /*
             * Заголовка сверху нет намеренно.
             *
             * Он занимал строку на каждом экране, а отвечал на вопрос,
             * который возникает редко: и так понятно, что перед тобой
             * журнал вызовов — по содержимому.
             *
             * Поиск и фильтры живут в нижней панели: там до них
             * дотягивается большой палец, а верх экрана требует
             * перехватывать телефон.
             */
        },
        bottomBar = {
            // Внизу — только когда клавиатура не стоит сбоку: разбор у sideDialpad
            /*
             * Панель внизу уезжает и возвращается плавно — в паре
             * с клавиатурой сбоку (разбор ниже, у боковой панели).
             * На телефоне в портрете sideDialpad всегда false: панель
             * видна с самого начала, анимации нет.
             */
            AnimatedVisibility(
                // Лёжа панель справа — внизу её нет вовсе
                visible = !sideDialpad && !landscapeRail,
                enter = expandVertically(tween(Motion.NORMAL_MS), expandFrom = Alignment.Top) +
                    fadeIn(tween(Motion.NORMAL_MS)),
                exit = shrinkVertically(tween(Motion.NORMAL_MS), shrinkTowards = Alignment.Top) +
                    fadeOut(tween(Motion.QUICK_MS))
            ) {
                controlPanel(Modifier, false, null, false)
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { scaffoldPadding ->
        /*
         * Отступы списка при клавиатуре сбоку.
         *
         * Справа у списка теперь не край экрана, а клавиатура: отступ
         * под панель навигации (в альбомном режиме она справа, ~48 dp)
         * лёг бы пустой полосой между журналом и клавиатурой. Его
         * берёт на себя сама клавиатура, а у списка справа — ноль.
         */
        val layoutDirection = LocalLayoutDirection.current
        val padding = if (sideDialpad) {
            PaddingValues(
                start = scaffoldPadding.calculateStartPadding(layoutDirection),
                top = scaffoldPadding.calculateTopPadding(),
                end = 0.dp,
                bottom = scaffoldPadding.calculateBottomPadding()
            )
        } else {
            scaffoldPadding
        }

        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                /*
                 * Фишек фильтров сверху нет намеренно.
                 *
                 * Они занимали строку на каждом экране ради того, что
                 * выбирают несколько раз в день. Фильтры и поиск живут внизу,
                 * в пределах большого пальца: тянуться к верху экрана ради
                 * частого действия неудобно.
                 */
                // Вид журнала «Группы по дням»: место строки в группе.
                // В обычном виде каждая строка — отдельная карточка
                val groupPositionOf: (Int, Int) -> GroupPosition = { i, n ->
                    if (settings.journalStyle == JournalStyle.GROUPS) GroupPosition.of(i, n)
                    else GroupPosition.SINGLE
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (state.query.isNotEmpty() && contactRows.isNotEmpty()) {
                        itemsIndexed(
                            contactRows,
                            key = { _, r -> "found_${r.key}" },
                            contentType = { _, _ -> "call_log_row" }
                        ) { index, row ->
                            CallLogRow(
                                groupPosition = groupPositionOf(index, contactRows.size),
                                row = liveRow(row),
                                vm = vm,
                                state = state,
                                settings = settings,
                                selection = selection,
                                swipeController = swipeController,
                                swipeTuning = swipeTuning,
                                startHint = startHint,
                                endHint = endHint,
                                rightExtra = rightExtra,
                                leftExtra = leftExtra,
                                scope = scope,
                                listScrolling = listScrolling,
                                swipeKeyPrefix = "found_",
                                onOpenSubscriber = onOpenSubscriber,
                                onLongPress = { selectedGroup = it },
                                onNeedNumberPicker = { g, options ->
                                    pickerOptions = options
                                    pickerFor = g
                                }
                            )
                        }
                    }


                    if (list.isEmpty() && state.loading &&
                        state.query.isEmpty() && !activeFilter.isActive
                    ) {
                        item(key = "skeleton", contentType = "skeleton") {
                            CallLogSkeleton()
                        }
                    }


                    /*
                     * Пустое состояние — вместо списка, а не над ним.
                     *
                     * Прежде оно добавлялось элементом ленты, и при непустых
                     * закреплённых человек видел одновременно «звонков нет»
                     * и сами звонки. Теперь остальное содержимое при пустом
                     * результате не рисуется вовсе — см. условия ниже.
                     */
                    /*
                     * ПУСТОТА ОПРЕДЕЛЯЕТСЯ ПО ОТБОРУ, А НЕ ПО СТРОКАМ.
                     *
                     * ── Почему не rows.isEmpty() ───────────────────────────────
                     * Строки и отбор обновляются не в один миг: сперва читается
                     * журнал, затем пересчитывается фильтр, затем пересобираются
                     * строки. Между этими шагами rows пуст, хотя записи есть.
                     *
                     * Если ориентироваться на них, после каждого удаления
                     * или блокировки мелькало бы «звонков нет» — и тем заметнее,
                     * чем длиннее журнал.
                     *
                     * `list` — это результат отбора, он и есть ответ на вопрос
                     * «есть ли что показывать». Пока строки догоняют, экран
                     * просто пуст долю секунды, без ложного сообщения.
                     */
                    if (list.isEmpty() &&
                        (!state.loading || state.query.isNotEmpty() || activeFilter.isActive)
                    ) {
                        item {
                            EmptyState(
                                reason = when {
                                    state.query.isNotEmpty() ->
                                        EmptyReason.NoSearchResults(state.query)
                                    !state.hasCallLogPermission ->
                                        EmptyReason.NoCallLogPermission
                                    else -> EmptyReason.NoCalls
                                },
                                onAction = {
                                    if (state.query.isNotEmpty()) vm.onQueryChange("")
                                    else onOpenSettings()
                                }
                            )
                        }
                    }


                    /*
                     * Закреплённые — и при пустом журнале (1.3.0). Раньше
                     * раздел показывался только при непустом журнале:
                     * после очистки закреплённые карточки пропадали, пока
                     * не появится новый звонок, — хотя закрепление никуда
                     * не делось.
                     */
                    if (pinnedRows.isNotEmpty() || pinnedWithoutCalls.isNotEmpty()) {
                        item(key = "pinned_header", contentType = "date_header") {
                            LetterHeader(stringResource(R.string.pinned_header))
                        }


                        itemsIndexed(
                            pinnedRows,
                            key = { _, r -> "pin_${r.key}" },
                            contentType = { _, _ -> "call_log_row" }
                        ) { index, row ->
                            CallLogRow(
                                groupPosition = groupPositionOf(index, pinnedRows.size),
                                row = liveRow(row),
                                vm = vm,
                                state = state,
                                settings = settings,
                                selection = selection,
                                swipeController = swipeController,
                                swipeTuning = swipeTuning,
                                startHint = startHint,
                                endHint = endHint,
                                rightExtra = rightExtra,
                                leftExtra = leftExtra,
                                scope = scope,
                                listScrolling = listScrolling,
                                swipeKeyPrefix = "pin_",
                                onOpenSubscriber = onOpenSubscriber,
                                onLongPress = { selectedGroup = it },
                                onNeedNumberPicker = { g, options ->
                                    pickerOptions = options
                                    pickerFor = g
                                }
                            )
                        }

                        /*
                         * Закреплённые без истории — отдельным видом строки.
                         *
                         * Обычная строка журнала показывает тип вызова, дату
                         * и длительность; здесь их нет и не будет. Подставлять
                         * пустые поля значило бы делать вид, что звонок был.
                         */
                        items(
                            pinnedWithoutCalls,
                            key = { "pin_only_${it.e164}" },
                            contentType = { "pinned_only_row" }
                        ) { row ->
                            PinnedOnlyCard(
                                row = row,
                                onCall = { vm.call(row.e164) },
                                // «number:» — карточка по номеру; голый номер
                                // карточка не распознавала и закрывалась
                                onOpen = { onOpenSubscriber("number:${row.e164}") }
                            )
                        }
                    }


                    dateGroups.forEach { (bucket, rowsInBucket) ->
                        item(key = "date_${bucket.ordinal}", contentType = "date_header") {
                            LetterHeader(stringResource(dateGroupLabel(bucket)))
                        }


                        itemsIndexed(
                            items = rowsInBucket,
                            key = { _, r -> r.key },
                            contentType = { _, _ -> "call_log_row" }
                        ) { index, row ->
                            CallLogRow(
                                groupPosition = groupPositionOf(index, rowsInBucket.size),
                                row = liveRow(row),
                                vm = vm,
                                state = state,
                                settings = settings,
                                selection = selection,
                                swipeController = swipeController,
                                swipeTuning = swipeTuning,
                                startHint = startHint,
                                endHint = endHint,
                                rightExtra = rightExtra,
                                leftExtra = leftExtra,
                                scope = scope,
                                listScrolling = listScrolling,
                                onOpenSubscriber = onOpenSubscriber,
                                onLongPress = { selectedGroup = it },
                                onNeedNumberPicker = { g, options ->
                                    pickerOptions = options
                                    pickerFor = g
                                }
                            )
                        }
                    }


                    if (loadingMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(FlowUi.contentPadding),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        }
                    }
                }
    
            }

            /*
             * ══════════════════════════════════════════════════════════════
             * КЛАВИАТУРА СБОКУ ВЫЕЗЖАЕТ, А НЕ ПОЯВЛЯЕТСЯ
             * ══════════════════════════════════════════════════════════════
             * Раньше при нажатии на клавиатуру на телефоне лёжа журнал
             * мгновенно сжимался на 360 dp, а справа из ниоткуда возникала
             * клавиатура. Резко — глаз не успевал понять, что произошло.
             *
             * Теперь клавиатура выезжает справа, расширяясь от левого края,
             * журнал плавно уступает ей место, а нижняя панель в это же
             * время уезжает вниз. При закрытии — всё обратно.
             *
             * Скорости — общие для приложения (Motion): выезд 300 мс,
             * исчезновение быстрее, 200, — уходящее не должно задерживать
             * взгляд.
             */
            AnimatedVisibility(
                visible = sideDialpad,
                enter = expandHorizontally(tween(Motion.NORMAL_MS), expandFrom = Alignment.Start) +
                    fadeIn(tween(Motion.NORMAL_MS)),
                exit = shrinkHorizontally(tween(Motion.NORMAL_MS), shrinkTowards = Alignment.Start) +
                    fadeOut(tween(Motion.QUICK_MS))
            ) {
                    Box(
                        Modifier
                            // Сверху — под строку состояния; справа — под вырез
                            // камеры, если он с этой стороны. Панель навигации
                            // панель учитывает сама (navigationBarsPadding внутри)
                            .padding(top = scaffoldPadding.calculateTopPadding())
                            // Вырез камеры обходит тот, кто у самого края:
                            // при полосе кнопок справа — она, а не клавиатура
                            .then(
                                if (landscapeRail) Modifier
                                else Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                            )
                            .width(SIDE_DIALPAD_WIDTH)
                            .fillMaxHeight()
                            .padding(end = 8.dp, bottom = 8.dp)
                    ) {
                        // Ряды клавиш делят высоту сами: разбор в Dialpad.fillHeight
                        controlPanel(Modifier.fillMaxHeight(), true, null, false)
                    }
            }

            // Полоса кнопок справа — телефон лёжа (1.2.2)
            if (landscapeRail) {
                Box(
                    Modifier
                        .padding(top = scaffoldPadding.calculateTopPadding())
                        // Вырез камеры справа — полоса его обходит
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                        .fillMaxHeight()
                        .padding(end = 8.dp, bottom = 8.dp)
                ) {
                    controlPanel(Modifier.fillMaxHeight(), false, null, true)
                }
            }
        }
    }


    // ── Всплывающие элементы ───────────────────────────────────────────────


    val noteEditorFor by vm.noteEditorFor.collectAsState()
    noteEditorFor?.let { group ->
        val existing = state.notes[group.e164]
        NoteEditorDialog(
            initialNote = existing?.note.orEmpty(),
            initialTags = existing?.tags?.toSet().orEmpty(),
            onSave = { text, tags ->
                vm.saveNote(group.e164, text, tags.toList())
                vm.closeNoteEditor()
            },
            onDismiss = { vm.closeNoteEditor() }
        )
    }


    val reminderFor by vm.reminderRequest.collectAsState()
    reminderFor?.let { group ->
        ReminderDialog(
            displayName = group.displayName ?: group.number,
            /*
             * Выбор варианта не ставит напоминание, а открывает окно
             * с этим временем: промах по строке виден до сохранения,
             * а не после.
             */
            onPick = { at ->
                vm.dismissReminderRequest()
                pendingReminder = group to at
            },
            onDismiss = { vm.dismissReminderRequest() }
        )
    }


    val simRequest by vm.simRequest.collectAsState()


    LaunchedEffect(simRequest) {
        if (simRequest == null) swipeController.closeAll()
    }


    val closeSwipesToken by vm.closeSwipes.collectAsState()
    LaunchedEffect(closeSwipesToken) {
        if (closeSwipesToken > 0) swipeController.closeAll()
    }
    simRequest?.let { request ->
        SimChooserDialog(
            displayName = request.displayName,
            sims = request.sims,
            onChoose = request.onChosen,
            onDismiss = { vm.dismissSimRequest() }
        )
    }


    /*
     * ВЫБОР, КОГДА «ПОЗВОНИТЬ».
     *
     * Три быстрых варианта и «выбрать время» — то же окно, что
     * у напоминаний: человеку не приходится осваивать второй способ
     * назначить время.
     *
     * «Сейчас» стоит первым и не требует подтверждения: если функцию
     * открыли, чтобы выйти из разговора прямо сейчас, лишнее нажатие —
     * это лишние секунды на виду у собеседника.
     */
    if (fakeCallOpen) {
        val scheduledAt by vm.fakeCallAt.collectAsState()
        val caller by vm.fakeCallCaller.collectAsState()
        val timeFormat = remember {
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        }

        OgnelissDialog(
            onDismissRequest = { fakeCallOpen = false },
            title = stringResource(R.string.fake_call_title),
            content = {
                Text(
                    stringResource(R.string.settings_fake_call_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                /*
                 * СОСТОЯНИЕ: НАЗНАЧЕН ЛИ И НА КОГДА.
                 *
                 * Будильник в системе о себе не рассказывает, поэтому
                 * время хранится у нас. Без этой строки «Отменить
                 * псевдозвонок» нажимали вслепую: назначено ли что-то
                 * вообще, по окну было не понять.
                 *
                 * Просроченное не показываем: время могло пройти,
                 * пока окно закрыто.
                 */
                if (scheduledAt > System.currentTimeMillis()) {
                    Text(
                        stringResource(
                            R.string.fake_call_scheduled_at,
                            timeFormat.format(java.util.Date(scheduledAt))
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                /*
                 * КТО «ЗВОНИТ» — ЗДЕСЬ, А НЕ ТОЛЬКО В НАСТРОЙКАХ.
                 *
                 * Это единственная настройка, которую хочется поменять
                 * ровно в момент использования: «пусть будет не жена,
                 * а рабочий номер». Гонять человека в настройки ради
                 * одного поля, когда окно уже открыто, — лишний путь.
                 *
                 * В настройках пункт тоже остался: там его ищут те,
                 * кто настраивает заранее.
                 */
                Surface(
                    onClick = {
                        fakeCallOpen = false
                        onOpenFakeCallPicker()
                    },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                ) {
                    // Отступ как у соседних строк окна: подпись в две
                    // строки прижималась к краю плитки, остальные пункты
                    // стоят с полем
                    Column(
                        // contentPadding — это Dp; cardPadding здесь
                        // не подходит, он PaddingValues и задаёт обе оси
                        Modifier.padding(
                            horizontal = FlowUi.contentPadding,
                            vertical = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            stringResource(R.string.settings_fake_call_number),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            caller.ifBlank { stringResource(R.string.fake_call_random) },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                listOf(
                    R.string.fake_call_now to 0L,
                    R.string.fake_call_in_1min to 60_000L,
                    R.string.fake_call_in_5min to 5 * 60_000L
                ).forEach { (labelRes, delay) ->
                    Surface(
                        onClick = {
                            fakeCallOpen = false
                            if (delay == 0L) {
                                FakeCall.ringNow(context)
                            } else {
                                val at = System.currentTimeMillis() + delay
                                FakeCall.schedule(context, at)
                                vm.rememberFakeCallAt(at)
                            }
                        },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                    ) {
                        Text(
                            stringResource(labelRes),
                            Modifier.padding(FlowUi.cardPadding),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Surface(
                    onClick = {
                        fakeCallOpen = false
                        fakeCallAt = System.currentTimeMillis() + 15 * 60_000L
                    },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                ) {
                    Text(
                        stringResource(R.string.fake_call_pick_time),
                        Modifier.padding(FlowUi.cardPadding),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { fakeCallOpen = false }) {
                    Text(stringResource(R.string.nav_close))
                }
            },
            dismissButton = {
                // Отмена назначенного: пока он не сработал, его надо
                // как-то снять, и другого места для этого нет
                TextButton(onClick = {
                    FakeCall.cancel(context)
                    vm.forgetFakeCall()
                    fakeCallOpen = false
                }) {
                    Text(stringResource(R.string.fake_call_cancel))
                }
            }
        )
    }

    fakeCallAt?.let { at ->
        ReminderTimeDialog(
            displayName = stringResource(R.string.fake_call_title),
            initialMillis = at,
            onSave = { chosen ->
                FakeCall.schedule(context, chosen)
                vm.rememberFakeCallAt(chosen)
                fakeCallAt = null
            },
            onDismiss = { fakeCallAt = null }
        )
    }

    pendingReminder?.let { (group, at) ->
        ReminderTimeDialog(
            displayName = group.displayName ?: group.number,
            initialMillis = at,
            onSave = { chosen ->
                vm.scheduleReminderAt(group.number, group.displayName, chosen)
                pendingReminder = null
            },
            onDismiss = { pendingReminder = null }
        )
    }


    if (filtersOpen) {
        FilterSheet(
            filter = activeFilter,
            /*
             * Счёт по отбору, а не по строкам.
             *
             * Строки пересобираются с задержкой в кадр, и счётчик
             * успевал показать ноль там, где записи есть.
             */
            matchCount = list.size,
            simCount = state.sims.size,
            onFilterChange = { vm.setFilter(it) },
            onDismiss = { filtersOpen = false }
        )
    }


    pickerFor?.let { group ->
        NumberPickerSheet(
            title = group.displayName ?: PhoneFormatter.format(group.number),
            numbers = pickerOptions,
            sims = state.sims,
            onCall = { number, slot -> vm.call(number, slot); pickerFor = null },
            onSms = { vm.openSms(it); pickerFor = null },
            onMessenger = { vm.handleSwipe(SwipeAction.WHATSAPP, group); pickerFor = null },
            onDismiss = { pickerFor = null }
        )
    }


    selectedGroup?.let { group ->
        ContactActionSheet(
            group = group,
            vm = vm,
            onOpenCard = { onOpenSubscriber(group.key) },
            onOpenBlockLists = onOpenBlockLists,
            onDismiss = { selectedGroup = null }
        )
    }


    assigningSpeedDial?.let { digit ->
        SpeedDialPickerDialog(
            digit = digit,
            vm = vm,
            onAssign = { number, name ->
                vm.assignSpeedDial(digit, number, name)
                assigningSpeedDial = null
            },
            onDismiss = { assigningSpeedDial = null }
        )
    }


    if (confirmBulkDelete) {
        /*
         * Общий контейнер приложения.
         *
         * Удаление записей журнала необратимо, поэтому треугольник
         * предупреждения сохранён — он переехал из шапки в текст:
         * места под значок у OgnelissDialog нет.
         */
        OgnelissDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = stringResource(R.string.selection_delete_title),
            content = {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        pluralStringResource(
                            R.plurals.selection_delete_text, selection.count, selection.count
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteGroups(list.filter { it.key in selection.selected })
                    selection.clear()
                    confirmBulkDelete = false
                }) {
                    Text(
                        stringResource(R.string.selection_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) {
                    Text(stringResource(R.string.nav_cancel))
                }
            }
        )
    }


    pendingWarning?.let { warning ->
        /*
         * Предупреждение перед звонком на платный или международный
         * номер. Значок остаётся: цена ошибки здесь — деньги со счёта,
         * и предупреждение должно опознаваться раньше, чем прочитано.
         */
        OgnelissDialog(
            onDismissRequest = { pendingWarning = null; pendingCall = null },
            title = stringResource(R.string.predial_title),
            content = {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(
                            when (warning.category) {
                                NumberCategory.PREMIUM -> R.string.predial_premium
                                NumberCategory.INTERNATIONAL -> R.string.predial_international
                                else -> R.string.predial_generic
                            }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCall?.let { (number, slot) -> vm.callFromDialpad(number, slot) }
                    pendingWarning = null
                    pendingCall = null
                }) { Text(stringResource(R.string.predial_call_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingWarning = null; pendingCall = null }) {
                    Text(stringResource(R.string.nav_cancel))
                }
            }
        )
    }
}


/**
 * Долгое нажатие цифры.
 */
private suspend fun handleLongDigit(
    vm: DialerViewModel,
    query: String,
    digit: Char,
    onAssignSpeedDial: (Int) -> Unit
) {
    when {
        digit == '0' -> vm.onQueryChange(query + "+")
        digit == '*' -> vm.onQueryChange(query + DialString.PAUSE)
        digit == '#' -> vm.onQueryChange(query + DialString.WAIT)
        query.isEmpty() && digit in '2'..'9' -> {
            val dialed = vm.dialSpeedDial(digit.digitToInt())
            if (!dialed) onAssignSpeedDial(digit.digitToInt())
        }
    }
}


/**
 * Главное меню.
 *
 * Выпадающее от своей кнопки, а не диалог по центру экрана.
 * Разрушительное действие внизу и отделено чертой.
 * Переключение темы находится внутри меню.
 */
// ModalBottomSheet в Material 3 1.3 ещё помечен как экспериментальный
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverflowMenu(
    expanded: Boolean,
    /** Собственный номер или пусто, если не указан. */
    ownNumber: String = "",
    onOwnNumber: () -> Unit = {},
    onDismiss: () -> Unit,
    /**
     * Фильтры журнала.
     *
     * Переехали сюда с нижней панели: значок занимал там место
     * постоянно, а включают их несколько раз в неделю.
     */
    settings: com.ogneliss.dialer.ui.SettingsState,
    onThemeToggle: () -> Unit,
    onClearLog: () -> Unit,
    onSettings: () -> Unit,
    /** Псевдозвонок: выбор, когда «позвонить». */
    onFakeCall: () -> Unit,
    /** Экран заметок. */
    onNotes: () -> Unit,
    /** Экран напоминаний. */
    onReminders: () -> Unit,
    onStartSelection: () -> Unit,
    selectionActive: Boolean = false
) {
    /*
     * ПОРЯДОК ПУНКТОВ МЕНЮ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПО ЧАСТОТЕ И ПО СМЫСЛУ, А НЕ ПО ИСТОРИИ ДОБАВЛЕНИЯ
     * ══════════════════════════════════════════════════════════════════════
     * Раньше пункты лежали в том порядке, в каком появлялись в коде.
     * Теперь сгруппированы, и группы разделены линиями:
     *
     *   1. Псевдозвонок — единственный пункт, который нажимают
     *      в спешке и не глядя. Первым, под большим пальцем.
     *   2. Тема — переключатель, которым пользуются чаще прочего.
     *   3. Мой контакт, заметки, напоминания — переходы к своим
     *      данным.
     *   4. Выбрать несколько, настройки — режимы и управление.
     *   5. Очистить журнал — необратимое, отдельно и красным.
     *
     * ⚠️ «Очистить журнал» остаётся последним и за линией: пункт,
     * который нельзя отменить, не должен оказаться рядом с тем,
     * что нажимают часто.
     */
    /*
     * ══════════════════════════════════════════════════════════════════════
     * «ЕЩЁ» — ШТОРКА С ГРУППАМИ, А НЕ ВЫПАДАЮЩЕЕ МЕНЮ (1.2.1)
     * ══════════════════════════════════════════════════════════════════════
     * Было стандартное выпадающее меню: углы 4 dp и тень — единственный
     * такой элемент в приложении, где скругления крупные, а теней нет.
     * Выпадало от кнопки внизу, а выглядело как меню верхней строки.
     *
     * Теперь — шторка снизу, как у меню долгого нажатия: появляется
     * там, где палец, и выглядит так же. Порядок и смысл групп прежние:
     *
     *   1. Псевдозвонок и свой номер — быстрые действия;
     *   2. заметки, напоминания, выбор нескольких — свои данные и режимы;
     *   3. тема и настройки — управление;
     *   4. очистить журнал — необратимое, отдельно и красным.
     */
    if (!expanded) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val fakeCallLabel = stringResource(R.string.fake_call_title)
    val ownLabel = stringResource(
        if (ownNumber.isBlank()) R.string.menu_my_number_unknown else R.string.menu_my_contact
    )
    val notesLabel = stringResource(R.string.nav_notes)
    val remindersLabel = stringResource(R.string.nav_reminders)
    val selectionLabel = stringResource(
        if (selectionActive) R.string.selection_exit else R.string.selection_start
    )
    val themeLabel = stringResource(
        if (settings.theme == ThemeMode.DARK) R.string.menu_theme_light else R.string.menu_theme_dark
    )
    val settingsLabel = stringResource(R.string.nav_settings)
    val clearLabel = stringResource(R.string.log_clear_all)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(Modifier.padding(bottom = 16.dp)) {
            SheetGroup {
                SheetRow(Icons.AutoMirrored.Outlined.PhoneCallback, fakeCallLabel, onClick = onFakeCall)
                SheetDivider()
                SheetRow(
                    Icons.Default.Person,
                    ownLabel,
                    // Номер второй строкой: видно, какой задан, без похода в настройки
                    supporting = ownNumber.takeIf { it.isNotBlank() }?.let { PhoneFormatter.format(it) },
                    // Приглушённый, пока номера нет: не требует внимания, но и не прячется
                    iconTint = if (ownNumber.isBlank()) MaterialTheme.colorScheme.outline else null,
                    onClick = onOwnNumber
                )
            }
            SheetGroup {
                SheetRow(
                    Icons.AutoMirrored.Filled.StickyNote2,
                    notesLabel,
                    iconTint = NoteTeal,
                    onClick = onNotes
                )
                SheetDivider()
                SheetRow(Icons.Default.Alarm, remindersLabel, onClick = onReminders)
                SheetDivider()
                SheetRow(
                    if (selectionActive) Icons.Default.Close else Icons.Default.Checklist,
                    selectionLabel,
                    onClick = onStartSelection
                )
            }
            SheetGroup {
                SheetRow(
                    if (settings.theme == ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                    themeLabel,
                    onClick = onThemeToggle
                )
                SheetDivider()
                SheetRow(Icons.Default.Settings, settingsLabel, onClick = onSettings)
            }
            SheetRow(
                Icons.Default.DeleteSweep,
                clearLabel,
                color = MaterialTheme.colorScheme.error,
                onClick = onClearLog,
                // Вне панели — отступ как у групп выше
                standalone = true
            )
        }
    }
}


// Размеры пунктов меню — общие для приложения, разбор в FlowUi
private val MENU_ITEM_HEIGHT = FlowUi.menuItemHeight
private val MENU_ITEM_PADDING = FlowUi.menuItemPadding


/**
 * СТРОКА ЖУРНАЛА.
 */
@Composable
private fun CallLogRow(
    row: CallLogRowState,
    vm: DialerViewModel,
    state: com.ogneliss.dialer.ui.UiState,
    settings: com.ogneliss.dialer.ui.SettingsState,
    selection: SelectionState<String>,
    swipeController: com.ogneliss.dialer.ui.components.swipe.SwipeRevealController,
    swipeTuning: com.ogneliss.dialer.settings.SwipeTuning,
    startHint: com.ogneliss.dialer.ui.components.swipe.RevealAction?,
    endHint: com.ogneliss.dialer.ui.components.swipe.RevealAction?,
    rightExtra: List<SwipeAction>,
    leftExtra: List<SwipeAction>,
    scope: kotlinx.coroutines.CoroutineScope,
    listScrolling: Boolean,
    swipeKeyPrefix: String = "",
    onOpenSubscriber: (String) -> Unit,
    onLongPress: (CallLogGroup) -> Unit,
    onNeedNumberPicker: (CallLogGroup, List<NumberOption>) -> Unit,
    groupPosition: GroupPosition = GroupPosition.SINGLE
) {
    val group = row.group
    // Несколько действий на сторону — панель кнопок вместо мгновенного действия
    val rightMulti = settings.swipeRight != SwipeAction.NONE && rightExtra.isNotEmpty()
    val leftMulti = settings.swipeLeft != SwipeAction.NONE && leftExtra.isNotEmpty()


    CallLogCard(
        groupPosition = groupPosition,
        row = row,
        /*
         * Кнопка вызова в строке.
         *
         * Тот же путь, что у свайпа: через onNeedNumberPicker, если
         * у абонента несколько номеров. Иначе кнопка звонила бы
         * на последний использованный, а свайп спрашивал — и человек
         * получал разное от двух способов сделать одно.
         */
        onCall = {
            /*
             * Тот же путь, что у свайпа вызова.
             *
             * performSwipe сам решает, спросить ли номер и SIM: если
             * у абонента их несколько, появится тот же выбор, что при
             * свайпе. Звонить напрямую значило бы дать двум способам
             * сделать одно разное поведение.
             */
            vm.performSwipe(SwipeAction.CALL, group) { swipeController.close() }
        },
        searchQuery = state.query,
        selected = selection.isSelected(row.key),
        selectionActive = selection.isActive,
        swipeKey = swipeKeyPrefix + row.key,
        swipeController = swipeController,
        swipeTuning = swipeTuning,
        directStartAction = startHint,
        directEndAction = endHint,
        /*
         * ДВА-ТРИ ДЕЙСТВИЯ НА СВАЙП — ПАНЕЛЬ КНОПОК (1.2.5).
         * Строка сдвигается и ждёт нажатия, как в почтовых клиентах;
         * мгновенного действия в эту сторону нет (onDirect… ниже — null).
         * Одно действие — как было: срабатывает сразу.
         */
        startActions = if (rightMulti) {
            remember(row.key, settings.swipeRight, rightExtra, state.sims) {
                vm.revealActionsFor(settings.swipeRight, rightExtra, group, state.sims)
            }
        } else emptyList(),
        endActions = if (leftMulti) {
            remember(row.key, settings.swipeLeft, leftExtra, state.sims) {
                vm.revealActionsFor(settings.swipeLeft, leftExtra, group, state.sims)
            }
        } else emptyList(),
        /*
         * СВАЙП ВЫКЛЮЧЕН В НАСТРОЙКАХ — ОБРАБОТЧИКА НЕТ ВОВСЕ.
         *
         * Раньше обработчик передавался всегда, даже для «Нет действия».
         * Строка свайпа считает направление рабочим, если обработчик есть
         * (SwipeRevealRow.hasStartDirect), — и выключенный свайп всё равно
         * тянулся: строка уезжала, возвращалась, только ничего не делала.
         * А при обоих выключенных строка продолжала перехватывать
         * горизонтальные движения пальца.
         *
         * Теперь для «Нет действия» — null: направление не тянется,
         * а при обоих выключенных жест отключается целиком.
         */
        onDirectStart = remember(row.key, settings.swipeRight, rightMulti) {
            if (settings.swipeRight == SwipeAction.NONE || rightMulti) null
            else {
                {
                    vm.performSwipe(settings.swipeRight, group) {
                        swipeController.close()
                    }
                }
            }
        },
        onDirectEnd = remember(row.key, settings.swipeLeft, leftMulti) {
            if (settings.swipeLeft == SwipeAction.NONE || leftMulti) null
            else {
                {
                    vm.performSwipe(settings.swipeLeft, group) {
                        swipeController.close()
                    }
                }
            }
        },
        onClick = {
            /*
             * Нажатие — в журнал приложения. Жалоба «после звонка нажатие
             * на запись ничего не делает» пришла с логом, где нажатий
             * не было вовсе, и причину пришлось искать по коду. Теперь
             * видно, какая ветка сработала и с каким ключом.
             */
            when {
                selection.isActive -> {
                    Logger.i("Journal", "Нажатие: режим выбора")
                    selection.toggle(row.key)
                }
                swipeController.isOpen(row.key) -> {
                    Logger.i("Journal", "Нажатие: закрыта раскрытая строка")
                    swipeController.close()
                }
                else -> {
                    Logger.i("Journal", "Нажатие: карточка ${Logger.maskKey(row.key)}")
                    onOpenSubscriber(row.key)
                }
            }
        },
        onLongClick = {
            if (selection.isActive) selection.toggle(row.key)
            else onLongPress(group)
        }
    )
}


/** Подпись группы давности. */
private fun dateGroupLabel(group: CallLogDateGroup): Int = when (group) {
    CallLogDateGroup.TODAY -> R.string.date_group_today
    CallLogDateGroup.YESTERDAY -> R.string.date_group_yesterday
    CallLogDateGroup.THIS_WEEK -> R.string.date_group_this_week
    CallLogDateGroup.LAST_WEEK -> R.string.date_group_last_week
    CallLogDateGroup.EARLIER -> R.string.date_group_earlier
}


private const val LOAD_MORE_THRESHOLD = 5



/** Закреплённый номер, по которому ещё не было звонков. */
data class PinnedOnlyRow(
    val e164: String,
    val title: String,
    val photoUri: String?
)

/**
 * Строка закреплённого номера без истории.
 *
 * Отличается от журнальной ровно тем, чего у неё нет: типа вызова,
 * даты и длительности. Всё остальное — фотография, имя, звонок
 * по нажатию — то же самое, чтобы строка не выглядела чужой
 * в списке.
 */
@Composable
private fun PinnedOnlyCard(
    row: PinnedOnlyRow,
    onCall: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.screenPadding, vertical = FlowUi.cardSpacing)
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    PhoneFormatter.format(row.e164),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onCall) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = stringResource(R.string.action_call),
                    tint = CallGreen
                )
            }
        }
    }
}

/**
 * Ширина клавиатуры сбоку. 360 dp — ширина обычного телефона:
 * клавиши того же размера, что человек привык видеть в портрете.
 */
private val SIDE_DIALPAD_WIDTH = 360.dp


