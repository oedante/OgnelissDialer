package com.ogneliss.dialer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.Space

/**
 * ОКНО IMEI ПОСЛЕ *#06# (2.9.5, автор: «сервисные коды не работают»).
 *
 * Как у системной звонилки: набрал *#06# — окно с IMEI каждой SIM. Текст
 * выделяется, «Копировать» кладёт все IMEI в буфер (человек сам попросил —
 * например, чтобы вписать в гарантийный талон).
 *
 * [imeis] пустой — Android IMEI не отдал (приложение не SMS по умолчанию:
 * с Android 10 читать IMEI можно только системным и SMS по умолчанию). Тогда —
 * где его посмотреть и кнопка «О телефоне». Это ответ на действие человека,
 * а не сообщение об ошибке.
 */
@Composable
internal fun ImeiDialog(imeis: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.imei_title),
        content = {
            if (imeis.isEmpty()) {
                Text(stringResource(R.string.imei_unavailable))
            } else {
                // Номер SIM — подписью, IMEI — крупно и выделяемо, как в системном окне
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        imeis.forEachIndexed { i, imei ->
                            Column {
                                if (imeis.size > 1) {
                                    Text(
                                        stringResource(R.string.imei_sim, i + 1),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(imei, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (imeis.isEmpty()) {
                TextButton(onClick = {
                    onDismiss()
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(stringResource(R.string.imei_open_about)) }
            } else {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("IMEI", imeis.joinToString("\n")))
                    onDismiss()
                }) { Text(stringResource(R.string.action_copy)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_close)) }
        }
    )
}
