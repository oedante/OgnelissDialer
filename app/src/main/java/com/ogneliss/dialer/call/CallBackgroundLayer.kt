package com.ogneliss.dialer.call

/**
 * КАКОЙ СЛОЙ РИСОВАТЬ ЗА ЭКРАНОМ ВЫЗОВА (2.7.4).
 *
 * Было: фото контакта во весь экран, живой фон или ровная заливка. Стало:
 * между ними — свой снимок человека (CallBackgrounds), отдельный для входящих
 * и исходящих. Фото контакта в круге, имя, состояние и кнопки лежат поверх
 * и не меняются; решение вынесено сюда, чтобы настоящий и пробный вызов
 * выбирали слой одинаково и это проверялось без Android (CallBackgroundLayerTest).
 */
enum class CallBackgroundLayer {
    /** Фото контакта на весь экран: оно и есть фон, свой снимок не рисуется. */
    PHOTO,

    /** Свой снимок человека во весь экран, под ним ничего. */
    CUSTOM,

    /** Живой фон (CallAmbientBackground). */
    AMBIENT,

    /** Ровная заливка цветом страницы (или фон окна, если панель под кнопками). */
    PLAIN;

    companion object {
        /**
         * @param photoBackground фото контакта идёт на весь экран (настройка + есть фото)
         * @param photoTop фото контакта по ширине сверху: для него свой снимок
         *   рисуется вместо ровной заливки под ним, а живой фон там и так не рисуется
         * @param hasCustom свой снимок включён и уже загружен: пока грузится
         *   или не прочитался — прежний фон, без мигания пустотой
         * @param ambientOn живой фон включён настройкой
         */
        fun decide(
            photoBackground: Boolean,
            photoTop: Boolean,
            hasCustom: Boolean,
            ambientOn: Boolean
        ): CallBackgroundLayer = when {
            photoBackground -> PHOTO
            hasCustom -> CUSTOM
            photoTop -> PLAIN
            ambientOn -> AMBIENT
            else -> PLAIN
        }

        /*
         * ЦВЕТ ТЕКСТА НАД СВОИМ СНИМКОМ (2.7.6, форум, Saxena1: «сделайте цифры
         * номера чёткими и крупными, когда установлены собственные обои»; автор:
         * «можно сделать подстройку под фон»). Было: имя цветом темы, номер —
         * бледно-серым (outline): на ярком снимке номер пропадал. Стало: по
         * яркости снимка в полосе, где стоят имя и номер, с учётом подложки
         * цвета страницы (CallCustomBackground) — белый или почти чёрный, что
         * контрастнее (формула контраста WCAG).
         */

        /** Полоса снимка под именем и номером — доли высоты сверху. */
        const val TEXT_BAND_FROM = 0.15f
        const val TEXT_BAND_TO = 0.60f

        /** Средняя плотность подложки в этой полосе (градиент 0,60 → 0,30 → 0,70). */
        const val TEXT_BAND_SCRIM = 0.40

        /** Относительная яркость цвета sRGB (WCAG), 0…1. */
        fun luminance(argb: Int): Double {
            fun ch(v: Int): Double {
                val c = v / 255.0
                return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
        }

        /**
         * Средняя яркость полосы [from, to) по высоте; по ширине — середина
         * (80 %): по краям снимок обрезается под экран, а текст — по центру.
         */
        fun bandLuminance(
            pixels: IntArray,
            width: Int,
            height: Int,
            from: Float = TEXT_BAND_FROM,
            to: Float = TEXT_BAND_TO
        ): Double {
            if (width <= 0 || height <= 0 || pixels.size < width * height) return 0.0
            val y0 = (height * from).toInt().coerceIn(0, height - 1)
            val y1 = (height * to).toInt().coerceIn(y0 + 1, height)
            val x0 = (width * 0.1f).toInt().coerceIn(0, width - 1)
            val x1 = (width * 0.9f).toInt().coerceIn(x0 + 1, width)
            var sum = 0.0
            var n = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                sum += luminance(pixels[y * width + x])
                n++
            }
            return if (n == 0) 0.0 else sum / n
        }

        /**
         * Тёмный текст над снимком? Яркость под текстом — снимок, смешанный
         * с подложкой цвета страницы; выбирается то, что контрастнее.
         */
        fun darkTextOver(imageLuminance: Double, pageLuminance: Double, scrim: Double = TEXT_BAND_SCRIM): Boolean {
            val l = imageLuminance * (1 - scrim) + pageLuminance * scrim
            val withBlack = (l + 0.05) / 0.05
            val withWhite = 1.05 / (l + 0.05)
            return withBlack > withWhite
        }
    }
}
