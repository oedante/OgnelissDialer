package com.ogneliss.dialer.data

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NumberStats(
    val totalCount: Int = 0,
    val answeredCount: Int = 0,
    val rejectedCount: Int = 0,
    val maxDurationSec: Long = 0
)

/**
 * Агрегаты по журналу вызовов, считаются в памяти при загрузке журнала.
 * Питают и офлайн-определитель, и экран статистики.
 */
@Singleton
class CallStatsRepository @Inject constructor() {

    private val cache = MutableStateFlow<Map<String, NumberStats>>(emptyMap())

    fun rebuild(log: List<CallLogGroup>) {
        val acc = HashMap<String, NumberStats>()
        log.forEach { g ->
            val cur = acc[g.e164] ?: NumberStats()
            acc[g.e164] = cur.copy(
                totalCount = cur.totalCount + g.count,
                answeredCount = cur.answeredCount +
                    if (g.durationSec > 0) g.count else 0,
                rejectedCount = cur.rejectedCount +
                    if (g.lastType == CallType.REJECTED || g.lastType == CallType.BLOCKED) g.count else 0,
                maxDurationSec = maxOf(cur.maxDurationSec, g.durationSec)
            )
        }
        cache.value = acc
    }

    fun statsFor(e164: String): NumberStats = cache.value[e164] ?: NumberStats()

    /** Топ абонентов — для экрана статистики и подсказок быстрого набора. */
    fun topContacts(limit: Int = 10): List<Pair<String, NumberStats>> =
        cache.value.entries.sortedByDescending { it.value.totalCount }
            .take(limit).map { it.key to it.value }
}
