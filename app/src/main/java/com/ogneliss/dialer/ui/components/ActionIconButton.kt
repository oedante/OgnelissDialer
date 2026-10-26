package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.ogneliss.dialer.ui.theme.Dimens

/**
 * КНОПКА-ЗНАЧОК С ДОЛГИМ НАЖАТИЕМ (1.8.0).
 *
 * IconButton из Material долгого нажатия не умеет, а у кнопок звонка
 * и SMS оно теперь есть везде — выбор SIM (автор: «не на всех кнопках
 * звонка и сообщений можно выбрать сим-карту»). Трубка в строке журнала,
 * контакта, закреплённого, заметки, напоминания, конверт у номера — всё
 * это одна кнопка: 48 dp, круглая рябь, как у IconButton.
 *
 * [onLongClick] null — обычная кнопка (одна SIM: выбирать нечего).
 * [onLongClickLabel] — что скажет TalkBack о долгом нажатии: «Выбрать SIM».
 */
@Composable
fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null
) {
    Box(
        modifier
            .size(Dimens.MIN_TOUCH_TARGET)
            .clip(CircleShape)
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
            .semantics {
                role = Role.Button
                contentDescription?.let { this.contentDescription = it }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}
