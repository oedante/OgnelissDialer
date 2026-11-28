package com.ogneliss.dialer

import com.ogneliss.dialer.call.AutoRedialMode
import com.ogneliss.dialer.call.QuietHours
import com.ogneliss.dialer.call.RedialPolicy
import com.ogneliss.dialer.call.RedialPolicy.Decision
import com.ogneliss.dialer.data.RulePattern
import com.ogneliss.dialer.settings.SimRule
import com.ogneliss.dialer.settings.SimRules
import com.ogneliss.dialer.t9.T9Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * ПЕРЕБОР (2.9.13, техдолг, этап 1.3).
 *
 * Обычные тесты проверяют примеры, которые пришли в голову. Здесь — свойства,
 * которые обязаны выполняться для ЛЮБОГО входа, на сотнях случайных имён и
 * номеров: T9, маски блокировки, правила «Карты по номеру», тихие часы,
 * автодозвон. Именно в таких местах прячутся регрессии, которых не видно
 * в интерфейсе: подсветка съехала на букву, правило молча не срабатывает.
 *
 * Случайность повторяемая — у каждого теста свой постоянный seed: упавший
 * тест падает одинаково на любой машине, и в сообщении — сам вход.
 * Новых библиотек нет (kotest, jqwik не нужны): Random и циклы.
 *
 * Что перебор уже нашёл (исправлено в 2.9.13):
 * • T9Engine.matchIndex — подсветка в середине слова съезжала после дефиса
 *   или точки («Ab-cde» по «33» → «cd» вместо «de»);
 * • RulePattern — маска «0049*» становилась «+70049*», «810 49*» — «+71049*»:
 *   правило не срабатывало ни разу.
 */
class InvariantsTest {

    // ── T9 ──────────────────────────────────────────────────────────────────

    private val letters = "абвгдеёжзийклмнопрстуфхцчшщъыьэюяabcdefghijklmnopqrstuvwxyz"
    private val separators = listOf(" ", "-", ". ", " (", ") ", ", ", "  ", "'")

    private fun Random.word(): String =
        (1..nextInt(1, 9)).map { letters[nextInt(letters.length)] }.joinToString("")
            .replaceFirstChar { if (nextBoolean()) it.uppercaseChar() else it }

    private fun Random.name(): String {
        val words = (1..nextInt(1, 4)).map { word() }
        return words.reduce { acc, w -> acc + separators[nextInt(separators.size)] + w }
    }

    @Test
    fun `T9 — имя переводится только в цифры, кэш отвечает так же`() {
        val rnd = Random(9_001)
        repeat(500) {
            val name = rnd.name()
            val digits = T9Engine.toDigits(name)
            assertTrue("«$name» → «$digits»", digits.all { it in '0'..'9' })
            assertEquals("«$name»", digits, T9Engine.toDigitsCached(name))
        }
    }

    @Test
    fun `T9 — любое имя находится по началу любого своего слова`() {
        val rnd = Random(9_002)
        repeat(500) {
            val name = rnd.name()
            name.split(*separators.map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray(), " ")
                .filter { it.isNotEmpty() }
                .forEach { word ->
                    val prefix = word.take(rnd.nextInt(1, word.length + 1))
                    val query = T9Engine.toDigits(prefix)
                    assertTrue("«$name» не нашлось по «$query» («$prefix»)", T9Engine.matchIndex(query, name) >= 0)
                }
        }
    }

    @Test
    fun `T9 — подсветка начинается там, где совпало`() {
        val rnd = Random(9_003)
        repeat(2_000) {
            val name = rnd.name()
            val query = (1..rnd.nextInt(1, 4)).map { ('2' + rnd.nextInt(8)) }.joinToString("")
            val at = T9Engine.matchIndex(query, name)
            assertTrue("«$name» по «$query» → $at", at in -1 until name.length)
            if (at >= 0) {
                val shown = T9Engine.toDigits(name.substring(at))
                assertTrue("«$name» по «$query»: подсветка с $at — «${name.substring(at)}» ($shown)", shown.startsWith(query))
            }
        }
    }

    @Test
    fun `T9 — подсветка после дефиса не съезжает`() {
        // Пример, на котором перебор нашёл ошибку: «33» — это «de», с позиции 4
        assertEquals(4, T9Engine.matchIndex("33", "Ab-cde"))
        assertEquals(5, T9Engine.matchIndex("33", "A. bcde"))
    }

