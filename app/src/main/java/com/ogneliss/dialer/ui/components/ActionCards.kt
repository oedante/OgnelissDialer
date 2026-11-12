package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.ScaledWindow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ДЕЙСТВИЯ ПО ДОЛГОМУ НАЖАТИЮ — КАРТОЧКАМИ, КАК В OGNELISS MAIL (1.5.7).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ОТКУДА
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 22.10 (снимки Mail): «действия по длинному нажатию в журнале
 * и контактах сделать аналогично Ogneliss Mail — в виде открытия
 * карточек». Так же устроено меню сообщения в Telegram: список за ним
 * размывается, нажатая запись «поднимается» карточкой, под ней —
 * действия.
 *
 * ── Что на экране ─────────────────────────────────────────────────────────
 * 1. Карточка записи — кто это: портрет, имя, номер, последний звонок,
 *    заметка, все номера контакта. Нажатие на неё открывает карточку
 *    абонента (как в Telegram нажатие на поднятое сообщение).
 * 2. Карточка быстрых действий — до четырёх круглых кнопок и «Ещё».
 * 3. «Ещё» раскрывает третью карточку: действия разделами с подписями,
 *    по пять в ряд. Повторное нажатие сворачивает.
 *
 * Стопка встаёт там, где было нажатие (запись как будто приподнялась),
 * но целиком помещается на экране: раскрыли «Ещё» — стопка плавно
 * поднимается, если снизу не хватает места; не хватает и так —
 * прокручивается. Касание мимо карточек и «Назад» закрывают.
 *
 * ── Почему отдельное окно ─────────────────────────────────────────────────
 * Затемнение должно лечь и на строку состояния, и на нижнюю панель —
 * поверх всего экрана; изнутри экрана журнала до них не дотянуться.
 * Размытие под окном рисует корневой экран (Modifier.blurBehindCards
 * по LocalCardsBlur, с 1.8.0; до того — каждый экран свой список):
 * размыть чужое окно надёжно нельзя — размытие между окнами есть
 * не на всех телефонах.
 *
 * ── Почему координаты экрана, а не окна ───────────────────────────────────
 * Место нажатия измеряется в окне приложения, а карточки рисуются в окне
 * диалога. Совпадают ли их начала, зависит от версии Android и прошивки;
 * координаты экрана у обоих одни.
 */
@Immutable
data class CardAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    /** Цвет значка: красный у разрушительных, синий у «снять». */
    val tint: Color? = null,
    /** Цвет круга: зелёный у вызова; по умолчанию — светло-голубой, как в Mail. */
    val container: Color? = null,
    /** Подпись красным — «Удалить» в Mail тоже красный. */
    val danger: Boolean = false,
    /**
     * Не закрывать карточки после нажатия: действие спросит подтверждение
     * (удаление контакта, блокировка по маске) или закроет их само.
     */
    val keepOpen: Boolean = false,
    /**
     * Долгое нажатие (1.8.0): у «Позвонить» и «SMS» — выбор SIM. Карточки
     * после него закрываются: выбор карты открывается своими карточками.
     */
    val onLongClick: (() -> Unit)? = null,
    /** Что скажет TalkBack о долгом нажатии: «Выбрать SIM». */
    val longClickLabel: String? = null
)

@Immutable
data class CardActionSection(val title: String, val actions: List<CardAction>)

