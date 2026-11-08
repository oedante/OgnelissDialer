package com.ogneliss.dialer.settings

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.AutoRedialMode
import com.ogneliss.dialer.call.QuietHours
import com.ogneliss.dialer.call.RedialPace
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.data.NotesRetention
import com.ogneliss.dialer.ui.SettingsState
import com.ogneliss.dialer.util.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * КОМАНДЫ ИЗМЕНЕНИЯ НАСТРОЕК.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ВЫНЕСЕНО ИЗ VIEWMODEL
 * ══════════════════════════════════════════════════════════════════════════
 * Экраны настроек строятся из декларативного реестра и обращаются к ним
 * по строковым ключам. Соответствие «ключ → сеттер» — механическая
 * таблица на две сотни строк, которая не имеет отношения ни к журналу
 * вызовов, ни к контактам, ни к чему-либо ещё во ViewModel.
 *
 * Пока она лежала там, ViewModel рос, а таблицу нельзя было проверить
 * отдельно: пришлось бы поднимать весь граф зависимостей ради того,
 * чтобы убедиться, что ключ «large_buttons» доходит до нужного сеттера.
 *
 * ── Про строковые ключи ────────────────────────────────────────────────────
 * Ключи строковые, потому что реестр настроек декларативный, а декларация
 * не может ссылаться на функции. Опечатка в таком ключе не даёт ошибки
 * компиляции — переключатель просто молча не работает.
 *
 * Именно так пять настроек оказались мёртвыми. Защита от этого —
 * тест согласованности реестра и состояния, который сверяет наборы ключей
 * и падает при расхождении.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Singleton
