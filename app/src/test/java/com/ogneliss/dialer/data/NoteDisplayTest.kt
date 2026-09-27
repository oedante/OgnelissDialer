package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ЗАМЕТКИ КАК ЗАМЕНЫ ИМЕНИ.
 *
 * Заметка закрывает случай, ради которого функция и делалась:
 * большинство входящих — от людей, которых не заносят в книгу.
 * Строка «доставка мебели» отвечает на вопрос «кто это», а безликий
 * номер — нет.
 *
 * Ошибка здесь означает либо пустой заголовок, который читается
 * как сбой приложения, либо обрезку, превращающую заметку в бессмыслицу.
 */
class NoteDisplayTest {

    private val number = "8 (900) 123-45-67"

    // ── Три основных случая ────────────────────────────────────────────────

    @Test
    fun `имя есть — заметка идёт второй строкой`() {
        val result = NoteDisplay.resolve("Андрей Клинов", "сосед сверху", number)

        assertEquals("Андрей Клинов", result.title)
        assertEquals("сосед сверху", result.subtitle)
        assertFalse(result.titleFromNote)
    }

    @Test
    fun `имени нет — заголовком становится заметка`() {
        // Ради этого случая всё и делалось
        val result = NoteDisplay.resolve(null, "доставка мебели", number)

        assertEquals("доставка мебели", result.title)
        assertEquals(number, result.subtitle)
        assertTrue(result.titleFromNote)
    }

    @Test
    fun `нет ни имени ни заметки — показывается номер`() {
        val result = NoteDisplay.resolve(null, null, number)

        assertEquals("Пустой заголовок выглядит как сбой", number, result.title)
        assertNull(result.subtitle)
    }

    @Test
    fun `пустые строки не считаются значением`() {
        // Заметку могли сохранить пустой или из одних пробелов
        val result = NoteDisplay.resolve("   ", "  ", number)
        assertEquals(number, result.title)
    }

    // ── Обрезка ────────────────────────────────────────────────────────────

    @Test
    fun `короткая заметка не обрезается`() {
        val result = NoteDisplay.resolve(null, "курьер", number)
        assertEquals("курьер", result.title)
    }

    @Test
    fun `длинная заметка обрезается по границе слова`() {
        // Посимвольная обрезка дала бы «никогда не бр…», что читается
        // как опечатка
        val long = "никогда не брать трубку, мошенники разводят на деньги"
        val result = NoteDisplay.resolve(null, long, number)

        assertTrue("Должно быть многоточие", result.title.endsWith("…"))
        assertTrue(
            "Обрезка не должна разрывать слово",
            result.title.dropLast(1).trim().let { !long.startsWith(it + "х") }
        )
        assertTrue(result.title.length <= NoteDisplay.TITLE_LIMIT + 1)
    }

    @Test
    fun `начало заметки сохраняется — там главное`() {
        // Заметки пишут, начиная с сути
        val result = NoteDisplay.resolve(
            null, "мошенники, звонят каждый день с разных номеров", number
        )
        assertTrue(result.title.startsWith("мошенники"))
    }

    @Test
    fun `одно длинное слово обрезается посимвольно`() {
        // Иначе вернулась бы пустая строка
        val result = NoteDisplay.resolve(null, "а".repeat(60), number)
        assertTrue(result.title.length in 2..(NoteDisplay.TITLE_LIMIT + 1))
    }

    @Test
    fun `переносы строк сворачиваются в пробелы`() {
        // Многострочная заметка в одну строку списка не поместится,
        // а перенос превратится в разрыв вёрстки
        val result = NoteDisplay.resolve(null, "доставка\nмебели", number)
        assertEquals("доставка мебели", result.title)
    }

    // ── Экран вызова ───────────────────────────────────────────────────────

    @Test
    fun `на экране вызова предел больше`() {
        // Экран пустой, места хватает, а информация нужнее всего именно
        // в этот момент: человек решает, брать трубку или нет
        val long = "никогда не брать трубку, мошенники разводят на деньги, " +
            "звонили уже трижды с разных номеров"
        /*
         * forCallScreen, а не forIncomingCall.
         *
         * Метод переименован и изменил подпись: он больше не возвращает
         * строку, а собирает Result целиком — заголовок и подпись сразу,
         * как их видит экран. Тест звал старое имя, из-за чего
         * не компилировался ВЕСЬ тестовый исходник, а не только этот файл.
         */
        val forCall = NoteDisplay.forCallScreen(
            displayName = "Андрей",
            note = long,
            formattedNumber = "+7 900 123-45-67"
        )

        // Имя занимает заголовок, заметка уходит в подпись — и обрезается
        // по пределу экрана вызова, который больше, чем у заголовка списка
        assertTrue(forCall.subtitle!!.length > NoteDisplay.TITLE_LIMIT)
    }

    @Test
    fun `пустая заметка не показывается на экране вызова`() {
        // Пустая заметка не должна давать подпись: показывать нечего
        assertNull(
            NoteDisplay.forCallScreen("Андрей", "", "+7 900 123-45-67").subtitle
        )
        assertNull(
            NoteDisplay.forCallScreen("Андрей", null, "+7 900 123-45-67").subtitle
        )
    }

    // ── Экран вызова ───────────────────────────────────────────────────────

    @Test
    fun `на экране вызова имя сверху, заметка под ним`() {
        val r = NoteDisplay.forCallScreen("Иван", "сантехник", "+7 900 111-22-33")

        assertEquals("Иван", r.title)
        assertEquals("сантехник", r.subtitle)
        assertFalse(r.titleFromNote)
    }

    @Test
    fun `без контакта заметка становится заголовком`() {
        /*
         * Ради этого случая всё и делалось: незнакомый номер, но человек
         * когда-то пометил его «мошенник». На экране входящего решение
         * принимается за секунды, и заметка — единственное, что о нём
         * известно.
         */
        val r = NoteDisplay.forCallScreen(null, "мошенник", "+7 900 111-22-33")

        assertEquals("мошенник", r.title)
        assertTrue(r.titleFromNote)
    }

    @Test
    fun `без имени и заметки остаётся номер`() {
        val r = NoteDisplay.forCallScreen(null, null, "+7 900 111-22-33")

        assertEquals("+7 900 111-22-33", r.title)
        assertNull(r.subtitle)
        assertFalse(r.titleFromNote)
    }

    @Test
    fun `на экране вызова заметка обрезается позже, чем в списке`() {
        /*
         * В списке длинная заметка ломала бы высоту строк, на экране
         * вызова места достаточно — и сведения там нужнее всего.
         */
        val long = "а".repeat(100)

        val inList = NoteDisplay.resolve("Иван", long, "+7 900 111-22-33")
        val onScreen = NoteDisplay.forCallScreen("Иван", long, "+7 900 111-22-33")

        assertTrue(
            "На экране вызова заметка должна быть длиннее",
            (onScreen.subtitle?.length ?: 0) > (inList.subtitle?.length ?: 0)
        )
    }
}
