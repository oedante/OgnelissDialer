package com.ogneliss.dialer.ui

import com.ogneliss.dialer.ui.settings.SettingKind
import com.ogneliss.dialer.ui.settings.SettingsCategory
import com.ogneliss.dialer.settings.SettingsCommands
import com.ogneliss.dialer.ui.settings.SettingsRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ СОГЛАСОВАННОСТИ РЕЕСТРА НАСТРОЕК.
 *
 * Реестр и SettingsState связаны строковыми ключами. Опечатка в ключе
 * не вызовет ошибку компиляции: переключатель просто всегда будет
 * выключен, и заметить это можно только вручную открыв экран.
 * Эти тесты ловят рассинхронизацию на сборке.
 */
class SettingsRegistryTest {

    @Test
    fun `каждый раздел — в одной группе главного экрана`() {
        // 1.5.6: главный экран настроек собран группами, как в Ogneliss Mail.
        // Забытый раздел пропал бы с экрана, повторённый — встал бы дважды
        val grouped = com.ogneliss.dialer.ui.screens.HOME_GROUPS.flatMap { it.second }
        assertEquals(SettingsCategory.entries.toList(), grouped)
    }

    @Test
    fun `у всех настроек уникальные идентификаторы`() {
        val ids = SettingsRegistry.entries.map { it.id }
        assertEquals("Обнаружены дублирующиеся id", ids.size, ids.distinct().size)
    }

    @Test
    fun `каждый переключатель обрабатывается SettingsCommands`() {
        // Реестр описывает настройки, SettingsCommands их применяет.
        // Расхождение означает молча неработающий переключатель:
        // пользователь щёлкает, ничего не происходит, и вывод —
        // приложение сломано. Компилятор такое не ловит: ключи строковые
        val registryKeys = SettingsRegistry.entries
            .mapNotNull { (it.kind as? SettingKind.Switch)?.key }

        registryKeys.forEach { key ->
            assertTrue(
                "Ключ '$key' есть в реестре, но не обрабатывается " +
                    "в SettingsCommands.setSwitch",
                key in SettingsCommands.SWITCH_KEYS
            )
        }
    }

    @Test
    fun `каждый выбор обрабатывается SettingsCommands`() {
        val choiceKeys = SettingsRegistry.entries
            .mapNotNull { (it.kind as? SettingKind.Choice)?.key }

        choiceKeys.forEach { key ->
            assertTrue(
                "Ключ '$key' есть в реестре, но не обрабатывается " +
                    "в SettingsCommands.setChoice",
                key in SettingsCommands.CHOICE_KEYS
            )
        }
    }

    @Test
    fun `в SettingsCommands нет ключей вне реестра`() {
        val registryKeys = SettingsRegistry.entries
            .mapNotNull { (it.kind as? SettingKind.Switch)?.key }.toSet()

        SettingsCommands.SWITCH_KEYS.forEach { key ->
            assertTrue(
                "Ключ '$key' обрабатывается, но недоступен пользователю: " +
                    "настройки нет в реестре",
                key in registryKeys
            )
        }
    }

    @Test
    fun `каждый переключатель известен SettingsState`() {
        val registryKeys = SettingsRegistry.entries
            .mapNotNull { (it.kind as? SettingKind.Switch)?.key }

        registryKeys.forEach { key ->
            assertTrue(
                "Ключ '$key' есть в реестре, но не обрабатывается в SettingsState. " +
                    "Переключатель будет всегда выключен.",
                key in SettingsState.SWITCH_KEYS
            )
        }
    }

    @Test
    fun `в SettingsState нет лишних ключей`() {
        val registryKeys = SettingsRegistry.entries
            .mapNotNull { (it.kind as? SettingKind.Switch)?.key }.toSet()

        SettingsState.SWITCH_KEYS.forEach { key ->
            assertTrue(
                "Ключ '$key' обрабатывается в SettingsState, но отсутствует в реестре — " +
                    "настройка недоступна пользователю.",
                key in registryKeys
            )
        }
    }

