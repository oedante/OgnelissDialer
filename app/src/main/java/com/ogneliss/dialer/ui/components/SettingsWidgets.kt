package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.Dimens

/**
 * ЭЛЕМЕНТЫ ЭКРАНОВ НАСТРОЕК.
 *
 * Все виджеты здесь построены с оглядкой на доступность:
 *
 * • Минимальная высота строки — 48 dp (Dimens.MIN_TOUCH_TARGET).
 *   Требование Material Design и WCAG. Меньшая цель промахивается
 *   при тряске, в перчатках и при треморе.
 *
 * • Нажатие обрабатывается на ВСЕЙ строке, а не только на переключателе.
 *   Попасть пальцем в Switch шириной 32 dp гораздо труднее, чем в строку.
 *
 * • У составных элементов задана `mergeDescendants` — TalkBack прочитает
 *   строку целиком одной фразой («Крупные кнопки, включено»), а не будет
 *   перебирать заголовок, описание и переключатель по отдельности.
 *
 * • Состояние переключателя объявлено через `stateDescription`, иначе
 *   незрячий пользователь услышит «переключатель», но не узнает, включён ли он.
 */

/** Карточка категории на главном экране настроек. */
@Composable
fun SettingsCategoryCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    val a11yLabel = stringResource(R.string.a11y_expand_category, title)

    OgnelissListRow(
        headline = title,
        supporting = summary,
        leading = {
            // contentDescription = null: иконка декоративна, смысл несёт заголовок.
            // Озвучивать её отдельно значило бы дублировать текст для TalkBack.
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailing = {
            Icon(OgnelissIcons.KeyboardArrowRight, contentDescription = null)
        },
        onClick = onClick,
        /*
         * Подпись для чтения с экрана объединяет строку целиком:
         * TalkBack иначе зачитал бы заголовок, описание и стрелку
         * тремя отдельными элементами.
         */
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = a11yLabel
            role = Role.Button
        }
    )
}

/**
 * Строка-переключатель.
 *
 * @param dangerous настройка с неочевидными последствиями — подсвечивается
 *        и требует подтверждения при ВКЛЮЧЕНИИ (выключить можно свободно:
 *        возврат к безопасному состоянию подтверждать незачем).
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    dangerous: Boolean = false,
    /** Значение отличается от исходного — точка после заголовка (1.5.8). */
    changed: Boolean = false,
    /** Значок слева, цветом акцента — как в настройках Ogneliss Mail (2.2.2). */
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit
) {
    val stateText = stringResource(
        if (checked) R.string.a11y_setting_on else R.string.a11y_setting_off
    )

    OgnelissRowLayout(
        headline = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (dangerous) {
                    Icon(
                        OgnelissIcons.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(FlowUi.iconSizeInline)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(title)
                if (changed) ChangedDot()
            }
        },
        supporting = summary?.let { { Text(it) } },
        leading = icon?.let { { SettingLeadingIcon(it) } },
        trailing = {
            // null: нажатие ловит вся строка, отдельная цель не нужна
            Switch(checked = checked, onCheckedChange = null)
        },
        /*
         * Заголовок — не текст, а строка со значком предупреждения
         * у опасных настроек; раскладка — общая (OgnelissRowLayout,
         * плотность как в почте, 1.8.4).
         */
        modifier = Modifier.toggleableRow(checked, stateText) { onChange(!checked) }
    )
}

/** Строка выбора: показывает текущее значение, открывает диалог. */
@Composable
fun SettingsChoiceRow(
    title: String,
    currentValue: String,
    /** Значение отличается от исходного — точка после заголовка (1.5.8). */
    changed: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    if (!changed) {
        OgnelissListRow(
            headline = title,
            supporting = currentValue,
            leading = icon?.let { { SettingLeadingIcon(it) } },
            onClick = onClick,
            modifier = Modifier.semantics(mergeDescendants = true) { role = Role.Button }
        )
        return
    }
    // С отметкой — заголовок строкой со значком, как у переключателя
    OgnelissRowLayout(
        headline = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title)
                ChangedDot()
            }
        },
        supporting = { Text(currentValue) },
        leading = icon?.let { { SettingLeadingIcon(it) } },
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
    )
}

/**
 * ОТМЕТКА «ИЗМЕНЕНО» (1.5.8, обзор интерфейса, У7).
 *
 * Точка цвета акцента после заголовка — у настройки, которая отличается
 * от исходного значения. Видно, что вернёт «Вернуть как было» внизу
 * раздела, и что в нём вообще трогали. Для TalkBack — слово «изменено»
 * в описании строки.
 */
@Composable
fun ChangedDot(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.a11y_setting_changed)
    Box(
        modifier
            .padding(start = Space.s)
            .size(CHANGED_DOT)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = label }
    )
}

private val CHANGED_DOT = 6.dp

/** Строка-действие: экспорт, очистка, переход. */
@Composable
fun SettingsActionRow(
    title: String,
    summary: String?,
    dangerous: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    OgnelissListRow(
        headline = title,
        supporting = summary,
        // У разрушительного действия и значок красный — как заголовок
        leading = icon?.let { { SettingLeadingIcon(it, if (dangerous) BlockRed else null) } },
        // Красный у разрушительных действий — тот же цвет, что
        // у блокировки: разбор в Color.kt
        headlineColor = if (dangerous) BlockRed else null,
        onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) { role = Role.Button }
    )
}

@Composable
fun DangerousSettingDialog(
    title: String,
    explanation: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    /*
     * Общий контейнер приложения.
     *
     * Значок предупреждения переехал из шапки в текст: у OgnelissDialog
     * отдельного места под значок нет, а терять его нельзя — это
     * подтверждение опасной настройки, и треугольник читается раньше
     * заголовка.
     */
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    OgnelissIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(Space.m))
                Text(explanation)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(stringResource(R.string.confirm_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_cancel)) }
        }
    )
}

/**
 * Модификатор строки-переключателя.
 * Вынесен отдельно, потому что комбинация toggleable + семантика
 * повторяется и легко ошибиться, потеряв озвучивание состояния.
 */
private fun Modifier.toggleableRow(
    checked: Boolean,
    stateText: String,
    onToggle: () -> Unit
): Modifier = this
    .clickable(onClick = onToggle)
    .semantics(mergeDescendants = true) {
        role = Role.Switch
        stateDescription = stateText
        toggleableState = if (checked)
            androidx.compose.ui.state.ToggleableState.On
        else androidx.compose.ui.state.ToggleableState.Off
    }

/**
 * Значок строки настроек — слева, цветом акцента, как `SettingsItem` почты
 * (2.2.2). Чисто украшение: смысл несёт заголовок, TalkBack значок не читает.
 */
@Composable
fun SettingLeadingIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color? = null) {
    Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.primary)
}
