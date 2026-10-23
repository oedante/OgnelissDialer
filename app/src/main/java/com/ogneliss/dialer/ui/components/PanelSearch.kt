package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.PanelReachesNavBar
import com.ogneliss.dialer.ui.theme.Space

/**
 * ПОИСК В НИЖНЕЙ ПАНЕЛИ (1.6.0, обзор интерфейса, П1 — вариант «Б»).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Кнопка «Поиск» в панели журнала и контактов. Касание — и строка кнопок
 * становится полем ввода с клавиатурой: там же, где был палец, на той же
 * высоте. Список над ним — результаты.
 *
 * В контактах это прежнее поле «Имя», только не на виду постоянно:
 * раньше оно занимало панель целиком, и для фильтров и переходов
 * оставалась одна кнопка. В журнале — поиск буквами по имени и заметке
 * (разбор у ContactSearch.searchText); цифрами по-прежнему ищет
 * клавиатура набора.
 *
 * ── Как закрыть ────────────────────────────────────────────────────────────
 * Стрелкой слева или «Назад». Закрытие очищает запрос: как и у клавиатуры
 * набора, иначе список остался бы отобран, а причины этому на экране
 * не было бы видно.
 *
 * Высота строки — как у строки кнопок (FlowUi.panelRowHeight): панель
 * не прыгает, когда одна сменяет другую.
 */
@Composable
fun PanelSearchRow(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Поле открывается для ввода — сразу с фокусом и клавиатурой
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(FlowUi.panelRowHeight())
            .padding(start = Space.xs, end = FlowUi.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        IconButton(onClick = onClose) {
            Icon(OgnelissIcons.ArrowBack, contentDescription = stringResource(R.string.search_close))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            // Как поле «Имя» прежней панели контактов: белое на панели,
            // рамка цвета акцента — только при вводе (разбор в ContactsBottomBar)
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PanelColors.key(),
                unfocusedContainerColor = PanelColors.key(),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline
            ),
            placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(OgnelissIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(OgnelissIcons.Clear, contentDescription = stringResource(R.string.empty_clear_search))
                    }
                }
            },
            singleLine = true,
            // Ищется по мере ввода — кнопка клавиатуры её только прячет
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = OgnelissShapeTokens.dialKey,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
    }
}

/**
 * Поиск отдельной панелью внизу — где своей нижней панели у экрана нет:
 * журнал на телефоне лёжа (кнопки там полосой справа, и поле в неё
 * не помещается). Вид — как у нижней панели: тот же цвет, скругление
 * и тень.
 */
@Composable
fun SearchPanel(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = PanelColors.panel(),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        shape = OgnelissShapeTokens.bottomPanel,
        modifier = modifier
    ) {
        PanelReachesNavBar()
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
        ) {
            Spacer(Modifier.height(Space.s))
            PanelSearchRow(value, onValueChange, hint, onClose)
            Spacer(Modifier.height(Space.s))
        }
    }
}
