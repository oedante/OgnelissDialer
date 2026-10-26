package com.ogneliss.dialer.call

import com.ogneliss.dialer.data.*
import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.settings.Prefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * СБОРЩИК ДАННЫХ ДЛЯ ПОЛИТИКИ ФИЛЬТРАЦИИ.
 *
 * ── Это самый горячий путь в приложении ────────────────────────────────────
 * Система вызывает CallScreeningService при КАЖДОМ входящем звонке и ждёт
 * ответа ограниченное время (порядка 5 секунд, на части прошивок меньше).
 * Не уложились — вызов проходит без нашего решения, а телефон уже звонит.
 *
 * Отсюда три требования, определившие устройство класса:
 *
 * 1. НИКАКИХ полных выборок. Раньше здесь вызывался contacts.loadAll(),
 *    который читает всю телефонную книгу — тысячи строк курсора плюс
 *    отдельный запрос за адресами почты. На книге в 2000 контактов это
 *    сотни миллисекунд, а на слабом устройстве под нагрузкой — секунды.
 *    Заменено на точечный PhoneLookup: система ищет по индексу.
 *
 * 2. ПАРАЛЛЕЛЬНОЕ чтение. Проверка контактов, правил, системного списка
 *    и меток независимы. Последовательно это сумма задержек, параллельно —
 *    максимум из них.
 *
 * 3. ЖЁСТКИЙ ТАЙМАУТ. Если что-то зависло (провайдер контактов на MIUI
 *    иногда отвечает секундами), лучше отдать решение по неполным данным,
 *    чем не ответить вовсе.
 *
 * ── Принцип при любой ошибке ───────────────────────────────────────────────
 * Пропустить вызов. Молчащий телефон вредит сильнее, чем один принятый
 * спам-звонок, поэтому все catch трактуются в пользу пропуска.
 */
