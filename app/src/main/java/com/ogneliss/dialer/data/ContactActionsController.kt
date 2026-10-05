package com.ogneliss.dialer.data

import com.ogneliss.dialer.util.PhoneFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ДЕЙСТВИЯ НАД КОНТАКТАМИ.
 *
 * ── Что здесь собрано ──────────────────────────────────────────────────────
 * Всё, что приложение делает с контактами помимо чтения: избранное,
 * тихий режим, мелодия, удаление, ярлыки на рабочем столе и переходы
 * в системный редактор.
 *
 * Части лежали во ViewModel, части — в двух репозиториях, и разделение
 * между ними было неочевидным: почему избранное здесь, а ярлык там.
 * Теперь граница проходит по смыслу: репозитории читают и пишут,
 * этот класс решает, что именно делать.
 *
 * ── Про ярлыки ─────────────────────────────────────────────────────────────
 * Динамические ярлыки обновляются после каждой загрузки данных: важные
 * контакты меняются со временем, и список в меню значка приложения
 * должен за этим следовать.
 */
@Singleton
class ContactActionsController @Inject constructor(
    private val writer: ContactWriter,
    private val editor: ContactEditorLauncher,
    private val shortcuts: ShortcutSink
) {

    private companion object { const val TAG = "Contacts" }

    // ── Быстрые операции ───────────────────────────────────────────────────

    suspend fun toggleStar(contact: Contact): Boolean =
        writer.setStarred(contact.id, !contact.starred)

    /**
     * Отправлять вызовы контакта сразу на голосовую почту.
     * Телефон при этом не звонит вовсе.
     */
    suspend fun setSilent(contactId: Long, silent: Boolean): Boolean =
        writer.setSendToVoicemail(contactId, silent)

    suspend fun isSilent(contactId: Long): Boolean = writer.isSendToVoicemail(contactId)

    suspend fun setRingtone(contactId: Long, uri: android.net.Uri?): Boolean =
        writer.setCustomRingtone(contactId, uri)

    /**
     * Удаление контакта.
     *
     * Разрушительно: удаляется агрегат вместе с записями из всех аккаунтов,
     * включая Google. Вызывать только после явного подтверждения.
     */
    suspend fun delete(contactId: Long): Boolean = writer.delete(contactId)

    // ── Переходы в системное приложение ────────────────────────────────────

    fun create(number: String, name: String? = null) = editor.createWithNumber(number, name)
    fun addToExisting(number: String) = editor.addNumberToExisting(number)
    fun edit(contactId: Long) = editor.edit(contactId)

    // ── Ярлыки ─────────────────────────────────────────────────────────────

    /**
     * Обновление ярлыков в меню значка приложения.
     *
     * Порядок: сначала избранные — пользователь отметил их сам,
     * затем те, кому звонили чаще. Система показывает не больше
     * четырёх, поэтому важен именно порядок, а не полнота списка.
     */
    /**
     * Отпечаток последнего набора ярлыков.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Обновление ярлыков — Binder-вызовы в системный процесс. В логе
     * видно, что они происходили при каждой смене темы: список избранных
     * при этом не менялся, а работа выполнялась.
     *
     * Сравнение отпечатка отсекает такие вызовы: набор из четырёх имён
     * и номеров сравнивается мгновенно.
     */
    private var lastShortcutSignature = 0

    fun updateShortcuts(contacts: List<Contact>, callLog: List<CallLogGroup>) {
        /*
         * Фотографии для значков ярлыков.
         *
         * Строится по номеру: у записи журнала своего фото нет,
         * а contactId из журнала часто пуст — на нём полагаться нельзя,
         * это уже подводило в других местах.
         */
        val photoByNumber = buildMap {
            contacts
                .sortedBy { it.displayName.lowercase() }
                .forEach { contact ->
                    val photo = contact.photoUri ?: return@forEach
                    contact.numbers.forEach { number ->
                        putIfAbsent(number.e164, photo)
                    }
                }
        }

        val targets = buildList {
            contacts.filter { it.starred }.forEach { contact ->
                contact.numbers.firstOrNull()?.let {
                    add(
                        ShortcutTarget(
                            contact.displayName, it.value, it.e164,
                            photoUri = contact.photoUri
                        )
                    )
                }
            }
            callLog
                .filter { it.displayName != null }
                .sortedByDescending { it.count }
                .forEach { group ->
                    add(
                        ShortcutTarget(
                            group.displayName!!, group.number, group.e164,
                            photoUri = photoByNumber[group.e164] ?: group.photoUri
                        )
                    )
                }
        }.distinctBy { it.e164 }

        // Набор не изменился — системе сообщать нечего
        val signature = targets.hashCode()
        if (signature == lastShortcutSignature) return

        lastShortcutSignature = signature
        shortcuts.updateDynamic(targets)
    }

    fun pinShortcut(name: String, number: String, e164: String): Boolean =
        shortcuts.requestPin(ShortcutTarget(name, number, e164))

    // ── Поиск ──────────────────────────────────────────────────────────────

    /**
     * Есть ли номер в телефонной книге.
     *
     * Точечная проверка по загруженному списку, без запроса к провайдеру:
     * вызывается при отрисовке и должна быть мгновенной.
     */
    fun isKnown(contacts: List<Contact>, number: String): Boolean {
        val e164 = PhoneFormatter.toE164(number)
        return contacts.any { contact -> contact.numbers.any { it.e164 == e164 } }
    }
}
