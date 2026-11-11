package com.ogneliss.dialer.ui.conversation

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ogneliss.dialer.R
import com.ogneliss.dialer.sms.Message
import com.ogneliss.dialer.sms.OtpDetector
import com.ogneliss.dialer.sms.MessageNotifications
import com.ogneliss.dialer.sms.SmsClipboard
import com.ogneliss.dialer.sms.SmsRole
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.PhotoViewerDialog
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.ui.components.listSheetContainerColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.ui.components.SelectionAction
import com.ogneliss.dialer.ui.components.SelectionBottomBar
import com.ogneliss.dialer.ui.components.SelectionMark
import com.ogneliss.dialer.ui.components.SelectionState
import com.ogneliss.dialer.ui.components.dragSelect
import com.ogneliss.dialer.ui.components.rememberSelectionState

/**
 * ЭКРАН ПЕРЕПИСКИ SMS (2.0.0).
 *
 * ── Вид ─────────────────────────────────────────────────────────────────────
 * Как переписка в Ogneliss Mail и принятые правила UI диалера:
 * • свои сообщения — справа на primaryContainer, чужие — слева
 *   в тоне собеседника (2.1.3); скругление — shapes.large (настройка
 *   «Углы» его масштабирует);
 * • над первым сообщением дня — подпись дня, как в журнале;
 * • под сообщением — время и состояние («Не отправлено» — цветом ошибки,
 *   касание — повторить);
 * • строка ввода — панель внизу: картинка (MMS), поле, карта, отправка.
 *
 * ── Коды и номера ──────────────────────────────────────────────────────────
 * Во входящем с кодом подтверждения под пузырём — чип «Копировать 482913».
 * Долгое нажатие — шторка: копировать текст, код, номер из текста;
 * выделить часть текста; повторить; удалить. Разбор — OtpDetector,
 * только на устройстве.
 *
 * ── 2.1.0: тихо ────────────────────────────────────────────────────────────
 * • Без роли SMS экран не открывается: переписка передаётся приложению SMS
 *   человека (SmsRole.handOff — ссылка sms: выбрана в «Открыть с помощью»,
 *   роль ушла другому) и экран закрывается. Было: «только отправка»,
 *   плашка «сделать приложением SMS», окно SEND_SMS.
 * • Не отправлено — без сообщения внизу: пузырь «Не отправлено» с повтором
 *   и черновик в поле остаются. Уведомление о неотправленном, пока
 *   переписка на экране, не показывается (MessageNotifications).
 * • Подсказки «разрешите автозапуск» здесь нет — она в списке SMS
 *   и только там, где нужна (SmsHealth).
 * • «MMS не загружено» — касание скачивает (MmsDownloads).
 *
 * ── 2.2.0 ──────────────────────────────────────────────────────────────────
 * • Долгое нажатие — выбор сообщений (как в журнале и списке SMS):
 *   касания отмечают, протягивание по кружкам слева — подряд. Внизу —
 *   копировать, переслать, в избранное, удалить. Копировать одно —
 *   прежняя шторка: код, номера из текста, выделение части.
 * • Избранное — звёздочка у времени.
 * • Звонки с собеседником — строками между сообщениями, с SIM.
 * • Очередь: пузырь «Отправляется · Отменить» на время окна отмены,
 *   «Запланировано на …» — для отложенного. Ничего не всплывает поверх.
 * • Долгое нажатие на «Отправить» — «Отправить позже» (время — то же
 *   окно, что у напоминаний).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    onOpenCard: (String) -> Unit,
    onCall: (String) -> Unit,
    /** Переслать текст (2.2.0): выбор получателя — в разделе SMS. */
    onForward: (String) -> Unit = {},
    vm: ConversationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    /*
     * Без роли SMS — передать переписку приложению SMS и закрыть экран (2.1.0).
     * Решается один раз при входе: роль на глазах не меняется.
     */
    val ownsSms = remember { SmsRole.isDefault(context) }
    if (!ownsSms) {
        LaunchedEffect(Unit) {
            SmsRole.handOff(context, vm.address, vm.initialBody, vm.requestedSubId)
            onBack()
        }
        return
    }
    val state by vm.state.collectAsStateWithLifecycle()
    // Шторка действий, выделение текста и просмотр картинки — по одному окну за раз
    var actionsFor by remember { mutableStateOf<Message?>(null) }
    var selectTextOf by remember { mutableStateOf<Message?>(null) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    // 2.2.0: выбор сообщений и «Отправить позже»
    val selection = rememberSelectionState<String>()
    var scheduling by remember { mutableStateOf(false) }
    BackHandler(enabled = selection.isActive) { selection.clear() }
    val selected = { state.messages.filter { selection.isSelected(it.key) } }

    // Вернулись на экран — перечитать; на экране — без уведомлений о неотправленном здесь
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    MessageNotifications.setConversationVisible(vm.address, true)
                    vm.refresh()
                }
                Lifecycle.Event.ON_PAUSE -> MessageNotifications.setConversationVisible(vm.address, false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            MessageNotifications.setConversationVisible(vm.address, false)
        }
    }

    /*
     * Системное окно экрана одно — выбор картинок (Photo Picker): без доступа
     * ко всей галерее, только к выбранным снимкам. Роль SMS и SEND_SMS
     * здесь больше не спрашиваются (2.1.0): без роли экрана нет, роль
     * выдаёт SEND_SMS сама.
     */
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        vm.addImages(uris)
    }

    ScreenScaffold(
        title = state.title,
        onBack = onBack,
        pinnedTopBar = true,
        /*
         * ПОРТРЕТ СОБЕСЕДНИКА В ШАПКЕ (2.1.3, как в мессенджерах): фото или
         * буквы в его тоне — тем же, что у входящих сообщений; под именем —
         * номер. Касание по шапке — карточка (отдельной кнопки «человек»
         * справа больше нет: она дублировала бы портрет).
         */
        titleContent = {
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        enabled = state.dialable,
                        onClickLabel = stringResource(R.string.sms_open_card)
                    ) { onOpenCard(vm.address) }
                    .padding(end = Space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.ogneliss.dialer.ui.components.RowAvatar(
                    state.photoUri,
                    size = HEADER_AVATAR,
                    name = state.title.takeIf { state.subtitle != null }
                )
                Spacer(Modifier.width(Space.m))
                Column {
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    state.subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        actions = {
            if (state.dialable) {
                IconButton(onClick = { onCall(vm.address) }) {
                    // Зелёная трубка, как везде; звонить нельзя — серая (2.1.0)
                    Icon(
                        OgnelissIcons.Call,
                        contentDescription = stringResource(R.string.action_call),
                        tint = com.ogneliss.dialer.util.LocalCallAvailability.current
                            .callTint(com.ogneliss.dialer.ui.theme.CallGreen)
                    )
                }
            }
        },
        bottomBar = {
            if (selection.isActive) {
                val allStarred = selection.selected.isNotEmpty() && selection.selected.all { it in state.starred }
                SelectionBottomBar(
                    count = selection.count,
                    onClose = { selection.clear() },
                    onSelectAll = null,
                    extra = {
                        SelectionAction(
                            OgnelissIcons.ContentCopy, stringResource(R.string.action_copy), selection.count > 0,
                            {
                                val chosen = selected()
                                // Одно — шторка: код, номера из текста, выделение части; несколько — текстом подряд
                                if (chosen.size == 1) actionsFor = chosen.first()
                                else SmsClipboard.copy(context, joinForCopy(chosen), SmsClipboard.Kind.TEXT)
                                if (chosen.size != 1) selection.clear()
                            }
                        )
                        SelectionAction(
                            OgnelissIcons.Forward, stringResource(R.string.sms_forward), selection.count > 0,
                            {
                                val text = joinForCopy(selected())
                                selection.clear()
                                if (text.isNotBlank()) onForward(text)
                            }
                        )
                        SelectionAction(
                            if (allStarred) OgnelissIcons.StarBorder else OgnelissIcons.Star,
                            stringResource(if (allStarred) R.string.sms_unstar else R.string.sms_star),
                            selection.count > 0,
                            {
                                vm.star(selected(), on = !allStarred)
                                selection.clear()
                            }
                        )
                    },
                    onDelete = {
                        vm.deleteMany(selected())
                        selection.clear()
                    }
                )
            } else ComposeBar(
                state = state,
                onDraft = vm::onDraft,
                onPickImages = {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveImage = vm::removeImage,
                onSelectSim = vm::selectSim,
                onSend = vm::send,
                onSendLater = { scheduling = true }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (state.messages.isEmpty() && state.queued.isEmpty() && !state.loading) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.sms_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Входящие — в тоне собеседника (2.1.3, AvatarPalette.bubble):
                // имя из книги есть, когда под заголовком стоит номер (subtitle)
                val scheme = MaterialTheme.colorScheme
                val highContrast = com.ogneliss.dialer.ui.theme.OgnelissTheme.surfaces.outlined
                val incomingColor = remember(state.title, state.subtitle, scheme, highContrast) {
                    com.ogneliss.dialer.ui.theme.AvatarPalette.bubble(
                        key = state.title.takeIf { state.subtitle != null },
                        dark = scheme.surface.luminance() < 0.5f,
                        highContrast = highContrast,
                        surface = scheme.surface,
                        // В светлой теме surfaceContainerHigh белый, как страница, — берём серый
                        neutral = if (highContrast) scheme.surfaceContainerHigh else scheme.surfaceContainerHighest,
                        own = scheme.primary
                    )
                }
                MessageList(
                    state = state,
                    incomingColor = incomingColor,
                    selection = selection,
                    modifier = Modifier.weight(1f),
                    onRetry = vm::retry,
                    onDownload = vm::downloadMms,
                    onOpenImage = { viewing = it },
                    onCancelQueued = vm::cancelQueued,
                    onSendQueuedNow = vm::sendQueuedNow
                )
            }
        }
    }

    if (scheduling) {
        com.ogneliss.dialer.ui.components.ReminderTimeDialog(
            displayName = state.title,
            initialMillis = System.currentTimeMillis() + SCHEDULE_DEFAULT_MS,
            onSave = { at -> vm.sendLater(at); scheduling = false },
            onDismiss = { scheduling = false },
            title = stringResource(R.string.sms_send_later),
            icon = OgnelissIcons.Schedule
        )
    }

    actionsFor?.let { message ->
        MessageActionsSheet(
            message = message,
            canDelete = state.isDefaultSms || message.id < 0,
            onCopy = { value, kind -> SmsClipboard.copy(context, value, kind) },
            onSelectText = { selectTextOf = message },
            onRetry = { vm.retry(message) },
            onDelete = { vm.delete(message) },
            onDismiss = { actionsFor = null; selection.clear() }
        )
    }

    selectTextOf?.let { message ->
        OgnelissDialog(
            onDismissRequest = { selectTextOf = null },
            title = stringResource(R.string.sms_select_text),
            content = {
                // Выделение любой части: адрес, сумма, ссылка — что нужно
                SelectionContainer {
                    Text(message.body, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectTextOf = null }) { Text(stringResource(R.string.nav_close)) }
            }
        )
    }

    viewing?.let { uri ->
        PhotoViewerDialog(uri = uri, title = state.title, onDismiss = { viewing = null })
    }

}

// ── Список ───────────────────────────────────────────────────────────────────

/**
 * Лента сообщений снизу вверх (reverseLayout): новое — у строки ввода,
 * как в любой переписке, и клавиатура не прячет последнее сообщение.
 * Подпись дня ставится над первым сообщением дня; в перевёрнутом списке
 * это значит — следующим элементом после него.
 *
 * 2.2.0: в ленте и звонки (по дате, между сообщениями), а ниже всех —
 * очередь отправки. В режиме выбора слева у сообщений — кружки,
 * протягивание по ним отмечает подряд (dragSelect).
 */
@Composable
private fun MessageList(
    state: ConversationViewModel.UiState,
    /** Заливка входящих — тон собеседника (2.1.3). */
    incomingColor: Color,
    selection: SelectionState<String>,
    modifier: Modifier,
    onRetry: (Message) -> Unit,
    onDownload: (Message) -> Unit,
    onOpenImage: (String) -> Unit,
    onCancelQueued: (Long) -> Unit,
    onSendQueuedNow: (Long) -> Unit
) {
    val messages = state.messages
    val listState = rememberLazyListState()
    // Новое сообщение — список у нижнего края (reverseLayout: нулевой — самый новый)
    LaunchedEffect(messages.size, state.queued.size) {
        if (messages.isNotEmpty() || state.queued.isNotEmpty()) listState.animateScrollToItem(0)
    }
    // Сообщения и звонки — одной лентой по времени, новые первыми
    val newestFirst: List<Any> = remember(messages, state.calls) {
        if (state.calls.isEmpty()) messages.asReversed()
        else (messages + state.calls).sortedByDescending { if (it is Message) it.date else (it as ConversationViewModel.CallEvent).date }
    }
    val messageKeys = remember(messages) { messages.mapTo(HashSet()) { it.key } }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .dragSelect(selection, listState) { key -> (key as? String)?.takeIf { it in messageKeys } },
        state = listState,
        reverseLayout = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = FlowUi.screenPadding, vertical = Space.m
        )
    ) {
        // Очередь — ниже всего: позже последнего сообщения (в перевёрнутом списке — первой)
        state.queued.asReversed().forEach { queued ->
            item(key = "queued:" + queued.id) { QueuedBubble(queued, onCancelQueued, onSendQueuedNow) }
        }
        newestFirst.forEachIndexed { index, entry ->
            val date = if (entry is Message) entry.date else (entry as ConversationViewModel.CallEvent).date
            if (entry is Message) {
                /*
                 * ВРЕМЯ — ОДНО НА СООБЩЕНИЯ ПОДРЯД (2.1.3, обзор интерфейса).
                 * Было: строка времени под каждым пузырём — три сообщения за
                 * минуту давали три одинаковых «05:37», и лента наполовину
                 * состояла из подписей. Стало: время — под последним из идущих
                 * подряд (та же сторона, тот же день, не дальше [TIME_GROUP_MS]
                 * от следующего); состояние («Не отправлено», «Доставлено»)
                 * показывается всегда.
                 */
                val newer = newestFirst.getOrNull(index - 1) as? Message
                val showTime = newer == null || newer.outgoing != entry.outgoing ||
                    newer.date - entry.date > TIME_GROUP_MS || !sameDay(newer.date, entry.date)
                item(key = entry.key) {
                    SelectableRow(selection, entry.key) {
                        Bubble(
                            message = entry,
                            incomingColor = incomingColor,
                            showTime = showTime,
                            starred = entry.key in state.starred,
                            onLongPress = { m ->
                                if (selection.isActive) selection.toggle(m.key) else selection.start(m.key)
                            },
                            onClick = if (selection.isActive) { m -> selection.toggle(m.key) } else null,
                            onRetry = onRetry,
                            onDownload = onDownload,
                            onOpenImage = onOpenImage
                        )
                    }
                }
            } else if (entry is ConversationViewModel.CallEvent) {
                item(key = entry.key) { CallLine(entry) }
            }
            // Подпись дня — над первым сообщением дня (в перевёрнутом списке — после)
            val older = newestFirst.getOrNull(index + 1)
            val olderDate = when (older) {
                is Message -> older.date
                is ConversationViewModel.CallEvent -> older.date
                else -> null
            }
            if (olderDate == null || !sameDay(olderDate, date)) {
                val key = if (entry is Message) entry.key else (entry as ConversationViewModel.CallEvent).key
                item(key = "day:$key") { DayCaption(date) }
            }
        }
    }
}

