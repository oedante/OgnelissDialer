package com.ogneliss.dialer.data

import com.ogneliss.dialer.concurrency.suspendRunCatching

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.telecom.TelecomManager
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * СИСТЕМНЫЙ список блокировки Android (BlockedNumberContract).
 *
 * Почему это важнее собственного блок-листа:
 * блокировка происходит на уровне телефонии — телефон вообще не звонит,
 * вызов не доходит до приложения. Наш CallScreeningService срабатывает позже
 * и на части прошивок успевает пропустить один гудок. Это ровно та жалоба
 * «блокировка работает не всегда, звонок всё равно проходит».
 *
 * Доступ к этому провайдеру есть ТОЛЬКО у приложения, выбранного телефоном
 * по умолчанию (или SMS по умолчанию). Ещё одна причина запросить роль.
 *
 * Второй бонус: список общий с системой. Номера, заблокированные в штатном
 * приложении «Телефон», видны нам, и наоборот — пользователь не теряет
 * свои блокировки при переходе на Ogneliss.
 */
@Singleton
class SystemBlockRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Может ли приложение сейчас править системный список. */
    fun canModify(): Boolean = runCatching {
        BlockedNumberContract.canCurrentUserBlockNumbers(context)
    }.getOrDefault(false) && isDefaultDialer()

    private fun isDefaultDialer(): Boolean {
        val tm = context.getSystemService(TelecomManager::class.java)
        return context.packageName == tm?.defaultDialerPackage
    }

    suspend fun isBlocked(number: String): Boolean = withContext(Dispatchers.IO) {
        suspendRunCatching {
            BlockedNumberContract.isBlocked(context, number)
        }.getOrDefault(false)
    }

    suspend fun block(number: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                put(
                    BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER,
                    PhoneFormatter.toE164(number)
                )
            }
            context.contentResolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI, values
            ) != null
        }.getOrDefault(false)
    }

    /** Открыть системный экран управления заблокированными номерами. */
    fun openSystemScreen() {
        runCatching {
            val tm = context.getSystemService(TelecomManager::class.java) ?: return
            context.startActivity(
                tm.createManageBlockedNumbersIntent()
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
