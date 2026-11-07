package com.ogneliss.dialer.ui.conversation

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalConfiguration
import com.ogneliss.dialer.data.JournalDays
import com.ogneliss.dialer.sms.smsPinKey
import com.ogneliss.dialer.ui.components.FloatingDatePill
import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.components.SectionBand
import com.ogneliss.dialer.ui.components.SimBadge
import com.ogneliss.dialer.ui.components.swipe.RevealAction
import com.ogneliss.dialer.ui.screens.FAB_CLEARANCE
import com.ogneliss.dialer.ui.screens.LetterHeader
import com.ogneliss.dialer.ui.screens.spokenDayLabel
import com.ogneliss.dialer.ui.theme.LocalAppearance
import java.util.Locale
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallLogGrouper
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.ListPanel
import com.ogneliss.dialer.ui.components.MainSection
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.RoleHelpDialog
import com.ogneliss.dialer.ui.components.RowAvatar
import com.ogneliss.dialer.ui.components.rememberSmsRoleRequest
import com.ogneliss.dialer.ui.components.rememberTouchAnchor
import com.ogneliss.dialer.ui.components.trackTouch
import com.ogneliss.dialer.ui.screens.CardsOrigin
import com.ogneliss.dialer.ui.screens.RecordActionCards
import com.ogneliss.dialer.ui.components.swipe.SwipeRevealRow
import com.ogneliss.dialer.ui.components.swipe.rememberSwipeRevealController
import com.ogneliss.dialer.ui.screens.OverflowMenu
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.ui.theme.listName
import com.ogneliss.dialer.util.PhoneFormatter
import com.ogneliss.dialer.util.RoleAccess

