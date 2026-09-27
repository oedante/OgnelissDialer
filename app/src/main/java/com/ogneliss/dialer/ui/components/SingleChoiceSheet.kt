package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = FlowUi.sheetBottomPadding)) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    horizontal = FlowUi.sheetPadding,
                    vertical = 8.dp
                )
            )

            options.forEach { (value, labelRes) ->
                ListItem(
                    headlineContent = { Text(stringResource(labelRes)) },
                    leadingContent = {
                        RadioButton(
                            selected = current == value,
                            // null: нажатие обрабатывает вся строка —
                            // попасть в неё проще, чем в кружок
                            onClick = null
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = FlowUi.rowHeight)
                        .padding(horizontal = FlowUi.sheetPadding - 16.dp)
                        .clickable {
                            onSelect(value)
                            onDismiss()
                        }
                )
            }
        }
    }
}
