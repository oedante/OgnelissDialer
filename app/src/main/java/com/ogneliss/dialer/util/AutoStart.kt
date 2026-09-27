package com.ogneliss.dialer.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.ogneliss.dialer.log.Logger

/**
 * АВТОЗАПУСК НА ОБОЛОЧКАХ ПРОИЗВОДИТЕЛЕЙ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ЭТО НУЖНО ДИАЛЕРУ
 * ══════════════════════════════════════════════════════════════════════════
 * Приложение следит за журналом через ContentObserver. Чтобы наблюдатель
 * проснулся после пропущенного звонка, система должна разрешить процессу
 * запуститься — а оболочки Xiaomi, Huawei, Oppo и подобные по умолчанию
 * это запрещают.
 *
 * Без разрешения пропущенные не появляются, пока человек сам не откроет
 * приложение. Он решает, что журнал теряет вызовы, и удаляет его.
 *
 * ── Почему нельзя проверить, выдано ли ─────────────────────────────────────
 * Общего способа нет. У каждого производителя своя оболочка, свой экран
 * и своё хранилище настройки — наружу состояние не отдаётся.
 *
 * Поэтому мы только открываем экран. Отметить шаг выполненным нельзя:
 * галочка, которая ничего не проверяет, обманывает.
 *
 * ── Почему списки заданы вручную ───────────────────────────────────────────
 * Стандарта нет, документации нет. Эти имена компонентов собраны
 * сообществом разработчиков и проверены на практике; часть перестаёт
 * работать при смене версии оболочки — поэтому вариантов несколько,
 * и есть запасной путь в свойства приложения.
 */
object AutoStart {

    private const val TAG = "AutoStart"

    /**
     * Производители, у которых автозапуск нужно выдавать вручную.
     *
     * Список неполон намеренно: сюда попадают только те, чьи
     * ограничения известны и подтверждены. Показывать шаг там, где
     * экрана нет, значит вести человека в никуда.
     */
    private val AGGRESSIVE = setOf(
        "xiaomi", "redmi", "poco",
        "huawei", "honor",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo",
        "meizu", "asus", "nubia", "zte", "letv", "samsung"
    )

    /** Есть ли на этом телефоне экран автозапуска. */
    fun isSupported(): Boolean =
        Build.MANUFACTURER.lowercase() in AGGRESSIVE ||
            Build.BRAND.lowercase() in AGGRESSIVE

    /**
     * Открытие экрана автозапуска.
     *
     * Пробуем по очереди: экран оболочки, затем свойства приложения.
     * Первый сработавший открывается — молчаливого отказа не остаётся.
     */
    fun open(context: Context) {
        val candidates = componentsFor(Build.MANUFACTURER.lowercase()) + listOf(
            // Запасной путь: свойства приложения есть везде, и оттуда
            // человек доберётся до нужного раздела сам
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            )
        )

        for (intent in candidates) {
            val opened = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) {
                    return@runCatching false
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)

            if (opened) return
        }

        Logger.w(TAG, "Экран автозапуска не найден на ${Build.MANUFACTURER}")
    }

    /**
     * Экраны автозапуска по производителям.
     *
     * У одного производителя бывает несколько вариантов: имена
     * компонентов менялись между версиями оболочки, и на руках у людей
     * все они одновременно.
     */
    private fun componentsFor(manufacturer: String): List<Intent> {
        val components: List<Pair<String, String>> = when {
            manufacturer in setOf("xiaomi", "redmi", "poco") -> listOf(
                "com.miui.securitycenter" to
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )

            manufacturer in setOf("huawei", "honor") -> listOf(
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )

            manufacturer in setOf("oppo", "realme") -> listOf(
                "com.coloros.safecenter" to
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.oppo.safe" to
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
            )

            manufacturer in setOf("vivo", "iqoo") -> listOf(
                "com.vivo.permissionmanager" to
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )

            manufacturer == "oneplus" -> listOf(
                "com.oneplus.security" to
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )

            manufacturer == "asus" -> listOf(
                "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity"
            )

            manufacturer == "letv" -> listOf(
                "com.letv.android.letvsafe" to
                    "com.letv.android.letvsafe.AutobootManageActivity"
            )

            else -> emptyList()
        }

        return components.map { (pkg, cls) ->
            Intent().setComponent(ComponentName(pkg, cls))
        }
    }
}
