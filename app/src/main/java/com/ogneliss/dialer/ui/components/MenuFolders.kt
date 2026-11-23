package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.FlowUi
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
 * Подпись раздела и под ней карточка со строками (2.7.5; было — строки
 * прямо на странице меню). Строка — значок, название и число
 * (непрочитанные); открытая — заливкой и цветом акцента. [onEdit] — значок справа от подписи
 * (порядок и показ папок), как «настроить» у «Избранного» почты.
 *
 * ── Плотнее и одинаково во всех разделах (2.4.0) ───────────────────────────
 * Автор, по снимкам меню журнала, SMS и контактов: «слишком много пустого
 * места и всегда разное расположение элементов». Подпись — MenuBand
 * постоянной высоты: значок «настроить» у SMS больше не сдвигает строки
 * вниз; строка папки — 48 dp (было 52).
 */
@Composable
internal fun MenuFolderRows(
    title: String,
    folders: List<MenuFolder>,
    onEdit: (() -> Unit)? = null,
    editLabel: String? = null
) {
    Column(Modifier.fillMaxWidth()) {
        MenuBand(
            title,
            action = if (onEdit != null) {
                {
                    IconButton(onClick = onEdit) {
                        // «Настроить папки» — ползунки, как «настроить избранное» в почте
                        // (2.7.4, автор: шестерёнку путали с «Настройками» внизу меню)
                        Icon(
                            OgnelissIcons.Tune,
                            contentDescription = editLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else null
        )
        OgnelissCard { folders.forEach { folder -> FolderRow(folder) } }
    }
}

/**
 * Подпись части меню («Папки», «Инструменты») — одной высоты, со значком
 * действия справа или без: части меню стоят на одних и тех же местах
 * во всех разделах.
 */
@Composable
internal fun MenuBand(title: String, action: (@Composable () -> Unit)? = null) {
    /*
     * Обычным регистром, как подписи групп во всём приложении (SectionCaption:
     * прописные — только в высококонтрастной теме). В 2.5.0 подпись меню
     * была прописной всегда, и автор заметил: «шрифт панели выглядит более
     * жирным, чем у всего остального приложения» — «ПАПКИ» и «ИНСТРУМЕНТЫ»
     * тем же начертанием, но прописными выглядят заметно тяжелее «Папок»
     * в настройках.
     */
    Row(
        Modifier
            .fillMaxWidth()
            .height(BAND_HEIGHT)
            // Подпись — вровень с текстом строк в карточке (поле карточки + поле строки)
            .padding(start = FlowUi.groupInset + Space.l, end = FlowUi.groupInset - Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionCaption(title, Modifier.weight(1f))
        action?.invoke()
    }
}

/** Высота подписи части меню — как у кнопки-значка справа (48 dp). */
private val BAND_HEIGHT = 48.dp

/** Папка меню: значок, название, число справа (0 — не показывается), открыта ли, действие. */
internal class MenuFolder(
    val icon: ImageVector,
    val label: String,
    val count: Int = 0,
    val selected: Boolean = false,
    /** Фото вместо значка («Мой контакт», 2.5.0); пусто — значок. */
    val photoUri: String? = null,
    val onClick: () -> Unit
)

/**
 * Строки действий меню («Инструменты», 2.5.0) — тем же видом, что папки:
 * значок, название, 48 dp. Было: белые плитки, каких больше нигде
 * в приложении нет (автор: «вид отличается от остального приложения»).
 */
@Composable
internal fun MenuActionRows(title: String, actions: List<MenuFolder>) {
    Column(Modifier.fillMaxWidth()) {
        MenuBand(title)
        OgnelissCard { actions.forEach { FolderRow(it, role = Role.Button) } }
    }
}

/**
 * «Приложение» — плитками: значок и подпись под ним, ровными долями по ширине
 * одной карточки (2.9.2, автор по снимку боковой панели по высоте содержимого:
 * «раздел Приложение я бы сделал панелями как раньше»). Как было в 2.5.0
 * (AppActionsRow), но в карточке — как остальные части меню с 2.7.5.
 *
 * В 2.7.4 плитки сменили на строки: «отличается размер текста и много пустого
 * пространства». Пустоты больше нет (панель — по высоте содержимого, EndDrawer),
 * а подпись — того же размера, что подписи кнопок нижней панели, к которой
 * меню и прилегает. Длинная подпись ужимается (FitText), а не рвётся.
 */
@Composable
internal fun MenuTileRow(title: String, actions: List<MenuFolder>) {
    Column(Modifier.fillMaxWidth()) {
        MenuBand(title)
        OgnelissCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.xs),
                verticalAlignment = Alignment.Top
            ) {
                actions.forEach { action ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Button, onClick = action.onClick)
                            .heightIn(min = TILE_MIN_HEIGHT)
                            .padding(horizontal = Space.xxs, vertical = Space.s),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Space.xs))
                        FitText(
                            action.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            // Две строки: «На главный / экран» при крупном шрифте
                            maxLines = 2,
                            minScale = 0.6f
                        )
                    }
                }
            }
        }
    }
}

/** Плитка «Приложения»: не ниже двух строк касания — значок и подпись. */
private val TILE_MIN_HEIGHT = 64.dp

/*
 * КАРТОЧКИ, КАК В НАСТРОЙКАХ (2.7.5, автор выбрал вариант «А» из трёх
 * снимков 17.11.2026). Было (2.5.0–2.7.4): строки прямо на белой панели,
 * 48 dp, bodyLarge, открытая папка — пилюлей. Автор: «не нравится
 * оформление боковой панели». Теперь части меню — белые карточки на
 * серо-голубой странице (EndDrawer), как группы настроек; строка 44 dp
 * и bodyMedium — как строки настроек, без пустоты; открытая папка —
 * заливкой во всю ширину карточки и цветом акцента.
 */
@Composable
private fun FolderRow(folder: MenuFolder, role: Role = Role.Tab) {
    val on = folder.selected
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(role = role, onClick = folder.onClick)
            .semantics { if (role == Role.Tab) selected = on }
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val photo = folder.photoUri?.takeIf { it.isNotBlank() }
        if (photo != null) RowAvatar(photo, size = PHOTO_SIZE)
        else Icon(
            folder.icon, contentDescription = null,
            tint = if (on) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ICON_SIZE)
        )
        Spacer(Modifier.width(Space.l))
        Text(
            folder.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (on) FontWeight.SemiBold else null,
            color = if (on) accent else MaterialTheme.colorScheme.onSurface,
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

/** Высота строки (2.7.5): 44 dp, как строки в карточках настроек (было 48, ещё раньше 52). */
private val ROW_HEIGHT = 44.dp

/** Значок строки — 22 dp: под bodyMedium 24 dp выглядели крупнее текста. */
private val ICON_SIZE = 22.dp

/** Фото на месте значка строки — размер значка. */
private val PHOTO_SIZE = ICON_SIZE
