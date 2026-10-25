package com.ogneliss.dialer.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.ExternalApps
import com.ogneliss.dialer.data.RulePattern
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.ActionCardsOverlay
import com.ogneliss.dialer.ui.components.CardAction
import com.ogneliss.dialer.ui.components.CardActionSection
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.rememberActionHaptics
import com.ogneliss.dialer.ui.components.rememberNumberInfoLabels
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.util.PhoneFormatter

/** Номер карточки контакта, по которому открыты карточки действий. */
@Immutable
internal data class NumberCardsTarget(
    /** Номер как в книге — на него звонят и пишут. */
    val number: String,
    /** Тип номера из книги: «Мобильный», «Рабочий». */
    val label: String?
)

/**
 * ДЕЙСТВИЯ С НОМЕРОМ ИЗ КАРТОЧКИ КОНТАКТА — ДОЛГИМ НАЖАТИЕМ (1.5.8, У1).
 *
 * Строка номера теперь сама звонит, а справа у неё осталась только SMS
 * (разбор у NumberRow). Остальное, что раньше было значком или пряталось
 * в журнале, — здесь, той же оболочкой, что в журнале и контактах
 * (ActionCardsOverlay): карточка номера, четыре быстрых действия и «Ещё».
 *
 * Действия — с ЭТИМ номером, а не с контактом целиком: у человека
 * с рабочим и личным номером заблокировать или отметить спамом можно
 * один, не трогая другой.
 */
@Composable
internal fun NumberActionCards(
    target: NumberCardsTarget,
    vm: DialerViewModel,
    /** Место нажатия — TouchAnchor.screenY. */
    anchorY: Float?,
    /** Переход в чёрный список: туда ведёт разбор блокировки по маске. */
    onOpenBlockLists: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val haptics = rememberActionHaptics()
    val infoLabels = rememberNumberInfoLabels()
    val scheme = MaterialTheme.colorScheme

    val number = target.number
    val e164 = remember(number) { PhoneFormatter.toE164(number) }
    val formatted = remember(number) { PhoneFormatter.format(number) }
    val mark = state.spamMarks[e164]
    val exactRule = rules.firstOrNull { it.isBlock && it.pattern == e164 }
    val maskRule = rules.firstOrNull {
        it.isBlock && it.isMask && RulePattern.matches(it.pattern, e164)
    }

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

    val quick = listOf(
        CardAction(
            OgnelissIcons.Call, stringResource(R.string.action_call),
            onClick = { vm.call(number) },
            tint = Color.White, container = CallGreen
        ),
        CardAction(
            OgnelissIcons.Message, stringResource(R.string.action_sms),
            onClick = { vm.openSms(number) }
        ),
        CardAction(
            OgnelissIcons.ContentCopy, stringResource(R.string.action_copy),
            onClick = { vm.copyNumber(number) }
        ),
        CardAction(
            OgnelissIcons.Share, stringResource(R.string.action_share),
            onClick = { ExternalApps.share(context, number) }
        )
    )

    // Блок — с тремя состояниями, как в карточках журнала: снять точное
    // правило; объяснить маску (снять отсюда нельзя — снялась бы вся
    // маска); заблокировать
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
                exactRule != null -> { haptics(); vm.removeRule(e164) }
                maskRule != null -> maskWarning = maskRule.pattern
                else -> { haptics(); vm.blockNumber(e164) }
            }
        },
        tint = if (exactRule != null) scheme.primary else BlockRed,
        keepOpen = exactRule == null && maskRule != null
    )
    // Отметка «спам»: ставит, если нет; снимает любую, если есть
    val spam = CardAction(
        OgnelissIcons.Report,
        stringResource(if (mark == null) R.string.cards_spam else R.string.cards_not_spam),
        onClick = {
            haptics()
            vm.setSpamMark(e164, if (mark == null) SpamMarkType.SPAM else null)
        },
        tint = if (mark == null) BlockRed else scheme.primary
    )

    ActionCardsOverlay(
        anchorY = anchorY,
        onDismiss = onDismiss,
        quick = quick,
        sections = listOf(
            CardActionSection(stringResource(R.string.cards_section_number), listOf(spam, block))
        )
    ) {
        Text(
            formatted,
            // Обычной толщины, как имя в журнале (1.7.0)
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        target.label?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        infoLabels.infoSummary(state.numberInfo[e164])?.let {
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
