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
            ACTION_ANSWER ->
                if (CallManager.waiting != null) CallManager.answerWaitingHoldCurrent()
                else CallManager.answer()
            ACTION_REJECT ->
                if (CallManager.waiting != null) CallManager.rejectWaiting()
                else CallManager.reject()
            ACTION_HANGUP -> CallManager.hangup()
        }
    }
}
