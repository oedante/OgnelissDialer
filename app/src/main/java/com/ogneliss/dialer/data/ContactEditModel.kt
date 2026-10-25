package com.ogneliss.dialer.data

/*
 * ══════════════════════════════════════════════════════════════════════════
 * СВОЙ РЕДАКТОР КОНТАКТОВ (1.7.0) — МОДЕЛЬ И ПЛАН ЗАПИСИ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 23.10: «Давай попробуем сделать свой редактор контактов
 * в приложении. Очень много запросов на редактор контакта, системные
 * не всегда правильно воспринимают переход в них» (письмо с OPPO Reno 14:
 * системные «Контакты» там закрыты, пока основной — не системный
 * «Телефон»; отзыв в Google Play: «сильно не хватает возможности
 * редактировать контакты»).
 *
 * До 1.7.0 правка уходила в системное приложение — разбор рисков
 * в ContactEditor.kt. Те же риски определяют, как устроен свой редактор:
 *
 *   1. КОНТАКТ — СКЛЕЙКА. Один контакт — несколько «сырых» записей
 *      (RawContacts) из разных аккаунтов: Google, «Телефон», SIM,
 *      WhatsApp, Telegram. Редактор меняет только записи, которые можно
 *      менять (ContactEditRepository.isWritable): аккаунты с выгрузкой
 *      и «Телефон». Записи мессенджеров и SIM не трогаются вовсе —
 *      мессенджер перезапишет их при синхронизации, а SIM хранит только
 *      имя и один номер.
 *   2. СТРОКА ПРАВИТСЯ ТАМ, ГДЕ ЛЕЖИТ. Каждое поле помнит свою строку
 *      данных (_ID) и сырую запись. Изменили номер — обновляется именно
 *      эта строка; добавили новый — он ложится в основную запись
 *      (Google, если есть, иначе «Телефон»). Ничего не пересоздаётся:
 *      пересоздание поменяло бы _ID строк и сбило бы «основной номер»
 *      и статистику звонков в других приложениях.
 *   3. ЧЕГО РЕДАКТОР НЕ ЗНАЕТ — НЕ ТРОГАЕТ. Адреса, сайты, группы,
 *      фонетические имена, отчество из другой записи — остаются как
 *      были. Для них в меню — «Открыть в системных «Контактах»».
 *
 * План записи ([ContactEditPlan]) — чистый расчёт без Android: что
 * вставить, что обновить, что удалить. Его проверяют тесты
 * (ContactEditPlanTest); перевод в ContentProviderOperation —
 * в ContactEditRepository.
 */

/** Учётная запись, в которой хранится новый контакт. */
data class ContactAccount(
    /** null — «Телефон» без аккаунта. */
    val type: String?,
    val name: String?,
    /** Для показа: адрес Google, «Телефон», название сервиса. */
    val label: String,
    val kind: Kind
) {
    enum class Kind { GOOGLE, DEVICE, OTHER }

    /** Ключ для запоминания последнего выбора. */
    val key: String get() = "${type.orEmpty()}\u0000${name.orEmpty()}"
}

/** Ссылка на строку данных в книге. */
data class RowRef(val dataId: Long, val rawId: Long)

/**
 * Номер или адрес почты.
 *
 * [rows] — строки книги, которые это значение представляет: обычно одна;
 * больше — когда один номер записан в двух склеенных записях (Google
 * и «Телефон»). Правка и удаление идут во все сразу — иначе в контакте
 * остался бы старый номер рядом с новым. Пусто — новое значение.
 */
data class EditableValue(
    val key: Long,
    val value: String = "",
    val type: Int,
    /** Своя подпись типа (TYPE_CUSTOM = 0) — например, «Дача». */
    val label: String? = null,
    val rows: List<RowRef> = emptyList()
)

/** Всё, что редактор показывает и меняет. */
data class ContactDraft(
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val phones: List<EditableValue> = emptyList(),
    val emails: List<EditableValue> = emptyList(),
    val company: String = "",
    val title: String = "",
    /** «1988-12-05» или «--12-05» (без года); пусто — нет. Разбор — ContactBirthday. */
    val birthday: String = "",
    val note: String = ""
) {
    /** Сохранять нечего: ни имени, ни номера, ни почты, ни организации. */
    val isEmpty: Boolean
        get() = given.isBlank() && middle.isBlank() && family.isBlank() &&
            phones.none { it.value.isNotBlank() } && emails.none { it.value.isNotBlank() } &&
            company.isBlank()
}

