package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.LocalAppearance

/*
 * Круглая кнопка действия с подписью — общая для карточки абонента
 * и меню долгого нажатия в журнале (с 1.2.1).
 *
 * Раньше жила в SubscriberScreen, а меню записи показывало те же
 * действия — «Позвонить», «SMS», «Напомнить», «Блок» — простыми
 * строками текста. Одно действие выглядело по-разному в двух местах.
 */

@Composable
fun RowScope.QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    /**
     * Цвет значка, когда действие уже применено.
     *
     * null — обычный вид. Красный у заблокированного номера: состояние
     * должно быть видно без чтения подписи.
     */
    tint: androidx.compose.ui.graphics.Color? = null,
    /**
     * Цвет подложки плитки.
     *
     * null — общий для ряда. Зелёный только у «Позвонить»: главное
     * действие карточки, и цвет — единственное, что теперь отличает
     * его от соседей по ряду.
     */
    container: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    val label = stringResource(labelRes)

    /*
     * ══════════════════════════════════════════════════════════════════════
     * КРУГЛАЯ КНОПКА, ПОДПИСЬ ПОД НЕЙ — ВМЕСТО ПЛИТКИ С ЗАЛИВКОЙ
     * ══════════════════════════════════════════════════════════════════════
     * Плитки — прямоугольники с заливкой и подписью внутри — пришли
     * из ранних версий Material. Пять таких прямоугольников в ряд
     * спорят за внимание между собой и с содержимым карточки: глазу
     * кажется, что все пять одинаково важны.
     *
     * Круг меньше по площади и читается как «кнопка», а не как «блок».
     * Подпись под ним не отнимает ширину у значка, поэтому помещается
     * целиком — «Отправить SMS» пришлось сокращать до «SMS» именно
     * потому, что подпись делила место с заливкой.
     *
     * ── Что осталось от прежнего вида ──────────────────────────────────────
     * Зелёный у вызова и красный у блокировки: цвет несёт смысл,
     * а не украшает. У остальных круг нейтральный — они равнозначны.
     *
     * ⚠️ Нажимается вся колонка, а не только круг: попасть в подпись
     * так же естественно, как в значок, и человек не должен угадывать
     * границу.
     */
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = container ?: MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(FlowUi.actionCircleSize)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val labelsShown = LocalAppearance.current.buttonLabels
                Icon(
                    icon,
                    // С подписью описание не нужно: TalkBack прочёл бы
                    // его дважды. Без подписи — нужно, иначе кнопка немая
                    contentDescription = if (labelsShown) null else label,
                    tint = tint ?: MaterialTheme.colorScheme.primary
                )
            }
        }

        if (LocalAppearance.current.buttonLabels) {
            Spacer(Modifier.height(6.dp))

            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                // Две строки: длинные подписи есть не только в русском
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
