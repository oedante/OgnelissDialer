package com.ogneliss.dialer.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ogneliss.dialer.data.ContactsRepository
import com.ogneliss.dialer.log.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * ФОНОВАЯ ОБРАБОТКА ЗАВЕРШЁННОГО ВЫЗОВА.
 *
 * Назначение: после того как разговор закончился, нужно перечитать журнал
 * и решить, показывать ли экран заметки. Делать это прямо в InCallService
 * нельзя: сервис живёт ровно до конца вызова, и система вправе убить
 * процесс сразу после.
 *
 * WorkManager гарантирует выполнение даже если процесс умрёт: задача
 * переживёт перезапуск и выполнится, когда система сочтёт нужным.
 *
 * ── Почему не foreground-сервис ────────────────────────────────────────────
 * Работа короткая и не требует показа уведомления пользователю.
 * Foreground-сервис ради полусекундной задачи — злоупотребление,
 * которое Google Play отслеживает.
 */
@HiltWorker
class CallEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contacts: ContactsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val number = inputData.getString(KEY_NUMBER) ?: return Result.success()

        return try {
            // Точечный запрос вместо чтения всей книги
            val known = contacts.isKnownNumber(number)
            Logger.i(
                "CallEnd",
                "Вызов завершён: ${Logger.mask(number)}, " +
                    if (known) "номер в контактах" else "номер незнакомый"
            )
            // Индекс T9 мог устареть, если пользователь добавил контакт
            // прямо из экрана после звонка
            T9IndexWorker.enqueue(applicationContext)
            Result.success()
        } catch (c: kotlinx.coroutines.CancellationException) {
            // Работу остановила система (перезапустит сама) — это не ошибка постобработки (2.2.3)
            throw c
        } catch (t: Throwable) {
            Logger.e("CallEnd", "Ошибка постобработки вызова", t)
            // Не retry: повторять анализ завершённого звонка бессмысленно
            Result.failure()
        }
    }

    companion object {
        private const val KEY_NUMBER = "number"

        fun enqueue(context: Context, number: String?) {
            if (number.isNullOrBlank()) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                "call_end_${number.hashCode()}",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CallEndWorker>()
                    .setInputData(Data.Builder().putString(KEY_NUMBER, number).build())
                    .build()
            )
        }
    }
}
