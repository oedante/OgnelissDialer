package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ActionCard
import com.ogneliss.dialer.ui.components.CardButtons
import com.ogneliss.dialer.ui.components.CardHeader
import com.ogneliss.dialer.ui.components.CardsOverlay
import com.ogneliss.dialer.ui.components.LocalTouchAnchor
import com.ogneliss.dialer.ui.theme.NoteTeal
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space

/**
 * РЕДАКТОР ЗАМЕТКИ И МЕТОК.
 *
 * Используется и на карточке абонента, и на экране после звонка,
 * чтобы поведение было одинаковым в обоих местах.
 *
 * Метки хранятся стабильными ключами ("spam", "bank"), а показываются
 * локализованно: смена языка не должна ломать уже проставленные метки.
 */
@Composable
fun NoteEditorDialog(
    initialNote: String,
    initialTags: Set<String>,
    onSave: (note: String, tags: Set<String>) -> Unit,
    onDismiss: () -> Unit,
    /** С кем заметка — второй строкой шапки (1.8.0); null — без неё. */
    displayName: String? = null
) {
    var note by remember { mutableStateOf(initialNote) }
    var tags by remember { mutableStateOf(initialTags) }

    /*
     * 1.8.0 — КАРТОЧКАМИ (автор: «привести окна напоминаний и заметок к одному
     * UI с остальным приложением»). Шапка — бирюзовый значок заметки, как
     * в журнале, и имя; поле — во всю ширину карточки; «Сохранить» —
     * заливкой справа внизу. Стопка поднимается над клавиатурой сама:
     * её высоту и место считает CardsOverlay по отступам окна.
     */
    CardsOverlay(anchorY = LocalTouchAnchor.current?.screenY, onDismiss = onDismiss) { close ->
        ActionCard {
            CardHeader(
                icon = OgnelissIcons.StickyNote2,
                accent = NoteTeal,
                title = stringResource(R.string.subscriber_note),
                subtitle = displayName
            )
        }
        ActionCard {
            /*
             * ПОЛЕ ЗАМЕТКИ В ОБЩЕМ СТИЛЕ.
             *
             * Скругление и подсказка — те же, что у поиска в контактах:
             * это одно и то же по сути — поле ввода текста. Четыре строки:
             * заметка о человеке — обычно фраза, а не два слова, и в трёх
             * текст начинал прокручиваться внутри поля.
             */
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.postcall_note_hint)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.l, end = Space.l, top = Space.l),
                minLines = 4
            )
            /*
             * ОЧИСТКА ЗАМЕТКИ И МЕТОК РАЗОМ — без подтверждения: кнопка
             * не сохраняет, а очищает поля; запись — только по «Сохранить».
             * Передумав, человек закрывает окно — прежняя заметка остаётся.
             *
             * ФИШЕК «СПАМ» И «МОШЕННИКИ» ЗДЕСЬ НЕТ (с 1.1.1): отметка живёт
             * отдельно от заметки (SpamMarkEntity). Прочие метки со старых
             * версий сохраняются как были: редактор их не показывает,
             * но и не стирает.
             */
            CardButtons(
                confirmLabel = stringResource(R.string.nav_save),
                onConfirm = { onSave(note, tags); close() },
                dismissLabel = stringResource(R.string.nav_cancel),
                onDismiss = close,
                extra = if (note.isNotBlank() || tags.isNotEmpty()) {
                    {
                        TextButton(onClick = {
                            note = ""
                            tags = emptySet()
                        }) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                } else null
            )
        }
    }
}
