package com.ogneliss.dialer.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.FolderOrder
import com.ogneliss.dialer.ui.components.MainSection
import com.ogneliss.dialer.ui.theme.OgnelissIcons

/**
 * КОЛОНКА СПРАВА — ПОРЯДОК И ПОКАЗ (2.9.9).
 *
 * Автор, 25.11.2026: «Боковую панель тоже желательно настраивать (порядок,
 * отображение). И лучше частое отображать ближе к нижнему правому углу.
 * Например, от кнопки клавиатуры или создания: журнал звонков, SMS, контакты
 * и остальное».
 *
 * Пункты колонки (SideSectionRail) — свой список, независимо от конструктора
 * нижней панели (PanelSlots) и «Инструментов» меню (MenuTool): разделы,
 * «Мой контакт» и псевдозвонок. Хранится строкой FolderOrder («JOURNAL,
 * MESSAGES,-NOTES,…»: порядок, минус — скрыт), настраивается тем же экраном,
 * что папки (MenuFoldersScreen, маршрут «rail_items»). «Журнал» не скрыть,
 * как и на нижней панели.
 *
 * Первый пункт — ближе всего к кнопке действия, у нижнего правого угла, под
 * большим пальцем; дальше — вверх по колонке. «Меню» — всегда наверху колонки.
 */
internal enum class RailItem(@StringRes val labelRes: Int) {
    JOURNAL(R.string.nav_journal),
    MESSAGES(R.string.nav_messages),
    CONTACTS(R.string.nav_contacts),
    NOTES(R.string.nav_notes),
    REMINDERS(R.string.nav_reminders),
    OWN_CONTACT(R.string.menu_my_contact),
    FAKE_CALL(R.string.fake_call_title);

    /** Раздел пункта; у «Моего контакта» и псевдозвонка раздела нет. */
    val section: MainSection?
        get() = when (this) {
            JOURNAL -> MainSection.JOURNAL
            MESSAGES -> MainSection.MESSAGES
            CONTACTS -> MainSection.CONTACTS
            NOTES -> MainSection.NOTES
            REMINDERS -> MainSection.REMINDERS
            else -> null
        }

    /** Значок — тот же, что у раздела на панели и у строки меню. */
    val icon: ImageVector
        get() = section?.icon ?: when (this) {
            OWN_CONTACT -> OgnelissIcons.Person
            else -> OgnelissIcons.PhoneCallback
        }

    companion object {
        /** Ключи в порядке по умолчанию: частое — первым, у кнопки действия. */
        val keys: List<String> = entries.map { it.name }

        /** Не скрывается — как «Журнал» на нижней панели. */
        val FIXED: String = JOURNAL.name

        /** Видимые пункты по порядку (первый — у кнопки действия); null — все, как по умолчанию. */
        fun visible(raw: String?): List<RailItem> = FolderOrder.visible(raw, keys, FIXED).map { valueOf(it) }

        /** Строки для экрана настройки (MenuFoldersScreen): ключ, подпись, значок. */
        fun items(): List<Triple<String, Int, ImageVector>> = entries.map { Triple(it.name, it.labelRes, it.icon) }
    }
}