/**
 * РАЗДЕЛ «SMS» (2.0.0) — список переписок.
 *
 * ── 2.1.0: как журнал (автор, 07.11.2026: «окно SMS не доделано») ──────────
 * • Каркас журнала и контактов: без верхней строки с заголовком — было
 *   «Сообщения» крупно и карандаш сверху, единственный такой раздел;
 *   «Написать» — круглой кнопкой справа внизу, как «Написать» в почте.
 * • По дням: «СЕГОДНЯ», «ВЧЕРА», «29 октября · ЧЕТВЕРГ» и плавающая дата
 *   при прокрутке (JournalDays, FloatingDatePill) — как журнал.
 * • Закреплённые переписки — сверху, своим списком (Prefs.smsPinned),
 *   отдельно от закреплённых журнала. Закрепить — карточки долгого нажатия.
 * • Свайпы — свои (Настройки → SMS): по умолчанию вправо «SMS» (открыть
 *   переписку), влево «Удалить» (с подтверждением).
 * • Перелистывание по нижней панели: вправо — журнал, влево — контакты.
 * • Строка — как строка журнала: те же поля, фон, линии внутри дня.
 *
 * Без роли SMS — объяснение и кнопка «Сделать»; написать можно и так.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagesScreen(
    vm: DialerViewModel,
    onOpenConversation: (String) -> Unit,
    onOpenSection: (MainSection) -> Unit,
    onOpenDialpad: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenHelp: () -> Unit,
    onFakeCall: () -> Unit,
    /** Карточка абонента по ключу (contact:… / number:…) — из карточек действий. */
    onOpenCard: (String) -> Unit,
    onOpenBlockLists: () -> Unit,
    messagesVm: MessagesViewModel = hiltViewModel()
) {
    // Переписки — свой ViewModel; книга и настройки — общие с журналом
    val state by messagesVm.state.collectAsStateWithLifecycle()
    val dialer by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val pinnedKeys by vm.smsPinned.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    /*
     * Поиск — полем на месте строки кнопок панели (ListPanel), как
     * в контактах; включается из «Ещё». Ищет по имени из книги, номеру
     * и тексту последнего сообщения.
     */
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Окно «Новое сообщение» переживает поворот: человек мог начать набирать
    var composeNew by rememberSaveable { mutableStateOf(false) }
    // Удаление переписки — только с подтверждением: необратимо
    var confirmDelete by remember { mutableStateOf<MessagesViewModel.Thread?>(null) }
    /*
     * Долгое нажатие — те же карточки действий, что в журнале (2.0.2):
     * номера с вызовом и SMS, «Позвонить», «Закрепить», «Блок», «Ещё».
     * Карточки встают у места касания (TouchAnchor).
     */
    var cardsFor by remember { mutableStateOf<MessagesViewModel.Thread?>(null) }
    var cardsAnchorY by remember { mutableStateOf<Float?>(null) }
    val touchAnchor = rememberTouchAnchor()
    var roleHelp by remember { mutableStateOf<RoleAccess.Help?>(null) }
    val requestRole = rememberSmsRoleRequest { help ->
        messagesVm.refresh()
        roleHelp = help
    }

    // Вернулись из системного окна роли или настроек — перечитать
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) messagesVm.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Номер → контакт: точное E.164 и последние 10 цифр (как журнал)
    val byNumber = remember(dialer.contacts) { contactIndex(dialer.contacts) }
    fun contactOf(address: String): Contact? {
        val e164 = PhoneFormatter.toE164(address)
        return byNumber[e164] ?: CallLogGrouper.tailKey(e164)?.let { byNumber[it] }
    }

    /*
     * СВАЙПЫ (2.1.0) — свои, из «Настройки → SMS»: smsSwipeRight /
     * smsSwipeLeft и их дополнительные действия (до трёх — панель кнопок,
     * как в журнале). Порог, подсказка-иконка и выбор SIM — общие.
     *
     * «SMS» здесь открывает переписку сразу, не через выбор номера:
     * номер у переписки один. «Удалить» удаляет переписку
     * с подтверждением: журнала у сообщений нет, а смысл жеста тот же.
     */
    val swipeController = rememberSwipeRevealController()
    val swipeTuning by vm.swipeTuning.collectAsStateWithLifecycle()
    val rightExtra by vm.smsSwipeRightExtra.collectAsStateWithLifecycle()
    val leftExtra by vm.smsSwipeLeftExtra.collectAsStateWithLifecycle()
    val swipeRight = settings.smsSwipeRight
    val swipeLeft = settings.smsSwipeLeft
    val rightMulti = swipeRight != SwipeAction.NONE && rightExtra.isNotEmpty()
    val leftMulti = swipeLeft != SwipeAction.NONE && leftExtra.isNotEmpty()
    val deleteLabel = stringResource(R.string.action_delete_thread)
    // Подпись «Удалить» вместо «Удалить из журнала» — у подсказки и кнопок панели
    val relabel: (RevealAction) -> RevealAction = { a ->
        if (a.icon == OgnelissIcons.Delete) a.copy(label = deleteLabel, description = deleteLabel) else a
    }
    val startHint = remember(swipeRight, deleteLabel) { vm.directActionAppearance(swipeRight)?.let(relabel) }
    val endHint = remember(swipeLeft, deleteLabel) { vm.directActionAppearance(swipeLeft)?.let(relabel) }
    val closeSwipesToken by vm.closeSwipes.collectAsStateWithLifecycle()
    LaunchedEffect(closeSwipesToken) { if (closeSwipesToken > 0) swipeController.closeAll() }
    val listState = rememberLazyListState()

    fun swipe(action: SwipeAction, thread: MessagesViewModel.Thread) {
        val dialable = thread.address.any { it.isDigit() } && thread.address.none { it.isLetter() }
        when {
            action == SwipeAction.NONE -> Unit
            action == SwipeAction.DELETE_FROM_LOG -> {
                swipeController.close()
                confirmDelete = thread
            }
            // «SMS» по переписке — она сама; у буквенного отправителя тоже
            action == SwipeAction.SMS -> {
                swipeController.close()
                onOpenConversation(thread.address)
            }
            // Буквенный отправитель («Bank»): звонить и писать в мессенджер некуда
            !dialable -> swipeController.close()
            else -> vm.performSwipe(action, threadAsGroup(thread.address, contactOf(thread.address))) {
                swipeController.close()
            }
        }
    }

    val shown = remember(state.threads, query, byNumber) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.threads
        else state.threads.filter { t ->
            val name = contactOf(t.address)?.displayName.orEmpty()
            name.lowercase().contains(q) || t.address.contains(q) || t.last.body.lowercase().contains(q)
        }
    }
    // Закреплённые — в порядке закрепления; остальные — по дням
    val pinnedThreads = remember(shown, pinnedKeys) {
        val byKey = shown.associateBy { smsPinKey(it.address) }
        pinnedKeys.mapNotNull { byKey[it] }
    }
    val pinnedSet = remember(pinnedKeys) { pinnedKeys.toSet() }
    val days = remember(shown, pinnedSet) {
        val now = System.currentTimeMillis()
        shown.filterNot { smsPinKey(it.address) in pinnedSet }
            .groupBy { JournalDays.dayStart(it.last.date, now) }
            .toList()
            .sortedByDescending { it.first }
    }
    // Подписи дней — на языке приложения, как в журнале
    val dayLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val todayText = stringResource(R.string.date_group_today)
    val yesterdayText = stringResource(R.string.date_group_yesterday)
    val dayLabels = remember(days, dayLocale, todayText, yesterdayText) {
        val now = System.currentTimeMillis()
        val dayMonth = android.text.format.DateFormat.getBestDateTimePattern(dayLocale, "dMMMM")
        val dayMonthYear = android.text.format.DateFormat.getBestDateTimePattern(dayLocale, "dMMMMy")
        days.associate { (day, _) ->
            day to JournalDays.label(day, now, dayLocale, todayText, yesterdayText, dayMonth, dayMonthYear)
        }
    }
    val pinnedHeader = stringResource(R.string.pinned_header)
    val dayOfKey = remember(days) {
        buildMap { days.forEach { (day, list) -> list.forEach { put(threadKey(it), day) } } }
    }
    val labelForKey: (Any) -> String? = remember(dayOfKey, dayLabels, pinnedHeader) {
        { key ->
            val k = key as? String
            when {
                k == null -> null
                k.startsWith("pin_") -> pinnedHeader
                else -> dayOfKey[k]?.let { dayLabels[it] }
            }
        }
    }

    // Перелистывание по панели (настройка): вправо — журнал, влево — контакты
    val swipeNav = settings.panelSwipeNav

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            // «Написать» — как в почте: круглая кнопка справа внизу, у большого пальца
            if (!searching) {
                FloatingActionButton(
                    onClick = { composeNew = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(OgnelissIcons.Edit, contentDescription = stringResource(R.string.messages_new))
                }
            }
        },
        /*
         * Та же панель, что у журнала и контактов: Журнал · SMS ·
         * клавиатура · Контакты · Ещё. Клавиатура набора живёт в журнале —
         * кнопка открывает её там.
         */
        bottomBar = {
            ListPanel(
                section = MainSection.MESSAGES,
                onOpenSection = onOpenSection,
                filterActive = false,
                onDialpad = onOpenDialpad,
                searching = searching,
                searchQuery = query,
                searchHint = stringResource(R.string.messages_search_hint),
                onSearchChange = { query = it },
                onCloseSearch = { searching = false; query = "" },
                onMenu = { menuOpen = true },
                onSwipeRight = if (swipeNav) { { onOpenSection(MainSection.JOURNAL) } } else null,
                onSwipeLeft = if (swipeNav) { { onOpenSection(MainSection.CONTACTS) } } else null,
                menuContent = {
                    // Пункты — как у журнала и контактов, тем же окном (OverflowMenu)
                    OverflowMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        settings = settings,
                        onOwnNumber = null,
                        onSearch = { menuOpen = false; searching = true },
                        onThemeToggle = {
                            vm.setChoice(
                                "theme",
                                if (settings.theme == ThemeMode.DARK) ThemeMode.LIGHT.name else ThemeMode.DARK.name
                            )
                            menuOpen = false
                        },
                        onClearLog = null,
                        onSettings = { menuOpen = false; onOpenSettings() },
                        onFakeCall = { menuOpen = false; onFakeCall() },
                        onNotes = { menuOpen = false; onOpenNotes() },
                        onReminders = { menuOpen = false; onOpenReminders() },
                        onStartSelection = null,
                        onHelp = { menuOpen = false; onOpenHelp() }
                    )
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            /*
             * Три состояния: нет роли SMS — объяснение и кнопка; роль есть,
             * переписок нет (или поиск ничего не нашёл) — строка по центру;
             * иначе список. Пока идёт первое чтение — пусто, без мигания
             * «сообщений нет».
             */
            if (!state.isDefaultSms && !state.loading) {
                NotDefaultBlock(onMakeDefault = requestRole)
            } else if (shown.isEmpty() && !state.loading) {
                Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.messages_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                @Composable
                fun ThreadItem(thread: MessagesViewModel.Thread, rowKey: String, position: GroupPosition) {
                    val contact = contactOf(thread.address)
                    val pinned = smsPinKey(thread.address) in pinnedSet
                    // Номер переписки как запись журнала — для кнопок панели
                    val group = remember(thread.address, contact) { threadAsGroup(thread.address, contact) }
                    val slot = thread.last.subId?.let { id -> dialer.sims.firstOrNull { it.subscriptionId == id }?.slotIndex }
                    Column {
                        if (position == GroupPosition.MIDDLE || position == GroupPosition.LAST) {
                            Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                                RowDivider(Modifier.padding(start = Space.l + LocalAppearance.current.avatarSize + Space.m))
                            }
                        }
                        SwipeRevealRow(
                            key = rowKey,
                            controller = swipeController,
                            startActions = if (rightMulti) {
                                remember(rowKey, swipeRight, rightExtra, dialer.sims, group, deleteLabel) {
                                    vm.revealActionsFor(swipeRight, rightExtra, group, dialer.sims) { swipe(it, thread) }
                                        .map(relabel)
                                }
                            } else emptyList(),
                            endActions = if (leftMulti) {
                                remember(rowKey, swipeLeft, leftExtra, dialer.sims, group, deleteLabel) {
                                    vm.revealActionsFor(swipeLeft, leftExtra, group, dialer.sims) { swipe(it, thread) }
                                        .map(relabel)
                                }
                            } else emptyList(),
                            // «Нет действия» или панель — прямого действия в эту сторону нет
                            onDirectStart = if (swipeRight == SwipeAction.NONE || rightMulti) null
                            else { { swipe(swipeRight, thread) } },
                            onDirectEnd = if (swipeLeft == SwipeAction.NONE || leftMulti) null
                            else { { swipe(swipeLeft, thread) } },
                            directStartAction = startHint,
                            directEndAction = endHint,
                            tuning = swipeTuning,
                            listScrolling = listState.isScrollInProgress
                        ) {
                            ThreadRow(
                                thread = thread,
                                title = contact?.displayName ?: PhoneFormatter.format(thread.address),
                                photoUri = contact?.photoUri,
                                initialsFrom = contact?.displayName,
                                pinned = pinned,
                                // Карта — только при двух и больше: с одной выбирать не из чего
                                simSlot = slot.takeIf { dialer.sims.size > 1 },
                                // Касание — переписка (раскрытая строка — закрыть); долгое — карточки
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .combinedClickable(
                                        onClick = {
                                            if (swipeController.isOpen(rowKey)) swipeController.close()
                                            else onOpenConversation(thread.address)
                                        },
                                        onLongClick = {
                                            swipeController.close()
                                            cardsAnchorY = touchAnchor.screenY
                                            cardsFor = thread
                                        },
                                        onLongClickLabel = stringResource(R.string.sms_message_actions)
                                    )
                            )
                        }
                    }
                }

                LazyColumn(
                    Modifier.fillMaxSize().trackTouch(touchAnchor),
                    state = listState,
                    // Под кнопкой «Написать» — запас: она висит над правым краем
                    contentPadding = PaddingValues(top = Space.xs, bottom = FAB_CLEARANCE + Space.l)
                ) {
                    if (pinnedThreads.isNotEmpty()) {
                        item(key = "pinned_header", contentType = "date_header") {
                            LetterHeader(pinnedHeader)
                        }
                        itemsIndexed(pinnedThreads, key = { _, t -> "pin_" + threadKey(t) }, contentType = { _, _ -> "thread" }) { i, t ->
                            ThreadItem(t, "pin_" + threadKey(t), GroupPosition.of(i, pinnedThreads.size))
                        }
                    }
                    days.forEach { (day, list) ->
                        item(key = "day_$day", contentType = "date_header") {
                            val dayText = dayLabels[day].orEmpty()
                            SectionBand(dayText, description = spokenDayLabel(dayText, dayLocale))
                        }
                        itemsIndexed(list, key = { _, t -> threadKey(t) }, contentType = { _, _ -> "thread" }) { i, t ->
                            ThreadItem(t, threadKey(t), GroupPosition.of(i, list.size))
                        }
                    }
                }

                // Плавающая дата — как в журнале: день верхней строки при прокрутке
                FloatingDatePill(
                    listState = listState,
                    labelForKey = labelForKey,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = Space.s)
                )
            }
        }
    }

    if (composeNew) {
        NewMessageDialog(
            contacts = dialer.contacts,
            onDismiss = { composeNew = false },
            onPick = { number ->
                composeNew = false
                onOpenConversation(number)
            }
        )
    }

    confirmDelete?.let { thread ->
        val who = contactOf(thread.address)?.displayName ?: PhoneFormatter.format(thread.address)
        OgnelissDialog(
            onDismissRequest = { confirmDelete = null },
            title = stringResource(R.string.messages_delete_thread_title),
            content = { Text(stringResource(R.string.messages_delete_thread_text, who)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    // Удалённая переписка не остаётся закреплённой пустым местом
                    if (smsPinKey(thread.address) in pinnedSet) vm.toggleSmsPin(thread.address)
                    messagesVm.deleteThread(thread)
                }) { Text(stringResource(R.string.sms_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.nav_close)) }
            }
        )
    }

    cardsFor?.let { thread ->
        val contact = contactOf(thread.address)
        RecordActionCards(
            group = remember(thread.address, contact) { threadAsGroup(thread.address, contact) },
            vm = vm,
            anchorY = cardsAnchorY,
            origin = CardsOrigin.MESSAGES,
            onOpenCard = {
                cardsFor = null
                onOpenCard(contact?.let { "contact:${it.id}" } ?: "number:${thread.address}")
            },
            onOpenBlockLists = { cardsFor = null; onOpenBlockLists() },
            onDeleteThread = { confirmDelete = thread },
            smsPinned = smsPinKey(thread.address) in pinnedSet,
            onToggleSmsPin = { vm.toggleSmsPin(thread.address) },
            onDismiss = { cardsFor = null }
        )
    }

    roleHelp?.let { help -> RoleHelpDialog(help) { roleHelp = null } }
}

