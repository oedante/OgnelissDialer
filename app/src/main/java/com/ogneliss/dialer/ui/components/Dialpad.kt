package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.LocalAppearance
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens

/**
 * КЛАВИАТУРА НАБОРА.
 *
 * ── Что изменилось в оформлении ────────────────────────────────────────────
 * Раньше клавиши были прозрачными областями: границы не читались,
 * и промах по краю воспринимался как «кнопка не сработала».
 *
 * Теперь у каждой клавиши своя поверхность, форма и отклик нажатия.
 * Видно, где заканчивается область, и попадание подтверждается визуально,
 * тактильно и звуком — тремя каналами сразу, потому что набирают вслепую.
 *
 * ── 2.7.5: классический вид (автор выбрал вариант «А» из трёх снимков) ─────
 * Автор, 17.11.2026: «не нравится клавиатура». Выбран классический вид:
 * цифра крупно по центру, буквы под ней, клавиши без подложки, вызов —
 * круглые зелёные кнопки (CallButtons). Попадание по-прежнему видно:
 * нажатая клавиша подсвечивается (primaryContainer), плюс отклик
 * вибрацией и звуком. Где ряд ниже [STACK_MIN] (лёжа, тесная клавиатура
 * экрана вызова) — прежняя раскладка «цифра слева, буквы справа»: в 1.3.5–1.3.6
 * буквы под цифрой лёжа не помещались и пропадали.
 *
 * ── Раскладка не настраивается ─────────────────────────────────────────────
 * Порядок клавиш фиксирован телефонным стандартом. Перестановка сломала бы
 * мышечную память, которой люди пользуются десятилетиями.
 *
 * ── Пропорции ──────────────────────────────────────────────────────────────
 * Высота клавиши 60 dp при промежутке 6 dp: клавиши крупные, но между
 * ними есть воздух. Без промежутка сетка читается как сплошное поле,
 * и глаз не выделяет отдельные клавиши.
 */

/** Клавиша: цифра, латинские буквы и ресурс с буквами локали. */
data class DialKey(val digit: Char, val latin: String, val altRes: Int)

/**
 * Раскладка. Буквы локали берутся из ресурсов: в русской локали
 * это кириллица, в английской строка пустая и вторая строка не рисуется.
 */
