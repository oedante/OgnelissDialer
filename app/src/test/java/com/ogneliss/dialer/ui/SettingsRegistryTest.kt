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
    fun `категорий не больше десяти`() {
        assertTrue(
            "Категорий стало ${SettingsCategory.entries.size}. " +
                "Прежде чем добавлять новую, посмотрите, не тема ли это " +
                "существующей.",
            SettingsCategory.entries.size <= 10
        )
    }

    @Test
    fun `в разделе не больше десяти настроек`() {
        SettingsCategory.entries.forEach { category ->
            val size = SettingsRegistry.byCategory(category).size
            assertTrue(
                "В разделе ${category.name} ${size} настроек. " +
                    "Такой список читают прокруткой, а не взглядом — " +
                    "стоит разделить по смыслу.",
                size <= 10
            )
        }
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
            val groups = SettingsRegistry.byCategory(category).map { it.groupRes }

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
