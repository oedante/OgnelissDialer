package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.components.SectionCaption
import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.components.GroupedRow
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ReadableWidth
import com.ogneliss.dialer.data.RuleKind
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.data.RulePattern
import com.ogneliss.dialer.ui.DialerViewModel
import androidx.compose.foundation.combinedClickable
import com.ogneliss.dialer.ui.components.SelectionTopBar
import com.ogneliss.dialer.ui.components.rememberSelectionState
import com.ogneliss.dialer.ui.theme.Dimens

/**
 * ЭКРАН СПИСКОВ БЛОКИРОВКИ И РАЗРЕШЕНИЙ.
 *
 * Назначение: показать и отредактировать правила фильтрации, которые
 * до этого существовали только в базе.
 *
 * ── Почему белый и чёрный списки на одном экране ───────────────────────────
 * Они связаны логикой: белый список отменяет чёрный. Разнести их
 * по разным экранам значило бы скрыть эту связь. Пользователь, добавивший
 * «блокировать всех незнакомых», должен видеть рядом, кого он уже разрешил.
 *
 * Белый список идёт ПЕРВЫМ и подписан как приоритетный — именно этого
 * не понимают в отзывах на конкурентов («блокировка режет нужные звонки»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockListScreen(
    vm: DialerViewModel,
    onBack: () -> Unit
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<RuleKind?>(null) }

    // Выбор нескольких правил: чистить список по одному никто не станет,
    // и он превращается в свалку из старых блокировок
    val selection = rememberSelectionState<String>()

    val allowed = rules.filter { it.kind == RuleKind.ALLOW.key }
    val blocked = rules.filter { it.kind == RuleKind.BLOCK.key }

    Scaffold(
        // Отступы под системные панели: в edge-to-edge содержимое
        // иначе уезжает под строку состояния и кнопки навигации
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (selection.isActive) {
                SelectionTopBar(
                    count = selection.count,
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(rules.map { it.pattern }) },
                    onDelete = {
                        vm.removeRules(selection.selected)
                        selection.clear()
                    }
                )
                return@Scaffold
            }
            TopAppBar(
                title = { Text(stringResource(R.string.settings_section_spam)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            OgnelissIcons.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { pad ->
        // Не шире 640 dp по центру на широком окне: разбор в ReadableWidth
        ReadableWidth {
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {

            // ── Белый список: первым и с объяснением приоритета ────────────
            item {
                SectionHeaderWithAction(
                    title = stringResource(R.string.settings_allow_list),
                    onAdd = { adding = RuleKind.ALLOW }
                )
                InfoNote(stringResource(R.string.allow_list_priority_note))
            }

            if (allowed.isEmpty()) {
                item { EmptyNote(stringResource(R.string.allow_list_empty)) }
            }
            // Белый список идёт первым и с пояснением приоритета: именно
            // его непонимание порождает жалобы «блокировка режет нужные звонки»
            itemsIndexed(allowed, key = { _, rule -> "allow_${rule.pattern}" }) { i, rule ->
                // Правила — белой группой с рамкой, линии между строками
                // внутри группы (строгий стиль, 1.5.1)
                GroupedRow(GroupPosition.of(i, allowed.size)) {
                RuleRow(
                    pattern = rule.pattern,
                    comment = rule.comment,
                    isAllow = true,
                    selected = selection.isSelected(rule.pattern),
                    onLongClick = { selection.start(rule.pattern) },
                    onClick = { if (selection.isActive) selection.toggle(rule.pattern) },
                    onDelete = { vm.removeRule(rule.pattern) }
                )
                }
            }

            // ── Чёрный список ──────────────────────────────────────────────
            item {
                SectionHeaderWithAction(
                    title = stringResource(R.string.settings_block_list),
                    onAdd = { adding = RuleKind.BLOCK }
                )
                // Счётчик со склонением: «1 номер», «2 номера», «5 номеров»
                if (blocked.isNotEmpty()) {
                    Text(
                        pluralStringResource(
                            R.plurals.blocked_count, blocked.size, blocked.size
                        ),
                        Modifier.padding(horizontal = FlowUi.contentPadding),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (blocked.isEmpty()) {
                item { EmptyNote(stringResource(R.string.block_list_empty)) }
            }
            itemsIndexed(blocked, key = { _, rule -> "block_${rule.pattern}" }) { i, rule ->
                GroupedRow(GroupPosition.of(i, blocked.size)) {
                RuleRow(
                    pattern = rule.pattern,
                    comment = rule.comment,
                    isAllow = false,
                    selected = selection.isSelected(rule.pattern),
                    onLongClick = { selection.start(rule.pattern) },
                    onClick = { if (selection.isActive) selection.toggle(rule.pattern) },
                    onDelete = { vm.removeRule(rule.pattern) }
                )
                }
            }

            // ── Системный список ───────────────────────────────────────────
            item {
                Spacer(Modifier.height(Space.l))
                GroupedRow(GroupPosition.SINGLE) {
                    OgnelissListRow(
                        headline = stringResource(R.string.settings_blocked_system),
                        supporting = stringResource(R.string.settings_blocked_system_text),
                        leading = { Icon(OgnelissIcons.Block, null) },
                        onClick = { vm.openSystemBlockList() }
                    )
                }
                Spacer(Modifier.height(Space.xxl))
            }
        }
        }
    }

    adding?.let { kind ->
        AddRuleDialog(
            kind = kind,
            onAdd = { pattern, comment -> vm.addRule(pattern, kind, comment) },
            onDismiss = { adding = null }
        )
    }
}

@Composable
private fun SectionHeaderWithAction(title: String, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = FlowUi.screenPadding + Space.m, end = Space.s, top = 20.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Подпись раздела — как во всём приложении (строгий стиль, 1.5.1)
        SectionCaption(title, Modifier.weight(1f))
        IconButton(onClick = onAdd) {
            Icon(OgnelissIcons.Add, contentDescription = stringResource(R.string.rule_add))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RuleRow(
    pattern: String,
    comment: String?,
    isAllow: Boolean,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        leadingContent = {
            Icon(
                if (isAllow) OgnelissIcons.CheckCircle else OgnelissIcons.Block,
                contentDescription = null,
                /*
                 * Зелёный — правило пропускает звонок, красный —
                 * отрезает. Те же цвета, что у кнопок блокировки
                 * в карточке и в меню: человек читает смысл по цвету,
                 * не вчитываясь в подпись. Разбор — в Color.kt.
                 */
                tint = if (isAllow) MaterialTheme.colorScheme.primary else BlockRed
            )
        },
        headlineContent = { Text(RulePattern.describe(pattern)) },
        supportingContent = comment?.let { { Text(it) } },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(OgnelissIcons.Delete, contentDescription = stringResource(R.string.rule_remove))
            }
        },
        modifier = Modifier
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            // Долгое нажатие включает выбор: здесь этот жест свободен,
            // в отличие от журнала, где он открывает карточку
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}

@Composable
private fun InfoNote(text: String) {
    Row(
        Modifier.padding(horizontal = Space.l, vertical = Space.xs),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        Icon(
            OgnelissIcons.Info, contentDescription = null,
            Modifier.size(FlowUi.iconSizeInline), tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(Space.s))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = Space.l, vertical = Space.m),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
