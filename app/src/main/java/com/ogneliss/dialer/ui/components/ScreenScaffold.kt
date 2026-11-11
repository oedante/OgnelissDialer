package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.ogneliss.dialer.ui.theme.LocalPageTone
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.PageTone
import com.ogneliss.dialer.ui.theme.FlowUi
import androidx.compose.ui.unit.dp
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
    /**
     * Показывать стрелку «назад». Нет — когда экран стоит правой панелью
     * рядом со списком (настройки на планшете): возврат там — выбор
     * другого раздела слева, а стрелка закрыла бы настройки целиком.
     */
    showBack: Boolean = true,
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
    /**
     * Вид страницы (1.5.6, как в Ogneliss Mail): LIST — белый список,
     * TINTED — серо-голубые настройки с карточками. Разбор — Surfaces.kt.
     */
    tone: PageTone = PageTone.LIST,
    /**
     * Свой заголовок вместо строки [title] (2.1.3): портрет и имя в шапке
     * переписки. null — обычный текст.
     */
    titleContent: (@Composable () -> Unit)? = null,
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
        containerColor = OgnelissTheme.pageColor(tone),
        topBar = {
            TopAppBar(
                // Отступ заголовка — как в почте (FlowUi.topBarTitleInset, 1.8.4)
                title = {
                    if (titleContent != null) titleContent()
                    else Text(
                        title,
                        Modifier.padding(start = if (showBack) FlowUi.topBarTitleInset else 0.dp)
                    )
                },
                navigationIcon = {
                    if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            OgnelissIcons.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    // Белый список: панель окрашивается при прокрутке — видно,
                    // что под ней есть содержимое. Серо-голубые настройки:
                    // панель остаётся белой, как в Mail, — она и так отличается
                    // от страницы
                    scrolledContainerColor = if (tone == PageTone.TINTED) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                )
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        // Не шире 720 dp по центру на широком окне: разбор в ReadableWidth
        content = { pad ->
            CompositionLocalProvider(LocalPageTone provides tone) {
                ReadableWidth { content(pad) }
            }
        }
    )
}
