package com.ogneliss.dialer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * ОБЪЕКТЫ ДОСТУПА К ДАННЫМ (DAO).
 *
 * Room генерирует реализацию по этим интерфейсам во время сборки.
 *
 * ── Соглашения ─────────────────────────────────────────────────────────────
 * • `observe*` возвращают Flow — экран подписывается и обновляется сам.
 * • Остальные методы suspend: они блокируют поток на время запроса,
 *   и Room запрещает вызывать их из главного потока.
 * • Вставка идёт с OnConflictStrategy.REPLACE: повторное сохранение
 *   заметки для того же номера должно перезаписывать, а не падать.
 */

/** Заметки и метки к номерам. Ключ — номер в формате E.164. */
@Dao
interface NumberNoteDao {
    @Query("SELECT * FROM number_notes")
    fun observeAll(): Flow<List<NumberNoteEntity>>

    @Query("SELECT * FROM number_notes WHERE e164 = :e164")
    suspend fun get(e164: String): NumberNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NumberNoteEntity)

    @Query("DELETE FROM number_notes WHERE e164 = :e164")
    suspend fun delete(e164: String)

    @Query("SELECT * FROM number_notes WHERE note LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<NumberNoteEntity>
}

/** Правила блокировки и разрешений, включая маски вида «+7800*». */
@Dao
interface NumberRuleDao {
    @Query("SELECT * FROM number_rules")
    fun observeAll(): Flow<List<NumberRuleEntity>>

    @Query("SELECT * FROM number_rules WHERE kind = :kind")
    suspend fun byKind(kind: String): List<NumberRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NumberRuleEntity)

    @Query("DELETE FROM number_rules WHERE pattern = :pattern")
    suspend fun delete(pattern: String)
}

/** Запомненный выбор SIM для конкретных номеров. */
@Dao
interface SimPreferenceDao {
    @Query("SELECT * FROM sim_preferences WHERE e164 = :e164")
    suspend fun get(e164: String): SimPreferenceEntity?

    @Query("SELECT * FROM sim_preferences")
    fun observeAll(): Flow<List<SimPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SimPreferenceEntity)

    @Query("DELETE FROM sim_preferences WHERE e164 = :e164")
    suspend fun delete(e164: String)
}

/**
 * Индекс T9-поиска.
 *
 * Это КЭШ, а не источник правды: пересобирается из контактов фоновой
 * задачей. Поэтому здесь есть clear() — при пересборке индекс
 * очищается целиком, а не обновляется по записям.
 *
 * Сортировка в запросе выводит наверх совпадения с начала строки:
 * набирая «526», человек имеет в виду «Клинов», а не «Александр Клин».
 */
@Dao
interface T9IndexDao {
    @Query("""
        SELECT * FROM t9_index
        WHERE digits LIKE :q || '%' OR digits LIKE '%' || :q || '%'
           OR numberDigits LIKE '%' || :q || '%'
        ORDER BY CASE WHEN digits LIKE :q || '%' THEN 0 ELSE 1 END
        LIMIT :limit
    """)
    suspend fun search(q: String, limit: Int = 50): List<T9IndexEntity>

    @Query("DELETE FROM t9_index")
    suspend fun clear()

    @Insert
    suspend fun insertAll(items: List<T9IndexEntity>)

    @Query("SELECT COUNT(*) FROM t9_index")
    suspend fun count(): Int
}

@Dao
interface BlockedCallDao {

    /**
     * Последние записи.
     *
     * Ограничение в запросе, а не в коде: выбрать пятьсот строк
     * из базы дешевле, чем прочитать все и отбросить лишние.
     */
    @Query("SELECT * FROM blocked_calls ORDER BY at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<BlockedCallEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BlockedCallEntity)

    @Query("DELETE FROM blocked_calls WHERE e164 = :e164")
    suspend fun delete(e164: String)

    /** Запись о номере (1.8.0): «звонил без звука в тихие часы — и когда». */
    @Query("SELECT * FROM blocked_calls WHERE e164 = :e164 LIMIT 1")
    suspend fun get(e164: String): BlockedCallEntity?

    /**
     * Очистка старых записей.
     *
     * По времени, а не по количеству: причина блокировки годичной
     * давности бесполезна, а пятисотая за вчера — вполне нужна.
     */
    @Query("DELETE FROM blocked_calls WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM blocked_calls")
    suspend fun clear()
}

@Dao
interface ContactCacheDao {

    @Query("SELECT * FROM contact_cache ORDER BY displayName COLLATE NOCASE")
    suspend fun all(): List<ContactCacheEntity>

