package com.ogneliss.dialer.ui.components

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.ogneliss.dialer.ui.theme.groupBorder
import com.ogneliss.dialer.ui.theme.Space

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ogneliss.dialer.ui.theme.FlowUi

/**
 * ЕДИНАЯ ПАНЕЛЬ ПРИЛОЖЕНИЯ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ОНА ПОЯВИЛАСЬ
 * ══════════════════════════════════════════════════════════════════════════
 * Подсчёт по дереву ui/ на 17 сентября 2026: `Surface` — 33 применения,
 * `ListItem` — 30, `Card` — 10. Все три делают одно и то же: контейнер
 * для блока или строки. Какой применён, зависело от того, какой экран
 * писался в какой день.
 *
 * У них разные умолчания: тень, скругление, отступы внутри, цвет
 * подложки. Разница в два-три пикселя нигде не бросается в глаза
 * по отдельности, но складывается в ощущение «экраны из разных
 * приложений».
 *
 * ── Что теперь ─────────────────────────────────────────────────────────────
 * Один компонент на все блоки. Меняется оттенок или скругление —
 * меняется здесь, а не в тридцати местах.
 *
 * ⚠️ Тени нет намеренно. `Card` по умолчанию её рисует, и на светлой
 * теме получалась рамка вокруг каждого блока — экран из десятка
 * блоков превращался в решётку. Разделение делает подложка,
 * а не тень: так же, как в журнале.
 */
@Composable
fun OgnelissPanel(
    modifier: Modifier = Modifier,
    /**
     * Цвет подложки.
     *
     * null — обычный. Свой цвет нужен предупреждениям и пояснениям:
     * там подложка несёт смысл, а не просто отделяет.
     */
    color: Color? = null,
    /** Заголовок внутри панели. Пусто — панель без заголовка. */
    title: String? = null,
    /**
     * Значок рядом с заголовком.
     *
     * Им отмечается раздел, который надо узнавать не читая: заметка —
     * бирюзовым значком, тем же, что в журнале. Цвет отмечает,
     * а не заливает.
     */
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = color ?: MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        // Тонкая рамка — строгий стиль (1.5.1): группа читается как блок
        // и там, где подложка почти того же тона
        border = groupBorder(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.screenPadding, vertical = FlowUi.cardSpacing)
    ) {
        Column(Modifier.padding(FlowUi.cardPadding)) {
            title?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = Space.s)
                ) {
                    icon?.let { drawIcon ->
                        drawIcon()
                        Spacer(Modifier.width(Space.s))
                    }

                    // Подпись раздела — капителью, как заголовки групп
                    // (строгий стиль, 1.5.1); heading() — внутри
                    SectionCaption(it)
                }
            }

            content()
        }
    }
}

/**
 * РАЗДЕЛИТЕЛЬ МЕЖДУ СТРОКАМИ ПАНЕЛИ.
 *
 * Подсчёт показал 37 применений `HorizontalDivider` с тремя разными
 * прозрачностями — 0.4, 0.5 и полная. Разница видна, когда два таких
 * списка стоят на одном экране.
 *
 * Правило одно: линия ставится ТОЛЬКО между строками, не над первой
 * и не под последней. Там уже есть край панели, и вторая граница рядом
 * читается как ошибка вёрстки.
 */
@Composable
fun OgnelissDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA)
    )
}

/**
 * Прозрачность разделителя.
 *
 * 0.5 — середина между тем, что было. Линия должна отделять строки,
 * но не спорить с текстом: полная выглядит как рамка таблицы.
 */
private const val DIVIDER_ALPHA = 0.5f


/**
 * ПОДПИСЬ РАЗДЕЛА — СТРОГИЙ СТИЛЬ (1.5.1).
 *
 * Заглавными, мелко, с разрядкой: «СЕГОДНЯ», «НОМЕРА», «ВЫЗОВЫ». Так
 * подписывают разделы офисные приложения — подпись размечает экран,
 * а не спорит с содержимым. Одна функция на все заголовки групп:
 * журнал, контакты, панели карточки, настройки, шторки.
 *
 * Для чтения с экрана — исходный текст, а не заглавные: часть
 * синтезаторов речи читает слово заглавными по буквам. heading() —
 * TalkBack перескакивает по разделам, не слушая каждую строку.
 */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.semantics {
            heading()
            contentDescription = text
        },
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}
