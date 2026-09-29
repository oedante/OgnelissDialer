package com.ogneliss.dialer.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard
import com.ogneliss.dialer.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * РАБОТА С SIM-КАРТАМИ.
 *
 * ── Идентификация по СЛОТУ, а не по номеру ─────────────────────────────────
 * Номер телефона SIM-карты в Android часто недоступен: оператор может
 * не записать его в карту, а READ_PHONE_NUMBERS вернёт пустую строку.
 * Полагаться на номер как на идентификатор нельзя.
 *
 * Слот же есть всегда: первый и второй физический разъём (или eSIM-профиль).
 * Все предпочтения, бейджи и запомненные выборы привязаны к ИНДЕКСУ СЛОТА.
 *
 * ── Как называть карты ─────────────────────────────────────────────────────
 * Приоритет подписи:
 *  1. Имя, заданное пользователем в наших настройках («Рабочая», «Личная»)
 *  2. Имя из системы (обычно название оператора)
 *  3. Запасной вариант «SIM 1» / «SIM 2»
 *
 * Пользовательское имя первое не случайно: две карты одного оператора
 * дают две одинаковые системные подписи «MTS», которые ничего не различают.
 */
@Singleton
class SimRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : SimInfoSource {

    private companion object { const val TAG = "Sim" }

    /**
     * Пользовательские имена слотов, подставляются извне из настроек.
     * @Volatile обязателен: пишется из корутины настроек, читается
     * из потока телефонии при входящем вызове.
     */
    @Volatile
    private var customLabels: Map<Int, String> = emptyMap()

    /**
     * КЭШ СПИСКА SIM.
     *
     * Обращения к SubscriptionManager и TelecomManager — это binder-вызовы
     * в системный процесс. Каждый занимает единицы миллисекунд, но их легко
     * сделать сотни: список SIM нужен и при отрисовке каждой строки журнала,
     * и при сборке кнопок свайпа.
     *
     * Кэш живёт до явного сброса. Сбрасывается при смене состояния SIM
     * и при изменении пользовательских имён.
     */
    @Volatile
    private var cachedSlots: List<SimSlot>? = null
    private val cacheMutex = Mutex()

    fun setCustomLabels(labels: Map<Int, String>) {
        customLabels = labels
        invalidateCache()
    }

    fun invalidateCache() {
        cachedSlots = null
    }

    /**
     * Асинхронное получение списка SIM с кэшированием.
     * Mutex гарантирует, что при одновременном обращении из нескольких
     * корутин запрос к системе выполнится один раз, а не столько,
     * сколько было вызывающих.
     */
    override suspend fun availableSlots(): List<SimSlot> = slotsAsync()

    override fun labelForAccount(accountId: String?): String? = labelFor(accountId)

    suspend fun slotsAsync(): List<SimSlot> {
        cachedSlots?.let { return it }
        return cacheMutex.withLock {
            cachedSlots ?: withContext(io) { slots() }.also { result ->
                /*
                 * ПУСТОЙ ОТВЕТ НЕ КЭШИРУЕТСЯ.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ПОЧЕМУ КНОПКИ SIM ПРОПАДАЛИ ПРИ ЗАПУСКЕ
                 * ══════════════════════════════════════════════════════════
                 * Сразу после старта процесса SubscriptionManager иногда
                 * отдаёт пустой список: телефонная служба ещё не успела
                 * подняться. Карты при этом на месте, ответ придёт через
                 * доли секунды.
                 *
                 * Прежний код запоминал этот пустой список как результат
                 * и отдавал его всем, пока что-нибудь не сбросит кэш.
                 * Под клавиатурой вместо «SIM 1» и «SIM 2» оказывалась
                 * одна серая кнопка — ровно то, что видно на снимке
                 * экрана от человека.
                 *
                 * Восстанавливалось это само, но не сразу: подписка
                 * на смену карт (startWatching) присылала событие,
                 * кэш сбрасывался, и список перечитывался.
                 *
                 * Пустой ответ бывает и настоящим — на планшете без
                 * модема. Цена ошибки несимметрична: лишнее чтение
                 * у такого устройства стоит миллисекунды, а неверно
                 * закэшированная пустота ломает выбор карты до конца
                 * сеанса.
                 */
                if (result.isNotEmpty()) cachedSlots = result
            }
        }
    }

