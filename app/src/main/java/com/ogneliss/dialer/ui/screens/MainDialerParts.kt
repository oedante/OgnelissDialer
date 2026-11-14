package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.ScaledWindow
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.ui.theme.listName

import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.settings.ThemeMode
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ogneliss.dialer.ui.theme.CallGreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.data.CallLogDateGroup
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.*
import com.ogneliss.dialer.ui.components.CallLogRowState
import com.ogneliss.dialer.util.PhoneFormatter
import com.ogneliss.dialer.data.RemindersRepository
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/*
 * Журнал — меню «Ещё», строка журнала, закреплённые без звонков, подпись плиток псевдозвонка.
 *
 * Вынесено из MainDialerScreen.kt (1.3.4, 04.10.2026, фаза 4 — деление экранов
 * длиннее 2000 строк): вспомогательные части экрана. Видимость private →
 * internal — ими пользуется основной файл. Код не менялся.
 */

/**
 * МЕНЮ — ПАНЕЛЬЮ СПРАВА, КАК В OGNELISS MAIL (2.2.1).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО И ПОЧЕМУ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 11.11.2026: «за референс UI возьмём Ogneliss Mail… чтобы эти два
 * приложения были полностью похожи по UI и организации элементов и данных».
 * В почте правая кнопка нижней панели — «Меню»: панель выезжает справа,
 * сверху — «Папки» списком, ниже — «Избранное» плитками. Здесь то же:
 * • [header] — «Папки» раздела SMS (в журнале и контактах папок нет);
 * • плитки — «Инструменты» и «Приложение», прижаты к низу: под большим
 *   пальцем, как «Избранное» в почте.
 *
 * Было (2.1.0–2.2.0): шторка снизу с тремя группами плиток. Группа «Список»
 * (поиск, фильтры, выбор, архив, очистка журнала) переехала в шапку раздела
 * (SectionTopBar), как в почте: действия над списком — у списка, а меню —
 * про то, куда перейти.
 */
