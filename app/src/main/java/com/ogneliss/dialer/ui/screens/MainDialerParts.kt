package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.ui.theme.listName

import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.settings.ThemeMode
import androidx.compose.foundation.layout.*
import com.ogneliss.dialer.ui.theme.CallGreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
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
 * Главное меню.
 *
 * Выпадающее от своей кнопки, а не диалог по центру экрана.
 * Разрушительное действие внизу и отделено чертой.
 * Переключение темы находится внутри меню.
 */
// ModalBottomSheet в Material 3 1.3 ещё помечен как экспериментальный
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverflowMenu(
    expanded: Boolean,
    /** Собственный номер или пусто, если не указан. */
    ownNumber: String = "",
    /** Свой контакт; null — пункта нет (меню контактов, 1.6.0). */
    onOwnNumber: (() -> Unit)? = {},
    /** Фото своего контакта из книги (2.0.0); null — значок. */
    ownPhotoUri: String? = null,
    /**
     * Поиск и фильтры списка (2.0.0): переехали с нижней панели, где
     * теперь разделы. null — пункта нет.
     */
    onSearch: (() -> Unit)? = null,
    onFilters: (() -> Unit)? = null,
    /** Число включённых условий фильтра; null — фильтр выключен. */
    filterBadge: String? = null,
    onDismiss: () -> Unit,
    /**
     * Фильтры журнала.
     *
     * Переехали сюда с нижней панели: значок занимал там место
     * постоянно, а включают их несколько раз в неделю.
     */
    settings: com.ogneliss.dialer.ui.SettingsState,
    onThemeToggle: () -> Unit,
    /** Очистить журнал; null — пункта нет (меню контактов, 1.6.0). */
    onClearLog: (() -> Unit)?,
    onSettings: () -> Unit,
    /** Псевдозвонок: выбор, когда «позвонить». */
    onFakeCall: () -> Unit,
    /** Экран заметок. */
    onNotes: () -> Unit,
    /** Экран напоминаний. */
    onReminders: () -> Unit,
    /** Выбрать несколько; null — пункта нет (меню контактов, 1.6.0). */
    onStartSelection: (() -> Unit)?,
    selectionActive: Boolean = false,
    /**
     * «Создать контакт» — в меню контактов (1.6.0): кнопка «Добавить»
     * стояла в их верхней строке, а верхней строки больше нет.
     * null — пункта нет (журнал).
     */
    onAddContact: (() -> Unit)? = null,
    /**
     * «Вопросы и ответы» (1.6.0) — после «Настроек»: с вопросом идут
     * в меню, и ответ должен быть там же, а не на сайте.
     */
    onHelp: (() -> Unit)? = null,
    /**
     * «Архив» (2.1.0): записи, убранные из журнала или SMS режимом выбора.
     * null — пункта нет.
     */
    onArchive: (() -> Unit)? = null,
    /**
     * «Значок на главный экран» (2.1.0): значок раздела со счётчиком —
     * widget/SectionWidgets. null — пункта нет (журнал; лаунчер не умеет).
     */
    onPinToHome: (() -> Unit)? = null
) {
    /*
     * ПОРЯДОК ПУНКТОВ МЕНЮ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПО ЧАСТОТЕ И ПО СМЫСЛУ, А НЕ ПО ИСТОРИИ ДОБАВЛЕНИЯ
     * ══════════════════════════════════════════════════════════════════════
     * Раньше пункты лежали в том порядке, в каком появлялись в коде.
     * Теперь сгруппированы, и группы разделены линиями:
     *
     *   1. Псевдозвонок — единственный пункт, который нажимают
     *      в спешке и не глядя. Первым, под большим пальцем.
     *   2. Тема — переключатель, которым пользуются чаще прочего.
     *   3. Мой контакт, заметки, напоминания — переходы к своим
     *      данным.
     *   4. Выбрать несколько, настройки — режимы и управление.
     *   5. Очистить журнал — необратимое, отдельно и красным.
     *
     * ⚠️ «Очистить журнал» остаётся последним и за линией: пункт,
     * который нельзя отменить, не должен оказаться рядом с тем,
     * что нажимают часто.
     */
    /*
     * ══════════════════════════════════════════════════════════════════════
     * «ЕЩЁ» — ШТОРКА С ГРУППАМИ, А НЕ ВЫПАДАЮЩЕЕ МЕНЮ (1.2.1)
     * ══════════════════════════════════════════════════════════════════════
     * Было стандартное выпадающее меню: углы 4 dp и тень — единственный
     * такой элемент в приложении, где скругления крупные, а теней нет.
     * Выпадало от кнопки внизу, а выглядело как меню верхней строки.
     *
     * Теперь — шторка снизу, как у меню долгого нажатия: появляется
     * там, где палец, и выглядит так же. Порядок и смысл групп прежние:
     *
     *   1. Псевдозвонок и свой номер — быстрые действия;
     *   2. заметки, напоминания, выбор нескольких — свои данные и режимы;
     *   3. тема и настройки — управление;
     *   4. очистить журнал — необратимое, отдельно и красным.
     */
    if (!expanded) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val fakeCallLabel = stringResource(R.string.fake_call_title)
    val ownLabel = stringResource(
        if (ownNumber.isBlank()) R.string.menu_my_number_unknown else R.string.menu_my_contact
    )
    val notesLabel = stringResource(R.string.nav_notes)
    val remindersLabel = stringResource(R.string.nav_reminders)
    val selectionLabel = stringResource(
        if (selectionActive) R.string.selection_exit else R.string.selection_start
    )
    val themeLabel = stringResource(
        if (settings.theme == ThemeMode.DARK) R.string.menu_theme_light else R.string.menu_theme_dark
    )
    val settingsLabel = stringResource(R.string.nav_settings)
    val helpLabel = stringResource(R.string.faq_title)
    val clearLabel = stringResource(R.string.log_clear_all)

    ModalBottomSheet(
        // Страница настроек, группы — белыми карточками (1.6.1, SheetGroup)
        containerColor = com.ogneliss.dialer.ui.components.sheetContainerColor(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        /*
         * «ЕЩЁ» — ПЛИТКАМИ (2.1.0). Автор, 08.11.2026: «меню „Ещё“ сделаем
         * не списком, а панелями — несколько в одном ряду, а то очень
         * большое меню». Было: 12 строк по 56 dp в четырёх группах — шторка
         * во весь экран. Стало: круглые кнопки с подписью, как быстрые
         * действия карточек долгого нажатия (ActionCircle), по 4 в ряд
         * (на широком экране — больше), одной карточкой. Порядок — по частоте:
         * список (поиск, фильтры), свои данные и разделы, приложение.
         * «Очистить журнал» — по-прежнему отдельной красной строкой внизу:
         * необратимое не стоит в одном ряду с частым.
         */
        val tiles = buildList {
            if (onSearch != null) add(MenuTile(OgnelissIcons.Search, stringResource(R.string.nav_search), onSearch))
            if (onFilters != null) add(
                MenuTile(
                    OgnelissIcons.FilterList,
                    // «Фильтры · 2» — коротко: подпись плитки в две строки
                    stringResource(R.string.filter_title) + (filterBadge?.let { " · $it" } ?: ""),
                    onFilters,
                    selected = filterBadge != null
                )
            )
            if (onAddContact != null) add(MenuTile(OgnelissIcons.PersonAdd, stringResource(R.string.action_create_contact), onAddContact))
            if (onOwnNumber != null) add(MenuTile(OgnelissIcons.Person, ownLabel, onOwnNumber, photoUri = ownPhotoUri))
            add(MenuTile(OgnelissIcons.PhoneCallback, fakeCallLabel, onFakeCall))
            add(MenuTile(OgnelissIcons.StickyNote2, notesLabel, onNotes))
            add(MenuTile(OgnelissIcons.Alarm, remindersLabel, onReminders))
            if (onArchive != null) add(MenuTile(OgnelissIcons.Archive, stringResource(R.string.archive_title), onArchive))
            if (onStartSelection != null) add(
                MenuTile(if (selectionActive) OgnelissIcons.Close else OgnelissIcons.Checklist, selectionLabel, onStartSelection)
            )
            if (onPinToHome != null) add(MenuTile(OgnelissIcons.AddToHomeScreen, stringResource(R.string.menu_pin_home), onPinToHome))
            add(
                MenuTile(
                    if (settings.theme == ThemeMode.DARK) OgnelissIcons.LightMode else OgnelissIcons.DarkMode,
                    themeLabel,
                    onThemeToggle
                )
            )
            add(MenuTile(OgnelissIcons.Settings, settingsLabel, onSettings))
            if (onHelp != null) add(MenuTile(OgnelissIcons.Help, helpLabel, onHelp))
        }
        OverflowMenuBody(tiles, clearLabel, onClearLog)
    }
}

/** Содержимое «Ещё» (2.1.0): плитки одной карточкой и «Очистить журнал» отдельно. */
@Composable
internal fun OverflowMenuBody(tiles: List<MenuTile>, clearLabel: String, onClearLog: (() -> Unit)?) {
        // Колонок — по ширине: 4 на телефоне, больше на широком окне (плитка ≈ 88 dp)
        val columns = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 88).coerceIn(4, 6)
        Column(Modifier.padding(bottom = Space.l)) {
            SheetGroup(first = true) {
                Column(Modifier.padding(horizontal = Space.xs, vertical = Space.s)) {
                    tiles.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs)) {
                            row.forEach { tile ->
                                com.ogneliss.dialer.ui.components.ActionCircle(
                                    com.ogneliss.dialer.ui.components.CardAction(
                                        tile.icon, tile.label, onClick = tile.onClick,
                                        tint = if (tile.selected) MaterialTheme.colorScheme.primary else null
                                    ),
                                    Modifier.weight(1f),
                                    selected = tile.selected,
                                    circleContent = tile.photoUri?.takeIf { it.isNotBlank() }?.let { uri ->
                                        { RowAvatar(uri, size = 48.dp) }
                                    },
                                    onClick = tile.onClick
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (onClearLog != null) {
                SheetGroup {
                    SheetRow(
                        OgnelissIcons.DeleteSweep,
                        clearLabel,
                        color = MaterialTheme.colorScheme.error,
                        onClick = onClearLog
                    )
                }
            }
        }
}

/** Плитка «Ещё» (2.1.0): значок или фото, подпись, действие. */
internal class MenuTile(
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
