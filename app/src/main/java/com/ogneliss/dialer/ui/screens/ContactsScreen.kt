package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.components.ActionIconButton
import com.ogneliss.dialer.ui.theme.listName
import com.ogneliss.dialer.settings.ContactSort
import com.ogneliss.dialer.data.ContactNames
import com.ogneliss.dialer.ui.components.SectionBand
import com.ogneliss.dialer.ui.components.RowAvatar
import androidx.compose.foundation.lazy.itemsIndexed
import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.ui.theme.CallGreen
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.ui.components.ScrollToTopPill
import com.ogneliss.dialer.ui.components.rememberTouchAnchor
import com.ogneliss.dialer.ui.components.trackTouch
import com.ogneliss.dialer.ui.components.ContactFilterSheet
import com.ogneliss.dialer.ui.components.CallLogSkeleton
import com.ogneliss.dialer.ui.components.swipe.RevealAction
import com.ogneliss.dialer.ui.components.swipe.SwipeRevealController
import com.ogneliss.dialer.ui.components.swipe.SwipeRevealRow
import com.ogneliss.dialer.ui.components.swipe.rememberSwipeRevealController
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.data.ContactFilter
import com.ogneliss.dialer.ui.components.ContactsBottomBar
import com.ogneliss.dialer.ui.components.ListPanel
import com.ogneliss.dialer.ui.components.MainSection
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.util.TextFold
import androidx.compose.runtime.saveable.rememberSaveable
import com.ogneliss.dialer.ui.components.EmptyReason
import com.ogneliss.dialer.ui.components.EmptyState
import com.ogneliss.dialer.ui.components.HighlightedName
import com.ogneliss.dialer.ui.theme.LocalAppearance
import com.ogneliss.dialer.data.RulePattern
import com.ogneliss.dialer.util.PhoneFormatter
import kotlinx.coroutines.launch

