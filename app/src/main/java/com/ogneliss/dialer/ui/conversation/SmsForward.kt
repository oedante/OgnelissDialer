package com.ogneliss.dialer.ui.conversation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * ПЕРЕСЫЛКА SMS (2.2.0).
 *
 * «Переслать» в переписке кладёт сюда текст и открывает раздел SMS в режиме
 * «Кому» (тот же выбор получателя, что у «Написать»). Выбрал получателя —
 * переписка с ним открывается с этим текстом в поле, его можно поправить
 * перед отправкой. Закрыл «Кому» — пересылка отменена.
 *
 * Держатель на время процесса, а не аргумент навигации: раздел SMS —
 * общий экран нижней панели без аргументов, и текст не нужен в стеке
 * переходов после выбора.
 */
internal object SmsForward {

    var pending by mutableStateOf<String?>(null)

    /** Взять текст для переписки и забыть: пересылка выполнена. */
    fun take(): String? = pending.also { pending = null }
}
