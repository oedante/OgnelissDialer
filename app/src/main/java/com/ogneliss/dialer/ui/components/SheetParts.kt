package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.FlowUi

/**
 * ГРУППА ДЕЙСТВИЙ В ШТОРКЕ — СПЛОШНОЙ СПИСОК (1.5.3, макет B).
 *
 * Шторка белая, действия — строками во всю ширину без разделителей между
 * ними; группы отделены тонкой линией во всю ширину, над группой может быть
 * подпись раздела заглавными ([title]). Так меню читается как оглавление:
 * линия — граница смысла, а не каждой строки.
 *
 * [first] — первая группа шторки: линия над ней не нужна, если сверху
 * ничего нет (меню «Ещё»). В меню записи над первой группой шапка —
 * там линия отделяет её от шапки, [first] = false.
 *
 * История: 1.2.1–1.5.0 — скруглённые панели; 1.5.1–1.5.2 — белые группы
 * с рамкой на серой шторке (макет A).
 */
@Composable
fun SheetGroup(
    title: String? = null,
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        if (!first) {
            OgnelissDivider(Modifier.padding(vertical = Space.xs))
        }
        title?.let {
            SectionCaption(
                it,
                Modifier.padding(start = Space.xl, end = Space.l, top = Space.s, bottom = Space.xxs)
            )
        }
        Column(content = content)
    }
}

/**
 * Разделитель между строками группы шторки.
 *
 * С 1.5.3 (сплошной список) строки внутри группы линиями не делятся —
 * границы только между группами (SheetGroup). Функция оставлена, чтобы
 * не править все шторки: вызов ничего не рисует.
 */
@Composable
fun SheetDivider() = Unit

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
    supporting: String? = null,
    /**
     * Строка стоит в шторке сама по себе, вне SheetGroup (например,
     * красное «Очистить журнал» внизу). Тогда ей нужен тот же отступ
     * от края, что у панелей групп, — иначе её значок и подпись
     * прижимаются к краю шторки левее всех остальных строк.
     */
    standalone: Boolean = false
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
            .then(
                if (standalone) {
                    Modifier
                        .padding(horizontal = FlowUi.screenPadding, vertical = Space.xs)
                        // Отклик нажатия — в пределах скругления, как у панелей
                        .clip(MaterialTheme.shapes.large)
                } else Modifier
            )
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .clickable(onClick = onClick)
    )
}

/**
 * Строка шторки с переключателем (1.3.4, 04.10.2026).
 *
 * Для действий-«состояний»: «Отправлять вызовы на голосовую почту» было
 * обычной строкой — не видно, включено ли, и выключить было нельзя.
 * Нажатие по всей строке переключает; переключатель лишь показывает
 * состояние (onCheckedChange = null), чтобы TalkBack читал строку
 * одним элементом с состоянием.
 */
@Composable
fun SheetSwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(label) },
        trailingContent = {
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = onToggle
            )
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
