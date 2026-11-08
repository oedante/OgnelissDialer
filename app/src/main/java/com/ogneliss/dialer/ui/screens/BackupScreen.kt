package com.ogneliss.dialer.ui.screens

import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.SettingsGroup
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.components.OgnelissListRow
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
    val scope = rememberCoroutineScope()

    /*
     * 2.1.0 (правило автора 07.11.2026): без «Копия сохранена», «Не удалось
     * сохранить копию», «Копия восстановлена», «Не удалось прочитать файл».
     * Успех видно по файлу и по вернувшимся заметкам; неудача — в журнале
     * разработчика (BackupManager пишет причину), а пустой файл неудачной
     * копии удаляется, чтобы потом не принять его за копию.
     */
    // CreateDocument сам предлагает имя и папку, а результат — Uri
    // с правом записи именно в этот файл
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (!vm.exportBackup(uri)) vm.discardExport(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { vm.importBackup(uri) }
    }

    ScreenScaffold(
        // Как настройки Ogneliss Mail: серо-голубая страница, группы — карточками (1.5.6)
        tone = com.ogneliss.dialer.ui.theme.PageTone.TINTED,
        title = stringResource(R.string.settings_section_maintenance),
        onBack = onBack,
        // Экран настроек: заголовок закреплён
        pinnedTopBar = true
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {

            /*
             * Панели «Облачное копирование отключено намеренно…» и строки
             * «В файле — заметки, метки…» под действиями нет с 2.1.0:
             * правило автора 07.11.2026, пояснений не показываем. Что
             * переносится, сказано в «Вопросах и ответах» («Новый телефон»).
             */
            Spacer(Modifier.height(Space.s))

            // Действия — белой группой с рамкой (строгий стиль, 1.5.1)
            SettingsGroup {
                OgnelissListRow(
                    headline = stringResource(R.string.settings_backup_export),
                    supporting = stringResource(R.string.settings_backup_export_text),
                    leading = { Icon(OgnelissIcons.FileDownload, null) },
                    onClick = { exportLauncher.launch(vm.suggestedBackupFileName()) }
                )
                RowDivider(Modifier.padding(start = 56.dp))

                OgnelissListRow(
                    headline = stringResource(R.string.settings_backup_import),
                    supporting = stringResource(R.string.settings_backup_import_text),
                    leading = { Icon(OgnelissIcons.FileUpload, null) },
                    // Тип фильтруем по JSON, но принимаем и произвольный:
                    // некоторые файловые менеджеры отдают octet-stream
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                )
            }

            Spacer(Modifier.height(Space.xxl))
        }
    }
}
