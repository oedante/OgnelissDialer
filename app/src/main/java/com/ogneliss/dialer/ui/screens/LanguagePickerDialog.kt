package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.util.LocaleManager
import java.text.Collator
import java.util.Locale

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ВЫБОР ЯЗЫКА — 48 ЯЗЫКОВ, ПОИСК, ДВА НАЗВАНИЯ, ПОМЕТКА АВТОПЕРЕВОДА (1.2.4)
 * ══════════════════════════════════════════════════════════════════════════
 * Было: общий список выбора с названиями только на самих языках — пока
 * языков было четыре, этого хватало. С автопереводом Google Play их 48,
 * и после первых четырёх они шли в порядке, в каком перечислены в
 * locales_config.xml, — искать нужный было неудобно.
 *
 * Теперь:
 * • у каждого языка два названия: на самом языке («Deutsch») — его
 *   узнает любой носитель — и на языке интерфейса («немецкий»);
 * • порядок: «Как в системе», выбранный сейчас, четыре языка с ручным
 *   переводом, дальше остальные по алфавиту названий на языке интерфейса;
 * • поиск по обоим названиям и по коду языка;
 * • у 44 языков пометка «автоперевод»: честно сказать заранее, что
 *   перевод машинный, лучше, чем получить отзыв о его качестве.
 *
 * Флагов нет намеренно: язык — не страна (английский, испанский,
 * арабский, португальский — это десятки стран), и выбор флага за язык
 * сам по себе был бы высказыванием. Android в выборе языка их тоже
 * не показывает.
 */
@Composable
fun LanguagePickerDialog(
    vm: DialerViewModel,
    current: String?,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val uiLocale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val autoLabel = stringResource(R.string.language_auto_translated)
    val systemLabel = stringResource(R.string.settings_language_system)

    val entries = remember(uiLocale, current) {
        val collator = Collator.getInstance(uiLocale)
        val all = LocaleManager.options(ctx)
            .filter { it.tag != LocaleManager.SYSTEM }
            .map { option ->
                val locale = Locale.forLanguageTag(option.tag)
                LanguageEntry(
                    tag = option.tag,
                    native = option.nativeName,
                    localized = locale.getDisplayLanguage(uiLocale)
                        .replaceFirstChar { it.titlecase(uiLocale) },
                    auto = option.tag !in HAND_TRANSLATED
                )
            }
        val selected = all.filter { it.tag == current }
        val own = HAND_TRANSLATED.mapNotNull { tag -> all.firstOrNull { it.tag == tag } }
            .filterNot { it.tag == current }
        val rest = all.filter { it.tag !in HAND_TRANSLATED && it.tag != current }
            .sortedWith { a, b -> collator.compare(a.localized, b.localized) }
        selected + own + rest
    }

    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(entries, query) {
        val q = query.trim()
        if (q.isEmpty()) entries
        else entries.filter {
            it.native.contains(q, ignoreCase = true) ||
                it.localized.contains(q, ignoreCase = true) ||
                it.tag.startsWith(q, ignoreCase = true)
        }
    }

    fun choose(tag: String) {
        vm.setChoice("language", tag)
        onDismiss()
    }

    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_language),
        content = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.language_search_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    // «Как в системе» — всегда первым, в поиске не участвует
                    if (query.isBlank()) {
                        item(key = "system") {
                            LanguageRow(
                                selected = current.isNullOrEmpty(),
                                title = systemLabel,
                                subtitle = null,
                                onSelect = { choose(LocaleManager.SYSTEM) }
                            )
                        }
                    }
                    items(shown, key = { it.tag }) { entry ->
                        val sameName = entry.native.equals(entry.localized, ignoreCase = true)
                        LanguageRow(
                            selected = entry.tag == current,
                            title = entry.native,
                            // Второе название — если отличается; пометка
                            // автоперевода — у машинных переводов
                            subtitle = listOfNotNull(
                                entry.localized.takeUnless { sameName },
                                autoLabel.takeIf { entry.auto }
                            ).joinToString(" · ").ifEmpty { null },
                            onSelect = { choose(entry.tag) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_close)) }
        }
    )
}

/** Языки с ручным переводом — у них нет пометки «автоперевод». */
private val HAND_TRANSLATED = listOf("ru", "en", "de", "fr")

private data class LanguageEntry(
    val tag: String,
    val native: String,
    val localized: String,
    val auto: Boolean
)

@Composable
private fun LanguageRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onSelect: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
