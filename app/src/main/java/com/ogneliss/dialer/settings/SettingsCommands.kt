package com.ogneliss.dialer.settings

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.ogneliss.dialer.R
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.data.NotesRetention
import com.ogneliss.dialer.ui.SettingsState
import com.ogneliss.dialer.util.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            "panel_swipe_nav" -> prefs.setPanelSwipeNav(value)
            "fake_call_button" -> prefs.setFakeCallButton(value)
            "haptic" -> prefs.setHaptic(value)
            "key_tones" -> prefs.setKeyTones(value)
            "merge_calls" -> prefs.setMergeCalls(value)
            "remember_sim" -> prefs.setRememberSim(value)
            "dialpad_open_at_start" -> prefs.setDialpadOpenAtStart(value)
            "smart_speaker" -> prefs.setSmartSpeaker(value)
            "callback_suggestion" -> prefs.setCallbackSuggestion(value)
            "post_call" -> prefs.setPostCallEnabled(value)
            "post_call_unknown" -> prefs.setPostCallUnknownOnly(value)
            "block_hidden" -> prefs.setBlockHidden(value)
            "block_unknown" -> prefs.setBlockNotInContacts(value)
            "block_tagged" -> prefs.setBlockTaggedSpam(value)
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
        "grouping" -> state.groupingMode.name
        "sort_order" -> state.sortOrder.name
        "swipe_sim" -> state.swipeSimMode.name
        "carrier_info" -> state.carrierInfo.name
        "notes_retention" -> state.notesRetention.name
        "notes_retention_scope" -> state.notesRetentionScope.name
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

        "swipe_right" -> context.getString(swipeActionLabelRes(state.swipeRight))
        "swipe_left" -> context.getString(swipeActionLabelRes(state.swipeLeft))

        "grouping" -> context.getString(
            when (state.groupingMode) {
                GroupingMode.CONSECUTIVE -> R.string.grouping_consecutive
                GroupingMode.BY_CONTACT -> R.string.grouping_by_contact
                GroupingMode.NONE -> R.string.grouping_none
            }
        )

        "carrier_info" -> context.getString(
            when (state.carrierInfo) {
                CarrierInfoMode.UNKNOWN_ONLY -> R.string.carrier_info_unknown
                CarrierInfoMode.ALL -> R.string.carrier_info_all
                CarrierInfoMode.HIDDEN -> R.string.carrier_info_hidden
            }
        )

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
            "swipe_right" -> R.string.settings_swipe_right
            "swipe_left" -> R.string.settings_swipe_left
            "grouping" -> R.string.settings_grouping
            "sort_order" -> R.string.settings_sort_order
            "swipe_sim" -> R.string.settings_swipe_sim
            "carrier_info" -> R.string.settings_carrier_info
            "notes_retention" -> R.string.settings_notes_retention
            "notes_retention_scope" -> R.string.settings_notes_scope
            else -> R.string.nav_settings
        }
    )

    companion object {

        private const val TAG = "Settings"
        /**
         * Единственное место сопоставления действия свайпа с подписью.
         * Публично, потому что то же соответствие нужно панели свайпа.
         */
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
            "dynamic_color", "large_buttons", "show_photo", "panel_swipe_nav",
            "fake_call_button",
            "haptic", "key_tones",
            "merge_calls", "remember_sim", "smart_speaker",
            "callback_suggestion", "post_call", "post_call_unknown",
            "block_hidden", "block_unknown", "block_tagged", "file_logging",
            "dialpad_open_at_start"
        )

        /** Ключи, которые обрабатывает setChoice. */
        val CHOICE_KEYS = setOf(
            "language", "theme", "swipe_right", "swipe_left", "grouping", "sort_order",
            "notes_retention", "notes_retention_scope", "swipe_sim", "carrier_info"
        )
    }
}
