package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.theme.Space

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.launch
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ogneliss.dialer.ui.SettingsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.BuildConfig
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.*
import com.ogneliss.dialer.ui.settings.*
import com.ogneliss.dialer.util.AppPermissions
import com.ogneliss.dialer.util.InstallSource

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    vm: DialerViewModel,
    onBack: () -> Unit,
    /*
     * Поля для коротких сообщений (snackbar) больше нет (2.1.0): выгрузки
     * из раздела заканчиваются молча — успех видно по файлу, неудача
     * уходит в журнал разработчика (правило автора 07.11.2026).
     */
    /** Стрелка «назад»: разбор у ScreenScaffold.showBack. */
    showBack: Boolean = true,
    /** Настройка, найденная поиском: показать её (1.5.8). */
    reveal: SettingReveal? = null,
    /** Просьба исполнена — снять её, чтобы не сработала при следующем входе. */
    onRevealConsumed: () -> Unit = {},
    onAction: (String) -> Unit
) {
    val ctx = LocalContext.current
    // Без Google Play «Оценить» не показывается (2.1.0): разбор у SettingsRegistry.shown
    val entries = remember(category) {
        SettingsRegistry.shown(SettingsRegistry.byCategory(category), InstallSource.isGooglePlay(ctx))
    }

    /*
     * «Работа в фоне» уже разрешена (2.1.0) — так и написано в строке.
     * Было: нажатие отвечало сообщением «Уже разрешено». Состояние
     * перечитывается при возвращении: разрешение выдают в системном окне.
     */
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val hasBattery = remember(entries) { entries.any { it.id == "battery" } }
    var batteryAllowed by remember { mutableStateOf(hasBattery && AppPermissions.isIgnoringBatteryOptimizations(ctx)) }
    DisposableEffect(lifecycle, hasBattery) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (hasBattery && event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                batteryAllowed = AppPermissions.isIgnoringBatteryOptimizations(ctx)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Диалог подтверждения опасной настройки: null — закрыт
    var pendingDangerous by remember { mutableStateOf<SettingEntry?>(null) }
    var openChoice by remember { mutableStateOf<String?>(null) }
    var editingSimLabels by remember { mutableStateOf(false) }

    val state by vm.settingsState.collectAsStateWithLifecycle()

    /*
     * ОТМЕТКИ И «ВЕРНУТЬ КАК БЫЛО» (1.5.8, обзор, У7).
     *
     * Изменённое отмечено точкой (ChangedDot): «изменено» — значит,
     * отличается от SettingsState() — тех же значений, что Prefs отдаёт,
     * пока ничего не выбрано (их совпадение держит DefaultsTest).
     * Внизу раздела — «Вернуть как было»: изменённые пункты этого раздела
     * получают исходные значения; другие разделы, заметки и списки
     * не трогаются. Язык не отмечается и не сбрасывается: это не вкус
     * оформления, а выбор человека, и сбросить его заодно с темой — обидно.
     *
     * Все пункты — на виду (1.8.0, автор: «в настройках не надо прятать
     * пункты, их у нас не так много»). Свёрнутая группа «Дополнительно»
     * (1.5.8–1.7.0) убрана: в самом длинном разделе одиннадцать строк,
     * а спрятанный пункт приходилось искать поиском.
     *
     * Найдено поиском — или кнопкой в «Вопросах и ответах» (1.6.0):
     * раздел прокручивается к пункту и на секунду подсвечивает его.
     */
    val entryRequesters = remember(entries) { entries.associate { it.id to BringIntoViewRequester() } }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    val highlight = remember { Animatable(0f) }
    val highlightScope = rememberCoroutineScope()
    LaunchedEffect(reveal) {
        if (reveal == null) return@LaunchedEffect
        val id = reveal.entryId
        // Кадр — чтобы строки легли на место
        withFrameNanos { }
        entryRequesters[id]?.bringIntoView()
        // Подсветка — своей корутиной: снятая просьба перезапускает этот
        // эффект, и гаснуть на полпути подсветке незачем
        onRevealConsumed()
        highlightScope.launch {
            highlightedId = id
            highlight.snapTo(1f)
            kotlinx.coroutines.delay(REVEAL_HOLD_MS)
            highlight.animateTo(0f, tween(REVEAL_FADE_MS))
            highlightedId = null
        }
    }
    val highlightColor = MaterialTheme.colorScheme.primary
    var confirmReset by remember { mutableStateOf(false) }
    val defaults = remember { SettingsState() }
    // Дополнительные действия свайпа: исходно их нет — есть, значит, изменено
    val rightExtra by vm.swipeRightExtra.collectAsStateWithLifecycle()
    val leftExtra by vm.swipeLeftExtra.collectAsStateWithLifecycle()
    val smsRightExtra by vm.smsSwipeRightExtra.collectAsStateWithLifecycle()
    val smsLeftExtra by vm.smsSwipeLeftExtra.collectAsStateWithLifecycle()
    val isChanged: (SettingEntry) -> Boolean = { entry ->
        when (val k = entry.kind) {
            is SettingKind.Switch -> state.switchValue(k.key) != defaults.switchValue(k.key)
            is SettingKind.Choice -> k.key !in NOT_RESETTABLE && (
                vm.choiceValueOf(k.key, state) != vm.choiceValueOf(k.key, defaults) ||
                    (k.key == "swipe_right" && rightExtra.isNotEmpty()) ||
                    (k.key == "swipe_left" && leftExtra.isNotEmpty()) ||
                    (k.key == "sms_swipe_right" && smsRightExtra.isNotEmpty()) ||
                    (k.key == "sms_swipe_left" && smsLeftExtra.isNotEmpty())
                )
            else -> false
        }
    }
    val changedEntries = entries.filter(isChanged)

    /*
     * Пункты раздела подгруппами — одна вёрстка для пунктов на виду
     * и для «Дополнительно».
     *
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
    val renderEntries: @Composable (List<SettingEntry>) -> Unit = { list ->
        val chunks = buildList<Pair<Int?, List<SettingEntry>>> {
            list.forEach { entry ->
                val last = lastOrNull()
                if (last != null && last.first == entry.groupRes) {
                    this[lastIndex] = last.first to (last.second + entry)
                } else {
                    add(entry.groupRes to listOf(entry))
                }
            }
        }

        chunks.forEach { (groupRes, chunkEntries) ->
            // Зазор между подгруппами — только отступ над заголовком
            // в SettingsGroup (12 dp). Был ещё и этот, 16 dp: вместе 36 —
            // группы разъезжались, и раздел занимал полтора экрана
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
                    when {
                        entry.id == "version" -> stringResource(
                            res,
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                        )
                        // Выдано — «Уже разрешено» вместо пояснения (2.1.0, разбор выше)
                        entry.id == "battery" && batteryAllowed ->
                            stringResource(R.string.settings_battery_already)
                        else -> stringResource(res)
                    }
                }

                // Строка-цель поиска: к ней прокручивают и её подсвечивают
                val lit = entry.id == highlightedId
                Box(
                    Modifier
                        .bringIntoViewRequester(entryRequesters.getValue(entry.id))
                        .drawBehind {
                            if (lit) drawRect(highlightColor.copy(alpha = REVEAL_HIGHLIGHT_ALPHA * highlight.value))
                        }
                ) {
                when (val kind = entry.kind) {
                    is SettingKind.Switch -> {
                        val checked = state.switchValue(kind.key)
                        SettingsSwitchRow(
                            title = title,
                            summary = summary,
                            checked = checked,
                            dangerous = entry.dangerous,
                            changed = isChanged(entry)
                        ) { newValue ->
                            // Подтверждение только при включении опасной настройки
                            if (newValue && entry.dangerous) pendingDangerous = entry
                            else vm.setSwitch(kind.key, newValue)
                        }
                    }

                    is SettingKind.Choice -> SettingsChoiceRow(
                        title = title,
                        currentValue = vm.choiceLabel(ctx, kind.key),
                        changed = isChanged(entry),
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
                } // Box — цель поиска
                if (entryIndex < chunkEntries.lastIndex) {
                    // От начала текста, как в списках (1.5.3)
                    RowDivider(Modifier.padding(start = Space.l))
                }
            }
            }
        }
    }

    ScreenScaffold(
        // Как настройки Ogneliss Mail: серо-голубая страница, группы — карточками (1.5.6)
        tone = com.ogneliss.dialer.ui.theme.PageTone.TINTED,
        title = stringResource(category.titleRes),
        onBack = onBack,
        showBack = showBack,
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
            Spacer(Modifier.height(Space.s))

            renderEntries(entries)

            if (changedEntries.isNotEmpty()) {
                SettingsGroup(title = null) {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_reset_section),
                        summary = stringResource(R.string.settings_reset_section_text, changedEntries.size),
                        onClick = { confirmReset = true }
                    )
                }
            }
            Spacer(Modifier.height(Space.xxl))
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

    // ── «Вернуть как было» — только изменённые пункты этого раздела ────────
    if (confirmReset) {
        OgnelissDialog(
            onDismissRequest = { confirmReset = false },
            title = stringResource(R.string.settings_reset_section),
            content = {
                Text(
                    stringResource(
                        R.string.settings_reset_section_confirm,
                        stringResource(category.titleRes),
                        changedEntries.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    changedEntries.forEach { entry ->
                        when (val k = entry.kind) {
                            is SettingKind.Switch -> vm.setSwitch(k.key, defaults.switchValue(k.key))
                            // Вместе с дополнительными действиями свайпа
                            is SettingKind.Choice -> vm.resetChoice(k.key)
                            else -> Unit
                        }
                    }
                    confirmReset = false
                }) {
                    Text(stringResource(R.string.settings_reset_section_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.nav_cancel))
                }
            }
        )
    }

    if (editingSimLabels) {
        val uiState by vm.state.collectAsStateWithLifecycle()
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
    // «Внешние справочники» — без этого окна с 2.1.0: вопрос задаёт сам экран (SettingsRegistry)
    else -> R.string.confirm_title
}

/** Варианты, которые «Вернуть как было» не трогает: разбор у отметок «изменено» в начале экрана. */
private val NOT_RESETTABLE = setOf("language")

/**
 * ПОКАЗАТЬ НАЙДЕННОЕ ПОИСКОМ (1.5.8).
 *
 * Поиск по настройкам открывает раздел найденной. С «Дополнительно»
 * этого стало мало: редкий пункт лежит в свёрнутой группе внизу, и поиск
 * приводил туда, где найденного не видно. Просьба говорит разделу, что
 * показать: он раскрывает группу и прокручивает к ней.
 *
 * Класс, а не строка: каждая просьба — новый объект (сравнение по
 * ссылке), и та же настройка, найденная второй раз, — снова просьба,
 * даже если группу успели свернуть.
 */
@Stable
class SettingReveal(val entryId: String)


/**
 * Подсветка найденного пункта (1.6.0): держится чуть больше полсекунды
 * и гаснет за девятьсот миллисекунд — хватает, чтобы глаз нашёл строку,
 * и не мешает сразу ею пользоваться. Цвет — primary, 16 %: заметно
 * на белой карточке и на тёмной, текст читается.
 */
private const val REVEAL_HOLD_MS = 600L
private const val REVEAL_FADE_MS = 900
private const val REVEAL_HIGHLIGHT_ALPHA = 0.16f