/**
 * ЭКРАН КОНТАКТОВ.
 *
 * ── Что добавилось по сравнению с первой версией ───────────────────────────
 * Было: плоский список без поиска и без пустых состояний. На книге
 * в тысячу контактов это означало бесконечную прокрутку.
 *
 * Стало:
 * • поиск по имени;
 * • буквенные разделители — по ним видно, где ты находишься при прокрутке;
 * • пустые состояния с объяснением причины;
 * • избранные отдельной вкладкой;
 * • переход в карточку долгим нажатием.
 *
 * ── Почему разделители, а не алфавитный указатель сбоку ────────────────────
 * Указатель-полоска с буквами требует точного попадания пальцем
 * в зону шириной 20 dp — при прокрутке большим пальцем на ходу это
 * почти невозможно. Разделители же работают пассивно: они просто
 * показывают, где ты, и не требуют прицеливания.
 *
 * Для быстрого перехода есть поиск, который надёжнее любого указателя.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    vm: DialerViewModel,
    onBack: () -> Unit,
    /** Псевдозвонок круглой кнопкой. */
    onFakeCall: () -> Unit = {},
    /** Следующий экран карусели: заметки. */
    onOpenNotes: () -> Unit = {},
    /**
     * Режим выбора номера для закрепления.
     *
     * Экран тот же, что обычный список: заводить второй ради выбора
     * означало бы поддерживать два поиска, две группировки и два набора
     * свайпов — и они разошлись бы при первой же правке.
     *
     * Отличий три: заголовок объясняет задачу, нажатие возвращает
     * выбранное вместо открытия карточки, а свайпы выключены —
     * в режиме выбора они только мешают.
     */
    pickForPinning: Boolean = false,
    onPicked: (String) -> Unit = {},
    /**
     * Выбор самого контакта, а не его номера (1.7.0): «Добавить номер
     * к контакту» — выбранный открывается в редакторе с новым номером.
     * null — выбор номера, как для закрепления.
     */
    onPickedContact: ((Long) -> Unit)? = null,
    /** Заголовок режима выбора. */
    pickTitleRes: Int = R.string.pinned_pick_title,
    onOpenContact: (Long) -> Unit = {},
    /** Чёрный список — из карточек долгого нажатия (блокировка по маске). */
    onOpenBlockLists: () -> Unit = {},
    /**
     * Переход между разделами (1.6.0, вариант «Б»): журнал — касанием
     * переключателя на панели, остальные — из списка разделов и меню.
     */
    onOpenSection: (MainSection) -> Unit = {},
    /** Клавиатура набора — в журнале (разбор у DialerViewModel.requestDialpad). */
    onOpenDialpad: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    /** «Вопросы и ответы» из меню (1.6.0). */
    onOpenHelp: () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Режим выбора: контакт целиком (1.7.0) или его первый номер
    val pickContact: (Contact) -> Unit = { contact ->
        if (onPickedContact != null) {
            onPickedContact(contact.id)
        } else {
            contact.numbers.firstOrNull()?.let { onPicked(it.e164) }
        }
    }

    /*
     * ДОЛГОЕ НАЖАТИЕ — КАРТОЧКИ ДЕЙСТВИЙ (1.5.7), как в журнале.
     * Было: долгое нажатие повторяло обычное — открывало карточку.
     */
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var selectedAnchorY by remember { mutableStateOf<Float?>(null) }
    val touchAnchor = rememberTouchAnchor()

    /*
     * ЗАПРОС КОНТАКТОВ ПРИ ОТКРЫТИИ ЭКРАНА.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Список мог остаться пустым после запуска: провайдер контактов
     * занят синхронизацией с аккаунтом первые секунды, и чтение
     * возвращает пустоту.
     *
     * Раньше это лечилось поворотом экрана — он вызывал повторное чтение
     * случайно. Здесь запрос делается осознанно и только при пустом
     * списке: если контакты уже загружены, лишнего чтения не будет.
     */
    LaunchedEffect(Unit) {
        if (state.contacts.isEmpty()) vm.reloadContacts()
    }
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val rightExtra by vm.swipeRightExtra.collectAsStateWithLifecycle()
    val leftExtra by vm.swipeLeftExtra.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }

    /*
     * ПОИСК — ПО КНОПКЕ НА ПАНЕЛИ (1.6.0, вариант «Б»).
     *
     * Поле «Имя» было на панели всегда и занимало её целиком. Теперь
     * у контактов та же строка кнопок, что у журнала, а поле появляется
     * по кнопке «Поиск» на её месте (PanelSearchRow). Закрытие — стрелкой
     * или «Назад» — очищает запрос. В режиме выбора (закрепить,
     * псевдозвонок) панель прежняя, с полем на виду.
     */
    var searching by rememberSaveable { mutableStateOf(false) }
    val closeSearch: () -> Unit = {
        searching = false
        search = ""
    }
    var menuOpen by remember { mutableStateOf(false) }

    /*
     * Фильтр списка вместо отдельного экрана аудита.
     *
     * Прежний переключатель «предлагать раз в месяц» не давал добраться
     * до результата по своей воле: человек включал настройку и ждал
     * неизвестно чего. Фильтр доступен в любой момент и находится там,
     * где контакты и разбирают.
     */
    var contactFilter by remember { mutableStateOf(ContactFilter.ALL) }
    var contactFiltersOpen by remember { mutableStateOf(false) }

    if (contactFiltersOpen) {
        ContactFilterSheet(
            current = contactFilter,
            onSelect = { contactFilter = it },
            onDismiss = { contactFiltersOpen = false }
        )
    }
    /*
     * ПОЛОЖЕНИЕ СПИСКА ПЕРЕЖИВАЕТ УХОД В КАРТОЧКУ.
     *
     * То же, что в журнале: rememberLazyListState помнит положение,
     * пока экран в композиции, а переход в карточку её разрушает.
     * Человек прокрутил до буквы «С», открыл контакт, вернулся —
     * и снова листает с «А».
     */
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = vm.contactsScrollIndex,
        initialFirstVisibleItemScrollOffset = vm.contactsScrollOffset
    )

    DisposableEffect(Unit) {
        onDispose {
            vm.rememberContactsScroll(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    /*
     * «Назад» сначала возвращает к началу списка.
     *
     * Прокрутил до «Я», нажал «назад» — вернулся к «А», нажал ещё раз —
     * ушёл с экрана. Обработчик включён только в прокрутанном
     * состоянии, иначе он перехватывал бы обычный выход.
     */
    val canScrollUp by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    BackHandler(enabled = canScrollUp) {
        scope.launch { listState.animateScrollToItem(0) }
    }
    // Поиск закрывается раньше, чем список едет наверх (объявлен позже —
    // спрашивается первым, 1.6.0)
    BackHandler(enabled = searching, onBack = closeSearch)

    // Свайп выключен, пока список прокручивается: палец при листании
    // идёт по дуге, и горизонтальной составляющей хватает, чтобы
    // Compose счёл движение свайпом
    val listScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }

    /*
     * ВОЗВРАТ К НАЧАЛУ ПРИ ИЗМЕНЕНИИ ВЫБОРКИ.
     *
     * Список контактов длинный, и прокрутка от прежней выборки
     * указывает в никуда: при поиске первые совпадения оказываются выше
     * видимой области, а при смене фильтра — вообще на записи,
     * которой в новом списке нет.
     *
     * Тот же приём, что в журнале: одинаковое поведение двух списков
     * не требует привыкания.
     */
    LaunchedEffect(search, contactFilter) {
        if (listState.firstVisibleItemIndex > 0) {
            listState.animateScrollToItem(0)
        }
    }

    // Свайпы в контактах работают так же, как в журнале: один общий
    // координатор не даёт открыть две панели одновременно
    val swipeController = rememberSwipeRevealController()

    // Избранных единицы, фильтрация по флагу дешёвая — фон здесь избыточен
    /*
     * ИМЕНА И ПОРЯДОК (1.6.2): «Как показывать имя» и «Сортировка
     * контактов» — разбор в data/ContactNames.kt. Список показывает копии
     * с именем в выбранной форме; открывается карточка по id, как раньше.
     */
    val nameFormat = settings.contactNameFormat
    val contactSort = settings.contactSort
    val favourites = remember(state.contacts, nameFormat, contactSort) {
        state.contacts.filter { it.starred }
            .let { starred -> if (contactSort == ContactSort.FAMILY) ContactNames.sortedByFamily(starred) else starred }
            .map { ContactNames.present(it, nameFormat) }
    }

    /**
     * Действия свайпа для контакта.
     *
     * Те же, что настроены для журнала: пользователь настраивает жест
     * один раз, и он работает одинаково везде. Разное поведение
     * на разных экранах пришлось бы запоминать отдельно.
     */
    /*
     * Лямбда пересоздаётся только при смене настроек.
     *
     * Раньше она объявлялась в теле composable без remember: на каждой
     * перерисовке возникал новый объект, а Compose сравнивает параметры
     * по равенству — то есть все строки списка считались изменившимися
     * и перерисовывались заново.
     *
     * Сам вызов внутри строки обёрнут в remember по ключу контакта:
     * без этого создание списка объектов и чтение строк из ресурсов
     * повторялось бы на каждый кадр прокрутки для каждой видимой строки.
     * Ровно эта ошибка давала рывки в журнале.
     */
    // Подсказки прямого действия: зависят только от настроек жестов
    // Чувствительность свайпа — общая с журналом: расхождение порогов
    // между экранами читается как «свайп сломан»
    val swipeTuning by vm.swipeTuning.collectAsStateWithLifecycle()

    /*
     * НАБОР ЗАБЛОКИРОВАННЫХ НОМЕРОВ.
     *
     * Считается один раз на список, а не для каждой строки: правил
     * бывает несколько десятков, контактов — тысячи, и проверка
     * «номер против всех правил» в каждой строке означала бы
     * десятки тысяч сравнений на прокрутку.
     *
     * Маски (8800*) раскрываются по номерам самих контактов: обратный
     * порядок — перебирать все номера под каждую маску — стоил бы
     * столько же, но требовал бы держать оба списка целиком.
     */
    val rules by vm.rules.collectAsStateWithLifecycle()

    /*
     * Номера с напоминанием.
     *
     * Нужны дважды: для фильтра «с напоминанием» и для значка в строке.
     * Считаем один раз множеством — проверка вхождения в каждой строке
     * должна быть мгновенной.
     */
    val reminders by vm.activeReminders.collectAsStateWithLifecycle()
    val reminderNumbers = remember(reminders) {
        reminders.mapTo(HashSet()) { it.e164 }
    }
    val blockedNumbers = remember(rules, state.contacts) {
        val blocking = rules.filter { it.isBlock }
        if (blocking.isEmpty()) return@remember emptySet<String>()

        val exact = blocking.filterNot { it.isMask }.mapTo(HashSet()) { it.pattern }
        val masks = blocking.filter { it.isMask }.map { it.pattern }

        buildSet {
            state.contacts.forEach { contact ->
                contact.numbers.forEach { number ->
                    val e164 = number.e164
                    if (e164 in exact || masks.any { RulePattern.matches(it, e164) }) {
                        add(e164)
                    }
                }
            }
        }
    }

    /*
     * ДИАЛОГ ВЫБОРА SIM ПОКАЗЫВАЕТСЯ И ЗДЕСЬ.
     *
     * ── Почему свайп «не работал» ──────────────────────────────────────────
     * performSwipe при двух картах выставлял запрос выбора, но диалог
     * жил только на главном экране. Запрос уходил в пустоту: жест
     * распознан, действие не выполнено, и внешне это выглядело как
     * неработающий свайп.
     *
     * То же правило, что с напоминанием: диалог живёт на том экране,
     * где может понадобиться.
     */
    val simRequest by vm.simRequest.collectAsStateWithLifecycle()

    /*
     * Закрытие диалога возвращает строки на место.
     *
     * Отмена выбора SIM не выполняет действие, но свайп уже сделан
     * и строка сдвинута. Анимация возврата к этому моменту могла быть
     * прервана появлением диалога — именно так карточка и застывала.
     *
     * Команда «закрыть всё» действует независимо от того, что случилось
     * с анимацией.
     */
    LaunchedEffect(simRequest) {
        if (simRequest == null) swipeController.closeAll()
    }

    // Возврат в приложение: строка могла остаться сдвинутой, если
    // действие открыло другое приложение и прервало анимацию
    val closeSwipesToken by vm.closeSwipes.collectAsStateWithLifecycle()
    LaunchedEffect(closeSwipesToken) {
        if (closeSwipesToken > 0) swipeController.closeAll()
    }
    // Карточки выбора SIM показывает корневой экран — SimChoiceOverlay, 1.8.0;
    // здесь — только возврат сдвинутой строки, когда выбор закрыт

    val startHint = remember(settings.swipeRight) {
        vm.directActionAppearance(settings.swipeRight)
    }
    val endHint = remember(settings.swipeLeft) {
        vm.directActionAppearance(settings.swipeLeft)
    }

    val swipeActionsFor: (Contact) -> List<RevealAction> =
        remember(settings.swipeRight, rightExtra, state.sims) {
            { contact ->
                vm.revealActionsFor(
                    settings.swipeRight, rightExtra, contactAsGroup(contact), state.sims
                )
            }
        }
    val swipeEndActionsFor: (Contact) -> List<RevealAction> =
        remember(settings.swipeLeft, leftExtra, state.sims) {
            { contact ->
                vm.revealActionsFor(
                    settings.swipeLeft, leftExtra, contactAsGroup(contact), state.sims
                )
            }
        }
    /*
     * Два-три действия на сторону — панель кнопок, как в журнале (1.2.5);
     * одно — мгновенно, как было.
     */
    val rightMulti = settings.swipeRight != SwipeAction.NONE && rightExtra.isNotEmpty()
    val leftMulti = settings.swipeLeft != SwipeAction.NONE && leftExtra.isNotEmpty()

    // Статистика вызовов нужна фильтрам «часто» и «давно не звонили»
    val callStats = remember(state.callLog) {
        val last = mutableMapOf<String, Long>()
        val count = mutableMapOf<String, Int>()
        state.callLog.forEach { group ->
            last[group.e164] = maxOf(last[group.e164] ?: 0, group.lastDate)
            count[group.e164] = (count[group.e164] ?: 0) + group.count
        }
        last to count
    }

    /*
     * ФИЛЬТРАЦИЯ И ГРУППИРОВКА — В ФОНОВОМ ПОТОКЕ.
     *
     * ── Почему недостаточно remember ───────────────────────────────────────
     * remember не переносит вычисление в другой поток: он лишь запоминает
     * результат между перерисовками. Само вычисление по-прежнему идёт
     * в главном потоке, и на тысяче контактов фильтрация, поиск
     * и разбивка по буквам занимают десятки миллисекунд.
     *
     * Заметно это в двух местах: при открытии экрана и при каждом нажатии
     * в поле поиска — то есть ровно там, где ждут мгновенной реакции.
     *
     * ── Почему всё в одном produceState ────────────────────────────────────
     * Три этапа идут подряд и зависят друг от друга. Разбить их
     * на три produceState значило бы три переключения потока вместо
     * одного и три промежуточных состояния списка на экране.
     *
     * Прежний результат показывается, пока считается новый: список
     * не мигает пустотой при вводе каждой буквы.
     */
    val grouped by produceState(
        initialValue = sortedMapOf<Char, List<Contact>>(),
        state.contacts, contactFilter, callStats, search, state.notes, reminderNumbers,
        nameFormat, contactSort
    ) {
        value = withContext(Dispatchers.Default) {
            /*
             * По фамилии (1.6.2) — пересортировка по правилам языка; буква
             * раздела — по тому же имени, по которому отсортировано: при
             * «Имя Фамилия» и сортировке по фамилии «Андрей Петров» — под «П».
             */
            val ordered = if (contactSort == ContactSort.FAMILY) {
                ContactNames.sortedByFamily(state.contacts)
            } else {
                state.contacts
            }
            val letters = ordered.associate { contact ->
                contact.id to sectionLetter(ContactNames.sortName(contact, contactSort))
            }
            val shown = ordered.map { ContactNames.present(it, nameFormat) }

            val source = ContactFilter.apply(
                contacts = shown,
                filter = contactFilter,
                lastCallByE164 = callStats.first,
                callCountByE164 = callStats.second,
                notedNumbers = state.notes.keys,
                remindedNumbers = reminderNumbers
            )

            // Без учёта регистра и с «ё» = «е», как в журнале (1.6.0, TextFold)
            val query = search.trim()
            val filtered = if (query.isEmpty()) source
            else source.filter { TextFold.contains(it.displayName, query) }

            /*
             * ПОРЯДОК БУКВ — КАК В КНИГЕ (1.5.7).
             *
             * Было toSortedMap() — по коду символа. Латиница в Юникоде
             * раньше кириллицы, и на снимке автора раздел «V» стоял
             * перед «А», а «Ё» ушла бы в самое начало, до «А».
             *
             * Сами контакты приходят из книги уже по правилам языка
             * телефона (COLLATE LOCALIZED в ContactsRepository): по-русски —
             * сначала кириллица, потом латиница, цифры и знаки — в начале.
             * Буквы берут тот же порядок: по первому появлению в полном
             * списке, а не в отфильтрованном — фильтр «Частые» сортирует
             * по числу звонков, и порядок букв сбился бы.
             */
            val letterOrder = HashMap<Char, Int>()
            shown.forEach { contact ->
                letterOrder.getOrPut(letters[contact.id] ?: '#') { letterOrder.size }
            }
            filtered.groupBy { contact -> letters[contact.id] ?: sectionLetter(contact.displayName) }
                .toSortedMap(
                    compareBy<Char> { letterOrder[it] ?: Int.MAX_VALUE }.thenBy { it }
                )
        }
    }

    /*
     * ВОССТАНОВЛЕНИЕ, КОГДА СПИСОК УЖЕ ПОСТРОЕН.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НЕ ПО state.contacts
     * ══════════════════════════════════════════════════════════════════════
     * Раньше эффект ждал появления контактов. Но список на экране —
     * это grouped: контакты, отфильтрованные, отсортированные
     * и разложенные по буквам в фоновом потоке. Между «контакты пришли»
     * и «список построен» проходит кадр-другой, и в этот момент
     * прокручивать всё ещё нечего — положение обрезалось до нуля,
     * как и раньше.
     *
     * Ждём именно то, что показывается.
     *
     * ── Один раз за заход ──────────────────────────────────────────────────
     * Признак в remember: при возврате композиция создаётся заново,
     * и восстановление срабатывает снова. Пока человек на экране,
     * список не дёргается: иначе смена фильтра возвращала бы его
     * к сохранённой позиции.
     */
    var scrollRestored by remember { mutableStateOf(false) }
    LaunchedEffect(grouped.isEmpty()) {
        if (!scrollRestored && grouped.isNotEmpty()) {
            scrollRestored = true
            if (vm.contactsScrollIndex > 0 || vm.contactsScrollOffset > 0) {
                runCatching {
                    listState.scrollToItem(
                        vm.contactsScrollIndex,
                        vm.contactsScrollOffset
                    )
                }
            }
        }
    }


    Scaffold(
        // Под карточками долгого нажатия экран размывается (1.5.7)
        // Размытие под карточками — в корне (RootScreen.kt, LocalCardsBlur, 1.8.0)
        modifier = Modifier,
        // Отступы под системные панели: в edge-to-edge содержимое
        // иначе уезжает под строку состояния и кнопки навигации
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            // Та же кнопка, что в журнале: человек не должен помнить,
            // на каком экране псевдозвонок есть, а на каком нет
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
            // При поиске висела бы над клавиатурой — прячем (1.6.0)
            if (settings.fakeCallButton && !searching) SmallFloatingActionButton(
                onClick = onFakeCall,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    OgnelissIcons.PhoneCallback,
                    contentDescription = stringResource(R.string.fake_call_title)
                )
            }
        },
        /*
         * ВЕРХНЕЙ СТРОКИ БОЛЬШЕ НЕТ (1.6.0, вариант «Б»).
         *
         * Стрелка «назад», заголовок «Контакты» и «Добавить» говорили «вы
         * зашли внутрь журнала», хотя это соседний список. Переход — тем же
         * переключателем на панели, «Создать контакт» — в меню. Сверху,
         * как у журнала, — сразу список.
         *
         * Строка осталась только в режиме выбора (закрепить, псевдозвонок):
         * туда приходят из настроек за одним действием, и стрелка «назад»
         * и заголовок с задачей там на месте.
         */
        topBar = {
            if (pickForPinning) TopAppBar(
                // Заголовок остаётся заголовком: поле поиска переехало
                // в нижнюю панель, где до него дотягивается большой палец
                title = {
                    Text(
                        stringResource(
                            if (pickForPinning) pickTitleRes
                            else R.string.nav_contacts
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            OgnelissIcons.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = {
                    // Поиск переехал вниз: кнопка в верхней панели
                    // больше не нужна
                    IconButton(onClick = { vm.createContact("") }) {
                        Icon(
                            OgnelissIcons.PersonAdd,
                            contentDescription = stringResource(R.string.action_create_contact)
                        )
                    }
                }
            )
        }
        ,
        /*
         * ПОИСК И ФИЛЬТРЫ ВНИЗУ.
         *
         * Раньше поиск открывался кнопкой в верхней панели: чтобы
         * его активировать, приходилось тянуться через весь экран
         * второй рукой. Список контактов пролистывают большим пальцем,
         * и точка входа в поиск должна быть там же.
         */
        bottomBar = {
            if (pickForPinning) {
                // Режим выбора — прежняя панель: поле «Имя» на виду
                ContactsBottomBar(
                    // Фильтры в шторке: фишки занимали две строки экрана
                    onFilters = { contactFiltersOpen = true },
                    onSwipeToCalls = null,
                    onSwipeToNotes = null,
                    query = search,
                    onQueryChange = { search = it },
                    filter = contactFilter,
                    onFilterChange = { contactFilter = it }
                )
            } else {
                // Та же панель, что у журнала (1.6.0): разбор в ListPanel
                val toJournal: () -> Unit = { onOpenSection(MainSection.JOURNAL) }
                ListPanel(
                    section = MainSection.CONTACTS,
                    onSwitchList = toJournal,
                    onOpenSection = onOpenSection,
                    filterActive = contactFilter != ContactFilter.ALL,
                    onFilters = { contactFiltersOpen = true },
                    onDialpad = onOpenDialpad,
                    onSearch = { searching = true },
                    searching = searching,
                    searchQuery = search,
                    searchHint = stringResource(R.string.contacts_search_hint),
                    onSearchChange = { search = it },
                    onCloseSearch = closeSearch,
                    onMenu = { menuOpen = true },
                    // Перелистывание по панели (настройка): вправо — журнал,
                    // влево — заметки, как раньше
                    onSwipeRight = if (settings.panelSwipeNav) toJournal else null,
                    onSwipeLeft = if (settings.panelSwipeNav) onOpenNotes else null,
                    menuContent = {
                        OverflowMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            settings = settings,
                            // Своего номера, очистки журнала и выбора нескольких
                            // у контактов нет — пункты журнала
                            onOwnNumber = null,
                            onThemeToggle = {
                                vm.setChoice(
                                    "theme",
                                    if (settings.theme == ThemeMode.DARK) ThemeMode.LIGHT.name
                                    else ThemeMode.DARK.name
                                )
                                menuOpen = false
                            },
                            onClearLog = null,
                            onSettings = { menuOpen = false; onOpenSettings() },
                            onFakeCall = { menuOpen = false; onFakeCall() },
                            onNotes = { menuOpen = false; onOpenNotes() },
                            onReminders = { menuOpen = false; onOpenReminders() },
                            onStartSelection = null,
                            onAddContact = { menuOpen = false; vm.createContact("") },
                            onHelp = { menuOpen = false; onOpenHelp() }
                        )
                    }
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            /*
             * ВКЛАДОК БОЛЬШЕ НЕТ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ УБРАНЫ
             * ══════════════════════════════════════════════════════════════
             * Вкладка «Избранные» дублировала то, что и так есть:
             * избранные показываются отдельной группой в начале общего
             * списка. Человек видел одно и то же двумя способами
             * и вынужден был выбирать между ними без причины.
             *
             * Хуже того, фильтрация внутри вкладки работала неверно —
             * она накладывалась на выбор из шторки и давала пустой
             * список там, где записи есть.
             *
             * Отбор по избранным остался в шторке фильтров: там он
             * один и работает предсказуемо.
             */
            /*
             * ЗАГЛУШКА, ПОКА КОНТАКТЫ ЧИТАЮТСЯ.
             *
             * ── Что было не так ────────────────────────────────────────────
             * Экран не отличал «ещё грузится» от «контактов нет»
             * и показывал пустоту в обоих случаях. При запуске это
             * выглядело так, будто книга опустела, а через мгновение
             * контакты появлялись.
             *
             * Пустота читается как результат, а не как ожидание: человек
             * успевает сделать вывод раньше, чем придут данные.
             *
             * Разбивка по буквам считается в фоне, поэтому пустой grouped
             * бывает и после загрузки контактов — проверяем оба признака.
             *
             * Рисуется НАПРЯМУЮ, без item: этот блок находится в Column,
             * а не в LazyColumn — рядом с EmptyState, который тоже
             * вызывается напрямую. item существует только внутри списка.
             */
            /*
             * Заглушка не показывается при поиске и фильтре.
             *
             * grouped — это результат отбора: при поиске без совпадений
             * он пуст, а контакты давно загружены. Заглушка выглядела бы
             * так, будто приложение ещё ищет, хотя ответ уже есть —
             * совпадений нет.
             *
             * Та же ошибка была в журнале вызовов, см. MainDialerScreen.
             */
            if (grouped.isEmpty() && search.isBlank() && contactFilter == ContactFilter.ALL &&
                (state.loading || state.contacts.isNotEmpty())
            ) {
                CallLogSkeleton()
            }

            // grouped, а не filtered: последний объявлен внутри
            // produceState и снаружи не виден
            if (grouped.isEmpty() && !state.loading && state.contacts.isEmpty()) {
                EmptyState(
                    reason = when {
                        search.isNotBlank() -> EmptyReason.NoSearchResults(search)
                        contactFilter == ContactFilter.FAVOURITES ->
                            EmptyReason.NoFavourites
                        /*
                         * Отсутствие разрешения проверяется ПОСЛЕ поиска
                         * и избранных, но ПЕРЕД «контактов нет».
                         *
                         * Причина в том, что без разрешения список пуст
                         * всегда, и подпись «контактов нет» была бы
                         * неправдой: они есть, их просто не дают прочитать.
                         * Этот случай был единственным, который экран
                         * не различал, — в журнале он разбирался, а здесь нет.
                         */
                        !state.hasContactsPermission ->
                            EmptyReason.NoContactsPermission
                        else -> EmptyReason.NoContacts
                    },
                    onAction = { search = "" }
                )
                return@Column
            }

            /*
             * Оговорка при фильтрах давности.
             *
             * Отсутствие ЗВОНКОВ не означает отсутствия общения:
             * с человеком можно ежедневно переписываться и ни разу
             * не позвонить. Без этой строки список читался бы как
             * «ненужные контакты», и кто-то удалил бы лишнее.
             */
            if (contactFilter == ContactFilter.STALE_HALF_YEAR ||
                contactFilter == ContactFilter.STALE_YEAR
            ) {
                Text(
                    stringResource(R.string.contacts_stale_hint),
                    Modifier.padding(horizontal = Space.l, vertical = Space.s),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(Modifier.fillMaxSize().trackTouch(touchAnchor)) {
            LazyColumn(state = listState) {

                /*
                 * ИЗБРАННЫЕ ОТДЕЛЬНОЙ ГРУППОЙ СВЕРХУ.
                 *
                 * Раньше их можно было увидеть только на второй вкладке,
                 * а на первой они терялись среди сотен контактов
                 * в алфавитном порядке. Пользователь отметил этих людей
                 * сам — значит, к ним обращается чаще, и заставлять
                 * его прокручивать список или переключать вкладку
                 * бессмысленно.
                 *
                 * Показываются только на вкладке «Все»: на вкладке
                 * «Избранные» дублировать их незачем.
                 */
                // Избранные показываются группой в начале, пока
                // не ищут и не отбирают: при поиске они мешали бы,
                // а при отборе дублировали бы результат
                if (search.isBlank() && contactFilter == ContactFilter.ALL &&
                    favourites.isNotEmpty()
                ) {
                    item(key = "favourites_header") {
                        LetterHeader(stringResource(R.string.contacts_favourites))
                    }
                    itemsIndexed(favourites, key = { _, it -> "fav_${it.id}" }) { index, contact ->
                        ContactRow(
                            groupPosition = GroupPosition.of(index, favourites.size),
                            contact = contact,
                            query = search,
                            blocked = contact.numbers.any { it.e164 in blockedNumbers },
                            hasReminder = contact.numbers.any { it.e164 in reminderNumbers },
                            swipeController = swipeController,
                            swipeKey = "fav_${contact.id}",
                            /*
                             * Панели кнопок нет: одно действие, как
                             * в журнале. Раньше здесь оставалась старая
                             * система с несколькими вариантами —
                             * поведение свайпа отличалось между экранами,
                             * и это приходилось запоминать отдельно.
                             */
                            startActions = if (rightMulti) swipeActionsFor(contact) else emptyList(),
                            endActions = if (leftMulti) swipeEndActionsFor(contact) else emptyList(),
                            swipeTuning = swipeTuning,
                            listScrolling = listScrolling,
                            /*
                             * Кнопка вызова идёт тем же путём, что свайп.
                             *
                             * Прямой вызов звонил на ПЕРВЫЙ номер, а свайп
                             * спрашивает, на какой, если их несколько.
                             * Два способа сделать одно давали разное:
                             * человек не мог предсказать, куда попадёт
                             * звонок.
                             *
                             * performSwipe сам решает, нужен ли выбор
                             * номера и SIM.
                             */
                            onCall = {
                                vm.performSwipe(
                                    SwipeAction.CALL, contactAsGroup(contact)
                                ) { swipeController.close() }
                            },
                            /*
                             * Долгое нажатие на трубку — выбор SIM (1.8.0).
                             * Номеров несколько — тот же выбор номера, что
                             * у касания: в нём у каждого номера свои
                             * кнопки карт.
                             */
                            onCallLongPress = if (state.sims.size > 1) {
                                {
                                    val numbers = contact.numbers
                                    if (numbers.size > 1) {
                                        vm.performSwipe(SwipeAction.CALL, contactAsGroup(contact)) { swipeController.close() }
                                    } else {
                                        numbers.firstOrNull()?.let {
                                            vm.chooseSim(it.value, DialerViewModel.SimAction.CALL, contact.displayName)
                                        }
                                    }
                                }
                            } else null,
                            /*
                             * Через performSwipe: он сам решает, нужен ли
                             * выбор SIM, и закрывает панель перед диалогом.
                             */
                            onSwipeCall = {
                                vm.performSwipe(
                                    settings.swipeRight, contactAsGroup(contact)
                                ) { swipeController.close() }
                            },
                            onSwipeSms = {
                                vm.performSwipe(
                                    settings.swipeLeft, contactAsGroup(contact)
                                ) { swipeController.close() }
                            },
                            startHint = startHint,
                            endHint = endHint,
                            onOpen = {
                                // В режиме выбора нажатие возвращает номер,
                                // а не открывает карточку: человек пришёл
                                // сюда за одним действием
                                if (pickForPinning) {
                                    pickContact(contact)
                                } else {
                                    onOpenContact(contact.id)
                                }
                            },
                            // В режиме выбора долгое нажатие — тот же выбор
                            onLongPress = {
                                if (pickForPinning) {
                                    pickContact(contact)
                                } else {
                                    selectedAnchorY = touchAnchor.screenY
                                    selectedContact = contact
                                }
                            },
                            pickMode = pickForPinning
                        )
                    }
                }

                grouped.forEach { (letter, contacts) ->
                    // Разделители показывают, где ты при прокрутке,
                    // и не требуют прицеливания, в отличие от алфавитной
                    // полоски сбоку
                    item(key = "header_$letter") {
                        LetterHeader(letter.toString())
                    }
                    itemsIndexed(contacts, key = { _, it -> it.id }) { index, contact ->
                        ContactRow(
                            groupPosition = GroupPosition.of(index, contacts.size),
                            contact = contact,
                            query = search,
                            blocked = contact.numbers.any { it.e164 in blockedNumbers },
                            hasReminder = contact.numbers.any { it.e164 in reminderNumbers },
                            swipeController = swipeController,
                            swipeKey = "c_${contact.id}",
                            /*
                             * Панели кнопок нет: одно действие, как
                             * в журнале. Раньше здесь оставалась старая
                             * система с несколькими вариантами —
                             * поведение свайпа отличалось между экранами,
                             * и это приходилось запоминать отдельно.
                             */
                            startActions = if (rightMulti) swipeActionsFor(contact) else emptyList(),
                            endActions = if (leftMulti) swipeEndActionsFor(contact) else emptyList(),
                            swipeTuning = swipeTuning,
                            listScrolling = listScrolling,
                            /*
                             * Кнопка вызова идёт тем же путём, что свайп.
                             *
                             * Прямой вызов звонил на ПЕРВЫЙ номер, а свайп
                             * спрашивает, на какой, если их несколько.
                             * Два способа сделать одно давали разное:
                             * человек не мог предсказать, куда попадёт
                             * звонок.
                             *
                             * performSwipe сам решает, нужен ли выбор
                             * номера и SIM.
                             */
                            onCall = {
                                vm.performSwipe(
                                    SwipeAction.CALL, contactAsGroup(contact)
                                ) { swipeController.close() }
                            },
                            /*
                             * Долгое нажатие на трубку — выбор SIM (1.8.0).
                             * Номеров несколько — тот же выбор номера, что
                             * у касания: в нём у каждого номера свои
                             * кнопки карт.
                             */
                            onCallLongPress = if (state.sims.size > 1) {
                                {
                                    val numbers = contact.numbers
                                    if (numbers.size > 1) {
                                        vm.performSwipe(SwipeAction.CALL, contactAsGroup(contact)) { swipeController.close() }
                                    } else {
                                        numbers.firstOrNull()?.let {
                                            vm.chooseSim(it.value, DialerViewModel.SimAction.CALL, contact.displayName)
                                        }
                                    }
                                }
                            } else null,
                            /*
                             * Через performSwipe: он сам решает, нужен ли
                             * выбор SIM, и закрывает панель перед диалогом.
                             */
                            onSwipeCall = {
                                vm.performSwipe(
                                    settings.swipeRight, contactAsGroup(contact)
                                ) { swipeController.close() }
                            },
                            onSwipeSms = {
                                vm.performSwipe(
                                    settings.swipeLeft, contactAsGroup(contact)
                                ) { swipeController.close() }
                            },
                            startHint = startHint,
                            endHint = endHint,
                            onOpen = {
                                // В режиме выбора нажатие возвращает номер,
                                // а не открывает карточку: человек пришёл
                                // сюда за одним действием
                                if (pickForPinning) {
                                    pickContact(contact)
                                } else {
                                    onOpenContact(contact.id)
                                }
                            },
                            // В режиме выбора долгое нажатие — тот же выбор
                            onLongPress = {
                                if (pickForPinning) {
                                    pickContact(contact)
                                } else {
                                    selectedAnchorY = touchAnchor.screenY
                                    selectedContact = contact
                                }
                            },
                            pickMode = pickForPinning
                        )
                    }
                }
                // Под кнопкой псевдозвонка — запас, чтобы последняя строка
                // выходила из-под неё (1.5.7, снимок автора)
                item {
                    Spacer(Modifier.height(if (settings.fakeCallButton) FAB_CLEARANCE else Space.xxl))
                }
            }
            // «Наверх» — при прокрутке вверх далеко от начала (1.5.7)
            ScrollToTopPill(
                listState,
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Space.s)
            )
            }
        }
    }

    selectedContact?.let { contact ->
        RecordActionCards(
            group = remember(contact) { contactAsGroup(contact) },
            vm = vm,
            anchorY = selectedAnchorY,
            origin = CardsOrigin.CONTACTS,
            onOpenCard = { onOpenContact(contact.id) },
            onOpenBlockLists = onOpenBlockLists,
            onDismiss = { selectedContact = null }
        )
    }
}

/**
 * ЗАГОЛОВОК ГРУППЫ В СПИСКЕ.
 *
 * ── Почему фон прозрачный ──────────────────────────────────────────────────
 * Заливка сливалась с карточками: они лежат на surfaceContainerLow,
 * и полоса того же семейства читалась как ещё одна карточка, только
 * пустая.
 *
 * Разделение строится на пустоте: отступ сверху отделяет от предыдущей
 * группы, малый снизу привязывает заголовок к его карточке.
 *
 * ── 1.5.3: полоса раздела (сплошной список) ────────────────────────────────
 * Ниже — история: в 1.4.0–1.5.2 заголовок был подписью без черты над
 * белой карточкой; с 1.5.3 карточек нет, раздел отмечает серая полоса.
 *
 * ── Без черты снизу (1.4.0) ────────────────────────────────────────────────
 * Раньше под заголовком шла линия: карточка почти не отличалась от фона,
 * и без линии было неясно, где начинается группа. Теперь край карточки
 * виден сам (белая на сером в светлой теме), и линия в 6 dp над ним
 * читалась как двойная граница. Заголовок — вровень с содержимым строк
 * (поле экрана + поле карточки, по левому краю аватаров), а не с краем
 * карточки: глаз идёт по одной вертикали от заголовка вниз.
 */
/**
 * Буква раздела списка: первая буква имени, заглавная.
 *
 * «Ё» — под «Е», как в системной книге контактов: отдельной буквы
 * для неё там нет. Имя с цифры или знака — в раздел «#».
 */
internal fun sectionLetter(name: String): Char {
    val first = name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: return '#'
    return if (first == 'Ё') 'Е' else first
}

@Composable
fun LetterHeader(letter: String) {
    // Полоса раздела во всю ширину (сплошной список, 1.5.3)
    SectionBand(letter)
}

/**
 * СТРОКА КОНТАКТА.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ОФОРМЛЕНИЕ ПОВТОРЯЕТ КАРТОЧКУ ЖУРНАЛА
 * ══════════════════════════════════════════════════════════════════════════
 * Раньше здесь был ListItem — компонент Material с собственными
 * отступами и раскладкой. Выглядел он иначе: другая высота, другое
 * положение аватара, другой отступ до текста.
 *
 * Человек переключается между вкладками десятки раз в день, и два
 * разных вида одного и того же — списка людей — заставляют каждый раз
 * заново находить глазами нужное.
 *
 * Теперь разметка та же, что в журнале: аватар слева, имя крупно,
 * номер под ним, всё в карточке с одинаковыми отступами.
 *
 * ── Чего здесь нет по сравнению с журналом ─────────────────────────────────
 * Времени звонка, значка типа вызова и счётчиков: у контакта их нет.
 * Звезда избранного стояла справа до 1.5.8 — теперь она в карточках
 * долгого нажатия.
 * ══════════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    query: String,
    /**
     * Заблокирован ли контакт.
     *
     * Проверяется по всем его номерам: у человека с рабочим и личным
     * заблокированным может быть один. Показать «заблокирован» в этом
     * случае честнее, чем молчать: звонки с него не дойдут, и объяснение
     * должно быть там же, где имя.
     */
    blocked: Boolean,
    /** Назначено ли напоминание перезвонить кому-то из его номеров. */
    hasReminder: Boolean,
    swipeController: SwipeRevealController,
    swipeKey: String,
    startActions: List<RevealAction>,
    endActions: List<RevealAction> = emptyList(),
    swipeTuning: com.ogneliss.dialer.settings.SwipeTuning,
    listScrolling: Boolean,
    onSwipeCall: () -> Unit,
    onSwipeSms: () -> Unit,
    startHint: RevealAction?,
    endHint: RevealAction?,
    onCall: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    /** Долгое нажатие на трубку — выбор SIM (1.8.0); null — одна карта. */
    onCallLongPress: (() -> Unit)? = null,
    /** Выбор контакта (закрепить, псевдозвонок): нажатия выбирают, а не открывают. */
    pickMode: Boolean = false,
    /*
     * ОДИН ШАБЛОН СПИСКА С ЖУРНАЛОМ (1.3.5, 05.10.2026, аудит: «разные
     * шаблоны списков — контакты / журнал»). Было: каждый контакт —
     * отдельная карточка с промежутками даже внутри одной буквы. Стало —
     * как группа журнала: контакты одной буквы в одной карточке,
     * скругление у первой и последней строки, разделитель от начала текста.
     * GroupPosition — тот же, что у CallLogCard.
     */
    groupPosition: GroupPosition = GroupPosition.SINGLE
) {
    val appearance = LocalAppearance.current
    /*
     * СПЛОШНОЙ СПИСОК (1.5.3, макет B): строка во всю ширину экрана, без
     * скруглений и зазоров; между строками — тонкая линия от начала текста.
     * Над первой строкой раздела линии нет — там полоса раздела.
     */
    val rowShape = androidx.compose.ui.graphics.RectangleShape
    val topGap = 0.dp
    val bottomGap = 0.dp

    Card(
        // Card прозрачна: цвет задаёт содержимое, иначе фон рисуется
        // дважды — то же решение, что в карточке журнала
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = rowShape,
        modifier = Modifier
            .fillMaxWidth()
            /*
             * Отступы из правил FlowUI, а не числами.
             *
             * Строка журнала и строка контакта должны выглядеть
             * одинаково: человек переключается между вкладками
             * десятки раз в день. Числа в двух местах при первой же
             * правке разойдутся — поправят одно, забудут другое.
             */
            .padding(
                top = topGap,
                bottom = bottomGap
            )
    ) {
        if (groupPosition == GroupPosition.MIDDLE || groupPosition == GroupPosition.LAST) {
            // От начала текста, а не от края — как в журнале
            Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                RowDivider(Modifier.padding(start = Space.l + appearance.avatarSize + Space.m))
            }
        }
        SwipeRevealRow(
            key = swipeKey,
            controller = swipeController,
            startActions = startActions,
            endActions = endActions,
            // Выключенный в настройках свайп — без обработчика: иначе
            // строка тянется, хотя ничего не делает (разбор — у журнала,
            // MainDialerScreen, onDirectStart)
            // Есть панель (два-три действия) — мгновенного действия нет
            onDirectStart = onSwipeCall.takeIf { startHint != null && startActions.isEmpty() },
            onDirectEnd = onSwipeSms.takeIf { endHint != null && endActions.isEmpty() },
            // Пороги те же, что в журнале: расхождение читается
            // как «свайп сломан»
            tuning = swipeTuning,
        listScrolling = listScrolling,
            directStartAction = startHint,
            directEndAction = endHint
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    // Подписи для TalkBack (1.5.8): звезды в строке больше нет —
                    // «В избранное» в карточках долгого нажатия, и о нём
                    // нужно сказать словами
                    .combinedClickable(
                        // В режиме выбора оба нажатия выбирают — подпись общая
                        onClickLabel = if (pickMode) null else stringResource(R.string.a11y_cards_open),
                        onLongClickLabel = if (pickMode) null else stringResource(R.string.a11y_record_actions),
                        onClick = onOpen,
                        onLongClick = onLongPress
                    )
                    // Тот же отступ, что в журнале: экраны должны
                    // выглядеть одинаково (1.7.0: 10 dp без линий, 6 — с ними)
                    .padding(
                        horizontal = Space.l,
                        // 1.9.0: по плотности списков (Оформление), обычная — как было
                        vertical = appearance.rowPadding(com.ogneliss.dialer.ui.theme.OgnelissTheme.surfaces.outlined)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Аватар — общий RowAvatar (1.3.5): буквы у контактов без фото,
                // силуэт под фотографией на случай сбоя загрузки — как в журнале
                RowAvatar(contact.photoUri, name = contact.displayName)

                Spacer(Modifier.width(Space.m))

                Column(Modifier.weight(1f)) {
                    HighlightedName(
                        name = contact.displayName,
                        query = query,
                        // Обычной толщины, как в журнале (1.7.0; полужирное — с 1.5.6);
                        // размер — как имя в списке писем почты (1.8.4)
                        style = MaterialTheme.typography.listName
                    )

                    /*
                     * НОМЕР И ПОМЕТКА О БЛОКИРОВКЕ — В ОДНОЙ СТРОКЕ.
                     *
                     * ══════════════════════════════════════════════════════
                     * ПОЧЕМУ НЕ ОТДЕЛЬНОЙ СТРОКОЙ
                     * ══════════════════════════════════════════════════════
                     * Сначала пометка стояла третьей строкой, как в журнале.
                     * Но в журнале третья строка есть у каждой записи
                     * (там регион или оператор), а в контактах её нет
                     * ни у кого — и заблокированный контакт становился
                     * выше соседей. В списке, где строки идут вплотную,
                     * такой разнобой заметнее самой пометки.
                     *
                     * Значок и красный цвет вместе, а не по отдельности:
                     * при высоком контрасте и у людей с нарушением
                     * цветовосприятия один цвет не читается.
                     */
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        contact.numbers.firstOrNull()?.let { number ->
                            Text(
                                PhoneFormatter.format(number.value),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        /*
                         * Значок напоминания — там же, где пометка
                         * блокировки: в строке с номером, а не отдельной
                         * строкой. Иначе контакт с напоминанием стал бы
                         * выше соседей.
                         */
                        if (hasReminder) {
                            Spacer(Modifier.width(Space.s))
                            Icon(
                                OgnelissIcons.Alarm,
                                contentDescription = stringResource(
                                    R.string.filter_with_reminder
                                ),
                                modifier = Modifier.size(FlowUi.iconSizeSmall),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (blocked) {
                            Spacer(Modifier.width(Space.s))
                            Icon(
                                OgnelissIcons.Block,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeSmall),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                stringResource(R.string.contact_blocked),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        }
                    }
                }

                /*
                 * КНОПКА ВЫЗОВА — КАК В ЖУРНАЛЕ.
                 *
                 * ── Зачем ──────────────────────────────────────────────────
                 * Из контактов звонят чаще, чем из журнала: туда и заходят
                 * ради этого. А нажатие на строку открывает карточку —
                 * то есть самое частое действие требовало двух шагов.
                 *
                 * Свайп остаётся, но он невидим, и о нём знают не все.
                 *
                 * ── Почему тот же зелёный ──────────────────────────────────
                 * Одно действие должно выглядеть одинаково везде: та же
                 * трубка того же цвета, что в журнале и на кнопке
                 * «Позвонить» в карточке.
                 */
                ActionIconButton(
                    icon = OgnelissIcons.Call,
                    contentDescription = stringResource(R.string.a11y_call_number, contact.displayName),
                    tint = CallGreen,
                    onClick = onCall,
                    onLongClick = onCallLongPress,
                    onLongClickLabel = stringResource(R.string.a11y_choose_sim)
                )

                /*
                 * ЗВЕЗДЫ В СТРОКЕ БОЛЬШЕ НЕТ (1.5.8, обзор интерфейса, У2).
                 *
                 * Была справа у каждого контакта: второй значок рядом
                 * с вызовом. Нажимают её редко, а место и внимание она
                 * занимала в каждой строке — и при длинном имени отнимала
                 * у него ширину. «В избранное» — в карточках долгого
                 * нажатия (быстрое действие) и в карточке контакта;
                 * раздел избранных сверху списка остался.
                 */
            }
        }
    }
}

/**
 * Контакт в виде карточки журнала.
 *
 * Нужен фабрике кнопок свайпа: она работает с записями журнала,
 * а здесь у нас контакт без истории вызовов. Переписывать фабрику
 * ради этого не стоит — набор действий и их поведение одинаковы.
 */
private fun contactAsGroup(contact: Contact): CallLogGroup {
    val primary = contact.numbers.firstOrNull()
    return CallLogGroup(
        key = "contact:${contact.id}",
        lastCallId = -1,
        number = primary?.value.orEmpty(),
        e164 = primary?.e164.orEmpty(),
        displayName = contact.displayName,
        photoUri = contact.photoUri,
        contactId = contact.id,
        lastType = CallType.INCOMING,
        lastDate = 0, durationSec = 0, count = 0,
        incomingCount = 0, outgoingCount = 0, missedCount = 0, rejectedCount = 0,
        totalDurationSec = 0,
        numbers = contact.numbers.map { it.value },
        subscriptionLabel = null, subscriptionId = null
    )
}
