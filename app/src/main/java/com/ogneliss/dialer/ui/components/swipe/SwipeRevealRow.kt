package com.ogneliss.dialer.ui.components.swipe

import com.ogneliss.dialer.ui.theme.Space

import kotlin.math.abs
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
// animateTo и snapTo объявлены функциями-расширениями, а не методами:
// без явного импорта компилятор их не находит
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.ogneliss.dialer.settings.SwipeTuning
import com.ogneliss.dialer.ui.theme.Dimens

/**
 * СТРОКА С РАСКРЫВАЮЩЕЙСЯ ПАНЕЛЬЮ ДЕЙСТВИЙ.
 *
 * ── Что здесь важно с точки зрения реализации ──────────────────────────────
 *
 * 1. AnchoredDraggableState вместо ручного смещения.
 *    Наивная реализация с animateFloatAsState дерётся с пальцем: анимация
 *    тянет offset к цели, пока пользователь тащит строку в другую сторону,
 *    и движение выглядит вязким. AnchoredDraggable различает фазу
 *    перетаскивания и фазу оседания.
 *
 * 2. Учёт скорости броска.
 *    Решение принимается не только по пройденному расстоянию, но и по
 *    скорости: быстрый короткий флик открывает панель, медленное движение
 *    на то же расстояние — нет. Без этого приходится тянуть далеко,
 *    что на длинном списке неудобно.
 *
 * 3. Только фиксированные положения.
 *    Якорей ровно три: закрыто, открыто вправо, открыто влево. Панель
 *    никогда не остаётся приоткрытой — наполовину видная кнопка
 *    провоцирует промах.
 *
 * 4. Панель НЕ срабатывает сама.
 *    В отличие от swipe-to-dismiss, достижение порога только раскрывает
 *    панель. Действие всегда требует отдельного нажатия — резкий
 *    случайный свайп никому не позвонит.
 *
 * 5. Доступность.
 *    Жест свайпа недоступен пользователю TalkBack в принципе. Поэтому все
 *    действия панели продублированы через customActions: экранный диктор
 *    предлагает их списком по долгому нажатию. Без этого функция была бы
 *    недоступна незрячим полностью.
 *
 * 6. Тактильный отклик при раскрытии — подтверждает, что панель открылась,
 *    когда её загораживает палец.
 */

/**
 * Состояние строки.
 *
 * TRIGGER_* — не «открыто», а «действие сработало»: строка уезжает
 * до порога, вызывает действие и возвращается назад. Используется,
 * когда действие одно и выбирать не из чего.
 */
private enum class RevealState { CLOSED, OPEN_START, OPEN_END, TRIGGER_START, TRIGGER_END }

/**
 * Одно действие в раскрытой панели.
 *
 * ── Почему @Immutable ──────────────────────────────────────────────────────
 * Класс содержит лямбду `onClick`, а функциональные типы Compose считает
 * нестабильными: за лямбдой может стоять что угодно, включая захват
 * изменяемого состояния.
 *
 * Следствие было бы неприятным: список действий передаётся в КАЖДУЮ
 * строку журнала, и нестабильность одного элемента делала бы весь список
 * изменившимся при каждой перерисовке — то есть строки перерисовывались
 * бы все и всегда, сколько бы мы ни кэшировали их подготовку.
 *
 * Обещание выполнимо: объекты создаются фабрикой один раз на набор
 * настроек и дальше не меняются.
 */
