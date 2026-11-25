package com.ogneliss.dialer.ui

import androidx.compose.runtime.MutableState
import androidx.navigation.NavController
import com.ogneliss.dialer.ui.components.MainSection

/**
 * РАЗДЕЛЫ И «НАЗАД» (2.9.9).
 *
 * Автор, 25.11.2026: «пользователи жаловались, что по уведомлению или по значку
 * открывают SMS, отвечают, потом „Назад“ (в список SMS), ещё „Назад“ — ожидают
 * скрытие приложения, а оно переходит в журнал звонков. „Назад“ из журнала
 * звонков, SMS или контактов должно закрывать/скрывать приложение».
 *
 * Было (до 2.9.8): журнал — основание, разделы открывались поверх него, и «Назад»
 * в любом разделе вело в журнал. 2.9.8 — основанием становился только раздел,
 * открытый снаружи (значок, виджет); переписка из уведомления по-прежнему
 * ложилась поверх журнала.
 *
 * Стало: раздел, в который перешли, — всегда основание стека ([root]). Под ним
 * ничего нет, и «Назад» в нём сворачивает приложение — это делает система: при
 * одном экране в стеке навигация «Назад» не забирает, своего обработчика нет.
 * Вложенные экраны (переписка, карточка, настройки) ложатся поверх раздела,
 * «Назад» возвращает в раздел. Состояние раздела — прокрутка, открытая
 * переписка — сохраняется при уходе и возвращается при возврате
 * (saveState/restoreState), как у нижней навигации Android.
 *
 * Разделы, у которых есть свой вход снаружи, открываются поверх своего раздела
 * (RootScreen): переписка — поверх SMS, карточка из уведомления — поверх журнала.
 */
internal class SectionNavigator(
    private val nav: NavController,
    /** Раздел-основание (имя MainSection): сохраняется вместе со стеком навигации. */
    private val root: MutableState<String>
) {
    val rootSection: MainSection
        get() = MainSection.entries.firstOrNull { it.name == root.value } ?: MainSection.JOURNAL

    /** Переход в раздел: он — основание; тот же раздел — к его началу. */
    fun open(section: MainSection) {
        val from = rootSection
        if (section == from) {
            // Вложенные экраны закрываются, остаётся список раздела
            nav.popBackStack(routePattern(section), inclusive = false)
            return
        }
        nav.navigate(section.route) {
            popUpTo(routePattern(from)) { inclusive = true; saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        root.value = section.name
    }

    /**
     * Раздел, открытый снаружи (значок «SMS»/«Контакты», виджет, «При запуске
     * открывать»): основанием, как любой переход, и сразу — его список, а не то,
     * что в нём было открыто в прошлый раз: человек пришёл за списком.
     */
    fun openFromOutside(section: MainSection) {
        open(section)
        nav.popBackStack(routePattern(section), inclusive = false)
    }

    /** Убедиться, что основание — [section] (вход снаружи во вложенный экран раздела). */
    fun ensureRoot(section: MainSection) {
        if (rootSection != section) open(section)
    }

    companion object {
        /** Маршрут списка контактов: «pick» — выбор контакта для настройки, «number» — добавить номер. */
        const val CONTACTS_ROUTE = "contacts?pick={pick}&number={number}"

        /**
         * Маршрут раздела так, как он объявлен в навигации: у контактов —
         * с необязательными параметрами, у остальных — как в MainSection.
         */
        fun routePattern(section: MainSection): String =
            if (section == MainSection.CONTACTS) CONTACTS_ROUTE else section.route
    }
}
