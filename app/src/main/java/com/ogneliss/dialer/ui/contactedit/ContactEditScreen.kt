package com.ogneliss.dialer.ui.contactedit

import android.graphics.BitmapFactory
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.ContactAccount
import com.ogneliss.dialer.data.ContactBirthday
import com.ogneliss.dialer.data.ContactDraft
import com.ogneliss.dialer.data.EditableValue
import com.ogneliss.dialer.ui.components.ListSheetGroup
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.RowDivider
import com.ogneliss.dialer.ui.components.ScreenScaffold
import com.ogneliss.dialer.ui.components.SettingsGroup
import com.ogneliss.dialer.ui.components.SheetChoiceRow
import com.ogneliss.dialer.ui.components.SheetDivider
import com.ogneliss.dialer.ui.components.SheetGroup
import com.ogneliss.dialer.ui.components.SheetRow
import com.ogneliss.dialer.ui.components.listSheetContainerColor
import com.ogneliss.dialer.ui.components.sheetContainerColor
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.OgnelissTheme
import com.ogneliss.dialer.ui.theme.PageTone
import com.ogneliss.dialer.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * СВОЙ РЕДАКТОР КОНТАКТА (1.7.0).
 *
 * Устроен как настройки: серо-голубая страница, группы полосой с подписью
 * и белыми карточками — имя, телефоны, почта, работа, день рождения,
 * примечание. Фото — кругом вверху: выбрать снимок, обрезать, удалить.
 * Новый контакт — ещё и «Где сохранить»: Google, «Телефон» или другой
 * аккаунт, последний выбор стоит первым.
 *
 * Что редактор знает и чего не трогает — разбор в ContactEditModel.kt.
 * Для остального в меню — «Открыть в системных «Контактах»».
 *
 * «Сохранить» — в верхней панели, как у системных редакторов. Выход
 * с несохранёнными правками — через вопрос, а не молча: набранный
 * номер и выбранное фото жалко потерять одним «Назад».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    onClose: () -> Unit,
    onSaved: (contactId: Long?) -> Unit,
    vm: ContactEditViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cropSource by vm.cropSource.collectAsStateWithLifecycle()
    val familyFirst by vm.familyFirst.collectAsStateWithLifecycle()

    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var accountSheet by rememberSaveable { mutableStateOf(false) }
    var birthdayDialog by rememberSaveable { mutableStateOf(false) }
    // Выбор типа: ключ строки и вид — телефон или почта
    var typeSheet by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }

    /*
     * 2.1.0 (правило автора 07.11.2026): сообщений внизу экрана нет.
     * Нет разрешения на запись — системный запрос; отказ — черновик
     * уходит в системные «Контакты» (разбор у ContactEditViewModel.Event).
     */
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.writePermissionResult(granted)
    }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ContactEditViewModel.Event.Saved -> onSaved(event.contactId)
                ContactEditViewModel.Event.OpenedInSystem -> onClose()
                ContactEditViewModel.Event.AskWritePermission ->
                    askWrite.launch(android.Manifest.permission.WRITE_CONTACTS)
            }
        }
    }

    /*
     * Контакта нет (удалили, пока открывался) — редактор закрывается сам;
     * менять здесь нечего (только мессенджеры или SIM) — сразу системные
     * «Контакты» с этим контактом. Было (1.7.0–2.0.x): текст «Контакт
     * не найден» и «Хранится в …, измените там» с кнопкой.
     */
    val readOnly = state.loaded?.editable == false
    LaunchedEffect(state.notFound, readOnly) {
        when {
            state.notFound -> onClose()
            readOnly -> vm.openInSystem()
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::photoPicked)
    }

    val tryClose = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    ScreenScaffold(
        title = stringResource(if (vm.isNew) R.string.contact_edit_title_new else R.string.contact_edit_title),
        onBack = tryClose,
        tone = PageTone.TINTED,
        pinnedTopBar = true,
        actions = {
            val editable = vm.isNew || state.loaded?.editable == true
            if (editable && !state.loading) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.padding(horizontal = Space.l).size(24.dp), strokeWidth = 2.dp)
                } else {
                    // Пусто — неактивна (2.1.0): разбор у UiState.canSave
                    TextButton(onClick = vm::save, enabled = state.canSave) {
                        Text(stringResource(R.string.contact_edit_save))
                    }
                }
            }
            if (!vm.isNew && !state.notFound) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(OgnelissIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
            }
        }
    ) { pad ->
        when {
            state.loading -> Box(Modifier.padding(pad).fillMaxWidth().padding(Space.xxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            // Закрывается или уходит в системные «Контакты» — разбор выше
            state.notFound || readOnly -> Unit
            else -> Column(
                Modifier
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(bottom = Space.xxl)
            ) {
                PhotoBlock(
                    state = state,
                    onPick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = vm::removePhoto
                )

                if (vm.isNew) {
                    AccountGroup(state.account, onClick = { accountSheet = true })
                }

                /*
                 * Строки «Часть сведений — из WhatsApp, они меняются там»
                 * больше нет (2.1.0): пояснений не показываем; такие сведения
                 * редактор и так не трогает (ContactEditModel.kt).
                 */
                NameGroup(state.draft, familyFirst) { change -> vm.edit(change) }
                ValuesGroup(
                    title = stringResource(R.string.contact_edit_group_phones),
                    values = state.draft.phones,
                    fieldLabel = stringResource(R.string.contact_edit_phone),
                    keyboard = KeyboardType.Phone,
                    addLabel = stringResource(R.string.contact_edit_add_phone),
                    typeLabel = { phoneTypeLabel(it) },
                    onChange = { updated -> vm.edit { d -> d.copy(phones = d.phones.map { if (it.key == updated.key) updated else it }) } },
                    onRemove = { key -> vm.edit { d -> d.copy(phones = d.phones.filter { it.key != key }) } },
                    onType = { key -> typeSheet = key to true },
                    onAdd = vm::addPhone
                )
                ValuesGroup(
                    title = stringResource(R.string.contact_edit_group_emails),
                    values = state.draft.emails,
                    fieldLabel = stringResource(R.string.contact_edit_email),
                    keyboard = KeyboardType.Email,
                    addLabel = stringResource(R.string.contact_edit_add_email),
                    typeLabel = { emailTypeLabel(it) },
                    onChange = { updated -> vm.edit { d -> d.copy(emails = d.emails.map { if (it.key == updated.key) updated else it }) } },
                    onRemove = { key -> vm.edit { d -> d.copy(emails = d.emails.filter { it.key != key }) } },
                    onType = { key -> typeSheet = key to false },
                    onAdd = vm::addEmail
                )
                SettingsGroup(title = stringResource(R.string.contact_edit_group_work)) {
                    Field(state.draft.company, stringResource(R.string.contact_edit_company), KeyboardCapitalization.Sentences) { v ->
                        vm.edit { it.copy(company = v) }
                    }
                    Field(state.draft.title, stringResource(R.string.contact_edit_job_title), KeyboardCapitalization.Sentences) { v ->
                        vm.edit { it.copy(title = v) }
                    }
                    Spacer(Modifier.height(Space.s))
                }
                BirthdayGroup(
                    raw = state.draft.birthday,
                    onPick = { birthdayDialog = true },
                    onClear = { vm.edit { it.copy(birthday = "") } }
                )
                SettingsGroup(title = stringResource(R.string.contact_edit_group_note)) {
                    OutlinedTextField(
                        value = state.draft.note,
                        onValueChange = { v -> vm.edit { it.copy(note = v) } },
                        // Подсказки «Хранится в книге — её видят и другие приложения» нет с 2.1.0
                        shape = MaterialTheme.shapes.large,
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(Space.m)
                    )
                }
            }
        }
    }

    // ── Окна и шторки ────────────────────────────────────────────────────────

    cropSource?.let { bitmap ->
        PhotoCropDialog(source = bitmap, onDone = vm::cropDone, onCancel = vm::cropCancelled)
    }

    if (confirmDiscard) {
        OgnelissDialog(
            onDismissRequest = { confirmDiscard = false },
            title = stringResource(R.string.contact_edit_discard_title),
            content = { Text(stringResource(R.string.contact_edit_discard_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }) {
                    Text(stringResource(R.string.contact_edit_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.contact_edit_keep_editing)) }
            }
        )
    }

    if (menuOpen) {
        ModalBottomSheet(
            onDismissRequest = { menuOpen = false },
            containerColor = sheetContainerColor(),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { WindowInsets.navigationBars }
        ) {
            Column(Modifier.padding(bottom = Space.l)) {
                SheetGroup {
                    SheetRow(
                        OgnelissIcons.OpenInNew,
                        stringResource(R.string.contact_edit_open_system),
                        supporting = stringResource(R.string.contact_edit_open_system_text),
                        onClick = { menuOpen = false; vm.openInSystem() }
                    )
                }
            }
        }
    }

    if (accountSheet) {
        ModalBottomSheet(
            onDismissRequest = { accountSheet = false },
            containerColor = listSheetContainerColor(),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.padding(bottom = FlowUi.sheetBottomPadding)) {
                Text(
                    stringResource(R.string.contact_edit_account_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = FlowUi.sheetPadding, vertical = Space.s)
                )
                ListSheetGroup {
                    state.accounts.forEachIndexed { index, account ->
                        SheetChoiceRow(
                            selected = account.key == state.account?.key,
                            label = accountLabel(account),
                            onClick = { vm.chooseAccount(account); accountSheet = false }
                        )
                        if (index < state.accounts.lastIndex) SheetDivider()
                    }
                }
            }
        }
    }

    typeSheet?.let { (key, isPhone) ->
        val values = if (isPhone) state.draft.phones else state.draft.emails
        val current = values.firstOrNull { it.key == key }
        if (current != null) {
            TypeSheet(
                isPhone = isPhone,
                current = current,
                onSelect = { type ->
                    val updated = current.copy(type = type, label = null)
                    vm.edit { d ->
                        if (isPhone) d.copy(phones = d.phones.map { if (it.key == key) updated else it })
                        else d.copy(emails = d.emails.map { if (it.key == key) updated else it })
                    }
                    typeSheet = null
                },
                onDismiss = { typeSheet = null }
            )
        }
    }

    if (birthdayDialog) {
        BirthdayDialog(
            raw = state.draft.birthday,
            onDone = { value -> vm.edit { it.copy(birthday = value) }; birthdayDialog = false },
            onDismiss = { birthdayDialog = false }
        )
    }
}

