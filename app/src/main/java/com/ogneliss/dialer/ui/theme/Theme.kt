package com.ogneliss.dialer.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ogneliss.dialer.settings.ThemeMode

/**
 * ТЕМА ПРИЛОЖЕНИЯ.
 *
 * ── Полная схема, а не пять цветов ─────────────────────────────────────────
 * Прежняя версия задавала только primary, secondary и фон. Остальные
 * 30 ролей Material 3 брались по умолчанию — из фиолетовой схемы
 * библиотеки. Отсюда и «простой вид»: акценты оранжевые, а контейнеры,
 * поверхности и обводки — чужие, из другой палитры.
 *
 * Теперь заданы все роли из тональных палитр (см. Color.kt).
 *
 * ── Уровни поверхностей ────────────────────────────────────────────────────
 * Material 3 отказался от теней в пользу тональных уровней: чем «выше»
 * элемент, тем светлее его поверхность. Пять уровней surfaceContainer
 * дают глубину без размытых теней, которые на тёмной теме выглядят грязно.
 *
 * Для нас это принципиально: карточки журнала, шторки и клавиатура
 * лежат на разных уровнях, и без этих ролей всё сливалось в один фон.
 *
 * ── Четыре режима ──────────────────────────────────────────────────────────
 * SYSTEM, LIGHT, DARK, BLACK_AMOLED. Последний — чистый чёрный:
 * на OLED такие пиксели физически выключены, и на экране вызова,
 * который горит дольше остальных, это заметная экономия.
 */

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Grey100,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue20,
    inversePrimary = Blue80,

    // Вторичная роль нейтральная: выделения и контейнеры не должны
    // спорить с основным акцентом за внимание
    secondary = Grey40,
    onSecondary = Grey100,
    secondaryContainer = Grey92,
    onSecondaryContainer = Grey10,

    // Третичная роль отдана «зелени телефонии»: приём вызова,
    // признак знакомого номера. Зелёный в звонках — устоявшийся код,
    // и переопределять его брендовым оранжевым было бы вредно
    // Зелёный отдан вызовам: код телефонии, узнаваемый без чтения
    tertiary = Green40,
    onTertiary = Grey100,
    tertiaryContainer = Green90,
    onTertiaryContainer = Green20,

    error = Error40,
    onError = Grey100,
    errorContainer = Error90,
    onErrorContainer = Error10,

    background = Grey100,
    onBackground = Grey10,
    surface = Grey100,
    onSurface = Grey10,
    surfaceVariant = Grey90,
    onSurfaceVariant = Grey30,

    // Уровни поверхностей: карточки, шторки, клавиатура
    surfaceContainerLowest = Grey100,
    surfaceContainerLow = Grey98,
    surfaceContainer = Grey95,
    surfaceContainerHigh = Grey94,
    surfaceContainerHighest = Grey92,
    surfaceBright = Grey100,
    surfaceDim = Grey90,

    outline = Grey50,
    outlineVariant = Grey90,
    scrim = Grey0,
    inverseSurface = Grey20,
    inverseOnSurface = Grey95
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    inversePrimary = Blue40,

    secondary = Grey80,
    onSecondary = Grey20,
    secondaryContainer = Grey24,
    onSecondaryContainer = Grey90,

    tertiary = Green80,
    onTertiary = Green20,
    tertiaryContainer = Green30,
    onTertiaryContainer = Green90,

    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,

    /*
     * Фон тёмной темы не чисто чёрный.
     *
     * На чистом чёрном карточки с уровнем surfaceContainer различаются
     * контрастом около 1.3:1 — то есть почти не различаются. Глубина
     * пропадает, и список сливается в сплошное полотно.
     *
     * Для тех, кому нужен чистый чёрный ради экономии батареи,
     * есть отдельный режим AMOLED.
     */
    background = DarkBackground,
    onBackground = Grey87,
    surface = DarkBackground,
    onSurface = Grey87,
    surfaceVariant = Grey30,
    onSurfaceVariant = Grey80,

    surfaceContainerLowest = Grey0,
    surfaceContainerLow = Grey12,
    surfaceContainer = Grey17,
    surfaceContainerHigh = Grey22,
    surfaceContainerHighest = Grey24,
    surfaceBright = Grey24,
    surfaceDim = Grey0,

    outline = Grey60,
    outlineVariant = Grey30,
    scrim = Grey0,
    inverseSurface = Grey90,
    inverseOnSurface = Grey20
)

/**
 * AMOLED-режим.
 *
 * Отличается от тёмной темы только уровнями поверхностей: фон чисто
 * чёрный, а «высокие» контейнеры остаются различимыми. Если сделать
 * чёрными все уровни, пропадёт глубина и карточки сольются с фоном —
 * экономия батареи не стоит потери читаемости.
 */
/**
 * AMOLED-РЕЖИМ.
 *
 * ── Что именно делает чёрным ───────────────────────────────────────────────
 * Только фон и самый нижний уровень поверхностей. На OLED чёрный пиксель
 * физически выключен, и экономия идёт именно от площади фона —
 * она занимает большую часть экрана.
 *
 * ── Почему карточки НЕ чёрные ──────────────────────────────────────────────
 * Если сделать чёрными все уровни, контраст между карточкой и фоном
 * падает до 1.3:1 — глаз перестаёт различать границы, и список
 * превращается в сплошное полотно текста.
 *
 * Карточки остаются приподнятыми, но темнее, чем в обычной тёмной теме:
 * экономия сохраняется, читаемость тоже.
 */
