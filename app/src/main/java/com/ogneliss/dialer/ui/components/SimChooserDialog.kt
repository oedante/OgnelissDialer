package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.FlowUi

/**
 * ВЫБОР SIM-КАРТЫ ДЛЯ ВЫЗОВА.
 *
 * ── Зачем диалог, а не две кнопки в свайпе ─────────────────────────────────
 * Две кнопки возвращали прицеливание в жест, который ценен именно тем,
 * что делает действие одним движением. Плюс выглядело это по-разному
 * в разных строках: где-то две трубки, где-то одна.
 *
 * Диалог появляется только когда карт действительно две И пользователь
 * не выбрал карту по умолчанию. Кому неважно — ставит в настройках
 * «Первая» или «Вторая», и диалога больше нет.
 *
 * ── Что показано в строке карты ────────────────────────────────────────────
 * Номер слота и подпись, которую человек задал сам. Без подписи две
 * карты одного оператора неразличимы: обе называются одинаково.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ЗДЕСЬ БОЛЬШЕ НЕТ AlertDialog И ListItem
 * ══════════════════════════════════════════════════════════════════════════
 * AlertDialog задаёт 24 dp по краям и между блоками, ListItem добавляет
 * свои 16. Рядом с журналом, где расстояния 4–12 dp, окно выглядело
 * из другого приложения — при том что выбор карты человек видит чаще,
 * чем любое другое окно: при каждом звонке, пока не задал карту
 * по умолчанию.
 *
 * Теперь контейнер общий (OgnelissDialog), а карта — такая же карточка
 * на подложке, как строка журнала: shapes.large, отступы из FlowUi.
 * Нажимается вся карточка целиком, а не текст внутри неё.
 */
@Composable
fun SimChooserDialog(
    displayName: String,
    sims: List<SimSlot>,
    onChoose: (slotIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sim_chooser_title),
        content = {
            // Кому звоним — иначе при быстром свайпе легко потерять,
            // по какой записи он был сделан
            Text(
                displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            sims.forEach { sim ->
                SimRow(sim = sim, onClick = { onChoose(sim.slotIndex) })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nav_cancel))
            }
        }
    )
}

/**
 * Строка одной карты.
 *
 * Зелёная трубка: по правилу приложения зелёный означает ровно одно —
 * «позвонить». Здесь нажатие и есть вызов, промежуточного шага нет.
 */
@Composable
private fun SimRow(sim: SimSlot, onClick: () -> Unit) {
    /*
     * Литерал, а не строковый ресурс — сознательно.
     *
     * SimRepository, когда система не дала карте имени, подставляет
     * ровно такую же строку: "SIM ${slotIndex + 1}". Проверка ниже
     * сравнивает подпись именно с ней, чтобы не показать «SIM 2»
     * дважды. Локализованный ресурс сравнение бы сломал: подпись
     * из репозитория осталась бы латинской, а заголовок стал бы
     * переведённым — и дубль вернулся бы.
     *
     * Само «SIM 1» перевода и не требует: так карта подписана
     * и в системных настройках.
     */
    val slotName = "SIM ${sim.slotIndex + 1}"

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(FlowUi.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Call, contentDescription = null, tint = CallGreen)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(slotName, style = MaterialTheme.typography.titleMedium)
                // Подпись показывается, только если отличается от номера
                // слота: иначе она дублировала бы заголовок
                sim.label.takeIf { it.isNotBlank() && it != slotName }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