// ── Части экрана ─────────────────────────────────────────────────────────────

/**
 * Фото — кругом, как его увидят в журнале и на экране вызова.
 *
 * Нажимается сам круг: туда палец идёт первым. Под кругом — подписанные
 * кнопки, потому что «нажать на фото, чтобы сменить» не очевидно тем,
 * кто редактором контактов пользуется раз в месяц. «Удалить фото» —
 * красным: возвращается только выбором снимка заново.
 *
 * Новое фото показывается из готового JPEG, а не из исходного снимка:
 * человек видит ровно то, что уйдёт в книгу.
 */
@Composable
private fun PhotoBlock(state: ContactEditViewModel.UiState, onPick: () -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val size = 112.dp
        val newPhoto = state.newPhoto
        val preview = remember(newPhoto) {
            newPhoto?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center
        ) {
            when {
                preview != null -> androidx.compose.foundation.Image(
                    preview, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size)
                )
                state.hasPhoto -> AsyncImage(
                    model = state.photoUri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size)
                )
                else -> Icon(
                    OgnelissIcons.Person, contentDescription = null,
                    tint = OgnelissTheme.surfaces.avatarGlyph,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onPick) {
                Text(stringResource(if (state.hasPhoto) R.string.contact_edit_photo_change else R.string.contact_edit_photo_pick))
            }
            if (state.hasPhoto) {
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.contact_edit_photo_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * «Где сохранить» — только у нового контакта: у существующего запись
 * уже лежит в своём аккаунте, и переносить её между аккаунтами редактор
 * не берётся (это удаление и создание заново — с потерей истории
 * в других приложениях).
 *
 * 2.1.0: под названием аккаунта больше нет пояснения «синхронизируется
 * и переедет на новый телефон» / «только на этом телефоне» — правило
 * автора 07.11.2026, без пояснений; значок (облако-контакты или телефон)
 * остался.
 */
@Composable
private fun AccountGroup(account: ContactAccount?, onClick: () -> Unit) {
    SettingsGroup(title = stringResource(R.string.contact_edit_group_account)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FlowUi.rowHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = Space.l, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (account?.kind == ContactAccount.Kind.DEVICE) OgnelissIcons.PhoneAndroid else OgnelissIcons.Contacts,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Space.l))
            Text(
                account?.let { accountLabel(it) }.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(OgnelissIcons.ExpandMore, contentDescription = null)
        }
    }
}

