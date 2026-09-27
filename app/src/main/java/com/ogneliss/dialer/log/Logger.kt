package com.ogneliss.dialer.log

import android.content.Context
import android.util.Log
import com.ogneliss.dialer.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * ЛОГГЕР ПРИЛОЖЕНИЯ.
 *
 * Назначение: единая точка записи диагностических сообщений одновременно
 * в системный logcat и в файл на устройстве, чтобы тестировщик мог выгрузить
 * журнал работы после воспроизведения проблемы, не подключая компьютер.
 *
 * ── Почему нельзя просто писать всё подряд ────────────────────────────────
 * Номера телефонов, имена контактов и содержимое заметок — персональные
 * данные. Файл лога пользователь отправляет наружу (в мессенджер, на почту),
 * поэтому попадание туда «сырых» номеров означало бы неконтролируемую утечку
 * и прямое нарушение раздела User Data правил Google Play.
 *
 * Решение: ВСЕ номера и имена проходят через маскирование (см. [mask]).
 * В файл попадает «+7*****4512», по которому можно сопоставить события
 * между собой, но нельзя восстановить абонента.
 *
 * ── Устройство ─────────────────────────────────────────────────────────────
 * • Запись асинхронная: очередь + один фоновый поток. Логирование никогда
 *   не блокирует UI и не влияет на скорость набора.
 * • Ротация: два файла по 512 КБ. При переполнении текущий становится
 *   предыдущим, новый начинается с нуля. Больше 1 МБ на диске не занимаем.
 * • Файлы лежат в приватном каталоге приложения и удаляются вместе с ним.
 * • В release-сборке уровень DEBUG в файл не пишется — только INFO и выше.
 */
object Logger {

    private const val MAX_FILE_BYTES = 512 * 1024
    private const val FILE_CURRENT = "ogneliss.log"
    private const val FILE_PREVIOUS = "ogneliss.log.1"
    private const val DIR = "logs"

    /** Уровни. Порядок важен: сравниваются по ordinal. */
    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val queue = LinkedBlockingQueue<String>()
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OgnelissLogger").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var logDir: File? = null
    private var fileLoggingEnabled: Boolean = true

    /** Минимальный уровень, попадающий В ФАЙЛ. В logcat пишется всё. */
    private val minFileLevel: Level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO

    /**
     * Инициализация. Вызывается один раз из Application.onCreate.
     * До инициализации сообщения уходят только в logcat.
     */
    fun init(context: Context) {
        logDir = File(context.filesDir, DIR).apply { mkdirs() }
        i("Logger", "=== Запуск ${BuildConfig.APPLICATION_ID} " +
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
            "Android ${android.os.Build.VERSION.SDK_INT}, " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} ===")
    }

    /** Пользователь может выключить запись в файл в настройках. */
    fun setFileLogging(enabled: Boolean) {
        fileLoggingEnabled = enabled
        i("Logger", "Запись лога в файл: ${if (enabled) "включена" else "выключена"}")
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) =
        log(Level.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) =
        log(Level.ERROR, tag, message, error)

    /**
     * МАСКИРОВАНИЕ ПЕРСОНАЛЬНЫХ ДАННЫХ.
     *
     * Обязательно применять к любому номеру телефона и имени контакта
     * перед передачей в логгер. Оставляет код страны и последние 4 цифры —
     * этого хватает, чтобы отследить один и тот же номер в цепочке событий,
     * и недостаточно, чтобы кому-то дозвониться.
     *
     * Пример: "+79676660512" → "+7*****0512"
     */
    fun mask(number: String?): String {
        if (number.isNullOrBlank()) return "<пусто>"
        val digits = number.filter { it.isDigit() || it == '+' }
        if (digits.length <= 4) return "*".repeat(digits.length)
        val head = if (digits.startsWith("+")) digits.take(2) else digits.take(1)
        val tail = digits.takeLast(4)
        return "$head${"*".repeat((digits.length - head.length - 4).coerceAtLeast(1))}$tail"
    }

    /** Маскирование имени: "Андрей Клинов" → "А. К." */
    fun maskName(name: String?): String {
        if (name.isNullOrBlank()) return "<без имени>"
        return name.split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { "${it.first().uppercaseChar()}." }
    }

    private fun log(level: Level, tag: String, message: String, error: Throwable?) {
        // 1. Всегда в logcat — бесплатно и удобно при отладке через Android Studio
        val fullTag = "Ogneliss/$tag"
        when (level) {
            Level.DEBUG -> Log.d(fullTag, message, error)
            Level.INFO -> Log.i(fullTag, message, error)
            Level.WARN -> Log.w(fullTag, message, error)
            Level.ERROR -> Log.e(fullTag, message, error)
        }

        // 2. В файл — только если включено и уровень достаточный
        if (!fileLoggingEnabled) return
        if (level.ordinal < minFileLevel.ordinal) return
        val dir = logDir ?: return

        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ').append(level.name.first())
            append(" [").append(tag).append("] ")
            append(message)
            if (error != null) {
                append('\n').append(Log.getStackTraceString(error))
            }
        }
        queue.offer(line)
        worker.execute { drain(dir) }
    }

    /** Пишет накопленное. Выполняется строго в одном фоновом потоке. */
    private fun drain(dir: File) {
        val current = File(dir, FILE_CURRENT)
        try {
            val batch = mutableListOf<String>()
            queue.drainTo(batch)
            if (batch.isEmpty()) return

            if (current.exists() && current.length() > MAX_FILE_BYTES) {
                val previous = File(dir, FILE_PREVIOUS)
                if (previous.exists()) previous.delete()
                current.renameTo(previous)
            }
            current.appendText(batch.joinToString("\n", postfix = "\n"))
        } catch (io: Exception) {
            // Падение логгера не должно ронять приложение
            Log.e("Ogneliss/Logger", "Не удалось записать лог", io)
        }
    }

    /** Все файлы лога от новых к старым. */
    fun files(): List<File> {
        val dir = logDir ?: return emptyList()
        return listOfNotNull(
            File(dir, FILE_CURRENT).takeIf { it.exists() },
            File(dir, FILE_PREVIOUS).takeIf { it.exists() }
        )
    }

    /** Суммарный размер лога в байтах — показывается в настройках. */
    fun sizeBytes(): Long = files().sumOf { it.length() }

    fun clear() {
        val dir = logDir ?: return
        worker.execute {
            runCatching { files().forEach { it.delete() } }
            Log.i("Ogneliss/Logger", "Лог очищен")
        }
    }
}
