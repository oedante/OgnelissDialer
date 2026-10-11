package com.ogneliss.dialer.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
    /*
     * СПЛОШНОЙ СПИСОК (1.5.3, макет B).
     *
     * Обычный раздел — во всю ширину, без скругления и полей по бокам:
     * сверху серая полоса с подписью (SectionBand), под ней содержимое
     * на белом. Разделы идут друг за другом, их отделяют сами полосы.
     *
     * Цветная панель (предупреждение, пояснение) — по-прежнему отдельный
     * скруглённый блок с полями: это выноска, а не часть списка.
     */
    if (color != null) {
        Surface(
            color = color,
            shape = MaterialTheme.shapes.large,
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
                        SectionCaption(it)
                    }
                }
                content()
            }
        }
        return
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // С подписью — полоса раздела; без подписи — линия во всю ширину,
        // иначе две панели подряд слились бы в одну (слои защиты от спама,
        // пустая заметка под номерами)
        if (title != null) SectionBand(title, icon = icon) else OgnelissDivider()
        Column(Modifier.padding(FlowUi.cardPadding), content = content)
    }
}

/**
 * ПОЛОСА РАЗДЕЛА (1.5.3, «сплошной список», макет B).
 *
 * Серая полоса во всю ширину с подписью заглавными («СЕГОДНЯ», «НОМЕРА»),
 * тонкие линии сверху и снизу. Разделяет списки на разделы вместо
 * карточек и отступов: журнал, контакты, карточка, настройки, заметки,
 * напоминания, статистика, списки блокировки.
 *
 * [action] — кнопка справа (например, «+» у чёрного списка).
 * [icon] — значок перед подписью (бирюзовый у заметки).
 */
@Composable
fun SectionBand(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = BAND_MIN_HEIGHT)
                .padding(start = Space.l, end = if (action != null) Space.xs else Space.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let { drawIcon ->
                drawIcon()
                Spacer(Modifier.width(Space.s))
            }
            SectionCaption(text, Modifier.weight(1f).padding(vertical = Space.s))
            action?.invoke()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Высота полосы раздела без кнопки: подпись и поля по 8 dp. */
private val BAND_MIN_HEIGHT = 32.dp

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
