package com.ogneliss.dialer.ui.conversation

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
import androidx.compose.ui.graphics.Color
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

/**
 * ЭКРАН ПЕРЕПИСКИ SMS (2.0.0).
 *
 * ── Вид ─────────────────────────────────────────────────────────────────────
 * Как переписка в Ogneliss Mail и принятые правила UI диалера:
 * • свои сообщения — справа на primaryContainer, чужие — слева
 *   на surfaceContainerHigh; скругление — shapes.large (настройка
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
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    onOpenCard: (String) -> Unit,
    onCall: (String) -> Unit,
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
                IconButton(onClick = { onOpenCard(vm.address) }) {
                    Icon(OgnelissIcons.Person, contentDescription = stringResource(R.string.sms_open_card))
                }
            }
        },
        bottomBar = {
            ComposeBar(
                state = state,
                onDraft = vm::onDraft,
                onPickImages = {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveImage = vm::removeImage,
                onSelectSim = vm::selectSim,
                onSend = vm::send
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (state.messages.isEmpty() && !state.loading) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.sms_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                MessageList(
                    messages = state.messages,
                    modifier = Modifier.weight(1f),
                    onLongPress = { actionsFor = it },
                    onRetry = vm::retry,
                    onDownload = vm::downloadMms,
                    onOpenImage = { viewing = it }
                )
            }
        }
    }

    actionsFor?.let { message ->
        MessageActionsSheet(
            message = message,
            canDelete = state.isDefaultSms || message.id < 0,
            onCopy = { value, kind -> SmsClipboard.copy(context, value, kind) },
            onSelectText = { selectTextOf = message },
            onRetry = { vm.retry(message) },
            onDelete = { vm.delete(message) },
            onDismiss = { actionsFor = null }
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
 */
@Composable
private fun MessageList(
    messages: List<Message>,
    modifier: Modifier,
    onLongPress: (Message) -> Unit,
    onRetry: (Message) -> Unit,
    onDownload: (Message) -> Unit,
    onOpenImage: (String) -> Unit
) {
    val listState = rememberLazyListState()
    // Новое сообщение — список у нижнего края (reverseLayout: нулевой — самый новый)
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }
    val newestFirst = remember(messages) { messages.asReversed() }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = FlowUi.screenPadding, vertical = Space.m
        )
    ) {
        newestFirst.forEachIndexed { index, message ->
            item(key = message.key) {
                Bubble(message, onLongPress, onRetry, onDownload, onOpenImage)
            }
            // Подпись дня — над первым сообщением дня (в перевёрнутом списке — после)
            val older = newestFirst.getOrNull(index + 1)
            if (older == null || !sameDay(older.date, message.date)) {
                item(key = "day:" + message.key) { DayCaption(message.date) }
            }
        }
    }
}

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
 * Своё — справа, на primaryContainer; чужое — слева, на
 * surfaceContainerHigh: цвет, а не только сторона, — для тех, кто
 * читает переписку крупным шрифтом, где пузыри во всю ширину.
 * Касание неотправленного — повтор; долгое нажатие — шторка действий.
 * Картинки MMS — квадратом 220 dp, касание открывает просмотр.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    message: Message,
    onLongPress: (Message) -> Unit,
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
    val container = if (mine) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
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
        Text(
            listOfNotNull(time, statusText).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                failed -> MaterialTheme.colorScheme.error
                notDownloaded -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xxs)
        )
    }
}

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
    onSend: () -> Unit
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
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSendNow,
                    colors = if (canSms) IconButtonDefaults.filledIconButtonColors()
                    else IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.padding(start = Space.xs)
                ) {
                    Icon(OgnelissIcons.Send, contentDescription = stringResource(R.string.sms_send))
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
        TextButton(onClick = { open = true }) {
            if (current != null) {
                com.ogneliss.dialer.ui.components.SimBadge(current.slotIndex)
            } else {
                Icon(OgnelissIcons.SimCard, contentDescription = null, modifier = Modifier.size(FlowUi.iconSizeInline))
            }
            Text(
                current?.let { simOrdinalName(it) } ?: stringResource(R.string.sms_sim_default),
                modifier = Modifier.padding(start = Space.xs),
                style = MaterialTheme.typography.labelLarge
            )
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
                    leadingIcon = { com.ogneliss.dialer.ui.components.SimBadge(sim.slotIndex) },
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
