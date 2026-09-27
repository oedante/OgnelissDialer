package com.ogneliss.dialer.ui.components.swipe

import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ogneliss.dialer.ui.theme.ReminderAmber
import com.ogneliss.dialer.ui.theme.MaxBlue
import com.ogneliss.dialer.ui.theme.ViberPurple
import com.ogneliss.dialer.ui.theme.TelegramBlue
import com.ogneliss.dialer.ui.theme.WhatsAppGreen
import com.ogneliss.dialer.ui.theme.NeutralGrey
import com.ogneliss.dialer.ui.theme.MessageBlue
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.util.NumberDisplay

/**
 * СБОРКА КНОПОК ДЛЯ ПАНЕЛИ СВАЙПА.
 *
 * Назначение: превращает настроенное действие и данные абонента
 * в конкретный набор кнопок.
 *
 * ── Цвета ──────────────────────────────────────────────────────────────────
 * Цвет кодирует смысл действия, чтобы его можно было опознать боковым
 * зрением, не читая подпись:
 *   зелёный  — вызов
 *   синий    — сообщение
 *   красный  — разрушительное (удалить, заблокировать)
 *   серый    — нейтральное
 *
 * Все цвета проверены на контраст с белым текстом.
 */
object RevealActionFactory {

    /*
     * ЦВЕТА ДЕЙСТВИЙ.
     *
     * Мессенджеры получают фирменные оттенки: их узнают по цвету
     * быстрее, чем по значку, особенно боковым зрением во время жеста.
     *
     * Зелёный остаётся только у вызова — это код телефонии, и красить
     * им отправку сообщения значило бы обещать звонок.
     *
     * Оттенки затемнены относительно официальных: на них ложится белая
     * пиктограмма, и фирменный зелёный WhatsApp (#25D366) давал бы
     * с ней контраст около 2:1 — значок терялся бы на фоне.
     *
     * Проверено расчётом: все дают не меньше 4.5:1 с белым. Для значков
     * хватило бы и 3:1 по WCAG, но жест делают быстро, часто на ходу,
     * и запас здесь не лишний.
     */
    // CallGreen вынесен в ui/theme/Color.kt: тот же цвет нужен
    // кнопке «Позвонить» в карточке абонента
    // DeclineRed из темы: тот же красный, что у кнопок отклонения
    // и завершения на экране вызова
    private val DestructiveRed = com.ogneliss.dialer.ui.theme.DeclineRed

    /**
     * @param labels подписи номеров из телефонной книги, в порядке numbers
     * @param onCall (номер, слот SIM)
     */
    /**
     * Действие только для вида: пиктограмма и цвет, без обработчика.
     *
     * Нужно подсказке прямого свайпа — она показывает, что произойдёт,
     * но сама ничего не выполняет. Берётся из того же места, что
     * и кнопки панели: иначе подсказка обещала бы одно, а срабатывало
     * другое.
     */
    fun appearanceOnly(action: SwipeAction, strings: Strings): RevealAction {
        val (icon, color) = visuals(action)
        return RevealAction(
            label = "",
            description = strings.actionLabel(action),
            color = color,
            icon = icon,
            onClick = {}
        )
    }

    /**
     * ОДНО ДЕЙСТВИЕ НА СВАЙП.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ПАНЕЛЬ КНОПОК УБРАНА
     * ══════════════════════════════════════════════════════════════════════
     * Раньше при двух SIM или нескольких номерах свайп раскрывал панель
     * с двумя-тремя кнопками. Это ломало сам смысл жеста: свайп ценен
     * тем, что делает действие одним движением, а панель возвращала
     * прицеливание — и в разных строках оно выглядело по-разному:
     * где-то две трубки, где-то две иконки SMS, где-то одна.
     *
     * Теперь всегда одна иконка и одно действие. Неоднозначности решаются
     * иначе:
     *
     * • НЕСКОЛЬКО НОМЕРОВ у абонента — берём номер ПОСЛЕДНЕГО вызова.
     *   Именно на него человек и рассчитывает: свайп по записи журнала
     *   продолжает разговор, который в ней записан.
     *
     * • НЕСКОЛЬКО SIM — по настройке: спросить диалогом или действовать
     *   заранее выбранной картой.
     * ══════════════════════════════════════════════════════════════════════
     */
    fun singleAction(
        action: SwipeAction,
        strings: Strings,
        onPerform: () -> Unit
    ): RevealAction {
        val (icon, color) = visuals(action)
        return RevealAction(
            label = "",
            description = strings.actionLabel(action),
            color = color,
            icon = icon,
            onClick = onPerform
        )
    }

