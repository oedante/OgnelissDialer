package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.QuietHours
import com.ogneliss.dialer.call.SpamPolicy
import com.ogneliss.dialer.settings.SettingsCommands
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.components.SettingsChoiceRow
import com.ogneliss.dialer.ui.components.SettingsGroup
import com.ogneliss.dialer.ui.components.SettingsSwitchRow
import com.ogneliss.dialer.ui.components.SingleChoiceSheet
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import kotlinx.coroutines.launch

/**
 * «ПРАВИЛА ДЛЯ SIM 2» (2.9.11, Защита от спама → «Правила — для карты» →
 * «Свои для каждой SIM»).
 *
 * Форум, Bublikov34: «на личной надо сделать запрет на номера не из контактов,
 * а на рабочей только спам блокировать»; автор: «настройку спама раздельно для
 * каждой симки, в принципе, можно сделать».
 *
 * ── Устройство ─────────────────────────────────────────────────────────────
 * Те же четыре правила, что на экране «Защита от спама», — для вызовов на вторую
 * карту: метки «спам», скрытые номера, номера не из контактов, тихие часы. Правила
 * на главном экране раздела в этом режиме — для SIM 1. Списки (чёрный, белый,
 * системный) — общие: это решение о номере, а не о карте (разбор — SpamSimScope).
 *
 * Пока правило не меняли, оно такое же, как у SIM 1 (Prefs.spamSim2Rules), —
 * включив режим, человек меняет только то, что у второй карты иначе.
 *
 * Вид — как у раздела настроек: серо-голубая страница, правила — группой
 * «Правила блокировки», строки и значки те же, что на экране «Защита от спама»
 * (иначе выглядело бы, будто это другие правила). Пояснений на экране нет —
 * правило автора 07.11.2026; что значит каждое правило — подписи, как в разделе.
 *
 * Отметки «изменено» (точка) и «Вернуть как было» здесь нет: «как было» для
 * второй карты — правила первой, а они на соседнем экране.
 */
@Composable
fun SpamSim2Screen(vm: DialerViewModel, onBack: () -> Unit) {
    val rules by vm.prefs.spamSim2Rules.collectAsStateWithLifecycle(SpamPolicy.Settings())
    val scope = rememberCoroutineScope()
    /** Шторка выбора тихих часов открыта. */
    var choosingQuiet by remember { mutableStateOf(false) }

    ScreenScaffold(
        tone = com.ogneliss.dialer.ui.theme.PageTone.TINTED,
        title = stringResource(R.string.settings_spam_sim2),
        onBack = onBack,
        pinnedTopBar = true
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroup(title = stringResource(R.string.settings_group_block_rules)) {
                // Порядок и подписи — как в SettingsRegistry (раздел SPAM)
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_block_tagged),
                    summary = stringResource(R.string.settings_block_tagged_text),
                    checked = rules.blockTaggedSpam,
                    icon = OgnelissIcons.Report,
                    onChange = { v -> scope.launch { vm.prefs.setSpamSim2BlockTagged(v) } }
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_block_hidden),
                    summary = null,
                    checked = rules.blockHidden,
                    icon = OgnelissIcons.PhoneDisabled,
                    onChange = { v -> scope.launch { vm.prefs.setSpamSim2BlockHidden(v) } }
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_block_unknown),
                    summary = stringResource(R.string.settings_block_unknown_text),
                    checked = rules.blockNotInContacts,
                    icon = OgnelissIcons.PersonOff,
                    onChange = { v -> scope.launch { vm.prefs.setSpamSim2BlockNotInContacts(v) } }
                )
                SettingsChoiceRow(
                    title = stringResource(R.string.settings_quiet_hours),
                    currentValue = stringResource(SettingsCommands.quietHoursLabelRes(rules.quietHours)),
                    icon = OgnelissIcons.Schedule,
                    onClick = { choosingQuiet = true }
                )
            }
        }
    }

    // Выбор — шторкой снизу, как у остальных настроек с вариантами (2.7.0)
    if (choosingQuiet) {
        SingleChoiceSheet(
            options = QuietHours.entries.map { it to SettingsCommands.quietHoursLabelRes(it) },
            current = rules.quietHours,
            onSelect = { v ->
                scope.launch { vm.prefs.setSpamSim2QuietHours(v) }
                choosingQuiet = false
            },
            onDismiss = { choosingQuiet = false },
            titleRes = R.string.settings_quiet_hours
        )
    }
}
