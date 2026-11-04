package com.ogneliss.dialer.data

import androidx.compose.runtime.Immutable

/** Тип вызова в журнале. */
enum class CallType { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL }

/**
 * ОДНА ЗАПИСЬ ЖУРНАЛА, как её отдаёт система.
 * Сырые данные до группировки.
 */
/*
 * ══════════════════════════════════════════════════════════════════════════
 * ПРО @Immutable НА МОДЕЛЯХ
 * ══════════════════════════════════════════════════════════════════════════
 * Compose делит типы на стабильные и нестабильные. Для стабильных он
 * сравнивает значения и пропускает перерисовку, если ничего не изменилось.
 * Нестабильные считаются изменившимися ВСЕГДА.
 *
 * Класс считается нестабильным, если у него есть поле нестабильного типа.
 * `List` — интерфейс: за ним может стоять изменяемая реализация, поэтому
 * Compose считает его нестабильным по умолчанию.
 *
 * Следствие: класс с одним полем `List` делает нестабильным всё,
 * что его содержит. При любом обновлении данных перерисовывались ВСЕ
 * строки списка, а не только изменившиеся, — и вся работа по подготовке
 * строк заранее пропадала впустую.
 *
 * `@Immutable` — обещание компилятору: поля не меняются после создания,
 * можно сравнивать по значению. Обещание нужно держать: если внутрь
 * попадёт изменяемый список и его кто-то поменяет, Compose об этом
 * не узнает и покажет устаревшие данные.
 *
 * У нас списки создаются один раз при чтении из провайдера и дальше
 * не меняются — заменяются новыми объектами целиком. Обещание выполнимо.
 * ══════════════════════════════════════════════════════════════════════════
 */

@Immutable
data class CallLogEntry(
    val id: Long,
    val number: String,
    val e164: String,
    val cachedName: String?,
    val photoUri: String?,
    val type: CallType,
    val date: Long,
    val durationSec: Long,
    val phoneAccountId: String?,
    val contactId: Long?,
    /**
     * SMS или MMS вместо вызова (1.10.0): журнал показывает сообщения
     * одной лентой с вызовами. У вызовов — null; type у сообщения —
     * направление (INCOMING / OUTGOING), длительность — 0, номер записи
     * отрицательный (Message.journalId). Счётчики вызовов карточки
     * сообщения не считают.
     */
    val message: com.ogneliss.dialer.sms.JournalMessage? = null
) {
    val isMessage: Boolean get() = message != null
}

/**
 * КАРТОЧКА ЖУРНАЛА — несколько вызовов одного абонента, объединённые вместе.
 *
 * ── Что считается «одним абонентом» ────────────────────────────────────────
 * Если номер принадлежит контакту, группировка идёт по КОНТАКТУ, а не по
 * номеру. У «Любимой» в телефонной книге может быть три номера — звонки
 * с них должны попасть в одну карточку, иначе история разваливается
 * на три ленты и пользователь не видит целостной картины общения.
 *
 * Для номеров вне контактов ключ — сам номер в формате E.164.
 *
 * ── Счётчики по типам ──────────────────────────────────────────────────────
 * Карточка показывает не просто «(8)», а сколько из них входящих,
 * исходящих и пропущенных. Это отвечает на вопрос, который человек
 * задаёт себе, глядя в журнал: «это я ему звонил или он мне?».
 */
@Immutable
data class CallLogGroup(
    /** Стабильный ключ группы: "contact:123" или "number:+79001234567". */
    val key: String,
    /** Id последнего вызова — для операций удаления. */
    val lastCallId: Long,
    val number: String,
    val e164: String,
    val displayName: String?,
    val photoUri: String?,
    val contactId: Long?,

    /** Тип последнего вызова — определяет иконку карточки. */
    val lastType: CallType,
    val lastDate: Long,
    /** Длительность последнего вызова. */
    val durationSec: Long,

    /** Общее число вызовов в группе. */
    val count: Int,
    val incomingCount: Int,
    val outgoingCount: Int,
    val missedCount: Int,
    val rejectedCount: Int,

    /** Суммарная длительность всех разговоров группы. */
    val totalDurationSec: Long,
    /** Все номера абонента, попавшие в группу (у контакта их может быть несколько). */
    val numbers: List<String>,
    val subscriptionLabel: String?,
    val subscriptionId: String?,
    /** Все записи группы — нужны для экрана истории по абоненту. */
    val entries: List<CallLogEntry> = emptyList(),
    /**
     * Последнее событие группы — сообщение (1.10.0): строка показывает его
     * текст, нажатие открывает переписку. null — последнее событие — вызов.
     */
    val lastMessage: com.ogneliss.dialer.sms.JournalMessage? = null,
    /** Непрочитанных входящих сообщений в группе. */
    val unreadMessages: Int = 0
) {
    /** Есть ли непросмотренные пропущенные — карточка подсвечивается. */
    /**
     * В истории общения ЕСТЬ пропущенные.
     *
     * ── Не путать с тревожной меткой в журнале ─────────────────────────────
     * Она загорается, только когда пропущен ПОСЛЕДНИЙ вызов:
     * см. CallLogRowMapper. Здесь же — факт за всю историю, и годится
     * он для фильтров и статистики, но не для окраски карточки.
     *
     * Иначе у человека, с которым созваниваются годами, карточка
     * горела бы красным всегда — и метка перестала бы что-либо
     * означать.
     */
    val hasAnyMissed: Boolean get() = missedCount > 0

    /** Односторонняя ли связь: только входящие или только исходящие. */
    val isOneWay: Boolean
        get() = (incomingCount + missedCount == 0) || (outgoingCount == 0)
}

