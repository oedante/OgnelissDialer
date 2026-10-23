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
    onSwitchList: () -> Unit,
    onOpenSection: (MainSection) -> Unit,
    filterActive: Boolean,
    onFilters: () -> Unit,
    /** Клавиатура набора — в журнале. */
    onDialpad: () -> Unit,
    onSearch: () -> Unit,
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
        // Цвет, скругление и тень — как у панели журнала (BottomControlPanel)
        color = PanelColors.panel(),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        shape = OgnelissShapeTokens.bottomPanel,
        modifier = Modifier.panelSwipeNavigation(
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
                    // Фильтр контактов всегда один — метка точкой, без числа
                    filterBadge = null,
                    onToggleExpanded = onDialpad,
                    onSwitchList = onSwitchList,
                    onOpenSection = onOpenSection,
                    onFilters = onFilters,
                    onSearch = onSearch,
                    onMenu = onMenu,
                    menuContent = menuContent
                )
            }
        }
    }
}
