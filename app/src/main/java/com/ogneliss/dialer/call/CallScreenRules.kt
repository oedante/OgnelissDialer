package com.ogneliss.dialer.call

import android.telecom.Call
import android.telecom.CallAudioState

/**
 * ПРАВИЛА ЭКРАНА ВЫЗОВА, КОТОРЫЕ ПРОВЕРЯЮТСЯ БЕЗ ТЕЛЕФОНА (1.8.1).
 *
 * Чистые функции: состояние и размеры на входе, решение на выходе.
 * Проверяет CallScreenRulesTest.
 */
internal object CallScreenRules {

    /*
     * ВЫСОТА КЛАВИШ ТОНОВОГО НАБОРА ПО МЕСТУ.
     *
     * Панель клавиш со своей строкой и полями — около 360 dp. На телефоне
     * 16 : 9 вместе с кнопками разговора она не помещалась, и «Завершить»
     * уезжала за край. Теперь при открытых клавишах кнопки разговора убраны
     * (как в системном диалере), а клавиши ниже, если места мало.
     *
     * Четыре ряда, у каждой клавиши ещё по 3 dp полей сверху и снизу. Сколько
     * места отдать остальному: стоя — имя и состояние (~110 dp), отступ панели,
     * строка набранного и поля панели клавиш (~100 dp), главная кнопка
     * с отступом (~96 dp); лёжа — поля экрана и строка набранного.
     */
    const val DTMF_RESERVED_PORTRAIT_DP = 320
    const val DTMF_RESERVED_LANDSCAPE_DP = 130

    /** Обычная высота клавиши — как на главном экране (Dimens.dialKeyHeight). */
    const val DTMF_KEY_PREFERRED_DP = 60

    /** Ниже — промахи пальцем (Dimens.MIN_TOUCH_TARGET). */
    const val DTMF_KEY_MIN_DP = 48

    private const val DTMF_KEY_PADDING_DP = 6

    /** Высота клавиши, dp: не выше обычной и не ниже удобной для пальца. */
    fun dtmfKeyHeightDp(windowHeightDp: Int, reservedDp: Int): Int =
        ((windowHeightDp - reservedDp) / 4 - DTMF_KEY_PADDING_DP)
            .coerceIn(DTMF_KEY_MIN_DP, DTMF_KEY_PREFERRED_DP)

    /**
     * ДАТЧИК ПРИБЛИЖЕНИЯ — ТОЛЬКО У УХА (разбор в ProximityLock).
     *
     * Набор или разговор, и звук в разговорном динамике или проводной
     * гарнитуре. Звонит, громкая связь, Bluetooth — экран не гасится:
     * так в системном диалере.
     *
     * @param state состояние главного вызова (Call.STATE_*), null — вызова нет
     * @param route маршрут звука (CallAudioState.ROUTE_*), null — ещё неизвестен
     */
    @Suppress("DEPRECATION")
    fun proximityWanted(state: Int?, route: Int?): Boolean {
        val atEar = route == null ||
            route == CallAudioState.ROUTE_EARPIECE ||
            route == CallAudioState.ROUTE_WIRED_HEADSET
        val offHook = state == Call.STATE_DIALING ||
            state == Call.STATE_CONNECTING ||
            state == Call.STATE_ACTIVE ||
            state == Call.STATE_HOLDING ||
            state == Call.STATE_PULLING_CALL
        return offHook && atEar
    }

    /**
     * ПЛАШКА ВХОДЯЩЕГО — ТОЛЬКО ГДЕ ОНА НАВЕРНЯКА ВСПЛЫВЁТ (2.1.0, разбор
     * в IncomingScreen.bannerSafe). Иначе — экран вызова целиком.
     *
     * @param restrictiveShell MIUI/HyperOS, vivo, ColorOS — свои запреты
     *        на всплытие и окна из фона
     * @param channelImportance важность канала входящих (NotificationManager.IMPORTANCE_*)
     * @param interruptionFilterAll «Не беспокоить» выключен
     * @param headsUpOn всплывающие уведомления не отключены в системе
     */
    fun incomingBannerAllowed(
        restrictiveShell: Boolean,
        notificationsEnabled: Boolean,
        channelImportance: Int,
        interruptionFilterAll: Boolean,
        headsUpOn: Boolean
    ): Boolean = !restrictiveShell && notificationsEnabled &&
        channelImportance >= android.app.NotificationManager.IMPORTANCE_HIGH &&
        interruptionFilterAll && headsUpOn

    /**
     * ИСХОДЯЩИЙ ПО СОСТОЯНИЮ — ANDROID 8–9 (2.1.0).
     *
     * До Android 10 направления вызова в Details нет, и в onCallAdded его
     * угадывают по состоянию. Было: только «набирается» и «соединяется», —
     * исходящий с выбором SIM («Спрашивать каждый раз») приходил
     * в SELECT_PHONE_ACCOUNT и считался входящим: пропущенные сбрасывались
     * не тогда, а после сброса выходило «Перезвонить?».
     */
    @Suppress("DEPRECATION")
    fun outgoingOnAdd(state: Int): Boolean =
        state == Call.STATE_DIALING ||
            state == Call.STATE_CONNECTING ||
            state == Call.STATE_SELECT_PHONE_ACCOUNT

    /**
     * Вызов, к которому можно вернуться полосой «Вернуться к вызову» (2.1.0):
     * идёт, удержан, набирается, ждёт выбора SIM или звонит. Участник
     * конференции не в счёт — к ней ведёт сама конференция.
     */
    @Suppress("DEPRECATION")
    fun returnable(state: Int, isConferenceChild: Boolean): Boolean =
        !isConferenceChild && state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING
}
