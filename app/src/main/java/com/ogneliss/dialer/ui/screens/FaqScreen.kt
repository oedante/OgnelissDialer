package com.ogneliss.dialer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.components.SettingsGroup
import com.ogneliss.dialer.ui.help.Faq
import com.ogneliss.dialer.ui.help.FaqItem
import com.ogneliss.dialer.ui.help.FaqLink
import com.ogneliss.dialer.ui.help.inText
import com.ogneliss.dialer.ui.help.isButton
import com.ogneliss.dialer.ui.settings.SettingEntry
import com.ogneliss.dialer.ui.settings.SettingsRegistry
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.PageTone
import com.ogneliss.dialer.ui.theme.Space

/**
 * ЭКРАН «ВОПРОСЫ И ОТВЕТЫ» (1.6.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК УСТРОЕН
 * ══════════════════════════════════════════════════════════════════════════
 * Как настройки: серо-голубая страница, вопросы группами в белых
 * карточках («Начало работы», «Журнал и контакты», «Вызовы»,
 * «О приложении»). Касание вопроса раскрывает ответ, второе — сворачивает;
 * раскрытым может быть сколько угодно — сравнивать два ответа удобно.
 *
 * Под ответом — кнопки туда, о чём он: пункт настроек открывается своим
 * разделом и на секунду подсвечивается, системный экран и письмо —
 * сразу, лицензия — здесь же, без сайта. Что и куда — Faq.kt.
 *
 * Открытый из поиска по настройкам — сразу с раскрытым вопросом
 * и прокрученный к нему.
 *
 * ── Для TalkBack ───────────────────────────────────────────────────────────
 * Вопрос — кнопка с состоянием «развёрнуто» / «свёрнуто», как
 * «Дополнительно» в настройках; ответ читается следом обычным текстом.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FaqScreen(
    onBack: () -> Unit,
    /** Раскрыть этот вопрос и прокрутить к нему (id из Faq.kt). */
    openQuestion: String? = null,
    /** Уже телефон по умолчанию — кнопки «Сделать основным» нет. */
    isDefaultDialer: Boolean,
    onOpenLink: (FaqLink) -> Unit
) {
    // Раскрытые вопросы переживают поворот экрана
    val expanded = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) {
        mutableStateListOf<String>().apply { Faq.byId(openQuestion)?.let { add(it.id) } }
    }
    val target = remember { BringIntoViewRequester() }
    LaunchedEffect(openQuestion) {
        if (Faq.byId(openQuestion) == null) return@LaunchedEffect
        // Кадр — чтобы карточки легли на место
        withFrameNanos { }
        target.bringIntoView()
    }

    ScreenScaffold(
        tone = PageTone.TINTED,
        title = stringResource(R.string.faq_title),
        onBack = onBack,
        pinnedTopBar = true
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.faq_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Вровень с подписями полос разделов
                modifier = Modifier.padding(
                    start = FlowUi.groupInset + Space.l,
                    end = FlowUi.groupInset + Space.l,
                    top = Space.m
                )
            )
            Faq.sections.forEach { section ->
                SettingsGroup(title = stringResource(section.title)) {
                    section.items.forEachIndexed { index, item ->
                        FaqQuestion(
                            item = item,
                            open = item.id in expanded,
                            isDefaultDialer = isDefaultDialer,
                            onToggle = { if (!expanded.remove(item.id)) expanded.add(item.id) },
                            onOpenLink = onOpenLink,
                            modifier = if (item.id == openQuestion) {
                                Modifier.bringIntoViewRequester(target)
                            } else {
                                Modifier
                            }
                        )
                        if (index < section.items.lastIndex) {
                            // От начала текста, как в настройках
                            RowDivider(Modifier.padding(start = Space.l))
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun FaqQuestion(
    item: FaqItem,
    open: Boolean,
    isDefaultDialer: Boolean,
    onToggle: () -> Unit,
    onOpenLink: (FaqLink) -> Unit,
    modifier: Modifier = Modifier
) {
    val stateText = stringResource(if (open) R.string.a11y_expanded else R.string.a11y_collapsed)
    Column(modifier) {
        ListItem(
            headlineContent = { Text(stringResource(item.question)) },
            trailingContent = {
                Icon(
                    if (open) OgnelissIcons.ExpandLess else OgnelissIcons.ExpandMore,
                    contentDescription = null
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .heightIn(min = FlowUi.rowHeight)
                .clickable(onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = stateText
                }
        )
        AnimatedVisibility(visible = open) {
            Column(
                Modifier.padding(start = Space.l, end = Space.l, bottom = Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                Text(
                    faqAnswerText(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // «Сделать основным» — только пока приложение не основное
                val buttons = item.links.filter { link ->
                    link.isButton && !(link == FaqLink.DefaultDialer && isDefaultDialer)
                }
                if (buttons.isNotEmpty()) FaqButtons(buttons, onOpenLink)
            }
        }
    }
}

/**
 * Кнопки под ответом. Тональные, как «Показать все» в карточке:
 * второстепенное действие, но форма говорит, что это кнопка, а не ссылка.
 * Не помещаются в строку — переносятся.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FaqButtons(links: List<FaqLink>, onOpenLink: (FaqLink) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s)
    ) {
        links.forEach { link ->
            val label = faqLinkLabel(link) ?: return@forEach
            FilledTonalButton(onClick = { onOpenLink(link) }) {
                Icon(faqLinkIcon(link), contentDescription = null, modifier = Modifier.size(FlowUi.iconSizeInline))
                Spacer(Modifier.width(Space.s))
                Text(label)
            }
        }
    }
}

/**
 * Текст ответа с подставленными путями к настройкам — те же строки,
 * что видит человек в самих настройках. Поиск по настройкам ищет
 * по этому же тексту.
 */
@Composable
internal fun faqAnswerText(item: FaqItem): String {
    val paths = item.links.filter { it.inText }.map { faqLinkPath(it).orEmpty() }
    return if (paths.isEmpty()) {
        stringResource(item.answer)
    } else {
        stringResource(item.answer, *paths.toTypedArray())
    }
}

/** Путь «Настройки → раздел → «пункт»»; не место в настройках — null. */
@Composable
private fun faqLinkPath(link: FaqLink): String? = when (link) {
    is FaqLink.Setting -> SettingsRegistry.byId(link.entryId)?.let { entry -> settingPath(entry) }
    is FaqLink.Section -> stringResource(
        R.string.faq_path_section,
        stringResource(R.string.nav_settings),
        stringResource(link.category.titleRes)
    )
    else -> null
}

@Composable
private fun settingPath(entry: SettingEntry): String {
    val settings = stringResource(R.string.nav_settings)
    val section = stringResource(entry.category.titleRes)
    val title = stringResource(entry.titleRes)
    // Спрятан под «Дополнительно» — путь называет и группу
    return if (SettingsRegistry.isInAdvancedGroup(entry)) {
        stringResource(R.string.faq_path_advanced, settings, section, stringResource(R.string.settings_advanced), title)
    } else {
        stringResource(R.string.faq_path, settings, section, title)
    }
}

@Composable
private fun faqLinkLabel(link: FaqLink): String? = when (link) {
    is FaqLink.Setting -> SettingsRegistry.byId(link.entryId)?.let { entry -> stringResource(entry.titleRes) }
    is FaqLink.Section -> stringResource(link.category.titleRes)
    FaqLink.DefaultDialer -> stringResource(R.string.faq_make_default)
    is FaqLink.Document -> stringResource(link.document.titleRes)
    FaqLink.SourceCode -> stringResource(R.string.settings_source_code)
    FaqLink.Mail -> stringResource(R.string.faq_write)
}

private fun faqLinkIcon(link: FaqLink): ImageVector = when (link) {
    is FaqLink.Setting, is FaqLink.Section -> OgnelissIcons.Settings
    FaqLink.DefaultDialer -> OgnelissIcons.Call
    is FaqLink.Document -> OgnelissIcons.Gavel
    FaqLink.SourceCode -> OgnelissIcons.Code
    FaqLink.Mail -> OgnelissIcons.Email
}
