package com.ogneliss.dialer.call

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ogneliss.dialer.ui.components.Motion
import kotlin.math.PI
import kotlin.math.max
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
 * Три больших мягких пятна цветов темы медленно плывут за содержимым
 * экрана вызова. Цвет говорит о состоянии вызова, не требуя чтения:
 *
 *   входящий, соединение — синие (primaryContainer), входящий плывёт
 *                          чуть быстрее — «зовёт»;
 *   разговор             — зелёное с синим (tertiaryContainer — зелёный
 *                          вызовов в схеме, как плашка «Текущий вызов»);
 *   удержание            — серые (secondaryContainer, surfaceVariant);
 *   завершён             — гаснут.
 *
 * Смена цвета — плавная (300 мс). Пятна — контейнерные цвета темы
 * с прозрачностью: текст поверх них читается так же, как на фоне —
 * onSurface на этих контейнерах контрастен в любой теме.
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
 *   17–41 с), разницы глазом не видно, работы вдвое меньше.
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
    // В тёмной теме контейнеры насыщеннее на тёмном фоне — меньше
    // прозрачности, иначе пятна спорят с текстом
    val strength = if (scheme.surface.luminance() < 0.5f) 0.55f else 0.9f

    val (first, second) = when (mood) {
        AmbientMood.INCOMING, AmbientMood.CONNECTING ->
            scheme.primaryContainer to scheme.secondaryContainer
        AmbientMood.ACTIVE -> scheme.tertiaryContainer to scheme.primaryContainer
        AmbientMood.HOLDING -> scheme.secondaryContainer to scheme.surfaceVariant
        AmbientMood.ENDED -> Color.Transparent to Color.Transparent
    }
    val colorA by animateColorAsState(
        first.copy(alpha = first.alpha * strength), tween(Motion.NORMAL_MS), label = "ambientA"
    )
    val colorB by animateColorAsState(
        second.copy(alpha = second.alpha * strength), tween(Motion.NORMAL_MS), label = "ambientB"
    )
    // Подкраска создаётся при смене цвета, а не в каждом кадре
    val tintA = remember(colorA) { ColorFilter.tint(colorA, BlendMode.SrcIn) }
    val tintB = remember(colorB) { ColorFilter.tint(colorB, BlendMode.SrcIn) }
    val tintC = remember(colorA) {
        ColorFilter.tint(colorA.copy(alpha = colorA.alpha * 0.6f), BlendMode.SrcIn)
    }

    val blob = remember { softBlob(BLOB_PX) }

    // Время движения в секундах. Копится по кадрам, а не считается
    // от часов: при смене скорости (входящий → разговор) пятна
    // не перескакивают
    val motion = remember { mutableFloatStateOf(0f) }
    val speed by rememberUpdatedState(if (mood == AmbientMood.INCOMING) 1.6f else 1f)

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
                    motion.floatValue += pending / 1000f * speed
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
                val big = max(size.width, size.height)
                drawBlob(
                    blob, tintA,
                    x = 0.25f + 0.18f * wave(t, 23f),
                    y = 0.22f + 0.12f * wave(t, 29f, PHASE_COS),
                    radius = big * 0.62f * (1f + 0.06f * wave(t, 17f))
                )
                drawBlob(
                    blob, tintB,
                    x = 0.78f + 0.16f * wave(t, 31f, 1f + PHASE_COS),
                    y = 0.40f + 0.14f * wave(t, 19f, 2f),
                    radius = big * 0.58f
                )
                drawBlob(
                    blob, tintC,
                    x = 0.50f + 0.25f * wave(t, 37f, 3f),
                    y = 0.66f + 0.10f * wave(t, 41f, PHASE_COS),
                    radius = big * 0.5f
                )
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

private const val BLOB_PX = 96
/** 30 кадров в секунду: медленному движению больше не нужно. */
private const val FRAME_MS = 33L
/** Шаг после паузы (экран гас) — не больше 0,1 с: без рывка при возврате. */
private const val MAX_STEP_MS = 100L
/** Сдвиг синуса на четверть периода — косинус. */
private const val PHASE_COS = 1.5707964f
