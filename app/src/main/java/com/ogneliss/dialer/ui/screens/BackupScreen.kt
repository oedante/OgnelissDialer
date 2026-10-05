package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.DialerViewModel
import kotlinx.coroutines.launch

/**
 * ЭКРАН РЕЗЕРВНОГО КОПИРОВАНИЯ.
 *
 * ── Почему этот экран вообще нужен ─────────────────────────────────────────
 * В манифесте стоит `allowBackup="false"`: облачное копирование Android
 * отключено намеренно, мы обещали, что данные не покидают устройство.
 *
 * Обратная сторона обещания — при смене телефона пользователь теряет
 * заметки, метки и списки блокировки. Ручной экспорт закрывает эту дыру,
 * не нарушая обещания: файл создаёт сам пользователь и сам решает,
 * куда его положить.
 *
 * ── Storage Access Framework вместо разрешений ─────────────────────────────
 * Файл выбирается системным диалогом. Разрешение на доступ ко всему
 * хранилищу приложению не нужно — пользователь указывает конкретный файл.
 * Это и требование Play, и минимально необходимый доступ.
 *
 * ── Что НЕ попадает в копию ────────────────────────────────────────────────
 * Журнал вызовов и контакты. Они принадлежат системным провайдерам
 * и восстанавливаются самим Android при переносе устройства. Создавать
 * файл с чужими персональными данными мы не хотим — он окажется
 * в мессенджере или облаке без всякого контроля.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    vm: DialerViewModel,
    onBack: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exportDone = stringResource(R.string.backup_done)
    val exportFailed = stringResource(R.string.backup_failed)
    val importDone = stringResource(R.string.restore_done)
    val importFailed = stringResource(R.string.restore_failed)

    // CreateDocument сам предлагает имя и папку, а результат — Uri
    // с правом записи именно в этот файл
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = vm.exportBackup(uri)
            snackbar.showSnackbar(if (ok) exportDone else exportFailed)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = vm.importBackup(uri)
            snackbar.showSnackbar(if (ok) importDone else importFailed)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.settings_section_maintenance),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        // Экран настроек: заголовок закреплён
        pinnedTopBar = true
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {

            // Объяснение, почему копия вообще нужна: без него функция
            // выглядит необязательной и её не используют до потери данных
            OgnelissPanel(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(OgnelissIcons.Info, null)
                    Spacer(Modifier.width(Space.m))
                    Text(
                        stringResource(R.string.backup_why),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            OgnelissListRow(
                headline = stringResource(R.string.settings_backup_export),
                supporting = stringResource(R.string.settings_backup_export_text),
                leading = { Icon(OgnelissIcons.FileDownload, null) },
                onClick = { exportLauncher.launch(vm.suggestedBackupFileName()) }
            )
            OgnelissDivider()

            OgnelissListRow(
                headline = stringResource(R.string.settings_backup_import),
                supporting = stringResource(R.string.settings_backup_import_text),
                leading = { Icon(OgnelissIcons.FileUpload, null) },
                // Тип фильтруем по JSON, но принимаем и произвольный:
                // некоторые файловые менеджеры отдают octet-stream
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
            )
            OgnelissDivider()

            Text(
                stringResource(R.string.backup_contents),
                Modifier.padding(FlowUi.contentPadding),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Space.xxl))
        }
    }
}
