package com.ogneliss.benchmark

import android.content.ContentValues
import android.os.Build
import android.provider.CallLog
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * Тестовые звонки в журнал ЭМУЛЯТОРА перед съёмкой профиля (1.3.4, 05.10.2026).
 *
 * Профиль запуска снимается на чистом эмуляторе с пустым журналом: в профиль
 * попадали шапка и нижняя панель, но не строки журнала, свайп и бросок
 * (CallLogCard, SwipeRevealRow — 0 правил). У людей после установки строки —
 * большая часть первого кадра, и без профиля этот код выполняется без
 * предкомпиляции.
 *
 * ⚠️ ТОЛЬКО ЭМУЛЯТОР. Генератор запускается и на телефоне автора, где сразу
 * пропускается — но подготовка устройства (@Before) к этому моменту уже
 * прошла. Поэтому заполнение вызывается из блока съёмки (он выполняется
 * только там, где генератор работает), и вдобавок здесь проверка железа
 * эмулятора: в настоящий журнал телефона ничего не пишется ни при каких
 * условиях.
 */
object CallLogSeed {

    private const val ROWS = 40

    /** Эмулятор Android: виртуальное железо ranchu (новые образы) или goldfish. */
    private val isEmulator: Boolean
        get() = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"

    fun ensureOnEmulator() {
        if (!isEmulator) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val device = UiDevice.getInstance(instrumentation)
        listOf("READ_CALL_LOG", "WRITE_CALL_LOG").forEach {
            device.executeShellCommand("pm grant ${context.packageName} android.permission.$it")
        }

        val resolver = context.contentResolver
        val existing = resolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID), null, null, null)
            ?.use { it.count } ?: 0
        if (existing >= ROWS) return

        // Явно тестовые номера; повторы — чтобы журнал группировал, как у людей
        val types = listOf(
            CallLog.Calls.INCOMING_TYPE, CallLog.Calls.OUTGOING_TYPE,
            CallLog.Calls.MISSED_TYPE, CallLog.Calls.OUTGOING_TYPE
        )
        val now = System.currentTimeMillis()
        val hour = 60L * 60 * 1000
        repeat(ROWS - existing) { i ->
            val values = ContentValues().apply {
                put(CallLog.Calls.NUMBER, "+7900000%04d".format(i % 15))
                put(CallLog.Calls.TYPE, types[i % types.size])
                // Разброс на две недели: группы «Сегодня», «Вчера», «На этой неделе»…
                put(CallLog.Calls.DATE, now - i * 8 * hour)
                put(CallLog.Calls.DURATION, if (types[i % types.size] == CallLog.Calls.MISSED_TYPE) 0 else 30 + i * 7)
                put(CallLog.Calls.NEW, 0)
            }
            resolver.insert(CallLog.Calls.CONTENT_URI, values)
        }
    }
}
