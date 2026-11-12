package com.ogneliss.dialer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.UiFont
import androidx.compose.ui.unit.sp

/**
 * ТИПОГРАФИКА MATERIAL 3.
 *
 * ── Почему все размеры в sp ────────────────────────────────────────────────
 * sp масштабируется системной настройкой размера шрифта, dp — нет.
 * Пользователь, увеличивший шрифт из-за слабого зрения, должен увидеть
 * увеличенный текст и здесь. Для диалера это не мелочь: им пользуются
 * в том числе пожилые.
 *
 * ── Пять ролей и как они соотносятся ───────────────────────────────────────
 * display  — набранный номер. Единственное место, где нужен крупный текст
 * headline — заголовки экранов, имя на экране вызова
 * title    — заголовки разделов и карточек
 * body     — основной текст, описания настроек
 * label    — подписи кнопок, чипы, метки
 *
 * ── Отступ между строками задан явно ───────────────────────────────────────
 * Без lineHeight текст с крупным системным шрифтом сжимается: строки
 * налезают друг на друга, и описания настроек становятся нечитаемыми.
 * Множитель около 1.4 — то, что рекомендует Material 3.
 *
 * ── Межбуквенный интервал у мелкого текста ─────────────────────────────────
 * У label и body положительный letterSpacing: мелкий текст без него
 * выглядит плотной серой массой. У крупного — отрицательный, иначе
 * заголовок кажется разреженным.
 */
val OgnelissTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 57.sp, lineHeight = 64.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Normal
    ),
    /*
     * Набранный номер: тонкое начертание крупного размера выглядит
     * опрятнее жирного и привычно для номеронабирателей.
     *
     * 30 sp, а не 34. При 34 номер с кодом страны — «+7 961 777-88-99»,
     * шестнадцать знаков — занимал почти всю ширину строки, и строка
     * получалась выше соседних кнопок: 42 sp высоты строки против 48 dp
     * значков по бокам, плюс поля. Клавиатура от этого вырастала,
     * и журнала над ней оставалось меньше.
     *
     * 30 sp по-прежнему крупнее всего остального на экране — номер
     * читается с расстояния вытянутой руки, — но строка перестаёт
     * доминировать над клавишами, ради которых её и открывают.
     *
     * Используется только в строке набора: проверено поиском по коду.
     */
    displaySmall = TextStyle(
        fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light
    ),

    headlineLarge = TextStyle(
        fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal
    ),

    titleLarge = TextStyle(
        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp
    ),

    /*
     * РАЗМЕРЫ — КАК В OGNELISS MAIL (2.2.1, дизайн-система Д1).
     * Было: основной текст и подписи на 1 sp крупнее Material (17/15/13)
     * при масштабе 90 % по умолчанию — на экране тот же текст, что в почте.
     * Масштаб теперь считается как в почте (UiScale.kt), поэтому и размеры
     * те же, что у неё — умолчания Material 3 (16/14/12): одинаковые числа
     * × одинаковый масштаб. Кому мелко — «Оформление → Размер шрифта».
     */
    bodyLarge = TextStyle(
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),

    labelLarge = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp
    )
)

/**
 * ИМЯ В СТРОКЕ СПИСКА — КАК В OGNELISS MAIL (1.8.4).
 *
 * Автор: «Проверь ещё и масштаб. В Ogneliss Mail он мельче». Замер по
 * снимкам обоих приложений: заголовки, подписи разделов, строки настроек,
 * значки и нижняя панель — одного размера (±3 %); крупнее только имена
 * в журнале, контактах, заметках и напоминаниях — на 17 %. У почты имя
 * отправителя — titleSmall (14 sp, Medium) при её «Авто»-масштабе
 * (~96 % на этом телефоне), у диалера было titleMedium (17 sp) при 90 %.
 *
 * 15 sp при масштабе 90 % — те же 13,5 sp на экране, что 14 sp почты при 96 %.
 * Строки под именем (тип вызова, дата, оператор — bodySmall) с почтой
 * уже совпадали.
 *
 * 2.2.1: масштаб считается как в почте — и имя то же, что у неё:
 * 14 sp Medium (titleSmall), без пересчёта через свой масштаб.
 */
val Typography.listName: TextStyle
    get() = titleMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp)

/**
 * ШРИФТ ИНТЕРФЕЙСА НА ВЫБОР (1.9.0, как «Шрифт интерфейса» Ogneliss Mail).
 *
 * • «Системный» — шрифт телефона, прежний вид (по умолчанию): у Samsung,
 *   Xiaomi и других — свой, у Pixel — Roboto.
 * • «Roboto» — из пакета приложения (res/font, Roboto 2.138, Apache 2.0 —
 *   та же лицензия, что у приложения; те же файлы, что в почте): одинаков
 *   на любом телефоне, как почта по умолчанию.
 * • «Noto Serif», «Roboto Condensed» — шрифты системы по их общим именам
 *   («serif», «sans-serif-condensed»): есть на всех телефонах, у оболочек
 *   производителей — их замена. Приложение их не распространяет.
 *
 * Размеры, начертания и интервалы — те же (OgnelissTypography), меняется
 * только семейство.
 */
fun uiFontFamily(font: UiFont): FontFamily? = when (font) {
    UiFont.SYSTEM -> null
    UiFont.ROBOTO -> RobotoFamily
    UiFont.SERIF -> FontFamily.Serif
    UiFont.CONDENSED -> condensedFamily
}

/** Типографика со шрифтом [font]; «Системный» — прежняя, тот же объект. */
fun typographyFor(font: UiFont): Typography {
    val family = uiFontFamily(font) ?: return OgnelissTypography
    return typographies.getOrPut(font) { OgnelissTypography.withFamily(family) }
}

private val RobotoFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

// Создаётся при первом выборе: системный шрифт по имени, начертания
// (обычное, Medium, полужирное) подбирает система
private val condensedFamily: FontFamily by lazy {
    FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
}

private val typographies = java.util.concurrent.ConcurrentHashMap<UiFont, Typography>()

private fun Typography.withFamily(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)
