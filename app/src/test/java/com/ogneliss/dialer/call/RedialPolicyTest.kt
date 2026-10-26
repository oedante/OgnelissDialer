package com.ogneliss.dialer.call

import android.telecom.DisconnectCause
import com.ogneliss.dialer.call.RedialPolicy.Decision
import com.ogneliss.dialer.call.RedialPolicy.Outcome
import com.ogneliss.dialer.call.RedialPolicy.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Автодозвон (1.8.0): чем кончился вызов и что делать дальше.
 * Разбор правил — в RedialPolicy.
 */
class RedialPolicyTest {

    /*
     * Коды из android.telecom — константы времени компиляции: подставляются
     * числами, поэтому сверка работает и в обычных тестах без эмулятора.
     */
    @Test
    fun кодыСовпадаютССистемными() {
        assertEquals(DisconnectCause.UNKNOWN, RedialPolicy.CAUSE_UNKNOWN)
        assertEquals(DisconnectCause.ERROR, RedialPolicy.CAUSE_ERROR)
        assertEquals(DisconnectCause.LOCAL, RedialPolicy.CAUSE_LOCAL)
        assertEquals(DisconnectCause.REMOTE, RedialPolicy.CAUSE_REMOTE)
        assertEquals(DisconnectCause.CANCELED, RedialPolicy.CAUSE_CANCELED)
        assertEquals(DisconnectCause.MISSED, RedialPolicy.CAUSE_MISSED)
        assertEquals(DisconnectCause.REJECTED, RedialPolicy.CAUSE_REJECTED)
        assertEquals(DisconnectCause.BUSY, RedialPolicy.CAUSE_BUSY)
        assertEquals(DisconnectCause.RESTRICTED, RedialPolicy.CAUSE_RESTRICTED)
        assertEquals(DisconnectCause.OTHER, RedialPolicy.CAUSE_OTHER)
    }

    @Test
    fun занятоИНеОтвечаютПовторяются() {
        assertEquals(Outcome.BUSY, RedialPolicy.outcome(true, false, RedialPolicy.CAUSE_BUSY))
        // Собеседник сбросил — звонящему это то же «занято»
        assertEquals(Outcome.BUSY, RedialPolicy.outcome(true, false, RedialPolicy.CAUSE_REJECTED))
        assertEquals(Outcome.NO_ANSWER, RedialPolicy.outcome(true, false, RedialPolicy.CAUSE_REMOTE))
        assertEquals(Outcome.NO_ANSWER, RedialPolicy.outcome(true, false, RedialPolicy.CAUSE_OTHER))
        assertEquals(Outcome.FAILED, RedialPolicy.outcome(true, false, RedialPolicy.CAUSE_ERROR))
    }

    @Test
    fun сбросилСамНеПовторяется() {
        assertEquals(Outcome.USER_ENDED, RedialPolicy.outcome(true, false, RedialPolicy.CAUSE_LOCAL))
        assertEquals(Outcome.USER_ENDED, RedialPolicy.outcome(true, false, RedialPolicy.CAUSE_CANCELED))
        // Неизвестная причина — тоже нет: так кончаются и сброшенные вручную
        assertEquals(Outcome.NOT_ELIGIBLE, RedialPolicy.outcome(true, false, RedialPolicy.CAUSE_UNKNOWN))
        assertEquals(Outcome.NOT_ELIGIBLE, RedialPolicy.outcome(true, false, RedialPolicy.CAUSE_RESTRICTED))
    }

    @Test
    fun состоявшийсяИВходящийНеПовторяются() {
        // Соединились — даже если потом «занято»: разговор был
        assertEquals(Outcome.ANSWERED, RedialPolicy.outcome(true, true, RedialPolicy.CAUSE_REMOTE))
        assertEquals(Outcome.NOT_ELIGIBLE, RedialPolicy.outcome(false, false, RedialPolicy.CAUSE_BUSY))
        assertEquals(Outcome.NOT_ELIGIBLE, RedialPolicy.outcome(false, false, RedialPolicy.CAUSE_MISSED))
    }

