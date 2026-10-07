package com.ogneliss.dialer.ui.settings

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.R

/**
 * РЕЕСТР НАСТРОЕК.
 *
 * Назначение: единое ДЕКЛАРАТИВНОЕ описание всех настроек приложения.
 * Из него автоматически строятся: главный экран настроек, экраны категорий
 * и поиск по настройкам.
 *
 * ── Зачем реестр вместо обычной вёрстки ────────────────────────────────────
 * Раньше все настройки лежали одним плоским списком из 20+ пунктов —
 * ровно то, на что жалуются в отзывах на конкурентов: «слишком много,
 * ничего не найти». Разложить их по экранам вручную означало бы дублировать
 * каждый пункт трижды: в категории, в поиске и в описании.
 *
 * Здесь каждая настройка описана ОДИН раз. Добавление новой настройки —
 * это одна запись в этом файле; она сама появится и в нужной категории,
 * и в результатах поиска.
 *
 * ── Правило семи ───────────────────────────────────────────────────────────
 * На верхнем уровне не больше семи категорий. Это не эстетика: список
 * длиннее семи пунктов человек перестаёт охватывать взглядом и начинает
 * читать построчно, что резко замедляет поиск нужного.
 */

/** Идентификаторы категорий. Используются в маршрутах навигации. */
enum class SettingsCategory(
    val route: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val icon: ImageVector
) {
    APPEARANCE(
        "settings/appearance",
        R.string.settings_section_appearance,
        R.string.settings_section_appearance_summary,
        OgnelissIcons.Palette
    ),
    GESTURES(
        "settings/gestures",
        R.string.settings_section_gestures,
        R.string.settings_section_gestures_summary,
        OgnelissIcons.Swipe
    ),
    /*
     * РАЗДЕЛ «ВЫЗОВЫ» РАЗДЕЛЁН НА ЧЕТЫРЕ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЧТО БЫЛО НЕ ТАК
     * ══════════════════════════════════════════════════════════════════════
     * В «Вызовах» лежало двадцать пять пунктов из семи разных областей:
     * поведение журнала, SIM-карты, срок хранения заметок, закреплённые
     * номера, статистика, псевдозвонок и пять ссылок в системные
     * настройки.
     *
     * Найти что-то в таком списке можно только прокруткой сверху донизу.
     * Хуже того, соседство сбивало с толку: «Хранить заметки» между
     * «Умной громкой связью» и «Экраном после звонка» читается как
     * настройка звонков, а не заметок.
     *
     * ── Как разделено ──────────────────────────────────────────────────────
     * По вопросу, на который отвечает настройка:
     *
     *   ВЫЗОВЫ      — что происходит во время и после звонка
     *   ЖУРНАЛ      — как выглядит и сортируется список
     *   SIM         — всё про карты и свой номер
     *   БЫСТРЫЙ ДОСТУП — закреплённые, быстрый набор, псевдозвонок
     *   СИСТЕМА     — ссылки в настройки Android; они не наши, и мешать
     *                 их со своими значит обещать управление тем,
     *                 чем мы не управляем
     *
     * Заметки переехали в «Журнал»: срок хранения относится к тому,
     * что человек записал, а записывает он в журнале и карточке.
     */
    CALLS(
        "settings/calls",
        R.string.settings_section_calls,
        R.string.settings_section_calls_summary,
        OgnelissIcons.Call
    ),
    CALL_LOG(
        "settings/calllog",
        R.string.settings_section_log,
        R.string.settings_section_log_summary,
        OgnelissIcons.List
    ),
    SIM(
        "settings/sim",
        R.string.settings_section_sim,
        R.string.settings_section_sim_summary,
        OgnelissIcons.SimCard
    ),
    QUICK_ACCESS(
        "settings/quick",
        R.string.settings_section_quick,
        R.string.settings_section_quick_summary,
        OgnelissIcons.Bolt
    ),
    SPAM(
        "settings/spam",
        R.string.settings_section_spam,
        R.string.settings_section_spam_summary,
        OgnelissIcons.Block
    ),
    /*
     * «Резервная копия» и «Диагностика» объединены.
     *
     * В первой было два пункта, во второй четыре, и оба раздела
     * отвечали на один вопрос: что делать с данными приложения —
     * сохранить, восстановить, собрать журнал для разбора, сбросить.
     * Два экрана по три строки на одну тему — лишний выбор на пути
     * к тому, что человек и так делает раз в год.
     */
    MAINTENANCE(
        "settings/maintenance",
        R.string.settings_section_maintenance,
        R.string.settings_section_maintenance_summary,
        OgnelissIcons.Save
    ),
    SYSTEM(
        "settings/system",
        R.string.settings_section_system,
        R.string.settings_section_system_summary,
        OgnelissIcons.PhoneAndroid
    ),
    ABOUT(
        "settings/about",
        R.string.settings_section_about,
        R.string.settings_section_about_summary,
        OgnelissIcons.Info
    )
}

