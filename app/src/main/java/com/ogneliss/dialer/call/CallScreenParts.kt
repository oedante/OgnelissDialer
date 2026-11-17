package com.ogneliss.dialer.call

import com.ogneliss.dialer.ui.theme.PanelColors
import androidx.compose.foundation.border
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxScope
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.ui.theme.FlowUi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout

/*
 * Общий вид экранов вызова (1.3.4, 04.10.2026): фото звонящего на весь
 * экран, светлый текст поверх, светлые значки строки состояния.
 * Пользуются InCallActivity (настоящий вызов) и FakeCallActivity
 * (псевдозвонок) — псевдозвонок должен выглядеть как настоящий входящий,
 * иначе подделку видно с первого взгляда.
 */

/**
 * Фото звонящего под всем экраном (1.3.4). Затемнение сверху сильнее:
 * там имя, состояние и значки строки состояния; снизу — слегка, под край
 * панели. Фото — тот же PHOTO_URI, что у круглого портрета.
 */
@Composable
internal fun CallPhotoBackground(photoUri: String) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.60f),
                        0.45f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Black.copy(alpha = 0.35f)
                    )
                )
        )
    }
}

/**
 * ФОТО ПО ШИРИНЕ ЭКРАНА, ЦЕЛИКОМ (2.6.0, CallPhotoStyle.WIDTH — форум,
 * oleg_p: «на весь экран значительная часть изображения съедается»).
 *
 * Квадрат со стороной [side] сверху, до самого края экрана и под строкой
 * состояния: фото контакта квадратное, и в квадрате оно видно целиком.
 * Сверху — лёгкое затемнение под часы и значки (как у фото на весь экран),
 * снизу фото плавно уходит в цвет страницы: края нет, и имя под ним стоит
 * на ровном фоне, в цветах темы. Фон страницы рисует PlainCallBackground —
 * здесь только фото. Сторону считает экран вызова (photoTopSide): ширина
 * экрана, но не больше, чем оставляют имя и кнопки, — на низком экране
 * квадрат меньше и стоит по центру.
 */
@Composable
internal fun BoxScope.CallPhotoTop(photoUri: String, side: Dp) {
    if (side <= 0.dp) return
    val page = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .size(side)
    ) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.86f to Color.Transparent,
                        1f to page
                    )
                )
        )
    }
}

/**
 * Сколько фото «по ширине» уходит под имя (2.6.0): нижняя полоса, где фото
 * уже почти растворилось в фоне. Имя начинается на ней — без пустой
 * полосы между фото и именем.
 */
internal val PHOTO_TOP_OVERLAP = 24.dp

/** Меньше этого фото по ширине не показывается — место отдаётся имени (2.6.0). */
internal val PHOTO_TOP_MIN = 120.dp

/**
 * Сторона фото «по ширине» (2.6.0): ширина экрана [screenWidth], но не
 * больше места над кнопками. [topArea] — высота верхней части экрана
 * под строкой состояния (над кнопками), [statusBar] — высота строки
 * состояния (фото уходит под неё), [identity] — высота имени и подписей,
 * [gap] — отступ под ними. Меньше [min] — фото не показывается: имя
 * важнее.
 */
internal fun photoTopSide(
    screenWidth: Dp,
    topArea: Dp,
    statusBar: Dp,
    identity: Dp,
    gap: Dp,
    min: Dp
): Dp {
    if (topArea <= 0.dp) return 0.dp
    val byHeight = topArea + statusBar + PHOTO_TOP_OVERLAP - identity - gap
    val side = minOf(screenWidth, byHeight)
    return if (side < min) 0.dp else side
}

/**
 * Светлые цвета для блока «кто звонит» поверх фото (1.3.4).
 * Подмена цветов темы, а не правка CallerIdentity и CallStatusLine:
 * они берут onSurface, onSurfaceVariant и outline — здесь те же роли
 * становятся светлыми, а фоны плашек (surfaceContainer*) — затемнением.
 * Шрифты и формы — прежние.
 */
