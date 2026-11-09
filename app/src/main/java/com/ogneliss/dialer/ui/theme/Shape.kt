package com.ogneliss.dialer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/**
 * ФОРМЫ — СТРОГИЙ СТИЛЬ (1.5.1, 11.10.2026).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО ПОМЕНЯЛОСЬ И ПОЧЕМУ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор: «стиль всего приложения — на более строгий, корпоративный».
 * 1.5.1–1.5.2 — макет «A» (белые группы с рамкой на сером, кнопки 8 dp);
 * 1.5.3 — макет «B. Строгий, сплошной список» (docs/strict_style_1.5.1.png):
 * строки во всю ширину без карточек, разделы — серые полосы с подписью
 * капителью; кнопки, клавиши, строка номера и поиск — снова «пилюли».
 *
 * 1.5.5 — МЯГЧЕ (автор, 18.10: «стиль стал резким и угловатым, лучше
 * смягчить»): радиусы 6 · 10 · 14 · 18 · 24 dp вместо 4 · 6 · 8 · 8 · 12,
 * шторки, диалоги и нижняя панель — 24 dp. Сплошной список (строки во всю
 * ширину) остался; угловатость давали мелкие радиусы и жёсткие линии.
 *
 * Было по спецификации Material 3: 4 · 8 · 12 · 16 · 28 dp, клавиши
 * и кнопки вызова — полное скругление. Мягко и «по-домашнему»; рядом
 * с почтой и офисными приложениями выглядело игрушечно.
 *
 * ── Где что ────────────────────────────────────────────────────────────────
 * extraSmall (6)  — бейджи SIM, мелкие метки
 * small (12)      — чипы фильтров (2.1.2: 12, как в Mail; было 10)
 * medium (14)     — поля ввода, мелкие плашки
 * large (18)      — цветные пояснения (предупреждение, «без интернета»)
 * extraLarge (28) — шторки, диалоги, нижние панели (2.1.2: 28, как в Mail;
 *                   было 24 — дизайн-система, Д11)
 *
 * Круглые — аватары и круглые кнопки-значки; «пилюли» — кнопки
 * (Button), клавиши, строка номера, поиск, плашка состояния вызова. Для них — CircleShape явно, а не extraLarge (раньше
 * extraLarge 28 dp на 48 dp и давал круг — теперь дал бы квадрат).
 */
val OgnelissShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * СКРУГЛЕНИЕ УГЛОВ НА ВЫБОР (1.9.0, как «Скругление» Ogneliss Mail).
 *
 * Настройки → Оформление → «Скругление углов»: малое (× 0,5), среднее
 * (× 1 — прежний вид), большое (× 1,4). Множитель меняет формы темы
 * ([shapesFor]: поля, плашки, шторки, диалоги) и токены карточек и полос
 * ([OgnelissShapeTokens]). «Пилюли» — кнопки вызова, клавиши, поиск — и круги
 * остаются как есть: их форма — смысл, а не стиль. Крупные формы — не больше
 * [MAX_SHEET_RADIUS]: иначе верх шторки становится «капсулой» (как в почте).
 *
 * Множитель — состояние Compose: токены читают его, и экраны, где они
 * использованы, перерисовываются сами, когда тема его меняет
 * (OgnelissDialerTheme).
 */
object CornerScale {
    var factor by mutableFloatStateOf(1f)
        internal set
}

/** Не больше этого — у шторок, окон, листа обложки карточки. */
val MAX_SHEET_RADIUS = 32.dp

/** Радиус [base] со скруглением из настроек. */
fun scaledRadius(base: Dp): Dp = base * CornerScale.factor

/** Радиус крупной формы (шторка, лист) со скруглением, не больше [MAX_SHEET_RADIUS]. */
fun sheetRadius(base: Dp): Dp = min(base * CornerScale.factor, MAX_SHEET_RADIUS)

