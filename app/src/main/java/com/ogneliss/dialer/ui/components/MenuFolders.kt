package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space

/**
 * «ПАПКИ» В МЕНЮ РАЗДЕЛА — как «Папки» в меню Ogneliss Mail (2.2.1 — SMS,
 * 2.2.2 — журнал и контакты).
 *
 * Автор, 12.11.2026: «а что в панель уходит в журнале звонков и
 * в контактах?» — то же, что в почте и в SMS: список того, что можно
 * открыть в этом разделе. Журнал — пропущенные, входящие, исходящие…
 * (JournalFolder), контакты — избранные, частые, с заметкой… (ContactFilter).
 *
 * Без карточки: подпись раздела и строки прямо на странице меню. Строка —
 * значок, название и число (непрочитанные); открытая — заливкой, как
 * выбранный раздел нижней панели. [onEdit] — значок справа от подписи
 * (порядок и показ папок), как «настроить» у «Избранного» почты.
 */
@Composable
internal fun MenuFolderRows(
    title: String,
    folders: List<MenuFolder>,
    onEdit: (() -> Unit)? = null,
    editLabel: String? = null
) {
    Column(Modifier.fillMaxWidth()) {
        SectionBand(
            title,
            action = if (onEdit != null) {
                {
                    IconButton(onClick = onEdit) {
                        Icon(
                            OgnelissIcons.Settings,
                            contentDescription = editLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else null
        )
        Column(Modifier.padding(vertical = Space.xs)) {
            folders.forEach { folder -> FolderRow(folder) }
        }
    }
}

/** Папка меню: значок, название, число справа (0 — не показывается), открыта ли, действие. */
internal class MenuFolder(
    val icon: ImageVector,
    val label: String,
    val count: Int = 0,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun FolderRow(folder: MenuFolder) {
    val on = folder.selected
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xs)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(role = Role.Tab, onClick = folder.onClick)
            .semantics { selected = on }
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            folder.icon, contentDescription = null,
            tint = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Space.l))
        Text(
            folder.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (on) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f)
        )
        // Число — только то, что требует внимания (непрочитанные): всего строк человеку не нужно
        if (folder.count > 0) {
            Text(
                folder.count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Высота строки папки — как строки меню почты. */
private val ROW_HEIGHT = 52.dp
