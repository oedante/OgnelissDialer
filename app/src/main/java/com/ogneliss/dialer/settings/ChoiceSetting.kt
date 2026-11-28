package com.ogneliss.dialer.settings

import android.content.Context
import androidx.annotation.StringRes
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.AmbientStyle
import com.ogneliss.dialer.call.AutoRedialMode
import com.ogneliss.dialer.call.QuietHours
import com.ogneliss.dialer.call.RedialPace
import com.ogneliss.dialer.call.SpamSimScope
import com.ogneliss.dialer.data.NotesRetention
import com.ogneliss.dialer.sms.SmsTextMode
import com.ogneliss.dialer.ui.SettingsState

/**
 * НАСТРОЙКА С ВЫБОРОМ — ОДНО ОПИСАНИЕ (2.9.15, техдолг, этап 2б).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Однотипная настройка с выбором (значение — перечисление, хранится именем)
 * была разнесена по шести веткам: setChoice, currentChoice, choiceLabel,
 * choiceTitle и список CHOICE_KEYS в SettingsCommands, плюс список вариантов
 * и подсказки в SettingsChoiceDialog. Порядок вариантов в окне и подписи
 * строки настройки жили отдельно и могли разойтись.
 *
 * Теперь — описание здесь: ключ, заголовок, варианты по порядку, подпись и
 * подсказка варианта, где лежит значение в снимке и как его записать.
 * SettingsCommands и окно выбора берут всё из него.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧЕГО ЗДЕСЬ НЕТ
 * ══════════════════════════════════════════════════════════════════════════
 * • Хранение и поле снимка — по-прежнему свои у каждой настройки (Prefs,
 *   SettingsState): значения типизированные, их читают десятки экранов, и
 *   хранятся они по-разному (карта для звонков — номером слота, текст SMS —
 *   двумя ключами и копией для приёмника SMS). Описание лишь указывает на них.
 * • Особые выборы — язык (смена локали), цветовая тема с акцентом, действия
 *   свайпов (своя шторка), свои фоны вызова и картинки (выбор файла): их логика
 *   в SettingsCommands как раньше.
 */
class ChoiceSetting<E : Enum<E>>(
    /** Ключ пункта в реестре настроек. */
    val key: String,
    /** Заголовок шторки выбора. */
    @StringRes val titleRes: Int,
    /** Варианты — в порядке показа. */
    private val options: List<E>,
    private val labelRes: (E) -> Int,
    private val current: (SettingsState) -> E,
    private val parse: (String) -> E,
    private val write: suspend (Prefs, E) -> Unit,
    /** Пояснение под вариантом в шторке; null — без пояснения. */
    private val hintRes: (E) -> Int? = { null },
    /** Что сделать после записи (виджеты на главном экране). */
    private val afterWrite: ((Context) -> Unit)? = null
) {
    /** Имена вариантов в порядке показа — это и сохраняемые значения. */
    val optionNames: List<String> get() = options.map { it.name }

    /** Подпись варианта по имени. */
    @StringRes
    fun labelResOf(name: String): Int = labelRes(parse(name))

    /** Пояснение под вариантом; null — его нет. */
    @StringRes
    fun hintResOf(name: String): Int? = options.firstOrNull { it.name == name }?.let(hintRes)

    /** Выбранное сейчас — имя варианта. */
    fun currentName(state: SettingsState): String = current(state).name

    /** Подпись выбранного сейчас — для строки настройки. */
    @StringRes
    fun currentLabelRes(state: SettingsState): Int = labelRes(current(state))

    /** Записать выбор из шторки (имя варианта; незнакомое — умолчание разбора). */
    suspend fun set(prefs: Prefs, appContext: Context, name: String) {
        write(prefs, parse(name))
        afterWrite?.invoke(appContext)
    }
}

/**
 * Все однотипные настройки с выбором. Порядок списка ничего не значит:
 * порядок на экране задаёт реестр, порядок вариантов — [ChoiceSetting.options].
 */
object Choices {

