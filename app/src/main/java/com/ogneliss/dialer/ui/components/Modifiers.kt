package com.ogneliss.dialer.ui.components

import kotlin.math.abs
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

/**
 * Обёртка над combinedClickable.
 *
 * Нужна, чтобы не помечать каждый вызывающий компонент аннотацией
 * ExperimentalFoundationApi. Когда API стабилизируется, обёртку
 * можно будет удалить одной правкой вместо десятка.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = this.combinedClickable(onClick = onClick, onLongClick = onLongClick)


/**
 * ПЕРЕЛИСТЫВАНИЕ МЕЖДУ ЭКРАНАМИ СВАЙПОМ ПО ПАНЕЛИ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ИМЕННО ПО ПАНЕЛИ, А НЕ ПО ВСЕМУ ЭКРАНУ
 * ══════════════════════════════════════════════════════════════════════════
 * Свайп по списку уже занят: он вызывает или отправляет сообщение.
 * Добавить туда ещё и переход между экранами значило бы заставить
 * приложение угадывать намерение по длине жеста — а оно ошибается,
 * и цена ошибки высока: вместо перехода уходит звонок.
 *
 * Нижняя панель для этого подходит: она не прокручивается, кнопки
 * на ней срабатывают по нажатию, а не по протягиванию, и горизонтальное
 * движение по ней не означает больше ничего.
 *
 * ── Почему порог именно такой ──────────────────────────────────────────────
 * 64 dp — примерно ширина одной кнопки панели. Меньше означало бы
 * срабатывание при попытке нажать: палец редко опускается строго
 * вертикально.
 *
 * Больше сотни неудобно: панель невысокая, и длинный жест по ней
 * приходится делать нарочито.
 *
 * ── Почему не учитывается скорость ─────────────────────────────────────────
 * Быстрый короткий взмах здесь неотличим от промаха по кнопке.
 * Расстояние — единственный надёжный признак намерения на узкой полосе.
 */
fun Modifier.panelSwipeNavigation(
    enabled: Boolean,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
): Modifier = if (!enabled) this else composed {
    val thresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.dp.toPx() }

    /*
     * Ключ Unit по той же причине, что у вертикального жеста:
     * лямбды пересоздаются при перерисовке и перезапускают
     * обработчик, отменяя распознавание.
     */
    val currentLeft by rememberUpdatedState(onSwipeLeft)
    val currentRight by rememberUpdatedState(onSwipeRight)

    /*
     * ЖЕСТ ПЕРЕХВАТЫВАЕТСЯ ДО ДОЧЕРНИХ ЭЛЕМЕНТОВ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НЕ detectHorizontalDragGestures
     * ══════════════════════════════════════════════════════════════════════
     * Обычный распознаватель работает в основной фазе доставки событий,
     * а она идёт СНИЗУ ВВЕРХ: сначала дочерние элементы, потом родитель.
     * Поле поиска на панели заметок и напоминаний забирает касание себе,
     * и до панели ничего не доходит: свайп не работал вовсе, а вместо
     * него открывалась клавиатура.
     *
     * В журнале беды не было — там под полоской кнопки, а они
     * на перетаскивание не претендуют. Отсюда и ложный вывод в прошлый
     * раз: «в контактах работает, значит дело не в поле».
     *
     * ── Как теперь ─────────────────────────────────────────────────────────
     * События слушаются в НАЧАЛЬНОЙ фазе (PointerEventPass.Initial) —
     * она идёт сверху вниз, и родитель видит движение первым. Но жест
     * забирается себе, только когда ясно, что он горизонтальный:
     *
     *   • пока палец не сдвинулся дальше порога, не трогаем ничего —
     *     обычное касание по полю по-прежнему ставит курсор;
     *   • когда сдвинулся, сравниваем оси: горизонталь должна
     *     превосходить вертикаль, иначе жест уходит детям — это
     *     прокрутка или выделение текста.
     *
     * ⚠️ Решение принимается ОДИН раз за жест. Иначе палец, ведущий
     * по дуге, отбирал бы касание у поля на середине движения, и для
     * человека это выглядит как подвисший интерфейс.
     */
    pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop

        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )

            var dx = 0f
            var dy = 0f
            var decided = false
            var claimed = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                dx += change.positionChange().x
                dy += change.positionChange().y

                if (!decided && (abs(dx) > slop || abs(dy) > slop)) {
                    decided = true
                    claimed = abs(dx) > abs(dy) * HORIZONTAL_DOMINANCE
                }

                // Забранное событие не дойдёт до поля ввода: иначе оно
                // получит курсор посреди свайпа
                if (claimed) change.consume()
            }

            if (!claimed || abs(dx) < thresholdPx) return@awaitEachGesture

            /*
             * ПЕРЕХОД ВЫПОЛНЯЕТСЯ ПО ОТПУСКАНИЮ ПАЛЬЦА.
             *
             * Ни один свайп в приложении не срабатывает, пока палец
             * на экране: человек может потянуть, передумать и вернуть
             * назад. Здесь это следует из порядка — проверка стоит
             * после выхода из цикла, то есть после отпускания.
             */
            if (dx < 0) currentLeft?.invoke() else currentRight?.invoke()
        }
    }
}

