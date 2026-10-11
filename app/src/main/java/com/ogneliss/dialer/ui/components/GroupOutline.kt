package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * СТРОКА СПИСКА ВО ВСЮ ШИРИНУ (1.5.3, «сплошной список», макет B).
 *
 * Для экранов, где строки собраны из отдельных элементов LazyColumn
 * (быстрый набор, разрешения, закреплённые, статистика, списки блокировки,
 * поиск по настройкам, внешние определители): строка во всю ширину экрана,
 * между строками раздела — тонкая линия от начала текста ([dividerStart]),
 * над первой — нет (там полоса раздела или край экрана).
 *
 * В 1.5.1–1.5.2 (макет A) здесь была белая группа с рамкой на сером;
 * имя и место строки (GroupPosition) остались — меняется только вид.
 *
 * ── Почему подменяется surface ─────────────────────────────────────────────
 * ListItem красится в surface. Внутри строки surface = цвет строки
 * (surfaceContainerLow), и любой ListItem в ней одного цвета со списком
 * без правки каждого вызова — в тёмной теме это важно.
 */
@Composable
fun GroupedRow(
    position: GroupPosition,
    dividerStart: Dp = 56.dp,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow)
    ) {
        if (position == GroupPosition.MIDDLE || position == GroupPosition.LAST) {
            OgnelissDivider(Modifier.padding(start = dividerStart))
        }
        MaterialTheme(
            colorScheme = scheme.copy(surface = scheme.surfaceContainerLow),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
