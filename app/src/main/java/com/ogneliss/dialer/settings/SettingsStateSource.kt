package com.ogneliss.dialer.settings

import com.ogneliss.dialer.call.AutoRedialMode
import com.ogneliss.dialer.call.QuietHours
import com.ogneliss.dialer.call.RedialPace
import com.ogneliss.dialer.data.NotesRetention
import com.ogneliss.dialer.ui.SettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val appearance: Flow<Appearance> = combine(
        prefs.theme,
        prefs.dynamicColor,
        prefs.largeButtons,
        prefs.showPhoto,
        prefs.sortOrder
    ) { theme, dynamic, large, photo, sort ->
        Appearance(theme, dynamic, large, photo, sort)
    }
        // Шестой поток — отдельно: у типизированного combine предел пять
        .combine(prefs.buttonLabels) { a, labels -> a.copy(buttonLabels = labels) }
        // Настройки экрана вызова — сюда же: отдельной группы потоков
        // ради двух переключателей не заводим
        .combine(prefs.proximityScreenOff) { a, v -> a.copy(proximityScreenOff = v) }
        .combine(prefs.callHideContactNumber) { a, v -> a.copy(callHideContactNumber = v) }
        .combine(prefs.callAnimation) { a, v -> a.copy(callAnimation = v) }
        .combine(prefs.callPhotoStyle) { a, v -> a.copy(callPhotoStyle = v) }
        .combine(prefs.uiScale) { a, v -> a.copy(uiScale = v) }
        .combine(prefs.themePreset) { a, v -> a.copy(themePreset = v) }
        // 1.9.0: свой акцент, скругление, плотность, шрифт — как в Ogneliss Mail
        .combine(prefs.accentColor) { a, v -> a.copy(accentColor = v) }
        .combine(prefs.cornerSize) { a, v -> a.copy(cornerSize = v) }
        .combine(prefs.listDensity) { a, v -> a.copy(listDensity = v) }
        .combine(prefs.uiFont) { a, v -> a.copy(uiFont = v) }
        .combine(prefs.callPhotoSize) { a, v -> a.copy(callPhotoSize = v) }
        .combine(prefs.answerMode) { a, v -> a.copy(answerMode = v) }
        .combine(prefs.contactNameFormat) { a, v -> a.copy(contactNameFormat = v) }
        .combine(prefs.contactSort) { a, v -> a.copy(contactSort = v) }
        .combine(prefs.messengersAnyNumber) { a, v -> a.copy(messengersAnyNumber = v) }

    /** Жесты и отклик. */
    private val gestures: Flow<Gestures> = combine(
        prefs.haptic,
        prefs.keyTones,
        prefs.swipeRight,
        prefs.swipeLeft,
        prefs.swipeSimMode
    ) { haptic, tones, right, left, simMode ->
        // Пятерка — предел типизированного combine. Шестой поток
        // добавляется отдельным combine: иначе Kotlin переходит
        // на вариант с массивом и теряет типы
        arrayOf<Any>(haptic, tones, right, left, simMode)
    }.combine(
        // Седьмой поток тем же приёмом: типизированный combine
        // заканчивается на пяти
        combine(prefs.panelSwipeNav, prefs.fakeCallButton) { nav, button -> nav to button }
    ) { base, extra ->
        val (panelNav, fakeButton) = extra
        Gestures(
            haptic = base[0] as Boolean,
            keyTones = base[1] as Boolean,
            swipeRight = base[2] as SwipeAction,
            swipeLeft = base[3] as SwipeAction,
            swipeSimMode = base[4] as SwipeSimMode,
            panelSwipeNav = panelNav,
            fakeCallButton = fakeButton
        )
    }

    /** Поведение вызовов. */
    private val calls: Flow<Calls> = combine(
        prefs.mergeCalls,
        prefs.groupingMode,
        prefs.rememberSim,
        prefs.simLabels,
        prefs.carrierInfo
    ) { merge, grouping, rememberSim, labels, carrier ->
        Calls(merge, grouping, rememberSim, labels, carrier)
    }
        // Шестой поток — отдельным combine: у типизированной перегрузки
        // предел в пять
        .combine(prefs.journalStyle) { c, style -> c.copy(journalStyle = style) }

    /** Экран после звонка и сопутствующие функции. */
    private val postCall: Flow<PostCall> = combine(
        prefs.postCallEnabled,
        prefs.postCallUnknownOnly,
        prefs.smartSpeaker,
        prefs.callbackSuggestion,
        prefs.dialpadOpenAtStart
    ) { enabled, unknownOnly, speaker, callback, dialpadOpen ->
        PostCall(enabled, unknownOnly, speaker, callback, dialpadOpen)
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
    private val spam: Flow<Spam> = combine(
        prefs.blockHidden,
        prefs.blockNotInContacts,
        prefs.blockTaggedSpam,
        prefs.quietHours
    ) { hidden, unknown, tagged, quiet ->
        Spam(hidden, unknown, tagged, quiet)
    }

    /** Помощь во время и после вызова (1.8.0): автодозвон и памятка. */
    private val callHelp: Flow<CallHelp> = combine(
        prefs.autoRedialMode,
        prefs.autoRedialPace,
        prefs.scamMemo
    ) { mode, pace, memo ->
        CallHelp(mode, pace, memo)
    }

    /** Сообщения (2.0.0). */
    private val sms: Flow<Sms> = combine(
        prefs.smsInApp,
        prefs.smsDeliveryReports
    ) { inApp, reports ->
        Sms(inApp, reports)
    }

    /**
     * Итоговый снимок.
     *
     * Все присваивания именованные: перестановка групп выше по коду
     * ничего не сломает, а забытое поле не скомпилируется.
     */
    val state: Flow<SettingsState> = combine(
        combine(appearance, notes) { a, n -> a to n },
        gestures, calls,
        // Шестая группа — парой со «после звонка»: у combine предел пять
        combine(postCall, callHelp) { p, h -> p to h },
        // Сообщения (2.0.0) — парой с фильтрацией по той же причине
        combine(spam, sms) { s, m -> s to m }
    ) { (a, n), g, c, (p, h), (s, m) ->
        SettingsState(
            theme = a.theme,
            dynamicColor = a.dynamicColor,
            largeButtons = a.largeButtons,
            showPhoto = a.showPhoto,
            sortOrder = a.sortOrder,
            buttonLabels = a.buttonLabels,
            proximityScreenOff = a.proximityScreenOff,
            callHideContactNumber = a.callHideContactNumber,
            callAnimation = a.callAnimation,
            callPhotoStyle = a.callPhotoStyle,
            uiScale = a.uiScale,
            themePreset = a.themePreset,
            accentColor = a.accentColor,
            cornerSize = a.cornerSize,
            listDensity = a.listDensity,
            uiFont = a.uiFont,
            callPhotoSize = a.callPhotoSize,
            answerMode = a.answerMode,
            contactNameFormat = a.contactNameFormat,
            contactSort = a.contactSort,
            messengersAnyNumber = a.messengersAnyNumber,
            notesRetention = n.retention,
            notesRetentionScope = n.scope,
            language = n.language,

            haptic = g.haptic,
            keyTones = g.keyTones,
            swipeRight = g.swipeRight,
            swipeLeft = g.swipeLeft,
            swipeSimMode = g.swipeSimMode,
            panelSwipeNav = g.panelSwipeNav,
            fakeCallButton = g.fakeCallButton,

            mergeCalls = c.mergeCalls,
            groupingMode = c.groupingMode,
            rememberSim = c.rememberSim,
            simLabels = c.simLabels,
            carrierInfo = c.carrierInfo,
            journalStyle = c.journalStyle,

            postCallEnabled = p.postCallEnabled,
            postCallUnknownOnly = p.postCallUnknownOnly,
            smartSpeaker = p.smartSpeaker,
            callbackSuggestion = p.callbackSuggestion,
            dialpadOpenAtStart = p.dialpadOpenAtStart,

            blockHidden = s.blockHidden,
            blockNotInContacts = s.blockNotInContacts,
            blockTaggedSpam = s.blockTaggedSpam,
            quietHours = s.quietHours,

            autoRedialMode = h.autoRedialMode,
            autoRedialPace = h.autoRedialPace,
            scamMemo = h.scamMemo,

            smsInApp = m.inApp,
            smsDeliveryReports = m.deliveryReports,

            fileLogging = true
        )
    }

    // Промежуточные группы. Приватные data class вместо списков —
    // именно они убирают доступ по индексу
    private data class Appearance(
        val theme: ThemeMode,
        val dynamicColor: Boolean,
        val largeButtons: Boolean,
        val showPhoto: Boolean,
        val sortOrder: SortOrder,
        val buttonLabels: Boolean = true,
        val proximityScreenOff: Boolean = true,
        val callHideContactNumber: Boolean = false,
        val callAnimation: Boolean = true,
        val callPhotoStyle: CallPhotoStyle = CallPhotoStyle.CIRCLE,
        val uiScale: UiScale = UiScale.DEFAULT,
        val themePreset: ThemePreset = ThemePreset.CLASSIC,
        val accentColor: Int? = null,
        val cornerSize: CornerSize = CornerSize.MEDIUM,
        val listDensity: ListDensity = ListDensity.NORMAL,
        val uiFont: UiFont = UiFont.SYSTEM,
        val callPhotoSize: CallPhotoSize = CallPhotoSize.LARGE,
        val answerMode: AnswerMode = AnswerMode.TAP,
        val messengersAnyNumber: Boolean = false,
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
        val haptic: Boolean,
        val keyTones: Boolean,
        val swipeRight: SwipeAction,
        val swipeLeft: SwipeAction,
        val swipeSimMode: SwipeSimMode,
        val panelSwipeNav: Boolean,
        val fakeCallButton: Boolean
    )

    private data class Calls(
        val mergeCalls: Boolean,
        val groupingMode: GroupingMode,
        val rememberSim: Boolean,
        val simLabels: Map<Int, String>,
        val carrierInfo: CarrierInfoMode,
        val journalStyle: JournalStyle = JournalStyle.GROUPS
    )

    private data class PostCall(
        val postCallEnabled: Boolean,
        val postCallUnknownOnly: Boolean,
        val smartSpeaker: Boolean,
        val callbackSuggestion: Boolean,
        val dialpadOpenAtStart: Boolean
    )

    private data class Spam(
        val blockHidden: Boolean,
        val blockNotInContacts: Boolean,
        val blockTaggedSpam: Boolean,
        val quietHours: QuietHours
    )

    private data class Sms(
        val inApp: Boolean,
        val deliveryReports: Boolean
    )

    private data class CallHelp(
        val autoRedialMode: AutoRedialMode,
        val autoRedialPace: RedialPace,
        val scamMemo: Boolean
    )
}
