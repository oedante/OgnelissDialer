package com.ogneliss.dialer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.OgnelissPanel
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.call.ScreeningRoleInfo
import com.ogneliss.dialer.ui.DialerViewModel

/**
 * СВОДНЫЙ ЭКРАН ЗАЩИТЫ ОТ СПАМА.
 *
 * Назначение: показать все слои фильтрации в одном месте и объяснить,
 * какой из них сейчас работает.
 *
 * ── Зачем этот экран нужен ─────────────────────────────────────────────────
 * Слоёв четыре, они срабатывают в разном порядке, и часть из них
 * принадлежит не нам. Без общей картины пользователь не понимает,
 * почему звонок прошёл или не прошёл, и пишет в отзывах
 * «блокировка не работает».
 *
 * ── Самое важное сообщение экрана ──────────────────────────────────────────
 * Установка Ogneliss телефоном по умолчанию НЕ отключает Яндекс.Определитель
 * или Kaspersky: они держат отдельную роль ROLE_CALL_SCREENING и работают
 * раньше нас. Это главное возражение против перехода на сторонний диалер,
 * и снимать его нужно явно, а не надеяться, что пользователь догадается.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamProtectionScreen(
    vm: DialerViewModel,
    onBack: () -> Unit,
    onOpenLists: () -> Unit
) {
    val context = LocalContext.current
    val rules by vm.rules.collectAsState()
    val settings by vm.settingsState.collectAsState()

    // Состояние ролей читается при каждом входе: пользователь мог
    // поменять определитель в системных настройках и вернуться
    val roleState = remember { ScreeningRoleInfo.state(context) }

    ScreenScaffold(
        title = stringResource(R.string.settings_section_spam),
        onBack = onBack,
        // Настройки: заголовок остаётся на месте, как в разделах
        pinnedTopBar = true
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {

            Text(
                stringResource(R.string.spam_overview_title),
                Modifier.padding(FlowUi.contentPadding).semantics { heading() },
                style = MaterialTheme.typography.titleMedium
            )

            // Слои показаны в порядке срабатывания — это и есть объяснение,
            // почему один вызов блокируется, а другой проходит
            LayerCard(
                order = 1,
                icon = Icons.Default.PhonelinkLock,
                title = stringResource(R.string.spam_layer_system),
                text = stringResource(R.string.spam_layer_system_text),
                active = true,
                onClick = { vm.openSystemBlockList() }
            )

            /*
             * СЛОЙ ОПРЕДЕЛИТЕЛЯ.
             *
             * Роль определителя независима от роли диалера: чужое
             * приложение продолжает работать, даже когда телефоном
             * по умолчанию выбраны мы. Пользователю не нужно выбирать
             * между нами и Яндексом — это главное, что должен сообщать
             * этот блок.
             *
             * Но если роль возьмём мы, появится третий способ обращения
             * с сомнительными вызовами — беззвучный пропуск. Он доступен
             * только держателю роли, и предлагать его стоит именно
             * с этим объяснением, а не как «сделайте нас главными».
             */
            LayerCard(
                order = 2,
                icon = Icons.Default.Shield,
                title = stringResource(R.string.spam_layer_screening),
                text = when {
                    roleState.weAreScreener ->
                        stringResource(R.string.spam_layer_screening_ours)
                    roleState.otherScreenerPackage != null ->
                        stringResource(
                            R.string.spam_layer_other_app_text,
                            appLabel(context, roleState.otherScreenerPackage)
                        )
                    else -> stringResource(R.string.spam_layer_other_app_none)
                },
                active = roleState.weAreScreener || roleState.otherScreenerPackage != null
            )

            /*
             * ══════════════════════════════════════════════════════════════
             * ПРЕДЛОЖЕНИЯ «НАЗНАЧИТЬ OGNELISS ОПРЕДЕЛИТЕЛЕМ» БОЛЬШЕ НЕТ
             * ══════════════════════════════════════════════════════════════
             * Роль ROLE_CALL_SCREENING предлагалась ради третьего варианта
             * для сомнительных номеров: не отклонять, а пропускать
             * беззвучно. Сомнительность оценивала эвристика — а её мы
             * убрали в 1.0.10 (разбор в SpamPolicy.evaluate): оценка без
             * данных о номерах давала ложные срабатывания на курьерах
             * и службах доставки.
             *
             * Без эвристики у роли не осталось назначения. Блокировка
             * по правилам и меткам работает и так: приложение — телефон
             * по умолчанию, и система передаёт вызов в наш
             * OgnelissCallScreeningService без отдельной роли.
             *
             * ⚠️ Хуже того, роль у определителя одна. Если бы человек нажал
             * кнопку, Android отобрал бы её у «Яндекса» или Kaspersky —
             * то есть выключил бы тот самый определитель, который второй
             * карточкой выше обещан работающим вместе с нами.
             */

            LayerCard(
                order = 3,
                icon = Icons.AutoMirrored.Filled.Rule,
                title = stringResource(R.string.spam_layer_rules),
                text = stringResource(R.string.spam_layer_rules_text, rules.size),
                active = rules.isNotEmpty() || settings.blockTaggedSpam,
                onClick = onOpenLists
            )

            /*
             * Слой «офлайн-оценка риска» убран вместе с самой оценкой:
             * показывать слой защиты, которого нет, значит обещать
             * несуществующее.
             */
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LayerCard(
    order: Int,
    icon: ImageVector,
    title: String,
    text: String,
    active: Boolean,
    onClick: (() -> Unit)? = null
) {
    OgnelissPanel(
        // Слой фильтрации нажимается, если за ним есть экран;
        // если нет — это просто сведение, и нажимать не на что
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                icon, contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Порядковый номер показывает очерёдность срабатывания
                    Text("$order. ", style = MaterialTheme.typography.labelLarge)
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        stringResource(
                            if (active) R.string.spam_layer_active
                            else R.string.spam_layer_inactive
                        )
                    )
                }
            )
        }
    }
}

/** Название приложения по имени пакета — читаемее, чем «com.yandex…». */
private fun appLabel(context: android.content.Context, packageName: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
