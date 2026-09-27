package com.ogneliss.dialer.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DirectoryProvider(
    val id: Long,
    val packageName: String,
    val displayName: String
)

data class DirectoryResult(val displayName: String, val sourcePackage: String)

/**
 * НАСТОЯЩИЙ механизм получения имени абонента от стороннего определителя.
 *
 * Сторонние сервисы (Truecaller, корпоративные справочники, CardDAV-клиенты)
 * публикуют себя как удалённые директории через ContactsContract.Directory.
 * Диалер выполняет обычный запрос к ContactsContract с параметром DIRECTORY_PARAM_KEY
 * и получает имя. Никакого приватного API и никакой «пересылки вызова» —
 * это штатный путь, которым пользуется и штатное приложение Google Phone.
 *
 * ⚠️ ВАЖНО ДЛЯ НАШЕЙ МОДЕЛИ ПРИВАТНОСТИ:
 * запрос к удалённой директории передаёт номер стороннему приложению,
 * которое МОЖЕТ отправить его на свой сервер. Наше приложение при этом
 * само в сеть не ходит (разрешения INTERNET нет), но номер всё равно
 * покидает наш периметр.
 *
 * Поэтому: по умолчанию ВЫКЛЮЧЕНО. Включается пользователем поимённо
 * для конкретного провайдера, с явным предупреждением на экране.
 */
@Singleton
class DirectoryLookupRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Какие удалённые директории установлены на устройстве. */
    suspend fun availableProviders(): List<DirectoryProvider> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DirectoryProvider>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Directory.CONTENT_URI,
                arrayOf(
                    ContactsContract.Directory._ID,
                    ContactsContract.Directory.PACKAGE_NAME,
                    ContactsContract.Directory.DISPLAY_NAME,
                    ContactsContract.Directory.DIRECTORY_AUTHORITY
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    // Локальные директории (0, 1) — это сама книга устройства
                    if (id == ContactsContract.Directory.DEFAULT ||
                        id == ContactsContract.Directory.LOCAL_INVISIBLE
                    ) continue
                    val pkg = c.getString(1) ?: continue
                    if (pkg == context.packageName) continue
                    out += DirectoryProvider(id, pkg, c.getString(2) ?: pkg)
                }
            }
        }
        out
    }

    /**
     * Поиск имени по номеру в конкретной директории.
     * Вызывать ТОЛЬКО если пользователь явно включил этого провайдера.
     */
    suspend fun lookup(number: String, directoryId: Long): DirectoryResult? =
        withContext(Dispatchers.IO) {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            ).buildUpon()
                .appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, directoryId.toString()
                )
                .build()

            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0)
                        if (!name.isNullOrBlank()) {
                            return@runCatching DirectoryResult(name, directoryId.toString())
                        }
                    }
                }
                null
            }.getOrNull()
        }
}
