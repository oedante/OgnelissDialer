package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.ui.components.OgnelissDivider
import com.ogneliss.dialer.ui.components.OgnelissPanel
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.data.DirectoryProvider
import com.ogneliss.dialer.ui.DialerViewModel

/**
 * ЭКРАН ВНЕШНИХ ОПРЕДЕЛИТЕЛЕЙ НОМЕРА.
 *
 * ── Что это за функция ─────────────────────────────────────────────────────
 * Сторонние приложения (Truecaller, корпоративные справочники, CardDAV-клиенты)
 * публикуют себя как удалённые директории через `ContactsContract.Directory`.
 * Диалер может запросить у них имя по номеру штатным способом.
 *
 * ── Почему это отдельный экран с предупреждением ───────────────────────────
 * Здесь единственное место во всём приложении, где данные могут покинуть
 * устройство. Не через нас — у нас нет разрешения на сеть, — а через
 * стороннее приложение, которому мы передадим номер.
 *
 * Вся модель доверия приложения построена на «данные не покидают
 * устройство». Включать такую функцию молча было бы обманом, поэтому:
 *
 *  • по умолчанию выключено для каждого провайдера;
 *  • включается поимённо, а не одним общим переключателем;
 *  • предупреждение показывается прямо на экране, а не в мелком тексте;
 *  • называется приложение, которому уйдёт номер, а не «сторонний сервис».
 *
 * ── Почему функция вообще есть ─────────────────────────────────────────────
 * Корпоративные справочники — реальный сценарий: сотрудник хочет видеть
 * имена коллег, которых нет в личной книге. Запрещать это ради чистоты
 * принципа значило бы решать за пользователя.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalLookupScreen(
    vm: DialerViewModel,
    onBack: () -> Unit
) {
    val providers by produceState(initialValue = emptyList<DirectoryProvider>()) {
        value = vm.availableLookupProviders()
    }
    val enabled by vm.enabledLookupProviders.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<DirectoryProvider?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = stringResource(R.string.settings_external_lookup),
        onBack = onBack,
        // Экран настроек: заголовок закреплён
        pinnedTopBar = true
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {

            // Предупреждение крупно и сверху, а не мелким шрифтом внизу:
            // это единственное место, где данные могут уйти с устройства
            item {
                OgnelissPanel(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.padding(FlowUi.contentPadding), verticalAlignment = Alignment.Top) {
                        Icon(OgnelissIcons.Warning, null)
                        Spacer(Modifier.width(Space.m))
                        Text(
                            stringResource(R.string.external_lookup_warning),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (providers.isEmpty()) {
                item {
                    /*
                     * ПУСТОЙ СПИСОК — ЭТО НОРМА, И ЭТО НАДО ОБЪЯСНИТЬ.
                     *
                     * ── Что здесь на самом деле ────────────────────────────
                     * Экран показывает СТОРОННИЕ СПРАВОЧНИКИ — приложения
                     * вроде Truecaller, которые отдают системе базу
                     * номеров. На большинстве телефонов их нет вовсе,
                     * и список пуст законно.
                     *
                     * ── Чего ждал человек ──────────────────────────────────
                     * Выбора приложения-определителя, как в системных
                     * настройках. Это другое: роль определителя выдаёт
                     * система, и меняется она там же.
                     *
                     * Прежний текст этого не различал, и пустой экран
                     * читался как поломка. Теперь объясняем разницу
                     * и даём кнопку в системные настройки.
                     */
                    Column(Modifier.padding(FlowUi.contentPadding)) {
                        Text(
                            stringResource(R.string.external_lookup_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(Space.l))

                        Text(
                            stringResource(R.string.external_lookup_system_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(Space.m))

                        FilledTonalButton(
                            onClick = { openCallerIdSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.external_lookup_open_system))
                        }
                    }
                }
            }

            items(providers, key = { it.id }) { provider ->
                val isOn = provider.id.toString() in enabled
                OgnelissListRow(
                    headline = provider.displayName,
                    supporting = provider.packageName,
                    // null у переключателя: нажатие ловит вся строка
                    trailing = { Switch(checked = isOn, onCheckedChange = null) },
                    onClick = {
                        // Выключение свободно, включение — с подтверждением:
                        // возврат к безопасному состоянию защищать незачем
                        if (isOn) vm.setLookupProvider(provider.id, false)
                        else confirming = provider
                    }
                )
                OgnelissDivider()
            }
        }
    }

    confirming?.let { provider ->
        /*
         * Общий контейнер приложения.
         *
         * Треугольник предупреждения переехал из шапки в текст:
         * отдельного места под значок у OgnelissDialog нет, а здесь
         * он важен — это согласие на отправку своего номера
         * постороннему сервису.
         */
        OgnelissDialog(
            onDismissRequest = { confirming = null },
            title = stringResource(R.string.confirm_title),
            content = {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        OgnelissIcons.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(Space.m))
                    // Называем конкретное приложение, а не «сторонний
                    // сервис»: человек должен понимать, кому именно
                    // уйдёт его номер
                    Text(
                        stringResource(
                            R.string.external_lookup_confirm,
                            provider.displayName
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setLookupProvider(provider.id, true)
                    confirming = null
                }) { Text(stringResource(R.string.confirm_enable)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.nav_cancel))
                }
            }
        )
    }
}

/**
 * СИСТЕМНЫЕ НАСТРОЙКИ ОПРЕДЕЛИТЕЛЯ НОМЕРА.
 *
 * ── Почему через систему ───────────────────────────────────────────────────
 * Роль определителя и фильтра спама выдаёт система, приложение
 * назначить её себе или другому не может. Единственное, что мы можем
 * полезного, — привести человека на нужный экран, потому что искать
 * его в настройках долго.
 *
 * ── Порядок попыток ────────────────────────────────────────────────────────
 * Сначала экран приложений по умолчанию — там определитель среди
 * прочих ролей. Если его нет, открываем общие настройки: дальше
 * человек дойдёт сам, а пустое нажатие хуже длинного пути.
 */
private fun openCallerIdSettings(context: android.content.Context) {
    val candidates = listOf(
        android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
        ),
        android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
    )

    for (intent in candidates) {
        val opened = runCatching {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) {
                return@runCatching false
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (opened) return
    }
}
