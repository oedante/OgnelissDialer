package com.ogneliss.dialer.call

import android.os.Build
import android.telecom.Call

/**
 * СОВМЕСТИМЫЙ ДОСТУП К ДАННЫМ ВЫЗОВА.
 *
 * ── Зачем нужен ────────────────────────────────────────────────────────────
 * Часть свойств `Call.Details` появилась позже нашего minSdk 26:
 *
 *   getContactDisplayName()  — API 30
 *   getCallDirection()       — API 29
 *
 * Обращение к ним без проверки версии даёт `NoSuchMethodError` на старых
 * устройствах. Причём падение случится в момент входящего вызова —
 * то есть тогда, когда приложение обязано работать безупречно,
 * и воспроизвести это на новом телефоне разработчика невозможно.
 *
 * Lint такие обращения находит и помечает ошибкой, но ошибка выглядит
 * как формальность, и её легко подавить аннотацией. Здесь вместо
 * подавления сделаны честные запасные пути.
 */
object CallDetailsCompat {

    /**
     * Имя контакта.
     *
     * До API 30 системного поля нет. Запасной путь — искать номер
     * в телефонной книге самим. Здесь возвращаем null, а вызывающий код
     * подставит номер: показать номер честнее, чем показать пустоту.
     */
    fun contactDisplayName(details: Call.Details?): String? {
        if (details == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            details.contactDisplayName
        } else {
            null
        }
    }

    /** Номер абонента. Доступен на всех версиях. */
    fun number(details: Call.Details?): String? =
        details?.handle?.schemeSpecificPart

    /**
     * Исходящий ли вызов.
     *
     * До API 29 направление в Details не отдаётся. Определяем косвенно:
     * у входящего вызова состояние проходит через RINGING, у исходящего —
     * через DIALING или CONNECTING. Этого достаточно для наших задач:
     * решить, предлагать ли напоминание после сброса.
     */
    fun isOutgoing(details: Call.Details?, call: Call?): Boolean {
        if (details == null) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return details.callDirection == Call.Details.DIRECTION_OUTGOING
        }

        @Suppress("DEPRECATION")
        return when (call?.state) {
            Call.STATE_DIALING, Call.STATE_CONNECTING -> true
            else -> false
        }
    }

    /**
     * Имя для показа: контакт, если система его знает, иначе номер.
     * Пустоту не возвращаем никогда — на экране вызова она выглядит
     * как сбой приложения.
     */
    fun displayLabel(details: Call.Details?, fallback: String): String =
        contactDisplayName(details) ?: number(details) ?: fallback
}
