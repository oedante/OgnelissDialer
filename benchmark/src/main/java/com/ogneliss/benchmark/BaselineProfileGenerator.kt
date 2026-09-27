package com.ogneliss.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ГЕНЕРАТОР BASELINE PROFILE.
 *
 * Прогоняет типичные сценарии, записывая, какие методы при этом
 * выполняются. Результат — список для предварительной компиляции.
 *
 * ── Какие сценарии включать ────────────────────────────────────────────────
 * Только те, что происходят в первые секунды работы. Профиль не бесплатен:
 * он увеличивает время установки и размер приложения. Включать в него
 * редко используемые экраны — тратить бюджет впустую.
 *
 * Для диалера это: запуск, показ журнала, открытие клавиатуры, набор цифр.
 * Настройки и списки блокировки открывают редко, их в профиль не берём.
 *
 * ── Как запускать ──────────────────────────────────────────────────────────
 *   ./gradlew :app:generateBaselineProfile
 * Результат копируется в app/src/main/baseline-prof.txt и коммитится.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.ogneliss.dialer") {
        pressHome()
        startActivityAndWait()

        // Журнал вызовов — первое, что видит пользователь
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.scroll(Direction.DOWN, 0.6f)
            device.waitForIdle()
        }

        // Набор номера — второй по частоте сценарий
        listOf("2", "9", "5").forEach { digit ->
            device.findObject(By.text(digit))?.click()
            device.waitForIdle()
        }
    }
}
