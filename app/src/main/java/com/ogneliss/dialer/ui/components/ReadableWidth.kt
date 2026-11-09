package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * СОДЕРЖИМОЕ ЭКРАНА НЕ ШИРЕ 640 dp, ПО ЦЕНТРУ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * На планшете строка настройки во всю ширину — это две тысячи точек:
 * подпись слева, переключатель справа, и глаз теряет начало строки,
 * пока дойдёт до переключателя. То же с текстом политик и списком
 * правил блокировки: строки такой длины читать тяжело.
 *
 * 640 dp — ширина, при которой строка текста ещё читается одним
 * движением глаза, а строка настройки остаётся одной связной строкой.
 *
 * ── Где применяется ──────────────────────────────────────────────────────
 * В ScreenScaffold — значит, на всех второстепенных экранах сразу:
 * настройки, статистика, быстрый набор, резервная копия, о приложении
 * и остальные. Экраны со своим каркасом (списки блокировки, главная
 * настроек, мастер разрешений, согласие) оборачиваются вручную.
 *
 * Журнал, контакты и карточка сюда не входят: у них своя раскладка
 * для широкого окна (разбор в WindowLayout).
 *
 * ── На телефоне ────────────────────────────────────────────────────────────
 * Ничего не меняется: телефон уже 640 dp, и ограничение не срабатывает.
 * Поэтому здесь нет проверки раскладки — она не нужна.
 */
@Composable
fun ReadableWidth(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .widthIn(max = READABLE_MAX_WIDTH)
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            content()
        }
    }
}

// 2.1.2: 720, как настройки Ogneliss Mail (было 640; дизайн-система, Д10)
private val READABLE_MAX_WIDTH = 720.dp