/**
 * Строка ленты в режиме выбора (2.2.0): кружок слева, отмеченное — подсветкой.
 * Вне режима — только содержимое, без отступа под кружок.
 */
@Composable
private fun SelectableRow(selection: SelectionState<String>, key: String, content: @Composable () -> Unit) {
    if (!selection.isActive) {
        content()
        return
    }
    val on = selection.isSelected(key)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClickLabel = stringResource(R.string.sms_message_actions)) { selection.toggle(key) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionMark(on)
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * Звонок в ленте переписки (2.2.0): тип значком (пропущенный — красным),
 * словом, длительность, время и SIM при двух картах. Строкой по центру,
 * мельче сообщений: это контекст переписки, а не её часть.
 */
@Composable
private fun CallLine(event: ConversationViewModel.CallEvent) {
    val context = LocalContext.current
    val typeRes = when (event.type) {
        CallType.INCOMING -> R.string.call_incoming
        CallType.OUTGOING -> R.string.call_outgoing
        CallType.MISSED -> R.string.call_missed
        CallType.REJECTED -> R.string.call_rejected
        CallType.BLOCKED -> R.string.call_blocked
        CallType.VOICEMAIL -> R.string.call_voicemail
    }
    val icon = when (event.type) {
        CallType.OUTGOING -> OgnelissIcons.CallMade
        CallType.MISSED -> OgnelissIcons.CallMissed
        CallType.REJECTED, CallType.BLOCKED -> OgnelissIcons.Block
        else -> OgnelissIcons.CallReceived
    }
    val missed = event.type == CallType.MISSED
    val color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val time = remember(event.date) { DateUtils.formatDateTime(context, event.date, DateUtils.FORMAT_SHOW_TIME) }
    val duration = if (event.durationSec > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d", event.durationSec / 60, event.durationSec % 60)
    } else null
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(FlowUi.iconSizeInline))
        Spacer(Modifier.width(Space.xs))
        Text(
            listOfNotNull(stringResource(typeRes), duration, time).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
        event.simSlot?.let { slot ->
            Spacer(Modifier.width(Space.xs))
            com.ogneliss.dialer.ui.components.SimBadge(slot)
        }
    }
}

