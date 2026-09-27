package com.ogneliss.dialer.call

import android.content.Context
import com.ogneliss.dialer.R

/**
 * БЫСТРЫЙ ОТВЕТ ПРИ ОТКЛОНЕНИИ ВЫЗОВА.
 *
 * Назначение: отклонить звонок и одновременно отправить сообщение,
 * объясняющее почему.
 *
 * ── Как это работает на уровне телефонии ───────────────────────────────────
 * `Call.reject(true, message)` — второй параметр и есть текст. Система
 * сама отправляет его через SMS-приложение по умолчанию. Нам НЕ нужно
 * разрешение SEND_SMS и не нужна роль SMS-обработчика: телефония
 * делает это от имени системы.
 *
 * Это принципиально: запрос SEND_SMS в диалере вызвал бы те же
 * подозрения, что и микрофон, и потребовал бы отдельной декларации
 * в Google Play.
 *
 * ── Почему шаблоны, а не свободный ввод ────────────────────────────────────
 * Момент отклонения — худшее время для набора текста: телефон звонит,
 * человек на совещании или за рулём. Свободный ввод здесь означает
 * пропустить звонок, пока печатаешь.
 *
 * Шаблоны нажимаются одним касанием. Свой текст можно добавить заранее,
 * в спокойной обстановке.
 *
 * ── Ограничение ────────────────────────────────────────────────────────────
 * Не все операторы и не все прошивки поддерживают отклонение с текстом.
 * Если возможность недоступна, кнопки просто не показываются —
 * молча, без объяснений, потому что объяснять пользователю
 * особенности его оператора бессмысленно.
 */
object QuickReply {

    /**
     * Стандартные шаблоны.
     *
     * Формулировки нейтральные и короткие: длинный текст разобьётся
     * на два SMS, а это уже деньги.
     */
    fun defaultTemplates(context: Context): List<String> = listOf(
        context.getString(R.string.quick_reply_busy),
        context.getString(R.string.quick_reply_call_later),
        context.getString(R.string.quick_reply_meeting),
        context.getString(R.string.quick_reply_driving)
    )

    /**
     * Отклонить вызов с текстом.
     *
     * @return false, если возможность недоступна — тогда вызывающий код
     *         должен отклонить звонок обычным способом
     */
    fun rejectWith(message: String): Boolean {
        val call = CallManager.primary ?: return false
        return runCatching {
            call.reject(true, message)
            true
        }.getOrDefault(false)
    }

    /**
     * Доступно ли отклонение с сообщением для текущего вызова.
     * Проверяется по возможностям вызова, а не предполагается.
     */
    fun isAvailable(): Boolean {
        val call = CallManager.primary ?: return false
        val capabilities = call.details?.callCapabilities ?: return false
        return capabilities and android.telecom.Call.Details.CAPABILITY_RESPOND_VIA_TEXT != 0
    }
}
