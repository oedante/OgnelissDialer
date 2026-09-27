package com.ogneliss.dialer.data

import com.ogneliss.dialer.data.db.NumberRuleDao
import com.ogneliss.dialer.data.db.NumberRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class RuleKind(val key: String) { BLOCK("block"), ALLOW("allow") }

/**
 * Чёрный и белый списки с поддержкой масок.
 * Белый список ВСЕГДА побеждает — иначе правило «блокировать всех не из контактов»
 * отрежет школу, поликлинику и курьера, что и вызывает жалобы
 * «блокировка режет нужные звонки».
 */
@Singleton
class RulesRepository @Inject constructor(private val dao: NumberRuleDao) : RulesSource {

    /**
     * Все правила в виде моделей.
     *
     * Сущности Room наружу не отдаются: потребителю не нужно знать,
     * что данные лежат в базе, а тестовой заглушке — тащить за собой
     * аннотации Room.
     */
    override val all: Flow<List<NumberRule>> = dao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    val blocked: Flow<List<NumberRuleEntity>> =
        dao.observeAll().map { list -> list.filter { it.kind == RuleKind.BLOCK.key } }

    val allowed: Flow<List<NumberRuleEntity>> =
        dao.observeAll().map { list -> list.filter { it.kind == RuleKind.ALLOW.key } }

    override suspend fun add(pattern: String, kind: RuleKind, comment: String?) =
        dao.upsert(NumberRuleEntity(pattern.trim(), kind.key, comment))

    override suspend fun remove(pattern: String) = dao.delete(pattern)

    /** null — правила не сработали, решение принимает вызывающий код. */
    override suspend fun decide(e164: String): RuleKind? {
        if (dao.byKind(RuleKind.ALLOW.key).any { matches(it.pattern, e164) }) return RuleKind.ALLOW
        if (dao.byKind(RuleKind.BLOCK.key).any { matches(it.pattern, e164) }) return RuleKind.BLOCK
        return null
    }

    /**
     * Маска: '*' — любое продолжение, '?' — один любой символ.
     *
     * Своей копии здесь больше нет: проверка живёт в RulePattern,
     * а тут был её дубль — и дубль был сломан. Он экранировал шаблон
     * целиком, из-за чего маски не совпадали ни с одним номером:
     * правило «8800*» сохранялось, показывалось в списке и молча
     * пропускало все звонки. Разбор — в комментарии к RulePattern.matches.
     *
     * Две копии кода, от которого зависит, зазвонит ли телефон, —
     * это ровно тот случай, когда расхождение никто не заметит.
     */
    private fun matches(pattern: String, e164: String): Boolean =
        RulePattern.matches(pattern, e164)

    /** Сущность Room → модель. Единственное место преобразования. */
    private fun NumberRuleEntity.toModel() = NumberRule(
        pattern = pattern,
        kind = kind,
        comment = comment,
        createdAt = createdAt
    )
}
