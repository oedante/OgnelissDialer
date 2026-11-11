package com.ogneliss.dialer.ui.conversation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.sms.SmsFolder
import com.ogneliss.dialer.sms.SmsFolders
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ПАПКИ SMS — ПОРЯДОК И ПОКАЗ (2.2.0, «Настройки → SMS → Папки»).
 *
 * Как экран закреплённых контактов: стрелки «выше» и «ниже», переключатель
 * показа. «Все» не скрывается (SmsFolders). Изменения — сразу, без
 * «Сохранить»: список папок в «Ещё» раздела SMS перестраивается сам.
 */
@Composable
fun SmsFoldersScreen(onBack: () -> Unit, vm: SmsFoldersViewModel = hiltViewModel()) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    ScreenScaffold(
        title = stringResource(R.string.settings_sms_folders),
        onBack = onBack,
        pinnedTopBar = true
    ) { pad ->
        // Строки — в порядке вкладок: первая здесь — первая слева в разделе SMS
        LazyColumn(Modifier.padding(pad)) {
            itemsIndexed(folders, key = { _, f -> f.first.name }) { i, (folder, visible) ->
                ListItem(
                    headlineContent = { Text(stringResource(smsFolderLabel(folder))) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.move(folders, i, -1) }, enabled = i > 0) {
                                Icon(OgnelissIcons.ArrowUpward, contentDescription = stringResource(R.string.pinned_move_up))
                            }
                            IconButton(onClick = { vm.move(folders, i, 1) }, enabled = i < folders.lastIndex) {
                                Icon(OgnelissIcons.ArrowDownward, contentDescription = stringResource(R.string.pinned_move_down))
                            }
                            // «Все» не скрыть: иначе часть переписок пропала бы из виду
                            Switch(
                                checked = visible,
                                enabled = folder != SmsFolder.ALL,
                                onCheckedChange = { vm.setVisible(folders, i, it) }
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.heightIn(min = Dimens.MIN_TOUCH_TARGET)
                )
            }
        }
    }
}

/** Название папки — общее для вкладок и этого экрана. */
internal fun smsFolderLabel(folder: SmsFolder): Int = when (folder) {
    SmsFolder.ALL -> R.string.sms_folder_all
    SmsFolder.PERSONAL -> R.string.sms_folder_personal
    SmsFolder.UNKNOWN -> R.string.sms_folder_unknown
    SmsFolder.SERVICE -> R.string.sms_folder_service
    SmsFolder.UNREAD -> R.string.sms_folder_unread
    SmsFolder.STARRED -> R.string.sms_folder_starred
}

/**
 * Порядок и показ папок — строкой в настройках (SmsFolders.encode).
 * Своя модель, а не MessagesViewModel: той для работы нужен весь список
 * переписок, а здесь — одна строка настроек.
 */
@HiltViewModel
class SmsFoldersViewModel @Inject constructor(private val prefs: Prefs) : ViewModel() {

    val folders: StateFlow<List<Pair<SmsFolder, Boolean>>> = prefs.smsFolders
        .map(SmsFolders::parse)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), SmsFolders.DEFAULT)

    /** Сдвинуть папку на [delta] (−1 — выше, +1 — ниже); за край — ничего. */
    fun move(list: List<Pair<SmsFolder, Boolean>>, index: Int, delta: Int) {
        val target = index + delta
        if (target !in list.indices) return
        val next = list.toMutableList().apply { add(target, removeAt(index)) }
        save(next)
    }

    /** Показать или скрыть вкладку; выбранная скрытая — раздел вернётся на «Все». */
    fun setVisible(list: List<Pair<SmsFolder, Boolean>>, index: Int, visible: Boolean) {
        save(list.toMutableList().apply { this[index] = this[index].first to visible })
    }

    private fun save(list: List<Pair<SmsFolder, Boolean>>) {
        viewModelScope.launch { prefs.setSmsFolders(SmsFolders.encode(list)) }
    }

    private companion object {
        // Поворот экрана не обрывает подписку на настройки
        const val STOP_MS = 5_000L
    }
}