/** Ключ строки ленты: разговор и адрес (у старых прошивок разговора может не быть). */
private fun threadKey(t: MessagesViewModel.Thread): String = "thread:${t.threadId}:${t.address}"

/**
 * Строка переписки — как строка журнала (2.1.0): те же поля по плотности
 * списков, фон строки и размеры. Было: своя высота 72 dp и поля экрана —
 * строки SMS выглядели крупнее и «другими» рядом с журналом.
 */
@Composable
private fun ThreadRow(
    thread: MessagesViewModel.Thread,
    title: String,
    photoUri: String?,
    initialsFrom: String?,
    pinned: Boolean,
    simSlot: Int?,
    modifier: Modifier
) {
    val context = LocalContext.current
    val unread = thread.unread > 0
    // Пустой текст — MMS: «Картинка» или «MMS», как в журнале
    val body = thread.last.body.replace('\n', ' ').trim().ifEmpty {
        stringResource(if (thread.last.attachments.isNotEmpty()) R.string.sms_picture else R.string.sms_mms)
    }
    val line = if (thread.last.outgoing) stringResource(R.string.sms_you, body) else body
    // Сегодня — время, раньше — дата: как в списках почты
    val time = remember(thread.last.date) {
        val date = thread.last.date
        if (DateUtils.isToday(date)) DateUtils.formatDateTime(context, date, DateUtils.FORMAT_SHOW_TIME)
        else DateUtils.formatDateTime(context, date, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(
                horizontal = Space.l,
                vertical = LocalAppearance.current.rowPadding(OgnelissTheme.surfaces.outlined)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowAvatar(photoUri, name = initialsFrom)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.listName,
                    fontWeight = if (unread) FontWeight.SemiBold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (pinned) {
                    Spacer(Modifier.width(Space.xs))
                    Icon(
                        OgnelissIcons.PushPin,
                        contentDescription = stringResource(R.string.pinned_header),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (simSlot != null) {
                    SimBadge(simSlot)
                    Spacer(Modifier.width(Space.xs))
                }
                Text(
                    time,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Space.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread) {
                    Spacer(Modifier.width(Space.s))
                    Badge { Text(thread.unread.toString()) }
                }
            }
        }
    }
}

/** Диалер ещё не приложение SMS: что даст роль и кнопка. */
@Composable
private fun NotDefaultBlock(onMakeDefault: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(OgnelissIcons.Textsms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Space.m))
        Text(
            stringResource(R.string.messages_not_default),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.l))
        FilledTonalButton(onClick = onMakeDefault) { Text(stringResource(R.string.settings_sms_default_app)) }
    }
}

