package com.ogneliss.dialer.call

import android.telecom.Call
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Подписи состояния на экране вызова (2.9.7, письмо пользователя: «скрывать
 * подписи „Входящий“, „Вызов“, оставив только таймер»). Разбор — CallStatusLabels.
 */
class CallStatusLabelsTest {

    private val routine = listOf(Call.STATE_RINGING, Call.STATE_DIALING, Call.STATE_CONNECTING)
    private val meaningful = listOf(
        Call.STATE_ACTIVE, Call.STATE_HOLDING, Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED
    )

    @Test
    fun `настройка включена - видно всё, как было`() {
        (routine + meaningful).forEach { assertTrue("state=$it", CallStatusLabels.shown(it, labelsOn = true)) }
    }

    @Test
    fun `выключена - нет входящего и соединения`() {
        routine.forEach { assertFalse("state=$it", CallStatusLabels.shown(it, labelsOn = false)) }
    }

    @Test
    fun `выключена - таймер, удержание и завершение остаются`() {
        meaningful.forEach { assertTrue("state=$it", CallStatusLabels.shown(it, labelsOn = false)) }
    }
}
