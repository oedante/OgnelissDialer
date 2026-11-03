package com.ogneliss.dialer.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.ogneliss.dialer.settings.UiScale
import androidx.core.view.WindowCompat
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.settings.ThemePreset

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
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue20,
    inversePrimary = Blue80,

    // Вторичная роль нейтральная: выделения и контейнеры не должны
    // спорить с основным акцентом за внимание
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSelected,
    onSecondaryContainer = LightText,

    // Зелёный отдан вызовам: код телефонии, узнаваемый без чтения
    tertiary = Green40,
    onTertiary = Color.White,
    tertiaryContainer = Green90,
    onTertiaryContainer = Green20,

    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error10,

    /*
     * СТРАНИЦА СЕРАЯ, ВСЁ НАД НЕЙ — БЕЛОЕ (1.4.0).
     *
     * Было наоборот: белая страница и почти белые (#FAFAFA) карточки,
     * 1,04 : 1 — группы журнала и блоки карточки контакта сливались.
     * Теперь карточки, шторки, диалоги и меню (Low, Container, High) —
     * белые на светло-сером, 1,10 : 1: зоны видны сразу, без рамок и теней.
     *
     * Highest — серый темнее страницы: нижняя панель (белые клавиши на ней)
     * и кнопки на белом — круги быстрых действий, строки выбора в диалогах.
     * Шкала не монотонна, как в базовой схеме Material, — намеренно: светлое
     * «поднятое» и тёмное «под пальцем» нужны разным ролям, а четырёх
     * едва различимых серых между ними глаз всё равно не видит.
     */
    /*
     * 1.5.3 — «СПЛОШНОЙ СПИСОК» (макет B, автор 11.10): страница белая,
     * строки на ней во всю ширину, разделы — серые полосы (surfaceContainer).
     * В 1.5.1–1.5.2 (макет A) страница была серой, а группы — белыми
     * карточками; после недели с приложением автор выбрал B.
     */
    /*
     * 1.5.5 — МЯГЧЕ (автор, 13 и 18.10: «стиль стал ярким, резким
     * и угловатым, потерял лёгкость»). Страница и шторки — #FAFBFC
     * вместо чисто белого, текст — #22252B вместо почти чёрного: меньше
     * слепящей разницы. Белыми остались диалоги, меню и клавиши —
     * то, что лежит над страницей.
     */
    /*
     * 1.5.6 — КАК OGNELISS MAIL (автор, 18.10, снимки Mail). Список —
     * белый (#FFFFFF), страница настроек — серо-голубая #F2F5FA
     * (OgnelissSurfaces.tintedPage), полосы разделов — скруглённые.
     * Мягкость теперь дают формы и серо-голубые тона, а не приглушённый
     * белый 1.5.5. Разбор — ui/theme/Surfaces.kt.
     */
    background = LightCanvas,
    onBackground = LightText,
    surface = LightCanvas,
    onSurface = LightText,
    surfaceVariant = LightVariant,
    onSurfaceVariant = LightTextVariant,

    surfaceContainerLowest = LightRaised,
    surfaceContainerLow = LightCanvas,
    // Полоса раздела в списке, меню, строка заголовка при прокрутке — #F2F5FA
    surfaceContainer = LightPage,
    surfaceContainerHigh = LightRaised,
    surfaceContainerHighest = LightControl,
    surfaceBright = LightRaised,
    surfaceDim = LightDim,
    // Без голубой дымки на приподнятых Surface: оттенок даёт цвет поверхности
    surfaceTint = Color.Transparent,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color.Black,
    // Всплывающие сообщения — цвет значка приложения
    inverseSurface = BrandDark,
    inverseOnSurface = Color(0xFFEEF0F4)
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue95,
    inversePrimary = Blue40,

    secondary = DarkSecondary,
    onSecondary = DarkPage,
    secondaryContainer = DarkSelected,
    onSecondaryContainer = DarkText,

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
     *
     * 1.4.0: тот же холодный серый, что в светлой теме и на значке;
     * уровни — с ровным шагом (карточка 1,11 : 1 к странице, клавиша
     * 1,28 : 1 к панели).
     */
    background = DarkPage,
    onBackground = DarkText,
    surface = DarkPage,
    onSurface = DarkText,
    surfaceVariant = DarkVariant,
    onSurfaceVariant = DarkTextVariant,

    surfaceContainerLowest = DarkLowest,
    // 1.5.3: строки — цвета страницы (сплошной список), полосы разделов —
    // surfaceContainer, светлее страницы
    surfaceContainerLow = DarkPage,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkHigh,
    surfaceContainerHighest = DarkHighest,
    surfaceBright = DarkHighest,
    surfaceDim = DarkLowest,
    surfaceTint = Color.Transparent,

    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color.Black,
    inverseSurface = DarkText,
    inverseOnSurface = BrandDark
)

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
 * экономия сохраняется, читаемость тоже. Оттенок — тот же холодный (1.4.0).
 */
