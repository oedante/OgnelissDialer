package com.ogneliss.dialer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ogneliss.dialer.BuildConfig
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * БАЗА ДАННЫХ ПРИЛОЖЕНИЯ.
 *
 * Хранит всё, чего может быть много и что нужно искать:
 * заметки к номерам, метки, чёрный и белый списки, T9-индекс.
 * Одиночные настройки-переключатели живут в DataStore (см. settings/Prefs.kt).
 *
 * ── О миграциях ────────────────────────────────────────────────────────────
 * fallbackToDestructiveMigration НЕ используется намеренно: при обновлении
 * приложения он молча стирает все данные. Для заметок пользователя и списков
 * блокировки это неприемлемо.
 *
 * При изменении схемы обязательно:
 *  1. Увеличить version.
 *  2. Написать Migration и добавить в MIGRATIONS.
 *  3. Схемы экспортируются в app/schemas — их нужно коммитить в репозиторий,
 *     Room по ним проверяет корректность миграций в тестах.
 *
 * Исключение — T9-индекс: это КЭШ, его можно и нужно пересобирать заново
 * при любой миграции, данных пользователя там нет.
 */
@Database(
    entities = [
        NumberNoteEntity::class,
        NumberRuleEntity::class,
        T9IndexEntity::class,
        SimPreferenceEntity::class,
        BlockedCallEntity::class,
        ContactCacheEntity::class,
        ReminderEntity::class,
        SpamMarkEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NumberNoteDao
    abstract fun rules(): NumberRuleDao
    abstract fun t9(): T9IndexDao

    abstract fun blockedCalls(): BlockedCallDao

    abstract fun contactCache(): ContactCacheDao
    abstract fun simPreferences(): SimPreferenceDao
    abstract fun reminders(): ReminderDao
    abstract fun spamMarks(): SpamMarkDao

    companion object {
        /**
         * Список миграций. Пока схема первой версии — список пуст.
         * Пример будущей миграции оставлен закомментированным как образец.
         */
        val MIGRATIONS: Array<Migration>
            get() = arrayOf(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7
            )

        /**
         * ВЕРСИЯ 6 → 7: ИМЯ «ФАМИЛИЯ ИМЯ» В КЭШЕ КНИГИ (1.6.2).
         *
         * Настройка «Как показывать имя» берёт форму «Фамилия Имя»
         * из книги (DISPLAY_NAME_ALTERNATIVE). Кэш даёт журнал при
         * запуске, до чтения книги, — без этого столбца имена первые
         * секунды шли бы «Имя Фамилия» и потом переставлялись.
         *
         * Один новый столбец с пустым значением: пусто значит «как
         * displayName», и старые строки остаются верными до первого
         * чтения книги, которое их перезапишет. Других таблиц не трогает.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contact_cache ADD COLUMN nameAlt TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * ВЕРСИЯ 5 → 6: ОТМЕТКИ О СПАМЕ ОТДЕЛЕНЫ ОТ ЗАМЕТОК.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО ДЕЛАЕТ
         * ══════════════════════════════════════════════════════════════════
         * 1. Создаёт таблицу spam_marks.
         * 2. Переносит в неё метки «spam» и «fraud» из заметок.
         *    Если у номера были обе — остаются мошенники.
         * 3. Убирает эти метки из заметок, прочие метки не трогает.
         * 4. Удаляет заметки, которые после этого опустели: они
         *    существовали только ради метки, и оставить их — значит
         *    показать на экране «Заметки» пустые строки.
         *
         * ── Почему шаги 3 и 4 в коде, а не одним SQL ───────────────────────
         * Метки хранятся строкой через запятую («spam,банк»). Вырезать
         * из неё одно слово средствами SQLite можно, но это цепочка
         * REPLACE с краевыми случаями (первая, последняя, единственная
         * метка, пробелы). Разобрать строку в Kotlin — три строки,
         * которые видно целиком. Для миграции, которая выполняется
         * один раз на каждом телефоне и ошибка в которой портит данные,
         * читаемость важнее изящества.
         *
         * ⚠️ Миграция не удаляет ничего, кроме меток спама и пустых
         * заметок. Текст заметки, её подпись и дата остаются как были.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS spam_marks (
                        e164 TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        markedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                data class Row(
                    val e164: String,
                    val note: String,
                    val tags: List<String>,
                    val customLabel: String?,
                    val updatedAt: Long
                )

                val rows = mutableListOf<Row>()
                db.query(
                    "SELECT e164, note, tags, customLabel, updatedAt FROM number_notes"
                ).use { c ->
                    while (c.moveToNext()) {
                        rows += Row(
                            e164 = c.getString(0),
                            note = c.getString(1).orEmpty(),
                            tags = c.getString(2).orEmpty()
                                .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            customLabel = if (c.isNull(3)) null else c.getString(3),
                            updatedAt = c.getLong(4)
                        )
                    }
                }

                rows.forEach { row ->
                    val type = when {
                        "fraud" in row.tags -> "fraud"
                        "spam" in row.tags -> "spam"
                        else -> return@forEach
                    }

                    db.execSQL(
                        "INSERT OR REPLACE INTO spam_marks (e164, type, markedAt) VALUES (?, ?, ?)",
                        arrayOf<Any>(row.e164, type, row.updatedAt)
                    )

                    val rest = row.tags.filter { it != "spam" && it != "fraud" }
                    val empty = row.note.isBlank() && rest.isEmpty() && row.customLabel.isNullOrBlank()

                    if (empty) {
                        db.execSQL("DELETE FROM number_notes WHERE e164 = ?", arrayOf<Any>(row.e164))
                    } else {
                        db.execSQL(
                            "UPDATE number_notes SET tags = ? WHERE e164 = ?",
                            arrayOf<Any>(rest.joinToString(","), row.e164)
                        )
                    }
                }
            }
        }

        /**
         * Версия 4 → 5: напоминания перезвонить.
         *
         * Раньше напоминание существовало только как будильник в системе:
         * посмотреть список было нельзя, отменить до срабатывания —
         * тоже, а после перезагрузки телефона оно молча пропадало,
         * потому что восстанавливать было не из чего.
         *
         * Существующие таблицы не трогаются.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS callback_reminders (
                        e164 TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT,
                        atMillis INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Версия 1 → 2: добавлена таблица предпочтений SIM для абонентов.
         *
         * Существующие таблицы НЕ трогаются: заметки, метки и списки
         * блокировки пользователя переживают обновление без потерь.
         * Именно ради этого мы отказались от fallbackToDestructiveMigration.
         */
        /**
         * Версия 2 → 3: таблица заблокированных вызовов.
         *
         * Раньше причины блокировки жили в памяти и терялись при
         * перезапуске процесса. Самый частый случай при этом ломался:
         * человек утром открывает телефон, видит пропущенные за ночь
         * и хочет понять, почему они не прошли — а объяснять уже нечем.
         *
         * Существующие таблицы не трогаются: заметки, правила
         * и предпочтения SIM переживают обновление без потерь.
         */
        /**
         * Версия 3 → 4: кэш телефонной книги.
         *
         * Чтение книги через провайдера занимает секунды, и всё это время
         * журнал показывался без фотографий. Кэш даёт их сразу при
         * запуске, а настоящее чтение приходит следом и заменяет его.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS contact_cache (
                        id INTEGER NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        photoUri TEXT,
                        starred INTEGER NOT NULL,
                        numbers TEXT NOT NULL,
                        e164 TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS blocked_calls (
                        e164 TEXT NOT NULL PRIMARY KEY,
                        reason TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Индекс по времени: выборка последних записей и очистка
                // старых идут именно по нему, а без индекса каждая
                // означала бы перебор всей таблицы
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_blocked_calls_at ON blocked_calls(at)"
                )
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sim_preferences (
                        e164 TEXT NOT NULL PRIMARY KEY,
                        slotIndex INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
/**
 * Имена @Provides-функций с префиксом provide — не стиль, а необходимость:
 * Hilt превращает их в имена Java-методов, и совпадение с ключевым словом
 * Java ломает генерацию с невнятной ошибкой KSP.
 */
object DatabaseModule {

    private const val DB_NAME = "ogneliss.db"

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            /*
             * РАЗРУШИТЕЛЬНАЯ МИГРАЦИЯ ТОЛЬКО В DEBUG.
             *
             * Во время разработки схема меняется часто, и версию базы
             * при этом забывают увеличить. Room сравнивает контрольную
             * сумму схемы и при расхождении бросает IllegalStateException
             * «Room cannot verify the data integrity» — приложение падает
             * при первом же обращении к базе, то есть сразу после запуска.
             *
             * Ровно этим объясняется «падает при открытии, помогает только
             * очистка данных»: очистка удаляет файл базы, и он создаётся
             * заново по актуальной схеме.
             *
             * В release этого нет и быть не должно: там расхождение схемы
             * означает ошибку в миграции, и молча стирать заметки, метки
             * и списки блокировки пользователя недопустимо. Пусть лучше
             * упадёт на нашем тестировании, чем потеряет данные у людей.
             */
            .apply {
                if (BuildConfig.DEBUG) {
                    // Параметр dropAllTables появился в Room 2.7; на более ранних
                    // версиях метод вызывается без него
                    @Suppress("DEPRECATION")
                    fallbackToDestructiveMigration()
                }
            }
            // Разрушительная миграция разрешена ТОЛЬКО при откате версии назад
            // (например, тестировщик поставил старую сборку поверх новой).
            // Обычное обновление вперёд всегда идёт через явные миграции.
            /*
             * Откат на предыдущую версию приложения.
             *
             * Случается при установке старого APK поверх нового —
             * например, когда пользователь ставит сборку из другого
             * источника. База новее, чем ожидает код, и открыть её
             * нельзя: остаётся пересоздать.
             *
             * Параметр dropAllTables появился только в Room 2.7,
             * поэтому вызов без него — иначе сборка падает на более
             * ранних версиях библиотеки.
             */
            // Room 2.8 объявил вариант без параметра устаревшим и требует сказать
            // явно, сносить ли при откате версии и чужие таблицы. false — ровно то,
            // что делал прежний вариант: пересоздаются только таблицы Room.
            // Поведение не меняется
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
            .build()

    @Provides fun provideNoteDao(db: AppDatabase) = db.notes()
    @Provides fun provideRuleDao(db: AppDatabase) = db.rules()
    @Provides fun provideT9Dao(db: AppDatabase) = db.t9()
    @Provides fun provideBlockedCallDao(db: AppDatabase) = db.blockedCalls()
    @Provides fun provideReminderDao(db: AppDatabase) = db.reminders()
    @Provides fun provideSpamMarkDao(db: AppDatabase) = db.spamMarks()
    @Provides fun provideContactCacheDao(db: AppDatabase) = db.contactCache()
    @Provides fun provideSimPreferenceDao(db: AppDatabase) = db.simPreferences()
}