/**
 * Тип элемента настройки. Определяет, как элемент рисуется
 * и что происходит при нажатии.
 */
sealed interface SettingKind {
    /** Переключатель вкл/выкл. */
    data class Switch(val key: String) : SettingKind
    /** Выбор из списка вариантов (открывает диалог). */
    data class Choice(val key: String) : SettingKind
    /** Числовой ползунок. */
    /*
     * Ползунка среди видов настроек больше нет.
     *
     * Единственным был порог оценки риска, а оценка убрана. Оставлять
     * неиспользуемый вид нельзя: when по видам обязан покрывать все,
     * и ветка «ползунок» осталась бы в экране категорий как мёртвый
     * код, который однажды кто-нибудь оживит по ошибке.
     */
    /** Действие: переход, экспорт, очистка. */
    data class Action(val key: String) : SettingKind
    /** Ссылка в системные настройки Android. */
    data class SystemLink(val key: String) : SettingKind
}

/**
 * Описание одной настройки.
 *
 * @param dangerous помечает настройки с неочевидными последствиями
 *        (например, блокировка всех незнакомых номеров). Такие требуют
 *        подтверждения и показываются с предупреждающим цветом.
 * @param keywordsRes дополнительные слова для поиска — те, которыми
 *        пользователь может искать настройку, но которых нет в её названии.
 *        Ресурс, а не строка: поиск должен работать на любом языке.
 */
data class SettingEntry(
    val id: String,
    val category: SettingsCategory,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int? = null,
    val kind: SettingKind,
    val dangerous: Boolean = false,
    @StringRes val keywordsRes: Int? = null,
    /**
     * Подгруппа внутри раздела — заголовок над соседними пунктами.
     *
     * Появилась, когда в разделах стало по семь-восемь пунктов о разных
     * вещах: в «Журнале» рядом жили группировка вызовов, заметки
     * и порядок поиска, и настройки одной функции оказывались не рядом.
     * Новый раздел завести нельзя — их уже десять, это предел, который
     * держит тест, — а подзаголовок разбивает длинный раздел на части
     * без нового уровня навигации.
     *
     * Пункты одной подгруппы должны стоять в реестре подряд: экран
     * режет список по смене этого поля, а не собирает группы заново.
     * Проверяется тестом «подгруппы не разорваны».
     */
    @StringRes val groupRes: Int? = null
)

/** Полный список настроек приложения. */
object SettingsRegistry {

