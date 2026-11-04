package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.swipe.RevealActionFactory
import com.ogneliss.dialer.settings.SettingsCommands
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.settings.SwipeSimMode
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.util.LocaleManager

/**
 * ДИАЛОГ ВЫБОРА ЗНАЧЕНИЯ НАСТРОЙКИ.
 *
 * Один диалог на все настройки типа Choice — язык, тема, действия свайпов.
 * Варианты берутся по ключу настройки.
 *
 * Доступность: используется RadioButton с `selectable` на всей строке
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
        "call_photo_size" -> com.ogneliss.dialer.settings.CallPhotoSize.entries.map {
            it.name to stringResource(SettingsCommands.callPhotoSizeLabelRes(it))
        }
        "answer_mode" -> com.ogneliss.dialer.settings.AnswerMode.entries.map {
            it.name to stringResource(SettingsCommands.answerModeLabelRes(it))
        }
        "contact_name_format" -> com.ogneliss.dialer.settings.ContactNameFormat.entries.map {
            it.name to stringResource(SettingsCommands.contactNameFormatLabelRes(it))
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
        "swipe_right", "swipe_left" ->
            // Подписи берутся из единого места: у настроек и панели свайпа
            // они обязаны совпадать
            SwipeAction.entries.map {
                it.key to stringResource(SettingsCommands.swipeActionLabelRes(it))
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
    if (settingKey == "swipe_right" || settingKey == "swipe_left") {
        val isRight = settingKey == "swipe_right"
        val extra by (if (isRight) vm.swipeRightExtra else vm.swipeLeftExtra).collectAsStateWithLifecycle()
        SwipeActionsDialog(
            vm = vm,
            isRight = isRight,
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
     * ОБЩИЙ КОНТЕЙНЕР ВМЕСТО AlertDialog.
     *
     * Отступы AlertDialog (24 dp по краям) не меняются, и рядом
     * с остальным приложением, где расстояния 4–12 dp, окно выглядело
     * чужим. Через это окно проходят выбор языка, темы, действий
     * свайпов и группировки — то есть почти все настройки.
     *
     * ── Ограничение высоты обязательно ─────────────────────────────────────
     * OgnelissDialog высоту не ограничивает: он ровно такой, как его
     * содержимое. У списка языков это больше экрана, и нижние строки
     * вместе с кнопкой «Закрыть» ушли бы за край без возможности
     * прокрутить.
     *
     * 400 dp — примерно две трети экрана телефона: список заметно
     * длиннее кнопки под ним, и понятно, что он прокручивается.
     */
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = vm.choiceTitle(ctx, settingKey),
        content = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                /*
                 * У действий свайпа — значки, те же, что появляются под
                 * строкой журнала при свайпе, в тех же цветах: список
                 * быстрее читается, и выбранное действие потом узнаётся
                 * в журнале с первого взгляда.
                 */
                val swipeIcons = settingKey == "swipe_right" || settingKey == "swipe_left"
                options.forEach { (value, label) ->
                    val visual = if (swipeIcons) {
                        SwipeAction.entries.firstOrNull { it.key == value }
                            ?.let { RevealActionFactory.iconOf(it) }
                    } else null
                    ChoiceRow(
                        selected = value == current,
                        label = label,
                        hint = choiceHint(settingKey, value),
                        // Шрифт — каждый вариант своим шрифтом (как в почте)
                        labelFont = if (settingKey == "ui_font") {
                            com.ogneliss.dialer.ui.theme.uiFontFamily(com.ogneliss.dialer.settings.UiFont.from(value))
                        } else null,
                        icon = visual?.first,
                        iconTint = visual?.second,
                        // У «Ничего» значка нет, но место под него оставлено:
                        // иначе подпись съехала бы левее остальных
                        reserveIconSpace = swipeIcons,
                        onSelect = {
                            vm.setChoice(settingKey, value)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_close)) }
        }
    )
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    reserveIconSpace: Boolean = false,
    /** Пояснение под вариантом (1.9.0: плотность, шрифт). */
    hint: String? = null,
    /** Шрифт подписи — вариант «Шрифта» показан своим шрифтом (1.9.0). */
    labelFont: androidx.compose.ui.text.font.FontFamily? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // onClick = null: нажатие обрабатывает вся строка
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Space.m))
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Space.m))
        } else if (reserveIconSpace) {
            Spacer(Modifier.width(36.dp))
        }
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = labelFont ?: MaterialTheme.typography.bodyLarge.fontFamily))
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = labelFont ?: MaterialTheme.typography.bodyMedium.fontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
