package com.ogneliss.dialer.sms

import com.ogneliss.dialer.settings.OtpCleanup
import com.ogneliss.dialer.settings.UndoSend
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Умолчания настроек SMS 2.2.0 читаются в двух местах: DataStore (экран
 * настроек) и SmsSettings (приёмник SMS, очередь). Разошлись бы — настройка
 * показывала бы одно, а отправка делала другое.
 */
class UndoSendDefaultsTest {

    @Test
    fun отменаОтправки_поУмолчаниюПятьСекунд() {
        assertEquals(UndoSend.S5, UndoSend.from(null))
        assertEquals(5, UndoSend.from("нет такого").seconds)
        assertEquals(0, UndoSend.OFF.seconds)
    }

    @Test
    fun автоудалениеКодов_поУмолчаниюВыключено() {
        assertEquals(OtpCleanup.OFF, OtpCleanup.from(null))
        assertEquals(0, OtpCleanup.from(null).hours)
        assertEquals(24, OtpCleanup.H24.hours)
    }
}
