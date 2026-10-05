package com.ogneliss.dialer.data

import com.ogneliss.dialer.settings.GroupingMode
import kotlinx.coroutines.flow.Flow

/**
 * ИНТЕРФЕЙСЫ ИСТОЧНИКОВ ДАННЫХ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОНИ НУЖНЫ
 * ══════════════════════════════════════════════════════════════════════════
 * В проекте 25 наборов тестов и ни одного — на слой, который связывает
 * данные с интерфейсом. Причина не в лени: контроллеры зависели
 * от конкретных репозиториев, а те намертво привязаны к Android —
 * `ContentResolver`, `Context`, системные провайдеры.
 *
 * Чтобы проверить, что подгрузка страницы обновляет список, пришлось бы
 * поднимать эмулятор с заполненным журналом вызовов. Никто такие тесты
 * не пишет, поэтому весь слой оставался непроверенным — и именно в нём
 * находилась половина ошибок этого проекта.
 *
 * Интерфейсы разрывают эту связь. Реализация для Android остаётся прежней,
 * а в тестах подставляется простая заглушка со списком в памяти.
 *
 * ── Почему интерфейсы узкие ────────────────────────────────────────────────
 * Каждый описывает ровно то, что нужно потребителю, а не всё, что умеет
 * репозиторий. Широкий интерфейс заставил бы заглушку реализовывать
 * два десятка методов ради проверки одного — и тесты писать перестали бы
 * снова, теперь уже по другой причине.
 * ══════════════════════════════════════════════════════════════════════════
 */

/** Чтение и изменение журнала вызовов. */
interface CallLogSource {

    /**
     * Страница журнала, от новых записей к старым.
     *
     * @param offset сколько записей пропустить — для подгрузки
     */
    suspend fun loadEntries(limit: Int, offset: Int = 0): List<CallLogEntry>

    /**
     * Итоги по каждому номеру за всю историю.
     *
     * Отдельно от постраничной загрузки: счётчики, посчитанные
     * по загруженным страницам, растут на глазах у человека
     * при подгрузке следующих.
     */
    suspend fun totalsByNumber(): Map<String, CallTotals>

    /** История вызовов по конкретному номеру. */
    suspend fun historyFor(number: String, limit: Int = 200): List<CallLogEntry>

    suspend fun deleteGroup(group: CallLogGroup): Boolean

    suspend fun clearAll(): Boolean
}

/**
 * Сведения о SIM-картах.
 *
 * Загрузчику журнала нужны только две вещи: сколько карт и как подписать
 * ту, с которой шёл вызов. Всё остальное — управление предпочтениями,
 * подписка на изменения — его не касается.
 */
interface SimInfoSource {

    /** Активные карты, с которых можно звонить. */
    suspend fun availableSlots(): List<SimSlot>

    /** Подпись карты по идентификатору аккаунта из журнала. */
    fun labelForAccount(accountId: String?): String?
}

/**
 * Настройки, влияющие на вид журнала.
 *
 * Отдельно от общего Prefs: загрузчику нужен один переключатель,
 * а не тридцать. Заглушка для теста получается в одну строку.
 */
interface GroupingSettings {
    val groupingMode: Flow<GroupingMode>
}


/**
 * Правила блокировки и разрешений.
 *
 * Возвращает модели, а не сущности Room: потребителю не нужно знать,
 * что данные лежат в базе. Заодно это избавляет тест от зависимости
 * на аннотации Room в заглушке.
 */
interface RulesSource {

    val all: Flow<List<NumberRule>>

    suspend fun add(pattern: String, kind: RuleKind, comment: String? = null)

    suspend fun remove(pattern: String)

    /**
     * Решение по номеру.
     *
     * @return ALLOW, BLOCK или null, если ни одно правило не подошло.
     *         Три состояния, а не булево: «нет правила» и «явно разрешён» —
     *         разные вещи, и путать их нельзя
     */
    suspend fun decide(e164: String): RuleKind?
}

/** Запись контактов: то немногое, что приложение меняет само. */
interface ContactWriter {
    suspend fun setStarred(contactId: Long, starred: Boolean): Boolean
    suspend fun setSendToVoicemail(contactId: Long, enabled: Boolean): Boolean
    /** Текущее значение (1.3.4): меню карточки показывает его переключателем. */
    suspend fun isSendToVoicemail(contactId: Long): Boolean
    suspend fun setCustomRingtone(contactId: Long, uri: android.net.Uri?): Boolean
    suspend fun delete(contactId: Long): Boolean
}

/**
 * Переходы в системное приложение «Контакты».
 *
 * Отдельно от записи: здесь не меняются данные, а запускается чужой
 * экран. Разные по природе операции — разные интерфейсы, иначе заглушка
 * для теста записи тащила бы за собой запуск Activity.
 */
interface ContactEditorLauncher {
    fun createWithNumber(number: String, name: String?)
    fun addNumberToExisting(number: String)
    fun edit(contactId: Long)
}

/** Ярлыки на рабочем столе и в меню значка приложения. */
interface ShortcutSink {
    fun updateDynamic(targets: List<ShortcutTarget>)
    fun requestPin(target: ShortcutTarget): Boolean
}

/** Цель ярлыка: кому звонить в один тап. */
data class ShortcutTarget(
    val name: String,
    val number: String,
    val e164: String,
    /**
     * Фотография контакта для значка ярлыка.
     *
     * Без неё все ярлыки выглядели одинаково — белый круг с трубкой, —
     * и различить их можно было только по подписи. А смысл ярлыка
     * в том, чтобы позвонить не читая: лицо узнаётся быстрее имени.
     */
    val photoUri: String? = null
)
