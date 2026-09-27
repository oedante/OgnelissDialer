package com.ogneliss.dialer.data

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard
import com.ogneliss.dialer.util.PhoneFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * СОБСТВЕННЫЙ НОМЕР ТЕЛЕФОНА.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ЕГО НЕЛЬЗЯ ПРОСТО СПРОСИТЬ У СИСТЕМЫ
 * ══════════════════════════════════════════════════════════════════════════
 * Кажется, что телефон свой номер знает. На деле — далеко не всегда:
 *
 * • Номер хранится на SIM-карте в поле MSISDN, и оператор заполняет
 *   его по желанию. Многие не заполняют вовсе.
 * • getLine1Number объявлен устаревшим и с Android 11 требует
 *   разрешений, которых обычному приложению не дают.
 * • На Android 13+ есть SubscriptionManager.getPhoneNumber, но и он
 *   возвращает пустоту, если оператор не записал номер.
 *
 * Поэтому единственный надёжный источник — сам человек. Автоматическое
 * определение остаётся попыткой: получилось — хорошо, нет — спросим.
 *
 * ── Зачем номер нужен ──────────────────────────────────────────────────────
 * Чтобы поделиться своей карточкой: QR-код, отправка контакта.
 * Без номера делиться нечем.
 */
@Singleton
class OwnNumber @Inject constructor() {

    /**
     * Попытка узнать номер у системы.
     *
     * @return номер в формате E.164 или null, если система его не знает.
     *         null — обычный, а не исключительный случай: у большинства
     *         операторов поле на SIM пустое.
     */
    /*
     * @SuppressLint("MissingPermission") — разрешение проверяется ниже.
     *
     * PermissionGuard.canReadPhoneState стоит первой строкой, а весь
     * доступ к телефонии обёрнут в runCatching: отсутствие номера здесь
     * штатное состояние, а не сбой. lint внутрь вспомогательной функции
     * не заглядывает и помечал три вызова ошибкой, роняя сборку release.
     *
     * Отдельно: line1Number помечен и как HardwareIds. Это предупреждение,
     * не ошибка, и оно по делу — номер запрашивается только по кнопке
     * «Попробовать определить» и никуда не уходит: у приложения нет
     * доступа к сети.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun detect(context: Context, slotIndex: Int = 0): String? {
        if (!PermissionGuard.canReadPhoneState(context)) return null

        return runCatching {
            val raw = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    val sm = context.getSystemService(SubscriptionManager::class.java)
                    val subs = sm?.activeSubscriptionInfoList.orEmpty()
                    val sub = subs.firstOrNull { it.simSlotIndex == slotIndex }
                        ?: subs.firstOrNull()

                    sub?.let { sm?.getPhoneNumber(it.subscriptionId) }
                }

                else -> {
                    @Suppress("DEPRECATION")
                    context.getSystemService(TelephonyManager::class.java)?.line1Number
                }
            }

            raw?.takeIf { it.isNotBlank() }?.let { PhoneFormatter.toE164(it) }
        }.getOrElse {
            // Не ошибка: отсутствие номера — штатное состояние.
            // Пишем в отладочный уровень, чтобы не засорять журнал
            Logger.d(TAG, "Система не сообщила собственный номер")
            null
        }
    }

    private companion object {
        const val TAG = "OwnNumber"
    }
}
