package com.ogneliss.dialer.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.FolderOrder
import com.ogneliss.dialer.ui.components.MainSection
import com.ogneliss.dialer.ui.theme.OgnelissIcons

/**
 * «ИНСТРУМЕНТЫ» МЕНЮ — ПОРЯДОК И ПОКАЗ, КАК У ПАПОК (2.9.3).
 *
 * Автор, 23.11.2026, о разделах в «Меню»: «давай сделаем их настраиваемыми,
 * как сейчас раздел папок. По умолчанию всё включено, но пользователь может
 * выключить ненужное». В 2.9.2 «Меню» само прятало разделы, которые стоят
 * на нижней панели, — человек не мог ни вернуть их, ни убрать лишнее.
 *
 * Теперь строки «Инструментов» — как папки: значок «настроить» справа от
 * подписи открывает экран порядка и показа (MenuFoldersScreen), хранится
 * строкой FolderOrder в Prefs.menuTools. Ни одна строка не обязательна
 * (у папок «Все» скрыть нельзя): раздел, которого нет ни на панели,
 * ни в «Меню», человек убрал сам. Экран настройки помечает такие разделы
 * «Нет на нижней панели», чтобы это было видно до выключения.
 */
internal enum class MenuTool(@StringRes val labelRes: Int) {
    OWN_CONTACT(R.string.menu_my_contact),
    FAKE_CALL(R.string.fake_call_title),
    MESSAGES(R.string.nav_messages),
    CONTACTS(R.string.nav_contacts),
    NOTES(R.string.nav_notes),
    REMINDERS(R.string.nav_reminders);

    /** Значок строки — тот же, что в меню (свойство, а не поле: значки строятся лениво). */
    val icon: ImageVector
        get() = when (this) {
            OWN_CONTACT -> OgnelissIcons.Person
            FAKE_CALL -> OgnelissIcons.PhoneCallback
            MESSAGES -> MainSection.MESSAGES.icon
            CONTACTS -> MainSection.CONTACTS.icon
            NOTES -> MainSection.NOTES.icon
            REMINDERS -> MainSection.REMINDERS.icon
        }

    /** Раздел строки; у «Моего контакта» и псевдозвонка раздела нет. */
    val section: MainSection?
        get() = when (this) {
            MESSAGES -> MainSection.MESSAGES
            CONTACTS -> MainSection.CONTACTS
            NOTES -> MainSection.NOTES
            REMINDERS -> MainSection.REMINDERS
            else -> null
        }

    companion object {
        /** Ключи в порядке по умолчанию. */
        val keys: List<String> = entries.map { it.name }

        /** Нет обязательной строки: FolderOrder ждёт имя, пустое не совпадёт ни с одним. */
        const val NO_FIXED = ""

        /** Видимые строки по порядку; null — все, как по умолчанию. */
        fun visible(raw: String?): List<MenuTool> =
            FolderOrder.visible(raw, keys, NO_FIXED).map { valueOf(it) }

        /** Строки для экрана настройки (MenuFoldersScreen): ключ, подпись, значок. */
        fun items(): List<Triple<String, Int, ImageVector>> = entries.map { Triple(it.name, it.labelRes, it.icon) }
    }
}

/**
 * Открыть настройку «Инструментов» (2.9.3) — даёт корень (RootScreen), чтобы
 * значок «настроить» был в меню любого раздела без параметра у каждого экрана.
 * null — значка нет (снимки галереи, превью).
 */
internal val LocalOpenMenuTools = staticCompositionLocalOf<(() -> Unit)?> { null }
