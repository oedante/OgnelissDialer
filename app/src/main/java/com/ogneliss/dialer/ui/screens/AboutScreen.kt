package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.components.SettingsGroup
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.BuildConfig
import com.ogneliss.dialer.ui.components.OgnelissPanel
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.R
import com.ogneliss.dialer.util.AppLinks

/**
 * ЭКРАН «О ПРИЛОЖЕНИИ».
 *
 * ── Что здесь и почему именно это ──────────────────────────────────────────
 * Обязательный минимум для Google Play — политика конфиденциальности
 * и условия использования. Их наличие проверяется на ревью.
 *
 * Плюс два пункта, которых обычно нет, но которые нужны именно нам:
 *
 * • ЛИЦЕНЗИИ ОТКРЫТОГО ПО. Мы используем чужой код (AndroidX, Kotlin,
 *   libphonenumber), и лицензии обязывают указывать это. Отсутствие
 *   раздела — нарушение условий Apache 2.0.
 *
 * • ПОЧЕМУ НЕТ ЗАПИСИ РАЗГОВОРОВ — до 2.1.0 строка с окном-объяснением.
 *   С 2.1.0 (правило автора 07.11.2026: пояснений не показываем) её
 *   нет: тот же ответ — в «Вопросах и ответах» («Можно ли записывать
 *   разговоры?», ui/help/Faq.kt), куда человек приходит за ним сам.
 *
 * ── Про блок приватности ───────────────────────────────────────────────────
 * Утверждение «нет доступа к сети» вынесено наверх и подано как
 * проверяемый факт, а не как обещание: пользователь может убедиться
 * сам в свойствах установленного приложения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    /*
     * ══════════════════════════════════════════════════════════════════════
     * ОБЩИЙ КАРКАС ВМЕСТО СВОЕГО SCAFFOLD
     * ══════════════════════════════════════════════════════════════════════
     * Свой Scaffold означал свою шапку, свою кнопку «назад» и свои
     * отступы под системные панели — три места, где экраны расходились.
     * Поведение шапки при прокрутке от этого было разным: где-то она
     * уезжала, где-то стояла.
     *
     * ScreenScaffold задаёт всё это одинаково. Здесь шапка закреплена:
     * экран короткий, и уезжающий заголовок на нём выглядит нервно.
     */
    ScreenScaffold(
        // Как настройки Ogneliss Mail: серо-голубая страница, группы — карточками (1.5.6)
        tone = com.ogneliss.dialer.ui.theme.PageTone.TINTED,
        title = stringResource(R.string.settings_section_about),
        onBack = onBack,
        pinnedTopBar = true
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {

            // Шапка: название, версия, и главный тезис о приватности
            Column(
                Modifier.fillMaxWidth().padding(Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    stringResource(
                        R.string.settings_version,
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OgnelissPanel(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(OgnelissIcons.WifiOff, null)
                    Spacer(Modifier.width(Space.m))
                    Text(
                        stringResource(R.string.about_no_internet),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(Space.l))

            // Документы и справка — строками во всю ширину (сплошной список, 1.5.3)
            SettingsGroup {
                AboutRow(
                    OgnelissIcons.PrivacyTip,
                    stringResource(R.string.settings_privacy_policy)
                ) { onOpenLegal(LegalDocument.PRIVACY) }

                RowDivider(Modifier.padding(start = 56.dp))
                AboutRow(
                    OgnelissIcons.Gavel,
                    stringResource(R.string.settings_license),
                    stringResource(R.string.about_license_hint)
                ) { onOpenLegal(LegalDocument.LICENSE) }

                RowDivider(Modifier.padding(start = 56.dp))
                AboutRow(
                    OgnelissIcons.Article,
                    stringResource(R.string.settings_terms)
                ) { onOpenLegal(LegalDocument.TERMS) }

                // Исходный код — в браузере (1.5.7); адрес виден в подписи,
                // если браузера нет
                RowDivider(Modifier.padding(start = 56.dp))
                AboutRow(
                    OgnelissIcons.Code,
                    stringResource(R.string.settings_source_code),
                    AppLinks.SOURCE_CODE.removePrefix("https://")
                ) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(AppLinks.SOURCE_CODE)
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }

                RowDivider(Modifier.padding(start = 56.dp))
                AboutRow(
                    OgnelissIcons.List,
                    stringResource(R.string.settings_licenses),
                    stringResource(R.string.about_licenses_hint)
                ) { onOpenLegal(LegalDocument.LICENSES) }
            }

            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/**
 * Какие юридические документы показываем.
 *
 * [url] — тот же документ на сайте (2.1.0): встроенный текст не прочитался —
 * LegalScreen открывает его там, без сообщения «Не удалось открыть».
 */
enum class LegalDocument(val assetName: String, val titleRes: Int, val url: String) {
    PRIVACY("privacy_policy.txt", R.string.settings_privacy_policy, AppLinks.PRIVACY),

    /**
     * Лицензия Apache 2.0 (1.5.7). До 1.5.7 здесь было лицензионное
     * соглашение закрытой программы: только личное некоммерческое
     * использование, запрет изменять и распространять. Код открыт —
     * соглашение снято.
     */
    LICENSE("license.txt", R.string.settings_license, AppLinks.LICENSE),
    TERMS("terms_of_use.txt", R.string.settings_terms, AppLinks.TERMS),
    LICENSES("licenses.txt", R.string.settings_licenses, AppLinks.LICENSES)
}

@Composable
private fun AboutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    OgnelissListRow(
        headline = title,
        supporting = subtitle,
        leading = { Icon(icon, contentDescription = null) },
        onClick = onClick
    )
}