/** Google — с адресом аккаунта: у многих их два, рабочий и личный. */
@Composable
private fun accountLabel(account: ContactAccount): String = when (account.kind) {
    ContactAccount.Kind.GOOGLE -> stringResource(R.string.contact_edit_account_google, account.label)
    else -> account.label
}

/**
 * Имя — тремя полями, а не одной строкой.
 *
 * Одна строка «Имя» выглядела бы проще, но тогда книга сама делила бы её
 * по последнему слову, и «Петров Андрей», введённый по-русски, стал бы
 * «Андреем Петровым» с фамилией «Андрей». Три поля — и «Как показывать
 * имя», и «Сортировка по фамилии» работают верно.
 *
 * Префикс и суффикс («д-р», «мл.») и фонетические имена редактор
 * не показывает и не трогает: в записи они остаются как были.
 */
@Composable
private fun NameGroup(draft: ContactDraft, familyFirst: Boolean, onEdit: ((ContactDraft) -> ContactDraft) -> Unit) {
    SettingsGroup(title = stringResource(R.string.contact_edit_group_name)) {
        val given: @Composable () -> Unit = {
            Field(draft.given, stringResource(R.string.contact_edit_given), KeyboardCapitalization.Words) { v -> onEdit { it.copy(given = v) } }
        }
        val middle: @Composable () -> Unit = {
            Field(draft.middle, stringResource(R.string.contact_edit_middle), KeyboardCapitalization.Words) { v -> onEdit { it.copy(middle = v) } }
        }
        val family: @Composable () -> Unit = {
            Field(draft.family, stringResource(R.string.contact_edit_family), KeyboardCapitalization.Words) { v -> onEdit { it.copy(family = v) } }
        }
        // Порядок — как «Как показывать имя»: «Фамилия Имя Отчество» или «Имя Отчество Фамилия»
        if (familyFirst) { family(); given(); middle() } else { given(); middle(); family() }
        Spacer(Modifier.height(Space.s))
    }
}

