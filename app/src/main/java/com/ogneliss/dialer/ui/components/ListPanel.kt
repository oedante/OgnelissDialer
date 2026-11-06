package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.PanelReachesNavBar

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
    /** Перелистывание свайпом по панели: вправо — журнал, влево — заметки. */
    onSwipeRight: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
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
            Spacer(Modifier.height(FlowUi.bottomPanelHandleHeight))
            if (searching) {
                PanelSearchRow(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    hint = searchHint,
                    onClose = onCloseSearch
                )
            } else {
                ActionRow(
                    section = section,
                    expanded = false,
                    filterActive = filterActive,
                    onToggleExpanded = onDialpad,
                    onOpenSection = onOpenSection,
                    onMenu = onMenu,
                    menuContent = menuContent
                )
            }
        }
    }
}
