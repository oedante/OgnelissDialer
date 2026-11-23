package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.PanelSlot
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.Space

/**
 * РАЗДЕЛЫ СПРАВА — КОЛОНКА ВМЕСТО НИЖНЕЙ ПАНЕЛИ (2.9.3, ПРОБА).
 *
 * Автор, 23.11.2026, по своему макету: «рассмотреть экспериментальный вариант
 * расположения переключения окон сбоку… первый вариант мне нравится. И надо
 * посмотреть, как лучше сделать, чтобы основные кнопки не перекрывались»;
 * затем: «давай попробуем сделать. Возможно, придётся изменить весь интерфейс,
 * чтобы всё было логично».
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК УСТРОЕНО
 * ══════════════════════════════════════════════════════════════════════════
 * • Колонка — отдельная, рядом со списком, а не поверх него: справа в каждой
 *   строке журнала, SMS и контактов — кнопка звонка, и плавающие кнопки её
 *   закрывали бы. Список становится уже на ширину колонки, зато ничего
 *   не перекрыто.
 * • В колонке — то же, что на нижней панели, и в том же порядке (конструктор
 *   PanelSlots): места 1–2, центральная кнопка раздела («Набор», «Написать»,
 *   «Создать»), места 3–4 — сверху вниз и прижаты к низу, к большому пальцу.
 *   По умолчанию «Меню» — самое нижнее.
 * • Внизу экрана остаётся только нужное в этот момент: постоянное поле поиска
 *   (контакты, заметки, напоминания), клавиатура набора и поиск журнала, когда
 *   они открыты. Списку — вся высота.
 * • Только в разделах-списках (журнал, SMS, контакты, заметки, напоминания):
 *   в переписке, карточке и настройках колонки нет — там нужна ширина.
 *
 * ── Откуда колонка знает центральную кнопку и «Меню» ───────────────────────
 * Они у каждого раздела свои и живут в его ряду кнопок (ActionRow): «Написать»
 * в SMS, клавиатура в журнале, окно «Меню» со своими папками. В режиме колонки
 * ActionRow ряд не рисует, а передаёт сюда свои действия ([SideRailState]);
 * окно «Меню» остаётся в разделе. Так ни один раздел не пришлось переписывать.
 *
 * Только телефон в портрете и раскладушка (RootScreen): у планшета своя полоса
 * слева, у телефона лёжа — своя колонка кнопок журнала.
 */
@Stable
internal class SideRailState {
    /** Действия открытого раздела; null — раздел их не передал (открыта клавиатура). */
    var actions by mutableStateOf<SideRailActions?>(null)
        private set

    /** Кто передал: при смене раздела старый не сотрёт действия нового. */
    private var owner: Any? = null

    fun publish(owner: Any, actions: SideRailActions) {
        this.owner = owner
        this.actions = actions
    }

    fun withdraw(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            actions = null
        }
    }
}

/** Центральная кнопка раздела и «Меню» — для колонки справа. */
internal class SideRailActions(
    val centerIcon: ImageVector,
    val centerLabelRes: Int,
    /** Для TalkBack — действие («Показать клавиатуру»), как у центра нижней панели. */
    val centerDescription: String,
    val onCenter: () -> Unit,
    val onMenu: () -> Unit,
    /** Метка на «Меню» — число условий фильтра журнала; null — без метки. */
    val menuBadge: String?
)

/**
 * Колонка справа включена (RootScreen): разделы-списки не рисуют ряд кнопок
 * внизу, а передают действия сюда. null — нижняя панель, как всегда.
 */
internal val LocalSideRail = staticCompositionLocalOf<SideRailState?> { null }

@Composable
internal fun SideSectionRail(
    state: SideRailState,
    slots: List<PanelSlot>,
    /** Открытый раздел — подсвечен, как на нижней панели. */
    current: MainSection?,
    onOpenSection: (MainSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = state.actions
    Surface(
        // Цвет нижней панели и её линия — слева: та же панель, повёрнутая к краю
        color = PanelColors.panel(),
        modifier = modifier
            .fillMaxHeight()
            .width(SIDE_RAIL_WIDTH)
            .panelStartLine(OgnelissTheme.surfaces.divider)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
                .padding(vertical = Space.s),
            // Прижато к низу — к большому пальцу; сверху — пустая полоса панели
            verticalArrangement = Arrangement.spacedBy(Space.xs, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val slot: @Composable (Int) -> Unit = { index ->
                when (val item = slots.getOrNull(index)) {
                    null -> Unit
                    PanelSlot.MENU -> Box(Modifier.fillMaxWidth().height(CELL_HEIGHT), contentAlignment = Alignment.Center) {
                        LabeledIconButton(
                            OgnelissIcons.Menu, R.string.bar_menu,
                            onClick = { actions?.onMenu?.invoke() },
                            fitLabel = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val badge = actions?.menuBadge
                        if (badge != null) Badge(Modifier.align(Alignment.TopEnd).padding(end = Space.s)) { Text(badge) }
                    }
                    else -> {
                        val section = item.toSection()
                        LabeledIconButton(
                            icon = section.icon,
                            labelRes = section.labelRes,
                            onClick = { if (section != current) onOpenSection(section) },
                            selected = section == current,
                            fitLabel = true,
                            modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT)
                        )
                    }
                }
            }
            slot(0)
            slot(1)
            // Центр — сплошной, как на нижней панели; пока раздел его не передал
            // (открыта клавиатура набора) — пустое место той же высоты, чтобы
            // кнопки под ним не прыгали
            if (actions != null) {
                LabeledIconButton(
                    icon = actions.centerIcon,
                    labelRes = actions.centerLabelRes,
                    onClick = actions.onCenter,
                    filled = true,
                    fitLabel = true,
                    contentDescription = actions.centerDescription,
                    modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT)
                )
            } else {
                Spacer(Modifier.height(CELL_HEIGHT))
            }
            slot(2)
            slot(3)
        }
    }
}

/**
 * Ширина колонки: значок 24 dp в «таблетке» 56 dp и подпись под ним.
 * Длинная подпись («Напоминания») ужимается (FitText), а не переносится.
 */
internal val SIDE_RAIL_WIDTH = 72.dp

/** Высота места — одна на всех, чтобы колонка не прыгала без центра. */
private val CELL_HEIGHT = 68.dp
