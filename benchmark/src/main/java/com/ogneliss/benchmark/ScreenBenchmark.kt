package com.ogneliss.benchmark

import android.content.Intent
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Базовые замеры экранов (1.3.2, 01.10.2026) — для сравнения «до/после»
 * в фазах «Новые инструменты» и «Архитектура и интерфейс».
 *
 * Все экраны открываются через Intent, без нажатий. Нажатие на строку
 * журнала или свайп в замере могли бы начать настоящий звонок.
 *
 * Карточке нужен номер, который есть в контактах телефона:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.cardNumber=+79XXXXXXXXX
 * Без номера замер карточки пропускается.
 *
 * Константы повторяют MainActivity: модуль замеров не видит классы
 * приложения и должен работать с ним как внешний процесс.
 */
@RunWith(AndroidJUnit4::class)
class ScreenBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    // Разрешения и роль — до первого запуска, иначе меряется мастер
    // разрешений (1.3.3, DeviceSetup)
    @Before
    fun prepareDevice() = DeviceSetup.prepare()

    private companion object {
        const val PACKAGE = "com.ogneliss.dialer"
        const val ITERATIONS = 10
        const val ACTION_OPEN_CARD = "com.ogneliss.dialer.OPEN_CARD"
        const val EXTRA_CARD_NUMBER = "card_number"
        const val EXTRA_OPEN_DIALPAD = "open_dialpad"
    }

    /** Тёплый запуск: процесс жив, активность создаётся заново. */
    @Test
    fun startupWarm() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM
    ) {
        pressHome()
        startActivityAndWait()
    }

    /** Открытие клавиатуры — так же, как кнопка «Клавиатура» с экрана вызова. */
    @Test
    fun openDialpad() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM
    ) {
        pressHome()
        startActivityAndWait(launchIntent().putExtra(EXTRA_OPEN_DIALPAD, true))
    }

    /** Открытие карточки абонента — как из уведомления о пропущенном. */
    @Test
    fun openCard() {
        val number = InstrumentationRegistry.getArguments().getString("cardNumber")
        assumeTrue("Не задан cardNumber — замер карточки пропущен", !number.isNullOrBlank())
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM
        ) {
            pressHome()
            startActivityAndWait(
                launchIntent()
                    .setAction(ACTION_OPEN_CARD)
                    .putExtra(EXTRA_CARD_NUMBER, number)
            )
        }
    }

    private fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN)
            .setPackage(PACKAGE)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(PACKAGE, "$PACKAGE.MainActivity")
}