@Composable
internal fun OverflowMenu(
    expanded: Boolean,
    /** Собственный номер или пусто, если не указан. */
    ownNumber: String = "",
    /** Свой контакт; null — пункта нет (меню контактов, 1.6.0). */
    onOwnNumber: (() -> Unit)? = {},
    /** Фото своего контакта из книги (2.0.0); null — значок. */
    ownPhotoUri: String? = null,
    onDismiss: () -> Unit,
    settings: com.ogneliss.dialer.ui.SettingsState,
    onThemeToggle: () -> Unit,
    onSettings: () -> Unit,
    /** Псевдозвонок: выбор, когда «позвонить». */
    onFakeCall: () -> Unit,
    /** Экран заметок. */
    onNotes: () -> Unit,
    /** Экран напоминаний. */
    onReminders: () -> Unit,
    /**
     * «Вопросы и ответы» (1.6.0) — после «Настроек»: с вопросом идут
     * в меню, и ответ должен быть там же, а не на сайте.
     */
    onHelp: (() -> Unit)? = null,
    /**
     * «Значок на главный экран» (2.1.0): значок раздела со счётчиком —
     * widget/SectionWidgets. null — пункта нет (журнал; лаунчер не умеет).
     */
    onPinToHome: (() -> Unit)? = null,
    /** Своё над плитками (2.2.0): «Папки» раздела SMS. null — только плитки. */
    header: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * «Поиск» — строкой-пилюлей в самом верху панели (2.4.0, автор: «перенесём
     * в боковую панель поиск»). Было: значок в шапке раздела. null — нет.
     */
    onSearch: (() -> Unit)? = null,
    /**
     * «Фильтры» — кнопкой у поиска внизу (2.5.0, автор: «почему в разделе Папки
     * находятся фильтры?»): свой набор условий — не папка. null — нет (у SMS
     * и контактов своих фильтров нет: их отборы — сами папки).
     */
    onFilters: (() -> Unit)? = null,
    /** Сколько условий фильтра включено — число на кнопке; 0 — без числа. */
    filtersCount: Int = 0
) {
    if (!expanded) return

    val fakeCallLabel = stringResource(R.string.fake_call_title)
    val ownLabel = stringResource(
        if (ownNumber.isBlank()) R.string.menu_my_number_unknown else R.string.menu_my_contact
    )
    val notesLabel = stringResource(R.string.nav_notes)
    val remindersLabel = stringResource(R.string.nav_reminders)
    val themeLabel = stringResource(
        if (settings.theme == ThemeMode.DARK) R.string.menu_theme_light else R.string.menu_theme_dark
    )
    val settingsLabel = stringResource(R.string.nav_settings)
    val helpLabel = stringResource(R.string.faq_title)

    EndDrawer(onDismiss = onDismiss) {
        /*
         * МЕНЮ — КАК ОСТАЛЬНОЕ ПРИЛОЖЕНИЕ (2.5.0). Автор, по снимкам меню журнала,
         * SMS и контактов: «почему в разделе Папки находятся фильтры?», «поиск
         * надо перенести вниз», «почему настройки панели только в SMS?», «вид
         * отличается от остального приложения».
         *
         * Сверху вниз:
         * • «Папки» — только папки; значок «Настроить папки» — у всех трёх
         *   разделов (порядок и показ: SMS — SmsFoldersScreen, журнал и
         *   контакты — MenuFoldersScreen);
         * • «Инструменты» — строками, как папки (были белые плитки, каких
         *   в приложении больше нигде нет);
         * • внизу, у большого пальца и кнопки «Меню», — приложение: настройки,
         *   справка, тема, значок на главный экран — значками с подписью, как
         *   нижняя панель; и самым низом поиск, а в журнале рядом с ним —
         *   «Фильтры» с числом условий.
         * Подписи частей — обычным регистром, как подписи разделов в настройках
         * (было прописными — выглядело жирнее остального приложения, MenuBand).
         * Верх листается, если не помещается (низкий экран, лёжа); низ — на месте.
         */
        val tools = buildList {
            if (onOwnNumber != null) add(MenuFolder(OgnelissIcons.Person, ownLabel, photoUri = ownPhotoUri, onClick = onOwnNumber))
            add(MenuFolder(OgnelissIcons.PhoneCallback, fakeCallLabel, onClick = onFakeCall))
            add(MenuFolder(OgnelissIcons.StickyNote2, notesLabel, onClick = onNotes))
            add(MenuFolder(OgnelissIcons.Alarm, remindersLabel, onClick = onReminders))
        }
        // Постоянные — первыми, необязательный («на главный экран») — последним,
        // чтобы его отсутствие (журнал) не сдвигало остальные
        val app = buildList {
            add(MenuTile(MenuGroup.APP, OgnelissIcons.Settings, settingsLabel, onSettings))
            if (onHelp != null) add(MenuTile(MenuGroup.APP, OgnelissIcons.Help, helpLabel, onHelp))
            add(
                MenuTile(
                    MenuGroup.APP,
                    if (settings.theme == ThemeMode.DARK) OgnelissIcons.LightMode else OgnelissIcons.DarkMode,
                    themeLabel,
                    onThemeToggle
                )
            )
            if (onPinToHome != null) add(MenuTile(MenuGroup.APP, OgnelissIcons.AddToHomeScreen, stringResource(R.string.menu_pin_home), onPinToHome))
        }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (header != null) header()
                MenuActionRows(stringResource(R.string.menu_section_tools), tools)
            }
            com.ogneliss.dialer.ui.components.OgnelissDivider()
            AppActionsRow(app)
            if (onSearch != null || onFilters != null) {
                Row(
                    Modifier.fillMaxWidth().padding(end = Space.s, bottom = Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSearch != null) SearchPill(stringResource(R.string.nav_search), onSearch, Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    if (onFilters != null) FiltersButton(filtersCount, onFilters)
                }
            }
        }
    }
}

/**
 * Приложение — значками с подписью, как нижняя панель (2.5.0): настройки,
 * справка, тема, значок на главный экран. Ровными долями по ширине.
 */