    // ── Номера ──────────────────────────────────────────────────────────────

    /** Случайный российский мобильный в E.164: «+79xxxxxxxxx». */
    private fun Random.mobile(): String = "+79" + (1..9).map { nextInt(10) }.joinToString("")

    /** Тот же номер так, как его пишут люди. */
    private fun Random.written(e164: String): String {
        val n = e164.removePrefix("+7")
        return when (nextInt(5)) {
            0 -> e164
            1 -> "+7 ${n.substring(0, 3)} ${n.substring(3, 6)}-${n.substring(6, 8)}-${n.substring(8)}"
            2 -> "8 (${n.substring(0, 3)}) ${n.substring(3, 6)}-${n.substring(6, 8)}-${n.substring(8)}"
            3 -> "8$n"
            else -> "+7(${n.substring(0, 3)})${n.substring(3)}"
        }
    }

    @Test
    fun `маска блокировки — номер в любой записи попадает под своё начало`() {
        val rnd = Random(9_101)
        repeat(1_000) {
            val e164 = rnd.mobile()
            val national = e164.removePrefix("+7")
            val prefix = national.take(rnd.nextInt(3, 10))
            val written = when (rnd.nextInt(4)) {
                0 -> "+7$prefix*"
                1 -> "8$prefix*"
                2 -> "8 (${prefix.take(3)}) ${prefix.drop(3)}*"
                else -> "$prefix*"
            }
            val parsed = RulePattern.parse(written, "RU")
            assertTrue("«$written» → $parsed", parsed is RulePattern.Result.Valid && parsed.isMask)
            val pattern = (parsed as RulePattern.Result.Valid).pattern
            assertTrue("«$written» ($pattern) не совпал с $e164", RulePattern.matches(pattern, e164))
            // Другая первая цифра после кода — уже не этот номер
            val other = "+7" + ((national[0] - '0' + 1) % 10) + national.drop(1)
            assertFalse("«$written» ($pattern) совпал с чужим $other", RulePattern.matches(pattern, other))
        }
    }

    @Test
    fun `маска с «?» — любая цифра на своём месте и только на своём`() {
        val rnd = Random(9_102)
        repeat(1_000) {
            val e164 = rnd.mobile()
            val pos = rnd.nextInt(3, e164.length)
            val mask = e164.substring(0, pos) + "?" + e164.substring(pos + 1)
            val pattern = (RulePattern.parse(mask, "RU") as RulePattern.Result.Valid).pattern
            val anyDigit = e164.substring(0, pos) + rnd.nextInt(10) + e164.substring(pos + 1)
            assertTrue("$pattern и $anyDigit", RulePattern.matches(pattern, anyDigit))
            // Цифра не на месте «?» — уже другой номер
            val fixed = (3 until e164.length).filter { it != pos }.random(rnd)
            val changed = e164.substring(0, fixed) + ((e164[fixed] - '0' + 1) % 10) + e164.substring(fixed + 1)
            assertFalse("$pattern и $changed", RulePattern.matches(pattern, changed))
        }
    }

    @Test
    fun `точный номер в любой записи — один и тот же`() {
        val rnd = Random(9_103)
        repeat(500) {
            val e164 = rnd.mobile()
            val written = rnd.written(e164)
            val parsed = RulePattern.parse(written, "RU")
            assertEquals("«$written»", RulePattern.Result.Valid(e164, isMask = false), parsed)
        }
    }

    @Test
    fun `8800 и международный выход в маске`() {
        fun mask(s: String) = (RulePattern.parse(s, "RU") as RulePattern.Result.Valid).pattern
        assertTrue(RulePattern.matches(mask("8800*"), "+78005553535"))
        assertTrue(RulePattern.matches(mask("8 800 *"), "+78005553535"))
        // «00» и «810» — выход на международную линию, то же, что «+»
        assertEquals("+49*", mask("0049*"))
        assertEquals("+49*", mask("810 49*"))
        assertTrue(RulePattern.matches(mask("0049 30*"), "+493012345678"))
        assertFalse(RulePattern.matches(mask("0049*"), "+70049123456"))
    }

