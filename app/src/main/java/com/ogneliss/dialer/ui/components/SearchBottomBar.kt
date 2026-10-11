package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.PanelReachesNavBar
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens

/**
 * НИЖНЯЯ ПАНЕЛЬ С ПОИСКОМ ДЛЯ ЗАМЕТОК И НАПОМИНАНИЙ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ОТДЕЛЬНЫЙ КОМПОНЕНТ, А НЕ ContactsBottomBar
 * ══════════════════════════════════════════════════════════════════════════
 * Панель контактов несёт фильтр книги: значок, метку включённого отбора,
 * шторку. Заметкам и напоминаниям фильтровать нечего — там сам список
 * и есть отбор.
 *
 * Копировать панель с выключенным фильтром значило бы тащить мёртвый
 * код в два новых экрана, а параметр «показывать ли фильтр» превратил бы
 * один компонент в два, склеенных условием.
 *
 * Общее вынесено в размеры: высота ряда и место под полоску берутся
 * из тех же токенов FlowUi, поэтому все четыре панели одинаковой
 * высоты и нижний край не прыгает при перелистывании.
 *
 * ── Свайпы ─────────────────────────────────────────────────────────────────
 * Экраны идут каруселью: журнал → контакты → заметки → напоминания.
 * Влево — вперёд, вправо — назад, то же направление, что у системного
 * жеста «назад». Жест висит на панели, а не на всём экране: список
 * прокручивается вертикально, и горизонтальная составляющая пальца
 * при листании легко доходит до угла срабатывания.
 */
@Composable
fun SearchBottomBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    /** Следующий экран карусели. null — жест выключен настройкой. */
    onSwipeNext: (() -> Unit)? = null,
    /** Предыдущий экран карусели. */
    onSwipePrevious: (() -> Unit)? = null,
    /**
     * Кнопка фильтра справа от поиска.
     *
     * ── Зачем она здесь, а не в шапке ──────────────────────────────────────
     * Кроме собственной пользы она возвращает панели площадь, свободную
     * от поля ввода: поле перехватывает касания себе, и чем больше его
     * доля, тем меньше остаётся жесту перелистывания. Полоска сверху
     * тонкая, кнопка сбоку — крупная и в том месте, куда палец и так
     * тянется.
     *
     * null — кнопки нет, поле занимает всю ширину.
     */
    filterButton: (@Composable () -> Unit)? = null
) {
    Surface(
        // Цвет панели общий для четырёх экранов: разбор в PanelColors
        color = PanelColors.panel(),
        // Без тонального подъёма — разбор в BottomControlPanel
        tonalElevation = 0.dp,
        shape = OgnelissShapeTokens.bottomPanel,
        /*
         * ЖЕСТ — НА ВСЮ ПАНЕЛЬ, КАК В КОНТАКТАХ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ НЕ СРАБОТАЛА ПРЕДЫДУЩАЯ ПОПЫТКА
         * ══════════════════════════════════════════════════════════════════
         * Я решил, что поле поиска перехватывает касания себе, и перенёс
         * жест на полоску сверху — двадцать точек высоты. Попасть в неё
         * пальцем при движении вбок почти невозможно, и свайп перестал
         * работать совсем.
         *
         * При этом в контактах панель устроена так же — поле поиска
         * во всю ширину — и там жест висит на всей панели и работает.
         * То есть исходная догадка была неверной: поле ввода забирает
         * касание внутри себя, но края, отступы и кнопка фильтра
         * остаются свободными, и этого хватает.
         *
         * Делаем в точности как в контактах.
         */
        modifier = Modifier.panelSwipeNavigation(
            enabled = onSwipeNext != null || onSwipePrevious != null,
            onSwipeLeft = onSwipeNext,
            onSwipeRight = onSwipePrevious
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
             * ЖЕСТ ПЕРЕЛИСТЫВАНИЯ ЖИВЁТ НА ПОЛОСКЕ, А НЕ НА ВСЕЙ ПАНЕЛИ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ СВАЙП НЕ РАБОТАЛ
             * ══════════════════════════════════════════════════════════════
             * Жест висел на всей панели, но панель здесь почти целиком
             * занята полем поиска. Поле ввода перехватывает касания
             * себе — так и должно быть, иначе в нём нельзя было бы
             * выделять текст, — и до жеста ничего не доходило.
             *
             * В журнале этой беды нет: там под полоской клавиатура
             * и кнопки, а не поле ввода.
             *
             * Полоска сверху свободна и видна: она и раньше занимала
             * это место ради одинаковой высоты панелей, теперь ещё
             * и работает.
             *
             * ⚠️ Высоту полоски трогать нельзя: 20 dp — это и высота
             * панели вровень с журнальной, и площадь для попадания
             * пальцем.
             */
            // Пустое место под полоску журнала: без него панель ниже
            // журнальной на 20 dp, и край прыгает при перелистывании
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FlowUi.bottomPanelHandleHeight)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(FlowUi.panelRowHeight())
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
                    modifier = Modifier.weight(1f),
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(placeholder) },
                    leadingIcon = { Icon(OgnelissIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    OgnelissIcons.Clear,
                                    contentDescription = stringResource(
                                        R.string.empty_clear_search
                                    )
                                )
                            }
                        }
                    },
                    singleLine = true,
                    // Поиск идёт по мере ввода: кнопка ввода просто
                    // закрывает клавиатуру
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.medium  // 8 dp, как клавиши (строгий стиль, 1.5.1)
                )

                filterButton?.invoke()
            }
        }
    }
}