/** Формы темы со скруглением × [k]; при × 1 — прежний набор цвет в цвет. */
fun shapesFor(k: Float): Shapes = if (k == 1f) OgnelissShapes else Shapes(
    extraSmall = RoundedCornerShape(6.dp * k),
    small = RoundedCornerShape(12.dp * k),
    medium = RoundedCornerShape(14.dp * k),
    large = RoundedCornerShape(18.dp * k),
    extraLarge = RoundedCornerShape(min(28.dp * k, MAX_SHEET_RADIUS))
)

/** Формы для случаев, которых нет в стандартном наборе. */
object OgnelissShapeTokens {

    /*
     * Клавиша набора, строка номера и поле поиска — «пилюля» (с 1.3.5).
     * В 1.5.1–1.5.2 были прямоугольники 8 dp; 1.5.3 — снова пилюля
     * (автор: «кнопки верни округлые, иначе они спорят со всей темой»).
     */
    val dialKey = RoundedCornerShape(percent = 50)

    /**
     * НИЖНЯЯ ПАНЕЛЬ.
     *
     * С 1.7.0 — плоская («воздушный» вид, вариант А макета docs/ui_1.7.0.png):
     * без скруглений и тени, край — тонкая линия (PanelEdge.kt). Скруглённая
     * панель с тенью читалась отдельным тяжёлым блоком под списком.
     * История: скругление верхних углов 28 dp → 12 (1.5.1–1.5.4) → 24 (1.5.5–1.6.2).
     *
     * ── Почему в токенах ───────────────────────────────────────────────────
     * Форма задавалась числами в двух местах: в панели журнала
     * и в панели контактов. При правке одного второе осталось бы
     * прежним, и панели различались бы при переходе между вкладками.
     */
    val bottomPanel = RoundedCornerShape(0.dp)

    /**
     * ПОЛОСА РАЗДЕЛА И КАРТОЧКА ГРУППЫ — КАК В OGNELISS MAIL (1.5.6).
     *
     * Сняты по снимкам Mail: полоса — скругление ≈ 11 dp при высоте 32–37,
     * карточка настроек — ≈ 22 dp. Полоса не «пилюля»: с полным скруглением
     * она читалась бы кнопкой.
     *
     * 1.8.4: карточка — 20 dp, как Radii.card почты (tokens.dart): сравнение
     * экранов автором — у диалера углы заметно круглее.
     */
    val band: RoundedCornerShape get() = scaled().band
    val card: RoundedCornerShape get() = scaled().card
    /** Радиус карточки отдельно — для рамки сегментов группы (GroupedRow). */
    val cardRadius: Dp get() = CARD_RADIUS * CornerScale.factor

    /**
     * Кнопки вызова: «Позвонить», SIM, «Ответить», «Отклонить», «Завершить» —
     * полное скругление (1.5.3 — снова, как до 1.5.1).
     */
    val callButton = RoundedCornerShape(percent = 50)

    /**
     * Группа настроек. Скруглены только внешние углы группы,
     * внутренние разделители остаются прямыми — так группа читается
     * как единый блок, а не как набор отдельных карточек.
     */
    val settingsGroupTop = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val settingsGroupBottom = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
    val settingsGroupSingle = RoundedCornerShape(18.dp)
    val settingsGroupMiddle = RoundedCornerShape(0.dp)

    /**
     * Полоса и карточка при текущем скруглении (1.9.0). Формы создаются
     * один раз на множитель, а не при каждом чтении: карточку читают
     * все строки списков.
     */
    private class Scaled(val k: Float) {
        val band = RoundedCornerShape(BAND_RADIUS * k)
        val card = RoundedCornerShape(CARD_RADIUS * k)
    }

    // Не создаётся при загрузке объекта: радиусы ниже ещё не заданы
    private var cache: Scaled? = null

    private fun scaled(): Scaled {
        val k = CornerScale.factor
        return cache?.takeIf { it.k == k } ?: Scaled(k).also { cache = it }
    }

    private val BAND_RADIUS = 12.dp
    private val CARD_RADIUS = 20.dp
}
