package com.ogneliss.dialer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * ПЛАВАЮЩАЯ ДАТА НАД ЛЕНТОЙ — КАК В OGNELISS MAIL (1.8.1).
 *
 * Автор: «давай добавим плашку с датой в журнал» — снимки ленты почты,
 * «можно сделать UI одинаковыми». Повторяет `_FloatingDateChip` почты
 * (mail_list_screen.dart, 1.6.40):
 *
 * - пока ленту листают — по центру сверху подпись дня, который сейчас
 *   у верхнего края («ВЧЕРА», «29 октября · ЧЕТВЕРГ»);
 * - вверху сама подпись дня или лента у начала — плашки нет: дата и так
 *   на виду;
 * - список остановился — через 1,2 с плашка гаснет (180 мс);
 * - вид — как подпись раздела ленты, на плашке цвета приподнятой
 *   поверхности со скруглением 10 dp и лёгкой тенью; в высоком контрасте —
 *   прописными серыми, как полосы разделов.
 *
 * Появляется только от прокрутки пальцем или «Наверх» — не от возврата
 * на прежнее место при открытии журнала: вспышка без причины отвлекает.
 *
 * TalkBack плашку не читает: подписи дней в ленте — заголовки, и по ним
 * он переходит сам; плашка — подсказка для глаз.
 *
 * @param labelForKey подпись дня по ключу строки ленты; null — у строки
 *        дня нет (подпись раздела, поиск, напоминания) — плашку не показываем
 */
@Composable
fun FloatingDatePill(
    listState: LazyListState,
    labelForKey: (Any) -> String?,
    modifier: Modifier = Modifier
) {
    val labelFor by rememberUpdatedState(labelForKey)
    val nearTopPx = with(LocalDensity.current) { NEAR_TOP.toPx() }

    // Верхняя строка ленты — та, что пересекает её верхний край
    val label by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset < nearTopPx
            ) {
                null
            } else {
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.offset + it.size > 0 }
                    ?.let { labelFor(it.key) }
            }
        }
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .drop(1)
            .collectLatest {
                // Возврат на прежнее место (scrollToItem) — не прокрутка
                if (listState.isScrollInProgress) shown = true
                delay(HIDE_AFTER_MS)
                shown = false
            }
    }

    // Последняя подпись — чтобы гаснущая плашка не опустела раньше времени.
    // Не состояние: запись при построении не должна вызывать перестройку
    val last = remember { arrayOf("") }
    label?.let { last[0] = it }

    AnimatedVisibility(
        visible = shown && label != null,
        modifier = modifier,
        enter = fadeIn(tween(FADE_MS)),
        exit = fadeOut(tween(FADE_MS))
    ) {
        DateChip(label ?: last[0])
    }
}

/** Плашка с подписью дня: та же форма, что у плавающей даты почты. */
@Composable
private fun DateChip(text: String) {
    val outlined = OgnelissTheme.surfaces.outlined
    Surface(
        // shapes.small — 10 dp, как у почты; со скруглением из настроек (1.9.0)
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
        modifier = Modifier.clearAndSetSemantics { }
    ) {
        Text(
            if (outlined) text.uppercase() else text,
            modifier = Modifier.padding(horizontal = Space.m, vertical = 6.dp),
            style = if (outlined) {
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
            } else {
                MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
            },
            color = if (outlined) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

/** Лента у самого начала — дата и так видна (почта: 40 px). */
private val NEAR_TOP = 16.dp

/** Сколько плашка держится после остановки списка. */
private const val HIDE_AFTER_MS = 1_200L

private const val FADE_MS = 180