@Singleton
class SpamInputCollector @Inject constructor(
    private val rules: RulesRepository,
    /*
     * Отметки о спаме, а не заметки: с версии 1.1.1 это разные
     * хранилища. Заметка — то, что человек написал себе, и на решение
     * о блокировке она не влияет никак.
     */
    private val marks: SpamMarksRepository,
    private val contacts: ContactsRepository,
    private val systemBlock: SystemBlockRepository,
    private val numberInfo: NumberInfoRepository,
    private val prefs: Prefs,
    /** Тихие часы (1.8.0): звонил ли номер без звука за последние 15 минут. */
    private val blockedCalls: BlockedCallLog,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /**
     * Метки источников данных для сообщений в логе.
     * Не интерфейсные строки: попадают только в файл журнала,
     * который читает разработчик при разборе проблемы.
     */
    private object Source {
        const val CONTACT = "контакт"
        const val RULES = "правила"
        const val SYSTEM_BLOCK = "системный блок-лист"
        const val TAGS = "метки"
        const val NUMBER_INFO = "определитель"
        const val QUIET_REPEAT_INFO = "повторный звонок"
    }

    private companion object {
        const val TAG = "SpamInput"

        /**
         * Бюджет на сбор данных. Взят с запасом относительно системного
         * лимита: остаток нужен на само решение и на ответ системе.
         */
        const val BUDGET_MS = 1500L
    }

    suspend fun collect(number: String?, e164: String): SpamPolicy.SpamInput =
        withContext(io) {
            val started = System.currentTimeMillis()
            val input = try {
                withTimeout(BUDGET_MS) { collectParallel(number, e164) }
            } catch (t: TimeoutCancellationException) {
                // Не уложились — решаем по минимуму. Практически это означает
                // «пропустить», если номер не в явных списках
                Logger.w(TAG, "Сбор данных не уложился в $BUDGET_MS мс, решаю по минимуму")
                SpamPolicy.SpamInput(number = number, e164 = e164, isInContacts = false)
            }
            Logger.d(TAG, "Данные собраны за ${System.currentTimeMillis() - started} мс")
            input
        }

    /**
     * Все независимые проверки запускаются одновременно.
     * Время сбора равно самой медленной из них, а не их сумме.
     */
    private suspend fun collectParallel(
        number: String?,
        e164: String
    ): SpamPolicy.SpamInput = coroutineScope {

        val settingsDeferred = async { collectSettings() }
        val contactDeferred = async {
            safe(Source.CONTACT, false) { number != null && contacts.isKnownNumber(number) }
        }
        val ruleDeferred = async { safe<RuleKind?>(Source.RULES, null) { rules.decide(e164) } }
        val systemBlockDeferred = async {
            safe(Source.SYSTEM_BLOCK, false) {
                number != null && systemBlock.isBlocked(number)
            }
        }
        // Повтор нужен только тихим часам; запрос по ключу — дешёвый
        val repeatDeferred = async {
            safe(Source.QUIET_REPEAT_INFO, false) {
                e164.isNotBlank() && blockedCalls.silencedQuietlySince(
                    e164, System.currentTimeMillis() - QuietHours.REPEAT_WINDOW_MS
                )
            }
        }
        val tagsDeferred = async {
            // Отметка переводится в прежний вид — список ключей: SpamPolicy
            // проверяет «tags содержит spam или fraud», и менять правило
            // решения ради смены хранилища незачем
            safe(Source.TAGS, emptyList<String>()) {
                listOfNotNull(marks.get(e164)?.type?.key)
            }
        }

        val settings = settingsDeferred.await()
        val isInContacts = contactDeferred.await()

        // Оценку риска считаем ТОЛЬКО если автоблокировка включена.
        // Разбор номера через libphonenumber — заметная вычислительная
        // работа, и делать её впустую на каждом звонке незачем.
        /*
         * Определитель больше не запрашивается.
         *
         * Он был нужен только ради оценки риска — она убрана. Категория
         * номера (платный, короткий, международный) в решении о блокировке
         * тоже больше не участвует: платные номера опасны при ИСХОДЯЩЕМ
         * звонке, и предупреждение перед набором осталось на месте.
         */

        val ruleDecision = ruleDeferred.await()

        SpamPolicy.SpamInput(
            number = number,
            e164 = e164,
            isInContacts = isInContacts,
            inAllowList = ruleDecision == RuleKind.ALLOW,
            inBlockList = ruleDecision == RuleKind.BLOCK,
            inSystemBlockList = systemBlockDeferred.await(),
            tags = tagsDeferred.await(),
            category = NumberCategory.UNKNOWN,
            settings = settings,
            minuteOfDay = minuteOfDayNow(),
            repeatCall = repeatDeferred.await()
        )
    }

    /** Минута суток по часам телефона — в его часовом поясе. */
    private fun minuteOfDayNow(): Int {
        val now = java.util.Calendar.getInstance()
        return now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
    }

    /** Настройки читаются одним заходом, тоже параллельно. */
    private suspend fun collectSettings(): SpamPolicy.Settings = coroutineScope {
        val hidden = async { safe("blockHidden", false) { prefs.blockHidden.first() } }
        val unknown = async {
            safe("blockNotInContacts", false) { prefs.blockNotInContacts.first() }
        }
        val tagged = async { safe("blockTaggedSpam", true) { prefs.blockTaggedSpam.first() } }
        val quiet = async { safe("quietHours", QuietHours.OFF) { prefs.quietHours.first() } }

        SpamPolicy.Settings(
            blockHidden = hidden.await(),
            blockNotInContacts = unknown.await(),
            blockTaggedSpam = tagged.await(),
            quietHours = quiet.await()
        )
    }

    /**
     * Обёртка: сбой при чтении не должен приводить к блокировке вызова.
     * Отмену корутины (таймаут) пробрасываем дальше — её глотать нельзя,
     * иначе withTimeout перестанет работать.
     */
    private suspend inline fun <T> safe(what: String, fallback: T, block: suspend () -> T): T =
        try {
            block()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            Logger.w(TAG, "Не удалось прочитать «$what», беру значение по умолчанию", t)
            fallback
        }
}
