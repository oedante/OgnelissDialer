package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.BuildConfig
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.*
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.settings.*

/**
 * ЭКРАН ОДНОЙ КАТЕГОРИИ НАСТРОЕК.
 *
 * Не содержит собственной вёрстки под каждую настройку: берёт список
 * из реестра и рисует по типу элемента. Добавление настройки в реестр
 * автоматически добавляет её сюда.
 *
 * ── Обработка опасных настроек ─────────────────────────────────────────────
 * Настройки с dangerous = true при ВКЛЮЧЕНИИ показывают диалог
 * с конкретным объяснением последствий. Выключение подтверждения
 * не требует — возврат к безопасному состоянию не нуждается в защите.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    vm: DialerViewModel,
    onBack: () -> Unit,
    /**
     * Поле для коротких сообщений.
     *
     * Нужно действиям, которые пишут файлы: сохранение заканчивается
     * молча, и без сообщения непонятно, получилось ли. Хозяин поля —
     * тот, кто запускает запись, поэтому оно приходит снаружи.
     */
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (String) -> Unit
) {
    val ctx = LocalContext.current
    val entries = remember(category) { SettingsRegistry.byCategory(category) }

    // Диалог подтверждения опасной настройки: null — закрыт
    var pendingDangerous by remember { mutableStateOf<SettingEntry?>(null) }
    var openChoice by remember { mutableStateOf<String?>(null) }
    var editingSimLabels by remember { mutableStateOf(false) }

    val state by vm.settingsState.collectAsState()

    ScreenScaffold(
        title = stringResource(category.titleRes),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        /*
         * Панель закреплена.
         *
         * Разделы настроек похожи друг на друга: десяток переключателей
         * с подписями. Заголовок — единственное, что отличает «Вызовы»
         * от «Защиты от спама», и когда он уезжал при прокрутке,
         * человек терял понимание, где находится.
         *
         * В длинных текстах (документы, лицензии) прокручивающаяся
         * панель уместна и осталась.
         */
        pinnedTopBar = true
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            /*
             * ПОДГРУППЫ ВНУТРИ РАЗДЕЛА.
             *
             * Раньше весь раздел шёл одной группой. Когда пунктов стало
             * семь-восемь о разных функциях, настройки одной функции
             * терялись среди соседей. Теперь список режется на части
             * по полю groupRes — подряд идущие пункты с одинаковой
             * подгруппой идут вместе, под общим заголовком.
             *
             * Пункты без подгруппы идут без заголовка — как раньше.
             */
            val chunks = buildList<Pair<Int?, List<SettingEntry>>> {
                entries.forEach { entry ->
                    val last = lastOrNull()
                    if (last != null && last.first == entry.groupRes) {
                        this[lastIndex] = last.first to (last.second + entry)
                    } else {
                        add(entry.groupRes to listOf(entry))
                    }
                }
            }

            chunks.forEachIndexed { chunkIndex, (groupRes, chunkEntries) ->
            if (chunkIndex > 0) Spacer(Modifier.height(16.dp))
            SettingsGroup(title = groupRes?.let { stringResource(it) }) {
            chunkEntries.forEachIndexed { entryIndex, entry ->
                val title = stringResource(entry.titleRes)
                val summary = entry.summaryRes?.let { res ->
                    /*
                     * У пункта «Версия» подпись с подстановкой.
                     *
                     * Номер версии живёт в BuildConfig, а реестр настроек —
                     * это список без кода: подставить значение он не может,
                     * поэтому единственное исключение сделано здесь.
                     */
                    if (entry.id == "version") {
                        stringResource(
                            res,
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                        )
                    } else {
                        stringResource(res)
                    }
                }

                when (val kind = entry.kind) {
                    is SettingKind.Switch -> {
                        val checked = state.switchValue(kind.key)
                        SettingsSwitchRow(
                            title = title,
                            summary = summary,
                            checked = checked,
                            dangerous = entry.dangerous
                        ) { newValue ->
                            // Подтверждение только при включении опасной настройки
                            if (newValue && entry.dangerous) pendingDangerous = entry
                            else vm.setSwitch(kind.key, newValue)
                        }
                    }

                    is SettingKind.Choice -> SettingsChoiceRow(
                        title = title,
                        currentValue = vm.choiceLabel(ctx, kind.key),
                        onClick = { openChoice = kind.key }
                    )

                    is SettingKind.Action -> SettingsActionRow(
                        title = title,
                        summary = summary,
                        dangerous = entry.dangerous,
                        onClick = {
                            when {
                                entry.dangerous -> pendingDangerous = entry
                                kind.key == "sim_labels" -> editingSimLabels = true
                                else -> onAction(kind.key)
                            }
                        }
                    )

                    is SettingKind.SystemLink -> SettingsActionRow(
                        title = title,
                        summary = summary ?: stringResource(R.string.settings_system_settings),
                        onClick = { onAction("system:${kind.key}") }
                    )
                }
                if (entryIndex < chunkEntries.lastIndex) {
                    OgnelissDivider()
                }
            }
            }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Подтверждение опасной настройки ────────────────────────────────────
    pendingDangerous?.let { entry ->
        DangerousSettingDialog(
            title = stringResource(R.string.confirm_title),
            explanation = stringResource(dangerExplanationRes(entry.id)),
            onConfirm = {
                when (val k = entry.kind) {
                    is SettingKind.Switch -> vm.setSwitch(k.key, true)
                    is SettingKind.Action -> onAction(k.key)
                    else -> Unit
                }
            },
            onDismiss = { pendingDangerous = null }
        )
    }

    if (editingSimLabels) {
        val uiState by vm.state.collectAsState()
        SimLabelsDialog(
            sims = uiState.sims,
            currentLabels = state.simLabels,
            onSave = { slot, label -> vm.setSimLabel(slot, label) },
            onDismiss = { editingSimLabels = false }
        )
    }

    // ── Диалог выбора значения ─────────────────────────────────────────────
    openChoice?.let { key ->
        SettingsChoiceDialog(
            settingKey = key,
            vm = vm,
            onDismiss = { openChoice = null }
        )
    }
}

/**
 * Текст предупреждения для конкретной опасной настройки.
 * Общее «вы уверены?» бесполезно — пользователь должен понять,
 * что именно сломается.
 */
private fun dangerExplanationRes(entryId: String): Int = when (entryId) {
    "block_unknown" -> R.string.confirm_block_unknown
    "reset" -> R.string.confirm_reset
    "external_lookup" -> R.string.confirm_external_lookup
    else -> R.string.confirm_title
}