private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,  // 1.5.3: строки — цвета страницы
    surfaceContainer = Color(0xFF15171A),
    surfaceContainerHigh = Color(0xFF1C1E22),
    surfaceContainerHighest = Color(0xFF26292E)
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
    /** Масштаб интерфейса (1.5.6): разбор в UiScale.kt. */
    uiScale: UiScale = UiScale.DEFAULT,
    /** Цветовая тема, как в Ogneliss Mail (1.8.4): разбор в ThemePresetMath. */
    themePreset: ThemePreset = ThemePreset.CLASSIC,
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
    /*
     * Цветовая тема (1.8.4) — к светлой, тёмной и чёрной палитре; высокий
     * контраст и цвета из обоев — свои, как в почте. «Полночь» в тёмной
     * теме — чистый чёрный (палитра AMOLED).
     */
    val presetApplies = themePreset != ThemePreset.CLASSIC &&
        themeMode != ThemeMode.HIGH_CONTRAST &&
        !(dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    val midnightBlack = presetApplies && darkTheme && ThemePresets.blackDark(themePreset)
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
        themeMode == ThemeMode.BLACK_AMOLED || midnightBlack ->
            presetScheme(AmoledColors, themePreset, presetApplies, dark = true)
        darkTheme -> presetScheme(DarkColors, themePreset, presetApplies, dark = true)
        else -> presetScheme(LightColors, themePreset, presetApplies, dark = false)
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

    // Поверхности в стиле Ogneliss Mail (1.5.6) — под режим темы
    val surfaces = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicSurfaces(scheme, darkTheme)
        themeMode == ThemeMode.HIGH_CONTRAST ->
            if (darkTheme) HighContrastDarkSurfaces else HighContrastLightSurfaces
        themeMode == ThemeMode.BLACK_AMOLED || midnightBlack ->
            if (presetApplies) ThemePresets.surfaces(AmoledSurfaces, themePreset, scheme) else AmoledSurfaces
        darkTheme -> if (presetApplies) ThemePresets.surfaces(DarkSurfaces, themePreset, scheme) else DarkSurfaces
        else -> if (presetApplies) ThemePresets.surfaces(LightSurfaces, themePreset, scheme) else LightSurfaces
    }

    /*
     * МАСШТАБ ИНТЕРФЕЙСА ПОД ЭКРАН (1.5.6) — разбор в UiScale.kt.
     * Подменяется плотность для всего, что внутри темы: размеры в dp
     * умножаются на коэффициент, текст — мягче (корень коэффициента
     * при уменьшении), чтобы не стать мелким.
     */
    val baseDensity = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scaleFactor = remember(uiScale, configuration.smallestScreenWidthDp, configuration.densityDpi) {
        UiScaleMath.factor(uiScale, configuration.smallestScreenWidthDp, configuration.densityDpi)
    }
    val density = remember(baseDensity, scaleFactor) { UiScaleMath.scaledDensity(baseDensity, scaleFactor) }

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalUiScaleFactor provides scaleFactor,
        LocalOgnelissSurfaces provides surfaces
    ) {
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
}

/*
 * Схема темы — один раз на сочетание: расчёт яркостей идёт циклами,
 * а тема собирается заново при каждой смене настроек и на каждом экране
 * вызова (1.8.4).
 */
private val presetSchemes = java.util.concurrent.ConcurrentHashMap<Triple<ColorScheme, ThemePreset, Boolean>, ColorScheme>()

private fun presetScheme(base: ColorScheme, preset: ThemePreset, applies: Boolean, dark: Boolean): ColorScheme =
    if (!applies) base
    else presetSchemes.getOrPut(Triple(base, preset, dark)) { ThemePresets.scheme(base, preset, dark) }