    @Test
    fun `карта по номеру — запись номера не меняет карту`() {
        val rnd = Random(9_104)
        repeat(1_000) {
            val e164 = rnd.mobile()
            val national = e164.removePrefix("+7")
            val code = national.take(rnd.nextInt(3, 7))
            val rule = when (rnd.nextInt(3)) {
                0 -> "+7 $code"
                1 -> "8 $code"
                else -> code
            }
            val slot = rnd.nextInt(2)
            val rules = listOf(SimRule(rule, slot))
            val written = rnd.written(e164)
            assertEquals("«$rule» и «$written»", slot, SimRules.match(written, rules)?.slot)
        }
    }

    // ── Тихие часы ──────────────────────────────────────────────────────────

    @Test
    fun `тихие часы — каждая минута суток`() {
        for (minute in 0 until 24 * 60) {
            val hour = minute / 60
            assertFalse("OFF $minute", QuietHours.OFF.contains(minute))
            assertTrue("ALWAYS $minute", QuietHours.ALWAYS.contains(minute))
            assertEquals("22–08, $minute", hour >= 22 || hour < 8, QuietHours.NIGHT_22_08.contains(minute))
            assertEquals("23–07, $minute", hour >= 23 || hour < 7, QuietHours.NIGHT_23_07.contains(minute))
        }
        // Ночь через полночь: 10 и 8 часов тишины ровно
        assertEquals(600, (0 until 1440).count { QuietHours.NIGHT_22_08.contains(it) })
        assertEquals(480, (0 until 1440).count { QuietHours.NIGHT_23_07.contains(it) })
    }

    // ── Автодозвон ──────────────────────────────────────────────────────────

    private val causes = listOf(
        RedialPolicy.CAUSE_UNKNOWN, RedialPolicy.CAUSE_ERROR, RedialPolicy.CAUSE_LOCAL,
        RedialPolicy.CAUSE_REMOTE, RedialPolicy.CAUSE_CANCELED, RedialPolicy.CAUSE_MISSED,
        RedialPolicy.CAUSE_REJECTED, RedialPolicy.CAUSE_BUSY, RedialPolicy.CAUSE_RESTRICTED,
        RedialPolicy.CAUSE_OTHER
    )

    @Test
    fun `автодозвон — все сочетания`() {
        val bool = listOf(false, true)
        for (outgoing in bool) for (connected in bool) for (cause in causes)
            for (mode in AutoRedialMode.entries) for (otherCalls in bool) for (dialable in bool)
                for (ours in bool) for (max in 0..5) for (attempt in 0..max) {
                    val outcome = RedialPolicy.outcome(outgoing, connected, cause)
                    val d = RedialPolicy.afterCall(ours, attempt, max, outcome, mode, otherCalls, dialable)
                    val where = "исх=$outgoing соед=$connected код=$cause $mode другие=$otherCalls " +
                        "можно=$dialable наш=$ours $attempt/$max → $d"
                    val repeats = d is Decision.Wait || d is Decision.Offer
                    // Дозвонились, входящий, сбросил сам — повторять нечего
                    if (connected || !outgoing || cause == RedialPolicy.CAUSE_LOCAL || cause == RedialPolicy.CAUSE_CANCELED) {
                        assertFalse(where, repeats)
                    }
                    // Идёт другой разговор — никогда не перебиваем его повтором
                    if (otherCalls) assertFalse(where, repeats)
                    // Не наш сеанс: выключено или номер нельзя повторять — ничего
                    if (!ours && (mode == AutoRedialMode.OFF || !dialable)) assertEquals(where, Decision.None, d)
                    // Попыток не больше, чем выбрано
                    if (d is Decision.Wait) assertTrue(where, d.attempt <= maxOf(max, 1))
                }
    }

    @Test
    fun `автодозвон — короткие и служебные номера не повторяются`() {
        val rnd = Random(9_201)
        repeat(500) {
            val short = (1..rnd.nextInt(1, RedialPolicy.MIN_DIGITS)).map { rnd.nextInt(10) }.joinToString("")
            assertFalse(short, RedialPolicy.isDialable(short))
            val service = "*" + rnd.mobile().drop(1) + "#"
            assertFalse(service, RedialPolicy.isDialable(service))
            assertTrue(RedialPolicy.isDialable(rnd.written(rnd.mobile())))
        }
    }
}
