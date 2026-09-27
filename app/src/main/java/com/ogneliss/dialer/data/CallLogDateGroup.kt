package com.ogneliss.dialer.data

import java.util.Calendar

/**
 * ГРУППИРОВКА ЖУРНАЛА ПО ДАВНОСТИ.
 *
 * Чистая логика без Android — покрыта тестами.
 *
 * ── Зачем ──────────────────────────────────────────────────────────────────
 * Без разделителей журнал читается как сплошная лента: дата у каждой
 * записи есть, но чтобы понять, где кончается сегодняшнее и начинается
 * вчерашнее, приходится вчитываться в каждую строку.
 *
 * Заголовки отвечают на вопрос «когда это было» одним взглядом,
 * не требуя чтения.
 *
 * ── Почему именно такие границы ────────────────────────────────────────────
 * «Сегодня» и «Вчера» — то, чем человек оперирует в разговоре.
 * «На этой неделе» и «На прошлой» — следующий по естественности уровень.
 * Дальше дробить бессмысленно: «три недели назад» и «месяц назад»
 * для памяти одно и то же, поэтому остальное сведено в «Раньше».
 *
 * ── Границы календарные, а не по числу часов ───────────────────────────────
 * «Вчера» — это вчерашний календарный день, а не «от 24 до 48 часов
 * назад». Звонок вчера в 23:50 попадает во «Вчера», даже если сейчас
 * час ночи и прошло меньше двух часов.
 */
enum class CallLogDateGroup {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    LAST_WEEK,
    EARLIER;

    companion object {

        /**
         * Группа для времени вызова.
         *
         * @param firstDayOfWeek первый день недели по настройкам системы:
         *        в России неделя начинается с понедельника, в США —
         *        с воскресенья, и «на этой неделе» означает разное
         */
        fun of(
            callMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
            firstDayOfWeek: Int = Calendar.MONDAY
        ): CallLogDateGroup {
            // Будущее означает сбитые часы устройства или запись
            // из другого часового пояса. Показываем как сегодняшнюю:
            // прятать её в «Раньше» значило бы потерять из виду
            if (callMillis > nowMillis) return TODAY

            val now = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                this.firstDayOfWeek = firstDayOfWeek
            }

            val startOfToday = startOfDay(now)
            if (callMillis >= startOfToday) return TODAY

            val startOfYesterday = startOfToday - DAY_MILLIS
            if (callMillis >= startOfYesterday) return YESTERDAY

            val startOfWeek = startOfWeek(now)
            if (callMillis >= startOfWeek) return THIS_WEEK

            val startOfLastWeek = startOfWeek - WEEK_MILLIS
            if (callMillis >= startOfLastWeek) return LAST_WEEK

            return EARLIER
        }

        private fun startOfDay(calendar: Calendar): Long =
            (calendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        private fun startOfWeek(calendar: Calendar): Long =
            (calendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                // set(DAY_OF_WEEK) может увести ВПЕРЁД, если сегодня
                // воскресенье при неделе с понедельника: тогда отступаем
                // на неделю назад
                if (timeInMillis > calendar.timeInMillis) {
                    timeInMillis -= WEEK_MILLIS
                }
            }.timeInMillis

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val WEEK_MILLIS = 7 * DAY_MILLIS
    }
}
