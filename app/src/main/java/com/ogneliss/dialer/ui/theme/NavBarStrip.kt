package com.ogneliss.dialer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * ПОЛОСА ПОД СИСТЕМНОЙ НАВИГАЦИЕЙ — ЦВЕТА НИЖНЕЙ ПАНЕЛИ (1.5.1).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО БЫЛО
 * ══════════════════════════════════════════════════════════════════════════
 * Корень приложения отступает от системных панелей (RootScreen,
 * windowInsetsPadding(safeDrawing)) и этим «съедает» отступ: панели экранов
 * зовут navigationBarsPadding(), но получают ноль и кончаются над кнопками
 * ◁ ○ □. Под ними оставалась полоса цвета страницы — на снимках магазина
 * серая панель журнала, а под ней белая полоса с кнопками навигации.
 * На экране вызова то же самое уже исправлено (CallScreenParts,
 * NavBarUnderPanel, 1.3.5) — здесь тот же приём для главного окна.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК УСТРОЕНО
 * ══════════════════════════════════════════════════════════════════════════
 * Корень рисует полосу высотой в системную навигацию. Её цвет — панели,
 * пока на экране есть нижняя панель, иначе — страницы. Экран с панелью
 * сообщает о ней вызовом [PanelReachesNavBar] внутри панели.
 *
 * Счётчик, а не флаг: при переходе новый экран появляется раньше, чем
 * уходит старый. С флагом уходящий журнал сбросил бы метку, которую
 * только что поставили контакты, и полоса мигнула бы цветом страницы.
 *
 * Отступы корня не меняются: правка видна только в полосе, раскладка
 * экранов та же.
 */
@Stable
class NavBarStripState {
    /** Сколько нижних панелей сейчас на экране (с учётом уходящих). */
    var panels by mutableIntStateOf(0)
        internal set
}

/** Состояние полосы; null — вне главного окна (превью, другие активности). */
val LocalNavBarStrip = staticCompositionLocalOf<NavBarStripState?> { null }

/**
 * Вызвать внутри нижней панели экрана: полоса под навигацией станет
 * её цвета, пока панель на экране. Панель сбоку (клавиатура лёжа)
 * не зовёт — под навигацией там список, а не панель.
 */
@Composable
fun PanelReachesNavBar() {
    val strip = LocalNavBarStrip.current ?: return
    DisposableEffect(strip) {
        strip.panels++
        onDispose { strip.panels-- }
    }
}
