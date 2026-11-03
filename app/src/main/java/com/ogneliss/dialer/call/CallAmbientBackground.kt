package com.ogneliss.dialer.call

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.telecom.Call
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ogneliss.dialer.ui.components.Motion
import com.ogneliss.dialer.ui.components.rememberPhotoSwatches
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.PhotoPalette
import com.ogneliss.dialer.ui.theme.ThemePresetMath
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ЖИВОЙ ФОН ЭКРАНА ВЫЗОВА (1.5.1, автор: «лёгкая и плавная анимация
 * со спокойными цветами темы, которую потянут даже слабые телефоны —
 * типа живых обоев»; 1.8.4 — второй облик).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * 1.8.4: ЦВЕТОВЫЕ ПОЛЯ ВМЕСТО БЛЕДНЫХ ПЯТЕН
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 03.11: «посмотри экраны звонка, особенно живой фон — наверное, его
 * можно улучшить». Что было не так (наброски docs/call_ambient_1.8.4.png):
 * • один цвет — акцент темы с прозрачностью 7–20 % — на светлой теме
 *   давал бледную серо-голубую муть, на тёмной — почти ровный серый;
 * • пятна — картинка 96 точек, растянутая в 20 раз: у края видны ступени
 *   и «ромбы» сглаживания; кольца — картинки 256 точек, растянутые до
 *   двух ширин экрана;
 * • «искры» на светлом фоне читались как пыль на стекле.
 *
 * Теперь:
 * • три больших мягких поля трёх тонов медленно плывут по экрану, как
 *   «сетчатый градиент». Тоны — у звонящего с фото из его снимка
 *   (PhotoPalette.swatches: фон в цветах человека), без фото — акцент темы
 *   и два соседних тона. В светлой теме — пастель (яркость 84–90 %),
 *   в тёмной — глубокие (22–30 %); подписи на любом тоне проходят AA
 *   (PhotoPalette.fitToText, проверяет PhotoPaletteTest);
 * • поля, свечение и волны — градиенты видеокарты (RadialGradient
 *   с матрицей), а не растянутые картинки: край гладкий при любом размере;
 *   поверх — едва заметное зерно 1–2 %: на 8-битном экране мягкий
 *   градиент иначе идёт полосами;
 * • низ экрана спокойнее — под кнопками фон ближе к цвету страницы;
 * • волны — три, а не четыре, реже и тоньше; искр нет.
 *
 * Цвет по-прежнему говорит о состоянии вызова:
 *
 *   входящий, соединение — тоны снимка или темы, волны заметные (3,6 с),
 *                          поля плывут быстрее;
 *   разговор             — без фото тоны сдвинуты к зелёному звонка,
 *                          с фото — те же, приглушённые; волны едва видны
 *                          и реже (8 с), поля плывут медленнее;
 *   удержание            — почти серые, без волн;
 *   завершён             — гаснут.
 *
 * Смена — плавная (0,75 с).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ЛЁГКИЙ
 * ══════════════════════════════════════════════════════════════════════════
 * • Градиенты (поля, свечение, волны) создаются один раз — при смене цвета,
 *   а в кадре только двигаются матрицей: ни новых объектов, ни размытия,
 *   ни шейдеров своего кода. Каждое поле — один круг, волна — один овал:
 *   около восьми заливок за кадр, работа видеокарты, которую тянет любой
 *   телефон.
 * • Перерисовывается только слой фона (время читается внутри drawBehind),
 *   экран не пересобирается; цвета состояния тоже читаются в кадре.
 * • 30 кадров в секунду, а не 60–120: движение медленное (периоды 19–38 с).
 * • Только пока экран на переднем плане (RESUMED): у уха экран гаснет —
 *   анимация стоит.
 *
 * ── Когда неподвижен или выключен ──────────────────────────────────────────
 * • Системное «Убрать анимацию» (масштаб длительности 0) — поля стоят,
 *   волн нет, цвета состояний остаются.
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
 * него.
 *
 * Нет портрета (фото нет) — волны идут от имени (1.8.1): экран вызова
 * сообщает, где оно, с [edge] = false — тогда у центра нет края, волна
 * проявляется по пути, как раньше от середины верха. Было: от точки
 * на 13 % высоты экрана, где имя стояло до 1.7.0; с тех пор имя опустилось
 * к середине, и волны расходились от пустого места над ним (снимок автора).
 */
