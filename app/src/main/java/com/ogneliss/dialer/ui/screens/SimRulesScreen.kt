package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.settings.SimRule
import com.ogneliss.dialer.settings.SimRules
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.components.GroupedRow
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.components.OgnelissTextField
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.components.SheetChoiceRow
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.SimColors
import com.ogneliss.dialer.ui.theme.Space

/**
 * «КАРТА ПО НОМЕРУ» — СПИСОК ПРАВИЛ (2.9.11, Настройки → SIM-карты).
 *
 * Автор, 26.11.2026: «пользователи просят возможность звонков с разных сим-карт
 * по маске номера»; следом — «не только маску номера, но и регион из нашей базы…
 * „Краснодар*“… или „Билайн“, „МТС“».
 *
 * ── Вид ────────────────────────────────────────────────────────────────────
 * Как остальные подэкраны настроек (ExternalLookupScreen): серо-голубая страница,
 * правила — белой группой строк. Строка — правило как его написал человек
 * («+7 495», «Краснодар*», «МТС») и карта: «SIM 1 · Билайн», значок в цвете карты,
 * тот же, что у пометки карты в журнале. Касание — правка, корзина — удалить
 * без вопроса: правило — одна строка, набрать заново быстрее, чем подтверждать
 * (и «Отменить» для такой мелочи — лишний шум, правило автора 07.11.2026).
 * Внизу — «Добавить правило»; больше SimRules.MAX_RULES — кнопки нет.
 *
 * ── Чего на экране нет ─────────────────────────────────────────────────────
 * Пояснений (правило автора 07.11.2026): что значит «*» — подсказка под полем,
 * что главнее и оговорка про перенос номера к другому оператору — «Вопросы
 * и ответы» (faq_a_sim). Порядок правил не меняется: при равной точности главнее
 * то, что добавлено раньше (SimRules.match).
 *
 * Разбор правил — SimRules, где действуют — SimRouter.
 */
