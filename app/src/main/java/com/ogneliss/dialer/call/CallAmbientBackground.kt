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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ogneliss.dialer.ui.components.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
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
 * от имени звонящего расходятся мягкие волны — как круги по воде:
 * у места рождения узкие и плотные, дальше шире, размытее и прозрачнее
 * (1.5.5). За именем — мягкое свечение, вспыхивающее с каждой новой
 * волной; по экрану медленно всплывают редкие светлые «искры», и волна,
 * проходя через искру, на миг её подсвечивает.
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
 *   четыре текстуры (256 × 256, все вместе 1 МБ) разной толщины;
 *   каждая волна — две растянутые копии соседних с долей прозрачности.
 *   Свечение — пятно; искры — десять маленьких пятен. Всего около 25
 *   растянутых картинок за кадр, крупные — только пятна и волны.
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

/**
 * Откуда расходятся волны: центр и радиус портрета звонящего в координатах
 * окна (1.5.5). Волна рождается гребнем у края портрета, свечение — вокруг
 * него. Нет портрета (фото нет) — волны идут от имени (RING_CENTER_Y).
 */
@Immutable
data class AmbientFocus(val center: Offset, val radius: Float)

@Composable
fun CallAmbientBackground(
    mood: AmbientMood,
    modifier: Modifier = Modifier,
    /**
     * Портрет, от которого расходятся волны. Лямбда, а не значение:
     * читается в кадре, и сдвиг портрета (ответ на вызов меняет высоту
     * блока) не пересобирает экран, а только перерисовывает фон.
     */
    focus: () -> AmbientFocus? = { null }
) {
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
     * ВОЛНЫ — расходящиеся от имени звонящего кольца С ТОЛЩИНОЙ.
     *
     * 1.5.4: вместо линии 1,5 dp — мягкая полоса с градиентом поперёк.
     * 1.5.5 (автор, 18.10: «чтобы волны меняли толщину и становились
     * прозрачнее по мере удаления»): толщина растёт быстрее радиуса —
     * у рождения узкий плотный гребень, к краю экрана широкая размытая
     * полоса; прозрачность — плавное появление в первые 10 % пути и спад
     * (1 − p)^2,4. Волна чуть сплюснута и «дышит» (± 3,5 %), как вода,
     * а не циркуль.
     *
     * Толщина — четыре текстуры с разным профилем (RING_INNER, RING_CREST); между
     * соседними — смешение прозрачностью, без шейдеров.
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
    val rings = remember { Array(RING_INNER.size) { softRing(RING_PX, RING_INNER[it], RING_CREST[it]) } }
    // Гребни и яркость волн текущего кадра — для подсветки искр.
    // Массив один на экран: в кадре ничего не создаётся
    val crest = remember { FloatArray(RINGS * 2) }

    /*
     * ИСКРЫ (1.5.5) — десять мягких точек медленно всплывают снизу вверх
     * с лёгким покачиванием и мерцанием. Цвет — по состоянию (синие,
     * зелёные в разговоре, серые на удержании). В светлой теме слабее:
     * цветные точки на белом читаются как пыль на экране.
     */
    val sparkStrength by animateFloatAsState(
        when (mood) {
            AmbientMood.INCOMING, AmbientMood.CONNECTING -> 1f
            AmbientMood.ACTIVE -> 0.7f
            AmbientMood.HOLDING -> 0.35f
            AmbientMood.ENDED -> 0f
        },
        tween(Motion.NORMAL_MS),
        label = "ambientSparks"
    )
    val sparkColor by animateColorAsState(
        when (mood) {
            AmbientMood.ACTIVE -> scheme.tertiary
            AmbientMood.HOLDING -> scheme.secondary
            else -> scheme.primary
        },
        tween(Motion.NORMAL_MS),
        label = "ambientSparkColor"
    )
    val sparkTint = remember(sparkColor) { ColorFilter.tint(sparkColor, BlendMode.SrcIn) }
    val sparkScale = if (scheme.background.luminance() > 0.5f) 0.8f else 1f
    val sparks = remember { sparkSeeds() }

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

                val strength = ringStrength
                val target = focus()
                val center = target?.center ?: Offset(w / 2f, size.height * RING_CENTER_Y)
                // С портретом волна рождается гребнем у его края: гребень
                // текстуры — на RING_CREST[0] её радиуса
                val ringStart = target?.let { it.radius / RING_CREST[0] } ?: (w * RING_START)
                // От портрета волна видна сразу у края; без него — проявляется
                // по пути, иначе возникала бы из ниоткуда посреди экрана
                val appear = if (target != null) RING_APPEAR_FROM_PORTRAIT else RING_APPEAR
                val haloBase = target?.let { max(w * HALO_RADIUS, it.radius * HALO_OVER_PORTRAIT) }
                    ?: (w * HALO_RADIUS)
                val ripplePhase = ripple.floatValue

                // Свечение за именем: вспыхивает с рождением каждой волны
                if (strength > 0f) {
                    val since = (ripplePhase * RINGS) % 1f
                    val pulse = (1f - since) * (1f - since)
                    val haloR = haloBase * (1f + 0.08f * pulse)
                    val haloD = (haloR * 2f).roundToInt()
                    drawImage(
                        image = blob,
                        dstOffset = IntOffset(
                            (center.x - haloR).roundToInt(),
                            (center.y - haloR).roundToInt()
                        ),
                        dstSize = IntSize(haloD, haloD),
                        alpha = (strength * (0.35f + 0.65f * pulse) * 0.9f).coerceAtMost(1f),
                        colorFilter = ringTint
                    )
                }

                // Волны: RINGS колец со сдвигом в долю периода
                for (i in 0 until RINGS) {
                    val p = (ripplePhase + i.toFloat() / RINGS) % 1f
                    val alpha = strength * smoothstep(0f, appear, p) * (1f - p).pow(RING_FADE_POWER)
                    val radius = ringStart + w * RING_TRAVEL * p
                    // Толщина: 0 — узкий гребень, last — широкая полоса
                    val spread = p.pow(0.75f) * (rings.size - 1)
                    val k = spread.toInt().coerceAtMost(rings.size - 2)
                    val f = spread - k
                    crest[2 * i] = radius * (RING_CREST[k] + (RING_CREST[k + 1] - RING_CREST[k]) * f)
                    crest[2 * i + 1] = alpha
                    if (alpha < MIN_ALPHA) continue
                    // «Дыхание»: волна чуть сплюснута, ось медленно меняется
                    val wobble = RING_WOBBLE * sin(t * 0.9f + i * 1.7f)
                    val rx = radius * (1f + wobble)
                    val ry = radius * (1f - wobble)
                    val offset = IntOffset((center.x - rx).roundToInt(), (center.y - ry).roundToInt())
                    val dst = IntSize((rx * 2f).roundToInt(), (ry * 2f).roundToInt())
                    if (alpha * (1f - f) >= MIN_ALPHA) {
                        drawImage(rings[k], dstOffset = offset, dstSize = dst, alpha = alpha * (1f - f), colorFilter = ringTint)
                    }
                    if (alpha * f >= MIN_ALPHA) {
                        drawImage(rings[k + 1], dstOffset = offset, dstSize = dst, alpha = alpha * f, colorFilter = ringTint)
                    }
                }

                // Искры: всплывают, покачиваются, мерцают; волна их подсвечивает
                val sparkPower = sparkStrength * sparkScale
                if (sparkPower > 0f) {
                    val h = size.height
                    val band = w * SPARK_LIT_BAND
                    for (j in 0 until SPARKS) {
                        val base = j * SPARK_FIELDS
                        val baseX = sparks[base]
                        val rise = sparks[base + 1]
                        val radius = (sparks[base + 2] * density)
                        val phase = sparks[base + 3]
                        val twinklePeriod = sparks[base + 4]
                        val y = SPARK_BOTTOM - ((t * rise + phase) % 1f) * SPARK_SPAN
                        val x = baseX + 0.035f * sin(t / twinklePeriod * 2f + phase * 2f * PI.toFloat())
                        val edge = smoothstep(0f, 0.12f, y) * (1f - smoothstep(0.85f, SPARK_BOTTOM, y))
                        if (edge <= 0f) continue
                        val twinkle = 0.55f + 0.45f * sin(t * 2f * PI.toFloat() / twinklePeriod + phase * 2f * PI.toFloat())
                        val px = x * w
                        val py = y * h
                        val dist = hypot(px - center.x, py - center.y)
                        var lit = 0f
                        for (i in 0 until RINGS) {
                            val g = (1f - abs(dist - crest[2 * i]) / band).coerceAtLeast(0f)
                            lit += g * g * crest[2 * i + 1] * 2.5f
                        }
                        val alpha = (sparkPower * edge * (0.05f + 0.10f * twinkle + lit)).coerceAtMost(0.45f)
                        if (alpha < MIN_ALPHA) continue
                        val r = radius * 1.6f
                        drawImage(
                            image = blob,
                            dstOffset = IntOffset((px - r).roundToInt(), (py - r).roundToInt()),
                            dstSize = IntSize((r * 2f).roundToInt(), (r * 2f).roundToInt()),
                            alpha = alpha,
                            colorFilter = sparkTint
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

/** Плавная ступенька 0 → 1 между [from] и [to] (как smoothstep в шейдерах). */
private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val k = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

/**
 * Волна: кольцо с мягким профилем поперёк. От центра до [inner] радиуса —
 * пусто; дальше длинный подъём (тень за гребнем) до гребня на [crest]
 * и спад к краю. Чем меньше [inner] и [crest], тем волна шире и мягче.
 * 256 px: кольцо растягивается до двух ширин экрана, и на меньшей
 * текстуре гребень шёл бы ступеньками.
 */
private fun softRing(px: Int, inner: Float, crest: Float): ImageBitmap {
    val image = ImageBitmap(px, px)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    val half = px / 2f
    val rise = crest - inner
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
                Color.White.copy(alpha = 0.40f),
                Color.Transparent
            ),
            colorStops = listOf(
                0f, inner, inner + 0.40f * rise, inner + 0.75f * rise,
                crest, crest + 0.5f * (1f - crest), 1f
            )
        )
    }
    canvas.drawRect(0f, 0f, px.toFloat(), px.toFloat(), paint)
    return image
}

/**
 * Искры: по SPARK_FIELDS чисел на каждую — положение по ширине (доля),
 * скорость всплытия (доля высоты в секунду движения), радиус (dp),
 * сдвиг фазы, период мерцания (с). Псевдослучайно, но одинаково
 * при каждом запуске — без Random и без новых объектов в кадре.
 */
private fun sparkSeeds(): FloatArray {
    fun rnd(i: Int, k: Int): Float {
        val v = sin(i * 12.9898f + k * 78.233f) * 43758.547f
        return v - floor(v)
    }
    return FloatArray(SPARKS * SPARK_FIELDS).also { a ->
        for (j in 0 until SPARKS) {
            val b = j * SPARK_FIELDS
            a[b] = 0.06f + 0.88f * rnd(j, 1)
            a[b + 1] = 0.018f + 0.02f * rnd(j, 2)
            a[b + 2] = 3f + 4f * rnd(j, 3)
            a[b + 3] = rnd(j, 4)
            a[b + 4] = 2.5f + 3f * rnd(j, 5)
        }
    }
}

private const val RING_PX = 256
private const val BLOB_PX = 96
/**
 * Толщина волн: границы пустого центра и гребня (доли радиуса текстуры)
 * от узкой волны у рождения до широкой у края экрана.
 */
private val RING_INNER = floatArrayOf(0.70f, 0.58f, 0.42f, 0.25f)
private val RING_CREST = floatArrayOf(0.86f, 0.85f, 0.81f, 0.77f)
/** Волны: число колец, центр (доля высоты — у имени), период в секундах. */
private const val RINGS = 4
/** Радиус волны — от RING_START до RING_START + RING_TRAVEL ширины экрана. */
private const val RING_START = 0.16f
private const val RING_TRAVEL = 1.10f
/** Доля пути, за которую волна проявляется, и крутизна угасания. */
private const val RING_APPEAR = 0.10f
private const val RING_APPEAR_FROM_PORTRAIT = 0.03f
private const val RING_FADE_POWER = 2.4f
/** «Дыхание» волны: сплюснутость ± 3,5 %. */
private const val RING_WOBBLE = 0.035f
/** Свечение за именем — радиус в долях ширины экрана. */
private const val HALO_RADIUS = 0.30f
/** Свечение вокруг портрета — не меньше 1,7 его радиуса: видно из-за краёв. */
private const val HALO_OVER_PORTRAIT = 1.7f
/** Искры: число, чисел на искру, нижняя граница и путь (доли высоты). */
private const val SPARKS = 10
private const val SPARK_FIELDS = 5
private const val SPARK_BOTTOM = 1.08f
private const val SPARK_SPAN = 1.16f
/** Ширина зоны у гребня, где волна подсвечивает искру (доля ширины). */
private const val SPARK_LIT_BAND = 0.08f
/** Слабее этого не рисуем — глазом не видно, а работа та же. */
private const val MIN_ALPHA = 0.004f
private const val RING_CENTER_Y = 0.13f
private const val RING_PERIOD_CALL = 4.2f
private const val RING_PERIOD_CALM = 8f
/** 30 кадров в секунду: медленному движению больше не нужно. */
private const val FRAME_MS = 33L
/** Шаг после паузы (экран гас) — не больше 0,1 с: без рывка при возврате. */
private const val MAX_STEP_MS = 100L
/** Сдвиг синуса на четверть периода — косинус. */
private const val PHASE_COS = 1.5707964f