@Immutable
data class AmbientFocus(
    val center: Offset,
    val radius: Float,
    /** У фокуса есть край — портрет: волна рождается у края и видна сразу. */
    val edge: Boolean = true
)

@Composable
fun CallAmbientBackground(
    mood: AmbientMood,
    modifier: Modifier = Modifier,
    /**
     * Фото звонящего (1.8.4): фон в цветах его снимка. null — в цветах темы
     * (фото нет или оно на весь экран — тогда живого фона и так нет).
     */
    photoUri: String? = null,
    /**
     * Портрет, от которого расходятся волны. Лямбда, а не значение:
     * читается в кадре, и сдвиг портрета (ответ на вызов меняет высоту
     * блока) не пересобирает экран, а только перерисовывает фон.
     */
    focus: () -> AmbientFocus? = { null }
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val page = scheme.background.toArgb()
    val textMain = scheme.onSurface.toArgb()
    val textMuted = scheme.onSurfaceVariant.toArgb()
    val accent = scheme.primary.toArgb()

    /*
     * ТОНЫ: из снимка или из темы, по состоянию вызова. Снимок читается
     * не в главном потоке; пока он читается — тоны темы, потом плавно
     * переходят в тоны снимка.
     */
    val photoSwatches = rememberPhotoSwatches(photoUri)
    val fromPhoto = photoSwatches != null
    val tones = remember(photoSwatches, accent, dark, textMain, textMuted, mood) {
        val texts = intArrayOf(textMain, textMuted)
        val base = PhotoPalette.ambientTones(
            photoSwatches ?: PhotoPalette.themeSwatches(accent), dark, texts
        )
        PhotoPalette.moodTones(
            base,
            active = mood == AmbientMood.ACTIVE,
            holding = mood == AmbientMood.HOLDING,
            fromPhoto = fromPhoto,
            greenHue = CALL_GREEN_HUE,
            dark = dark,
            texts = texts
        )
    }
    val ringTarget = remember(tones, dark) { PhotoPalette.ringTone(tones[0], dark) }
    val haloTarget = remember(tones, dark, textMain, textMuted) {
        PhotoPalette.haloTone(tones[0], dark, intArrayOf(textMain, textMuted))
    }
    // State, а не значения: читаются в кадре — смена цвета не пересобирает экран
    val toneA = animateColorAsState(Color(tones[0]), tween(Motion.SLOW_MS), label = "ambientA")
    val toneB = animateColorAsState(Color(tones[1]), tween(Motion.SLOW_MS), label = "ambientB")
    val toneC = animateColorAsState(Color(tones[2]), tween(Motion.SLOW_MS), label = "ambientC")
    val ringColor = animateColorAsState(Color(ringTarget), tween(Motion.SLOW_MS), label = "ambientRing")
    val haloColor = animateColorAsState(Color(haloTarget), tween(Motion.SLOW_MS), label = "ambientHalo")
    // Завершён — поля гаснут
    val fieldStrength = animateFloatAsState(
        if (mood == AmbientMood.ENDED) 0f else 1f,
        tween(Motion.NORMAL_MS),
        label = "ambientFields"
    )

    /*
     * ВОЛНЫ — расходящиеся от портрета (или имени) кольца С ТОЛЩИНОЙ.
     *
     * 1.5.4: вместо линии 1,5 dp — мягкая полоса с градиентом поперёк.
     * 1.5.5 (автор, 18.10: «чтобы волны меняли толщину и становились
     * прозрачнее по мере удаления»): толщина растёт быстрее радиуса —
     * у рождения узкий плотный гребень, к краю экрана широкая размытая
     * полоса; прозрачность — плавное появление и спад (1 − p)^1,8. Волна
     * чуть сплюснута и «дышит» (± 3,5 %), как вода, а не циркуль.
     * 1.8.4: три волны вместо четырёх и реже — спокойнее; градиенты вместо
     * растянутых картинок — гребень ровный по всей длине.
     *
     * Толщина — четыре профиля (RING_INNER, RING_CREST); между соседними —
     * смешение прозрачностью.
     */
    val ringStrength = animateFloatAsState(
        when (mood) {
            AmbientMood.INCOMING, AmbientMood.CONNECTING -> if (dark) 0.50f else 0.40f
            AmbientMood.ACTIVE -> if (dark) 0.18f else 0.15f
            else -> 0f
        },
        tween(Motion.NORMAL_MS),
        label = "ambientRings"
    )
    val ringPeriod by rememberUpdatedState(
        if (mood == AmbientMood.ACTIVE) RING_PERIOD_CALM else RING_PERIOD_CALL
    )

    val art = remember(dark) { AmbientArt(dark) }

    // Время движения в секундах. Копится по кадрам, а не считается
    // от часов: при смене скорости (входящий → разговор) поля
    // не перескакивают
    val motion = remember { mutableFloatStateOf(0f) }
    // Фаза волн — отдельно: у неё свой период, и при его смене кольца
    // не должны прыгать
    val ripple = remember { mutableFloatStateOf(0f) }
    val speed by rememberUpdatedState(
        when (mood) {
            AmbientMood.INCOMING -> 1.3f
            AmbientMood.ACTIVE -> 0.7f
            else -> 1f
        }
    )

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
                val h = size.height
                val target = focus()
                val center = target?.center ?: Offset(w / 2f, h * RING_CENTER_Y)
                // Портрет — фокус с краем; имя (1.8.1) — точка без края
                val portrait = target?.takeIf { it.edge }
                val fields = fieldStrength.value
                drawIntoCanvas { composeCanvas ->
                    val canvas = composeCanvas.nativeCanvas
                    // Основа — цвет страницы темы, а не фон окна: у цветовых
                    // тем (1.8.4) страница своего тона
                    canvas.drawColor(page)
                    if (fields > 0f) {
                        art.paint.alpha = (fields * 255f).roundToInt()
                        // Три поля: правое, нижнее и у портрета (верхнее —
                        // поверх, оно и даёт цвет вокруг имени)
                        art.field(
                            canvas, art.fieldB, toneB.value.toArgb(),
                            w * (0.85f + 0.12f * sin(t / 3f)),
                            h * (0.42f + 0.05f * sin(t / 4f + PHASE_COS)),
                            w * 0.95f
                        )
                        art.field(
                            canvas, art.fieldC, toneC.value.toArgb(),
                            w * (0.20f + 0.15f * sin(t / 5f + 1f)),
                            h * (0.78f + 0.04f * sin(t / 3.5f)),
                            w * 1.05f
                        )
                        art.field(
                            canvas, art.fieldA, toneA.value.toArgb(),
                            center.x + w * 0.08f * sin(t / 6f),
                            center.y - h * 0.02f,
                            w * 0.9f
                        )
                        // Низ спокойнее: под кнопками фон ближе к странице
                        art.calm(canvas, page, w, h, fields)
                    }

                    val strength = if (animationsOff) 0f else ringStrength.value
                    val ripplePhase = ripple.floatValue
                    if (strength > 0f) {
                        // Свечение вокруг портрета (за именем): вспыхивает
                        // с рождением каждой волны
                        val since = (ripplePhase * RINGS) % 1f
                        val pulse = (1f - since) * (1f - since)
                        val haloBase = portrait?.let { max(w * HALO_RADIUS, it.radius * HALO_OVER_PORTRAIT) }
                            ?: (w * HALO_RADIUS)
                        art.paint.alpha = ((strength * (0.35f + 0.65f * pulse) * HALO_POWER).coerceAtMost(1f) * 255f).roundToInt()
                        art.field(
                            canvas, art.halo, haloColor.value.toArgb(),
                            center.x, center.y, haloBase * (1f + 0.08f * pulse)
                        )

                        // С портретом волна рождается гребнем у его края: гребень
                        // профиля — на RING_CREST[0] его радиуса
                        val ringStart = portrait?.let { it.radius / RING_CREST[0] } ?: (w * RING_START)
                        // От портрета волна видна сразу у края; без него — проявляется
                        // по пути, иначе возникала бы из ниоткуда посреди экрана
                        val appear = if (portrait != null) RING_APPEAR_FROM_PORTRAIT else RING_APPEAR
                        art.tintRings(ringColor.value.toArgb())
                        for (i in 0 until RINGS) {
                            val p = (ripplePhase + i.toFloat() / RINGS) % 1f
                            val alpha = strength * smoothstep(0f, appear, p) * (1f - p).pow(RING_FADE_POWER)
                            if (alpha < MIN_ALPHA) continue
                            val radius = ringStart + w * RING_TRAVEL * p
                            // Толщина: 0 — узкий гребень, last — широкая полоса
                            val spread = p.pow(0.75f) * (RING_INNER.size - 1)
                            val k = spread.toInt().coerceAtMost(RING_INNER.size - 2)
                            val f = spread - k
                            // «Дыхание»: волна чуть сплюснута, ось медленно меняется
                            val wobble = RING_WOBBLE * sin(t * 0.9f + i * 1.7f)
                            val rx = radius * (1f + wobble)
                            val ry = radius * (1f - wobble)
                            if (alpha * (1f - f) >= MIN_ALPHA) art.ring(canvas, k, center, rx, ry, alpha * (1f - f))
                            if (alpha * f >= MIN_ALPHA) art.ring(canvas, k + 1, center, rx, ry, alpha * f)
                        }
                    }
                    // Зерно — поверх всего: убирает полосы мягких градиентов
                    art.grain(canvas, w, h)
                }
            }
    )
}

