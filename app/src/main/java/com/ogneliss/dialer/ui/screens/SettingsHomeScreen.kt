package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.components.OgnelissDivider
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ReadableWidth
import com.ogneliss.dialer.ui.components.SettingsCategoryCard
import com.ogneliss.dialer.ui.components.SettingsGroup
import com.ogneliss.dialer.ui.settings.SettingEntry
import com.ogneliss.dialer.ui.settings.SettingsCategory
import com.ogneliss.dialer.ui.settings.SettingsRegistry

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
    onOpenEntry: (SettingEntry) -> Unit
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

    Scaffold(
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
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) {
                            Icon(
                                Icons.Default.Search,
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
                if (results.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.settings_search_empty, query),
                            // Отступ пустого состояния из общих правил:
                            // в журнале и контактах он такой же
                            Modifier.padding(FlowUi.sheetPadding),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    items(results, key = { it.id }) { entry ->
                        OgnelissListRow(
                            headline = stringResource(entry.titleRes),
                            // Показываем, в какой категории лежит настройка —
                            // иначе пользователь найдёт её, но не запомнит где
                            supporting = stringResource(entry.category.titleRes),
                            onClick = { onOpenEntry(entry) }
                        )
                        OgnelissDivider()
                    }
                }
            } else {
                /*
                 * Категории одной карточкой, а не отдельными строками
                 * с разделителями. Семь пунктов сплошным списком читаются
                 * как таблица; собранные в блок — как оглавление.
                 */
                item {
                    SettingsGroup {
                        SettingsCategory.entries.forEachIndexed { index, category ->
                            SettingsCategoryCard(
                                icon = category.icon,
                                title = stringResource(category.titleRes),
                                summary = stringResource(category.summaryRes),
                                onClick = { onOpenCategory(category) }
                            )
                            // Разделитель между строками, но не после последней:
                            // черта у нижней границы карточки выглядит обрывом
                            if (index < SettingsCategory.entries.lastIndex) {
                                HorizontalDivider(
                                    Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

