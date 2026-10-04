package com.ogneliss.benchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
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

    /*
     * Все разрешения из AppPermissions — и обязательные, и нет.
     * Было (до 03.10.2026): шесть из PermissionGuard, без ANSWER_PHONE_CALLS
     * и READ_PHONE_NUMBERS, которые мастер тоже считает обязательными, —
     * и замеры опять мерили мастер (профиль: PermissionWizardScreenKt).
     * Разрешения, которых нет на этой версии Android, pm grant отклонит —
     * это не ошибка.
     */
    private val PERMISSIONS = listOf(
        "CALL_PHONE", "READ_PHONE_STATE", "ANSWER_PHONE_CALLS", "READ_PHONE_NUMBERS",
        "READ_CALL_LOG", "WRITE_CALL_LOG",
        "READ_CONTACTS", "WRITE_CONTACTS",
        "POST_NOTIFICATIONS", "BLUETOOTH_CONNECT"
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
        verifyMainScreen(device)
    }

    /*
     * Главная защита: после подготовки на экране должен быть журнал
     * (root_screen), а не мастер или экран согласия. Без этой проверки
     * ошибка подготовки видна только по профилю, и то не сразу.
     */
    private fun verifyMainScreen(device: UiDevice) {
        device.executeShellCommand("am start -W -n $PACKAGE/$PACKAGE.MainActivity")
        // 10 с, а не 5: первый запуск после согласия бывает дольше (04.10.2026)
        var main = device.wait(Until.findObject(By.res("root_screen")), 10_000)
        if (main == null) {
            /*
             * Сразу после выдачи разрешений приложение один раз показывает
             * подсказки поверх журнала (автор, 04.10.2026: это и был
             * «неизвестный экран» в тёплом запуске). Закрываем «Назад»
             * и проверяем снова.
             */
            device.pressBack()
            main = device.wait(Until.findObject(By.res("root_screen")), 3_000)
        }
        if (main == null) {
            val shown = when {
                device.findObject(By.res("permission_wizard")) != null ->
                    "мастер разрешений — не хватает разрешения или роли"
                device.findObject(By.res(CONSENT_ACCEPT)) != null -> "экран согласия"
                // Чужое окно поверх (системный запрос, диалог прошивки)
                device.currentPackageName != PACKAGE ->
                    "окно другого приложения: ${device.currentPackageName}"
                else -> "неизвестный экран Ogneliss"
            }
            val saved = saveScreen(device)
            error("После подготовки вместо журнала открылся $shown — замер был бы недостоверен. $saved")
        }
        device.executeShellCommand("am force-stop $PACKAGE")
    }

    /*
     * Снимок экрана и дерево окон при сбое проверки (04.10.2026): logcat
     * на телефоне не ведётся, а «неизвестный экран» без картинки
     * не разобрать. Папка additional_test_output забирается Gradle
     * в benchmark/build/outputs/connected_android_test_additional_output.
     */
    private fun saveScreen(device: UiDevice): String = runCatching {
        val dir = java.io.File(
            InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.first(),
            "additional_test_output"
        ).apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        device.takeScreenshot(java.io.File(dir, "setup-failed-$stamp.png"))
        device.dumpWindowHierarchy(java.io.File(dir, "setup-failed-$stamp.xml"))
        "Снимок и дерево окон: setup-failed-$stamp.png / .xml в выходных файлах замеров."
    }.getOrElse { "Снимок сохранить не удалось: ${it.message}" }

    /** Принять условия, если экран согласия показан; уже принято — ничего не делать. */
    private fun acceptConsent(device: UiDevice) {
        device.executeShellCommand("am start -W -n $PACKAGE/$PACKAGE.MainActivity")
        if (device.wait(Until.findObject(By.res(CONSENT_ACCEPT)), 5_000) == null) {
            device.executeShellCommand("am force-stop $PACKAGE")
            return
        }
        // Флажок может быть ниже края экрана: текст условий длинный
        if (device.findObject(By.res(CONSENT_CHECKBOX)) == null) {
            device.findObject(By.scrollable(true))
                ?.scrollUntil(Direction.DOWN, Until.findObject(By.res(CONSENT_CHECKBOX)))
        }
        device.waitForIdle()
        check(clickFresh(device, By.res(CONSENT_CHECKBOX))) {
            "Экран согласия: флажок «принимаю» не найден — замер был бы недостоверен"
        }
        device.waitForIdle()
        check(clickFresh(device, By.res(CONSENT_ACCEPT).enabled(true))) {
            "Экран согласия: кнопка «Принять» не нажалась"
        }
        check(device.wait(Until.gone(By.res(CONSENT_ACCEPT)), 5_000)) {
            "Экран согласия не закрылся после «Принять»"
        }
        device.executeShellCommand("am force-stop $PACKAGE")
    }

    /*
     * Нажатие с повтором (04.10.2026). На Compose 1.12 экран согласия
     * перерисовывается между поиском объекта и нажатием — найденный
     * UiObject2 устаревал: StaleObjectException в acceptConsent, тёплый
     * запуск не мерился. Каждая попытка ищет объект заново.
     */
    private fun clickFresh(device: UiDevice, selector: BySelector): Boolean {
        repeat(5) {
            val obj = device.wait(Until.findObject(selector), 2_000) ?: return@repeat
            try {
                obj.click()
                return true
            } catch (e: StaleObjectException) {
                device.waitForIdle()
            }
        }
        return false
    }

    private const val CONSENT_ACCEPT = "consent_accept"
    private const val CONSENT_CHECKBOX = "consent_checkbox"
}
