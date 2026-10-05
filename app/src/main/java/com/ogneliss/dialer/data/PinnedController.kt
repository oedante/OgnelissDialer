package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.util.PhoneFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Закреплённые абоненты (1.3.4, фаза 4 — деление DialerViewModel).
 *
 * Было: всё жило в DialerViewModel. Стало: запись в настройки — здесь,
 * а перевод старых записей (migrate) и сборка списка для экрана (entries) —
 * чистые функции без Android: их проверяют модульные тесты. Устройство
 * записей и причины «контакт, а не номер» — PinnedContacts.
 *
 * ViewModel передаёт текущий список, контакты и журнал параметрами,
 * сама обновляет журнал после записи.
 */
@Singleton
class PinnedController @Inject constructor(
    private val prefs: Prefs
) {

    /** Закрепить номер или его контакт. false — предел или пустой номер. */
    suspend fun pin(current: List<String>, e164: String, lookupKey: String?): Boolean {
        val updated = PinnedContacts.pinSubscriber(current, e164, lookupKey) ?: return false
        prefs.setPinnedNumbers(updated)
        return true
    }

    suspend fun unpin(current: List<String>, e164: String, lookupKey: String?) =
        prefs.setPinnedNumbers(PinnedContacts.unpinSubscriber(current, e164, lookupKey))

    /** Открепление из списка «Закреплённые» — по записи, а не по номеру. */
    suspend fun unpinKey(current: List<String>, key: String) =
        prefs.setPinnedNumbers(current.filterNot { it == key })

    suspend fun move(current: List<String>, key: String, up: Boolean) =
        prefs.setPinnedNumbers(PinnedContacts.move(current, key, up))

    /**
     * Перевод закреплений по номеру на контакты. Пишет в настройки только
     * при изменении (migrate возвращает тот же список, если менять нечего) —
     * поэтому подписка на настройки не зацикливается.
     * @return сколько записей было и стало, или null — ничего не менялось
     */
    suspend fun migrateIfNeeded(pins: List<String>, contacts: List<Contact>): Pair<Int, Int>? {
        val migrated = migrate(pins, contacts) ?: return null
        prefs.setPinnedNumbers(migrated)
        return pins.size to migrated.size
    }

    /**
     * Снять все закрепления удалённого контакта (1.3.4, 04.10.2026).
     * @return true — что-то было закреплено и снято
     */
    suspend fun unpinContact(current: List<String>, contact: Contact): Boolean {
        val updated = withoutContact(current, contact)
        if (updated.size == current.size) return false
        prefs.setPinnedNumbers(updated)
        return true
    }

    companion object {

        /**
         * Список без закреплений контакта: по ключу контакта и по каждому
         * его номеру — закрепления из версий до 1.3.2 могли быть по номеру.
         */
        fun withoutContact(pins: List<String>, contact: Contact): List<String> =
            contact.numbers.fold(PinnedContacts.unpinSubscriber(pins, "", contact.lookupKey)) { acc, number ->
                PinnedContacts.unpinSubscriber(acc, number.e164, contact.lookupKey)
            }

        fun isContactPinned(pins: List<String>, contact: Contact): Boolean =
            withoutContact(pins, contact).size != pins.size

        /** Новый список после перевода или null, если менять нечего. */
        fun migrate(pins: List<String>, contacts: List<Contact>): List<String>? {
            if (pins.isEmpty() || contacts.isEmpty()) return null
            val byE164 = HashMap<String, String>()
            contacts.forEach { c -> c.numbers.forEach { byE164.putIfAbsent(it.e164, c.lookupKey) } }
            val migrated = PinnedContacts.migrate(pins) { byE164[it] }
            return if (migrated === pins) null else migrated
        }

        /**
         * Строки экрана «Закреплённые»: имя и фото — из книги, номер —
         * последнего звонка этого контакта (с любого его номера).
         */
        fun entries(
            pins: List<String>,
            contacts: List<Contact>,
            callLog: List<CallLogGroup>
        ): List<PinnedEntry> {
            val byLookup = contacts.associateBy { it.lookupKey }
            return pins.map { raw ->
                val pin = PinnedContacts.parse(raw)
                val contact = pin.lookupKey?.let { byLookup[it] }
                    ?: contacts.firstOrNull { c -> c.numbers.any { it.e164 == pin.e164 } }
                // Последний звонок этого контакта (с любого номера) или номера
                val group = callLog.firstOrNull { g ->
                    (contact != null && g.contactId == contact.id) || g.e164 == pin.e164
                }
                val number = group?.number ?: contact?.numbers?.firstOrNull()?.value ?: pin.e164
                PinnedEntry(
                    key = raw,
                    e164 = group?.e164 ?: contact?.numbers?.firstOrNull()?.e164 ?: pin.e164,
                    displayName = contact?.displayName ?: group?.displayName,
                    formattedNumber = PhoneFormatter.format(number),
                    photoUri = contact?.photoUri ?: group?.photoUri
                )
            }
        }
    }
}

/** Строка экрана «Закреплённые». */
data class PinnedEntry(
    /** Запись в настройках — для перестановки и открепления. */
    val key: String,
    val e164: String,
    val displayName: String?,
    val formattedNumber: String,
    val photoUri: String?
)