/**
 * Поле формы. Подпись — над вводом (label), а не подсказкой внутри:
 * в форме из десятка полей подсказка исчезает при вводе, и через минуту
 * уже не видно, где имя, а где отчество. Скругление — как у поиска
 * и заметки: одно поле ввода в приложении выглядит одинаково.
 */
@Composable
private fun Field(
    value: String,
    label: String,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboard),
        modifier = modifier.fillMaxWidth().padding(start = Space.m, end = Space.m, top = Space.s)
    )
}

/**
 * Номера или адреса почты: поле, крестик «удалить» и тип под полем.
 *
 * Тип — фишкой под номером, а не рядом: в одну строку с полем номер
 * обрезался бы на узком экране, а «Мобильный ▾» — короткое и вторичное.
 * Выбор типа — шторкой из списка, как фильтры, а не выпадающим меню:
 * меню закрывало бы само поле.
 *
 * Пустое поле при сохранении не пишется, а очищенное прежнее —
 * удаляется (разбор — ContactEditPlan): «стереть номер» и «удалить номер»
 * для человека одно и то же.
 */
@Composable
private fun ValuesGroup(
    title: String,
    values: List<EditableValue>,
    fieldLabel: String,
    keyboard: KeyboardType,
    addLabel: String,
    typeLabel: @Composable (EditableValue) -> String,
    onChange: (EditableValue) -> Unit,
    onRemove: (Long) -> Unit,
    onType: (Long) -> Unit,
    onAdd: () -> Unit
) {
    SettingsGroup(title = title) {
        values.forEachIndexed { index, value ->
            if (index > 0) {
                Spacer(Modifier.height(Space.s))
                RowDivider(Modifier.padding(start = Space.l))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(
                    value = value.value,
                    label = fieldLabel,
                    keyboard = keyboard,
                    modifier = Modifier.weight(1f)
                ) { v -> onChange(value.copy(value = v)) }
                IconButton(
                    onClick = { onRemove(value.key) },
                    modifier = Modifier.padding(top = Space.s, end = Space.xs).size(Dimens.MIN_TOUCH_TARGET)
                ) {
                    // TalkBack называет, что удаляется: «Удалить: +7 900…», у пустой строки — «Удалить: Номер»
                    Icon(
                        OgnelissIcons.Close,
                        contentDescription = stringResource(R.string.contact_edit_remove, value.value.ifBlank { fieldLabel })
                    )
                }
            }
            AssistChip(
                onClick = { onType(value.key) },
                label = { Text(typeLabel(value)) },
                trailingIcon = { Icon(OgnelissIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(FlowUi.iconSizeInline)) },
                modifier = Modifier.padding(start = Space.m)
            )
        }
        TextButton(onClick = onAdd, modifier = Modifier.padding(start = Space.s, bottom = Space.xs)) {
            Icon(OgnelissIcons.Add, contentDescription = null, modifier = Modifier.size(FlowUi.iconSizeInline))
            Spacer(Modifier.width(Space.s))
            Text(addLabel)
        }
    }
}