/** Контакт, открытый для правки: черновик и где что лежит. */
data class LoadedContact(
    val contactId: Long,
    val draft: ContactDraft,
    /** Строки имени во всех изменяемых записях: имя меняется во всех. */
    val nameRows: List<RowRef> = emptyList(),
    val companyRow: RowRef? = null,
    val birthdayRow: RowRef? = null,
    val noteRow: RowRef? = null,
    /** Строки фото в изменяемых записях — для «Удалить фото». */
    val photoRows: List<RowRef> = emptyList(),
    /** Куда ложатся новые строки; null — изменяемых записей нет. */
    val primaryRawId: Long? = null,
    /** Откуда остальные сведения: «WhatsApp», «SIM» — их здесь не меняют. */
    val readOnlySources: List<String> = emptyList(),
    /** Фото контакта для показа (общее по склейке). */
    val photoUri: String? = null
) {
    val editable: Boolean get() = primaryRawId != null
}

/** Что записать: строка одного из видов, которые знает редактор. */
sealed interface RowValue {
    data class Name(val given: String, val middle: String, val family: String) : RowValue
    data class Phone(val number: String, val type: Int, val label: String?) : RowValue
    data class Email(val address: String, val type: Int, val label: String?) : RowValue
    data class Organization(val company: String, val title: String) : RowValue
    data class Birthday(val date: String) : RowValue
    data class Note(val text: String) : RowValue
}

/** Шаг записи. [rawId] = null у вставки — в новую запись (создание контакта). */
sealed interface EditOp {
    data class Insert(val rawId: Long?, val row: RowValue) : EditOp
    data class Update(val dataId: Long, val row: RowValue) : EditOp
    data class Delete(val dataId: Long) : EditOp
}

/** Изменение фото. */
sealed interface PhotoChange {
    data object Keep : PhotoChange
    data object Remove : PhotoChange
    class Set(val jpeg: ByteArray) : PhotoChange
}

object ContactEditPlan {

    /** Тип TYPE_CUSTOM у телефонов и почты: подпись — в label. */
    const val TYPE_CUSTOM = 0

    /** Новый контакт: всё непустое — вставками в новую запись. */
    fun forNew(draft: ContactDraft): List<EditOp> = buildList {
        name(draft)?.let { add(EditOp.Insert(null, it)) }
        draft.phones.filter { it.value.isNotBlank() }.forEach { add(EditOp.Insert(null, phone(it))) }
        draft.emails.filter { it.value.isNotBlank() }.forEach { add(EditOp.Insert(null, email(it))) }
        organization(draft)?.let { add(EditOp.Insert(null, it)) }
        draft.birthday.trim().takeIf { it.isNotEmpty() }?.let { add(EditOp.Insert(null, RowValue.Birthday(it))) }
        draft.note.trim().takeIf { it.isNotEmpty() }?.let { add(EditOp.Insert(null, RowValue.Note(it))) }
    }

    /**
     * Правка: только то, что изменилось. Неизменённое не пишется вовсе —
     * у синхронизируемого аккаунта каждая запись уходит на сервер.
     */
    fun forEdit(loaded: LoadedContact, draft: ContactDraft): List<EditOp> {
        val primary = loaded.primaryRawId ?: return emptyList()
        val before = loaded.draft
        return buildList {
            // ── Имя: во всех изменяемых записях, чтобы склейка не показала старое
            if (!sameName(before, draft)) {
                val value = name(draft)
                when {
                    value == null -> loaded.nameRows.forEach { add(EditOp.Delete(it.dataId)) }
                    loaded.nameRows.isEmpty() -> add(EditOp.Insert(primary, value))
                    else -> loaded.nameRows.forEach { add(EditOp.Update(it.dataId, value)) }
                }
            }

            // ── Номера и почта
            addAll(listDiff(before.phones, draft.phones, primary, ::phone))
            addAll(listDiff(before.emails, draft.emails, primary, ::email))

            // ── Одиночные строки
            single(loaded.companyRow, primary, organization(before), organization(draft))
            single(loaded.birthdayRow, primary, birthday(before), birthday(draft))
            single(loaded.noteRow, primary, note(before), note(draft))
        }
    }

