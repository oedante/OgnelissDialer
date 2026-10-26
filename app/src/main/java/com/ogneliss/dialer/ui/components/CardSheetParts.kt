package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.Space

/*
 * ЧАСТИ ОКОН-КАРТОЧЕК (1.8.0).
 *
 * Автор: «надо глобально пересмотреть окна напоминаний и заметок и привести
 * их к одному UI с остальным приложением» — и про выбор SIM: «как при долгом
 * нажатии на карточку в журнале или контакт. Пользователям такое нравится».
 *
 * Было (1.2–1.7): окна посередине экрана (OgnelissDialog) с серыми
 * плитками-строками — вид, которого больше нигде в приложении нет.
 * Стало: та же стопка карточек, что у действий по долгому нажатию
 * (CardsOverlay) — экран размыт, у места нажатия белые карточки:
 *   • шапка — значок дела в светлом круге его цвета, что делаем, с кем;
 *   • выбор — строки без линий, нажимаются целиком (CardChoiceRow);
 *   • кнопки — «Отмена» текстом, главное действие — заливкой (CardButtons).
 * Цвет значка — цвет дела в журнале: напоминание — синий акцент,
 * заметка — бирюзовый (NoteTeal), звонок — зелёный.
 */

/** Шапка окна-карточек: значок дела, что делаем, с кем (если есть). */
@Composable
internal fun CardHeader(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String?
) {
    Row(
        Modifier.padding(Space.l),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(HEADER_BADGE)
                .clip(CircleShape)
                .background(accent.copy(alpha = HEADER_BADGE_ALPHA).compositeOver(OgnelissTheme.surfaces.card)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent)
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Строка выбора в карточке: подпись слева, значение справа (время
 * напоминания), значок — по желанию. Без линий, нажимается целиком.
 */
@Composable
internal fun CardChoiceRow(
    label: String,
    trailing: String?,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    /** Значение справа — акцентом: главное в строке (дата и время в выборе срока). */
    emphasizeTrailing: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = CHOICE_MIN)
            .padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.m))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing?.let {
            Text(
                it,
                style = if (emphasizeTrailing) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (emphasizeTrailing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Кнопки внизу карточки: дополнительные слева ([extra] — «Очистить»),
 * «Отмена» текстом, главное действие — заливкой справа, у большого пальца.
 */
@Composable
internal fun CardButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    extra: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.s, end = Space.l, top = Space.xs, bottom = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        extra?.invoke(this)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(dismissLabel) }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            shape = OgnelissShapeTokens.callButton
        ) { Text(confirmLabel) }
    }
}

private val HEADER_BADGE = 40.dp
/** Светлая заливка круга — как значки действий в карточках SIM. */
private const val HEADER_BADGE_ALPHA = 0.14f
/** Строка выбора — не ниже обязательных 48 dp, с запасом для пальца. */
private val CHOICE_MIN = 52.dp
