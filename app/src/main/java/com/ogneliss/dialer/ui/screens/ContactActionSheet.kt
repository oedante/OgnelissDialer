package com.ogneliss.dialer.ui.screens

import androidx.compose.ui.unit.coerceAtLeast
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.LocalAppearance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.PinnedContacts
import com.ogneliss.dialer.data.RulePattern
import androidx.compose.ui.graphics.Color
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.rememberActionHaptics
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.ui.text.style.TextOverflow
import com.ogneliss.dialer.ui.theme.NoteTeal
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.components.RowAvatar
import com.ogneliss.dialer.ui.components.SheetRow
import com.ogneliss.dialer.ui.components.SheetGroup
import com.ogneliss.dialer.ui.components.SheetDivider
import com.ogneliss.dialer.ui.components.QuickAction
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * ДЕЙСТВИЯ ПО ДОЛГОМУ НАЖАТИЮ НА ЗАПИСЬ ЖУРНАЛА.
 *
 * ── Шторка, а не диалог ────────────────────────────────────────────────────
 * Диалог появляется по центру экрана: до его пунктов нужно тянуться,
 * и он выглядит как системное предупреждение. Шторка выезжает снизу,
 * то есть прямо под большой палец — а именно здесь список действий,
 * которые совершают одной рукой.
 *
 * ── Порядок пунктов ────────────────────────────────────────────────────────
 * Сверху то, что делают чаще: позвонить, написать. Ниже — работа
 * с контактом. В самом низу разрушительное: блокировка и удаление,
 * отделённые чертой, чтобы случайное касание при закрытии шторки
 * не попало на них.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactActionSheet(
    group: CallLogGroup,
    vm: DialerViewModel,
    onOpenCard: () -> Unit,
    /** Переход в чёрный список: туда ведёт разбор блокировки по маске. */
    onOpenBlockLists: () -> Unit,
    onDismiss: () -> Unit
) {
    /*
     * Шторка раскрывается сразу полностью и учитывает системные панели.
     *
     * По умолчанию ModalBottomSheet открывается наполовину и не знает
     * о навигационной панели: нижние пункты оказываются под кнопками
     * системы, и нажать их нельзя.
     */
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val state by vm.state.collectAsState()

    // Правила нужны, чтобы пункт знал, блокировать ему или разблокировать
    val rules by vm.rules.collectAsState()

    /** Маска, под которую попал номер, — показывается в предупреждении. */
    var maskWarning by remember { mutableStateOf<String?>(null) }

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

    var pinLimitReached by remember { mutableStateOf(false) }

    if (pinLimitReached) {
        // Общий контейнер приложения вместо AlertDialog
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        /*
         * ══════════════════════════════════════════════════════════════════
         * МЕНЮ ЗАПИСИ — КАК МАЛЕНЬКАЯ КАРТОЧКА (1.2.1)
         * ══════════════════════════════════════════════════════════════════
         * Было: пятнадцать строк одним списком со стандартными
         * разделителями во всю ширину. Приходилось прокручивать,
         * а «Позвонить», «SMS», «Напомнить», «Блок» выглядели простыми
         * строками — хотя в карточке это круглые кнопки.
         *
         * Стало, сверху вниз:
         * 1. шапка — портрет, имя, номер, как в строке журнала;
         * 2. четыре главных действия — те же круглые кнопки, что
         *    в карточке (общий QuickAction);
         * 3. работа с контактом — одной панелью;
         * 4. работа с номером — второй панелью;
         * 5. «Удалить из журнала» — отдельно, красным, внизу.
         */
        Column(Modifier.padding(bottom = 16.dp)) {
            // Отклик на необратимое: блокировка, снятие, удаление
            val haptics = rememberActionHaptics()

            // ── 1. Шапка ────────────────────────────────────────────────
            /*
             * Выравнивание шапки по пунктам меню (1.3.3, 03.10.2026, замечание
             * автора: шапка «стоит обособленно»).
             * Было: отступ 24 dp — кружок правее колонки иконок, имя заметно
             * правее текста пунктов.
             * Стало: центр кружка — на центре колонки иконок. Пункты лежат
             * в SheetGroup (отступ FlowUi.screenPadding), у пункта ListItem
             * иконка 24 dp начинается через 16 dp: центр — 12 + 16 + 12 = 40 dp.
             * Размер кружка зависит от настройки «крупные кнопки» (48 или 60 dp),
             * поэтому отступ считается от него.
             */
            val avatarSize = LocalAppearance.current.avatarSize
            val iconColumnCenter = FlowUi.screenPadding + 16.dp + 12.dp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = (iconColumnCenter - avatarSize / 2).coerceAtLeast(8.dp),
                    end = 24.dp,
                    top = 8.dp,
                    bottom = 8.dp
                )
            ) {
                /*
                 * Фото — из книги, как у строки журнала (1.3.3, 03.10.2026).
                 * Было: group.photoUri — кэш системного журнала звонков.
                 * Его заполняет системное приложение для набора номера,
                 * а не наше, поэтому он часто пуст: в журнале портрет
                 * был, а в меню записи — пустой кружок.
                 */
                val photoUri = remember(state.contacts, group.contactId, group.e164) {
                    state.contacts.firstOrNull { it.id == group.contactId }?.photoUri
                        ?: state.contacts.firstOrNull { c -> c.numbers.any { it.e164 == group.e164 } }?.photoUri
                        ?: group.photoUri
                }
                RowAvatar(photoUri)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        group.displayName ?: PhoneFormatter.format(group.number),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (group.displayName != null) {
                        Text(
                            PhoneFormatter.format(group.number),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── 2. Главные действия — как в карточке ─────────────────────
            val exactRule = rules.firstOrNull { it.isBlock && it.pattern == group.e164 }
            val maskRule = rules.firstOrNull {
                it.isBlock && it.isMask && RulePattern.matches(it.pattern, group.e164)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                QuickAction(
                    Icons.Default.Call,
                    R.string.action_call,
                    container = CallGreen,
                    tint = Color.White
                ) { vm.call(group.number); onDismiss() }
                QuickAction(Icons.AutoMirrored.Filled.Message, R.string.action_sms) {
                    vm.openSms(group.number); onDismiss()
                }
                QuickAction(Icons.Default.Alarm, R.string.action_remind_short) {
                    vm.requestReminder(group); onDismiss()
                }
                /*
                 * Блок — с тремя состояниями, как в карточке: заблокирован
                 * точным правилом — снять; попадает под маску — показать
                 * маску и предложить открыть список (снять отсюда нельзя,
                 * иначе снялась бы вся маска); не заблокирован — блокировать.
                 */
                QuickAction(
                    icon = Icons.Default.Block,
                    labelRes = when {
                        exactRule != null -> R.string.action_unblock_short
                        maskRule != null -> R.string.action_blocked_by_mask
                        else -> R.string.action_block_short
                    },
                    tint = if (exactRule != null) MaterialTheme.colorScheme.primary else BlockRed
                ) {
                    when {
                        exactRule != null -> {
                            haptics(); vm.removeRule(group.e164); onDismiss()
                        }
                        maskRule != null -> maskWarning = maskRule.pattern
                        else -> {
                            haptics(); vm.blockNumber(group.e164); onDismiss()
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 3. Контакт ──────────────────────────────────────────────
            val knownContactId = remember(state.contacts, group.e164) {
                state.contacts
                    .filter { contact -> contact.numbers.any { it.e164 == group.e164 } }
                    .minByOrNull { it.displayName.lowercase() }
                    ?.id ?: group.contactId
            }
            val pinned = vm.isPinned(group.e164)
            val historyLabel = stringResource(R.string.subscriber_history)
            val contactLabel = stringResource(
                if (knownContactId == null) R.string.action_add_contact else R.string.action_edit_contact
            )
            val pinLabel = stringResource(if (pinned) R.string.action_unpin else R.string.action_pin)
            val noteLabel = stringResource(R.string.action_add_note)
            SheetGroup {
                        SheetRow(Icons.Default.History, historyLabel, onClick = {
                            onOpenCard(); onDismiss()
                        })
                SheetDivider()
                        SheetRow(
                            if (knownContactId == null) Icons.Default.PersonAdd else Icons.Default.Edit,
                            contactLabel,
                            onClick = {
                                if (knownContactId == null) {
                                    vm.createContact(group.number, group.displayName)
                                } else {
                                    vm.editContact(knownContactId)
                                }
                                onDismiss()
                            }
                        )
                SheetDivider()
                        // Контурная булавка — «не закреплён», залитая — «закреплён»
                        SheetRow(
                            if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            pinLabel,
                            onClick = {
                                if (pinned) {
                                    vm.unpinNumber(group.e164)
                                    onDismiss()
                                } else if (vm.pinNumber(group.e164)) {
                                    onDismiss()
                                } else {
                                    pinLimitReached = true
                                }
                            }
                        )
                SheetDivider()
                        SheetRow(
                            Icons.AutoMirrored.Filled.StickyNote2,
                            noteLabel,
                            iconTint = NoteTeal,
                            onClick = { vm.openNoteEditor(group); onDismiss() }
                        )
            }

            // ── 4. Номер ────────────────────────────────────────────────
            val mark = state.spamMarks[group.e164]
            val copyLabel = stringResource(R.string.action_copy_number)
            val spamLabel = stringResource(
                if (mark == null) R.string.action_mark_spam else R.string.action_unmark_spam
            )
            SheetGroup {
                        SheetRow(Icons.Default.ContentCopy, copyLabel, onClick = {
                            vm.copyNumber(group.number); onDismiss()
                        })
                SheetDivider()
                        // Отметка «спам»: ставит, если нет; снимает любую, если есть.
                        // «Мошенники» — в карточке, где видны обе отметки
                        SheetRow(
                            Icons.Default.Report,
                            spamLabel,
                            color = if (mark == null) BlockRed else MaterialTheme.colorScheme.primary,
                            onClick = {
                                haptics()
                                vm.setSpamMark(group.e164, if (mark == null) SpamMarkType.SPAM else null)
                                onDismiss()
                            }
                        )
            }

            // ── 5. Необратимое — отдельно, внизу ───────────────────────
            SheetRow(
                Icons.Default.Delete,
                stringResource(R.string.action_delete_from_log),
                color = BlockRed,
                onClick = { haptics(); vm.deleteGroups(listOf(group)); onDismiss() },
                // Вне панели — отступ как у групп выше
                standalone = true
            )
        }
    }
}

