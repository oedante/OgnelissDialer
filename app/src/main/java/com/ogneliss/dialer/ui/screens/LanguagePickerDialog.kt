package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.ChoiceSheet
import com.ogneliss.dialer.ui.components.SheetChoiceRow
import com.ogneliss.dialer.ui.components.SheetDivider
import com.ogneliss.dialer.ui.theme.FlowUi
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
 * 2.7.0: с этой версии — шторка снизу, а не окно по центру (см. комментарий
 * у ChoiceSheet в теле): имя прежнее, чтобы не трогать вызовы.
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

    /*
     * ШТОРКА, А НЕ ОКНО ПО ЦЕНТРУ (2.7.0, обзор вида 15.11.2026).
     *
     * Было — OgnelissDialog: поле поиска, список до 400 dp, кнопка «Закрыть».
     * Стало — шторка выбора (ChoiceSheet), как остальные настройки-выборы:
     * владелец: «шторка снизу не всегда удобна; где используется выбор по
     * долгому нажатию и всплывающая форма (журнал, контакты, SMS) —
     * оставить, остальное поменять на шторку». Поле поиска осталось над
     * списком и не прокручивается; список — LazyColumn на остаток высоты
     * шторки (48 языков). Выбор применяется сразу и закрывает шторку.
     */
    ChoiceSheet(title = stringResource(R.string.settings_language), onDismiss = onDismiss) {
        com.ogneliss.dialer.ui.components.OgnelissTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(OgnelissIcons.Search, contentDescription = null) },
            placeholder = stringResource(R.string.language_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlowUi.sheetPadding)
        )
        Spacer(Modifier.height(Space.s))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            // «Как в системе» — всегда первым, в поиске не участвует
            if (query.isBlank()) {
                item(key = "system") {
                    Column {
                        SheetChoiceRow(
                            selected = current.isNullOrEmpty(),
                            label = systemLabel,
                            onClick = { choose(LocaleManager.SYSTEM) }
                        )
                        SheetDivider()
                    }
                }
            }
            items(shown, key = { it.tag }) { entry ->
                val sameName = entry.native.equals(entry.localized, ignoreCase = true)
                Column {
                    SheetChoiceRow(
                        selected = entry.tag == current,
                        label = entry.native,
                        // Второе название — если отличается; пометка
                        // автоперевода — у машинных переводов
                        supporting = listOfNotNull(
                            entry.localized.takeUnless { sameName },
                            autoLabel.takeIf { entry.auto }
                        ).joinToString(" · ").ifEmpty { null },
                        onClick = { choose(entry.tag) }
                    )
                    SheetDivider()
                }
            }
        }
    }
}

/** Языки с ручным переводом — у них нет пометки «автоперевод». */
private val HAND_TRANSLATED = listOf("ru", "en", "de", "fr")

private data class LanguageEntry(
    val tag: String,
    val native: String,
    val localized: String,
    val auto: Boolean
)
