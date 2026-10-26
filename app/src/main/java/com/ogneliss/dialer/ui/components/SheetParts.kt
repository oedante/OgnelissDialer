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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.LocalPageTone
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.PageTone
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.FlowUi

/**
 * ГРУППА ДЕЙСТВИЙ В ШТОРКЕ — КАК ГРУППА НАСТРОЕК (1.6.1).
 *
 * Автор, 23.10 (снимки меню «Ещё» и «Фильтры»): «панель „Ещё“ и „Фильтры“
 * изменил в соответствии с UI всего приложения». Было (1.5.3–1.6.0):
 * белая шторка, подписи групп заглавными и тонкие линии между группами —
 * единственное место приложения, где группы не были карточками. Настройки,
 * карточка контакта, «Вопросы и ответы» устроены иначе: серо-голубая
 * страница, скруглённая полоса с подписью, под ней белая карточка
 * со строками.
 *
 * Стало так же: шторка — цвета страницы настроек ([sheetContainerColor]),
 * группа — SettingsGroup с видом TINTED: полоса [title] и карточка;
 * без подписи — только карточка. Строки внутри разделены линией от начала
 * текста ([SheetDivider]).
 *
 * Это шторки действий («Ещё», меню карточки). Фильтры устроены как журнал —
 * [ListSheetGroup] (автор, 23.10: «фильтры сделаем как в журнале»).
 *
 * [first] оставлен ради совместимости вызовов: отступ над первой группой
 * теперь даёт сама полоса.
 *
 * История: 1.2.1–1.5.0 — скруглённые панели; 1.5.1–1.5.2 — белые группы
 * с рамкой на серой шторке (макет A); 1.5.3–1.6.0 — сплошной список.
 */
@Composable
fun SheetGroup(
    title: String? = null,
    @Suppress("UNUSED_PARAMETER") first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalPageTone provides PageTone.TINTED) {
        SettingsGroup(title = title, content = content)
    }
}

/** Фон шторки с группами — страница настроек (1.6.1): на ней видны белые карточки. */
@Composable
fun sheetContainerColor(): Color = OgnelissTheme.pageColor(PageTone.TINTED)

/**
 * ГРУППА ШТОРКИ ФИЛЬТРОВ — КАК РАЗДЕЛ ЖУРНАЛА (1.6.1).
 *
 * Автор, 23.10, по снимку «Фильтров» журнала: «Давай фильтры сделаем как
 * в журнале». Фильтры отбирают список — журнал, контакты, напоминания, —
 * поэтому и выглядят как список: белая страница ([listSheetContainerColor]),
 * группа — та же скруглённая полоса с подписью, что «СЕГОДНЯ» и «ВЧЕРА»
 * над записями («ТИП ВЫЗОВА», «ПЕРИОД»), содержимое — под ней прямо
 * на странице, без карточки; строки — во всю ширину, линии между ними
 * от начала текста ([SheetDivider]).
 *
 * Шторки действий («Ещё», меню карточки) — [SheetGroup], как настройки.
 */
@Composable
fun ListSheetGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalPageTone provides PageTone.LIST) {
        Column(Modifier.fillMaxWidth()) {
            title?.let { SectionBand(it) }
            content()
        }
    }
}

/** Фон шторки фильтров — страница журнала (1.6.1). */
@Composable
fun listSheetContainerColor(): Color = OgnelissTheme.pageColor(PageTone.LIST)

/**
 * Разделитель между строками группы шторки — от начала текста, после
 * значка (16 + 24 + 16 dp), как в настройках. С 1.5.3 по 1.6.0 строки
 * не делились (сплошной список), вызов ничего не рисовал.
 */
@Composable
fun SheetDivider() = RowDivider(Modifier.padding(start = SHEET_TEXT_START))

private val SHEET_TEXT_START = 56.dp

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
    standalone: Boolean = false,
    /** Долгое нажатие (1.8.0): у SMS в «Написать» — выбор SIM; null — только касание. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null
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
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickableCompat(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
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