@Composable
private fun AppActionsRow(actions: List<MenuTile>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.xs),
        verticalAlignment = Alignment.Top
    ) {
        actions.forEach { action ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = action.onClick)
                    .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                    .padding(vertical = Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Space.xxs))
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** «Фильтры» у поиска (журнал): число включённых условий; включены — цветом акцента. */
@Composable
private fun FiltersButton(count: Int, onClick: () -> Unit) {
    val label = stringResource(R.string.filter_title)
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
            Icon(
                OgnelissIcons.FilterList,
                contentDescription = label,
                tint = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * «МОЙ КОНТАКТ» В МЕНЮ ЛЮБОГО РАЗДЕЛА (2.4.0).
 *
 * Было: плитка только в меню журнала — в SMS и контактах её не было,
 * и плитки стояли в разных разделах на разных местах (автор: «всегда
 * разное расположение элементов»). Теперь одно и то же везде: номер
 * не указан — окно своего номера, указан — своя карточка (OWN_CARD_KEY).
 */
internal class OwnContactEntry(val number: String, val photoUri: String?, val open: () -> Unit)

@Composable
internal fun rememberOwnContactEntry(
    vm: DialerViewModel,
    contacts: List<com.ogneliss.dialer.data.Contact>,
    onOpenOwnCard: () -> Unit
): OwnContactEntry {
    val ownNumber by vm.ownNumber.collectAsStateWithLifecycle()
    var askNumber by remember { mutableStateOf(false) }
    if (askNumber) {
        OwnNumberDialog(
            current = ownNumber,
            onDetect = { vm.detectOwnNumber() },
            onSave = { vm.setOwnNumber(it) },
            onDismiss = { askNumber = false }
        )
    }
    val photo = remember(ownNumber, contacts) { ownPhotoUriOf(ownNumber, contacts) }
    return OwnContactEntry(ownNumber, photo) { if (ownNumber.isBlank()) askNumber = true else onOpenOwnCard() }
}

/*
 * ФОТО СВОЕГО КОНТАКТА В МЕНЮ (2.0.1).
 *
 * Свой номер часто записан в нескольких контактах: полный (два номера,
 * почта, фото) и короткие дубли — с SIM-карты, из мессенджера, «Мой
 * номер». В 2.0.0 брался первый попавшийся, и «Мой контакт» открывал
 * дубль с одним номером. Теперь фото берётся у самого полного из них
 * (больше номеров и почты, с фото — выше). Карточка — по ключу
 * OWN_CARD_KEY (2.1.0).
 */
internal fun ownPhotoUriOf(ownNumber: String, contacts: List<com.ogneliss.dialer.data.Contact>): String? {
    if (ownNumber.isBlank()) return null
    val e164 = PhoneFormatter.toE164(ownNumber)
    val tail = com.ogneliss.dialer.data.CallLogGrouper.tailKey(e164)
    return contacts
        .filter { c ->
            c.numbers.any { it.e164 == e164 || (tail != null && com.ogneliss.dialer.data.CallLogGrouper.tailKey(it.e164) == tail) }
        }
        .sortedWith(
            compareByDescending<com.ogneliss.dialer.data.Contact> { !it.photoUri.isNullOrBlank() }
                .thenByDescending { it.numbers.size + it.emails.size }
        )
        .firstOrNull()
        ?.photoUri
}

/**
 * ПОИСК — ПИЛЮЛЕЙ ВВЕРХУ МЕНЮ (2.4.0).
 *
 * Как поле поиска в шапке почты на широком окне: пилюля во всю ширину
 * панели, значок и слово «Поиск». Касание закрывает меню и открывает поле
 * поиска внизу, над клавиатурой, — там, где его ищет большой палец.
 */
/**
 * Пилюля поиска: значок и подпись, касание открывает поле поиска внизу,
 * над клавиатурой. Вверху «Меню» (2.4.0) и вверху списка контактов (2.5.0).
 */
@Composable
internal fun SearchPill(label: String, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SEARCH_PILL_ALPHA),
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.s)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onSearch)
    ) {
        Row(
            Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET).padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(OgnelissIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.l))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Подложка пилюли поиска — как у поиска в шапке почты (6 % цвета текста). */
