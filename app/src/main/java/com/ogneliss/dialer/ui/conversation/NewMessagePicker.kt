package com.ogneliss.dialer.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.ui.components.RowAvatar
import com.ogneliss.dialer.ui.components.panelTopLine
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.LocalAppearance
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.PanelReachesNavBar
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.ui.theme.listName
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * НОВОЕ СООБЩЕНИЕ: КОМУ (2.1.3).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО БЫЛО НЕ ТАК
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 11.11.2026: «при составлении нового SMS появляется окно, в котором
 * предлагается ввести номер или имя. При наборе окно начинает „прыгать“…
 * окно поиска, кому написать, надо доделать и перенести ниже, ближе
 * к пальцам». Было окно посередине экрана: с каждой буквой под полем
 * появлялись и пропадали подсказки, окно меняло высоту и заново вставало
 * по центру — поле ввода уезжало из-под взгляда. Подсказок — не больше
 * шести, без прокрутки.
 *
 * ── Стало ──────────────────────────────────────────────────────────────────
 * Как поиск в журнале и контактах (PanelSearch): поле — на месте нижней
 * панели, над клавиатурой, и никуда не двигается. Подсказки — списком над
 * ним, снизу вверх: лучшая — вплотную к полю, у большого пальца. Пока
 * ничего не набрано — те, кому писали недавно. Набран номер — первой
 * строкой «Написать на …». Слева от поля — выход, в поле — переключатель
 * «буквы / цифры» (номер удобнее набирать цифровой клавиатурой), справа —
 * «Дальше» (активна, когда набран номер или подсказка одна).
 */
internal object NewMessage {

    /** Кому можно написать: имя (null — номера нет в книге), номер и подпись номера. */
    data class Suggestion(
        val name: String?,
        val number: String,
        val label: String,
        val photoUri: String?
    ) {
        val key: String get() = (name ?: "") + "|" + number
    }

    /** Больше строк в списке не нужно: дальше уточняют запрос. */
    const val LIMIT = 30

    /** Недавних — столько: помещаются над клавиатурой без прокрутки. */
    const val RECENT_LIMIT = 6

    /** Набран номер (не меньше трёх цифр, только знаки номера) — по нему можно написать. */
    fun typedNumber(text: String): String? =
        text.trim().takeIf { t -> t.count(Char::isDigit) >= 3 && t.all { it.isDigit() || it in "+-() " } }

    /**
     * Контакты под запрос. По имени: сначала те, чьё имя с запроса
     * начинается, затем — где с него начинается слово, затем — где он внутри.
     * По номеру — от двух набранных цифр («а» иначе совпадала бы с каждым
     * номером, а одна цифра — с половиной книги); «8 918…» находит «+7 918…».
     * У совпавшего по имени — все номера, по номеру — только совпавшие.
     */
    fun suggestions(query: String, contacts: List<Contact>, limit: Int = LIMIT): List<Suggestion> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val digits = q.filter(Char::isDigit)
        // Цифры ищем в номере, только когда набирают номер, а не «152 Дарья»
        val byNumber = digits.length >= 2 && q.all { it.isDigit() || it in "+-() " }
        val alt = if (byNumber && digits.startsWith("8")) "7" + digits.drop(1) else null

        fun numberMatches(e164: String, raw: String): Boolean {
            val a = e164.filter(Char::isDigit)
            val b = raw.filter(Char::isDigit)
            return digits in a || digits in b || (alt != null && alt in a)
        }

        return contacts.asSequence()
            .filter { it.numbers.isNotEmpty() }
            .mapNotNull { contact ->
                val name = contact.displayName.lowercase()
                val rank = when {
                    name.startsWith(q) -> 0
                    name.split(' ', '-', '(', '«', '"').any { it.startsWith(q) } -> 1
                    q in name -> 2
                    else -> null
                }
                val numbers = when {
                    rank != null -> contact.numbers
                    byNumber -> contact.numbers.filter { numberMatches(it.e164, it.value) }
                    else -> emptyList()
                }
                if (numbers.isEmpty()) null else Triple(rank ?: 3, contact, numbers)
            }
            .sortedWith(compareBy({ it.first }, { it.second.displayName.lowercase() }))
            .flatMap { (_, contact, numbers) ->
                numbers.asSequence().map { Suggestion(contact.displayName, it.value, it.label, contact.photoUri) }
            }
            .take(limit)
            .toList()
    }

    /**
     * Те, кому писали недавно: адреса переписок от новых к старым. Буквенные
     * отправители («Bank») и короткие номера-рассылки не предлагаются —
     * написать им нельзя или незачем.
     */
    fun recents(
        addresses: List<String>,
        contactOf: (String) -> Contact?,
        limit: Int = RECENT_LIMIT
    ): List<Suggestion> {
        val seen = HashSet<String>()
        return addresses.asSequence()
            .filter { a -> a.count(Char::isDigit) >= 7 && a.all { it.isDigit() || it in "+-() " } }
            .filter { seen.add(PhoneFormatter.toE164(it).ifBlank { it }) }
            .map { address ->
                val contact = contactOf(address)
                val label = contact?.numbers
                    ?.firstOrNull { it.e164 == PhoneFormatter.toE164(address) }?.label.orEmpty()
                Suggestion(contact?.displayName, address, label, contact?.photoUri)
            }
            .take(limit)
            .toList()
    }

    /** Что откроет «Дальше»: набранный номер, иначе единственная подсказка. */
    fun target(query: String, shown: List<Suggestion>): String? =
        typedNumber(query) ?: shown.singleOrNull()?.number
}

