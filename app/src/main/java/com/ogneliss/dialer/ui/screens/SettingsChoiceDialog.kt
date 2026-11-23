package com.ogneliss.dialer.ui.screens


import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.SettingsCommands
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.settings.SwipeSimMode
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.ChoiceSheet
import com.ogneliss.dialer.ui.components.ListSheetGroup
import com.ogneliss.dialer.ui.components.SheetChoiceRow
import com.ogneliss.dialer.ui.components.SheetDivider
import com.ogneliss.dialer.util.LocaleManager

/**
 * ВЫБОР ЗНАЧЕНИЯ НАСТРОЙКИ — С 2.7.0 ШТОРКА СНИЗУ (имя прежнее, чтобы
 * не трогать вызовы).
 *
 * Было окно по центру с кружками, стало шторка выбора (ChoiceSheet): обзор
 * вида 15.11.2026, владелец: «шторка снизу не всегда удобна; где используется
 * выбор по долгому нажатию и всплывающая форма (журнал, контакты, SMS) —
 * оставить, остальное поменять на шторку». Настройки — «остальное».
 * Значки у вариантов действий свайпа убраны вместе с ветвью: свайпы всегда
 * уходили в SwipeActionsDialog раньше, и значки в этом списке не показывались.
 *
 * Одна шторка на все настройки типа Choice — язык, тема, действия свайпов.
 * Варианты берутся по ключу настройки.
 *
 * Доступность: строка SheetChoiceRow — RadioButton с `selectable` на всей строке
 * и role = RadioButton. TalkBack тогда сообщает «выбрано» / «не выбрано»
 * и позволяет переключаться свайпами, а не только точным попаданием
 * в кружок диаметром 20 dp.
 */
