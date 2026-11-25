package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
 * • В колонке — «Меню» наверху, кнопка действия раздела («Набор», «Написать»,
 *   «Создать») в самом низу и между ними пункты «Колонки справа» (RailItem):
 *   первый — у кнопки действия, дальше вверх. Было (2.9.3): места нижней панели
 *   сверху вниз; 2.9.6 — кнопка действия внизу; 2.9.8 — «Меню» наверху
 *   и инструменты меню на колонке; 2.9.9 — свой порядок и показ, снизу вверх.
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

    /**
     * Действия над списком из «⋮» шапки (2.9.6, автор: «кнопку меню перенести наверх
     * и объединить с „Ещё“»): в режиме колонки шапка показывает одну кнопку «Меню»,
     * а её прежние пункты («Выбрать», «Очистить журнал», «Архив») — частью
     * «Действия» в самом меню (OverflowMenu).
     */
    var listActions by mutableStateOf<List<TopBarMenuItem>>(emptyList())
        private set
    private var listOwner: Any? = null

    fun publishList(owner: Any, items: List<TopBarMenuItem>) {
        listOwner = owner
        listActions = items
    }

    fun withdrawList(owner: Any) {
        if (listOwner === owner) {
            listOwner = null
            listActions = emptyList()
        }
    }

    /**
     * «Мой контакт» и псевдозвонок для колонки (2.9.8, автор: «на панель перенесём
     * из „Ещё“ инструменты»). У каждого раздела они свои (в журнале псевдозвонок
     * открывает выбор времени), поэтому их передаёт меню раздела (OverflowMenu),
     * как «⋮» шапки — [listActions]. Разделы-инструменты колонка открывает сама.
     */
    var tools by mutableStateOf<SideRailTools?>(null)
        private set
    private var toolsOwner: Any? = null

    fun publishTools(owner: Any, tools: SideRailTools) {
        toolsOwner = owner
        this.tools = tools
    }

    fun withdrawTools(owner: Any) {
        if (toolsOwner === owner) {
            toolsOwner = null
            tools = null
        }
    }
}

/** Инструменты раздела без своего раздела: null — в этом разделе строки нет. */
internal class SideRailTools(
    val onOwnContact: (() -> Unit)?,
    val onFakeCall: (() -> Unit)?
)

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
    /**
     * Пункты колонки по порядку (2.9.9, RailItem.visible): первый — у кнопки
     * действия, дальше — вверх.
     */
    items: List<com.ogneliss.dialer.ui.screens.RailItem>,
    /** Открытый раздел — отмечен, как на нижней панели. */
    current: MainSection?,
    onOpenSection: (MainSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = state.actions
    val extras = state.tools
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /*
             * 2.9.8 (автор: «на панель перенесём из „Ещё“ инструменты и саму кнопку
             * „Ещё“; в режиме панели справа не отмечается активная вкладка»).
             * Сверху — «Меню» (было в шапке, 2.9.6): папки, «Действия», приложение
             * и поиск. В самом низу — кнопка действия раздела.
             *
             * 2.9.9 (автор: «частое — ближе к нижнему правому углу, от кнопки
             * клавиатуры или создания: журнал, SMS, контакты и остальное»; «боковую
             * панель тоже настраивать — порядок, отображение»). Между ними — пункты
             * из настройки «Колонка справа» (RailItem): первый — сразу над кнопкой
             * действия, следующие — выше. Было (2.9.8): разделы из конструктора
             * нижней панели сверху вниз, под ними инструменты меню через черту.
             * Не помещается (низкий экран, крупный шрифт) — середина листается,
             * «Меню» и кнопка действия на месте.
             */
            Box {
                LabeledIconButton(
                    icon = OgnelissIcons.Menu,
                    labelRes = R.string.bar_menu,
                    onClick = { actions?.onMenu?.invoke() },
                    fitLabel = true,
                    twoLineLabel = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = CELL_HEIGHT)
                )
                // Число условий фильтра журнала — на «Меню», как на нижней панели
                val badge = actions?.menuBadge
                if (badge != null) Badge(Modifier.align(Alignment.TopEnd).padding(end = Space.s)) { Text(badge) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Снизу вверх: первый пункт — у кнопки действия
                    items.asReversed().forEach { item ->
                        val section = item.section
                        when {
                            section != null -> RailCell(item.icon, item.railLabelRes, item.labelRes, selected = section == current) {
                                if (section != current) onOpenSection(section)
                            }
                            item == com.ogneliss.dialer.ui.screens.RailItem.OWN_CONTACT ->
                                extras?.onOwnContact?.let { RailCell(item.icon, item.railLabelRes, item.labelRes, onClick = it) }
                            item == com.ogneliss.dialer.ui.screens.RailItem.FAKE_CALL ->
                                extras?.onFakeCall?.let { RailCell(item.icon, item.railLabelRes, item.labelRes, onClick = it) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.xs))
            // Кнопка действия — сплошная, как центр нижней панели; пока раздел её
            // не передал (открыта клавиатура набора) — пустое место той же высоты
            if (actions != null) {
                LabeledIconButton(
                    icon = actions.centerIcon,
                    labelRes = actions.centerLabelRes,
                    onClick = actions.onCenter,
                    filled = true,
                    fitLabel = true,
                    twoLineLabel = true,
                    contentDescription = actions.centerDescription,
                    modifier = Modifier.fillMaxWidth().heightIn(min = CELL_HEIGHT)
                )
            } else {
                Spacer(Modifier.height(CELL_HEIGHT))
            }
        }
    }
}

