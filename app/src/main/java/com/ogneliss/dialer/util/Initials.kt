package com.ogneliss.dialer.util

/**
 * Буквы для аватара контакта без фото (1.3.5, 05.10.2026, задача автора:
 * «чтобы контакты отделять от незнакомых номеров»).
 *
 * Правило:
 *  - первые буквы двух первых слов, которые начинаются с буквы:
 *    «Тестовый Контакт» → «ТК», «Sankras, Montazhnaya Kompaniya» → «SM»;
 *  - одно такое слово — одна буква: «Тест1» → «Т», «RusTobaco» → «R»;
 *  - слов с буквой нет («8 (495) 123-45-67», «911») — null: остаётся
 *    значок человека, как у незнакомого номера.
 * Слова — по пробелам и знакам препинания; цифры и знаки внутри слова
 * не мешают («Тест1» — слово «Тест1», первая буква «Т»).
 */
object Initials {

    fun of(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val letters = name
            .split(Regex("[\\s,;/|()\\[\\]«»\"]+"))
            .filter { it.isNotEmpty() && it.first().isLetter() }
            .take(2)
            .map { it.first().uppercaseChar() }
        return if (letters.isEmpty()) null else letters.joinToString("")
    }
}