    // ── Оформление ──────────────────────────────────────────────────────────
    val THEME = ChoiceSetting(
        "theme", R.string.settings_theme,
        listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.BLACK_AMOLED, ThemeMode.HIGH_CONTRAST),
        labelRes = {
            when (it) {
                ThemeMode.SYSTEM -> R.string.settings_theme_system
                ThemeMode.LIGHT -> R.string.settings_theme_light
                ThemeMode.DARK -> R.string.settings_theme_dark
                ThemeMode.BLACK_AMOLED -> R.string.settings_theme_amoled
                // Режим для слабого зрения
                ThemeMode.HIGH_CONTRAST -> R.string.settings_theme_contrast
            }
        },
        current = { it.theme },
        parse = { v -> runCatching { ThemeMode.valueOf(v) }.getOrDefault(ThemeMode.SYSTEM) },
        write = { p, v -> p.setTheme(v) }
    )
    // 1.9.0: скругление, плотность, шрифт — как в Ogneliss Mail
    val CORNER_SIZE = ChoiceSetting(
        "corner_size", R.string.settings_corner_size, CornerSize.entries,
        labelRes = SettingsCommands::cornerSizeLabelRes, current = { it.cornerSize },
        parse = CornerSize::from, write = { p, v -> p.setCornerSize(v) }
    )
    val LIST_DENSITY = ChoiceSetting(
        "list_density", R.string.settings_list_density, ListDensity.entries,
        labelRes = SettingsCommands::listDensityLabelRes, current = { it.listDensity },
        parse = ListDensity::from, write = { p, v -> p.setListDensity(v) },
        hintRes = {
            when (it) {
                ListDensity.COMPACT -> R.string.list_density_compact_hint
                ListDensity.COMFORTABLE -> R.string.list_density_comfortable_hint
                else -> null
            }
        }
    )
    val UI_FONT = ChoiceSetting(
        "ui_font", R.string.settings_ui_font, UiFont.entries,
        labelRes = SettingsCommands::uiFontLabelRes, current = { it.uiFont },
        parse = UiFont::from, write = { p, v -> p.setUiFont(v) },
        hintRes = {
            when (it) {
                UiFont.SYSTEM -> R.string.ui_font_system_hint
                UiFont.ROBOTO -> R.string.ui_font_roboto_hint
                else -> null
            }
        }
    )
    val UI_SCALE = ChoiceSetting(
        "ui_scale", R.string.settings_ui_scale, UiScale.entries,
        labelRes = SettingsCommands::uiScaleLabelRes, current = { it.uiScale },
        parse = UiScale::from, write = { p, v -> p.setUiScale(v) }
    )
    val CONTACT_NAME_FORMAT = ChoiceSetting(
        "contact_name_format", R.string.settings_contact_name_format, ContactNameFormat.entries,
        labelRes = SettingsCommands::contactNameFormatLabelRes, current = { it.contactNameFormat },
        parse = ContactNameFormat::from, write = { p, v -> p.setContactNameFormat(v) }
    )
    val CONTACT_SORT = ChoiceSetting(
        "contact_sort", R.string.settings_contact_sort, ContactSort.entries,
        labelRes = SettingsCommands::contactSortLabelRes, current = { it.contactSort },
        parse = ContactSort::from, write = { p, v -> p.setContactSort(v) }
    )
    /** Форма значков «SMS» и «Контакты» (2.1.3): значки перерисовываются сразу. */
    val WIDGET_SHAPE = ChoiceSetting(
        "widget_shape", R.string.settings_widget_shape, WidgetShape.entries,
        labelRes = SettingsCommands::widgetShapeLabelRes, current = { it.widgetShape },
        parse = WidgetShape::from, write = { p, v -> p.setWidgetShape(v) },
        afterWrite = { c ->
            com.ogneliss.dialer.widget.SectionWidgets.updateAll(c)
            com.ogneliss.dialer.widget.PinnedWidget.update(c)
        }
    )
    /** Что открывать при запуске значком (2.7.0). */
    val START_SECTION = ChoiceSetting(
        "start_section", R.string.settings_start_section, StartChoice.entries,
        labelRes = SettingsCommands::startChoiceLabelRes, current = { it.startSection },
        parse = StartChoice::from, write = { p, v -> p.setStartSection(v) }
    )

    // ── Журнал ──────────────────────────────────────────────────────────────
    val GROUPING = ChoiceSetting(
        "grouping", R.string.settings_grouping,
        listOf(GroupingMode.CONSECUTIVE, GroupingMode.BY_CONTACT, GroupingMode.NONE),
        labelRes = {
            when (it) {
                GroupingMode.CONSECUTIVE -> R.string.grouping_consecutive
                GroupingMode.BY_CONTACT -> R.string.grouping_by_contact
                GroupingMode.NONE -> R.string.grouping_none
            }
        },
        current = { it.groupingMode },
        parse = { v -> runCatching { GroupingMode.valueOf(v) }.getOrDefault(GroupingMode.BY_CONTACT) },
        write = { p, v -> p.setGroupingMode(v) }
    )
    val SORT_ORDER = ChoiceSetting(
        "sort_order", R.string.settings_sort_order,
        listOf(SortOrder.RELEVANCE, SortOrder.FREQUENCY, SortOrder.RECENCY, SortOrder.ALPHABET),
        labelRes = {
            when (it) {
                SortOrder.RELEVANCE -> R.string.sort_relevance
                SortOrder.FREQUENCY -> R.string.sort_frequency
                SortOrder.RECENCY -> R.string.sort_recency
                SortOrder.ALPHABET -> R.string.sort_alphabet
            }
        },
        current = { it.sortOrder },
        parse = { v -> runCatching { SortOrder.valueOf(v) }.getOrDefault(SortOrder.RELEVANCE) },
        write = { p, v -> p.setSortOrder(v) }
    )
    val CARRIER_INFO = ChoiceSetting(
        "carrier_info", R.string.settings_carrier_info,
        listOf(CarrierInfoMode.UNKNOWN_ONLY, CarrierInfoMode.ALL, CarrierInfoMode.HIDDEN),
        labelRes = {
            when (it) {
                CarrierInfoMode.UNKNOWN_ONLY -> R.string.carrier_info_unknown
                CarrierInfoMode.ALL -> R.string.carrier_info_all
                CarrierInfoMode.HIDDEN -> R.string.carrier_info_hidden
            }
        },
        current = { it.carrierInfo },
        parse = CarrierInfoMode::from, write = { p, v -> p.setCarrierInfo(v) }
    )
    val NOTES_RETENTION = ChoiceSetting(
        "notes_retention", R.string.settings_notes_retention,
        listOf(
            NotesRetention.Period.NEVER, NotesRetention.Period.MONTH,
            NotesRetention.Period.HALF_YEAR, NotesRetention.Period.YEAR
        ),
        labelRes = {
            when (it) {
                NotesRetention.Period.NEVER -> R.string.retention_never
                NotesRetention.Period.MONTH -> R.string.retention_month
                NotesRetention.Period.HALF_YEAR -> R.string.retention_half_year
                NotesRetention.Period.YEAR -> R.string.retention_year
            }
        },
        current = { it.notesRetention },
        parse = NotesRetention.Period::from, write = { p, v -> p.setNotesRetention(v) }
    )
    val NOTES_RETENTION_SCOPE = ChoiceSetting(
        "notes_retention_scope", R.string.settings_notes_scope,
        listOf(NotesRetention.Scope.UNKNOWN_ONLY, NotesRetention.Scope.ALL),
        labelRes = {
            when (it) {
                NotesRetention.Scope.UNKNOWN_ONLY -> R.string.retention_scope_unknown
                NotesRetention.Scope.ALL -> R.string.retention_scope_all
            }
        },
        current = { it.notesRetentionScope },
        parse = NotesRetention.Scope::from, write = { p, v -> p.setNotesRetentionScope(v) }
    )

    // ── SIM и вызовы ────────────────────────────────────────────────────────
    /** Карта для звонков и SMS (2.9.1): хранится номером слота — разбор у OutgoingSim. */
    val OUTGOING_SIM = ChoiceSetting(
        "outgoing_sim", R.string.settings_outgoing_sim, OutgoingSim.entries,
        labelRes = SettingsCommands::outgoingSimLabelRes, current = { it.outgoingSim },
        parse = OutgoingSim::from, write = { p, v -> p.setOutgoingSim(v) }
    )
    val AUTO_REDIAL = ChoiceSetting(
        "auto_redial", R.string.settings_auto_redial, AutoRedialMode.entries,
        labelRes = SettingsCommands::autoRedialLabelRes, current = { it.autoRedialMode },
        parse = AutoRedialMode::from, write = { p, v -> p.setAutoRedialMode(v) }
    )
    val AUTO_REDIAL_PACE = ChoiceSetting(
        "auto_redial_pace", R.string.settings_auto_redial_pace, RedialPace.entries,
        labelRes = SettingsCommands::redialPaceLabelRes, current = { it.autoRedialPace },
        parse = RedialPace::from, write = { p, v -> p.setAutoRedialPace(v) }
    )

    // ── Экран вызова ────────────────────────────────────────────────────────
    val CALL_PHOTO_STYLE = ChoiceSetting(
        "call_photo_style", R.string.settings_call_photo, CallPhotoStyle.entries,
        labelRes = SettingsCommands::callPhotoStyleLabelRes, current = { it.callPhotoStyle },
        parse = CallPhotoStyle::from, write = { p, v -> p.setCallPhotoStyle(v) }
    )
    val CALL_PHOTO_SIZE = ChoiceSetting(
        "call_photo_size", R.string.settings_call_photo_size, CallPhotoSize.entries,
        labelRes = SettingsCommands::callPhotoSizeLabelRes, current = { it.callPhotoSize },
        parse = CallPhotoSize::from, write = { p, v -> p.setCallPhotoSize(v) }
    )
    /** Облик живого фона (2.9.5): тоны темы или «Пламя». */
    val CALL_AMBIENT_STYLE = ChoiceSetting(
        "call_ambient_style", R.string.settings_call_ambient_style, AmbientStyle.entries,
        labelRes = SettingsCommands::ambientStyleLabelRes, current = { it.callAmbientStyle },
        parse = { v -> AmbientStyle.entries.firstOrNull { it.name == v } ?: AmbientStyle.THEME },
        write = { p, v -> p.setCallAmbientStyle(v) }
    )
    val ANSWER_MODE = ChoiceSetting(
        "answer_mode", R.string.settings_answer_mode, AnswerMode.entries,
        labelRes = SettingsCommands::answerModeLabelRes, current = { it.answerMode },
        parse = AnswerMode::from, write = { p, v -> p.setAnswerMode(v) }
    )
    /** Где показывать картинки вместо фото (2.8.0). */
    val AVATAR_SCOPE = ChoiceSetting(
        "avatar_scope", R.string.settings_avatar_scope, AvatarScope.entries,
        labelRes = SettingsCommands::avatarScopeLabelRes, current = { it.avatarScope },
        parse = AvatarScope::from, write = { p, v -> p.setAvatarScope(v) }
    )

    // ── Защита от спама ─────────────────────────────────────────────────────
    /** Правила — для всех карт или свои для каждой (2.9.8): разбор в SpamSimScope. */
    val SPAM_SIM_SCOPE = ChoiceSetting(
        "spam_sim_scope", R.string.settings_spam_sim_scope, SpamSimScope.entries,
        labelRes = SettingsCommands::spamSimScopeLabelRes, current = { it.spamSimScope },
        parse = SpamSimScope::from, write = { p, v -> p.setSpamSimScope(v) }
    )
    val QUIET_HOURS = ChoiceSetting(
        "quiet_hours", R.string.settings_quiet_hours, QuietHours.entries,
        labelRes = SettingsCommands::quietHoursLabelRes, current = { it.quietHours },
        parse = QuietHours::from, write = { p, v -> p.setQuietHours(v) }
    )

    // ── Сообщения ───────────────────────────────────────────────────────────
    val SMS_UNDO_SEND = ChoiceSetting(
        "sms_undo_send", R.string.settings_sms_undo_send, UndoSend.entries,
        labelRes = SettingsCommands::undoSendLabelRes, current = { it.smsUndoSend },
        parse = UndoSend::from, write = { p, v -> p.setSmsUndoSend(v) }
    )
    val SMS_OTP_CLEANUP = ChoiceSetting(
        "sms_otp_cleanup", R.string.settings_sms_otp_cleanup, OtpCleanup.entries,
        labelRes = SettingsCommands::otpCleanupLabelRes, current = { it.smsOtpCleanup },
        parse = OtpCleanup::from, write = { p, v -> p.setSmsOtpCleanup(v) }
    )
    /** Текст SMS в уведомлениях (2.9.12): хранится с копией для приёмника SMS — разбор в Prefs. */
    val SMS_TEXT_MODE = ChoiceSetting(
        "sms_text_mode", R.string.settings_sms_text_mode, SmsTextMode.entries,
        labelRes = SettingsCommands::smsTextModeLabelRes, current = { it.smsTextMode },
        parse = { v -> SmsTextMode.parse(v) ?: SmsTextMode.SHOW },
        write = { p, v -> p.setSmsTextMode(v) },
        hintRes = {
            when (it) {
                SmsTextMode.UNTIL_EXPANDED -> R.string.sms_text_until_expanded_hint
                SmsTextMode.HIDE -> R.string.sms_text_hide_hint
                else -> null
            }
        }
    )

    /** Все описания. */
    val ALL: List<ChoiceSetting<*>> = listOf(
        THEME, CORNER_SIZE, LIST_DENSITY, UI_FONT, UI_SCALE, CONTACT_NAME_FORMAT, CONTACT_SORT,
        WIDGET_SHAPE, START_SECTION, GROUPING, SORT_ORDER, CARRIER_INFO, NOTES_RETENTION,
        NOTES_RETENTION_SCOPE, OUTGOING_SIM, AUTO_REDIAL, AUTO_REDIAL_PACE, CALL_PHOTO_STYLE,
        CALL_PHOTO_SIZE, CALL_AMBIENT_STYLE, ANSWER_MODE, AVATAR_SCOPE, SPAM_SIM_SCOPE, QUIET_HOURS,
        SMS_UNDO_SEND, SMS_OTP_CLEANUP, SMS_TEXT_MODE
    )

    private val byKey = ALL.associateBy { it.key }

    /** Описание по ключу реестра; null — выбор особый или такого нет. */
    fun of(key: String): ChoiceSetting<*>? = byKey[key]

    val KEYS: Set<String> = byKey.keys
}
