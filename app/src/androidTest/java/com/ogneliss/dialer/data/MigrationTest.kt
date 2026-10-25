package com.ogneliss.dialer.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ogneliss.dialer.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ТЕСТЫ МИГРАЦИЙ БАЗЫ ДАННЫХ.
 *
 * ── Зачем это критично ─────────────────────────────────────────────────────
 * В базе лежат данные, которые пользователь создавал руками: заметки
 * к номерам, метки, чёрный и белый списки. Ошибка в миграции означает
 * их безвозвратную потерю при обновлении приложения — и пользователь
 * узнает об этом уже после установки апдейта, когда откатиться нельзя.
 *
 * Ручное тестирование здесь не работает: разработчик обычно ставит
 * приложение поверх чистой установки и миграцию просто не выполняет.
 *
 * ── Как это работает ───────────────────────────────────────────────────────
 * MigrationTestHelper создаёт базу СТАРОЙ версии по экспортированной схеме
 * из app/schemas, наполняет данными, прогоняет миграцию и проверяет,
 * что данные на месте.
 *
 * ⚠️ ТРЕБОВАНИЕ: файлы схем из app/schemas ОБЯЗАТЕЛЬНО коммитить
 * в репозиторий. Они генерируются при сборке (ksp room.schemaLocation)
 * и без них тест не запустится, а Room не сможет проверить миграцию.
 *
 * ── Как добавить тест новой миграции ───────────────────────────────────────
 * 1. Увеличить version в AppDatabase, написать Migration.
 * 2. Собрать проект — появится app/schemas/<N>.json.
 * 3. Скопировать шаблон migrate1To2 ниже, подставив свои версии и поля.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Проверка, что база первой версии вообще открывается.
     * Пока миграций нет, это защита от ошибок в самой схеме.
     */
    @Test
    fun createVersion1() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO number_notes (e164, note, tags, customLabel, updatedAt)
                VALUES ('+79001234567', 'Мастер по ремонту', 'work', NULL, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO number_rules (pattern, kind, comment, createdAt)
                VALUES ('+7800*', 'block', 'Все холодные обзвоны', 0)
                """.trimIndent()
            )
            close()
        }
    }

    /**
     * РЕАЛЬНЫЙ тест миграции 1 → 2: добавление таблицы sim_preferences.
     *
     * Ключевая проверка — не «миграция не упала», а «данные пользователя
     * на месте после миграции». Первое проходит и при полном очищении таблиц.
     */
    @Test
    fun migrate1To2_сохраняет_заметки_и_правила() {
        // 1. База версии 1 с данными пользователя
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO number_notes (e164, note, tags, customLabel, updatedAt) " +
                    "VALUES ('+79671112233', 'Не терять эту заметку', 'work', NULL, 100)"
            )
            execSQL(
                "INSERT INTO number_rules (pattern, kind, comment, createdAt) " +
                    "VALUES ('+7800*', 'block', 'Холодные обзвоны', 100)"
            )
            close()
        }

        // 2. Прогоняем миграцию
        val db = helper.runMigrationsAndValidate(
            TEST_DB, 2, true, AppDatabase.MIGRATION_1_2
        )

        // 3. Заметка пользователя должна выжить
        db.query("SELECT note, tags FROM number_notes WHERE e164 = '+79671112233'").use {
            assertTrue("Заметка пользователя потеряна при миграции", it.moveToFirst())
            assertEquals("Не терять эту заметку", it.getString(0))
            assertEquals("work", it.getString(1))
        }

        // 4. Правило блокировки тоже
        db.query("SELECT kind FROM number_rules WHERE pattern = '+7800*'").use {
            assertTrue("Правило блокировки потеряно при миграции", it.moveToFirst())
            assertEquals("block", it.getString(0))
        }

        // 5. Новая таблица должна существовать и быть пустой
        db.query("SELECT COUNT(*) FROM sim_preferences").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    /**
     * ШАБЛОН теста миграции для будущих версий.
     *
     * Ключевая проверка — не «миграция не упала», а «данные пользователя
     * на месте после миграции». Первое проходит и при полном очищении таблицы.
     */
    // @Test
    // fun migrate2To3_сохраняет_данные() {
    //     // 1. Создаём базу версии 1 и кладём данные
    //     helper.createDatabase(TEST_DB, 1).apply {
    //         execSQL(
    //             "INSERT INTO number_notes (e164, note, tags, customLabel, updatedAt) " +
    //                 "VALUES ('+79001234567', 'Не терять', 'work', NULL, 100)"
    //         )
    //         close()
    //     }
    //
    //     // 2. Прогоняем миграцию
    //     val db = helper.runMigrationsAndValidate(
    //         TEST_DB, 2, true, AppDatabase.MIGRATION_1_2
    //     )
    //
    //     // 3. Проверяем, что заметка выжила
    //     db.query("SELECT note, tags FROM number_notes WHERE e164 = '+79001234567'").use {
    //         assertTrue("Заметка пользователя потеряна при миграции", it.moveToFirst())
    //         assertEquals("Не терять", it.getString(0))
    //         assertEquals("work", it.getString(1))
    //     }
    // }

    /**
     * 5 → 6: метки спама уезжают из заметок в отдельную таблицу.
     *
     * Шесть случаев — те же, на которых миграция проверялась перед
     * выпуском. Главное, что проверяется: текст заметки не теряется,
     * посторонние метки остаются, а удаляются только заметки,
     * которые существовали лишь ради метки.
     */
    @Test
    fun отметкиСпамаПереезжаютИзЗаметок() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            fun note(e164: String, note: String, tags: String, label: String?) {
                db.execSQL(
                    "INSERT INTO number_notes (e164, note, tags, customLabel, updatedAt) " +
                        "VALUES (?, ?, ?, ?, 100)",
                    arrayOf(e164, note, tags, label)
                )
            }
            note("+71", "Замер мебели", "spam", null)
            note("+72", "", "spam", null)
            note("+73", "", "spam,fraud", null)
            note("+74", "Курьер", "банк,fraud", null)
            note("+75", "Просто заметка", "", null)
            note("+76", "", " spam ", "Сосед")
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6)

        val marks = mutableMapOf<String, String>()
        db.query("SELECT e164, type FROM spam_marks").use { c ->
            while (c.moveToNext()) marks[c.getString(0)] = c.getString(1)
        }
        assertEquals(
            mapOf("+71" to "spam", "+72" to "spam", "+73" to "fraud",
                  "+74" to "fraud", "+76" to "spam"),
            marks
        )

        val notes = mutableMapOf<String, Pair<String, String>>()
        db.query("SELECT e164, note, tags FROM number_notes").use { c ->
            while (c.moveToNext()) notes[c.getString(0)] = c.getString(1) to c.getString(2)
        }
        // +72 и +73 существовали только ради метки — их больше нет
        assertEquals(setOf("+71", "+74", "+75", "+76"), notes.keys)
        assertEquals("Замер мебели" to "", notes["+71"])
        assertEquals("Курьер" to "банк", notes["+74"])
    }

    /**
     * 6 → 7: в кэше книги появляется имя «Фамилия Имя» (1.6.2).
     *
     * Строка кэша переживает миграцию целиком, новый столбец — пустой
     * («как displayName»), пока книга не будет прочитана заново.
     */
    @Test
    fun кэшКнигиПолучаетИмяФамилияИмя() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO contact_cache (id, displayName, photoUri, starred, numbers, e164) " +
                    "VALUES (1, 'Анна Смирнова', NULL, 1, '+79001112233', '+79001112233')"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)

        db.query("SELECT displayName, starred, e164, nameAlt FROM contact_cache WHERE id = 1").use { c ->
            assertTrue("Строка кэша потерялась", c.moveToFirst())
            assertEquals("Анна Смирнова", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals("+79001112233", c.getString(2))
            assertEquals("", c.getString(3))
        }
    }

    /**
     * Проверка, что все объявленные миграции применяются подряд
     * от первой версии до текущей. Ловит пропущенную миграцию:
     * например, версия поднята с 2 до 3, а Migration(2,3) забыли написать.
     */
    @Test
    fun миграцииПрименяютсяПоследовательно() {
        helper.createDatabase(TEST_DB, 1).close()

        val currentVersion = AppDatabase::class.java
            .getAnnotation(androidx.room.Database::class.java)!!.version

        if (currentVersion > 1) {
            val db = helper.runMigrationsAndValidate(
                TEST_DB, currentVersion, true, *AppDatabase.MIGRATIONS
            )
            assertTrue("База не открылась после миграций", db.isOpen)
        }
    }
}
