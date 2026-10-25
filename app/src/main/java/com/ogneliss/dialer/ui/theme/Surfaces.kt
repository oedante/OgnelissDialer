package com.ogneliss.dialer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * ПОВЕРХНОСТИ В СТИЛЕ OGNELISS MAIL (1.5.6, 18.10.2026).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ОТКУДА
 * ══════════════════════════════════════════════════════════════════════════
 * Автор: «давай интерфейс сделаем на подобии Ogneliss Mail» — два снимка
 * экрана Mail (настройки и список писем). Цвета сняты по пикселям снимков
 * (1116 × 2480, плотность ≈ 2,17):
 *
 *   страница настроек       #F2F5FA   серо-голубая, под карточками
 *   полоса раздела на ней   #E3E8EE   скругление ≈ 11 dp, поля 14 dp
 *   карточка группы         #FFFFFF   скругление ≈ 22 dp, рамка #EAEDF2
 *   список писем            #FFFFFF   строки во всю ширину
 *   полоса раздела в списке #F2F5FA   та же форма, что в настройках
 *   линия между строками    #E2E5EA   от начала текста
 *   кольцо аватара          #C4CBD5   ≈ 2 dp
 *   нижняя навигация        #E8EBF0   с тенью сверху
 *   центральная кнопка      #D9E4F6   ≈ primaryContainer, с тенью
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ДВА ВИДА СТРАНИЦ — PageTone
 * ══════════════════════════════════════════════════════════════════════════
 * LIST — списки (журнал, контакты, заметки, напоминания): белая страница,
 *   строки во всю ширину, разделы — скруглённые полосы #F2F5FA.
 * TINTED — настройки и всё, что устроено как настройки (карточка контакта,
 *   «О приложении», резервная копия, чёрный список, статистика…):
 *   серо-голубая страница, разделы — полосы #E3E8EE, под ними белые
 *   карточки с рамкой.
 *
 * Экран объявляет свой вид один раз (параметр tone у ScreenScaffold или
 * LocalPageTone вокруг своего Scaffold), а полосы, карточки и строки групп (SectionBand,
 * SettingsGroup, OgnelissPanel, GroupedRow) подстраиваются сами — правок
 * в каждом месте использования не нужно.
 */
enum class PageTone { LIST, TINTED }

val LocalPageTone = staticCompositionLocalOf { PageTone.LIST }

@Immutable
data class OgnelissSurfaces(
    /** Страница настроек и карточки контакта (TINTED). */
    val tintedPage: Color,
    /** Полоса раздела на белом списке (LIST). */
    val listBand: Color,
    /** Полоса раздела на серо-голубой странице (TINTED). */
    val tintBand: Color,
    /** Карточка группы на TINTED-странице. */
    val card: Color,
    /** Рамка карточки — тоньше и светлее линий между строками. */
    val cardBorder: Color,
    /** Кольцо вокруг аватара в списках. */
    val avatarRing: Color,
    /** Силуэт в аватаре незнакомого номера (1.6.1) — приглушённый. */
    val avatarGlyph: Color,
    /** Линия между строками: непрозрачный готовый цвет. */
    val divider: Color,
    /** Подложка активной кнопки панели и центральной кнопки (как в Mail). */
    val navIndicator: Color
)

internal val LightSurfaces = OgnelissSurfaces(
    tintedPage = LightPage,
    listBand = LightPage,
    tintBand = LightBand,
    card = LightRaised,
    cardBorder = LightCardBorder,
    avatarRing = LightAvatarRing,
    avatarGlyph = LightAvatarGlyph,
    divider = LightOutlineVariant,
    navIndicator = Blue90
)

internal val DarkSurfaces = OgnelissSurfaces(
    tintedPage = DarkPage,
    listBand = DarkCard,
    tintBand = DarkContainer,
    card = DarkCard,
    cardBorder = DarkCardBorder,
    avatarRing = DarkAvatarRing,
    avatarGlyph = DarkAvatarGlyph,
    divider = DarkDivider,
    navIndicator = Blue30
)

internal val AmoledSurfaces = OgnelissSurfaces(
    tintedPage = Color.Black,
    listBand = Color(0xFF15171A),
    tintBand = Color(0xFF1A1C20),
    card = Color(0xFF111214),
    cardBorder = Color(0xFF23262B),
    avatarRing = Color(0xFF2E3237),
    avatarGlyph = Color(0xFF5E646D),
    divider = Color(0xFF222428),
    navIndicator = Blue30
)

/**
 * Высокий контраст: границы видны всегда, полосы — заметные серые.
 */
internal val HighContrastLightSurfaces = OgnelissSurfaces(
    tintedPage = Color(0xFFF0F0F0),
    listBand = Color(0xFFE6E6E6),
    tintBand = Color(0xFFDADADA),
    card = Color.White,
    cardBorder = Color(0xFF5A5A5A),
    avatarRing = Color(0xFF5A5A5A),
    avatarGlyph = Color(0xFF5A5A5A),
    divider = Color(0xFF5A5A5A),
    navIndicator = Color(0xFFCFE0FF)
)

internal val HighContrastDarkSurfaces = OgnelissSurfaces(
    tintedPage = Color.Black,
    listBand = Color(0xFF1F1F1F),
    tintBand = Color(0xFF2A2A2A),
    card = Color(0xFF121212),
    cardBorder = Color(0xFFAAAAAA),
    avatarRing = Color(0xFFAAAAAA),
    avatarGlyph = Color(0xFFAAAAAA),
    divider = Color(0xFFAAAAAA),
    navIndicator = Color(0xFF00348C)
)

/**
 * Material You (цвета из обоев): поверхности — из ролей динамической схемы,
 * иначе серо-голубые полосы спорили бы с оттенком обоев.
 */
internal fun dynamicSurfaces(scheme: ColorScheme, dark: Boolean) = OgnelissSurfaces(
    tintedPage = scheme.surfaceContainer,
    listBand = scheme.surfaceContainer,
    tintBand = scheme.surfaceContainerHighest,
    card = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest,
    cardBorder = scheme.outlineVariant.copy(alpha = 0.6f).compositeOver(scheme.surfaceContainerLowest),
    avatarRing = scheme.outlineVariant.copy(alpha = 0.6f).compositeOver(scheme.surface),
    avatarGlyph = scheme.outline.copy(alpha = 0.6f).compositeOver(scheme.surfaceContainerHighest),
    divider = scheme.outlineVariant.copy(alpha = 0.7f).compositeOver(scheme.surface),
    navIndicator = scheme.primaryContainer
)

val LocalOgnelissSurfaces = staticCompositionLocalOf { LightSurfaces }

/** Доступ к поверхностям Mail-стиля: `OgnelissTheme.surfaces.card`. */
object OgnelissTheme {
    val surfaces: OgnelissSurfaces
        @Composable
        @ReadOnlyComposable
        get() = LocalOgnelissSurfaces.current

    val pageTone: PageTone
        @Composable
        @ReadOnlyComposable
        get() = LocalPageTone.current

    /** Фон страницы по её виду: белый список или серо-голубые настройки. */
    @Composable
    @ReadOnlyComposable
    fun pageColor(tone: PageTone = LocalPageTone.current): Color =
        if (tone == PageTone.TINTED) surfaces.tintedPage else MaterialTheme.colorScheme.background

    /** Цвет полосы раздела на текущей странице. */
    @Composable
    @ReadOnlyComposable
    fun bandColor(): Color =
        if (LocalPageTone.current == PageTone.TINTED) surfaces.tintBand else surfaces.listBand
}