@Composable
internal fun OnPhotoColors(
    enabled: Boolean,
    /**
     * Цвет текста (2.7.6): над фото на весь экран — белый, над своим снимком —
     * белый или почти чёрный по яркости снимка (customCallTextColor).
     */
    text: Color = Color.White,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val scheme = MaterialTheme.colorScheme
    // Под тёмным текстом плашки светлые, под белым — затемнение, как было
    val darkText = text.luminance() < 0.5f
    val veil = if (darkText) Color.White else Color.Black
    MaterialTheme(
        colorScheme = scheme.copy(
            onSurface = text,
            onSurfaceVariant = text.copy(alpha = 0.85f),
            outline = text.copy(alpha = 0.75f),
            primary = text,
            /*
             * Плашки («Соединение…») — затемнение, а не светлый контейнер
             * темы (05.10.2026, скриншот автора): белый текст на светлой
             * плашке поверх фото был почти не виден.
             */
            surfaceContainerHighest = veil.copy(alpha = if (darkText) 0.55f else 0.40f),
            surfaceContainerHigh = veil.copy(alpha = if (darkText) 0.50f else 0.35f),
            surfaceContainer = veil.copy(alpha = if (darkText) 0.45f else 0.30f)
        ),
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography
    ) {
        CompositionLocalProvider(
            LocalContentColor provides text,
            LocalCallTextOnImage provides text,
            content = content
        )
    }
}

/**
 * ТЕКСТ ПОВЕРХ СНИМКА (2.7.6): цвет текста, когда под ним фото на весь экран
 * или свой снимок; null — обычный фон. По нему номер становится крупнее
 * и чётче, а под имя и номер ложится мягкая тень (форум, Saxena1: «сделайте
 * цифры номера чёткими и крупными, когда установлены собственные обои»).
 */
internal val LocalCallTextOnImage = staticCompositionLocalOf<Color?> { null }

/** Тень под текстом поверх снимка: тёмная под белым, светлая под тёмным; без снимка — нет. */
@Composable
internal fun callTextShadow(): androidx.compose.ui.graphics.Shadow? = LocalCallTextOnImage.current?.let { text ->
    androidx.compose.ui.graphics.Shadow(
        color = (if (text.luminance() < 0.5f) Color.White else Color.Black).copy(alpha = 0.45f),
        offset = androidx.compose.ui.geometry.Offset(0f, 1.5f),
        blurRadius = 6f
    )
}

/**
 * Номер под именем — один для настоящего и пробного вызова. Над снимком
 * (2.7.6) — 18 sp, средней насыщенности, цветом текста и с тенью; было —
 * 16 sp бледно-серым (outline), на ярком снимке его не было видно.
 */
