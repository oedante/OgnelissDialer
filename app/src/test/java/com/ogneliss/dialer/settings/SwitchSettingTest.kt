package com.ogneliss.dialer.settings

import android.app.Application
import com.ogneliss.dialer.sms.SmsSettings
import com.ogneliss.dialer.ui.SettingsState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ПЕРЕКЛЮЧАТЕЛИ — ОДНО ОПИСАНИЕ (2.9.14, техдолг, этап 2).
 *
 * До 2.9.14 у каждого переключателя были свой ключ, поток и запись в Prefs, поле
 * в SettingsState и строки в двух списках ключей. Теперь всё это — строка
 * SwitchSetting. Здесь проверяется, что при переезде ничего не потерялось:
 * ключи хранения и умолчания — ровно те, что были (таблица ниже списана с 2.9.13),
 * запись, копия для приёмника SMS и сброс работают через описание.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwitchSettingTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs = Prefs(app)

    @After
    fun tearDown() = runBlocking { prefs.resetAll() }

    /*
     * Ключ пункта, ключ хранения, умолчание — как в 2.9.13 (Prefs.Keys, потоки Prefs).
     * Сменить ключ хранения — у людей слетит настройка; поменять умолчание без
     * решения автора — изменить приложение молча. Поэтому таблица — литералами,
     * а не из самого SwitchSetting.
     */
    private val was = listOf(
        Triple("dynamic_color", "dynamic_color", false),
        Triple("large_buttons", "large_buttons", false),
        Triple("show_photo", "show_photo", true),
        Triple("button_labels", "button_labels", true),
        Triple("section_icons", "section_icons", false),
        Triple("colored_call_arrows", "colored_call_arrows", true),
        Triple("call_log_duration", "call_log_duration", false),
        Triple("messengers_any_number", "messengers_any_number", false),
        Triple("panel_swipe_nav", "panel_swipe_nav", true),
        Triple("fake_call_button", "fake_call_button", false),
        Triple("haptic", "haptic", true),
        Triple("key_tones", "key_tones", true),
        Triple("remember_sim", "remember_sim", true),
        Triple("dialpad_open_at_start", "dialpad_open_at_start", false),
        Triple("smart_speaker", "smart_speaker", false),
        Triple("callback_suggestion", "callback_suggestion", true),
        Triple("post_call", "post_call_enabled", false),
        Triple("post_call_unknown", "post_call_unknown_only", true),
        Triple("proximity_screen_off", "proximity_screen_off", true),
        Triple("call_hide_contact_number", "call_hide_contact_number", false),
        Triple("call_status_labels", "call_status_labels", true),
        Triple("call_animation", "call_animation", true),
        Triple("swap_answer", "swap_answer", false),
        Triple("vibrate_on_connect", "vibrate_on_connect", true),
        Triple("scam_memo", "scam_memo", true),
        Triple("block_hidden", "block_hidden", false),
        Triple("block_unknown", "block_not_in_contacts", false),
        Triple("block_tagged", "block_tagged_spam", true),
        Triple("sms_delivery_reports", "sms_delivery_reports", false),
        Triple("sms_notifications_off", "sms_notifications_off", false),
        Triple("sms_calls_in_thread", "sms_calls_in_thread", true),
        Triple("file_logging", "file_logging", true)
    )

    @Test
    fun `ключи хранения и умолчания — как были`() {
        was.forEach { (key, storage, default) ->
            val s = SwitchSetting.of(key) ?: error("нет переключателя $key")
            assertEquals(key, storage, s.prefKey.name)
            assertEquals(key, default, s.default)
        }
        // Плашку входящего — отдельно: умолчание зависит от прошивки (IncomingCallAccess)
        assertEquals("incoming_banner", SwitchSetting.INCOMING_BANNER.prefKey.name)
        // Новые после 2.9.13 — со своими умолчаниями
        val added = mapOf("call_notification_number_type" to true)
        added.forEach { (key, default) -> assertEquals(key, default, SwitchSetting.of(key)?.default) }
        // Убранные после 2.9.13 — ни на что не влиявшие (merge_calls, 2.9.15)
        listOf("merge_calls").forEach { assertEquals(it, null, SwitchSetting.of(it)) }
        assertEquals(was.size + 1 + added.size, SwitchSetting.entries.size)
    }

    @Test
    fun `ключи не повторяются`() {
        assertEquals(SwitchSetting.entries.size, SwitchSetting.entries.map { it.key }.toSet().size)
        assertEquals(SwitchSetting.entries.size, SwitchSetting.entries.map { it.prefKey.name }.toSet().size)
    }

    @Test
    fun `умолчания в снимке — из описаний`() {
        val defaults = SettingsState()
        SwitchSetting.entries.forEach { assertEquals(it.key, it.default, defaults[it]) }
        assertFalse(defaults.switchValue("нет_такого"))
    }

    @Test
    fun `запись читается тем же описанием`() = runBlocking {
        SwitchSetting.entries.forEach { s ->
            prefs.set(s, !s.default)
            assertEquals(s.key, !s.default, prefs.flow(s).first())
            assertEquals(s.key, !s.default, prefs.switches.first()[s])
        }
    }

    @Test
    fun `копия для приёмника SMS пишется и сбрасывается`() = runBlocking {
        prefs.set(SwitchSetting.SMS_DELIVERY_REPORTS, true)
        prefs.set(SwitchSetting.SMS_NOTIFICATIONS_OFF, true)
        assertTrue(SmsSettings.deliveryReports(app))
        assertTrue(SmsSettings.notificationsOff(app))
        prefs.resetAll()
        assertFalse(SmsSettings.deliveryReports(app))
        assertFalse(SmsSettings.notificationsOff(app))
        assertFalse(prefs.flow(SwitchSetting.SMS_NOTIFICATIONS_OFF).first())
    }

    @Test
    fun `сброс не трогает отдельные значки`() = runBlocking {
        // Значки в списке приложений хранит Android — настройка их только повторяет
        prefs.set(SwitchSetting.SECTION_ICONS, true)
        prefs.set(SwitchSetting.HAPTIC, false)
        prefs.resetAll()
        assertTrue(prefs.flow(SwitchSetting.SECTION_ICONS).first())
        assertTrue(prefs.flow(SwitchSetting.HAPTIC).first())
    }

    @Test
    fun `«Журнал в файл» на экране — сохранённое значение`() = runBlocking {
        // До 2.9.14 снимок всегда говорил «вкл.»: выключенный переключатель
        // оставался на экране включённым, и включить его обратно было нельзя
        prefs.set(SwitchSetting.FILE_LOGGING, false)
        val state = SettingsStateSource(prefs).state.first()
        assertFalse(state[SwitchSetting.FILE_LOGGING])
        assertFalse(state.switchValue("file_logging"))
    }
}
