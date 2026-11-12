package com.ogneliss.dialer.ui.components


import com.ogneliss.dialer.ui.theme.ScaledSheetContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.FlowUi

/**
 * ШТОРКА ВЫБОРА ОДНОГО ИЗ НЕСКОЛЬКИХ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЕДИНОЕ ПРАВИЛО ДЛЯ ФИЛЬТРОВ
 * ══════════════════════════════════════════════════════════════════════════
 * В приложении два вида отбора, и вид должен читаться по устройству
 * экрана, а не по опыту:
 *
 *   • можно выбрать НЕСКОЛЬКО условий разом — фишки (FilterSheet
 *     в журнале). Фишка либо нажата, либо нет, и их видно все сразу;
 *
 *   • можно выбрать ТОЛЬКО ОДНО — эта шторка. Список с кружком
 *     показывает исключительность честно, а фишки намекали бы
 *     на возможность выбрать второе.
 *
 * До этого одиночный выбор был сделан тремя способами: шторкой
 * со списком в контактах и выпадающими меню в заметках и напоминаниях.
 * Меню при этом открывалось у кнопки фильтра и перекрывало сам список —
 * то, по чему человек и выбирает.
 *
 * ⚠️ Новый экран с одиночным отбором берёт этот компонент, а не пишет
 * своё меню. Иначе через месяц способов снова станет три.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleChoiceSheet(
    /** Пары «значение — строка подписи». Порядок задаёт экран. */
    options: List<Pair<T, Int>>,
    current: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    /** Заголовок шторки. По умолчанию — «Фильтры». */
    titleRes: Int = R.string.filter_title
) {
    /*
     * Шторка раскрывается сразу полностью.
     *
     * По умолчанию ModalBottomSheet открывается наполовину, и нижние
     * пункты приходится дотягивать — а их и так немного.
     */
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = listSheetContainerColor()) {
        ScaledSheetContent {
            Column(Modifier.padding(bottom = FlowUi.sheetBottomPadding)) {
                SheetTitle(stringResource(titleRes))

                /*
                 * Варианты — строками во всю ширину на белом, как записи
                 * журнала (1.6.1, автор: «фильтры сделаем как в журнале»):
                 * та же форма, что у фильтров журнала (ListSheetGroup).
                 */
                ListSheetGroup {
                    options.forEachIndexed { index, (value, labelRes) ->
                        SheetChoiceRow(
                            selected = current == value,
                            label = stringResource(labelRes),
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            }
                        )
                        if (index < options.lastIndex) SheetDivider()
                    }
                }
            }
        }
    }
}
