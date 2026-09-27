package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.ogneliss.dialer.ui.theme.PanelColors
import com.ogneliss.dialer.ui.theme.FlowUi
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

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
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
                            // Клавиша заметно отличается от панели: разбор в PanelColors
                            .background(PanelColors.key())
                            .semantics(mergeDescendants = true) {
                                contentDescription = keyLabel
                                role = Role.Button
                            }
                            .combinedClickable(
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

                        Row(
                            Modifier.fillMaxSize().padding(horizontal = FlowUi.contentPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                key.digit.toString(),
                                // headlineLarge вместо Medium: цифра —
                                // главное на клавише, её читают боковым
                                // зрением при слепом наборе
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Normal
                            )

                            if (primary.isNotEmpty()) {
                                Spacer(Modifier.width(10.dp))

                                Column(
                                    // Буквы прижаты к цифре, а не к краю:
                                    // вместе они читаются как одна метка,
                                    // и середина клавиши перестаёт пустовать
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        primary,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )

                                    if (secondary.isNotEmpty()) {
                                        Text(
                                            secondary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1
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