@Composable
fun ActionCardsOverlay(
    /**
     * Где было нажатие — по вертикали, в координатах ЭКРАНА
     * (TouchAnchor.screenY). null — стопка встаёт на пятую часть высоты.
     */
    anchorY: Float?,
    onDismiss: () -> Unit,
    /** Быстрые действия — до четырёх; «Ещё» добавляется само, если есть [sections]. */
    quick: List<CardAction>,
    sections: List<CardActionSection>,
    /** Нажатие на карточку записи; null — карточка не нажимается. */
    onPreviewClick: (() -> Unit)? = null,
    previewDescription: String? = null,
    preview: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    CardsOverlay(anchorY = anchorY, onDismiss = onDismiss) { close ->
        ActionCard(
            onClick = onPreviewClick?.let { open -> { open(); close() } },
            description = previewDescription
        ) {
            Column(Modifier.padding(PREVIEW_PADDING), content = preview)
        }

        ActionCard {
            QuickRow(
                quick = quick,
                hasMore = sections.isNotEmpty(),
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onAction = { action -> perform(action, close) },
                onLongAction = { action -> performLong(action, close) }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            ActionCard {
                Column(Modifier.padding(vertical = Space.s)) {
                    sections.forEachIndexed { index, section ->
                        if (index > 0) {
                            OgnelissDivider(
                                Modifier.padding(horizontal = Space.l, vertical = Space.xs)
                            )
                        }
                        SectionCaption(
                            section.title,
                            Modifier.padding(
                                start = Space.l + Space.xs,
                                top = Space.s,
                                bottom = Space.xxs
                            )
                        )
                        ActionGrid(
                            section.actions,
                            onAction = { action -> perform(action, close) },
                            onLongAction = { action -> performLong(action, close) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * ОКНО КАРТОЧЕК — ОБЩАЯ ОБОЛОЧКА (1.8.0).
 *
 * Вынесено из ActionCardsOverlay, когда тем же видом понадобилось
 * показать выбор SIM (автор: «панели выбора SIM лучше сделать не
 * выезжающими снизу, а как при долгом нажатии на карточку в журнале
 * или контакт. Пользователям такое нравится»). Здесь — окно поверх
 * всего экрана, своё затемнение, стопка у места нажатия, появление
 * и угасание; что в карточках — решает [content]. Ему передаётся
 * close(): закрыть с затуханием.
 */
@Composable
internal fun CardsOverlay(
    anchorY: Float?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    // Закрытие с затуханием: стопка гаснет, потом окно уходит
    val appear = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                appear.animateTo(0f, tween(Motion.QUICK_MS))
                onDismiss()
            }
        }
    }

    // Экран под карточками размывается — пока стопка не начала гаснуть
    val blur = LocalCardsBlur.current
    var holdingBlur by remember { mutableStateOf(false) }
    DisposableEffect(blur) {
        if (blur != null && !holdingBlur) {
            blur.open++
            holdingBlur = true
        }
        onDispose {
            if (blur != null && holdingBlur) {
                blur.open--
                holdingBlur = false
            }
        }
    }
    LaunchedEffect(closing) {
        if (closing && blur != null && holdingBlur) {
            blur.open--
            holdingBlur = false
        }
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ScaledWindow {
            // Своё затемнение вместо системного: оно проявляется вместе
            // с карточками, а не вспышкой до них
            val view = LocalView.current
            SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }

            var stackHeight by remember { mutableIntStateOf(0) }
            var rootScreenTop by remember { mutableStateOf<Float?>(null) }

            val density = LocalDensity.current
            val insets = WindowInsets.safeDrawing
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { rootScreenTop = it.positionOnScreen().y }
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA * appear.value))
                    .pointerInput(Unit) { detectTapGestures { close() } }
            ) {
                val screenH = constraints.maxHeight
                val topInset = insets.getTop(density)
                val bottomInset = insets.getBottom(density)
                val margin = with(density) { STACK_MARGIN.roundToPx() }
                val minTop = topInset + margin
                val stackMaxHeight = (screenH - topInset - bottomInset - 2 * margin).coerceAtLeast(0)
                val origin = rootScreenTop
                val desiredTop = if (anchorY != null && origin != null) {
                    (anchorY - origin - with(density) { ANCHOR_LIFT.toPx() }).roundToInt()
                } else {
                    (screenH * 0.2f).roundToInt()
                }
                val maxTop = (screenH - bottomInset - margin - stackHeight).coerceAtLeast(minTop)
                val targetTop = desiredTop.coerceIn(minTop, maxTop)

                // Первое место — сразу, дальше (раскрыли «Ещё») — плавно
                val top = remember { Animatable(0f) }
                var placed by remember { mutableStateOf(false) }
                LaunchedEffect(targetTop, stackHeight, origin) {
                    if (stackHeight == 0 || (anchorY != null && origin == null)) return@LaunchedEffect
                    if (!placed) {
                        top.snapTo(targetTop.toFloat())
                        placed = true
                    } else {
                        top.animateTo(targetTop.toFloat(), tween(Motion.NORMAL_MS))
                    }
                }
                LaunchedEffect(placed) {
                    if (placed && !closing) appear.animateTo(1f, tween(Motion.QUICK_MS))
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, top.value.roundToInt()) }
                        .heightIn(max = with(density) { stackMaxHeight.toDp() })
                        .onSizeChanged { stackHeight = it.height }
                        .graphicsLayer {
                            // До первой расстановки стопка невидима: иначе на кадр
                            // мелькнула бы у верхнего края
                            val shown = if (placed) appear.value else 0f
                            alpha = shown
                            val scale = 0.94f + 0.06f * shown
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CARD_INSET),
                    verticalArrangement = Arrangement.spacedBy(CARD_GAP)
                ) {
                    content(close)
                }
            }
        }
    }
}

