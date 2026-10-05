package com.ogneliss.dialer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * ФОРМЫ MATERIAL 3.
 *
 * ── Зачем система форм ─────────────────────────────────────────────────────
 * Скругление в Material 3 несёт смысл, а не украшает. Радиус говорит
 * о размере и роли элемента: чем крупнее блок, тем сильнее скругление.
 * Одинаковый радиус у чипа и у диалога делает интерфейс плоским
 * и невыразительным — глазу не за что зацепиться при поиске нужного.
 *
 * ── Где что используется у нас ─────────────────────────────────────────────
 * extraSmall (4)  — бейджи SIM, мелкие метки
 * small (8)       — чипы фильтров
 * medium (12)     — карточки списка, поля ввода
 * large (16)      — клавиши набора, группы настроек
 * extraLarge (28) — шторки, диалоги, кнопка вызова
 *
 * Значения взяты из спецификации Material 3, а не подобраны на глаз:
 * они согласованы с формами системных элементов Android, и интерфейс
 * не выглядит чужеродным среди них.
 */
val OgnelissShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Формы для случаев, которых нет в стандартном наборе. */
object OgnelissShapeTokens {

    /**
     * Клавиша набора. Скругление 20 dp при высоте 72 — форма ближе
     * к «таблетке», чем к квадрату: пальцу так проще целиться, край
     * читается как граница нажимаемой области.
     */
    val dialKey = RoundedCornerShape(20.dp)

    /**
     * НИЖНЯЯ ПАНЕЛЬ.
     *
     * Скруглены только верхние углы: панель прижата к низу экрана,
     * и нижние углы всё равно не видны — скруглять их значило бы
     * оставить полосу фона под ними.
     *
     * ── Почему в токенах ───────────────────────────────────────────────────
     * Форма задавалась числами в двух местах: в панели журнала
     * и в панели контактов. При правке одного второе осталось бы
     * прежним, и панели различались бы при переходе между вкладками —
     * заметно ровно настолько, чтобы раздражать, не позволяя понять,
     * что не так.
     */
    val bottomPanel = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Фото в шапке карточки: нижние углы как у панелей (1.3.4). */
    val coverBottom = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

    /**
     * Кнопка вызова. Полное скругление — это главная кнопка экрана,
     * и форма выделяет её среди прямоугольных клавиш.
     */
    val callButton = RoundedCornerShape(percent = 50)

    /**
     * Группа настроек. Скруглены только внешние углы группы,
     * внутренние разделители остаются прямыми — так группа читается
     * как единый блок, а не как набор отдельных карточек.
     */
    val settingsGroupTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val settingsGroupBottom = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    val settingsGroupSingle = RoundedCornerShape(16.dp)
    val settingsGroupMiddle = RoundedCornerShape(0.dp)
}