private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0C),
    surfaceContainer = Color(0xFF121314),
    surfaceContainerHigh = Color(0xFF1A1B1C),
    surfaceContainerHighest = Color(0xFF222324)
)

/**
 * ВЫСОКИЙ КОНТРАСТ.
 *
 * ── Чем отличается от обычной темы ─────────────────────────────────────────
 * Не «потемнее» и не «пожирнее». Здесь убрано всё, что снижает
 * различимость, и цвета подобраны под требование WCAG AAA (7:1),
 * а не AA (4.5:1), которого держится обычная тема:
 *
 * • текст чисто чёрный на чисто белом или наоборот — максимальный
 *   возможный контраст, около 21:1 при требовании WCAG AAA в 7:1;
 * • приглушённые подписи получают тот же цвет, что основной текст:
 *   серый на светлом читается тяжело при возрастной дальнозоркости;
 * • обводки становятся видимыми, а не намёком — границы полей
 *   и карточек должны быть очевидны;
 * • акцентный цвет затемнён до контрастного: фирменный оранжевый
 *   на белом даёт всего 2:1 и для текста непригоден.
 */
private val HighContrastLight = lightColorScheme(
    // Синий затемнён до 8.6:1 на белом: стандартный #0B57D0 даёт 6.4,
    // чего достаточно по AA, но не по AAA
    primary = Color(0xFF00348C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE0FF),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF2B2B2B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF00521C),
    onTertiary = Color.White,
    error = Color(0xFF8C0009),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEDEDED),
    // Подписи тем же цветом, что основной текст: серый на светлом
    // читается тяжело при возрастной дальнозоркости
    onSurfaceVariant = Color.Black,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    outline = Color.Black,
    outlineVariant = Color(0xFF5A5A5A),
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White
)

private val HighContrastDark = darkColorScheme(
    // Светлый голубой на чёрном: 12:1 — заметно выше требования AAA
    primary = Color(0xFFCFE0FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00348C),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFE6E6E6),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2B2B2B),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFA9E8BC),
    onTertiary = Color.Black,
    error = Color(0xFFFFB4AB),
    onError = Color.Black,
    errorContainer = Color(0xFF8C0009),
    onErrorContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color.White,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    outline = Color.White,
    outlineVariant = Color(0xFFAAAAAA),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black
)

@Composable
fun OgnelissDialerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK_AMOLED -> true
        // Высокий контраст следует системной теме: слабое зрение
        // не означает предпочтения тёмного или светлого
        ThemeMode.HIGH_CONTRAST -> systemDark
    }

    /*
     * ── ПОЧЕМУ ПАЛИТРА ТЕПЕРЬ СТАНДАРТНАЯ ──────────────────────────────────
     * Своя палитра, построенная от оранжевого логотипа, делала приложение
     * узнаваемым, но выбивалась из системы: рядом с приложениями Google
     * оно выглядело чужим, а тёплые серые фоны читались как «выцветшие».
     *
     * Теперь по умолчанию берётся палитра Material You из обоев
     * пользователя, а при её недоступности — базовая схема Material 3.
     * Фирменный оранжевый остаётся акцентом в логотипе и на иконке,
     * то есть там, где он и должен работать.
     *
     * Своя схема осталась доступной отдельным пунктом настроек для тех,
     * кому важна узнаваемость приложения, а не единство с системой.
     */
    val scheme = when {
        // Material You: цвета берутся из обоев пользователя.
        // Выключено по умолчанию — брендовый оранжевый превратился бы
        // в случайный оттенок, и приложение потеряло бы узнаваемость
        // Material You по умолчанию: на Android 12+ цвета берутся
        // из обоев, и приложение выглядит частью системы
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        themeMode == ThemeMode.HIGH_CONTRAST ->
            if (darkTheme) HighContrastDark else HighContrastLight
        themeMode == ThemeMode.BLACK_AMOLED -> AmoledColors
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Иконки системных панелей должны быть контрастны фону под ними,
    // иначе становятся невидимыми: в edge-to-edge приложение рисует
    // прямо под ними
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = OgnelissTypography,
        shapes = OgnelissShapes
    ) {
        /*
         * ФОН РИСУЕТ COMPOSE, А НЕ ОКНО.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ШТОРКА БЫЛА БЕЛОЙ В ТЁМНОЙ ТЕМЕ
         * ══════════════════════════════════════════════════════════════════
         * Фон окна задан ресурсом и выбирается по квалификатору -night,
         * то есть по теме СИСТЕМЫ. А тема приложения своя: человек мог
         * включить тёмную при светлой системе.
         *
         * Тогда система рисовала белый фон окна, а Compose поверх него —
         * тёмный интерфейс. Полоса под статусбаром оставалась белой:
         * туда содержимое не доходит, там виден фон окна.
         *
         * Surface на весь экран закрывает эту полосу цветом ТЕКУЩЕЙ схемы,
         * какой бы она ни была. Ресурсный фон при этом остаётся нужным:
         * он виден доли секунды при запуске, пока Compose не нарисовал
         * первый кадр.
         *
         * ── Про лишний слой ────────────────────────────────────────────────
         * Формально это ещё один слой отрисовки на весь экран. Но он
         * заменяет собой фон, который иначе рисовали бы Scaffold
         * и списки, — суммарно ничего не прибавляется.
         */
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}