/**
 * Всё, что фон рисует, — создаётся один раз на экран: кисть, матрица,
 * градиенты полей, волн и зерна. Градиент поля пересоздаётся только
 * при смене его цвета (смена состояния — 0,75 с), волны красятся
 * фильтром цвета, а в кадре всё только двигается матрицей.
 */
private class AmbientArt(dark: Boolean) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val matrix = Matrix()

    /** Поле: колокол exp(−2,2 (r/R)²), обрезанный на 1,3 R; пик — FIELD_ALPHA. */
    private val bell = FloatArray(FIELD_STOPS) { i ->
        val d = i / (FIELD_STOPS - 1f) * FIELD_REACH
        if (i == FIELD_STOPS - 1) 0f else exp(-2.2f * d * d)
    }
    private val bellStops = FloatArray(FIELD_STOPS) { it / (FIELD_STOPS - 1f) }
    private val peak = if (dark) FIELD_ALPHA_DARK else FIELD_ALPHA_LIGHT
    val fieldA = ToneShader(FloatArray(FIELD_STOPS) { bell[it] * peak }, bellStops)
    val fieldB = ToneShader(FloatArray(FIELD_STOPS) { bell[it] * peak }, bellStops)
    val fieldC = ToneShader(FloatArray(FIELD_STOPS) { bell[it] * peak }, bellStops)
    val halo = ToneShader(bell.copyOf(), bellStops)

    /**
     * Волны: профиль поперёк — от центра до RING_INNER пусто; дальше длинный
     * подъём (тень за гребнем) до гребня на RING_CREST и спад к краю.
     * Белые: цвет — фильтром [tintRings].
     */
    private val rings = Array(RING_INNER.size) { k ->
        val inner = RING_INNER[k]
        val crest = RING_CREST[k]
        val rise = crest - inner
        RadialGradient(
            0f, 0f, 1f,
            intArrayOf(
                0x00FFFFFF, 0x00FFFFFF, white(0.18f), white(0.62f),
                white(1f), white(0.40f), 0x00FFFFFF
            ),
            floatArrayOf(
                0f, inner, inner + 0.40f * rise, inner + 0.75f * rise,
                crest, crest + 0.5f * (1f - crest), 1f
            ),
            Shader.TileMode.CLAMP
        )
    }
    private var ringTint = 0
    private var ringFilter: PorterDuffColorFilter? = null

    /** Низ экрана: прозрачное → цвет страницы, по smootherstep. */
    private var calmShader: LinearGradient? = null
    private var calmKey = 0L
    private val calmPower = if (dark) CALM_DARK else CALM_LIGHT

    /**
     * Зерно: 64 × 64 точки шума ±[GRAIN_LIGHT] (тёмная — [GRAIN_DARK])
     * по яркости, повторяется плиткой. Псевдослучайное, но одинаковое
     * при каждом запуске: на месте стоит, не «кипит».
     */
    private val grainShader: BitmapShader = run {
        val amp = if (dark) GRAIN_DARK else GRAIN_LIGHT
        val px = IntArray(GRAIN_PX * GRAIN_PX) { i ->
            val v = sin(i * 12.9898f + 78.233f) * 43758.547f
            val n = (v - floor(v)) * 2f - 1f
            val a = (abs(n) * amp * 255f).roundToInt().coerceIn(0, 255)
            (a shl 24) or (if (n > 0f) 0xFFFFFF else 0x000000)
        }
        val bitmap = Bitmap.createBitmap(px, GRAIN_PX, GRAIN_PX, Bitmap.Config.ARGB_8888)
        BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val grainPaint = Paint().apply { shader = grainShader }

    fun field(canvas: android.graphics.Canvas, tone: ToneShader, argb: Int, cx: Float, cy: Float, r: Float) {
        val reach = r * FIELD_REACH
        val shader = tone.get(argb)
        matrix.setScale(reach, reach)
        matrix.postTranslate(cx, cy)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.colorFilter = null
        canvas.drawCircle(cx, cy, reach, paint)
    }

    fun calm(canvas: android.graphics.Canvas, page: Int, w: Float, h: Float, strength: Float) {
        val top = h * CALM_FROM
        val key = (h.toLong() shl 32) or (page.toLong() and 0xFFFFFFFFL)
        val shader = calmShader?.takeIf { calmKey == key } ?: run {
            val rgb = page and 0xFFFFFF
            val steps = 6
            LinearGradient(
                0f, top, 0f, h,
                IntArray(steps + 1) { i ->
                    val k = i / steps.toFloat()
                    val s = k * k * k * (k * (k * 6f - 15f) + 10f)
                    ((s * calmPower * 255f).roundToInt().coerceIn(0, 255) shl 24) or rgb
                },
                FloatArray(steps + 1) { it / steps.toFloat() },
                Shader.TileMode.CLAMP
            ).also {
                calmShader = it
                calmKey = key
            }
        }
        paint.shader = shader
        paint.colorFilter = null
        paint.alpha = (strength * 255f).roundToInt()
        canvas.drawRect(0f, top, w, h, paint)
    }

    fun tintRings(argb: Int) {
        if (ringFilter == null || ringTint != argb) {
            ringFilter = PorterDuffColorFilter(argb, PorterDuff.Mode.SRC_IN)
            ringTint = argb
        }
    }

    fun ring(canvas: android.graphics.Canvas, k: Int, center: Offset, rx: Float, ry: Float, alpha: Float) {
        val shader = rings[k]
        matrix.setScale(rx, ry)
        matrix.postTranslate(center.x, center.y)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.colorFilter = ringFilter
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        canvas.drawOval(center.x - rx, center.y - ry, center.x + rx, center.y + ry, paint)
    }

    fun grain(canvas: android.graphics.Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, grainPaint)
    }

    private fun white(a: Float) = ((a * 255f).roundToInt() shl 24) or 0xFFFFFF
}

