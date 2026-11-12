package com.ogneliss.dialer.ui.conversation

import com.ogneliss.dialer.ui.components.SectionTopBar
import com.ogneliss.dialer.ui.components.TopBarMenuItem
import com.ogneliss.dialer.ui.components.scrollEdgeFade
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import com.ogneliss.dialer.data.ArchiveRules
import com.ogneliss.dialer.data.JournalDays
import com.ogneliss.dialer.ui.components.PanelCenter
import com.ogneliss.dialer.ui.components.dragSelect
import com.ogneliss.dialer.ui.components.SelectionBottomBar
import com.ogneliss.dialer.ui.components.rememberSelectionState
import com.ogneliss.dialer.sms.smsPinKey
import com.ogneliss.dialer.sms.SmsFolder
import com.ogneliss.dialer.sms.SmsFolders
import com.ogneliss.dialer.ui.components.FloatingDatePill
import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.components.SectionBand
import com.ogneliss.dialer.ui.components.SimBadge
import com.ogneliss.dialer.ui.components.swipe.RevealAction
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
 *   переписку), влево «Удалить».
 * • Перелистывание по нижней панели: вправо — журнал, влево — контакты.
 * • Строка — как строка журнала: те же поля, фон, линии внутри дня.
 *
 * ── 2.1.0: тихо (правило автора: выполнять или пропускать, не объяснять) ────
 * • Без роли SMS — только «Открыть «Сообщения»» (приложение SMS человека)
 *   и «Вести SMS в Ogneliss» (роль по просьбе); у приложения с RCS — одна
 *   строка, что чаты RCS останутся там и выключатся. Было: абзац
 *   объяснения и кнопка «Приложение SMS по умолчанию».
 * • Удаление — сразу, с «Отменить» (было — окно подтверждения);
 *   из хранилища — когда сообщение погаснет (MessagesViewModel.deleteWithUndo).
 * • Сверху — полоса только о критичном: уведомления о SMS выключены,
 *   автозапуск MIUI запрещён (SmsHealth); на прошивках, где автозапуск
 *   не проверить, — один раз «разрешите автозапуск».
 * • Пусто — значок и «Сообщений пока нет», без пояснений; пояснение
 *   архива убрано.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    /** Найденное поиском сообщение (2.2.0): адрес и Message.key. */
    onOpenMessage: (String, String) -> Unit = { address, _ -> onOpenConversation(address) },
    /** Порядок и показ папок (2.2.0): долгое нажатие на вкладки. */
    onOpenFolders: () -> Unit = {},
    messagesVm: MessagesViewModel = hiltViewModel()
) {
    // Переписки — свой ViewModel; книга и настройки — общие с журналом
    val state by messagesVm.state.collectAsStateWithLifecycle()
    val dialer by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val pinnedKeys by vm.smsPinned.collectAsStateWithLifecycle()
    /*
     * ВЫБОР И АРХИВ (2.1.0, автор: «долгое нажатие на портрет — режим выбора
     * с удалением и архивом»). Было: переписку можно было только удалить,
     * по одной. Стало: долгое нажатие на портрет включает выбор с этой
     * строкой; сверху — «Выбрать все», «В архив», «Удалить». Тело строки
     * по долгому нажатию — карточки, как раньше.
     *
     * Архив — тем же экраном: showArchive переключает список на архивные
     * переписки («Вернуть из архива» и «Удалить»). Отдельный маршрут не
     * нужен — строки, свайпы и подписи дней те же.
     */
    val archive by vm.smsArchived.collectAsStateWithLifecycle()
    val selection = rememberSelectionState<String>()
    var showArchive by rememberSaveable { mutableStateOf(false) }
    val pendingDeletion by messagesVm.pendingDeletion.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    /*
     * Поиск — полем на месте строки кнопок панели (ListPanel), как
     * в контактах; включается из «Ещё». Ищет по имени из книги, номеру
     * и тексту последнего сообщения.
     */
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // «Новое сообщение» (2.1.3: поле «Кому» на месте панели, NewMessagePicker.kt)
    // переживает поворот: человек мог начать набирать
    var composeNew by rememberSaveable { mutableStateOf(false) }
    var composeQuery by rememberSaveable { mutableStateOf("") }
    val closeCompose = { composeNew = false; composeQuery = "" }
    // Папка (2.2.0): скрытая в настройках — снова «Все»
    var folderName by rememberSaveable { mutableStateOf(SmsFolder.ALL.name) }
    val visibleFolders = remember(state.folders) { state.folders.filter { it.second }.map { it.first } }
    val folder = SmsFolder.entries.firstOrNull { it.name == folderName }?.takeIf { it in visibleFolders } ?: SmsFolder.ALL
    // Поиск по тексту всех SMS (2.2.0) — модель ищет сообщения, экран — переписки
    LaunchedEffect(query) { messagesVm.search(query) }
    // Закрыли «Кому» сами — и пересылка отменена (2.2.0, SmsForward)
    val cancelCompose = { closeCompose(); SmsForward.pending = null }
    // «Переслать» из переписки — сразу «Кому» с текстом пересылки
    val forwarding = SmsForward.pending
    LaunchedEffect(forwarding, state.isDefaultSms) {
        if (forwarding != null && state.isDefaultSms) composeNew = true
    }
    /*
     * Удаление (2.1.0): сразу из списка, «Отменить» — в общем поле с кольцом
     * (DialerViewModel.notifyWithUndo), из хранилища — когда оно погаснет.
     * Закрепление и архив снимаются только у удалённых на самом деле.
     */
    fun deleteThreads(threads: List<MessagesViewModel.Thread>) {
        if (threads.isEmpty()) return
        val (commit, undo) = messagesVm.deleteWithUndo(threads) { deleted ->
            vm.forgetSmsThreads(deleted.map { it.address })
        }
        vm.notifyWithUndo(
            textRes = if (threads.size == 1) R.string.msg_sms_thread_deleted else R.string.msg_sms_threads_deleted,
            arg = if (threads.size == 1) null else threads.size.toString(),
            onTimeout = commit,
            undo = undo
        )
    }
    /*
     * Долгое нажатие — те же карточки действий, что в журнале (2.0.2):
     * номера с вызовом и SMS, «Позвонить», «Закрепить», «Блок», «Ещё».
     * Карточки встают у места касания (TouchAnchor).
     */
    var cardsFor by remember { mutableStateOf<MessagesViewModel.Thread?>(null) }
    var cardsAnchorY by remember { mutableStateOf<Float?>(null) }
    val touchAnchor = rememberTouchAnchor()
    // Роль по просьбе человека («Вести SMS в Ogneliss») — отказ объясняется окном
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
        if (e164.isBlank()) return null
        return byNumber[e164] ?: CallLogGrouper.tailKey(e164)?.let { byNumber[it] }
    }

    /*
     * СВАЙПЫ (2.1.0) — свои, из «Настройки → SMS»: smsSwipeRight /
     * smsSwipeLeft и их дополнительные действия (до трёх — панель кнопок,
     * как в журнале). Порог, подсказка-иконка и выбор SIM — общие.
     *
     * «SMS» здесь открывает переписку сразу, не через выбор номера:
     * номер у переписки один. «Удалить» удаляет переписку (2.1.0 — сразу,
     * с «Отменить»): журнала у сообщений нет, а смысл жеста тот же.
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
                deleteThreads(listOf(thread))
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

    // Удалённые с «Отменить» — не видны; пришло новое сообщение — переписка снова видна
    val visibleThreads = remember(state.threads, pendingDeletion) {
        if (pendingDeletion.isEmpty()) state.threads
        else state.threads.filterNot { t -> pendingDeletion[t.key]?.let { t.last.date <= it } == true }
    }
    // Архивные — отдельно; новое сообщение возвращает переписку само (ArchiveRules)
    val (archivedThreads, activeThreads) = remember(visibleThreads, archive) {
        visibleThreads.partition { ArchiveRules.isSmsArchived(smsPinKey(it.address), it.last.date, archive) }
    }
    // Непрочитанные по папкам — числом справа в списке папок (2.2.0)
    val folderUnread = remember(activeThreads, visibleFolders, byNumber) {
        visibleFolders.associateWith { f ->
            if (f == SmsFolder.STARRED || f == SmsFolder.ALL) 0
            else activeThreads.count { t -> t.unread > 0 && SmsFolders.matches(f, t.address, contactOf(t.address) != null, t.unread) }
        }
    }
    // Папка — только у общего списка; в архиве и при поиске — все переписки
    val inFolder = if (showArchive || query.isNotBlank() || folder == SmsFolder.ALL) null else folder
    val source = remember(showArchive, archivedThreads, activeThreads, inFolder, byNumber) {
        val base = if (showArchive) archivedThreads else activeThreads
        if (inFolder == null) base
        else base.filter { SmsFolders.matches(inFolder, it.address, contactOf(it.address) != null, it.unread) }
    }
    val shown = remember(source, query, byNumber) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) source
        else source.filter { t ->
            val name = contactOf(t.address)?.displayName.orEmpty()
            name.lowercase().contains(q) || t.address.contains(q) || t.last.body.lowercase().contains(q)
        }
    }
    // Закреплённые — в порядке закрепления; остальные — по дням
    /*
     * Все переписки закреплённого номера, а не одна: «8 912…» и «+7 912…»
     * хранилище может держать двумя разговорами, а ключ закрепления у них
     * один. associateBy оставлял последний, и второй пропадал отовсюду.
     */
    val pinnedThreads = remember(shown, pinnedKeys, showArchive) {
        // В архиве закреплённых сверху нет: там всё — по дням
        if (showArchive) emptyList()
        else {
            val order = pinnedKeys.withIndex().associate { (i, k) -> k to i }
            shown.filter { smsPinKey(it.address) in order }
                .sortedBy { order.getValue(smsPinKey(it.address)) }
        }
    }
    val pinnedSet = remember(pinnedKeys) { pinnedKeys.toSet() }
    val days = remember(shown, pinnedSet, showArchive) {
        val now = System.currentTimeMillis()
        shown.filterNot { !showArchive && smsPinKey(it.address) in pinnedSet }
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

    fun selectedThreads(): List<MessagesViewModel.Thread> = shown.filter { threadKey(it) in selection.selected }
    // «Назад»: сперва снять выбор, потом выйти из архива
    BackHandler(enabled = showArchive && !selection.isActive) { showArchive = false }
    BackHandler(enabled = selection.isActive) { selection.clear() }
    BackHandler(enabled = composeNew) { cancelCompose() }

    // Подсказки «Кому»: недавние переписки и контакты под запрос (NewMessage)
    val composeRecents = remember(composeNew, state.threads, byNumber) {
        if (!composeNew) emptyList()
        else NewMessage.recents(
            state.threads.sortedByDescending { it.last.date }.map { it.address },
            contactOf = { contactOf(it) }
        )
    }
    val composeMatches = remember(composeNew, composeQuery, dialer.contacts) {
        if (!composeNew) emptyList() else NewMessage.suggestions(composeQuery, dialer.contacts)
    }
    val pickRecipient: (String) -> Unit = { number ->
        closeCompose()
        onOpenConversation(number)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            when {
                /*
                 * Шапка раздела — как в Ogneliss Mail (2.2.1, SectionTopBar):
                 * название открытой папки («SMS» — все), поиск и действия над
                 * списком. Папку меняют в «Меню» — как в почте. В режиме
                 * «Кому» шапки нет: экран занят подсказками.
                 */
                !showArchive && !composeNew -> SectionTopBar(
                    title = stringResource(if (folder == SmsFolder.ALL) R.string.nav_messages else smsFolderLabel(folder)),
                    subtitle = if (folder == SmsFolder.ALL) null else stringResource(R.string.nav_messages),
                    // Поиск — в меню (2.4.0, как в журнале и контактах)
                    menu = if (state.isDefaultSms) listOf(
                        TopBarMenuItem(
                            if (selection.isActive) OgnelissIcons.Close else OgnelissIcons.Checklist,
                            stringResource(if (selection.isActive) R.string.selection_exit else R.string.selection_start)
                        ) { if (selection.isActive) selection.clear() else selection.startEmpty() },
                        TopBarMenuItem(OgnelissIcons.Archive, stringResource(R.string.archive_title)) {
                            selection.clear(); showArchive = true
                        }
                    ) else emptyList()
                )
                // Архив — своей строкой заголовка со стрелкой назад
                showArchive -> TopAppBar(
                    title = { Text(stringResource(R.string.archive_title)) },
                    navigationIcon = {
                        IconButton(onClick = { showArchive = false }) {
                            Icon(OgnelissIcons.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                        }
                    }
                )
            }
        },
        /*
         * Плавающей «Написать» больше нет (2.1.3, автор: «кнопку „Написать“
         * лучше менять на клавиатуру, а не сбоку»): она в центре панели
         * (PanelCenter) и не закрывает время последней строки.
         */
        /*
         * Та же панель, что у журнала и контактов: Журнал · SMS ·
         * клавиатура · Контакты · Ещё. Клавиатура набора живёт в журнале —
         * кнопка открывает её там.
         */
        bottomBar = {
            // Режим выбора (2.1.1): действия внизу, на месте панели разделов —
            // сверху панель сдвигала список (автор, 09.11.2026; Д2)
            if (selection.isActive) SelectionBottomBar(
                count = selection.count,
                onClose = { selection.clear() },
                onSelectAll = { selection.selectAll(shown.map { threadKey(it) }) },
                // В списке — «В архив», в архиве — «Вернуть из архива»
                onArchive = if (showArchive) null else {
                    {
                        vm.archiveSms(selectedThreads().associate { it.address to it.last.date })
                        selection.clear()
                    }
                },
                onUnarchive = if (!showArchive) null else {
                    {
                        vm.unarchiveSms(selectedThreads().map { it.address })
                        selection.clear()
                    }
                },
                onDelete = {
                    val chosen = selectedThreads()
                    selection.clear()
                    deleteThreads(chosen)
                }
            )
            // Новое сообщение: поле «Кому» на месте панели, над клавиатурой
            else if (composeNew) NewMessageBar(
                value = composeQuery,
                onValueChange = { composeQuery = it },
                canGo = NewMessage.target(composeQuery, composeMatches) != null,
                onGo = { NewMessage.target(composeQuery, composeMatches)?.let(pickRecipient) },
                onClose = cancelCompose
            )
            // В архиве панели разделов нет: из него возвращаются стрелкой
            else if (!showArchive) ListPanel(
                section = MainSection.MESSAGES,
                // «Написать» — в центре панели (2.1.3). Без роли SMS пишут
                // в приложении SMS человека — открываем его
                center = PanelCenter(OgnelissIcons.Edit, R.string.messages_new) {
                    if (state.isDefaultSms) composeNew = true
                    else com.ogneliss.dialer.sms.SmsRole.openCompanionApp(context)
                },
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
                    val pinContext = androidx.compose.ui.platform.LocalContext.current
                    val pinToHome: (() -> Unit)? = if (com.ogneliss.dialer.widget.SectionWidgets.pinSupported(pinContext)) {
                        { menuOpen = false; com.ogneliss.dialer.widget.SectionWidgets.requestPin(pinContext, com.ogneliss.dialer.ui.components.MainSection.MESSAGES) }
                    } else null
                    OverflowMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        settings = settings,
                        onOwnNumber = null,
                        onThemeToggle = {
                            vm.setChoice(
                                "theme",
                                if (settings.theme == ThemeMode.DARK) ThemeMode.LIGHT.name else ThemeMode.DARK.name
                            )
                            menuOpen = false
                        },
                        onSettings = { menuOpen = false; onOpenSettings() },
                        onFakeCall = { menuOpen = false; onFakeCall() },
                        onNotes = { menuOpen = false; onOpenNotes() },
                        onReminders = { menuOpen = false; onOpenReminders() },
                        onHelp = { menuOpen = false; onOpenHelp() },
                        // 2.1.0: значок «SMS» со счётчиком на главный экран (SectionWidgets)
                        onPinToHome = pinToHome,
                        // Папки (2.2.0) — над плитками, как «Папки» в меню почты
                        header = if (state.isDefaultSms && visibleFolders.size > 1) {
                            {
                                SmsFolderRows(
                                    folders = visibleFolders,
                                    current = folder,
                                    unread = folderUnread,
                                    onSelect = { menuOpen = false; selection.clear(); folderName = it.name },
                                    onEdit = { menuOpen = false; onOpenFolders() }
                                )
                            }
                        } else null,
                        onSearch = if (state.isDefaultSms) { { menuOpen = false; searching = true } } else null
                    )
                }
            )
        }
    ) { pad ->
        if (composeNew) {
            Column(Modifier.fillMaxSize().padding(pad)) {
                // Что пересылается — строкой сверху (2.2.0)
                forwarding?.let { ForwardPreview(it) }
                // Подсказки — снизу вверх, первая — у поля (NewMessagePicker.kt)
                NewMessageSuggestions(
                    query = composeQuery,
                    suggestions = composeMatches,
                    recents = composeRecents,
                    onPick = pickRecipient,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad)) {
                /*
                 * Полоса сверху (2.1.0) — только критичное, что исправит лишь
                 * человек (SmsHealth): уведомления о SMS выключены, автозапуск
                 * MIUI запрещён. Иначе — один раз «разрешите автозапуск» там,
                 * где его не проверить. В архиве и при выборе — без полос.
                 */
                if (state.isDefaultSms && !showArchive && !selection.isActive) {
                    state.problem?.let { problem ->
                        SmsProblemStrip(problem) {
                            com.ogneliss.dialer.sms.SmsHealth.fix(context, problem)
                        }
                    } ?: run {
                        if (state.autostartHint) {
                            AutostartHintStrip(
                                onOpen = {
                                    messagesVm.dismissAutostartHint()
                                    com.ogneliss.dialer.util.AutoStart.open(context)
                                },
                                onDismiss = messagesVm::dismissAutostartHint
                            )
                        }
                    }
                }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                /*
                 * Три состояния: нет роли SMS — приложение SMS человека и роль
                 * по просьбе; роль есть, переписок нет — значок и строка по центру
                 * (поиск ничего не нашёл — пусто); иначе список. Пока идёт первое
                 * чтение — пусто, без мигания «сообщений нет».
                 */
                if (!state.isDefaultSms && !state.loading) {
                    CompanionBlock(
                        companion = state.companion,
                        onOpenCompanion = { com.ogneliss.dialer.sms.SmsRole.openCompanionApp(context) },
                        onUseHere = requestRole
                    )
                } else if (folder == SmsFolder.STARRED && !showArchive && query.isBlank()) {
                    // Избранное (2.2.0): не переписки, а отмеченные сообщения
                    StarredList(
                        starred = state.starred,
                        titleOf = { address -> contactOf(address)?.displayName ?: PhoneFormatter.format(address) },
                        onOpen = { e -> onOpenMessage(e.address, e.msgKey) },
                        onUnstar = { e -> messagesVm.unstar(e.msgKey) }
                    )
                } else if (shown.isEmpty() && state.found.isEmpty() && !state.loading &&
                    (showArchive || archivedThreads.isEmpty() || query.isNotBlank() || inFolder != null)
                ) {
                    // Пустой общий список при непустом архиве — не «пусто»: сверху строка «Архив · N»
                    // Пустая папка — без надписи (тихий интерфейс): вкладки внизу и так говорят, где человек
                    if (query.isBlank() && inFolder == null) {
                        MessagesEmpty(if (showArchive) R.string.archive_empty else R.string.messages_empty)
                    }
                } else {
                    @Composable
                    fun ThreadItem(thread: MessagesViewModel.Thread, rowKey: String, position: GroupPosition) {
                        val contact = contactOf(thread.address)
                        val pinned = smsPinKey(thread.address) in pinnedSet
                        // Номер переписки как запись журнала — для кнопок панели
                        val group = remember(thread.address, contact) { threadAsGroup(thread.address, contact) }
                        val slot = thread.last.subId?.let { id -> dialer.sims.firstOrNull { it.subscriptionId == id }?.slotIndex }
                        val selected = selection.isSelected(threadKey(thread))
                        // В режиме выбора касание отмечает строку (2.1.0)
                        val onRowClick: () -> Unit = {
                            when {
                                selection.isActive -> selection.toggle(threadKey(thread))
                                swipeController.isOpen(rowKey) -> swipeController.close()
                                else -> onOpenConversation(thread.address)
                            }
                        }
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
                                    draft = state.drafts[smsPinKey(thread.address)],
                                    title = contact?.displayName ?: PhoneFormatter.format(thread.address),
                                    photoUri = contact?.photoUri,
                                    initialsFrom = contact?.displayName,
                                    pinned = pinned,
                                    // Карта — только при двух и больше: с одной выбирать не из чего
                                    simSlot = slot.takeIf { dialer.sims.size > 1 },
                                    selectionActive = selection.isActive,
                                    selected = selected,
                                    // Портрет: короткое — как строка, долгое — выбор с этой строкой (2.1.0)
                                    onAvatarClick = onRowClick,
                                    onAvatarLongClick = {
                                        swipeController.close()
                                        selection.start(threadKey(thread))
                                    },
                                    // Касание — переписка (раскрытая строка — закрыть); долгое — карточки.
                                    // В режиме выбора оба отмечают строку
                                    modifier = Modifier
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                        .combinedClickable(
                                            onClick = onRowClick,
                                            onLongClick = {
                                                if (selection.isActive) selection.toggle(threadKey(thread))
                                                else {
                                                    swipeController.close()
                                                    cardsAnchorY = touchAnchor.screenY
                                                    cardsFor = thread
                                                }
                                            },
                                            onLongClickLabel = if (selection.isActive) null else stringResource(R.string.sms_message_actions)
                                        )
                                )
                            }
                        }
                    }

                    LazyColumn(
                        // Тень у края прокрутки (2.1.2, Д7 — как в Mail)
                        Modifier.fillMaxSize().trackTouch(touchAnchor).scrollEdgeFade(listState)
                            // Выбор протягиванием по кружкам (2.1.3, DragSelect)
                            .dragSelect(selection, listState) { key ->
                                (key as? String)?.removePrefix("pin_")?.takeIf { it.startsWith("thread:") }
                            },
                        state = listState,
                        contentPadding = PaddingValues(top = Space.xs, bottom = Space.l)
                    ) {
                        // «Архив · N» — первой строкой, только когда архив не пуст (2.1.0)
                        if (!showArchive && !selection.isActive && query.isBlank() && inFolder == null && archivedThreads.isNotEmpty()) {
                            item(key = "archive_row", contentType = "archive_row") {
                                ArchiveEntryRow(archivedThreads.size) { showArchive = true }
                            }
                        }
                        // 2.1.0: пояснение «новое сообщение вернёт переписку» над архивом убрано
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
                        // Поиск по тексту (2.2.0): найденные сообщения — под переписками
                        if (query.isNotBlank() && state.found.isNotEmpty() && !selection.isActive) {
                            item(key = "found_header", contentType = "date_header") {
                                LetterHeader(stringResource(R.string.messages_found_header))
                            }
                            itemsIndexed(state.found, key = { _, m -> "found_" + m.key }, contentType = { _, _ -> "found" }) { _, m ->
                                FoundMessageRow(
                                    message = m,
                                    title = contactOf(m.address)?.displayName ?: PhoneFormatter.format(m.address),
                                    query = query,
                                    onClick = { onOpenMessage(m.address, m.key) }
                                )
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
        }
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
            onDeleteThread = { cardsFor = null; deleteThreads(listOf(thread)) },
            smsPinned = smsPinKey(thread.address) in pinnedSet,
            onToggleSmsPin = { vm.toggleSmsPin(thread.address) },
            onDismiss = { cardsFor = null }
        )
    }

    roleHelp?.let { help -> RoleHelpDialog(help) { roleHelp = null } }
}

