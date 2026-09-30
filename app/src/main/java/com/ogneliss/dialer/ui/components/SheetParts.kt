package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.FlowUi

/**
 * ГРУППА ДЕЙСТВИЙ В ШТОРКЕ — СКРУГЛЁННАЯ ПАНЕЛЬ, КАК В КАРТОЧКЕ.
 *
 * Шторки «Ещё» и долгого нажатия до 1.2.1 были длинными плоскими списками
 * со стандартными разделителями во всю ширину — единственное место
 * в приложении, где действия не собраны в панели. Теперь действия
 * сгруппированы по смыслу, каждая группа — панель того же вида, что
 * «Номера» или «Сведения» в карточке, строки внутри разделены общим
 * тонким разделителем от начала текста.
 *
 * Строки — в content, между ними — SheetDivider(). Слот, а не список
 * лямбд: список с составными лямбдами Kotlin выводит ненадёжно,
 * а слот — обычный для Compose способ.
 */
@Composable
fun SheetGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.screenPadding, vertical = 4.dp)
    ) {
        Column(content = content)
    }
}

/** Разделитель между строками группы — от начала текста, под значком не проходит. */
@Composable
fun SheetDivider() = OgnelissDivider(Modifier.padding(start = 56.dp))

/**
 * Строка действия в шторке.
 *
 * color — цвет значка и подписи: null — обычный, красный — для
 * необратимых действий, зелёный — для «снять блокировку».
 * supporting — вторая строка (например, заданный свой номер).
 */
@Composable
fun SheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color? = null,
    iconTint: Color? = null,
    supporting: String? = null
) {
    val textColor = color ?: MaterialTheme.colorScheme.onSurface
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = iconTint ?: color ?: MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(label, color = textColor) },
        supportingContent = supporting?.let {
            { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .clickable(onClick = onClick)
    )
}

/**
 * Строка выбора одного варианта из нескольких — для шторок фильтров.
 * Внутри SheetGroup, как строки действий: одна форма для всех шторок.
 */
@Composable
fun SheetChoiceRow(selected: Boolean, label: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { androidx.compose.material3.RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .clickable(onClick = onClick)
    )
}
