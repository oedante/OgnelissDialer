package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.FlowUi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens

/**
 * ГРУППА НАСТРОЕК КАРТОЧКОЙ.
 *
 * ── Почему карточки, а не сплошной список ──────────────────────────────────
 * Сплошной список из двадцати строк с разделителями читается как таблица:
 * глаз не находит границ смысловых блоков и вынужден читать всё подряд.
 *
 * Группировка карточками решает это физически. Так устроены системные
 * настройки Android начиная с 12 версии, и повторение этого приёма
 * делает наши настройки узнаваемыми: пользователь уже умеет ими
 * пользоваться, не разбираясь заново.
 *
 * ── Про скругление ─────────────────────────────────────────────────────────
 * Скруглены только внешние углы группы, внутренние разделители прямые.
 * Иначе каждая строка выглядела бы отдельной карточкой, и группировка
 * перестала бы читаться.
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.padding(horizontal = FlowUi.contentPadding)) {
        title?.let {
            Text(
                it,
                Modifier
                    .padding(start = 8.dp, top = 20.dp, bottom = 8.dp)
                    // heading() позволяет TalkBack прыгать по разделам,
                    // не прослушивая каждую строку
                    .semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                // Тёмно-серый: заголовок группы — разметка, а не акцент
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = OgnelissShapeTokens.settingsGroupSingle,
            // Уровень выше фона: группа читается как приподнятый блок
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }

        Spacer(Modifier.height(8.dp))
    }
}