/**
 * Подсказки над полем «Кому»: список снизу вверх — первая строка у поля.
 * [query] пуст — [recents]; набран номер — первой «Написать на …».
 */
@Composable
internal fun NewMessageSuggestions(
    query: String,
    suggestions: List<NewMessage.Suggestion>,
    recents: List<NewMessage.Suggestion>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val typed = NewMessage.typedNumber(query)
    val rows = if (query.isBlank()) recents else suggestions
    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        // Снизу вверх: при двух строках они стоят у поля, а не под потолком
        verticalArrangement = Arrangement.Bottom
    ) {
        if (typed != null) {
            item(key = "typed") {
                SuggestionRow(
                    title = stringResource(R.string.messages_new_to_number, PhoneFormatter.format(typed)),
                    subtitle = null,
                    onClick = { onPick(typed) }
                ) {
                    Box(
                        Modifier
                            .size(LocalAppearance.current.avatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            OgnelissIcons.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        items(rows, key = { it.key }) { row ->
            val number = PhoneFormatter.format(row.number)
            SuggestionRow(
                title = row.name ?: number,
                subtitle = listOf(number.takeIf { row.name != null }.orEmpty(), row.label)
                    .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                onClick = { onPick(row.number) }
            ) {
                RowAvatar(row.photoUri, name = row.name)
            }
        }
        if (query.isBlank() && recents.isNotEmpty()) {
            // В перевёрнутом списке последний элемент — верхний: подпись над недавними
            item(key = "recent_header") {
                Text(
                    stringResource(R.string.messages_new_recent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.l, vertical = Space.s)
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    avatar: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(
                horizontal = Space.l,
                vertical = LocalAppearance.current.rowPadding(OgnelissTheme.surfaces.outlined)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        avatar()
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.listName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Поле «Кому» на месте нижней панели. Высота и край — как у панели
 * разделов (ListPanel): при входе и выходе низ экрана не прыгает.
 */
@Composable
internal fun NewMessageBar(
    value: String,
    onValueChange: (String) -> Unit,
    /** «Дальше» есть куда: набран номер или подсказка одна. */
    canGo: Boolean,
    onGo: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Цифровая клавиатура — по кнопке в поле; выбор переживает поворот
    var digits by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Surface(
        color = PanelColors.panel(),
        tonalElevation = 0.dp,
        shape = OgnelissShapeTokens.bottomPanel,
        modifier = Modifier.panelTopLine(OgnelissTheme.surfaces.divider)
    ) {
        PanelReachesNavBar()
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
        ) {
            Spacer(Modifier.height(FlowUi.bottomPanelHandleHeight))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(FlowUi.panelRowHeight())
                    .padding(start = Space.xs, end = Space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                IconButton(onClick = onClose) {
                    Icon(OgnelissIcons.ArrowBack, contentDescription = stringResource(R.string.nav_close))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    // Как поле поиска панели (PanelSearchRow)
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PanelColors.key(),
                        unfocusedContainerColor = PanelColors.key(),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline
                    ),
                    placeholder = {
                        Text(stringResource(R.string.messages_new_hint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingIcon = {
                        // Подписью, а не значком: «123» и «АБВ» понятны без догадок
                        val toLabel = stringResource(
                            if (digits) R.string.messages_new_letters else R.string.messages_new_digits
                        )
                        TextButton(
                            onClick = { digits = !digits },
                            modifier = Modifier.semantics { contentDescription = toLabel }
                        ) {
                            Text(
                                if (digits) stringResource(R.string.messages_new_letters_short) else DIGITS_LABEL,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (digits) KeyboardType.Phone else KeyboardType.Text,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { if (canGo) onGo() }),
                    shape = OgnelissShapeTokens.dialKey,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
                FilledIconButton(onClick = onGo, enabled = canGo) {
                    Icon(
                        OgnelissIcons.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.messages_write)
                    )
                }
            }
        }
    }
}

/** Подпись переключателя на цифровую клавиатуру — цифры, перевода не требует. */
private const val DIGITS_LABEL = "123"