/** Действие сначала выполняется, потом карточки гаснут — звонок не ждёт анимации. */
private fun perform(action: CardAction, close: () -> Unit) {
    action.onClick()
    if (!action.keepOpen) close()
}

/** Долгое нажатие: выбор SIM открывается своими карточками — эти закрываются. */
private fun performLong(action: CardAction, close: () -> Unit) {
    action.onLongClick?.invoke() ?: return
    close()
}

/**
 * СКОЛЬКО ОКОН КАРТОЧЕК ОТКРЫТО — ДЛЯ РАЗМЫТИЯ В КОРНЕ (1.8.0).
 *
 * Карточками теперь открываются не только действия по долгому нажатию,
 * но и выбор SIM, выбор номера, напоминание и заметка — из любого экрана.
 * Раньше каждый экран размывал свой список сам; теперь окно карточек
 * отмечается здесь, а размывает весь экран один хозяин — RootScreen.kt.
 */
@Stable
class CardsBlurState {
    var open by mutableIntStateOf(0)
}

/** null — вне корневого экрана (экран вызова, превью): размытия нет, только затемнение. */
val LocalCardsBlur = staticCompositionLocalOf<CardsBlurState?> { null }

/**
 * Размытие экрана под карточками (Android 12+; на старых — только
 * затемнение окна). Плавно — вместе с появлением карточек.
 */
@Composable
fun Modifier.blurBehindCards(open: Boolean): Modifier {
    val radius by animateDpAsState(
        if (open) BLUR_RADIUS else 0.dp,
        tween(Motion.QUICK_MS),
        label = "cardsBlur"
    )
    return if (radius > 0.dp) this.blur(radius) else this
}

/**
 * ГДЕ ЧЕЛОВЕК КОСНУЛСЯ СПИСКА ПОСЛЕДНИЙ РАЗ — ДЛЯ МЕСТА КАРТОЧЕК.
 *
 * Строка журнала сообщает о долгом нажатии, но не о том, где она стоит;
 * а следить за положением каждой строки — лишняя работа на каждом кадре
 * прокрутки. Достаточно запомнить касание: долгое нажатие — это касание,
 * которое не отпустили.
 *
 * Обычное поле, а не состояние Compose: менять его на каждое касание
 * и перерисовывать экран незачем — читается оно в миг долгого нажатия.
 */
@Stable
class TouchAnchor {
    internal var originY = 0f
    internal var lastY: Float? = null

    /** Вертикаль последнего касания в координатах экрана; null — касаний не было. */
    val screenY: Float? get() = lastY
}

@Composable
fun rememberTouchAnchor(): TouchAnchor = remember { TouchAnchor() }

/**
 * Последнее касание в окне приложения (1.8.0) — его следит RootScreen.
 * Нужно карточкам, которые открываются не из списка с собственным
 * якорем: выбор номера, выбор SIM — они встают у места нажатия.
 * null — вне корневого экрана (превью, тесты): стопка на пятой части высоты.
 */
val LocalTouchAnchor = staticCompositionLocalOf<TouchAnchor?> { null }

/** Следит за касаниями, ничего не перехватывая: список работает как прежде. */
fun Modifier.trackTouch(anchor: TouchAnchor): Modifier = this
    .onGloballyPositioned { anchor.originY = it.positionOnScreen().y }
    .pointerInput(anchor) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }?.let {
                    anchor.lastY = anchor.originY + it.position.y
                }
            }
        }
    }

/** Белая карточка стопки; касание по ней не закрывает окно. */
@Composable
internal fun ActionCard(
    onClick: (() -> Unit)? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaces = OgnelissTheme.surfaces
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(surfaces.card)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { description?.let { contentDescription = it } }
                } else {
                    // Поглощает касание: иначе его получил бы фон и закрыл окно
                    Modifier.pointerInput(Unit) { detectTapGestures { } }
                }
            ),
        content = content
    )
}

@Composable
private fun QuickRow(
    quick: List<CardAction>,
    hasMore: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: (CardAction) -> Unit,
    onLongAction: (CardAction) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xs, vertical = Space.m)
    ) {
        quick.take(QUICK_MAX).forEach { action ->
            ActionCircle(
                action,
                Modifier.weight(1f),
                onLongClick = action.onLongClick?.let { { onLongAction(action) } }
            ) { onAction(action) }
        }
        if (hasMore) {
            val more = CardAction(
                icon = if (expanded) OgnelissIcons.ExpandLess else OgnelissIcons.MoreVert,
                label = stringResource(R.string.action_more),
                onClick = onToggle
            )
            ActionCircle(
                more,
                Modifier.weight(1f),
                selected = expanded,
                // Три точки — лёжа, как «Ещё» в Mail
                iconRotation = if (expanded) 0f else 90f,
                description = stringResource(
                    if (expanded) R.string.a11y_cards_collapse else R.string.a11y_cards_expand
                ),
                onClick = onToggle
            )
        }
    }
}

