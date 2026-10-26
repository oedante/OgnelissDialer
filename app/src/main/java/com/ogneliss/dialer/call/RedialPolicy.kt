package com.ogneliss.dialer.call

/*
 * АВТОДОЗВОН: ПРАВИЛА (1.8.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, в списке функций для 1.8.0: «Автодозвон». Сценарий знаком всем:
 * поликлиника, такси, служба доставки, бабушка, которая говорит с соседкой
 * сорок минут. «Занято» — и человек жмёт «Позвонить» снова, и снова,
 * и снова. Системный диалер так не умеет; в отзывах на сторонние диалеры
 * автодозвон — одна из самых частых просьб.
 *
 * ── Что здесь, а что в AutoRedial ──────────────────────────────────────────
 * Здесь — чистые правила без Android: по коду завершения понять, чем
 * кончился вызов, и решить, что делать дальше. Их проверяет
 * RedialPolicyTest. AutoRedial (рядом) держит сеанс, таймер и сам набор.
 *
 * ── Чего автодозвон не делает намеренно ────────────────────────────────────
 * • Не повторяет экстренные и короткие служебные номера (112, 0611, *100#):
 *   повтор экстренного вызова без человека — недопустим, служебные
 *   не бывают «заняты».
 * • Не повторяет, если человек сбросил сам: «Завершить» — это решение.
 * • Не повторяет состоявшийся разговор — даже если ответил автоответчик.
 * • Не трогает входящие: «перезвонить» — это напоминание, оно уже есть.
 */
object RedialPolicy {

    /*
     * КОДЫ android.telecom.DisconnectCause.
     *
     * Числами, а не ссылками на класс: правила проверяются без Android.
     * Совпадение с системными сверяет тест `коды совпадают с системными`.
     * Значения неизменны с Android 6 — это часть открытого API.
     */
    const val CAUSE_UNKNOWN = 0
    const val CAUSE_ERROR = 1
    const val CAUSE_LOCAL = 2
    const val CAUSE_REMOTE = 3
    const val CAUSE_CANCELED = 4
    const val CAUSE_MISSED = 5
    const val CAUSE_REJECTED = 6
    const val CAUSE_BUSY = 7
    const val CAUSE_RESTRICTED = 8
    const val CAUSE_OTHER = 9

    /**
     * Меньше стольких цифр — служебный номер: 112, 103, 0611, 900.
     * Пять — с запасом: у коротких номеров операторов четыре цифры.
     */
    const val MIN_DIGITS = 5

    /** Почему не дозвонились — так и пишется на экране. */
    enum class Reason { BUSY, NO_ANSWER, FAILED }

    /**
     * Где сейчас сеанс автодозвона.
     *
     * OFFER — вызов не прошёл, экран спрашивает «Дозваниваться?»;
     * WAITING — пауза перед следующей попыткой, идёт отсчёт;
     * CALLING — попытка набрана и идёт;
     * GAVE_UP — попытки кончились, экран предлагает «Ещё раз».
     */
    enum class Phase { OFFER, WAITING, CALLING, GAVE_UP }

    /** Чем кончился вызов — с точки зрения автодозвона. */
    enum class Outcome(val reason: Reason?) {
        /** Соединились — дозвонились, повторять нечего. */
        ANSWERED(null),

        /** Сбросил сам человек — его решение, не спорим. */
        USER_ENDED(null),

        /** Входящий, запрет, вызов ушёл на другое устройство и прочее. */
        NOT_ELIGIBLE(null),
        BUSY(Reason.BUSY),
        NO_ANSWER(Reason.NO_ANSWER),
        FAILED(Reason.FAILED)
    }

    /**
     * Чем кончился вызов.
     *
     * ── Разбор кодов ───────────────────────────────────────────────────────
     * • LOCAL, CANCELED — «Завершить» на этом телефоне: сбросил сам.
     * • BUSY — занято. REJECTED у исходящего — собеседник сбросил: для
     *   звонящего это то же «занято», так и показывают сети.
     * • REMOTE, OTHER у несостоявшегося — сеть или собеседник закончили
     *   вызов до ответа: не взяли трубку, абонент недоступен.
     * • ERROR — не прошёл: нет сети, сбой. Через минуту может пройти.
     * • UNKNOWN — не повторяем. Так завершаются вызовы на части прошивок,
     *   в том числе сброшенные самим человеком: повтор после «Завершить»
     *   раздражал бы сильнее, чем помогал.
     * • RESTRICTED (запрет вызовов), MISSED, ANSWERED_ELSEWHERE и прочие —
     *   не про исходящий, который можно повторить.
     */
    fun outcome(outgoing: Boolean, connected: Boolean, cause: Int): Outcome = when {
        !outgoing -> Outcome.NOT_ELIGIBLE
        connected -> Outcome.ANSWERED
        else -> when (cause) {
            CAUSE_LOCAL, CAUSE_CANCELED -> Outcome.USER_ENDED
            CAUSE_BUSY, CAUSE_REJECTED -> Outcome.BUSY
            CAUSE_REMOTE, CAUSE_OTHER -> Outcome.NO_ANSWER
            CAUSE_ERROR -> Outcome.FAILED
            else -> Outcome.NOT_ELIGIBLE
        }
    }

