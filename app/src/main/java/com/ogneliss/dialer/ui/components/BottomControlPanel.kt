package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.PanelReachesNavBar
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons


import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.ogneliss.dialer.ui.theme.NoteTeal
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.PanelColors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.CallLogFilter
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens


/**
 * НИЖНЯЯ ПАНЕЛЬ УПРАВЛЕНИЯ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ГЛАВНЫЙ ПРИНЦИП: ВСЁ УПРАВЛЕНИЕ ВНИЗУ
 * ══════════════════════════════════════════════════════════════════════════
 * Номер набирают одной рукой, чаще на ходу. Большой палец уверенно
 * достаёт нижнюю треть экрана; верхняя граница комфортной зоны
 * на современном телефоне — примерно 60 % высоты снизу.
 *
 * Всё, что выше, требует перехвата телефона второй рукой или
 * подтягивания пальца — и то и другое на ходу неудобно, а с телефоном
 * в чехле ещё и рискованно.
 *
 * Поэтому здесь собрано ВСЁ, что нажимают: клавиатура, вызов, контакты,
 * фильтры, поиск, меню. Верх экрана отдан карточкам вызовов — их
 * читают и по ним свайпают, но точных нажатий там почти нет.
 *
 * ── Два состояния панели ───────────────────────────────────────────────────
 * СВЁРНУТАЯ — только строка действий. Журнал занимает почти весь экран,
 *   это состояние по умолчанию: чаще открывают приложение посмотреть,
 *   кто звонил, а не набрать номер.
 *
 * РАЗВЁРНУТАЯ — набранный номер, клавиатура и кнопки вызова.
 *
 * Переключение анимировано по высоте: резкое появление клавиатуры
 * сбивает с толку, потому что содержимое списка скачком уезжает вверх.
 */