private const val SEARCH_PILL_ALPHA = 0.06f

/**
 * Панель у правого края во всю высоту — как выдвижное меню почты (endDrawer):
 * выезжает справа, остальной экран затемнён, касание по затемнению или
 * «Назад» закрывает. Ширина — как у почты: не шире 360 dp и не шире 86 %
 * экрана (слева остаётся полоса списка — видно, куда вернёшься).
 */
@Composable
private fun EndDrawer(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ScaledWindow {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClickLabel = stringResource(R.string.nav_close),
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = shown,
                    enter = androidx.compose.animation.slideInHorizontally { it }
                ) {
                    Surface(
                        color = com.ogneliss.dialer.ui.components.sheetContainerColor(),
                        // Скруглён левый край — тот, которым панель «выезжает»
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            topStart = DRAWER_CORNER, bottomStart = DRAWER_CORNER
                        ),
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(DRAWER_WIDTH_FRACTION)
                            .widthIn(max = DRAWER_MAX_WIDTH)
                            // Касание по панели её не закрывает
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing), content = content)
                    }
                }
            }
        }
    }
}

/** Ширина меню: доля экрана и предел — как у выдвижного меню почты. */
private const val DRAWER_WIDTH_FRACTION = 0.86f
private val DRAWER_MAX_WIDTH = 360.dp

/** Скругление выезжающего края — радиус шторки (28 dp). */
private val DRAWER_CORNER = 28.dp

/*
 * ПЛИТКИ МЕНЮ (2.2.1–2.4.1, `tile_grid.dart` почты) убраны в 2.5.0: белые
 * плитки были только здесь и выглядели чужими в приложении (автор: «вид
 * отличается от остального приложения»). Инструменты — строками, как папки
 * (MenuActionRows), приложение — значками с подписью (AppActionsRow).
 */

/**
 * Группы плиток меню: TOOLS — с подписью (две колонки), APP — значком (одна).
 */
internal enum class MenuGroup {
    TOOLS,
    APP
}

/** Название «папки» журнала (2.2.2) — для меню и шапки раздела. */
internal fun journalFolderLabel(folder: com.ogneliss.dialer.data.JournalFolder): Int = when (folder) {
    com.ogneliss.dialer.data.JournalFolder.ALL -> R.string.journal_folder_all
    com.ogneliss.dialer.data.JournalFolder.MISSED -> R.string.journal_folder_missed
    com.ogneliss.dialer.data.JournalFolder.AWAITING -> R.string.filter_awaiting
    com.ogneliss.dialer.data.JournalFolder.INCOMING -> R.string.journal_folder_incoming
    com.ogneliss.dialer.data.JournalFolder.OUTGOING -> R.string.journal_folder_outgoing
    com.ogneliss.dialer.data.JournalFolder.UNKNOWN -> R.string.filter_unknown
}

/** Все папки журнала для экрана порядка и показа (2.5.0): ключ, название, значок. */
internal fun journalFolderItems(): List<Triple<String, Int, androidx.compose.ui.graphics.vector.ImageVector>> =
    com.ogneliss.dialer.data.JournalFolder.entries.map { Triple(it.name, journalFolderLabel(it), journalFolderIcon(it)) }

/** Значок «папки» журнала — те же значки типов вызовов, что в строках. */
internal fun journalFolderIcon(folder: com.ogneliss.dialer.data.JournalFolder): androidx.compose.ui.graphics.vector.ImageVector =
    when (folder) {
        com.ogneliss.dialer.data.JournalFolder.ALL -> OgnelissIcons.History
        com.ogneliss.dialer.data.JournalFolder.MISSED -> OgnelissIcons.CallMissed
        com.ogneliss.dialer.data.JournalFolder.AWAITING -> OgnelissIcons.PhoneCallback
        com.ogneliss.dialer.data.JournalFolder.INCOMING -> OgnelissIcons.CallReceived
        com.ogneliss.dialer.data.JournalFolder.OUTGOING -> OgnelissIcons.CallMade
        com.ogneliss.dialer.data.JournalFolder.UNKNOWN -> OgnelissIcons.PersonOff
    }