/**
 * Новое сообщение: номер или имя. По мере ввода — до шести контактов
 * из книги; набранный номер можно отправить сразу.
 */
@Composable
private fun NewMessageDialog(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    /*
     * Подсказки — контакты, у которых совпало имя или номер; по номеру —
     * только если набраны цифры (иначе «а» совпадала бы с каждым номером).
     */
    val q = text.trim().lowercase()
    val matches = remember(q, contacts) {
        if (q.isEmpty()) emptyList()
        else contacts.asSequence()
            .filter { c -> c.numbers.isNotEmpty() && (c.displayName.lowercase().contains(q) || c.numbers.any { it.e164.contains(q.filter(Char::isDigit).ifEmpty { "\u0000" }) }) }
            .flatMap { c -> c.numbers.asSequence().map { c to it } }
            .take(6)
            .toList()
    }
    // Набран номер (не меньше трёх цифр, только знаки номера) — «Написать» доступна
    val typedNumber = text.trim().takeIf { t -> t.count(Char::isDigit) >= 3 && t.all { it.isDigit() || it in "+-() " } }
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.messages_new),
        content = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.messages_new_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                matches.forEach { (contact, number) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onPick(number.value) }
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RowAvatar(contact.photoUri, name = contact.displayName)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(contact.displayName, style = MaterialTheme.typography.listName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(PhoneFormatter.format(number.value), number.label).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { typedNumber?.let(onPick) }, enabled = typedNumber != null) {
                Text(stringResource(R.string.messages_write))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_close)) }
        }
    )
}

