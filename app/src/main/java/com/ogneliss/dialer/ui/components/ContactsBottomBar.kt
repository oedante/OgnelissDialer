package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.PanelReachesNavBar
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.PanelColors
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.ContactFilter

/**
 * НИЖНЯЯ ПАНЕЛЬ ЭКРАНА КОНТАКТОВ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ПОИСК ВНИЗУ
 * ══════════════════════════════════════════════════════════════════════════
 * Раньше поиск открывался кнопкой в верхней панели. Список контактов
 * пролистывают большим пальцем в нижней трети экрана, и чтобы начать
 * искать, приходилось тянуться через весь экран или перехватывать
 * телефон второй рукой.
 *
 * Поле внизу доступно тем же пальцем, которым только что листали.
 * Клавиатура открывается прямо под ним и не перекрывает поле —
 * при верхнем расположении она закрывала бы половину результатов.
 *
 * ── Фильтры рядом с поиском ────────────────────────────────────────────────
 * Это два способа решить одну задачу — найти нужного человека. Держать
 * их в разных концах экрана значит заставлять переключаться между
 * дальним и ближним.
 * ══════════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsBottomBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ContactFilter,
    onFilterChange: (ContactFilter) -> Unit,
    /**
     * Открыть шторку фильтров.
     *
     * Фишки убраны с панели: пять штук занимали две строки экрана
     * постоянно ради выбора, который делают несколько раз в неделю.
     */
    onFilters: () -> Unit = {},
    /**
     * Возврат к вызовам свайпом вправо.
     *
     * null — жест выключен настройкой. Направление обратно тому,
     * что уводит из вызовов: перелистывание должно работать в обе
     * стороны одинаково.
     */
    onSwipeToCalls: (() -> Unit)? = null,
    /**
     * Следующий экран карусели: заметки.
     *
     * Порядок журнал → контакты → заметки → напоминания. Влево —
     * вперёд, вправо — назад: то же направление, что у системного
     * жеста «назад», и запоминать нечего.
     */
    onSwipeToNotes: (() -> Unit)? = null
) {
    Surface(
        // Цвет панели общий для четырёх экранов: разбор в PanelColors
        color = PanelColors.panel(),
        // Без тонального подъёма — разбор в BottomControlPanel
        tonalElevation = 0.dp,
        // Тень сверху (1.5.6, как у навигации Ogneliss Mail) с 1.7.0 заменена
        // тонкой линией — «воздушный» вид, разбор в PanelEdge.kt
        shape = OgnelissShapeTokens.bottomPanel,
        // Свайп вправо возвращает к вызовам: то же направление,
        // что у системного жеста «назад», и запоминать нечего
        modifier = Modifier.panelTopLine(com.ogneliss.dialer.ui.theme.OgnelissTheme.surfaces.divider).panelSwipeNavigation(
            enabled = onSwipeToCalls != null || onSwipeToNotes != null,
            onSwipeLeft = onSwipeToNotes,
            onSwipeRight = onSwipeToCalls
        )
    ) {
        // Полоса под навигацией — цвета этой панели (NavBarStrip.kt, 1.5.1)
        PanelReachesNavBar()
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
        ) {
            /*
             * Фильтры прячутся при вводе: во время поиска они не нужны,
             * а место под результаты важнее. Плюс два механизма сужения
             * одновременно дают пустой список и непонимание.
             */
            /*
             * ФИЛЬТРЫ УБРАНЫ В ШТОРКУ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ
             * ══════════════════════════════════════════════════════════════
             * Пять фишек не помещались в строку и переносились на две.
             * Две строки экрана заняты постоянно ради выбора, который
             * делают несколько раз в неделю — и почти всегда возвращаются
             * к «Все».
             *
             * Теперь значок в ряду действий, как в журнале: одно нажатие,
             * а место освободилось под сами контакты.
             *
             * ── Метка обязательна ──────────────────────────────────────────
             * Без неё включённый отбор невидим: половина книги пропала,
             * и это выглядит как потеря контактов.
             */

            /*
             * ФИЛЬТР РЯДОМ С ПОИСКОМ, А НЕ ВНУТРИ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ ВЫНЕСЕН
             * ══════════════════════════════════════════════════════════════
             * Внутри поля значок читался как часть поиска — будто он
             * фильтрует то, что вы вводите. На деле он отбирает всю
             * книгу и к поиску отношения не имеет.
             *
             * Рядом он честнее: два разных действия выглядят как два
             * разных элемента.
             */
            /*
             * Пустое место под полоску журнала.
             *
             * В журнале здесь полоска, за которую вытягивается
             * клавиатура. В контактах тянуть нечего, но высоту нужно
             * занять — иначе панели различаются на 20 dp и нижний край
             * прыгает при переходе между экранами.
             */
            Spacer(Modifier.height(FlowUi.bottomPanelHandleHeight))

            Row(
                Modifier
                    .fillMaxWidth()
                    /*
                     * ВЫСОТА КАК В ЖУРНАЛЕ.
                     *
                     * Панель набирала высоту из поля поиска и своих
                     * отступов и выходила выше журнальной. При переходе
                     * между экранами нижний край подпрыгивал — мелочь,
                     * которую видно каждый раз.
                     *
                     * Общее значение лежит в FlowUi: менять его теперь
                     * можно только для обеих панелей сразу.
                     */
                    .height(FlowUi.panelRowHeight())
                    // Отступ по краям: раньше поле упиралось в границу
                    // панели, и они сливались в одну линию
                    .padding(horizontal = FlowUi.screenPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s)
            ) {
            OutlinedTextField(
                // Белое поле на серой панели — как клавиши и строка номера:
                // иначе в светлой теме поле сливается с панелью.
                // Разбор — в PanelColors
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PanelColors.key(),
                    unfocusedContainerColor = PanelColors.key(),
                    /*
                     * Рамка — только при вводе.
                     *
                     * Стандартная рамка поля была самым контрастным
                     * элементом панели: замер по снимкам — 3,5 : 1
                     * в светлой теме и 4,9 : 1 в тёмной, тогда как клавиши
                     * и поле номера отличаются от панели на 1,2–1,3.
                     * Поле и так видно по своему фону — как поле номера
                     * в журнале, у которого рамки нет вовсе. Рамка цвета
                     * primary появляется, когда в поле вводят: видно,
                     * куда идут буквы.
                     */
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    // Подсказка — тем же цветом, что «Номер» в журнале:
                    // читается, но бледнее введённого текста
                    focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline
                ),
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
                leadingIcon = { Icon(OgnelissIcons.Search, contentDescription = null) },
                trailingIcon = {
                    /*
                     * Очистка или фильтры — в одном месте.
                     *
                     * Пока в поле есть текст, нужнее очистка: человек
                     * ищет и хочет начать заново. Как только поле
                     * пусто — нужнее фильтры, потому что искать больше
                     * нечего, а отобрать книгу можно.
                     *
                     * Два значка рядом не поместились бы: поле стало бы
                     * уже, а подсказка обрезалась.
                     */
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                OgnelissIcons.Clear,
                                contentDescription = stringResource(R.string.empty_clear_search)
                            )
                        }
                    }
                },
                singleLine = true,
                // Поиск идёт по мере ввода, поэтому кнопка ввода
                // просто закрывает клавиатуру
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = OgnelissShapeTokens.dialKey,  // пилюля, как клавиши (1.5.3)
                // weight, а не fillMaxWidth: рядом стоит кнопка
                // фильтров, и поле должно оставить ей место
                modifier = Modifier.weight(1f)
            )

            /*
             * Кнопка фильтров.
             *
             * Тот же значок и та же метка, что в журнале: одинаковое
             * действие должно выглядеть одинаково на обоих экранах.
             *
             * Точка вместо числа — фильтр контактов всегда один,
             * считать нечего.
             */
            Box {
                // Та же кнопка с подписью, что на панели журнала:
                // иначе панели разной высоты. Разбор — в LabeledIconButton
                LabeledIconButton(
                    icon = OgnelissIcons.FilterList,
                    labelRes = R.string.filter_title,
                    onClick = onFilters,
                    tint = if (filter != ContactFilter.ALL) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    },
                    selected = filter != ContactFilter.ALL
                )

                if (filter != ContactFilter.ALL) {
                    Badge(Modifier.align(Alignment.TopEnd))
                }
            }
            }
        }
    }
}

/*
 * FilterChipFor удалён вместе с фишками.
 *
 * Фильтры переехали в шторку: пять фишек занимали две строки экрана
 * постоянно ради выбора, к которому почти всегда возвращаются
 * как «Все».
 */
