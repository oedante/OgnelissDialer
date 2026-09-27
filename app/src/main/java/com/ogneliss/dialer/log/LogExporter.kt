package com.ogneliss.dialer.log

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ogneliss.dialer.BuildConfig
import com.ogneliss.dialer.R
import java.io.File

/**
 * ВЫГРУЗКА ЛОГА ПОЛЬЗОВАТЕЛЕМ.
 *
 * Назначение: дать тестировщику возможность отправить журнал работы
 * (в мессенджер, на почту, в файловый менеджер) прямо с устройства.
 *
 * Как это работает и почему именно так:
 * • Файл отдаётся через FileProvider — прямая передача file:// URI
 *   запрещена начиная с Android 7 и приводит к FileUriExposedException.
 * • Получателю выдаётся временное разрешение только на чтение
 *   и только на конкретный файл.
 * • Перед отправкой файлы склеиваются в один и снабжаются шапкой
 *   с версией и моделью устройства — чтобы не пришлось это переспрашивать.
 * • Никакой автоматической отправки: делится только пользователь и только
 *   вручную. Приложение само в сеть не выходит.
 */
object LogExporter {

    private const val SHARE_FILE = "ogneliss-log.txt"

    /**
     * Собирает единый файл лога и возвращает Intent для системного
     * диалога «Поделиться». Null — если логов ещё нет.
     */
    fun buildShareIntent(context: Context): Intent? {
        val files = Logger.files()
        if (files.isEmpty()) return null

        val shareDir = File(context.filesDir, "share").apply { mkdirs() }
        val target = File(shareDir, SHARE_FILE)

        return runCatching {
            target.writeText(buildHeader(context))
            // Файлы возвращаются от новых к старым, склеиваем в хронологии
            files.reversed().forEach { f ->
                target.appendText("\n----- ${f.name} -----\n")
                target.appendText(f.readText())
            }

            val uri = FileProvider.getUriForFile(
                context, "${BuildConfig.APPLICATION_ID}.fileprovider", target
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.log_share_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.onFailure {
            Logger.e("LogExporter", "Не удалось подготовить лог к отправке", it)
        }.getOrNull()
    }

    /** Шапка с окружением: без неё половина вопросов к тестировщику. */
    private fun buildHeader(context: Context): String = buildString {
        appendLine("Ogneliss Dialer — журнал работы")
        appendLine("Версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Сборка: ${BuildConfig.BUILD_TYPE}")
        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("Устройство: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Локаль: ${context.resources.configuration.locales[0]}")
        appendLine()
        appendLine("Номера телефонов и имена в этом файле маскированы:")
        appendLine("виден код страны и последние 4 цифры, восстановить абонента нельзя.")
    }

    fun share(context: Context) {
        val intent = buildShareIntent(context)
        if (intent == null) {
            Logger.w("LogExporter", "Лог пуст, отправлять нечего")
            return
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.log_share_title))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Logger.e("LogExporter", "Не удалось открыть диалог отправки", it) }
    }
}
