package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.data.JournalFolder
import com.ogneliss.dialer.ui.components.MenuFolder
import com.ogneliss.dialer.ui.components.MenuFolderRows
import com.ogneliss.dialer.ui.components.SectionTopBar
import com.ogneliss.dialer.ui.components.TopBarMenuItem
import com.ogneliss.dialer.ui.components.scrollEdgeFade
import com.ogneliss.dialer.ui.components.SelectionBottomBar
import com.ogneliss.dialer.ui.components.dragSelect
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle


import com.ogneliss.dialer.data.PinnedContacts
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.settings.ThemeMode
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
import androidx.compose.material3.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.FakeCall
import com.ogneliss.dialer.ui.theme.Dimens
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.ui.components.ReminderTimeDialog
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.data.JournalDays
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallLogRowMapper
import com.ogneliss.dialer.data.CallType
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
import kotlinx.coroutines.delay
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
    /** Переключатель «журнал | контакты» на панели (1.6.0). */
    onOpenContacts: () -> Unit,
    /** Список разделов — долгим нажатием на переключатель (1.6.0). */
    onOpenSection: (MainSection) -> Unit = {},
    onOpenSettings: () -> Unit,
    /** «Вопросы и ответы» из меню (1.6.0). */
    onOpenHelp: () -> Unit = {},
    /** Экран заметок: из меню и свайпом с контактов. */
    /** Выбор того, кто «звонит»: тот же список контактов. */
    onOpenFakeCallPicker: () -> Unit,
    onOpenNotes: () -> Unit,
    /** Экран напоминаний. */
    onOpenReminders: () -> Unit,
    onOpenSubscriber: (String) -> Unit,
    /** Чёрный и белый списки — передаются карточкам по удержанию. */
    onOpenBlockLists: () -> Unit,
    /** Архив журнала — «Ещё» → «Архив» (2.1.0). */
    onOpenArchive: () -> Unit = {},
    /** «Закреплённые» — при полном списке закреплённых (2.1.0, RecordActionCards). */
    onOpenPinned: () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()

    // Правила блокировки: от них зависит подпись «заблокирован»
    // в строке журнала, поэтому экран на них подписан
    val rules by vm.rules.collectAsStateWithLifecycle()

    // Напоминания: значок в строке и фильтр «с напоминанием»
    val reminders by vm.activeReminders.collectAsStateWithLifecycle()
    val activeFilter by vm.filter.collectAsStateWithLifecycle()
    val swipeTuning by vm.swipeTuning.collectAsStateWithLifecycle()
    val rightExtra by vm.swipeRightExtra.collectAsStateWithLifecycle()
    val leftExtra by vm.swipeLeftExtra.collectAsStateWithLifecycle()
    val filteredByChips by vm.filteredLog.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Подсказка о свайпах (2.2.2): до чтения настроек считаем показанной — без мелькания
    val swipeHintDone by vm.prefs.swipeHintDone.collectAsStateWithLifecycle(true)
    val context = LocalContext.current


    // ── Состояние экрана ───────────────────────────────────────────────────
    var dialpadExpanded by rememberSaveable { mutableStateOf(false) }

    /*
     * ПОИСК БУКВАМИ В ПАНЕЛИ (1.6.0, вариант «Б»): разбор в PanelSearchRow.
     * Запрос — тот же, что у клавиатуры набора (state.query): цифры ищут
     * по T9 и номеру, буквы — по тексту имени и заметки
     * (ContactSearch.searchText). Закрытие очищает запрос.
     */
    var searching by rememberSaveable { mutableStateOf(false) }
    val closeSearch: () -> Unit = {
        searching = false
        vm.onQueryChange("")
    }
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
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                /*
                 * Клавиатуру просили из контактов (1.6.0): там та же панель,
                 * и её кнопка клавиатуры ведёт сюда. Разбор у requestDialpad.
                 */
                if (vm.takeDialpadRequest()) {
                    searching = false
                    dialpadExpanded = true
                } else if (vm.state.value.query.isEmpty()) {
                    dialpadExpanded = openAtStart
                }
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
    val ownNumber by vm.ownNumber.collectAsStateWithLifecycle()
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
    /*
     * Где нажали запись — туда встаёт карточка записи (1.5.7, меню
     * долгого нажатия карточками, как в Ogneliss Mail).
     */
    var selectedAnchorY by remember { mutableStateOf<Float?>(null) }
    val touchAnchor = rememberTouchAnchor()
    var pickerFor by remember { mutableStateOf<CallLogGroup?>(null) }
    var pickerOptions by remember { mutableStateOf<List<NumberOption>>(emptyList()) }
    var assigningSpeedDial by remember { mutableStateOf<Int?>(null) }
    var pendingCall by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var pendingWarning by remember { mutableStateOf<PreDialWarning.Warning?>(null) }

    /*
     * «Разрешить» в пустом журнале (2.1.0, ошибка): было — переход
     * в настройки приложения (сам раздел «Настройки» Ogneliss), где
     * доступа к журналу не выдать. Стало — системный запрос READ_CALL_LOG;
     * окна уже нет — системные настройки приложения (rememberPermissionRequest).
     */
    val requestCallLog = rememberPermissionRequest(android.Manifest.permission.READ_CALL_LOG) {
        vm.onPermissionsChanged()
    }


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
    // Поиск закрывается «Назад» раньше, чем список едет наверх: объявлен
    // позже — значит, спрашивается первым (1.6.0)
    BackHandler(enabled = searching, onBack = closeSearch)


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


    val baseList = when {
        state.query.isNotEmpty() -> state.filteredLog
        activeFilter.isActive -> filteredByChips
        else -> state.callLog
    }
    /*
     * Записи, удалённые с «Отменить» (1.5.7), пропадают сразу, а из журнала
     * уходят, когда сообщение погаснет: разбор в deleteGroupsWithUndo.
     */
    val pendingDeletion by vm.pendingDeletion.collectAsStateWithLifecycle()
    /*
     * АРХИВ (2.1.0): карточки, все записи которых в архиве, в журнал
     * не попадают — было: только удаление, стало ещё «В архив» в режиме
     * выбора. Скрываются тем же путём, что удалённые с «Отменить»: ключом
     * в общем наборе — и для `list`, и для `rows` ниже.
     */
    val archivedIds by vm.callArchivedIds.collectAsStateWithLifecycle()
    val hiddenKeys = remember(baseList, pendingDeletion, archivedIds) {
        pendingDeletion + com.ogneliss.dialer.data.ArchiveRules.archivedGroupKeys(baseList, archivedIds)
    }
    val list = remember(baseList, hiddenKeys) {
        if (hiddenKeys.isEmpty()) baseList else baseList.filterNot { it.key in hiddenKeys }
    }

    /*
     * ДЕНЬ НА ЧАСАХ — ДЛЯ «СЕГОДНЯ» И «ВЧЕРА» (1.8.0).
     *
     * Подпись «Сегодня, 18:02» собирается вместе со строкой, а строки
     * с 1.8.0 снова берутся из прошлой сборки, пока не изменились их
     * данные. Сам по себе журнал в полночь не меняется — и без этих часов
     * «Сегодня» висело бы до следующего звонка. Здесь полночь входит
     * в ключ пересборки: наступила — пересобираются звонки последних двух
     * дней (остальные берутся готовыми).
     *
     * Пока экран виден — ждём полночь; ушли и вернулись — день сверяется
     * сразу: ожидание в фоне с телефоном во сне не идёт.
     */
    var today by remember { mutableLongStateOf(CallLogRowMapper.Days().today) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                val days = CallLogRowMapper.Days()
                today = days.today
                delay(days.untilTomorrow() + 1_000L)
            }
        }
    }

    /*
     * Напоминания на сегодня — для полосы над журналом (1.6.0): все
     * до полуночи, ближайшее первым. Сработавшие уже убраны (reminders.active).
     */
    // Ключ today (1.8.0): после полуночи «сегодня» — уже другой день
    val todayReminders = remember(reminders, today) {
        val endOfToday = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        reminders.filter { it.atMillis < endOfToday }.sortedBy { it.atMillis }
    }

    /*
     * НОВЫЕ ВЫЗОВЫ, ПОКА ЛИСТАЛИ (1.5.8, обзор интерфейса, У4).
     *
     * Пока список в самом начале, всё, что в нём есть, считается увиденным:
     * запоминается время самого свежего вызова. Ушли вниз — запоминание
     * останавливается, и всё, что пришло позже, — «новое»: его число
     * показывает кнопка «Наверх».
     *
     * По времени, а не по положению строк: повторный звонок того же
     * человека поднимает его запись наверх, а не добавляет новую — по
     * положению его бы не заметили. При поиске не считаем: там список
     * не журнал, а выдача.
     */
    val listAtTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var seenNewest by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listAtTop, list) {
        if (listAtTop || seenNewest == 0L) seenNewest = list.maxOfOrNull { it.lastDate } ?: 0L
    }
    val newCalls = remember(list, seenNewest, listAtTop, state.query) {
        if (listAtTop || state.query.isNotEmpty() || seenNewest == 0L) 0
        else list.count { it.lastDate > seenNewest }
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

    val allRows by vm.rows.collectAsStateWithLifecycle()

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
    val rows = remember(allRows, list, activeFilter, state.query, hiddenKeys) {
        val shown = if (!activeFilter.isActive && state.query.isEmpty()) {
            allRows
        } else {
            val visible = list.mapTo(HashSet()) { it.key }
            allRows.filter { it.key in visible }
        }
        if (hiddenKeys.isEmpty()) shown else shown.filterNot { it.key in hiddenKeys }
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
                isContact = true,
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


    val gestureHintShown by vm.gestureHintShown.collectAsStateWithLifecycle()
    if (!gestureHintShown && list.isNotEmpty()) {
        GestureHintSheet(onDismiss = { vm.markGestureHintShown() })
    }


    val pinnedNumbers by vm.pinnedNumbers.collectAsStateWithLifecycle()


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
    /*
     * ЗАКРЕПЛЁННЫЙ — КОНТАКТ (1.3.2, 03.10.2026).
     * Было (1.2.4–1.3.1): закреплялся номер, а контакт подбирался к нему
     * здесь, на каждом пересчёте. Строка «без звонков» при этом не рисовала
     * фото и открывала карточку по номеру — после очистки журнала автор
     * видел закреплённого без портрета и без реакции на нажатие.
     * Стало: в записи хранится ключ контакта (устройство — PinnedContacts),
     * строка относится к закреплённому, если это звонок с любого номера
     * этого контакта; строка без звонков — с фото и открывает карточку
     * контакта тем же путём, что экран «Контакты».
     */
    val pins = remember(pinnedNumbers) { pinnedNumbers.map(PinnedContacts::parse) }
    val lookupById = remember(state.contacts) {
        state.contacts.associate { it.id to it.lookupKey }
    }
    /*
     * Номер → контакт по книге на экране (2.0.0): закреплённый человек
     * с двумя номерами находит строку звонка на любой из них, даже если
     * журнал сгруппирован до того, как книга дочиталась.
     */
    val lookupByNumber = remember(state.contacts) {
        buildMap {
            state.contacts.forEach { contact ->
                contact.numbers.forEach { n ->
                    if (n.e164.isBlank()) return@forEach
                    putIfAbsent(n.e164, contact.lookupKey)
                    com.ogneliss.dialer.data.CallLogGrouper.tailKey(n.e164)?.let { putIfAbsent(it, contact.lookupKey) }
                }
            }
        }
    }
    fun pinIndexOf(row: CallLogRowState): Int? {
        val lookup = row.group.contactId?.let { lookupById[it] }
            ?: lookupByNumber[row.e164]
            ?: com.ogneliss.dialer.data.CallLogGrouper.tailKey(row.e164)?.let { lookupByNumber[it] }
        return PinnedContacts.indexOf(pins, row.e164, lookup).takeIf { it >= 0 }
    }

    val pinnedRows = remember(rows, pins, lookupById, lookupByNumber, state.query) {
        if (pins.isEmpty() || state.query.isNotEmpty()) emptyList()
        else {
            // Строки идут от новых к старым: у контакта остаётся последний звонок
            rows.mapNotNull { row -> pinIndexOf(row)?.let { it to row } }
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
    val pinnedWithoutCalls = remember(pins, pinnedRows, state.contacts, state.query) {
        if (pins.isEmpty() || state.query.isNotEmpty()) emptyList()
        else {
            val covered = pinnedRows.mapNotNullTo(HashSet()) { pinIndexOf(it) }
            val byLookup = state.contacts.associateBy { it.lookupKey }
            val byNumber = buildMap {
                state.contacts.forEach { contact ->
                    contact.numbers.forEach { number -> if (number.e164.isNotBlank()) putIfAbsent(number.e164, contact) }
                }
            }

            pins.withIndex()
                .filterNot { it.index in covered }
                .mapNotNull { (_, pin) ->
                    // Контакт — по ключу; не нашёлся (удалён, ключ сменился) —
                    // по запасному номеру, как закреплённый номер
                    val contact = pin.lookupKey?.let { byLookup[it] } ?: byNumber[pin.e164]
                    val e164 = pin.e164.ifEmpty { contact?.numbers?.firstOrNull()?.e164.orEmpty() }
                    if (e164.isEmpty()) return@mapNotNull null
                    PinnedOnlyRow(
                        key = pin.raw,
                        e164 = e164,
                        title = contact?.displayName ?: PhoneFormatter.format(e164),
                        photoUri = contact?.photoUri,
                        openKey = contact?.let { "contact:${it.id}" } ?: "number:$e164"
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
     *
     * С 1.8.1 — по дням, как лента Ogneliss Mail (разбор в JournalDays):
     * «СЕГОДНЯ», «ВЧЕРА», «29 октября · ЧЕТВЕРГ». Заодно — день каждой
     * строки для плавающей даты (FloatingDatePill).
     */
    val dayList by produceState(
        initialValue = emptyList<Pair<Long, List<CallLogRowState>>>() to emptyMap<String, Long>(),
        unpinnedRows
    ) {
        value = withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val groups = unpinnedRows.groupBy { JournalDays.dayStart(it.group.lastDate, now) }
                .toList()
                .sortedByDescending { it.first }
            val dayOfKey = HashMap<String, Long>(unpinnedRows.size * 2)
            groups.forEach { (day, rowsOfDay) -> rowsOfDay.forEach { dayOfKey[it.key] = day } }
            groups to dayOfKey
        }
    }
    val dateGroups = dayList.first

    // Подписи дней — на языке приложения; пересчёт — вместе с разбивкой
    // (она пересобирается и в полночь: «Сегодня» становится «Вчера»)
    val dayLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val todayText = stringResource(R.string.date_group_today)
    val yesterdayText = stringResource(R.string.date_group_yesterday)
    val dayLabels = remember(dateGroups, dayLocale, todayText, yesterdayText) {
        val now = System.currentTimeMillis()
        val dayMonth = android.text.format.DateFormat.getBestDateTimePattern(dayLocale, "dMMMM")
        val dayMonthYear = android.text.format.DateFormat.getBestDateTimePattern(dayLocale, "dMMMMy")
        dateGroups.associate { (day, _) ->
            day to JournalDays.label(day, now, dayLocale, todayText, yesterdayText, dayMonth, dayMonthYear)
        }
    }
    val pinnedHeader = stringResource(R.string.pinned_header)
    val pinnedKeys = remember(pinnedRows, pinnedWithoutCalls) {
        buildSet {
            pinnedRows.forEach { add("pin_${it.key}") }
            pinnedWithoutCalls.forEach { add("pin_only_${it.key}") }
        }
    }
    val dayOfKey = dayList.second
    // Подпись дня для плавающей даты по ключу строки ленты; закреплённые —
    // «Закреплённые», как в почте; подписи разделов, поиск, напоминания — нет
    val dayLabelForKey: (Any) -> String? = remember(dayOfKey, dayLabels, pinnedKeys, pinnedHeader) {
        { key ->
            val rowKey = key as? String
            when {
                rowKey == null -> null
                rowKey in pinnedKeys -> pinnedHeader
                else -> dayOfKey[rowKey]?.let { dayLabels[it] }
            }
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
            /*
             * Заметки — по самому списку, а не по числу (1.8.0): поправили
             * текст заметки — число то же, ключ был прежним, и строка
             * показывала старый текст до следующего звонка. Новый список
             * заметок приходит только при их изменении.
             */
            h = 31 * h + System.identityHashCode(state.notes)
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
             * Фотографии теперь входят в отпечаток книги ниже — вместе
             * с именами и номерами (1.8.0): прежний размер карты фотографий
             * в этом месте не обновлялся вовсе, разбор там же.
             */
            h = 31 * h + state.sims.size
            /*
             * Режим показа оператора — тоже часть ключа.
             *
             * Иначе переключение настройки «Оператор и регион»
             * не перестраивало бы уже собранные строки: список
             * менялся бы только после следующего звонка.
             */
            h = 31 * h + settings.carrierInfo.ordinal
            // «Как показывать имя» (1.6.2): строки пересобираются с новым именем
            h = 31 * h + settings.contactNameFormat.ordinal
            /*
             * Имена из книги (1.7.0): переименовали контакт — число контактов
             * то же, а строки должны пересобраться. С 1.8.0 — и фото, и номера:
             * раньше здесь стоял размер карты фотографий, но карта — обычная
             * переменная экрана, и внутри derivedStateOf она осталась такой,
             * какой была при первом показе (та же ошибка, что разобрана выше
             * про список). Сменили фото — строка его не получала.
             */
            h = 31 * h + state.contacts.sumOf { contact ->
                var c = contact.displayName.hashCode()
                c = 31 * c + (contact.photoUri?.hashCode() ?: 0)
                contact.numbers.forEach { c = 31 * c + it.e164.hashCode() }
                c
            }
            // Полночь (1.8.0): «Сегодня» становится «Вчера» — разбор у today
            h = 31 * h + today.hashCode()

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


    // Подписи и формат даты — тоже ключи: сменились — строки собираются заново
    LaunchedEffect(rowsKey, infoLabels, dateFormat) {
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


    val scrollToTop by vm.scrollToTop.collectAsStateWithLifecycle()
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

    /*
     * Поиск журнала — из «Ещё» (2.0.0): на нижней панели теперь разделы.
     * Тот же путь, что прежняя кнопка: клавиатура прячется, поле — на месте
     * строки кнопок.
     */
    val startSearch: () -> Unit = {
        dialpadExpanded = false
        vm.onQueryChange("")
        searching = true
    }

    // Фото своего контакта в меню — разбор у ownPhotoUriOf (MainDialerParts)
    val ownPhotoUri = remember(ownNumber, state.contacts) { ownPhotoUriOf(ownNumber, state.contacts) }

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
                // Клавиатура набора и поле поиска не открыты вместе (1.6.0)
                if (searching) closeSearch()
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
            // Переключатель «журнал | контакты» (1.6.0): разбор в ListSwitchButton
            onSwitchList = onOpenContacts,
            onOpenSection = onOpenSection,
            onFilters = { filtersOpen = true },
            onVoice = { voiceLauncher.launch(VoiceInput.createIntent(context, voicePrompt)) },
            onStartSelection = {
                if (selection.isActive) selection.clear() else selection.startEmpty()
            },
            selectionActive = selection.isActive,
            /*
             * «Поиск» — на месте «Напоминаний» (1.6.0, вариант «Б»):
             * сегодняшние напоминания — полосой над журналом, все — в меню.
             */
            onSearch = startSearch,
            searching = searching,
            searchHint = stringResource(R.string.journal_search_hint),
            onSearchChange = { vm.onQueryChange(it) },
            onCloseSearch = closeSearch,
            onMenu = { menuOpen = true },
            // 2.1.0: влево — соседний раздел «SMS», а не контакты через голову
            // (карусель журнал → SMS → контакты → заметки → напоминания)
            onSwipeToContacts = if (settings.panelSwipeNav) { { onOpenSection(MainSection.MESSAGES) } } else null,
            menuContent = {
                OverflowMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    settings = settings,
                    // Свой номер: пункт показывает его или «не указан»,
                    // чтобы было видно, готово ли приложение делиться
                    // карточкой
                    ownNumber = ownNumber,
                    ownPhotoUri = ownPhotoUri,
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
                            // 2.1.0: своя карточка — все номера, почта, профиль «Я»
                            onOpenSubscriber(com.ogneliss.dialer.ui.screens.OWN_CARD_KEY)
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
                    onSettings = { menuOpen = false; onOpenSettings() },
                    onFakeCall = { menuOpen = false; fakeCallOpen = true },
                    onNotes = { menuOpen = false; onOpenNotes() },
                    onReminders = { menuOpen = false; onOpenReminders() },
                    onHelp = { menuOpen = false; onOpenHelp() },
                    /*
                     * «Папки» журнала (2.2.2) — как папки в меню почты и SMS:
                     * готовые наборы фильтра (JournalFolder) и «Архив».
                     * Свой набор условий из «Фильтров» папкой не считается —
                     * тогда в списке ничего не выделено.
                     */
                    header = {
                        val current = JournalFolder.of(activeFilter)
                        MenuFolderRows(
                            title = stringResource(R.string.settings_sms_folders),
                            folders = JournalFolder.entries.map { f ->
                                MenuFolder(journalFolderIcon(f), stringResource(journalFolderLabel(f)), selected = f == current) {
                                    menuOpen = false
                                    vm.setFilter(f.filter())
                                }
                            } + MenuFolder(
                                // Свой набор условий (карта, период, заметка…) — «Фильтры · N» (2.4.0)
                                OgnelissIcons.FilterList,
                                stringResource(R.string.filter_title),
                                count = if (activeFilter.isActive && current == null) activeFilter.activeCount else 0,
                                selected = activeFilter.isActive && current == null
                            ) {
                                menuOpen = false
                                filtersOpen = true
                            } + MenuFolder(OgnelissIcons.Archive, stringResource(R.string.archive_title)) {
                                menuOpen = false
                                onOpenArchive()
                            }
                        )
                    },
                    onSearch = { menuOpen = false; startSearch() }
                )
            }
        )
    }

    Scaffold(
        // Под карточками долгого нажатия экран размывается (1.5.7)
        // Размытие под карточками — в корне (RootScreen.kt, LocalCardsBlur, 1.8.0)
        modifier = Modifier,
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
            // При поиске кнопка висела бы над клавиатурой — прячем (1.6.0)
            if (settings.fakeCallButton && !sideDialpad && !searching) SmallFloatingActionButton(
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
                    OgnelissIcons.PhoneCallback,
                    contentDescription = stringResource(R.string.fake_call_title)
                )
            }
        },
        topBar = {
            /*
             * ШАПКА РАЗДЕЛА — КАК В OGNELISS MAIL (2.2.1, SectionTopBar).
             *
             * С 1.6.0 заголовка сверху не было («понятно и так, что перед
             * тобой журнал»), а поиск, фильтры и действия над списком лежали
             * плитками в «Ещё». Автор, 11.11.2026: «за референс UI возьмём
             * Ogneliss Mail… полностью похожи по организации элементов» —
             * в почте над лентой шапка: что открыто и действия над списком.
             *
             * Шапка не появляется и не исчезает при выборе (2.1.1: панель
             * выбора сверху «сдвигала весь журнал» — она постоянная).
             * С раскрытой клавиатурой набора шапки нет: список и так занимает
             * меньше половины экрана. Клавиатура сбоку (планшет) — шапка есть.
             */
            // Открыта «папка» (пропущенные, входящие…) — её название, раздел — строкой ниже, как в SMS
            val journalFolder = JournalFolder.of(activeFilter)?.takeIf { it != JournalFolder.ALL }
            if (!dialpadExpanded || sideDialpad) SectionTopBar(
                title = stringResource(journalFolder?.let { journalFolderLabel(it) } ?: R.string.nav_journal),
                subtitle = when {
                    journalFolder != null -> stringResource(R.string.nav_journal)
                    activeFilter.isActive -> stringResource(R.string.filter_title) + " · " + activeFilter.activeCount
                    else -> null
                },
                // Поиск и фильтры — в меню (2.4.0, автор: «уберём из верхней панели
                // фильтр (он есть в боковой панели) и перенесём в боковую панель поиск»)
                menu = listOf(
                    TopBarMenuItem(
                        if (selection.isActive) OgnelissIcons.Close else OgnelissIcons.Checklist,
                        stringResource(if (selection.isActive) R.string.selection_exit else R.string.selection_start)
                    ) { if (selection.isActive) selection.clear() else selection.startEmpty() },
                    // «Архив» — только папкой в «Меню» (2.4.0, автор: «оставим только
                    // в панели»): одно место для одного перехода
                    // Только после подтверждения: очистка необратима и стирает
                    // и системный журнал (разбор у confirmClearLog)
                    TopBarMenuItem(
                        OgnelissIcons.DeleteSweep, stringResource(R.string.log_clear_all), destructive = true
                    ) { confirmClearLog = true }
                )
            )
        },
        bottomBar = {
            // Внизу — только когда клавиатура не стоит сбоку: разбор у sideDialpad
            /*
             * Панель внизу уезжает и возвращается плавно — в паре
             * с клавиатурой сбоку (разбор ниже, у боковой панели).
             * На телефоне в портрете sideDialpad всегда false: панель
             * видна с самого начала, анимации нет.
             */
            // Лёжа кнопки — полосой справа, и поле поиска встаёт внизу
            // отдельной панелью (1.6.0)
            if (searching && landscapeRail) {
                SearchPanel(
                    value = state.query,
                    onValueChange = { vm.onQueryChange(it) },
                    hint = stringResource(R.string.journal_search_hint),
                    onClose = closeSearch
                )
            }
            // Режим выбора (2.1.1): действия — на месте панели разделов
            if (selection.isActive) {
                SelectionBottomBar(
                    count = selection.count,
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(list.map { it.key }) },
                    onBlock = {
                        vm.blockGroups(list.filter { it.key in selection.selected })
                        selection.clear()
                    },
                    // 2.1.0: «В архив» — убрать из журнала, не стирая
                    onArchive = {
                        vm.archiveGroups(list.filter { it.key in selection.selected })
                        selection.clear()
                    },
                    // Без окна «Удалить N записей?»: есть «Отменить» (правило автора 07.11.2026)
                    onDelete = {
                        vm.deleteGroupsWithUndo(list.filter { it.key in selection.selected })
                        selection.clear()
                    }
                )
            }
            AnimatedVisibility(
                // Лёжа панель справа — внизу её нет вовсе; при выборе — панель выбора
                visible = !sideDialpad && !landscapeRail && !selection.isActive,
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
            Box(Modifier.weight(1f).fillMaxHeight().trackTouch(touchAnchor)) {
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
                // 1.5.3, сплошной список: строки во всю ширину без зазоров —
                // в обоих видах журнала нужны линии между строками, иначе
                // «Карточки» слились бы в сплошное поле
                val groupPositionOf: (Int, Int) -> GroupPosition = { i, n ->
                    GroupPosition.of(i, n)
                }

                val selectableKeys = remember(list) { list.mapTo(HashSet()) { it.key } }
                LazyColumn(
                    state = listState,
                    // Тень у края прокрутки (2.1.2, Д7 — как в Mail)
                    modifier = Modifier.padding(padding).fillMaxSize().scrollEdgeFade(listState)
                        // Выбор протягиванием по кружкам (2.1.3, DragSelect): ключи строк —
                        // с приставками закреплённых и найденных
                        .dragSelect(selection, listState) { key ->
                            (key as? String)
                                ?.removePrefix("found_")?.removePrefix("pin_only_")?.removePrefix("pin_")
                                ?.takeIf { it in selectableKeys }
                        },
                    /*
                     * Под кнопкой псевдозвонка — запас (1.5.7): круглая кнопка
                     * висит над правым краем списка и закрывала кнопку вызова
                     * последней строки, а прокрутить строку выше было некуда.
                     */
                    contentPadding = PaddingValues(
                        top = Space.xs,
                        bottom = if (settings.fakeCallButton && !sideDialpad) FAB_CLEARANCE else Space.xs
                    )
                ) {
                    /*
                     * Напоминания на сегодня — полосой над журналом (1.6.0,
                     * вариант «Б»): кнопки «Напоминания» на панели больше нет,
                     * а сегодняшние должны быть на виду. Только когда они есть
                     * и журнал показан целиком — не при поиске, отборе и выборе.
                     */
                    /*
                     * ПОДСКАЗКА О СВАЙПАХ — ОДИН РАЗ (2.2.2, форум и автор:
                     * «надо краткое обучение добавить»). Говорит, что сделают
                     * свайпы СЕЙЧАС (действия из настроек) и что строку нужно
                     * довести и отпустить. Уходит по «Понятно» и после первого
                     * свайпа (DialerViewModel.performSwipe).
                     */
                    if (!swipeHintDone && state.query.isEmpty() && !selection.isActive &&
                        (settings.swipeRight != com.ogneliss.dialer.settings.SwipeAction.NONE ||
                            settings.swipeLeft != com.ogneliss.dialer.settings.SwipeAction.NONE)
                    ) {
                        item(key = "swipe_hint", contentType = "swipe_hint") {
                            com.ogneliss.dialer.ui.conversation.Strip(
                                text = stringResource(
                                    R.string.swipe_hint_text,
                                    stringResource(com.ogneliss.dialer.settings.SettingsCommands.swipeActionLabelRes(settings.swipeRight)),
                                    stringResource(com.ogneliss.dialer.settings.SettingsCommands.swipeActionLabelRes(settings.swipeLeft))
                                ),
                                icon = OgnelissIcons.Swipe,
                                action = stringResource(R.string.swipe_hint_ok),
                                onAction = { scope.launch { vm.prefs.setSwipeHintDone() } },
                                critical = false
                            )
                        }
                    }

                    if (todayReminders.isNotEmpty() && state.query.isEmpty() &&
                        !activeFilter.isActive && !selection.isActive
                    ) {
                        item(key = "today_reminders", contentType = "today_reminders") {
                            TodayRemindersStrip(todayReminders, onOpen = onOpenReminders)
                        }
                    }

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
                                avatarSelects = false,
                                onOpenSubscriber = onOpenSubscriber,
                                onLongPress = {
                                    selectedAnchorY = touchAnchor.screenY
                                    selectedGroup = it
                                },
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
                                // Кнопка есть только у «нет доступа» (разбор у requestCallLog)
                                onAction = requestCallLog
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
                                onLongPress = {
                                    selectedAnchorY = touchAnchor.screenY
                                    selectedGroup = it
                                },
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
                            key = { "pin_only_${it.key}" },
                            contentType = { "pinned_only_row" }
                        ) { row ->
                            PinnedOnlyCard(
                                row = row,
                                onCall = { vm.call(row.e164) },
                                onCallLongPress = if (state.sims.size > 1) {
                                    { vm.chooseSim(row.e164, DialerViewModel.SimAction.CALL, row.title) }
                                } else null,
                                // «contact:» для контакта из книги, «number:» —
                                // для номера не из книги (1.3.2); голый номер
                                // карточка не распознавала и закрывалась
                                onOpen = { onOpenSubscriber(row.openKey) }
                            )
                        }
                    }


                    dateGroups.forEach { (day, rowsInBucket) ->
                        item(key = "day_$day", contentType = "date_header") {
                            val dayText = dayLabels[day].orEmpty()
                            SectionBand(dayText, description = spokenDayLabel(dayText, dayLocale))
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
                                onLongPress = {
                                    selectedAnchorY = touchAnchor.screenY
                                    selectedGroup = it
                                },
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

                // Плавающая дата (1.8.1, как в Ogneliss Mail): пока листают —
                // день верхней строки по центру сверху
                FloatingDatePill(
                    listState = listState,
                    labelForKey = dayLabelForKey,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = padding.calculateTopPadding() + Space.s)
                )

                // «Наверх» — при прокрутке вверх далеко от начала (1.5.7);
                // с числом вызовов, пришедших, пока листали (1.5.8).
                // Ниже плавающей даты, как «↑ N новых» в почте: не закрывает её
                ScrollToTopPill(
                    listState,
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = padding.calculateTopPadding() + Space.xxl + Space.m),
                    newCount = newCalls
                )
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
                            .padding(end = Space.s, bottom = Space.s)
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
                        .padding(end = Space.s, bottom = Space.s)
                ) {
                    controlPanel(Modifier.fillMaxHeight(), false, null, true)
                }
            }
        }
    }


    // ── Всплывающие элементы ───────────────────────────────────────────────


    val noteEditorFor by vm.noteEditorFor.collectAsStateWithLifecycle()
    noteEditorFor?.let { group ->
        val existing = state.notes[group.e164]
        NoteEditorDialog(
            displayName = group.displayName ?: PhoneFormatter.format(group.number),
            initialNote = existing?.note.orEmpty(),
            initialTags = existing?.tags?.toSet().orEmpty(),
            onSave = { text, tags ->
                vm.saveNote(group.e164, text, tags.toList())
                vm.closeNoteEditor()
            },
            onDismiss = { vm.closeNoteEditor() }
        )
    }


    val reminderFor by vm.reminderRequest.collectAsStateWithLifecycle()
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


    val simRequest by vm.simRequest.collectAsStateWithLifecycle()


    LaunchedEffect(simRequest) {
        if (simRequest == null) swipeController.closeAll()
    }


    val closeSwipesToken by vm.closeSwipes.collectAsStateWithLifecycle()
    LaunchedEffect(closeSwipesToken) {
        if (closeSwipesToken > 0) swipeController.closeAll()
    }
    // Карточки выбора SIM показывает корневой экран — SimChoiceOverlay, 1.8.0;
    // здесь — только возврат сдвинутой строки, когда выбор закрыт


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
        val scheduledAt by vm.fakeCallAt.collectAsStateWithLifecycle()
        val caller by vm.fakeCallCaller.collectAsStateWithLifecycle()
        val timeFormat = remember {
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        }

        OgnelissDialog(
            onDismissRequest = { fakeCallOpen = false },
            title = stringResource(R.string.fake_call_title),
            content = {
                // 2.1.0: без пояснения «экран как у входящего, но вызова нет» —
                // правило автора 07.11.2026, пояснений не показываем

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
                    /*
                     * Поле выбора, а не действие (1.3.4, 04.10.2026, аудит:
                     * «Кто звонит» оформлен так же, как действия). Контурная
                     * рамка без заливки и стрелка справа — это настройка;
                     * залитые плитки ниже — действия.
                     */
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    // Отступ как у соседних строк окна: подпись в две
                    // строки прижималась к краю плитки, остальные пункты
                    // стоят с полем
                    Column(
                        // contentPadding — это Dp; cardPadding здесь
                        // не подходит, он PaddingValues и задаёт обе оси
                        Modifier.padding(
                            horizontal = FlowUi.contentPadding,
                            vertical = Space.m
                        ),
                        verticalArrangement = Arrangement.spacedBy(Space.xxs)
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
                    Spacer(Modifier.weight(1f))
                    Icon(
                        OgnelissIcons.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Space.m)
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
                        FakeCallTileText(stringResource(labelRes))
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
                    FakeCallTileText(stringResource(R.string.fake_call_pick_time))
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
            // Окно то же, что у напоминания; шапка — псевдозвонок (1.8.0)
            displayName = "",
            title = stringResource(R.string.fake_call_title),
            icon = OgnelissIcons.PhoneCallback,
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
            onChooseSmsSim = { number ->
                vm.chooseSim(number, DialerViewModel.SimAction.SMS, group.displayName)
                pickerFor = null
            },
            onDismiss = { pickerFor = null }
        )
    }


    selectedGroup?.let { group ->
        RecordActionCards(
            group = group,
            vm = vm,
            anchorY = selectedAnchorY,
            origin = CardsOrigin.LOG,
            onOpenCard = { onOpenSubscriber(group.key) },
            onOpenBlockLists = onOpenBlockLists,
            onOpenPinned = onOpenPinned,
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


    if (pendingWarning != null) {
        /*
         * Предупреждение перед звонком на платный номер. Значок остаётся:
         * цена ошибки здесь — деньги со счёта, и предупреждение должно
         * опознаваться раньше, чем прочитано. 2.1.0: только платные —
         * о международном больше не спрашиваем (PreDialWarning).
         */
        OgnelissDialog(
            onDismissRequest = { pendingWarning = null; pendingCall = null },
            title = stringResource(R.string.predial_title),
            content = {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        OgnelissIcons.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(Space.m))
                    Text(stringResource(R.string.predial_premium))
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
        // 2.1.0: долгое «1» на пустом поле — голосовая почта (было: ничего)
        query.isEmpty() && digit == '1' -> vm.actions.placeVoicemail()
        query.isEmpty() && digit in '2'..'9' -> {
            val dialed = vm.dialSpeedDial(digit.digitToInt())
            if (!dialed) onAssignSpeedDial(digit.digitToInt())
        }
    }
}

/** Запас под круглой кнопкой псевдозвонка: 40 dp кнопки и 16 dp поля. */
internal val FAB_CLEARANCE = 64.dp
