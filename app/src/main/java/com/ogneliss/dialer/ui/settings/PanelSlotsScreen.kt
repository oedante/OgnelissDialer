package com.ogneliss.dialer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.PanelSlot
import com.ogneliss.dialer.ui.components.ChoiceSheet
import com.ogneliss.dialer.ui.components.FitText
import com.ogneliss.dialer.ui.components.ListSheetGroup
import com.ogneliss.dialer.ui.components.OgnelissCard
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.components.SettingsActionRow
import com.ogneliss.dialer.ui.components.SheetChoiceRow
import com.ogneliss.dialer.ui.components.SheetDivider
import com.ogneliss.dialer.ui.components.toSection
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.PageTone
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.Space

/**
 * КОНСТРУКТОР НИЖНЕЙ ПАНЕЛИ (2.9.2) — как «Нижняя панель» в настройках
 * Ogneliss Mail (phone_bar_section.dart).
 *
 * Сверху — пояснение, под ним — макет панели: четыре места и кнопка в центре
 * (её не выбирают: она всегда посередине — клавиатура, «Написать», «Создать»).
 * Касание места — шторка «Раздел на этом месте»: раздел, который уже на панели,
 * меняется местами («Поменяются местами»); «Журнал» с панели не убрать — его
 * место можно только отдать другому разделу панели. Под макетом — «Как по
 * умолчанию», если порядок меняли, «Инструменты меню» (2.9.3: что показывать
 * в «Меню», MenuTool) и где стоят разделы — внизу или справа (2.9.3, проба:
 * SideSectionRail).
 *
 * Сохраняет вызывающий (RootScreen): сразу, без кнопки «Готово», как
 * остальные настройки. Правила мест — settings/PanelSlots.kt (PanelSlotsTest).
 */
@Composable
internal fun PanelSlotsScreen(
    slots: List<PanelSlot>,
    /** Порядок меняли (есть сохранённый) — показать «Как по умолчанию». */
    customized: Boolean,
    onPlace: (index: Int, item: PanelSlot) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    /** Разделы справа (2.9.3, проба) — колонка SideSectionRail вместо нижней панели. */
    side: Boolean = false,
    onSideChange: (Boolean) -> Unit = {},
    /** «Инструменты меню» (2.9.3) — порядок и показ строк «Меню»; null — пункта нет. */
    onOpenMenuTools: (() -> Unit)? = null,
    /** «Колонка справа» — порядок и показ (2.9.9, RailItem); видна, когда выбраны разделы справа. */
    onOpenRailItems: (() -> Unit)? = null
) {
    // Номер места, для которого открыта шторка выбора; null — шторки нет
    var picking by remember { mutableStateOf<Int?>(null) }

    ScreenScaffold(
        title = stringResource(R.string.settings_panel_slots),
        onBack = onBack,
        pinnedTopBar = true,
        tone = PageTone.TINTED
    ) { pad ->
        // Экран листается: при шрифте 160 % пояснение и макет выше экрана телефона лёжа
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.l)
        ) {
            // Правила мест — словами над макетом, как пояснение раздела в почте
            Text(
                stringResource(R.string.panel_slots_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = FlowUi.screenPadding, vertical = Space.m)
            )
            OgnelissCard {
                // Макет — цветом настоящей панели: видно, что это она
                Surface(
                    color = PanelColors.panel(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(Space.s)
                ) {
                    // Пять равных долей, как на настоящей панели (ActionRow): места 0–1, центр, места 2–3
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.xs, horizontal = Space.xxs),
                        verticalAlignment = Alignment.Top
                    ) {
                        SlotCell(0, slots, onPick = { picking = 0 })
                        SlotCell(1, slots, onPick = { picking = 1 })
                        CenterCell()
                        SlotCell(2, slots, onPick = { picking = 2 })
                        SlotCell(3, slots, onPick = { picking = 3 })
                    }
                }
                Text(
                    stringResource(R.string.panel_slots_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, bottom = Space.m)
                )
            }
            /*
             * ГДЕ РАЗДЕЛЫ (2.9.3, проба по макету автора): внизу — панель, как
             * всегда; справа — колонка у края (SideSectionRail) с теми же местами
             * сверху вниз. Только телефон в портрете и раскладушка: у планшета
             * своя полоса слева, лёжа — своя колонка кнопок.
             */
            // Подпись группы — как подписи групп настроек (SectionCaption)
            com.ogneliss.dialer.ui.components.SectionCaption(
                stringResource(R.string.panel_side_title),
                Modifier.padding(start = FlowUi.groupInset + Space.l, top = Space.l, bottom = Space.xs)
            )
            OgnelissCard {
                SheetChoiceRow(
                    selected = !side,
                    label = stringResource(R.string.panel_side_bottom),
                    onClick = { onSideChange(false) }
                )
                SheetDivider()
                SheetChoiceRow(
                    selected = side,
                    label = stringResource(R.string.panel_side_right),
                    supporting = stringResource(R.string.panel_side_right_text),
                    onClick = { onSideChange(true) }
                )
            }
            // 2.9.9: колонка справа — свой порядок и показ (первый — у кнопки действия)
            if (side && onOpenRailItems != null) {
                Spacer(Modifier.height(Space.m))
                OgnelissCard {
                    SettingsActionRow(
                        title = stringResource(R.string.rail_items_title),
                        summary = stringResource(R.string.rail_items_summary),
                        icon = OgnelissIcons.Tune,
                        onClick = onOpenRailItems
                    )
                }
            }
            // Что показывать в «Меню» — тот же экран, что у значка «настроить» в самом меню
            if (onOpenMenuTools != null) {
                Spacer(Modifier.height(Space.m))
                OgnelissCard {
                    SettingsActionRow(
                        title = stringResource(R.string.menu_tools_title),
                        summary = stringResource(R.string.menu_tools_summary),
                        icon = OgnelissIcons.Tune,
                        onClick = onOpenMenuTools
                    )
                }
            }
            // «Как по умолчанию» — только когда есть что возвращать (как «barReset» почты)
            if (customized) {
                Spacer(Modifier.height(Space.m))
                OgnelissCard {
                    SettingsActionRow(
                        title = stringResource(R.string.panel_slots_reset),
                        summary = null,
                        icon = OgnelissIcons.RestartAlt,
                        onClick = onReset
                    )
                }
            }
        }
    }

    /*
     * ШТОРКА «РАЗДЕЛ НА ЭТОМ МЕСТЕ». Все шесть вариантов: четыре места — из шести
     * (журнал, SMS, контакты, заметки, напоминания, «Меню»). Выбранный на панели
     * раздел помечен «Поменяются местами» — PanelSlots.place меняет их, а не
     * дублирует. Чужое для «Журнала» место не предлагается: убрать его нельзя.
     */
    picking?.let { index ->
        val current = slots.getOrNull(index) ?: return@let
        val journalHere = current == PanelSlot.JOURNAL
        ChoiceSheet(title = stringResource(R.string.panel_slot_pick), onDismiss = { picking = null }) {
            if (journalHere) {
                Text(
                    stringResource(R.string.panel_slot_journal_stays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = FlowUi.screenPadding, vertical = Space.xs)
                )
            }
            // «Журнал» с панели не убрать: на его место — только то, что уже на панели
            val options = PanelSlot.entries.filter { !journalHere || it in slots }
            val swap = stringResource(R.string.panel_slot_swap)
            // Строки — как выбор значения настройки (SingleChoiceSheet): кружок, подпись, пояснение
            ListSheetGroup {
                options.forEachIndexed { i, item ->
                    SheetChoiceRow(
                        selected = item == current,
                        label = stringResource(item.slotLabelRes()),
                        supporting = if (item != current && item in slots) swap else null,
                        onClick = {
                            onPlace(index, item)
                            picking = null
                        }
                    )
                    if (i < options.lastIndex) SheetDivider()
                }
            }
        }
    }
}

