package com.ogneliss.dialer.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Кнопки на уведомлении вызова работают, даже когда UI не запущен. */
class CallControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER = "com.ogneliss.dialer.ANSWER"
        const val ACTION_REJECT = "com.ogneliss.dialer.REJECT"
        const val ACTION_HANGUP = "com.ogneliss.dialer.HANGUP"

        /** Переключатели в уведомлении разговора (2.1.0): громкая связь и микрофон. */
        const val ACTION_SPEAKER = "com.ogneliss.dialer.SPEAKER"
        const val ACTION_MUTE = "com.ogneliss.dialer.MUTE"

        /** Уведомление о пропущенных смахнули (2.1.0): сбросить счётчик на значке. */
        const val ACTION_CLEAR_MISSED = "com.ogneliss.dialer.CLEAR_MISSED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            /*
             * Ответ и отклонение из уведомления — сначала второй входящий.
             *
             * Уведомление о входящем показывается и тогда, когда разговор
             * уже идёт, а человек вышел из экрана вызова. «Ответить» при
             * этом должно отвечать на ЗВОНЯЩИЙ вызов. Раньше оно отвечало
             * на текущий, то есть на уже идущий разговор, — и ничего
             * не происходило, а второй звонок продолжал звонить.
             */
            // 2.4.3: текущий нельзя удержать — завершить и ответить (CallLines.waitingAnswer)
            ACTION_ANSWER ->
                if (CallManager.waiting != null) CallManager.answerWaiting()
                else CallManager.answer()
            ACTION_REJECT ->
                if (CallManager.waiting != null) CallManager.rejectWaiting()
                else CallManager.reject()
            ACTION_HANGUP -> CallManager.hangup()
            // «Выключить динамик» — гарнитура, если вставлена, иначе трубка (2.1.0)
            ACTION_SPEAKER -> CallManager.setAudioRoute(
                if (CallManager.isSpeakerOn) android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE
                else android.telecom.CallAudioState.ROUTE_SPEAKER
            )
            ACTION_MUTE -> CallManager.setMuted(!CallManager.isMuted)
            ACTION_CLEAR_MISSED -> MissedCallsNotifier.clear(context)
        }
    }
}
