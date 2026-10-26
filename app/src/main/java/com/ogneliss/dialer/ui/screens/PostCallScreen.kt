package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.ui.components.ActionCard
import com.ogneliss.dialer.ui.components.CardAction
import com.ogneliss.dialer.ui.components.CardActionsRow
import com.ogneliss.dialer.ui.components.CardButtons
import com.ogneliss.dialer.ui.components.CardHeader
import com.ogneliss.dialer.ui.components.CardsOverlay
import com.ogneliss.dialer.ui.components.LocalTouchAnchor
import com.ogneliss.dialer.ui.components.SectionCaption
import com.ogneliss.dialer.ui.theme.NoteTeal
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
     * 1.8.0 — КАРТОЧКАМИ, как все окна заметок и напоминаний (автор:
     * «привести окна напоминаний и заметок к одному UI с остальным
     * приложением»). Было: окно посередине с полем, фишками и строкой
     * фишек-действий. Стало — стопка карточек:
     *   1. С кем и сколько: имя или номер, длительность («3 минуты»,
     *      «Вызов не состоялся»).
     *   2. Заметка, отметка «спам» / «мошенники», кнопки.
     *   3. Действия кругами, как у долгого нажатия: «В контакты»,
     *      «Через 30 мин», «Завтра», «Блок».
     */
    val duration = if (durationSec > 0) {
        // Склонение выбирает Android по правилам локали
        val m = (durationSec / 60).toInt()
        val sec = (durationSec % 60).toInt()
        if (m > 0) pluralStringResource(R.plurals.duration_minutes, m, m)
        else pluralStringResource(R.plurals.duration_seconds, sec, sec)
    } else stringResource(R.string.postcall_no_answer)

    val actions = buildList {
        if (!isKnownContact) {
            add(CardAction(OgnelissIcons.PersonAdd, stringResource(R.string.action_add_contact), onClick = onAddContact))
        }
        add(CardAction(OgnelissIcons.Alarm, stringResource(R.string.postcall_remind_30), onClick = { onRemind(30) }))
        add(CardAction(OgnelissIcons.Schedule, stringResource(R.string.postcall_remind_tomorrow), onClick = { onRemind(60 * 24) }))
        // Блокировка красная и здесь: цвет действия одинаков везде, где оно встречается
        add(CardAction(OgnelissIcons.Block, stringResource(R.string.action_block), onClick = onBlock, tint = BlockRed, danger = true))
    }

    CardsOverlay(anchorY = LocalTouchAnchor.current?.screenY, onDismiss = onDismiss) { close ->
        ActionCard {
            CardHeader(
                icon = OgnelissIcons.StickyNote2,
                accent = NoteTeal,
                title = displayName ?: PhoneFormatter.format(number),
                subtitle = duration
            )
        }
        ActionCard {
            Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.l)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.postcall_note_hint)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                    // Три строки: заметка после разговора — обычно фраза,
                    // а не два слова
                    minLines = 3
                )
                Spacer(Modifier.height(Space.m))
                SectionCaption(stringResource(R.string.spam_mark_section))
                Spacer(Modifier.height(Space.xs))
                /*
                 * ОТМЕТКА — ОДИН ВЫБОР, НЕ НАБОР.
                 *
                 * Номер либо спам, либо мошенники, либо ничего: разбор
                 * в SpamMarkEntity. Ставится сразу по нажатию, а не по
                 * «Сохранить»: человек только что положил трубку после
                 * спам-звонка — отметка должна сработать немедленно.
                 * Повторное нажатие снимает. FlowRow: на немецком и при
                 * крупном шрифте фишки не помещались в строку.
                 */
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs)
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
                                        OgnelissIcons.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(FlowUi.iconSizeInline)
                                    )
                                }
                            } else null,
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }
                // То же объяснение, что в карточке: при включённой блокировке
                // телефон перестанет принимать вызовы с этого номера
                Text(
                    stringResource(R.string.note_tags_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Очищает только текст: отметка ставится и снимается сама, по фишке
            CardButtons(
                confirmLabel = stringResource(R.string.nav_save),
                onConfirm = { onSaveNote(note, emptyList()); close() },
                dismissLabel = stringResource(R.string.nav_close),
                onDismiss = close,
                extra = if (note.isNotBlank()) {
                    { TextButton(onClick = { note = "" }) { Text(stringResource(R.string.action_clear)) } }
                } else null
            )
        }
        ActionCard {
            CardActionsRow(actions, close)
        }
    }
}
