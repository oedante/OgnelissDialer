package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.PanelReachesNavBar
import com.ogneliss.dialer.ui.theme.Space

/**
 * НИЖНЯЯ ПАНЕЛЬ КОНТАКТОВ — ТА ЖЕ, ЧТО У ЖУРНАЛА (1.6.0, вариант «Б»).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО БЫЛО
 * ══════════════════════════════════════════════════════════════════════════
 * У контактов была своя нижняя строка — поле «Имя» во всю ширину
 * и кнопка фильтров, — а переход и «Добавить» жили наверху. Два разных
 * каркаса для двух равноправных списков.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК ТЕПЕРЬ
 * ══════════════════════════════════════════════════════════════════════════
 * Та же строка кнопок, что в журнале (ActionRow), на тех же местах:
 * переключатель «журнал | контакты», фильтр, клавиатура, поиск, меню.
 * Клавиатура открывает набор номера в журнале — там он живёт. «Поиск»
 * превращает строку в поле «Имя» (PanelSearchRow).
 *
 * Высота — как у панели журнала: над строкой то же место, что у полоски
 * клавиатуры набора (тянуть здесь нечего, но нижний край не должен
 * прыгать при переключении).
 */
@Composable
internal fun ListPanel(
    section: MainSection,
    onOpenSection: (MainSection) -> Unit,
    /** Работает фильтр — метка на «Ещё»; сами фильтры и поиск — в меню (2.0.0). */
    filterActive: Boolean,
    /** Клавиатура набора — в журнале. */
    onDialpad: () -> Unit,
    searching: Boolean,
    searchQuery: String,
    searchHint: String,
    onSearchChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onMenu: () -> Unit,
    /** Перелистывание свайпом по панели — к соседним разделам (журнал · SMS · контакты · заметки). */
    onSwipeRight: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    /** Главное действие раздела в центре (2.1.3, PanelCenter); null — клавиатура набора. */
    center: PanelCenter? = null,
    /**
     * Поле поиска над кнопками — всегда на виду (2.6.0, контакты; разбор
     * у PanelSearchButton). Касание открывает ввод (onCloseSearch закрывает).
     * null — поля нет: поиск в «Меню», как в журнале и SMS.
     */
    onOpenSearch: (() -> Unit)? = null,
    /** Подпись постоянного поля поиска («Поиск по контактам»). */
    searchButtonLabel: String = searchHint,
    menuContent: @Composable () -> Unit
) {
    Surface(
        // Цвет, форма и край — как у панели журнала (BottomControlPanel);
        // 1.7.0: без тени, край — линия (panelTopLine)
        color = PanelColors.panel(),
        tonalElevation = 0.dp,
        shape = OgnelissShapeTokens.bottomPanel,
        modifier = Modifier.panelTopLine(com.ogneliss.dialer.ui.theme.OgnelissTheme.surfaces.divider).panelSwipeNavigation(
            enabled = !searching && (onSwipeRight != null || onSwipeLeft != null),
            onSwipeLeft = onSwipeLeft,
            onSwipeRight = onSwipeRight
        )
    ) {
        PanelReachesNavBar()
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
        ) {
            // С постоянным полем поиска место под полоску не нужно: панель
            // и так выше, а поле само отделяет кнопки от списка
            Spacer(Modifier.height(if (onOpenSearch != null && !searching) Space.s else FlowUi.bottomPanelHandleHeight))
            if (searching) {
                PanelSearchRow(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    hint = searchHint,
                    onClose = onCloseSearch
                )
                // «Меню» из «⋮» шапки (2.9.2) открывается и во время поиска: окно меню
                // живёт в ряду кнопок (ActionRow), а его на это время заменяет поле
                Box { menuContent() }
            } else {
                if (onOpenSearch != null) {
                    PanelSearchButton(
                        label = searchButtonLabel,
                        onClick = onOpenSearch,
                        modifier = Modifier.padding(horizontal = FlowUi.screenPadding)
                    )
                }
                ActionRow(
                    section = section,
                    expanded = false,
                    filterActive = filterActive,
                    onToggleExpanded = onDialpad,
                    onOpenSection = onOpenSection,
                    center = center,
                    onMenu = onMenu,
                    menuContent = menuContent
                )
            }
        }
    }
}

/**
 * ПОИСК ВНИЗУ «КОНТАКТОВ» — ВСЕГДА НА ВИДУ (2.6.0).
 *
 * Форум, oleg_p: «сделать на странице "Контакты" размещение поискового
 * поля постоянным и внизу экрана: функция поиска ключевая для списка
 * контактов и должна быть доступна всегда и с минимальным количеством
 * телодвижений». Было (2.5.0): пилюля над списком — уезжала вверх вместе
 * со списком, — и «Поиск» в «Меню», на два касания дальше.
 *
 * Теперь поле над кнопками нижней панели, у большого пальца, и не
 * прокручивается. Вид — как у поля ввода, в которое оно превращается
 * (PanelSearchRow): белая пилюля на панели, значок поиска, подсказка
 * цветом подсказки. Касание — то же поле с клавиатурой.
 */
@Composable
internal fun PanelSearchButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = PanelColors.key(),
        shape = OgnelissShapeTokens.dialKey,
        modifier = modifier
            .fillMaxWidth()
            .clip(OgnelissShapeTokens.dialKey)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            Modifier
                .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                .padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(OgnelissIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.l))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
