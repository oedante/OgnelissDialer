package com.ogneliss.dialer.data

import android.content.ActivityNotFoundException
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

    private companion object {
        const val TAG = "ContactEditor"

        /** Понимают стандартные «Контакты» и Samsung: после сохранения — назад к нам. */
        const val EXTRA_FINISH_ON_SAVE = "finishActivityOnSaveCompleted"
    }

    /**
     * Создание нового контакта с заполненным номером — сразу форма.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НЕ ACTION_INSERT_OR_EDIT (1.5.7)
     * ══════════════════════════════════════════════════════════════════════
     * До 1.5.7 здесь был INSERT_OR_EDIT: «системное приложение само решит,
     * создать контакт или дополнить существующий». На Samsung оно решает
     * одинаково — открывает список контактов с поиском, «куда добавить
     * номер» (снимок автора, 22.10). Человек нажал «Создать контакт»,
     * а попал в поиск, и форма — ещё одним нажатием, если он догадается.
     *
     * Теперь — ACTION_INSERT: форма нового контакта сразу. Дополнить
     * существующий — отдельное действие («К контакту», addNumberToExisting):
     * там выбор контакта и нужен.
     *
     * ── Запасные пути ──────────────────────────────────────────────────────
     * Тип данных у INSERT прошивки понимают по-разному: сначала
     * RawContacts.CONTENT_TYPE (так в документации Android), затем адрес
     * Contacts.CONTENT_URI, и в последнюю очередь — прежний INSERT_OR_EDIT,
     * чтобы на редкой прошивке кнопка не перестала работать совсем.
     *
     * finishActivityOnSaveCompleted — после «Сохранить» возврат к нам,
     * а не в список контактов системного приложения.
     */
    override fun createWithNumber(number: String, name: String?) {
        val fill: Intent.() -> Unit = {
            number.takeIf { it.isNotBlank() }
                ?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            name?.takeIf { it.isNotBlank() }
                ?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
            putExtra(EXTRA_FINISH_ON_SAVE, true)
        }
        launchFirst(
            "создание контакта",
            Intent(ContactsContract.Intents.Insert.ACTION)
                .setType(ContactsContract.RawContacts.CONTENT_TYPE).apply(fill),
            Intent(ContactsContract.Intents.Insert.ACTION, ContactsContract.Contacts.CONTENT_URI)
                .apply(fill),
            Intent(Intent.ACTION_INSERT_OR_EDIT)
                .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE).apply(fill)
        )
    }

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
            putExtra(EXTRA_FINISH_ON_SAVE, true)
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

    /**
     * Первое намерение, которое удалось запустить.
     *
     * Пробуется запуском, а не заранее через resolveActivity: с Android 11
     * чужие приложения видны нам только по списку <queries>, и проверка
     * могла бы сказать «некому», когда приложение есть.
     */
    private fun launchFirst(what: String, vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                Logger.i(TAG, "$what: ${intent.action} ${intent.type ?: intent.data} — некому, пробую дальше")
            } catch (e: SecurityException) {
                Logger.w(TAG, "$what: ${intent.action} — нет доступа", e)
            }
        }
        Logger.w(TAG, "Не удалось открыть системное приложение: $what")
    }

    /** Есть ли на устройстве приложение, способное редактировать контакты. */
    fun isEditorAvailable(): Boolean = runCatching {
        val probe = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        }
        context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
    }.getOrDefault(false)
}
