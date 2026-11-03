package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.Space

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
    OgnelissRowLayout(
        headline = {
            Text(
                headline,
                color = headlineColor ?: MaterialTheme.colorScheme.onSurface
            )
        },
        supporting = supporting?.let { { Text(it) } },
        leading = leading,
        trailing = trailing,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

/**
 * РАСКЛАДКА СТРОКИ — ПЛОТНОСТЬ КАК В OGNELISS MAIL (1.8.4).
 *
 * Было: строки на ListItem из Material — у двухстрочной он держит не меньше
 * 72 dp, сколько бы ни занимал текст. Автор сравнил настройки с почтой
 * («отличаются… давай подгоним»): у почты (ListTile с minTileHeight 48)
 * те же строки на треть ниже, у диалера — заметно рыхлее.
 *
 * Стало: те же места, что у ListItem (значок, заголовок, подпись, хвост),
 * те же стили и цвета, но высота — по тексту: поля 16 по бокам и 6 сверху
 * и снизу, не ниже 48 dp — цели касания. Двухстрочная строка — 56 dp.
 * В высококонтрастной теме — поля 10: там плотность не сжимаем, как в почте.
 */
@Composable
fun OgnelissRowLayout(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val vertical = if (OgnelissTheme.surfaces.outlined) 10.dp else ROW_VERTICAL_PADDING
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = Space.l, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.let {
            Box(Modifier.padding(end = Space.l), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { it() }
            }
        }
        Column(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) { headline() }
            }
            supporting?.let {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                }
            }
        }
        trailing?.let {
            Box(Modifier.padding(start = Space.l), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { it() }
            }
        }
    }
}

/** Не ниже цели касания (почта: minTileHeight 48). */
private val ROW_MIN_HEIGHT = 48.dp

/** Поля сверху и снизу: двухстрочная строка — 56 dp (почта ~ 52–56). */
private val ROW_VERTICAL_PADDING = 6.dp
