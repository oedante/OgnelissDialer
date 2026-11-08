package com.ogneliss.dialer.sms

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard

/**
 * SMS В ДИАЛЕРЕ (2.0.0) — ЧТО МОЖНО И КОГДА.
 *
 * Автор, 05.11.2026: «добавить SMS — редактирование и отправку…
 * ориентировать на журнал звонков и журнал SMS с поддержкой свайпов»;
 * выбрано: «по выбору человека», SMS «в одном журнале с вызовами», MMS —
 * «приём и отправка».
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ДВА РЕЖИМА — САМИ, БЕЗ ВЫБОРА ЧЕЛОВЕКА (2.1.0)
 * ══════════════════════════════════════════════════════════════════════════
 * Было (2.0.0): переключатель «Писать SMS в диалере» — отправка из своей
 * переписки при одной роли телефона (SEND_SMS), без истории и входящих;
 * в переписке — плашка «сделайте приложением SMS» и окно SEND_SMS.
 * Стало: режим следует из роли, человеку выбирать нечего (решение
 * автора: «пользователь не должен думать и выбирать, чтобы приложение
 * работало как надо — с сервисами Google или без»):
 *
 * • РОЛЬ SMS У НАС — переписки, приём, MMS, уведомления; «Написать»
 *   открывает свою переписку. Роль обязывает само принимать SMS и MMS
 *   и записывать их в хранилище (SmsDeliverReceiver, MmsTransport) —
 *   иначе сообщения потеряются. Чтение — только пока роль у нас
 *   ([canRead] при каждом чтении), как требует политика Google Play
 *   («SMS and Call Log permissions»).
 * • РОЛЬ У ДРУГОГО ПРИЛОЖЕНИЯ — режим спутника: «Написать» открывает
 *   переписку в приложении SMS по умолчанию ([companionTarget]), без окон
 *   SEND_SMS и без плашек «сделайте по умолчанию».
 *
 * ── Когда роль просится сама ───────────────────────────────────────────────
 * Сразу после роли телефона (MainActivity) — только если сейчас
 * по умолчанию не приложение с чатами RCS ([autoOfferAllowed]): взяв
 * роль, диалер молча выключил бы человеку RCS (сторонним приложениям
 * RCS недоступен). Там роль — только по явной просьбе («Вести SMS
 * в Ogneliss» в разделе SMS, настройки, «Разрешения»).
 */
object SmsRole {

    private const val TAG = "SmsRole"

    /** Код запроса роли из настроек (у роли телефона — 4201). */
    const val REQUEST_CODE = 4202

    /**
     * Код запроса роли без просьбы человека — сразу за ролью телефона
     * (2.1.0). Отдельный, чтобы по ответу отличить: у такого запроса отказ
     * не объясняется окном (RoleHelpDialog) — пропускается и пишется в журнал.
     */
    const val AUTO_REQUEST_CODE = 4204

