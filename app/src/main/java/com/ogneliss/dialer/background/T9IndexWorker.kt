package com.ogneliss.dialer.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ogneliss.dialer.data.ContactsRepository
import com.ogneliss.dialer.data.db.T9IndexDao
import com.ogneliss.dialer.data.db.T9IndexEntity
import com.ogneliss.dialer.t9.T9Engine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Пересборка T9-индекса в фоне.
 *
 * Зачем: линейный перебор 2000 контактов на каждое нажатие цифры — это те самые
 * «лаги при наборе», на которые жалуются в отзывах. Предпосчитанный индекс
 * в Room с LIKE по префиксу отдаёт результат за единицы миллисекунд.
 */
@HiltWorker
class T9IndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contactsRepository: ContactsRepository,
    private val dao: T9IndexDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val contacts = contactsRepository.loadAll()
        val rows = contacts.flatMap { c ->
            c.numbers.map { n ->
                T9IndexEntity(
                    contactId = c.id,
                    displayName = c.displayName,
                    number = n.value,
                    e164 = n.e164,
                    digits = T9Engine.toDigits(c.displayName),
                    numberDigits = T9Engine.normalizeNumber(n.value)
                )
            }
        }
        dao.clear()
        rows.chunked(500).forEach { dao.insertAll(it) }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        private const val NAME = "t9_index_rebuild"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<T9IndexWorker>()
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                    .build()
            )
        }

        /** Периодическая подстраховка на случай пропущенных уведомлений об изменении. */
        fun enqueuePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "${NAME}_periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<T9IndexWorker>(12, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresBatteryNotLow(true)
                            .build()
                    ).build()
            )
        }
    }
}