/**
 * SMS в очереди (2.2.0): свой пузырь бледнее отправленного, под ним —
 * «Отправляется» или «Запланировано на …» и действия словами.
 * Ничего не всплывает поверх ленты и строки ввода — автор: «отмена
 * отправки, только чтобы не сильно мешало и не перекрывало интерфейс».
 */
@Composable
private fun QueuedBubble(queued: ConversationViewModel.Queued, onCancel: (Long) -> Unit, onSendNow: (Long) -> Unit) {
    val context = LocalContext.current
    val whenText = remember(queued.atMillis) {
        DateUtils.formatDateTime(
            context, queued.atMillis,
            DateUtils.FORMAT_SHOW_TIME or (if (DateUtils.isToday(queued.atMillis)) 0 else DateUtils.FORMAT_SHOW_DATE)
        )
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = Space.xxs),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                queued.body,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (queued.scheduled) {
                Icon(
                    OgnelissIcons.Schedule, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(FlowUi.iconSizeInline)
                )
            }
            Text(
                if (queued.scheduled) stringResource(R.string.sms_scheduled_for, whenText)
                else stringResource(R.string.sms_status_sending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.xs)
            )
            if (queued.scheduled) QueueLink(stringResource(R.string.sms_send_now)) { onSendNow(queued.id) }
            QueueLink(stringResource(R.string.sms_undo)) { onCancel(queued.id) }
        }
    }
}