    private fun MutableList<EditOp>.single(row: RowRef?, primary: Long, old: RowValue?, new: RowValue?) {
        if (old == new) return
        when {
            row == null -> new?.let { add(EditOp.Insert(primary, it)) }
            new == null -> add(EditOp.Delete(row.dataId))
            else -> add(EditOp.Update(row.dataId, new))
        }
    }

    private fun listDiff(
        before: List<EditableValue>,
        after: List<EditableValue>,
        primary: Long,
        row: (EditableValue) -> RowValue
    ): List<EditOp> = buildList {
        val afterByKey = after.associateBy { it.key }
        // Удалённые из списка — удалить все их строки
        before.filter { it.key !in afterByKey }.forEach { gone ->
            gone.rows.forEach { add(EditOp.Delete(it.dataId)) }
        }
        val beforeByKey = before.associateBy { it.key }
        after.forEach { value ->
            val old = beforeByKey[value.key]
            when {
                // Новое значение
                old == null || value.rows.isEmpty() -> {
                    if (value.value.isNotBlank()) add(EditOp.Insert(primary, row(value)))
                }
                // Поле очистили — как удалить
                value.value.isBlank() -> value.rows.forEach { add(EditOp.Delete(it.dataId)) }
                // Изменили — во всех строках-близнецах
                row(old) != row(value) -> value.rows.forEach { add(EditOp.Update(it.dataId, row(value))) }
            }
        }
    }

    private fun sameName(a: ContactDraft, b: ContactDraft) =
        a.given.trim() == b.given.trim() && a.middle.trim() == b.middle.trim() && a.family.trim() == b.family.trim()

    private fun name(d: ContactDraft): RowValue.Name? =
        RowValue.Name(d.given.trim(), d.middle.trim(), d.family.trim())
            .takeIf { it.given.isNotEmpty() || it.middle.isNotEmpty() || it.family.isNotEmpty() }

    private fun phone(v: EditableValue) =
        RowValue.Phone(v.value.trim(), v.type, v.label?.trim()?.takeIf { v.type == TYPE_CUSTOM && it.isNotEmpty() })

    private fun email(v: EditableValue) =
        RowValue.Email(v.value.trim(), v.type, v.label?.trim()?.takeIf { v.type == TYPE_CUSTOM && it.isNotEmpty() })

    private fun organization(d: ContactDraft): RowValue.Organization? =
        RowValue.Organization(d.company.trim(), d.title.trim())
            .takeIf { it.company.isNotEmpty() || it.title.isNotEmpty() }

    private fun birthday(d: ContactDraft): RowValue.Birthday? =
        d.birthday.trim().takeIf { it.isNotEmpty() }?.let { RowValue.Birthday(it) }

    private fun note(d: ContactDraft): RowValue.Note? =
        d.note.trim().takeIf { it.isNotEmpty() }?.let { RowValue.Note(it) }
}

/**
 * ДЕНЬ РОЖДЕНИЯ В КНИГЕ: «1988-12-05» или «--12-05».
 *
 * Второй вид — без года: люди часто помнят число и месяц, а год не
 * спрашивают. Редактор пишет оба вида так же, как системные «Контакты»,
 * и читает их же; строку иного вида (введённую руками в другом
 * приложении) не разбирает — показывает как есть и не меняет, пока
 * человек сам не выберет дату.
 */
object ContactBirthday {

    data class Parts(val year: Int?, val month: Int, val day: Int)

    private val WITH_YEAR = Regex("""^(\d{4})-(\d{2})-(\d{2})""")
    private val NO_YEAR = Regex("""^--(\d{2})-(\d{2})$""")

    fun parse(raw: String): Parts? {
        val s = raw.trim()
        WITH_YEAR.find(s)?.destructured?.let { (y, m, d) ->
            return Parts(y.toInt(), m.toInt(), d.toInt()).takeIf { valid(it) }
        }
        NO_YEAR.find(s)?.destructured?.let { (m, d) ->
            return Parts(null, m.toInt(), d.toInt()).takeIf { valid(it) }
        }
        return null
    }

    fun format(parts: Parts): String {
        val md = "%02d-%02d".format(parts.month, parts.day)
        return parts.year?.let { "%04d-%s".format(it, md) } ?: "--$md"
    }

    private fun valid(p: Parts) = p.month in 1..12 && p.day in 1..31
}
