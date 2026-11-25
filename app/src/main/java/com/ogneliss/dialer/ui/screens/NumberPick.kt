package com.ogneliss.dialer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.ui.UiState
import com.ogneliss.dialer.ui.conversation.NewMessage
import com.ogneliss.dialer.ui.conversation.NewMessageBar
import com.ogneliss.dialer.ui.conversation.NewMessageSuggestions
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * ВЫБОР НОМЕРА ДЛЯ НОВОЙ ЗАМЕТКИ ИЛИ НАПОМИНАНИЯ (2.9.11).
 *
 * В «Заметках» и «Напоминаниях» в центре панели стояла клавиатура набора —
 * действие журнала. Обзор интерфейса (25.11.2026), автор согласился: в центре —
 * главное действие раздела, как «Написать» в SMS и «Создать» в контактах.
 *
 * Заметка и напоминание привязаны к номеру, поэтому «Создать» сперва спрашивает
 * номер — тем же полем «Кому», что новое SMS (NewMessagePicker): поле на месте
 * панели, над клавиатурой; подсказки снизу вверх; пока ничего не набрано — с кем
 * недавно говорили (журнал вызовов). Дальше — то же окно, что из журнала:
 * заметка (NoteEditorDialog) или срок напоминания (ReminderDialog).
 */
@Stable
internal class NumberPick(open: Boolean = false, query: String = "") {
    var open by mutableStateOf(open)
        private set
    var query by mutableStateOf(query)

    fun start() { query = ""; open = true }
    fun close() { open = false; query = "" }

    companion object {
        val Saver = listSaver<NumberPick, Any>(
            save = { listOf(it.open, it.query) },
            restore = { NumberPick(it[0] as Boolean, it[1] as String) }
        )
    }
}

/** Выбор переживает поворот экрана, как поле «Кому» в SMS. */
@Composable
internal fun rememberNumberPick(): NumberPick = rememberSaveable(saver = NumberPick.Saver) { NumberPick() }

/** Контакт номера — для имени в окне заметки или напоминания. */
internal fun contactOfNumber(number: String, contacts: List<Contact>): Contact? {
    val e164 = PhoneFormatter.toE164(number)
    return contacts.firstOrNull { c -> c.numbers.any { it.e164 == e164 } }
}

/** Подсказки под запрос: контакты; пока выбор закрыт — пусто (не считать зря). */
@Composable
internal fun rememberPickMatches(pick: NumberPick, contacts: List<Contact>): List<NewMessage.Suggestion> =
    remember(pick.open, pick.query, contacts) {
        if (!pick.open) emptyList() else NewMessage.suggestions(pick.query, contacts)
    }

/** Поле «Кому» на месте нижней панели. */
@Composable
internal fun NumberPickBar(
    pick: NumberPick,
    matches: List<NewMessage.Suggestion>,
    goDescriptionRes: Int,
    onPick: (String) -> Unit
) {
    NewMessageBar(
        value = pick.query,
        onValueChange = { pick.query = it },
        canGo = NewMessage.target(pick.query, matches) != null,
        onGo = { NewMessage.target(pick.query, matches)?.let(onPick) },
        onClose = { pick.close() },
        goDescriptionRes = goDescriptionRes
    )
}

/** Подсказки над полем: недавние вызовы, контакты под запрос, набранный номер. */
@Composable
internal fun NumberPickList(
    pick: NumberPick,
    state: UiState,
    matches: List<NewMessage.Suggestion>,
    typedTitleRes: Int,
    typedIcon: ImageVector,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val recents = remember(pick.open, state.callLog, state.contacts) {
        if (!pick.open) emptyList()
        else NewMessage.recents(
            state.callLog.map { it.number },
            contactOf = { contactOfNumber(it, state.contacts) }
        )
    }
    NewMessageSuggestions(
        query = pick.query,
        suggestions = matches,
        recents = recents,
        onPick = onPick,
        modifier = modifier,
        typedTitleRes = typedTitleRes,
        typedIcon = typedIcon
    )
}
