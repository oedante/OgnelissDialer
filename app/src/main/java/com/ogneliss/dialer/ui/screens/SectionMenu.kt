package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.MainSection

/**
 * ПЕРЕХОДЫ РАЗДЕЛА-СПИСКА (2.9.2).
 *
 * «Заметки» и «Напоминания» стали разделами нижней панели (конструктор,
 * PanelSlots; автор: «контакты, смс, меню, заметки, напоминания можно
 * добавить или убрать из нижней панели»). Было (до 2.9.1): отдельные экраны
 * со стрелкой «назад» и одним полем поиска внизу. Теперь у них та же панель
 * и то же «Меню», что у журнала, SMS и контактов, — а значит, и те же
 * переходы. Набор один на оба раздела, чтобы они не разошлись.
 */
internal class SectionLinks(
    val onOpenSection: (MainSection) -> Unit = {},
    /** Клавиатура набора — в журнале (DialerViewModel.requestDialpad). */
    val onOpenDialpad: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenHelp: () -> Unit = {},
    val onFakeCall: () -> Unit = {},
    /** «Мой контакт» — своя карточка (OWN_CARD_KEY). */
    val onOpenOwnCard: () -> Unit = {}
)

/**
 * «Меню» раздела заметок или напоминаний — та же боковая панель, что
 * в журнале, SMS и контактах (OverflowMenu): «Мой контакт», псевдозвонок,
 * разделы, которых нет на панели, «Приложение» и поиск.
 */
@Composable
internal fun SectionMenu(
    vm: DialerViewModel,
    expanded: Boolean,
    onDismiss: () -> Unit,
    links: SectionLinks,
    onSearch: (() -> Unit)?,
    /** Свои «папки» раздела над инструментами (отбор напоминаний по сроку); null — нет. */
    header: (@Composable ColumnScope.() -> Unit)? = null
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val ownContact = rememberOwnContactEntry(vm, state.contacts, links.onOpenOwnCard)
    OverflowMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        settings = settings,
        ownNumber = ownContact.number,
        ownPhotoUri = ownContact.photoUri,
        onOwnNumber = { onDismiss(); ownContact.open() },
        onThemeToggle = {
            vm.setChoice(
                "theme",
                if (settings.theme == ThemeMode.DARK) ThemeMode.LIGHT.name else ThemeMode.DARK.name
            )
            onDismiss()
        },
        onSettings = { onDismiss(); links.onOpenSettings() },
        onFakeCall = { onDismiss(); links.onFakeCall() },
        onNotes = { onDismiss(); links.onOpenSection(MainSection.NOTES) },
        onReminders = { onDismiss(); links.onOpenSection(MainSection.REMINDERS) },
        onMessages = { onDismiss(); links.onOpenSection(MainSection.MESSAGES) },
        onContacts = { onDismiss(); links.onOpenSection(MainSection.CONTACTS) },
        onHelp = { onDismiss(); links.onOpenHelp() },
        header = header,
        onSearch = onSearch?.let { search -> { onDismiss(); search() } }
    )
}
