package com.ogneliss.dialer.call

import com.ogneliss.dialer.ui.theme.PanelColors
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxScope
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage

/*
 * Общий вид экранов вызова (1.3.4, 04.10.2026): фото звонящего на весь
 * экран, светлый текст поверх, светлые значки строки состояния.
 * Пользуются InCallActivity (настоящий вызов) и FakeCallActivity
 * (псевдозвонок) — псевдозвонок должен выглядеть как настоящий входящий,
 * иначе подделку видно с первого взгляда.
 */

/**
 * Фото звонящего под всем экраном (1.3.4). Затемнение сверху сильнее:
 * там имя, состояние и значки строки состояния; снизу — слегка, под край
 * панели. Фото — тот же PHOTO_URI, что у круглого портрета.
 */
@Composable
internal fun CallPhotoBackground(photoUri: String) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.60f),
                        0.45f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Black.copy(alpha = 0.35f)
                    )
                )
        )
    }
}

/**
 * Светлые цвета для блока «кто звонит» поверх фото (1.3.4).
 * Подмена цветов темы, а не правка CallerIdentity и CallStatusLine:
 * они берут onSurface, onSurfaceVariant и outline — здесь те же роли
 * становятся светлыми, а фоны плашек (surfaceContainer*) — затемнением.
 * Шрифты и формы — прежние.
 */
@Composable
internal fun OnPhotoColors(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = scheme.copy(
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.85f),
            outline = Color.White.copy(alpha = 0.75f),
            primary = Color.White,
            /*
             * Плашки («Соединение…») — затемнение, а не светлый контейнер
             * темы (05.10.2026, скриншот автора): белый текст на светлой
             * плашке поверх фото был почти не виден.
             */
            surfaceContainerHighest = Color.Black.copy(alpha = 0.40f),
            surfaceContainerHigh = Color.Black.copy(alpha = 0.35f),
            surfaceContainer = Color.Black.copy(alpha = 0.30f)
        ),
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

/**
 * Значки строки состояния — светлые, пока под ними фото (1.3.4): в светлой
 * теме они тёмные и на затемнённом фото терялись бы. При уходе фото —
 * как было.
 */
@Composable
internal fun LightStatusIconsOnPhoto(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        if (enabled) controller?.isAppearanceLightStatusBars = false
        onDispose {
            // before получен через controller?. — раз он не null, Kotlin знает,
            // что и controller не null (предупреждение компилятора 05.10)
            if (before != null) controller.isAppearanceLightStatusBars = before
        }
    }
}

/**
 * Полоса цвета нижней панели под системной навигацией (1.3.5, 05.10.2026,
 * скриншот автора: в тёмной теме под кнопками ◁ ○ □ просвечивало фото —
 * панель кончалась над навигацией). Высота — системный отступ навигации:
 * лёжа, когда навигация сбоку, полоса нулевой высоты.
 */
@Composable
internal fun BoxScope.NavBarUnderPanel() {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(PanelColors.panel())
    )
}
