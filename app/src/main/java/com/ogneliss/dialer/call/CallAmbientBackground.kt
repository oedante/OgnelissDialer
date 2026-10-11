package com.ogneliss.dialer.call

import androidx.compose.animation.core.animateFloatAsState
import android.provider.Settings
import android.telecom.Call
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ogneliss.dialer.ui.components.Motion
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ЖИВОЙ ФОН ЭКРАНА ВЫЗОВА (1.5.1, автор: «лёгкая и плавная анимация
 * со спокойными цветами темы, которую потянут даже слабые телефоны —
 * типа живых обоев»).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО НА ЭКРАНЕ
 * ══════════════════════════════════════════════════════════════════════════
 * Три мягких пятна цветов темы плывут за содержимым экрана вызова,
 * от имени звонящего расходятся тонкие кольца — как круги по воде.
 * Цвет говорит о состоянии вызова, не требуя чтения:
 *
 *   входящий, соединение — синие пятна (primary), кольца заметные
 *                          и частые (4,2 с); входящий плывёт быстрее;
 *   разговор             — зелёное с синим (tertiary — зелёный вызовов
 *                          в схеме), кольца едва видны и реже (8 с);
 *   удержание            — серые (secondary), без колец;
 *   завершён             — гаснут.
 *
 * Смена — плавная (300 мс). Акценты с прозрачностью 7–20 %: текст поверх
 * читается как на ровном фоне. Прототип подбора — снимки в
 * docs/RELEASE_NOTES_1.5.1.md, §5.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ЛЁГКИЙ
 * ══════════════════════════════════════════════════════════════════════════
 * • Пятно — одна текстура 96 × 96 с радиальным градиентом, нарисованная
 *   один раз. Каждый кадр — три растянутых копии с подкраской: работа
 *   видеокарты, которую тянет любой телефон. Ни размытия, ни шейдеров
 *   на каждый кадр, ни новых объектов в кадре.
 * • Перерисовывается только слой фона (чтение времени — внутри
 *   drawBehind), экран не пересобирается.
 * • 30 кадров в секунду, а не 60–120: движение медленное (периоды
 *   7–17 с), разницы глазом не видно, работы вдвое меньше. Волны —
 *   ещё одна текстура (256 × 256), три растянутые копии за кадр.
 * • Только пока экран на переднем плане (RESUMED): у уха экран гаснет —
 *   анимация стоит.
 *
 * ── Когда неподвижен или выключен ──────────────────────────────────────────
 * • Системное «Убрать анимацию» (масштаб длительности 0) — пятна стоят,
 *   цвета состояний остаются.
 * • Настройки → Вызовы → «Живой фон вызова» — выключен совсем.
 * • Высокий контраст и фото звонящего на весь экран — не рисуется
 *   (решает вызывающий экран).
 */
enum class AmbientMood { INCOMING, CONNECTING, ACTIVE, HOLDING, ENDED }

/** Настроение фона по состоянию вызова Telecom. */
fun ambientMoodOf(state: Int): AmbientMood = when (state) {
    Call.STATE_RINGING -> AmbientMood.INCOMING
    Call.STATE_ACTIVE -> AmbientMood.ACTIVE
    Call.STATE_HOLDING -> AmbientMood.HOLDING
    Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> AmbientMood.ENDED
    else -> AmbientMood.CONNECTING
}