@Immutable
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val photoUri: String?,
    val starred: Boolean,
    val numbers: List<PhoneNumber>,
    val emails: List<String> = emptyList(),
    /**
     * Даты: день рождения, годовщины.
     *
     * Хранятся как есть из книги — приложение их не толкует и не считает
     * возраст: у половины записей год не указан вовсе, а достраивать
     * его значило бы показывать выдуманное.
     */
    val events: List<ContactEvent> = emptyList(),
    /**
     * Имя в форме «Фамилия Имя» из книги (DISPLAY_NAME_ALTERNATIVE, 1.6.2)
     * — для настроек «Как показывать имя» и «Сортировка контактов».
     * Пусто — такое же, как displayName. Показывать — через ContactNames.
     */
    val nameAlt: String = ""
)

/** Дата из карточки контакта: подпись типа и сама дата в читаемом виде. */
@Immutable
data class ContactEvent(val label: String, val date: String)

@Immutable
data class PhoneNumber(val value: String, val label: String, val e164: String)

/**
 * SIM-карта в слоте.
 *
 * label — то, что показываем на кнопках: своё название, если задано,
 * иначе системное.
 *
 * carrier и systemName — чтобы человек мог понять, какая карта где,
 * когда подписывает слоты. «Слот 1» и «Слот 2» сами по себе ничего
 * не говорят: где у него какой номер, пользователь не знает (вопрос
 * пришёл от пользователя). По умолчанию null: сохранённый на диске
 * список карт этих полей не хранит, и после первого живого чтения
 * они появятся сами.
 */
@Immutable
data class SimSlot(
    val subscriptionId: Int,
    val slotIndex: Int,
    val label: String,
    val number: String?,
    /** Оператор, как его сообщает карта: «Beeline», «MTS RUS». */
    val carrier: String? = null,
    /** Имя карты в системе — то, что видно в настройках телефона. */
    val systemName: String? = null,
    /**
     * Встроенная eSIM, а не пластиковая карта. Не у всех два физических
     * слота: часто вторая «карта» — eSIM, и «Слот 2» человеку ни о чём
     * не говорит, а «eSIM» — сразу понятно.
     */
    val embedded: Boolean = false
)

data class SpeedDialEntry(val digit: Int, val number: String, val name: String?)


/**
 * ПРАВИЛО БЛОКИРОВКИ ИЛИ РАЗРЕШЕНИЯ.
 *
 * Модель, а не сущность Room: экранам и контроллерам не нужно знать,
 * что данные лежат в базе, а тестовым заглушкам — тащить за собой
 * аннотации Room.
 *
 * @param pattern номер в E.164 или маска вида «+7800*»
 * @param comment пометка пользователя: «холодные обзвоны», «курьер»
 */
@Immutable
data class NumberRule(
    val pattern: String,
    val kind: String,
    val comment: String? = null,
    val createdAt: Long = 0
) {
    val isAllow: Boolean get() = kind == RuleKind.ALLOW.key
    val isBlock: Boolean get() = kind == RuleKind.BLOCK.key

    /** Маска, а не точный номер: под неё попадает целый диапазон. */
    val isMask: Boolean get() = pattern.contains('*') || pattern.contains('?')
}

/**
 * ИТОГИ ПО АБОНЕНТУ ЗА ВСЮ ИСТОРИЮ.
 *
 * Считаются отдельным лёгким запросом, а не по загруженным страницам:
 * иначе числа в карточках растут по мере подгрузки, и человек видит,
 * как «5 вызовов» превращаются в «47».
 */
data class CallTotals(
    val total: Int,
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
    val rejected: Int,
    val durationSec: Long
) {
    /** Накопитель: считать в одном проходе дешевле, чем фильтровать список. */
    class Builder {
        private var total = 0
        private var incoming = 0
        private var outgoing = 0
        private var missed = 0
        private var rejected = 0
        private var duration = 0L

        fun add(type: Int, durationSec: Long) {
            total++
            duration += durationSec

            when (type) {
                android.provider.CallLog.Calls.INCOMING_TYPE -> incoming++
                android.provider.CallLog.Calls.OUTGOING_TYPE -> outgoing++
                android.provider.CallLog.Calls.MISSED_TYPE -> missed++
                android.provider.CallLog.Calls.REJECTED_TYPE -> rejected++
            }
        }

        fun build() = CallTotals(total, incoming, outgoing, missed, rejected, duration)
    }
}
