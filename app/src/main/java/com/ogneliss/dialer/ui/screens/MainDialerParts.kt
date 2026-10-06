package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

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
    onOwnNumber: () -> Unit = {},
    onDismiss: () -> Unit,
    /**
     * Фильтры журнала.
     *
     * Переехали сюда с нижней панели: значок занимал там место
     * постоянно, а включают их несколько раз в неделю.
     */
    settings: com.ogneliss.dialer.ui.SettingsState,
    onThemeToggle: () -> Unit,
    onClearLog: () -> Unit,
    onSettings: () -> Unit,
    /** Псевдозвонок: выбор, когда «позвонить». */
    onFakeCall: () -> Unit,
    /** Экран заметок. */
    onNotes: () -> Unit,
    /** Экран напоминаний. */
    onReminders: () -> Unit,
    onStartSelection: () -> Unit,
    selectionActive: Boolean = false
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
    val clearLabel = stringResource(R.string.log_clear_all)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(Modifier.padding(bottom = Space.l)) {
            SheetGroup {
                SheetRow(OgnelissIcons.PhoneCallback, fakeCallLabel, onClick = onFakeCall)
                SheetDivider()
                SheetRow(
                    OgnelissIcons.Person,
                    ownLabel,
                    // Номер второй строкой: видно, какой задан, без похода в настройки
                    supporting = ownNumber.takeIf { it.isNotBlank() }?.let { PhoneFormatter.format(it) },
                    // Приглушённый, пока номера нет: не требует внимания, но и не прячется
                    iconTint = if (ownNumber.isBlank()) MaterialTheme.colorScheme.outline else null,
                    onClick = onOwnNumber
                )
            }
            SheetGroup {
                SheetRow(
                    OgnelissIcons.StickyNote2,
                    notesLabel,
                    // Нейтральная — правило шторок (1.3.5, аудит)
                    onClick = onNotes
                )
                SheetDivider()
                SheetRow(OgnelissIcons.Alarm, remindersLabel, onClick = onReminders)
                SheetDivider()
                SheetRow(
                    if (selectionActive) OgnelissIcons.Close else OgnelissIcons.Checklist,
                    selectionLabel,
                    onClick = onStartSelection
                )
            }
            SheetGroup {
                SheetRow(
                    if (settings.theme == ThemeMode.DARK) OgnelissIcons.LightMode else OgnelissIcons.DarkMode,
                    themeLabel,
                    onClick = onThemeToggle
                )
                SheetDivider()
                SheetRow(OgnelissIcons.Settings, settingsLabel, onClick = onSettings)
            }
            SheetRow(
                OgnelissIcons.DeleteSweep,
                clearLabel,
                color = MaterialTheme.colorScheme.error,
                onClick = onClearLog,
                // Вне панели — отступ как у групп выше
                standalone = true
            )
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
    groupPosition: GroupPosition = GroupPosition.SINGLE
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
        }
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
    onOpen: () -> Unit
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.screenPadding, vertical = FlowUi.cardSpacing)
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
                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    PhoneFormatter.format(row.e164),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onCall) {
                Icon(
                    OgnelissIcons.Call,
                    contentDescription = stringResource(R.string.action_call),
                    tint = CallGreen
                )
            }
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
