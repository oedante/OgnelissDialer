package com.ogneliss.dialer.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.ogneliss.dialer.log.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ПЕРЕДАЧА РЕДАКТИРОВАНИЯ КОНТАКТОВ СИСТЕМНОМУ ПРИЛОЖЕНИЮ.
 *
 * ── Почему полное редактирование НЕ реализовано внутри приложения ──────────
 *
 * Дело не в разрешениях: WRITE_CONTACTS у нас есть и отдельной декларации
 * для Google Play не требует. Дело в модели данных ContactsContract.
 *
 * 1. АГРЕГАЦИЯ. Один «контакт» — это склейка нескольких RawContacts:
 *    Google, локальный, SIM, плюс свои записи добавляют WhatsApp и Telegram.
 *    Редактируя контакт, нужно выбрать, в какой именно raw писать.
 *    Ошибка порождает дубли, которые пользователь потом разбирает руками.
 *
 * 2. АККАУНТЫ ОГРАНИЧИВАЮТ ПОЛЯ. Каждый тип аккаунта объявляет, какие поля
 *    он поддерживает. Запись неподдерживаемого поля теряется при первой же
 *    синхронизации — молча, без ошибки.
 *
 * 3. ЕСТЬ RAW ТОЛЬКО ДЛЯ ЧТЕНИЯ. Записи, созданные мессенджерами,
 *    редактировать нельзя.
 *
 * 4. ПОЛЯ СЛОЖНЕЕ, ЧЕМ КАЖУТСЯ. Структурированное имя из шести частей,
 *    произвольные mimetype, фото в двух вариантах (миниатюра и полноразмерное
 *    через PHOTO_FILE_ID), пользовательские метки типов, группы.
 *
 * Цена ошибки здесь особенная: мы портим данные, которые живут ВНЕ нашего
 * приложения и нужны десятку других программ. Системное приложение
 * «Контакты» уже решает все эти задачи и поддерживается производителем.
 *
 * ── Что делаем сами (см. ContactQuickActions) ──────────────────────────────
 * Быстрые операции, не затрагивающие агрегацию: избранное, мелодия звонка,
 * переадресация на голосовую почту. Плюс наши собственные заметки и метки,
 * которые вообще не хранятся в системных контактах.
 */
@Singleton
class ContactEditor @Inject constructor(
    @ApplicationContext private val context: Context
) : ContactEditorLauncher {

    private companion object { const val TAG = "ContactEditor" }

    /**
     * Создание нового контакта с заполненным номером.
     *
     * ACTION_INSERT_OR_EDIT позволяет системному приложению самому решить:
     * если номер уже есть у кого-то, оно предложит дополнить существующий
     * контакт, а не плодить дубль. Это ровно та логика, которую пришлось бы
     * писать самим и которую легко сделать неправильно.
     */
    override fun createWithNumber(number: String, name: String?) = launch(
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
            name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
        },
        "создание контакта"
    )

    /** Добавление номера к существующему контакту — выбор делает пользователь. */
    override fun addNumberToExisting(number: String) = launch(
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        },
        "добавление номера к контакту"
    )

    /** Полное редактирование контакта в системном приложении. */
    override fun edit(contactId: Long) = launch(
        Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                ContactsContract.Contacts.CONTENT_ITEM_TYPE
            )
            // Возврат в наше приложение после сохранения
            putExtra("finishActivityOnSaveCompleted", true)
        },
        "редактирование контакта"
    )

    /** Просмотр карточки контакта в системном приложении. */
    fun view(contactId: Long) = launch(
        Intent(
            Intent.ACTION_VIEW,
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        ),
        "просмотр контакта"
    )

    private fun launch(intent: Intent, what: String) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Logger.w(TAG, "Не удалось открыть системное приложение: $what", it)
        }
    }

    /** Есть ли на устройстве приложение, способное редактировать контакты. */
    fun isEditorAvailable(): Boolean = runCatching {
        val probe = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        }
        context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
    }.getOrDefault(false)
}
