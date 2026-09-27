package com.ogneliss.dialer.ui.components.swipe

import android.content.Context
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.SettingsCommands
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.util.NumberDisplay

/**
 * ЛОКАЛИЗОВАННЫЕ ПОДПИСИ ДЛЯ ПАНЕЛИ СВАЙПА.
 *
 * ── Почему отдельным классом ───────────────────────────────────────────────
 * `RevealActionFactory` собирает кнопки и намеренно не зависит ни от
 * Android, ни от ресурсов — иначе её нельзя было бы вызвать из теста.
 * Строки она получает через интерфейс, а реализация живёт здесь.
 *
 * Раньше эта реализация была анонимным объектом внутри ViewModel: около
 * сорока строк сопоставления действий с ресурсами посреди кода,
 * управляющего вызовами.
 *
 * ── Про соответствие действия и подписи ────────────────────────────────────
 * Оно берётся из `SettingsCommands.swipeActionLabelRes` — того же места,
 * что использует экран настроек. Две копии такой таблицы неизбежно
 * разошлись бы: в настройках действие называлось бы одним словом,
 * на кнопке свайпа другим.
 */
class AndroidRevealStrings(private val context: Context) : RevealActionFactory.Strings {

    /*
     * ОКОНЧАНИЕ НОМЕРА УБРАНО С ПОДПИСЕЙ.
     *
     * Куда пойдёт вызов, видно из строки, по которой сделан свайп.
     * Дублировать цифры на узкой кнопке — тратить место на сведения,
     * которые человек уже прочитал.
     *
     * Исключение обрабатывается в RevealActionFactory: когда у абонента
     * несколько номеров, кнопки действительно нужно различать.
     *
     * Прежний вариант оставлен в комментарии на случай возврата.
     */
    override fun callTo(number: String): String =
        context.getString(R.string.action_call)
    //  context.getString(R.string.action_call) + " " + NumberDisplay.lastFour(number)

    override fun callWithSim(number: String, simLabel: String): String =
        context.getString(R.string.a11y_call_with_sim_only, simLabel)
    //  context.getString(R.string.a11y_call_with_sim, simLabel,
    //      NumberDisplay.lastFour(number))

    override fun smsTo(number: String): String =
        context.getString(R.string.action_sms)
    //  context.getString(R.string.action_sms) + " " + NumberDisplay.lastFour(number)

    override fun messengerTo(number: String): String =
        context.getString(R.string.action_whatsapp)
    //  context.getString(R.string.action_whatsapp) + " " + NumberDisplay.lastFour(number)

    // Единый источник подписей действий: у настроек и панели свайпа
    // они обязаны совпадать
    override fun actionLabel(action: SwipeAction): String =
        context.getString(SettingsCommands.swipeActionLabelRes(action))
}
