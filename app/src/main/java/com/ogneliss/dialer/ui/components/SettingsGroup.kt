package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * ГРУППА НАСТРОЕК — РАЗДЕЛ СПЛОШНОГО СПИСКА (1.5.3, макет B).
 *
 * Сверху серая полоса с подписью раздела (SectionBand), под ней строки
 * во всю ширину экрана, между строками — линии внутри раздела. Разделы
 * отделяют полосы, а не карточки и отступы.
 *
 * История: до 1.5.0 — скруглённые карточки на сером, как системные
 * настройки Android 12+; 1.5.1–1.5.2 — белые группы с рамкой (макет A);
 * с 1.5.3 автор выбрал строгий сплошной список (макет B) для всего
 * приложения, и настройки — его часть.
 *
 * Без заголовка — просто строки во всю ширину (главный экран настроек).
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        title?.let { SectionBand(it) }
        Column(content = content)
    }
}
