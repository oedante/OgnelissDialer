package com.ogneliss.dialer.data

/**
 * ОТМЕТКА СОСТОЯНИЯ КНИГИ — КОГДА ЕЁ ПЕРЕЧИТЫВАТЬ (2.8.8).
 *
 * Разбор — у ContactsRepository.changeStamp. Здесь — только правило, чистый
 * Kotlin: проверяется модульными тестами без провайдера контактов.
 */
object ContactsStamp {

    /** Нет разрешения на контакты: своя отметка — выдали доступ, и она сменится. */
    const val NO_ACCESS = "no-access"

    fun of(count: Int, starred: Int, lastUpdated: Long, lastDeleted: Long): String =
        "$count/$starred/$lastUpdated/$lastDeleted"

    /**
     * Читать книгу целиком.
     *
     * @param current отметка сейчас; null — не прочиталась
     * @param lastRead отметка, с которой книга прочитана в прошлый раз; null — ещё не читали
     * @param haveContacts в состоянии уже есть контакты. Пусто — читать всегда:
     *        пустота бывает сбоем занятого провайдера, и повтор её исправляет
     *        (разбор у повторных попыток в DialerViewModel.doRefresh)
     */
    fun mustRead(current: String?, lastRead: String?, haveContacts: Boolean): Boolean =
        !haveContacts || current == null || lastRead == null || current != lastRead
}
