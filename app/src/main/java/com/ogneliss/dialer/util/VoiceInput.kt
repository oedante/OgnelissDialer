package com.ogneliss.dialer.util

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.ogneliss.dialer.log.Logger
import java.util.Locale

/**
 * ГОЛОСОВОЙ ВВОД НОМЕРА ИЛИ ИМЕНИ.
 *
 * ── Как это согласуется с офлайн-принципом ─────────────────────────────────
 * Распознавание выполняет СИСТЕМА, а не мы. У нашего приложения нет
 * разрешения INTERNET, поэтому отправить что-либо мы не можем физически.
 *
 * Но системный распознаватель по умолчанию может уйти в облако Google.
 * Поэтому мы запрашиваем `EXTRA_PREFER_OFFLINE`: если офлайн-модель
 * языка установлена, распознавание пройдёт на устройстве.
 *
 * Флаг называется PREFER, а не REQUIRE — гарантии он не даёт. Поэтому
 * в интерфейсе есть честное пояснение: распознавание выполняет система,
 * и оно может использовать сеть. Скрывать это было бы нечестно
 * на фоне остальных обещаний приложения.
 *
 * ── Почему Intent, а не SpeechRecognizer напрямую ──────────────────────────
 * SpeechRecognizer требует разрешения RECORD_AUDIO. Запрашивать доступ
 * к микрофону в диалере — плохая идея: пользователь сразу заподозрит
 * запись разговоров, а Google Play задаст вопросы при ревью.
 *
 * Intent открывает системный экран распознавания, микрофоном пользуется
 * система, и разрешение нам не нужно вовсе.
 */
object VoiceInput {

    private const val TAG = "Voice"

    /** Доступно ли распознавание речи на устройстве. */
    /**
     * Доступно ли распознавание речи.
     *
     * ── Почему проверка стала строже ───────────────────────────────────────
     * Раньше достаточно было, чтобы кто-то умел обработать Intent.
     * Но приложение Google отвечает на него всегда, а само распознавание
     * может быть недоступно — например, без сети и без офлайн-модели
     * языка. Пользователь нажимал микрофон и получал окно
     * «Голосовой поиск недоступен», из которого непонятно, что делать.
     *
     * Теперь спрашиваем именно систему распознавания. Если она молчит,
     * кнопки нет вовсе — это честнее, чем кнопка, ведущая в тупик.
     */
    fun isAvailable(context: Context): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(context)
    }.getOrDefault(false)

    /**
     * Intent для системного распознавания.
     * Результат обрабатывается вызывающим кодом через ActivityResult.
     */
    fun createIntent(context: Context, promptText: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // Язык берём из текущей локали приложения, а не системы:
            // пользователь мог переключить интерфейс на русский
            // при английской системе
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
            // Просим офлайн-режим. Гарантии нет — флаг называется PREFER
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Несколько вариантов: первый не всегда лучший для имён
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

    /**
     * Достаёт варианты из результата.
     * Возвращает список в порядке уверенности распознавателя.
     */
    fun extractResults(data: Intent?): List<String> {
        val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        Logger.d(TAG, "Распознано вариантов: ${results?.size ?: 0}")
        return results.orEmpty()
    }

    /**
     * Выбирает лучший вариант для набора.
     *
     * Распознаватель ранжирует по общей вероятности фразы, а нам нужно
     * другое: если хоть один вариант похож на телефонный номер,
     * он почти наверняка и есть искомый. «Восемь девятьсот» распознаётся
     * как число хуже, чем как слова, поэтому первый вариант часто не тот.
     */
    fun pickBest(variants: List<String>): VoiceQueryParser.Query {
        if (variants.isEmpty()) return VoiceQueryParser.Query.Empty

        val parsed = variants.map { VoiceQueryParser.parse(it) }

        // Сначала ищем вариант, похожий на настоящий номер
        parsed.filterIsInstance<VoiceQueryParser.Query.Number>()
            .firstOrNull { VoiceQueryParser.looksLikePhoneNumber(it.digits) }
            ?.let { return it }

        // Иначе берём первый осмысленный
        return parsed.firstOrNull { it !is VoiceQueryParser.Query.Empty }
            ?: VoiceQueryParser.Query.Empty
    }
}