@Composable
fun CallAmbientBackground(mood: AmbientMood, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    /*
     * ЦВЕТА — АКЦЕНТЫ ТЕМЫ С МАЛОЙ ПРОЗРАЧНОСТЬЮ (1.5.1, второй заход).
     *
     * Первый заход брал контейнерные цвета (primaryContainer и т. п.).
     * На телефоне автора (11.10, снимки) экран вышел ровно-серым: в светлой
     * теме контейнеры почти той же яркости, что фон страницы, а пятна
     * радиусом в высоту экрана перекрывали друг друга и сливались в одну
     * заливку — «живых волн не видно».
     *
     * Теперь — сами акценты (primary, tertiary, secondary) с прозрачностью
     * 7–20 %: оттенок заметен и на светлом, и на тёмном фоне, а текст поверх
     * читается как на ровном. Пятна — по ширине экрана, а не по высоте:
     * между ними виден фон, и движение заметно.
     */
    val (first, second, third) = when (mood) {
        AmbientMood.INCOMING, AmbientMood.CONNECTING -> Triple(
            scheme.primary.copy(alpha = 0.20f),
            scheme.primary.copy(alpha = 0.11f),
            scheme.secondary.copy(alpha = 0.08f)
        )
        AmbientMood.ACTIVE -> Triple(
            scheme.tertiary.copy(alpha = 0.17f),
            scheme.primary.copy(alpha = 0.10f),
            scheme.tertiary.copy(alpha = 0.08f)
        )
        AmbientMood.HOLDING -> Triple(
            scheme.secondary.copy(alpha = 0.12f),
            scheme.secondary.copy(alpha = 0.07f),
            scheme.secondary.copy(alpha = 0.05f)
        )
        AmbientMood.ENDED -> Triple(Color.Transparent, Color.Transparent, Color.Transparent)
    }
    val colorA by animateColorAsState(first, tween(Motion.NORMAL_MS), label = "ambientA")
    val colorB by animateColorAsState(second, tween(Motion.NORMAL_MS), label = "ambientB")
    val colorC by animateColorAsState(third, tween(Motion.NORMAL_MS), label = "ambientC")
    // Подкраска создаётся при смене цвета, а не в каждом кадре
    val tintA = remember(colorA) { ColorFilter.tint(colorA, BlendMode.SrcIn) }
    val tintB = remember(colorB) { ColorFilter.tint(colorB, BlendMode.SrcIn) }
    val tintC = remember(colorC) { ColorFilter.tint(colorC, BlendMode.SrcIn) }

    /*
     * ВОЛНЫ — расходящиеся от имени звонящего кольца С ТОЛЩИНОЙ (1.5.3).
     *
     * Было: тонкая линия 1,5 dp — «слишком чёткие круги» (автор). Стало:
     * мягкая полоса с градиентом поперёк — изнутри длинный подъём от фона
     * к цвету (как тень за гребнем), на гребне полный цвет, наружу короткий
     * спад в фон. Выглядит как волна на воде, а не как обводка.
     *
     * Полоса — одна текстура (softRing), растянутая до радиуса волны:
     * толщина растёт вместе с радиусом, как у настоящей расходящейся волны.
     * Входящий и соединение — заметнее и чаще (зовёт), разговор — едва
     * видны и реже (живёт), удержание и конец — нет.
     */
    val ringStrength by animateFloatAsState(
        when (mood) {
            AmbientMood.INCOMING, AmbientMood.CONNECTING -> 0.34f
            AmbientMood.ACTIVE -> 0.16f
            else -> 0f
        },
        tween(Motion.NORMAL_MS),
        label = "ambientRings"
    )
    val ringPeriod by rememberUpdatedState(
        if (mood == AmbientMood.ACTIVE) RING_PERIOD_CALM else RING_PERIOD_CALL
    )
    // Цвет волны — подкраской текстуры; прозрачность — параметром кадра,
    // так что подкраска создаётся один раз на тему, а не в каждом кадре
    val ringTint = remember(scheme.primary) { ColorFilter.tint(scheme.primary, BlendMode.SrcIn) }
    val ring = remember { softRing(RING_PX) }

    val blob = remember { softBlob(BLOB_PX) }

    // Время движения в секундах. Копится по кадрам, а не считается
    // от часов: при смене скорости (входящий → разговор) пятна
    // не перескакивают
    val motion = remember { mutableFloatStateOf(0f) }
    // Фаза волн — отдельно: у неё свой период, и при его смене кольца
    // не должны прыгать
    val ripple = remember { mutableFloatStateOf(0f) }
    val speed by rememberUpdatedState(if (mood == AmbientMood.INCOMING) 1.3f else 1f)

    val context = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = !animationsOff && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = -1L
        var pending = 0L
        while (true) {
            withFrameMillis { now ->
                if (last >= 0) pending += (now - last).coerceAtMost(MAX_STEP_MS)
                last = now
                if (pending >= FRAME_MS) {
                    val seconds = pending / 1000f
                    motion.floatValue += seconds * speed
                    ripple.floatValue = (ripple.floatValue + seconds / ringPeriod) % 1f
                    pending = 0L
                }
            }
        }
    }

    Spacer(
        modifier
            .fillMaxSize()
            .drawBehind {
                val t = motion.floatValue
                val w = size.width
                drawBlob(
                    blob, tintA,
                    x = 0.30f + 0.28f * wave(t, 11f),
                    y = 0.20f + 0.07f * wave(t, 13f, PHASE_COS),
                    radius = w * 0.95f * (1f + 0.05f * wave(t, 7f))
                )
                drawBlob(
                    blob, tintB,
                    x = 0.75f + 0.25f * wave(t, 14f, 1f + PHASE_COS),
                    y = 0.48f + 0.08f * wave(t, 9f, 2f),
                    radius = w * 0.85f
                )
                drawBlob(
                    blob, tintC,
                    x = 0.45f + 0.30f * wave(t, 17f, 3f),
                    y = 0.76f + 0.05f * wave(t, 15f, PHASE_COS),
                    radius = w * 0.8f
                )

                // Волны: три кольца со сдвигом в треть периода
                val strength = ringStrength
                if (strength > 0f) {
                    val center = Offset(w / 2f, size.height * RING_CENTER_Y)
                    for (i in 0 until RINGS) {
                        val p = (ripple.floatValue + i.toFloat() / RINGS) % 1f
                        val fade = (1f - p) * (1f - p)
                        val radius = w * (0.18f + 1.05f * p)
                        val d = (radius * 2f).roundToInt()
                        drawImage(
                            image = ring,
                            dstOffset = IntOffset(
                                (center.x - radius).roundToInt(),
                                (center.y - radius).roundToInt()
                            ),
                            dstSize = IntSize(d, d),
                            alpha = strength * fade,
                            colorFilter = ringTint
                        )
                    }
                }
            }
    )
}