@Immutable
data class RevealAction(
    /** Короткая подпись: «…05-12» или «Рабочий». */
    val label: String,
    /** Полное описание для TalkBack: «Позвонить на 05-12 с SIM 1». */
    val description: String,
    val color: Color,
    val icon: ImageVector? = null,
    /** Номер SIM для бейджа, если действие привязано к карте. */
    val simBadge: Int? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeRevealRow(
    /** Стабильный ключ строки — нужен координатору. */
    key: String,
    controller: SwipeRevealController,
    /** Действия, раскрывающиеся при свайпе ВПРАВО. */
    startActions: List<RevealAction>,
    /** Действия, раскрывающиеся при свайпе ВЛЕВО. */
    endActions: List<RevealAction>,
    modifier: Modifier = Modifier,
    /** Вызывается при свайпе, когда действие одно и панель не нужна. */
    onDirectStart: (() -> Unit)? = null,
    onDirectEnd: (() -> Unit)? = null,
    /**
     * ВИД ПРЯМОГО ДЕЙСТВИЯ ДЛЯ ПОДСКАЗКИ.
     *
     * При одном действии панели нет, и раньше из-под строки выезжало
     * пустое место: понять, что произойдёт, было нельзя — приходилось
     * доводить жест вслепую и узнавать результат постфактум.
     *
     * Здесь передаётся тот же объект, что был бы кнопкой в панели:
     * пиктограмма и цвет. Обработчик не используется — действие
     * выполняет onDirectStart.
     */
    directStartAction: RevealAction? = null,
    directEndAction: RevealAction? = null,
    /**
     * Параметры чувствительности.
     *
     * Приходят снаружи, а не задаются константами: пороги зависят
     * от размера телефона и манеры движений, и подобрать одно значение
     * для всех невозможно. Значения по умолчанию — компромисс,
     * пользователь может его сдвинуть.
     */
    tuning: SwipeTuning = SwipeTuning.DEFAULT,
    /**
     * Список сейчас прокручивается.
     *
     * Пока это так, свайп выключен: намерение пользователя однозначно,
     * и перехватывать его горизонтальным жестом нельзя.
     */
    listScrolling: Boolean = false,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val actionWidth = Dimens.swipeActionWidth
    val actionWidthPx = with(density) { actionWidth.toPx() }

    /*
     * Два режима работы, и различие принципиальное.
     *
     * ПАНЕЛЬ — когда действий несколько: строка фиксируется открытой,
     * пользователь выбирает кнопку отдельным нажатием.
     *
     * ПРЯМОЕ ДЕЙСТВИЕ — когда действие одно: панель показывать нечего,
     * свайп сразу выполняет его и строка возвращается на место.
     * Это основной случай: у большинства контактов один номер.
     */
    val hasStartPanel = startActions.isNotEmpty()
    val hasEndPanel = endActions.isNotEmpty()
    val hasStartDirect = !hasStartPanel && onDirectStart != null
    val hasEndDirect = !hasEndPanel && onDirectEnd != null

    /**
     * Насколько нужно протянуть для прямого действия.
     *
     * ── Почему порог увеличен со 96 до 140 dp ──────────────────────────────
     * При 96 dp действие срабатывало от лёгкого движения пальцем поперёк
     * экрана — а такое движение случается при обычной прокрутке списка,
     * если палец идёт не строго вертикально. Пользователь листал журнал
     * и внезапно начинал звонок.
     *
     * 140 dp — примерно треть ширины телефона: столько случайно
     * не проводят, но и намеренно тянуть не утомительно.
     */
    val directTriggerPx = with(density) { tuning.triggerDistanceDp.dp.toPx() }

    val startWidth = if (hasStartPanel) actionWidthPx * startActions.size else directTriggerPx
    val endWidth = if (hasEndPanel) actionWidthPx * endActions.size else directTriggerPx

    /*
     * ЯКОРЯ СЧИТАЮТСЯ ОДИН РАЗ.
     *
     * Раньше DraggableAnchors создавался в теле composable — на каждую
     * строку при каждой перерисовке. По отдельности дёшево, но при
     * десяти видимых строках и шестидесяти кадрах это шестьсот
     * созданий в секунду на ровном месте.
     */
    val rememberedAnchors = remember(
        startWidth, endWidth, hasStartPanel, hasEndPanel, hasStartDirect, hasEndDirect
    ) {
        DraggableAnchors {
            RevealState.CLOSED at 0f
            if (hasStartPanel) RevealState.OPEN_START at startWidth
            if (hasEndPanel) RevealState.OPEN_END at -endWidth
            // Якоря прямого действия: строка доезжает до них, выполняет
            // действие и сама возвращается назад
            if (hasStartDirect) RevealState.TRIGGER_START at startWidth
            if (hasEndDirect) RevealState.TRIGGER_END at -endWidth
        }
    }

    /*
     * НОВЫЙ API СВАЙПА (1.3.3, 04.10.2026, Compose 1.12).
     *
     * Состояние — только якоря; пороги и анимация — в revealFlingBehavior
     * (RevealFlingBehavior.kt), с нашим настраиваемым порогом скорости:
     * стандартный AnchoredDraggableDefaults.flingBehavior порог скорости
     * не принимает (зашит 125 dp/с, у нас 450). Прежний конструктор
     * с порогами Compose 1.12 пометил устаревшим.
     */
    val dragState = remember(rememberedAnchors, tuning) {
        AnchoredDraggableState(
            initialValue = RevealState.CLOSED,
            anchors = rememberedAnchors
        )
    }

    val revealFling = remember(dragState, tuning, density) {
        revealFlingBehavior(
            state = dragState,
            /*
             * ПОРОГ ЗАЩЁЛКИВАНИЯ.
             *
             * Было 0.4 — панель фиксировалась, стоило протянуть чуть
             * больше трети пути. При быстром движении это происходило
             * почти мгновенно, и жест ощущался неуправляемым:
             * пользователь листал журнал, а строка уезжала вбок.
             *
             * 0.75 требует довести движение почти до конца, зато
             * результат предсказуем и случайных срабатываний нет.
             */
            positionalThreshold = { distance -> distance * tuning.positionalThreshold },
            /*
             * ПОРОГ СКОРОСТИ.
             *
             * 120 dp/с ловил обычную прокрутку списка, если палец шёл
             * под небольшим углом к вертикали — а он почти всегда идёт
             * под углом. 450 требует именно горизонтального броска.
             */
            velocityThreshold = {
                with(density) { tuning.velocityDpPerSecond.dp.toPx() }
            }
        )
    }

    // ── Синхронизация с координатором ──────────────────────────────────────
    // Когда открывается другая строка, эта должна закрыться
    /*
     * СТРАХОВКА ПРИ ВОЗВРАЩЕНИИ НА ЭКРАН.
     *
     * Если строка всё же осталась в состоянии срабатывания — процесс
     * приостановили в неудачный момент, анимацию прервали, — она
     * снапится на место при первой же перекомпоновке.
     *
     * snapTo, а не animateTo: анимация возврата спустя минуту после
     * жеста выглядела бы как сбой, а мгновенная установка незаметна.
     */
    /*
     * Область для анимаций возврата.
     *
     * Живёт при композиции, а не при значении ключа эффекта: анимация
     * в ней доигрывает, даже когда эффект, её запустивший, отменён.
     */
    val rowScope = rememberCoroutineScope()

    /*
     * Намерение прокрутить, а не смахнуть.
     *
     * Ставится, когда первое заметное движение оказалось
     * преимущественно вертикальным. Держится до конца касания:
     * менять решение посреди жеста значит дёргать строку.
     */
    var verticalIntent by remember { mutableStateOf(false) }

    // Порог начала жеста в пикселях: ниже него движение считается
    // дрожанием пальца, а не намерением
    val touchSlopPx = with(LocalDensity.current) { 8.dp.toPx() }

    /*
     * КОМАНДА «ЗАКРЫТЬ ВСЁ».
     *
     * ── Почему не наблюдатель жизненного цикла в каждой строке ─────────────
     * Сначала здесь стоял DisposableEffect с подпиской на жизненный цикл.
     * Он работал, но обходился дорого: при прокрутке строки создаются
     * и уничтожаются постоянно, и каждая добавляла и снимала подписку —
     * десятки операций в секунду ради случая, который происходит
     * раз в несколько минут.
     *
     * Счётчик в координаторе стоит одно сравнение целых чисел.
     * Подписку на жизненный цикл держит экран — один раз на список,
     * а не по разу на строку.
     */
    LaunchedEffect(controller.closeAllToken) {
        if (dragState.settledValue != RevealState.CLOSED) {
            dragState.snapTo(RevealState.CLOSED)
        }
    }

    LaunchedEffect(controller.openKey) {
        if (controller.openKey != key && dragState.settledValue != RevealState.CLOSED) {
            dragState.animateTo(RevealState.CLOSED)
        }
    }

    /*
     * ДЕЙСТВИЕ СРАБАТЫВАЕТ ТОЛЬКО ПОСЛЕ ОТПУСКАНИЯ ПАЛЬЦА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ settledValue, А НЕ currentValue
     * ══════════════════════════════════════════════════════════════════════
     * currentValue меняется В МОМЕНТ пересечения порога — палец ещё
     * на экране, а состояние уже переключилось. Действие выполнялось
     * прямо посреди жеста: человек тянул строку, чтобы посмотреть,
     * что будет, и звонок уходил сам собой.
     *
     * Отменить такое нельзя: к моменту, когда палец вернулся назад,
     * действие уже произошло.
     *
     * settledValue обновляется только когда жест ЗАВЕРШЁН и анимация
     * доиграла.
     *
     * ── Это общее правило приложения ───────────────────────────────────────
     * Ни один свайп не срабатывает, пока палец на экране. То же сделано
     * для перехода между вызовами и контактами — там через onDragEnd,
     * см. Modifier.panelSwipeNavigation.
     *
     * Средства разные, поведение для человека одинаковое: иначе
     * он не сможет предсказать, когда жест засчитается. Пока палец на экране, значение остаётся прежним —
     * можно тянуть в обе стороны сколько угодно и вернуть назад
     * без последствий.
     *
     * ── Побочная польза ────────────────────────────────────────────────────
     * Подсказка под строкой при этом продолжает светлеть по мере сдвига:
     * она читает смещение напрямую, а не состояние. Человек видит,
     * что произойдёт, и сам решает, отпускать или вернуть.
     */
    LaunchedEffect(dragState.settledValue) {
        when (dragState.settledValue) {
            RevealState.CLOSED -> controller.closeIfOpen(key)

            /*
             * ПРЯМОЕ ДЕЙСТВИЕ: СНАЧАЛА ВОЗВРАТ, ПОТОМ ВЫПОЛНЕНИЕ.
             *
             * ── Почему порядок именно такой ────────────────────────────
             * Действие открывает диалог выбора SIM или чужое приложение —
             * сообщения, мессенджер. Композиция при этом приостанавливается,
             * и корутина LaunchedEffect ОТМЕНЯЕТСЯ.
             *
             * Если возврат стоит после вызова, он до отмены не доходит:
             * человек нажимает «Отмена» в диалоге или возвращается
             * из SMS — а карточка всё ещё сдвинута.
             *
             * Возврат до вызова этой беды не имеет: анимация запускается
             * и завершается, пока действие ещё не начато.
             *
             * ── Тактильный отклик остаётся первым ──────────────────────
             * Он должен совпасть с моментом, когда жест распознан,
             * а не с концом анимации: иначе ощущается запаздывающим.
             */
            /*
             * ВОЗВРАТ ЗАПУСКАЕТСЯ В ОТДЕЛЬНОЙ ОБЛАСТИ.
             *
             * ── Почему не напрямую ─────────────────────────────────────
             * Этот LaunchedEffect привязан к dragState.settledValue.
             * Вызов animateTo меняет это значение — и эффект ОТМЕНЯЕТ
             * САМ СЕБЯ, не дойдя до следующей строки.
             *
             * Из-за этого действие не выполнялось вовсе: жест распознан,
             * строка вернулась, а звонка нет.
             *
             * rowScope живёт при композиции, а не при значении ключа,
             * поэтому анимация в нём доигрывает независимо. Действие
             * при этом вызывается сразу, не дожидаясь её конца:
             * ждать полсекунды ради вида — заметная задержка.
             */
            RevealState.TRIGGER_START -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                rowScope.launch { dragState.animateTo(RevealState.CLOSED) }
                onDirectStart?.invoke()
            }
            RevealState.TRIGGER_END -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                rowScope.launch { dragState.animateTo(RevealState.CLOSED) }
                onDirectEnd?.invoke()
            }

            // Панель раскрыта и ждёт выбора
            else -> {
                controller.open(key)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /*
     * Закрытие панели: и снятие отметки в координаторе, и возврат
     * строки анимацией.
     *
     * Раньше вызывался только координатор — он снимал признак «открыто»,
     * но сама строка оставалась сдвинутой, потому что её положением
     * управляет dragState, а он о координаторе не знает.
     */
    val closeRow: () -> Unit = {
        controller.close()
        rowScope.launch { dragState.animateTo(RevealState.CLOSED) }
    }

    // ── Доступность: дублируем действия панели для TalkBack ────────────────
    val a11yActions = remember(startActions, endActions) {
        (startActions + endActions).map { action ->
            CustomAccessibilityAction(action.description) { action.onClick(); true }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .semantics { customActions = a11yActions }
    ) {

        // Панель раскрывается ровно на пройденное расстояние.
        // Ширина считается от смещения, поэтому кнопки «выезжают»
        // из-под строки, а не появляются целиком рывком.
        /*
         * Смещение для панелей.
         *
         * Здесь чтение в композиции неизбежно: от знака смещения зависит,
         * какую панель показывать, а это структурное решение — его
         * в фазе отрисовки не принять.
         *
         * Зато сама строка сдвигается через graphicsLayer и от этого
         * чтения не зависит: перекомпоновка затрагивает только подсказку
         * под строкой, а не её содержимое.
         */
        val offset = dragState.requireOffset()

        /*
         * ПОДСКАЗКА ПРЯМОГО ДЕЙСТВИЯ.
         *
         * Показывает, что произойдёт, если довести жест. Прозрачность
         * растёт по мере сдвига: у порога значок полностью непрозрачен,
         * и это единственный способ понять, засчитается ли жест,
         * не отпуская палец.
         */
        if (offset > 0 && hasStartDirect && directStartAction != null) {
            DirectActionHint(
                action = directStartAction,
                progress = (offset / directTriggerPx).coerceIn(0f, 1f),
                alignment = Alignment.CenterStart,
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentWidth(Alignment.Start)
                    .width(with(density) { offset.toDp() })
            )
        }

        if (offset < 0 && hasEndDirect && directEndAction != null) {
            DirectActionHint(
                action = directEndAction,
                progress = (-offset / directTriggerPx).coerceIn(0f, 1f),
                alignment = Alignment.CenterEnd,
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentWidth(Alignment.End)
                    .width(with(density) { (-offset).toDp() })
            )
        }

        // Фон рисуется только при сдвиге: иначе он закрашивает площадь
        // под каждой строкой списка, которую тут же перекрывает
        // содержимое — лишний слой на весь экран ради невидимого
        if (offset > 0 && hasStartPanel) {
            ActionPanel(
                actions = startActions,
                actionWidth = actionWidth,
                alignment = Alignment.CenterStart,
                onAction = closeRow,
                /*
                 * ВЫСОТА ПАНЕЛИ ПРИВЯЗАНА К СТРОКЕ.
                 *
                 * fillMaxHeight заставлял панель претендовать на всю
                 * доступную высоту, и при перетягивании строка «прыгала»:
                 * менялась не только ширина открытой части, но и высота
                 * всей карточки — целиться в кнопки становилось трудно.
                 *
                 * matchParentSize берёт размер уже измеренного содержимого
                 * и на измерение не влияет вовсе.
                 */
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentWidth(Alignment.Start)
                    .width(with(density) { offset.toDp() })
            )
        }

        if (offset < 0 && hasEndPanel) {
            ActionPanel(
                actions = endActions,
                actionWidth = actionWidth,
                alignment = Alignment.CenterEnd,
                onAction = closeRow,
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentWidth(Alignment.End)
                    .width(with(density) { (-offset).toDp() })
            )
        }

        Surface(
            Modifier
                /*
                 * СДВИГ ЧЕРЕЗ graphicsLayer, А НЕ offset.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ПОЧЕМУ ЭТО ВАЖНО
                 * ══════════════════════════════════════════════════════════
                 * Modifier.offset двигает содержимое, меняя его положение
                 * при РАЗМЕЩЕНИИ. Compose при этом заново проходит фазу
                 * размещения для строки и всего, что внутри: аватара,
                 * текстов, счётчиков.
                 *
                 * graphicsLayer сдвигает уже готовый слой при ОТРИСОВКЕ.
                 * Измерение и размещение пропускаются целиком — меняется
                 * только матрица преобразования.
                 *
                 * Разница в разы: во время свайпа кадр идёт шестьдесят
                 * раз в секунду, и каждый лишний проход размещения
                 * съедает часть шестнадцатимиллисекундного бюджета.
                 *
                 * ── Побочная польза ───────────────────────────────────────
                 * Отдельный слой не участвует в перерисовке соседей:
                 * сдвиг одной строки не заставляет пересчитывать список.
                 */
                /*
                 * Смещение читается ВНУТРИ лямбды graphicsLayer.
                 *
                 * Это принципиально: лямбда выполняется в фазе отрисовки,
                 * и чтение состояния там подписывает на изменения только
                 * её саму. Композиция строки не перезапускается вовсе.
                 *
                 * Если прочитать dragState.requireOffset() снаружи
                 * и передать значение, подписка окажется на композиции —
                 * и каждый кадр свайпа будет перекомпоновывать строку
                 * целиком вместе с аватаром, текстами и счётчиками.
                 */
                .graphicsLayer { translationX = dragState.requireOffset() }
                /*
                 * ПРОВЕРКА УГЛА: ЖЕСТ ДОЛЖЕН БЫТЬ ГОРИЗОНТАЛЬНЫМ.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ПОЧЕМУ СВАЙП СРАБАТЫВАЛ ПОД 45°
                 * ══════════════════════════════════════════════════════════
                 * anchoredDraggable смотрит только на горизонтальную
                 * составляющую движения: превысила порог — жест начался.
                 * При движении по диагонали она его превышает, хотя
                 * человек вёл палец наискось и, скорее всего, листал.
                 *
                 * startDragImmediately этого не решает: он лишь
                 * запрещает мгновенный старт, но угол не сравнивает.
                 *
                 * ── Как решено ─────────────────────────────────────────────
                 * Здесь мы смотрим первое заметное движение и сравниваем
                 * составляющие. Если вертикальная больше горизонтальной,
                 * жест признаётся прокруткой и свайп на него не отвечает
                 * до конца касания.
                 *
                 * ── Насколько узкий сектор ─────────────────────────────────
                 * Свайпом считается движение в пределах 20° от горизонтали,
                 * всё остальное — прокрутка. Разбор числа —
                 * в MAX_SWIPE_TANGENT внизу файла.
                 */
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalX = 0f
                            var totalY = 0f
                            verticalIntent = false

                            /*
                             * Палец на экране: копим смещение.
                             *
                             * Флаг pressed вынесен из условия цикла
                             * наружу: change объявляется внутри тела,
                             * и в условии do-while он недоступен.
                             */
                            var pressed = true

                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break

                                totalX += change.positionChange().x
                                totalY += change.positionChange().y
                                pressed = change.pressed

                                // Решение принимается один раз, на первом
                                // заметном движении: менять его посреди
                                // жеста значит дёргать строку
                                if (!verticalIntent &&
                                    abs(totalY) > touchSlopPx &&
                                    abs(totalY) > abs(totalX) * MAX_SWIPE_TANGENT
                                ) {
                                    verticalIntent = true
                                }
                            }

                            verticalIntent = false
                        }
                    }
                }
                .anchoredDraggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    /*
                     * Жест отдаётся списку, если начался вертикально.
                     *
                     * Без этого свайп перехватывает прокрутку у тех,
                     * чей палец при листании идёт по дуге — а таких
                     * большинство при управлении одной рукой.
                     */
                    /*
                     * startDragImmediately убран (1.3.3): в Compose 1.12 его
                     * больше нет. Жест начинается после порога касания,
                     * а во время анимации строки — сразу, это делает сама
                     * библиотека. Так и работала настройка по умолчанию
                     * («только по горизонтали» включена).
                     */
                    flingBehavior = revealFling,
                    // Явно, хотя это и значения по умолчанию: тогда компилятор
                    // однозначно выбирает новую перегрузку, а не устаревшую
                    // со startDragImmediately
                    interactionSource = null,
                    overscrollEffect = null,
                    // Жест включён, если возможен хоть один сценарий:
                    // панель или прямое действие. Раньше здесь стояла
                    // проверка только на панели, из-за чего свайп
                    // с одним номером не работал вовсе
                    /*
                     * ЖЕСТ ВЫКЛЮЧЕН, ПОКА СПИСОК ПРОКРУЧИВАЕТСЯ.
                     *
                     * ══════════════════════════════════════════════════════
                     * ПОЧЕМУ СВАЙП СРАБАТЫВАЛ ПРИ ПРОКРУТКЕ
                     * ══════════════════════════════════════════════════════
                     * Compose определяет начало горизонтального жеста
                     * по превышению порога ПО ГОРИЗОНТАЛИ. Он не сравнивает
                     * её с вертикальной составляющей: палец может пройти
                     * сто пикселей вниз и двадцать вбок — и двадцати
                     * хватит, чтобы начать свайп.
                     *
                     * А палец при прокрутке почти всегда идёт по дуге:
                     * большой палец вращается вокруг сустава, и прямой
                     * вертикали не бывает.
                     *
                     * ── Почему запрет во время прокрутки решает это ────────
                     * Список уже прокручивается — значит, намерение
                     * пользователя однозначно, и перехватывать его нельзя.
                     * Свайп остаётся доступен, когда список стоит: тогда
                     * горизонтальное движение действительно означает свайп.
                     *
                     * Отменить начатый свайп по-прежнему можно: вернуть
                     * палец назад, не доводя до порога.
                     */
                    // verticalIntent: диагональное движение отдаётся
                    // списку — см. проверку угла выше
                    enabled = !listScrolling && !verticalIntent &&
                        (hasStartPanel || hasEndPanel ||
                            hasStartDirect || hasEndDirect)
                )
        ) {
            content()
        }
    }
}

