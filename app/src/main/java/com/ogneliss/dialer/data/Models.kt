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
    /** Как сеть показала номер (2.6.0): скрытый — без номера, разбор у NumberPresentation. */
    val presentation: NumberPresentation = NumberPresentation.ALLOWED
)

/**
 * КАК СЕТЬ ПОКАЗАЛА НОМЕР ЗВОНЯЩЕГО (2.6.0) — CallLog.Calls.NUMBER_PRESENTATION.
 *
 * Автор, 14.11.2026, снимок карточки пропущенного: «Неизвестный номер никак
 * не определяется». Было: признак не читался вовсе — у вызова со скрытого
 * номера пустой заголовок в журнале и в карточке, пустая строка «Номера»,
 * и кнопки «Позвонить», «Написать», «В контакты», «Отметка» — на пустоту.
 * Системный диалер (снимок того же вызова) пишет «Номер скрыт», кнопки
 * вызова нет, «Добавить» и «Отправить сообщение» — серые.
 *
 * • ALLOWED — номер есть;
 * • RESTRICTED — звонящий скрыл номер («Номер скрыт»);
 * • UNKNOWN — сеть номер не передала («Номер не определён»), в том числе
 *   UNAVAILABLE (5, Android 13) и пустой номер без признака;
 * • PAYPHONE — таксофон.
 * Старые прошивки вместо признака пишут в номер «-1», «-2», «-3» — тоже
 * узнаются здесь, и номер тогда считается пустым.
 */
enum class NumberPresentation {
    ALLOWED, RESTRICTED, UNKNOWN, PAYPHONE;

    /** Номера нет: звонить, писать, заносить в книгу и отмечать нечего. */
    val hidden: Boolean get() = this != ALLOWED

    companion object {
        // Значения CallLog.Calls.PRESENTATION_* (без Android: проверяется тестами)
        private const val CODE_RESTRICTED = 2
        private const val CODE_UNKNOWN = 3
        private const val CODE_PAYPHONE = 4
        private const val CODE_UNAVAILABLE = 5

        fun of(code: Int?, number: String?): NumberPresentation {
            val raw = number?.trim().orEmpty()
            return when {
                code == CODE_RESTRICTED || raw == "-2" -> RESTRICTED
                code == CODE_PAYPHONE || raw == "-3" -> PAYPHONE
                code == CODE_UNKNOWN || code == CODE_UNAVAILABLE || raw == "-1" -> UNKNOWN
                raw.isEmpty() -> UNKNOWN
                else -> ALLOWED
            }
        }
    }
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
    /** Как сеть показала номер последнего вызова (2.6.0, NumberPresentation). */
    val presentation: NumberPresentation = NumberPresentation.ALLOWED,
    /** Все записи группы — нужны для экрана истории по абоненту. */
    val entries: List<CallLogEntry> = emptyList()
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
    val nameAlt: String = "",
    /**
     * ОРГАНИЗАЦИЯ ИЗ КНИГИ (2.6.0, форум, oleg_p: «добавить поиск по
     * организации — для деловых контактов это важно»): название, отдел,
     * должность — первая заполненная запись Organization. По ним ищет
     * ContactSearch, строку для показа даёт [organizationLine].
     *
     * В кэш контактов (ContactCache) не попадает: кэш показывается до
     * первого чтения книги — доли секунды, — а лишний столбец значил бы
     * миграцию базы ради них.
     */
    val company: String = "",
    val department: String = "",
    val jobTitle: String = ""
) {
    /**
     * Организация одной строкой: «Газпромбанк, ведущий специалист». Отдел
     * не показывается — строка вышла бы длиннее имени; искать по нему можно.
     */
    val organizationLine: String
        get() = listOf(company, jobTitle).filter { it.isNotBlank() }.joinToString(", ")
}

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
