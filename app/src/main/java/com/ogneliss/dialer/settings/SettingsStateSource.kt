package com.ogneliss.dialer.settings

import com.ogneliss.dialer.call.AutoRedialMode
import com.ogneliss.dialer.call.QuietHours
import com.ogneliss.dialer.call.RedialPace
import com.ogneliss.dialer.data.NotesRetention
import com.ogneliss.dialer.ui.SettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * СБОРКА СНИМКА НАСТРОЕК.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ЭТОТ КЛАСС ПОЯВИЛСЯ
 * ══════════════════════════════════════════════════════════════════════════
 * Раньше настройки собирались во ViewModel цепочкой `combine`, где значения
 * передавались списком и разбирались по индексу:
 *
 *     base.copy(keyTones = flags[0], mergeCalls = flags[1])
 *
 * Так писать нельзя. Перестановка одной строки выше по коду молча меняет
 * смысл всех остальных: «тоны клавиш» начинают управлять группировкой
 * вызовов. Типы совпадают, компилятор молчит, тест не факт что поймает —
 * ошибка проявится как «настройка не работает» через недели.
 *
 * Здесь все значения именованные. Перестановка строк ничего не ломает,
 * а пропущенная настройка — ошибка компиляции, потому что `SettingsState`
 * требует все поля.
 *
 * ── Почему отдельный класс, а не метод ViewModel ───────────────────────────
 * ViewModel вырос до 1200 строк и стал непроверяемым: чтобы протестировать
 * сборку настроек, пришлось бы поднимать весь граф зависимостей.
 * Здесь нужен только Prefs.
 *
 * ── Про ограничение combine на пять потоков ────────────────────────────────
 * Стандартный `combine` принимает максимум пять источников. Настроек больше
 * двадцати, поэтому они собираются группами по смыслу, а группы сливаются
 * между собой. Группировка по смыслу, а не по счёту: если добавится
 * настройка оформления, она попадёт в свою группу и соседние не тронет.
 */
