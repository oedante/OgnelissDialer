package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ПРОСМОТР ЮРИДИЧЕСКОГО ДОКУМЕНТА.
 *
 * ── Почему документы внутри приложения, а не ссылкой ───────────────────────
 * Google Play требует политику конфиденциальности по публичному URL —
 * это обязательно и остаётся. Но открывать браузер ради её прочтения
 * неудобно, а у приложения нет доступа в сеть: ссылка уведёт
 * в стороннее приложение, и часть пользователей просто не дочитает.
 *
 * Поэтому тексты лежат в assets и показываются прямо здесь. Заодно они
 * доступны без интернета — что согласуется со всем остальным подходом.
 *
 * ── Про формат ─────────────────────────────────────────────────────────────
 * Обычный текст, а не Markdown или HTML. Подключать парсер разметки ради
 * трёх документов означало бы тащить библиотеку в APK; читаемость
 * от этого выиграет незначительно.
 */
/**
 * Чтение документа с откатом по языкам.
 *
 * Порядок: язык интерфейса → английский → русский. Если перевода нет,
 * человек получит текст на понятном большинству языке, а не пустой экран.
 */
private fun readLocalizedAsset(
    context: android.content.Context,
    baseName: String,
    languageTag: String
): String? {
    val candidates = listOf(
        "legal/$languageTag/$baseName",
        "legal/en/$baseName",
        "legal/ru/$baseName",
        // Совместимость с прежним расположением файлов
        baseName
    )

    candidates.forEach { path ->
        runCatching {
            return context.assets.open(path).bufferedReader().use { it.readText() }
        }
    }

    Logger.w("Legal", "Документ не найден ни на одном языке: $baseName")
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    document: LegalDocument,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Чтение файла — операция с диском, поэтому в фоновом контексте
    /*
     * ЯЗЫК ЮРИДИЧЕСКОГО ДОКУМЕНТА.
     *
     * ── Почему это важнее, чем кажется ─────────────────────────────────────
     * Согласие с условиями на языке, которого человек не понимает,
     * юридически слабо: нельзя утверждать, что он ознакомился с текстом.
     * Для интерфейса допустимо откатиться на английский, для документа,
     * под которым ставят галочку, — сомнительно.
     *
     * ── Как выбирается файл ────────────────────────────────────────────────
     * Сначала ищем перевод на язык интерфейса, затем английский,
     * затем русский. Английский вторым, а не первым, потому что он
     * понятнее большинству, чем русский.
     */
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
    val languageTag = locale?.language ?: "en"

    /*
     * Текст не прочитался (2.1.0): было — на экране «Не удалось открыть
     * документ». Стало (правило автора 07.11.2026 «выполнить запасной
     * путь, а не объяснять»): тот же документ открывается на сайте
     * (LegalDocument.url), а пустой экран закрывается. Нет и браузера —
     * экран просто закрывается, причина — в журнале разработчика.
     * null — ещё читается; "" — не прочитался.
     */
    val text by produceState<String?>(initialValue = null, document, languageTag) {
        value = withContext(Dispatchers.IO) {
            readLocalizedAsset(context, document.assetName, languageTag).orEmpty()
        }
    }
    LaunchedEffect(text) {
        if (text?.isEmpty() == true) {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(document.url))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { Logger.w("Legal", "Документ не открылся и на сайте: ${document.name}", it) }
            onBack()
        }
    }

    ScreenScaffold(
        title = stringResource(document.titleRes),
        onBack = onBack
    ) { pad ->
        Text(
            text.orEmpty(),
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(FlowUi.contentPadding),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
