package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.components.OgnelissPanel
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ReadableWidth

/**
 * СОГЛАСИЕ С ДОКУМЕНТАМИ ПРИ ПЕРВОМ ЗАПУСКЕ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ ЭТОТ ЭКРАН
 * ══════════════════════════════════════════════════════════════════════════
 * Google Play требует политику конфиденциальности для любого приложения,
 * работающего с персональными данными. Лицензионное соглашение
 * не обязательно, но у закрытого кода без него нет запрета
 * на декомпиляцию и распространение — то есть защищать нечем.
 *
 * ── Галочка НЕ отмечена заранее ────────────────────────────────────────────
 * Предустановленная галочка обесценивает согласие: человек ничего
 * не выбирал, а формально согласился. Это и тёмный приём, и юридически
 * слабая позиция — согласие должно быть действием.
 *
 * ── Почему без согласия дальше не пускаем ──────────────────────────────────
 * Приложение работает с журналом вызовов и контактами. Использовать его,
 * не приняв условия обработки этих данных, невозможно по смыслу.
 *
 * Кнопка выхода есть и подписана честно: не «Позже», а «Выйти».
 * Прятать отказ за расплывчатой формулировкой было бы обманом.
 *
 * ── Документы читаются здесь же ────────────────────────────────────────────
 * Не ссылками в браузер: у приложения нет доступа в сеть, и уводить
 * человека в другое приложение ради текста, который лежит внутри, —
 * лишний шаг, на котором половина не дочитает.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Composable
fun ConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenDocument: (LegalDocument) -> Unit
) {
    var accepted by remember { mutableStateOf(false) }

    /*
     * Метки для замеров (1.3.3, 03.10.2026). Сборка для замеров ставится
     * заново, и первым открывается этот экран — замеры и профиль запуска
     * мерили его, а не журнал. benchmark/DeviceSetup находит флажок
     * и кнопку по этим меткам и принимает условия один раз до замеров.
     * testTagsAsResourceId делает метки видимыми для UI Automator
     * как resource-id. На поведение для пользователя не влияет.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(Space.xl)
                ) {
                    Button(
                        onClick = onAccept,
                        enabled = accepted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("consent_accept")
                    ) {
                        Text(stringResource(R.string.consent_accept))
                    }

                    Spacer(Modifier.height(Space.s))

                    // Отказ подписан честно: не «Позже», а «Выйти».
                    // Расплывчатая формулировка здесь была бы обманом
                    TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.consent_decline))
                    }
                }
            }
        }
    ) { padding ->
        // Не шире 640 dp по центру на широком окне: разбор в ReadableWidth
        ReadableWidth {
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl)
        ) {
            Spacer(Modifier.height(Space.xxl))

            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Space.s))

            Text(
                stringResource(R.string.consent_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Space.xl))

            /*
             * Главное о приватности — до документов, а не после.
             *
             * Человек, открывший приложение впервые, читает первые
             * два абзаца и решает, ставить ли галочку. Прятать самое
             * важное в конце десятистраничного текста бессмысленно.
             */
            OgnelissPanel(
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(Modifier.padding(FlowUi.contentPadding), verticalAlignment = Alignment.Top) {
                    Icon(OgnelissIcons.WifiOff, null)
                    Spacer(Modifier.width(Space.m))
                    Text(
                        stringResource(R.string.consent_privacy_summary),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(Space.xl))

            DocumentRow(
                icon = OgnelissIcons.PrivacyTip,
                title = stringResource(R.string.settings_privacy_policy),
                subtitle = stringResource(R.string.consent_privacy_hint),
                onClick = { onOpenDocument(LegalDocument.PRIVACY) }
            )

            DocumentRow(
                icon = OgnelissIcons.Gavel,
                title = stringResource(R.string.settings_eula),
                subtitle = stringResource(R.string.consent_eula_hint),
                onClick = { onOpenDocument(LegalDocument.EULA) }
            )

            Spacer(Modifier.height(Space.xl))

            /*
             * Галочка не отмечена заранее.
             *
             * Предустановленное согласие обесценивает сам факт согласия:
             * человек ничего не выбирал. Это и тёмный приём,
             * и юридически слабая позиция.
             */
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { accepted = !accepted }
                    .testTag("consent_checkbox")
                    .padding(vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                Spacer(Modifier.width(Space.s))
                Text(
                    stringResource(R.string.consent_checkbox),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(Space.xl))
        }
        }
    }
}

@Composable
private fun DocumentRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    OgnelissListRow(
        headline = title,
        supporting = subtitle,
        leading = { Icon(icon, contentDescription = null) },
        trailing = {
            Icon(OgnelissIcons.OpenInNew, contentDescription = null)
        },
        onClick = onClick
    )
}