    /**
     * ПРИЛОЖЕНИЯ SMS С ЧАТАМИ RCS (2.1.0).
     *
     * Было: роль SMS просилась сама у всех сразу после роли телефона.
     * На телефоне с Google Сообщениями или Samsung Messages это молча
     * выключало чаты RCS: они работают только в своём приложении и только
     * пока оно — приложение SMS по умолчанию.
     * Стало: при таком приложении по умолчанию роль сама не просится.
     * Без сервисов Google (AOSP «Сообщения», LineageOS, GrapheneOS, /e/OS,
     * Huawei и другие) RCS нет — там всё как раньше.
     *
     * Список — по известным приложениям с RCS; появится новое — дописать.
     */
    val RCS_APPS: Set<String> = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )

    /**
     * Диалер — приложение SMS по умолчанию.
     *
     * Android 10+: роль SMS у нас (RoleManager) — это и показывают
     * «Приложения по умолчанию». Telephony.getDefaultSmsPackage на части
     * прошивок (замечено: nubia, Android 15) отвечает иначе, хотя роль
     * выдана, — по нему одному приложение считало себя не SMS и показывало
     * «сделайте приложением SMS» человеку, который уже сделал. Поэтому
     * роль — главный признак, ответ телефонии — запасной (Android 8–9).
     */
    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val held = runCatching {
                context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
            }.getOrDefault(false)
            if (held) return true
        }
        return runCatching { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName }
            .getOrDefault(false)
    }

    /** Пакет приложения SMS по умолчанию по ответу телефонии; null — нет такого. */
    fun defaultSmsPackage(context: Context): String? =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Можно ли попросить роль SMS без просьбы человека (2.1.0): по умолчанию
     * сейчас не приложение с RCS ([RCS_APPS]). Приложения SMS нет вовсе
     * (планшет) — тоже можно: тогда и окна роли не будет (request → null).
     */
    fun autoOfferAllowed(context: Context): Boolean {
        val current = defaultSmsPackage(context)
        val allowed = current == null || current !in RCS_APPS
        if (!allowed) Logger.i(TAG, "Роль SMS сама не просится: по умолчанию $current (RCS)")
        return allowed
    }

    /**
     * Для журнала: роль и ответ телефонии расходятся — так видно в логе,
     * почему SMS не приходят (телефония отдаёт их другому приложению).
     */
    fun diagnostics(context: Context): String {
        val held = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS)
        }.getOrNull() else null
        val telephony = defaultSmsPackage(context)
        val same = telephony == context.packageName
        return "роль SMS: ${held ?: "—"}, телефония: ${if (same) "мы" else telephony ?: "нет"}, " +
            "READ_SMS: ${PermissionGuard.has(context, Manifest.permission.READ_SMS)}"
    }

    /** Можно читать переписку: роль и разрешение (роль выдаёт его сама). */
    fun canRead(context: Context): Boolean =
        isDefault(context) && PermissionGuard.has(context, Manifest.permission.READ_SMS)

    /**
     * Приложение SMS без разрешения на уведомления (Android 13+): входящие
     * пришли бы молча. Спрашивается сразу после выдачи роли.
     */
    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionGuard.has(context, Manifest.permission.POST_NOTIFICATIONS)

    /** Можно отправлять SMS из приложения. */
    fun canSend(context: Context): Boolean =
        PermissionGuard.has(context, Manifest.permission.SEND_SMS)

    /**
     * Намерение «сделать приложением SMS по умолчанию»; null — роль уже
     * у нас или на телефоне её нет (планшет без SIM).
     */
    fun request(context: Context): Intent? {
        if (isDefault(context)) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java) ?: return null
            if (!rm.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РЕЖИМ СПУТНИКА: SMS ВЕДЁТ ДРУГОЕ ПРИЛОЖЕНИЕ (2.1.0)
    // ═══════════════════════════════════════════════════════════════════════

    /** Приложение SMS по умолчанию — для кнопки «Открыть «…»» в разделе SMS. */
    data class CompanionApp(val packageName: String, val label: String, val rcs: Boolean)

    /** Приложение SMS по умолчанию, если это не мы и его видно; иначе null. */
    fun companionApp(context: Context): CompanionApp? {
        if (isDefault(context)) return null
        val pkg = defaultSmsPackage(context)?.takeIf { it != context.packageName } ?: return null
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return CompanionApp(pkg, label, pkg in RCS_APPS)
    }

    /**
     * Кому адресовать «Написать» (ExternalApps.smsIntent): роль у нас — себе;
     * иначе приложению SMS по умолчанию, если оно принимает smsto:; его нет —
     * единственному другому приложению сообщений; иначе null (система
     * спросит сама). Себя без роли не выбираем никогда: своя переписка
     * без роли только передала бы намерение дальше.
     */
    fun composeTarget(context: Context): String? {
        if (isDefault(context)) return context.packageName
        return companionTarget(context)
    }

    /** Приложение сообщений, которое не мы: по умолчанию, иначе единственное. */
    fun companionTarget(context: Context): String? {
        val own = context.packageName
        val probe = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", "", null))
        defaultSmsPackage(context)?.takeIf { it != own }?.let { pkg ->
            if (handles(context, Intent(probe).setPackage(pkg))) return pkg
        }
        val others = handlersOf(context, probe).filter { it != own }.distinct()
        return others.singleOrNull()
    }

    /**
     * Переписка открыта у нас без роли (ссылка sms: выбрана в окне «Открыть
     * с помощью», роль ушла другому) — передать её приложению SMS
     * по умолчанию. Себе не передаём (кольцо); некому — молча пропускаем.
     *
     * @return передано ли
     */
    fun handOff(context: Context, address: String, body: String?, subId: Int?): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", address, null)).apply {
            body?.takeIf { it.isNotEmpty() }?.let { putExtra("sms_body", it) }
            subId?.let {
                putExtra("subscription", it)
                putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", it)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val target = companionTarget(context)
        val launch = when {
            target != null -> intent.setPackage(target)
            // Несколько приложений и ни одного по умолчанию — выбор системы, но без нас
            handlersOf(context, intent).any { it != context.packageName } -> Intent.createChooser(intent, null).apply {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(context, com.ogneliss.dialer.MainActivity::class.java))
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            else -> null
        }
        if (launch == null) {
            Logger.w(TAG, "Переписку без роли передать некому")
            return false
        }
        return runCatching { context.startActivity(launch) }
            .onFailure { Logger.w(TAG, "Переписку без роли передать не удалось", it) }
            .isSuccess
            .also { if (it) Logger.i(TAG, "Переписка передана приложению SMS: ${target ?: "выбор системы"}") }
    }

    /** «Открыть «Сообщения»» в разделе SMS без роли: главный экран приложения SMS. */
    fun openCompanionApp(context: Context): Boolean {
        val app = companionApp(context) ?: return false
        val pm = context.packageManager
        val launch = runCatching { pm.getLaunchIntentForPackage(app.packageName) }.getOrNull()
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
                .setPackage(app.packageName)
        return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Logger.w(TAG, "Приложение SMS не открылось", it) }
            .isSuccess
    }

    private fun handles(context: Context, intent: Intent): Boolean =
        handlersOf(context, intent).isNotEmpty()

    private fun handlersOf(context: Context, intent: Intent): List<String> = runCatching {
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
    }.getOrDefault(emptyList())
}