    /**
     * Полная замена кэша.
     *
     * Именно замена, а не слияние: удалённый контакт должен исчезнуть,
     * а понять, какие исчезли, дешевле очисткой, чем сравнением.
     */
    @Transaction
    suspend fun replaceAll(items: List<ContactCacheEntity>) {
        clear()
        insertAll(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ContactCacheEntity>)

    @Query("DELETE FROM contact_cache")
    suspend fun clear()
}

@Dao
interface ReminderDao {

    /**
     * Все напоминания по времени срабатывания.
     *
     * Flow, а не разовое чтение: значок напоминания стоит в строке
     * журнала и в контактах, и оба списка должны обновиться сразу,
     * как только напоминание поставили или сняли.
     */
    @Query("SELECT * FROM callback_reminders ORDER BY atMillis")
    fun all(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM callback_reminders")
    suspend fun snapshot(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReminderEntity)

    @Query("DELETE FROM callback_reminders WHERE e164 = :e164")
    suspend fun delete(e164: String)

    /**
     * Уборка сработавших.
     *
     * Само срабатывание удаляет свою запись, но если процесс убили
     * между будильником и удалением, строка останется навсегда
     * и будет показывать значок несуществующего напоминания.
     */
    @Query("DELETE FROM callback_reminders WHERE atMillis < :before")
    suspend fun deleteExpired(before: Long)
}

@Dao
interface SpamMarkDao {

    /**
     * Все отметки.
     *
     * Flow: пометка в строке журнала должна поменяться в тот же миг,
     * как отметку поставили или сняли, — без пересборки списка.
     */
    @Query("SELECT * FROM spam_marks")
    fun all(): Flow<List<SpamMarkEntity>>

    @Query("SELECT * FROM spam_marks")
    suspend fun snapshot(): List<SpamMarkEntity>

    /** Для фильтра вызовов: одно чтение по ключу, без обхода таблицы. */
    @Query("SELECT * FROM spam_marks WHERE e164 = :e164")
    suspend fun get(e164: String): SpamMarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SpamMarkEntity)

    @Query("DELETE FROM spam_marks WHERE e164 = :e164")
    suspend fun delete(e164: String)
}

/** Имена от оператора (1.8.0): разбор у NetworkNameEntity. */
@Dao
interface NetworkNameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: NetworkNameEntity)

    @Query("SELECT * FROM network_names WHERE e164 = :e164 LIMIT 1")
    suspend fun get(e164: String): NetworkNameEntity?

    /** Последние — для журнала: ограничение в запросе, как у заблокированных. */
    @Query("SELECT * FROM network_names ORDER BY seenAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NetworkNameEntity>

    @Query("DELETE FROM network_names WHERE seenAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

/** Черновики SMS (2.2.0). Ключ — smsPinKey(адрес). */
@Dao
interface SmsDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: SmsDraftEntity)

    @Query("SELECT * FROM sms_drafts WHERE address = :key LIMIT 1")
    suspend fun get(key: String): SmsDraftEntity?

    @Query("DELETE FROM sms_drafts WHERE address = :key")
    suspend fun delete(key: String)

    /** Все — для строки «Черновик: …» в списке SMS. */
    @Query("SELECT * FROM sms_drafts")
    fun all(): Flow<List<SmsDraftEntity>>
}

/** Очередь отправки SMS (2.2.0): отложенные и окно «Отменить». */
@Dao
interface ScheduledSmsDao {

    @Insert
    suspend fun insert(entry: ScheduledSmsEntity): Long

    @Query("SELECT * FROM sms_scheduled WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): ScheduledSmsEntity?

    @Query("SELECT * FROM sms_scheduled ORDER BY atMillis")
    fun all(): Flow<List<ScheduledSmsEntity>>

    @Query("SELECT * FROM sms_scheduled ORDER BY atMillis")
    suspend fun snapshot(): List<ScheduledSmsEntity>

    /** Число удалённых строк: 0 — уже отправлено или отменено в другом месте. */
    @Query("DELETE FROM sms_scheduled WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE sms_scheduled SET atMillis = :at WHERE id = :id")
    suspend fun reschedule(id: Long, at: Long)
}

/** Избранные сообщения (2.2.0). */
@Dao
interface StarredSmsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entries: List<StarredSmsEntity>)

    @Query("DELETE FROM sms_starred WHERE msgKey IN (:keys)")
    suspend fun delete(keys: List<String>)

    @Query("SELECT * FROM sms_starred ORDER BY date DESC")
    fun all(): Flow<List<StarredSmsEntity>>

    @Query("SELECT * FROM sms_starred ORDER BY date DESC")
    suspend fun snapshot(): List<StarredSmsEntity>
}
