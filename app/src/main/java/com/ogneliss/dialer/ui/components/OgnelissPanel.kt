package com.ogneliss.dialer.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import com.ogneliss.dialer.ui.theme.LocalPageTone
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.PageTone

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
     * 1.5.6 — КАК В OGNELISS MAIL.
     *
     * Цветная панель (предупреждение, пояснение) — скруглённая выноска
     * с полями, как и была.
     *
     * Обычная — по виду страницы (PageTone, Surfaces.kt):
     * • TINTED (настройки, карточка контакта): подпись раздела, под ней
     *   белая карточка — как группы настроек Mail;
     * • LIST (списки): подпись раздела и содержимое на белом, во всю ширину.
     * С 1.7.0 подпись — без полосы, карточка — без рамки (SectionBand,
     * OgnelissCard); в высококонтрастной теме — полоса и рамка, как было.
     */
    if (color != null) {
        Surface(
            color = color,
            shape = MaterialTheme.shapes.large,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = FlowUi.groupInset, vertical = FlowUi.cardSpacing)
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

    if (LocalPageTone.current == PageTone.TINTED) {
        Column(modifier.fillMaxWidth()) {
            if (title != null) SectionBand(title, icon = icon) else Spacer(Modifier.height(Space.m))
            OgnelissCard {
                Column(Modifier.padding(FlowUi.cardPadding), content = content)
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
 * КАРТОЧКА ГРУППЫ — КАК В OGNELISS MAIL (1.5.6).
 *
 * Белая (в тёмной — на ступень светлее страницы), скругление 22 dp, поля
 * 14 dp от краёв. Рамка — с 1.7.0 только в AMOLED (карточка #111214 на
 * чёрном без неё не видна) и в высококонтрастной теме; в остальных
 * карточку отделяет цвет («воздушный» вид, Surfaces.kt — cardBorder). Внутри
 * ListItem и прочие поверхности цвета surface получают цвет карточки:
 * в тёмной теме страница и карточка разные, а ListItem красится в surface.
 */
@Composable
fun OgnelissCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaces = OgnelissTheme.surfaces
    val shape = OgnelissShapeTokens.card
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = FlowUi.groupInset)
            .clip(shape)
            .background(surfaces.card)
            .border(CARD_BORDER, surfaces.cardBorder, shape)
    ) {
        val scheme = MaterialTheme.colorScheme
        MaterialTheme(
            colorScheme = scheme.copy(surface = surfaces.card, surfaceContainerLow = surfaces.card),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography
        ) {
            Column(content = content)
        }
    }
}

/** Рамка карточки: 1 dp, как в Mail. */
internal val CARD_BORDER = 1.dp

/**
 * ЗАГОЛОВОК РАЗДЕЛА: «Сегодня», «Номера», «Вызовы».
 *
 * 1.7.0 — «ВОЗДУШНЫЙ» ВИД (автор: «сейчас он тяжёлый и монументальный»,
 * выбран вариант А макета docs/ui_1.7.0.png). Подпись без полосы:
 * строчными, синим (primary), 14 sp — как подзаголовки списков Android.
 * Полоса с заливкой на каждом разделе была самым тяжёлым на экране журнала:
 * шесть-семь серых плашек на экран, тяжелее самих записей.
 *
 * Начало подписи — по содержимому под ней: в списке — с 16 dp, как
 * аватары; на странице настроек — с 30 dp, как текст в карточке.
 *
 * Высококонтрастная тема (OgnelissSurfaces.outlined) — как было с 1.5.6:
 * скруглённая полоса с подписью заглавными, там граница важнее вида.
 *
 * История: 1.5.3–1.5.4 — серая полоса во всю ширину с линиями; 1.5.5 —
 * без линий и обычным регистром; 1.5.6 — как в Ogneliss Mail, скруглённая
 * полоса с полями 14 dp; 1.7.0 — подпись без полосы.
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
    val tinted = LocalPageTone.current == PageTone.TINTED
    if (!OgnelissTheme.surfaces.outlined) {
        Row(
            modifier
                .fillMaxWidth()
                // Над подписью — воздух (он и отделяет раздел), под ней —
                // чуть-чуть: подпись принадлежит тому, что ниже
                .padding(
                    start = if (tinted) FlowUi.groupInset + Space.l else Space.l,
                    end = if (action != null) FlowUi.groupInset else Space.l,
                    top = if (tinted) Space.l else Space.m,
                    bottom = if (tinted) Space.xs else Space.xxs
                )
                .heightIn(min = CAPTION_MIN_HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let { drawIcon ->
                drawIcon()
                Spacer(Modifier.width(Space.s))
            }
            SectionCaption(text, Modifier.weight(1f))
            action?.invoke()
        }
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            // Поля вокруг полосы: на белом списке полоса стоит между строками
            // (8 dp над ней, 4 под), в настройках — над карточкой (16 и 8)
            .padding(
                start = FlowUi.groupInset,
                end = FlowUi.groupInset,
                top = if (tinted) Space.l else Space.s,
                bottom = if (tinted) Space.s else Space.xs
            )
            .clip(OgnelissShapeTokens.band)
            .background(OgnelissTheme.bandColor())
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
}

/** Высота строки подписи раздела без полосы (1.7.0): одна строка 14 sp с запасом. */
private val CAPTION_MIN_HEIGHT = 32.dp

/** Высота полосы раздела без кнопки — 36 dp (Mail: 32 в списке, 37 в настройках). */
private val BAND_MIN_HEIGHT = 36.dp

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
 *
 * 1.5.6: цвет — готовый непрозрачный (OgnelissSurfaces.divider, #E2E5EA
 * в светлой, как у Mail), а не outlineVariant с прозрачностью 0,5.
 */
@Composable
fun OgnelissDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = OgnelissTheme.surfaces.divider
    )
}

/**
 * ЛИНИЯ МЕЖДУ СТРОКАМИ ГРУППЫ — «ВОЗДУШНЫЙ» ВИД (1.7.0).
 *
 * Строки журнала, контактов, настроек и шторок разделяет отступ, а не
 * линия: линия под каждой строкой и делала список «решёткой». Рисуется
 * только в высококонтрастной теме (OgnelissSurfaces.outlined).
 *
 * OgnelissDivider остаётся для границ между частями, а не строками:
 * панель без заголовка под панелью, клавиатура под полосой вызова,
 * разделы карточки действий.
 */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    if (OgnelissTheme.surfaces.outlined) OgnelissDivider(modifier)
}

/**
 * ПОДПИСЬ РАЗДЕЛА.
 *
 * 1.7.0 — строчными, синим (primary), titleSmall (14 sp, Medium): легче
 * заглавных с разрядкой и читается как подзаголовок, а не как плашка.
 * Высококонтрастная тема — как с 1.5.6: заглавными, мелко, полужирным,
 * с разрядкой («СЕГОДНЯ», «НОМЕРА»), цветом onSurfaceVariant.
 * Одна функция на все заголовки групп: журнал, контакты, панели карточки,
 * настройки, шторки.
 *
 * Для чтения с экрана — исходный текст, а не заглавные: часть
 * синтезаторов речи читает слово заглавными по буквам. heading() —
 * TalkBack перескакивает по разделам, не слушая каждую строку.
 */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    val outlined = OgnelissTheme.surfaces.outlined
    Text(
        if (outlined) text.uppercase() else text,
        modifier = modifier.semantics {
            heading()
            contentDescription = text
        },
        style = if (outlined) {
            MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.9.sp
            )
        } else {
            MaterialTheme.typography.titleSmall
        },
        color = if (outlined) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        maxLines = 1
    )
}
