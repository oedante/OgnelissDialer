package com.ogneliss.dialer.settings

/**
 * ЧТО ОТКРЫВАТЬ ПРИ ЗАПУСКЕ (2.7.0).
 *
 * Форум, 15.11.2026, oleg_p: «А есть возможность реализовать выбор экрана
 * при запуске приложения: показать конкретный экран (по умолчанию) либо
 * последний использованный?»
 *
 * Было: приложение всегда начинало с журнала. Стало: настройка
 * «При запуске открывать» (start_section) — журнал (по умолчанию), SMS,
 * контакты или раздел, на котором человек закончил в прошлый раз.
 * Заметки и напоминания в выбор не входят: это не главные разделы.
 */
enum class StartChoice {
    JOURNAL,
    MESSAGES,
    CONTACTS,

    /** Последний открытый главный раздел (last_section). */
    LAST;

    companion object {
        fun from(name: String?): StartChoice =
            entries.firstOrNull { it.name == name } ?: JOURNAL
    }
}

/**
 * РЕШЕНИЕ, С КАКОГО РАЗДЕЛА НАЧАТЬ. Чистая функция, без Android: проверяется
 * модульным тестом (StartSectionTest).
 *
 * Возвращает маршрут раздела (MainSection.route) или null — «остаться, где
 * был» (нынешнее поведение: возврат из фона, обычный запуск журналом).
 *
 * Правила по порядку:
 * 1. Явное намерение главнее настройки: значок раздела или виджет
 *    (SectionWidgets.EXTRA_OPEN_SECTION) — раздел из намерения. Набор номера
 *    из другого приложения, уведомления, «Написать» — это не запуск значком
 *    (нет MAIN/LAUNCHER), для них [coldLauncherStart] ложь, и настройка
 *    их не трогает.
 * 2. Пока включены отдельные значки «SMS» и «Контакты» (2.6.0,
 *    widget/SectionIcons.kt), значок приложения — «Телефон» и открывает
 *    журнал: это правило главнее настройки.
 * 3. Не холодный запуск значком (возврат из фона, поворот, восстановление) —
 *    null: человек остаётся там, где был.
 * 4. Иначе по выбору: журнал / SMS / контакты — свой маршрут; «Последний» —
 *    сохранённый [last], а без него (первый запуск) — журнал.
 */
object StartSection {
    // Маршруты главных разделов (MainSection.route). Своя копия, чтобы функция
    // не тянула за собой Compose-значки; совпадение проверяет StartSectionTest
    const val JOURNAL = "dialer"
    const val MESSAGES = "messages"
    const val CONTACTS = "contacts"

    /** Маршруты, которые можно запомнить и открыть при запуске. */
    val MAIN_ROUTES = setOf(JOURNAL, MESSAGES, CONTACTS)

    fun resolve(
        choice: StartChoice,
        last: String?,
        explicitRoute: String?,
        sectionIconsEnabled: Boolean,
        coldLauncherStart: Boolean
    ): String? {
        if (explicitRoute != null) return explicitRoute
        if (!coldLauncherStart) return null
        if (sectionIconsEnabled) return JOURNAL
        return when (choice) {
            StartChoice.JOURNAL -> JOURNAL
            StartChoice.MESSAGES -> MESSAGES
            StartChoice.CONTACTS -> CONTACTS
            StartChoice.LAST -> last?.takeIf { it in MAIN_ROUTES } ?: JOURNAL
        }
    }
}