@Composable
fun SimRulesScreen(vm: DialerViewModel, onBack: () -> Unit) {
    val rules by vm.simRules.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    /** Правка: индекс правила, −1 — новое; null — окна нет. */
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }

    ScreenScaffold(
        // Как остальные экраны настроек: серо-голубая страница, группы — карточками
        tone = com.ogneliss.dialer.ui.theme.PageTone.TINTED,
        title = stringResource(R.string.settings_sim_rules),
        onBack = onBack,
        pinnedTopBar = true
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            if (rules.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.sim_rules_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(FlowUi.contentPadding)
                    )
                }
            }
            itemsIndexed(rules, key = { index, rule -> "$index|${rule.mask}" }) { index, rule ->
                GroupedRow(GroupPosition.of(index, rules.size), dividerStart = Space.l) {
                    OgnelissListRow(
                        headline = rule.mask,
                        supporting = simTitle(rule.slot, state.sims),
                        leading = {
                            Icon(OgnelissIcons.SimCard, contentDescription = null, tint = SimColors.of(rule.slot))
                        },
                        trailing = {
                            IconButton(onClick = { vm.setSimRules(rules.filterIndexed { i, _ -> i != index }) }) {
                                Icon(
                                    OgnelissIcons.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { editing = index }
                    )
                }
            }
            if (rules.size < SimRules.MAX_RULES) {
                item(key = "add") {
                    FilledTonalButton(
                        onClick = { editing = -1 },
                        modifier = Modifier.fillMaxWidth().padding(FlowUi.contentPadding)
                    ) {
                        Icon(OgnelissIcons.Add, contentDescription = null)
                        Spacer(Modifier.width(Space.s))
                        Text(stringResource(R.string.sim_rules_add))
                    }
                }
            }
        }
    }

    editing?.let { index ->
        val current = rules.getOrNull(index)
        SimRuleDialog(
            initial = current,
            sims = state.sims,
            onSave = { rule ->
                vm.setSimRules(
                    if (current == null) rules + rule
                    else rules.mapIndexed { i, r -> if (i == index) rule else r }
                )
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/** «SIM 1 · Билайн»: номер слота и имя карты, если она вставлена. */
@Composable
private fun simTitle(slot: Int, sims: List<SimSlot>): String {
    val name = sims.firstOrNull { it.slotIndex == slot }?.label?.takeIf { it.isNotBlank() }
    val base = "SIM ${slot + 1}"
    return if (name != null && !name.equals(base, ignoreCase = true)) "$base · $name" else base
}

/**
 * ОКНО ПРАВИЛА: строка правила и карта. «Сохранить» — когда строка годится
 * (SimRules.isValid): начало номера или название региона, оператора.
 *
 * ── Клавиатура ─────────────────────────────────────────────────────────────
 * Номер удобнее набирать цифровой клавиатурой, регион и оператора — буквенной.
 * Вид правила определяется по самой строке, а клавиатуру переключает кнопка в поле
 * «123 / АБВ» — как в поле «Кому» нового SMS (NewMessageBar). Правка правила
 * с названием открывается сразу с буквами.
 *
 * ── Карта ──────────────────────────────────────────────────────────────────
 * Обе карты — всегда, даже если вставлена одна: правило переживает время, когда
 * карта вынута. Строки выбора — общие строки шторок (SheetChoiceRow): кружок
 * выбора в приложении один (scripts/ui_roles_check.py, правило 9); перед
 * подписью — значок карты в её цвете, как в журнале.
 */
@Composable
private fun SimRuleDialog(
    initial: SimRule?,
    sims: List<SimSlot>,
    onSave: (SimRule) -> Unit,
    onDismiss: () -> Unit
) {
    var mask by rememberSaveable { mutableStateOf(initial?.mask.orEmpty()) }
    var slot by rememberSaveable { mutableIntStateOf(initial?.slot ?: 0) }
    // Буквы — у правила с названием; новое начинается с цифр (чаще пишут код)
    var letters by rememberSaveable { mutableStateOf(initial?.let { SimRules.isName(it.mask) } ?: false) }
    val valid = remember(mask) { SimRules.isValid(mask) }

    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (initial == null) R.string.sim_rule_new else R.string.sim_rule_edit),
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(SimRule(mask.trim(), slot)) }) {
                Text(stringResource(R.string.nav_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_cancel)) }
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                OgnelissTextField(
                    value = mask,
                    onValueChange = { if (it.length <= SimRules.MAX_MASK_LENGTH) mask = it },
                    label = stringResource(R.string.sim_rule_mask),
                    placeholder = stringResource(R.string.sim_rule_mask_hint),
                    // «*» — любая цифра в номере, любые буквы в названии; подробнее — FAQ
                    supportingText = stringResource(R.string.sim_rule_mask_help),
                    trailingIcon = {
                        // Подписью, а не значком: «123» и «АБВ» понятны без догадок
                        val toLabel = stringResource(
                            if (letters) R.string.messages_new_digits else R.string.messages_new_letters
                        )
                        TextButton(
                            onClick = { letters = !letters },
                            modifier = Modifier.semantics { contentDescription = toLabel }
                        ) {
                            Text(
                                if (letters) DIGITS_LABEL else stringResource(R.string.messages_new_letters_short),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (letters) KeyboardType.Text else KeyboardType.Phone,
                        capitalization = if (letters) KeyboardCapitalization.Words else KeyboardCapitalization.None
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    stringResource(R.string.sim_rule_call_from),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(0, 1).forEach { s ->
                    SheetChoiceRow(
                        selected = slot == s,
                        label = simTitle(s, sims),
                        onClick = { slot = s },
                        swatch = { Icon(OgnelissIcons.SimCard, contentDescription = null, tint = SimColors.of(s)) }
                    )
                }
            }
        }
    )
}

/** Подпись переключателя на цифровую клавиатуру — цифры, перевода не требует. */
private const val DIGITS_LABEL = "123"
