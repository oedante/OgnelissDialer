package com.ogneliss.dialer.util

/**
 * АДРЕСА, НА КОТОРЫЕ ВЕДЁТ ПРИЛОЖЕНИЕ.
 *
 * Одно место на все экраны: «О приложении», настройки и согласие при
 * первом запуске ссылаются на одни и те же страницы, и при переезде
 * адрес правится здесь, а не в трёх файлах.
 *
 * Сеть приложению для этого не нужна: адрес открывает браузер.
 */
object AppLinks {
    /** Исходный код (1.5.7: код открыт по лицензии Apache 2.0). */
    const val SOURCE_CODE = "https://github.com/oedante/OgnelissDialer"

    /**
     * Страница лицензии на сайте. Адрес прежний — /dialer/eula: по нему
     * лицензию открывают уже выпущенные версии и ссылки в обсуждениях,
     * а настройки сервера менять не нужно.
     */
    const val LICENSE = "https://ogneliss.ru/dialer/eula"
    const val PRIVACY = "https://ogneliss.ru/dialer/privacy"
    const val TERMS = "https://ogneliss.ru/dialer/terms"
    const val LICENSES = "https://ogneliss.ru/dialer/licenses"

    /**
     * Почта разработчика — та же, что на сайте и в карточке Google Play.
     * Кнопка «Написать письмо» в «Вопросах и ответах» (1.6.0).
     */
    const val SUPPORT_EMAIL = "oedante@gmail.com"
}
