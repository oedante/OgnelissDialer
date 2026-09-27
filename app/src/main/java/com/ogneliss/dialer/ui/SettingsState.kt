package com.ogneliss.dialer.ui

import androidx.compose.runtime.Immutable

import com.ogneliss.dialer.settings.CarrierInfoMode
import com.ogneliss.dialer.settings.GroupingMode
import com.ogneliss.dialer.data.NotesRetention
import com.ogneliss.dialer.settings.SortOrder
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.settings.SwipeSimMode
import com.ogneliss.dialer.settings.ThemeMode

/**
 * СНИМОК ЗНАЧЕНИЙ НАСТРОЕК ДЛЯ UI.
 *
 * Экран категории рисует настройки по реестру и не знает про конкретные
 * поля Prefs. Чтобы не тащить в него десяток отдельных Flow, все значения
 * собираются в один объект, а доступ к переключателям идёт по ключу.
 *
 * Ключи здесь ДОЛЖНЫ совпадать с ключами в SettingsRegistry.
 * Несовпадение приведёт к тому, что переключатель всегда будет выключен —
 * поэтому набор ключей покрыт unit-тестом.
 */
/**
 * Помечено @Immutable: без этого Compose считает состояние
 * изменившимся при каждом обновлении и перерисовывает всё,
 * что от него зависит, — из-за полей типа List и Map.
 */
@Immutable
data class SettingsState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val largeButtons: Boolean = false,
    val showPhoto: Boolean = true,
    val haptic: Boolean = true,
    val keyTones: Boolean = true,
    val mergeCalls: Boolean = true,
    /** По умолчанию все вызовы абонента одной карточкой — см. Prefs. */
    val groupingMode: GroupingMode = GroupingMode.BY_CONTACT,
    val rememberSim: Boolean = true,
    val smartSpeaker: Boolean = false,
    val dialpadOpenAtStart: Boolean = false,
    val callbackSuggestion: Boolean = true,
    val simLabels: Map<Int, String> = emptyMap(),
    /** Экран после звонка выключен по умолчанию — см. пояснение в Prefs. */
    val postCallEnabled: Boolean = false,
    val postCallUnknownOnly: Boolean = true,
    val blockHidden: Boolean = false,
    val blockNotInContacts: Boolean = false,
    val blockTaggedSpam: Boolean = true,
    val fileLogging: Boolean = true,
    val swipeRight: SwipeAction = SwipeAction.CALL,
    val swipeLeft: SwipeAction = SwipeAction.SMS,
    val sortOrder: SortOrder = SortOrder.RELEVANCE,
    val notesRetention: NotesRetention.Period = NotesRetention.Period.NEVER,
    val notesRetentionScope: NotesRetention.Scope = NotesRetention.Scope.UNKNOWN_ONLY,
    val language: String = "",
    /** Какой SIM действовать при свайпе; по умолчанию спрашиваем. */
    val swipeSimMode: SwipeSimMode = SwipeSimMode.ASK,
    /** Перелистывание между вызовами и контактами свайпом по панели. */
    val panelSwipeNav: Boolean = true,
    /**
     * Где показывать оператора и регион из реестра нумерации.
     *
     * По умолчанию только у незнакомых: у контакта с именем эта строка
     * ничего не добавляет, а у номера без имени она единственная,
     * за что можно зацепиться.
     */
    val carrierInfo: CarrierInfoMode = CarrierInfoMode.UNKNOWN_ONLY,
    /** Круглая кнопка псевдозвонка на экранах журнала и контактов. */
    val fakeCallButton: Boolean = true
) {

    /** Значение переключателя по ключу из реестра настроек. */
    fun switchValue(key: String): Boolean = when (key) {
        "dynamic_color" -> dynamicColor
        "large_buttons" -> largeButtons
        "show_photo" -> showPhoto
        "panel_swipe_nav" -> panelSwipeNav
        "fake_call_button" -> fakeCallButton
        "haptic" -> haptic
        "key_tones" -> keyTones
        "merge_calls" -> mergeCalls
        "remember_sim" -> rememberSim
        "smart_speaker" -> smartSpeaker
        "dialpad_open_at_start" -> dialpadOpenAtStart
        "callback_suggestion" -> callbackSuggestion
        "post_call" -> postCallEnabled
        "post_call_unknown" -> postCallUnknownOnly
        "block_hidden" -> blockHidden
        "block_unknown" -> blockNotInContacts
        "block_tagged" -> blockTaggedSpam
        "file_logging" -> fileLogging
        else -> false
    }

    companion object {
        /** Ключи всех переключателей — используется тестом на согласованность. */
        val SWITCH_KEYS = setOf(
            "dynamic_color", "large_buttons", "show_photo", "panel_swipe_nav",
            "fake_call_button",
            "haptic", "key_tones",
            "merge_calls", "remember_sim", "smart_speaker",
            "callback_suggestion", "dialpad_open_at_start", "post_call", "post_call_unknown",
            "block_hidden", "block_unknown", "block_tagged", "file_logging"
        )
    }
}
