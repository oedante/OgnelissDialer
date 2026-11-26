package com.ogneliss.dialer.call

import com.ogneliss.dialer.data.NumberingPlan
import com.ogneliss.dialer.di.ApplicationScope
import com.ogneliss.dialer.settings.NumberFacts
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.settings.SimRule
import com.ogneliss.dialer.settings.SimRules
import com.ogneliss.dialer.util.PhoneFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * С КАКОЙ КАРТЫ ЗВОНИТЬ, КОГДА КАРТУ НЕ ВЫБРАЛИ ЯВНО (2.9.11, «Карта по номеру»).
 *
 * Ogneliss — телефон по умолчанию, поэтому ограничения Android для сторонних
 * «менеджеров SIM» его не касаются: он сам передаёт карту в вызов
 * (EXTRA_PHONE_ACCOUNT_HANDLE) и сам отвечает системе на вопрос «с какой SIM?».
 *
 * Что главнее (сверху вниз):
 *  1. карта, выбранная явно: кнопка «SIM 1/2» на клавиатуре, окно выбора карты,
 *     долгое нажатие, свайп «Позвонить с SIM N» — сюда не доходит;
 *  2. «Карта для звонков и SMS» (2.9.1) — все исходящие с неё, правила не нужны;
 *  3. запомненная для номера карта («Запоминать SIM для номера», DialerViewModel.call) —
 *     выбор человека для этого номера, а маска — для многих;
 *  4. правило «Карты по номеру» ([SimRules.match]): начало номера, регион или оператор
 *     по реестру Россвязи ([NumberingPlan] — тот же, что подписи в журнале);
 *  5. ничего — как раньше: при двух картах — вопрос.
 * Здесь — 2 и 4; 3 решает DialerViewModel.call до вызова CallActions.placeCall.
 *
 * Где действует: CallActions.placeCall без карты (журнал, контакты, карточка,
 * уведомления, напоминания, виджеты), свайп и трубка в строке вместо окна выбора
 * (DialerViewModel.performSwipe), вопрос системы «с какой SIM?» у звонка из другого
 * приложения или по ссылке tel: (OgnelissInCallService.pickAccountByRule).
 */
@Singleton
class SimRouter @Inject constructor(
    private val prefs: Prefs,
    /** Регион и оператор номера — для правил с названием («Краснодар*», «МТС»). */
    private val numberingPlan: NumberingPlan,
    @ApplicationScope scope: CoroutineScope
) {
    /*
     * Копии настроек — для мест, где ждать нельзя (CallActions.placeCall не suspend
     * и зовётся из главного потока). Читаются с запуска приложения; пока не
     * пришли — правил как будто нет, и выбор карты останется системе: тогда
     * ответит служба вызовов, у неё чтение с ожиданием ([slotFor]).
     */
    private val rules = prefs.simRules.stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val outgoing = prefs.defaultSim.stateIn(scope, SharingStarted.Eagerly, -1)

    /*
     * Реестр читается, только если есть правило с названием, и один раз на звонок
     * (SimRules.match). Файл реестра отображён в память и обычно уже открыт —
     * журнал подписывает номера при запуске, поэтому чтение в главном потоке
     * ([cachedSlot]) — обращение по смещению, а не разбор файла.
     */
    private fun facts(number: String): NumberFacts? {
        val entry = numberingPlan.lookup(PhoneFormatter.toE164(number)) ?: return null
        return NumberFacts(
            region = entry.region.takeIf { it.isNotBlank() },
            operator = NumberingPlan.shortOperatorName(entry.operator).takeIf { it.isNotBlank() }
        )
    }

    /** Карта для [number] по копиям настроек; null — решать не нам. */
    fun cachedSlot(number: String): Int? =
        decide(number, outgoing.value, rules.value) { facts(number) }

    /** То же с чтением настроек и реестра вне главного потока (служба вызовов). */
    suspend fun slotFor(number: String): Int? {
        val outgoingSlot = prefs.defaultSim.first()
        val list = prefs.simRules.first()
        return withContext(Dispatchers.Default) { decide(number, outgoingSlot, list) { facts(number) } }
    }

    /** Есть ли правило для [number] — без «Карты для звонков и SMS». */
    fun ruleSlot(number: String): Int? = SimRules.match(number, rules.value) { facts(number) }?.slot

    companion object {
        internal fun decide(
            number: String,
            outgoingSlot: Int,
            rules: List<SimRule>,
            facts: () -> NumberFacts? = { null }
        ): Int? = if (outgoingSlot >= 0) outgoingSlot else SimRules.match(number, rules, facts)?.slot
    }
}
