package com.ogneliss.dialer.log

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Перехватчик падений (2.9.7): «Process 'Gradle Test Executor N' finished with
 * non-zero exit value 2» — без предыдущего обработчика он вызывал exitProcess(2),
 * а Robolectric ставил его заново в каждом тесте. Разбор — CrashHandler.install.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashHandlerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var original: Thread.UncaughtExceptionHandler? = null

    @Before
    fun remember() {
        original = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun restore() {
        Thread.setDefaultUncaughtExceptionHandler(original)
    }

    @Test
    fun `повторная установка не оборачивает обработчик снова`() {
        CrashHandler.install(context)
        val first = Thread.getDefaultUncaughtExceptionHandler()
        CrashHandler.install(context)
        CrashHandler.install(context)
        assertSame(first, Thread.getDefaultUncaughtExceptionHandler())
    }

    @Test
    fun `без предыдущего обработчика процесс не завершается`() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        CrashHandler.install(context)
        // Раньше здесь был exitProcess(2) — весь процесс тестов падал с кодом 2
        Thread.getDefaultUncaughtExceptionHandler()!!
            .uncaughtException(Thread.currentThread(), IllegalStateException("проверка перехватчика"))
    }
}
