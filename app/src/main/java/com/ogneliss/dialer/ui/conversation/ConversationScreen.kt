package com.ogneliss.dialer.ui.conversation

import android.Manifest
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val state by vm.state.collectAsStateWithLifecycle()
    // Ошибка отправки — снизу, не окном: человек может сразу повторить
    val snackbar = remember { SnackbarHostState() }
    // Шторка действий, выделение текста и просмотр картинки — по одному окну за раз
    var actionsFor by remember { mutableStateOf<Message?>(null) }
    var selectTextOf by remember { mutableStateOf<Message?>(null) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    var sendAfterGrant by remember { mutableStateOf(false) }

    // Вернулись на экран (сделали приложением SMS, выдали разрешение) — перечитать
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    /*
     * Три системных окна экрана:
     * • роль SMS — системный выбор приложения SMS по умолчанию;
     * • SEND_SMS — когда диалер не SMS по умолчанию (роль выдаёт его сама);
     *   после выдачи черновик уходит сразу — человек уже нажал «Отправить»;
     * • выбор картинок — системный (Photo Picker): без доступа ко всей
     *   галерее, только к выбранным снимкам.
     */
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refresh()
    }
    val sendPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refresh()
        if (granted && sendAfterGrant) vm.send()
        sendAfterGrant = false
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        vm.addImages(uris)
    }

    val failedText = stringResource(R.string.sms_send_failed)
    LaunchedEffect(state.sendFailed) {
        if (state.sendFailed) {
            snackbar.showSnackbar(failedText)
            vm.consumeSendFailed()
        }
    }

    val onSend = {
        if (SmsRole.canSend(context)) vm.send()
        else {
            sendAfterGrant = true
            sendPermission.launch(Manifest.permission.SEND_SMS)
        }
    }

    ScreenScaffold(
        title = state.title,
        onBack = onBack,
        pinnedTopBar = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (state.dialable) {
                IconButton(onClick = { onCall(vm.address) }) {
                    Icon(OgnelissIcons.Call, contentDescription = stringResource(R.string.action_call))
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
                onSend = onSend
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (!state.isDefaultSms && !state.loading) {
                DefaultSmsBanner(onMakeDefault = {
                    SmsRole.request(context)?.let { roleLauncher.launch(it) }
                })
            }
            if (state.messages.isEmpty() && !state.loading) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (state.isDefaultSms) R.string.sms_empty else R.string.sms_empty_send_only),
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
                Bubble(message, onLongPress, onRetry, onOpenImage)
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
    val label = remember(date) {
        when {
            DateUtils.isToday(date) -> context.getString(R.string.date_group_today)
            DateUtils.isToday(date + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.date_group_yesterday)
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
    onOpenImage: (String) -> Unit
) {
    val context = LocalContext.current
    val mine = message.outgoing
    val failed = message.status == Message.Status.FAILED
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
                    onClick = { if (failed) onRetry(message) },
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
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                // Картинки — MMS, только у приложения SMS по умолчанию
                if (state.isDefaultSms) {
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
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSendNow,
                    modifier = Modifier.padding(start = Space.xs)
                ) {
                    Icon(OgnelissIcons.Send, contentDescription = stringResource(R.string.sms_send))
                }
            }
        }
    }
}

/** Карта отправки: «SIM 1» / «SIM 2» по касанию — меню карт. */
@Composable
private fun SimPicker(state: ConversationViewModel.UiState, onSelectSim: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = state.sims.firstOrNull { it.subscriptionId == state.subId }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(OgnelissIcons.SimCard, contentDescription = null, modifier = Modifier.size(FlowUi.iconSizeInline))
            Text(
                current?.label ?: stringResource(R.string.sms_sim_default),
                modifier = Modifier.padding(start = Space.xs),
                style = MaterialTheme.typography.labelLarge
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.sims.forEach { sim ->
                DropdownMenuItem(
                    text = { Text(sim.label) },
                    onClick = { onSelectSim(sim.subscriptionId); open = false },
                    modifier = Modifier.heightIn(min = FlowUi.menuItemHeight),
                    leadingIcon = if (sim.subscriptionId == state.subId) {
                        { Icon(OgnelissIcons.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

// ── Плашка роли SMS ──────────────────────────────────────────────────────────

/**
 * Плашка «сделать приложением SMS». Не окно и не запрет: отправлять
 * можно и так, а история и входящие — по желанию человека.
 */
@Composable
private fun DefaultSmsBanner(onMakeDefault: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = FlowUi.screenPadding, vertical = Space.s)
    ) {
        Row(Modifier.padding(start = Space.l, end = Space.s, top = Space.s, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.sms_default_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onMakeDefault) {
                Text(stringResource(R.string.sms_default_action), fontWeight = FontWeight.Medium)
            }
        }
    }
}

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
