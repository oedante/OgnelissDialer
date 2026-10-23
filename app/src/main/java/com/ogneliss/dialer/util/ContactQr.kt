package com.ogneliss.dialer.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ogneliss.dialer.log.Logger

/**
 * QR-КОД С КОНТАКТОМ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Поделиться номером при встрече: собеседник наводит камеру и получает
 * готовый контакт. Быстрее, чем диктовать, и без ошибок в цифрах.
 *
 * Работает без сети с обеих сторон — код рисуется на экране,
 * а читает его системная камера, которая понимает vCard из коробки.
 *
 * ── Почему vCard, а не «tel:» ──────────────────────────────────────────────
 * Ссылка tel: даёт только номер: собеседник получит цифры без имени
 * и сам будет заводить контакт. vCard несёт имя и все номера сразу,
 * и телефон предлагает сохранить их одним нажатием.
 *
 * Формат 3.0, а не 4.0: его понимают все камеры, включая старые.
 */
object ContactQr {

    /**
     * Визитка в формате vCard.
     *
     * @param name имя, как оно записано в книге
     * @param numbers номера в порядке важности — первый считается
     *        основным, его телефон предложит по умолчанию
     */
    /**
     * @param emails адреса почты. Пусто — строк EMAIL в визитке не будет:
     *        пустое поле часть камер показывает как «e-mail: —»,
     *        и это хуже, чем его отсутствие.
     */
    fun vCard(
        name: String,
        numbers: List<String>,
        emails: List<String> = emptyList()
    ): String = buildString {
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")

        // FN — отображаемое имя. Экранируем запятые и точки с запятой:
        // в vCard они разделяют поля, и имя «Иванов, Иван» без экранирования
        // распалось бы на два
        appendLine("FN:${escape(name)}")

        numbers.forEachIndexed { index, number ->
            val type = if (index == 0) "CELL,PREF" else "CELL"
            appendLine("TEL;TYPE=$type:${escape(number)}")
        }

        /*
         * Почта после телефонов.
         *
         * Порядок не случаен: визитку из диалера сканируют, чтобы
         * позвонить, и телефон должен идти первым. Часть камер
         * предлагает действие по первому полю визитки.
         */
        emails.forEachIndexed { index, email ->
            val type = if (index == 0) "INTERNET,PREF" else "INTERNET"
            appendLine("EMAIL;TYPE=$type:${escape(email)}")
        }

        append("END:VCARD")
    }

    /**
     * Отрисовка кода в изображение.
     *
     * @param size сторона в пикселях. Меньше 400 не стоит: код с большим
     *        числом модулей станет нечитаемым на бликующем экране
     * @return null, если строка не поместилась — для визитки это очень
     *         длинное имя или десяток номеров
     */
    fun render(content: String, size: Int = 640): Bitmap? = runCatching {
        val hints = mapOf(
            // Уровень M восстанавливает до 15 % повреждённых модулей.
            // Для экрана избыточно, но телефон снимают под углом
            // и при бликах — запас окупается
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            // Поля вокруг кода: без них камера не находит границы
            EncodeHintType.MARGIN to 2
        )

        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        /*
         * Рисуем чёрным по белому независимо от темы приложения.
         *
         * Инвертированный код — светлый по тёмному — камеры читают
         * заметно хуже: многие ищут именно тёмные модули на светлом
         * фоне. Ради тёмной темы жертвовать читаемостью нельзя.
         */
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val pixels = IntArray(size * size)

        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        bitmap
    }.getOrElse {
        Logger.w(TAG, "Не удалось построить QR-код", it)
        null
    }

    /** Экранирование символов, разделяющих поля в vCard. */
    private fun escape(value: String): String =
        value.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")

    private const val TAG = "ContactQr"

    /**
     * КОНТАКТ ТЕКСТОМ ДЛЯ ОТПРАВКИ ЛЮДЯМ.
     *
     * ══════════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НЕ vCard
     * ══════════════════════════════════════════════════════════════════════════
     * vCard создан для программ, а не для людей. Отправленный в мессенджер,
     * он выглядит так:
     *
     *   BEGIN:VCARD
     *   VERSION:3.0
     *   FN:Любимая
     *   TEL;TYPE=CELL,PREF:+79671112233
     *   END:VCARD
     *
     * Получатель видит служебную разметку и должен сам выискивать в ней
     * номер. Это ровно та работа, от которой мы хотели избавить.
     *
     * ── Где vCard всё же нужен ─────────────────────────────────────────────────
     * В QR-коде: его читает камера, а не человек. Там разметка уместна —
     * телефон разберёт её сам и предложит сохранить контакт.
     *
     * ── Почему номера в удобном для набора виде ────────────────────────────────
     * Форматированный номер можно продиктовать вслух и легче проверить
     * глазами. Скопировать его получатель тоже сможет: мессенджеры
     * распознают номера в тексте и делают их нажимаемыми.
     */
    fun plainText(
        name: String,
        numbers: List<String>,
        /** Почта из карточки. Пусто — строк не будет. */
        emails: List<String> = emptyList()
    ): String = buildString {
        appendLine(name)

        numbers.forEach { number ->
            appendLine(PhoneFormatter.format(number))
        }

        // Почта после телефонов и без подписи «e-mail»: адрес узнаётся
        // по собаке, а лишнее слово в коротком тексте только мешает
        emails.forEach { appendLine(it) }
    }.trim()

}
