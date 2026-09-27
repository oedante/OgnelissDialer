package com.ogneliss.dialer.data

import android.content.Context
import android.provider.CallLog
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ogneliss.dialer.di.DefaultDispatcher
import com.ogneliss.dialer.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ЧТЕНИЕ ЖУРНАЛА ВЫЗОВОВ.
 *
 * Назначение: достаёт записи из системного провайдера CallLog и приводит
 * их к модели приложения. Сама группировка здесь НЕ выполняется —
 * этим занимается CallLogGrouper, который можно тестировать отдельно.
 *
 * ── О лимите ───────────────────────────────────────────────────────────────
 * Читаем ограниченное число последних записей. У активного пользователя
 * журнал за годы может содержать десятки тысяч строк; вычитывать всё
 * при каждом обновлении означало бы заметную задержку при открытии.
 * Постраничная подгрузка запланирована (Paging 3).
 */
@Singleton
class CallLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val simRepository: SimRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : CallLogSource {

    private companion object {
        const val TAG = "CallLog"
        const val DEFAULT_LIMIT = 500
    }

    /** Сырые записи журнала, отсортированные от новых к старым. */
    /**
     * Чтение страницы журнала.
     *
     * @param offset сколько записей пропустить (для подгрузки при прокрутке)
     * @param limit сколько прочитать
     *
     * LIMIT/OFFSET передаются в строке сортировки — это единственный
     * способ ограничить выборку у ContentProvider, отдельного параметра
     * для этого в API нет.
     */
    override suspend fun loadEntries(
        limit: Int,
        offset: Int
    ): List<CallLogEntry> = withContext(io) {
        if (limit <= 0) return@withContext emptyList()

        // Без разрешения система отвечает SecurityException. Проверяем сами:
        // иначе на каждое обновление списка в лог падает стек, а пользователь
        // видит «Звонков пока нет» и считает это правдой
        if (!PermissionGuard.canReadCallLog(context)) {
            PermissionGuard.logDenied("чтение журнала вызовов", "READ_CALL_LOG")
            return@withContext emptyList()
        }

        val out = ArrayList<CallLogEntry>(limit)

        /*
         * ЧТЕНИЕ С ЗАПАСНЫМ ПУТЁМ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ЗАПАСНОЙ ПУТЬ ОБЯЗАТЕЛЕН
         * ══════════════════════════════════════════════════════════════════
         * LIMIT в строке сортировки — общепринятый приём: отдельного
         * параметра у ContentProvider нет. Но часть прошивок проверяет
         * эту строку и отвергает лишнее:
         *
         *   E [CallLog] Не удалось прочитать журнал (offset=0)
         *   java.lang.IllegalArgumentException: Invalid token LIMIT
         *
         * На таком телефоне журнал не читается ВООБЩЕ. Приложение
         * показывает «Звонков пока нет» и выглядит полностью
         * неработающим — при живом журнале.
         *
         * ── Как устроен запасной путь ──────────────────────────────────────
         * Читаем без LIMIT и обрезаем сами. Это дороже: провайдер отдаёт
         * весь журнал, а не страницу. Но курсор ленивый — строки
         * вычитываются по мере обхода, и мы прекращаем на нужном месте.
         *
         * ── Почему не всегда так ───────────────────────────────────────────
         * Где LIMIT работает, он экономит заметно: провайдер обрывает
         * выборку на своей стороне. Терять это ради прошивок,
         * где приём не поддерживается, незачем.
         */
        val readWith: (String?) -> Unit = { sortOrder ->
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection(), null, null, sortOrder
            )?.use { c ->
                // Пропускаем offset записей: без LIMIT в запросе смещение
                // тоже приходится отрабатывать самим
                if (sortOrder?.contains("LIMIT") != true && offset > 0) {
                    var skipped = 0
                    while (skipped < offset && c.moveToNext()) skipped++
                }

                while (out.size < limit && c.moveToNext()) out += readEntry(c)
            }
        }

        val limited = "${CallLog.Calls.DATE} DESC LIMIT $limit OFFSET $offset"

