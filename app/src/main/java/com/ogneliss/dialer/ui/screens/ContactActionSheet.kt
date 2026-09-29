package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.PinnedContacts
import com.ogneliss.dialer.data.RulePattern
import androidx.compose.ui.graphics.Color
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.UnblockGreen
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.rememberActionHaptics
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.theme.Dimens
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
        Column(Modifier.padding(bottom = 24.dp)) {

            // Шапка: с кем работаем. Без неё после долгого нажатия
            // легко потерять, к какой записи относится список
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    group.displayName ?: PhoneFormatter.format(group.number),
                    style = MaterialTheme.typography.titleLarge
                )
                if (group.displayName != null) {
                    Text(
                        PhoneFormatter.format(group.number),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            SheetAction(Icons.Default.Call, R.string.action_call) {
                vm.call(group.number); onDismiss()
            }
            SheetAction(Icons.AutoMirrored.Filled.Message, R.string.action_sms) {
                vm.openSms(group.number); onDismiss()
            }
            SheetAction(Icons.Default.History, R.string.subscriber_history) {
                onOpenCard(); onDismiss()
            }

            /*
             * ЕСТЬ ЛИ АБОНЕНТ В КНИГЕ — ПРОВЕРКА ПО НОМЕРУ.
             *
             * ── Почему не group.contactId ──────────────────────────────────
             * Он разбирается из CACHED_LOOKUP_URI журнала, а на многих
             * прошивках последний сегмент этой строки — lookup-ключ,
             * а не число. Разбор возвращает null, приложение считает
             * контакт отсутствующим — и предлагает «В контакты» тем,
             * кто в книге давно.
             *
             * Нажатие приводило к созданию дубликата.
             *
             * Сверка по номеру в формате E.164 надёжна: он есть
             * у каждой записи и от кэша журнала не зависит.
             */
            val knownContactId = remember(state.contacts, group.e164) {
                // sortedBy: при нескольких контактах на номер выбираем
                // первого по алфавиту — тот же порядок, что у имени
                // и фотографии в журнале. Иначе экраны показывали бы
                // разные контакты для одной записи
                state.contacts
                    .filter { contact -> contact.numbers.any { it.e164 == group.e164 } }
                    .minByOrNull { it.displayName.lowercase() }
                    ?.id ?: group.contactId
            }

            if (knownContactId == null) {
                SheetAction(Icons.Default.PersonAdd, R.string.action_add_contact) {
                    vm.createContact(group.number, group.displayName); onDismiss()
                }
            } else {
                SheetAction(Icons.Default.Edit, R.string.action_edit_contact) {
                    vm.editContact(knownContactId); onDismiss()
                }
            }

            // Заметка выше копирования: пометить, кто звонил, хотят чаще,
            // чем скопировать номер
            /*
             * Закрепление — свойство журнала, а не контакта.
             *
             * Избранное живёт в телефонной книге и синхронизируется
             * с Google; закрепить же хочется курьера или мастера,
             * которых в книгу не заносят вовсе.
             */
            val pinned = vm.isPinned(group.e164)
            SheetAction(
                if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                if (pinned) R.string.action_unpin else R.string.action_pin
            ) {
                if (pinned) {
                    vm.unpinNumber(group.e164)
                    onDismiss()
                } else if (vm.pinNumber(group.e164)) {
                    onDismiss()
                } else {
                    // Предел достигнут: сообщаем причину, а не закрываем
                    // шторку молча — иначе выглядит как сбой
                    pinLimitReached = true
                }
            }

            SheetAction(Icons.Default.EditNote, R.string.action_add_note) {
                vm.openNoteEditor(group); onDismiss()
            }

            SheetAction(Icons.Default.ContentCopy, R.string.action_copy_number) {
                // Напрямую, а не через handleSwipe: путь короче
                // и не зависит от разбора действий свайпа
                vm.copyNumber(group.number); onDismiss()
            }
            /*
             * «Напомнить перезвонить» — через то же окно выбора, что в карточке.
             *
             * Раньше этот пункт молча ставил напоминание через час. В карточке
             * та же кнопка открывала выбор («через час», «вечером», «завтра»,
             * своё время), и человек, привыкший к карточке, получал
             * напоминание не на то время — и узнавал об этом, когда оно
             * срабатывало.
             *
             * Окно выбора на экране журнала уже есть: оно отвечает
             * на reminderRequest, как и в карточке. Достаточно попросить.
             */
            SheetAction(Icons.Default.Notifications, R.string.action_remind) {
                vm.requestReminder(group); onDismiss()
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            /*
             * БЛОКИРОВКА, РАЗБЛОКИРОВКА И МАСКА — ОДИН ПУНКТ.
             *
             * ══════════════════════════════════════════════════════════════
             * ЧТО БЫЛО НЕ ТАК
             * ══════════════════════════════════════════════════════════════
             * Пункт всегда назывался «Заблокировать номер» и всегда
             * добавлял правило. Снять блокировку отсюда было нельзя:
             * приходилось открывать карточку или искать номер в списке
             * правил. При том что заблокировали его тем же длинным
             * нажатием секунду назад.
             *
             * Теперь пункт показывает состояние: у заблокированного
             * номера подпись «Разблокировать», и нажатие снимает
             * правило.
             *
             * ── Третье состояние: заблокирован маской ──────────────────────
             * Номер мог попасть под маску вида 8800*, не имея своего
             * правила. Снять её отсюда нельзя: вместе с ней разблокируется
             * весь диапазон, а человек этого не просил.
             *
             * Раньше пункт в этом случае просто предлагал «заблокировать»
             * уже заблокированный номер — нажатие добавляло точное
             * правило и внешне не меняло ничего. Теперь показывается
             * маска и предлагается открыть чёрный список: решение
             * там, где его видно целиком.
             */
            /*
     * Отклик на необратимое: блокировка, снятие блокировки, удаление
     * записи. Разбор — в ActionHaptics.
     */
    val haptics = rememberActionHaptics()

    val exactRule = rules.firstOrNull { it.isBlock && it.pattern == group.e164 }
            val maskRule = rules.firstOrNull {
                it.isBlock && it.isMask && RulePattern.matches(it.pattern, group.e164)
            }

            when {
                exactRule != null -> SheetAction(
                    Icons.Default.Block,
                    R.string.action_unblock,
                    accent = UnblockGreen
                ) {
                    haptics(); vm.removeRule(group.e164); onDismiss()
                }

                maskRule != null -> SheetAction(
                    Icons.Default.Block,
                    R.string.action_blocked_by_mask,
                    destructive = true
                ) {
                    maskWarning = maskRule.pattern
                }

                else -> SheetAction(
                    Icons.Default.Block,
                    R.string.action_block,
                    destructive = true
                ) {
                    haptics(); vm.blockNumber(group.e164); onDismiss()
                }
            }
            /*
             * «СПАМ» — РЯДОМ С БЛОКИРОВКОЙ.
             *
             * С 1.1.1 отметка «спам» живёт отдельно от заметки, а поставить
             * её можно было только в карточке. Здесь, в меню записи, где
             * человек и так решает «что делать с этим номером», её не было.
             *
             * Пункт переключает отметку: нет отметки — ставит «спам»,
             * есть любая (спам или мошенники) — снимает. «Мошенники» —
             * тоньше, их ставят в карточке, где видны обе фишки.
             */
            val mark = state.spamMarks[group.e164]
            if (mark == null) {
                SheetAction(
                    Icons.Default.Report,
                    R.string.action_mark_spam,
                    destructive = true
                ) {
                    haptics(); vm.setSpamMark(group.e164, SpamMarkType.SPAM); onDismiss()
                }
            } else {
                SheetAction(
                    Icons.Default.Report,
                    R.string.action_unmark_spam,
                    accent = UnblockGreen
                ) {
                    haptics(); vm.setSpamMark(group.e164, null); onDismiss()
                }
            }

            SheetAction(
                Icons.Default.Delete,
                R.string.action_delete_from_log,
                destructive = true
            ) {
                haptics(); vm.deleteGroups(listOf(group)); onDismiss()
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    labelRes: Int,
    destructive: Boolean = false,
    /**
     * Свой цвет пункта.
     *
     * Нужен разблокировке: она не разрушительна, но и не обычна —
     * зелёный говорит, что нажатие СНИМЕТ блокировку. Разбор цветов —
     * в Color.kt рядом с BlockRed.
     */
    accent: Color? = null,
    onClick: () -> Unit
) {
    val color = accent
        ?: if (destructive) BlockRed
        else MaterialTheme.colorScheme.onSurface

    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        headlineContent = { Text(stringResource(labelRes), color = color) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .clickable(onClick = onClick)
    )
}
