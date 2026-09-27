package com.ogneliss.dialer.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import android.view.View
import com.ogneliss.dialer.log.Logger

/**
 * ОТКЛИК КЛАВИАТУРЫ: ВИБРАЦИЯ И ТОНАЛЬНЫЕ СИГНАЛЫ.
 *
 * ── Почему это важнее, чем кажется ─────────────────────────────────────────
 * Набор номера — действие вслепую: человек смотрит на бумажку или в окно,
 * а не на экран. Отклик подтверждает нажатие, не заставляя проверять
 * глазами. Без него растёт число ошибок набора, а пользователь
 * жалуется на «нечувствительные кнопки», хотя нажатие прошло.
 *
 * ── Про тоны DTMF ──────────────────────────────────────────────────────────
 * Это те самые звуки кнопочного телефона. Они привычны, а на слух
 * ещё и различимы между собой — опытное ухо замечает ошибку набора
 * по неверному тону.
 *
 * ToneGenerator создаётся один раз и переиспользуется: создание занимает
 * заметное время, и делать его на каждое нажатие означало бы задержку
 * там, где отклик обязан быть мгновенным.
 *
 * ── Уважение к системным настройкам ────────────────────────────────────────
 * Тон играется по каналу DTMF, поэтому подчиняется системной громкости
 * и молчит в беззвучном режиме. Вибрация идёт через стандартный
 * HapticFeedback, который так же учитывает системную настройку:
 * если пользователь отключил тактильный отклик глобально, наш
 * переключатель его не пересилит — и это правильно.
 */
class DialpadFeedback {

    private companion object {
        const val TAG = "Feedback"
        /**
         * Длительность тона. 120 мс — примерно как на кнопочном телефоне:
         * короче звучит щелчком, длиннее начинает мешать быстрому набору.
         */
        const val TONE_DURATION_MS = 120
        /** Громкость относительно канала DTMF, проценты. */
        const val TONE_VOLUME = 70
    }

    private var toneGenerator: ToneGenerator? = null

    /** Создаём при первом использовании: не всем нужны тоны. */
    private fun generator(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = runCatching {
                ToneGenerator(AudioManager.STREAM_DTMF, TONE_VOLUME)
            }.getOrElse {
                // На части устройств ToneGenerator недоступен при занятом
                // аудиоканале. Это не повод ломать набор номера
                Logger.w(TAG, "Тональные сигналы недоступны", it)
                null
            }
        }
        return toneGenerator
    }

    /**
     * Отклик на нажатие клавиши.
     *
     * @param view нужен для тактильного отклика: он идёт через систему,
     *             а не через Vibrator напрямую, и потому учитывает
     *             настройки доступности
     */
    fun onKeyPress(view: View?, digit: Char, haptic: Boolean, tones: Boolean) {
        if (haptic) {
            runCatching {
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        if (tones) {
            runCatching { generator()?.startTone(toneFor(digit), TONE_DURATION_MS) }
        }
    }

    /** Отклик на удаление цифры — другой по ощущению, чем ввод. */
    fun onBackspace(view: View?, haptic: Boolean) {
        if (!haptic) return
        runCatching {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Освобождение ресурсов при закрытии экрана. */
    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    /** Соответствие цифры тону DTMF — стандарт телефонии. */
    private fun toneFor(digit: Char): Int = when (digit) {
        '0' -> ToneGenerator.TONE_DTMF_0
        '1' -> ToneGenerator.TONE_DTMF_1
        '2' -> ToneGenerator.TONE_DTMF_2
        '3' -> ToneGenerator.TONE_DTMF_3
        '4' -> ToneGenerator.TONE_DTMF_4
        '5' -> ToneGenerator.TONE_DTMF_5
        '6' -> ToneGenerator.TONE_DTMF_6
        '7' -> ToneGenerator.TONE_DTMF_7
        '8' -> ToneGenerator.TONE_DTMF_8
        '9' -> ToneGenerator.TONE_DTMF_9
        '*' -> ToneGenerator.TONE_DTMF_S
        '#' -> ToneGenerator.TONE_DTMF_P
        else -> ToneGenerator.TONE_DTMF_0
    }
}
