package com.ogneliss.dialer.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AssistChipDefaults
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * Экран после завершения вызова.
 *
 * Появляется НЕ всегда: только для номеров вне контактов или если
 * пользователь включил его для всех. Навязчивый экран после каждого звонка —
 * частая причина удаления сторонних диалеров, поэтому по умолчанию
 * он показывается 4 секунды и закрывается сам.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCallSheet(
    number: String,
    displayName: String?,
    durationSec: Long,
    isKnownContact: Boolean,
    onSaveNote: (note: String, tags: List<String>) -> Unit,
    /**
     * Текущая отметка номера и её смена.
     *
     * Отметка ставится сразу по нажатию, а не вместе с заметкой по кнопке
     * «Сохранить»: с 1.1.1 это разные вещи. Человек только что положил
     * трубку после спам-звонка — отметка должна сработать немедленно,
     * даже если заметку он писать не станет и окно закроется само.
     */
    currentMark: SpamMarkType? = null,
    onMark: (SpamMarkType?) -> Unit = {},
    onAddContact: () -> Unit,
    onRemind: (minutes: Long) -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    // Состояние локальное: заметка сохраняется по кнопке, а не посимвольно.
    // Запись в базу на каждый символ дала бы десятки обращений к диску
    // сразу после разговора, когда система и так занята
    var note by remember { mutableStateOf("") }

    /*
     * Общий контейнер приложения вместо AlertDialog.
     *
     * Заголовок у OgnelissDialog — строка, поэтому длительность
     * разговора, стоявшая второй строкой заголовка, переехала
     * в начало содержимого. Смысл тот же: сначала «с кем говорили»,
     * потом «сколько», потом поле заметки.
     */
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = displayName ?: PhoneFormatter.format(number),
        confirmButton = {
            TextButton(onClick = { onSaveNote(note, emptyList()); onDismiss() }) {
                Text(stringResource(R.string.nav_save))
            }
        },
        dismissButton = {
            Row {
                /*
                 * Очистка — как в окне заметки из карточки.
                 *
                 * Здесь она нужна реже: окно открывается пустым после
                 * разговора. Но человек мог начать писать и передумать,
                 * а стирать посимвольно долго.
                 *
                 * Появляется только когда есть что убирать: пустая
                 * кнопка сбивала бы с толку.
                 */
                // Очищает только текст: отметка ставится и снимается
                // сама, по нажатию фишки, и стирать её отсюда нельзя
                if (note.isNotBlank()) {
                    TextButton(onClick = {
                        note = ""
                    }) {
                        Text(stringResource(R.string.action_clear))
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.nav_close))
                }
            }
        },
        content = {
            // Длительность разговора: была второй строкой заголовка
            Text(
                if (durationSec > 0) {
                    // Склонение выбирает Android по правилам локали
                    val m = (durationSec / 60).toInt()
                    val s = (durationSec % 60).toInt()
                    if (m > 0) pluralStringResource(R.plurals.duration_minutes, m, m)
                    else pluralStringResource(R.plurals.duration_seconds, s, s)
                } else stringResource(R.string.postcall_no_answer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )

            /*
             * Прокрутка по вертикали.
             *
             * При включённой крупной системной шрифтовой настройке
             * содержимое перестаёт помещаться, и без прокрутки нижние
             * кнопки становятся недоступны. Диалог не растягивается
             * бесконечно — система ограничивает его высоту.
             */
            /*
             * heightIn добавлен вместе с переходом на OgnelissDialog.
             *
             * AlertDialog ограничивал высоту сам, наш контейнер —
             * нет: он ровно такой, как содержимое. Без потолка при
             * крупном системном шрифте кнопки «Сохранить» и «Закрыть»
             * ушли бы за нижний край.
             */
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.postcall_note_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    // Три строки вместо двух: заметка после разговора —
                    // это обычно фраза, а не два слова, и в двух строках
                    // текст сразу начинал прокручиваться внутри поля
                    minLines = 3
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    stringResource(R.string.spam_mark_section),
                    style = MaterialTheme.typography.labelMedium
                )

                /*
                 * ОТМЕТКА — ОДИН ВЫБОР, НЕ НАБОР.
                 *
                 * Номер либо спам, либо мошенники, либо ничего: разбор
                 * в SpamMarkEntity. Повторное нажатие на выбранную фишку
                 * снимает отметку — так же, как было с метками.
                 *
                 * FlowRow, а не Row: на немецком («Betrug») и при крупном
                 * шрифте фишки не помещались в строку.
                 */
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        SpamMarkType.SPAM to R.string.tag_spam,
                        SpamMarkType.FRAUD to R.string.tag_fraud
                    ).forEach { (type, labelRes) ->
                        val selected = currentMark == type
                        FilterChip(
                            selected = selected,
                            onClick = { onMark(if (selected) null else type) },
                            // С заглавной — как в карточке и журнале
                            label = { Text(stringResource(labelRes).replaceFirstChar { it.titlecase() }) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        Icons.Default.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(FlowUi.iconSizeInline)
                                    )
                                }
                            } else null,
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                /*
                 * То же объяснение, что в карточке.
                 *
                 * Здесь оно нужнее: человек только что положил трубку,
                 * раздражён спам-звонком и жмёт отметку не глядя. Он должен
                 * знать, что при включённой блокировке телефон перестанет
                 * принимать вызовы с этого номера.
                 */
                Text(
                    stringResource(R.string.note_tags_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // Действия тоже переносятся: подписи бывают длинными,
                // и «Заблокировать номер» рядом с «В контакты» за край
                // уходило целиком
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isKnownContact) {
                        AssistChip(onClick = onAddContact, label = { Text(stringResource(R.string.action_add_contact)) })
                        Spacer(Modifier.width(8.dp))
                    }
                    AssistChip(onClick = { onRemind(30) }, label = { Text(stringResource(R.string.postcall_remind_30)) })
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = { onRemind(60 * 24) }, label = { Text(stringResource(R.string.postcall_remind_tomorrow)) })
                    Spacer(Modifier.width(8.dp))
                    // Блокировка красная и здесь: цвет действия
                    // одинаков во всех местах, где оно встречается
                    AssistChip(
                        onClick = onBlock,
                        label = { Text(stringResource(R.string.action_block)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeInline),
                                tint = BlockRed
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(labelColor = BlockRed)
                    )
                }
            }
        }
    )
}
