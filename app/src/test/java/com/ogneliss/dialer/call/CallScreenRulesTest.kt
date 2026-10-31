package com.ogneliss.dialer.call

import android.telecom.Call
import android.telecom.CallAudioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Правила экрана вызова (1.8.1): разбор в CallScreenRules. */
class CallScreenRulesTest {

    private val portrait = CallScreenRules.DTMF_RESERVED_PORTRAIT_DP
    private val landscape = CallScreenRules.DTMF_RESERVED_LANDSCAPE_DP

    @Test
    fun `клавиши обычной высоты, когда места хватает`() {
        // Телефон 412 × 915 dp: окно без панелей ~850 dp
        assertEquals(60, CallScreenRules.dtmfKeyHeightDp(850, portrait))
    }

    @Test
    fun `на телефоне 16 на 9 клавиши ниже`() {
        // 360 × 640 dp с тремя кнопками навигации: окно ~568 dp
        assertEquals(56, CallScreenRules.dtmfKeyHeightDp(568, portrait))
    }

    @Test
    fun `клавиши не мельче удобной для пальца высоты`() {
        // Маленькое окно — разделённый экран: лучше прокрутка, чем промах
        assertEquals(48, CallScreenRules.dtmfKeyHeightDp(420, portrait))
        // Лёжа на телефоне: окно ~340 dp по высоте
        assertEquals(48, CallScreenRules.dtmfKeyHeightDp(340, landscape))
        // Лёжа на высоком планшете — обычные
        assertEquals(60, CallScreenRules.dtmfKeyHeightDp(600, landscape))
    }

    @Test
    fun `датчик у уха — в разговоре и при наборе`() {
        assertTrue(CallScreenRules.proximityWanted(Call.STATE_ACTIVE, CallAudioState.ROUTE_EARPIECE))
        assertTrue(CallScreenRules.proximityWanted(Call.STATE_DIALING, CallAudioState.ROUTE_EARPIECE))
        assertTrue(CallScreenRules.proximityWanted(Call.STATE_HOLDING, CallAudioState.ROUTE_WIRED_HEADSET))
        // Маршрут ещё неизвестен — как у уха: так безопаснее для щеки
        assertTrue(CallScreenRules.proximityWanted(Call.STATE_CONNECTING, null))
    }

    @Test
    fun `пока звонит и на громкой связи — экран не гасится`() {
        // Отзыв: «при звонке не загорается экран» — датчик гасил звонящий телефон
        assertFalse(CallScreenRules.proximityWanted(Call.STATE_RINGING, CallAudioState.ROUTE_EARPIECE))
        assertFalse(CallScreenRules.proximityWanted(Call.STATE_ACTIVE, CallAudioState.ROUTE_SPEAKER))
        assertFalse(CallScreenRules.proximityWanted(Call.STATE_ACTIVE, CallAudioState.ROUTE_BLUETOOTH))
        assertFalse(CallScreenRules.proximityWanted(Call.STATE_DISCONNECTED, CallAudioState.ROUTE_EARPIECE))
        // Вызова нет — между попытками автодозвона
        assertFalse(CallScreenRules.proximityWanted(null, CallAudioState.ROUTE_EARPIECE))
    }
}
