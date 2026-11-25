package com.ogneliss.dialer.util

import java.util.Locale

/**
 * ДЛИТЕЛЬНОСТЬ РАЗГОВОРА КОРОТКО (2.9.8): «2:35», с часа — «1:02:10».
 *
 * Для строки журнала (настройка «Длительность разговора», форум, Bublikov34).
 * null — разговора не было (пропущенный, отклонённый, не дозвонились):
 * «0:00» рядом с датой читался бы как ошибка.
 *
 * Locale.ROOT — это часы и минуты, а не текст: в арабской и некоторых
 * индийских локалях %d рисуется местными цифрами (как таймер экрана
 * вызова, CallScreenRules.timer).
 */
object CallDurationText {

    fun short(seconds: Long): String? {
        if (seconds <= 0) return null
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