/**
 * Радиальный градиент тона: прозрачность по профилю [profile] (0…1)
 * на долях радиуса [stops], радиус 1 — размер и место задаёт матрица.
 * Пересоздаётся только при смене цвета.
 */
private class ToneShader(private val profile: FloatArray, private val stops: FloatArray) {
    private var color = 0
    private var shader: RadialGradient? = null

    fun get(argb: Int): RadialGradient {
        shader?.let { if (argb == color) return it }
        val alpha = (argb ushr 24) / 255f
        val rgb = argb and 0xFFFFFF
        val colors = IntArray(profile.size) { i ->
            ((alpha * profile[i] * 255f).roundToInt().coerceIn(0, 255) shl 24) or rgb
        }
        return RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP).also {
            shader = it
            color = argb
        }
    }
}

/** Плавная ступенька 0 → 1 между [from] и [to] (как smoothstep в шейдерах). */
private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val k = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

/** Тон зелёного звонка — к нему сдвигается разговор без фото. */
private val CALL_GREEN_HUE = ThemePresetMath.toHsl(CallGreen.toArgb()).h

/**
 * Поле: колокол exp(−2,2 x²), где x — расстояние в радиусах поля;
 * градиент доходит до 1,3 радиуса (там колокол — 2 %) и обрывается в ноль.
 */
