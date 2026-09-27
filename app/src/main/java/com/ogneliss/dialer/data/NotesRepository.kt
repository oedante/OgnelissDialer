package com.ogneliss.dialer.data

import androidx.compose.runtime.Immutable

import com.ogneliss.dialer.data.db.NumberNoteDao
import com.ogneliss.dialer.data.db.NumberNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ЗАМЕТКИ И МЕТКИ К НОМЕРАМ.
 *
 * ── Ключ — E.164, а не идентификатор контакта ──────────────────────────────
 * Заметка переживает удаление контакта и работает для номеров, которых
 * в книге нет вовсе — а это как раз самый частый случай: пометить
 * незнакомый номер, с которого звонили.
 *
 * ── Метки хранятся ключами, а не переводами ────────────────────────────────
 * В базу пишется "spam", а показывается «спам» или «spam» по локали.
 * Иначе при смене языка интерфейса метки пользователя «расползлись» бы
 * по языкам, и фильтр по спаму перестал бы их находить.
 *
 * ── Пустая заметка удаляет запись ──────────────────────────────────────────
 * Хранить пустые строки незачем: они занимают место и попадают
 * в резервную копию, ничего не значая.
 */

@Immutable
data class NumberNote(
    val e164: String,
    val note: String,
    val tags: List<String>,
    val customLabel: String? = null,
    /**
     * Когда заметка изменена в последний раз.
     *
     * Нужно очистке по сроку давности: без этого поля нельзя отличить
     * вчерашнюю пометку о курьере от прошлогодней.
     *
     * В базе поле есть с первой версии — оно просто не выносилось
     * в модель, потому что до появления очистки не требовалось.
     */
    val updatedAt: Long = 0
)

@Singleton
class NotesRepository @Inject constructor(private val dao: NumberNoteDao) {

    val all: Flow<Map<String, NumberNote>> = dao.observeAll().map { list ->
        list.associate { it.e164 to it.toModel() }
    }

    suspend fun get(e164: String): NumberNote? = dao.get(e164)?.toModel()

    suspend fun save(e164: String, note: String, tags: List<String>, label: String?) {
        if (note.isBlank() && tags.isEmpty() && label.isNullOrBlank()) {
            dao.delete(e164); return
        }
        dao.upsert(
            NumberNoteEntity(
                e164 = e164,
                note = note.trim(),
                tags = tags.joinToString(","),
                customLabel = label?.takeIf { it.isNotBlank() }
            )
        )
    }

    /**
     * Удаление заметки.
     *
     * Нужно и фоновой очистке по сроку, и сохранению пустого текста:
     * стёртая заметка должна исчезать, а не оставаться пустой строкой,
     * иначе она продолжит попадать в поиск и занимать место.
     */
    suspend fun delete(e164: String) = dao.delete(e164)

    suspend fun addTag(e164: String, tag: String) {
        val cur = get(e164)
        val tags = (cur?.tags.orEmpty() + tag).distinct()
        save(e164, cur?.note.orEmpty(), tags, cur?.customLabel)
    }

    suspend fun search(query: String): List<NumberNote> =
        dao.search(query.trim()).map { it.toModel() }

    private fun NumberNoteEntity.toModel() = NumberNote(
        e164 = e164,
        note = note,
        tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        customLabel = customLabel,
        // Время изменения переносится из сущности: без него очистка
        // по сроку давности считала бы все заметки одинаково свежими
        updatedAt = updatedAt
    )
}
