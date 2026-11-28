package com.ogneliss.dialer.ui

import com.ogneliss.dialer.settings.SwitchSetting
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
    /**
     * Все переключатели (2.9.14, техдолг, этап 2): одно описание на каждый —
     * SwitchSetting. Читать — `settings[SwitchSetting.X]`. До 2.9.14 здесь было
     * 34 отдельных поля со своими умолчаниями — вторым экземпляром умолчаний Prefs.
     */
    val switches: Map<SwitchSetting, Boolean> = SwitchSetting.defaults(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Масштаб интерфейса (1.5.6). */
    val uiScale: UiScale = UiScale.DEFAULT,
    /** Размер шрифта (2.2.1): множитель поверх системного. */
    val fontScale: Float = 1f,
    /** Размер текста SMS в переписке (2.8.5), множитель поверх размера шрифта. */
    val smsTextScale: Float = 1f,
    /** Цветовая тема, как в Ogneliss Mail (1.8.4). */
    val themePreset: com.ogneliss.dialer.settings.ThemePreset = com.ogneliss.dialer.settings.ThemePreset.CLASSIC,
    // 1.9.0: оформление как в Ogneliss Mail
    val accentColor: Int? = null,
    val cornerSize: com.ogneliss.dialer.settings.CornerSize = com.ogneliss.dialer.settings.CornerSize.MEDIUM,
    val listDensity: com.ogneliss.dialer.settings.ListDensity = com.ogneliss.dialer.settings.ListDensity.NORMAL,
    val uiFont: com.ogneliss.dialer.settings.UiFont = com.ogneliss.dialer.settings.UiFont.SYSTEM,
    /** Фото звонящего: на весь экран, круг или квадрат (1.5.5). */
    val callPhotoStyle: CallPhotoStyle = CallPhotoStyle.CIRCLE,
    /** Облик живого фона вызова (2.9.5): тоны темы или «Пламя». */
    val callAmbientStyle: com.ogneliss.dialer.call.AmbientStyle = com.ogneliss.dialer.call.AmbientStyle.THEME,
    /** Правила защиты от спама — для какой SIM (2.9.8): разбор в SpamSimScope. */
    val spamSimScope: com.ogneliss.dialer.call.SpamSimScope = com.ogneliss.dialer.call.SpamSimScope.ALL,
    /** Отметки своего фона экрана вызова (2.7.4): время сохранения снимка, 0 — снимка нет. */
    val callBgIncoming: Long = 0L,
    val callBgOutgoing: Long = 0L,
    /** Картинки вместо фото (2.8.0): отметки снимков (0 — нет) и где показывать. */
    val avatarNoPhoto: Long = 0L,
    val avatarUnknown: Long = 0L,
    val avatarScope: com.ogneliss.dialer.settings.AvatarScope = com.ogneliss.dialer.settings.AvatarScope.EVERYWHERE,
    /** Размер портрета на экране вызова (1.5.7). */
    val callPhotoSize: CallPhotoSize = CallPhotoSize.LARGE,
    /** Ответ на вызов: касанием или сдвигом (1.5.8). */
    val answerMode: AnswerMode = AnswerMode.TAP,
    /** Форма значков «SMS» и «Контакты» на главном экране (2.1.3). */
    val widgetShape: com.ogneliss.dialer.settings.WidgetShape = com.ogneliss.dialer.settings.WidgetShape.AUTO,
    /** Места нижней панели (2.9.2, PanelSlots); null — порядок по роли SMS. */
    val panelSlots: String? = null,
    /** «Инструменты» меню (2.9.3): строка FolderOrder; null — всё по порядку MenuTool. */
    val menuTools: String? = null,
    /** Колонка справа (2.9.9): строка FolderOrder; null — всё по порядку RailItem. */
    val railItems: String? = null,
    /** Разделы справа (2.9.3, проба): колонка SideSectionRail вместо нижней панели. */
    val panelSide: Boolean = false,
    /** Что открывать при запуске значком (2.7.0, форум, oleg_p): разбор в StartSection. */
    val startSection: com.ogneliss.dialer.settings.StartChoice = com.ogneliss.dialer.settings.StartChoice.JOURNAL,
    /** Имя контакта: «Имя Фамилия» или «Фамилия Имя» (1.6.2). */
    val contactNameFormat: ContactNameFormat = ContactNameFormat.GIVEN_FIRST,
    /** Порядок списка контактов: по имени или по фамилии (1.6.2). */
    val contactSort: ContactSort = ContactSort.GIVEN,
    /** По умолчанию все вызовы абонента одной карточкой — см. Prefs. */
    val groupingMode: GroupingMode = GroupingMode.BY_CONTACT,
    val simLabels: Map<Int, String> = emptyMap(),
    val swipeRight: SwipeAction = SwipeAction.CALL,
    val swipeLeft: SwipeAction = SwipeAction.SMS,
    val sortOrder: SortOrder = SortOrder.RELEVANCE,
    val notesRetention: NotesRetention.Period = NotesRetention.Period.NEVER,
    val notesRetentionScope: NotesRetention.Scope = NotesRetention.Scope.UNKNOWN_ONLY,
    val language: String = "",
    /** Карта для звонков и SMS (2.9.1): разбор у OutgoingSim. */
    val outgoingSim: com.ogneliss.dialer.settings.OutgoingSim = com.ogneliss.dialer.settings.OutgoingSim.ASK,
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
    /** Автодозвон (1.8.0): разбор в call/RedialPolicy.kt. */
    val autoRedialMode: AutoRedialMode = AutoRedialMode.DEFAULT,
    val autoRedialPace: RedialPace = RedialPace.DEFAULT,
    /** Тихие часы для незнакомых (1.8.0): разбор в call/QuietHours.kt. */
    val quietHours: QuietHours = QuietHours.DEFAULT,
    /** Текст SMS в уведомлениях (2.9.12; в 2.9.1 — переключатель «Скрывать текст»): SmsTextMode. */
    val smsTextMode: com.ogneliss.dialer.sms.SmsTextMode = com.ogneliss.dialer.sms.SmsTextMode.SHOW,
    // 2.2.0
    val smsUndoSend: com.ogneliss.dialer.settings.UndoSend = com.ogneliss.dialer.settings.UndoSend.S5,
    val smsOtpCleanup: com.ogneliss.dialer.settings.OtpCleanup = com.ogneliss.dialer.settings.OtpCleanup.OFF,
    /** Свайпы раздела «SMS» (2.1.0): свои, по умолчанию «SMS» и «Удалить». */
    val smsSwipeRight: SwipeAction = SwipeAction.SMS,
    val smsSwipeLeft: SwipeAction = SwipeAction.DELETE_FROM_LOG
) {

    /**
     * Значение переключателя (2.9.14): из снимка, а если его там нет — умолчание
     * из описания. Описание и умолчание — SwitchSetting.
     */
    operator fun get(setting: SwitchSetting): Boolean = switches[setting] ?: setting.default

    /** Значение переключателя по ключу из реестра настроек; неизвестный ключ — «выкл.». */
    fun switchValue(key: String): Boolean = SwitchSetting.of(key)?.let { get(it) } ?: false

    companion object {
        /** Ключи всех переключателей — выводятся из описаний (2.9.14). */
        val SWITCH_KEYS: Set<String> get() = SwitchSetting.KEYS
    }
}