    fun build(
        action: SwipeAction,
        numbers: List<String>,
        labels: List<String?>,
        sims: List<SimSlot>,
        strings: Strings,
        onCall: (String, Int) -> Unit,
        onSms: (String) -> Unit,
        onMessenger: (String) -> Unit,
        onSimple: (SwipeAction) -> Unit
    ): List<RevealAction> {
        val allLabels = labels.filterNotNull()

        return when (action) {
            SwipeAction.CALL -> buildCallActions(
                numbers, labels, allLabels, sims, strings, onCall
            )

            SwipeAction.SMS -> numbers.mapIndexed { i, number ->
                RevealAction(
                    label = chip(number, labels.getOrNull(i), allLabels),
                    description = strings.smsTo(number),
                    color = MessageBlue,
                    icon = Icons.AutoMirrored.Filled.Message,
                    onClick = { onSms(number) }
                )
            }

            SwipeAction.WHATSAPP, SwipeAction.TELEGRAM,
            SwipeAction.VIBER, SwipeAction.MAX ->
                numbers.mapIndexed { i, number ->
                    RevealAction(
                        label = chip(number, labels.getOrNull(i), allLabels),
                        description = strings.messengerTo(number),
                        color = MessageBlue,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        onClick = { onMessenger(number) }
                    )
                }

            // Действия, не зависящие от номера — всегда одна кнопка
            else -> listOf(simpleAction(action, strings, onSimple))
        }
    }

    /**
     * Кнопки вызова. При двух SIM каждый номер даёт две кнопки —
     * иначе выбор карты потребовал бы отдельного шага.
     */
    /**
     * ПОДПИСИ КНОПОК ВЫЗОВА В ПАНЕЛИ СВАЙПА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЧТО ИМЕННО ПИСАТЬ НА КНОПКЕ
     * ══════════════════════════════════════════════════════════════════════
     * Раньше на кнопку попадал номер абонента: он длинный, на узкой кнопке
     * не помещается и не отвечает на вопрос, что произойдёт при нажатии —
     * куда звонить, и так понятно из строки, по которой сделан свайп.
     *
     * ОДНА АКТИВНАЯ SIM — подпись не нужна вовсе. Выбирать не из чего,
     * и любой текст рядом с трубкой только отвлекает. Остаётся
     * пиктограмма.
     *
     * НЕСКОЛЬКО SIM — номер слота: «1» и «2». Коротко, помещается
     * на кнопке любой ширины и однозначно.
     *
     * Названия карт («Билайн», «рабочая») показываются на кнопках вызова
     * внизу экрана, где для них есть место. Дублировать их в свайпе
     * не нужно: человек уже знает, какая карта первая, а какая вторая —
     * он сам их подписал в настройках.
     * ══════════════════════════════════════════════════════════════════════
     */
    private fun buildCallActions(
        numbers: List<String>,
        labels: List<String?>,
        allLabels: List<String>,
        sims: List<SimSlot>,
        strings: Strings,
        onCall: (String, Int) -> Unit
    ): List<RevealAction> = buildList {
        numbers.forEachIndexed { i, number ->
            val chipLabel = chip(number, labels.getOrNull(i), allLabels)
            if (sims.size > 1) {
                sims.forEachIndexed { slot, sim ->
                    add(
                        RevealAction(
                            label = chipLabel,
                            description = strings.callWithSim(number, sim.label),
                            color = CallGreen,
                            icon = Icons.Default.Call,
                            simBadge = slot + 1,
                            onClick = { onCall(number, slot) }
                        )
                    )
                }
            } else {
                add(
                    RevealAction(
                        label = chipLabel,
                        description = strings.callTo(number),
                        color = CallGreen,
                        icon = Icons.Default.Call,
                        onClick = { onCall(number, -1) }
                    )
                )
            }
        }
    }