/** Место макета: значок на подложке и подпись, как кнопка панели. */
@Composable
private fun RowScope.SlotCell(index: Int, slots: List<PanelSlot>, onPick: () -> Unit) {
    val item = slots.getOrNull(index) ?: return
    val label = stringResource(item.slotLabelRes())
    // TalkBack: «Место 2: Контакты» — номер места важнее, чем на глаз (макет без рамок)
    val cd = stringResource(R.string.panel_slot_cd, index + 1, label)
    Column(
        Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .clearAndSetSemantics {
                contentDescription = cd
                role = Role.Button
                onClick { onPick(); true }
            }
            .clickable(onClick = onPick)
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Подложка — признак «место можно поменять» (как в почте)
        Box(
            Modifier
                .size(width = 48.dp, height = 32.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.slotIcon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(Space.xxs))
        // Подпись ужимается, а не переносится: «Напоминания» при крупном шрифте (FitText)
        FitText(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

/** Центр макета — для наглядности: его не выбирают. */
@Composable
private fun RowScope.CenterCell() {
    Column(
        Modifier.weight(1f).padding(vertical = Space.xs).clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Сплошная, как центр настоящей панели (LabeledIconButton.filled)
        Box(
            Modifier
                .size(width = 48.dp, height = 32.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(OgnelissIcons.Dialpad, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.height(Space.xxs))
        FitText(
            stringResource(R.string.bar_center_dial),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

/** Подпись места — та же, что у кнопки на панели. */
internal fun PanelSlot.slotLabelRes(): Int =
    if (this == PanelSlot.MENU) R.string.bar_menu else toSection().labelRes

/** Значок места — тот же, что у кнопки на панели. */
internal fun PanelSlot.slotIcon(): ImageVector =
    if (this == PanelSlot.MENU) OgnelissIcons.Menu else toSection().icon
