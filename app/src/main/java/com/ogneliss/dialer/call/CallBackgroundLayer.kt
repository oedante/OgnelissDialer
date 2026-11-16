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
    }
}
