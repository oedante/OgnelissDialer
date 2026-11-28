package com.ogneliss.dialer.util

import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.ogneliss.dialer.log.Logger

/**
 * Экстренные номера (1.3.2, 01.10.2026).
 *
 * Было: проверок экстренного номера в приложении не было. 112 считался
 * коротким номером и получал подсказку «может оказаться платным»,
 * а фильтр незнакомых номеров мог заблокировать обратный звонок
 * экстренной службы — он приходит с номера, которого нет в контактах.
 *
 * Стало: список экстренных номеров берётся у системы (он зависит
 * от SIM и страны); после исходящего на экстренный номер фильтр
 * два часа пропускает все входящие.
 *
 * Не проверять вызовом — ни на 112, ни на тестовый номер из
 * cmd phone emergency-number-test-mode: маршрут такого вызова
 * зависит от прошивки. Проверка — по коду и журналу приложения.
 */
object EmergencyNumbers {

    private const val TAG = "Emergency"

    /** Сколько после экстренного вызова фильтр не трогает входящие. */
    const val CALLBACK_WINDOW_MS = 2 * 60 * 60 * 1000L

    /**
     * Без доступа к системе — только номера, обязательные во всех сетях
     * GSM: 112 и 911 работают в любой стране и без SIM.
     */
    private val ALWAYS = setOf("112", "911")

    /**
     * Экстренный ли номер. Запись — любая: пробелы, скобки, дефисы не важны.
     *
     * @param ifUnknown ответ, когда система не ответила (исключение). По умолчанию
     *   false — так решают фильтр и подсказки; автодозвон передаёт true: не повторить
     *   безопаснее, чем повторить экстренный без человека (2.9.13).
     */
    fun isEmergency(context: Context, number: String?, ifUnknown: Boolean = false): Boolean {
        val digits = number?.filter { it.isDigit() || it == '+' }.orEmpty()
        if (digits.isEmpty()) return false
        if (digits in ALWAYS) return true
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Нет телефонии (планшет без модема, сбой службы) — «не знаю», а не «нет»:
                // для автодозвона это разница между «не повторять» и «повторить» (2.9.13)
                val telephony = context.getSystemService(TelephonyManager::class.java)
                    ?: return ifUnknown
                telephony.isEmergencyNumber(digits)
            } else {
                @Suppress("DEPRECATION") // единственный способ до Android 10
                PhoneNumberUtils.isEmergencyNumber(digits)
            }
        }.getOrElse {
            Logger.w(TAG, "Система не ответила, экстренный ли номер", it)
            ifUnknown
        }
    }

    /**
     * Был ли исходящий на экстренный номер за последние два часа.
     * Читает только исходящие за это время — запрос короткий.
     * Нет доступа к журналу — false: решать будет обычный фильтр.
     */
    fun recentlyCalled(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (!PermissionGuard.canReadCallLog(context)) return false
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), (now - CALLBACK_WINDOW_MS).toString()),
                null
            )?.use { c ->
                var found = false
                while (!found && c.moveToNext()) {
                    found = isEmergency(context, c.getString(0))
                }
                found
            } ?: false
        }.getOrElse {
            Logger.w(TAG, "Не удалось проверить недавние экстренные вызовы", it)
            false
        }
    }
}
