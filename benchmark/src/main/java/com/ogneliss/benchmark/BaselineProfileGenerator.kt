package com.ogneliss.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Before
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

    // Разрешения и роль — до первого запуска, иначе меряется мастер
    // разрешений (1.3.3, DeviceSetup)
    @Before
    fun prepareDevice() = DeviceSetup.prepare()

    @Test
    /*
     * includeInStartupProfile = true — вдобавок к обычному профилю
     * собирается профиль запуска.
     *
     * Обычный профиль говорит системе, какие методы скомпилировать заранее.
     * Профиль запуска говорит R8, в каком порядке раскладывать код внутри
     * DEX-файла: всё нужное для первого экрана — в начало, одним куском.
     * Так при холодном старте с диска читается меньше страниц. Для диалера,
     * которого открывают, когда нужно позвонить сейчас, это заметно.
     *
     * Без флага сборка профиля сама предупреждала: «No startup profile
     * rules were generated».
     */
    fun generate() = rule.collect(
        packageName = "com.ogneliss.dialer",
        includeInStartupProfile = true
    ) {
        // Журнал эмулятора — тестовыми звонками, иначе строки журнала и свайп
        // не попадают в профиль (1.3.4). Здесь, а не в @Before: этот блок
        // выполняется только там, где генератор работает, — не на телефоне
        CallLogSeed.ensureOnEmulator()

        pressHome()
        startActivityAndWait()

        // Журнал вызовов — первое, что видит пользователь. Жест по координатам,
        // а не UiObject2.scroll: на Compose 1.12 объект списка устаревал (1.3.4)
        val x = device.displayWidth / 2
        repeat(2) {
            device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 20)
            device.waitForIdle()
        }

        // Набор номера — второй по частоте сценарий
        listOf("2", "9", "5").forEach { digit ->
            device.findObject(By.text(digit))?.click()
            device.waitForIdle()
        }
    }
}
