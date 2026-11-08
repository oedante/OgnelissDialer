package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.components.GroupedRow
import androidx.compose.foundation.shape.CircleShape
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import com.ogneliss.dialer.data.PinnedEntry
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.PinnedContacts
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.theme.Dimens

/**
 * УПРАВЛЕНИЕ ЗАКРЕПЛЁННЫМИ АБОНЕНТАМИ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОТДЕЛЬНЫЙ ЭКРАН, ЕСЛИ ЕСТЬ ПУНКТ В ШТОРКЕ
 * ══════════════════════════════════════════════════════════════════════════
 * Шторка на карточке решает задачу «закрепить вот этого» — она под рукой
 * в тот момент, когда мысль возникла.
 *
 * Но три вещи через неё сделать нельзя:
 *
 * • ПОСМОТРЕТЬ ВЕСЬ СПИСОК. Закреплённых пять, и держать их в голове
 *   не обязан никто;
 * • ПОМЕНЯТЬ ПОРЯДОК. Он задаётся временем закрепления, и переставить
 *   через шторку можно только открепив и закрепив заново — то есть
 *   потеряв позицию остальных;
 * • ОТКРЕПИТЬ НОМЕР, КОТОРОГО НЕТ НА ЭКРАНЕ. Если человек не звонил
 *   давно, его карточка ушла за пределы загруженной страницы,
 *   и добраться до шторки нечем.
 *
 * ── Почему стрелки, а не перетаскивание ────────────────────────────────────
 * Перетаскивание в списке на телефоне ненадёжно: длинное нажатие
 * конфликтует с прокруткой, палец перекрывает перемещаемый элемент,
 * а промах возвращает всё на место без объяснения.
 *
 * Две стрелки решают ту же задачу однозначно. Элементов не больше пяти —
 * переставить нужное на любое место занимает четыре нажатия в худшем
 * случае, и результат каждого виден.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Composable
fun PinnedContactsScreen(
    vm: DialerViewModel,
    onBack: () -> Unit,
    onAddFromContacts: () -> Unit
) {
    val pinnedNumbers by vm.pinnedNumbers.collectAsStateWithLifecycle()
    val uiState by vm.state.collectAsStateWithLifecycle()
    // Контакты — тоже ключ: имя и фото закреплённого берутся из книги (1.3.2)
    val entries = remember(pinnedNumbers, uiState.callLog, uiState.contacts) {
        vm.pinnedEntries()
    }

    ScreenScaffold(
        // Как настройки Ogneliss Mail: серо-голубая страница, группы — карточками (1.5.6)
        tone = com.ogneliss.dialer.ui.theme.PageTone.TINTED,
        title = stringResource(R.string.settings_pinned),
        onBack = onBack,
        // Экран настроек: заголовок закреплён
        pinnedTopBar = true
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {

            // 2.1.0: без абзаца «Закреплённые стоят вверху журнала… курьер,
            // ремонт» — правило автора 07.11.2026, пояснений не показываем
            item { Spacer(Modifier.height(Space.l)) }

            if (entries.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(Space.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            OgnelissIcons.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(Space.l))
                        // Значок и заголовок (2.1.0): подсказки «можно закрепить
                        // и долгим нажатием в журнале» нет — пояснений не показываем
                        Text(
                            stringResource(R.string.pinned_empty),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            itemsIndexed(entries) { index, entry ->
                // Одной группой с рамкой (строгий стиль, 1.5.1)
                GroupedRow(GroupPosition.of(index, entries.size), dividerStart = 96.dp) {
                    PinnedRow(
                        position = index + 1,
                        entry = entry,
                        canMoveUp = index > 0,
                        canMoveDown = index < entries.lastIndex,
                        // По записи, а не по номеру (1.3.2): у закреплённого
                        // контакта номер в записи может быть не тем, что в строке
                        onMoveUp = { vm.movePinned(entry.key, up = true) },
                        onMoveDown = { vm.movePinned(entry.key, up = false) },
                        onUnpin = { vm.unpinKey(entry.key) }
                    )
                }
            }

            item {
                // Кнопка добавления неактивна при достижении предела:
                // активная кнопка, ведущая к отказу, сбивает с толку
                val canAdd = PinnedContacts.canPinMore(pinnedNumbers)

                /*
                 * Строка «Закрепить контакт».
                 *
                 * Приглушается, когда мест не осталось: цвет говорит
                 * о недоступности раньше, чем человек нажмёт и получит
                 * отказ.
                 */
                if (entries.isNotEmpty()) Spacer(Modifier.height(Space.l))
                GroupedRow(GroupPosition.SINGLE) {
                OgnelissListRow(
                    headline = stringResource(R.string.pinned_add),
                    /*
                     * Сколько мест осталось — второй строкой.
                     *
                     * Без этой подписи человек узнаёт о пределе только
                     * нажав и получив отказ. Строка отвечает заранее:
                     * «осталось два места» или «все пять заняты,
                     * сначала открепите».
                     */
                    supporting = if (canAdd) {
                        stringResource(
                            R.string.pinned_slots_left,
                            PinnedContacts.MAX_PINNED - pinnedNumbers.size
                        )
                    } else {
                        stringResource(
                            R.string.pinned_limit_reached,
                            PinnedContacts.MAX_PINNED
                        )
                    },
                    headlineColor = if (canAdd) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                    leading = {
                        Icon(
                            OgnelissIcons.Add,
                            contentDescription = null,
                            tint = if (canAdd) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    },
                    onClick = if (canAdd) onAddFromContacts else null
                )
                }
            }

            item { Spacer(Modifier.height(Space.xxl)) }
        }
    }
}

/**
 * Строка закреплённого абонента.
 *
 * Номер позиции слева — не украшение: он показывает, где карточка
 * окажется в журнале, и делает результат перестановки предсказуемым
 * до нажатия.
 */
@Composable
private fun PinnedRow(
    position: Int,
    entry: PinnedEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUnpin: () -> Unit
) {
    ListItem(
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$position",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(40.dp)
                ) {
                    if (entry.photoUri != null) {
                        AsyncImage(model = entry.photoUri, contentDescription = null)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            // Светлый силуэт, как в журнале (1.6.1)
                            Icon(
                                OgnelissIcons.Person, null, Modifier.size(20.dp),
                                tint = com.ogneliss.dialer.ui.theme.OgnelissTheme.surfaces.avatarGlyph
                            )
                        }
                    }
                }
            }
        },
        headlineContent = {
            Text(entry.displayName ?: entry.formattedNumber)
        },
        supportingContent = {
            // Номер второй строкой показывается, только если есть имя:
            // иначе он дублировал бы заголовок
            entry.displayName?.let { Text(entry.formattedNumber) }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        OgnelissIcons.ArrowUpward,
                        contentDescription = stringResource(R.string.pinned_move_up)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        OgnelissIcons.ArrowDownward,
                        contentDescription = stringResource(R.string.pinned_move_down)
                    )
                }
                IconButton(onClick = onUnpin) {
                    Icon(
                        OgnelissIcons.PushPin,
                        contentDescription = stringResource(R.string.action_unpin),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        modifier = Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET)
    )
}