    val entries: List<SettingEntry> = listOf(

        // ── Оформление ─────────────────────────────────────────────────────
        SettingEntry(
            "language", SettingsCategory.APPEARANCE,
            R.string.settings_language, R.string.settings_language_summary,
            SettingKind.Choice("language"),
            keywordsRes = R.string.keywords_language
        ),
        SettingEntry(
            "theme", SettingsCategory.APPEARANCE,
            R.string.settings_theme, R.string.settings_theme_summary,
            SettingKind.Choice("theme"),
            keywordsRes = R.string.keywords_theme
        ),
        SettingEntry(
            "dynamic_color", SettingsCategory.APPEARANCE,
            R.string.settings_dynamic_color, R.string.settings_dynamic_color_text,
            SettingKind.Switch("dynamic_color")
        ),
        SettingEntry(
            "large_buttons", SettingsCategory.APPEARANCE,
            R.string.settings_large_buttons, R.string.settings_large_buttons_text,
            SettingKind.Switch("large_buttons"),
            keywordsRes = R.string.keywords_accessibility
        ),
        SettingEntry(
            "button_labels", SettingsCategory.APPEARANCE,
            R.string.settings_button_labels, R.string.settings_button_labels_text,
            SettingKind.Switch("button_labels")
        ),
        SettingEntry(
            "show_photo", SettingsCategory.APPEARANCE,
            R.string.settings_show_photo, null,
            SettingKind.Switch("show_photo")
        ),
        // 1.3.5: шапка меню записи в цвете темы — по умолчанию вкл.
        SettingEntry(
            "sheet_header_tint", SettingsCategory.APPEARANCE,
            R.string.settings_sheet_header_tint, R.string.settings_sheet_header_tint_text,
            SettingKind.Switch("sheet_header_tint")
        ),
        SettingEntry(
            "sim_labels", SettingsCategory.SIM,
            R.string.settings_sim_labels, R.string.settings_sim_labels_text,
            SettingKind.Action("sim_labels")
        ),
        SettingEntry(
            "remember_sim", SettingsCategory.SIM,
            R.string.settings_remember_sim, R.string.settings_remember_sim_text,
            SettingKind.Switch("remember_sim")
        ),

        // ── Жесты ──────────────────────────────────────────────────────────
        SettingEntry(
            "swipe_sim", SettingsCategory.SIM,
            R.string.settings_swipe_sim, R.string.settings_swipe_sim_text,
            SettingKind.Choice("swipe_sim")
        ),

        // ── О приложении ───────────────────────────────────────────────────
        /*
         * ЭНЕРГОСБЕРЕЖЕНИЕ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ЭТО ВАЖНЕЕ, ЧЕМ КАЖЕТСЯ
         * ══════════════════════════════════════════════════════════════════
         * Приложение слушает изменения журнала через ContentObserver.
         * Если система усыпила процесс — а прошивки Xiaomi, Huawei
         * и подобные делают это агрессивно, — наблюдатель не проснётся.
         *
         * Итог: пропущенный звонок не появится в журнале, пока человек
         * сам не откроет приложение. И он решит, что журнал теряет
         * вызовы.
         *
         * Проверка была написана, но нигде не показывалась: механизм
         * есть, а человек о нём не знает.
         *
         * ── Почему не в мастере при первом запуске ─────────────────────────
         * Мастер и так просит четыре разрешения. Пятый экран с просьбой,
         * которую система показывает как «разрешить работать в фоне»,
         * читается как навязчивость — и его пропускают вместе
         * с остальными.
         *
         * Здесь же человек приходит сам, когда заметил, что журнал
         * отстаёт.
         */
        /*
         * СВОЙ НОМЕР В НАСТРОЙКАХ.
         *
         * ── Зачем, если есть пункт в меню ──────────────────────────────────
         * В меню он открывает карточку — это действие. Здесь настройка:
         * посмотреть, что задано, изменить или убрать.
         *
         * Разделять важно: человек, который хочет сменить номер,
         * не должен для этого открывать карточку и искать в ней
         * кнопку правки, которой там нет.
         */
        SettingEntry(
            "own_number", SettingsCategory.SIM,
            R.string.my_number_title, R.string.settings_own_number_text,
            SettingKind.Action("own_number")
        ),
        SettingEntry(
            "sys_sim", SettingsCategory.SIM,
            R.string.settings_sim, null, SettingKind.SystemLink("sim")
        ),
        SettingEntry(
            "swipe_right", SettingsCategory.GESTURES,
            R.string.settings_swipe_right, null,
            SettingKind.Choice("swipe_right"),
            groupRes = R.string.settings_group_row_swipe
        ),
        SettingEntry(
            "swipe_left", SettingsCategory.GESTURES,
            R.string.settings_swipe_left, null,
            SettingKind.Choice("swipe_left"),
            groupRes = R.string.settings_group_row_swipe
        ),
        SettingEntry(
            "swipe_tuning", SettingsCategory.GESTURES,
            R.string.settings_swipe_tuning, R.string.settings_swipe_tuning_text,
            SettingKind.Action("swipe_tuning"),
            groupRes = R.string.settings_group_row_swipe
        ),
        SettingEntry(
            "panel_swipe_nav", SettingsCategory.GESTURES,
            R.string.settings_panel_swipe_nav, R.string.settings_panel_swipe_nav_text,
            SettingKind.Switch("panel_swipe_nav"),
            groupRes = R.string.settings_group_screen_swipe
        ),
        SettingEntry(
            "haptic", SettingsCategory.GESTURES,
            R.string.settings_haptic, null,
            SettingKind.Switch("haptic"),
            groupRes = R.string.settings_group_feedback
        ),
        SettingEntry(
            "key_tones", SettingsCategory.GESTURES,
            R.string.settings_key_tones, null,
            SettingKind.Switch("key_tones"),
            groupRes = R.string.settings_group_feedback
        ),

        // ── Вызовы ─────────────────────────────────────────────────────────
        SettingEntry(
            "grouping", SettingsCategory.CALL_LOG,
            R.string.settings_grouping, R.string.settings_grouping_text,
            SettingKind.Choice("grouping"),
            groupRes = R.string.settings_group_log_view
        ),
        SettingEntry(
            "merge_calls", SettingsCategory.CALL_LOG,
            R.string.settings_merge_calls, R.string.settings_merge_calls_text,
            SettingKind.Switch("merge_calls"),
            groupRes = R.string.settings_group_log_view
        ),
        SettingEntry(
            "journal_style", SettingsCategory.CALL_LOG,
            R.string.settings_journal_style, R.string.settings_journal_style_text,
            SettingKind.Choice("journal_style"),
            groupRes = R.string.settings_group_log_view
        ),
        SettingEntry(
            "carrier_info", SettingsCategory.CALL_LOG,
            R.string.settings_carrier_info, R.string.settings_carrier_info_text,
            SettingKind.Choice("carrier_info"),
            groupRes = R.string.settings_group_log_view
        ),
        SettingEntry(
            "sort_order", SettingsCategory.CALL_LOG,
            R.string.settings_sort_order, R.string.settings_sort_order_text,
            SettingKind.Choice("sort_order"),
            groupRes = R.string.settings_group_search_stats
        ),
        SettingEntry(
            "stats", SettingsCategory.CALL_LOG,
            R.string.stats_title, R.string.stats_summary,
            SettingKind.Action("stats"),
            groupRes = R.string.settings_group_search_stats
        ),
        SettingEntry(
            "notes_retention", SettingsCategory.CALL_LOG,
            R.string.settings_notes_retention, R.string.settings_notes_retention_text,
            SettingKind.Choice("notes_retention"),
            groupRes = R.string.settings_group_notes
        ),
        SettingEntry(
            "notes_retention_scope", SettingsCategory.CALL_LOG,
            R.string.settings_notes_scope, R.string.settings_notes_scope_text,
            SettingKind.Choice("notes_retention_scope"),
            groupRes = R.string.settings_group_notes
        ),
        /*
         * Кто «звонит» при псевдозвонке.
         *
         * Действие, а не переключатель: открывает выбор контакта тем же
         * экраном, что и закрепление номеров. Пусто — случайный контакт,
         * и это разумное умолчание: настраивать заранее никто не будет.
         */
        SettingEntry(
            "fake_call_button", SettingsCategory.QUICK_ACCESS,
            R.string.settings_fake_call_button, R.string.settings_fake_call_button_text,
            SettingKind.Switch("fake_call_button"),
            groupRes = R.string.settings_group_fake_call
        ),
        SettingEntry(
            "fake_call_number", SettingsCategory.QUICK_ACCESS,
            R.string.settings_fake_call_number, R.string.settings_fake_call_number_text,
            SettingKind.Action("fake_call_number"),
            groupRes = R.string.settings_group_fake_call
        ),
        SettingEntry(
            "pinned", SettingsCategory.QUICK_ACCESS,
            R.string.settings_pinned, R.string.settings_pinned_text,
            SettingKind.Action("pinned"),
            groupRes = R.string.settings_group_at_hand
        ),
        SettingEntry(
            "speed_dial", SettingsCategory.QUICK_ACCESS,
            R.string.speed_dial_title, R.string.speed_dial_summary,
            SettingKind.Action("speed_dial"),
            groupRes = R.string.settings_group_at_hand
        ),
        SettingEntry(
            "dialpad_open_at_start", SettingsCategory.APPEARANCE,
            R.string.settings_dialpad_open, R.string.settings_dialpad_open_text,
            SettingKind.Switch("dialpad_open_at_start")
        ),
        SettingEntry(
            "smart_speaker", SettingsCategory.CALLS,
            R.string.settings_smart_speaker, R.string.settings_smart_speaker_text,
            SettingKind.Switch("smart_speaker"),
            groupRes = R.string.settings_group_during_call
        ),
        SettingEntry(
            "proximity_screen_off", SettingsCategory.CALLS,
            R.string.settings_proximity, R.string.settings_proximity_text,
            SettingKind.Switch("proximity_screen_off"),
            groupRes = R.string.settings_group_during_call
        ),
        SettingEntry(
            "call_hide_contact_number", SettingsCategory.CALLS,
            R.string.settings_hide_contact_number, R.string.settings_hide_contact_number_text,
            SettingKind.Switch("call_hide_contact_number"),
            groupRes = R.string.settings_group_during_call
        ),
        SettingEntry(
            "post_call", SettingsCategory.CALLS,
            R.string.settings_post_call, R.string.settings_post_call_text,
            SettingKind.Switch("post_call"),
            groupRes = R.string.settings_group_after_call
        ),
        SettingEntry(
            "post_call_unknown", SettingsCategory.CALLS,
            R.string.settings_post_call_unknown, R.string.settings_post_call_unknown_text,
            SettingKind.Switch("post_call_unknown"),
            groupRes = R.string.settings_group_after_call
        ),
        SettingEntry(
            "callback_suggestion", SettingsCategory.CALLS,
            R.string.settings_callback_suggestion, R.string.settings_callback_suggestion_text,
            SettingKind.Switch("callback_suggestion"),
            groupRes = R.string.settings_group_after_call
        ),
        // 1.3.5: отзыв в Google Play — «Написать в WhatsApp тем, кого нет в контактах»
        SettingEntry(
            "messengers_any_number", SettingsCategory.CALLS,
            R.string.settings_messengers_any_number, R.string.settings_messengers_any_number_text,
            SettingKind.Switch("messengers_any_number")
        ),
        SettingEntry(
            "sys_dnd", SettingsCategory.SYSTEM,
            R.string.settings_dnd, null, SettingKind.SystemLink("dnd")
        ),
        SettingEntry(
            "sys_sound", SettingsCategory.SYSTEM,
            R.string.settings_sound, null, SettingKind.SystemLink("sound")
        ),
        SettingEntry(
            "sys_notifications", SettingsCategory.SYSTEM,
            R.string.settings_notifications, null, SettingKind.SystemLink("notifications")
        ),
        SettingEntry(
            "sys_default_apps", SettingsCategory.SYSTEM,
            R.string.settings_default_apps, null, SettingKind.SystemLink("default_apps")
        ),

        // ── Защита от спама ────────────────────────────────────────────────
        /*
         * Пункт «Определитель номера и спам» убран.
         *
         * ── Почему ─────────────────────────────────────────────────────────
         * Он должен был открывать системный выбор приложения-определителя.
         * На проверенных прошивках не открывал ничего: роль
         * ROLE_CALL_SCREENING доступна не везде, а часть производителей
         * прячет этот экран.
         *
         * Три запасных пути — запрос роли, приложения по умолчанию,
         * общие настройки — положения не исправили.
         *
         * Пункт, который не работает, хуже отсутствующего: человек
         * нажимает и решает, что сломано всё приложение. Роль при этом
         * не нужна для работы: беззвучный пропуск без неё недоступен,
         * но блокировка и определение по книге работают.
         */
        SettingEntry(
            "spam_overview", SettingsCategory.SPAM,
            R.string.spam_overview_title, R.string.spam_overview_summary,
            SettingKind.Action("spam_overview")
        ),
        SettingEntry(
            "block_tagged", SettingsCategory.SPAM,
            R.string.settings_block_tagged, R.string.settings_block_tagged_text,
            SettingKind.Switch("block_tagged"),
            keywordsRes = R.string.keywords_spam,
            groupRes = R.string.settings_group_block_rules
        ),
        SettingEntry(
            "block_hidden", SettingsCategory.SPAM,
            R.string.settings_block_hidden, null,
            SettingKind.Switch("block_hidden"),
            groupRes = R.string.settings_group_block_rules
        ),
        SettingEntry(
            "block_unknown", SettingsCategory.SPAM,
            R.string.settings_block_unknown, R.string.settings_block_unknown_text,
            SettingKind.Switch("block_unknown"),
            dangerous = true,
            groupRes = R.string.settings_group_block_rules
        ),
        /*
         * Автоблокировки по оценке риска и порога уверенности здесь
         * больше нет: сама оценка убрана — она была догадкой, которую
         * нечем проверить. Разбор в комментарии к SpamPolicy.evaluate.
         */
        SettingEntry(
            "allow_list", SettingsCategory.SPAM,
            R.string.settings_allow_list, R.string.settings_allow_list_text,
            SettingKind.Action("allow_list"),
            groupRes = R.string.settings_group_lists
        ),
        SettingEntry(
            "block_list", SettingsCategory.SPAM,
            R.string.settings_block_list, null,
            SettingKind.Action("block_list"),
            groupRes = R.string.settings_group_lists
        ),
        SettingEntry(
            "sys_blocked", SettingsCategory.SPAM,
            R.string.settings_blocked_system, R.string.settings_blocked_system_text,
            SettingKind.SystemLink("blocked_numbers"),
            groupRes = R.string.settings_group_lists
        ),
        SettingEntry(
            "external_lookup", SettingsCategory.SPAM,
            R.string.settings_external_lookup, R.string.settings_external_lookup_text,
            SettingKind.Action("external_lookup"),
            dangerous = true,
            groupRes = R.string.settings_group_lookup
        ),

        // ── Резервная копия ────────────────────────────────────────────────
        SettingEntry(
            "backup_export", SettingsCategory.MAINTENANCE,
            R.string.settings_backup_export, R.string.settings_backup_export_text,
            SettingKind.Action("backup_export"),
            groupRes = R.string.settings_group_backup
        ),
        SettingEntry(
            "backup_import", SettingsCategory.MAINTENANCE,
            R.string.settings_backup_import, R.string.settings_backup_import_text,
            SettingKind.Action("backup_import"),
            groupRes = R.string.settings_group_backup
        ),
        /*
         * Выгрузка журнала и сохранение технического журнала —
         * рядом с остальными действиями над данными.
         *
         * Обе пишут файл туда, куда укажет человек, через системный
         * выбор: приложение не лезет в память само и не оставляет
         * файлов, о которых владелец не знает.
         */
        SettingEntry(
            "calllog_export", SettingsCategory.MAINTENANCE,
            R.string.settings_calllog_export, R.string.settings_calllog_export_text,
            SettingKind.Action("calllog_export"),
            groupRes = R.string.settings_group_backup
        ),

        // ── Диагностика ────────────────────────────────────────────────────
        SettingEntry(
            "file_logging", SettingsCategory.MAINTENANCE,
            R.string.settings_file_logging, R.string.settings_file_logging_text,
            SettingKind.Switch("file_logging"),
            groupRes = R.string.settings_group_diagnostics
        ),
        SettingEntry(
            "log_save", SettingsCategory.MAINTENANCE,
            R.string.settings_log_save, R.string.settings_log_save_text,
            SettingKind.Action("log_save"),
            groupRes = R.string.settings_group_diagnostics
        ),
        SettingEntry(
            "log_share", SettingsCategory.MAINTENANCE,
            R.string.settings_log_share, null,
            SettingKind.Action("log_share"),
            groupRes = R.string.settings_group_diagnostics
        ),
        SettingEntry(
            "log_clear", SettingsCategory.MAINTENANCE,
            R.string.settings_log_clear, null,
            SettingKind.Action("log_clear"),
            groupRes = R.string.settings_group_diagnostics
        ),
        SettingEntry(
            "reset", SettingsCategory.MAINTENANCE,
            R.string.settings_reset, R.string.settings_reset_text,
            SettingKind.Action("reset"),
            dangerous = true,
            groupRes = R.string.settings_group_reset
        ),
        SettingEntry(
            "battery", SettingsCategory.SYSTEM,
            R.string.settings_battery, R.string.settings_battery_text,
            SettingKind.Action("battery")
        ),
        SettingEntry(
            /*
             * Разрешения — в разделе системных настроек, а не в «О приложении».
             *
             * Они выдаются системой, а не приложением, и человек ищет их
             * там же, где «Приложения по умолчанию» и «Работа в фоне» —
             * то есть среди того, что ведёт в настройки Android.
             */
            "permissions", SettingsCategory.SYSTEM,
            R.string.settings_permissions, R.string.settings_permissions_text,
            SettingKind.Action("permissions")
        ),
        SettingEntry(
            "privacy", SettingsCategory.ABOUT,
            R.string.settings_privacy_policy, null, SettingKind.Action("privacy")
        ),
        SettingEntry(
            "terms", SettingsCategory.ABOUT,
            R.string.settings_terms, null, SettingKind.Action("terms")
        ),
        SettingEntry(
            "licenses", SettingsCategory.ABOUT,
            R.string.settings_licenses, null, SettingKind.Action("licenses")
        ),
        /*
         * Пункта «Почему нет записи звонков» больше нет.
         *
         * Он вёл на старый экран «О приложении» со встроенными текстами
         * политик, а не на сайт, где они теперь живут. Сам вопрос
         * о записи разобран в политике конфиденциальности — её пункт
         * выше, и он ведёт куда нужно.
         */
        /*
         * ВЕРСИЯ — СТРОКА СО СВЕДЕНИЯМИ, А НЕ ПЕРЕХОД.
         *
         * Пункт открывал экран «О приложении», а тот давно заменён
         * ссылками на сайт: человек нажимал «Версия» и попадал
         * в окно, где версии как раз и не было видно сразу.
         *
         * Теперь номер версии стоит прямо в подписи, а нажатие ведёт
         * на страницу приложения в Google Play — туда, куда идут
         * за обновлением и отзывом.
         *
         * Подпись подставляется в экране категорий: номер версии
         * приходит из BuildConfig, а реестр — это список без кода.
         */
        /*
         * Оценка в магазине — ссылка, а не окно с просьбой.
         *
         * Встроенный запрос отзыва (In-App Review) Google показывает
         * сам и не чаще нескольких раз в год: вызвать его по нажатию
         * нельзя, а если попытаться — человек нажмёт и ничего
         * не произойдёт. Прямая ссылка честнее: нажал — открылась
         * страница приложения.
         */
        SettingEntry(
            "rate_app", SettingsCategory.ABOUT,
            R.string.settings_rate, R.string.settings_rate_text,
            SettingKind.Action("rate_app")
        ),
        SettingEntry(
            "version", SettingsCategory.ABOUT,
            R.string.settings_version_title, R.string.settings_version,
            SettingKind.Action("version")
        )
    )

    fun byCategory(category: SettingsCategory): List<SettingEntry> =
        entries.filter { it.category == category }

    fun byId(id: String): SettingEntry? = entries.firstOrNull { it.id == id }
}