/**
 * День рождения — строкой с датой, нажатие открывает календарь.
 * Вводить дату руками не просим: в книге она хранится строго
 * «ГГГГ-ММ-ДД», и опечатка в формате сделала бы её невидимой
 * для календарей и напоминаний других приложений.
 */
@Composable
private fun BirthdayGroup(raw: String, onPick: () -> Unit, onClear: () -> Unit) {
    SettingsGroup(title = stringResource(R.string.contact_edit_group_birthday)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FlowUi.rowHeight)
                .clickable(onClick = onPick)
                .padding(start = Space.l, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(OgnelissIcons.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.l))
            Text(
                if (raw.isBlank()) stringResource(R.string.contact_edit_birthday_add) else birthdayText(raw),
                style = MaterialTheme.typography.bodyLarge,
                color = if (raw.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (raw.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(OgnelissIcons.Close, contentDescription = stringResource(R.string.contact_edit_birthday_clear))
                }
            }
        }
    }
}

/** «3 декабря 1988» или «3 декабря»; строку иного вида — как записана. */
@Composable
private fun birthdayText(raw: String): String {
    val parts = ContactBirthday.parse(raw) ?: return raw
    val locale = Locale.getDefault()
    return runCatching {
        val date = LocalDate.of(parts.year ?: 2000, parts.month, parts.day)
        val skeleton = if (parts.year != null) "d MMMM yyyy" else "d MMMM"
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .format(date)
    }.getOrDefault(raw)
}

