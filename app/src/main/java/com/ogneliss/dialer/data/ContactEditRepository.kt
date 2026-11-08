package com.ogneliss.dialer.data

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import com.ogneliss.dialer.R
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ЧТЕНИЕ И ЗАПИСЬ КОНТАКТА ДЛЯ СВОЕГО РЕДАКТОРА (1.7.0).
 *
 * Правила — в ContactEditModel.kt: меняются только записи, которые можно
 * менять; строка правится там, где лежит; чего редактор не знает — не
 * трогается. План записи считает ContactEditPlan; здесь — перевод его
 * в операции провайдера одной пачкой (applyBatch): либо всё, либо ничего.
 */
@Singleton
class ContactEditRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    private val resolver: ContentResolver get() = context.contentResolver

    private companion object {
        const val TAG = "ContactEdit"
        const val GOOGLE = "com.google"

        /** Мессенджеры — по типу аккаунта, для подписи «что здесь не меняется». */
        val SOURCE_NAMES = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.telegram.messenger" to "Telegram",
            "com.viber.voip" to "Viber",
            "ru.oneme.app" to "MAX"
        )

        /** Сторона фото для запасного пути (строка Photo с миниатюрой). */
        const val THUMB_FALLBACK_PX = 256
    }

    // ── Какие записи можно менять ────────────────────────────────────────────

    /**
     * Аккаунты, которые синхронизируют контакты и ПРИНИМАЮТ правки
     * (supportsUploading), и аккаунты без синхронизации («Телефон») — да.
     * Синхронизация без выгрузки (WhatsApp, Telegram) — нет: мессенджер
     * перезапишет запись при следующей синхронизации. SIM — нет: на карте
     * помещаются имя и один номер, остальное потерялось бы молча.
     */
    private class AccountRules(val readOnly: Set<String>, val synced: Set<String>) {
        fun isSim(type: String?) = type?.contains("sim", ignoreCase = true) == true
        fun isWritable(type: String?) = !isSim(type) && (type == null || type !in readOnly)
        fun kind(type: String?): ContactAccount.Kind = when {
            type == GOOGLE -> ContactAccount.Kind.GOOGLE
            type == null || type !in synced -> ContactAccount.Kind.DEVICE
            else -> ContactAccount.Kind.OTHER
        }
        /** Куда класть новые строки: Google, другой синхронизируемый, «Телефон». */
        fun rank(type: String?) = when (kind(type)) {
            ContactAccount.Kind.GOOGLE -> 0
            ContactAccount.Kind.OTHER -> 1
            ContactAccount.Kind.DEVICE -> 2
        }
    }

    private fun rules(): AccountRules = runCatching {
        val adapters = ContentResolver.getSyncAdapterTypes()
            .filter { it.authority == ContactsContract.AUTHORITY }
        AccountRules(
            readOnly = adapters.filter { !it.supportsUploading() }.mapTo(HashSet()) { it.accountType },
            synced = adapters.filter { it.supportsUploading() }.mapTo(HashSet()) { it.accountType }
        )
    }.getOrElse {
        Logger.w(TAG, "Не удалось прочитать адаптеры синхронизации", it)
        AccountRules(emptySet(), setOf(GOOGLE))
    }

    // ── Чтение ───────────────────────────────────────────────────────────────

    private class Raw(val id: Long, val type: String?, val name: String?)

    /** Контакт для правки; null — не найден или нет доступа. */
    suspend fun load(contactId: Long): LoadedContact? = withContext(io) {
        if (!PermissionGuard.canReadContacts(context)) return@withContext null
        runCatching { loadInternal(contactId) }.getOrElse {
            Logger.w(TAG, "Не удалось прочитать контакт для правки", it)
            null
        }
    }

    private fun loadInternal(contactId: Long): LoadedContact? {
        val rules = rules()
        val raws = mutableListOf<Raw>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            null
        )?.use { c -> while (c.moveToNext()) raws += Raw(c.getLong(0), c.getString(1), c.getString(2)) }
        if (raws.isEmpty()) return null

        val writable = raws.filter { rules.isWritable(it.type) }
        val writableIds = writable.mapTo(HashSet()) { it.id }
        val primary = writable.minByOrNull { rules.rank(it.type) }

        // Имя — из основной записи; нет там — из первой, где оно есть
        class NameCandidate(val ref: RowRef, val given: String, val middle: String, val family: String) {
            val blank get() = given.isBlank() && middle.isBlank() && family.isBlank()
        }
        val names = mutableListOf<NameCandidate>()
        val phones = LinkedHashMap<String, EditableValue>()
        val emails = LinkedHashMap<String, EditableValue>()
        var company = ""
        var title = ""
        var companyRow: RowRef? = null
        var birthday = ""
        var birthdayRow: RowRef? = null
        var note = ""
        var noteRow: RowRef? = null
        val photoRows = mutableListOf<RowRef>()
        var key = 1L

        val placeholders = writable.joinToString(",") { "?" }
        if (writable.isNotEmpty()) {
            resolver.query(
                Data.CONTENT_URI,
                arrayOf(
                    Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE,
                    Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5
                ),
                "${Data.RAW_CONTACT_ID} IN ($placeholders)",
                writable.map { it.id.toString() }.toTypedArray(),
                "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val ref = RowRef(c.getLong(0), c.getLong(1))
                    if (ref.rawId !in writableIds) continue
                    fun col(i: Int) = c.getString(i).orEmpty()
                    when (c.getString(2)) {
                        // StructuredName: GIVEN_NAME = data2, FAMILY_NAME = data3, MIDDLE_NAME = data5
                        StructuredName.CONTENT_ITEM_TYPE ->
                            names += NameCandidate(ref, given = col(4), middle = col(7), family = col(5))
                        // Phone: NUMBER = data1, TYPE = data2, LABEL = data3
                        Phone.CONTENT_ITEM_TYPE -> {
                            val number = col(3)
                            if (number.isBlank()) continue
                            val twin = PhoneFormatter.toE164(number).ifBlank { number.filter { it.isDigit() || it == '+' } }
                            val existing = phones[twin]
                            phones[twin] = existing?.copy(rows = existing.rows + ref)
                                ?: EditableValue(key++, number, c.getInt(4), c.getString(5), listOf(ref))
                        }
                        // Email: ADDRESS = data1, TYPE = data2, LABEL = data3
                        Email.CONTENT_ITEM_TYPE -> {
                            val address = col(3)
                            if (address.isBlank()) continue
                            val twin = address.trim().lowercase()
                            val existing = emails[twin]
                            emails[twin] = existing?.copy(rows = existing.rows + ref)
                                ?: EditableValue(key++, address, c.getInt(4), c.getString(5), listOf(ref))
                        }
                        // Organization: COMPANY = data1, TITLE = data4
                        Organization.CONTENT_ITEM_TYPE -> if (companyRow == null) {
                            companyRow = ref; company = col(3); title = col(6)
                        }
                        // Event: START_DATE = data1, TYPE = data2
                        Event.CONTENT_ITEM_TYPE -> if (birthdayRow == null && c.getInt(4) == Event.TYPE_BIRTHDAY) {
                            birthdayRow = ref; birthday = col(3)
                        }
                        // Note: NOTE = data1
                        Note.CONTENT_ITEM_TYPE -> if (noteRow == null) {
                            noteRow = ref; note = col(3)
                        }
                        Photo.CONTENT_ITEM_TYPE -> photoRows += ref
                    }
                }
            }
        }

        val name = names.firstOrNull { it.ref.rawId == primary?.id && !it.blank }
            ?: names.firstOrNull { !it.blank }

        val photoUri = runCatching {
            resolver.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                arrayOf(ContactsContract.Contacts.PHOTO_URI), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()

        val readOnlySources = raws.filter { !rules.isWritable(it.type) }
            .map { raw ->
                when {
                    rules.isSim(raw.type) -> "SIM"
                    else -> SOURCE_NAMES[raw.type] ?: authenticatorLabel(raw.type) ?: raw.type.orEmpty()
                }
            }
            .filter { it.isNotBlank() }
            .distinct()

        return LoadedContact(
            contactId = contactId,
            draft = ContactDraft(
                given = name?.given.orEmpty(), middle = name?.middle.orEmpty(), family = name?.family.orEmpty(),
                phones = phones.values.toList(), emails = emails.values.toList(),
                company = company, title = title, birthday = birthday, note = note
            ),
            nameRows = names.map { it.ref },
            companyRow = companyRow,
            birthdayRow = birthdayRow,
            noteRow = noteRow,
            photoRows = photoRows,
            primaryRawId = primary?.id,
            readOnlySources = readOnlySources,
            photoUri = photoUri
        )
    }

    /**
     * Куда можно сохранить новый контакт.
     *
     * Список аккаунтов без разрешения GET_ACCOUNTS система не отдаёт,
     * поэтому берём их из самой книги: у каких аккаунтов уже есть записи
     * и настройки (таблица Settings). «Телефон» есть всегда. Google —
     * первыми, затем прочие, «Телефон» — последним.
     */
    suspend fun accounts(): List<ContactAccount> = withContext(io) {
        val rules = rules()
        val pairs = LinkedHashSet<Pair<String?, String?>>()
        runCatching {
            resolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
                "${RawContacts.DELETED} = 0", null, null
            )?.use { c -> while (c.moveToNext()) pairs += c.getString(0) to c.getString(1) }
        }.onFailure { Logger.w(TAG, "Не удалось прочитать аккаунты записей", it) }
        runCatching {
            resolver.query(
                ContactsContract.Settings.CONTENT_URI,
                arrayOf(ContactsContract.Settings.ACCOUNT_TYPE, ContactsContract.Settings.ACCOUNT_NAME),
                null, null, null
            )?.use { c -> while (c.moveToNext()) pairs += c.getString(0) to c.getString(1) }
        }.onFailure { Logger.w(TAG, "Не удалось прочитать настройки аккаунтов", it) }

        val device = context.getString(R.string.contact_account_device)
        val found = pairs.filter { (type, _) -> rules.isWritable(type) }
            .map { (type, name) ->
                val kind = rules.kind(type)
                ContactAccount(
                    type = type,
                    name = name,
                    label = when (kind) {
                        ContactAccount.Kind.GOOGLE -> name ?: "Google"
                        ContactAccount.Kind.DEVICE -> device
                        ContactAccount.Kind.OTHER -> listOfNotNull(authenticatorLabel(type), name)
                            .distinct().joinToString(" · ").ifBlank { type.orEmpty() }
                    },
                    kind = kind
                )
            }
        // «Телефон» — один: своя запись прошивки (Samsung, Xiaomi) важнее null
        val devices = found.filter { it.kind == ContactAccount.Kind.DEVICE }
        val deviceAccount = devices.firstOrNull { it.type != null } ?: devices.firstOrNull()
            ?: ContactAccount(null, null, device, ContactAccount.Kind.DEVICE)
        found.filter { it.kind == ContactAccount.Kind.GOOGLE }.sortedBy { it.label } +
            found.filter { it.kind == ContactAccount.Kind.OTHER }.sortedBy { it.label } +
            deviceAccount
    }

    /** Название сервиса аккаунта: «Samsung account», «Exchange»… — без разрешений. */
    private fun authenticatorLabel(type: String?): String? = type?.let { wanted ->
        runCatching {
            val auth = AccountManager.get(context).authenticatorTypes.firstOrNull { it.type == wanted }
                ?: return@runCatching null
            context.packageManager.getResourcesForApplication(auth.packageName).getString(auth.labelId)
        }.getOrNull()
    }

    // ── Запись ───────────────────────────────────────────────────────────────

    /**
     * Новый контакт в [account]. Возвращает id контакта или null при ошибке.
     * Фото пишется после — ему нужен id новой записи.
     */
    suspend fun saveNew(account: ContactAccount, draft: ContactDraft, photo: PhotoChange): Long? =
        withContext(io) {
            if (!PermissionGuard.canWriteContacts(context)) return@withContext null
            runCatching {
                val ops = arrayListOf(
                    ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                        .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                        .withValue(RawContacts.ACCOUNT_NAME, account.name)
                        .build()
                )
                ContactEditPlan.forNew(draft).forEach { op ->
                    if (op is EditOp.Insert) {
                        ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                            .withValues(values(op.row, insert = true))
                            .build()
                    }
                }
                val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                val rawId = results.firstOrNull()?.uri?.let { ContentUris.parseId(it) }
                    ?: return@runCatching null
                if (photo is PhotoChange.Set) writePhoto(rawId, photo.jpeg)
                Logger.i(TAG, "Контакт создан: ${ops.size - 1} строк, аккаунт ${account.kind}")
                contactIdOf(rawId)
            }.getOrElse {
                Logger.e(TAG, "Не удалось создать контакт", it)
                null
            }
        }

    /** Правка: только изменённое, одной пачкой. true — сохранено. */
    suspend fun saveEdit(loaded: LoadedContact, draft: ContactDraft, photo: PhotoChange): Boolean =
        withContext(io) {
            if (!PermissionGuard.canWriteContacts(context)) return@withContext false
            val primary = loaded.primaryRawId ?: return@withContext false
            runCatching {
                val plan = ContactEditPlan.forEdit(loaded, draft)
                val ops = ArrayList<ContentProviderOperation>()
                plan.forEach { op ->
                    ops += when (op) {
                        is EditOp.Insert -> ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValue(Data.RAW_CONTACT_ID, op.rawId ?: primary)
                            .withValues(values(op.row, insert = true))
                            .build()
                        is EditOp.Update -> ContentProviderOperation
                            .newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, op.dataId))
                            .withValues(values(op.row, insert = false))
                            .build()
                        is EditOp.Delete -> ContentProviderOperation
                            .newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, op.dataId))
                            .build()
                    }
                }
                when (photo) {
                    PhotoChange.Keep -> Unit
                    PhotoChange.Remove -> loaded.photoRows.forEach { row ->
                        ops += ContentProviderOperation
                            .newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, row.dataId))
                            .build()
                    }
                    is PhotoChange.Set -> Unit
                }
                if (ops.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                if (photo is PhotoChange.Set) {
                    // Фото — в запись, где оно уже есть, иначе в основную
                    writePhoto(loaded.photoRows.firstOrNull()?.rawId ?: primary, photo.jpeg)
                }
                Logger.i(TAG, "Контакт изменён: ${plan.size} строк, фото: ${photo::class.simpleName}")
                true
            }.getOrElse {
                Logger.e(TAG, "Не удалось сохранить контакт", it)
                false
            }
        }

    private fun contactIdOf(rawId: Long): Long? =
        resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
            arrayOf(RawContacts.CONTACT_ID), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    /** Значения строки — общий перевод с системным редактором (rowValues ниже, 2.1.0). */
    private fun values(row: RowValue, insert: Boolean): ContentValues = rowValues(row, insert)

    /**
     * ФОТО — ПОЛНОРАЗМЕРНОЕ, ЧЕРЕЗ DisplayPhoto.
     *
     * Так пишет фото и системное приложение: провайдер сам делает
     * миниатюру для списков и хранит большое фото для карточки и экрана
     * вызова. Если прошивка этот путь не поддерживает — запасной: строка
     * Photo с уменьшенным снимком (как было до Android 4).
     */
    private fun writePhoto(rawId: Long, jpeg: ByteArray) {
        val written = runCatching {
            val uri = Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
                RawContacts.DisplayPhoto.CONTENT_DIRECTORY
            )
            resolver.openAssetFileDescriptor(uri, "rw")?.use { fd ->
                fd.createOutputStream().use { it.write(jpeg) }
            } != null
        }.getOrElse {
            Logger.w(TAG, "DisplayPhoto не принял фото, пробую строкой Photo", it)
            false
        }
        if (written) return

        runCatching {
            val thumb = ContactPhotos.downscale(jpeg, THUMB_FALLBACK_PX) ?: return@runCatching
            val existing = resolver.query(
                Data.CONTENT_URI, arrayOf(Data._ID),
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(rawId.toString(), Photo.CONTENT_ITEM_TYPE), null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            val values = ContentValues().apply { put(Photo.PHOTO, thumb) }
            if (existing != null) {
                resolver.update(ContentUris.withAppendedId(Data.CONTENT_URI, existing), values, null, null)
            } else {
                values.put(Data.RAW_CONTACT_ID, rawId)
                values.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                resolver.insert(Data.CONTENT_URI, values)
            }
        }.onFailure { Logger.e(TAG, "Не удалось записать фото контакта", it) }
    }
}