/**
 * Место колонки: значок и подпись. Открытый раздел (2.9.8, автор: «не отмечается
 * активная вкладка») — кроме цвета акцента, полоса акцента у внутреннего края,
 * со стороны списка: одного цвета на узкой колонке было не видно. Тоновой
 * подложки нет — как на нижней панели («вариант А», 2.9.2).
 */
@Composable
private fun RailCell(
    icon: ImageVector,
    labelRes: Int,
    /** Для TalkBack — подпись без мягкого переноса (RailItem.labelRes, 2.9.11). */
    descriptionRes: Int,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    /*
     * 2.9.11 (обзор интерфейса, автор согласен): подпись — в две строки, длинное
     * слово — переносом по слогам («Мой / контакт», «Напоми- / нания»). Было: одна
     * строка, ужатая до 60 % — при обычном шрифте мельче остального текста.
     * Высота места — не меньше прежней, с двумя строками — больше.
     */
    Box(Modifier.fillMaxWidth().heightIn(min = CELL_HEIGHT)) {
        LabeledIconButton(
            icon = icon,
            labelRes = labelRes,
            onClick = onClick,
            selected = selected,
            fitLabel = true,
            twoLineLabel = true,
            contentDescription = stringResource(descriptionRes),
            modifier = Modifier.fillMaxWidth().heightIn(min = CELL_HEIGHT)
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(SELECTED_MARK_WIDTH)
                    .height(SELECTED_MARK_HEIGHT)
                    .clip(RoundedCornerShape(topEnd = SELECTED_MARK_WIDTH, bottomEnd = SELECTED_MARK_WIDTH))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** Полоса открытого раздела (2.9.8): толщина и высота — как у отметки вкладки. */
private val SELECTED_MARK_WIDTH = 3.dp
private val SELECTED_MARK_HEIGHT = 32.dp

/**
 * Ширина колонки (2.9.6, автор: «панель всё ещё очень широкая»; было 72 dp):
 * значок 24 dp и подпись под ним. Длинная подпись («Напоминания») — с 2.9.11
 * в две строки, переносом по слогам; без правил переноса — ужимается (FitText).
 */
internal val SIDE_RAIL_WIDTH = 60.dp

/**
 * Высота места — не меньше этой (с 2.9.11 — «не меньше»: подпись в две строки
 * выше), чтобы колонка не прыгала без центра.
 */
private val CELL_HEIGHT = 68.dp