val DIAL_KEYS = listOf(
    DialKey('1', "", 0),
    DialKey('2', "ABC", R.string.key_2_alt),
    DialKey('3', "DEF", R.string.key_3_alt),
    DialKey('4', "GHI", R.string.key_4_alt),
    DialKey('5', "JKL", R.string.key_5_alt),
    DialKey('6', "MNO", R.string.key_6_alt),
    DialKey('7', "PQRS", R.string.key_7_alt),
    DialKey('8', "TUV", R.string.key_8_alt),
    DialKey('9', "WXYZ", R.string.key_9_alt),
    DialKey('*', "", 0),
    DialKey('0', "+", 0),
    DialKey('#', "", 0)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dialpad(
    onDigit: (Char) -> Unit,
    onLongDigit: (Char) -> Unit,
    /**
     * Высота клавиши, если её диктует место, а не настройка.
     *
     * Клавиатура сбоку на телефоне лёжа: по высоте там ~360 dp на всё —
     * строку номера, четыре ряда клавиш и кнопки вызова. С обычными
     * 60 dp клавиатура не помещалась бы. null — высота из настроек
     * (обычная или крупная), как было всегда.
     */
    keyHeight: Dp? = null,
    /**
     * Ряды делят между собой всю высоту, которую дали клавиатуре.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ВЕСА, А НЕ РАСЧЁТ ВЫСОТЫ КЛАВИШИ
     * ══════════════════════════════════════════════════════════════════════
     * Сначала высота клавиши считалась: «доступная высота минус строка
     * номера, кнопки вызова, отступы — на четыре ряда». Проверка на размерах
     * телефонов лёжа показала, что так не выходит: высота строки номера,
     * панели навигации и выреза камеры известна только приблизительно,
     * и на телефоне высотой 360 dp кнопки вызова обрезались бы на пару
     * десятков точек.
     *
     * С весами ошибиться нельзя: что осталось после строки номера и кнопок
     * вызова — то и делится на четыре ряда, до точки.
     */
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val appearance = LocalAppearance.current
    val view = LocalView.current
    val feedback = remember { DialpadFeedback() }
    DisposableEffect(Unit) { onDispose { feedback.release() } }

    BoxWithConstraints(modifier.fillMaxWidth()) {
    // Высота ряда: задана — известна сразу; ряды делят место — четверть высоты
    val rowHeight = when {
        !fillHeight -> keyHeight ?: appearance.dialKeyHeight
        constraints.hasBoundedHeight -> maxHeight / 4 - KEY_GAP * 2
        else -> 0.dp
    }
    // Текст растёт с размером шрифта, а высота ряда — нет: порог растёт вместе с ним
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val stacked = rowHeight >= STACK_MIN * fontScale
    /*
     * Ячейки «цифра слева, буквы справа» растут с размером шрифта (2.7.7, галерея
     * при шрифте 160 %: «АБВ», «МНС» — буквы обрезались, цифра наезжала на них).
     * Ширина клавиши это выдерживает: 24 + 8 + 44 dp × 1,6 ≈ 120 dp.
     */
    val slotScale = fontScale.coerceAtLeast(1f)
    // Буквы языка не латиница (русская локаль) — под ними строка латиницы у всех клавиш
    val latinRow = stringResource(R.string.key_2_alt).isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
            .padding(horizontal = Space.m)
    ) {
        DIAL_KEYS.chunked(3).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.weight(1f) else Modifier)
            ) {
                row.forEach { key ->
                    // TalkBack читает «Клавиша 5», а не «5 JKL МНОП»:
                    // перечисление букв на слух бесполезно и мешает
                    val keyLabel = stringResource(R.string.a11y_dial_key, key.digit.toString())
                    val altText = if (key.altRes != 0) stringResource(key.altRes) else ""

                    /*
                     * Фон клавиши рисуется напрямую, без Surface.
                     *
                     * Surface помимо фона создаёт слой для тени и ряби,
                     * даже когда тень нулевая. Для двенадцати клавиш,
                     * лежащих на непрозрачной панели, это двенадцать
                     * лишних слоёв на каждом кадре.
                     *
                     * Порядок важен: clip перед background, иначе фон
                     * нарисуется прямоугольником и скругление закрасится.
                     */
                    /*
                     * НАЖАТАЯ КЛАВИША — С ЛЁГКИМ ОТТЕНКОМ ОСНОВНОГО ЦВЕТА.
                     *
                     * Одной ряби мало: она светлая и на белой клавише едва
                     * видна, особенно при быстром наборе. Пока палец на
                     * клавише, её фон — primaryContainer: видно, какая
                     * нажата. Плавно (100 мс), чтобы быстрый набор не мигал.
                     * Предложено разбором палитры: синий — цвет управления.
                     */
                    val keyInteraction = remember { MutableInteractionSource() }
                    val keyPressed by keyInteraction.collectIsPressedAsState()
                    val keyColor by animateColorAsState(
                        // 2.7.5: без подложки в покое — классический вид (разбор вверху)
                        if (keyPressed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = tween(100),
                        label = "dialKeyPressed"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(KEY_GAP)
                            .then(
                                if (fillHeight) {
                                    // Вся высота ряда; без нижней границы 48 dp —
                                    // иначе при нехватке места ряды вылезли бы
                                    // за панель и обрезали кнопки вызова
                                    Modifier.fillMaxHeight()
                                } else {
                                    Modifier
                                        .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                                        .height(keyHeight ?: appearance.dialKeyHeight)
                                }
                            )
                            .clip(OgnelissShapeTokens.dialKey)
                            // В покое прозрачная, нажатая — с подсветкой (2.7.5)
                            .background(keyColor)
                            .semantics(mergeDescendants = true) {
                                contentDescription = keyLabel
                                role = Role.Button
                            }
                            .combinedClickable(
                                interactionSource = keyInteraction,
                                indication = LocalIndication.current,
                                onClick = {
                                    // Три канала отклика сразу: набирают
                                    // вслепую, глядя на бумажку, а не на экран
                                    feedback.onKeyPress(
                                        view, key.digit,
                                        appearance.haptic, appearance.keyTones
                                    )
                                    onDigit(key.digit)
                                },
                                onLongClick = { onLongDigit(key.digit) }
                            )
                    ) {
                        /*
                         * РАСКЛАДКА КЛАВИШИ: ЦИФРА СЛЕВА, БУКВЫ СПРАВА.
                         *
                         * Прежний вариант ставил цифру над буквами по центру.
                         * Клавиша при этом оставалась почти пустой: цифра
                         * занимала треть высоты, буквы — узкую строку внизу,
                         * а по бокам оставалось пустое место.
                         *
                         * Здесь цифра занимает левую половину и набрана
                         * крупно — она главная и должна читаться первой,
                         * в том числе боковым зрением при слепом наборе.
                         *
                         * Буквы стоят справа в два ряда: латиница сверху,
                         * кириллица снизу. Порядок не случаен — латиница
                         * нанесена на физические кнопки телефонов
                         * десятилетиями, и глаз ищет её первой.
                         */
                        /*
                         * БУКВЫ ТЕКУЩЕГО ЯЗЫКА — ГЛАВНЫЕ.
                         *
                         * ══════════════════════════════════════════════════
                         * ЧТО БЫЛО НЕ ТАК
                         * ══════════════════════════════════════════════════
                         * Латиница показывалась ВСЕГДА, а буквы локали
                         * добавлялись второй строкой под ней. В русской
                         * локали клавиша несла «ABC» и «АБВГ» одинаково
                         * мелко, хотя набирают тут по-русски: поиск
                         * по буквам работает с той раскладкой, которая
                         * включена.
                         *
                         * Две мелкие строки при этом жались к правому
                         * краю, оставляя середину клавиши пустой.
                         *
                         * ── Как теперь ────────────────────────────────────
                         * Буквы языка идут крупнее и первыми. Латиница
                         * остаётся подсказкой — она нанесена на физические
                         * кнопки десятилетиями, и по ней ищут те, кто
                         * помнит «2 — это ABC». Но она мельче и бледнее:
                         * это справка, а не то, чем пользуются.
                         *
                         * В английской локали буквы языка И ЕСТЬ латиница,
                         * дублировать её незачем — вторая строка тогда
                         * не рисуется вовсе.
                         */
                        val primary = altText.ifEmpty { key.latin }
                        val secondary = if (altText.isNotEmpty()) key.latin else ""

                        /*
                         * ЦИФРА СЛЕВА, БУКВЫ СТОЛБИКОМ СПРАВА — ПАРА ПО ЦЕНТРУ
                         * (1.4.0, автор: «удобнее, когда цифры слева, а буквы
                         * друг над другом… так всё легко читается и
                         * помещается»). Попытка 1.3.5–1.3.6 — цифра по центру,
                         * буквы строкой под ней — лёжа не помещалась (буквы
                         * пропадали) и читалась хуже.
                         *
                         * Пара «цифра + буквы» — по центру пилюли, а не у края:
                         * на скруглённой клавише содержимое у края выглядит
                         * криво. Ячейки фиксированной ширины — цифры всех рядов
                         * стоят ровным столбцом, в том числе у «1», «*», «#»,
                         * где букв нет. Буквы языка сверху и крупнее, латиница
                         * под ними мельче и бледнее. Две короткие строки
                         * не выше цифры — помещаются и лёжа.
                         */
                        /*
                         * 2.7.5 — ЦИФРА ПО ЦЕНТРУ, БУКВЫ ЯЗЫКА ПОД НЕЙ (вариант «А»).
                         * Строки букв есть и у «1», «*», «#» (пустые): цифры ряда
                         * стоят на одной высоте.
                         *
                         * 2.7.6: латиница вернулась третьей строкой, мельче
                         * и бледнее (автор, по снимку с телефона: «клавиатура стала
                         * выглядеть по-другому без латиницы»). В 2.7.5 её убрали,
                         * как на снимке варианта. Строки ужаты по высоте
                         * (32 + 13 + 12 sp), чтобы три строки встали в клавишу 60 dp.
                         */
                        if (stacked) Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                key.digit.toString(),
                                style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 32.sp),
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                            Text(
                                primary,
                                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (latinRow) {
                                Text(
                                    secondary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                    color = MaterialTheme.colorScheme.outline,
                                    letterSpacing = 1.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(DIGIT_SLOT * slotScale), contentAlignment = Alignment.Center) {
                                Text(
                                    key.digit.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.width(Space.s))
                            Column(
                                Modifier.width(LETTERS_SLOT * slotScale),
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (primary.isNotEmpty()) {
                                    // FitText (2.7.7): при крупном шрифте «ДЕЖЗ» ужимается, а не обрезается
                                    FitText(
                                        primary,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        minScale = 0.6f
                                    )
                                }
                                if (secondary.isNotEmpty()) {
                                    Text(
                                        secondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/*
 * Ячейки клавиши (1.4.0): фиксированная ширина — цифры всех рядов в одном
 * столбце. Цифра headlineMedium ≈ 18 dp, «*» и «#» тоже; самая широкая
 * строка букв — «ШЩЪЫ» labelLarge ≈ 40 dp.
 */
private val DIGIT_SLOT = 24.dp
private val LETTERS_SLOT = 44.dp

/** Поле вокруг клавиши (между соседними — вдвое больше). */
private val KEY_GAP = 3.dp

/**
 * Ниже этой высоты ряда — цифра и буквы в строку (2.7.5): цифра (32 sp
 * строки), буквы языка (13 sp) и латиница (12 sp) столбиком — 57, с запасом 58 dp
 * (2.7.6; в 2.7.5 без латиницы было 52).
 */
private val STACK_MIN = 58.dp
