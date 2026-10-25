package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import com.ogneliss.dialer.ui.components.GroupPosition
import com.ogneliss.dialer.ui.components.GroupedRow
import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ReadableWidth
import com.ogneliss.dialer.ui.components.SettingsCategoryCard
import com.ogneliss.dialer.ui.components.SettingsGroup
import com.ogneliss.dialer.ui.settings.SettingEntry
import com.ogneliss.dialer.ui.settings.SettingsCategory
import com.ogneliss.dialer.ui.settings.SettingsRegistry
import com.ogneliss.dialer.ui.help.Faq

/**
 * ГЛАВНЫЙ ЭКРАН НАСТРОЕК.
 *
 * Показывает семь категорий вместо прежнего плоского списка из 20+ пунктов.
 * Каждая категория снабжена подписью с перечислением того, что внутри —
 * без этого пользователю приходится заходить в каждую наугад.
 *
 * Поиск работает по названиям, описаниям И дополнительным ключевым словам
 * (см. keywordsRes в реестре). Это важно: человек ищет «тёмная тема»,
 * а настройка называется просто «Тема».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    onOpenEntry: (SettingEntry) -> Unit,
    /**
     * «Вопросы и ответы» (1.6.0): строкой внизу — весь экран (null),
     * из поиска — сразу найденный вопрос.
     */
    onOpenFaq: (String?) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    /*
     * ПОИСКОВЫЙ ИНДЕКС.
     *
     * Строки берутся через stringResource, а не через LocalContext:
     * первый — часть системы состояния Compose и вызывает перерисовку
     * при смене языка, второй читает ресурсы в обход неё, и после
     * переключения языка индекс остался бы на прежнем.
     *
     * Собираем список заранее, вне remember: stringResource нельзя
     * вызывать внутри лямбды remember, это композиция.
     */
    val indexEntries = SettingsRegistry.entries.map { entry ->
        val title = stringResource(entry.titleRes)
        val summary = entry.summaryRes?.let { stringResource(it) }.orEmpty()
        val keywords = entry.keywordsRes?.let { stringResource(it) }.orEmpty()
        val category = stringResource(entry.category.titleRes)
        entry to "$title $summary $keywords $category".lowercase()
    }

    val index = remember(indexEntries) { indexEntries.toMap() }

    val results = remember(query, index) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList()
        else index.filter { (_, text) -> text.contains(q) }.keys.toList()
    }

    /*
     * ПОИСК НАХОДИТ И ВОПРОСЫ (1.6.0).
     *
     * Человек ищет «заблокировать» или «новый телефон» — и получает не только
     * пункты настроек, но и ответ, где сказано, что делать. Ищется по вопросу
     * и ответу с подставленными путями — тем же текстом, что на экране
     * «Вопросы и ответы». По словам «помощь», «вопросы» находится сам экран.
     */
    val faqIndex = Faq.items.map { faq ->
        faq to "${stringResource(faq.question)} ${faqAnswerText(faq)}".lowercase()
    }
    val faqScreenText = "${stringResource(R.string.faq_title)} ${stringResource(R.string.faq_keywords)}".lowercase()
    val faqResults = remember(query, faqIndex) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList()
        else faqIndex.filter { (_, text) -> text.contains(q) }.map { it.first }
    }
    val faqScreenFound = remember(query, faqScreenText) {
        val q = query.trim().lowercase()
        q.length >= 2 && faqScreenText.contains(q)
    }

    androidx.compose.runtime.CompositionLocalProvider(
        com.ogneliss.dialer.ui.theme.LocalPageTone provides com.ogneliss.dialer.ui.theme.PageTone.TINTED
    ) {
        Scaffold(
            // Как настройки Ogneliss Mail: серо-голубая страница, группы — карточками (1.5.6)
            containerColor = com.ogneliss.dialer.ui.theme.OgnelissTheme.pageColor(com.ogneliss.dialer.ui.theme.PageTone.TINTED),
            // В edge-to-edge содержимое иначе уезжает под системные панели
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = ImeAction.Search
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(stringResource(R.string.nav_settings))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (searching) { searching = false; query = "" } else onBack()
                        }) {
                            Icon(
                                OgnelissIcons.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back)
                            )
                        }
                    },
                    actions = {
                        if (!searching) {
                            IconButton(onClick = { searching = true }) {
                                Icon(
                                    OgnelissIcons.Search,
                                    contentDescription = stringResource(R.string.settings_search)
                                )
                            }
                        }
                    }
                )
            }
        ) { pad ->
            // Не шире 640 dp по центру на широком окне: разбор в ReadableWidth
            ReadableWidth {
            LazyColumn(Modifier.padding(pad).fillMaxSize()) {

                if (searching && query.trim().length >= 2) {
                    if (results.isEmpty() && faqResults.isEmpty() && !faqScreenFound) {
                        item {
                            Text(
                                stringResource(R.string.settings_search_empty, query),
                                // Отступ пустого состояния из общих правил:
                                // в журнале и контактах он такой же
                                Modifier.padding(FlowUi.sheetPadding),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Найденное — белой группой с рамкой (строгий стиль, 1.5.1)
                        item { Spacer(Modifier.height(Space.s)) }
                        itemsIndexed(results, key = { _, e -> e.id }) { index, entry ->
                            GroupedRow(GroupPosition.of(index, results.size), dividerStart = Space.l) {
                                OgnelissListRow(
                                    headline = stringResource(entry.titleRes),
                                    // Показываем, в какой категории лежит настройка —
                                    // иначе пользователь найдёт её, но не запомнит где
                                    supporting = stringResource(entry.category.titleRes),
                                    onClick = { onOpenEntry(entry) }
                                )
                            }
                        }
                        // Вопросы — под настройками, своей группой (1.6.0)
                        if (faqResults.isNotEmpty() || faqScreenFound) {
                            item(key = "faq_results") {
                                SettingsGroup(title = stringResource(R.string.faq_title)) {
                                    if (faqScreenFound) {
                                        OgnelissListRow(
                                            headline = stringResource(R.string.faq_title),
                                            supporting = stringResource(R.string.faq_summary),
                                            leading = { Icon(OgnelissIcons.Help, contentDescription = null) },
                                            onClick = { onOpenFaq(null) }
                                        )
                                    }
                                    faqResults.forEachIndexed { index, faq ->
                                        if (index > 0 || faqScreenFound) {
                                            RowDivider(Modifier.padding(start = 56.dp))
                                        }
                                        OgnelissListRow(
                                            headline = stringResource(faq.question),
                                            leading = { Icon(OgnelissIcons.Help, contentDescription = null) },
                                            onClick = { onOpenFaq(faq.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    /*
                     * Категории одной карточкой, а не отдельными строками
                     * с разделителями. Семь пунктов сплошным списком читаются
                     * как таблица; собранные в блок — как оглавление.
                     */
                    /*
                     * 1.5.6 — КАК НАСТРОЙКИ OGNELISS MAIL: разделы собраны
                     * в три группы под скруглёнными полосами («ОСНОВНОЕ»,
                     * «ВЫЗОВЫ», «ПРИЛОЖЕНИЕ»), каждая — белая карточка.
                     * Десять пунктов одной карточкой читались длинным
                     * перечнем; три коротких группы — оглавлением.
                     */
                    HOME_GROUPS.forEach { (titleRes, categories) ->
                        item(key = "group_$titleRes") {
                            SettingsGroup(title = stringResource(titleRes)) {
                                categories.forEachIndexed { index, category ->
                                    SettingsCategoryCard(
                                        icon = category.icon,
                                        title = stringResource(category.titleRes),
                                        summary = stringResource(category.summaryRes),
                                        onClick = { onOpenCategory(category) }
                                    )
                                    // Разделитель между строками, но не после последней:
                                    // черта у нижней границы карточки выглядит обрывом
                                    if (index < categories.lastIndex) {
                                        RowDivider(Modifier.padding(start = 56.dp))
                                    }
                                }
                            }
                        }
                    }
                    /*
                     * «Вопросы и ответы» (1.6.0) — отдельной карточкой внизу:
                     * это не раздел настроек, а справка по ним, и среди
                     * разделов она читалась бы ещё одним разделом.
                     */
                    item(key = "faq") {
                        SettingsGroup {
                            SettingsCategoryCard(
                                icon = OgnelissIcons.Help,
                                title = stringResource(R.string.faq_title),
                                summary = stringResource(R.string.faq_summary),
                                onClick = { onOpenFaq(null) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(Space.xxl)) }
                }
            }
            }
        }
    }
}

/**
 * Группы главного экрана настроек (1.5.6). Порядок разделов — как
 * в SettingsCategory; каждый раздел — ровно в одной группе: иначе
 * забытый раздел пропал бы с экрана настроек. Проверяет
 * SettingsRegistryTest («каждый раздел — в одной группе главного экрана»).
 */
internal val HOME_GROUPS: List<Pair<Int, List<SettingsCategory>>> = listOf(
    R.string.settings_home_group_main to listOf(
        SettingsCategory.APPEARANCE,
        SettingsCategory.GESTURES
    ),
    R.string.settings_section_calls to listOf(
        SettingsCategory.CALLS,
        SettingsCategory.CALL_LOG,
        SettingsCategory.SIM,
        SettingsCategory.QUICK_ACCESS,
        SettingsCategory.SPAM
    ),
    R.string.menu_section_app to listOf(
        SettingsCategory.MAINTENANCE,
        SettingsCategory.SYSTEM,
        SettingsCategory.ABOUT
    )
)
