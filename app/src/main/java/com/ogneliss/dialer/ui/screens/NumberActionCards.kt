package com.ogneliss.dialer.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    // Перехода в чёрный список нет с 2.1.0: номер под маской разблокируется сразу
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
    // В белом списке — маска на номер не действует (белый список сильнее)
    val allowed = rules.any { it.isAllow && RulePattern.matches(it.pattern, e164) }
    val maskRule = if (allowed) null else rules.firstOrNull {
        it.isBlock && it.isMask && RulePattern.matches(it.pattern, e164)
    }

    // Долгое нажатие на «Позвонить» и «SMS» — выбор SIM (1.8.0); одна карта — нет
    val multiSim = state.sims.size > 1
    val quick = listOf(
        CardAction(
            OgnelissIcons.Call, stringResource(R.string.action_call),
            onClick = { vm.call(number) },
            tint = Color.White, container = com.ogneliss.dialer.util.LocalCallAvailability.current.callTint(com.ogneliss.dialer.ui.theme.CallGreen),
            onLongClick = if (multiSim) { { vm.chooseSim(number, DialerViewModel.SimAction.CALL) } } else null,
            longClickLabel = stringResource(R.string.a11y_choose_sim)
        ),
        CardAction(
            OgnelissIcons.Message, stringResource(R.string.action_sms),
            onClick = { vm.openSms(number) },
            onLongClick = if (multiSim) { { vm.chooseSim(number, DialerViewModel.SimAction.SMS) } } else null,
            longClickLabel = stringResource(R.string.a11y_choose_sim_sms)
        ),
        CardAction(
            OgnelissIcons.ContentCopy, stringResource(R.string.action_copy),
            // Отклик — вибрацией, без сообщения (2.1.0, разбор у copyNumber)
            onClick = { haptics(); vm.copyNumber(number) }
        ),
        CardAction(
            OgnelissIcons.Share, stringResource(R.string.action_share),
            onClick = { ExternalApps.share(context, number) }
        )
    )

    /*
     * Блок — с тремя состояниями, как в карточках журнала: снять точное
     * правило; номер под маской — в белый список (маску отсюда не снять —
     * разблокировался бы весь диапазон); заблокировать. 2.1.0: было —
     * окно с объяснением маски; стало — «Разблокировать» сразу,
     * с «Отменить» (vm.allowNumber), и «Блок» — тоже с «Отменить».
     */
    val unblock = exactRule != null || maskRule != null
    val block = CardAction(
        OgnelissIcons.Block,
        stringResource(if (unblock) R.string.action_unblock_short else R.string.action_block_short),
        onClick = {
            haptics()
            when {
                exactRule != null -> vm.removeRule(e164)
                maskRule != null -> vm.allowNumber(e164)
                else -> vm.blockNumber(e164)
            }
        },
        tint = if (unblock) scheme.primary else BlockRed
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
