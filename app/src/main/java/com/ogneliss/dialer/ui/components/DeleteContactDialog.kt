package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons

/**
 * Подтверждение удаления контакта (1.3.4, 04.10.2026).
 *
 * Общее для карточки (меню «Ещё») и меню долгого нажатия — раньше диалог
 * жил только в карточке, и вызвать его было нечем.
 *
 * Удаление необратимо и затрагивает все аккаунты, поэтому текст объясняет
 * последствия, а треугольник предупреждения стоит в тексте (у OgnelissDialog
 * нет места под значок в шапке). Если контакт закреплён — отдельная строка:
 * он будет убран и из закреплённых (это делает DialerViewModel.deleteContact).
 */
@Composable
fun DeleteContactDialog(
    pinned: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.confirm_delete_contact_title),
        content = {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    OgnelissIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(Space.m))
                Column {
                    Text(stringResource(R.string.confirm_delete_contact_text))
                    if (pinned) {
                        Spacer(Modifier.height(Space.s))
                        Text(stringResource(R.string.confirm_delete_contact_pinned))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_delete_contact),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nav_cancel))
            }
        }
    )
}
