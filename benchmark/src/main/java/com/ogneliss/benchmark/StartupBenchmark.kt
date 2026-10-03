package com.ogneliss.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ЗАМЕРЫ ХОЛОДНОГО СТАРТА И ПРОКРУТКИ.
 *
 * ── Почему это важно именно для диалера ────────────────────────────────────
 * Самая частая жалоба в отзывах на сторонние номеронабиратели —
 * «долго открывается» и «лаги при наборе». Функциональность при этом
 * у всех примерно одинаковая. Скорость и есть главное конкурентное
 * преимущество, а её нельзя улучшать вслепую.
 *
 * Заявленные в дорожной карте цели:
 *   • холодный старт до интерактивности < 400 мс
 *   • ноль пропущенных кадров при прокрутке журнала
 *
 * ── Как запускать ──────────────────────────────────────────────────────────
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 *
 * Только на реальном устройстве: эмулятор даёт цифры,
 * не имеющие отношения к действительности.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    // Разрешения и роль — до первого запуска, иначе меряется мастер
    // разрешений (1.3.3, DeviceSetup)
    @Before
    fun prepareDevice() = DeviceSetup.prepare()

    private companion object {
        const val PACKAGE = "com.ogneliss.dialer"
        const val ITERATIONS = 10
    }

    /**
     * Холодный старт БЕЗ профиля — базовая точка отсчёта.
     * Показывает, что видит пользователь, если Baseline Profile не работает.
     */
    @Test
    fun startupNoCompilation() = measureStartup(CompilationMode.None())

    /**
     * Холодный старт С Baseline Profile.
     * Разница с предыдущим тестом и есть польза от профиля.
     */
    @Test
    fun startupWithBaselineProfile() =
        measureStartup(CompilationMode.Partial())

    private fun measureStartup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = mode
    ) {
        pressHome()
        startActivityAndWait()
    }

    /**
     * Прокрутка журнала вызовов.
     *
     * Измеряется время отрисовки кадров. Цель — отсутствие кадров,
     * выпадающих за бюджет: именно они воспринимаются как «дёрганье».
     */
    @Test
    fun scrollCallLog() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM
    ) {
        startActivityAndWait()

        val list = device.findObject(By.scrollable(true)) ?: return@measureRepeated
        // Настройка жеста: без неё UiAutomator скроллит рывками,
        // и метрика измеряет не прокрутку, а сам тест
        list.setGestureMargin(device.displayWidth / 5)

        repeat(3) {
            list.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
        }
    }
}
