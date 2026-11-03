package com.ogneliss.dialer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
        fontSize = 17.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp
    ),

    // Основной текст чуть крупнее стандарта Material: диалером
    // пользуются в спешке и на ходу
    bodyLarge = TextStyle(
        fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp
    ),

    labelLarge = TextStyle(
        fontSize = 15.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp, lineHeight = 16.sp,
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
 */
val Typography.listName: TextStyle
    get() = titleMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp)
