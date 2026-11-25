package com.ogneliss.dialer.ui.components

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import com.ogneliss.dialer.ui.theme.Space

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.ui.theme.Dimens

/**
 * КНОПКА НИЖНЕЙ ПАНЕЛИ: ЗНАЧОК С ПОДПИСЬЮ ПОД НИМ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ОБЩИЙ КОМПОНЕНТ
 * ══════════════════════════════════════════════════════════════════════════
 * Нижняя панель есть на четырёх экранах: журнал, контакты, заметки,
 * напоминания. В журнале под значками появились подписи, а на остальных
 * кнопка фильтра осталась голым значком — и панели разошлись по высоте:
 * значок с подписью выше голого значка на строку текста.
 *
 * Высоту панелей выравнивает общий токен FlowUi.bottomPanelRowHeight,
 * а вид кнопок — этот компонент. Раньше он был приватным ActionIcon
 * внутри панели журнала; второй экран с такой же кнопкой — повод
 * вынести его, а не скопировать.
 *
 * ── Устройство ─────────────────────────────────────────────────────────────
 * Нажимается вся колонка вместе с подписью. Подпись в одну строку,
 * с многоточием: под значком помещается одно слово.
 *
 * Для чтения с экрана описание у всей колонки, а у значка его нет:
 * иначе TalkBack прочёл бы подпись дважды.
 */