@Composable
fun BottomControlPanel(
    expanded: Boolean,
    query: String,
    /** Заметка о набранном номере: показывается строкой над клавишами. */
    noteHint: String? = null,
    sims: List<SimSlot>,
    filter: CallLogFilter,
    voiceAvailable: Boolean,
    pauseLabel: String,
    waitLabel: String,
    onToggleExpanded: () -> Unit,
    onDigit: (Char) -> Unit,
    onLongDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCall: (slotIndex: Int) -> Unit,
    onAddContact: (String) -> Unit,
    /** Вставка номера из буфера обмена в поле набора. */
    onPaste: (String) -> Unit,
    onContacts: () -> Unit,
    onFilters: () -> Unit,
    onVoice: () -> Unit,
    /**
     * Включение режима множественного выбора.
     *
     * null — кнопки нет: в контактах выбирать нечего, там своя панель.
     */
    onStartSelection: (() -> Unit)? = null,
    /** Режим выбора включён: кнопка тогда показывает выход. */
    selectionActive: Boolean = false,
    /** Переход к напоминаниям — место на панели вместо выбора нескольких. */
    onReminders: (() -> Unit)? = null,
    onMenu: () -> Unit,
    /**
     * Перелистывание на контакты свайпом влево.
     *
     * null — жест выключен настройкой.
     */
    onSwipeToContacts: (() -> Unit)? = null,
    /**
     * Панель стоит сбоку, а не внизу: широкое окно с открытой
     * клавиатурой. Разбор — в MainDialerScreen, у раскладки журнала.
     *
     * Сбоку у панели все углы скруглены (она стоит рядом со списком,
     * а не выезжает снизу) и нет полоски-ручки: тянуть панель вниз там
     * некуда, а закрыть её можно кнопкой рядом с кнопками вызова.
     * Полоска сбоку отняла бы 20 dp высоты — а её там и так впритык.
     */
    side: Boolean = false,
    /**
     * Вертикальная полоса справа — телефон лёжа (1.2.2). Разбор —
     * у ActionRail ниже.
     */
    rail: Boolean = false,
    /** Высота клавиш, если её диктует место: разбор в Dialpad. */
    dialKeyHeight: androidx.compose.ui.unit.Dp? = null,
    modifier: Modifier = Modifier,
    menuContent: @Composable () -> Unit
) {
    if (rail) {
        Surface(
            color = PanelColors.panel(),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = modifier.panelVerticalSwipeNavigation(
                enabled = onSwipeToContacts != null,
                onSwipeUp = onSwipeToContacts
            )
        ) {
            ActionRail(
                expanded = expanded,
                filter = filter,
                onToggleExpanded = onToggleExpanded,
                onContacts = onContacts,
                onFilters = onFilters,
                onStartSelection = onStartSelection,
                selectionActive = selectionActive,
                onReminders = onReminders,
                onMenu = onMenu,
                menuContent = menuContent
            )
        }
        return
    }
    val handleHint = stringResource(R.string.gesture_handle_hint)

    Surface(
        // Панель лежит на уровень выше содержимого: видна граница,
        // и понятно, что список уходит под неё, а не обрывается
        // Цвет панели общий для четырёх экранов: разбор в PanelColors
        color = PanelColors.panel(),
        /*
         * Жест перелистывания на самой панели.
         *
         * Свайп по списку уже занят вызовом и сообщением; добавить
         * туда ещё и переход значило бы заставить приложение угадывать
         * намерение по длине жеста — а цена ошибки высока: вместо
         * перехода уходит звонок.
         *
         * Панель не прокручивается, кнопки на ней срабатывают
         * по нажатию, и горизонтальное движение по ней не означает
         * больше ничего.
         */
        modifier = modifier.panelSwipeNavigation(
            enabled = onSwipeToContacts != null,
            onSwipeLeft = onSwipeToContacts
        ),
        shape = if (side) MaterialTheme.shapes.extraLarge else RoundedCornerShapeTop,
        /*
         * Без тонального подъёма (было 3 dp).
         *
         * Сама панель от него не менялась: Material подкрашивает только
         * поверхности цвета surface, а у панели свой цвет. Но подъём
         * наследуется вложенными Surface — и кнопка «Скрыть клавиатуру»
         * (FilledTonalIconButton — это Surface) цвета «клавиши» попадала
         * под подкраску: в светлой теме цвет клавиши — белый, ровно
         * такой же, как surface. Material принимал её за surface и
         * подмешивал синеву — кнопка выходила #EAF1FB вместо белого
         * и почти сливалась с панелью, хотя в коде ей задан белый.
         * Найдено по пикселям снимка: клавиши #FFFFFF, кнопка #EAF1FB.
         */
        tonalElevation = 0.dp
    ) {
        // Внизу — полоса под навигацией её цвета (NavBarStrip.kt); сбоку — нет
        if (!side) PanelReachesNavBar()
        Column(
            Modifier
                .navigationBarsPadding()
                // Сбоку панель во всю высоту, и клавиши делят её между собой
                .then(if (side) Modifier.fillMaxHeight() else Modifier)
        ) {

            /*
             * ПОЛОСКА-НАМЁК НА ЖЕСТЫ.
             *
             * ══════════════════════════════════════════════════════════════
             * ЗАЧЕМ ОНА
             * ══════════════════════════════════════════════════════════════
             * Свайпы по панели ничем себя не выдают: панель выглядит
             * как ряд кнопок, и тянуть её никто не пробует. Подсказка
             * при первом запуске это решает лишь отчасти — её видят
             * один раз и забывают.
             *
             * Короткая полоска сверху — устоявшийся знак «это можно
             * тянуть». Так помечают шторки и модальные окна во всей
             * системе, и жест человек угадывает без объяснений.
             *
             * ── Почему не текстовая подсказка ──────────────────────────
             * Надпись на панели занимала бы место и читалась бы каждый
             * раз заново, а нужна она однажды. Полоска не отвлекает,
             * но остаётся на виду.
             *
             * Описание для TalkBack при этом текстовое: незрячему
             * человеку полоска ничего не говорит, и жест нужно назвать
             * словами.
             */
            if (!side) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        /*
                         * ЖЕСТ КЛАВИАТУРЫ ЖИВЁТ НА ПОЛОСКЕ.
                         *
                         * ══════════════════════════════════════════════════════
                         * ПОЧЕМУ НЕ НА ПОЛОСЕ С НОМЕРОМ
                         * ══════════════════════════════════════════════════════
                         * Там он не работал: полоса занята кнопками — удаление,
                         * очистка, добавление в контакты. Дочерние элементы
                         * получают касание первыми и потребляют его,
                         * до жеста дело не доходит.
                         *
                         * На полоске нет ничего. Это и есть её назначение:
                         * во всей системе такой знак означает «здесь можно
                         * тянуть», и человек тянет именно за неё.
                         *
                         * Область касания шире самой полоски — на всю ширину
                         * панели и с отступами: попасть в четыре пикселя
                         * высоты пальцем невозможно.
                         */
                        .padding(vertical = Space.s)
                        .verticalSwipeGesture(
                            onSwipeUp = if (!expanded) onToggleExpanded else null,
                            onSwipeDown = if (expanded) onToggleExpanded else null
                        )
                        /*
                         * Полоска теперь ещё и нажимается.
                         *
                         * Пока ряд кнопок был виден всегда, клавиатуру
                         * закрывали кнопкой в нём. Ряд прячется вместе
                         * с открытой клавиатурой, и жест остался бы
                         * единственным способом — а жесту неоткуда
                         * научиться тому, кто его не знает.
                         *
                         * Касание полоски делает то же самое и не требует
                         * ничего знать: на неё смотрят, потому что она
                         * единственное, что отделяет клавиатуру от списка.
                         */
                        .clickable(onClick = onToggleExpanded)
                        .semantics {
                            contentDescription = handleHint
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.size(width = 32.dp, height = 4.dp)
                    ) {}
                }
            } else {
                Spacer(Modifier.height(Space.s))
            }

            AnimatedVisibility(
                visible = expanded,
                // Сбоку — всё место под строкой номера: ряды клавиш делят
                // его поровну (Dialpad с fillHeight)
                modifier = if (side) Modifier.weight(1f) else Modifier,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                /*
                 * СКРЫТИЕ СВАЙПОМ ВНИЗ ПО ПОЛОСЕ НОМЕРА.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ПОЧЕМУ ИМЕННО ЗДЕСЬ, А НЕ ПО КЛАВИШАМ
                 * ══════════════════════════════════════════════════════════
                 * Начать движение вниз с клавиш заманчиво — до верхнего
                 * ряда «1 2 3» ближе всего. Но там жест мешает набору:
                 *
                 * • Палец при быстром наборе не опускается строго
                 *   вертикально — он скользит от клавиши к клавише.
                 *   Часть таких движений система засчитала бы как
                 *   перетаскивание, и клавиатура закрывалась бы
                 *   посреди набора номера.
                 *
                 * • Долгое нажатие на клавише занято: «0» даёт плюс,
                 *   остальные — быстрый набор. Жест поверх этого
                 *   заставил бы различать удержание и удержание
                 *   со сдвигом.
                 *
                 * Полоса с набранным номером свободна: по ней не нажимают
                 * ничего, кроме кнопок удаления справа, а те срабатывают
                 * по касанию.
                 *
                 * Направление совпадает с тем, как убирают системную
                 * клавиатуру и шторку уведомлений — жест уже знаком.
                 */
                Column(if (side) Modifier.fillMaxHeight() else Modifier) {
                    // Жест только на полосе номера, не на всём столбце:
                    // иначе он захватил бы и клавиши, где мешает набору
                    Box(
                        Modifier.verticalSwipeGesture(onSwipeDown = onToggleExpanded)
                    ) {
                        NumberDisplay(
                            query = query,
                            pauseLabel = pauseLabel,
                            waitLabel = waitLabel,
                            onBackspace = onBackspace,
                            onClear = onClear,
                            onAddContact = onAddContact,
                            onPaste = onPaste
                        )
                    }

                    /*
                     * ЗАМЕТКА О НАБИРАЕМОМ НОМЕРЕ.
                     *
                     * ══════════════════════════════════════════════════════
                     * ЗАЧЕМ
                     * ══════════════════════════════════════════════════════
                     * Заметки — то, чего нет у других диалеров, но увидеть
                     * их можно было только в карточке. То есть человек,
                     * набирающий номер вручную, свою же запись
                     * не встречал: «этот номер не отвечает после 18:00»
                     * лежала там, куда он в этот момент не смотрит.
                     *
                     * Подсказка появляется ровно тогда, когда набранное
                     * совпало с номером, у которого есть заметка.
                     * Она ничего не требует и ни на что не нажимается:
                     * это напоминание, а не действие.
                     *
                     * ── Почему одна строка ─────────────────────────────────
                     * Полоса стоит между номером и клавишами. Две строки
                     * сдвинули бы клавиатуру вниз, и цифры оказались бы
                     * не там, где палец их ждёт, — цена выше пользы.
                     */
                    /*
                     * Подсказка появляется раскрытием, а не рывком.
                     *
                     * Она возникает по мере набора: без анимации строка
                     * возникала бы посреди набора и сдвигала клавиатуру
                     * скачком — палец в этот момент уже летит к следующей
                     * цифре. Раскрытие сверху вниз занимает те же
                     * миллисекунды, но движение видно, и промах
                     * не воспринимается как случайность.
                     */
                    AnimatedVisibility(
                        visible = !noteHint.isNullOrBlank(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val hint = noteHint.orEmpty()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = FlowUi.screenPadding, vertical = Space.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                OgnelissIcons.StickyNote2,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeInline),
                                tint = NoteTeal
                            )
                            Spacer(Modifier.width(Space.s))
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }


                    Dialpad(
                        onDigit = onDigit,
                        onLongDigit = onLongDigit,
                        keyHeight = dialKeyHeight,
                        fillHeight = side,
                        modifier = if (side) Modifier.weight(1f) else Modifier
                    )


                    Spacer(Modifier.height(Space.s))


                    /*
                     * ══════════════════════════════════════════════════════
                     * КНОПКА «СКРЫТЬ КЛАВИАТУРУ» — ТАМ, ГДЕ БЫЛА КНОПКА
                     * ЕЁ ОТКРЫТИЯ
                     * ══════════════════════════════════════════════════════
                     * Клавиатуру открывают большой синей кнопкой внизу по
                     * центру. Открыл — кнопка исчезла вместе с рядом
                     * действий, и закрыть можно было свайпом или касанием
                     * полоски сверху. Оба способа надо знать; забыл —
                     * и клавиатура закрывает полэкрана без видимого
                     * выхода.
                     *
                     * Теперь слева от кнопок вызова — небольшая кнопка
                     * со значком «скрыть клавиатуру». Место выбрано
                     * нарочно: нижний край, куда палец уже привык тянуться
                     * за клавиатурой. Открыл внизу — закрыл тоже внизу.
                     *
                     * Значок сменяет значок клавиатуры плавно (Crossfade):
                     * так видно, что это та же кнопка в другом состоянии,
                     * а не новая.
                     *
                     * Небольшая, а не во всю высоту: главное в этом ряду —
                     * вызов, и кнопка скрытия не должна с ним спорить.
                     */
                    // Отступ слева — у ряда, справа и между кнопками —
                    // собственный отступ CallButtons: так поля по краям
                    // экрана остаются одинаковыми, по 16 dp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = FlowUi.contentPadding)
                    ) {
                        FilledTonalIconButton(
                            onClick = onToggleExpanded,
                            // Тот же фон, что у клавиш: кнопка стоит в одном
                            // блоке с ними и должна читаться как их соседка
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = PanelColors.key()
                            ),
                            modifier = Modifier.size(Dimens.MIN_TOUCH_TARGET)
                        ) {
                            androidx.compose.animation.Crossfade(
                                targetState = expanded,
                                label = "dialpadToggleIcon"
                            ) { open ->
                                Icon(
                                    if (open) OgnelissIcons.KeyboardHide else OgnelissIcons.Dialpad,
                                    contentDescription = stringResource(R.string.a11y_hide_dialpad)
                                )
                            }
                        }

                        // Кнопки вызова: при двух SIM их две, и выбор карты
                        // не требует лишнего шага
                        Box(Modifier.weight(1f)) {
                            CallButtons(
                                sims = sims,
                                enabled = query.isNotEmpty(),
                                onCall = onCall
                            )
                        }
                    }


                    Spacer(Modifier.height(Space.m))
                }
            }


            /*
             * СТРОКА ДЕЙСТВИЙ ПРЯЧЕТСЯ ВМЕСТЕ С ОТКРЫТОЙ КЛАВИАТУРОЙ.
             *
             * ══════════════════════════════════════════════════════════════
             * ЗАЧЕМ
             * ══════════════════════════════════════════════════════════════
             * Клавиатура и так занимает нижнюю половину экрана. Ряд
             * кнопок под ней — ещё 68 dp, то есть полторы строки журнала
             * из тех немногих, что остались видны. А нужен он в этот
             * момент меньше всего: человек набирает номер, а не ищет
             * контакты или фильтры.
             *
             * ── Чем закрыть клавиатуру, если кнопки нет ────────────────────
             * Тремя способами, и все они уже были: полоска сверху
             * (свайп вниз или касание), свайп вниз по строке номера,
             * системная кнопка «назад».
             *
             * ── Что НЕ прячется ───────────────────────────────────────────
             * Перелистывание свайпом по панели. Жест висит на самой
             * панели, а не на строке кнопок, поэтому переход к контактам
             * работает и с открытой клавиатурой — как и просили.
             */
            AnimatedVisibility(
                visible = !expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
            ActionRow(
                expanded = expanded,
                filter = filter,
                voiceAvailable = voiceAvailable,
                onToggleExpanded = onToggleExpanded,
                onContacts = onContacts,
                onFilters = onFilters,
                onVoice = onVoice,
                onStartSelection = onStartSelection,
                selectionActive = selectionActive,
                onReminders = onReminders,
                onMenu = onMenu,
                menuContent = menuContent
            )
            }
        }
    }
}


