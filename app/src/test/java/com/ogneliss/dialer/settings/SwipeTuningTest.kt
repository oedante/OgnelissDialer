package com.ogneliss.dialer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ПАРАМЕТРОВ СВАЙПА.
 *
 * Значения по умолчанию — не произвольные числа, а результат разбора
 * жалобы «свайп срабатывает случайно при прокрутке». Тесты фиксируют их,
 * чтобы правка в одном месте не сдвинула поведение незаметно: ровно так
 * однажды и вышло, когда пороги остались прежними, хотя в дорожной карте
 * значилось иначе.
 */
class SwipeTuningTest {

    @Test
    fun `значения по умолчанию соответствуют разобранным причинам жалоб`() {
        val d = SwipeTuning.DEFAULT

        // Примерно треть ширины телефона: столько случайно не проводят
        assertEquals(140, d.triggerDistanceDp)

        // Требует довести движение почти до конца — результат предсказуем
        assertEquals(75, d.positionalThresholdPercent)

        // Скорость осознанного броска вбок; обычная прокрутка такой
        // горизонтальной составляющей не даёт
        assertEquals(450, d.velocityDpPerSecond)

        assertTrue(d.requireHorizontalStart)
    }

    @Test
    fun `доля пути переводится в множитель`() {
        assertEquals(0.75f, SwipeTuning.DEFAULT.positionalThreshold, 0.001f)
        assertEquals(
            0.3f,
            SwipeTuning(positionalThresholdPercent = 30).positionalThreshold,
            0.001f
        )
    }

    @Test
    fun `заводская настройка распознаётся`() {
        assertTrue(SwipeTuning.DEFAULT.isDefault)
        assertFalse(SwipeTuning(triggerDistanceDp = 100).isDefault)
        assertFalse(SwipeTuning(requireHorizontalStart = false).isDefault)
    }

    @Test
    fun `границы ползунков включают значения по умолчанию`() {
        // Иначе ползунок нельзя было бы вернуть в исходное положение
        assertTrue(SwipeTuning.DEFAULT_TRIGGER_DP in SwipeTuning.TRIGGER_RANGE)
        assertTrue(SwipeTuning.DEFAULT_POSITIONAL_PERCENT in SwipeTuning.POSITIONAL_RANGE)
        assertTrue(SwipeTuning.DEFAULT_VELOCITY_DP in SwipeTuning.VELOCITY_RANGE)
    }

    @Test
    fun `нижние границы не позволяют сделать жест неуправляемым`() {
        // Слишком малые значения вернули бы ровно ту проблему, ради
        // которой настройка и появилась. Полностью запрещать их
        // неправильно — кому-то так удобнее, — но дно должно быть
        assertTrue(
            "Меньше 60 dp — случайные срабатывания гарантированы",
            SwipeTuning.TRIGGER_RANGE.first >= 60
        )
        assertTrue(
            "Меньше 30 % защёлкивания — жест перестаёт слушаться",
            SwipeTuning.POSITIONAL_RANGE.first >= 30
        )
    }

    @Test
    fun `верхние границы не делают жест бесполезным`() {
        // Слишком большие значения означают, что свайп не срабатывает
        // никогда, и функция превращается в мёртвую
        assertTrue(SwipeTuning.TRIGGER_RANGE.last <= 240)
        assertTrue(SwipeTuning.POSITIONAL_RANGE.last <= 95)
    }
}
