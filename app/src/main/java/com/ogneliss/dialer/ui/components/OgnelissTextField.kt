package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import com.ogneliss.dialer.ui.theme.Space

/**
 * ПОЛЕ ВВОДА — ОДНО НА ВСЁ ПРИЛОЖЕНИЕ (2.7.0, обзор вида 15.11.2026).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО БЫЛО
 * ══════════════════════════════════════════════════════════════════════════
 * Два вида поля. Поиск (контакты, журнал, заметки, «Кому») и строка SMS —
 * заливка без рамки, рамка цвета акцента только при вводе. Остальные
 * одиннадцать полей в восьми местах (редактор контакта, заметка, правило
 * блокировки, названия SIM, свой номер, быстрый набор, поиск языка, окно
 * после звонка) — стандартная рамка Material: тёмная линия, самый
 * контрастный элемент экрана. Автор: «ощущение, что приложение сделано
 * из разных кусков».
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК ТЕПЕРЬ
 * ══════════════════════════════════════════════════════════════════════════
 * Везде — заливка без рамки: поле видно по своему фону (как строка SMS),
 * при вводе — рамка цвета акцента: видно, куда идут буквы. Однострочное —
 * пилюля, как клавиши и поиск; многострочное (заметка) — скруглённый
 * прямоугольник: у пилюли в четыре строки текст упирался бы в дуги.
 * Подпись [label] — внутри поля: пока пусто — на месте текста, при вводе —
 * мелко над ним.
 *
 * [container] — цвет заливки: на странице и в окнах — общий; на нижней
 * панели — цвет клавиши (PanelColors.key), иначе поле слилось бы с панелью.
 *
 * Проверка: scripts/ui_roles_check.py, правило 8 — OutlinedTextField
 * и TextField напрямую только здесь, в строке SMS и в поиске настроек.
 */
@Composable
fun OgnelissTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    /** Предел строк у многострочного поля (заметка во время разговора — 6). */
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    /** Пояснение под полем (кто эта SIM): мелко, вне заливки. */
    supportingText: String? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = if (singleLine) OgnelissShapeTokens.dialKey else MaterialTheme.shapes.large
    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.border(
                FOCUS_BORDER,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape
            ),
            label = label?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            maxLines = if (singleLine) 1 else maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = container,
                unfocusedContainerColor = container,
                disabledContainerColor = container,
                // Линии под текстом нет: поле — заливкой, ввод — рамкой
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                // Подсказка — бледнее введённого текста, как «Номер» в журнале
                focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
    if (supportingText == null) {
        field(modifier)
    } else {
        Column(modifier) {
            field(Modifier.fillMaxWidth())
            Text(
                supportingText,
                Modifier.padding(start = Space.l, top = Space.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Рамка поля при вводе. */
private val FOCUS_BORDER = 1.5.dp
