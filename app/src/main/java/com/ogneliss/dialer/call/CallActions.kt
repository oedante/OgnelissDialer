package com.ogneliss.dialer.call

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.annotation.SuppressLint
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.ogneliss.dialer.log.Logger
import androidx.core.content.ContextCompat
import com.ogneliss.dialer.data.SimRepository

/**
 * ВНЕШНИЕ ДЕЙСТВИЯ ПО НОМЕРУ.
 *
 * Назначение: единая точка для всего, что можно сделать с номером,
 * кроме самого вызова — SMS, мессенджеры, почта, редактирование.
 *
 * ── Зачем собрано в один класс ─────────────────────────────────────────────
 * Свайпы настраиваемые: пользователь назначает любое из этих действий
 * на любую сторону. Разбросав их по экранам, пришлось бы дублировать
 * обработку в журнале, карточке абонента и шторке выбора номера.
 *
 * ── Общий принцип ──────────────────────────────────────────────────────────
 * Приложение НЕ отправляет сообщения само. Оно передаёт намерение
 * системе, а завершает действие пользователь в приложении по умолчанию.
 * Поэтому не нужны ни SEND_SMS, ни роль SMS-обработчика.
 *
 * Отсутствующее приложение не должно ронять диалер: все запуски
 * обёрнуты и при неудаче просто ничего не делают.
 */
class CallActions(
    private val context: Context,
    private val simRepository: SimRepository
) {

    fun placeCall(number: String, slotIndex: Int = -1) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val tm = context.getSystemService(TelecomManager::class.java) ?: return
        val uri = Uri.fromParts("tel", number, null)
        val extras = Bundle()
        if (slotIndex >= 0) {
            simRepository.accountForSlot(slotIndex)?.let {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
            }
        }
        runCatching { tm.placeCall(uri, extras) }
    }

    /*
     * ГОЛОСОВАЯ ПОЧТА — ДОЛГОЕ НАЖАТИЕ «1» (2.1.0).
     *
     * Было: долгое «1» ничего не делало, хотя подсказка быстрого набора
     * говорит, что цифра 1 занята под почту. Стало: вызов адреса
     * «voicemail:» — Telecom сам подставит номер почты выбранной SIM
     * (без номера в настройках оператора система покажет своё окно).
     */
    fun placeVoicemail() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val tm = context.getSystemService(TelecomManager::class.java) ?: return
        runCatching { tm.placeCall(Uri.fromParts("voicemail", "", null), Bundle()) }
            .onFailure { Logger.w("Call", "Не удалось позвонить на голосовую почту", it) }
    }

    /*
     * СЕКРЕТНЫЕ КОДЫ НАБОРА (2.1.0).
     *
     * Было: «*#06#» и «*#*#…#*#*» уходили обычным вызовом. Стало:
     * • *#*#код#*#* — TelephonyManager.sendDialerSpecificSecretCode: система
     *   рассылает код приложениям, которые его ждут (сервисные меню и т. п.).
     *   Разрешено только телефону по умолчанию — мы им и являемся.
     * • *#06# (IMEI/MEID) — прочитать IMEI сами мы не можем: с Android 10
     *   он доступен только привилегированным приложениям. Передаём код
     *   системе (TelecomManager.handleMmi — телефону по умолчанию разрешено).
     *
     * @return true — код обработан, обычный вызов не нужен
     */
    @SuppressLint("MissingPermission")
    fun handleSecretCode(query: String): Boolean {
        val code = query.trim()
        SECRET_CODE.matchEntire(code)?.let { match ->
            val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
            /*
             * Через отражение: метод открыт с API 26, но из заглушек
             * compileSdk 37 его убрали (в android.jar его нет) — прямой
             * вызов не компилируется. На устройствах он есть. Не нашёлся —
             * false, и код уйдёт обычным вызовом, как раньше.
             */
            return runCatching {
                TelephonyManager::class.java
                    .getMethod("sendDialerSpecificSecretCode", String::class.java)
                    .invoke(tm, match.groupValues[1])
                true
            }
                .onFailure { Logger.w("Call", "Секретный код не передан", it) }
                .getOrDefault(false)
        }
        if (code == IMEI_CODE) {
            val tm = context.getSystemService(TelecomManager::class.java) ?: return false
            return runCatching { tm.handleMmi(code) }
                .onFailure { Logger.w("Call", "Код IMEI не обработан системой", it) }
                .getOrDefault(false)
        }
        return false
    }

    private companion object {
        val SECRET_CODE = Regex("""\*#\*#(\d+)#\*#\*""")
        const val IMEI_CODE = "*#06#"
    }

    /** «Написать»: переписка в диалере или приложение SMS (см. ExternalApps.sms). */
    fun openSms(number: String, body: String? = null) = start(
        ExternalApps.smsIntent(context, number).apply {
            if (body != null) putExtra("sms_body", body)
        }
    )

    fun openWhatsApp(number: String) {
        val digits = number.filter { it.isDigit() }
        start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
    }

    /*
     * Было (до 1.3.2): Uri.parse("mailto:$address") и молчаливый провал,
     * если почтового приложения нет. Стало (1.3.2, 01.10.2026):
     * Uri.fromParts — адрес с «+», «?» или «#» не ломает ссылку;
     * возвращается, удалось ли открыть, — карточка скажет, что почтового
     * приложения нет, а не промолчит.
     */
    fun openEmail(address: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    fun viewContact(contactId: Long) = start(
        Intent(Intent.ACTION_VIEW,
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId))
    )

    fun editBeforeCall(number: String) = start(
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
    )

    private fun start(intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
