package com.ogneliss.dialer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R

/**
 * ЗАГЛУШКА СПИСКА НА ВРЕМЯ ПЕРВОЙ ЗАГРУЗКИ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ, ЕСЛИ ЕСТЬ КРУТЯЩИЙСЯ ИНДИКАТОР
 * ══════════════════════════════════════════════════════════════════════════
 * До этого при открытии приложения был пустой экран: журнал читается
 * из провайдера, и первые сотни миллисекунд показывать нечего.
 *
 * Пустота читается как «звонков нет» — то есть как результат, а не как
 * ожидание. Человек успевает сделать вывод раньше, чем данные придут.
 *
 * Заглушка показывает СТРУКТУРУ будущего списка: сразу видно, что здесь
 * будут строки, и сколько их примерно. Экран не прыгает при появлении
 * данных, потому что разметка уже той же формы.
 *
 * ── Почему не крутящийся индикатор ─────────────────────────────────────────
 * Он сообщает «идёт загрузка», но не говорит, чего ждать. И при быстрой
 * загрузке успевает мелькнуть — а мелькание раздражает сильнее,
 * чем короткая пауза.
 *
 * Заглушка формой совпадает с результатом, поэтому переход к данным
 * незаметен: строки просто наполняются содержимым.
 *
 * ── Про мерцание ───────────────────────────────────────────────────────────
 * Плавное изменение прозрачности отличает заглушку от настоящего
 * содержимого: статичные серые полосы читались бы как сломанная вёрстка.
 * Период полторы секунды — медленно, чтобы не отвлекать.
 */
@Composable
fun CallLogSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 8
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.SLOW_MS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val loadingLabel = stringResource(R.string.a11y_loading)

    Column(
        modifier
            .fillMaxWidth()
            // Для TalkBack заглушка — один элемент «загрузка»:
            // восемь пустых строк он перечислял бы по очереди
            .semantics(mergeDescendants = true) { contentDescription = loadingLabel }
    ) {
        repeat(rows) {
            SkeletonRow(alpha)
        }
    }
}

@Composable
private fun SkeletonRow(alpha: Float) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.25f)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кружок на месте аватара
        Box(
            Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(color)
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            // Полоса имени: разной ширины у соседних строк, иначе
            // заглушка выглядит как таблица, а не как список людей
            Box(
                Modifier
                    .fillMaxWidth(0.45f)
                    .height(16.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(color)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.3f)
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(color)
            )
        }
    }
}
