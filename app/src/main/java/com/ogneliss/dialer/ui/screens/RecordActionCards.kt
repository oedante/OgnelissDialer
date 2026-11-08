package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.listName
import com.ogneliss.dialer.ui.components.ActionIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
/**
 * Откуда открыты карточки: журнал, контакты или раздел SMS (2.0.2).
 * В SMS — набор как в журнале, но «Удалить» удаляет переписку.
 */
enum class CardsOrigin { LOG, CONTACTS, MESSAGES }

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
 * ── Подтверждения ──────────────────────────────────────────────────────────
 * Удаление контакта открывает окно поверх карточек (CardAction.keepOpen):
 * закрыл окно — карточки на месте, можно выбрать другое. Удаление
 * из журнала — без вопроса, но с «Отменить» (deleteGroupsWithUndo).
 *
 * 2.1.0 (правило автора 07.11.2026 «не объяснять — выполнить»): окон
 * «Заблокирован маской» и «Закреплённых уже пять» больше нет. Номер под
 * маской разблокируется сразу (в белый список, с «Отменить»); при полном
 * списке «Закрепить» открывает экран «Закреплённые» — там видно, кого
 * открепить.
 */
@Composable
fun RecordActionCards(
    group: CallLogGroup,
    vm: DialerViewModel,
    /** Место нажатия — TouchAnchor.screenY. */
    anchorY: Float?,
    origin: CardsOrigin,
    onOpenCard: () -> Unit,
    /*
     * Переход в чёрный список — туда вело окно блокировки по маске.
     * С 2.1.0 окна нет (разбор у block ниже); параметр остаётся, пока его
     * передают экраны разделов (SMS — MessagesScreen).
     */
    @Suppress("UNUSED_PARAMETER") onOpenBlockLists: () -> Unit,
    // Экран «Закреплённые» — при полном списке закреплённых (2.1.0)
    onOpenPinned: () -> Unit = {},
    /** Раздел SMS: «Удалить переписку» (подтверждение — на экране списка). */
    onDeleteThread: (() -> Unit)? = null,
    /**
     * Раздел SMS (2.1.0): закрепление переписки — своё, отдельно от журнала.
     * null — «Закрепить» закрепляет номер в журнале, как раньше.
     */
    smsPinned: Boolean = false,
    onToggleSmsPin: (() -> Unit)? = null,
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
    // В белом списке — маска на номер не действует (белый список сильнее)
    val allowed = rules.any { it.isAllow && RulePattern.matches(it.pattern, group.e164) }
    val maskRule = if (allowed) null else rules.firstOrNull {
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

    // ── Окно подтверждения — поверх карточек ────────────────────────────────
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
            displayName = group.displayName ?: PhoneFormatter.format(group.number),
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
    // Долгое нажатие на «Позвонить» и «SMS» — выбор SIM (1.8.0); одна карта — нет
    val multiSim = state.sims.size > 1
    val who = group.displayName ?: PhoneFormatter.format(group.number)
    val call = CardAction(
        OgnelissIcons.Call, stringResource(R.string.action_call),
        onClick = { vm.call(group.number) },
        tint = Color.White, container = com.ogneliss.dialer.util.LocalCallAvailability.current.callTint(com.ogneliss.dialer.ui.theme.CallGreen),
        onLongClick = if (multiSim) { { vm.chooseSim(group.number, DialerViewModel.SimAction.CALL, who) } } else null,
        longClickLabel = stringResource(R.string.a11y_choose_sim)
    )
    val sms = CardAction(
        OgnelissIcons.Message, stringResource(R.string.action_sms),
        onClick = { vm.openSms(group.number) },
        // 2.1.0: писать нельзя — значок серый (CallAvailability)
        tint = if (com.ogneliss.dialer.util.LocalCallAvailability.current.canSms) null else scheme.outline,
        onLongClick = if (multiSim) { { vm.chooseSim(group.number, DialerViewModel.SimAction.SMS, who) } } else null,
        longClickLabel = stringResource(R.string.a11y_choose_sim_sms)
    )
    val remind = CardAction(
        OgnelissIcons.Alarm, stringResource(R.string.action_remind_short),
        onClick = { reminderOpen = true },
        keepOpen = true
    )
    /*
     * Блок — с тремя состояниями, как в карточке: заблокирован точным
     * правилом — снять; попадает под маску — в белый список (маску отсюда
     * не снять: разблокировался бы весь диапазон); не заблокирован —
     * блокировать. 2.1.0: было — окно с объяснением маски и переходом
     * в список; стало — сразу, с «Отменить» (vm.allowNumber, vm.blockNumber).
     */
    val unblock = exactRule != null || maskRule != null
    val block = CardAction(
        OgnelissIcons.Block,
        stringResource(if (unblock) R.string.action_unblock_short else R.string.action_block_short),
        onClick = {
            haptics()
            when {
                exactRule != null -> vm.removeRule(group.e164)
                maskRule != null -> vm.allowNumber(group.e164)
                else -> vm.blockNumber(group.e164)
            }
        },
        tint = if (unblock) scheme.primary else BlockRed
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
        CardsOrigin.LOG, CardsOrigin.MESSAGES -> listOf(call, sms, remind, block)
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
        if (onToggleSmsPin != null) add(
            CardAction(
                OgnelissIcons.PushPin,
                stringResource(if (smsPinned) R.string.action_unpin else R.string.action_pin),
                onClick = onToggleSmsPin,
                tint = if (smsPinned) scheme.primary else null
            )
        ) else add(
            CardAction(
                OgnelissIcons.PushPin,
                stringResource(if (pinned) R.string.action_unpin else R.string.action_pin),
                onClick = {
                    when {
                        pinned -> vm.unpinNumber(group.e164)
                        vm.pinNumber(group.e164) -> Unit
                        // Список полон (2.1.0): было окно «Закреплённых уже N» —
                        // стало: экран «Закреплённые», там видно, кого открепить
                        else -> onOpenPinned()
                    }
                    onDismiss()
                },
                tint = if (pinned) scheme.primary else null,
                // Карточки закрывает само действие: при полном списке —
                // уже после перехода на экран «Закреплённые»
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
        if (origin != CardsOrigin.CONTACTS && favourite != null) add(favourite)
    }

    val numberSection = buildList {
        add(
            CardAction(
                OgnelissIcons.ContentCopy, stringResource(R.string.action_copy),
                // Отклик — вибрацией, без сообщения (2.1.0, разбор у copyNumber)
                onClick = { haptics(); vm.copyNumber(group.number) }
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
        if (origin == CardsOrigin.MESSAGES && onDeleteThread != null) {
            add(
                CardAction(
                    OgnelissIcons.Delete, stringResource(R.string.messages_delete_thread_action),
                    onClick = { haptics(); onDeleteThread() },
                    tint = BlockRed, danger = true
                )
            )
        }
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
            onSms = { number -> vm.openSms(number); onDismiss() },
            onChooseSim = if (state.sims.size > 1) {
                { number, action ->
                    vm.chooseSim(number, action, group.displayName)
                    onDismiss()
                }
            } else null
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
    onSms: (String) -> Unit,
    /** Долгое нажатие на трубку или конверт номера — выбор SIM (1.8.0); null — одна карта. */
    onChooseSim: ((String, DialerViewModel.SimAction) -> Unit)? = null
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
                // Как имя в журнале и контактах (2.0.0: было titleMedium, 17 sp)
                style = MaterialTheme.typography.listName,
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
        numbers.forEach { number -> NumberLine(number, number.e164 == group.e164, onCall, onSms, onChooseSim) }
    }
}

/** Номер контакта с кнопками вызова и SMS. */
@Composable
private fun NumberLine(
    number: PhoneNumber,
    current: Boolean,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onChooseSim: ((String, DialerViewModel.SimAction) -> Unit)? = null
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
                // Как номер в строке журнала (2.0.0: было bodyLarge, 17 sp)
                style = MaterialTheme.typography.listName,
                // Номер этой записи — жирнее: видно, с какого был звонок
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
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
        ActionIconButton(
            icon = OgnelissIcons.Call,
            contentDescription = stringResource(R.string.a11y_call_number, formatted),
            tint = com.ogneliss.dialer.util.LocalCallAvailability.current.callTint(com.ogneliss.dialer.ui.theme.CallGreen),
            onClick = { onCall(number.value) },
            onLongClick = onChooseSim?.let { choose -> { choose(number.value, DialerViewModel.SimAction.CALL) } },
            onLongClickLabel = stringResource(R.string.a11y_choose_sim)
        )
        ActionIconButton(
            icon = OgnelissIcons.Message,
            contentDescription = stringResource(R.string.action_sms) + " " + formatted,
            tint = com.ogneliss.dialer.util.LocalCallAvailability.current.smsTint(scheme.onSurfaceVariant),
            onClick = { onSms(number.value) },
            onLongClick = onChooseSim?.let { choose -> { choose(number.value, DialerViewModel.SimAction.SMS) } },
            onLongClickLabel = stringResource(R.string.a11y_choose_sim_sms)
        )
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