/**
 * Значения строки. При правке имени пишутся только части — провайдер
 * сам пересоберёт DISPLAY_NAME (если передать и его, он взял бы
 * старое). Неизвестные редактору поля строки (префикс, фонетика,
 * отдел) не перечисляются и потому не меняются.
 *
 * Вынесено из класса в 2.1.0: те же строки (insert = true) уходят
 * в системные «Контакты», когда свой редактор не смог сохранить
 * (ContactEditor.openDraft, Insert.DATA).
 */
internal fun rowValues(row: RowValue, insert: Boolean): ContentValues = ContentValues().apply {
    when (row) {
        is RowValue.Name -> {
            if (insert) put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            put(StructuredName.GIVEN_NAME, row.given.ifEmpty { null })
            put(StructuredName.MIDDLE_NAME, row.middle.ifEmpty { null })
            put(StructuredName.FAMILY_NAME, row.family.ifEmpty { null })
        }
        is RowValue.Phone -> {
            if (insert) put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
            put(Phone.NUMBER, row.number)
            put(Phone.TYPE, row.type)
            put(Phone.LABEL, row.label)
        }
        is RowValue.Email -> {
            if (insert) put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
            put(Email.ADDRESS, row.address)
            put(Email.TYPE, row.type)
            put(Email.LABEL, row.label)
        }
        is RowValue.Organization -> {
            if (insert) {
                put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                put(Organization.TYPE, Organization.TYPE_WORK)
            }
            put(Organization.COMPANY, row.company.ifEmpty { null })
            put(Organization.TITLE, row.title.ifEmpty { null })
        }
        is RowValue.Birthday -> {
            if (insert) {
                put(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
                put(Event.TYPE, Event.TYPE_BIRTHDAY)
            }
            put(Event.START_DATE, row.date)
        }
        is RowValue.Note -> {
            if (insert) put(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
            put(Note.NOTE, row.text)
        }
    }
}
