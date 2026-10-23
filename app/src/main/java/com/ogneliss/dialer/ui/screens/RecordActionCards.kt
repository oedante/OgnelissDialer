package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.ExternalApps
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.data.ContactMessengers
import com.ogneliss.dialer.data.NumberNote
import com.ogneliss.dialer.data.PinnedContacts
import com.ogneliss.dialer.data.PhoneNumber
import com.ogneliss.dialer.data.RulePattern
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.settings.SettingsCommands
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.ActionCardsOverlay
import com.ogneliss.dialer.ui.components.CardAction
import com.ogneliss.dialer.ui.components.CardActionSection
import com.ogneliss.dialer.ui.components.DeleteContactDialog
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.OgnelissDivider
import com.ogneliss.dialer.ui.components.ReminderDialog
import com.ogneliss.dialer.ui.components.ReminderTimeDialog
import com.ogneliss.dialer.ui.components.RowAvatar
import com.ogneliss.dialer.ui.components.rememberActionHaptics
import com.ogneliss.dialer.ui.components.rememberNumberInfoLabels
import com.ogneliss.dialer.ui.components.swipe.RevealActionFactory
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.NoteTeal
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.util.PhoneFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Откуда открыты карточки: от этого зависят быстрые действия. */
enum class CardsOrigin { LOG, CONTACTS }

/**
 * ДЕЙСТВИЯ С ЗАПИСЬЮ ПО ДОЛГОМУ НАЖАТИЮ — КАРТОЧКАМИ (1.5.7).
 *
 * Было (1.2.1–1.5.6): шторка снизу — шапка, четыре круглые кнопки
 * и пятнадцать строк меню. Стало — как в Ogneliss Mail (просьба автора,
 * 22.10): запись поднимается карточкой над размытым списком, под ней —
 * быстрые действия, «Ещё» раскрывает остальные разделами. Оболочка —
 * ActionCardsOverlay; здесь — что именно показать для записи журнала
 * и для контакта.
 *
 * ── Карточка записи показывает все номера контакта ─────────────────────────
 * Отзыв valmikk (4PDA, 21.10): «в контекстное меню в журнале при долгом
 * тапе на контакт добавить карточку контакта (при наличии двух номеров)».
 * У контакта с несколькими номерами под именем — список номеров с кнопками
 * вызова и SMS: позвонить на домашний из записи о звонке с мобильного
 * можно, не открывая карточку. Нажатие на карточку записи открывает
 * полную карточку абонента.
 *
 * ── Быстрые действия ───────────────────────────────────────────────────────
 * Журнал: позвонить, SMS, напомнить, блок — те же, что были в шторке.
 * Контакты: позвонить, SMS, избранное, напомнить — блок для человека
 * из книги нужен реже, он в разделе «Номер».
 *
 * ── Подтверждения остались ─────────────────────────────────────────────────
 * Блокировка по маске, предел закреплённых и удаление контакта открывают
 * окно поверх карточек (CardAction.keepOpen): закрыл окно — карточки
 * на месте, можно выбрать другое. Удаление из журнала — без вопроса,
 * но с «Отменить» (deleteGroupsWithUndo).
 */