    @Test
    fun `каждая категория содержит хотя бы одну настройку`() {
        SettingsCategory.entries.forEach { category ->
            assertTrue(
                "Категория ${category.name} пуста — пользователь откроет пустой экран",
                SettingsRegistry.byCategory(category).isNotEmpty()
            )
        }
    }

    /*
     * ПОРОГ ПОДНЯТ С СЕМИ ДО ДЕСЯТИ — И ЭТО НЕ ПОДГОНКА ПОД КОД.
     *
     * Прежнее правило («не больше семи разделов») ловило не ту беду.
     * При семи разделах и полусотне настроек список внутри раздела
     * разрастается: в «Вызовах» оказалось двадцать пять пунктов из семи
     * разных областей, и найти там что-то можно было только прокруткой
     * сверху донизу. Ограничение на число разделов эту беду создавало,
     * а не предотвращало.
     *
     * Настоящее требование другое: раздел не должен быть свалкой.
     * Поэтому правил теперь два — предел на число разделов ослаблен,
     * а на размер раздела добавлен. Плюс на главном экране настроек
     * есть поиск: он ищет по всем разделам сразу, и цена лишнего
     * раздела ниже, чем цена переполненного.
     */
    @Test
    // 2.1.0: одиннадцатый — «SMS». Это раздел нижней панели, а не тема
    // существующего: в «Журнале вызовов» его настройки читались как звонковые.
    // 2.7.5: двенадцатый — «Экран вызова» (решение автора): «Вызовы» упёрлись
    // в предел 16 пунктов, вид экрана звонка — отдельная тема
    // 2.8.6: «Расширенные настройки» не считаются — это второй уровень всех
    // разделов, а не ещё одна тема
    fun `категорий не больше двенадцати`() {
        val themes = SettingsCategory.entries.filter { it != SettingsCategory.ADVANCED }
        assertTrue(
            "Категорий стало ${themes.size}. " +
                "Прежде чем добавлять новую, посмотрите, не тема ли это " +
                "существующей.",
            themes.size <= 12
        )
    }

    @Test
    fun `в разделе не больше шестнадцати настроек`() {
        /*
         * С 1.8.0 все пункты раздела на виду (автор: «в настройках не надо
         * прятать пункты, их у нас не так много»; «Дополнительно» 1.5.8
         * убрано). Совсем без предела раздел не остаётся: четырнадцать
         * строк — это уже прокрутка на два экрана, и раздел пора делить.
         *
         * 2.7.4: было 14, стало 16 — «Вызовы» получили два пункта (свой фон
         * входящих и исходящих, решение владельца) и стояли ровно на пределе.
         * Раздел поделён на группы («Во время вызова», «После вызова»),
         * так что прокрутка читается по заголовкам; следующий пункт —
         * повод делить раздел.
         *
         * 2.7.5: поделён — вид экрана звонка (фото, фон, скрытый номер)
         * вынесен в «Экран вызова», в «Вызовах» 10 пунктов.
         */
        /*
         * 2.8.6: основное и дополнительное (автор: «в основных категориях
         * оставить важные и основные настройки… а всё дополнительное убрать
         * в „Расширенные настройки“»). Предел — на то, что видно в разделе
         * сразу: не больше восьми. «Расширенные» — длинный экран, но поделённый
         * на разделы; предел — на группу одного раздела.
         */
        SettingsCategory.entries.filter { it != SettingsCategory.ADVANCED }.forEach { category ->
            val all = SettingsRegistry.byCategory(category)
            assertTrue(
                "В разделе ${category.name} ${all.size} основных настроек. " +
                    "Редкое — в «Расширенные» (advanced = true).",
                all.size <= 8
            )
            val extra = SettingsRegistry.advancedOf(category)
            assertTrue(
                "В «Расширенных» у раздела ${category.name} ${extra.size} пунктов — " +
                    "такой список читают прокруткой, стоит разделить по смыслу.",
                extra.size <= 8
            )
        }
    }

