package com.ogneliss.dialer.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * ФОРМЫ — СТРОГИЙ СТИЛЬ (1.5.1, 11.10.2026).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО ПОМЕНЯЛОСЬ И ПОЧЕМУ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор: «стиль всего приложения — на более строгий, корпоративный»;
 * из двух макетов выбран «A. Строгий, группы» (docs/strict_style_1.5.1.png):
 * белые группы со скруглением 8 dp и тонкой рамкой на сером фоне, подписи
 * разделов капителью; кнопки и клавиши — прямоугольники 8 dp вместо «пилюль».
 *
 * Было по спецификации Material 3: 4 · 8 · 12 · 16 · 28 dp, клавиши
 * и кнопки вызова — полное скругление. Мягко и «по-домашнему»; рядом
 * с почтой и офисными приложениями выглядело игрушечно.
 *
 * ── Где что ────────────────────────────────────────────────────────────────
 * extraSmall (4)  — бейджи SIM, мелкие метки
 * small (6)       — чипы фильтров
 * medium (8)      — строки и группы списков, поля ввода, плашки
 * large (8)       — панели карточки, группы настроек и шторок
 * extraLarge (12) — шторки, диалоги, нижние панели, поиск
 *
 * Круглыми остаются только аватары и круглые кнопки-значки (быстрые
 * действия, кнопки разговора): круг там — форма лица и знака, а не
 * украшение. Для них — CircleShape явно, а не extraLarge (раньше
 * extraLarge 28 dp на 48 dp и давал круг — теперь дал бы квадрат).
 */
val OgnelissShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

/** Формы для случаев, которых нет в стандартном наборе. */
object OgnelissShapeTokens {

    /*
     * Клавиша набора и строка номера — прямоугольник 8 dp (1.5.1, строгий
     * стиль; до этого — «пилюля» с 1.3.5, а ещё раньше 20 dp).
     */
    val dialKey = RoundedCornerShape(8.dp)

    /**
     * НИЖНЯЯ ПАНЕЛЬ.
     *
     * Скруглены только верхние углы: панель прижата к низу экрана,
     * и нижние углы всё равно не видны. 12 dp (было 28) — строгий стиль.
     *
     * ── Почему в токенах ───────────────────────────────────────────────────
     * Форма задавалась числами в двух местах: в панели журнала
     * и в панели контактов. При правке одного второе осталось бы
     * прежним, и панели различались бы при переходе между вкладками.
     */
    val bottomPanel = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

    /** Фото в шапке карточки: нижние углы как у панелей. */
    val coverBottom = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

    /**
     * Кнопки вызова: «Позвонить», SIM, «Ответить», «Отклонить», «Завершить».
     * 8 dp, как клавиши (1.5.1; было полное скругление). Главную кнопку
     * выделяет цвет, а не форма.
     */
    val callButton = RoundedCornerShape(8.dp)

    /**
     * Группа настроек. Скруглены только внешние углы группы,
     * внутренние разделители остаются прямыми — так группа читается
     * как единый блок, а не как набор отдельных карточек.
     */
    val settingsGroupTop = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    val settingsGroupBottom = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
    val settingsGroupSingle = RoundedCornerShape(8.dp)
    val settingsGroupMiddle = RoundedCornerShape(0.dp)

    /** Толщина рамки группы (строгий стиль, 1.5.1). */
    val groupBorderWidth = 1.dp
}

/**
 * РАМКА ГРУППЫ (1.5.1, строгий стиль).
 *
 * Тонкая линия outlineVariant вокруг белых групп: группа читается как
 * блок и там, где подложка почти того же тона (тёмная тема, шторки).
 * Одна функция — один цвет и одна толщина у всех групп приложения.
 */
@Composable
@ReadOnlyComposable
fun groupBorder(): BorderStroke =
    BorderStroke(OgnelissShapeTokens.groupBorderWidth, MaterialTheme.colorScheme.outlineVariant)
