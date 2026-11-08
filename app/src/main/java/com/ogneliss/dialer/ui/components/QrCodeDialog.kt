package com.ogneliss.dialer.ui.components


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.util.ContactQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ОКНО С QR-КОДОМ КОНТАКТА.
 *
 * ── Зачем ──────────────────────────────────────────────────────────────────
 * Поделиться номером при встрече: собеседник наводит камеру и получает
 * готовый контакт. Быстрее, чем диктовать, и без ошибок в цифрах.
 *
 * ── Почему код строится в фоне ─────────────────────────────────────────────
 * Кодирование и отрисовка 640×640 занимают десятки миллисекунд —
 * в главном потоке это заметный рывок при открытии окна.
 */
@Composable
fun QrCodeDialog(
    name: String,
    numbers: List<String>,
    /** Почта из карточки: попадает в визитку, если она есть. */
    emails: List<String> = emptyList(),
    onDismiss: () -> Unit
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, name, numbers, emails) {
        value = withContext(Dispatchers.Default) {
            ContactQr.render(ContactQr.vCard(name, numbers, emails))
        }
    }

    // Контейнер общий для всех окон приложения: у AlertDialog отступы
    // фиксированные и рядом с остальным выглядели чужими
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = name,
        content = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 2.1.0: без подсказки «Наведите камеру…» — правило автора
                // 07.11.2026, пояснений не показываем; код говорит сам
                val image = bitmap
                if (image != null) {
                    /*
                     * Код рисуется на белом независимо от темы.
                     *
                     * Инвертированный — светлый по тёмному — камеры
                     * читают заметно хуже: многие ищут именно тёмные
                     * модули на светлом фоне.
                     */
                    Surface(color = androidx.compose.ui.graphics.Color.White) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = stringResource(R.string.qr_content_description),
                            modifier = Modifier.size(240.dp)
                        )
                    }
                } else {
                    // Пока код строится — место под него, чтобы окно
                    // не прыгало в размере
                    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nav_close))
            }
        }
    )
}
