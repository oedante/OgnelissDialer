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
    SIM(
        "settings/sim",
        R.string.settings_section_sim,
        R.string.settings_section_sim_summary,
        OgnelissIcons.SimCard
    ),
    CALLS(
        "settings/calls",
        R.string.settings_section_calls,
        R.string.settings_section_calls_summary,
        OgnelissIcons.Call
    ),
    /*
     * «Экран вызова» (2.7.5, автор: «можно вообще вынести настройки фона
     * вызова в отдельную категорию»). «Вызовы» с двумя пунктами своего фона
     * дошли до предела в 16 строк. Сюда переехало всё, что про вид экрана
     * звонка: фото, живой фон, свой фон входящих и исходящих, скрытый номер.
     * В «Вызовах» осталось поведение: как отвечать, звук, что после звонка.
     */
    CALL_SCREEN(
        "settings/callscreen",
        R.string.settings_section_call_screen,
        R.string.settings_section_call_screen_summary,
        OgnelissIcons.Image
    ),
    CALL_LOG(
        "settings/calllog",
        R.string.settings_section_log,
        R.string.settings_section_log_summary,
        OgnelissIcons.List
    ),
    /*
     * «Контакты» (2.9.6, перегруппировка по плану, автор: «в настройках у нас всё
     * перемешано»): фото и имена (были в «Оформлении»), сортировка и заметки
     * (были в «Журнале и контактах»), закреплённые и быстрый набор (были
     * в «Быстром доступе»), картинки без фото (были в «Экране вызова»),
     * мессенджеры (были в «SMS»). «Быстрого доступа» больше нет.
     */
    CONTACTS(
        "settings/contacts",
        R.string.settings_section_contacts,
        R.string.settings_section_contacts_summary,
        OgnelissIcons.Contacts
    ),
    /*
     * «SMS» (2.1.0). Было: настройки сообщений жили подгруппой в «Журнале
     * вызовов», хотя журнал с 2.0.2 — только звонки, а у SMS свой раздел
     * на нижней панели. Стало: свой раздел — приложение SMS, свайпы
     * переписок, мессенджеры.
     */
    MESSAGES(
        "settings/messages",
        R.string.nav_messages,
        R.string.settings_section_messages_summary,
        OgnelissIcons.Textsms
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
    ),
    /*
     * «РАСШИРЕННЫЕ НАСТРОЙКИ» (2.8.6, автор: «Предлагаю пересмотреть настройки,
     * которые мы добавили по просьбам пользователей. И в основных категориях
     * оставить важные и основные настройки… а всё дополнительное убрать в пункт
     * меню „Расширенные настройки“»; план — в переписке 21.11.2026, автор:
     * «после плана сразу делай»).
     *
     * Не тема, а второй уровень: сюда попадают пункты с advanced = true из всех
     * разделов, разложенные по своим разделам (SettingsRegistry.groupKey).
     * В разделе такой пункт не показывается — внизу раздела ссылка «Расширенные
     * настройки» с их названиями; поиск по настройкам находит всё, как раньше.
     *
     * Последний в перечне: главный экран рисует его последней строкой,
     * а «Расширенные» по смыслу — после всего основного.
     */
    ADVANCED(
        "settings/advanced",
        R.string.settings_section_advanced,
        R.string.settings_section_advanced_summary,
        OgnelissIcons.Tune
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
    /**
     * Числовой ползунок (2.2.1): «Размер шрифта», как в Ogneliss Mail.
     * Был убран вместе с порогом оценки риска; вернулся с настоящим делом.
     */
    data class Slider(val key: String) : SettingKind
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
    @StringRes val groupRes: Int? = null,
    /**
     * Дополнительный пункт (2.8.6): показывается не в своём разделе,
     * а в «Расширенных настройках» — под названием раздела. [category]
     * при этом остаётся настоящим: по ней пункт стоит в своей группе
     * «Расширенных», а раздел перечисляет его в ссылке внизу.
     */
    val advanced: Boolean = false
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
        // 1.8.4: цветовые темы, как в Ogneliss Mail — расчёт в ThemePresetMath
        SettingEntry(
            "theme_preset", SettingsCategory.APPEARANCE,
            R.string.settings_theme_preset, R.string.settings_theme_preset_text,
            SettingKind.Choice("theme_preset"),
            keywordsRes = R.string.keywords_theme
        ),
        SettingEntry(
            "dynamic_color", SettingsCategory.APPEARANCE,
            R.string.settings_dynamic_color, R.string.settings_dynamic_color_text,
            SettingKind.Switch("dynamic_color"),
            keywordsRes = R.string.keywords_dynamic_color
            /*
             * 2.9.1: снова в основном «Оформлении» (было в «Расширенных» с 2.8.6).
             * Форум, eXense: «цвет темы нельзя выбрать… monet» — а это он и есть:
             * в «Расширенных» его не нашли. Цвет приложения — главное в оформлении.
             */
        ),
        /*
         * 2.2.1: размер шрифта и масштаб — как в настройках Ogneliss Mail:
         * ползунок 80–160 % и варианты масштаба в ряд, под ними — «Сейчас: N %»
         * (рисуются на месте, без окна выбора: SettingsCategoryScreen).
         */
        SettingEntry(
            "font_scale", SettingsCategory.APPEARANCE,
            R.string.settings_font_scale, null,
            SettingKind.Slider("font_scale"),
            keywordsRes = R.string.keywords_accessibility
        ),
        SettingEntry(
            "ui_scale", SettingsCategory.APPEARANCE,
            // Пояснение — под вариантами, с подстановкой «Сейчас: N %» (UiScaleRow)
            R.string.settings_ui_scale, null,
            SettingKind.Choice("ui_scale"),
            keywordsRes = R.string.keywords_accessibility
        ),
        /*
         * 1.9.0: шрифт, скругление и плотность — как «Оформление» Ogneliss
         * Mail (свой акцент — в окне «Цветовой темы», ColorThemeDialog:
         * в разделе не больше 14 пунктов, а тема и акцент — одно решение).
         */
        SettingEntry(
            "ui_font", SettingsCategory.APPEARANCE,
            R.string.settings_ui_font, R.string.settings_ui_font_text,
            SettingKind.Choice("ui_font"),
            keywordsRes = R.string.keywords_theme,
            advanced = true
        ),
        SettingEntry(
            "corner_size", SettingsCategory.APPEARANCE,
            R.string.settings_corner_size, R.string.settings_corner_size_text,
            SettingKind.Choice("corner_size"),
            keywordsRes = R.string.keywords_theme,
            advanced = true
        ),
        SettingEntry(
            "list_density", SettingsCategory.APPEARANCE,
            R.string.settings_list_density, R.string.settings_list_density_text,
            SettingKind.Choice("list_density"),
            keywordsRes = R.string.keywords_theme,
            advanced = true
        ),
        SettingEntry(
            "large_buttons", SettingsCategory.APPEARANCE,
            R.string.settings_large_buttons, R.string.settings_large_buttons_text,
            SettingKind.Switch("large_buttons"),
            keywordsRes = R.string.keywords_accessibility,
            advanced = true
        ),
        SettingEntry(
            "button_labels", SettingsCategory.APPEARANCE,
            R.string.settings_button_labels, R.string.settings_button_labels_text,
            SettingKind.Switch("button_labels"),
            advanced = true
        ),

        // ── Панель и жесты ─────────────────────────────────────────────────
        /*
         * Конструктор нижней панели (2.9.2, автор: «как в Ogneliss Mail сделать
         * конструктор нижней панели»). Разбор — settings/PanelSlots.kt, экран —
         * PanelSlotsScreen. Первым в разделе (с 2.9.6 — «Панель и жесты», было —
         * «Быстрый доступ»): панель — самое быстрое, что есть в приложении.
         */
        SettingEntry(
            "panel_slots", SettingsCategory.GESTURES,
            R.string.settings_panel_slots, R.string.settings_panel_slots_text,
            SettingKind.Action("panel_slots"),
            keywordsRes = R.string.keywords_panel_slots,
            groupRes = R.string.settings_group_panel_start
        ),
        /*
         * 2.7.0, форум (oleg_p, 15.11.2026): выбор экрана при запуске — журнал
         * (по умолчанию), SMS, контакты или последний раздел. Разбор —
         * settings/StartSection.kt. Свою группу завели, чтобы не путать
         * с «Главным экраном» выше: тот про рабочий стол лаунчера.
         */
        SettingEntry(
            "start_section", SettingsCategory.GESTURES,
            R.string.settings_start_section, R.string.settings_start_section_text,
            SettingKind.Choice("start_section"),
            keywordsRes = R.string.keywords_start_section,
            groupRes = R.string.settings_group_panel_start
        ),
        SettingEntry(
            "dialpad_open_at_start", SettingsCategory.GESTURES,
            R.string.settings_dialpad_open, R.string.settings_dialpad_open_text,
            SettingKind.Switch("dialpad_open_at_start"),
            // 2.1.0: из «Оформления» — это поведение журнала при запуске
            groupRes = R.string.settings_group_panel_start
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
            groupRes = R.string.settings_group_row_swipe,
            advanced = true
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
        /*
         * 2.5.0, форум: вибрация, когда вызываемый снял трубку (Prefs.vibrateOnConnect).
         * Здесь, в «Отклике», рядом с вибрацией клавиш: в «Звонках» уже четырнадцать
         * настроек — больше читают прокруткой (SettingsRegistryTest).
         */
        SettingEntry(
            "vibrate_on_connect", SettingsCategory.GESTURES,
            R.string.settings_vibrate_on_connect, R.string.settings_vibrate_on_connect_text,
            SettingKind.Switch("vibrate_on_connect"),
            groupRes = R.string.settings_group_feedback
        ),
        SettingEntry(
            "panel_swipe_nav", SettingsCategory.GESTURES,
            R.string.settings_panel_swipe_nav, R.string.settings_panel_swipe_nav_text,
            SettingKind.Switch("panel_swipe_nav"),
            groupRes = R.string.settings_group_screen_swipe,
            advanced = true
        ),
        SettingEntry(
            "sms_swipe_right", SettingsCategory.GESTURES,
            R.string.settings_sms_swipe_right, R.string.settings_sms_swipe_text,
            SettingKind.Choice("sms_swipe_right"),
            keywordsRes = R.string.keywords_sms,
            groupRes = R.string.settings_group_sms_swipe,
            advanced = true
        ),
        SettingEntry(
            "sms_swipe_left", SettingsCategory.GESTURES,
            R.string.settings_sms_swipe_left, R.string.settings_sms_swipe_text,
            SettingKind.Choice("sms_swipe_left"),
            keywordsRes = R.string.keywords_sms,
            groupRes = R.string.settings_group_sms_swipe,
            advanced = true
        ),
        /*
         * Форма значков «SMS» и «Контакты» на главном экране (2.1.3, автор:
         * «чтобы по форме можно было выбрать скругление»). Разбор — WidgetShape.
         */
        SettingEntry(
            "widget_shape", SettingsCategory.GESTURES,
            R.string.settings_widget_shape, R.string.settings_widget_shape_text,
            SettingKind.Choice("widget_shape"),
            keywordsRes = R.string.keywords_widget_shape,
            groupRes = R.string.settings_group_home_screen,
            advanced = true
        ),
        /*
         * 2.6.0, форум: отдельные значки «SMS» и «Контакты» в списке приложений —
         * в док и в папку (разбор в widget/SectionIcons.kt).
         */
        SettingEntry(
            "section_icons", SettingsCategory.GESTURES,
            R.string.settings_section_icons, R.string.settings_section_icons_text,
            SettingKind.Switch("section_icons"),
            keywordsRes = R.string.keywords_section_icons,
            groupRes = R.string.settings_group_home_screen,
            advanced = true
        ),

        // ── SIM-карты ──────────────────────────────────────────────────────
        // «Цветная шапка меню» (1.3.5) снята в 1.5.7: меню долгого нажатия
        // стало карточками (ActionCards), шапки у него больше нет
        SettingEntry(
            "sim_labels", SettingsCategory.SIM,
            R.string.settings_sim_labels, R.string.settings_sim_labels_text,
            SettingKind.Action("sim_labels")
        ),
        /*
         * Карта для звонков и SMS (2.9.1, форум, Hosato: «действия (звонки, смс)
         * только на одну симку, вторая — только для приёма входящих»). Разбор —
         * Prefs.OutgoingSim. В основном разделе: меняет главную кнопку экрана.
         */
        SettingEntry(
            "outgoing_sim", SettingsCategory.SIM,
            R.string.settings_outgoing_sim, R.string.settings_outgoing_sim_text,
            SettingKind.Choice("outgoing_sim"),
            keywordsRes = R.string.keywords_outgoing_sim
        ),
        /*
         * «Карта по номеру» (2.9.11, автор: «пользователи просят звонки с разных
         * SIM по маске номера»): правила «начало номера → карта», свой экран
         * (SimRulesScreen). Где действуют и что главнее — разбор у SimRouter.
         */
        SettingEntry(
            "sim_rules", SettingsCategory.SIM,
            R.string.settings_sim_rules, R.string.settings_sim_rules_text,
            SettingKind.Action("sim_rules"),
            keywordsRes = R.string.keywords_sim_rules
        ),
        SettingEntry(
            "remember_sim", SettingsCategory.SIM,
            R.string.settings_remember_sim, R.string.settings_remember_sim_text,
            SettingKind.Switch("remember_sim")
        ),
        /*
         * Здесь была «SIM для действий свайпа» (swipe_sim, 1.x). 2.9.11: убрана — карта
         * теперь в самом действии свайпа («Позвонить с SIM 1/2», 2.9.8); сохранённый
         * выбор переносится в свайпы один раз (SwipeSimMigration).
         */
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

        // ── Вызовы ─────────────────────────────────────────────────────────
        // 1.5.8, обзор интерфейса (У6): ответ сдвигом — от случайного касания
        SettingEntry(
            "answer_mode", SettingsCategory.CALLS,
            R.string.settings_answer_mode, R.string.settings_answer_mode_text,
            SettingKind.Choice("answer_mode"),
            groupRes = R.string.settings_group_during_call
        ),
        /*
         * «Ответить» слева (2.1.1, просьба пользователей): привыкшим к ответу
         * слева на прежнем телефоне. Разбор — Prefs.swapAnswer.
         */
        SettingEntry(
            "swap_answer", SettingsCategory.CALLS,
            R.string.settings_swap_answer, R.string.settings_swap_answer_text,
            SettingKind.Switch("swap_answer"),
            keywordsRes = R.string.keywords_swap_answer,
            groupRes = R.string.settings_group_during_call
        ),
        /*
         * Входящий поверх приложений (2.1.0, вопрос пользователя 07.11.2026):
         * на весь экран (как было) или плашкой сверху, пока телефон
         * разблокирован. Разбор — OgnelissInCallService.
         */
        SettingEntry(
            "incoming_banner", SettingsCategory.CALLS,
            R.string.settings_incoming_banner, R.string.settings_incoming_banner_text,
            SettingKind.Switch("incoming_banner"),
            keywordsRes = R.string.keywords_incoming_banner,
            groupRes = R.string.settings_group_during_call
        ),
        SettingEntry(
            "smart_speaker", SettingsCategory.CALLS,
            R.string.settings_smart_speaker, R.string.settings_smart_speaker_text,
            SettingKind.Switch("smart_speaker"),
            groupRes = R.string.settings_group_during_call,
            advanced = true
        ),
        SettingEntry(
            "proximity_screen_off", SettingsCategory.CALLS,
            R.string.settings_proximity, R.string.settings_proximity_text,
            SettingKind.Switch("proximity_screen_off"),
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
            groupRes = R.string.settings_group_after_call,
            advanced = true
        ),
        SettingEntry(
            "callback_suggestion", SettingsCategory.CALLS,
            R.string.settings_callback_suggestion, R.string.settings_callback_suggestion_text,
            SettingKind.Switch("callback_suggestion"),
            groupRes = R.string.settings_group_after_call,
            advanced = true
        ),
        /*
         * Автодозвон (1.8.0, автор: «Автодозвон»): «занято» — набрать снова.
         * Два пункта: режим и темп. Темп — готовыми сочетаниями, разбор
         * в RedialPace; правила — в call/RedialPolicy.kt.
         */
        SettingEntry(
            "auto_redial", SettingsCategory.CALLS,
            R.string.settings_auto_redial, R.string.settings_auto_redial_text,
            SettingKind.Choice("auto_redial"),
            keywordsRes = R.string.keywords_auto_redial,
            groupRes = R.string.settings_group_after_call
        ),
        SettingEntry(
            "auto_redial_pace", SettingsCategory.CALLS,
            R.string.settings_auto_redial_pace, R.string.settings_auto_redial_pace_text,
            SettingKind.Choice("auto_redial_pace"),
            keywordsRes = R.string.keywords_auto_redial,
            groupRes = R.string.settings_group_after_call,
            advanced = true
        ),
        /*
         * Кто «звонит» при псевдозвонке.
         *
         * Действие, а не переключатель: открывает выбор контакта тем же
         * экраном, что и закрепление номеров. Пусто — случайный контакт,
         * и это разумное умолчание: настраивать заранее никто не будет.
         */
        SettingEntry(
            "fake_call_button", SettingsCategory.CALLS,
            R.string.settings_fake_call_button, R.string.settings_fake_call_button_text,
            SettingKind.Switch("fake_call_button"),
            groupRes = R.string.settings_group_fake_call
        ),
        SettingEntry(
            "fake_call_number", SettingsCategory.CALLS,
            R.string.settings_fake_call_number, R.string.settings_fake_call_number_text,
            SettingKind.Action("fake_call_number"),
            groupRes = R.string.settings_group_fake_call
        ),

        // ── Экран вызова ───────────────────────────────────────────────────
        /*
         * Предпросмотр (2.9.11, форум, Bublikov34: «настройки отображения вызовов
         * как-то просматривать при выборе, до применения и звонка кому-то»): экран
         * псевдозвонка с текущими настройками, тихо (FakeCall.preview). Первым в
         * разделе — поменял ниже, посмотрел здесь; то же — кнопкой в окнах выбора.
         */
        SettingEntry(
            "call_preview", SettingsCategory.CALL_SCREEN,
            R.string.settings_call_preview, R.string.settings_call_preview_text,
            SettingKind.Action("call_preview"),
            keywordsRes = R.string.keywords_call_preview
        ),
        SettingEntry(
            "call_photo_style", SettingsCategory.CALL_SCREEN,
            R.string.settings_call_photo, R.string.settings_call_photo_text,
            SettingKind.Choice("call_photo_style")
        ),
        // 1.5.7: отзыв valmikk — портрет крупнее (разбор в CallPhotoSize)
        SettingEntry(
            "call_photo_size", SettingsCategory.CALL_SCREEN,
            R.string.settings_call_photo_size, R.string.settings_call_photo_size_text,
            SettingKind.Choice("call_photo_size"),
            advanced = true
        ),
        SettingEntry(
            "call_hide_contact_number", SettingsCategory.CALL_SCREEN,
            R.string.settings_hide_contact_number, R.string.settings_hide_contact_number_text,
            SettingKind.Switch("call_hide_contact_number"),
            advanced = true
        ),
        /*
         * Подписи состояния (2.9.7, письмо пользователя: «показывать или скрывать
         * подписи „Входящий“, „Вызов“, оставив только таймер»). Рядом с «Типом
         * номера вместо номера» — тоже о том, сколько текста на экране вызова.
         */
        SettingEntry(
            "call_status_labels", SettingsCategory.CALL_SCREEN,
            R.string.settings_call_status_labels, R.string.settings_call_status_labels_text,
            SettingKind.Switch("call_status_labels"),
            keywordsRes = R.string.keywords_call_status_labels,
            advanced = true
        ),
        SettingEntry(
            "call_animation", SettingsCategory.CALL_SCREEN,
            R.string.settings_call_animation, R.string.settings_call_animation_text,
            SettingKind.Switch("call_animation")
        ),
        /*
         * Облик живого фона (2.9.5, автор: «экран в светлой и тёмной теме с пламенем
         * и лисой и анимацией волн»). Разбор — AmbientStyle (CallAmbientBackground).
         */
        SettingEntry(
            "call_ambient_style", SettingsCategory.CALL_SCREEN,
            R.string.settings_call_ambient_style, R.string.settings_call_ambient_style_text,
            SettingKind.Choice("call_ambient_style"),
            keywordsRes = R.string.keywords_call_ambient_style
        ),
        // 2.7.4: свой снимок вместо живого фона — отдельно для входящих и исходящих
        SettingEntry(
            "call_bg_incoming", SettingsCategory.CALL_SCREEN,
            R.string.settings_call_bg_incoming, R.string.settings_call_bg_incoming_text,
            SettingKind.Choice("call_bg_incoming"),
            keywordsRes = R.string.keywords_call_bg
        ),
        SettingEntry(
            "call_bg_outgoing", SettingsCategory.CALL_SCREEN,
            R.string.settings_call_bg_outgoing, R.string.settings_call_bg_outgoing_text,
            SettingKind.Choice("call_bg_outgoing"),
            keywordsRes = R.string.keywords_call_bg
        ),

        // ── Журнал вызовов ─────────────────────────────────────────────────
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
            groupRes = R.string.settings_group_log_view,
            advanced = true
        ),
        /*
         * «Вид журнала» (Карточки / Группы по дням) убран в 1.5.3: в сплошном
         * списке строки идут во всю ширину без зазоров, и оба вида выглядели
         * одинаково. Сохранённое значение в настройках не трогаем — журнал
         * его больше не читает (MainDialerScreen, groupPositionOf).
         */
        SettingEntry(
            "carrier_info", SettingsCategory.CALL_LOG,
            R.string.settings_carrier_info, R.string.settings_carrier_info_text,
            SettingKind.Choice("carrier_info"),
            groupRes = R.string.settings_group_log_view
        ),
        /*
         * 2.7.0, форум (21vasya, 15.11.2026): цветные стрелки вызовов. Хотели
         * в «Оформлении», но там уже четырнадцать пунктов — предел
         * SettingsRegistryTest; стрелки живут в журнале, поэтому здесь,
         * в группе «Вид журнала». Разбор — ui/theme/CallArrowColor.kt.
         */
        SettingEntry(
            "colored_call_arrows", SettingsCategory.CALL_LOG,
            R.string.settings_colored_call_arrows, R.string.settings_colored_call_arrows_text,
            SettingKind.Switch("colored_call_arrows"),
            keywordsRes = R.string.keywords_colored_call_arrows,
            groupRes = R.string.settings_group_log_view
        ),
        // 2.9.8: форум, Bublikov34 — «не нашёл включение отображения длительности звонка в журнале»
        SettingEntry(
            "call_log_duration", SettingsCategory.CALL_LOG,
            R.string.settings_call_log_duration, R.string.settings_call_log_duration_text,
            SettingKind.Switch("call_log_duration"),
            keywordsRes = R.string.keywords_call_log_duration,
            groupRes = R.string.settings_group_log_view
        ),
        SettingEntry(
            "sort_order", SettingsCategory.CALL_LOG,
            R.string.settings_sort_order, R.string.settings_sort_order_text,
            SettingKind.Choice("sort_order"),
            groupRes = R.string.settings_group_search_stats,
            advanced = true
        ),
        SettingEntry(
            "stats", SettingsCategory.CALL_LOG,
            R.string.stats_title, R.string.stats_summary,
            SettingKind.Action("stats"),
            groupRes = R.string.settings_group_search_stats,
            advanced = true
        ),

        // ── Контакты ───────────────────────────────────────────────────────
        SettingEntry(
            "show_photo", SettingsCategory.CONTACTS,
            R.string.settings_show_photo, null,
            SettingKind.Switch("show_photo")
        ),
        /*
         * Имена контактов (1.6.2): отзыв в Google Play — «сортировку
         * по фамилии»; автор — «предлагать пользователю выбор, как
         * показывать». Два пункта, как в системных «Контактах»: формат
         * и порядок независимы. Разбор — data/ContactNames.kt.
         */
        SettingEntry(
            "contact_name_format", SettingsCategory.CONTACTS,
            R.string.settings_contact_name_format, R.string.settings_contact_name_format_text,
            SettingKind.Choice("contact_name_format"),
            keywordsRes = R.string.keywords_contact_names
        ),
        /*
         * 2.8.6, перестройка настроек (автор: «в основных категориях оставить важные
         * и основные настройки… а всё дополнительное убрать в „Расширенные
         * настройки“»): раздел стал «Журнал и контакты» — сюда переехали
         * сортировка контактов (из «Оформления») и экран при запуске (из «Быстрого
         * доступа»), вместе с «Открывать клавиатуру при запуске». Подгруппа
         * сортировки — «Контакты»: «Имена контактов» остались в «Оформлении»
         * одним пунктом, и там он стоит в общей карточке, без заголовка.
         */
        SettingEntry(
            "contact_sort", SettingsCategory.CONTACTS,
            R.string.settings_contact_sort, R.string.settings_contact_sort_text,
            SettingKind.Choice("contact_sort"),
            keywordsRes = R.string.keywords_contact_names
        ),
        SettingEntry(
            "pinned", SettingsCategory.CONTACTS,
            R.string.settings_pinned, R.string.settings_pinned_text,
            SettingKind.Action("pinned"),
            groupRes = R.string.settings_group_at_hand
        ),
        SettingEntry(
            "speed_dial", SettingsCategory.CONTACTS,
            R.string.speed_dial_title, R.string.speed_dial_summary,
            SettingKind.Action("speed_dial"),
            groupRes = R.string.settings_group_at_hand
        ),
        /*
         * 2.8.0: картинки вместо фото (форум, iKovalsky, как в TruePhone; автор:
         * «совместим варианты А и Б и дадим выбор, где применять»). Здесь, а не
         * в «Оформлении»: там 16 пунктов — предел SettingsRegistryTest; а придут
         * сюда чаще всего ради экрана вызова. Поиск по настройкам находит и так.
         */
        SettingEntry(
            "avatar_no_photo", SettingsCategory.CONTACTS,
            R.string.settings_avatar_no_photo, R.string.settings_avatar_no_photo_text,
            SettingKind.Choice("avatar_no_photo"),
            groupRes = R.string.settings_group_avatar_pictures,
            keywordsRes = R.string.keywords_avatar_pictures,
            advanced = true
        ),
        SettingEntry(
            "avatar_unknown", SettingsCategory.CONTACTS,
            R.string.settings_avatar_unknown, R.string.settings_avatar_unknown_text,
            SettingKind.Choice("avatar_unknown"),
            groupRes = R.string.settings_group_avatar_pictures,
            keywordsRes = R.string.keywords_avatar_pictures,
            advanced = true
        ),
        SettingEntry(
            "avatar_scope", SettingsCategory.CONTACTS,
            R.string.settings_avatar_scope, null,
            SettingKind.Choice("avatar_scope"),
            groupRes = R.string.settings_group_avatar_pictures,
            keywordsRes = R.string.keywords_avatar_pictures,
            advanced = true
        ),
        SettingEntry(
            "notes_retention", SettingsCategory.CONTACTS,
            R.string.settings_notes_retention, R.string.settings_notes_retention_text,
            SettingKind.Choice("notes_retention"),
            groupRes = R.string.settings_group_notes,
            advanced = true
        ),
        SettingEntry(
            "notes_retention_scope", SettingsCategory.CONTACTS,
            R.string.settings_notes_scope, R.string.settings_notes_scope_text,
            SettingKind.Choice("notes_retention_scope"),
            groupRes = R.string.settings_group_notes,
            advanced = true
        ),
        SettingEntry(
            "messengers_any_number", SettingsCategory.CONTACTS,
            R.string.settings_messengers_any_number, R.string.settings_messengers_any_number_text,
            SettingKind.Switch("messengers_any_number"),
            // 2.1.0: из «Вызовов» — это о том, куда писать
            groupRes = R.string.settings_group_messengers,
            advanced = true
        ),

        // ── SMS ────────────────────────────────────────────────────────────
        /*
         * Сообщения (2.0.0): SMS в журнале и переписка в диалере.
         * «Приложение SMS» — действие: роль выдаёт система своим окном.
         */
        SettingEntry(
            "sms_default_app", SettingsCategory.MESSAGES,
            R.string.settings_sms_default_app, R.string.settings_sms_default_app_text,
            SettingKind.Action("sms_default_app"),
            keywordsRes = R.string.keywords_sms,
            groupRes = R.string.settings_group_messages
        ),
        /*
         * Отключить уведомления SMS (2.8.6, форум: «возможность отключать уведомления
         * для SMS полностью»; автор: «можно добавить, но с предупреждением на красном
         * фоне или с красным треугольником»). Опасная: включение — после окна
         * с последствиями, включённая — красной строкой. Молчат только новые
         * входящие (MessageNotifications.showForAddress); «не отправлено» и «MMS
         * не скачано» остаются — без них сообщение теряется.
         */
        SettingEntry(
            "sms_notifications_off", SettingsCategory.MESSAGES,
            R.string.settings_sms_notifications_off, R.string.settings_sms_notifications_off_text,
            SettingKind.Switch("sms_notifications_off"),
            dangerous = true,
            keywordsRes = R.string.keywords_sms_notifications,
            groupRes = R.string.settings_group_messages
        ),
        /*
         * «Писать SMS в диалере» (sms_in_app) убран в 2.1.0: куда ведёт
         * «Написать», решает роль SMS сама (SmsRole.composeTarget) — правило
         * автора «пользователь не должен выбирать, чтобы всё работало».
         */
        SettingEntry(
            "sms_delivery_reports", SettingsCategory.MESSAGES,
            R.string.settings_sms_delivery_reports, R.string.settings_sms_delivery_reports_text,
            SettingKind.Switch("sms_delivery_reports"),
            keywordsRes = R.string.keywords_sms,
            groupRes = R.string.settings_group_messages
        ),
        /*
         * Свайпы раздела «SMS» (2.1.0) — свои, отдельно от журнала
         * (автор, 07.11.2026). По умолчанию вправо «SMS», влево «Удалить».
         */
        /*
         * 2.2.0: отправка и переписка — отмена отправки, звонки в ленте,
         * автоудаление кодов; папки и резервная копия — действия.
         */
        SettingEntry(
            "sms_undo_send", SettingsCategory.MESSAGES,
            R.string.settings_sms_undo_send, R.string.settings_sms_undo_send_text,
            SettingKind.Choice("sms_undo_send"),
            keywordsRes = R.string.keywords_sms,
            groupRes = R.string.settings_group_messages
        ),
        /*
         * Размер текста SMS (2.8.5, форум: «опцию увеличения шрифта только СМС,
         * без увеличения всего интерфейса»): множитель только для текста сообщений
         * в переписке, поверх размера шрифта приложения.
         */
        SettingEntry(
            "sms_text_scale", SettingsCategory.MESSAGES,
            R.string.settings_sms_text_scale, null,
            SettingKind.Slider("sms_text_scale"),
            keywordsRes = R.string.keywords_accessibility,
            groupRes = R.string.settings_group_messages
        ),
        /*
         * Текст SMS в уведомлениях (2.9.1 — переключатель «Скрывать текст», форум, eXense:
         * «прятать текст смс из всплывающих уведомлений… коды подтверждения могут попасть
         * не на те глаза»). 2.9.11 — выбор из трёх (eXense: «ожидалось, что хоть развернуть
         * можно будет текст»): показывать, скрывать до раскрытия, скрывать всегда.
         * Отправитель виден всегда; разбор — sms/SmsTextMode.kt, MessageNotifications.
         */
        SettingEntry(
            "sms_text_mode", SettingsCategory.MESSAGES,
            R.string.settings_sms_text_mode, R.string.settings_sms_text_mode_text,
            SettingKind.Choice("sms_text_mode"),
            keywordsRes = R.string.keywords_sms_notifications,
            groupRes = R.string.settings_group_messages,
            advanced = true
        ),
        SettingEntry(
            "sms_calls_in_thread", SettingsCategory.MESSAGES,
            R.string.settings_sms_calls_in_thread, R.string.settings_sms_calls_in_thread_text,
            SettingKind.Switch("sms_calls_in_thread"),
            keywordsRes = R.string.keywords_sms,
            groupRes = R.string.settings_group_messages,
            advanced = true
        ),
        SettingEntry(
            "sms_otp_cleanup", SettingsCategory.MESSAGES,
            R.string.settings_sms_otp_cleanup, R.string.settings_sms_otp_cleanup_text,
            SettingKind.Choice("sms_otp_cleanup"),
            keywordsRes = R.string.keywords_sms,
            groupRes = R.string.settings_group_messages,
            advanced = true
        ),
        SettingEntry(
            "sms_folders", SettingsCategory.MESSAGES,
            R.string.settings_sms_folders, R.string.settings_sms_folders_text,
            SettingKind.Action("sms_folders"),
            keywordsRes = R.string.keywords_sms,
            groupRes = R.string.settings_group_messages
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
            SettingKind.Action("spam_overview"),
            advanced = true
        ),
        SettingEntry(
            "block_list", SettingsCategory.SPAM,
            R.string.settings_block_list, null,
            SettingKind.Action("block_list"),
            groupRes = R.string.settings_group_lists
        ),
        SettingEntry(
            "allow_list", SettingsCategory.SPAM,
            R.string.settings_allow_list, R.string.settings_allow_list_text,
            SettingKind.Action("allow_list"),
            groupRes = R.string.settings_group_lists
        ),
        SettingEntry(
            "sys_blocked", SettingsCategory.SPAM,
            R.string.settings_blocked_system, R.string.settings_blocked_system_text,
            SettingKind.SystemLink("blocked_numbers"),
            groupRes = R.string.settings_group_lists,
            advanced = true
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
         * Тихие часы для незнакомых (1.8.0, автор: «Тихие часы для
         * незнакомых»): ночью чужой звонок идёт без звука, повторный
         * за 15 минут — звонит. Разбор — call/QuietHours.kt.
         */
        SettingEntry(
            "quiet_hours", SettingsCategory.SPAM,
            R.string.settings_quiet_hours, R.string.settings_quiet_hours_text,
            SettingKind.Choice("quiet_hours"),
            keywordsRes = R.string.keywords_quiet_hours,
            groupRes = R.string.settings_group_block_rules,
            advanced = true
        ),
        /*
         * Правила — для какой SIM (2.9.8, форум, Bublikov34: «разделение настроек
         * спама на разные сим-карты»). Разбор — call/SpamSimScope.kt.
         */
        SettingEntry(
            "spam_sim_scope", SettingsCategory.SPAM,
            R.string.settings_spam_sim_scope, R.string.settings_spam_sim_scope_text,
            SettingKind.Choice("spam_sim_scope"),
            keywordsRes = R.string.keywords_spam_sim_scope,
            groupRes = R.string.settings_group_block_rules
        ),
        /*
         * Свои правила второй карты (2.9.11, форум, Bublikov34: «на личной — запрет
         * не из контактов, на рабочей — только спам»). Виден, только когда выбрано
         * «Свои для каждой SIM» (visibleFor); экран — SpamSim2Screen.
         */
        SettingEntry(
            "spam_sim2", SettingsCategory.SPAM,
            R.string.settings_spam_sim2, R.string.settings_spam_sim2_text,
            SettingKind.Action("spam_sim2"),
            keywordsRes = R.string.keywords_spam_sim2,
            groupRes = R.string.settings_group_block_rules
        ),
        /*
         * Автоблокировки по оценке риска и порога уверенности здесь
         * больше нет: сама оценка убрана — она была догадкой, которую
         * нечем проверить. Разбор в комментарии к SpamPolicy.evaluate.
         */
        /*
         * 2.1.0: без dangerous. Было два вопроса подряд: «Вы уверены?»
         * перед входом на экран и ещё одно — при включении справочника
         * на самом экране. Стало (правило автора 07.11.2026) — только
         * второй: он называет приложение, которому уйдёт номер, а вход
         * на экран сам ничего не включает.
         */
        SettingEntry(
            "external_lookup", SettingsCategory.SPAM,
            R.string.settings_external_lookup, R.string.settings_external_lookup_text,
            SettingKind.Action("external_lookup"),
            groupRes = R.string.settings_group_lookup,
            advanced = true
        ),
        /*
         * Памятка о мошенниках (1.8.0, автор: «Памятка о мошенниках»):
         * долгий разговор с незнакомым номером — одно напоминание
         * о кодах из SMS. Разбор — call/ScamMemo.kt.
         *
         * 2.1.1: из «Звонков» — в «Защиту», своей группой «Во время вызова».
         * В «Звонках» стало бы пятнадцать пунктов («Ответить» слева), а памятка
         * по смыслу — защита от мошенников, рядом с блокировкой.
         */
        SettingEntry(
            "scam_memo", SettingsCategory.SPAM,
            R.string.settings_scam_memo, R.string.settings_scam_memo_text,
            SettingKind.Switch("scam_memo"),
            keywordsRes = R.string.keywords_scam_memo,
            groupRes = R.string.settings_group_during_call,
            advanced = true
        ),

        // ── Данные ─────────────────────────────────────────────────────────
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
        SettingEntry(
            "sms_backup", SettingsCategory.MAINTENANCE,
            R.string.settings_sms_backup, R.string.settings_sms_backup_text,
            SettingKind.Action("sms_backup"),
            keywordsRes = R.string.keywords_sms,
            groupRes = R.string.settings_group_backup
        ),
        SettingEntry(
            "sms_restore", SettingsCategory.MAINTENANCE,
            R.string.settings_sms_restore, R.string.settings_sms_restore_text,
            SettingKind.Action("sms_restore"),
            keywordsRes = R.string.keywords_sms,
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
            groupRes = R.string.settings_group_backup,
            advanced = true
        ),
        SettingEntry(
            "file_logging", SettingsCategory.MAINTENANCE,
            R.string.settings_file_logging, R.string.settings_file_logging_text,
            SettingKind.Switch("file_logging"),
            groupRes = R.string.settings_group_diagnostics,
            advanced = true
        ),
        SettingEntry(
            "log_save", SettingsCategory.MAINTENANCE,
            R.string.settings_log_save, R.string.settings_log_save_text,
            SettingKind.Action("log_save"),
            groupRes = R.string.settings_group_diagnostics,
            advanced = true
        ),
        SettingEntry(
            "log_share", SettingsCategory.MAINTENANCE,
            R.string.settings_log_share, null,
            SettingKind.Action("log_share"),
            groupRes = R.string.settings_group_diagnostics,
            advanced = true
        ),
        SettingEntry(
            "log_clear", SettingsCategory.MAINTENANCE,
            R.string.settings_log_clear, null,
            SettingKind.Action("log_clear"),
            groupRes = R.string.settings_group_diagnostics,
            advanced = true
        ),
        SettingEntry(
            "reset", SettingsCategory.MAINTENANCE,
            R.string.settings_reset, R.string.settings_reset_text,
            SettingKind.Action("reset"),
            dangerous = true,
            groupRes = R.string.settings_group_reset,
            advanced = true
        ),

        // ── Настройки Android ──────────────────────────────────────────────
        // 1.3.5: отзыв в Google Play — «Написать в WhatsApp тем, кого нет в контактах»
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
        SettingEntry(
            "battery", SettingsCategory.SYSTEM,
            R.string.settings_battery, R.string.settings_battery_text,
            SettingKind.Action("battery")
        ),
        // 1.8.1, проверка прошивок: разрешения прошивки для экрана входящего
        // (Xiaomi, vivo, OPPO…) и полноэкранные уведомления Android 14+ —
        // разбор в IncomingCallAccess
        SettingEntry(
            "incoming_screen", SettingsCategory.SYSTEM,
            R.string.incoming_access_title, R.string.incoming_access_text,
            SettingKind.Action("incoming_screen")
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

        // ── О приложении ───────────────────────────────────────────────────
        SettingEntry(
            "privacy", SettingsCategory.ABOUT,
            R.string.settings_privacy_policy, null, SettingKind.Action("privacy")
        ),
        SettingEntry(
            "terms", SettingsCategory.ABOUT,
            R.string.settings_terms, null, SettingKind.Action("terms")
        ),
        // 1.5.7: код открыт по лицензии Apache 2.0
        SettingEntry(
            "license", SettingsCategory.ABOUT,
            R.string.settings_license, R.string.about_license_hint,
            SettingKind.Action("license")
        ),
        SettingEntry(
            "source_code", SettingsCategory.ABOUT,
            R.string.settings_source_code, R.string.settings_source_code_text,
            SettingKind.Action("source_code")
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
         * за обновлением и отзывом. 2.1.0: только у установленного
         * из Google Play; иначе — «О приложении» (InstallSource).
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
         *
         * 2.1.0: пункт виден только у установленного из Google Play
         * (shown ниже). Было: без магазина нажатие отвечало «Google Play
         * недоступен» — ссылка, которая никуда не ведёт.
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

    /**
     * Пункты экрана раздела. У «Расширенных» — все пункты с advanced,
     * по порядку разделов (сортировка устойчивая: внутри раздела — порядок
     * реестра); у остальных — основные пункты своего раздела.
     */
    fun byCategory(category: SettingsCategory): List<SettingEntry> =
        if (category == SettingsCategory.ADVANCED) {
            entries.filter { it.advanced }.sortedBy { it.category.ordinal }
        } else {
            entries.filter { it.category == category && !it.advanced }
        }

    /** Дополнительные пункты раздела — для ссылки «Расширенные настройки» внизу него. */
    fun advancedOf(category: SettingsCategory): List<SettingEntry> =
        entries.filter { it.advanced && it.category == category }

    /** Экран, на котором пункт виден: свой раздел или «Расширенные настройки». */
    fun screenOf(entry: SettingEntry): SettingsCategory =
        if (entry.advanced) SettingsCategory.ADVANCED else entry.category

    /**
     * Заголовок группы пункта в «Расширенных» — название его раздела.
     * У «Резервной копии» — «Диагностика»: из неё сюда ушли только журнал
     * работы, выгрузка журнала и сброс, а не сама копия.
     */
    @StringRes
    fun advancedTitleRes(category: SettingsCategory): Int =
        if (category == SettingsCategory.MAINTENANCE) R.string.settings_group_diagnostics else category.titleRes

    /**
     * По чему экран раздела режет список на группы с заголовком: в разделе —
     * подгруппа пункта (groupRes), в «Расширенных» — его раздел. Одно правило
     * для экрана и для теста «подгруппы не разорваны».
     */
    @StringRes
    fun groupKey(screen: SettingsCategory, entry: SettingEntry): Int? =
        if (screen == SettingsCategory.ADVANCED) advancedTitleRes(entry.category) else entry.groupRes

    fun byId(id: String): SettingEntry? = entries.firstOrNull { it.id == id }

    /**
     * Пункты, которые есть на этом устройстве (2.1.0): «Оценить» —
     * только у установленного из Google Play (util/InstallSource).
     * Одно правило для раздела и для поиска по настройкам.
     */
    fun shown(list: List<SettingEntry>, fromGooglePlay: Boolean): List<SettingEntry> =
        if (fromGooglePlay) list else list.filter { it.id !in STORE_ONLY }

    /**
     * Пункт зависит от другой настройки (2.9.11): «Правила для SIM 2» — только при
     * «Свои для каждой SIM». Остальные пункты видны всегда.
     */
    fun visibleFor(entry: SettingEntry, state: com.ogneliss.dialer.ui.SettingsState): Boolean = when (entry.id) {
        "spam_sim2" -> state.spamSimScope == com.ogneliss.dialer.call.SpamSimScope.SEPARATE
        else -> true
    }

    /** Пункты, которым без Google Play некуда вести. */
    private val STORE_ONLY = setOf("rate_app")
}
