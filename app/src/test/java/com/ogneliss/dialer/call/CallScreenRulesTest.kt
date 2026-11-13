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

    @Test
    fun `плашка входящего — только где она всплывёт`() {
        val high = android.app.NotificationManager.IMPORTANCE_HIGH
        assertTrue(CallScreenRules.incomingBannerAllowed(false, true, high, true, true))
        // MIUI, vivo, ColorOS — свои запреты: экран вызова целиком
        assertFalse(CallScreenRules.incomingBannerAllowed(true, true, high, true, true))
        // Уведомления выключены, канал приглушён, «Не беспокоить», всплытие отключено
        assertFalse(CallScreenRules.incomingBannerAllowed(false, false, high, true, true))
        assertFalse(CallScreenRules.incomingBannerAllowed(false, true, android.app.NotificationManager.IMPORTANCE_DEFAULT, true, true))
        assertFalse(CallScreenRules.incomingBannerAllowed(false, true, high, false, true))
        assertFalse(CallScreenRules.incomingBannerAllowed(false, true, high, true, false))
        // Полноэкранные отняты (Android 14+): плашка не держится — экран вызова целиком
        assertFalse(CallScreenRules.incomingBannerAllowed(false, true, high, true, true, fullScreenAllowed = false))
    }

    @Test
    fun `на Android 8–9 выбор SIM — исходящий`() {
        assertTrue(CallScreenRules.outgoingOnAdd(Call.STATE_DIALING))
        assertTrue(CallScreenRules.outgoingOnAdd(Call.STATE_CONNECTING))
        assertTrue(CallScreenRules.outgoingOnAdd(Call.STATE_SELECT_PHONE_ACCOUNT))
        assertFalse(CallScreenRules.outgoingOnAdd(Call.STATE_RINGING))
    }

    @Test
    fun `к вызову можно вернуться, пока он не завершён`() {
        assertTrue(CallScreenRules.returnable(Call.STATE_ACTIVE, false))
        assertTrue(CallScreenRules.returnable(Call.STATE_HOLDING, false))
        assertTrue(CallScreenRules.returnable(Call.STATE_DIALING, false))
        assertTrue(CallScreenRules.returnable(Call.STATE_RINGING, false))
        assertFalse(CallScreenRules.returnable(Call.STATE_DISCONNECTED, false))
        assertFalse(CallScreenRules.returnable(Call.STATE_DISCONNECTING, false))
        // Участник конференции — к ней ведёт сама конференция
        assertFalse(CallScreenRules.returnable(Call.STATE_ACTIVE, true))
    }

    // ── Таймер разговора (2.4.3) ───────────────────────────────────────────

    @Test
    fun `таймер — минуты и секунды, с часа — часы`() {
        assertEquals("00:00", CallScreenRules.timer(0))
        assertEquals("05:07", CallScreenRules.timer(307))
        assertEquals("59:59", CallScreenRules.timer(3599))
        assertEquals("1:00:00", CallScreenRules.timer(3600))
        assertEquals("1:05:07", CallScreenRules.timer(3907))
        assertEquals("00:00", CallScreenRules.timer(-5))
    }

    @Test
    fun `нет времени соединения от сети — считать с момента, когда увидели разговор`() {
        assertEquals(1_000L, CallScreenRules.connectedSince(1_000L, 5_000L))
        // 0 — не «1970 год», а «не сообщили»
        assertEquals(5_000L, CallScreenRules.connectedSince(0L, 5_000L))
        assertEquals(5_000L, CallScreenRules.connectedSince(null, 5_000L))
    }
}