@Composable
fun RecordActionCards(
    group: CallLogGroup,
    vm: DialerViewModel,
    /** Место нажатия — TouchAnchor.screenY. */
    anchorY: Float?,
    origin: CardsOrigin,
    onOpenCard: () -> Unit,
    /** Переход в чёрный список: туда ведёт разбор блокировки по маске. */
    onOpenBlockLists: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val state by vm.state.collectAsStateWithLifecycle()
    // Правила нужны, чтобы кнопка знала, блокировать ей или разблокировать
    val rules by vm.rules.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    // Отклик на необратимое: блокировка, снятие, удаление
    val haptics = rememberActionHaptics()

    /** Маска, под которую попал номер, — показывается в предупреждении. */
    var maskWarning by remember { mutableStateOf<String?>(null) }
    var pinLimitReached by remember { mutableStateOf(false) }
    // id контакта, удаление которого подтверждается (1.3.4)
    var deleteContactId by remember { mutableStateOf<Long?>(null) }
    /*
     * Напоминание и заметка — окнами поверх карточек, а не через общие
     * запросы ViewModel: те показывает экран журнала, и из контактов окно
     * открылось бы только по возвращении в журнал (а на планшете, где
     * рядом открыта карточка абонента, — дважды).
     */
    var reminderOpen by remember { mutableStateOf(false) }
    var reminderAt by remember { mutableStateOf<Long?>(null) }
    var noteOpen by remember { mutableStateOf(false) }

    // Контакт записи: по id, иначе первый по алфавиту с этим номером —
    // тот же порядок, что у имени и фото в карточке абонента
    val contact = remember(state.contacts, group.contactId, group.e164) {
        state.contacts.firstOrNull { it.id == group.contactId }
            ?: state.contacts
                .filter { c -> c.numbers.any { it.e164 == group.e164 } }
                .minByOrNull { it.displayName.lowercase() }
    }
    val pinned = vm.isPinned(group.e164)
    val mark = state.spamMarks[group.e164]
    val exactRule = rules.firstOrNull { it.isBlock && it.pattern == group.e164 }
    val maskRule = rules.firstOrNull {
        it.isBlock && it.isMask && RulePattern.matches(it.pattern, group.e164)
    }

    // Мессенджеры: те, что есть у контакта в книге, и — при настройке
    // «Мессенджеры для любого номера» — установленные (как «Написать»
    // в карточке абонента)
    val messengerKinds by produceState(
        emptyList<SwipeAction>(), contact?.id, settings.messengersAnyNumber
    ) {
        value = withContext(Dispatchers.IO) {
            val inBook = contact?.id
                ?.let { id -> ContactMessengers.linksFor(appContext, id).map { it.kind } }
                .orEmpty()
            val installed = if (settings.messengersAnyNumber) {
                ExternalApps.installedMessengers(appContext)
            } else {
                emptyList()
            }
            MESSENGER_ORDER.filter { it in inBook || it in installed }
        }
    }

    // ── Окна подтверждения — поверх карточек ────────────────────────────────
    maskWarning?.let { pattern ->
        OgnelissDialog(
            onDismissRequest = { maskWarning = null },
            title = stringResource(R.string.block_by_mask_title),
            content = { Text(stringResource(R.string.block_by_mask_text, pattern)) },
            confirmButton = {
                TextButton(onClick = {
                    maskWarning = null
                    onDismiss()
                    onOpenBlockLists()
                }) {
                    Text(stringResource(R.string.block_by_mask_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { maskWarning = null }) {
                    Text(stringResource(R.string.nav_close))
                }
            }
        )
    }
    if (pinLimitReached) {
        OgnelissDialog(
            onDismissRequest = { pinLimitReached = false },
            title = stringResource(R.string.pin_limit_title),
            content = {
                Text(stringResource(R.string.pin_limit_text, PinnedContacts.MAX_PINNED))
            },
            confirmButton = {
                TextButton(onClick = { pinLimitReached = false }) {
                    Text(stringResource(R.string.nav_close))
                }
            }
        )
    }
    deleteContactId?.let { id ->
        DeleteContactDialog(
            pinned = vm.isContactPinned(id),
            onConfirm = {
                haptics()
                vm.deleteContact(id)
                deleteContactId = null
                onDismiss()
            },
            onDismiss = { deleteContactId = null }
        )
    }

    if (reminderOpen) {
        ReminderDialog(
            displayName = group.displayName ?: group.number,
            // Вариант не ставит напоминание, а открывает окно с этим временем
            onPick = { at ->
                reminderOpen = false
                reminderAt = at
            },
            onDismiss = { reminderOpen = false }
        )
    }
    reminderAt?.let { at ->
        ReminderTimeDialog(
            displayName = group.displayName?.takeIf { it.isNotBlank() }
                ?: PhoneFormatter.format(group.number),
            initialMillis = at,
            onSave = { chosen ->
                vm.scheduleReminderAt(group.number, group.displayName, chosen)
                reminderAt = null
                onDismiss()
            },
            onDismiss = { reminderAt = null }
        )
    }
    if (noteOpen) {
        val existing = state.notes[group.e164]
        NoteEditorDialog(
            initialNote = existing?.note.orEmpty(),
            initialTags = existing?.tags?.toSet().orEmpty(),
            onSave = { text, tags ->
                vm.saveNote(group.e164, text, tags.toList())
                noteOpen = false
                onDismiss()
            },
            onDismiss = { noteOpen = false }
        )
    }

    // ── Действия ───────────────────────────────────────────────────────────
    val scheme = MaterialTheme.colorScheme
    val call = CardAction(
        OgnelissIcons.Call, stringResource(R.string.action_call),
        onClick = { vm.call(group.number) },
        tint = Color.White, container = CallGreen
    )
    val sms = CardAction(
        OgnelissIcons.Message, stringResource(R.string.action_sms),
        onClick = { vm.openSms(group.number) }
    )
    val remind = CardAction(
        OgnelissIcons.Alarm, stringResource(R.string.action_remind_short),
        onClick = { reminderOpen = true },
        keepOpen = true
    )
    /*
     * Блок — с тремя состояниями, как в карточке: заблокирован точным
     * правилом — снять; попадает под маску — показать маску и предложить
     * открыть список (снять отсюда нельзя, иначе снялась бы вся маска);
     * не заблокирован — блокировать.
     */
    val block = CardAction(
        OgnelissIcons.Block,
        stringResource(
            when {
                exactRule != null -> R.string.action_unblock_short
                maskRule != null -> R.string.action_blocked_by_mask
                else -> R.string.action_block_short
            }
        ),
        onClick = {
            when {
                exactRule != null -> { haptics(); vm.removeRule(group.e164) }
                maskRule != null -> maskWarning = maskRule.pattern
                else -> { haptics(); vm.blockNumber(group.e164) }
            }
        },
        tint = if (exactRule != null) scheme.primary else BlockRed,
        keepOpen = exactRule == null && maskRule != null
    )
    val favourite = contact?.let { c ->
        CardAction(
            if (c.starred) OgnelissIcons.Star else OgnelissIcons.StarBorder,
            stringResource(if (c.starred) R.string.cards_unfavourite else R.string.action_favourite),
            onClick = { vm.toggleStar(c) },
            tint = if (c.starred) scheme.primary else null
        )
    }

    val quick = when (origin) {
        CardsOrigin.LOG -> listOf(call, sms, remind, block)
        CardsOrigin.CONTACTS -> listOfNotNull(call, sms, favourite, remind)
    }

    // ── Разделы «Ещё» ─────────────────────────────────────────────────────
    val write = buildList {
        messengerKinds.forEach { kind ->
            val visual = RevealActionFactory.iconOf(kind)
            add(
                CardAction(
                    visual?.first ?: OgnelissIcons.Chat,
                    stringResource(SettingsCommands.swipeActionLabelRes(kind)),
                    // handleSwipe открывает чат на том номере контакта,
                    // где мессенджер есть, иначе — по номеру записи
                    onClick = { vm.handleSwipe(kind, group) },
                    tint = visual?.second
                )
            )
        }
        contact?.emails?.firstOrNull()?.let { address ->
            add(
                CardAction(
                    OgnelissIcons.Email, stringResource(R.string.cards_email),
                    onClick = { vm.openEmail(address) }
                )
            )
        }
    }

    val contactSection = buildList {
        add(
            CardAction(
                OgnelissIcons.Person, stringResource(R.string.cards_open),
                onClick = onOpenCard
            )
        )
        if (contact == null) {
            add(
                CardAction(
                    OgnelissIcons.PersonAdd, stringResource(R.string.action_add_contact),
                    onClick = { vm.createContact(group.number, group.displayName) }
                )
            )
            add(
                CardAction(
                    OgnelissIcons.Contacts, stringResource(R.string.cards_add_to_existing),
                    onClick = { vm.addNumberToExistingContact(group.number) }
                )
            )
        } else {
            add(
                CardAction(
                    OgnelissIcons.Edit, stringResource(R.string.action_edit),
                    onClick = { vm.editContact(contact.id) }
                )
            )
        }
        add(
            CardAction(
                OgnelissIcons.PushPin,
                stringResource(if (pinned) R.string.action_unpin else R.string.action_pin),
                onClick = {
                    when {
                        pinned -> { vm.unpinNumber(group.e164); onDismiss() }
                        vm.pinNumber(group.e164) -> onDismiss()
                        else -> pinLimitReached = true
                    }
                },
                tint = if (pinned) scheme.primary else null,
                // Предел закреплённых выясняется при нажатии — карточки
                // закрывает само действие
                keepOpen = true
            )
        )
        add(
            CardAction(
                OgnelissIcons.StickyNote2, stringResource(R.string.cards_note),
                onClick = { noteOpen = true },
                keepOpen = true
            )
        )
        if (origin == CardsOrigin.LOG && favourite != null) add(favourite)
    }

    val numberSection = buildList {
        add(
            CardAction(
                OgnelissIcons.ContentCopy, stringResource(R.string.action_copy),
                onClick = { vm.copyNumber(group.number) }
            )
        )
        add(
            CardAction(
                OgnelissIcons.Share, stringResource(R.string.action_share),
                onClick = { vm.handleSwipe(SwipeAction.SHARE_NUMBER, group) }
            )
        )
        // Отметка «спам»: ставит, если нет; снимает любую, если есть.
        // «Мошенники» — в карточке, где видны обе отметки
        add(
            CardAction(
                OgnelissIcons.Report,
                stringResource(if (mark == null) R.string.cards_spam else R.string.cards_not_spam),
                onClick = {
                    haptics()
                    vm.setSpamMark(group.e164, if (mark == null) SpamMarkType.SPAM else null)
                },
                tint = if (mark == null) BlockRed else scheme.primary
            )
        )
        if (origin == CardsOrigin.CONTACTS) add(block)
    }

    val deleteSection = buildList {
        if (origin == CardsOrigin.LOG) {
            add(
                CardAction(
                    OgnelissIcons.Delete, stringResource(R.string.action_delete_from_log),
                    onClick = { haptics(); vm.deleteGroupsWithUndo(listOf(group)) },
                    tint = BlockRed, danger = true
                )
            )
        }
        contact?.let { c ->
            add(
                CardAction(
                    OgnelissIcons.PersonOff, stringResource(R.string.action_delete_contact),
                    onClick = { deleteContactId = c.id },
                    tint = BlockRed, danger = true, keepOpen = true
                )
            )
        }
    }

    val sections = listOfNotNull(
        write.takeIf { it.isNotEmpty() }
            ?.let { CardActionSection(stringResource(R.string.cards_section_write), it) },
        CardActionSection(stringResource(R.string.cards_section_contact), contactSection),
        CardActionSection(stringResource(R.string.cards_section_number), numberSection),
        deleteSection.takeIf { it.isNotEmpty() }
            ?.let { CardActionSection(stringResource(R.string.cards_section_delete), it) }
    )

    val infoLabels = rememberNumberInfoLabels()
    ActionCardsOverlay(
        anchorY = anchorY,
        onDismiss = onDismiss,
        quick = quick,
        sections = sections,
        onPreviewClick = onOpenCard,
        previewDescription = stringResource(R.string.a11y_cards_open)
    ) {
        RecordPreview(
            group = group,
            contact = contact,
            note = state.notes[group.e164],
            infoLine = infoLabels.infoSummary(state.numberInfo[group.e164]),
            showCallInfo = origin == CardsOrigin.LOG && group.lastDate > 0,
            onCall = { number -> vm.call(number); onDismiss() },
            onSms = { number -> vm.openSms(number); onDismiss() }
        )
    }
}

/**
 * КАРТОЧКА ЗАПИСИ: КТО ЭТО.
 *
 * Портрет, имя, номер с подписью, последний звонок, оператор и регион,
 * заметка. У контакта с несколькими номерами — все номера списком,
 * каждый со своими кнопками (отзыв valmikk).
 */
@Composable
private fun ColumnScope.RecordPreview(
    group: CallLogGroup,
    contact: Contact?,
    note: NumberNote?,
    infoLine: String?,
    showCallInfo: Boolean,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
        ?: java.util.Locale.getDefault()
    val numbers = remember(contact) {
        contact?.numbers?.distinctBy { it.e164.ifEmpty { it.value } }.orEmpty()
    }
    val formatted = remember(group.number) { PhoneFormatter.format(group.number) }
    val title = contact?.displayName ?: group.displayName ?: formatted
    val ownLabel = numbers.firstOrNull { it.e164 == group.e164 }?.label?.takeIf { it.isNotBlank() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        RowAvatar(contact?.photoUri ?: group.photoUri, size = PREVIEW_AVATAR, name = contact?.displayName)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Номер под именем — когда он один; несколько — списком ниже
            if (title != formatted && numbers.size < 2) {
                Text(
                    listOfNotNull(formatted, ownLabel).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showCallInfo) {
                val missed = group.lastType == CallType.MISSED
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        previewTypeIcon(group.lastType),
                        contentDescription = null,
                        tint = if (missed) scheme.error else scheme.onSurfaceVariant,
                        modifier = Modifier.size(PREVIEW_META_ICON)
                    )
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        relativeDateLabel(group.lastDate, locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (missed) scheme.error else scheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            infoLine?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    note?.note?.takeIf { it.isNotBlank() }?.let { text ->
        Spacer(Modifier.height(Space.m))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                OgnelissIcons.StickyNote2,
                contentDescription = null,
                tint = NoteTeal,
                modifier = Modifier.size(PREVIEW_META_ICON + Space.xxs)
            )
            Spacer(Modifier.width(Space.s))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (numbers.size >= 2) {
        Spacer(Modifier.height(Space.s))
        OgnelissDivider()
        numbers.forEach { number -> NumberLine(number, number.e164 == group.e164, onCall, onSms) }
    }
}

/** Номер контакта с кнопками вызова и SMS. */
@Composable
private fun NumberLine(
    number: PhoneNumber,
    current: Boolean,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val formatted = remember(number.value) { PhoneFormatter.format(number.value) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = Space.xs)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                formatted,
                style = MaterialTheme.typography.bodyLarge,
                // Номер этой записи — жирнее: видно, с какого был звонок
                fontWeight = if (current) FontWeight.SemiBold else null,
                maxLines = 1
            )
            number.label.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = { onCall(number.value) }) {
            Icon(
                OgnelissIcons.Call,
                contentDescription = stringResource(R.string.a11y_call_number, formatted),
                tint = CallGreen
            )
        }
        IconButton(onClick = { onSms(number.value) }) {
            Icon(
                OgnelissIcons.Message,
                contentDescription = stringResource(R.string.action_sms) + " " + formatted,
                tint = scheme.onSurfaceVariant
            )
        }
    }
}

private fun previewTypeIcon(type: CallType) = when (type) {
    CallType.OUTGOING -> OgnelissIcons.CallMade
    CallType.MISSED -> OgnelissIcons.CallMissed
    CallType.REJECTED, CallType.BLOCKED -> OgnelissIcons.Block
    else -> OgnelissIcons.CallReceived
}

/** Порядок мессенджеров — как в «Написать» карточки абонента. */
private val MESSENGER_ORDER = listOf(
    SwipeAction.TELEGRAM, SwipeAction.WHATSAPP, SwipeAction.MAX, SwipeAction.VIBER
)
private val PREVIEW_AVATAR = 52.dp
private val PREVIEW_META_ICON = 16.dp
