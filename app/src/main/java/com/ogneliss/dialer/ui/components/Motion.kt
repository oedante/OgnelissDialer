package com.ogneliss.dialer.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * ПЕРЕХОДЫ МЕЖДУ ЭКРАНАМИ.
 *
 * ── Зачем вообще анимация ──────────────────────────────────────────────────
 * Мгновенная подмена содержимого не даёт понять, куда ты попал: вглубь
 * или назад. Пользователь теряет ощущение структуры и начинает нажимать
 * «назад» наугад. Движение отвечает на этот вопрос без слов.
 *
 * ── Почему именно такие длительности ───────────────────────────────────────
 * 250 мс на вход, 200 мс на выход. Уход быстрее прихода: ожидание нового
 * экрана раздражает сильнее, чем мелькание старого.
 *
 * Дольше 300 мс анимация начинает восприниматься как медлительность
 * приложения — а «долго открывается» это главная жалоба в отзывах
 * на диалеры. Здесь скорость важнее плавности.
 *
 * ── Совместимость с predictive back ────────────────────────────────────────
 * На Android 14+ система показывает предпросмотр возврата, пока палец
 * ещё на экране. Наши переходы построены на смещении и прозрачности,
 * которые система умеет анимировать в обратную сторону.
 * Экзотические эффекты сломали бы этот предпросмотр.
 */
object Motion {

    private const val ENTER_MS = 250
    private const val EXIT_MS = 200

    /**
     * ДЛИТЕЛЬНОСТИ ДЛЯ ОСТАЛЬНЫХ АНИМАЦИЙ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЗАЧЕМ ВЫНЕСЕНЫ СЮДА
     * ══════════════════════════════════════════════════════════════════════
     * Переходы между экранами задавались здесь, а всё остальное —
     * числами по месту: 220 у пустого состояния, 300 у плашки вызова,
     * 750 у мерцания заглушки. Числа подбирались в разное время
     * и разными руками; на глаз разница в 80 миллисекунд не заметна
     * поштучно, но приложение из-за неё ощущается несобранным.
     *
     * ── Три длительности на всё ────────────────────────────────────────────
     * QUICK   — появление и исчезновение мелочей: подсказка, пометка,
     *           пустое состояние. Быстрее 150 мс глаз воспринимает
     *           как рывок, медленнее 250 — как задержку.
     * NORMAL  — смена состояния на месте: цвет плашки вызова,
     *           раскрытие панели.
     * SLOW    — только для того, что повторяется бесконечно: мерцание
     *           заглушки списка. Там медленный такт нужен, чтобы
     *           движение не отвлекало от чтения.
     */
    const val QUICK_MS = 200

    const val NORMAL_MS = 300

    const val SLOW_MS = 750

    /** Переход вглубь: новый экран приходит справа. */
    val enterForward: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(tween(ENTER_MS)) { fullWidth -> fullWidth / 4 } +
            fadeIn(tween(ENTER_MS))
    }

    val exitForward: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        // Предыдущий экран отходит НЕ на всю ширину, а на четверть:
        // так видно, что он остался позади, а не исчез
        slideOutHorizontally(tween(EXIT_MS)) { fullWidth -> -fullWidth / 4 } +
            fadeOut(tween(EXIT_MS))
    }

    /** Возврат: движение зеркальное. */
    val enterBackward: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(tween(ENTER_MS)) { fullWidth -> -fullWidth / 4 } +
            fadeIn(tween(ENTER_MS))
    }

    val exitBackward: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutHorizontally(tween(EXIT_MS)) { fullWidth -> fullWidth / 4 } +
            fadeOut(tween(EXIT_MS))
    }

    /**
     * Переключение соседних списков — журнал ↔ контакты (1.6.0, обзор
     * интерфейса, П1). Без сдвига: один растворяется, другой проявляется.
     * Сдвиг говорил бы «зашли внутрь», а это соседи — как вкладки.
     */
    val enterSibling: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        fadeIn(tween(ENTER_MS))
    }

    val exitSibling: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        fadeOut(tween(EXIT_MS))
    }
}
