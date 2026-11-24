package com.ogneliss.dialer.log

import android.content.Context

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

    /** Наш обработчик, если уже стоит: второй раз не оборачиваем (2.9.7, см. install). */
    @Volatile private var installed: Thread.UncaughtExceptionHandler? = null

    /**
     * 2.9.7: ПОВТОРНАЯ УСТАНОВКА И «КОД 2» В ТЕСТАХ.
     *
     * Было: каждый вызов оборачивал текущий обработчик заново, а если до нас
     * обработчика не было — после записи вызывался exitProcess(2). На телефоне
     * до нас всегда стоит обработчик Android, и до exitProcess дело не доходит.
     * Но в процессе unit-тестов Robolectric создаёт приложение заново для
     * каждого теста: обработчики выстраивались в цепочку, в конце которой —
     * exitProcess(2). Любое необработанное исключение в фоновом потоке любого
     * теста убивало весь процесс тестов: «Process 'Gradle Test Executor N'
     * finished with non-zero exit value 2» (у автора на Windows, 24.11 — один
     * раз и здесь).
     *
     * Стало: обработчик ставится один раз (дальше обновляется только контекст);
     * без предыдущего — как обработчик JVM по умолчанию: стек в stderr, поток
     * завершается, процесс живёт. На телефоне поведение то же, что было.
     */
    fun install(context: Context) {
        appContext = context.applicationContext ?: context
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current != null && current === installed) return
        val previous = current

        val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
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
                saveCrashStage(appContext ?: context, Stage.current)

                // Даём фоновому потоку логгера шанс дописать файл
                Thread.sleep(300)
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                // Как JVM без обработчика (ThreadGroup.uncaughtException): стек
                // в stderr, процесс не убиваем. На телефоне сюда не попадаем —
                // до нас всегда стоит обработчик Android
                System.err.print("Exception in thread \"${thread.name}\" ")
                throwable.printStackTrace()
            }
        }
        installed = handler
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    /** Контекст для отметки этапа: последний переданный в install. */
    @Volatile private var appContext: Context? = null
}
