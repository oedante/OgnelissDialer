package com.ogneliss.dialer.ui.conversation

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.CallLogGrouper
import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.ListPanel
import com.ogneliss.dialer.ui.components.MainSection
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.RoleHelpDialog
import com.ogneliss.dialer.ui.components.RowAvatar
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.components.rememberSmsRoleRequest
import com.ogneliss.dialer.ui.screens.OverflowMenu
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.ui.theme.listName
import com.ogneliss.dialer.util.PhoneFormatter
import com.ogneliss.dialer.util.RoleAccess

/**
 * РАЗДЕЛ «SMS» (2.0.0) — список переписок.
 *
 * ── Вид ─────────────────────────────────────────────────────────────────────
 * Как журнал и контакты: строка — фото или буквы, имя (listName, 15 sp),
 * последнее сообщение одной строкой («Вы: …» для своего), время справа,
 * число непрочитанных меткой. Непрочитанная переписка — имя и текст темнее.
 * Снизу — та же панель, что у журнала и контактов (ListPanel): разделы,
 * клавиатура, «Ещё» с поиском.
 *
 * «Написать» в шапке — новое сообщение: номер или имя из книги.
 * Долгое нажатие на переписку — удалить её (с подтверждением).
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
    messagesVm: MessagesViewModel = hiltViewModel()
) {
    // Переписки — свой ViewModel; книга и настройки — общие с журналом
    val state by messagesVm.state.collectAsStateWithLifecycle()
    val dialer by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()

    // «Ещё»: поиск, разделы, настройки — как у журнала и контактов
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

    val shown = remember(state.threads, query, byNumber) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.threads
        else state.threads.filter { t ->
            val name = contactOf(t.address)?.displayName.orEmpty()
            name.lowercase().contains(q) || t.address.contains(q) || t.last.body.lowercase().contains(q)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.messages_title),
        onBack = { onOpenSection(MainSection.JOURNAL) },
        showBack = false,
        pinnedTopBar = true,
        actions = {
            IconButton(onClick = { composeNew = true }) {
                Icon(OgnelissIcons.Edit, contentDescription = stringResource(R.string.messages_new))
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
                menuContent = {
                    // Своего номера, очистки журнала и выбора нескольких здесь нет
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
        Column(Modifier.fillMaxSize().padding(pad)) {
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
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { "${it.threadId}:${it.address}" }) { thread ->
                        val contact = contactOf(thread.address)
                        ThreadRow(
                            thread = thread,
                            title = contact?.displayName ?: PhoneFormatter.format(thread.address),
                            photoUri = contact?.photoUri,
                            initialsFrom = contact?.displayName,
                            // Касание — переписка; долгое — удалить её
                            modifier = Modifier.combinedClickable(
                                onClick = { onOpenConversation(thread.address) },
                                onLongClick = { confirmDelete = thread },
                                onLongClickLabel = stringResource(R.string.sms_delete)
                            )
                        )
                    }
                }
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
                    messagesVm.deleteThread(thread)
                }) { Text(stringResource(R.string.sms_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.nav_close)) }
            }
        )
    }

    roleHelp?.let { help -> RoleHelpDialog(help) { roleHelp = null } }
}

/** Строка переписки — по размерам как строка журнала. */
@Composable
private fun ThreadRow(
    thread: MessagesViewModel.Thread,
    title: String,
    photoUri: String?,
    initialsFrom: String?,
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
            .heightIn(min = 72.dp)
            .padding(horizontal = FlowUi.screenPadding, vertical = Space.s),
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
                    modifier = Modifier.weight(1f)
                )
                Text(
                    time,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
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

/** Номер → контакт: E.164 и последние 10 цифр; первый по алфавиту побеждает. */
private fun contactIndex(contacts: List<Contact>): Map<String, Contact> = buildMap {
    contacts.sortedBy { it.displayName.lowercase() }.forEach { c ->
        c.numbers.forEach { n ->
            putIfAbsent(n.e164, c)
            CallLogGrouper.tailKey(n.e164)?.let { putIfAbsent(it, c) }
        }
    }
}
