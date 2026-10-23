package com.ogneliss.dialer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.LocalAppearance
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.Space

/**
 * ПЕРЕКЛЮЧАТЕЛЬ «ЖУРНАЛ | КОНТАКТЫ» (1.6.0, обзор интерфейса, П1).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО БЫЛО НЕ ТАК
 * ══════════════════════════════════════════════════════════════════════════
 * Журнал и контакты — два равноправных списка, а устроены были как
 * родитель и ребёнок: контакты открывались поверх журнала со стрелкой
 * «назад», своим заголовком и своей нижней строкой. Туда — кнопкой
 * внизу, обратно — стрелкой вверху слева.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК ТЕПЕРЬ (выбор автора 23.10: вариант «Б» с уточнением)
 * ══════════════════════════════════════════════════════════════════════════
 * У обоих списков одна панель, и первая кнопка на ней — переключатель:
 *
 * • ТЕКУЩИЙ список — крупно: «таблетка» с подложкой, как у включённой
 *   кнопки, и подпись под ней;
 * • СОСЕДНИЙ — маленьким кружком рядом: туда переключает касание.
 *
 * Кнопка не шире обычной больше чем на пару точек, поэтому клавиатура
 * в центре панели остаётся в центре: группы слева и справа от неё одной
 * ширины (разбор в ActionRow).
 *
 * Долгое нажатие раскрывает над кнопкой список разделов — значки
 * с названиями: журнал, контакты, заметки, напоминания. Текущий отмечен
 * подложкой. Касание мимо и «Назад» закрывают список.
 *
 * ── Для TalkBack ───────────────────────────────────────────────────────────
 * Кнопка называет текущий список («Журнал»), действия — словами:
 * «Перейти: Контакты» и «Все разделы» для долгого нажатия.
 *
 * @param compact узкая полоса справа (телефон лёжа): маленький кружок —
 *   на углу «таблетки», а не сбоку; список разделов — слева от кнопки
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListSwitchButton(
    current: MainSection,
    onSwitch: () -> Unit,
    onOpenSection: (MainSection) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showLabel: Boolean = !LocalWindowLayout.current.isShort && LocalAppearance.current.buttonLabels
) {
    val target = current.sibling
    val label = stringResource(current.labelRes)
    val switchLabel = stringResource(R.string.a11y_switch_to, stringResource(target.labelRes))
    val sectionsLabel = stringResource(R.string.a11y_all_sections)
    var sectionsOpen by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClickLabel = switchLabel,
                onLongClickLabel = sectionsLabel,
                onLongClick = { sectionsOpen = true },
                onClick = onSwitch
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                selected = true
            }
            .padding(horizontal = Space.xxs, vertical = Space.xxs)
    ) {
        Box(
            if (compact) {
                Modifier.size(width = PILL_WIDTH + CORNER_OVERHANG, height = PILL_HEIGHT + CORNER_OVERHANG)
            } else {
                Modifier.size(width = PILL_WIDTH + TARGET_SIZE - TARGET_OVERLAP, height = PILL_HEIGHT)
            }
        ) {
            // Текущий список — «таблетка» включённой кнопки
            Box(
                Modifier
                    .align(if (compact) Alignment.BottomStart else Alignment.CenterStart)
                    .size(width = PILL_WIDTH, height = PILL_HEIGHT)
                    .clip(CircleShape)
                    .background(OgnelissTheme.surfaces.navIndicator),
                contentAlignment = Alignment.Center
            ) {
                Icon(current.icon, contentDescription = null)
            }
            // Соседний — маленький кружок: туда переключает касание
            Box(
                Modifier
                    .align(if (compact) Alignment.TopEnd else Alignment.CenterEnd)
                    .size(TARGET_SIZE)
                    .clip(CircleShape)
                    .background(PanelColors.key())
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    target.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TARGET_ICON)
                )
            }
        }

        if (showLabel) {
            Spacer(Modifier.height(Space.xs))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (sectionsOpen) {
            SectionsPopup(
                current = current,
                besideAnchor = compact,
                onPick = { section ->
                    sectionsOpen = false
                    if (section != current) onOpenSection(section)
                },
                onDismiss = { sectionsOpen = false }
            )
        }
    }
}

/**
 * Список разделов по долгому нажатию: значки с названиями, ближний
 * к пальцу — журнал. Раскрывается снизу вверх, как из самой кнопки.
 */
@Composable
private fun SectionsPopup(
    current: MainSection,
    besideAnchor: Boolean,
    onPick: (MainSection) -> Unit,
    onDismiss: () -> Unit
) {
    val gapPx = with(LocalDensity.current) { Space.s.roundToPx() }
    val provider = remember(gapPx, besideAnchor) { SectionsPositionProvider(gapPx, besideAnchor) }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        // Фокус — у списка: «Назад» и касание мимо закрывают его
        properties = PopupProperties(focusable = true)
    ) {
        val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(Motion.QUICK_MS)) +
                expandVertically(tween(Motion.QUICK_MS), expandFrom = Alignment.Bottom)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.s),
                // Поле — под тени кнопок: без него их обрезал бы край окна
                modifier = Modifier.padding(Space.xs)
            ) {
                MainSection.entries.reversed().forEach { section ->
                    SectionChip(section, selected = section == current) { onPick(section) }
                }
            }
        }
    }
}

@Composable
private fun SectionChip(section: MainSection, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) OgnelissTheme.surfaces.navIndicator else PanelColors.key(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = CHIP_SHADOW,
        modifier = Modifier.semantics { this.selected = selected }
    ) {
        Row(
            Modifier
                .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                .padding(start = Space.m, end = Space.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(section.icon, contentDescription = null)
            Spacer(Modifier.width(Space.m))
            Text(
                stringResource(section.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

/**
 * Где встаёт список разделов: над кнопкой, от её края внутрь экрана;
 * у полосы справа (телефон лёжа) — сбоку от кнопки, вровень с её верхом.
 * За края окна не выходит.
 */
private class SectionsPositionProvider(
    private val gapPx: Int,
    private val besideAnchor: Boolean
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val ltr = layoutDirection == LayoutDirection.Ltr
        if (besideAnchor) {
            val x = if (ltr) anchorBounds.left - gapPx - popupContentSize.width else anchorBounds.right + gapPx
            return IntOffset(x.coerceIn(0, maxX), anchorBounds.top.coerceIn(0, maxY))
        }
        val x = if (ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
        val y = anchorBounds.top - gapPx - popupContentSize.height
        return IntOffset(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }
}

/** Кружок соседнего списка: заметно меньше «таблетки» (40 dp). */
private val TARGET_SIZE = 22.dp
private val TARGET_ICON = 14.dp
/** Насколько кружок заходит на «таблетку» сбоку — кнопка от этого уже. */
private val TARGET_OVERLAP = 8.dp
/** Насколько кружок выступает за угол «таблетки» в узкой полосе. */
private val CORNER_OVERHANG = 8.dp
private val CHIP_SHADOW = 3.dp