@Composable
internal fun CallNumberText(text: String) {
    val onImage = LocalCallTextOnImage.current
    androidx.compose.material3.Text(
        text,
        style = androidx.compose.material3.LocalTextStyle.current.copy(
            fontSize = if (onImage != null) CALL_NUMBER_ON_IMAGE else FlowUi.callNumberSize,
            fontWeight = if (onImage != null) androidx.compose.ui.text.font.FontWeight.Medium else null,
            shadow = callTextShadow()
        ),
        color = onImage ?: MaterialTheme.colorScheme.outline,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

private val CALL_NUMBER_ON_IMAGE = 18.sp

/** Почти чёрный текст над светлым снимком: чистый чёрный режет глаз. */
private val CALL_TEXT_DARK = Color(0xFF15171A)

/**
 * Цвет текста над своим снимком (2.7.6): белый или почти чёрный — что
 * контрастнее снимку под именем с подложкой цвета страницы
 * (CallBackgroundLayer.darkTextOver). null — снимка нет.
 */
@Composable
internal fun customCallTextColor(bg: CustomCallBackground?): Color? {
    bg ?: return null
    val page = MaterialTheme.colorScheme.surface.luminance().toDouble()
    return if (CallBackgroundLayer.darkTextOver(bg.luminance, page)) CALL_TEXT_DARK else Color.White
}

/**
 * КНОПКИ ВЫЗОВА БЕЗ ПОДЛОЖКИ — НАД ЖИВЫМ ФОНОМ (1.6.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 22.10: «Можем на экране звонка не делать подложку под кнопки,
 * а чтобы там тоже была анимация?» Серая панель закрывала нижнюю треть
 * экрана, и волны с пятнами живого фона обрывались о её край.
 *
 * Теперь при живом фоне панель прозрачна: волны идут и под кнопками.
 *
 * С 1.6.2 — и на ровном фоне, когда живой фон выключен (отзыв в Google
 * Play: «уберите панель под кнопками даже на чёрном AMOLED-экране, как
 * вы это сделали с экраном живого звонка»): серая полоса на чёрном
 * выглядела заплаткой. Белые кнопки на светлом ровном фоне держатся
 * тем же ободком и тенью, что и над живым фоном. Панель осталась только
 * под фото на весь экран — без неё на снимке терялись бы подписи —
 * и в высококонтрастной теме, где границы важнее вида.
 *
 * ── Чем кнопки держатся на фоне ────────────────────────────────────────────
 * Белая кнопка на почти белом фоне — 1,05 : 1, её не видно. Поэтому над
 * живым фоном у кнопок и карточек (клавиши тонового набора, второй вызов,
 * конференция, дорожка ответа) — лёгкая тень и тонкий ободок цвета
 * outlineVariant: край виден в светлой теме, а в тёмной кнопка и так
 * светлее фона. Решает [LocalControlsOnAmbient]; на панели — как было.
 */
internal val LocalControlsOnAmbient = staticCompositionLocalOf { false }

/** Край кнопки или карточки над живым фоном: разбор у [LocalControlsOnAmbient]. */
internal fun Modifier.ambientEdge(enabled: Boolean, shape: Shape, ring: Color): Modifier =
    if (!enabled) this
    else this
        .shadow(AMBIENT_EDGE_SHADOW, shape, clip = false)
        .border(AMBIENT_EDGE_RING, ring, shape)

private val AMBIENT_EDGE_SHADOW = 2.dp
private val AMBIENT_EDGE_RING = 1.dp

/**
 * Значки строки состояния — светлые, пока под ними фото (1.3.4): в светлой
 * теме они тёмные и на затемнённом фото терялись бы. При уходе фото —
 * как было.
 */
@Composable
internal fun LightStatusIconsOnPhoto(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        if (enabled) controller?.isAppearanceLightStatusBars = false
        onDispose {
            // before получен через controller?. — раз он не null, Kotlin знает,
            // что и controller не null (предупреждение компилятора 05.10)
            if (before != null) controller.isAppearanceLightStatusBars = before
        }
    }
}

/**
 * РОВНЫЙ ФОН ЭКРАНА ВЫЗОВА БЕЗ ПАНЕЛИ (1.6.2).
 *
 * Живой фон выключен, фото не на весь экран, панели под кнопками нет:
 * цвет страницы тянется до краёв — под строкой состояния и навигацией
 * тоже. Иначе там проступал бы фон окна (#121316 в тёмной теме), а на
 * чёрном AMOLED это две разные полосы.
 */
@Composable
internal fun BoxScope.PlainCallBackground() {
    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
}

/**
 * СВОЙ СНИМОК ВМЕСТО ФОНА ВЫЗОВА (2.7.4, CallBackgrounds): во весь экран,
 * под строкой состояния и навигацией тоже, обрезкой по краям. Поверх —
 * вертикальный градиент цвета страницы темы (тот же, что у PlainCallBackground):
 * сверху 0,60 — под имя и состояние, в середине 0,30 — снимок виден, снизу
 * 0,70 — под кнопки без панели. На любом снимке текст темы остаётся читаемым,
 * потому что под ним всегда не меньше трети цвета страницы.
 */
@Composable
internal fun BoxScope.CallCustomBackground(image: androidx.compose.ui.graphics.ImageBitmap) {
    val page = MaterialTheme.colorScheme.surface
    androidx.compose.foundation.Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
    )
    Box(
        Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    0f to page.copy(alpha = 0.60f),
                    0.5f to page.copy(alpha = 0.30f),
                    1f to page.copy(alpha = 0.70f)
                )
            )
    )
}

/**
 * Свой снимок фона вызова — загруженный, по отметке (2.7.4). [mark] 0 — снимка
 * нет (или высокий контраст: там фон ровный ради различимости). Пока снимок
 * читается с диска (IO), возвращает null — экран рисует прежний фон, без
 * мигания пустотой; не прочитался — тоже null (причина в журнале).
 */
@Composable
internal fun rememberCustomCallBackground(
    kind: CallBackgrounds.Kind,
    mark: Long
): CustomCallBackground? {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val image by androidx.compose.runtime.produceState<CustomCallBackground?>(null, kind, mark) {
        value = if (mark <= 0L) null else {
            CallBackgrounds.load(context, kind)?.let { bitmap ->
                CustomCallBackground(bitmap.asImageBitmap(), CallBackgrounds.textBandLuminance(bitmap))
            }
        }
    }
    return image
}

/** Свой снимок и яркость его полосы под именем и номером (2.7.6). */
internal class CustomCallBackground(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val luminance: Double
)

