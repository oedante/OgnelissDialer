package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.ScaledSheetContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons

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
 * Поле поиска открывается внизу, над клавиатурой: в журнале поиск —
 * продолжение набора номера. 2.4.0 (автор): значков поиска и фильтра
 * в шапке больше нет — поиск пилюлей вверху «Меню», фильтры — папками
 * и строкой «Фильтры» там же. В шапке — название и «⋮».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionTopBar(
    title: String,
    /** Строка под названием: что отобрано (фильтр, папка); null — одной строкой. */
    subtitle: String? = null,
    /** Действия над списком — в «⋮»; пусто — кнопки нет. */
    menu: List<TopBarMenuItem> = emptyList()
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                /*
                 * Заголовок экрана — как на всех экранах (2.4.0, единый вид):
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
            if (menu.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(OgnelissIcons.MoreVert, contentDescription = stringResource(R.string.topbar_list_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        ScaledSheetContent {
                            menu.forEach { item ->
                                val color = if (item.destructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                                // Пункт меню — как строка меню «Папки» и меню почты: bodyLarge, значок
                                // onSurfaceVariant, необратимое — цветом ошибки (2.4.0)
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