class SettingsCommands @Inject constructor(
    private val prefs: Prefs,
    /**
     * Контекст приложения.
     *
     * Нужен смене языка: системный LocaleManager запрашивается через
     * него. Раньше класс обходился без контекста — подписи настроек
     * получают его параметром, — но смена языка вызывается изнутри,
     * и передавать его туда пришлось бы через всю цепочку.
     *
     * Именно контекст ПРИЛОЖЕНИЯ, а не активности: класс живёт
     * в графе Hilt дольше любого экрана, и ссылка на активность
     * стала бы утечкой.
     */
    @ApplicationContext private val appContext: Context
) {

    /**
     * ЯЗЫК ТОЛЬКО ЧТО ИЗМЕНЁН.
     *
     * Признак для интерфейса: активность нужно пересоздать.
     * setApplicationLocales меняет конфигурацию, но композиция Compose
     * читает строки один раз при первой отрисовке и запоминает —
     * без пересоздания интерфейс остаётся на прежнем языке.
     *
     * Сбрасывается сразу после обработки: иначе пересоздание
     * повторялось бы при каждом возвращении на экран.
     */
    private val _localeChanged = MutableStateFlow(false)
    val localeChanged: StateFlow<Boolean> = _localeChanged

    fun onLocaleChangeHandled() {
        _localeChanged.value = false
    }

    /**
     * Установка переключателя по ключу из реестра.
     *
     * Ветка `else` намеренно логирует, а не молчит: неизвестный ключ
     * означает расхождение реестра и этой таблицы, и такое должно
     * оставлять след, а не проходить незамеченным.
     */
    suspend fun setSwitch(key: String, value: Boolean) {
        when (key) {
            "dynamic_color" -> prefs.setDynamicColor(value)
            "large_buttons" -> prefs.setLargeButtons(value)
            "show_photo" -> prefs.setShowPhoto(value)
            "messengers_any_number" -> prefs.setMessengersAnyNumber(value)
            "button_labels" -> prefs.setButtonLabels(value)
            "proximity_screen_off" -> prefs.setProximityScreenOff(value)
            "call_hide_contact_number" -> prefs.setCallHideContactNumber(value)
            "call_animation" -> prefs.setCallAnimation(value)
            "panel_swipe_nav" -> prefs.setPanelSwipeNav(value)
            "fake_call_button" -> prefs.setFakeCallButton(value)
            "haptic" -> prefs.setHaptic(value)
            "key_tones" -> prefs.setKeyTones(value)
            "merge_calls" -> prefs.setMergeCalls(value)
            "remember_sim" -> prefs.setRememberSim(value)
            "dialpad_open_at_start" -> prefs.setDialpadOpenAtStart(value)
            "smart_speaker" -> prefs.setSmartSpeaker(value)
            "callback_suggestion" -> prefs.setCallbackSuggestion(value)
            "incoming_banner" -> prefs.setIncomingBanner(value)
            "post_call" -> prefs.setPostCallEnabled(value)
            "post_call_unknown" -> prefs.setPostCallUnknownOnly(value)
            "block_hidden" -> prefs.setBlockHidden(value)
            "block_unknown" -> prefs.setBlockNotInContacts(value)
            "block_tagged" -> prefs.setBlockTaggedSpam(value)
            "scam_memo" -> prefs.setScamMemo(value)
            "sms_delivery_reports" -> prefs.setSmsDeliveryReports(value)
            "file_logging" -> {
                prefs.setFileLogging(value)
                Logger.setFileLogging(value)
            }
            else -> Logger.w(TAG, "Неизвестный ключ переключателя: $key")
        }
    }

    /** Установка значения из списка вариантов. */
    suspend fun setChoice(key: String, value: String) {
        when (key) {
            /*
             * Язык сохраняется И применяется.
             *
             * Раньше вызывался только LocaleManager: система язык меняла,
             * но наше состояние об этом не знало, и переключатель
             * оставался на «Как в системе».
             */
            "language" -> {
                prefs.setLanguage(value)
                /*
                 * Системный путь на Android 13+: там язык меняет
                 * LocaleManager, и он же пересоздаёт активность.
                 * Если система взяла это на себя, ручное пересоздание
                 * не нужно — оно только вызвало бы лишнее мигание.
                 */
                val handledBySystem = LocaleManager.applyViaSystem(appContext, value)
                /*
                 * Признак для интерфейса: нужно пересоздать активность.
                 *
                 * Без этого строки остаются на прежнем языке до следующего
                 * запуска: композиция читает ресурсы один раз и запоминает,
                 * а смена конфигурации сама по себе её не перестраивает.
                 */
                _localeChanged.value = !handledBySystem
            }
            "theme" -> prefs.setTheme(
                runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.SYSTEM)
            )
            "swipe_right" -> prefs.setSwipeRight(SwipeAction.from(value))
            "swipe_left" -> prefs.setSwipeLeft(SwipeAction.from(value))
            // 2.1.0: свайпы раздела «SMS» — свои
            "sms_swipe_right" -> prefs.setSmsSwipeRight(SwipeAction.from(value))
            "sms_swipe_left" -> prefs.setSmsSwipeLeft(SwipeAction.from(value))
            "grouping" -> prefs.setGroupingMode(
                runCatching { GroupingMode.valueOf(value) }
                    .getOrDefault(GroupingMode.BY_CONTACT)
            )
            "notes_retention" -> prefs.setNotesRetention(
                NotesRetention.Period.from(value)
            )
            "notes_retention_scope" -> prefs.setNotesRetentionScope(
                NotesRetention.Scope.from(value)
            )
            "swipe_sim" -> prefs.setSwipeSimMode(SwipeSimMode.from(value))
            "carrier_info" -> prefs.setCarrierInfo(CarrierInfoMode.from(value))
            "call_photo_style" -> prefs.setCallPhotoStyle(CallPhotoStyle.from(value))
            "call_photo_size" -> prefs.setCallPhotoSize(CallPhotoSize.from(value))
            "answer_mode" -> prefs.setAnswerMode(AnswerMode.from(value))
            "contact_name_format" -> prefs.setContactNameFormat(ContactNameFormat.from(value))
            "contact_sort" -> prefs.setContactSort(ContactSort.from(value))
            "ui_scale" -> prefs.setUiScale(UiScale.from(value))
            /*
             * Цветовая тема и свой акцент (1.9.0) — одна настройка, как
             * в «Оформлении» почты: значение «ТЕМА» или «ТЕМА|#AARRGGBB»
             * ([themeChoice]). Выбор темы без акцента сбрасывает свой акцент —
             * у темы свой (pickPreset почты). Выбрали тему или акцент при
             * цветах из обоев — обои выключаются: иначе выбор не был бы
             * виден. Возврат к «Классике» без акцента обои не трогает.
             */
            "theme_preset" -> {
                val (preset, accent) = parseThemeChoice(value)
                prefs.setThemePreset(preset)
                prefs.setAccentColor(accent)
                if ((preset != ThemePreset.CLASSIC || accent != null) && prefs.dynamicColor.first()) {
                    prefs.setDynamicColor(false)
                }
            }
            // 1.9.0: скругление, плотность, шрифт — как в Ogneliss Mail
            "corner_size" -> prefs.setCornerSize(CornerSize.from(value))
            "list_density" -> prefs.setListDensity(ListDensity.from(value))
            "ui_font" -> prefs.setUiFont(UiFont.from(value))
            "journal_style" -> prefs.setJournalStyle(JournalStyle.from(value))
            "auto_redial" -> prefs.setAutoRedialMode(AutoRedialMode.from(value))
            "auto_redial_pace" -> prefs.setAutoRedialPace(RedialPace.from(value))
            "quiet_hours" -> prefs.setQuietHours(QuietHours.from(value))
            "sort_order" -> prefs.setSortOrder(
                runCatching { SortOrder.valueOf(value) }.getOrDefault(SortOrder.RELEVANCE)
            )
            else -> Logger.w(TAG, "Неизвестный ключ выбора: $key")
        }
    }

    suspend fun setSimLabel(slotIndex: Int, label: String) =
        prefs.setSimLabel(slotIndex, label.trim())

    suspend fun resetAll() = prefs.resetAll()

    /** Текущее значение варианта — для отметки в диалоге выбора. */
    fun currentChoice(key: String, state: SettingsState): String = when (key) {
        "language" -> state.language
        "theme" -> state.theme.name
        "swipe_right" -> state.swipeRight.key
        "swipe_left" -> state.swipeLeft.key
        "sms_swipe_right" -> state.smsSwipeRight.key
        "sms_swipe_left" -> state.smsSwipeLeft.key
        "grouping" -> state.groupingMode.name
        "sort_order" -> state.sortOrder.name
        "swipe_sim" -> state.swipeSimMode.name
        "carrier_info" -> state.carrierInfo.name
        "call_photo_style" -> state.callPhotoStyle.name
        "call_photo_size" -> state.callPhotoSize.name
        "answer_mode" -> state.answerMode.name
        "contact_name_format" -> state.contactNameFormat.name
        "contact_sort" -> state.contactSort.name
        "ui_scale" -> state.uiScale.name
        "theme_preset" -> themeChoice(state.themePreset, state.accentColor)
        "corner_size" -> state.cornerSize.name
        "list_density" -> state.listDensity.name
        "ui_font" -> state.uiFont.name
        "journal_style" -> state.journalStyle.name
        "notes_retention" -> state.notesRetention.name
        "notes_retention_scope" -> state.notesRetentionScope.name
        "auto_redial" -> state.autoRedialMode.name
        "auto_redial_pace" -> state.autoRedialPace.name
        "quiet_hours" -> state.quietHours.name
        else -> ""
    }

    /**
     * Подпись текущего значения для строки настройки.
     *
     * ── Почему не возвращаем ключ ──────────────────────────────────────────
     * Раньше для свайпов возвращался ключ хранения, и пользователь видел
     * «call» и «sms» вместо «Позвонить» и «Отправить SMS». Ключ существует
     * для базы и никогда не должен попадать на экран.
     */
    fun choiceLabel(context: Context, key: String, state: SettingsState): String = when (key) {
        // Подпись берётся по сохранённому значению, а не по состоянию
        // AppCompatDelegate: иначе она не обновляется вместе с настройкой
        "language" -> LocaleManager.options(context)
            .firstOrNull { it.tag == state.language }?.nativeName
            ?: context.getString(R.string.settings_language_system)

        "theme" -> context.getString(
            when (state.theme) {
                ThemeMode.SYSTEM -> R.string.settings_theme_system
                ThemeMode.LIGHT -> R.string.settings_theme_light
                ThemeMode.DARK -> R.string.settings_theme_dark
                ThemeMode.BLACK_AMOLED -> R.string.settings_theme_amoled
                ThemeMode.HIGH_CONTRAST -> R.string.settings_theme_contrast
            }
        )

        "theme_preset" -> state.accentColor?.let { accent ->
            context.getString(themePresetLabelRes(state.themePreset)) + " · " + accentLabel(context, accent)
        } ?: context.getString(themePresetLabelRes(state.themePreset))
        "corner_size" -> context.getString(cornerSizeLabelRes(state.cornerSize))
        "list_density" -> context.getString(listDensityLabelRes(state.listDensity))
        "ui_font" -> context.getString(uiFontLabelRes(state.uiFont))

        "swipe_right" -> context.getString(swipeActionLabelRes(state.swipeRight))
        "swipe_left" -> context.getString(swipeActionLabelRes(state.swipeLeft))
        "sms_swipe_right" -> context.getString(swipeActionLabelRes(state.smsSwipeRight, forSms = true))
        "sms_swipe_left" -> context.getString(swipeActionLabelRes(state.smsSwipeLeft, forSms = true))

        "grouping" -> context.getString(
            when (state.groupingMode) {
                GroupingMode.CONSECUTIVE -> R.string.grouping_consecutive
                GroupingMode.BY_CONTACT -> R.string.grouping_by_contact
                GroupingMode.NONE -> R.string.grouping_none
            }
        )

        "journal_style" -> context.getString(
            when (state.journalStyle) {
                JournalStyle.CARDS -> R.string.journal_style_cards
                JournalStyle.GROUPS -> R.string.journal_style_groups
            }
        )

        "carrier_info" -> context.getString(
            when (state.carrierInfo) {
                CarrierInfoMode.UNKNOWN_ONLY -> R.string.carrier_info_unknown
                CarrierInfoMode.ALL -> R.string.carrier_info_all
                CarrierInfoMode.HIDDEN -> R.string.carrier_info_hidden
            }
        )

        "call_photo_style" -> context.getString(callPhotoStyleLabelRes(state.callPhotoStyle))
        "call_photo_size" -> context.getString(callPhotoSizeLabelRes(state.callPhotoSize))
        "answer_mode" -> context.getString(answerModeLabelRes(state.answerMode))
        "contact_name_format" -> context.getString(contactNameFormatLabelRes(state.contactNameFormat))
        "contact_sort" -> context.getString(contactSortLabelRes(state.contactSort))
        "auto_redial" -> context.getString(autoRedialLabelRes(state.autoRedialMode))
        "auto_redial_pace" -> context.getString(redialPaceLabelRes(state.autoRedialPace))
        "quiet_hours" -> context.getString(quietHoursLabelRes(state.quietHours))

        // «Авто» — с тем, что выбрано для этого экрана: видно, что оно делает
        "ui_scale" -> if (state.uiScale == UiScale.AUTO) {
            val config = context.resources.configuration
            val percent = Math.round(
                com.ogneliss.dialer.ui.theme.UiScaleMath.autoFactor(
                    config.smallestScreenWidthDp, config.densityDpi
                ) * 100
            )
            context.getString(R.string.ui_scale_auto_now, percent)
        } else {
            context.getString(uiScaleLabelRes(state.uiScale))
        }

        "swipe_sim" -> context.getString(
            when (state.swipeSimMode) {
                SwipeSimMode.ASK -> R.string.swipe_sim_ask
                SwipeSimMode.FIRST -> R.string.swipe_sim_first
                SwipeSimMode.SECOND -> R.string.swipe_sim_second
            }
        )

        "notes_retention" -> context.getString(
            when (state.notesRetention) {
                NotesRetention.Period.NEVER -> R.string.retention_never
                NotesRetention.Period.MONTH -> R.string.retention_month
                NotesRetention.Period.HALF_YEAR -> R.string.retention_half_year
                NotesRetention.Period.YEAR -> R.string.retention_year
            }
        )

        "notes_retention_scope" -> context.getString(
            when (state.notesRetentionScope) {
                NotesRetention.Scope.UNKNOWN_ONLY -> R.string.retention_scope_unknown
                NotesRetention.Scope.ALL -> R.string.retention_scope_all
            }
        )

        "sort_order" -> context.getString(
            when (state.sortOrder) {
                SortOrder.RELEVANCE -> R.string.sort_relevance
                SortOrder.FREQUENCY -> R.string.sort_frequency
                SortOrder.RECENCY -> R.string.sort_recency
                SortOrder.ALPHABET -> R.string.sort_alphabet
            }
        )

        else -> ""
    }

    /** Заголовок диалога выбора. */
    fun choiceTitle(context: Context, key: String): String = context.getString(
        when (key) {
            "language" -> R.string.settings_language
            "theme" -> R.string.settings_theme
            "theme_preset" -> R.string.settings_theme_preset
            "corner_size" -> R.string.settings_corner_size
            "list_density" -> R.string.settings_list_density
            "ui_font" -> R.string.settings_ui_font
            "swipe_right" -> R.string.settings_swipe_right
            "swipe_left" -> R.string.settings_swipe_left
            "sms_swipe_right" -> R.string.settings_sms_swipe_right
            "sms_swipe_left" -> R.string.settings_sms_swipe_left
            "grouping" -> R.string.settings_grouping
            "sort_order" -> R.string.settings_sort_order
            "swipe_sim" -> R.string.settings_swipe_sim
            "carrier_info" -> R.string.settings_carrier_info
            "call_photo_style" -> R.string.settings_call_photo
            "call_photo_size" -> R.string.settings_call_photo_size
            "answer_mode" -> R.string.settings_answer_mode
            "contact_name_format" -> R.string.settings_contact_name_format
            "contact_sort" -> R.string.settings_contact_sort
            "ui_scale" -> R.string.settings_ui_scale
            "journal_style" -> R.string.settings_journal_style
            "notes_retention" -> R.string.settings_notes_retention
            "notes_retention_scope" -> R.string.settings_notes_scope
            "auto_redial" -> R.string.settings_auto_redial
            "auto_redial_pace" -> R.string.settings_auto_redial_pace
            "quiet_hours" -> R.string.settings_quiet_hours
            else -> R.string.nav_settings
        }
    )

    companion object {

        private const val TAG = "Settings"

        /** Подпись цветовой темы — для строки настройки и диалога (1.8.4). */
        fun themePresetLabelRes(preset: ThemePreset): Int = when (preset) {
            ThemePreset.CLASSIC -> R.string.theme_preset_classic
            ThemePreset.SKY -> R.string.theme_preset_sky
            ThemePreset.OCEAN -> R.string.theme_preset_ocean
            ThemePreset.FOREST -> R.string.theme_preset_forest
            ThemePreset.SUNSET -> R.string.theme_preset_sunset
            ThemePreset.GRAPHITE -> R.string.theme_preset_graphite
            ThemePreset.MIDNIGHT -> R.string.theme_preset_midnight
        }

        /** Значение настройки «Цветовая тема» (1.9.0): тема и, если задан, свой акцент. */
        fun themeChoice(preset: ThemePreset, accent: Int?): String =
            // Locale.ROOT: цифры латинские при любом языке телефона
            if (accent == null) preset.name else preset.name + "|#" + String.format(java.util.Locale.ROOT, "%08X", accent)

        /** Обратно: тема и свой акцент (null — акцент темы). Испорченное — «Классика». */
        fun parseThemeChoice(value: String): Pair<ThemePreset, Int?> {
            val preset = ThemePreset.from(value.substringBefore('|'))
            val accent = value.substringAfter('|', "").removePrefix("#")
                .takeIf { it.length == 8 }
                ?.toLongOrNull(16)?.toInt()
            return preset to accent
        }

        /** Названия двенадцати готовых акцентов — в порядке ThemePresetMath.ACCENTS. */
        val ACCENT_NAMES = intArrayOf(
            R.string.accent_blue, R.string.accent_sky, R.string.accent_teal, R.string.accent_green,
            R.string.accent_olive, R.string.accent_amber, R.string.accent_orange, R.string.accent_red,
            R.string.accent_raspberry, R.string.accent_purple, R.string.accent_indigo, R.string.accent_blue_grey
        )

        /** Подпись своего акцента: имя готового или «Свой, #RRGGBB». */
        fun accentLabel(context: Context, accent: Int): String {
            val i = com.ogneliss.dialer.ui.theme.ThemePresetMath.ACCENTS.indexOf(accent)
            return if (i >= 0) context.getString(ACCENT_NAMES[i])
            else context.getString(
                R.string.accent_custom_named,
                String.format(java.util.Locale.ROOT, "#%06X", accent and 0xFFFFFF)
            )
        }

        fun cornerSizeLabelRes(v: CornerSize): Int = when (v) {
            CornerSize.SMALL -> R.string.corner_size_small
            CornerSize.MEDIUM -> R.string.corner_size_medium
            CornerSize.LARGE -> R.string.corner_size_large
        }

        fun listDensityLabelRes(v: ListDensity): Int = when (v) {
            ListDensity.COMPACT -> R.string.list_density_compact
            ListDensity.NORMAL -> R.string.list_density_normal
            ListDensity.COMFORTABLE -> R.string.list_density_comfortable
        }

        fun uiFontLabelRes(v: UiFont): Int = when (v) {
            UiFont.SYSTEM -> R.string.ui_font_system
            UiFont.ROBOTO -> R.string.ui_font_roboto
            UiFont.SERIF -> R.string.ui_font_serif
            UiFont.CONDENSED -> R.string.ui_font_condensed
        }

        /** Подпись варианта масштаба — для строки настройки и диалога. */
        fun uiScaleLabelRes(scale: UiScale): Int = when (scale) {
            UiScale.AUTO -> R.string.ui_scale_auto
            UiScale.S90 -> R.string.ui_scale_90
            UiScale.S100 -> R.string.ui_scale_100
            UiScale.S110 -> R.string.ui_scale_110
            UiScale.S125 -> R.string.ui_scale_125
        }

        /** Подпись варианта фото на экране вызова — для строки настройки и диалога. */
        fun callPhotoStyleLabelRes(style: CallPhotoStyle): Int = when (style) {
            CallPhotoStyle.FULL_SCREEN -> R.string.call_photo_full_screen
            CallPhotoStyle.CIRCLE -> R.string.call_photo_circle
            CallPhotoStyle.SQUARE -> R.string.call_photo_square
        }

        /** Подпись размера портрета на экране вызова (1.5.7). */
        fun callPhotoSizeLabelRes(size: CallPhotoSize): Int = when (size) {
            CallPhotoSize.NORMAL -> R.string.call_photo_size_normal
            CallPhotoSize.LARGE -> R.string.call_photo_size_large
        }

        /** Подпись способа ответа на вызов (1.5.8). */
        fun answerModeLabelRes(mode: AnswerMode): Int = when (mode) {
            AnswerMode.TAP -> R.string.answer_mode_tap
            AnswerMode.SLIDE -> R.string.answer_mode_slide
        }

        /** Подпись формата имени контакта (1.6.2). */
        fun contactNameFormatLabelRes(format: ContactNameFormat): Int = when (format) {
            ContactNameFormat.GIVEN_FIRST -> R.string.contact_name_given_first
            ContactNameFormat.FAMILY_FIRST -> R.string.contact_name_family_first
        }

        /** Подпись режима автодозвона (1.8.0). */
        fun autoRedialLabelRes(mode: AutoRedialMode): Int = when (mode) {
            AutoRedialMode.OFF -> R.string.auto_redial_off
            AutoRedialMode.OFFER -> R.string.auto_redial_offer
            AutoRedialMode.AUTO -> R.string.auto_redial_auto
        }

        /** Подпись числа и частоты попыток автодозвона (1.8.0). */
        fun redialPaceLabelRes(pace: RedialPace): Int = when (pace) {
            RedialPace.P3_10 -> R.string.redial_pace_3_10
            RedialPace.P5_10 -> R.string.redial_pace_5_10
            RedialPace.P5_30 -> R.string.redial_pace_5_30
            RedialPace.P10_60 -> R.string.redial_pace_10_60
        }

        /** Подпись тихих часов для незнакомых (1.8.0). */
        fun quietHoursLabelRes(hours: QuietHours): Int = when (hours) {
            QuietHours.OFF -> R.string.quiet_hours_off
            QuietHours.NIGHT_22_08 -> R.string.quiet_hours_22_08
            QuietHours.NIGHT_23_07 -> R.string.quiet_hours_23_07
            QuietHours.ALWAYS -> R.string.quiet_hours_always
        }

        /** Подпись порядка списка контактов (1.6.2). */
        fun contactSortLabelRes(sort: ContactSort): Int = when (sort) {
            ContactSort.GIVEN -> R.string.contact_sort_given
            ContactSort.FAMILY -> R.string.contact_sort_family
        }
        /**
         * Единственное место сопоставления действия свайпа с подписью.
         * Публично, потому что то же соответствие нужно панели свайпа.
         */
        fun swipeActionLabelRes(action: SwipeAction, forSms: Boolean): Int =
            // В разделе «SMS» журнала нет: «Удалить» там удаляет переписку (2.1.0)
            if (forSms && action == SwipeAction.DELETE_FROM_LOG) R.string.action_delete_thread
            else swipeActionLabelRes(action)

        fun swipeActionLabelRes(action: SwipeAction): Int = when (action) {
            SwipeAction.NONE -> R.string.action_none
            SwipeAction.CALL -> R.string.action_call
            SwipeAction.SMS -> R.string.action_sms
            SwipeAction.WHATSAPP -> R.string.action_whatsapp
            SwipeAction.TELEGRAM -> R.string.action_telegram
            SwipeAction.VIBER -> R.string.action_viber
            SwipeAction.MAX -> R.string.action_max
            SwipeAction.EMAIL -> R.string.action_email
            SwipeAction.DELETE_FROM_LOG -> R.string.action_delete_from_log
            SwipeAction.BLOCK_NUMBER -> R.string.action_block
            SwipeAction.VIEW_CONTACT -> R.string.action_view_contact
            SwipeAction.EDIT_BEFORE_CALL -> R.string.action_edit_before_call
            SwipeAction.COPY_NUMBER -> R.string.action_copy_number
            SwipeAction.SHARE_NUMBER -> R.string.action_share_number
            SwipeAction.REMIND_CALLBACK -> R.string.action_remind
            SwipeAction.ADD_NOTE -> R.string.action_add_note
        }

        /**
         * Ключи, которые обрабатывает setSwitch.
         * Сверяется тестом с реестром настроек: расхождение означает
         * молча неработающий переключатель.
         */
        val SWITCH_KEYS = setOf(
            "dynamic_color", "large_buttons", "show_photo", "button_labels", "proximity_screen_off", "call_hide_contact_number", "call_animation", "messengers_any_number", "panel_swipe_nav",
            "fake_call_button",
            "haptic", "key_tones",
            "merge_calls", "remember_sim", "smart_speaker",
            "callback_suggestion", "post_call", "post_call_unknown", "incoming_banner",
            "block_hidden", "block_unknown", "block_tagged", "file_logging",
            "dialpad_open_at_start",
            // 1.8.0
            "scam_memo",
            // 2.0.0
            "sms_delivery_reports"
        )

        /** Ключи, которые обрабатывает setChoice. */
        val CHOICE_KEYS = setOf(
            "language", "theme", "swipe_right", "swipe_left", "grouping", "sort_order",
            // 2.1.0
            "sms_swipe_right", "sms_swipe_left",
            "notes_retention", "notes_retention_scope", "swipe_sim", "carrier_info",
            "call_photo_style", "call_photo_size", "answer_mode", "ui_scale",
            "contact_name_format", "contact_sort",
            // 1.8.4
            "theme_preset",
            // 1.9.0
            "corner_size", "list_density", "ui_font",
            // 1.8.0
            "auto_redial", "auto_redial_pace", "quiet_hours"
            // "journal_style" — снят из реестра в 1.5.3 (вид журнала один)
        )
    }
}
