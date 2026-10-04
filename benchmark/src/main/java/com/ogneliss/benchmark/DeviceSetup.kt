package com.ogneliss.benchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

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
 * 1.3.3, 03.10.2026, второе исправление: на свежей установке до журнала
 * стоит ещё экран согласия с условиями. Профиль запуска на эмуляторе
 * собрался почти целиком из ConsentScreen, замеры экранов мерили его же.
 * Теперь условия принимаются один раз до замеров — флажок и кнопка
 * находятся по меткам consent_checkbox и consent_accept.
 *
 * И третье: benchmark 1.4.1 кладёт на телефон свой trace_processor_shell,
 * а оставшийся от 1.3.3 не перезаписывается — все замеры падали
 * с «md5 sums don't match». Старый файл удаляется один раз за запуск.
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

    /*
     * Старый trace_processor_shell от benchmark 1.3.3 остаётся
     * в /data/local/tmp, и 1.4.1 не может его перезаписать — все замеры
     * падали с «md5 sums don't match» (03.10.2026). Удаляем один раз
     * за запуск: библиотека копирует файл при первом замере и дальше
     * им пользуется — удалять между замерами нельзя.
     */
    private var traceProcessorCleaned = false

    fun prepare() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (!traceProcessorCleaned) {
            device.executeShellCommand("rm -f /data/local/tmp/trace_processor_shell")
            traceProcessorCleaned = true
        }
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
        acceptConsent(device)
    }

    /** Принять условия, если экран согласия показан; уже принято — ничего не делать. */
    private fun acceptConsent(device: UiDevice) {
        device.executeShellCommand("am start -W -n $PACKAGE/$PACKAGE.MainActivity")
        if (device.wait(Until.findObject(By.res(CONSENT_ACCEPT)), 5_000) == null) {
            device.executeShellCommand("am force-stop $PACKAGE")
            return
        }
        // Флажок может быть ниже края экрана: текст условий длинный
        val box = device.findObject(By.res(CONSENT_CHECKBOX))
            ?: device.findObject(By.scrollable(true))
                ?.scrollUntil(Direction.DOWN, Until.findObject(By.res(CONSENT_CHECKBOX)))
        checkNotNull(box) { "Экран согласия: флажок «принимаю» не найден — замер был бы недостоверен" }
        box.click()
        device.waitForIdle()
        device.findObject(By.res(CONSENT_ACCEPT)).click()
        check(device.wait(Until.gone(By.res(CONSENT_ACCEPT)), 5_000)) {
            "Экран согласия не закрылся после «Принять»"
        }
        device.executeShellCommand("am force-stop $PACKAGE")
    }

    private const val CONSENT_ACCEPT = "consent_accept"
    private const val CONSENT_CHECKBOX = "consent_checkbox"
}