/**
 * Расстояние, с которого движение считается перелистыванием.
 *
 * Примерно ширина одной кнопки панели: короче — промах по кнопке,
 * длиннее — жест приходится делать нарочито.
 */
/**
 * Во сколько раз горизонталь должна превосходить вертикаль, чтобы жест
 * считался переходом между экранами.
 *
 * Полтора — то же значение, что у свайпа по строке (SwipeRevealRow):
 * оба жеста живут на одном экране, и разные пороги ощущались бы
 * как непредсказуемость.
 */
private const val HORIZONTAL_DOMINANCE = 1.5f

private const val SWIPE_THRESHOLD_DP = 64

/**
 * ВЕРТИКАЛЬНЫЙ ЖЕСТ: РАСКРЫТЬ И СКРЫТЬ КЛАВИАТУРУ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ, ЕСЛИ ЕСТЬ КНОПКА
 * ══════════════════════════════════════════════════════════════════════════
 * Кнопка остаётся и работает. Жест добавляет второй способ там, где
 * он естественнее: клавиатура выезжает снизу, и движение пальца вверх
 * повторяет то, что происходит на экране.
 *
 * Для скрытия то же самое в обратную сторону — как убирают системную
 * клавиатуру или шторку уведомлений. Этот жест человек уже знает
 * из других мест, запоминать нечего.
 *
 * ── Почему срабатывание по отрыву пальца ───────────────────────────────────
 * То же правило, что у всех жестов приложения: пока палец на экране,
 * можно передумать и вернуть его назад. См. panelSwipeNavigation выше
 * и settledValue в SwipeRevealRow.
 *
 * ── Почему тап при этом не ломается ────────────────────────────────────────
 * Нажатие и перетаскивание Compose различает сам: обработчик тапа
 * срабатывает, только если палец не сдвинулся дальше порога системы.
 * Кнопка клавиатуры продолжает работать по нажатию.
 */
fun Modifier.verticalSwipeGesture(
    enabled: Boolean = true,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null
): Modifier = if (!enabled) this else composed {
    val thresholdPx = with(LocalDensity.current) { VERTICAL_THRESHOLD_DP.dp.toPx() }

    /*
     * КЛЮЧ pointerInput — Unit, А НЕ ЛЯМБДЫ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ЖЕСТ НЕ СРАБАТЫВАЛ
     * ══════════════════════════════════════════════════════════════════════
     * Лямбда `onSwipeDown = onToggleExpanded` пересоздаётся при каждой
     * перерисовке панели. Для pointerInput это новый ключ — он
     * перезапускает обработчик и ОТМЕНЯЕТ распознавание жеста.
     *
     * А панель перерисовывается постоянно: меняется набранный номер,
     * приходит обновление журнала. Жест обрывался на середине,
     * не дойдя до onDragEnd, и человек видел, что свайп не работает.
     *
     * Unit как ключ означает «обработчик ставится один раз». Свежие
     * лямбды при этом берутся через rememberUpdatedState: они читаются
     * в момент вызова, а не запоминаются при установке.
     */
    val currentUp by rememberUpdatedState(onSwipeUp)
    val currentDown by rememberUpdatedState(onSwipeDown)

    pointerInput(Unit) {
        var total = 0f

        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (abs(total) >= thresholdPx) {
                    // Ось экрана направлена вниз: отрицательное смещение
                    // означает движение вверх
                    if (total < 0) currentUp?.invoke() else currentDown?.invoke()
                }
                total = 0f
            },
            onDragCancel = { total = 0f }
        ) { change, dragAmount ->
            total += dragAmount
            change.consume()
        }
    }
}

/**
 * Порог вертикального жеста.
 *
 * Меньше горизонтального: движение вверх-вниз человек делает увереннее,
 * а перепутать его не с чем — по вертикали на панели ничего другого
 * не происходит.
 */
private const val VERTICAL_THRESHOLD_DP = 48
