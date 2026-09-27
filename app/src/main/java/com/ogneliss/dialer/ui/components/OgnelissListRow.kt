package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ogneliss.dialer.ui.theme.FlowUi

/**
 * СТРОКА СПИСКА.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОБЁРТКА НАД ListItem
 * ══════════════════════════════════════════════════════════════════════════
 * Подсчёт по дереву ui/ на 17 сентября 2026: тридцать применений
 * `ListItem`, из них минимальная высота задана у семнадцати,
 * а цвет подложки — у пяти. То есть больше трети строк в приложении
 * живут на умолчаниях Material, а остальные — на наших значениях.
 *
 * Разница в высоте между экранами невелика, но она есть, и при переходе
 * между списками глаз её ловит: строки то плотнее, то свободнее.
 *
 * ── Почему обёртка, а не свой компонент с нуля ─────────────────────────────
 * `ListItem` делает то, что нужно: раскладывает значок, заголовок,
 * подпись и хвост по правилам Material, знает про TalkBack и ширину
 * экрана. Переписывать это — значит повторять чужую работу и получать
 * свои ошибки.
 *
 * Здесь задаются только те три вещи, в которых был разнобой: высота,
 * прозрачная подложка и нажатие на всю строку.
 *
 * ⚠️ Прозрачная подложка не случайна. Строки почти всегда лежат внутри
 * `OgnelissPanel`, у которой свой цвет; своя подложка у строки дала бы
 * второй оттенок поверх первого — на тёмной теме это видно сразу.
 */
@Composable
fun OgnelissListRow(
    headline: String,
    modifier: Modifier = Modifier,
    /** Подпись под заголовком. Пусто — строка в одну строку. */
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /**
     * Цвет заголовка.
     *
     * Нужен разрушительным действиям (красный) и снимающим их
     * (зелёный) — разбор цветов в Color.kt рядом с BlockRed.
     */
    headlineColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = {
            Text(
                headline,
                color = headlineColor ?: MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = supporting?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = leading,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .heightIn(min = FlowUi.rowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}
