package com.ogneliss.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeFalse
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
    fun startupNoCompilation() {
        /*
         * Холодный запуск меряется отдельным ПЕРВЫМ прогоном, на остывшем
         * телефоне (04.10.2026): после 6 минут других замеров он «вырос»
         * на 18,7% при неизменном тёплом запуске — нагрев, а не код
         * (повторы базы росли 207 → 266 мс внутри одного прогона).
         * Во втором прогоне phone_check.ps1 передаёт skipColdStart=true.
         * Свой аргумент, а не notClass: список через запятую в -P
         * до телефона не доходит.
         */
        assumeFalse(
            "Холодный запуск меряется отдельным первым прогоном",
            InstrumentationRegistry.getArguments().getString("skipColdStart") == "true"
        )
        measureStartup(CompilationMode.None())
    }

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

        /*
         * Жест по координатам, а не UiObject2.scroll (04.10.2026): на Compose 1.12
         * найденный объект списка устаревал между прокрутками, и замер падал
         * без текста ошибки. От 3/4 до 1/4 высоты по центру — середина
         * журнала, мимо нижней панели; вертикальный жест не задевает свайпы
         * строк. 20 шагов — плавный палец, а не рывок.
         */
        val x = device.displayWidth / 2
        val from = device.displayHeight * 3 / 4
        val to = device.displayHeight / 4
        repeat(3) {
            device.swipe(x, from, x, to, 20)
            device.waitForIdle()
        }
    }
}