/** Синус с периодом [period] секунд: плавно от −1 до 1. */
private fun wave(t: Float, period: Float, phase: Float = 0f): Float =
    sin(t * 2f * PI.toFloat() / period + phase)

/** Пятно с центром в долях экрана ([x], [y]) и радиусом в пикселях. */
private fun DrawScope.drawBlob(
    blob: ImageBitmap,
    tint: ColorFilter,
    x: Float,
    y: Float,
    radius: Float
) {
    val d = (radius * 2f).roundToInt()
    drawImage(
        image = blob,
        dstOffset = IntOffset(
            (size.width * x - radius).roundToInt(),
            (size.height * y - radius).roundToInt()
        ),
        dstSize = IntSize(d, d),
        colorFilter = tint
    )
}

/**
 * Мягкое пятно: белое в центре, к краю — в прозрачность. Ступени
 * подобраны «на глаз» под колокол: без резкого края даже при растяжении
 * в 20 раз (сглаживание текстуры при растяжении — линейное).
 */
private fun softBlob(px: Int): ImageBitmap {
    val image = ImageBitmap(px, px)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    val half = px / 2f
    val paint = Paint().apply {
        shader = RadialGradientShader(
            center = Offset(half, half),
            radius = half,
            colors = listOf(
                Color.White,
                Color.White.copy(alpha = 0.75f),
                Color.White.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.1f),
                Color.Transparent
            ),
            colorStops = listOf(0f, 0.3f, 0.6f, 0.82f, 1f)
        )
    }
    canvas.drawRect(0f, 0f, px.toFloat(), px.toFloat(), paint)
    return image
}

/**
 * Волна: кольцо с мягким профилем поперёк. От центра до 58 % радиуса —
 * пусто; дальше длинный подъём (тень за гребнем) до гребня на 91 %
 * и короткий спад к краю. 256 px: кольцо растягивается до двух ширин
 * экрана, и на меньшей текстуре гребень шёл бы ступеньками.
 */
private fun softRing(px: Int): ImageBitmap {
    val image = ImageBitmap(px, px)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    val half = px / 2f
    val paint = Paint().apply {
        shader = RadialGradientShader(
            center = Offset(half, half),
            radius = half,
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.62f),
                Color.White,
                Color.White.copy(alpha = 0.45f),
                Color.Transparent
            ),
            colorStops = listOf(0f, 0.58f, 0.72f, 0.84f, 0.91f, 0.955f, 1f)
        )
    }
    canvas.drawRect(0f, 0f, px.toFloat(), px.toFloat(), paint)
    return image
}

private const val RING_PX = 256
private const val BLOB_PX = 96
/** Волны: число колец, центр (доля высоты — у имени), период в секундах. */
private const val RINGS = 3
private const val RING_CENTER_Y = 0.13f
private const val RING_PERIOD_CALL = 4.2f
private const val RING_PERIOD_CALM = 8f
/** 30 кадров в секунду: медленному движению больше не нужно. */
private const val FRAME_MS = 33L
/** Шаг после паузы (экран гас) — не больше 0,1 с: без рывка при возврате. */
private const val MAX_STEP_MS = 100L
/** Сдвиг синуса на четверть периода — косинус. */
private const val PHASE_COS = 1.5707964f
