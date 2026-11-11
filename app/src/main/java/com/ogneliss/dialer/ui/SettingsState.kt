package com.ogneliss.dialer.ui

import androidx.compose.runtime.Immutable

import com.ogneliss.dialer.call.AutoRedialMode
import com.ogneliss.dialer.call.QuietHours
import com.ogneliss.dialer.call.RedialPace
import com.ogneliss.dialer.settings.CarrierInfoMode
import com.ogneliss.dialer.settings.CallPhotoSize
import com.ogneliss.dialer.settings.AnswerMode
import com.ogneliss.dialer.settings.ContactNameFormat
import com.ogneliss.dialer.settings.ContactSort
import com.ogneliss.dialer.settings.CallPhotoStyle
import com.ogneliss.dialer.settings.UiScale
import com.ogneliss.dialer.settings.JournalStyle
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
    /** Подписи под кнопками по всему приложению. */
    val buttonLabels: Boolean = true,
    /** Гасить экран у уха: разбор в Prefs.proximityScreenOff. */
    val proximityScreenOff: Boolean = true,
    /** Тип номера вместо номера у контакта на экране вызова. */
    val callHideContactNumber: Boolean = false,
    /** Живой фон экрана вызова (1.5.1). */
    val callAnimation: Boolean = true,
    /** Масштаб интерфейса (1.5.6). */
    val uiScale: UiScale = UiScale.DEFAULT,
    /** Цветовая тема, как в Ogneliss Mail (1.8.4). */
    val themePreset: com.ogneliss.dialer.settings.ThemePreset = com.ogneliss.dialer.settings.ThemePreset.CLASSIC,
    // 1.9.0: оформление как в Ogneliss Mail
    val accentColor: Int? = null,
    val cornerSize: com.ogneliss.dialer.settings.CornerSize = com.ogneliss.dialer.settings.CornerSize.MEDIUM,
    val listDensity: com.ogneliss.dialer.settings.ListDensity = com.ogneliss.dialer.settings.ListDensity.NORMAL,
    val uiFont: com.ogneliss.dialer.settings.UiFont = com.ogneliss.dialer.settings.UiFont.SYSTEM,
    /** Фото звонящего: на весь экран, круг или квадрат (1.5.5). */
    val callPhotoStyle: CallPhotoStyle = CallPhotoStyle.CIRCLE,
    /** Размер портрета на экране вызова (1.5.7). */
    val callPhotoSize: CallPhotoSize = CallPhotoSize.LARGE,
    /** Ответ на вызов: касанием или сдвигом (1.5.8). */
    val answerMode: AnswerMode = AnswerMode.TAP,
    /** Форма значков «SMS» и «Контакты» на главном экране (2.1.3). */
    val widgetShape: com.ogneliss.dialer.settings.WidgetShape = com.ogneliss.dialer.settings.WidgetShape.AUTO,
    /** Имя контакта: «Имя Фамилия» или «Фамилия Имя» (1.6.2). */
    val contactNameFormat: ContactNameFormat = ContactNameFormat.GIVEN_FIRST,
    /** Порядок списка контактов: по имени или по фамилии (1.6.2). */
    val contactSort: ContactSort = ContactSort.GIVEN,
    /** «Написать»: установленные мессенджеры для любого номера (1.3.5). */
    val messengersAnyNumber: Boolean = false,
    val haptic: Boolean = true,
    val keyTones: Boolean = true,
    val mergeCalls: Boolean = true,
    /** По умолчанию все вызовы абонента одной карточкой — см. Prefs. */
    val groupingMode: GroupingMode = GroupingMode.BY_CONTACT,
    val rememberSim: Boolean = true,
    val smartSpeaker: Boolean = false,
    val dialpadOpenAtStart: Boolean = false,
    val callbackSuggestion: Boolean = true,
    /** Входящий плашкой сверху, когда телефоном пользуются (2.1.0). */
    val incomingBanner: Boolean = true,
    /** «Ответить» слева, «Отклонить» справа (2.1.1). */
    val swapAnswer: Boolean = false,
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
    /** Вид журнала: разбор в JournalStyle. */
    val journalStyle: JournalStyle = JournalStyle.GROUPS,
    /** Круглая кнопка псевдозвонка на экранах журнала и контактов. */
    val fakeCallButton: Boolean = true,
    /** Автодозвон (1.8.0): разбор в call/RedialPolicy.kt. */
    val autoRedialMode: AutoRedialMode = AutoRedialMode.DEFAULT,
    val autoRedialPace: RedialPace = RedialPace.DEFAULT,
    /** Памятка о мошенниках в долгом разговоре с незнакомым (1.8.0). */
    val scamMemo: Boolean = true,
    /** Тихие часы для незнакомых (1.8.0): разбор в call/QuietHours.kt. */
    val quietHours: QuietHours = QuietHours.DEFAULT,
    /** Сообщения (2.0.0): разбор в sms/SmsRole.kt. «Писать SMS в диалере» убран в 2.1.0. */
    val smsDeliveryReports: Boolean = false,
    // 2.2.0
    val smsUndoSend: com.ogneliss.dialer.settings.UndoSend = com.ogneliss.dialer.settings.UndoSend.S5,
    val smsCallsInThread: Boolean = true,
    val smsOtpCleanup: com.ogneliss.dialer.settings.OtpCleanup = com.ogneliss.dialer.settings.OtpCleanup.OFF,
    /** Свайпы раздела «SMS» (2.1.0): свои, по умолчанию «SMS» и «Удалить». */
    val smsSwipeRight: SwipeAction = SwipeAction.SMS,
    val smsSwipeLeft: SwipeAction = SwipeAction.DELETE_FROM_LOG
) {

    /** Значение переключателя по ключу из реестра настроек. */
    fun switchValue(key: String): Boolean = when (key) {
        "dynamic_color" -> dynamicColor
        "large_buttons" -> largeButtons
        "show_photo" -> showPhoto
        "button_labels" -> buttonLabels
        "proximity_screen_off" -> proximityScreenOff
        "call_hide_contact_number" -> callHideContactNumber
        "call_animation" -> callAnimation
        "messengers_any_number" -> messengersAnyNumber
        "panel_swipe_nav" -> panelSwipeNav
        "fake_call_button" -> fakeCallButton
        "haptic" -> haptic
        "key_tones" -> keyTones
        "merge_calls" -> mergeCalls
        "remember_sim" -> rememberSim
        "smart_speaker" -> smartSpeaker
        "dialpad_open_at_start" -> dialpadOpenAtStart
        "callback_suggestion" -> callbackSuggestion
        "incoming_banner" -> incomingBanner
        "swap_answer" -> swapAnswer
        "post_call" -> postCallEnabled
        "post_call_unknown" -> postCallUnknownOnly
        "block_hidden" -> blockHidden
        "block_unknown" -> blockNotInContacts
        "block_tagged" -> blockTaggedSpam
        "file_logging" -> fileLogging
        "scam_memo" -> scamMemo
        "sms_delivery_reports" -> smsDeliveryReports
        "sms_calls_in_thread" -> smsCallsInThread
        else -> false
    }

    companion object {
        /** Ключи всех переключателей — используется тестом на согласованность. */
        val SWITCH_KEYS = setOf(
            "dynamic_color", "large_buttons", "show_photo", "panel_swipe_nav",
            "fake_call_button",
            "haptic", "key_tones",
            "merge_calls", "remember_sim", "smart_speaker",
            "callback_suggestion", "dialpad_open_at_start", "post_call", "post_call_unknown", "incoming_banner", "swap_answer",
            "block_hidden", "block_unknown", "block_tagged", "file_logging",
            // 1.3.3: переключатели обрабатывались (switchValue выше), но в список
            // не попали — SettingsRegistryTest падал ещё с 1.3.0
            "button_labels", "proximity_screen_off", "call_hide_contact_number",
            "messengers_any_number",
            "call_animation",
            // 1.8.0
            "scam_memo",
            // 2.0.0
            "sms_delivery_reports",
            // 2.2.0
            "sms_calls_in_thread"
        )
    }
}
