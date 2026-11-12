package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ogneliss.dialer.ui.theme.ScaledWindow
import com.ogneliss.dialer.ui.theme.Space

/**
 * ОКНО КАЛЕНДАРЯ И ЧАСОВ (2.4.0) — одно на всё приложение.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ СВОЁ ОКНО
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 12.11.2026: «не на все действует масштаб». Было два чужих окна:
 * • напоминание — системные календарь и часы Android: своя тема (по теме
 *   системы, а не приложения), свой акцент, масштаб приложения туда
 *   не доходит вовсе — это не Compose;
 * • день рождения — DatePickerDialog Material: рамку и кнопки он рисует
 *   сам, в отдельном окне с системной плотностью, — снаружи масштаб
 *   туда не передать (ScaledWindow оборачивал бы только календарь,
 *   а рамка с кнопками остались бы прежнего размера).
 *
 * Здесь рамка — наша: окно Dialog, внутри ScaledWindow, поверхность и
 * кнопки как у M3 DatePickerDialog (форма extraLarge, surfaceContainerHigh,
 * кнопки справа внизу). Календарь и часы Material — внутри, в масштабе
 * и теме приложения, как всё остальное.
 *
 * ── Ширина ─────────────────────────────────────────────────────────────────
 * Календарь Material не уже 360 dp. При масштабе больше 100 % на узком
 * экране окно уменьшается до ширины экрана целиком (ScaledWindow
 * с fitWidth) — иначе края календаря обрезались бы.
 */
@Composable
internal fun PickerDialog(
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    content: @Composable ColumnScope.() -> Unit
) {
    // Ширину задаёт содержимое (календарь — 360 dp), а не платформа:
    // окно платформы по умолчанию уже календаря на части телефонов
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ScaledWindow(fitWidth = PICKER_WIDTH) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(max = PICKER_WIDTH)
                    .heightIn(max = PICKER_MAX_HEIGHT)
            ) {
                Column(verticalArrangement = Arrangement.SpaceBetween) {
                    // Содержимое уступает место кнопкам на низком экране
                    Box(Modifier.weight(1f, fill = false)) {
                        Column(content = content)
                    }
                    Row(
                        Modifier
                            .align(Alignment.End)
                            .padding(end = Space.s, bottom = Space.s),
                        horizontalArrangement = Arrangement.spacedBy(Space.s)
                    ) {
                        TextButton(onClick = onDismissRequest) { Text(dismissLabel) }
                        TextButton(onClick = onConfirm) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}

/** Ширина календаря Material (DatePickerModalTokens.ContainerWidth). */
private val PICKER_WIDTH = 360.dp

/** Высота окна календаря Material (DatePickerModalTokens.ContainerHeight). */
private val PICKER_MAX_HEIGHT = 568.dp