private val RoundedCornerShapeTop =
    OgnelissShapeTokens.bottomPanel


/**
 * Строка действий.
 *
 * Центральная кнопка — клавиатура: она крупнее остальных и выделена
 * цветом, потому что это точка входа в основное действие приложения.
 * Расположена по центру, куда большой палец попадает без прицеливания
 * при любом хвате.
 */
@Composable
private fun ActionRow(
    expanded: Boolean,
    filter: CallLogFilter,
    voiceAvailable: Boolean,
    onToggleExpanded: () -> Unit,
    onContacts: () -> Unit,
    onFilters: () -> Unit,
    onVoice: () -> Unit,
    /**
     * Включение режима множественного выбора.
     *
     * null — кнопки нет: в контактах выбирать нечего, там своя панель.
     */
    onStartSelection: (() -> Unit)? = null,
    /** Режим выбора включён: кнопка тогда показывает выход. */
    selectionActive: Boolean = false,
    /** Переход к напоминаниям — место на панели вместо выбора нескольких. */
    onReminders: (() -> Unit)? = null,
    onMenu: () -> Unit,
    menuContent: @Composable () -> Unit
) {
    // Полоса навигации слева от всего экрана — признак из RootScreen.
    // Не по раскладке: в режиме «список — подробности» раскладка у журнала
    // своя, по ширине панели, и по ней полосу не увидеть
    val railShown = com.ogneliss.dialer.ui.adaptive.LocalNavigationRailShown.current
    Row(
        Modifier
            .fillMaxWidth()
            // Высота задана явно и тем же значением, что у панели
            // контактов: раньше обе набирали её из содержимого
            // и не совпадали
            .height(FlowUi.panelRowHeight())
            .padding(horizontal = Space.s),
        /*
         * Подписи — на одной линии: с 1.2.0 все кнопки ряда одной высоты
         * (таблетки по 40 dp), поэтому достаточно выравнивания по центру.
         * В 1.1.3 кнопка клавиатуры была выше соседей, и её подпись
         * садилась ниже остальных.
         */
        verticalAlignment = Alignment.CenterVertically
    ) {
        /*
         * ГЛАВНАЯ КНОПКА ЦЕНТРИРУЕТСЯ ПО ПОСТРОЕНИЮ.
         *
         * ── Почему не SpaceEvenly ──────────────────────────────────────────
         * Он распределяет промежутки поровну, но кнопка клавиатуры шире
         * значков (72 dp против 56). Пока по бокам поровну элементов,
         * центр совпадает случайно — стоит добавить или убрать один,
         * и кнопка уезжает.
         *
         * Две группы с равным весом держат её по центру независимо
         * от того, сколько значков в каждой: половина ширины слева,
         * половина справа, кнопка ровно на границе.
         */
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*
             * На широком окне (планшет, раскладушка лёжа) слева есть
             * боковая полоса навигации с контактами и напоминаниями —
             * здесь те же кнопки дублировали бы её. Место не пропадает:
             * Spacer того же размера держит кнопку клавиатуры по центру.
             */
            if (!railShown) {
                LabeledIconButton(OgnelissIcons.Person, R.string.nav_contacts, onContacts)
            } else {
                Spacer(Modifier.size(width = PILL_WIDTH, height = PILL_HEIGHT))
            }


            /*
             * ЗНАЧОК ФИЛЬТРОВ В ПАНЕЛИ.
             *
             * ── Почему здесь, а не в меню ──────────────────────────────────
             * Я убирал его в меню как редкое действие и вернул: фильтры
             * не настолько редки. Их включают, чтобы найти пропущенные
             * за день или посмотреть звонки с одной SIM — это часть
             * обычной работы с журналом, а не разовая настройка.
             *
             * В меню до них было два нажатия вместо одного, и включённый
             * отбор переставал бросаться в глаза.
             */
            Box {
                LabeledIconButton(OgnelissIcons.FilterList, R.string.filter_title, onFilters)

                // Без метки работающий фильтр невидим: журнал наполовину
                // пуст, и это выглядит как потеря данных
                if (filter.isActive) {
                    Badge(Modifier.align(Alignment.TopEnd)) {
                        Text(filter.activeCount.toString())
                    }
                }
            }
        }


        /*
         * Главная кнопка: крупнее и цветная.
         *
         * Свайп вверх раскрывает клавиатуру, вниз — скрывает.
         * Направление повторяет то, что происходит на экране: панель
         * выезжает снизу.
         *
         * Нажатие продолжает работать: Compose различает тап
         * и перетаскивание по порогу сдвига.
         */
        /*
         * Клавиатура: кнопка с подписью, как и соседи.
         *
         * Подпись здесь нужна меньше всего — значок клавиш узнаваем, —
         * но без неё кнопка оказалась бы единственной неподписанной
         * в ряду, и ряд читался бы кривым.
         */
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        FilledIconButton(
            onClick = onToggleExpanded,
            shape = OgnelissShapeTokens.callButton,
            modifier = Modifier
                // Высота — как у боковых кнопок (PILL_HEIGHT): ряд ровный.
                // Шире соседей и залита цветом — главная кнопка видна сразу
                .size(width = 72.dp, height = PILL_HEIGHT)
                .verticalSwipeGesture(
                    // Вверх раскрывает только если ещё закрыта, вниз —
                    // только если открыта: жест в сторону текущего
                    // состояния не должен ничего менять
                    onSwipeUp = if (!expanded) onToggleExpanded else null,
                    onSwipeDown = if (expanded) onToggleExpanded else null
                )
        ) {
            Icon(
                if (expanded) OgnelissIcons.KeyboardArrowDown else OgnelissIcons.Dialpad,
                contentDescription = stringResource(
                    if (expanded) R.string.dialpad_hide else R.string.dialpad_show
                )
            )
        }

        // Подпись — как у соседей: на телефоне лёжа прячется вместе с ними
        if (!com.ogneliss.dialer.ui.adaptive.LocalWindowLayout.current.isShort &&
            com.ogneliss.dialer.ui.theme.LocalAppearance.current.buttonLabels
        ) {
            // 4 dp, а не 2: подпись прилипала к таблетке кнопки
            Spacer(Modifier.height(Space.xs))

            Text(
                stringResource(R.string.bar_dialpad),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        }


        /*
         * ГОЛОСОВОЙ ВВОД УБРАН.
         *
         * SpeechRecognizer.isRecognitionAvailable отвечает утвердительно,
         * но фактическое распознавание зависит от приложения Google:
         * без сети и офлайн-модели языка оно показывает «Голосовой поиск
         * недоступен». Проверить это заранее нельзя — состояние чужого
         * приложения нам не видно.
         *
         * Кнопка, ведущая в тупик, хуже отсутствующей: человек нажимает,
         * получает чужое окно с ошибкой и не понимает, что сломалось.
         *
         * VoiceInput и VoiceQueryParser оставлены: если появится надёжный
         * способ проверки, вернуть кнопку — вопрос одной строки.
         */
        // Правая группа: тот же вес, что у левой
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*
             * ══════════════════════════════════════════════════════════════
             * НАПОМИНАНИЯ ВМЕСТО ВЫБОРА НЕСКОЛЬКИХ
             * ══════════════════════════════════════════════════════════════
             * Здесь стоял режим выбора записей. Он попал сюда не потому,
             * что часто нужен, а потому что освободилось место после
             * голосового ввода: «пусть хоть так попадётся на глаза».
             *
             * Оказалось, что попадается он чаще, чем требуется. Выбрать
             * несколько записей нужно, чтобы стереть пачку старых звонков —
             * дело редкое. Напоминания смотрят чаще: «кому я обещал
             * перезвонить сегодня».
             *
             * Место на панели одно, и достаётся оно тому, за чем приходят
             * чаще. Выбор нескольких остаётся в меню под тремя точками
             * и по долгому нажатию на запись.
             *
             * ⚠️ Пока режим выбора включён, кнопка остаётся выходом из него:
             * человек, включивший его случайно, ищет выход там, где нажал.
             * Крестик в шапке тоже работает, но он дальше от пальца.
             */
            if (selectionActive && onStartSelection != null) {
                LabeledIconButton(
                    icon = OgnelissIcons.Close,
                    // «Выйти», а не «Выйти из выбора»: под значком
                    // помещается одно слово
                    labelRes = R.string.bar_exit,
                    onClick = onStartSelection,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else if (onReminders != null && !railShown) {
                LabeledIconButton(OgnelissIcons.Alarm, R.string.nav_reminders, onReminders)
            } else {
                // Размером с кнопку-таблетку: пустое место не короче соседей
                Spacer(Modifier.size(width = PILL_WIDTH, height = PILL_HEIGHT))
            }


            Box {
                /*
                 * Признак включённого фильтра — на значке меню.
                 *
                 * Фильтры переехали внутрь меню, и без метки получилось
                 * бы хуже прежнего: журнал наполовину пуст, а понять
                 * почему нельзя, не открыв меню.
                 *
                 * Число, а не точка: видно, сколько условий действует,
                 * и стоит ли разбираться или проще сбросить.
                 */
                Box {
                    // Подпись короткая: «Открыть меню» под значком
                    // не поместилось бы, а рядом с ним стоят «Контакты»
                    // и «Напоминания» такой же длины
                    LabeledIconButton(OgnelissIcons.MoreVert, R.string.bar_menu, onMenu)
                    if (filter.isActive) {
                        Badge(Modifier.align(Alignment.TopEnd)) {
                            Text(filter.activeCount.toString())
                        }
                    }
                }
                menuContent()
            }
        }
    }
}


/**
 * ══════════════════════════════════════════════════════════════════════════
 * ПАНЕЛЬ ЖУРНАЛА СПРАВА — ТЕЛЕФОН ЛЁЖА
 * ══════════════════════════════════════════════════════════════════════════
 * Телефон лёжа держат двумя руками, большие пальцы — по бокам. Нижняя
 * панель при этом под пальцами не оказывается, а по высоте её место
 * дорого: экран и так низкий. Справа, вертикальной полосой, кнопки
 * ложатся под правый большой палец, а журнал получает всю высоту.
 *
 * Кнопки те же и в том же порядке, что на нижней панели, сверху вниз:
 * контакты, фильтры, клавиатура, напоминания, «Ещё». Без подписей —
 * лёжа их и на нижней панели не было.
 *
 * Перелистывание экранов — свайпом снизу вверх по полосе
 * (panelVerticalSwipeNavigation). У кнопки клавиатуры вертикального
 * жеста здесь нет: он спорил бы с перелистыванием.
 */
@Composable
private fun ActionRail(
    expanded: Boolean,
    filter: CallLogFilter,
    onToggleExpanded: () -> Unit,
    onContacts: () -> Unit,
    onFilters: () -> Unit,
    onStartSelection: (() -> Unit)?,
    selectionActive: Boolean,
    onReminders: (() -> Unit)?,
    onMenu: () -> Unit,
    menuContent: @Composable () -> Unit
) {
    /*
     * ПОДПИСИ В БОКОВОЙ КОЛОНКЕ (1.3.4, 04.10.2026, замечание аудита
     * «боковая панель без подписей»).
     * Было: showLabel = false у всех кнопок — лёжа кнопки были немыми.
     * Стало: подписи по той же настройке «Подписи под кнопками», что
     * и в нижней панели, — если по высоте помещаются пять кнопок с подписями
     * (около 340 dp). Колонка фиксированной ширины 80 dp — как стандартная
     * боковая навигация Material; длинные слова ужимает FitText.
     */
    BoxWithConstraints(
        Modifier
            .fillMaxHeight()
            .navigationBarsPadding()
            .width(RAIL_WIDTH)
    ) {
    val labels = com.ogneliss.dialer.ui.theme.LocalAppearance.current.buttonLabels &&
        maxHeight >= RAIL_MIN_HEIGHT_WITH_LABELS
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.s, vertical = Space.m),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LabeledIconButton(OgnelissIcons.Person, R.string.nav_contacts, onContacts, showLabel = labels, fitLabel = true)
        Box {
            LabeledIconButton(OgnelissIcons.FilterList, R.string.filter_title, onFilters, showLabel = labels, fitLabel = true)
            if (filter.isActive) {
                Badge(Modifier.align(Alignment.TopEnd)) { Text(filter.activeCount.toString()) }
            }
        }
        FilledIconButton(
            onClick = onToggleExpanded,
            shape = OgnelissShapeTokens.callButton,
            modifier = Modifier.size(width = PILL_WIDTH, height = 56.dp)
        ) {
            Icon(
                // Клавиатура открывается слева от полосы — стрелка туда же
                if (expanded) OgnelissIcons.KeyboardArrowRight else OgnelissIcons.Dialpad,
                contentDescription = stringResource(
                    if (expanded) R.string.dialpad_hide else R.string.dialpad_show
                )
            )
        }
        if (selectionActive && onStartSelection != null) {
            LabeledIconButton(
                icon = OgnelissIcons.Close,
                labelRes = R.string.bar_exit,
                onClick = onStartSelection,
                tint = MaterialTheme.colorScheme.primary,
                showLabel = labels,
                fitLabel = true
            )
        } else if (onReminders != null) {
            LabeledIconButton(OgnelissIcons.Alarm, R.string.nav_reminders, onReminders, showLabel = labels, fitLabel = true)
        }
        Box {
            LabeledIconButton(OgnelissIcons.MoreVert, R.string.bar_menu, onMenu, showLabel = labels, fitLabel = true)
            menuContent()
        }
    }
    }
}

/** Ширина боковой колонки — как у стандартной боковой навигации Material. */
private val RAIL_WIDTH = 80.dp

/**
 * Высота, с которой подписи помещаются: четыре кнопки по 40 dp с подписью
 * и отступами (~64 dp) + кнопка клавиатуры 56 dp + поля 24 dp ≈ 336 dp.
 */
private val RAIL_MIN_HEIGHT_WITH_LABELS = 340.dp
