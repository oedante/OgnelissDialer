package com.ogneliss.dialer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * «НАВЕРХ» — КНОПКА-ПИЛЮЛЯ НАД ДЛИННЫМ СПИСКОМ (1.5.7).
 *
 * Приём мессенджеров: в Telegram, пролистав далеко, видишь кнопку
 * возврата. Здесь она появляется, когда человек ушёл дальше [threshold]
 * строк и ЛИСТАЕТ ВВЕРХ — то есть, похоже, ищет начало. Листает вниз —
 * кнопка прячется и не закрывает строки, которые читают.
 *
 * Возврат издалека — прыжком почти до начала и короткой прокруткой:
 * плавная прокрутка через тысячу строк заняла бы секунды.
 *
 * «Назад» и повторное нажатие вкладки по-прежнему возвращают наверх;
 * кнопка — для тех, кто о них не знает.
 *
 * ── Число новых (1.5.8, обзор интерфейса, У4) ──────────────────────────────
 * Пока человек листал журнал, кто-то позвонил — новая запись появилась
 * наверху, а он её не видит. Как в Telegram, кнопка показывает число:
 * «↑ 2 новых» — и появляется, как только список не в самом начале,
 * не дожидаясь прокрутки вверх. Считает экран журнала ([newCount]).
 */
@Composable
fun ScrollToTopPill(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    threshold: Int = SCROLL_TOP_THRESHOLD,
    /** Сколько записей появилось наверху, пока список листали; 0 — не показывать. */
    newCount: Int = 0
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(listState, threshold) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val up = index < lastIndex || (index == lastIndex && offset < lastOffset)
                val down = index > lastIndex || (index == lastIndex && offset > lastOffset)
                show = when {
                    index < threshold -> false
                    up -> true
                    down -> false
                    else -> show
                }
                lastIndex = index
                lastOffset = offset
            }
    }

    val scope = rememberCoroutineScope()
    val surfaces = OgnelissTheme.surfaces
    val awayFromTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val label = if (newCount > 0) {
        pluralStringResource(R.plurals.scroll_top_new, newCount, newCount)
    } else {
        stringResource(R.string.cards_scroll_top)
    }
    val description = if (newCount > 0) {
        pluralStringResource(R.plurals.a11y_scroll_top_new, newCount, newCount)
    } else {
        label
    }
    AnimatedVisibility(
        visible = show || (newCount > 0 && awayFromTop),
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it }
    ) {
        Surface(
            onClick = {
                show = false
                scope.launch {
                    if (listState.firstVisibleItemIndex > JUMP_TO) listState.scrollToItem(JUMP_TO)
                    listState.animateScrollToItem(0)
                }
            },
            shape = CircleShape,
            color = surfaces.card,
            border = BorderStroke(1.dp, surfaces.cardBorder),
            shadowElevation = 3.dp,
            modifier = Modifier.semantics { contentDescription = description }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = Space.l, vertical = Space.s)
                    // Читается описание кнопки, а надпись — нет: иначе
                    // TalkBack сказал бы число дважды
                    .clearAndSetSemantics { }
            ) {
                Icon(
                    OgnelissIcons.ArrowUpward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    // Новые — цветом, как непрочитанные в Telegram
                    color = if (newCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** С какой строки кнопка имеет смысл: примерно два экрана журнала. */
private const val SCROLL_TOP_THRESHOLD = 12
/** Прыжок перед плавной прокруткой: её последние строки. */
private const val JUMP_TO = 6
