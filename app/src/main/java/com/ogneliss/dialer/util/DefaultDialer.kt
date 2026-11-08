package com.ogneliss.dialer.util

import com.ogneliss.dialer.log.Logger
import android.provider.Settings
import android.net.Uri
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager

/**
 * Запрос роли "Телефон по умолчанию".
 * Android 10+ — RoleManager, ниже — ACTION_CHANGE_DEFAULT_DIALER.
 */
object DefaultDialer {

    const val REQUEST_CODE = 4201

    /** Метка для журнала: по ней отбирают записи при разборе проблем. */
    private const val TAG = "DefaultDialer"

    fun isDefault(context: Context): Boolean {
        val tm = context.getSystemService(TelecomManager::class.java)
        return context.packageName == tm?.defaultDialerPackage
    }

    /**
     * ЗАПРОС РОЛИ ТЕЛЕФОНА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ЗДЕСЬ ЗАПАСНОЙ ПУТЬ
     * ══════════════════════════════════════════════════════════════════════
     * Системный диалог выбора приложения по умолчанию открывается
     * не всегда. Отказ бывает молчаливым:
     *
     * • роль уже запрашивали и отклонили — часть прошивок больше
     *   не показывает диалог, а просто возвращает отказ;
     * • производитель подменил экран выбора своим и сломал намерение;
     * • на устройстве есть ограничения администратора.
     *
     * Раньше в этих случаях не происходило НИЧЕГО: человек нажимал
     * кнопку и оставался с тем же экраном, не понимая, что пошло не так.
     * Приходилось искать обходной путь через системные настройки —
     * ровно то, с чем вы и столкнулись.
     *
     * Теперь при неудаче открывается системный экран приложений
     * по умолчанию: длиннее, но приводит к цели.
     *
     * @return true, если открылся диалог роли; false, если пришлось
     *         уйти в настройки — интерфейс тогда подскажет, что делать
     */
    fun request(activity: Activity): Boolean {
        if (isDefault(activity)) return true

        val launched = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = activity.getSystemService(RoleManager::class.java)
                    ?: return@runCatching false

                if (!rm.isRoleAvailable(RoleManager.ROLE_DIALER)) return@runCatching false

                // Время запроса — чтобы отличить «окно не показалось» от отказа (RoleAccess)
                RoleAccess.markRequested()
                activity.startActivityForResult(
                    rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQUEST_CODE
                )
                true
            } else {
                @Suppress("DEPRECATION")
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(
                        TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                        activity.packageName
                    )
                // Проверяем, что намерение кто-то обработает: на части
                // прошивок этот экран отсутствует, и запуск бросит
                // ActivityNotFoundException
                if (intent.resolveActivity(activity.packageManager) == null) {
                    return@runCatching false
                }
                RoleAccess.markRequested()
                activity.startActivityForResult(intent, REQUEST_CODE)
                true
            }
        }.getOrElse {
            Logger.w(TAG, "Диалог выбора приложения по умолчанию не открылся", it)
            false
        }

        // Молча уводить в настройки нельзя: человек не поймёт, куда попал
        // и что там делать. Объяснение показывает вызывающий экран
        return launched
    }

    /**
     * СИСТЕМНЫЙ ВЫБОР ПРИЛОЖЕНИЙ ПО УМОЛЧАНИЮ.
     *
     * ── Почему не «Изменение системных настроек» (1.8.1) ───────────────────
     * До 1.8.1 первым открывался экран «Изменение системных настроек»
     * (ACTION_MANAGE_WRITE_SETTINGS) — в расчёте, что оболочки Xiaomi,
     * Samsung, Huawei и nubia без него не дают сменить диалер. При проверке
     * прошивок это не подтвердилось: роль «Телефон» с этим разрешением
     * не связана, а приложение его и не объявляет (WRITE_SETTINGS нет
     * в манифесте), так что переключатель там серый — человек попадал
     * в тупик. Окно выбора роли не открывается по другим причинам:
     * выбор уже дважды отклоняли (система больше не спрашивает) или
     * оболочка заменила окно своим. Помогает одно — выбрать приложение
     * в «Приложениях по умолчанию» самому.
     *
     * Порядок попыток: выбор приложений по умолчанию, затем свойства
     * приложения.
     */
    fun openSystemDefaultApps(activity: Activity) {
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            // Не на всех прошивках есть экран приложений по умолчанию;
            // тогда открываем свойства приложения — оттуда до нужного
            // раздела один шаг
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
        )

        // 2.1.0: без предпроверки resolveActivity — с Android 11 она отвечает
        // «нет» и для существующих экранов; нет экрана — исключение, следующий
        for (intent in candidates) {
            val opened = runCatching {
                activity.startActivity(intent)
                true
            }.getOrDefault(false)

            if (opened) return
        }

        Logger.e(TAG, "Не удалось открыть системные настройки приложений по умолчанию")
    }
}