/**
 * Полоса цвета нижней панели под системной навигацией (1.3.5, 05.10.2026,
 * скриншот автора: в тёмной теме под кнопками ◁ ○ □ просвечивало фото —
 * панель кончалась над навигацией). Высота — системный отступ навигации:
 * лёжа, когда навигация сбоку, полоса нулевой высоты.
 */
@Composable
internal fun BoxScope.NavBarUnderPanel() {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(PanelColors.panel())
    )
}

/**
 * ПОРТРЕТ И ИМЯ — БЛИЖЕ К СЕРЕДИНЕ ЭКРАНА (1.7.0).
 *
 * Автор, 24.10, снимок разговора: «портрет при вызове опустим вниз ближе
 * к центру экрана, а то совсем пусто в середине получается». Блок стоял
 * под строкой состояния, а между ним и кнопками — полэкрана пустоты.
 * Теперь лишнее место делится 40 на 60: блок чуть выше середины — там,
 * где глаз ищет лицо. Мало места (раскрыта клавиатура тонов, маленький
 * экран) — смещения нет, и всё как раньше.
 */
internal const val CALL_IDENTITY_BIAS = 0.4f

/**
 * ПОРТРЕТ И ПОДПИСИ ПОД НИМ — РАЗМЕР ПОРТРЕТА ПО МЕСТУ (1.5.6).
 *
 * Сначала меряются подписи (имя, номер, состояние), потом портрету
 * отдаётся меньшее из: [widthFraction] ширины, оставшейся высоты
 * и [maxSize]. Меньше [minSize] — портрета нет: имя важнее лица, по нему
 * решают, брать ли трубку. Высота считается при каждой раскладке, поэтому
 * при раскрытии клавиатуры тонов портрет уменьшается вместе с местом,
 * кадр за кадром её анимации.
 *
 * [portrait] получает точный квадрат — свой размер внутри не важен.
 */
@Composable
internal fun AdaptivePortraitColumn(
    portrait: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    gap: Dp = Space.l,
    minSize: Dp = FlowUi.callPortraitMin,
    maxSize: Dp = FlowUi.callPortraitMax,
    widthFraction: Float = FlowUi.CALL_PORTRAIT_WIDTH_FRACTION,
    /**
     * Где стоит блок по высоте, когда места больше, чем нужно (1.7.0):
     * 0 — вверху (как было), 0,5 — посередине. Разбор — CALL_IDENTITY_BIAS.
     */
    verticalBias: Float = 0f,
    text: @Composable () -> Unit
) {
    val portraitContent: @Composable () -> Unit = portrait ?: {}
    Layout(
        contents = listOf(portraitContent, text),
        modifier = modifier
    ) { (portraitMeasurables, textMeasurables), constraints ->
        val textPlaceables = textMeasurables.map {
            it.measure(Constraints(maxWidth = constraints.maxWidth))
        }
        val textHeight = textPlaceables.sumOf { it.height }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth
        else textPlaceables.maxOfOrNull { it.width } ?: 0
        val gapPx = gap.roundToPx()

        var size = 0
        if (portrait != null && portraitMeasurables.isNotEmpty()) {
            val byWidth = (width * widthFraction).toInt()
            val byHeight = if (constraints.hasBoundedHeight) {
                constraints.maxHeight - textHeight - gapPx
            } else {
                Int.MAX_VALUE
            }
            size = minOf(byWidth, byHeight, maxSize.roundToPx())
            if (size < minSize.roundToPx()) size = 0
        }
        val portraitPlaceables = if (size > 0) {
            portraitMeasurables.map { it.measure(Constraints.fixed(size, size)) }
        } else {
            emptyList()
        }

        val contentHeight = (if (size > 0) size + gapPx else 0) + textHeight
        val height = if (constraints.hasBoundedHeight) {
            // Со смещением блок занимает всё место и сам встаёт по высоте
            if (verticalBias > 0f) constraints.maxHeight
            else contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        } else {
            contentHeight.coerceAtLeast(constraints.minHeight)
        }
        layout(width, height) {
            var y = ((height - contentHeight) * verticalBias).toInt().coerceAtLeast(0)
            portraitPlaceables.forEach { it.placeRelative((width - it.width) / 2, y) }
            if (size > 0) y += size + gapPx
            textPlaceables.forEach {
                it.placeRelative((width - it.width) / 2, y)
                y += it.height
            }
        }
    }
}
