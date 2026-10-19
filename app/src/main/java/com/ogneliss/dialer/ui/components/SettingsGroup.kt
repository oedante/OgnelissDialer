package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ogneliss.dialer.ui.theme.LocalPageTone
import com.ogneliss.dialer.ui.theme.PageTone
import com.ogneliss.dialer.ui.theme.Space

/**
 * ГРУППА НАСТРОЕК — КАК В OGNELISS MAIL (1.5.6).
 *
 * На странице настроек (PageTone.TINTED): скруглённая полоса с подписью
 * раздела, под ней белая карточка с рамкой, строки внутри разделены
 * линиями от начала текста. Без заголовка — только карточка.
 *
 * На белом списке (PageTone.LIST) — полоса и строки во всю ширину,
 * как было в 1.5.3–1.5.5.
 *
 * История: до 1.5.0 — скруглённые карточки на сером; 1.5.1–1.5.2 — белые
 * группы с рамкой (макет A); 1.5.3–1.5.5 — сплошной список (макет B);
 * 1.5.6 — группы настроек Mail: та же идея, что у A, но мягче — крупное
 * скругление, светлая рамка, скруглённые полосы.
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalPageTone.current == PageTone.TINTED) {
        Column(Modifier.fillMaxWidth()) {
            if (title != null) SectionBand(title) else Spacer(Modifier.height(Space.m))
            OgnelissCard(content = content)
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        title?.let { SectionBand(it) }
        Column(content = content)
    }
}