@Singleton
class SettingsStateSource @Inject constructor(
    private val prefs: Prefs
) {

    /** Оформление: тема, размеры, показ фото. */
    /*
     * 2.9.14 (техдолг, этап 2): переключатели — не здесь, а одним потоком
     * prefs.switches (описания — SwitchSetting); раньше каждый был своим
     * .combine в этих группах.
     */
    private val appearance: Flow<Appearance> = combine(
        prefs.theme,
        prefs.sortOrder,
        prefs.callPhotoStyle,
        prefs.callAmbientStyle,
        prefs.spamSimScope
    ) { theme, sort, photoStyle, ambient, simScope ->
        Appearance(theme, sort, callPhotoStyle = photoStyle, callAmbientStyle = ambient, spamSimScope = simScope)
    }
        // 2.7.4: свой фон входящих и исходящих
        .combine(prefs.callBgIncoming) { a, v -> a.copy(callBgIncoming = v) }
        .combine(prefs.callBgOutgoing) { a, v -> a.copy(callBgOutgoing = v) }
        // 2.8.0: картинки вместо фото и где их показывать
        .combine(prefs.avatarNoPhoto) { a, v -> a.copy(avatarNoPhoto = v) }
        .combine(prefs.avatarUnknown) { a, v -> a.copy(avatarUnknown = v) }
        .combine(prefs.avatarScope) { a, v -> a.copy(avatarScope = v) }
        .combine(prefs.uiScale) { a, v -> a.copy(uiScale = v) }
        .combine(prefs.fontScale) { a, v -> a.copy(fontScale = v) }
        .combine(prefs.smsTextScale) { a, v -> a.copy(smsTextScale = v) }
        .combine(prefs.themePreset) { a, v -> a.copy(themePreset = v) }
        // 1.9.0: свой акцент, скругление, плотность, шрифт — как в Ogneliss Mail
        .combine(prefs.accentColor) { a, v -> a.copy(accentColor = v) }
        .combine(prefs.cornerSize) { a, v -> a.copy(cornerSize = v) }
        .combine(prefs.listDensity) { a, v -> a.copy(listDensity = v) }
        .combine(prefs.uiFont) { a, v -> a.copy(uiFont = v) }
        .combine(prefs.callPhotoSize) { a, v -> a.copy(callPhotoSize = v) }
        .combine(prefs.answerMode) { a, v -> a.copy(answerMode = v) }
        .combine(prefs.widgetShape) { a, v -> a.copy(widgetShape = v) }
        .combine(prefs.startSection) { a, v -> a.copy(startSection = v) }
        .combine(prefs.contactNameFormat) { a, v -> a.copy(contactNameFormat = v) }
        .combine(prefs.contactSort) { a, v -> a.copy(contactSort = v) }
        .combine(prefs.panelSlots) { a, v -> a.copy(panelSlots = v) }
        .combine(prefs.menuTools) { a, v -> a.copy(menuTools = v) }
        .combine(prefs.railItems) { a, v -> a.copy(railItems = v) }
        .combine(prefs.panelSide) { a, v -> a.copy(panelSide = v) }

    /** Жесты и отклик. */
    /*
     * 2.9.11: без «SIM для действий свайпа» потоков шесть — пять в типизированном
     * combine, шестой — отдельным .combine (на шести Kotlin переходит на массив).
     */
    private val gestures: Flow<Gestures> = combine(
        prefs.swipeRight,
        prefs.swipeLeft
    ) { right, left -> Gestures(swipeRight = right, swipeLeft = left) }

    /** Поведение вызовов. */
    private val calls: Flow<Calls> = combine(
        prefs.groupingMode,
        prefs.simLabels,
        prefs.carrierInfo,
        prefs.journalStyle,
        prefs.outgoingSim
    ) { grouping, labels, carrier, style, sim ->
        Calls(grouping, labels, carrier, style, sim)
    }

    /** Заметки: срок хранения и область очистки. */
    private val notes: Flow<Notes> = combine(
        prefs.notesRetention,
        prefs.notesRetentionScope,
        prefs.language
    ) { retention, scope, language ->
        Notes(retention, scope, language)
    }

    /** Фильтрация вызовов. Тихие часы (1.8.0) — сюда же: это правило входящих. */
    private val spam: Flow<Spam> = prefs.quietHours.map { Spam(it) }

    /** Помощь во время и после вызова (1.8.0): автодозвон и памятка. */
    private val callHelp: Flow<CallHelp> = combine(
        prefs.autoRedialMode,
        prefs.autoRedialPace
    ) { mode, pace -> CallHelp(mode, pace) }

    /** Сообщения (2.0.0). 2.1.0: без «Писать SMS в диалере» — решает роль SMS. */
    private val sms: Flow<Sms> = combine(
        prefs.smsSwipeRight,
        prefs.smsSwipeLeft,
        prefs.smsUndoSend,
        prefs.smsOtpCleanup,
        prefs.smsTextMode
    ) { right, left, undo, otp, mode ->
        Sms(right, left, undo, otp, mode)
    }

    /**
     * Итоговый снимок.
     *
     * Все присваивания именованные: перестановка групп выше по коду
     * ничего не сломает, а забытое поле не скомпилируется.
     */
    val state: Flow<SettingsState> = combine(
        combine(appearance, notes) { a, n -> a to n },
        // Все переключатели одним снимком (2.9.14) — парой с жестами: у combine предел пять
        combine(prefs.switches, gestures) { sw, g -> sw to g },
        calls,
        callHelp,
        // Сообщения (2.0.0) — парой с фильтрацией по той же причине
        combine(spam, sms) { s, m -> s to m }
    ) { (a, n), (sw, g), c, h, (s, m) ->
        SettingsState(
            switches = sw,
            theme = a.theme,
            sortOrder = a.sortOrder,
            callPhotoStyle = a.callPhotoStyle,
            callAmbientStyle = a.callAmbientStyle,
            spamSimScope = a.spamSimScope,
            callBgIncoming = a.callBgIncoming,
            callBgOutgoing = a.callBgOutgoing,
            avatarNoPhoto = a.avatarNoPhoto,
            avatarUnknown = a.avatarUnknown,
            avatarScope = a.avatarScope,
            uiScale = a.uiScale,
            fontScale = a.fontScale,
            smsTextScale = a.smsTextScale,
            themePreset = a.themePreset,
            accentColor = a.accentColor,
            cornerSize = a.cornerSize,
            listDensity = a.listDensity,
            uiFont = a.uiFont,
            callPhotoSize = a.callPhotoSize,
            answerMode = a.answerMode,
            widgetShape = a.widgetShape,
            panelSlots = a.panelSlots,
            menuTools = a.menuTools,
            railItems = a.railItems,
            panelSide = a.panelSide,
            startSection = a.startSection,
            contactNameFormat = a.contactNameFormat,
            contactSort = a.contactSort,
            notesRetention = n.retention,
            notesRetentionScope = n.scope,
            language = n.language,

            swipeRight = g.swipeRight,
            swipeLeft = g.swipeLeft,

            groupingMode = c.groupingMode,
            outgoingSim = c.outgoingSim,
            simLabels = c.simLabels,
            carrierInfo = c.carrierInfo,
            journalStyle = c.journalStyle,

            quietHours = s.quietHours,

            autoRedialMode = h.autoRedialMode,
            autoRedialPace = h.autoRedialPace,

            smsSwipeRight = m.swipeRight,
            smsSwipeLeft = m.swipeLeft,
            smsUndoSend = m.undoSend,
            smsOtpCleanup = m.otpCleanup,
            smsTextMode = m.textMode
        )
    }

    // Промежуточные группы. Приватные data class вместо списков —
    // именно они убирают доступ по индексу
    private data class Appearance(
        val theme: ThemeMode,
        val sortOrder: SortOrder,
        val callPhotoStyle: CallPhotoStyle = CallPhotoStyle.CIRCLE,
        val callAmbientStyle: com.ogneliss.dialer.call.AmbientStyle = com.ogneliss.dialer.call.AmbientStyle.THEME,
        val spamSimScope: com.ogneliss.dialer.call.SpamSimScope = com.ogneliss.dialer.call.SpamSimScope.ALL,
        val callBgIncoming: Long = 0L,
        val callBgOutgoing: Long = 0L,
        val avatarNoPhoto: Long = 0L,
        val avatarUnknown: Long = 0L,
        val avatarScope: AvatarScope = AvatarScope.EVERYWHERE,
        val uiScale: UiScale = UiScale.DEFAULT,
        val fontScale: Float = 1f,
        val smsTextScale: Float = 1f,
        val themePreset: ThemePreset = ThemePreset.CLASSIC,
        val accentColor: Int? = null,
        val cornerSize: CornerSize = CornerSize.MEDIUM,
        val listDensity: ListDensity = ListDensity.NORMAL,
        val uiFont: UiFont = UiFont.SYSTEM,
        val callPhotoSize: CallPhotoSize = CallPhotoSize.LARGE,
        val answerMode: AnswerMode = AnswerMode.TAP,
        val widgetShape: WidgetShape = WidgetShape.AUTO,
        val panelSlots: String? = null,
        val menuTools: String? = null,
        val railItems: String? = null,
        val panelSide: Boolean = false,
        val startSection: StartChoice = StartChoice.JOURNAL,
        val contactNameFormat: ContactNameFormat = ContactNameFormat.GIVEN_FIRST,
        val contactSort: ContactSort = ContactSort.GIVEN
    )

    private data class Notes(
        val retention: NotesRetention.Period,
        val scope: NotesRetention.Scope,
        /** Язык добавлен сюда, а не в оформление: combine берёт максимум пять. */
        val language: String
    )

    private data class Gestures(
        val swipeRight: SwipeAction,
        val swipeLeft: SwipeAction
    )

    private data class Calls(
        val groupingMode: GroupingMode,
        val simLabels: Map<Int, String>,
        val carrierInfo: CarrierInfoMode,
        val journalStyle: JournalStyle = JournalStyle.GROUPS,
        val outgoingSim: OutgoingSim = OutgoingSim.ASK
    )

    private data class Spam(
        val quietHours: QuietHours
    )

    private data class Sms(
        val swipeRight: SwipeAction,
        val swipeLeft: SwipeAction,
        val undoSend: UndoSend,
        val otpCleanup: OtpCleanup = OtpCleanup.OFF,
        val textMode: com.ogneliss.dialer.sms.SmsTextMode = com.ogneliss.dialer.sms.SmsTextMode.SHOW
    )

    private data class CallHelp(
        val autoRedialMode: AutoRedialMode,
        val autoRedialPace: RedialPace
    )
}