/**
 * Календарь с отметкой «Без года»: год рождения часто не знают или
 * не хотят записывать, а книга такую дату хранит («--12-05»).
 * Календарь Material считает в UTC — поэтому и обратно переводим
 * по UTC, иначе в часовом поясе западнее Гринвича дата съезжала бы
 * на день назад.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayDialog(raw: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val parts = ContactBirthday.parse(raw)
    // Без года — показываем високосный 2000-й: 29 февраля тоже выбирается
    val initial = parts?.let {
        LocalDate.of(it.year ?: 2000, it.month, it.day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
    var noYear by rememberSaveable { mutableStateOf(parts != null && parts.year == null) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = picker.selectedDateMillis
                if (millis == null) onDismiss() else {
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onDone(
                        ContactBirthday.format(
                            ContactBirthday.Parts(if (noYear) null else date.year, date.monthValue, date.dayOfMonth)
                        )
                    )
                }
            }) { Text(stringResource(R.string.contact_edit_pick)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_cancel)) } }
    ) {
        DatePicker(state = picker, title = null)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { noYear = !noYear }
                .padding(horizontal = Space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = noYear, onCheckedChange = { noYear = it })
            Text(stringResource(R.string.contact_edit_no_year))
        }
    }
}

/**
 * Выбор типа номера или почты — пять (три) обычных вариантов.
 *
 * Тип, записанный другим приложением («Дача», «Факс рабочий»), не
 * теряется: он стоит первым и отмечен, пока человек сам не выберет
 * другой. Своих подписей типа редактор не создаёт — это редкость,
 * а поле для неё усложнило бы шторку для всех.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSheet(isPhone: Boolean, current: EditableValue, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = if (isPhone) PHONE_TYPES else EMAIL_TYPES
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = listSheetContainerColor(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(bottom = FlowUi.sheetBottomPadding)) {
            Text(
                stringResource(if (isPhone) R.string.contact_edit_phone_type else R.string.contact_edit_email_type),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = FlowUi.sheetPadding, vertical = Space.s)
            )
            ListSheetGroup {
                // Свой тип строки (например, «Дача») — первым и отмеченным
                val extra = current.type !in options.map { it.first }
                if (extra) {
                    SheetChoiceRow(selected = true, label = if (isPhone) phoneTypeLabel(current) else emailTypeLabel(current), onClick = onDismiss)
                    SheetDivider()
                }
                options.forEachIndexed { index, (type, label) ->
                    SheetChoiceRow(selected = !extra && current.type == type, label = stringResource(label), onClick = { onSelect(type) })
                    if (index < options.lastIndex) SheetDivider()
                }
            }
        }
    }
}

/** Типы номера в редакторе — как в системных «Контактах», без факсов и пейджеров. */
private val PHONE_TYPES = listOf(
    Phone.TYPE_MOBILE to R.string.contact_type_mobile,
    Phone.TYPE_HOME to R.string.contact_type_home,
    Phone.TYPE_WORK to R.string.contact_type_work,
    Phone.TYPE_MAIN to R.string.contact_type_main,
    Phone.TYPE_OTHER to R.string.contact_type_other
)

private val EMAIL_TYPES = listOf(
    Email.TYPE_HOME to R.string.email_type_home,
    Email.TYPE_WORK to R.string.email_type_work,
    Email.TYPE_OTHER to R.string.email_type_other
)

@Composable
private fun phoneTypeLabel(value: EditableValue): String =
    PHONE_TYPES.firstOrNull { it.first == value.type }?.let { stringResource(it.second) }
        ?: Phone.getTypeLabel(LocalContext.current.resources, value.type, value.label).toString()

@Composable
private fun emailTypeLabel(value: EditableValue): String =
    EMAIL_TYPES.firstOrNull { it.first == value.type }?.let { stringResource(it.second) }
        ?: Email.getTypeLabel(LocalContext.current.resources, value.type, value.label).toString()

