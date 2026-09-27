package com.ogneliss.dialer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ogneliss.dialer.log.Logger

/**
 * ПРОВЕРКА РАЗРЕШЕНИЙ ПЕРЕД ОБРАЩЕНИЕМ К ПРОВАЙДЕРАМ.
 *
 * ── Зачем понадобилось ─────────────────────────────────────────────────────
 * Приложение обращалось к CallLogProvider и ContactsContract, не проверив
 * разрешение. Система отвечала SecurityException, и в логе появлялись
 * стеки на каждое обновление списка.
 *
 * Формально исключение перехватывалось, и приложение не падало от него
 * напрямую. Но последствия были хуже видимого сбоя:
 *
 * • пустой журнал и пустые контакты без объяснения причины —
 *   пользователь видит «Звонков пока нет» и считает это правдой;
 * • подписка ContentObserver не устанавливалась, и после выдачи
 *   разрешения список не обновлялся сам;
 * • десятки стеков в логе маскировали настоящие ошибки.
 *
 * Теперь при отсутствии разрешения обращения не происходит вовсе,
 * а интерфейс показывает, чего именно не хватает и как это исправить.
 */
object PermissionGuard {

    private const val TAG = "Permissions"

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    /** Можно ли читать журнал вызовов. */
    fun canReadCallLog(context: Context): Boolean =
        has(context, Manifest.permission.READ_CALL_LOG)

    /** Можно ли изменять журнал: удалять записи. */
    fun canWriteCallLog(context: Context): Boolean =
        has(context, Manifest.permission.WRITE_CALL_LOG)

    fun canReadContacts(context: Context): Boolean =
        has(context, Manifest.permission.READ_CONTACTS)

    fun canWriteContacts(context: Context): Boolean =
        has(context, Manifest.permission.WRITE_CONTACTS)

    /** Можно ли читать состояние телефона: список SIM, активные карты. */
    fun canReadPhoneState(context: Context): Boolean =
        has(context, Manifest.permission.READ_PHONE_STATE)

    fun canCall(context: Context): Boolean =
        has(context, Manifest.permission.CALL_PHONE)

    /**
     * Выполняет действие, только если разрешение есть.
     *
     * @param what что именно делаем — попадёт в лог при отказе
     * @param fallback что вернуть, если разрешения нет
     */
    inline fun <T> withPermission(
        context: Context,
        permission: String,
        what: String,
        fallback: T,
        block: () -> T
    ): T = if (has(context, permission)) {
        block()
    } else {
        logDenied(what, permission)
        fallback
    }

    fun logDenied(what: String, permission: String) {
        Logger.d(TAG, "Нет разрешения для «$what»: ${permission.substringAfterLast('.')}")
    }
}
