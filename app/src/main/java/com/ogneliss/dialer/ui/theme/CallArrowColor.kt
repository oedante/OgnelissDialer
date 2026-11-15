package com.ogneliss.dialer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ogneliss.dialer.data.CallType

/**
 * ЦВЕТ СТРЕЛКИ ТИПА ВЫЗОВА (2.7.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЧТО ПРОСИЛИ
 * ══════════════════════════════════════════════════════════════════════════
 * Форум, 15.11.2026, 21vasya: «планируется раскрасить стрелки вызовов
 * цветными. Типа: зелёным исходящий, синим входящий, красным
 * неотвеченный».
 *
 * Было: стрелки серые, красная — только у пропущенного. Приложение «тихое»,
 * поэтому цвет — по желанию: переключатель «Цветные стрелки вызовов»
 * (colored_call_arrows), по умолчанию выключен — тогда всё как было.
 *
 * ── Цвета ──────────────────────────────────────────────────────────────────
 * • исходящий — зелёный: CallGreen (светлая тема) и Green80 (тёмная), та же
 *   пара, что у кнопки «Позвонить» в SimChoiceCards;
 * • входящий — синий: MessageBlue и Blue80, та же пара, что у «SMS» там же;
 * • пропущенный — error темы, как и раньше (в обоих режимах);
 * • отклонённый и заблокированный (значок «Block») и голосовая почта — как
 *   у остальных стрелок без цвета: [neutral].
 * Тёмной теме — светлые оттенки: тёмно-зелёная и тёмно-синяя стрелка
 * на тёмной подложке не читались бы (контраст ниже 3:1).
 *
 * ── Почему не primary ──────────────────────────────────────────────────────
 * Акцент человек меняет сам (в том числе на красный или зелёный) —
 * входящий тогда сливался бы с исходящим или пропущенным.
 *
 * ── Одно место ─────────────────────────────────────────────────────────────
 * Стрелка рисуется в строке журнала, истории абонента, шапке карточек по
 * удержанию, архиве и ленте переписки. Выбор цвета — только здесь:
 * [callArrowColor] (чистая функция, проверяется без Compose) и
 * [callArrowTint] (берёт настройку из LocalAppearance и тему).
 */
internal fun callArrowColor(
    type: CallType,
    colored: Boolean,
    dark: Boolean,
    missedColor: Color,
    neutral: Color
): Color {
    // Пропущенный красный и без настройки — как было
    if (type == CallType.MISSED) return missedColor
    if (!colored) return neutral
    return when (type) {
        CallType.OUTGOING -> if (dark) Green80 else CallGreen
        CallType.INCOMING -> if (dark) Blue80 else MessageBlue
        else -> neutral
    }
}

/**
 * Цвет стрелки для [type]. [neutral] — прежний цвет места (где-то outline,
 * где-то onSurfaceVariant): без настройки стрелка остаётся такой, как была.
 * [missed] — у строки-группы пропущенным считается и не последний вызов
 * (CallLogRow.hasMissed): тогда стрелка красная, каким бы ни был тип.
 */
@Composable
fun callArrowTint(type: CallType, neutral: Color, missed: Boolean = false): Color {
    val scheme = MaterialTheme.colorScheme
    return callArrowColor(
        type = if (missed) CallType.MISSED else type,
        colored = LocalAppearance.current.coloredCallArrows,
        dark = scheme.surface.luminance() < 0.5f,
        missedColor = scheme.error,
        neutral = neutral
    )
}
