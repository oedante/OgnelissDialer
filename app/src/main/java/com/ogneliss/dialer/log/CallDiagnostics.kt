package com.ogneliss.dialer.log

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import com.ogneliss.dialer.call.CallDetailsCompat

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ДИАГНОСТИКА ВЫЗОВОВ В ЖУРНАЛЕ ПРИЛОЖЕНИЯ (1.2.4)
 * ══════════════════════════════════════════════════════════════════════════
 * Повод: «вызов сбросился, когда были подключены наушники — виновато
 * приложение или связь?». В журнале была только строка «Вызов завершён»:
 * ни кто завершил, ни почему, ни куда шёл звук. Ответить по нему было
 * нельзя.
 *
 * Теперь при завершении пишется:
 * • направление и длительность разговора;
 * • причина по версии системы (DisconnectCause): LOCAL — завершили
 *   на этом телефоне (приложение или кнопка гарнитуры), REMOTE — собеседник,
 *   ERROR — сбой, BUSY, REJECTED, MISSED и т. д.; плюс служебная причина
 *   оператора или модема, если она есть — по ней видна плохая связь;
 * • куда шёл звук в момент завершения.
 *
 * Номер, как везде в журнале, скрыт наполовину.
 *
 * Лежит в пакете log, а не call: строки здесь — для журнала разработчика,
 * а не для экрана, и переводу не подлежат.
 */
object CallDiagnostics {

    @Suppress("DEPRECATION")
    fun logEnded(call: Call, audio: CallAudioState?) {
        val details = call.details ?: return
        val outgoing = CallDetailsCompat.isOutgoing(details, call)
        val connect = details.connectTimeMillis
        val talkSec = if (connect > 0) (System.currentTimeMillis() - connect) / 1000 else 0L
        val cause = details.disconnectCause
        val reason = cause?.reason?.takeIf { it.isNotBlank() }?.let { ", служебно: $it" }.orEmpty()
        Logger.i(
            "Call",
            "Завершён: ${if (outgoing) "исходящий" else "входящий"} " +
                "${Logger.mask(details.handle?.schemeSpecificPart)}, " +
                "разговор ${talkSec} с, причина: ${causeName(cause?.code)}$reason, " +
                "звук: ${routeName(audio?.route)}"
        )
    }

    /** Смена вывода звука во время вызова. */
    fun logRoute(state: CallAudioState) {
        Logger.i(
            "Audio",
            "Звук: ${routeName(state.route)} (доступно: ${routeMaskNames(state.supportedRouteMask)})"
        )
    }

    fun causeName(code: Int?): String = when (code) {
        DisconnectCause.LOCAL -> "LOCAL (на этом телефоне)"
        DisconnectCause.REMOTE -> "REMOTE (собеседник)"
        DisconnectCause.ERROR -> "ERROR (сбой)"
        DisconnectCause.BUSY -> "BUSY (занято)"
        DisconnectCause.REJECTED -> "REJECTED (отклонён)"
        DisconnectCause.MISSED -> "MISSED (пропущен)"
        DisconnectCause.CANCELED -> "CANCELED (отменён до ответа)"
        DisconnectCause.RESTRICTED -> "RESTRICTED (запрещён)"
        DisconnectCause.OTHER -> "OTHER (другое)"
        DisconnectCause.CONNECTION_MANAGER_NOT_SUPPORTED -> "CONNECTION_MANAGER_NOT_SUPPORTED"
        DisconnectCause.ANSWERED_ELSEWHERE -> "ANSWERED_ELSEWHERE (отвечен на другом устройстве)"
        DisconnectCause.CALL_PULLED -> "CALL_PULLED"
        DisconnectCause.UNKNOWN -> "UNKNOWN"
        null -> "нет данных"
        else -> "код $code"
    }

    fun routeName(route: Int?): String = when (route) {
        CallAudioState.ROUTE_EARPIECE -> "разговорный динамик"
        CallAudioState.ROUTE_SPEAKER -> "громкая связь"
        CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
        CallAudioState.ROUTE_WIRED_HEADSET -> "проводная гарнитура"
        CallAudioState.ROUTE_STREAMING -> "трансляция"
        null -> "нет данных"
        else -> "код $route"
    }

    fun routeMaskNames(mask: Int): String = listOf(
        CallAudioState.ROUTE_EARPIECE,
        CallAudioState.ROUTE_SPEAKER,
        CallAudioState.ROUTE_BLUETOOTH,
        CallAudioState.ROUTE_WIRED_HEADSET
    ).filter { mask and it != 0 }.joinToString(", ") { routeName(it) }.ifEmpty { "—" }
}
