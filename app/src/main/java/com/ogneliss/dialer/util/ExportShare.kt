package com.ogneliss.dialer.util

import android.content.Context
import android.content.Intent
import com.ogneliss.dialer.log.Logger

/**
 * ОТПРАВКА ТЕКСТОВОЙ ВЫГРУЗКИ.
 *
 * ── Почему «Поделиться», а не сохранение в файл ────────────────────────────
 * Выгрузку почти всегда нужно кому-то отправить: приложить к письму
 * оператору, переслать в мессенджере, вложить в заявление. Сохранение
 * добавляет три лишних шага — сохранить, найти, приложить.
 *
 * Кому нужен именно файл, выберет в том же системном листе пункт
 * сохранения на диск: это штатная возможность Android, и дублировать
 * её своей кнопкой незачем.
 *
 * ── Почему текст, а не вложение ────────────────────────────────────────────
 * `EXTRA_TEXT` вставляется в тело письма или сообщения — получатель
 * видит содержимое сразу. Вложение потребовало бы FileProvider
 * и временного файла, а читать его пришлось бы отдельным действием.
 *
 * Ограничение: очень длинный текст часть приложений обрезает. Для истории
 * по одному абоненту это не проблема — там десятки строк, а не тысячи.
 */
object ExportShare {

    fun shareText(
        context: Context,
        text: String,
        subject: String,
        chooserTitle: String
    ): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }

        context.startActivity(
            Intent.createChooser(intent, chooserTitle)
                // Флаг обязателен: контекст может быть не Activity,
                // и без него запуск падает
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrElse {
        Logger.w("Export", "Не удалось открыть лист отправки", it)
        false
    }
}