/**
 * Ряд круглых действий для других окон-карточек (1.8.0): окно после
 * звонка — «В контакты», «Напомнить», «Блок». Тот же вид, что быстрые
 * действия по долгому нажатию; карточки закрываются после действия,
 * если оно не просит остаться ([CardAction.keepOpen]).
 */
@Composable
internal fun CardActionsRow(actions: List<CardAction>, close: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xs, vertical = Space.m)
    ) {
        actions.take(GRID_COLUMNS).forEach { action ->
            ActionCircle(
                action,
                Modifier.weight(1f),
                onLongClick = action.onLongClick?.let { { performLong(action, close) } }
            ) { perform(action, close) }
        }
    }
}

/** Действия раздела — по пять в ряд, как в Mail. */
@Composable
private fun ActionGrid(
    actions: List<CardAction>,
    onAction: (CardAction) -> Unit,
    onLongAction: (CardAction) -> Unit
) {
    Column(Modifier.padding(horizontal = Space.xs)) {
        actions.chunked(GRID_COLUMNS).forEach { rowActions ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xs)
            ) {
                rowActions.forEach { action ->
                    ActionCircle(
                        action,
                        Modifier.weight(1f),
                        onLongClick = action.onLongClick?.let { { onLongAction(action) } }
                    ) { onAction(action) }
                }
                repeat(GRID_COLUMNS - rowActions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Круглая кнопка с подписью: круг 48 dp светло-голубой (как в Mail),
 * значок, подпись до двух строк.
 */
@Composable
internal fun ActionCircle(
    action: CardAction,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    iconRotation: Float = 0f,
    description: String? = null,
    /** Своё содержимое круга вместо значка — фото «Моего контакта» в «Ещё» (2.1.0). */
    circleContent: (@Composable () -> Unit)? = null,
    /** Долгое нажатие — выбор SIM у «Позвонить» и «SMS» (1.8.0); null — нет. */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = OgnelissTheme.surfaces
    // Светло-голубой круг: подложка активной кнопки, ослабленная по карточке
    val circle = action.container ?: surfaces.navIndicator
        .copy(alpha = if (selected) 1f else CIRCLE_ALPHA)
        .compositeOver(surfaces.card)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickableCompat(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = action.longClickLabel
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description ?: action.label
                role = Role.Button
            }
            .padding(vertical = Space.xs, horizontal = Space.xxs)
    ) {
        Box(
            Modifier
                .size(CIRCLE_SIZE)
                .clip(CircleShape)
                .background(circle),
            contentAlignment = Alignment.Center
        ) {
            if (circleContent != null) circleContent()
            else Icon(
                action.icon,
                contentDescription = null,
                tint = action.tint ?: scheme.onSurface,
                modifier = Modifier.rotate(iconRotation)
            )
        }
        Spacer(Modifier.height(Space.xs + Space.xxs))
        /*
         * FitText, а не Text: в ряду по пять колонка ≈ 70 dp, и «Копировать»,
         * «Поделиться» при крупном шрифте в неё не влезают. Text с maxLines = 2
         * рвал бы слово посередине — «Поделитьс / я», как в карточке лёжа
         * в 1.3.3. FitText уменьшает шрифт, пока самое длинное слово
         * не поместится (разбор в FitText.kt).
         */
        FitText(
            action.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (action.danger) action.tint ?: scheme.error else scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/** Затемнение под карточками — как в Mail (≈ 30 % чёрного поверх размытия). */
private const val SCRIM_ALPHA = 0.32f
private val BLUR_RADIUS = 14.dp
/** Поля стопки от краёв экрана и зазор между карточками — по снимкам Mail. */
private val CARD_INSET = 12.dp
private val CARD_GAP = 12.dp
private val STACK_MARGIN = 16.dp
private val PREVIEW_PADDING = 16.dp
/** Верх стопки — чуть выше точки нажатия: портрет карточки встаёт на место портрета строки. */
private val ANCHOR_LIFT = 44.dp
private val CIRCLE_SIZE = 48.dp
/** Прозрачность голубого круга: у Mail он светлее подложки активной кнопки. */
private const val CIRCLE_ALPHA = 0.6f
private const val QUICK_MAX = 4
private const val GRID_COLUMNS = 5
