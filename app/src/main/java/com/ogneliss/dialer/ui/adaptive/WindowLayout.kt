package com.ogneliss.dialer.ui.adaptive

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.flow.catch

/**
 * РАСКЛАДКА ОКНА: ТЕЛЕФОН, ТЕЛЕФОН ЛЁЖА, РАСКЛАДУШКА, ПЛАНШЕТ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ПО РАЗМЕРУ ОКНА, А НЕ ПО ОРИЕНТАЦИИ
 * ══════════════════════════════════════════════════════════════════════════
 * До 1.1.2 экраны сами решали, «широко ли», — и каждый по-своему:
 * одни сравнивали ширину с высотой, экран вызова смотрел на высоту
 * меньше 480 dp. На раскладушке это ломалось: раскрытый Galaxy Fold
 * в портрете — почти квадрат 673 × 841 dp. Ориентация «портретная»,
 * ни одна проверка не срабатывала, хотя места вдвое больше, чем
 * на телефоне.
 *
 * Теперь решение одно на всё приложение и принимается по размеру окна —
 * так советует Google для больших экранов. Окно, а не экран: в режиме
 * двух приложений рядом наше окно — половина планшета, и раскладка
 * должна быть телефонной.
 *
 * ── Пороги ─────────────────────────────────────────────────────────────────
 * Ширина 600 и 840 dp — стандартные границы классов окна Material.
 * Высота 480 dp — граница «низкого» окна: телефон лёжа. Проверяется
 * ПЕРВОЙ, потому что телефон лёжа по ширине (~800 dp) попадал бы
 * в раскладушки, а места по вертикали у него меньше, чем у любого
 * другого случая.
 */
enum class LayoutMode {
    /** Телефон в портрете: как было всегда. */
    PHONE,

    /** Телефон лёжа: места мало по высоте — клавиатура и колонки сбоку. */
    PHONE_LANDSCAPE,

    /** Раскладушка, планшет в портрете: две колонки, где есть пара «список — подробности». */
    FOLDABLE,

    /** Планшет и раскладушка в альбомном: две колонки везде, боковая навигация. */
    TABLET
}

/**
 * Положение раскладушки.
 *
 * Отдельно от размера: полураскрытая раскладушка может быть любого
 * размера окна, а важно ей другое — не класть содержимое на сгиб.
 */
enum class FoldPosture {
    /** Не раскладушка, или раскрыта полностью и сгиб не мешает. */
    FLAT,

    /** Сгиб вертикальный, полураскрыта: держат как книгу. */
    BOOK,

    /** Сгиб горизонтальный, полураскрыта: стоит на столе как ноутбук. */
    TABLETOP
}

/**
 * Сгиб в координатах окна, в dp.
 *
 * Нужен этапам, где содержимое раскладывается по обе стороны сгиба:
 * экран вызова в положении «ноутбук» — собеседник над сгибом, кнопки под ним.
 */
@Immutable
data class Hinge(
    val vertical: Boolean,
    val startDp: Float,
    val endDp: Float
)

@Immutable
data class WindowLayout(
    val mode: LayoutMode,
    val posture: FoldPosture,
    val widthDp: Int,
    val heightDp: Int,
    val hinge: Hinge? = null
) {
    /** Окно шире, чем высокое. Для пропорций картинок, не для выбора раскладки. */
    val isLandscape: Boolean get() = widthDp > heightDp

    /** Мало места по высоте: телефон лёжа. */
    val isShort: Boolean get() = heightDp < SHORT_HEIGHT_DP

    /**
     * Шире телефона: имеет смысл ограничивать ширину текста и строк.
     *
     * Строка настройки во всю ширину планшета — две тысячи точек:
     * глаз теряет начало строки, пока дойдёт до переключателя справа.
     */
    val isWide: Boolean get() = mode != LayoutMode.PHONE

    /**
     * Есть место поставить рядом вторую колонку шириной с телефон.
     *
     * Телефон лёжа — всегда: у него беда не с шириной, а с высотой,
     * и вторая колонка возвращает место, съеденное высотой. Остальные —
     * от 720 dp: 360 на вторую колонку и не меньше 360 на основную.
     * Раскрытая раскладушка в портрете (~673 dp) сюда не попадает:
     * основной колонке осталось бы ~310 dp, уже самого узкого телефона.
     *
     * Один признак на журнал (клавиатура сбоку) и карточку (две колонки):
     * если разойдутся, повернув телефон, человек увидит журнал в одной
     * раскладке, а карточку — в другой.
     */
    val hasRoomForSidePanel: Boolean
        get() = mode == LayoutMode.PHONE_LANDSCAPE || widthDp >= SIDE_BY_SIDE_MIN_WIDTH_DP

    /** Помещаются две колонки «список — подробности». */
    val hasTwoPanes: Boolean get() = mode == LayoutMode.FOLDABLE || mode == LayoutMode.TABLET

    companion object {
        const val SHORT_HEIGHT_DP = 480
        const val MEDIUM_WIDTH_DP = 600
        const val EXPANDED_WIDTH_DP = 840
        const val SIDE_BY_SIDE_MIN_WIDTH_DP = 720

        /** Телефон в портрете — значение по умолчанию, пока окно не измерено. */
        val Phone = WindowLayout(LayoutMode.PHONE, FoldPosture.FLAT, 400, 800)

        /**
         * Раскладка по размеру окна. Отдельная функция без Compose —
         * чтобы проверить пороги обычным модульным тестом.
         */
        fun classify(widthDp: Int, heightDp: Int): LayoutMode = when {
            heightDp < SHORT_HEIGHT_DP -> LayoutMode.PHONE_LANDSCAPE
            widthDp >= EXPANDED_WIDTH_DP -> LayoutMode.TABLET
            widthDp >= MEDIUM_WIDTH_DP -> LayoutMode.FOLDABLE
            else -> LayoutMode.PHONE
        }
    }
}