    @Test
    fun `каждая настройка ровно на одном экране`() {
        // 2.8.6: пункт в своём разделе или в «Расширенных» — не в обоих и не нигде
        val shown = SettingsCategory.entries.flatMap { SettingsRegistry.byCategory(it) }.map { it.id }
        assertEquals(SettingsRegistry.entries.map { it.id }.sorted(), shown.sorted())
        SettingsRegistry.entries.forEach { entry ->
            assertTrue(
                "Пункт '${entry.id}' не на своём экране",
                entry in SettingsRegistry.byCategory(SettingsRegistry.screenOf(entry))
            )
        }
    }

    @Test
    fun `у «Расширенных» нет своих пунктов`() {
        // Раздел пункта — настоящий; в «Расширенные» его выносит advanced
        assertTrue(SettingsRegistry.entries.none { it.category == SettingsCategory.ADVANCED })
    }

    @Test
    fun `главное о звонках и SMS — не в «Расширенных»`() {
        // Решение автора 21.11.2026: в основных разделах — важное. Эти пункты
        // ищут чаще всего; уйти в «Расширенные» они не должны и по ошибке
        val main = listOf(
            "theme", "font_scale", "language", "answer_mode", "swap_answer", "incoming_banner",
            "sms_default_app", "sms_notifications_off", "sms_text_scale", "block_list", "allow_list",
            "backup_export", "backup_import", "permissions", "battery"
        )
        main.forEach { id ->
            assertTrue("'$id' должен быть в основном разделе", SettingsRegistry.byId(id)?.advanced == false)
        }
    }

    @Test
    fun `без Google Play нет пункта «Оценить», а «Версия» остаётся`() {
        // 2.1.0: ссылка в магазин без магазина — мёртвая; «Версия»
        // при этом ведёт в «О приложении» и пропасть не должна
        val about = SettingsRegistry.byCategory(SettingsCategory.ABOUT)
        val withoutPlay = SettingsRegistry.shown(about, fromGooglePlay = false).map { it.id }
        assertTrue("rate_app" !in withoutPlay)
        assertTrue("version" in withoutPlay)
        assertEquals(about, SettingsRegistry.shown(about, fromGooglePlay = true))
    }

    @Test
    fun `опасные настройки имеют объяснение`() {
        val dangerous = SettingsRegistry.entries.filter { it.dangerous }
        assertTrue("Опасные настройки должны быть помечены", dangerous.isNotEmpty())
        dangerous.forEach {
            assertTrue(
                "Опасная настройка '${it.id}' должна иметь описание последствий",
                it.summaryRes != null
            )
        }
    }

    /**
     * Подгруппа внутри раздела — это подряд идущие пункты.
     *
     * Экран режет список по смене groupRes и не собирает группы заново.
     * Если пункт подгруппы оказался отдельно от своих (например, его
     * добавили в конец раздела), на экране появятся два одинаковых
     * заголовка — и это заметят не сразу.
     */
    @Test
    fun `подгруппы не разорваны`() {
        SettingsCategory.entries.forEach { category ->
            // 2.8.6: в «Расширенных» группа — раздел пункта (SettingsRegistry.groupKey)
            val groups = SettingsRegistry.byCategory(category).map { SettingsRegistry.groupKey(category, it) }

            val runs = mutableListOf<Int?>()
            groups.forEach { g -> if (runs.lastOrNull() != g || runs.isEmpty()) runs += g }

            val named = runs.filterNotNull()
            assertEquals(
                "В разделе ${category.name} подгруппа встречается в двух местах: " +
                    "пункты одной подгруппы должны стоять в реестре подряд",
                named.size,
                named.toSet().size
            )
        }
    }
}