@Composable
fun LabeledIconButton(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    /**
     * Показывать ли подпись. По умолчанию — везде, кроме низкого окна
     * (телефон лёжа): там подписи съедали бы четверть высоты экрана.
     * Описание для чтения с экрана остаётся в любом случае.
     */
    showLabel: Boolean = !LocalWindowLayout.current.isShort &&
        com.ogneliss.dialer.ui.theme.LocalAppearance.current.buttonLabels,
    /**
     * Ужимать длинную подпись через FitText (1.3.4, 05.10.2026). Только
     * в узкой боковой колонке: FitText меряет текст и строит вложенную
     * разметку, а нижняя панель журнала — на первом кадре запуска;
     * в ней подпись помещается и обычный Text дешевле.
     */
    fitLabel: Boolean = false,
    /**
     * Кнопка «включена»: работает фильтр, идёт выбор нескольких (1.5.6).
     * Как активный раздел нижней навигации Ogneliss Mail — голубая
     * подложка за значком и подпись темнее и плотнее.
     */
    selected: Boolean = false,
    /**
     * Сплошная кнопка (2.9.2, центральная кнопка нижней панели, вариант «А»):
     * значок на заливке цветом акцента, скругление — из «Скругления углов»
     * (MaterialTheme.shapes.medium). Место, подпись и высота — как у соседей:
     * значки панели на одной линии, подписи на другой.
     */
    filled: Boolean = false,
    /**
     * Описание для чтения с экрана, если подписи мало (2.9.2): у центральной
     * кнопки подпись короткая («Набор»), а TalkBack говорит действие
     * («Показать клавиатуру», «Новое сообщение»). null — подпись.
     */
    contentDescription: String? = null,
    /**
     * Подписи соседей по ряду (2.9.11, FitText.peers): при крупном шрифте все
     * подписи ряда ужимаются одинаково, по самой широкой. Только с [fitLabel].
     */
    labelPeers: List<String> = emptyList(),
    /**
     * Две строки подписи с переносом по слогам (2.9.11, колонка справа: «Мой
     * контакт», «Напоми-нания» — вместо подписи, ужатой до 60 %). Только с [fitLabel].
     */
    twoLineLabel: Boolean = false
) {
    val label = stringResource(labelRes)
    val description = contentDescription ?: label

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = description
                role = Role.Button
                if (selected) this.selected = true
            }
            .padding(horizontal = Space.xs, vertical = Space.xxs)
            .widthIn(min = Dimens.MIN_TOUCH_TARGET)
    ) {
        /*
         * ЗНАЧОК — В «ТАБЛЕТКЕ» ТОГО ЖЕ РАЗМЕРА, ЧТО КНОПКА КЛАВИАТУРЫ.
         *
         * Раньше по сторонам стояли голые значки (24 dp), а в центре —
         * крупная кнопка клавиатуры (88 × 52). Из-за разницы в высоте
         * над боковыми значками оставалась пустота, а подписи приходилось
         * выравнивать по низу.
         *
         * Теперь все пять кнопок одной высоты: боковые — таблетки
         * 56 × 40 со светлой подложкой (цвет клавиш набора), кнопка
         * клавиатуры — 72 × 40 и залита основным цветом. Ряд ровный,
         * пустоты нет, а кнопки стали крупнее и проще для пальца.
         *
         * Совмещены оба ваших варианта: боковые кнопки больше,
         * клавиатура — меньше (но всё ещё шире соседей и выделена цветом).
         */
        /*
         * 1.5.6 — КАК НИЖНЯЯ НАВИГАЦИЯ OGNELISS MAIL: значок без белой
         * подложки, подложка (голубая) — только у включённой кнопки.
         * Место таблетки остаётся (ряд той же высоты, область нажатия
         * та же), меняется только заливка. Было: белые таблетки у всех
         * кнопок (1.2.0–1.5.5).
         */
        /*
         * 2.9.2 — ВАРИАНТ «А» (автор по снимкам вариантов 23.11.2026; форум, eXense:
         * «активная вкладка и центральная кнопка не должны иметь тоновый цвет
         * выделения… несоответствие по высоте/выравниванию»). Выбранная кнопка —
         * без подложки: значок и подпись цветом акцента. Подложка — только
         * у центральной кнопки ([filled]), сплошная, на той же линии, что значки.
         */
        Box(
            Modifier
                .size(width = PILL_WIDTH, height = PILL_HEIGHT)
                .clip(if (filled) MaterialTheme.shapes.medium else CircleShape)
                .then(if (filled) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    filled -> MaterialTheme.colorScheme.onPrimary
                    selected -> MaterialTheme.colorScheme.primary
                    else -> tint
                }
            )
        }

        if (showLabel) {
            // 4 dp, а не 2: подпись прилипала к таблетке кнопки
            Spacer(Modifier.height(Space.xs))

            // Подписи — 12 sp, как в навигации Mail (было 11); включённая —
            // темнее и плотнее
            val labelDensity = androidx.compose.ui.platform.LocalDensity.current
            val labelStyle = MaterialTheme.typography.labelMedium.let {
                if (selected) it.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else it
            }.let {
                /*
                 * ПОТОЛОК РАЗМЕРА ПОДПИСИ (2.6.0, крайние настройки). При шрифте
                 * 160 % и масштабе 110 % на экране 360 dp подписи не помещались,
                 * а ужатые поодиночке выходили разного размера: «SMS» крупно,
                 * «Журнал» мелко. Подпись под значком растёт со шрифтом до
                 * LABEL_MAX — дальше все одинаковые; значки сами говорят, что это.
                 */
                with(labelDensity) {
                    if (it.fontSize.isSp && it.fontSize.toDp() > LABEL_MAX) it.copy(fontSize = LABEL_MAX.toSp(), lineHeight = (LABEL_MAX * 4 / 3).toSp())
                    else it
                }
            }
            val labelColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
            if (fitLabel) {
                // Узкая колонка: длинное слово ужимается, а не обрезается.
                // 2.6.0: до 60 % (было 80): при шрифте 160 % и масштабе 110 %
                // на экране 360 dp «SMS» обрезалось до «S…», а «Меню» — до «…»;
                // 60 % от увеличенного — обычный размер подписи
                FitText(
                    label,
                    style = labelStyle,
                    color = labelColor,
                    maxLines = if (twoLineLabel) 2 else 1,
                    minScale = 0.6f,
                    textAlign = if (twoLineLabel) androidx.compose.ui.text.style.TextAlign.Center else null,
                    peers = labelPeers,
                    hyphenate = twoLineLabel
                )
            } else {
                Text(
                    label,
                    style = labelStyle,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Предел размера подписи под кнопкой панели (2.6.0): 12 sp при шрифте 125 %. */
private val LABEL_MAX = 15.dp

/** Размер «таблетки» боковой кнопки панели. */
internal val PILL_WIDTH = 56.dp
internal val PILL_HEIGHT = 40.dp
