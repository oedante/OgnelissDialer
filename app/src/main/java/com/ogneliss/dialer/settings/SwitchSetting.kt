package com.ogneliss.dialer.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.ogneliss.dialer.sms.SmsSettings
import com.ogneliss.dialer.util.IncomingCallAccess

/**
 * ПЕРЕКЛЮЧАТЕЛИ НАСТРОЕК — ОДНО ОПИСАНИЕ НА КАЖДЫЙ (2.9.14, техдолг, этап 2).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Внешний аудит 28.11.2026 и свой список техдолга: новая настройка-переключатель
 * требовала правок в восьми местах — ключ, поток и запись в Prefs, поле и
 * switchValue в SettingsState, группа потоков в SettingsStateSource, ветка
 * setSwitch и два списка SWITCH_KEYS (в SettingsCommands и SettingsState).
 * Умолчание жило дважды — в Prefs и в SettingsState — и однажды разошлось:
 * у «Входящий плашкой сверху» в Prefs оно зависит от прошивки, а в SettingsState
 * было просто «вкл.», и на Xiaomi у пункта горела точка «изменено», хотя его не
 * трогали. А у «Журнал в файл» SettingsStateSource вписывал «вкл.» всегда
 * (с 1.5.5): выключенный, он на экране оставался включённым, и включить его
 * обратно с экрана было нельзя — нажатие снова записывало «выкл.».
 *
 * Теперь переключатель — строка здесь: ключ, ключ хранения, умолчание и,
 * если нужно, копия в SmsSettings. Остальное выводится:
 * поток и запись — Prefs.flow/Prefs.set; значение в снимке — SettingsState[...];
 * запись с экрана — SettingsCommands.setSwitch; списки ключей — [entries].
 * Вне этого файла новому переключателю нужны только строка реестра
 * (SettingsRegistry: название, раздел, группа), значок (SettingIcons) и тексты.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПРАВИЛА
 * ══════════════════════════════════════════════════════════════════════════
 * • [storageKey] — ключ в DataStore, у старых настроек он отличается от [key]
 *   (имя пункта на экране и в реестре). Менять нельзя: у людей слетят настройки.
 * • Порядок строк ничего не значит: порядок на экране задаёт реестр.
 * • Побочные действия при переключении с экрана (значки разделов, снятие
 *   уведомлений, журнал в файл) — в SettingsCommands.setSwitch: им нужен не
 *   только Context, и они бывают ДО записи.
 */
