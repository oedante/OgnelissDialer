package com.ogneliss.dialer.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.log.Logger

/**
 * «ПОЗВОНИТЬ» ИЗ УВЕДОМЛЕНИЙ И ЯРЛЫКОВ — СВОИМ ПРИЛОЖЕНИЕМ (2.1.1).
 *
 * Отзыв с форума (Samsung S23, 09.11.2026): «напоминание срабатывает, жму
 * „Позвонить“ — открывается выбор приложения, а Ogneliss Dialer в списке
 * нет». Было: кнопка отдавала системе общее намерение ACTION_CALL. На части
 * прошивок (Samsung) его перехватывает окно выбора среди приложений, которые
 * объявили себя «звонилками» (Телефон Samsung, True Phone, Zoom), — мы
 * такого намерения не объявляем, и человека уводило в чужое приложение.
 *
 * Стало: кнопка открывает это окно — без интерфейса (Theme.NoDisplay), оно
 * звонит само через Telecom (TelecomManager.placeCall — как набор номера
 * в приложении) и закрывается. Нет разрешения «Телефон» (приложение не
 * диалер по умолчанию) — открывается наш набор с этим номером: позвонить
 * одним нажатием. Уведомление, из которого позвонили, снимается.
 *
 * Тем же путём — «Перезвонить» у пропущенных и ярлыки контактов.
 */
class CallNumberActivity : Activity() {

    companion object {
        private const val TAG = "CallNumber"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_NOTIFICATION = "notification_id"

        /** Действие ярлыков: у намерения ярлыка оно обязано быть. */
        const val ACTION = "com.ogneliss.dialer.CALL_NUMBER"

        /**
         * Намерение «позвонить на [number]».
         * @param notificationId уведомление, которое снять после касания (0 — нет)
         */
        fun intent(context: Context, number: String, notificationId: Int = 0): Intent =
            Intent(context, CallNumberActivity::class.java)
                .setAction(ACTION)
                // Разные номера — разные намерения: иначе ярлыки и кнопки
                // уведомлений с одинаковым кодом запроса склеивались бы
                .setData(Uri.fromParts("tel", number, null))
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_NOTIFICATION, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent?.getStringExtra(EXTRA_NUMBER)
            ?: intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION, 0) ?: 0
        if (notificationId != 0) {
            runCatching { NotificationManagerCompat.from(this).cancel(notificationId) }
        }
        if (!number.isNullOrBlank() && !placeCall(number)) openDialpad(number)
        finish()
    }

    /** Вызов через Telecom; false — нельзя (нет разрешения) или система отказала. */
    private fun placeCall(number: String): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        val tm = getSystemService(TelecomManager::class.java) ?: return false
        return runCatching { tm.placeCall(Uri.fromParts("tel", number, null), Bundle()); true }
            .onFailure { Logger.w(TAG, "Вызов не передан системе — открываю набор", it) }
            .getOrDefault(false)
    }

    /** Наш набор с номером: позвонить — одно касание. */
    private fun openDialpad(number: String) {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_DIAL)
                    .setData(Uri.fromParts("tel", number, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }.onFailure { Logger.w(TAG, "Набор не открылся", it) }
    }
}
