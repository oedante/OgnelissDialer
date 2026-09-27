package com.ogneliss.dialer.data

import com.ogneliss.dialer.di.IoDispatcher
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.settings.Prefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ВЗАИМОДЕЙСТВИЕ СО СТОРОННИМ ОПРЕДЕЛИТЕЛЕМ НОМЕРА.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК ЭТО УСТРОЕНО В ANDROID
 * ══════════════════════════════════════════════════════════════════════════
 * Определитель (Яндекс, Kaspersky, Truecaller) — отдельное приложение
 * с ролью `ROLE_CALL_SCREENING`. Она НЕ конфликтует с ролью диалера:
 * обе существуют одновременно, и порядок обработки входящего вызова такой:
 *
 *   1. Системный список блокировки — вызов не звонит вообще
 *   2. `CallScreeningService` приложения с ROLE_CALL_SCREENING
 *   3. `CallScreeningService` диалера по умолчанию — наш
 *
 * Отсюда два следствия, которые обычно понимают неправильно:
 *
 * • Установка Ogneliss телефоном по умолчанию НЕ отключает определитель.
 *   Выбирать между ними не нужно, они работают вместе.
 *
 * • Прочитать вердикт чужого определителя нельзя — публичного API нет.
 *   Но заблокированные им вызовы видны в журнале с заполненным
 *   `BLOCK_REASON`, а имя абонента он отдаёт штатно.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК ИМЯ ПОПАДАЕТ К НАМ
 * ══════════════════════════════════════════════════════════════════════════
 * Через `ContactsContract.Directory` — механизм, которым приложения
 * публикуют «удалённые справочники». Определитель регистрирует себя как
 * такой справочник, а диалер запрашивает у него имя по номеру.
 *
 * Это единственный поддерживаемый способ, и именно так штатный «Телефон»
 * показывает подписи вроде «возможно, спам» от установленного определителя.
 *
 * ── Почему только по явному разрешению ─────────────────────────────────────
 * Запрос передаёт номер стороннему приложению, а оно может отправить его
 * на свои серверы. Это единственное место, где данные могут покинуть
 * устройство, поэтому провайдеры включаются поимённо и по умолчанию
 * выключены все.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Singleton
class ExternalCallerIdController @Inject constructor(
    private val directory: DirectoryLookupRepository,
    private val prefs: Prefs,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    private companion object {
        const val TAG = "CallerId"

        /**
         * Бюджет на один запрос.
         *
         * Обращение уходит в чужое приложение, и оно может ходить в сеть.
         * Ждать дольше нельзя: на входящем вызове счёт идёт на секунды,
         * а в журнале задержка заморозила бы обновление списка.
         */
        const val LOOKUP_TIMEOUT_MS = 2000L

        /**
         * Сколько номеров запрашивать за один проход по журналу.
         *
         * Каждый запрос — обращение к чужому приложению и, возможно,
         * сетевой вызов с его стороны. Просить сразу три сотни имён
         * означало бы устроить лавину запросов и посадить батарею —
         * причём чужому приложению, что заметят и припишут нам.
         */
        const val BATCH_LIMIT = 20
    }

    /**
     * Кэш найденных имён.
     *
     * Спрашивать одно и то же при каждом обновлении списка незачем:
     * это лишние обращения наружу с передачей номера. Кэш живёт
     * до перезапуска процесса — имена меняются редко.
     */
    private val cache = ConcurrentHashMap<String, String>()

    /** Включён ли хоть один провайдер. */
    suspend fun isEnabled(): Boolean = prefs.externalLookupIds.first().isNotEmpty()

    /**
     * Имя для одного номера.
     *
     * Применяется на входящем вызове: там нужен именно этот номер
     * и как можно быстрее.
     *
     * @return имя или null, если провайдеры выключены, ничего не нашлось
     *         или ответ не пришёл вовремя
     */
    suspend fun lookup(number: String): String? {
        if (number.isBlank()) return null
        cache[number]?.let { return it }

        val enabled = prefs.externalLookupIds.first()
        if (enabled.isEmpty()) return null

        return withContext(io) {
            // Таймаут обязателен: чужое приложение может уйти в сеть
            // и не ответить вовсе, а мы не имеем права ждать
            withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                runCatching {
                    directory.availableProviders()
                        .filter { it.id.toString() in enabled }
                        .firstNotNullOfOrNull { provider ->
                            directory.lookup(number, provider.id)?.displayName
                        }
                }.getOrNull()
            }?.also { name ->
                // Проверки на null здесь нет: ?.also вызывается только
                // при непустом значении. Прежняя проверка была всегда
                // истинной и вводила в заблуждение — казалось, что имя
                // может прийти пустым
                cache[number] = name
                Logger.d(TAG, "Внешний определитель дал имя для ${Logger.mask(number)}")
            }
        }
    }

    /**
     * Имена для незнакомых номеров журнала.
     *
     * Вызывается фоном после загрузки списка: спешить некуда,
     * но и заваливать чужое приложение запросами нельзя.
     *
     * @param unknownNumbers номера, которых нет в телефонной книге —
     *        для знакомых имя уже есть, и запрос был бы бессмысленной
     *        передачей данных наружу
     * @return карта «номер → имя» только для тех, что нашлись
     */
    suspend fun lookupBatch(unknownNumbers: List<String>): Map<String, String> {
        if (unknownNumbers.isEmpty()) return emptyMap()
        if (!isEnabled()) return emptyMap()

        val result = mutableMapOf<String, String>()

        // Сначала отдаём то, что уже знаем: без обращений наружу
        unknownNumbers.forEach { number ->
            cache[number]?.let { result[number] = it }
        }

        /*
         * containsKey, а не оператор `in`.
         *
         * У ConcurrentHashMap оператор `in` вызывает унаследованный
         * contains(), который проверяет ЗНАЧЕНИЯ, а не ключи. Проверка
         * молча возвращала бы false для всех номеров, и кэш не работал:
         * запросы уходили бы к определителю повторно для уже известных
         * номеров.
         *
         * Компилятор Kotlin предупреждает об этом, и предупреждение
         * здесь по делу.
         */
        val toQuery = unknownNumbers
            .filterNot { cache.containsKey(it) }
            .take(BATCH_LIMIT)

        toQuery.forEach { number ->
            lookup(number)?.let { result[number] = it }
        }

        if (result.isNotEmpty()) {
            Logger.i(TAG, "Внешний определитель дал имён: ${result.size}")
        }
        return result
    }

    /** Сброс кэша: например, после отключения провайдера. */
    fun clearCache() {
        cache.clear()
        Logger.d(TAG, "Кэш имён внешнего определителя очищен")
    }
}