    private fun simpleAction(
        action: SwipeAction,
        strings: Strings,
        onSimple: (SwipeAction) -> Unit
    ): RevealAction {
        val (icon, color) = visuals(action)
        return RevealAction(
            /*
             * На кнопке — НАЗВАНИЕ ДЕЙСТВИЯ, а при выборе SIM — подпись
             * карты. Раньше сюда попадал номер абонента: он длинный,
             * не помещался на узкой кнопке и не отвечал на вопрос,
             * что произойдёт при нажатии.
             */
            label = strings.actionLabel(action),
            description = strings.actionLabel(action),
            color = color,
            icon = icon,
            onClick = { onSimple(action) }
        )
    }

    /**
     * ПИКТОГРАММА И ЦВЕТ ДЕЙСТВИЯ.
     *
     * ── Почему перечислены ВСЕ значения ────────────────────────────────────
     * Раньше здесь была ветка `else -> Call`, и в неё проваливались
     * SMS, все мессенджеры, почта и копирование — то есть больше
     * половины действий показывали телефонную трубку независимо
     * от того, что выбрано в настройках.
     *
     * Ветка `else` в таком месте вредна вдвойне: она не только даёт
     * неверный результат, но и скрывает добавление нового действия —
     * компилятор молчит, а иконка оказывается чужой.
     *
     * ── Про цвета ──────────────────────────────────────────────────────────
     * Зелёный только у вызова: это код телефонии, и красить им отправку
     * сообщения значило бы обещать звонок. Мессенджеры получают
     * фирменные оттенки — их узнают по цвету быстрее, чем по значку.
     * Разрушительные действия красные.
     */
    private fun visuals(action: SwipeAction): Pair<ImageVector, Color> = when (action) {
        // Вызов
        SwipeAction.CALL -> Icons.Default.Call to CallGreen

        // Сообщения
        SwipeAction.SMS -> Icons.AutoMirrored.Filled.Message to MessageBlue
        SwipeAction.WHATSAPP -> Icons.AutoMirrored.Filled.Chat to WhatsAppGreen
        SwipeAction.TELEGRAM -> Icons.AutoMirrored.Filled.Send to TelegramBlue
        SwipeAction.VIBER -> Icons.Default.Forum to ViberPurple
        SwipeAction.MAX -> Icons.Default.Textsms to MaxBlue
        SwipeAction.EMAIL -> Icons.Default.Email to NeutralGrey

        // Работа с записью
        SwipeAction.DELETE_FROM_LOG -> Icons.Default.Delete to DestructiveRed
        SwipeAction.BLOCK_NUMBER -> Icons.Default.Block to DestructiveRed
        SwipeAction.VIEW_CONTACT -> Icons.Default.Person to NeutralGrey
        SwipeAction.REMIND_CALLBACK -> Icons.Default.Alarm to ReminderAmber
        SwipeAction.ADD_NOTE -> Icons.Default.EditNote to NeutralGrey
        SwipeAction.EDIT_BEFORE_CALL -> Icons.Default.Edit to NeutralGrey
        SwipeAction.SHARE_NUMBER -> Icons.Default.Share to NeutralGrey
        SwipeAction.COPY_NUMBER -> Icons.Default.ContentCopy to NeutralGrey

        // Действие не выбрано: иконки нет, но цвет нужен для фона
        SwipeAction.NONE -> Icons.Default.Block to NeutralGrey
    }

    private fun chip(number: String, label: String?, allLabels: List<String>) =
        NumberDisplay.chipLabel(number, label, allLabels)

    /**
     * Строки передаются извне, а не читаются здесь: фабрика не должна
     * зависеть от Compose-контекста, иначе её нельзя вызвать из теста.
     */
    interface Strings {
        fun callTo(number: String): String
        fun callWithSim(number: String, simLabel: String): String
        fun smsTo(number: String): String
        fun messengerTo(number: String): String
        fun actionLabel(action: SwipeAction): String
    }
}
