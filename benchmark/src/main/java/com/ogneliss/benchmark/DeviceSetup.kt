package com.ogneliss.benchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * Подготовка телефона перед замерами (1.3.3, 03.10.2026).
 *
 * Было: после установки сборки для замеров у приложения нет разрешений
 * и роли приложения для набора номера по умолчанию. MainActivity в этом
 * случае показывает мастер разрешений, а флаг «мастер пройден» живёт
 * только в памяти — каждый запуск снова открывал мастер. Замеры мерили
 * его, а не журнал (замечание автора: «при запуске нужно дать разрешения,
 * а в это время уже идут замеры»). Цифры BASELINE_1.3.2 и шага 1
 * поэтому недостоверны.
 *
 * Стало: перед замерами разрешения выдаются через `pm grant`, роль —
 * через `cmd role`. Если роль не назначилась, замер падает с понятной
 * ошибкой, а не меряет мастер.
 *
 * Константы повторяют приложение: модуль замеров не видит его классов.
 */
object DeviceSetup {

    private const val PACKAGE = "com.ogneliss.dialer"
    private const val ROLE_DIALER = "android.app.role.DIALER"

    /** Те же, что проверяет мастер разрешений (PermissionGuard). */
    private val PERMISSIONS = listOf(
        "READ_CALL_LOG", "WRITE_CALL_LOG",
        "READ_CONTACTS", "WRITE_CONTACTS",
        "CALL_PHONE", "READ_PHONE_STATE"
    )

    fun prepare() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        PERMISSIONS.forEach { name ->
            device.executeShellCommand("pm grant $PACKAGE android.permission.$name")
        }
        device.executeShellCommand("cmd role add-role-holder --user 0 $ROLE_DIALER $PACKAGE")
        val holders = device.executeShellCommand("cmd role get-role-holders --user 0 $ROLE_DIALER")
        check(PACKAGE in holders) {
            "Ogneliss не стал приложением для набора номера по умолчанию " +
                "(держатель роли: '${holders.trim()}'). Без роли приложение " +
                "показывает мастер разрешений, и замер был бы недостоверен. " +
                "Назначьте вручную: adb shell cmd role add-role-holder " +
                "--user 0 $ROLE_DIALER $PACKAGE"
        }
    }
}
