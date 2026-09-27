package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R

/**
 * КАРКАС ВТОРОСТЕПЕННОГО ЭКРАНА.
 *
 * ── Зачем общий каркас ─────────────────────────────────────────────────────
 * Раньше каждый экран строил свой `Scaffold` с `TopAppBar`. Отличия
 * накапливались незаметно: где-то отступы под системные панели учтены,
 * где-то нет; где-то заголовок крупный, где-то мелкий; где-то фон
 * `surface`, где-то `background`.
 *
 * По отдельности мелочи, вместе — ощущение, что экраны собирал разный
 * человек. Общий каркас убирает саму возможность такого расхождения.
 *
 * ── Что он делает ──────────────────────────────────────────────────────────
 * • Учитывает `safeDrawing`: содержимое не уезжает под строку состояния
 *   и кнопки навигации
 * • Панель сверху скрывается при прокрутке, освобождая экран содержимому
 * • Заголовок и кнопка «назад» одинаковы везде
 *
 * ── Про прокручиваемую панель ──────────────────────────────────────────────
 * `enterAlwaysScrollBehavior`: панель уезжает при прокрутке вниз
 * и возвращается при первом же движении вверх. Для длинных списков
 * это заметная прибавка полезной высоты, а «назад» остаётся в одном
 * движении, в отличие от панели, которая появляется только наверху списка.
 *
 * ── Когда панель нужно закрепить ───────────────────────────────────────────
 * Не везде это уместно. В настройках заголовок — единственное, что
 * говорит, в каком разделе человек находится: разделы похожи друг
 * на друга, и уехавшая панель оставляет список переключателей
 * без подписи. Такие экраны передают pinnedTopBar = true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    /**
     * Закрепить панель сверху.
     *
     * По умолчанию панель уезжает при прокрутке. true — остаётся
     * на месте, прокручивается только содержимое.
     */
    pinnedTopBar: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = if (pinnedTopBar) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    // Панель окрашивается при прокрутке: видно, что под ней
                    // есть содержимое, а не пустое место
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        // Не шире 640 dp по центру на широком окне: разбор в ReadableWidth
        content = { pad -> ReadableWidth { content(pad) } }
    )
}
