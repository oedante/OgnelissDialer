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

    /**
     * Пары «значение → подпись» для конкретной настройки. 2.9.15 (техдолг, этап 2б):
     * однотипные — из описания (Choices: варианты по порядку и их подписи, те же,
     * что у строки настройки); здесь — особые.
     */
    val choice = com.ogneliss.dialer.settings.Choices.of(settingKey)
    val options: List<Pair<String, String>> = choice?.let { c ->
        c.optionNames.map { it to stringResource(c.labelResOf(it)) }
    } ?: when (settingKey) {
        "language" -> LocaleManager.options(ctx).map { it.tag to it.nativeName }
        "theme_preset" -> com.ogneliss.dialer.settings.ThemePreset.entries.map {
            it.name to stringResource(SettingsCommands.themePresetLabelRes(it))
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

    /*
     * ПРЕДПРОСМОТР ПРИ ВЫБОРЕ (2.9.12, форум, Bublikov34: «настройки отображения
     * вызовов как-то просматривать при выборе, до применения»). У настроек вида
     * экрана вызова шторка после выбора не закрывается, а внизу — «Посмотреть
     * экран вызова» (FakeCall.preview): выбрал — посмотрел — вернулся в ту же
     * шторку и выбрал другое. Закрывается, как всегда, смахиванием и касанием мимо.
     */
    val previewable = settingKey in CALL_PREVIEW_KEYS
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
                                if (!previewable) onDismiss()
                            }
                        }
                    )
                    if (index < options.lastIndex) SheetDivider()
                }
            }
            if (previewable) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { com.ogneliss.dialer.call.FakeCall.preview(ctx) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = com.ogneliss.dialer.ui.theme.FlowUi.screenPadding, vertical = com.ogneliss.dialer.ui.theme.Space.m)
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.settings_call_preview))
                }
            }
        }
    }
}

/**
 * Настройки вида экрана вызова, у которых в шторке есть «Посмотреть экран вызова»
 * (2.9.12). Свой фон («Свой снимок») — нет: после выбора снимка шторка закрывается сама.
 */
private val CALL_PREVIEW_KEYS = setOf("call_photo_style", "call_photo_size", "call_ambient_style", "answer_mode")

/**
 * Пояснение под вариантом (1.9.0) — как подписи вариантов в «Оформлении»
 * почты: плотность — что меняется, шрифт — откуда он.
 */
@Composable
private fun choiceHint(key: String, value: String): String? =
    // 2.9.15: пояснения вариантов — в описании настройки (Choices)
    com.ogneliss.dialer.settings.Choices.of(key)?.hintResOf(value)?.let { stringResource(it) }

/** Настройки свайпа — журнал и раздел «SMS» (2.1.0). */
internal val SWIPE_KEYS = setOf("swipe_right", "swipe_left", "sms_swipe_right", "sms_swipe_left")