/** Ключ строки ленты (MessagesViewModel.Thread.key). */
private fun threadKey(t: MessagesViewModel.Thread): String = t.key

/**
 * Строка переписки — как строка журнала (2.1.0): те же поля по плотности
 * списков, фон строки и размеры. Было: своя высота 72 dp и поля экрана —
 * строки SMS выглядели крупнее и «другими» рядом с журналом.
 */
@Composable
private fun ThreadRow(
    thread: MessagesViewModel.Thread,
    /** Недописанное (2.2.0): «Черновик: …» вместо последнего сообщения. */
    draft: String? = null,
    title: String,
    photoUri: String?,
    initialsFrom: String?,
    pinned: Boolean,
    simSlot: Int?,
    modifier: Modifier,
    /** Режим выбора (2.1.0): кружок вместо портрета, как в журнале. */
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onAvatarClick: () -> Unit = {},
    /** Долгое нажатие на портрет — режим выбора с этой строкой (2.1.0). */
    onAvatarLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val unread = thread.unread > 0
    // Пустой текст — MMS: «Картинка» или «MMS», как в журнале
    val body = thread.last.body.replace('\n', ' ').trim().ifEmpty {
        stringResource(if (thread.last.attachments.isNotEmpty()) R.string.sms_picture else R.string.sms_mms)
    }
    val line = if (thread.last.outgoing) stringResource(R.string.sms_you, body) else body
    /*
     * Время справа (2.1.0): в группе дня — только время, как в журнале:
     * день уже в заголовке, и «7 нояб. 2026 г.» под «СЕГОДНЯ» повторял его.
     * У закреплённых заголовка дня нет — сегодня время, раньше дата.
     */
    val time = remember(thread.last.date, pinned) {
        val date = thread.last.date
        if (!pinned || DateUtils.isToday(date)) DateUtils.formatDateTime(context, date, DateUtils.FORMAT_SHOW_TIME)
        else DateUtils.formatDateTime(
            context, date,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR
        )
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
        // В режиме выбора кружок вместо портрета — как в журнале (CallLogCard)
        if (selectionActive) {
            com.ogneliss.dialer.ui.components.SelectionMark(selected)
        } else Box(
            // Портрет — своя цель касания: тело строки по долгому нажатию — карточки
            if (onAvatarLongClick != null) Modifier
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onAvatarClick,
                    onLongClick = onAvatarLongClick,
                    onLongClickLabel = stringResource(R.string.a11y_avatar_select)
                )
            else Modifier
        ) {
            RowAvatar(photoUri, name = initialsFrom)
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Имя и булавка — во всю свободную ширину; время и SIM — справа.
                // Было: два weight делили строку пополам — имя обрезалось на середине
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
                }
                Spacer(Modifier.width(Space.s))
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
                val draftLabel = stringResource(R.string.sms_draft_label)
                val shownLine: androidx.compose.ui.text.AnnotatedString = if (!draft.isNullOrBlank()) {
                    androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.error))
                        append(draftLabel)
                        pop()
                        append(" ")
                        append(draft.replace('\n', ' ').trim())
                    }
                } else androidx.compose.ui.text.AnnotatedString(line)
                Text(
                    shownLine,
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

/**
 * Строка «Архив · N» над списком (2.1.0): архив виден, только когда
 * в нём что-то есть, — пустой пункт сверху занимал бы место зря.
 */
@Composable
private fun ArchiveEntryRow(count: Int, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = Space.l, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Значок — по ширине портрета: подпись встаёт в колонку имён
            Box(Modifier.width(LocalAppearance.current.avatarSize), contentAlignment = Alignment.Center) {
                Icon(OgnelissIcons.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Space.m))
            Text(
                stringResource(R.string.archive_row, count),
                style = MaterialTheme.typography.listName,
                modifier = Modifier.weight(1f)
            )
        }
    }
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
            // Пустой ключ (номер из одних знаков) совпал бы с любым «пустым» адресом (2.1.3)
            if (n.e164.isBlank()) return@forEach
            putIfAbsent(n.e164, c)
            CallLogGrouper.tailKey(n.e164)?.let { putIfAbsent(it, c) }
        }
    }
}
