package com.ogneliss.dialer.log

import android.content.Context
import kotlin.system.exitProcess

/**
 * ПЕРЕХВАТЧИК НЕОБРАБОТАННЫХ ИСКЛЮЧЕНИЙ.
 *
 * Назначение: записать стек падения в файл лога ДО того, как процесс умрёт.
 * Без этого пользователь может сказать «вылетело при звонке», а причины
 * в логе не будет — logcat к тому моменту уже недоступен.
 *
 * Важно: мы не подменяем поведение системы. После записи вызывается
 * предыдущий обработчик, поэтому Android покажет штатный диалог
 * и (если пользователь согласился) отправит отчёт в Play Console.
 * Собственной отправки отчётов у нас нет — это потребовало бы сети.
 */
object CrashHandler {

    private const val PREFS_NAME = "crash_info"
    private const val KEY_STAGE = "last_crash_stage"

    /**
     * Этап последнего падения.
     *
     * Записывается через SharedPreferences, а не DataStore: DataStore
     * асинхронный, а у нас в распоряжении доли секунды перед смертью
     * процесса. Здесь важна не элегантность, а гарантия записи.
     */
    private fun saveCrashStage(context: Context, stage: String) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STAGE, stage)
                .commit()   // именно commit: apply может не успеть
        }
    }

    /**
     * Проверка при запуске: не падало ли приложение в прошлый раз.
     * Отметка читается один раз и сбрасывается.
     */
    fun consumeLastCrashStage(context: Context): String? = runCatching {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stage = prefs.getString(KEY_STAGE, null)
        if (stage != null) prefs.edit().remove(KEY_STAGE).apply()
        stage
    }.getOrNull()

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                /*
                 * Пишем сразу в три места, потому что каждое может подвести.
                 *
                 * android.util.Log — единственное, что видно в logcat, если
                 * наш файловый логгер не успел запуститься или отключён
                 * пользователем.
                 *
                 * Файл — единственное, что остаётся, если logcat пуст:
                 * буфер мог переполниться чужим приложением, а на части
                 * прошивок он очищается при перезапуске процесса.
                 *
                 * Отметка этапа отвечает на вопрос «что приложение делало
                 * последним» — при падении без внятного стека это
                 * единственная зацепка.
                 */
                android.util.Log.e(
                    "OgnelissCrash",
                    "Падение в потоке '${thread.name}'. ${Stage.describe()}",
                    throwable
                )

                Logger.e(
                    "Crash",
                    "НЕОБРАБОТАННОЕ ИСКЛЮЧЕНИЕ в потоке '${thread.name}'. " +
                        Stage.describe(),
                    throwable
                )

                // Этап сохраняется отдельно: при следующем запуске
                // приложение сообщит, на чём оборвалось в прошлый раз
                saveCrashStage(context, Stage.current)

                // Даём фоновому потоку логгера шанс дописать файл
                Thread.sleep(300)
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                exitProcess(2)
            }
        }
    }
}
