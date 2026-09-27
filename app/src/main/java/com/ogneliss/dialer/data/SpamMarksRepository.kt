package com.ogneliss.dialer.data

import com.ogneliss.dialer.data.db.SpamMarkDao
import com.ogneliss.dialer.data.db.SpamMarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ВИД ОТМЕТКИ.
 *
 * Ключ хранится в базе строкой, а не номером перечисления: если когда-нибудь
 * появится третий вид или порядок поменяется, старые записи не поедут.
 */
enum class SpamMarkType(val key: String) {
    SPAM("spam"),
    FRAUD("fraud");

    companion object {
        fun fromKey(key: String?): SpamMarkType? = entries.firstOrNull { it.key == key }
    }
}

data class SpamMark(
    val e164: String,
    val type: SpamMarkType,
    val markedAt: Long
)

/**
 * ОТМЕТКИ «СПАМ» И «МОШЕННИКИ».
 *
 * Отдельно от заметок с версии 1.1.1 — разбор в SpamMarkEntity.
 *
 * Что отметка делает, решает переключатель «Блокировать помеченные»:
 * включён — SpamPolicy отклоняет номер до звонка, выключен — номер
 * звонит, но в журнале и на экране вызова предупреждение. Само хранилище
 * этого не знает и знать не должно: оно отвечает на вопрос «что отмечено»,
 * а не «что с этим делать».
 */
@Singleton
class SpamMarksRepository @Inject constructor(private val dao: SpamMarkDao) {

    /** Все отметки по номеру — для журнала, контактов, фильтров. */
    val all: Flow<Map<String, SpamMark>> = dao.all().map { list ->
        list.mapNotNull { it.toModel() }.associateBy { it.e164 }
    }

    /** Одна отметка — для фильтра вызовов, где счёт идёт на миллисекунды. */
    suspend fun get(e164: String): SpamMark? = dao.get(e164)?.toModel()

    suspend fun snapshot(): List<SpamMark> = dao.snapshot().mapNotNull { it.toModel() }

    /**
     * Поставить отметку.
     *
     * Повторная отметка заменяет прежнюю: номер не может быть
     * одновременно спамом и мошенниками — разбор в SpamMarkEntity.
     */
    suspend fun mark(e164: String, type: SpamMarkType) {
        dao.upsert(SpamMarkEntity(e164 = e164, type = type.key, markedAt = System.currentTimeMillis()))
    }

    suspend fun unmark(e164: String) = dao.delete(e164)

    /**
     * Восстановление из резервной копии: дата отметки — та, что в копии,
     * а не сегодняшняя. Иначе после переноса на новый телефон все отметки
     * выглядели бы поставленными в один день.
     */
    suspend fun restore(mark: SpamMark) {
        dao.upsert(SpamMarkEntity(mark.e164, mark.type.key, mark.markedAt))
    }

    /*
     * Неизвестный ключ пропускается, а не роняет чтение: если новая версия
     * добавит вид отметки, а человек откатится на старую, остальные
     * отметки должны продолжить работать.
     */
    private fun SpamMarkEntity.toModel(): SpamMark? =
        SpamMarkType.fromKey(type)?.let { SpamMark(e164, it, markedAt) }
}