enum class SwitchSetting(
    /** Ключ пункта в реестре настроек и на экране. */
    val key: String,
    private val defaultValue: () -> Boolean,
    /** Ключ в DataStore. */
    storageKey: String = key,
    /**
     * Копия значения в SmsSettings (SharedPreferences): приёмник SMS решает сразу,
     * без чтения DataStore (2.0.0, 2.8.6). Пишется при каждой записи и при сбросе.
     */
    val mirror: ((Context, Boolean) -> Unit)? = null
) {
    // ── Оформление ──────────────────────────────────────────────────────────
    DYNAMIC_COLOR("dynamic_color", { false }),
    LARGE_BUTTONS("large_buttons", { false }),
    SHOW_PHOTO("show_photo", { true }),
    /** Подписи под кнопками по всему приложению. */
    BUTTON_LABELS("button_labels", { true }),
    /** Отдельные значки «SMS» и «Контакты» (2.6.0): повторяет то, что хранит Android. */
    SECTION_ICONS("section_icons", { false }),

    // ── Журнал и контакты ───────────────────────────────────────────────────
    /** Цветные стрелки вызовов (2.7.0; 2.8.9 — по умолчанию вкл.). Разбор — CallArrowColor. */
    COLORED_CALL_ARROWS("colored_call_arrows", { true }),
    /** Длительность разговора в строке журнала (2.9.8). */
    CALL_LOG_DURATION("call_log_duration", { false }),
    /*
     * «Группировать вызовы одного номера» (merge_calls) убран в 2.9.15: с 1.5.5
     * он ни на что не влиял — значение только переносилось в снимок настроек,
     * а журнал группирует по выбору «Группировка» (там и «Подряд идущие»).
     * Нашло правило audit.py «переключатель нигде не применяется».
     */
    /** Мессенджеры для любого номера, а не только для того, что в контакте. */
    MESSENGERS_ANY_NUMBER("messengers_any_number", { false }),

    // ── Жесты и отклик ──────────────────────────────────────────────────────
    PANEL_SWIPE_NAV("panel_swipe_nav", { true }),
    FAKE_CALL_BUTTON("fake_call_button", { false }),
    HAPTIC("haptic", { true }),
    KEY_TONES("key_tones", { true }),

    // ── SIM и вызовы ────────────────────────────────────────────────────────
    REMEMBER_SIM("remember_sim", { true }),
    DIALPAD_OPEN_AT_START("dialpad_open_at_start", { false }),
    SMART_SPEAKER("smart_speaker", { false }),
    CALLBACK_SUGGESTION("callback_suggestion", { true }),
    /** «После звонка» — экран с действиями. Ключ хранения — с 1.x. */
    POST_CALL_ENABLED("post_call", { false }, storageKey = "post_call_enabled"),
    /** По умолчанию «только незнакомые»: окно после КАЖДОГО звонка раздражает. */
    POST_CALL_UNKNOWN_ONLY("post_call_unknown", { true }, storageKey = "post_call_unknown_only"),

    // ── Экран вызова ────────────────────────────────────────────────────────
    /** Гасить экран у уха по датчику приближения. */
    PROXIMITY_SCREEN_OFF("proximity_screen_off", { true }),
    /** Тип номера вместо номера у контакта на экране вызова. */
    CALL_HIDE_CONTACT_NUMBER("call_hide_contact_number", { false }),
    /** Подписи «Входящий вызов» и «Соединение…» (2.9.7). */
    CALL_STATUS_LABELS("call_status_labels", { true }),
    /** Живой фон экрана вызова (1.5.1). */
    CALL_ANIMATION("call_animation", { true }),
    /**
     * Входящий плашкой сверху, когда телефоном пользуются (2.1.0). На прошивках,
     * где окно из фона поднимается не всегда, по умолчанию выключено — разбор
     * в IncomingCallAccess (2.2.2).
     */
    INCOMING_BANNER("incoming_banner", { !IncomingCallAccess.restrictiveShell }),
    /**
     * Тип номера («Мобильный», «Рабочий») под именем в уведомлении о входящем
     * (2.9.15). Подпись добавили в 2.1.0 по просьбе пользователя («плашка сверху
     * с фото и „мобильный“»); 28.11.2026 другой попросил её убрать («if it possible
     * to remove the label mobile below the name»). Поэтому выбор, а не удаление:
     * по умолчанию — как было. Читает служба вызовов (копия в CallerCard).
     */
    CALL_NOTIFICATION_NUMBER_TYPE("call_notification_number_type", { true }),
    /** «Ответить» слева (2.1.1). */
    SWAP_ANSWER("swap_answer", { false }),
    /** Вибрация, когда вызываемый снял трубку (2.5.0). */
    VIBRATE_ON_CONNECT("vibrate_on_connect", { true }),
    /** Памятка о мошенниках на экране вызова (1.8.0). */
    SCAM_MEMO("scam_memo", { true }),

    // ── Защита от спама ─────────────────────────────────────────────────────
    BLOCK_HIDDEN("block_hidden", { false }),
    BLOCK_NOT_IN_CONTACTS("block_unknown", { false }, storageKey = "block_not_in_contacts"),
    /** Метку «спам» человек ставил осознанно — значит, ждёт от неё эффекта. */
    BLOCK_TAGGED_SPAM("block_tagged", { true }, storageKey = "block_tagged_spam"),

    // ── Сообщения ───────────────────────────────────────────────────────────
    SMS_DELIVERY_REPORTS(
        "sms_delivery_reports", { false },
        mirror = { c, v -> SmsSettings.put(c, SmsSettings.KEY_DELIVERY_REPORTS, v) }
    ),
    /** Уведомления о новых SMS выключены (2.8.6). */
    SMS_NOTIFICATIONS_OFF(
        "sms_notifications_off", { false },
        mirror = { c, v -> SmsSettings.put(c, SmsSettings.KEY_NOTIFICATIONS_OFF, v) }
    ),
    /** Звонки в ленте переписки (2.2.0). */
    SMS_CALLS_IN_THREAD("sms_calls_in_thread", { true }),

    // ── Обслуживание ────────────────────────────────────────────────────────
    FILE_LOGGING("file_logging", { true });

    /** Ключ DataStore. */
    val prefKey: Preferences.Key<Boolean> = booleanPreferencesKey(storageKey)

    /** Значение, пока человек его не менял. */
    val default: Boolean get() = defaultValue()

    /** Значение из снимка хранилища. */
    fun read(p: Preferences): Boolean = p[prefKey] ?: default

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** Переключатель по ключу реестра; null — такого нет. */
        fun of(key: String): SwitchSetting? = byKey[key]

        /** Ключи всех переключателей — для реестра и тестов. */
        val KEYS: Set<String> = byKey.keys

        /** Все умолчания — начальный снимок до чтения хранилища. */
        fun defaults(): Map<SwitchSetting, Boolean> = entries.associateWith { it.default }

        /** Все значения из снимка хранилища. */
        fun readAll(p: Preferences): Map<SwitchSetting, Boolean> = entries.associateWith { it.read(p) }
    }
}
