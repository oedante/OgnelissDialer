package com.ogneliss.dialer.call

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import android.content.pm.PackageManager
import android.content.Intent

/**
 * Разъяснение двух РАЗНЫХ ролей Android, которые часто путают.
 *
 *   ROLE_DIALER          — «Телефон по умолчанию». Одна на систему. Наша.
 *   ROLE_CALL_SCREENING  — «Определитель номера и спама». Тоже одна,
 *                          но НЕЗАВИСИМАЯ от роли диалера.
 *
 * Ключевой факт: обе роли существуют одновременно. Порядок обработки
 * входящего вызова в AOSP:
 *
 *   1. Системный список блокировки (BlockedNumberContract) — вызов не звонит вообще
 *   2. CallScreeningService приложения с ROLE_CALL_SCREENING (Яндекс, Kaspersky…)
 *   3. CallScreeningService диалера по умолчанию (наш)
 *
 * Отсюда следствия, важные для архитектуры:
 *
 * • Ogneliss как диалер по умолчанию НЕ мешает Яндекс.Определителю работать —
 *   тот держит другую роль и отрабатывает раньше нас. Пользователю не нужно
 *   выбирать между нами и им.
 *
 * • «Перенаправить вызов в чужой определитель» технически невозможно и не нужно:
 *   если он установлен и держит свою роль, он уже отработал до нас.
 *
 * • Прочитать ВЕРДИКТ чужого определителя нельзя — публичного API нет.
 *   Но результат его работы виден: заблокированные им вызовы приходят
 *   в журнал с заполненным CallLog.Calls.BLOCK_REASON.
 *
 * • Получить от него ИМЯ абонента можно штатно — через
 *   ContactsContract.Directory (см. DirectoryLookupRepository).
 *
 * ── ЧТО ДАЁТ РОЛЬ ОПРЕДЕЛИТЕЛЯ ИМЕННО НАМ ─────────────────────────────────
 * Роль диалера позволяет отвечать на вызов «пропустить или отклонить».
 * Роль определителя добавляет к этому третий вариант — БЕЗЗВУЧНЫЙ ПРОПУСК:
 * телефон не звонит, но вызов проходит и попадает в журнал.
 *
 * Разница принципиальна для сомнительных номеров. Блокировка — решение
 * необратимое: если приложение ошиблось, человек не узнает, что ему
 * звонили. Беззвучный пропуск ошибку прощает: звонок виден в журнале,
 * и можно перезвонить.
 *
 * Поэтому предложение взять роль — не борьба за место с чужим
 * определителем, а возможность обращаться с сомнительными вызовами
 * мягче, чем позволяет роль диалера.
 */
object ScreeningRoleInfo {

    data class State(
        val weAreDialer: Boolean,
        val weAreScreener: Boolean,
        val otherScreenerPackage: String?,
        val screeningRoleAvailable: Boolean
    )

    /**
     * Intent для запроса роли определителя.
     *
     * Система показывает выбор приложений и предупреждает пользователя
     * о последствиях — молча забрать роль нельзя, и это правильно.
     *
     * @return null, если роль недоступна: до Android 10 её не существует,
     *         на части устройств она отключена производителем
     */
    fun requestRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return runCatching {
            rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        }.getOrNull()
    }

    /**
     * Можем ли мы отвечать беззвучным пропуском.
     *
     * Только держатель роли определителя может заглушить вызов.
     * Диалеру по умолчанию доступны лишь «пропустить» и «отклонить».
     */
    /*
     * @ChecksSdkIntAtLeast — не украшение, а способ объяснить это lint.
     *
     * Функция возвращает true только на Android 10 и новее: первая же
     * строка отсекает всё, что ниже. Но lint этого не знал и ругался
     * на setSilenceCall в OgnelissCallScreeningService — «Call requires
     * API level 29 (current min is 26)», и сборка release падала.
     *
     * Аннотация говорит проверке: «true отсюда означает, что версия
     * не ниже Q». После этого вызов внутри if(canSilenceCalls(...))
     * считается защищённым — и это правда, а не подавление ошибки.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    fun canSilenceCalls(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return runCatching {
            rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }.getOrDefault(false)
    }

    fun state(context: Context): State {
        val tm = context.getSystemService(android.telecom.TelecomManager::class.java)
        val isDialer = context.packageName == tm?.defaultDialerPackage

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return State(isDialer, false, null, false)
        }
        val rm = context.getSystemService(RoleManager::class.java)
            ?: return State(isDialer, false, null, false)

        val available = rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
        val weHold = available && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

        return State(
            weAreDialer = isDialer,
            weAreScreener = weHold,
            otherScreenerPackage = if (!weHold) findScreeningApps(context).firstOrNull() else null,
            screeningRoleAvailable = available
        )
    }

    /** Какие приложения вообще умеют быть определителем — для подсказки пользователю. */
    private fun findScreeningApps(context: Context): List<String> = runCatching {
        context.packageManager.queryIntentServices(
            Intent("android.telecom.CallScreeningService"), PackageManager.MATCH_ALL
        ).mapNotNull { it.serviceInfo?.packageName }
            .filter { it != context.packageName }
            .distinct()
    }.getOrDefault(emptyList())

    /**
     * Запрос роли определителя. Отдельный от роли диалера запрос —
     * пользователь может дать одну и не дать другую.
     */
    fun requestScreeningRole(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val rm = activity.getSystemService(RoleManager::class.java) ?: return
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) return
        runCatching {
            activity.startActivityForResult(
                rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQUEST_CODE
            )
        }.onFailure { com.ogneliss.dialer.log.Logger.w("ScreeningRole", "Окно роли определителя не открылось", it) }
    }

    /*
     * СВОЙ КОД ЗАПРОСА (2.1.0).
     *
     * Было: 4202 — тот же код, что у роли SMS (SmsRole.REQUEST_CODE).
     * Ответ приходит в MainActivity.onActivityResult, и отказ в роли
     * определителя разбирался как отказ в роли SMS: подсказка про SMS,
     * лишние записи в журнале. Стало: отдельный код вне ряда 42xx
     * (4201 — телефон, 4202 и соседние — SMS, 4203 — уведомления):
     * MainActivity его не разбирает.
     */
    const val REQUEST_CODE = 4301
}