    @Test
    fun служебныеНомераНеПовторяются() {
        assertTrue(RedialPolicy.isDialable("+7 900 000-00-00"))
        assertTrue(RedialPolicy.isDialable("84951234567"))
        assertFalse(RedialPolicy.isDialable("112"))
        assertFalse(RedialPolicy.isDialable("0611"))
        assertFalse(RedialPolicy.isDialable("*100#"))
        assertFalse(RedialPolicy.isDialable("+74950000000,123"))
        assertFalse(RedialPolicy.isDialable(""))
    }

    @Test
    fun предлагатьСпрашиваетСразуНабирает() {
        assertEquals(
            Decision.Offer(Reason.BUSY),
            decide(mode = AutoRedialMode.OFFER, outcome = Outcome.BUSY)
        )
        assertEquals(
            Decision.Wait(1, Reason.NO_ANSWER),
            decide(mode = AutoRedialMode.AUTO, outcome = Outcome.NO_ANSWER)
        )
        assertEquals(Decision.None, decide(mode = AutoRedialMode.OFF, outcome = Outcome.BUSY))
    }

    @Test
    fun безПоводаНичегоНеПредлагается() {
        assertEquals(Decision.None, decide(outcome = Outcome.USER_ENDED))
        assertEquals(Decision.None, decide(outcome = Outcome.ANSWERED))
        // Другой вызов на удержании — человек занят
        assertEquals(Decision.None, decide(outcome = Outcome.BUSY, otherCalls = true))
        // Экстренный или служебный
        assertEquals(Decision.None, decide(outcome = Outcome.BUSY, dialable = false))
    }

    @Test
    fun попыткиИдутДоПредела() {
        assertEquals(
            Decision.Wait(3, Reason.BUSY),
            decide(ourAttempt = true, attempt = 2, maxAttempts = 5, outcome = Outcome.BUSY)
        )
        assertEquals(
            Decision.GiveUp(Reason.NO_ANSWER),
            decide(ourAttempt = true, attempt = 5, maxAttempts = 5, outcome = Outcome.NO_ANSWER)
        )
    }

    @Test
    fun попыткаЗаканчиваетСеанс() {
        // Дозвонились
        assertEquals(Decision.Stop, decide(ourAttempt = true, attempt = 2, outcome = Outcome.ANSWERED))
        // Человек сбросил попытку сам
        assertEquals(Decision.Stop, decide(ourAttempt = true, attempt = 2, outcome = Outcome.USER_ENDED))
        // Пока шла попытка, появился другой вызов
        assertEquals(
            Decision.Stop,
            decide(ourAttempt = true, attempt = 2, outcome = Outcome.BUSY, otherCalls = true)
        )
    }

    @Test
    fun сеансИдётИПриВыключеннойНастройке() {
        // Настройку выключили посреди сеанса — начатый доводится до конца:
        // человек сам нажал «Дозваниваться»
        assertEquals(
            Decision.Wait(2, Reason.BUSY),
            decide(ourAttempt = true, attempt = 1, mode = AutoRedialMode.OFF, outcome = Outcome.BUSY)
        )
    }

    @Test
    fun темпПоУмолчаниюИРазбор() {
        assertEquals(RedialPace.P5_10, RedialPace.DEFAULT)
        assertEquals(5, RedialPace.DEFAULT.attempts)
        assertEquals(10, RedialPace.DEFAULT.intervalSec)
        assertEquals(RedialPace.P10_60, RedialPace.from("P10_60"))
        assertEquals(RedialPace.DEFAULT, RedialPace.from("мусор"))
        assertEquals(AutoRedialMode.OFFER, AutoRedialMode.from(null))
        assertEquals(AutoRedialMode.AUTO, AutoRedialMode.from("AUTO"))
        // Пауза короче 10 секунд не предлагается
        assertTrue(RedialPace.entries.all { it.intervalSec >= 10 })
    }

    private fun decide(
        ourAttempt: Boolean = false,
        attempt: Int = 0,
        maxAttempts: Int = 5,
        outcome: Outcome,
        mode: AutoRedialMode = AutoRedialMode.OFFER,
        otherCalls: Boolean = false,
        dialable: Boolean = true
    ) = RedialPolicy.afterCall(ourAttempt, attempt, maxAttempts, outcome, mode, otherCalls, dialable)
}
