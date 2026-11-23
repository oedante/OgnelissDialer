package com.ogneliss.dialer.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Сервисные коды срабатывают, только когда набраны целиком (2.9.1, ServiceCodes). */
class ServiceCodesTest {

    @Test
    fun `код для приложений набран целиком — срабатывает`() {
        assertTrue(ServiceCodes.isComplete("*#*#4636#*#*"))
        assertTrue(ServiceCodes.isComplete("*#*#6484#*#*"))
    }

    @Test
    fun `IMEI — срабатывает`() {
        assertTrue(ServiceCodes.isComplete("*#06#"))
    }

    @Test
    fun `недонабранный код и обычный номер — нет`() {
        listOf("*#*#4636#*#", "*#*#4636", "*#0", "*#06", "+79001110001", "112", "*100#", "#31#").forEach {
            assertFalse(it, ServiceCodes.isComplete(it))
        }
    }

    @Test
    fun `код с буквами или пустой — нет`() {
        assertFalse(ServiceCodes.isComplete("*#*#abc#*#*"))
        assertFalse(ServiceCodes.isComplete("*#*##*#*"))
        assertFalse(ServiceCodes.isComplete(""))
    }
}