/**
 * Панель кнопок. Обрезается по ширине родителя, поэтому при частичном
 * раскрытии видна только часть кнопок — это подсказывает, что панель
 * можно дотянуть.
 */
@Composable
private fun ActionPanel(
    actions: List<RevealAction>,
    actionWidth: androidx.compose.ui.unit.Dp,
    alignment: Alignment,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.clipToBounds(), contentAlignment = alignment) {
        Row {
            actions.forEach { action ->
                ActionCell(action, actionWidth) {
                    /*
                     * Сначала закрываем панель, потом выполняем действие.
                     *
                     * При обратном порядке действие успевает открыть другой
                     * экран, композиция уходит вместе с незавершённой
                     * анимацией — и вернувшись, человек видит строку
                     * по-прежнему сдвинутой.
                     */
                    onAction()
                    action.onClick()
                }
            }
        }
    }
}

@Composable
private fun ActionCell(
    action: RevealAction,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(action.color)
            // Кнопка панели тоже должна быть не меньше минимальной зоны нажатия
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            action.icon?.let {
                Icon(it, contentDescription = null, tint = Color.White)
                Spacer(Modifier.height(Space.xxs))
            }
            if (action.simBadge != null) {
                Surface(
                    color = Color.White.copy(alpha = 0.25f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        action.simBadge.toString(),
                        Modifier.padding(horizontal = Space.xs),
                        color = Color.White,
                        // labelSmall: метка SIM на кнопке свайпа —
                        // самая мелкая подпись в приложении, и шкала
                        // для неё уже есть
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(Space.xxs))
            }
            /*
             * Подпись рисуется, только если она есть.
             *
             * У кнопки вызова при одной активной SIM подписи нет:
             * выбирать не из чего, и текст рядом с трубкой отвлекает.
             * Пустой Text занимал бы высоту и сдвигал пиктограмму вверх.
             */
            if (action.label.isNotEmpty()) {
                Text(
                    action.label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * ПОДСКАЗКА ПРЯМОГО ДЕЙСТВИЯ ПОД СТРОКОЙ.
 *
 * ── Зачем ──────────────────────────────────────────────────────────────────
 * При одном действии панели кнопок нет, и раньше из-под строки выезжало
 * пустое цветное поле: понять, что произойдёт, было нельзя — приходилось
 * доводить жест вслепую и узнавать результат постфактум.
 *
 * ── Почему прозрачность зависит от сдвига ──────────────────────────────────
 * Порог срабатывания — 140 dp, и на глаз определить, хватило ли, нельзя.
 * Нарастающая яркость сообщает об этом непрерывно: значок бледный —
 * жест не засчитается, яркий — сработает.
 *
 * Без обратной связи человек либо не доводит движение, либо доводит
 * с запасом; и то и другое утомляет.
 *
 * ── 1.3.4: фон сплошной, обратная связь — в значке ─────────────────────────
 * Было: прозрачность нарастала у фона. Полупрозрачный зелёный поверх
 * карточки — уже другой, бледный зелёный: в приложении было три оттенка
 * «звонка», а синяя панель «Написать» бледнее основного синего (аудит,
 * «Дизайн-система»). Стало: фон — сплошной цвет действия (тот же CallGreen
 * и MessageBlue, что везде), а «засчитается ли» сообщает значок: проявляется
 * и подрастает с 75% до полного размера по мере сдвига.
 */
@Composable
private fun DirectActionHint(
    action: RevealAction,
    progress: Float,
    alignment: Alignment,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.background(action.color),
        contentAlignment = alignment
    ) {
        action.icon?.let { icon ->
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f + 0.55f * progress),
                modifier = Modifier
                    .padding(horizontal = Space.xl)
                    .size(24.dp)
                    .graphicsLayer {
                        val scale = 0.75f + 0.25f * progress
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

/**
 * ДОПУСТИМЫЙ НАКЛОН СВАЙПА — ТАНГЕНС 20°.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО БЫЛО И ПОЧЕМУ ЭТО НЕ РАБОТАЛО
 * ══════════════════════════════════════════════════════════════════════════
 * Раньше здесь стоял множитель 1.5: жест считался прокруткой, только
 * если вертикальная составляющая превышала горизонтальную в полтора
 * раза. То есть свайпом признавалось всё вплоть до 56° от горизонтали —
 * практически диагональ.
 *
 * На практике это давало ложные срабатывания: палец при листании идёт
 * по дуге (большой палец вращается вокруг сустава), и в середине
 * движения наклон легко доходит до 45°. Строка уезжала вбок там,
 * где человек просто листал.
 *
 * ── Как теперь ─────────────────────────────────────────────────────────────
 * Значение — тангенс предельного угла: 0.36 ≈ tg 20°. Жест признаётся
 * свайпом, пока вертикальное смещение не превышает 36% горизонтального,
 * то есть в пределах 20° от горизонтали. Всё остальное отдаётся списку.
 *
 * Двадцать градусов — не догма, но заметно уже прежних пятидесяти шести:
 * настолько отклоняется палец при осознанном движении вбок, и настолько
 * не отклоняется при листании.
 *
 * ⚠️ Меняя число, помните: это ТАНГЕНС, а не множитель. Больше —
 * шире сектор свайпа и больше ложных срабатываний; меньше — свайп
 * начнёт требовать линейки.
 */
private const val MAX_SWIPE_TANGENT = 0.36f
