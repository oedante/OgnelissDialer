package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ СРОКА ЖИЗНИ ЗАМЕТОК.
 *
 * Здесь удаляются данные пользователя, и ошибка необратима: восстановить
 * заметку неоткуда. Поэтому проверяется в первую очередь то, что НЕ должно
 * удаляться.
 */
class NotesRetentionTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun note(
        e164: String,
        ageDays: Int,
        tags: List<String> = emptyList()
    ) = NumberNote(
        e164 = e164,
        note = "заметка",
        tags = tags,
        updatedAt = now - ageDays * day
    )

    // ── Что не удаляется никогда ───────────────────────────────────────────

    @Test
    fun `при настройке «хранить всегда» ничего не удаляется`() {
        val notes = mapOf("+7900A" to note("+7900A", ageDays = 1000))

        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.NEVER,
            NotesRetention.Scope.ALL, emptySet(), now
        )
        assertTrue(toDelete.isEmpty())
    }

    @Test
    fun `метка спама защищает заметку от удаления`() {
        // Её писали именно затем, чтобы помнить долго: номер мошенников
        // остаётся номером мошенников и через год
        val notes = mapOf(
            "+7900A" to note("+7900A", ageDays = 1000, tags = listOf("spam"))
        )

        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.MONTH,
            NotesRetention.Scope.ALL, emptySet(), now
        )
        assertTrue("Заметка со спам-меткой не должна удаляться", toDelete.isEmpty())
    }

    @Test
    fun `метка мошенников тоже защищает`() {
        val notes = mapOf(
            "+7900A" to note("+7900A", ageDays = 1000, tags = listOf("fraud"))
        )
        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.MONTH,
            NotesRetention.Scope.ALL, emptySet(), now
        )
        assertTrue(toDelete.isEmpty())
    }

    @Test
    fun `свежая заметка не удаляется`() {
        val notes = mapOf("+7900A" to note("+7900A", ageDays = 5))
        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.MONTH,
            NotesRetention.Scope.ALL, emptySet(), now
        )
        assertTrue(toDelete.isEmpty())
    }

    // ── Область действия ───────────────────────────────────────────────────

    @Test
    fun `по умолчанию заметки о контактах сохраняются`() {
        // Заметка о человеке из книги относится к живой связи,
        // а не к разовому делу
        val notes = mapOf("+7900A" to note("+7900A", ageDays = 400))

        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.MONTH,
            NotesRetention.Scope.UNKNOWN_ONLY,
            knownNumbers = setOf("+7900A"), nowMillis = now
        )
        assertTrue(toDelete.isEmpty())
    }

    @Test
    fun `заметка о незнакомом номере удаляется`() {
        val notes = mapOf("+7900A" to note("+7900A", ageDays = 400))

        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.MONTH,
            NotesRetention.Scope.UNKNOWN_ONLY,
            knownNumbers = emptySet(), nowMillis = now
        )
        assertEquals(setOf("+7900A"), toDelete)
    }

    @Test
    fun `область «все» удаляет и заметки о контактах`() {
        val notes = mapOf("+7900A" to note("+7900A", ageDays = 400))

        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.YEAR,
            NotesRetention.Scope.ALL,
            knownNumbers = setOf("+7900A"), nowMillis = now
        )
        assertEquals(setOf("+7900A"), toDelete)
    }

    // ── Пороги ─────────────────────────────────────────────────────────────

    @Test
    fun `заметка на границе срока сохраняется`() {
        // Строго старше порога, а не «примерно»: на границе лучше
        // оставить лишнее, чем удалить нужное
        val notes = mapOf("+7900A" to note("+7900A", ageDays = 30))

        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.MONTH,
            NotesRetention.Scope.ALL, emptySet(), now
        )
        assertTrue(toDelete.isEmpty())
    }

    @Test
    fun `полугодовой порог не трогает заметку четырёхмесячной давности`() {
        val notes = mapOf("+7900A" to note("+7900A", ageDays = 120))
        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.HALF_YEAR,
            NotesRetention.Scope.ALL, emptySet(), now
        )
        assertTrue(toDelete.isEmpty())
    }

    @Test
    fun `заметка без времени изменения не удаляется`() {
        // updatedAt = 0 означает, что время неизвестно: судить о возрасте
        // нельзя, и удалять на всякий случай тем более
        val notes = mapOf("+7900A" to NumberNote("+7900A", "заметка", emptyList(), updatedAt = 0))

        val toDelete = NotesRetention.selectForDeletion(
            notes, NotesRetention.Period.MONTH,
            NotesRetention.Scope.ALL, emptySet(), now
        )
        assertTrue(toDelete.isEmpty())
    }
}
