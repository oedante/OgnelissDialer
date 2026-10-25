package com.ogneliss.dialer.data

import com.ogneliss.dialer.util.DefaultDialer
import android.os.Build
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
 * ── С 1.7.0 — запасной путь ────────────────────────────────────────────────
 * Контакт создаётся и меняется своим редактором (ui/contactedit,
 * data/ContactEditRepository): автор, 23.10 — «очень много запросов на
 * редактор контакта, системные не всегда правильно воспринимают переход
 * в них», а на OPPO, OnePlus, realme системные «Контакты» закрыты, пока
 * основной — не системный «Телефон». Сюда приходят только «Открыть
 * в системных «Контактах»» из меню редактора — для адресов, групп
 * и прочего, чего свой редактор не знает.
 *
 * Разбор ниже — почему своего редактора долго не было. Он же определил,
 * как свой устроен: меняет только записи, которые можно менять, и только
 * знакомые ему поля (ContactEditModel.kt).
 *
 * ── Почему полное редактирование долго НЕ делалось внутри приложения ───────
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

        /** «Google Контакты» — отдельный редактор, с «Телефоном» не связан. */
        const val GOOGLE_CONTACTS = "com.google.android.contacts"

        /**
         * Прошивки, где «Контакты» — часть системного «Телефона» и без роли
         * телефона по умолчанию не открываются (ColorOS и её ветви).
         */
        val LOCKING_BRANDS = listOf("oppo", "oneplus", "realme")

        /** Любой id: проверяется, кто берётся за форму, а не сам контакт. */
        const val EDIT_PROBE_ID = 1L
    }

    /*
     * ══════════════════════════════════════════════════════════════════════
     * РЕДАКТОР, ЗАПЕРТЫЙ В СИСТЕМНОМ «ТЕЛЕФОНЕ» (1.7.0)
     * ══════════════════════════════════════════════════════════════════════
     * Письмо пользователя с OPPO Reno 14: Ogneliss Dialer — основной,
     * «Изменить» открывает системные «Контакты», а те вместо формы
     * показывают «Использование системного телефона — Установить
     * в качестве приложения для вызовов по умолчанию». На ColorOS
     * (OPPO, OnePlus, realme) контакты — часть системного «Телефона»,
     * и пока основной другой, они закрыты.
     *
     * С 1.7.0 контакт меняется своим редактором (ui/screens/ContactEditScreen),
     * а сюда приходят только «Открыть в системных «Контактах»» и
     * «Добавить к контакту» из системы. Если системные заперты и есть
     * «Google Контакты» — форма открывается в них: те же контакты книги,
     * к роли телефона не привязаны. Нет — открываются системные, как есть.
     *
     * Признак — не одна марка: редактор контактов на телефоне должен
     * быть ещё и «Телефоном» (умеет ACTION_DIAL), а основной — мы.
     * На OnePlus с «Google Телефоном» и «Google Контактами» ничего
     * не меняется. Видеть чужие приложения позволяют <queries>
     * в манифесте (EDIT контакта, DIAL, пакет «Google Контакты»).
     */
    private fun systemPhoneHoldsEditor(): Boolean = runCatching {
        val brand = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        if (LOCKING_BRANDS.none { it in brand }) return@runCatching false
        if (!DefaultDialer.isDefault(context)) return@runCatching false
        val pm = context.packageManager
        val phones = pm.queryIntentActivities(Intent(Intent.ACTION_DIAL), 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }
        pm.queryIntentActivities(editIntent(EDIT_PROBE_ID), 0)
            .map { it.activityInfo.packageName }
            .any { it != context.packageName && it in phones }
    }.getOrDefault(false)

    /** «Google Контакты», если установлены. */
    private fun standaloneEditor(): String? = GOOGLE_CONTACTS.takeIf { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    /** Куда направить форму: отдельный редактор, если системный заперт. */
    private fun preferredPackage(): String? =
        if (systemPhoneHoldsEditor()) standaloneEditor() else null



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
    override fun edit(contactId: Long) = launch(editIntent(contactId), "редактирование контакта")

    private fun editIntent(contactId: Long) = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            ContactsContract.Contacts.CONTENT_ITEM_TYPE
        )
        // Возврат в наше приложение после сохранения
        putExtra(EXTRA_FINISH_ON_SAVE, true)
    }

    /** Просмотр карточки контакта в системном приложении. */
    fun view(contactId: Long) = launch(
        Intent(
            Intent.ACTION_VIEW,
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        ),
        "просмотр контакта"
    )

    private fun launch(intent: Intent, what: String) = launchFirst(what, intent)

    /**
     * Первое намерение, которое удалось запустить.
     *
     * Пробуется запуском, а не заранее через resolveActivity: с Android 11
     * чужие приложения видны нам только по списку <queries>, и проверка
     * могла бы сказать «некому», когда приложение есть.
     */
    private fun launchFirst(what: String, vararg intents: Intent) {
        // Редактор заперт в «Телефоне» — сначала те же формы в «Google Контактах»
        val preferred = preferredPackage()
        val attempts = if (preferred != null) {
            intents.map { Intent(it).setPackage(preferred) } + intents
        } else {
            intents.toList()
        }
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                Logger.i(TAG, "$what: ${intent.action} ${intent.type ?: intent.data} — некому, пробую дальше")
            } catch (e: SecurityException) {
                Logger.w(TAG, "$what: ${intent.action} — нет доступа", e)
            } catch (e: RuntimeException) {
                Logger.w(TAG, "$what: ${intent.action} — не открылось", e)
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