/**
 * Текущая раскладка окна для любого экрана.
 *
 * static, а не обычный: меняется только при смене размера окна,
 * а тогда пересобирается всё дерево в любом случае. static дешевле —
 * Compose не следит, кто именно его прочитал.
 */
val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.Phone }

/**
 * Поставить раскладку окна для всего содержимого активности.
 *
 * Вызывается один раз, в setContent каждой активности: главной,
 * экрана вызова и псевдозвонка.
 */
@Composable
fun ProvideWindowLayout(activity: Activity, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    /*
     * Сгиб приходит потоком: он меняется, когда раскладушку складывают
     * и раскрывают, не пересоздавая окно.
     *
     * ⚠️ На телефонах без сгиба библиотека просто не присылает
     * FoldingFeature — это не ошибка. А если служба окна на прошивке
     * отвечает исключением, остаёмся в FLAT: лучше обычная раскладка,
     * чем упавшее приложение.
     */
    val fold by produceState<FoldingFeature?>(null, activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .catch { Logger.w("WindowLayout", "Не удалось узнать положение сгиба", it) }
            .collect { info ->
                value = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            }
    }

    val layout = remember(configuration.screenWidthDp, configuration.screenHeightDp, fold, density) {
        val width = configuration.screenWidthDp
        val height = configuration.screenHeightDp

        val posture = when {
            fold == null || fold?.state != FoldingFeature.State.HALF_OPENED -> FoldPosture.FLAT
            fold?.orientation == FoldingFeature.Orientation.HORIZONTAL -> FoldPosture.TABLETOP
            else -> FoldPosture.BOOK
        }

        val hinge = fold?.let { f ->
            val vertical = f.orientation == FoldingFeature.Orientation.VERTICAL
            val b = f.bounds
            with(density) {
                if (vertical) Hinge(true, b.left.toFloat().toDp().value, b.right.toFloat().toDp().value)
                else Hinge(false, b.top.toFloat().toDp().value, b.bottom.toFloat().toDp().value)
            }
        }

        WindowLayout(
            mode = WindowLayout.classify(width, height),
            posture = posture,
            widthDp = width,
            heightDp = height,
            hinge = hinge
        )
    }

    CompositionLocalProvider(LocalWindowLayout provides layout, content = content)
}

/**
 * Показана ли боковая полоса навигации (RootScreen).
 *
 * Отдельно от раскладки окна: внутри панели «список — подробности»
 * раскладка переопределяется по ширине панели (см. ProvidePaneLayout),
 * и по ней уже не понять, что слева от всего экрана есть полоса.
 * А панели журнала это важно: при полосе кнопки «Контакты»
 * и «Напоминания» на ней лишние.
 */
val LocalNavigationRailShown = staticCompositionLocalOf { false }

/**
 * РАСКЛАДКА ДЛЯ ПАНЕЛИ, А НЕ ДЛЯ ВСЕГО ОКНА.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * На планшете журнал и карточка стоят рядом, каждая — на половине
 * экрана. Но их собственные решения о раскладке смотрят на окно
 * целиком: окно широкое — значит, клавиатуру сбоку, карточку в две
 * колонки. На половине экрана это давало бы журнал шириной в палец
 * рядом с клавиатурой и карточку, разрезанную на узкие колонки.
 *
 * Здесь раскладка переопределяется по размеру самой панели: для экрана
 * внутри она — «окно». Половина планшета — это раскладушка
 * или телефон, и экраны выбирают раскладку, подходящую своему месту,
 * ничего не зная о соседях.
 *
 * Положение сгиба и сам сгиб переносятся как есть: сгиб — свойство
 * устройства, а не панели.
 */
@Composable
fun ProvidePaneLayout(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        val parent = LocalWindowLayout.current
        val w = maxWidth.value.toInt()
        val h = maxHeight.value.toInt()
        val pane = parent.copy(
            mode = WindowLayout.classify(w, h),
            widthDp = w,
            heightDp = h
        )
        CompositionLocalProvider(LocalWindowLayout provides pane, content = content)
    }
}
