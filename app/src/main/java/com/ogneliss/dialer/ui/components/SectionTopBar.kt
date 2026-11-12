package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space

/**
 * ШАПКА РАЗДЕЛА — КАК ШАПКА ЛЕНТЫ В OGNELISS MAIL (2.2.1).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 11.11.2026: «за референс UI возьмём Ogneliss Mail… хочется, чтобы
 * эти два приложения были полностью похожи по UI и организации элементов
 * и данных». В почте над лентой — шапка: слева название открытой папки
 * и строка под ним, справа — поиск, фильтр и «⋮» с действиями над списком;
 * меню с папками — отдельно, нижней кнопкой «Меню».
 *
 * В диалере шапки не было с 1.6.0 («заголовок отвечал на вопрос, который
 * возникает редко»): поиск, фильтры и действия над списком лежали плитками
 * в «Ещё» вперемешку с настройками и заметками. Теперь раскладка та же,
 * что в почте:
 * • шапка — что открыто (раздел, папка SMS) и действия над ЭТИМ списком;
 * • «Меню» внизу — куда перейти (папки, инструменты, настройки).
 *
 * ── Что не как в почте ─────────────────────────────────────────────────────
 * Поле поиска открывается внизу, над клавиатурой (как и было): в журнале
 * поиск — продолжение набора номера, и поле у клавиатуры здесь важнее
 * единообразия. Значок поиска — в шапке, на том же месте, что в почте.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionTopBar(
    title: String,
    /** Строка под названием: что отобрано (фильтр, папка); null — одной строкой. */
    subtitle: String? = null,
    onSearch: (() -> Unit)? = null,
    onFilter: (() -> Unit)? = null,
    /** Фильтр включён — точка на значке: список неполный. */
    filterActive: Boolean = false,
    /** Действия над списком — в «⋮»; пусто — кнопки нет. */
    menu: List<TopBarMenuItem> = emptyList()
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                /*
                 * Заголовок экрана — как на всех экранах (2.3.1, единый вид):
                 * одной строкой — titleLarge, как «Настройки»; с подписью ниже —
                 * titleMedium (две строки в той же высоте шапки), как
                 * в шапке ленты почты. Было: всегда titleMedium — «Журнал»
                 * выглядел мельче соседних «Настройки» и «Архив».
                 */
                Text(
                    title,
                    style = if (subtitle == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(OgnelissIcons.Search, contentDescription = stringResource(R.string.nav_search))
                }
            }
            if (onFilter != null) {
                Box {
                    IconButton(onClick = onFilter) {
                        Icon(OgnelissIcons.FilterList, contentDescription = stringResource(R.string.filter_title))
                    }
                    if (filterActive) Badge(Modifier.align(Alignment.TopEnd).padding(top = Space.s, end = Space.s))
                }
            }
            if (menu.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(OgnelissIcons.MoreVert, contentDescription = stringResource(R.string.topbar_list_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        menu.forEach { item ->
                            val color = if (item.destructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                            // Пункт меню — как строка меню «Папки» и меню почты: bodyLarge, значок
                            // onSurfaceVariant, необратимое — цветом ошибки (2.3.1)
                            DropdownMenuItem(
                                text = { Text(item.label, color = color, style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = {
                                    Icon(
                                        item.icon, contentDescription = null,
                                        tint = if (item.destructive) color else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = { menuOpen = false; item.onClick() },
                                modifier = Modifier.heightIn(min = FlowUi.menuItemHeight)
                            )
                        }
                    }
                }
            }
        },
        // Высота шапки почты — 56 dp (у Material 3 по умолчанию 64)
        expandedHeight = BAR_HEIGHT,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

/** Пункт «⋮» шапки: действие над списком раздела. */
internal class TopBarMenuItem(
    val icon: ImageVector,
    val label: String,
    /** Необратимое (очистить журнал) — цветом ошибки. */
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/** Высота шапки — как AppBar почты. */
private val BAR_HEIGHT = 56.dp
