package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.R

/**
 * РЕДАКТОР ЗАМЕТКИ И МЕТОК.
 *
 * Используется и на карточке абонента, и на экране после звонка,
 * чтобы поведение было одинаковым в обоих местах.
 *
 * Метки хранятся стабильными ключами ("spam", "bank"), а показываются
 * локализованно: смена языка не должна ломать уже проставленные метки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorDialog(
    initialNote: String,
    initialTags: Set<String>,
    onSave: (note: String, tags: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf(initialNote) }
    var tags by remember { mutableStateOf(initialTags) }

    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.subscriber_note),
        confirmButton = {
            TextButton(onClick = { onSave(note, tags) }) {
                Text(stringResource(R.string.nav_save))
            }
        },
        dismissButton = {
            Row {
                /*
                 * ОЧИСТКА ЗАМЕТКИ И МЕТОК РАЗОМ.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ЗАЧЕМ
                 * ══════════════════════════════════════════════════════════
                 * Чтобы убрать заметку, приходилось стирать текст
                 * посимвольно и снимать метки по одной. При пяти метках
                 * это шесть действий вместо одного.
                 *
                 * Заметка не вечна: человек пометил номер «перезвонить
                 * в понедельник», перезвонил — и пометка теперь только
                 * мешает, потому что показывается вместо имени.
                 *
                 * ── Почему без подтверждения ───────────────────────────────
                 * Кнопка не сохраняет: она очищает поля, а запись
                 * произойдёт только по «Сохранить». Передумав, человек
                 * закрывает окно — и прежняя заметка остаётся.
                 *
                 * Подтверждение здесь было бы лишним шагом ради
                 * действия, которое и так обратимо.
                 */
                if (note.isNotBlank() || tags.isNotEmpty()) {
                    TextButton(onClick = {
                        note = ""
                        tags = emptySet()
                    }) {
                        Text(stringResource(R.string.action_clear))
                    }
                }

                TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_cancel)) }
            }
        },
        /*
         * content, а не text.
         *
         * У OgnelissDialog параметр называется content: это не AlertDialog,
         * и имени text у него нет. Здесь стояло text = — код с таким вызовом
         * не собирается вовсе, ошибка вида «no parameter with name 'text'».
         * Проверки prebuild_check и audit такое не ловят: имена аргументов
         * они не сверяют.
         */
        content = {
            /*
             * Прокрутка по вертикали.
             *
             * При крупном системном шрифте содержимое перестаёт
             * помещаться, и кнопки внизу становятся недоступны:
             * система ограничивает высоту диалога, а он молча
             * обрезается.
             */
            Column(Modifier.verticalScroll(rememberScrollState())) {
                /*
                 * ПОЛЕ ЗАМЕТКИ В ОБЩЕМ СТИЛЕ.
                 *
                 * ── Что изменилось ─────────────────────────────────────────
                 * Скругление и подсказка взяты те же, что у поиска
                 * в контактах: это одно и то же по сути — поле ввода
                 * текста, и выглядеть они должны одинаково.
                 *
                 * Раньше здесь была подпись сверху (label), а в поиске —
                 * подсказка внутри (placeholder). Два разных решения
                 * для одной задачи на соседних экранах.
                 */
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.postcall_note_hint)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                    // Четыре строки: заметка о человеке — обычно фраза,
                    // а не два слова, и в трёх текст начинал
                    // прокручиваться внутри поля
                    minLines = 4
                )
                /*
                 * ══════════════════════════════════════════════════════════
                 * ФИШЕК «СПАМ» И «МОШЕННИКИ» ЗДЕСЬ БОЛЬШЕ НЕТ
                 * ══════════════════════════════════════════════════════════
                 * С 1.1.1 отметка живёт отдельно от заметки — разбор
                 * в SpamMarkEntity. Ставится она в карточке номера своей
                 * строкой, а не внутри редактора текста: это решение
                 * о звонках, а не приписка к заметке.
                 *
                 * Прочие метки, если они остались со старых версий,
                 * сохраняются как были: редактор их не показывает,
                 * но и не стирает.
                 */
            }
        }
    )
}