        runCatching { readWith(limited) }
            .onFailure { error ->
                out.clear()

                Logger.w(
                    TAG,
                    "Прошивка не приняла LIMIT, читаю без него (offset=$offset)",
                    error
                )

                runCatching { readWith("${CallLog.Calls.DATE} DESC") }
                    .onFailure {
                        Logger.e(TAG, "Не удалось прочитать журнал (offset=$offset)", it)
                    }
            }
        Logger.d(TAG, "Прочитано записей: ${out.size} (offset=$offset, limit=$limit)")
        out
    }

    private fun projection() = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.CACHED_PHOTO_URI,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.PHONE_ACCOUNT_ID,
        CallLog.Calls.CACHED_LOOKUP_URI
    )

    /** Чтение одной строки курсора. Порядок колонок задан projection(). */
    private fun readEntry(c: android.database.Cursor): CallLogEntry {
        val number = c.getString(1).orEmpty()
        return CallLogEntry(
            id = c.getLong(0),
            number = number,
            e164 = PhoneFormatter.toE164(number),
            cachedName = c.getString(2),
            photoUri = c.getString(3),
            type = mapType(c.getInt(4)),
            date = c.getLong(5),
            durationSec = c.getLong(6),
            phoneAccountId = c.getString(7),
            contactId = parseContactId(c.getString(8))
        )
    }

    /** Разделять ли карточки по SIM: имеет смысл только при двух картах. */
    suspend fun shouldSeparateBySim(): Boolean = simRepository.slotsAsync().size > 1

    /** Подпись SIM для записи журнала. */
    fun simLabel(accountId: String?): String? = simRepository.labelFor(accountId)

    /**
     * История вызовов конкретного абонента.
     *
     * Запрос с условием, а не выборка всего журнала с последующей
     * фильтрацией: провайдер отфильтрует по индексу, не передавая
     * тысячи ненужных строк через binder.
     *
     * Номер ищется по NUMBER с нормализацией на стороне провайдера
     * (PHONE_NUMBERS_EQUAL), поэтому "+79001234567" найдёт и "89001234567".
     */
    /**
     * ИТОГОВЫЕ СЧЁТЧИКИ ПО ВСЕМУ ЖУРНАЛУ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЗАЧЕМ ОТДЕЛЬНЫЙ ЗАПРОС
     * ══════════════════════════════════════════════════════════════════════
     * Журнал читается страницами по 150 записей — иначе на десяти
     * тысячах вызовов открытие занимало бы секунды.
     *
     * Но счётчики в карточках считались по ЗАГРУЖЕННОМУ: пришла первая
     * страница — «5 вызовов», подгрузилась вторая — «12», третья —
     * «47». Человек видит, как числа растут у него на глазах, и это
     * выглядит как ошибка, хотя данные верны.
     *
     * ── Почему это дёшево ──────────────────────────────────────────────────
     * Здесь читаются только два столбца — номер и тип, — без имени,
     * фотографии, длительности и подписи SIM. Провайдер отдаёт такую
     * выборку в разы быстрее полной: на десяти тысячах записей это
     * десятки миллисекунд против секунд.
     *
     * ── Почему не считать при первой странице ──────────────────────────────
     * Потому что первая страница — это и есть источник неверных чисел.
     * Итоги должны быть итогами с самого начала, иначе смысл теряется.
     */
    override suspend fun totalsByNumber(): Map<String, CallTotals> = withContext(io) {
        if (!PermissionGuard.canReadCallLog(context)) return@withContext emptyMap()

        val out = HashMap<String, CallTotals.Builder>()

        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION),
                null, null, null
            )?.use { c ->
                val numberIndex = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeIndex = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durationIndex = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                while (c.moveToNext()) {
                    val raw = c.getString(numberIndex) ?: continue
                    val e164 = PhoneFormatter.toE164(raw)

                    out.getOrPut(e164) { CallTotals.Builder() }
                        .add(c.getInt(typeIndex), c.getLong(durationIndex))
                }
            }
        }.onFailure { Logger.w(TAG, "Не удалось посчитать итоги журнала", it) }

        out.mapValues { (_, builder) -> builder.build() }
    }

    override suspend fun historyFor(number: String, limit: Int): List<CallLogEntry> =
        withContext(io) {
            if (!PermissionGuard.canReadCallLog(context)) return@withContext emptyList()
            val out = mutableListOf<CallLogEntry>()

            // Запасной путь тот же, что при чтении журнала: часть прошивок
            // отвергает LIMIT в строке сортировки, и без отката история
            // абонента не открывалась бы вовсе
            val readWith: (String) -> Unit = { sortOrder ->
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection(),
                    "PHONE_NUMBERS_EQUAL(${CallLog.Calls.NUMBER}, ?)",
                    arrayOf(number),
                    sortOrder
                )?.use { c ->
                    while (out.size < limit && c.moveToNext()) out += readEntry(c)
                }
            }

            runCatching { readWith("${CallLog.Calls.DATE} DESC LIMIT $limit") }
                .onFailure { error ->
                    out.clear()
                    Logger.w(TAG, "Прошивка не приняла LIMIT в истории по номеру", error)

                    runCatching { readWith("${CallLog.Calls.DATE} DESC") }
                        .onFailure {
                            Logger.w(TAG, "Не удалось прочитать историю по номеру", it)
                        }
                }
            out
        }

    /**
     * Удаление всех вызовов группы.
     * Удаляем по конкретным id, а не по номеру: у контакта может быть
     * несколько номеров, и удаление по номеру снесло бы не то.
     */
    override suspend fun deleteGroup(group: CallLogGroup): Boolean = withContext(io) {
        if (!PermissionGuard.canWriteCallLog(context)) return@withContext false
        val ids = group.entries.map { it.id }.ifEmpty { listOf(group.lastCallId) }
        runCatching {
            val placeholders = ids.joinToString(",") { "?" }
            val deleted = context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN ($placeholders)",
                ids.map { it.toString() }.toTypedArray()
            )
            Logger.i(TAG, "Удалено записей журнала: $deleted")
            deleted > 0
        }.getOrElse {
            Logger.e(TAG, "Не удалось удалить записи журнала", it)
            false
        }
    }

    override suspend fun clearAll(): Boolean = withContext(io) {
        if (!PermissionGuard.canWriteCallLog(context)) return@withContext false
        runCatching {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            Logger.i(TAG, "Журнал вызовов очищен пользователем")
            true
        }.getOrElse {
            Logger.e(TAG, "Не удалось очистить журнал", it)
            false
        }
    }

    private fun mapType(t: Int): CallType = when (t) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
        CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
        else -> CallType.INCOMING
    }

    /**
     * Id контакта извлекается из lookup-URI вида
     * content://com.android.contacts/contacts/lookup/<key>/<id>.
     * Именно он позволяет объединить в одну карточку звонки
     * с разных номеров одного человека.
     */
    private fun parseContactId(lookupUri: String?): Long? {
        if (lookupUri.isNullOrBlank()) return null
        return lookupUri.substringAfterLast('/').toLongOrNull()
    }
}