    /**
     * Номер можно повторять без человека.
     *
     * Короткие служебные — нет (см. [MIN_DIGITS]); со звёздочкой, решёткой
     * и паузами — тоже: это запросы оператору и добавочные, а не вызов
     * человеку. Экстренные номера проверяет ещё и система (AutoRedial).
     */
    fun isDialable(number: String): Boolean {
        if (number.any { it == '*' || it == '#' || it == ',' || it == ';' }) return false
        return number.count { it.isDigit() } >= MIN_DIGITS
    }

    /** Что делать после завершения вызова. */
    sealed interface Decision {
        /** Ничего: автодозвон выключен или вызов не тот. */
        data object None : Decision

        /** Закончить сеанс: дозвонились или человек сбросил сам. */
        data object Stop : Decision

        /** Спросить «Дозваниваться?» (режим «Предлагать»). */
        data class Offer(val reason: Reason) : Decision

        /** Ждать паузу и набрать попытку [attempt]. */
        data class Wait(val attempt: Int, val reason: Reason) : Decision

        /** Попытки кончились. */
        data class GiveUp(val reason: Reason) : Decision
    }

    /**
     * Решение после завершения вызова.
     *
     * @param ourAttempt вызов набран автодозвоном: сеанс в фазе CALLING,
     *   номер тот же
     * @param attempt номер этой попытки в сеансе (1…[maxAttempts])
     * @param otherCalls на телефоне остались другие вызовы — удержанный,
     *   второй входящий: человек занят, набирать поверх нельзя
     * @param dialable номер можно повторять ([isDialable] и не экстренный)
     */
    fun afterCall(
        ourAttempt: Boolean,
        attempt: Int,
        maxAttempts: Int,
        outcome: Outcome,
        mode: AutoRedialMode,
        otherCalls: Boolean,
        dialable: Boolean
    ): Decision {
        if (ourAttempt) {
            // Дозвонились или сбросил сам — сеанс окончен
            val reason = outcome.reason ?: return Decision.Stop
            if (otherCalls) return Decision.Stop
            return if (attempt < maxAttempts) Decision.Wait(attempt + 1, reason)
            else Decision.GiveUp(reason)
        }
        if (mode == AutoRedialMode.OFF || otherCalls || !dialable) return Decision.None
        val reason = outcome.reason ?: return Decision.None
        return if (mode == AutoRedialMode.AUTO) Decision.Wait(1, reason) else Decision.Offer(reason)
    }
}

/**
 * НАСТРОЙКА «АВТОДОЗВОН» (Настройки → Вызовы → После звонка).
 *
 * OFFER — по умолчанию: после «занято» экран вызова не закрывается,
 * а спрашивает «Дозваниваться?». Так функцию находят, не открывая
 * настроек, а без согласия ничего не набирается. AUTO — повторять сразу,
 * без вопроса; OFF — как раньше, экран закрывается.
 *
 * Хранится строкой: имена менять нельзя.
 */
enum class AutoRedialMode {
    OFF,
    OFFER,
    AUTO;

    companion object {
        val DEFAULT = OFFER

        fun from(name: String?): AutoRedialMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Сколько раз и как часто повторять.
 *
 * Готовые сочетания, а не два отдельных выбора: «10 попыток каждые
 * 5 секунд» — это сотня гудков в минуту в чужую трубку, такое
 * предлагать не нужно. Пауза короче 10 секунд не предлагается вовсе:
 * сети нужно время освободить линию, и частый повтор оператор может
 * принять за автоматический обзвон.
 */
enum class RedialPace(val attempts: Int, val intervalSec: Int) {
    P3_10(3, 10),
    P5_10(5, 10),
    P5_30(5, 30),
    P10_60(10, 60);

    companion object {
        val DEFAULT = P5_10

        fun from(name: String?): RedialPace = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
