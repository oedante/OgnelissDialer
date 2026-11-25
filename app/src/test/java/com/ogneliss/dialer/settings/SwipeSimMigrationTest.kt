package com.ogneliss.dialer.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «SIM для действий свайпа» убрана (2.9.11): сохранённый выбор карты один раз
 * переносится в сами свайпы — «Позвонить» становится «Позвонить с SIM N».
 */
class SwipeSimMigrationTest {

    private val old = SwipeSimMigration.OLD_KEY
    private val right = stringPreferencesKey("swipe_right")
    private val left = stringPreferencesKey("swipe_left")
    private val smsRight = stringPreferencesKey("sms_swipe_right")
    private val smsLeft = stringPreferencesKey("sms_swipe_left")
    private val rightExtra = stringPreferencesKey("swipe_right_extra")
    private val smsLeftExtra = stringPreferencesKey("sms_swipe_left_extra")

    @Test
    fun `без старой настройки переноса нет`() = runTest {
        assertFalse(SwipeSimMigration.shouldMigrate(emptyPreferences()))
        assertFalse(SwipeSimMigration.shouldMigrate(mutablePreferencesOf(right to "call")))
    }

    @Test
    fun `первая SIM - свайп вправо по умолчанию становится звонком с SIM 1`() = runTest {
        // Свайп вправо не менялся — на диске его нет, по умолчанию «Позвонить»
        val before = mutablePreferencesOf(old to "FIRST")
        assertTrue(SwipeSimMigration.shouldMigrate(before))
        val after = SwipeSimMigration.migrate(before)
        assertEquals("call_sim1", after[right])
        // Влево по умолчанию «SMS» — не трогаем и не записываем
        assertNull(after[left])
        assertNull(after[old])
    }

    @Test
    fun `вторая SIM - все звонящие свайпы и кнопки панели`() = runTest {
        val before = mutablePreferencesOf(
            old to "SECOND",
            right to "sms",
            left to "call",
            smsRight to "call",
            smsLeft to "delete_log",
            rightExtra to "whatsapp,call,telegram",
            smsLeftExtra to "call,call_sim2"
        )
        val after = SwipeSimMigration.migrate(before)
        assertEquals("sms", after[right])
        assertEquals("call_sim2", after[left])
        assertEquals("call_sim2", after[smsRight])
        assertEquals("delete_log", after[smsLeft])
        assertEquals("whatsapp,call_sim2,telegram", after[rightExtra])
        // Уже было «с SIM 2» — второй раз не добавляется
        assertEquals("call_sim2", after[smsLeftExtra])
        assertNull(after[old])
    }

    @Test
    fun `спрашивать - свайпы как были, старый ключ удалён`() = runTest {
        val before = mutablePreferencesOf(old to "ASK", left to "call")
        val after = SwipeSimMigration.migrate(before)
        assertEquals("call", after[left])
        assertNull(after[right])
        assertNull(after[old])
        // Повторно не выполняется
        assertFalse(SwipeSimMigration.shouldMigrate(after))
    }

    @Test
    fun `неизвестное значение - как спрашивать`() = runTest {
        val after = SwipeSimMigration.migrate(mutablePreferencesOf(old to "THIRD", right to "call"))
        assertEquals("call", after[right])
        assertNull(after[old])
    }

    @Test
    fun `трубка в строке берёт карту свайпа`() {
        val call = SwipeAction.CALL
        val sim1 = SwipeAction.CALL_SIM1
        val sim2 = SwipeAction.CALL_SIM2
        val sms = SwipeAction.SMS
        // Звонящий свайп с одной карты — трубка с неё же
        assertEquals(sim1, SwipeAction.handsetCall(sim1, sms))
        assertEquals(sim2, SwipeAction.handsetCall(SwipeAction.NONE, sim2))
        assertEquals(sim1, SwipeAction.handsetCall(sim1, sim1))
        // «Позвонить» среди свайпов или свайпы на разные карты — спросить, как «Позвонить»
        assertEquals(call, SwipeAction.handsetCall(call, sms))
        assertEquals(call, SwipeAction.handsetCall(sim1, sim2))
        assertEquals(call, SwipeAction.handsetCall(call, sim2))
        // Ни один свайп не звонит — обычный звонок
        assertEquals(call, SwipeAction.handsetCall(sms, SwipeAction.DELETE_FROM_LOG))
    }
}