/** Действие под пузырём очереди — словом, цветом действия; касание не меньше 48 dp по высоте. */
@Composable
private fun QueueLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Space.s, vertical = Space.m)
    )
}

/** Текст выбранных для копирования и пересылки: по порядку, через пустую строку. */
private fun joinForCopy(messages: List<Message>): String =
    messages.sortedBy { it.date }.map { it.body }.filter { it.isNotBlank() }.joinToString("\n\n")

/** «Отправить позже» — по умолчанию через час: окно сразу предлагает разумное. */
private const val SCHEDULE_DEFAULT_MS = 60 * 60_000L

/** «Сегодня», «Вчера», «ср, 4 нояб.» — как группы давности в журнале. */
@Composable
private fun DayCaption(date: Long) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val label = remember(date, resources) {
        when {
            DateUtils.isToday(date) -> resources.getString(R.string.date_group_today)
            DateUtils.isToday(date + DateUtils.DAY_IN_MILLIS) -> resources.getString(R.string.date_group_yesterday)
            else -> DateUtils.formatDateTime(
                context, date,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
            )
        }
    }
    Text(
        label,
        Modifier.fillMaxWidth().padding(vertical = Space.m),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/**
 * Пузырь сообщения.
 *
 * Своё — справа, на primaryContainer; чужое — слева, в тоне собеседника
 * (2.1.3, AvatarPalette.bubble; было — surfaceContainerHigh, в светлой
 * теме белое на белом): цвет, а не только сторона, — для тех, кто
 * читает переписку крупным шрифтом, где пузыри во всю ширину.
 * Касание неотправленного — повтор; долгое нажатие — шторка действий.
 * Картинки MMS — квадратом 220 dp, касание открывает просмотр.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    message: Message,
    incomingColor: Color,
    /** Время под пузырём: у идущих подряд — только под последним (2.1.3). */
    showTime: Boolean,
    /** В избранном — звёздочка у времени (2.2.0). */
    starred: Boolean,
    onLongPress: (Message) -> Unit,
    /** Режим выбора (2.2.0): касание отмечает; null — обычное касание. */
    onClick: ((Message) -> Unit)?,
    onRetry: (Message) -> Unit,
    onDownload: (Message) -> Unit,
    onOpenImage: (String) -> Unit
) {
    val context = LocalContext.current
    val mine = message.outgoing
    val failed = message.status == Message.Status.FAILED
    val notDownloaded = message.status == Message.Status.NOT_DOWNLOADED
    // Код ищется только во входящих: в своём сообщении он человеку не нужен
    val code = remember(message.key, message.body) {
        if (mine) null else OtpDetector.code(message.body)
    }
    val container = if (mine) MaterialTheme.colorScheme.primaryContainer else incomingColor
    val onContainer = if (mine) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val time = remember(message.date) {
        DateUtils.formatDateTime(context, message.date, DateUtils.FORMAT_SHOW_TIME)
    }
    // «Отправлено» и «Получено» не пишем: это обычное состояние, шум под каждым пузырём
    val statusText = when (message.status) {
        Message.Status.SENDING -> stringResource(R.string.sms_status_sending)
        Message.Status.DELIVERED -> stringResource(R.string.sms_status_delivered)
        Message.Status.FAILED -> stringResource(R.string.sms_status_failed)
        Message.Status.NOT_DOWNLOADED -> stringResource(R.string.sms_status_not_downloaded)
        else -> null
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = Space.xxs),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = container,
            contentColor = onContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    // Касание: не отправлено — повтор; MMS не загружено — скачать (2.1.0)
                    onClick = {
                        when {
                            onClick != null -> onClick(message)
                            failed -> onRetry(message)
                            notDownloaded -> onDownload(message)
                        }
                    },
                    onLongClick = { onLongPress(message) },
                    onLongClickLabel = stringResource(R.string.sms_message_actions)
                )
        ) {
            Column(Modifier.padding(horizontal = Space.m, vertical = Space.s)) {
                message.subject?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall)
                }
                message.attachments.filter { it.contentType.startsWith("image/") }.forEach { image ->
                    AsyncImage(
                        model = image.uri,
                        contentDescription = stringResource(R.string.sms_picture),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(vertical = Space.xs)
                            .size(220.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onOpenImage(image.uri) }
                    )
                }
                if (message.body.isNotBlank()) {
                    Text(message.body, style = MaterialTheme.typography.bodyLarge)
                } else if (notDownloaded && message.subject.isNullOrBlank()) {
                    // Пустой пузырь не читается — хотя бы «MMS»
                    Text(stringResource(R.string.sms_mms), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (code != null) {
            AssistChip(
                onClick = { SmsClipboard.copy(context, code, SmsClipboard.Kind.CODE) },
                label = { Text(stringResource(R.string.sms_copy_code_action, code)) },
                leadingIcon = {
                    Icon(OgnelissIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(FlowUi.iconSizeInline))
                },
                modifier = Modifier.padding(top = Space.xs)
            )
        }
        if (showTime || statusText != null || starred) Row(
            Modifier.padding(horizontal = Space.xs, vertical = Space.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (starred) {
                Icon(
                    OgnelissIcons.Star,
                    contentDescription = stringResource(R.string.sms_starred),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(STAR_SIZE).padding(end = Space.xxs)
                )
            }
            if (showTime || statusText != null) Text(
                listOfNotNull(time, statusText).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    failed -> MaterialTheme.colorScheme.error
                    notDownloaded -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** Звёздочка избранного у времени — по высоте строки времени. */
private val STAR_SIZE = 14.dp

/** Портрет в шапке переписки — меньше строки списка: шапка ниже. */
private val HEADER_AVATAR = 36.dp

/** Кнопка «Отправить» — размер стандартной кнопки-значка. */
private val SEND_BUTTON = 48.dp

/** Сообщения одной стороны не дальше этого друг от друга — под одним временем. */
private const val TIME_GROUP_MS = 3 * 60_000L

// ── Строка ввода ─────────────────────────────────────────────────────────────

/**
 * Строка ввода — панелью внизу, над клавиатурой.
 *
 * Отступ — объединение клавиатуры и навигационной панели: без клавиатуры
 * панель стоит над жестовой полоской, с клавиатурой — вплотную к ней.
 * Слева — картинка (MMS доступно только приложению SMS по умолчанию),
 * поле до шести строк, справа — карта (при двух SIM) и «Отправить».
 * Под полем при длинном тексте — «2 · 35»: сколько SMS уйдёт и сколько
 * знаков осталось в последней: человек видит, что платит за две.
 */
@Composable
private fun ComposeBar(
    state: ConversationViewModel.UiState,
    onDraft: (String) -> Unit,
    onPickImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSelectSim: (Int) -> Unit,
    onSend: () -> Unit,
    /** Долгое нажатие на «Отправить» — «Отправить позже» (2.2.0); с картинками нет. */
    onSendLater: () -> Unit
) {
    val canSendNow = !state.sending && (state.draft.isNotBlank() || state.images.isNotEmpty())
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.s)) {
            if (state.images.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    modifier = Modifier.padding(start = Space.s, bottom = Space.s)
                ) {
                    items(state.images, key = { it.toString() }) { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = stringResource(R.string.sms_picture),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium)
                            )
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = Color.Black.copy(alpha = 0.55f),
                                contentColor = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(Space.xxs)
                                    .size(22.dp)
                                    .clickable { onRemoveImage(uri) }
                            ) {
                                Icon(
                                    OgnelissIcons.Close,
                                    contentDescription = stringResource(R.string.sms_remove_picture),
                                    modifier = Modifier.padding(Space.xxs)
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                // Картинки — MMS, только у приложения SMS по умолчанию и если оператор разрешает (2.1.0)
                if (state.isDefaultSms && state.mmsEnabled) {
                    IconButton(onClick = onPickImages) {
                        Icon(OgnelissIcons.AddPhotoAlternate, contentDescription = stringResource(R.string.sms_attach_picture))
                    }
                } else {
                    Spacer(Modifier.size(Space.s))
                }
                TextField(
                    value = state.draft,
                    onValueChange = onDraft,
                    placeholder = { Text(stringResource(R.string.sms_compose_hint)) },
                    supportingText = if (state.counter != null) {
                        { Text(state.counter) }
                    } else null,
                    maxLines = 6,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                )
                if (state.sims.size > 1) SimPicker(state, onSelectSim)
                // 2.1.0: отправить нельзя (нет SIM, режим полёта) — кнопка серая
                val canSms = com.ogneliss.dialer.util.LocalCallAvailability.current.canSms
                /*
                 * «Отправить» — касание; долгое нажатие — «Отправить позже»
                 * (2.2.0, как в мессенджерах). Своей кнопкой, а не
                 * FilledIconButton: у той нет долгого нажатия. Вид — тот же.
                 */
                val container = when {
                    !canSendNow -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    canSms -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
                val content = when {
                    !canSendNow -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    canSms -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.outline
                }
                val laterLabel = stringResource(R.string.sms_send_later)
                Box(
                    Modifier
                        .padding(start = Space.xs)
                        .size(SEND_BUTTON)
                        .clip(CircleShape)
                        .background(container)
                        .combinedClickable(
                            enabled = canSendNow,
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClick = onSend,
                            onLongClick = if (state.images.isEmpty()) onSendLater else null,
                            onLongClickLabel = laterLabel
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(OgnelissIcons.Send, contentDescription = stringResource(R.string.sms_send), tint = content)
                }
            }
        }
    }
}

/**
 * Карта отправки по касанию — меню карт.
 *
 * 2.1.0: подпись — порядковый номер слота «SIM 1» / «SIM 2», как в окне
 * выбора SIM при звонке (SimChoiceCards). Было: sim.label — имя карты
 * в системе, а оно на части прошивок техническое («Карта 3», номер
 * подписки), и по нему не понять, какая это карта (автор, 07.11.2026).
 * Имя, заданное человеком, или оператор — второй строкой в меню.
 */
@Composable
private fun SimPicker(state: ConversationViewModel.UiState, onSelectSim: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = state.sims.firstOrNull { it.subscriptionId == state.subId }
    Box {
        /*
         * КОМПАКТНО: «▯1» ВМЕСТО «▯ SIM 1» (2.1.1).
         *
         * Автор, 09.11.2026: «значок выбора SIM сделаем компактней, а то много
         * места занимает». Было: кнопка с текстом «SIM 1» — около 110 dp, и
         * поле сообщения сужалось на треть строки. Стало: кнопка размером
         * с соседние (48 dp) — значок карты и цифра цветом карты (SimColors),
         * как пометка SIM в строках журнала и SMS. «SIM 1» словами, имя
         * карты и оператор — в меню по касанию; TalkBack читает «SIM 1».
         */
        val name = current?.let { simOrdinalName(it) } ?: stringResource(R.string.sms_sim_default)
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = name }
        ) {
            if (current != null) {
                val simColor = com.ogneliss.dialer.ui.theme.SimColors.of(current.slotIndex)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.ogneliss.dialer.ui.components.SimIcon(current.slotIndex, Modifier.size(FlowUi.iconSizeInline))
                    Text(
                        (current.slotIndex + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = simColor,
                        modifier = Modifier.padding(start = Space.xxs)
                    )
                }
            } else {
                Icon(OgnelissIcons.SimCard, contentDescription = null, modifier = Modifier.size(FlowUi.iconSizeInline))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.sims.forEach { sim ->
                val second = simSecondLine(sim)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(simOrdinalName(sim))
                            if (second != null) {
                                Text(
                                    second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = { onSelectSim(sim.subscriptionId); open = false },
                    modifier = Modifier.heightIn(min = FlowUi.menuItemHeight),
                    leadingIcon = { com.ogneliss.dialer.ui.components.SimIcon(sim.slotIndex, Modifier.size(FlowUi.iconSizeInline)) },
                    trailingIcon = if (sim.subscriptionId == state.subId) {
                        { Icon(OgnelissIcons.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

/** «SIM 1» — порядковый номер слота, как в настройках Android. */
private fun simOrdinalName(sim: com.ogneliss.dialer.data.SimSlot): String = "SIM ${sim.slotIndex + 1}"

/** Вторая строка: имя карты, если оно не «SIM n», иначе оператор. */
private fun simSecondLine(sim: com.ogneliss.dialer.data.SimSlot): String? =
    sim.label.takeIf { it.isNotBlank() && it != simOrdinalName(sim) } ?: sim.carrier?.takeIf { it.isNotBlank() }

// ── Действия с сообщением ────────────────────────────────────────────────────

/**
 * Действия с сообщением — шторкой, как действия с записью журнала.
 *
 * Порядок — по частоте: код (ради него чаще всего и открывают),
 * весь текст, номера из текста, выделение части, повтор, удаление.
 * Удалить можно только у приложения SMS по умолчанию (или своё
 * отправленное за этот раз): чужое хранилище без роли не изменить.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(
    message: Message,
    canDelete: Boolean,
    onCopy: (String, SmsClipboard.Kind) -> Unit,
    onSelectText: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val code = remember(message.key) { if (message.outgoing) null else OtpDetector.code(message.body) }
    val phones = remember(message.key) { OtpDetector.phones(message.body).take(3) }
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = listSheetContainerColor()) {
        Column(Modifier.padding(bottom = FlowUi.sheetBottomPadding)) {
            val row: @Composable (String, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit) -> Unit =
                { label, icon, action ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Icon(icon, contentDescription = null) },
                        colors = itemColors,
                        modifier = Modifier.clickable { action(); onDismiss() }
                    )
                }
            if (code != null) {
                row(stringResource(R.string.sms_copy_code_action, code), OgnelissIcons.ContentCopy) {
                    onCopy(code, SmsClipboard.Kind.CODE)
                }
            }
            if (message.body.isNotBlank()) {
                row(stringResource(R.string.sms_copy_text), OgnelissIcons.ContentCopy) {
                    onCopy(message.body, SmsClipboard.Kind.TEXT)
                }
            }
            phones.forEach { phone ->
                row(stringResource(R.string.sms_copy_number, phone), OgnelissIcons.ContentCopy) {
                    onCopy(phone, SmsClipboard.Kind.NUMBER)
                }
            }
            if (message.body.isNotBlank()) {
                row(stringResource(R.string.sms_select_text), OgnelissIcons.SelectAll, onSelectText)
            }
            if (message.status == Message.Status.FAILED && !message.isMms) {
                row(stringResource(R.string.sms_retry), OgnelissIcons.Send, onRetry)
            }
            if (canDelete) {
                row(stringResource(R.string.sms_delete), OgnelissIcons.Delete, onDelete)
            }
        }
    }
}

/** Один календарный день в часовом поясе телефона. */
private fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}
