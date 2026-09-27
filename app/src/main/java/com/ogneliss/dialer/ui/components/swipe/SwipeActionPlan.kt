package com.ogneliss.dialer.ui.components.swipe

import com.ogneliss.dialer.settings.SwipeAction

/**
 * ПЛАНИРОВЩИК ПОВЕДЕНИЯ СВАЙПА.
 *
 * Назначение: решает, что должно произойти при свайпе по карточке журнала.
 * Чистая логика без Compose и Android — покрыта тестами.
 *
 * ── Откуда взялись три режима ──────────────────────────────────────────────
 * Хотелось показывать в свайпе несколько кнопок с последними цифрами номера.
 * Ограничение физическое: цель шириной 76 dp под движущимся пальцем ещё
 * попадаема, три-четыре подряд — уже нет, а на ходу и подавно. Плюс каждая
 * кнопка отъедает ширину: четыре кнопки закрывают почти весь экран,
 * и становится непонятно, по какой строке вообще идёт свайп.
 *
 * Отсюда компромисс:
 *
 * DIRECT — один номер (или действие не зависит от номера, например
 *   «удалить из журнала»). Свайп сразу выполняет действие. Быстро,
 *   привычно, и это подавляющее большинство случаев.
 *
 * REVEAL — два или три номера. Свайп открывает панель с кнопками,
 *   помеченными окончанием номера. Панель фиксируется открытой,
 *   нажатие спокойное.
 *
 * SHEET — четыре и больше. Панель не поместится: открываем шторку,
 *   где номера видны целиком с подписями из книги.
 */
object SwipeActionPlan {

    /** Максимум кнопок в раскрывающейся панели. */
    const val MAX_REVEAL_ACTIONS = 3

    enum class Behaviour {
        /** Выполнить действие немедленно. */
        DIRECT,
        /** Раскрыть панель с выбором. */
        REVEAL,
        /** Открыть шторку выбора номера. */
        SHEET,
        /** Свайп в эту сторону отключён. */
        DISABLED
    }

    /**
     * @param action настроенное пользователем действие для этой стороны
     * @param numberCount сколько номеров у абонента
     * @param simCount сколько активных SIM-карт
     */
    fun decide(action: SwipeAction, numberCount: Int, simCount: Int): Behaviour {
        if (action == SwipeAction.NONE) return Behaviour.DISABLED

        // Действия, не зависящие от конкретного номера, всегда прямые:
        // удалять запись или открывать карточку по номерам незачем
        if (!action.dependsOnNumber()) return Behaviour.DIRECT

        val targets = targetCount(action, numberCount, simCount)
        return when {
            targets <= 1 -> Behaviour.DIRECT
            targets <= MAX_REVEAL_ACTIONS -> Behaviour.REVEAL
            else -> Behaviour.SHEET
        }
    }

    /**
     * Сколько разных целей породит действие.
     *
     * Для вызова при двух SIM каждый номер даёт ДВЕ цели — позвонить
     * с первой и со второй карты. Поэтому у контакта с двумя номерами
     * и двумя SIM получается четыре кнопки, и это уже случай шторки.
     */
    fun targetCount(action: SwipeAction, numberCount: Int, simCount: Int): Int {
        if (!action.dependsOnNumber()) return 1
        val numbers = numberCount.coerceAtLeast(1)
        return if (action == SwipeAction.CALL && simCount > 1) numbers * simCount
        else numbers
    }

    /** Зависит ли действие от того, какой именно номер выбран. */
    private fun SwipeAction.dependsOnNumber(): Boolean = when (this) {
        SwipeAction.CALL,
        SwipeAction.SMS,
        SwipeAction.WHATSAPP,
        SwipeAction.TELEGRAM,
        SwipeAction.VIBER, SwipeAction.MAX,
        SwipeAction.EMAIL,
        SwipeAction.EDIT_BEFORE_CALL,
        SwipeAction.COPY_NUMBER,
        SwipeAction.SHARE_NUMBER -> true

        // Эти работают с записью журнала или контактом целиком
        SwipeAction.DELETE_FROM_LOG,
        SwipeAction.BLOCK_NUMBER,
        SwipeAction.VIEW_CONTACT,
        SwipeAction.REMIND_CALLBACK,
        SwipeAction.ADD_NOTE,
        SwipeAction.NONE -> false
    }
}
