package com.ogneliss.dialer.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.ogneliss.dialer.concurrency.suspendRunCatching
import com.ogneliss.dialer.data.db.StarredSmsDao
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * АВТОУДАЛЕНИЕ КОДОВ (2.2.0, настройка «Автоудаление кодов»).
 *
 * Входящие с кодом подтверждения (OtpDetector — только с словом-признаком
 * «код», «пароль», «code»…) удаляются через выбранное время после прихода:
 * через час или сутки. Код нужен минуту, а потом лишь засоряет переписку
 * с банком и магазином.
 *
 * ── Что не удаляется ───────────────────────────────────────────────────────
 * • Сообщения от собеседника из книги: «код от домофона 1234» от друга —
 *   не служебный код, его удалять нельзя.
 * • Отмеченные «В избранное»: человек сам сказал, что оно нужно.
 * • Исходящие — в них кодов приложение не ищет.
 *
 * ── Когда ──────────────────────────────────────────────────────────────────
 * При открытии раздела SMS и неточным будильником через выбранное время
 * после прихода кода (SmsDeliverReceiver → [scheduleAfterCode]). Один
 * будильник на всё: новый код переносит его, а обход удаляет и прежние.
 * Удалять может только приложение SMS по умолчанию.
 */
@Singleton
class OtpSweeper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val starred: StarredSmsDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /** Удалить коды старше срока из настройки. Возвращает, сколько удалено. */
    suspend fun sweep(): Int = withContext(io) {
        val hours = SmsSettings.otpCleanupHours(context)
        if (hours <= 0 || !SmsRole.isDefault(context)) return@withContext 0
        val before = System.currentTimeMillis() - hours * HOUR_MS
        val candidates = MessageStore.incomingBefore(context, before, SCAN_LIMIT)
            .filter { OtpDetector.code(it.body) != null }
        if (candidates.isEmpty()) return@withContext 0
        val keep = suspendRunCatching { starred.snapshot().mapTo(HashSet()) { it.msgKey } }.getOrDefault(emptySet())
        val known = HashMap<String, Boolean>()
        val doomed = candidates.filter { m ->
            m.key !in keep && !known.getOrPut(m.address) { inContacts(m.address) }
        }
        if (doomed.isEmpty()) return@withContext 0
        val deleted = MessageStore.delete(context, doomed.map { it.key })
        Logger.i(TAG, "Автоудаление кодов: удалено $deleted")
        deleted
    }

    /** Есть в книге — не трогать. Без доступа к книге — считаем, что есть: не удалять наугад. */
    private fun inContacts(address: String): Boolean {
        if (address.any { it.isLetter() }) return false
        if (!PermissionGuard.canReadContacts(context)) return true
        return runCatching {
            context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(true)
    }

    /** Будильник обхода. */
    @dagger.hilt.android.AndroidEntryPoint
    class Receiver : BroadcastReceiver() {

        @Inject
        lateinit var sweeper: OtpSweeper

        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            ).launch {
                suspendRunCatching { sweeper.sweep() }
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "OtpSweep"
        private const val HOUR_MS = 60 * 60_000L

        /** Сколько входящих старше срока просматривать за обход: новые первыми. */
        private const val SCAN_LIMIT = 2000

        /** Запас после срока: неточный будильник может прийти раньше. */
        private const val MARGIN_MS = 2 * 60_000L

        private const val REQUEST_CODE = 0x4f5450

        /** Пришёл код — обход через срок из настройки (если включено). */
        fun scheduleAfterCode(context: Context, body: String) {
            val hours = SmsSettings.otpCleanupHours(context)
            if (hours <= 0 || OtpDetector.code(body) == null) return
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_CODE, Intent(context, Receiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + hours * HOUR_MS + MARGIN_MS, pi)
        }
    }
}
