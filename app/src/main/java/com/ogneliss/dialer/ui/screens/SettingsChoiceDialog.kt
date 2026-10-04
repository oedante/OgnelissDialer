package com.ogneliss.dialer.ui.screens

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
    reserveIconSpace: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // onClick = null: нажатие обрабатывает вся строка
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
        } else if (reserveIconSpace) {
            Spacer(Modifier.width(36.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