/**
 * Настройки SMS — копия для мест без корутин (2.0.0).
 *
 * Приёмники SMS, сервис «ответить сообщением» и кнопки «Написать»
 * по всему приложению решают сразу, без чтения DataStore. Prefs при каждой
 * записи этих настроек повторяет их здесь (Prefs.setSms…).
 *
 * 2.1.0: переключателя «Писать SMS в диалере» (sms_in_app) больше нет —
 * куда писать, решает роль (SmsRole.composeTarget). Старое значение
 * в файле настроек не читается.
 */
object SmsSettings {
    private const val FILE = "sms_settings"
    const val KEY_DELIVERY_REPORTS = "sms_delivery_reports"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Просить отчёт о доставке (по умолчанию — нет: часть операторов берёт плату). */
    fun deliveryReports(context: Context): Boolean = prefs(context).getBoolean(KEY_DELIVERY_REPORTS, false)

    /*
     * Полоса «разрешите автозапуск» — один раз (2.1.0: в списке SMS,
     * SmsHealth; было — в переписке): на Huawei, OPPO, vivo, Tecno
     * и других закрытое приложение без автозапуска не получает входящие
     * SMS, а проверить автозапуск прошивки не дают.
     */
    private const val KEY_AUTOSTART_HINT = "sms_autostart_hint_done"

    /** Окно роли SMS после роли телефона — один раз (MainActivity.requestSmsRoleOnce). */
    private const val KEY_ROLE_PROMPT = "sms_role_prompt_done"

    fun rolePromptDone(context: Context): Boolean = prefs(context).getBoolean(KEY_ROLE_PROMPT, false)

    fun markRolePromptDone(context: Context) = put(context, KEY_ROLE_PROMPT, true)

    fun autostartHintDone(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTOSTART_HINT, false)

    fun markAutostartHintDone(context: Context) = put(context, KEY_AUTOSTART_HINT, true)

    fun put(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    /**
     * SMS открываются в диалере (2.1.0): только когда он — приложение SMS
     * по умолчанию. Было: ещё и при роли телефона с «Писать SMS в диалере» —
     * без истории и входящих, с окном SEND_SMS. Теперь без роли SMS
     * «Написать» открывает приложение сообщений человека.
     */
    fun composeHere(context: Context): Boolean = SmsRole.isDefault(context)
}