    /**
     * Чтение списка SIM.
     *
     * ── Почему private ─────────────────────────────────────────────────────
     * Метод синхронный и делает binder-запросы в системный процесс.
     * Пока он был публичным, оставалась готовая ловушка: рано или поздно
     * его вызвали бы из композиции, и запрос ушёл бы в главный поток.
     *
     * Снаружи доступен только slotsAsync(), который выполняется
     * в фоновом контексте и кэширует результат.
     */
    @SuppressLint("MissingPermission")
    private fun slots(): List<SimSlot> {
        cachedSlots?.let { return it }
        // Без READ_PHONE_STATE система не отдаёт список карт, и приложение
        // считает, что SIM одна. Отсюда пропадали кнопки выбора карты
        if (!PermissionGuard.canReadPhoneState(context)) {
            PermissionGuard.logDenied("чтение списка SIM", "READ_PHONE_STATE")
            return emptyList()
        }

        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()

        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty()
                // Порядок по слоту: первый разъём всегда первый в списке
                .sortedBy { it.simSlotIndex }
                .map { info ->
                    val slot = info.simSlotIndex
                    SimSlot(
                        subscriptionId = info.subscriptionId,
                        slotIndex = slot,
                        label = labelForSlot(slot, info.displayName?.toString()),
                        /*
                         * Номер SIM-карты — двумя путями.
                         *
                         * С Android 13 номер отдаёт SubscriptionManager
                         * .getPhoneNumber: он знает не только номер,
                         * записанный на карту, но и полученный от сети
                         * и заданный оператором. Нужно разрешение
                         * READ_PHONE_NUMBERS — оно у нас запрашивается
                         * вместе с остальными телефонными (AppPermissions,
                         * группа «phone»). Прежний комментарий здесь
                         * утверждал, что разрешения нет, — это устарело.
                         *
                         * На старых версиях и без разрешения — прежний
                         * SubscriptionInfo.number.
                         *
                         * Номера может не быть ни там, ни там: российские
                         * операторы записывают его на карту редко. Тогда
                         * человеку поможет оператор и системное имя —
                         * их показывает окно названий SIM.
                         */
                        number = readNumber(sm, info),
                        carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() },
                        systemName = info.displayName?.toString()?.takeIf { it.isNotBlank() },
                        // isEmbedded — с Android 9; на 8 eSIM в телефонах не было
                        embedded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.isEmbedded
                    )
                }
        }.getOrElse {
            Logger.w(TAG, "Не удалось прочитать список SIM", it)
            emptyList()
        }.also { cachedSlots = it }
    }

    /**
     * ИДЕНТИФИКАТОРЫ ПОДПИСОК С ГОЛОСОВОЙ СВЯЗЬЮ.
     *
     * Активная карта не обязательно умеет звонить. Тарифы для интернета
     * и eSIM-профили операторов часто идут без голоса: подписка в системе
     * есть, а голосового аккаунта в телефонии нет.
     *
     * Кнопка вызова для такой карты обещала бы то, чего не будет:
     * звонок не пройдёт, а пользователь не поймёт причину.
     *
     * Telecom отдаёт только те аккаунты, что реально способны на вызов,
     * поэтому сверяемся с ними, а не с одним лишь списком подписок.
     */
    fun voiceCapableSubscriptionIds(): Set<Int> = runCatching {
        val accountIds = phoneAccounts().mapNotNull { it.id }.toSet()
        slots()
            .filter { slot -> slot.subscriptionId.toString() in accountIds }
            .map { it.subscriptionId }
            .toSet()
            // Запасной путь: на части прошивок id аккаунта не совпадает
            // с subscriptionId. Тогда считаем голосовыми все карты —
            // лучше показать лишнюю кнопку, чем скрыть рабочую
            .ifEmpty { slots().map { it.subscriptionId }.toSet() }
    }.getOrElse {
        Logger.w(TAG, "Не удалось определить голосовые аккаунты", it)
        slots().map { it.subscriptionId }.toSet()
    }

    /**
     * ОТСЛЕЖИВАНИЕ СМЕНЫ SIM.
     *
     * Карту вынимают, вставляют и отключают в настройках при работающем
     * приложении. Без подписки список оставался бы в кэше прежним:
     * кнопка звонила бы с несуществующей карты, а новая не появлялась
     * до перезапуска.
     */
    fun startWatching(onChanged: () -> Unit) {
        if (!PermissionGuard.canReadPhoneState(context)) return
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return

        runCatching {
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                /*
                 * ПЕРВЫЙ ВЫЗОВ — НЕ СМЕНА КАРТ.
                 *
                 * ══════════════════════════════════════════════════════
                 * НАЙДЕНО ПО ЖУРНАЛУ ПРИЛОЖЕНИЯ
                 * ══════════════════════════════════════════════════════
                 * «Состав SIM изменился» писалось при КАЖДОМ запуске,
                 * через 20 мс после старта. Карты при этом никто
                 * не трогал: Android вызывает onSubscriptionsChanged
                 * сразу при регистрации слушателя — так устроен этот API,
                 * он сообщает текущее состояние.
                 *
                 * Раньше это стоило лишнего обновления журнала на старте.
                 * С 1.1.1 обходилось дороже: после события запускается
                 * серия повторных чтений (через 2 и через 6 секунд), то есть
                 * три лишних обновления на каждом запуске.
                 *
                 * Первый вызов пропускаем: список карт в этот момент
                 * и так читается с нуля — кэш на старте пуст.
                 */
                private var first = true

                override fun onSubscriptionsChanged() {
                    if (first) {
                        first = false
                        return
                    }
                    Logger.i(TAG, "Состав SIM изменился, сбрасываю кэш")
                    invalidateCache()
                    onChanged()
                }
            }
            /*
             * Executor собирается вручную, а не берётся из context.
             *
             * Context.getMainExecutor() появился в API 28, а minSdk у нас 26:
             * на Android 8.0 и 8.1 обращение к нему даёт NoSuchMethodError
             * прямо при выдаче разрешения на телефон — то есть в момент,
             * когда приложение только начинает работать.
             */
            val mainExecutor = java.util.concurrent.Executor { command ->
                android.os.Handler(android.os.Looper.getMainLooper()).post(command)
            }
            /*
             * ДВА ПУТИ ПОДПИСКИ.
             *
             * Вариант с Executor появился только в API 30, а minSdk у нас 26.
             * Раньше вызывался он один, без проверки версии: на Android
             * 8–10 подписка молча падала внутрь runCatching, и смена
             * SIM-карты переставала замечаться — кнопка вызова могла
             * звонить с вынутой карты до перезапуска приложения.
             *
             * lint это и показывал: «Call requires API level 30».
             *
             * Старый вариант требует Looper на вызывающем потоке,
             * поэтому регистрируем его через главный поток явно.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sm.addOnSubscriptionsChangedListener(mainExecutor, listener)
            } else {
                mainExecutor.execute {
                    @Suppress("DEPRECATION")
                    sm.addOnSubscriptionsChangedListener(listener)
                }
            }
            subscriptionListener = listener
        }.onFailure { Logger.w(TAG, "Не удалось подписаться на смену SIM", it) }
    }

    fun stopWatching() {
        runCatching {
            subscriptionListener?.let {
                context.getSystemService(SubscriptionManager::class.java)
                    ?.removeOnSubscriptionsChangedListener(it)
            }
        }
        subscriptionListener = null
    }

    private var subscriptionListener:
        SubscriptionManager.OnSubscriptionsChangedListener? = null

    /** Подпись слота с учётом пользовательского имени. */
    /** Номер карты: новым путём на Android 13+, иначе прежним. Разбор — у вызова. */
    @SuppressLint("MissingPermission")
    private fun readNumber(
        sm: SubscriptionManager,
        info: android.telephony.SubscriptionInfo
    ): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_PHONE_NUMBERS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { sm.getPhoneNumber(info.subscriptionId) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return runCatching { @Suppress("DEPRECATION") info.number }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun labelForSlot(slotIndex: Int, systemName: String? = null): String =
        customLabels[slotIndex]?.takeIf { it.isNotBlank() }
            ?: systemName?.takeIf { it.isNotBlank() }
            ?: "SIM ${slotIndex + 1}"

    @SuppressLint("MissingPermission")
    fun phoneAccounts(): List<PhoneAccountHandle> {
        /*
         * ПРОВЕРКА РАЗРЕШЕНИЯ ДО ОБРАЩЕНИЯ К СИСТЕМЕ.
         *
         * ── Зачем ──────────────────────────────────────────────────────────
         * Без неё вызов уходил в систему, получал SecurityException,
         * и тот писался в лог со стеком на пятнадцать строк — при каждом
         * запуске до выдачи разрешений.
         *
         * Отсутствие разрешения — не ошибка: человек его ещё не дал,
         * и это штатное состояние первого запуска. Ошибкой в логе
         * оно выглядит зря: при разборе настоящего сбоя такие записи
         * приходится отсеивать глазами.
         *
         * Исключение при этом ловится по-прежнему: разрешение могут
         * отозвать между проверкой и вызовом.
         */
        if (!PermissionGuard.canReadPhoneState(context)) return emptyList()

        val tm = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return runCatching { tm.callCapablePhoneAccounts }.getOrElse {
            Logger.w(TAG, "Не удалось получить телефонные аккаунты", it)
            emptyList()
        }
    }

    /** Аккаунт телефонии для указанного слота. */
    fun accountForSlot(slotIndex: Int): PhoneAccountHandle? {
        val accounts = phoneAccounts()
        // Порядок аккаунтов обычно совпадает с порядком слотов, но полагаться
        // на это нельзя: сопоставляем через subscriptionId, где возможно
        val slot = slots().firstOrNull { it.slotIndex == slotIndex } ?: return null
        return accounts.firstOrNull { it.id == slot.subscriptionId.toString() }
            ?: accounts.getOrNull(slotIndex)
    }

    /**
     * Индекс слота по идентификатору аккаунта из журнала вызовов.
     * Возвращает null, если карта одна — тогда бейдж SIM показывать не нужно.
     */
    fun slotForAccountId(accountId: String?): Int? {
        if (accountId == null) return null
        val slotList = slots()
        if (slotList.size <= 1) return null

        slotList.firstOrNull { it.subscriptionId.toString() == accountId }
            ?.let { return it.slotIndex }

        // Запасной путь: позиция аккаунта в списке
        val idx = phoneAccounts().indexOfFirst { it.id == accountId }
        return idx.takeIf { it >= 0 }
    }

    /** Человекочитаемая подпись SIM для записи журнала. */
    fun labelFor(accountId: String?): String? =
        slotForAccountId(accountId)?.let { labelForSlot(it) }
}
