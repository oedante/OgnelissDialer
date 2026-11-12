package com.ogneliss.dialer.ui

import com.ogneliss.dialer.ui.settings.SettingsRegistry
import com.ogneliss.dialer.ui.settings.settingIcon
import org.junit.Assert.assertTrue
import org.junit.Test

/** У каждой настройки реестра есть значок (2.2.2): строка без значка выбивалась бы из столбца. */
class SettingIconsTest {

    @Test
    fun уКаждойНастройкиЕстьЗначок() {
        val missing = SettingsRegistry.entries.map { it.id }.filter { settingIcon(it) == null }
        assertTrue("Нет значка у настроек: $missing", missing.isEmpty())
    }
}
