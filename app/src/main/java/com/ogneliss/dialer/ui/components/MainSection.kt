package com.ogneliss.dialer.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.theme.OgnelissIcons

/**
 * РАЗДЕЛЫ ГЛАВНОГО ЭКРАНА (1.6.0, обзор интерфейса, П1 — вариант «Б»).
 *
 * Журнал и контакты — соседи, а не родитель и ребёнок: у обоих одна
 * нижняя панель, и первая кнопка на ней переключает между ними
 * (ListSwitchButton). Заметки и напоминания — рядом: до них долгим
 * нажатием на ту же кнопку, из меню и с боковой полосы планшета.
 *
 * [route] — маршрут навигации в RootScreen; порядок — как на боковой
 * полосе планшета и в списке разделов.
 */
enum class MainSection(val route: String, @StringRes val labelRes: Int) {
    JOURNAL("dialer", R.string.nav_journal),
    CONTACTS("contacts", R.string.nav_contacts),
    NOTES("notes", R.string.nav_notes),
    REMINDERS("reminders", R.string.nav_reminders);

    /** Значок раздела — тот же, что на боковой полосе планшета. */
    val icon: ImageVector
        get() = when (this) {
            JOURNAL -> OgnelissIcons.History
            CONTACTS -> OgnelissIcons.Person
            NOTES -> OgnelissIcons.StickyNote2
            REMINDERS -> OgnelissIcons.Alarm
        }

    /**
     * Соседний список — куда переключает касание кнопки: из журнала
     * в контакты, отовсюду остального — в журнал.
     */
    val sibling: MainSection
        get() = if (this == JOURNAL) CONTACTS else JOURNAL
}
