package com.ogneliss.dialer.ui.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.ui.theme.OgnelissIcons

/**
 * ЗНАЧКИ НАСТРОЕК (2.2.2) — как в настройках Ogneliss Mail.
 *
 * Автор, 12.11.2026: «посмотри UI… настройки» (за образец взята почта).
 * В почте у каждой строки настроек слева значок цвета акцента
 * (`SettingsItem`, `SettingsSwitchItem`): строку находят глазом по значку
 * раньше, чем читают заголовок. В диалере значки были только у разделов
 * на главном экране настроек, а строки внутри — голым текстом.
 *
 * Значки — отдельной таблицей, а не полем реестра: реестр — чистые данные
 * без Compose (его читают поиск и тесты), а значок — векторная картинка.
 * Тест SettingIconsTest следит, чтобы значок был у каждой настройки.
 */
internal fun settingIcon(id: String): ImageVector? = when (id) {
    // Оформление
    "language" -> OgnelissIcons.Forum
    "theme" -> OgnelissIcons.DarkMode
    "theme_preset" -> OgnelissIcons.Palette
    "dynamic_color" -> OgnelissIcons.Image
    "font_scale" -> OgnelissIcons.EditNote
    "ui_scale" -> OgnelissIcons.Smartphone
    "ui_font" -> OgnelissIcons.Article
    "corner_size" -> OgnelissIcons.RadioButtonUnchecked
    "list_density" -> OgnelissIcons.List
    "large_buttons" -> OgnelissIcons.TouchApp
    "button_labels" -> OgnelissIcons.Edit
    "contact_name_format" -> OgnelissIcons.Person
    "contact_sort" -> OgnelissIcons.FilterList
    "show_photo" -> OgnelissIcons.Image
    // SIM
    "sim_labels" -> OgnelissIcons.SimCard
    "remember_sim" -> OgnelissIcons.CheckCircle
    "swipe_sim" -> OgnelissIcons.Swipe
    "own_number" -> OgnelissIcons.Person
    "sys_sim" -> OgnelissIcons.OpenInNew
    // Жесты
    "swipe_right" -> OgnelissIcons.Swipe
    "swipe_left" -> OgnelissIcons.SwipeLeft
    "swipe_tuning" -> OgnelissIcons.Settings
    "panel_swipe_nav" -> OgnelissIcons.SwipeUp
    "haptic" -> OgnelissIcons.Smartphone
    "key_tones" -> OgnelissIcons.MusicNote
    // Журнал
    "grouping" -> OgnelissIcons.MergeType
    "merge_calls" -> OgnelissIcons.CallSplit
    "carrier_info" -> OgnelissIcons.Info
    "dialpad_open_at_start" -> OgnelissIcons.Dialpad
    "sort_order" -> OgnelissIcons.Search
    "stats" -> OgnelissIcons.BarChart
    "notes_retention" -> OgnelissIcons.StickyNote2
    "notes_retention_scope" -> OgnelissIcons.Rule
    // SMS
    "sms_default_app" -> OgnelissIcons.Textsms
    "sms_delivery_reports" -> OgnelissIcons.MarkChatRead
    "sms_undo_send" -> OgnelissIcons.Restore
    "sms_calls_in_thread" -> OgnelissIcons.Call
    "sms_otp_cleanup" -> OgnelissIcons.DeleteSweep
    "sms_folders" -> OgnelissIcons.Forum
    "sms_backup" -> OgnelissIcons.Save
    "sms_restore" -> OgnelissIcons.FileUpload
    "sms_swipe_right" -> OgnelissIcons.Swipe
    "sms_swipe_left" -> OgnelissIcons.SwipeLeft
    "messengers_any_number" -> OgnelissIcons.Chat
    // Быстрый доступ
    "fake_call_button", "fake_call_number" -> OgnelissIcons.PhoneCallback
    "pinned" -> OgnelissIcons.PushPin
    "speed_dial" -> OgnelissIcons.Bolt
    "widget_shape" -> OgnelissIcons.AddToHomeScreen
    // Вызовы
    "incoming_banner" -> OgnelissIcons.Notifications
    "answer_mode" -> OgnelissIcons.TouchApp
    "swap_answer" -> OgnelissIcons.SwapCalls
    "smart_speaker" -> OgnelissIcons.VolumeUp
    "proximity_screen_off" -> OgnelissIcons.PhonelinkLock
    "call_hide_contact_number" -> OgnelissIcons.Lock
    "call_photo_style" -> OgnelissIcons.Image
    "call_photo_size" -> OgnelissIcons.Person
    "call_animation" -> OgnelissIcons.Bolt
    "post_call" -> OgnelissIcons.Checklist
    "post_call_unknown" -> OgnelissIcons.PersonOff
    "callback_suggestion" -> OgnelissIcons.PhoneCallback
    "auto_redial" -> OgnelissIcons.RestartAlt
    "auto_redial_pace" -> OgnelissIcons.Schedule
    // Система
    "sys_dnd" -> OgnelissIcons.NotificationsOff
    "sys_sound" -> OgnelissIcons.MusicNote
    "sys_notifications" -> OgnelissIcons.Notifications
    "sys_default_apps" -> OgnelissIcons.PhoneAndroid
    "battery" -> OgnelissIcons.BatteryAlert
    "incoming_screen" -> OgnelissIcons.Smartphone
    // Защита
    "spam_overview" -> OgnelissIcons.Shield
    "block_tagged" -> OgnelissIcons.Report
    "block_hidden" -> OgnelissIcons.PhoneDisabled
    "block_unknown" -> OgnelissIcons.PersonOff
    "quiet_hours" -> OgnelissIcons.Schedule
    "allow_list" -> OgnelissIcons.CheckCircle
    "block_list" -> OgnelissIcons.Block
    "sys_blocked" -> OgnelissIcons.OpenInNew
    "external_lookup" -> OgnelissIcons.Search
    "scam_memo" -> OgnelissIcons.PrivacyTip
    // Обслуживание
    "backup_export" -> OgnelissIcons.Save
    "backup_import" -> OgnelissIcons.Restore
    "calllog_export" -> OgnelissIcons.FileDownload
    "file_logging" -> OgnelissIcons.Code
    "log_save" -> OgnelissIcons.FileDownload
    "log_share" -> OgnelissIcons.Share
    "log_clear" -> OgnelissIcons.Delete
    "reset" -> OgnelissIcons.RestartAlt
    // О приложении
    "privacy" -> OgnelissIcons.PrivacyTip
    "terms" -> OgnelissIcons.Gavel
    "license" -> OgnelissIcons.Article
    "source_code" -> OgnelissIcons.Code
    "licenses" -> OgnelissIcons.List
    "rate_app" -> OgnelissIcons.Star
    "version" -> OgnelissIcons.Info
    else -> null
}
