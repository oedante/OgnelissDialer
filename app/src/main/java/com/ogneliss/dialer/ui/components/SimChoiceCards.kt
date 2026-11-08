package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.OperatorMatch
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.theme.Blue80
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.Green80
import com.ogneliss.dialer.ui.theme.MessageBlue
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.util.PhoneFormatter

/**
 * ВЫБОР SIM — КАРТОЧКАМИ, ПО ДЕЙСТВИЮ (1.8.0).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ОТКУДА
 * ══════════════════════════════════════════════════════════════════════════
 * Автор, 25.10: «не на всех кнопках звонка и сообщений можно выбрать
 * сим-карту»; «окно выбора сим-карты не учитывает действие, которое будет
 * совершаться. Оно всегда одно»; «панели выбора SIM лучше сделать не
 * выезжающими снизу, а как при долгом нажатии на карточку в журнале или
 * контакт. Пользователям такое нравится».
 *
 * Было (до 1.7.0): окно посередине (SimChooserDialog) с зелёной трубкой
 * в каждой строке — и для звонка, и для SMS; открывалось только свайпом
 * и из карточки абонента.
 *
 * ── Что на экране ─────────────────────────────────────────────────────────
 * Та же стопка, что у действий по долгому нажатию (CardsOverlay): список
 * размыт, карточки встают у места нажатия.
 *   1. Кому и что: портрет, имя, номер; строка действия — «С какой SIM
 *      звонить?» с зелёной трубкой или «С какой SIM написать?» с синим
 *      конвертом. Цвет и значок отвечают на вопрос «что сейчас будет».
 *   2. Карты: номер слота, подпись карты, свой номер; справа — значок
 *      действия или метка «тот же оператор». Ниже — пояснения: выбор
 *      запомнится за номером (звонок, настройка «Запоминать SIM»); для
 *      SMS карта — подсказка приложению сообщений; метка — по плану
 *      нумерации.
 *
 * Касание мимо карточек и «Назад» закрывают, как у действий.
 */
@Composable
fun SimChoiceOverlay(
    request: DialerViewModel.SimRequest,
    /** Где было нажатие — в координатах экрана; null — стопка на пятой части высоты. */
    anchorY: Float?,
    onDismiss: () -> Unit
) {
    val isCall = request.action == DialerViewModel.SimAction.CALL
    // В тёмной теме — светлые оттенки тех же цветов: тёмно-зелёный значок
    // и надпись на тёмной карточке не читались бы (контраст ниже 3 : 1)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val green = if (dark) Green80 else CallGreen
    val accent = if (isCall) green else if (dark) Blue80 else MessageBlue
    val actionIcon = if (isCall) OgnelissIcons.Call else OgnelissIcons.Message
    val formatted = remember(request.number) { PhoneFormatter.format(request.number) }
    // Метка — только у карт, чей оператор совпал с оператором номера
    val sameOperator = remember(request) {
        request.sims.filter { OperatorMatch.same(it.carrier, it.label, request.numberOperator) }
            .map { it.slotIndex }.toSet()
    }

    CardsOverlay(anchorY = anchorY, onDismiss = onDismiss) { close ->
        ActionCard {
            Row(
                Modifier.padding(start = Space.l, end = Space.l, top = Space.l),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RowAvatar(request.photoUri, size = AVATAR, name = request.contactName)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        request.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Номер вторым рядом — только если заголовок не он сам
                    if (request.displayName != formatted) {
                        Text(
                            formatted,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                Modifier.padding(Space.l),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionBadge(actionIcon, accent)
                Spacer(Modifier.width(Space.m))
                Text(
                    stringResource(if (isCall) R.string.sim_chooser_title else R.string.sim_chooser_title_sms),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        ActionCard {
            Column(Modifier.padding(vertical = Space.s)) {
                request.sims.forEach { sim ->
                    SimChoiceRow(
                        sim = sim,
                        same = sim.slotIndex in sameOperator,
                        actionIcon = actionIcon,
                        accent = accent,
                        green = green,
                        actionLabel = stringResource(if (isCall) R.string.action_call else R.string.action_sms)
                    ) {
                        // Действие — сразу, карточки гаснут после: звонок не ждёт анимации
                        request.onChosen(sim.slotIndex)
                        close()
                    }
                }
                /*
                 * Строк под картами («Выбор запомнится для номера», «Карта
                 * передаётся приложению SMS как подсказка», «Оператор —
                 * по плану нумерации») нет с 2.1.0: правило автора
                 * 07.11.2026, пояснений не показываем. Метка «тот же
                 * оператор» у карты осталась — это сведения, а не пояснение.
                 */
            }
        }
    }
}

/** Значок действия в светлом круге его цвета: зелёная трубка или синий конверт. */
@Composable
private fun ActionBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color) {
    Box(
        Modifier
            .size(BADGE)
            .clip(CircleShape)
            .background(accent.copy(alpha = BADGE_ALPHA).compositeOver(OgnelissTheme.surfaces.card)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(ICON_SMALL))
    }
}

/**
 * Строка карты: нажимается целиком; подпись действия для TalkBack —
 * «Позвонить» или «SMS», остальное (слот, подпись, номер, метка) читается
 * одной фразой.
 */
@Composable
private fun SimChoiceRow(
    sim: SimSlot,
    same: Boolean,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    /** Зелёный по теме — для метки «тот же оператор». */
    green: Color,
    actionLabel: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = OgnelissTheme.surfaces
    // Литерал, как в SimRepository: «SIM 1» так подписана карта и в настройках Android
    val slotName = "SIM ${sim.slotIndex + 1}"
    val second = sim.label.takeIf { it.isNotBlank() && it != slotName } ?: sim.carrier
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = ROW_MIN)
            .padding(horizontal = Space.s, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(SLOT)
                .clip(RoundedCornerShape(SLOT_RADIUS))
                // 2.1.0: цвет карты (SimColors), как плашка SIM в журнале
                .background(com.ogneliss.dialer.ui.theme.SimColors.container(sim.slotIndex).compositeOver(surfaces.card)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${sim.slotIndex + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = com.ogneliss.dialer.ui.theme.SimColors.of(sim.slotIndex)
            )
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(slotName, second).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            sim.number?.takeIf { it.isNotBlank() }?.let { own ->
                Text(
                    PhoneFormatter.format(own),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(Space.s))
        if (same) {
            // Метка тем же зелёным, что звонок: «выгоднее» читается без слов
            Text(
                stringResource(R.string.sim_choice_same_operator),
                style = MaterialTheme.typography.labelMedium,
                color = green,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(green.copy(alpha = BADGE_ALPHA).compositeOver(surfaces.card))
                    .padding(horizontal = Space.m, vertical = Space.xs)
            )
        } else {
            Icon(actionIcon, contentDescription = null, tint = accent)
        }
    }
}

private val AVATAR = 52.dp
private val BADGE = 32.dp
private val ICON_SMALL = 18.dp
/** Строка карты — выше обычной: в неё целятся пальцем, промах стоит денег. */
private val ROW_MIN = 64.dp
private val SLOT = 36.dp
private val SLOT_RADIUS = 10.dp
/** Светлая заливка значка действия и метки — как круги в карточках действий. */
private const val BADGE_ALPHA = 0.14f
private const val SLOT_ALPHA = 0.6f