/** Плитка меню (2.1.0): группа, значок или фото, подпись, действие. */
internal class MenuTile(
    val group: MenuGroup,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val selected: Boolean = false,
    val photoUri: String? = null
)



/**
 * НАПОМИНАНИЯ НА СЕГОДНЯ — ПОЛОСОЙ НАД ЖУРНАЛОМ (1.6.0, вариант «Б»).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Кнопка «Напоминания» стояла на панели всегда, даже когда напоминаний
 * нет ни одного. Её место занял «Поиск», а то, ради чего на неё
 * нажимали, — «кому я обещал перезвонить сегодня», — теперь видно
 * без нажатия: полосой над журналом, только когда такие есть.
 *
 * Ближайшее — с именем и временем, остальные — числом. Касание
 * открывает экран напоминаний. Полоса — первый элемент списка
 * и уезжает вместе с ним.
 */
@Composable
internal fun TodayRemindersStrip(
    reminders: List<RemindersRepository.Reminder>,
    onOpen: () -> Unit
) {
    val first = reminders.firstOrNull() ?: return
    val context = LocalContext.current
    val time = remember(first.atMillis) {
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(first.atMillis))
    }
    val who = first.displayName?.takeIf { it.isNotBlank() } ?: PhoneFormatter.format(first.e164)
    val title = stringResource(R.string.reminders_today)
    val text = stringResource(R.string.today_strip_text, who, time)
    val more = (reminders.size - 1).takeIf { it > 0 }?.let { n ->
        pluralStringResource(R.plurals.today_strip_more, n, n)
    }
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = OgnelissTheme.surfaces.navIndicator,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.groupInset, vertical = Space.xs)
    ) {
        Row(
            Modifier
                .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(OgnelissIcons.Alarm, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                    append("  ")
                    append(text)
                    if (more != null) {
                        append(" · ")
                        append(more)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(OgnelissIcons.KeyboardArrowRight, contentDescription = null)
        }
    }
}


// Размеры пунктов меню — общие для приложения, разбор в FlowUi
internal val MENU_ITEM_HEIGHT = FlowUi.menuItemHeight
internal val MENU_ITEM_PADDING = FlowUi.menuItemPadding


/**
 * СТРОКА ЖУРНАЛА.
 */
@Composable
internal fun CallLogRow(
    row: CallLogRowState,
    vm: DialerViewModel,
    state: com.ogneliss.dialer.ui.UiState,
    settings: com.ogneliss.dialer.ui.SettingsState,
    selection: SelectionState<String>,
    swipeController: com.ogneliss.dialer.ui.components.swipe.SwipeRevealController,
    swipeTuning: com.ogneliss.dialer.settings.SwipeTuning,
    startHint: com.ogneliss.dialer.ui.components.swipe.RevealAction?,
    endHint: com.ogneliss.dialer.ui.components.swipe.RevealAction?,
    rightExtra: List<SwipeAction>,
    leftExtra: List<SwipeAction>,
    scope: kotlinx.coroutines.CoroutineScope,
    listScrolling: Boolean,
    swipeKeyPrefix: String = "",
    onOpenSubscriber: (String) -> Unit,
    onLongPress: (CallLogGroup) -> Unit,
    onNeedNumberPicker: (CallLogGroup, List<NumberOption>) -> Unit,
    groupPosition: GroupPosition = GroupPosition.SINGLE,
    /**
     * Долгое нажатие на портрет включает выбор (2.1.0). Нет — у найденных
     * контактов: это не записи журнала, удалять и архивировать в них нечего.
     */
    avatarSelects: Boolean = true
) {
    val group = row.group
    // Несколько действий на сторону — панель кнопок вместо мгновенного действия
    val rightMulti = settings.swipeRight != SwipeAction.NONE && rightExtra.isNotEmpty()
    val leftMulti = settings.swipeLeft != SwipeAction.NONE && leftExtra.isNotEmpty()


    CallLogCard(
        groupPosition = groupPosition,
        row = row,
        /*
         * Кнопка вызова в строке.
         *
         * Тот же путь, что у свайпа: через onNeedNumberPicker, если
         * у абонента несколько номеров. Иначе кнопка звонила бы
         * на последний использованный, а свайп спрашивал — и человек
         * получал разное от двух способов сделать одно.
         */
        onCall = {
            /*
             * Тот же путь, что у свайпа вызова.
             *
             * performSwipe сам решает, спросить ли номер и SIM: если
             * у абонента их несколько, появится тот же выбор, что при
             * свайпе. Звонить напрямую значило бы дать двум способам
             * сделать одно разное поведение.
             */
            vm.performSwipe(SwipeAction.CALL, group) { swipeController.close() }
        },
        // Долгое нажатие на трубку — выбор SIM для этого номера (1.8.0)
        onCallLongPress = if (state.sims.size > 1) {
            { r -> vm.chooseSim(r.number, DialerViewModel.SimAction.CALL, r.title) }
        } else null,
        searchQuery = state.query,
        selected = selection.isSelected(row.key),
        selectionActive = selection.isActive,
        swipeKey = swipeKeyPrefix + row.key,
        swipeController = swipeController,
        swipeTuning = swipeTuning,
        directStartAction = startHint,
        directEndAction = endHint,
        /*
         * ДВА-ТРИ ДЕЙСТВИЯ НА СВАЙП — ПАНЕЛЬ КНОПОК (1.2.5).
         * Строка сдвигается и ждёт нажатия, как в почтовых клиентах;
         * мгновенного действия в эту сторону нет (onDirect… ниже — null).
         * Одно действие — как было: срабатывает сразу.
         */
        startActions = if (rightMulti) {
            remember(row.key, settings.swipeRight, rightExtra, state.sims) {
                vm.revealActionsFor(settings.swipeRight, rightExtra, group, state.sims)
            }
        } else emptyList(),
        endActions = if (leftMulti) {
            remember(row.key, settings.swipeLeft, leftExtra, state.sims) {
                vm.revealActionsFor(settings.swipeLeft, leftExtra, group, state.sims)
            }
        } else emptyList(),
        /*
         * СВАЙП ВЫКЛЮЧЕН В НАСТРОЙКАХ — ОБРАБОТЧИКА НЕТ ВОВСЕ.
         *
         * Раньше обработчик передавался всегда, даже для «Нет действия».
         * Строка свайпа считает направление рабочим, если обработчик есть
         * (SwipeRevealRow.hasStartDirect), — и выключенный свайп всё равно
         * тянулся: строка уезжала, возвращалась, только ничего не делала.
         * А при обоих выключенных строка продолжала перехватывать
         * горизонтальные движения пальца.
         *
         * Теперь для «Нет действия» — null: направление не тянется,
         * а при обоих выключенных жест отключается целиком.
         */
        onDirectStart = remember(row.key, settings.swipeRight, rightMulti) {
            if (settings.swipeRight == SwipeAction.NONE || rightMulti) null
            else {
                {
                    vm.performSwipe(settings.swipeRight, group) {
                        swipeController.close()
                    }
                }
            }
        },
        onDirectEnd = remember(row.key, settings.swipeLeft, leftMulti) {
            if (settings.swipeLeft == SwipeAction.NONE || leftMulti) null
            else {
                {
                    vm.performSwipe(settings.swipeLeft, group) {
                        swipeController.close()
                    }
                }
            }
        },
        onClick = {
            /*
             * Нажатие — в журнал приложения. Жалоба «после звонка нажатие
             * на запись ничего не делает» пришла с логом, где нажатий
             * не было вовсе, и причину пришлось искать по коду. Теперь
             * видно, какая ветка сработала и с каким ключом.
             */
            when {
                selection.isActive -> {
                    Logger.i("Journal", "Нажатие: режим выбора")
                    selection.toggle(row.key)
                }
                swipeController.isOpen(row.key) -> {
                    Logger.i("Journal", "Нажатие: закрыта раскрытая строка")
                    swipeController.close()
                }
                else -> {
                    Logger.i("Journal", "Нажатие: карточка ${Logger.maskKey(row.key)}")
                    onOpenSubscriber(row.key)
                }
            }
        },
        onLongClick = {
            if (selection.isActive) selection.toggle(row.key)
            else onLongPress(group)
        },
        // Портрет — вход в выбор сразу с этой строкой (2.1.0); тело — карточки, как было
        onAvatarLongClick = if (avatarSelects) {
            {
                swipeController.close()
                selection.start(row.key)
            }
        } else null
    )
}


/** Подпись группы давности. */
internal fun dateGroupLabel(group: CallLogDateGroup): Int = when (group) {
    CallLogDateGroup.TODAY -> R.string.date_group_today
    CallLogDateGroup.YESTERDAY -> R.string.date_group_yesterday
    CallLogDateGroup.THIS_WEEK -> R.string.date_group_this_week
    CallLogDateGroup.LAST_WEEK -> R.string.date_group_last_week
    CallLogDateGroup.EARLIER -> R.string.date_group_earlier
}


/**
 * Подпись дня для TalkBack (1.8.1): «29 ОКТЯБРЯ · ЧЕТВЕРГ» → «29 октября,
 * четверг». Прописные он может читать по буквам, а точку посередине —
 * вслух.
 */
internal fun spokenDayLabel(label: String, locale: java.util.Locale): String =
    label.replace(" · ", ", ").lowercase(locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

internal const val LOAD_MORE_THRESHOLD = 5



/** Закреплённый номер, по которому ещё не было звонков. */
data class PinnedOnlyRow(
    /** Запись закреплённого в настройках — ключ строки списка. */
    val key: String,
    val e164: String,
    val title: String,
    val photoUri: String?,
    /** Ключ карточки: «contact:<id>» или «number:<e164>». */
    val openKey: String
)

/**
 * Строка закреплённого номера без истории.
 *
 * Отличается от журнальной ровно тем, чего у неё нет: типа вызова,
 * даты и длительности. Всё остальное — фотография, имя, звонок
 * по нажатию — то же самое, чтобы строка не выглядела чужой
 * в списке.
 */
@Composable
internal fun PinnedOnlyCard(
    row: PinnedOnlyRow,
    onCall: () -> Unit,
    onOpen: () -> Unit,
    /** Долгое нажатие на трубку — выбор SIM (1.8.0); null — одна карта. */
    onCallLongPress: (() -> Unit)? = null
) {
    Surface(
        onClick = onOpen,
        // Строка во всю ширину (сплошной список, 1.5.3)
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = Space.l, end = Space.xs, top = Space.s, bottom = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Фото — как у строк журнала (1.3.2): photoUri передавался,
            // но не рисовался, и закреплённый «терял портрет»
            RowAvatar(row.photoUri, name = row.title)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.listName, maxLines = 1)
                Text(
                    PhoneFormatter.format(row.e164),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ActionIconButton(
                icon = OgnelissIcons.Call,
                contentDescription = stringResource(R.string.action_call),
                tint = com.ogneliss.dialer.util.LocalCallAvailability.current.callTint(com.ogneliss.dialer.ui.theme.CallGreen),
                onClick = onCall,
                onLongClick = onCallLongPress,
                onLongClickLabel = stringResource(R.string.a11y_choose_sim)
            )
        }
    }
}

/**
 * Ширина клавиатуры сбоку. 360 dp — ширина обычного телефона:
 * клавиши того же размера, что человек привык видеть в портрете.
 */
internal val SIDE_DIALPAD_WIDTH = 360.dp

/**
 * Подпись плитки-действия в окне псевдозвонка (1.3.4, 04.10.2026).
 * Было: Text с cardPadding — текст прижимался к верху высокой плитки
 * (аудит, скриншот автора). Стало: по центру по вертикали, поле слева
 * как у поля «Кто звонит», высота не меньше 56 dp.
 */
@Composable
internal fun FakeCallTileText(label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = FlowUi.contentPadding, vertical = Space.s),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