/**
 * Переписка как запись журнала — для действий свайпа (performSwipe),
 * как contactAsGroup в контактах. Номер один — номер переписки: звонок
 * и мессенджер идут на него, без выбора среди номеров контакта.
 */
private fun threadAsGroup(address: String, contact: Contact?): CallLogGroup {
    val e164 = PhoneFormatter.toE164(address).ifBlank { address }
    return CallLogGroup(
        key = contact?.let { "contact:${it.id}" } ?: "number:$e164",
        lastCallId = -1,
        number = address,
        e164 = e164,
        displayName = contact?.displayName,
        photoUri = contact?.photoUri,
        contactId = contact?.id,
        lastType = CallType.INCOMING,
        lastDate = 0, durationSec = 0, count = 0,
        incomingCount = 0, outgoingCount = 0, missedCount = 0, rejectedCount = 0,
        totalDurationSec = 0,
        numbers = listOf(address),
        subscriptionLabel = null, subscriptionId = null
    )
}

/** Номер → контакт: E.164 и последние 10 цифр; первый по алфавиту побеждает. */
private fun contactIndex(contacts: List<Contact>): Map<String, Contact> = buildMap {
    contacts.sortedBy { it.displayName.lowercase() }.forEach { c ->
        c.numbers.forEach { n ->
            putIfAbsent(n.e164, c)
            CallLogGrouper.tailKey(n.e164)?.let { putIfAbsent(it, c) }
        }
    }
}