private const val FIELD_STOPS = 10
private const val FIELD_REACH = 1.3f
/** Плотность поля в центре: в светлой теме — 90 %, в тёмной — 85 %. */
private const val FIELD_ALPHA_LIGHT = 0.90f
private const val FIELD_ALPHA_DARK = 0.85f
/** Низ экрана — с этой доли высоты к цвету страницы, до 60 % (тёмная — 55 %). */
private const val CALM_FROM = 0.62f
private const val CALM_LIGHT = 0.60f
private const val CALM_DARK = 0.55f
/** Зерно: плитка и размах по яркости (доля). */
private const val GRAIN_PX = 64
private const val GRAIN_LIGHT = 0.012f
private const val GRAIN_DARK = 0.02f
/**
 * Толщина волн: границы пустого центра и гребня (доли радиуса)
 * от узкой волны у рождения до широкой у края экрана.
 */
private val RING_INNER = floatArrayOf(0.70f, 0.58f, 0.42f, 0.25f)
private val RING_CREST = floatArrayOf(0.86f, 0.85f, 0.81f, 0.77f)
/** Волны: число колец. */
private const val RINGS = 3
/** Радиус волны — от RING_START до RING_START + RING_TRAVEL ширины экрана. */
private const val RING_START = 0.16f
private const val RING_TRAVEL = 1.0f
/** Доля пути, за которую волна проявляется, и крутизна угасания. */
private const val RING_APPEAR = 0.10f
private const val RING_APPEAR_FROM_PORTRAIT = 0.03f
private const val RING_FADE_POWER = 1.8f
/** «Дыхание» волны: сплюснутость ± 3,5 %. */
private const val RING_WOBBLE = 0.035f
/** Свечение за именем — радиус в долях ширины экрана. */
private const val HALO_RADIUS = 0.30f
/** Свечение вокруг портрета — не меньше 1,7 его радиуса: видно из-за краёв. */
private const val HALO_OVER_PORTRAIT = 1.7f
/** Сила свечения относительно силы волн. */
private const val HALO_POWER = 1.2f
/** Слабее этого не рисуем — глазом не видно, а работа та же. */
private const val MIN_ALPHA = 0.004f
/**
 * Центр волн, пока экран не сообщил, где портрет или имя (первый кадр,
 * псевдозвонок без фокуса), — в долях высоты экрана.
 */
private const val RING_CENTER_Y = 0.13f
private const val RING_PERIOD_CALL = 3.6f
private const val RING_PERIOD_CALM = 8f
/** 30 кадров в секунду: медленному движению больше не нужно. */
private const val FRAME_MS = 33L
/** Шаг после паузы (экран гас) — не больше 0,1 с: без рывка при возврате. */
private const val MAX_STEP_MS = 100L
/** Сдвиг синуса на четверть периода — косинус. */
private const val PHASE_COS = 1.5707964f
