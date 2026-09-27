package com.ogneliss.dialer.backup

import android.content.Context
import android.net.Uri
import com.ogneliss.dialer.BuildConfig
import com.ogneliss.dialer.data.NotesRepository
import com.ogneliss.dialer.data.SpamMark
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.data.SpamMarksRepository
import com.ogneliss.dialer.data.RuleKind
import com.ogneliss.dialer.data.RulesRepository
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.settings.SpeedDialStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * РЕЗЕРВНОЕ КОПИРОВАНИЕ ПОЛЬЗОВАТЕЛЬСКИХ ДАННЫХ.
 *
 * ── Зачем это нужно именно нам ─────────────────────────────────────────────
 * В манифесте стоит allowBackup="false", и облачное копирование Android
 * для нас отключено намеренно — мы обещали, что данные не покидают устройство.
 * Обратная сторона: при смене телефона пользователь потеряет заметки, метки
 * и списки блокировки. Ручной экспорт закрывает эту дыру, не нарушая обещания:
 * файл создаёт сам пользователь и сам решает, куда его положить.
 *
 * ── Формат ─────────────────────────────────────────────────────────────────
 * Обычный JSON, человекочитаемый, с версией схемы. Не проприетарный формат
 * и не зашифрованный архив: пользователь должен иметь возможность открыть
 * файл и убедиться, что там нет ничего лишнего. Это часть доверия.
 *
 * ── Что НЕ попадает в резервную копию ──────────────────────────────────────
 * Журнал вызовов и контакты. Они принадлежат системным провайдерам Android
 * и восстанавливаются самим Android при переносе устройства.
 * Дублировать их означало бы создать файл с чужими персональными данными,
 * чего мы делать не хотим.
 *
 * ── Как файл выбирается ────────────────────────────────────────────────────
 * Через Storage Access Framework (системный диалог выбора файла).
 * Разрешения на доступ ко всему хранилищу приложению не нужны —
 * пользователь сам указывает конкретный файл. Это и требование Play,
 * и минимально необходимый доступ.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notes: NotesRepository,
    private val spamMarks: SpamMarksRepository,
    private val rules: RulesRepository,
    private val speedDial: SpeedDialStore
) {

    private companion object {
        const val TAG = "Backup"
        /** Версия формата. Увеличивать при несовместимых изменениях. */
        const val SCHEMA_VERSION = 1
        const val FILE_NAME_PREFIX = "ogneliss-backup"
    }

    /** Имя файла по умолчанию с датой — чтобы копии не перезаписывали друг друга. */
    fun suggestedFileName(): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "$FILE_NAME_PREFIX-$date.json"
    }

    /**
     * Записывает резервную копию в выбранный пользователем файл.
     * @return true при успехе
     */
    suspend fun export(target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = buildJson()
            context.contentResolver.openOutputStream(target)?.use { out ->
                out.write(json.toString(2).toByteArray())
            } ?: error("Не удалось открыть файл для записи")
            Logger.i(TAG, "Резервная копия сохранена")
            true
        }.getOrElse {
            Logger.e(TAG, "Ошибка при создании резервной копии", it)
            false
        }
    }

    /**
     * Читает резервную копию и ДОБАВЛЯЕТ данные к текущим.
     *
     * Импорт намеренно НЕ очищает существующие данные: пользователь,
     * восстанавливающий копию на устройстве, где уже что-то настроено,
     * не должен внезапно потерять это. Совпадающие ключи перезаписываются.
     */
    suspend fun import(source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)?.use {
                it.readBytes().decodeToString()
            } ?: error("Не удалось открыть файл для чтения")

            val root = JSONObject(text)
            val version = root.optInt("schemaVersion", 0)
            if (version > SCHEMA_VERSION) {
                error("Файл создан более новой версией приложения (схема $version)")
            }

            restoreNotes(root.optJSONArray("notes"))
            restoreSpamMarks(root.optJSONArray("spamMarks"))
            restoreRules(root.optJSONArray("rules"))
            restoreSpeedDial(root.optJSONArray("speedDial"))

            Logger.i(TAG, "Резервная копия восстановлена (схема $version)")
            true
        }.getOrElse {
            Logger.e(TAG, "Ошибка при восстановлении резервной копии", it)
            false
        }
    }

    // ── Сборка JSON ────────────────────────────────────────────────────────

    private suspend fun buildJson(): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("appVersion", BuildConfig.VERSION_NAME)
        root.put("createdAt", System.currentTimeMillis())

        // Заметки и метки
        val notesArray = JSONArray()
        notes.all.first().values.forEach { n ->
            notesArray.put(JSONObject().apply {
                put("e164", n.e164)
                put("note", n.note)
                put("tags", JSONArray(n.tags))
                put("label", n.customLabel ?: JSONObject.NULL)
            })
        }
        root.put("notes", notesArray)

        /*
         * ОТМЕТКИ «СПАМ» И «МОШЕННИКИ» — ОТДЕЛЬНЫМ МАССИВОМ.
         *
         * С 1.1.1 они не внутри заметок. Без этого массива перенос
         * на новый телефон молча терял бы все отметки: заметки бы
         * восстановились, а номера мошенников снова звонили бы.
         *
         * Версия формата не поднимается: массив новый, и старая
         * программа его просто пропустит, а не упадёт.
         */
        val marksArray = JSONArray()
        spamMarks.snapshot().forEach { m ->
            marksArray.put(JSONObject().apply {
                put("e164", m.e164)
                put("type", m.type.key)
                put("markedAt", m.markedAt)
            })
        }
        root.put("spamMarks", marksArray)

        // Чёрный и белый списки
        val rulesArray = JSONArray()
        rules.all.first().forEach { r ->
            rulesArray.put(JSONObject().apply {
                put("pattern", r.pattern)
                put("kind", r.kind)
                put("comment", r.comment ?: JSONObject.NULL)
            })
        }
        root.put("rules", rulesArray)

        // Быстрый набор
        val speedArray = JSONArray()
        speedDial.entries.first().forEach { e ->
            speedArray.put(JSONObject().apply {
                put("digit", e.digit)
                put("number", e.number)
                put("name", e.name ?: JSONObject.NULL)
            })
        }
        root.put("speedDial", speedArray)

        return root
    }

    // ── Восстановление ─────────────────────────────────────────────────────

    private suspend fun restoreNotes(array: JSONArray?) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val tagsJson = o.optJSONArray("tags")
            val tags = buildList {
                if (tagsJson != null) for (j in 0 until tagsJson.length()) add(tagsJson.getString(j))
            }
            val e164 = o.optString("e164")

            /*
             * СТАРАЯ КОПИЯ: ОТМЕТКИ ВНУТРИ ЗАМЕТОК.
             *
             * Копии до 1.1.1 хранят «spam» и «fraud» в метках заметки.
             * Восстановить их туда же — значит вернуть сцепление,
             * которое мы только что разорвали: отметка оказалась бы
             * в заметке, и ни журнал, ни фильтр вызовов её бы не увидели.
             *
             * Поэтому при восстановлении они уходят в отметки, а в
             * заметке остаются только прочие метки. Это та же логика,
             * что у миграции базы 5 → 6.
             */
            val legacyMark = when {
                "fraud" in tags -> SpamMarkType.FRAUD
                "spam" in tags -> SpamMarkType.SPAM
                else -> null
            }
            legacyMark?.let { spamMarks.mark(e164, it) }

            val note = o.optString("note")
            val restTags = tags.filter { it != "spam" && it != "fraud" }
            val label = o.optString("label").takeIf { it.isNotBlank() && it != "null" }

            // Заметка, которая существовала только ради метки, не нужна
            if (note.isBlank() && restTags.isEmpty() && label == null) continue

            notes.save(e164 = e164, note = note, tags = restTags, label = label)
        }
    }

    /** Отметки из копии 1.1.1 и новее; в старых копиях массива нет. */
    private suspend fun restoreSpamMarks(array: JSONArray?) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val type = SpamMarkType.fromKey(o.optString("type")) ?: continue
            val e164 = o.optString("e164").takeIf { it.isNotBlank() } ?: continue

            // Дата отметки — из копии, а не сегодняшняя: разбор в restore()
            spamMarks.restore(
                SpamMark(e164 = e164, type = type, markedAt = o.optLong("markedAt"))
            )
        }
    }

    private suspend fun restoreRules(array: JSONArray?) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val kind = if (o.optString("kind") == RuleKind.ALLOW.key)
                RuleKind.ALLOW else RuleKind.BLOCK
            rules.add(
                pattern = o.optString("pattern"),
                kind = kind,
                comment = o.optString("comment").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    private suspend fun restoreSpeedDial(array: JSONArray?) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            speedDial.assign(
                digit = o.optInt("digit"),
                number = o.optString("number"),
                name = o.optString("name").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