@Composable
fun SettingsChoiceDialog(
    settingKey: String,
    vm: DialerViewModel,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current

    /** Пары «значение → подпись» для конкретной настройки. */
    val options: List<Pair<String, String>> = when (settingKey) {
        "language" -> LocaleManager.options(ctx).map { it.tag to it.nativeName }
        "theme" -> listOf(
            ThemeMode.SYSTEM.name to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT.name to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK.name to stringResource(R.string.settings_theme_dark),
            ThemeMode.BLACK_AMOLED.name to stringResource(R.string.settings_theme_amoled),
            // Режим для слабого зрения: без него настройка существовала
            // в коде, но выбрать её было нельзя
            ThemeMode.HIGH_CONTRAST.name to stringResource(R.string.settings_theme_contrast)
        )
        "outgoing_sim" -> com.ogneliss.dialer.settings.OutgoingSim.entries.map {
            it.name to stringResource(com.ogneliss.dialer.settings.SettingsCommands.outgoingSimLabelRes(it))
        }
        "swipe_sim" -> listOf(
            SwipeSimMode.ASK.name to stringResource(R.string.swipe_sim_ask),
            SwipeSimMode.FIRST.name to stringResource(R.string.swipe_sim_first),
            SwipeSimMode.SECOND.name to stringResource(R.string.swipe_sim_second)
        )
        "notes_retention" -> listOf(
            "NEVER" to stringResource(R.string.retention_never),
            "MONTH" to stringResource(R.string.retention_month),
            "HALF_YEAR" to stringResource(R.string.retention_half_year),
            "YEAR" to stringResource(R.string.retention_year)
        )
        "notes_retention_scope" -> listOf(
            "UNKNOWN_ONLY" to stringResource(R.string.retention_scope_unknown),
            "ALL" to stringResource(R.string.retention_scope_all)
        )
        "sort_order" -> listOf(
            "RELEVANCE" to stringResource(R.string.sort_relevance),
            "FREQUENCY" to stringResource(R.string.sort_frequency),
            "RECENCY" to stringResource(R.string.sort_recency),
            "ALPHABET" to stringResource(R.string.sort_alphabet)
        )
        "journal_style" -> listOf(
            "CARDS" to stringResource(R.string.journal_style_cards),
            "GROUPS" to stringResource(R.string.journal_style_groups)
        )

        "theme_preset" -> com.ogneliss.dialer.settings.ThemePreset.entries.map {
            it.name to stringResource(SettingsCommands.themePresetLabelRes(it))
        }
        // 1.9.0: как «Оформление» Ogneliss Mail
        "corner_size" -> com.ogneliss.dialer.settings.CornerSize.entries.map {
            it.name to stringResource(SettingsCommands.cornerSizeLabelRes(it))
        }
        "list_density" -> com.ogneliss.dialer.settings.ListDensity.entries.map {
            it.name to stringResource(SettingsCommands.listDensityLabelRes(it))
        }
        "ui_font" -> com.ogneliss.dialer.settings.UiFont.entries.map {
            it.name to stringResource(SettingsCommands.uiFontLabelRes(it))
        }
        "ui_scale" -> com.ogneliss.dialer.settings.UiScale.entries.map {
            it.name to stringResource(SettingsCommands.uiScaleLabelRes(it))
        }
        "call_photo_style" -> com.ogneliss.dialer.settings.CallPhotoStyle.entries.map {
            it.name to stringResource(SettingsCommands.callPhotoStyleLabelRes(it))
        }
        // 2.7.4: фон вызова — обычный или свой снимок (из окна выбора фото)
        "call_bg_incoming", "call_bg_outgoing" -> listOf(
            SettingsCommands.CALL_BG_DEFAULT to stringResource(R.string.call_bg_default),
            SettingsCommands.CALL_BG_CUSTOM to stringResource(R.string.call_bg_custom)
        )
        // 2.8.0: картинки вместо фото — обычное или своя картинка (окно выбора фото)
        "avatar_no_photo", "avatar_unknown" -> listOf(
            SettingsCommands.CALL_BG_DEFAULT to stringResource(SettingsCommands.avatarPictureLabelRes(settingKey, SettingsCommands.CALL_BG_DEFAULT)),
            SettingsCommands.CALL_BG_CUSTOM to stringResource(R.string.avatar_custom)
        )
        "avatar_scope" -> com.ogneliss.dialer.settings.AvatarScope.entries.map {
            it.name to stringResource(SettingsCommands.avatarScopeLabelRes(it))
        }
        "call_photo_size" -> com.ogneliss.dialer.settings.CallPhotoSize.entries.map {
            it.name to stringResource(SettingsCommands.callPhotoSizeLabelRes(it))
        }
        "answer_mode" -> com.ogneliss.dialer.settings.AnswerMode.entries.map {
            it.name to stringResource(SettingsCommands.answerModeLabelRes(it))
        }
        "sms_undo_send" -> com.ogneliss.dialer.settings.UndoSend.entries.map {
            it.name to stringResource(SettingsCommands.undoSendLabelRes(it))
        }
        "sms_otp_cleanup" -> com.ogneliss.dialer.settings.OtpCleanup.entries.map {
            it.name to stringResource(SettingsCommands.otpCleanupLabelRes(it))
        }
        "widget_shape" -> com.ogneliss.dialer.settings.WidgetShape.entries.map {
            it.name to stringResource(SettingsCommands.widgetShapeLabelRes(it))
        }
        "contact_name_format" -> com.ogneliss.dialer.settings.ContactNameFormat.entries.map {
            it.name to stringResource(SettingsCommands.contactNameFormatLabelRes(it))
        }
        // 2.7.0: «При запуске открывать» — журнал, SMS, контакты, последний раздел
        "start_section" -> com.ogneliss.dialer.settings.StartChoice.entries.map {
            it.name to stringResource(SettingsCommands.startChoiceLabelRes(it))
        }
        "contact_sort" -> com.ogneliss.dialer.settings.ContactSort.entries.map {
            it.name to stringResource(SettingsCommands.contactSortLabelRes(it))
        }
        // Автодозвон и тихие часы (1.8.0): подписи — из SettingsCommands,
        // общие со строкой настройки
        "auto_redial" -> com.ogneliss.dialer.call.AutoRedialMode.entries.map {
            it.name to stringResource(SettingsCommands.autoRedialLabelRes(it))
        }
        "auto_redial_pace" -> com.ogneliss.dialer.call.RedialPace.entries.map {
            it.name to stringResource(SettingsCommands.redialPaceLabelRes(it))
        }
        "quiet_hours" -> com.ogneliss.dialer.call.QuietHours.entries.map {
            it.name to stringResource(SettingsCommands.quietHoursLabelRes(it))
        }
        "carrier_info" -> listOf(
            "UNKNOWN_ONLY" to stringResource(R.string.carrier_info_unknown),
            "ALL" to stringResource(R.string.carrier_info_all),
            "HIDDEN" to stringResource(R.string.carrier_info_hidden)
        )
        "grouping" -> listOf(
            "CONSECUTIVE" to stringResource(R.string.grouping_consecutive),
            "BY_CONTACT" to stringResource(R.string.grouping_by_contact),
            "NONE" to stringResource(R.string.grouping_none)
        )
        "swipe_right", "swipe_left", "sms_swipe_right", "sms_swipe_left" ->
            // Подписи берутся из единого места: у настроек и панели свайпа
            // они обязаны совпадать
            SwipeAction.entries.map {
                it.key to stringResource(SettingsCommands.swipeActionLabelRes(it, settingKey.startsWith("sms_")))
            }
        else -> emptyList()
    }

    /*
     * Текущее значение читается через состояние настроек.
     *
     * Раньше здесь стоял простой вызов vm.choiceValue(): Compose
     * не видел зависимости от настроек и не перерисовывал диалог
     * после выбора. Отметка оставалась на прежнем пункте, хотя
     * настройка уже применилась — так вели себя и тема, и язык.
     */
    val settingsSnapshot by vm.settingsState.collectAsStateWithLifecycle()
    val current = remember(settingsSnapshot, settingKey) { vm.choiceValue(settingKey) }

    // Свайпы — своё окно: до трёх действий на сторону (разбор там)
    // 2.1.0: и свайпы раздела «SMS» — тем же окном, со своими ключами
    if (settingKey in SWIPE_KEYS) {
        val extra by vm.swipeExtraOf(settingKey).collectAsStateWithLifecycle()
        SwipeActionsDialog(
            vm = vm,
            settingKey = settingKey,
            primary = SwipeAction.from(current),
            extra = extra,
            onDismiss = onDismiss
        )
        return
    }

    // Цветовая тема и свой акцент — своё окно (1.9.0): плитки тем и кружки
    // акцентов, как «Оформление» Ogneliss Mail (разбор в ColorThemeDialog)
    if (settingKey == "theme_preset") {
        ColorThemeDialog(vm = vm, current = current, onDismiss = onDismiss)
        return
    }

    // Язык — своё окно: 48 вариантов, поиск, два названия (разбор там)
    if (settingKey == "language") {
        LanguagePickerDialog(vm = vm, current = current, onDismiss = onDismiss)
        return
    }

    /*
     * ШТОРКА СНИЗУ ВМЕСТО ОКНА ПО ЦЕНТРУ (2.7.0, обзор вида 15.11.2026).
     *
     * Было — OgnelissDialog с кружками (до этого — AlertDialog, чьи отступы
     * 24 dp выглядели чужими). Стало — шторка выбора, как фильтр контактов
     * и напоминания (SingleChoiceSheet): решение владельца «остальное
     * поменять на шторку». Поведение прежнее: нажатие по варианту применяет
     * его сразу и закрывает шторку; кнопки «Закрыть» нет — шторку смахивают.
     *
     * Высоту ограничивать вручную не нужно: шторка не выше экрана, длинный
     * список прокручивается внутри (weight + verticalScroll), заголовок
     * остаётся на месте. Раньше было heightIn(max = 400.dp) в диалоге.
     */
    /*
     * 2.7.4: «Свой снимок» открывает системное окно выбора фото (разрешений
     * не нужно, как в редакторе контакта). Шторка остаётся под окном, пока
     * не придёт ответ: закрытая раньше, она увела бы с собой и приёмник
     * ответа. Выбрал — сохраняем; отменил — ничего не меняется.
     */
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCallBackground(settingKey, uri)
        onDismiss()
    }

    ChoiceSheet(title = vm.choiceTitle(ctx, settingKey), onDismiss = onDismiss) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            ListSheetGroup {
                options.forEachIndexed { index, (value, label) ->
                    SheetChoiceRow(
                        selected = value == current,
                        label = label,
                        supporting = choiceHint(settingKey, value),
                        // Шрифт — каждый вариант своим шрифтом (как в почте)
                        labelFont = if (settingKey == "ui_font") {
                            com.ogneliss.dialer.ui.theme.uiFontFamily(com.ogneliss.dialer.settings.UiFont.from(value))
                        } else null,
                        onClick = {
                            if (value == SettingsCommands.CALL_BG_CUSTOM) {
                                pickBackground.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                vm.setChoice(settingKey, value)
                                onDismiss()
                            }
                        }
                    )
                    if (index < options.lastIndex) SheetDivider()
                }
            }
        }
    }
}

/**
 * Пояснение под вариантом (1.9.0) — как подписи вариантов в «Оформлении»
 * почты: плотность — что меняется, шрифт — откуда он.
 */
@Composable
private fun choiceHint(key: String, value: String): String? = when (key) {
    "list_density" -> when (value) {
        "COMPACT" -> stringResource(R.string.list_density_compact_hint)
        "COMFORTABLE" -> stringResource(R.string.list_density_comfortable_hint)
        else -> null
    }
    "ui_font" -> when (value) {
        "SYSTEM" -> stringResource(R.string.ui_font_system_hint)
        "ROBOTO" -> stringResource(R.string.ui_font_roboto_hint)
        else -> null
    }
    else -> null
}

/** Настройки свайпа — журнал и раздел «SMS» (2.1.0). */
internal val SWIPE_KEYS = setOf("swipe_right", "swipe_left", "sms_swipe_right", "sms_swipe_left")
