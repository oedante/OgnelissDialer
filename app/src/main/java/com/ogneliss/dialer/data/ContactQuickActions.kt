package com.ogneliss.dialer.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * БЫСТРЫЕ ОПЕРАЦИИ НАД КОНТАКТАМИ ВНУТРИ ПРИЛОЖЕНИЯ.
 *
 * Здесь только то, что безопасно делать самим. Критерий отбора один:
 * операция НЕ должна затрагивать агрегацию контактов, набор полей
 * и синхронизацию с аккаунтами.
 *
 * ── Что попало сюда и почему ───────────────────────────────────────────────
 *
 * ИЗБРАННОЕ (STARRED) — колонка агрегированного контакта, а не raw.
 *   Не участвует в синхронизации полей, менять безопасно.
 *   Нужна прямо в приложении: отправлять пользователя в системные контакты
 *   ради одной звёздочки — издевательство.
 *
 * МЕЛОДИЯ ЗВОНКА (CUSTOM_RINGTONE) — тоже колонка агрегата. Для диалера
 *   это профильная функция, странно её не иметь.
 *
 * ПЕРЕАДРЕСАЦИЯ НА ГОЛОСОВУЮ ПОЧТУ (SEND_TO_VOICEMAIL) — аналогично.
 *
 * УДАЛЕНИЕ — технически просто, но разрушительно и необратимо:
 *   удаляется агрегат целиком, вместе с raw-записями из всех аккаунтов.
 *   Оставлено здесь, но обязательно с явным предупреждением в UI.
 *
 * ── Чего здесь намеренно НЕТ ───────────────────────────────────────────────
 * Создание и редактирование полей, работа с фото, слияние дублей, группы.
 * Всё это уходит в системное приложение через ContactEditor —
 * см. подробное обоснование там.
 */
@Singleton
class ContactQuickActions @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : ContactWriter {

    private companion object { const val TAG = "ContactActions" }

    /** Добавить или убрать из избранного. */
    override suspend fun setStarred(contactId: Long, starred: Boolean): Boolean = withContext(io) {
        updateContactColumn(contactId, ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
    }

    /**
     * Персональная мелодия звонка.
     * @param ringtoneUri null сбрасывает на мелодию по умолчанию
     */
    override suspend fun setCustomRingtone(contactId: Long, uri: Uri?): Boolean =
        withContext(io) {
            updateContactColumn(
                contactId,
                ContactsContract.Contacts.CUSTOM_RINGTONE,
                uri?.toString()
            )
        }

    /** Сразу отправлять вызовы этого контакта на голосовую почту. */
    override suspend fun setSendToVoicemail(contactId: Long, enabled: Boolean): Boolean =
        withContext(io) {
            updateContactColumn(
                contactId,
                ContactsContract.Contacts.SEND_TO_VOICEMAIL,
                if (enabled) 1 else 0
            )
        }

    /**
     * УДАЛЕНИЕ КОНТАКТА.
     *
     * Удаляется агрегат целиком: вместе с ним уходят raw-записи из всех
     * аккаунтов, включая Google, где контакт мог храниться годами.
     * Отменить нельзя — восстановление возможно только из веб-интерфейса
     * аккаунта, и то не всегда.
     *
     * Вызывать ТОЛЬКО после явного подтверждения с предупреждением.
     */
    override suspend fun delete(contactId: Long): Boolean = withContext(io) {
        runCatching {
            val uri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI, contactId
            )
            val deleted = context.contentResolver.delete(uri, null, null) > 0
            Logger.i(TAG, "Контакт удалён: $deleted")
            deleted
        }.getOrElse {
            Logger.e(TAG, "Не удалось удалить контакт", it)
            false
        }
    }

    private fun updateContactColumn(contactId: Long, column: String, value: Any?): Boolean =
        runCatching {
            val values = ContentValues().apply {
                when (value) {
                    is Int -> put(column, value)
                    is String -> put(column, value)
                    null -> putNull(column)
                    else -> put(column, value.toString())
                }
            }
            val updated = context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                values,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString())
            ) > 0
            Logger.d(TAG, "Обновлено поле $column: $updated")
            updated
        }.getOrElse {
            Logger.e(TAG, "Не удалось обновить поле $column", it)
            false
        }
}
