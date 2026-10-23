package com.ogneliss.dialer.t9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ T9-ДВИЖКА.
 *
 * T9 — самая чувствительная к ошибкам часть приложения: она отрабатывает
 * на каждое нажатие цифры, и регрессия здесь заметна пользователю сразу.
 * Тесты гоняются на JVM без эмулятора, поэтому выполняются за миллисекунды
 * и подходят для запуска на каждый коммит.
 */
class T9EngineTest {

    /**
     * Кэш перевода не должен менять результат.
     *
     * Он появился ради скорости: при тысяче контактов и наборе из пяти
     * цифр без него выполнялось бы пять тысяч переводов одних и тех же
     * имён. Но ускорение, меняющее ответ, — это не ускорение, а ошибка.
     */
    @Test
    fun `кэшированный перевод совпадает с прямым`() {
        listOf("Клинов", "Андрей Клинов", "O'Brien", "Мария-Анна", "")
            .forEach { name ->
                assertEquals(
                    "Кэш изменил результат для «$name»",
                    T9Engine.toDigits(name),
                    T9Engine.toDigitsCached(name)
                )
            }
    }

    @Test
    fun `повторный вызов возвращает тот же результат`() {
        val first = T9Engine.toDigitsCached("Клинов")
        val second = T9Engine.toDigitsCached("Клинов")
        assertEquals(first, second)
    }

    @Test
    fun `сброс кэша не ломает перевод`() {
        val before = T9Engine.toDigitsCached("Клинов")
        T9Engine.clearCache()
        assertEquals(before, T9Engine.toDigitsCached("Клинов"))
    }

    @Test
    fun `длинные строки не кэшируются, но переводятся верно`() {
        // Они встречаются редко и занимали бы место в кэше без пользы
        val long = "А".repeat(100)
        assertEquals(T9Engine.toDigits(long), T9Engine.toDigitsCached(long))
    }

    @Test
    fun `русское имя переводится в цифры`() {
        // А=2 Н=5 Д=3 Р=6 Е=3 Й=4
        assertEquals("253634", T9Engine.toDigits("Андрей"))
    }

    @Test
    fun `латинское имя переводится в цифры`() {
        assertEquals("22337", T9Engine.toDigits("Acder"))
    }

    @Test
    fun `регистр не влияет на результат`() {
        assertEquals(T9Engine.toDigits("АНДРЕЙ"), T9Engine.toDigits("андрей"))
    }

    @Test
    fun `ё приравнивается к е`() {
        assertEquals(T9Engine.toDigits("Семен"), T9Engine.toDigits("Семён"))
    }

    @Test
    fun `совпадение по началу фамилии находится`() {
        // "Хабиров Ренат": поиск по "63" должен найти "Ренат" (Р=6, Е=3)
        val index = T9Engine.matchIndex("63", "Хабиров Ренат")
        assertTrue("Ожидалось совпадение по началу второго слова", index >= 0)
    }

    @Test
    fun `отсутствие совпадения возвращает минус один`() {
        assertEquals(-1, T9Engine.matchIndex("999", "Андрей"))
    }

    @Test
    fun `пустой запрос совпадает со всем`() {
        assertEquals(0, T9Engine.matchIndex("", "Кто угодно"))
    }

    @Test
    fun `из номера остаются только цифры`() {
        assertEquals("79671112233", T9Engine.normalizeNumber("+7 (967) 111-22-33"))
    }
}
