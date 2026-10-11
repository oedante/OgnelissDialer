package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.ogneliss.dialer.ui.theme.FlowUi

/**
 * ДИАЛОГ В СТИЛЕ ПРИЛОЖЕНИЯ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ НЕ AlertDialog
 * ══════════════════════════════════════════════════════════════════════════
 * AlertDialog задаёт внутренние отступы жёстко: 24 dp по краям и столько
 * же между заголовком, текстом и кнопками. Снаружи их не изменить —
 * параметра нет.
 *
 * Это рассчитано на короткое сообщение вроде «Удалить файл?». У нас
 * в диалогах живут списки, поля ввода и метки: с такими отступами
 * содержимое занимает половину окна, а вторая половина — пустота.
 * Рядом со списками, где расстояния 4–12 dp, диалог выглядит
 * как другое приложение.
 *
 * ── Что здесь вместо ───────────────────────────────────────────────────────
 * Dialog + Surface: то же окно, но отступы задаём мы. Форма, цвет
 * и тень берутся из темы — спорить с системой без причины незачем.
 *
 * ── Первая попытка была обёрткой над AlertDialog ───────────────────────────
 * Я написал её и удалил: она не меняла отступы, потому что менять
 * их через AlertDialog нельзя. Комментарий обещал одно, код делал
 * другое — хуже, чем ничего.
 */
@Composable
fun OgnelissDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            // Без tonalElevation (1.4.0): с цветом surfaceContainerHigh
            // он ничего не менял, а читался как намерение подкрасить
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(FlowUi.sheetPadding)) {
                Text(
                    title,
                    /*
                     * titleMedium, а не headlineSmall.
                     *
                     * Заголовок диалога — не заголовок экрана. Крупный
                     * спорит с содержимым, ради которого диалог открыли,
                     * и съедает строку в маленьком окне.
                     */
                    style = MaterialTheme.typography.titleMedium
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.m, bottom = Space.s),
                    // 8 dp между элементами: как между карточками
                    // в списках, только чуть свободнее
                    verticalArrangement = Arrangement.spacedBy(Space.s)
                ) {
                    content()
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
