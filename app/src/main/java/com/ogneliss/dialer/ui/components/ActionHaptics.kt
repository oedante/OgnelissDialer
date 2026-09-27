package com.ogneliss.dialer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * ОТКЛИК НА ДЕЙСТВИЕ, КОТОРОЕ НЕ ОТМЕНИТЬ ВЗГЛЯДОМ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Блокировка, удаление записи, снятие напоминания происходят мгновенно
 * и молча: список меняется, но человек в этот момент смотрит на палец,
 * а не на экран. Короткая вибрация подтверждает, что нажатие засчитано,
 * не требуя перевести взгляд.
 *
 * Тот же приём уже работает на свайпах по строке (SwipeRevealRow):
 * там вибрация говорит «порог пройден, отпускай». Здесь она говорит
 * «сделано».
 *
 * ── Почему LongPress, а не что-то иное ─────────────────────────────────────
 * Из доступных в Compose откликов LongPress — единственный заметный
 * без включения «сильной вибрации» в системе. TextHandleMove слишком
 * слаб и на половине устройств не ощущается вовсе.
 *
 * ⚠️ Отклик даётся только на необратимое. Если вибрировать на каждое
 * нажатие, он перестаёт что-либо значить — и в шумной обстановке
 * человек уже не отличит подтверждение от случайного дрожания.
 */
@Composable
fun rememberActionHaptics(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
