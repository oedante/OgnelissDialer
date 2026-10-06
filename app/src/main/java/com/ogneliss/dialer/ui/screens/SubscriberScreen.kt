package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.call.ExternalApps
import com.ogneliss.dialer.data.CallLogDateGroup
import com.ogneliss.dialer.ui.theme.OgnelissShapeTokens
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.components.DeleteContactDialog
import com.ogneliss.dialer.ui.theme.OgnelissIcons

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.ogneliss.dialer.concurrency.suspendRunCatching

import com.ogneliss.dialer.util.ContactQr
import androidx.compose.material3.FilledTonalButton
import com.ogneliss.dialer.ui.components.OgnelissDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.text.style.TextOverflow
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.NoteTeal
import com.ogneliss.dialer.ui.theme.FlowUi
import androidx.compose.material3.ButtonDefaults
import com.ogneliss.dialer.ui.components.QrCodeDialog
import com.ogneliss.dialer.ui.components.ReminderDialog
import com.ogneliss.dialer.util.ExportShare
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ogneliss.dialer.ui.components.OgnelissListRow
import com.ogneliss.dialer.data.SpamMarkType
import com.ogneliss.dialer.ui.components.OgnelissPanel
import com.ogneliss.dialer.ui.components.QuickAction
import com.ogneliss.dialer.ui.components.rememberActionHaptics
import com.ogneliss.dialer.R
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.ogneliss.dialer.data.ContactMessengers
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import com.ogneliss.dialer.data.RulePattern
import com.ogneliss.dialer.ui.components.ReminderTimeDialog
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.theme.Dimens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ogneliss.dialer.util.PhoneFormatter
import com.ogneliss.dialer.util.RingtonePicker
import com.ogneliss.dialer.util.TagCatalog
import java.text.DateFormat
import java.util.Date

/**
 * КАРТОЧКА АБОНЕНТА.
 *
 * Назначение: вся история общения с одним человеком или номером на одном
 * экране — звонки, статистика, заметка, метки, быстрые действия.
 *
 * Открывается долгим нажатием на карточку журнала или из результатов поиска.
 *
 * ── Почему это важный экран ────────────────────────────────────────────────
 * До сих пор заметки и метки хранились в базе, но посмотреть их было негде.
 * Функция без экрана — это функция, которой нет.
 *
 * ── Что показывается ───────────────────────────────────────────────────────
 * • Сводка: сколько входящих, исходящих, пропущенных, суммарное время
 * • Все номера абонента, если их несколько
 * • Заметка и метки с возможностью редактирования
 * • Полная лента вызовов с датами и длительностью
 *
 * Даты форматируются через DateFormat.getDateTimeInstance() — он учитывает
 * локаль и системные настройки формата, в отличие от жёсткого шаблона.
 */
/** Собирает пустую группу журнала из контакта без истории вызовов. */
/**
 * Карточка по одному номеру.
 *
 * Сначала ищем среди уже загруженных записей и контактов: там есть имя
 * и фотография. Если не нашлось — собираем минимальную карточку,
 * где известен только номер.
 */
private fun groupFromNumber(
    number: String,
    state: com.ogneliss.dialer.ui.UiState
): CallLogGroup {
    val e164 = PhoneFormatter.toE164(number)

    // Записи журнала могло не быть в загруженной странице, но контакт
    // с таким номером даст хотя бы имя
    state.contacts
        .firstOrNull { contact -> contact.numbers.any { it.e164 == e164 } }
        ?.let { return groupFromContact(it) }

    return CallLogGroup(
        key = "number:$number",
        lastCallId = -1,
        number = number,
        e164 = e164,
        displayName = null,
        photoUri = null,
        contactId = null,
        lastType = CallType.OUTGOING,
        lastDate = 0,
        durationSec = 0,
        count = 0,
        incomingCount = 0,
        outgoingCount = 0,
        missedCount = 0,
        rejectedCount = 0,
        totalDurationSec = 0,
        numbers = listOf(number),
        subscriptionLabel = null,
        subscriptionId = null
    )
}

private fun groupFromContact(contact: com.ogneliss.dialer.data.Contact): CallLogGroup {
    val primary = contact.numbers.firstOrNull()
    return CallLogGroup(
        key = "contact:${contact.id}",
        lastCallId = -1,
        number = primary?.value.orEmpty(),
        e164 = primary?.e164.orEmpty(),
        displayName = contact.displayName,
        photoUri = contact.photoUri,
        contactId = contact.id,
        lastType = CallType.INCOMING,
        lastDate = 0,
        durationSec = 0,
        count = 0,
        incomingCount = 0,
        outgoingCount = 0,
        missedCount = 0,
        rejectedCount = 0,
        totalDurationSec = 0,
        numbers = contact.numbers.map { it.value },
        subscriptionLabel = null,
        subscriptionId = null,
        entries = emptyList()
    )
}

// Одна аннотация на оба API: повторять @OptIn нельзя
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubscriberScreen(
    groupKey: String,
    vm: DialerViewModel,
    /** Чёрный и белый списки: туда ведёт разбор блокировки по маске. */
    onOpenBlockLists: () -> Unit,
    onBack: () -> Unit,
    /**
     * Стрелка «назад». Нет — когда карточка стоит правой панелью рядом
     * с журналом (планшет): «назад» там — выбрать другую запись слева.
     */
    showBack: Boolean = true
) {
    val state by vm.state.collectAsStateWithLifecycle()
    /**
     * Карточка открывается двумя путями: из журнала (есть готовая группа)
     * и из списка контактов (звонков может не быть вовсе).
     *
     * Во втором случае группа собирается из данных контакта — иначе
     * контакт, которому ни разу не звонили, нельзя было бы открыть,
     * а именно ему чаще всего и нужна заметка.
     */
    /*
     * ПОИСК АБОНЕНТА ПО КЛЮЧУ.
     *
     * Три источника, по убыванию точности:
     *
     * 1. Ключ записи журнала — карточка открыта из списка вызовов.
     *
     * 2. «contact:id» — из списка контактов. Записей журнала может
     *    не быть вовсе, если человеку ещё не звонили.
     *
     * 3. «number:...» — из уведомления напоминания. Номер может
     *    не найтись ни в журнале, ни в контактах: запись могли удалить,
     *    а контакта никогда и не было. Тогда собираем карточку
     *    из одного номера — показать её всё равно лучше, чем закрыть
     *    экран сразу после нажатия на уведомление.
     */
    val group = remember(groupKey, state.callLog, state.contacts) {
        state.callLog.firstOrNull { it.key == groupKey }
            /*
             * Запасной путь: тот же абонент, другая последняя запись.
             *
             * Ключ карточки — «абонент#номер_записи». После нового звонка
             * номер последней записи меняется, и ключ, взятый со строки
             * журнала мгновением раньше, уже не совпадает. Раньше карточка
             * в этом случае закрывалась сразу — нажатие выглядело
             * пустым. Теперь открывается свежая группа того же абонента.
             */
            ?: groupKey.substringBefore('#').takeIf { '#' in groupKey }?.let { subscriber ->
                state.callLog.firstOrNull { it.key.substringBefore('#') == subscriber }
            }
            ?: groupKey.removePrefix("contact:").toLongOrNull()
                ?.let { id -> state.contacts.firstOrNull { it.id == id } }
                ?.let { contact -> groupFromContact(contact) }
            ?: groupKey.takeIf { it.startsWith("number:") }
                ?.removePrefix("number:")
                ?.let { number -> groupFromNumber(number, state) }
            // Голый номер — ключ из старых мест (закреплённые без звонков
            // до 1.2.4, ярлыки): тоже открываем по номеру, а не закрываемся
            ?: groupKey.takeIf { BARE_NUMBER.matches(it) }
                ?.let { number -> groupFromNumber(number, state) }
    }

    if (group == null) {
        // Группа могла исчезнуть после удаления записей — не показываем пустоту.
        // Но пишем в журнал: молчаливое закрытие со стороны выглядит как
        // «нажатие не сработало», и без записи причину не найти
        LaunchedEffect(Unit) {
            Logger.w("Card", "Группа не найдена, карточка закрыта: ${Logger.maskKey(groupKey)}")
            onBack()
        }
        return
    }

    /*
     * История вызовов.
     *
     * Если карточка открыта из журнала, записи уже есть в группе.
     * Если из списка контактов — их там нет: загруженная страница журнала
     * могла не дойти до звонков этому человеку. Тогда запрашиваем
     * отдельно, запросом с условием по номеру.
     */
    val history by produceState(initialValue = group.entries, group.key, group.numbers) {
        value = when {
            group.entries.isNotEmpty() -> group.entries

            /*
             * История по ВСЕМ номерам абонента.
             *
             * ── Зачем ──────────────────────────────────────────────────────
             * Запрос по одному номеру показывал только часть звонков:
             * человек звонил и на рабочий, и на личный, а в истории
             * была половина.
             *
             * Собираем по каждому номеру и объединяем, сортируя
             * по времени: получается лента общения с человеком,
             * а не с одной его симкой.
             */
            group.numbers.size > 1 -> group.numbers
                .flatMap { number ->
                    suspendRunCatching { vm.historyFor(number) }.getOrDefault(emptyList())
                }
                .distinctBy { it.id }
                .sortedByDescending { it.date }

            else -> suspendRunCatching { vm.historyFor(group.number) }.getOrDefault(emptyList())
        }
    }

    /*
     * История свёрнута по умолчанию.
     *
     * Разворачивать её при каждом открытии карточки незачем: чаще
     * смотрят на последний звонок и кнопки действий, а не на список
     * за полгода.
     */
    /*
     * ОБНОВЛЕНИЕ ПРИ ОТКРЫТИИ КАРТОЧКИ.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Вы заметили, что при входе в карточку сведения в журнале
     * обновляются — то есть до этого они были неполными.
     *
     * Причина в том, что карточка сама ничего не запрашивала: она
     * ждала, пока данные придут попутно, с очередным обновлением
     * журнала. А оно могло случиться и через десять секунд.
     *
     * Теперь карточка обновляет данные при открытии, как и журнал.
     * Кэш при этом делает первое появление мгновенным: сведения уже
     * есть, а чтение лишь уточняет их.
     */
    LaunchedEffect(group.e164) { vm.refresh() }

    var historyExpanded by remember { mutableStateOf(false) }

    // QR-код с визиткой: поделиться номером при встрече
    var showQr by remember { mutableStateOf(false) }

    // Отправка визитки: нужен контекст и заголовок выбора приложения
    val context = androidx.compose.ui.platform.LocalContext.current
    val shareTitle = stringResource(R.string.action_share)

    var showStats by remember { mutableStateOf(false) }

    if (showStats) {
        // Общий контейнер приложения вместо AlertDialog
        OgnelissDialog(
            onDismissRequest = { showStats = false },
            title = stringResource(R.string.subscriber_summary),
            content = {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCell(group.incomingCount, R.string.stat_incoming)
                        StatCell(group.outgoingCount, R.string.stat_outgoing)
                        StatCell(group.missedCount, R.string.stat_missed, highlight = true)
                    }

                    Spacer(Modifier.height(Space.m))

                    Text(
                        /*
                         * Минуты через plurals, как в статистике.
                         *
                         * «1 минута», «2 минуты», «5 минут» — русский
                         * требует трёх форм, и подставлять число
                         * в готовую строку нельзя.
                         */
                        stringResource(
                            R.string.subscriber_total_time,
                            pluralStringResource(
                                R.plurals.duration_minutes,
                                (group.totalDurationSec / 60).toInt(),
                                (group.totalDurationSec / 60).toInt()
                            )
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showStats = false }) {
                    Text(stringResource(R.string.nav_close))
                }
            }
        )
    }

    if (showQr) {
        QrCodeDialog(
            name = group.displayName ?: PhoneFormatter.format(group.number),
            numbers = group.numbers,
            /*
             * Почта ищется здесь, а не берётся из cardContact:
             * тот объявлен ниже по файлу, когда уже известен контакт
             * карточки, а окно QR-кода живёт выше. Поиск по номеру
             * дешёвый и выполняется один раз при открытии окна.
             */
            emails = state.contacts
                .firstOrNull { c -> c.numbers.any { it.e164 == group.e164 } }
                ?.emails.orEmpty(),
            onDismiss = { showQr = false }
        )
    }

    /*
     * Номер уже заблокирован.
     *
     * Читается из правил блокировки, а не из состояния кнопки: правило
     * могли добавить из журнала, из настроек или свайпом, и карточка
     * обязана это показывать.
     */
    val blockedNumbers by vm.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    val isBlocked = remember(blockedNumbers, group.e164) {
        // kind хранится строкой: "block" | "allow". Сравнение
        // с RuleKind не проходит по типу
        blockedNumbers.any { it.pattern == group.e164 && it.kind == "block" }
    }

    /*
     * ТРЕТЬЕ СОСТОЯНИЕ: НОМЕР ПОД МАСКОЙ.
     *
     * Своего правила у номера нет, но он попадает под маску вида 8800*.
     * Кнопка в этом случае не блокирует и не разблокирует: первое
     * бессмысленно (звонки и так не проходят), второе разблокировало бы
     * весь диапазон.
     *
     * Поэтому она показывает «По маске» и объясняет, что происходит,
     * с переходом в чёрный список.
     */
    val blockingMask = remember(blockedNumbers, group.e164) {
        if (isBlocked) null
        else blockedNumbers.firstOrNull {
            it.isBlock && it.isMask && RulePattern.matches(it.pattern, group.e164)
        }?.pattern
    }

    var maskWarning by remember { mutableStateOf<String?>(null) }

    maskWarning?.let { pattern ->
        OgnelissDialog(
            onDismissRequest = { maskWarning = null },
            title = stringResource(R.string.block_by_mask_title),
            content = { Text(stringResource(R.string.block_by_mask_text, pattern)) },
            confirmButton = {
                TextButton(onClick = { maskWarning = null; onOpenBlockLists() }) {
                    Text(stringResource(R.string.block_by_mask_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { maskWarning = null }) {
                    Text(stringResource(R.string.nav_close))
                }
            }
        )
    }

    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Пока собирается выгрузка, показываем индикатор: на сотне записей
    // это заметная пауза, и без отклика кажется, что нажатие не прошло
    if (exporting) {
        /*
         * Окно ожидания: закрыть его нельзя, пока выгрузка идёт,
         * поэтому onDismissRequest пустой, а кнопки нет вовсе.
         */
        OgnelissDialog(
            onDismissRequest = { },
            title = stringResource(R.string.subscriber_export),
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(Space.l))
                    Text(stringResource(R.string.subscriber_export_progress))
                }
            },
            confirmButton = { }
        )
    }

    if (exportFailed) {
        // Общий контейнер приложения вместо AlertDialog
        OgnelissDialog(
            onDismissRequest = { exportFailed = false },
            title = stringResource(R.string.subscriber_export_failed),
            content = { Text(stringResource(R.string.subscriber_export_failed_hint)) },
            confirmButton = {
                TextButton(onClick = { exportFailed = false }) {
                    Text(stringResource(R.string.nav_close))
                }
            }
        )
    }

    /*
     * Фотография запрашивается по номеру.
     *
     * group.photoUri — кэш журнала вызовов, который система заполняет
     * при звонке и часто оставляет пустым. Точечный запрос к книге
     * идёт по индексу PhoneLookup и занимает миллисекунды.
     */
    val photoUri by produceState<String?>(initialValue = group.photoUri, group.e164) {
        value = suspendRunCatching { vm.photoForNumber(group.number) }.getOrNull()
            ?: group.photoUri
    }

    /*
     * ЕСТЬ ЛИ АБОНЕНТ В КНИГЕ.
     *
     * ── Почему не group.contactId ──────────────────────────────────────────
     * Он разбирается из CACHED_LOOKUP_URI журнала, а на многих прошивках
     * последний сегмент этой строки — lookup-ключ, а не число. Разбор
     * возвращает null, и приложение считает контакт отсутствующим —
     * отсюда «Добавить номер» даже у тех, кто в книге давно.
     *
     * Проверка по загруженному списку контактов надёжна: номер
     * в формате E.164 есть у каждой записи.
     */
    val knownContactId = remember(state.contacts, group.e164) {
        // Первый по алфавиту — тот же порядок, что у имени и фотографии
        state.contacts
            .filter { contact -> contact.numbers.any { it.e164 == group.e164 } }
            .minByOrNull { it.displayName.lowercase() }
            ?.id ?: group.contactId
    }

    // Мессенджеры контакта для кнопки «Написать» — из телефонной книги
    val appContext = LocalContext.current.applicationContext
    val messengerLinks by produceState(emptyList<ContactMessengers.Link>(), knownContactId) {
        value = knownContactId?.let { id ->
            withContext(Dispatchers.IO) { ContactMessengers.linksFor(appContext, id) }
        } ?: emptyList()
    }
    // «Мессенджеры для любого номера» (1.3.5): установленные, которых нет
    // у контакта в книге, — по номеру карточки
    val settingsNow by vm.settingsState.collectAsStateWithLifecycle()
    val byNumberMessengers by produceState(emptyList<SwipeAction>(), settingsNow.messengersAnyNumber, messengerLinks) {
        value = if (settingsNow.messengersAnyNumber) {
            val inBook = messengerLinks.map { it.kind }.toSet()
            withContext(Dispatchers.IO) { ExternalApps.installedMessengers(appContext) }
                .filter { it !in inBook }
        } else {
            emptyList()
        }
    }
    var writeSheetOpen by remember { mutableStateOf(false) }
    if (writeSheetOpen) {
        WriteSheet(
            numbers = remember(state.contacts, knownContactId, group.number) {
                state.contacts.firstOrNull { it.id == knownContactId }
                    ?.numbers?.map { it.value }?.distinct()
                    ?.ifEmpty { null } ?: listOf(group.number)
            },
            links = messengerLinks,
            onSms = { number -> writeSheetOpen = false; vm.openSms(number) },
            onMessenger = { link ->
                writeSheetOpen = false
                ContactMessengers.open(appContext, link)
            },
            onDismiss = { writeSheetOpen = false },
            byNumber = byNumberMessengers,
            byNumberTarget = group.number,
            onMessengerByNumber = { kind ->
                writeSheetOpen = false
                ExternalApps.messengerByNumber(appContext, kind, group.number)
            }
        )
    }

    /** Контакт карточки: из него берутся почта и даты. */
    val cardContact = remember(state.contacts, knownContactId) {
        state.contacts.firstOrNull { it.id == knownContactId }
    }


    /*
     * ДИАЛОГ НАПОМИНАНИЯ ПОКАЗЫВАЕТСЯ ЗДЕСЬ ЖЕ.
     *
     * Раньше он жил только на главном экране, а кнопка — в карточке
     * абонента: диалог всплывал не при нажатии, а когда карточку
     * закрывали. Человек уже забывал, что нажимал.
     *
     * Правило: диалог живёт на том экране, где стоит вызывающая
     * его кнопка.
     */
    val reminderFor by vm.reminderRequest.collectAsStateWithLifecycle()
    /** Выбранное время напоминания: открывает окно выбора даты и времени. */
    var pendingReminderAt by remember { mutableStateOf<Long?>(null) }

    /** Напоминание для этого номера, если назначено. */
    val reminders by vm.activeReminders.collectAsStateWithLifecycle()
    val reminder = remember(reminders, group.e164) {
        reminders.firstOrNull { it.e164 == group.e164 }
    }

    val reminderDateFormat = remember {
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT
        )
    }

    reminderFor?.let { target ->
        ReminderDialog(
            displayName = target.displayName ?: target.number,
            onPick = { at ->
                vm.dismissReminderRequest()
                pendingReminderAt = at
            },
            onDismiss = { vm.dismissReminderRequest() }
        )
    }

    pendingReminderAt?.let { at ->
        ReminderTimeDialog(
            // title объявлен ниже по файлу и здесь ещё не виден:
            // берём то же значение напрямую
            displayName = group.displayName?.takeIf { it.isNotBlank() }
                ?: PhoneFormatter.format(group.number),
            initialMillis = at,
            onSave = { chosen ->
                vm.scheduleReminderAt(group.number, group.displayName, chosen)
                pendingReminderAt = null
            },
            onDismiss = { pendingReminderAt = null }
        )
    }

    /*
     * ДРУГИЕ КОНТАКТЫ С ЭТИМ ЖЕ НОМЕРОМ.
     *
     * ── Зачем показывать ───────────────────────────────────────────────────
     * Номер бывает записан на нескольких контактов: рабочий телефон
     * человека и организации, задвоившийся после синхронизации контакт,
     * общий семейный номер.
     *
     * Карточка показывает первого по алфавиту — выбор предсказуемый,
     * но человек об этом не знает и может решить, что остальные записи
     * пропали.
     *
     * Подпись снимает вопрос: видно, что записей несколько, и какие
     * именно.
     */
    val sameNumberContacts = remember(state.contacts, group.e164) {
        state.contacts
            .filter { contact -> contact.numbers.any { it.e164 == group.e164 } }
            .sortedBy { it.displayName.lowercase() }
    }

    /*
     * Отклик на необратимое: блокировка, снятие блокировки, удаление
     * напоминания. Разбор — в ActionHaptics.
     */
    val haptics = rememberActionHaptics()

    // Блокировка — из меню «Ещё» (1.3.5); логика прежней кнопки «Блок».
// На уровне функции, а не в содержимом экрана: меню вызывается из верхней
// панели (topBar) — соседнего блока, откуда локальные переменные
// содержимого не видны (ошибка сборки 05.10.2026)
    var moreOpen by remember { mutableStateOf(false) }
    // В меню места много — полные подписи, а не «Блок» / «Разблок.»
    val blockLabelRes = when {
        isBlocked -> R.string.action_unblock
        blockingMask != null -> R.string.action_blocked_by_mask
        else -> R.string.action_block
    }
    val onBlockAction: () -> Unit = {
        when {
            isBlocked -> {
                haptics()
                vm.removeRule(group.e164)
            }
            // Показ объяснения про маску ничего не меняет — отклика не нужно
            blockingMask != null -> maskWarning = blockingMask
            else -> {
                haptics()
                vm.blockNumber(group.e164)
            }
        }
    }


    val note = state.notes[group.e164]
    // С 1.1.1 отметка отдельно от заметки — разбор в SpamMarkEntity
    val spamMark = state.spamMarks[group.e164]?.type
    /*
     * Отметка «Спам / Мошенники» у контактов из книги (1.3.4, 05.10.2026,
     * задача автора). Целый блок отметки у «Любимой» — лишний: своих
     * контактов спамом не отмечают. Блок виден у незнакомых номеров,
     * у контакта — если отметка уже стоит (её видно и можно снять) или
     * если её попросили через «Ещё» → «Отметить как спам».
     */
    var markRequested by remember(group.e164) { mutableStateOf(false) }
    val showMarkSection = knownContactId == null || spamMark != null || markRequested
    var editingNote by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Выбор мелодии открывает системный диалог: он показывает и встроенные
    // звуки, и файлы пользователя, и не требует доступа к аудиофайлам
    val ringtoneTitle = stringResource(R.string.contact_ringtone)
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        group.contactId?.let { id ->
            vm.setCustomRingtone(id, RingtonePicker.extractResult(result.data))
        }
    }
    /*
     * ЗАГОЛОВОК КАРТОЧКИ.
     *
     * takeIf { isNotBlank() } обязателен. Для номера, которого нет
     * в книге, группа отдаёт displayName ПУСТОЙ СТРОКОЙ, а не null,
     * поэтому оператор ?: не срабатывал: заголовок оставался пустым,
     * шапка выглядела незагрузившейся, и понять, чья это карточка,
     * можно было только по списку номеров ниже.
     *
     * Это же значение уходит в ярлык на рабочий стол и в подпись
     * фотографии для чтения с экрана — там пустая строка была бы
     * ещё заметнее.
     */
    /*
     * ══════════════════════════════════════════════════════════════════════
     * ЧЕМ ПОДПИСАНА КАРТОЧКА: ИМЯ, ЗАМЕТКА ИЛИ НОМЕР
     * ══════════════════════════════════════════════════════════════════════
     * Порядок тот же, что в журнале, и это не совпадение: человек видит
     * строку в списке и открывает её — подпись обязана совпасть,
     * иначе кажется, что открылось не то.
     *
     *   1. имя из книги — самое точное, что у нас есть;
     *   2. заметка — то, что человек написал сам: «Замер мебели».
     *      Для номера не из книги это единственное осмысленное имя;
     *   3. номер — когда больше нечего сказать.
     */
    val title = group.displayName?.takeIf { it.isNotBlank() }
        ?: note?.note?.takeIf { it.isNotBlank() }
        ?: PhoneFormatter.format(group.number)


    /*
     * ══════════════════════════════════════════════════════════════════════
     * СТРОКА ЗАГОЛОВКА НАД ОБЛОЖКОЙ
     * ══════════════════════════════════════════════════════════════════════
     * Обложка уходит под строку заголовка до верха экрана. Пока она там:
     *
     * • строка заголовка прозрачная, значки белые (сверху на фото лёгкое
     *   затемнение — на светлых фото белое иначе терялось бы);
     * • значки строки состояния (часы, батарея) — тоже светлые;
     * • имени в заголовке нет: оно под фото, второй раз не нужно.
     *
     * Когда шапка уехала вверх, строка заголовка становится обычной:
     * непрозрачная, тёмные значки, имя в заголовке — чтобы было видно,
     * чья это карточка.
     *
     * В две колонки (телефон лёжа) обложка в левой колонке, под строку
     * заголовка не уходит — там всё как раньше, только без имени
     * в заголовке: оно всегда видно слева.
     */
    val cardListState = rememberLazyListState()
    val cardTwoColumns = LocalWindowLayout.current.hasRoomForSidePanel
    val coverBehindBar = !photoUri.isNullOrBlank() && !cardTwoColumns
    /*
     * ══════════════════════════════════════════════════════════════════════
     * СТРОКА ЗАГОЛОВКА — ПО ПРОКРУТКЕ, А НЕ ПЕРЕКЛЮЧАТЕЛЕМ
     * ══════════════════════════════════════════════════════════════════════
     * Первая версия (1.1.3) переключала строку заголовка одним событием —
     * «шапка целиком уехала». Из этого вышло три ошибки, найденные
     * на устройстве:
     *
     * 1. Имя в заголовке появлялось, только когда уезжали и кнопки
     *    действий под именем, — а не само имя.
     * 2. Мигание — только в светлой теме, что и подсказало причину.
     *    Цвет анимировался от Color.Transparent к фону. Но Transparent —
     *    это прозрачный ЧЁРНЫЙ (0, 0, 0, 0), и по дороге к белому цвет
     *    проходил через серый: строка на миг темнела. В тёмной теме
     *    фон и так тёмный — промежуточного серого не видно. Теперь
     *    меняется только прозрачность самого цвета фона, а сам цвет
     *    не меняется — темнеть нечему.
     * 3. Невидимые значки. Пока шапка не уехала целиком, значки
     *    оставались белыми — даже когда фото уже скрылось и под ними
     *    белый фон. «Назад» и меню пропадали, а вместе с ними и часы
     *    со строки состояния, которую я делал светлой над обложкой.
     *
     * Теперь всё ведёт положение прокрутки:
     *
     * • подложка строки проявляется плавно, ровно по мере того как фото
     *   уходит под неё, и становится полностью непрозрачной, когда фото
     *   скрылось, — ничего не просвечивает;
     * • значки — белые над фото и тёмные над фоном, переход по той же
     *   прокрутке;
     * • имя в заголовке появляется, когда само имя под фото уехало под
     *   строку заголовка, — измеряется по его настоящему положению
     *   на экране.
     *
     * Строку состояния больше не трогаем: наше окно не заходит под неё
     * (отступы корня приложения), значит, над ней никогда нет фото —
     * светлые значки на светлом фоне просто исчезали.
     */
    val density = LocalDensity.current
    var coverHeightPx by remember { mutableIntStateOf(0) }
    var nameBottomInRoot by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var barBottomInRoot by remember { mutableFloatStateOf(0f) }

    val coverProgress by remember(coverBehindBar) {
        derivedStateOf {
            if (!coverBehindBar) return@derivedStateOf 1f
            if (cardListState.firstVisibleItemIndex > 0) return@derivedStateOf 1f
            val barPx = with(density) { 64.dp.toPx() }
            val travel = (coverHeightPx - barPx).coerceAtLeast(1f)
            (cardListState.firstVisibleItemScrollOffset / travel).coerceIn(0f, 1f)
        }
    }
    val nameHidden by remember {
        derivedStateOf {
            cardListState.firstVisibleItemIndex > 0 || nameBottomInRoot <= barBottomInRoot
        }
    }
    val showBarTitle = !cardTwoColumns && nameHidden

    val barContainer = MaterialTheme.colorScheme.background.copy(alpha = coverProgress)
    val barIcons = androidx.compose.ui.graphics.lerp(
        Color.White, MaterialTheme.colorScheme.onSurface, coverProgress
    )

    Scaffold(
        // В edge-to-edge содержимое иначе уезжает под системные панели
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            /*
             * СВОРАЧИВАЮЩАЯСЯ ШАПКА С ПОРТРЕТОМ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ ПРОШЛАЯ ПОПЫТКА НЕ РАБОТАЛА
             * ══════════════════════════════════════════════════════════════
             * LargeTopAppBar был подключён, но портрет остался в списке
             * под ним. Шапке было нечего сворачивать, кроме заголовка,
             * и на глаз ничего не менялось.
             *
             * Теперь портрет ВНУТРИ шапки: он и есть то, что уезжает.
             *
             * ── Как это устроено ───────────────────────────────────────────
             * collapsedFraction идёт от 0 (развёрнуто) до 1 (свёрнуто).
             * По нему гасим портрет и уменьшаем имя — плавно, а не
             * скачком: резкая смена на середине жеста читается как сбой.
             *
             * Заголовок при этом остаётся всегда: свёрнутая шапка должна
             * отвечать на вопрос «чья это карточка», иначе придётся
             * прокручивать наверх.
             */
            /*
             * ОБЫЧНАЯ ШАПКА С ИМЕНЕМ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ ОТКАЗАЛИСЬ ОТ СВОРАЧИВАЮЩЕЙСЯ
             * ══════════════════════════════════════════════════════════════
             * Портрет внутри шапки ломал две вещи сразу: имя пропадало
             * при некоторых состояниях, а фотография обрезалась
             * по высоте, потому что шапка задаёт её сама и не даёт
             * задать пропорции.
             *
             * Смысла в сворачивании не оказалось: портрет и так уезжает
             * вместе со списком, если он в списке. Сложность была,
             * выигрыша не было.
             *
             * Имя теперь всегда сверху и всегда видно — это главное,
             * ради чего шапка нужна.
             */
            TopAppBar(
                modifier = Modifier.onGloballyPositioned { coords ->
                    barBottomInRoot = coords.positionInRoot().y + coords.size.height
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barContainer,
                    navigationIconContentColor = barIcons,
                    actionIconContentColor = barIcons
                ),
                title = {
                    /*
                     * Имя — когда имени под фото уже не видно. С фото рядом
                     * маленький круглый портрет: фото «уходит» в строку
                     * заголовка кружком, как в журнале, — видно, чья
                     * карточка, и после прокрутки.
                     *
                     * Появление плавное: без этого строка дёргалась, когда
                     * в ней вдруг возникал текст.
                     */
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showBarTitle,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            photoUri?.takeIf { it.isNotBlank() }?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(Modifier.width(Space.m))
                            }
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                OgnelissIcons.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back)
                            )
                        }
                    }
                },
                actions = {
                    /*
                     * Звезда перед меню, а не после.
                     *
                     * «Ещё» — всегда последний элемент шапки: так
                     * устроено в системных приложениях, и рука тянется
                     * к правому краю именно за ним. Звезда после меню
                     * ломала этот порядок.
                     */
                    val starContact = remember(state.contacts, knownContactId) {
                        state.contacts.firstOrNull { it.id == knownContactId }
                    }

                    if (starContact != null) {
                        IconButton(onClick = { vm.toggleStar(starContact) }) {
                            Icon(
                                if (starContact.starred) OgnelissIcons.Star
                                else OgnelissIcons.StarBorder,
                                contentDescription = stringResource(R.string.action_favourite)
                            )
                        }
                    }

                    // Состояние «на голосовую почту» — из книги, при открытии
                    // карточки и смене контакта (1.3.4)
                    var silentContact by remember(knownContactId) { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(knownContactId) {
                        silentContact = knownContactId?.let { vm.isSilentContact(it) }
                    }

                    SubscriberMoreMenu(
                        knownContactId = knownContactId,
                        onStats = { showStats = true },
                        onQr = { showQr = true },
                        onExport = { exporting = true },
                        onRingtone = {
                            ringtoneLauncher.launch(
                                RingtonePicker.createIntent(null, ringtoneTitle)
                            )
                        },
                        silent = silentContact,
                        onSilent = { id, value ->
                            silentContact = value
                            vm.setSilentContact(id, value)
                        },
                        onShortcut = {
                            vm.pinContactShortcut(title, group.number, group.e164)
                        },
                        onEdit = { id -> vm.editContact(id) },
                        onDelete = { confirmDelete = true },
                        onMarkSpam = if (showMarkSection) null else {
                            { markRequested = true }
                        },
                        open = moreOpen,
                        onDismiss = { moreOpen = false },
                        blockLabelRes = blockLabelRes,
                        blockActive = isBlocked,
                        onBlock = onBlockAction
                    )
                }
            )
        }
    ) { pad ->
        /*
         * ЛАНДШАФТ: ШИРИНА ОГРАНИЧЕНА, ПОРТРЕТ НИЖЕ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО БЫЛО НЕ ТАК
         * ══════════════════════════════════════════════════════════════════
         * Карточка — одна колонка на всю ширину. В ландшафте это 900 dp:
         * строка «8 (967) 666-05-12» с тремя кнопками растягивалась
         * от края до края, между номером и кнопками оставалось полэкрана
         * пустоты, а глазу приходилось возвращаться через всю ширину
         * к следующей строке.
         *
         * Портрет при этом занимал экран целиком: при пропорции 1.9
         * и ширине 900 dp он выше, чем сам экран в ландшафте.
         *
         * ── Как теперь ─────────────────────────────────────────────────────
         * Содержимое ограничено 560 dp и стоит по центру — примерно
         * ширина телефона в портрете, то есть привычная длина строки.
         * Портрет в ландшафте плоский: 3.2 вместо 1.9.
         *
         * ⚠️ Это не планшетная раскладка в две колонки. Она была бы
         * лучше, но требует вынести шапку из списка отдельным
         * компонентом — четыреста строк разметки со ссылками
         * на полтора десятка локальных значений. Такое нельзя делать
         * вслепую; разбор — в docs/UI_AUDIT.md.
         */
        /*
         * Два разных вопроса, раньше слитые в один:
         *
         * • ограничивать ли ширину — да, на любом окне шире телефона
         *   (раскрытая раскладушка в портрете тоже);
         * • делать ли фото плоским — только когда окно лежит: в портрете
         *   раскладушки высоты хватает на обычные пропорции.
         */
        val windowLayout = LocalWindowLayout.current
        val landscape = windowLayout.isWide

        /*
         * ══════════════════════════════════════════════════════════════════
         * КАРТОЧКА В ДВЕ КОЛОНКИ НА ШИРОКОМ ОКНЕ
         * ══════════════════════════════════════════════════════════════════
         * На телефоне лёжа карточка шла одной лентой: фото, имя, кнопки,
         * номера, заметка, сведения, история. Высоты ~360 dp — и до истории
         * надо было прокручивать несколько экранов, а сбоку пустовало
         * пол-экрана.
         *
         * Теперь слева — шапка (фото с именем и кнопки действий), справа —
         * всё остальное. Кто это и что с ним сделать видно всегда, а номера,
         * заметка и история прокручиваются рядом.
         *
         * Признак тот же, что у клавиатуры сбоку в журнале: разбор
         * в WindowLayout.hasRoomForSidePanel.
         */
        val twoColumns = windowLayout.hasRoomForSidePanel

        // Фото плоское, только когда окно лежит и шапка во всю ширину.
        // В левой колонке шириной 360 dp обычные пропорции — как на телефоне
        val flatPhoto = windowLayout.isLandscape && !twoColumns

        /*
         * БЫСТРЫЕ ДЕЙСТВИЯ — ВНИЗУ (1.3.4, 05.10.2026, задача автора: «кнопки
         * лучше расположить внизу, чтобы соблюсти наш принцип доступности»).
         * Было: ряд из пяти кнопок в шапке под фото — уезжал вверх при прокрутке,
         * и до «Позвонить» на большом телефоне не дотянуться одной рукой.
         * Стало: стоя — неподвижная нижняя панель, как в журнале и на экране
         * вызова (bottomPanel, surfaceContainer); список прокручивается над ней.
         * Шапка отдельной колонкой (широкий экран) — ряд остаётся в ней.
         * Сами действия не менялись — ряд перенесён целиком.
         */
        val quickActions: @Composable RowScope.() -> Unit = {
            /*
             * «ПОЗВОНИТЬ» — ПЛИТКА В ОБЩЕМ РЯДУ.
             *
             * ══════════════════════════════════════════════
             * ПОЧЕМУ БОЛЬШЕ НЕ ОТДЕЛЬНАЯ КНОПКА
             * ══════════════════════════════════════════════
             * Широкая кнопка занимала отдельную строку
             * высотой 56 dp плюс отступы — около 80 dp
             * первого экрана. Под ней всё равно шёл ряд
             * из четырёх плиток, то есть две полосы
             * действий подряд.
             *
             * В один ряд помещается всё, и номера
             * поднимаются выше на целую строку.
             *
             * ── Что сохранено ──────────────────────────────
             * Зелёный цвет: это код телефонии, тот же,
             * что у свайпа вызова и кнопки ответа.
             * И место первой слева — там, куда палец
             * идёт по привычке.
             *
             * ⚠️ Плитка нажимается целиком, как и раньше
             * кнопка, и высота её не меньше обязательных
             * 48 dp. Промахнуться не легче, чем прежде:
             * ширина стала меньше, но по вертикали ничего
             * не изменилось.
             */
            QuickAction(
                OgnelissIcons.Call,
                R.string.action_call,
                // Круг зелёный, значок в нём белый.
                // Подпись под кругом — обычного цвета:
                // она лежит на фоне карточки, а не
                // на заливке
                container = CallGreen,
                tint = Color.White
            ) {
                vm.call(group.number)
            }

            /*
             * «НАПИСАТЬ» ВМЕСТО «SMS» (1.2.5).
             * Нет мессенджеров у контакта — сразу SMS, как было:
             * для таких контактов ничего не изменилось. Есть —
             * шторка выбора: SMS и только те мессенджеры,
             * в которых человек есть (ContactMessengers).
             */
            QuickAction(
                OgnelissIcons.Message,
                R.string.action_write
            ) {
                // Выбор — если есть что-то кроме SMS: мессенджер из книги или,
                // с настройкой «для любого номера», установленный (1.3.5)
                if (messengerLinks.isEmpty() && byNumberMessengers.isEmpty()) vm.openSms(group.number)
                else writeSheetOpen = true
            }
            /*
             * ПОДЕЛИТЬСЯ КОНТАКТОМ.
             *
             * ── Чем отличается от QR ───────────────────────
             * QR работает при личной встрече: собеседник
             * рядом и наводит камеру. Отправка — когда
             * его нет рядом: письмо, мессенджер, заметки.
             *
             * Оба нужны, и подменять одно другим нельзя:
             * показать код в переписке невозможно,
             * а при встрече отправлять сообщение долго.
             *
             * ── Что отправляется ───────────────────────────
             * Та же визитка vCard, что в коде. Получатель
             * сохранит контакт одним нажатием, а не будет
             * переписывать цифры из текста.
             */
            QuickAction(OgnelissIcons.Share, R.string.action_share) {
                val name = group.displayName
                    ?: PhoneFormatter.format(group.number)

                ExportShare.shareText(
                    context = context,
                    // Читаемый текст, а не vCard: получатель
                    // видит имя и номера, а не служебную
                    // разметку. vCard остаётся в QR-коде,
                    // где его читает камера
                    text = ContactQr.plainText(
                        name,
                        group.numbers,
                        cardContact?.emails.orEmpty()
                    ),
                    subject = name,
                    chooserTitle = shareTitle
                )
            }
            /*
             * Короткая подпись «Напомнить».
             *
             * «Напомнить перезвонить» не помещалось
             * в плитку и разъезжалось на две строки,
             * из-за чего эта плитка была выше трёх
             * соседних. Смысл не теряется: плитки стоят
             * в карточке конкретного человека,
             * и «напомнить» может значить только одно.
             *
             * Полная фраза осталась там, где места
             * хватает: в меню длинного нажатия,
             * в настройках свайпов и в названии канала
             * уведомлений.
             */
            QuickAction(OgnelissIcons.Alarm, R.string.action_remind_short) {
                vm.requestReminder(group)
            }
            /*
             * БЛОКИРОВКА — ПЕРЕКЛЮЧАТЕЛЬ, А НЕ КНОПКА.
             *
             * ══════════════════════════════════════════════
             * ЧТО БЫЛО НЕ ТАК
             * ══════════════════════════════════════════════
             * Кнопка блокировала, но выглядела одинаково
             * до и после нажатия. Человек не понимал,
             * сработало ли, и нажимал ещё раз — а второе
             * нажатие снова добавляло то же правило.
             *
             * И обратного хода не было: снять блокировку
             * из карточки было нельзя, только из настроек.
             *
             * ── Как теперь ─────────────────────────────────
             * Заблокированный номер: значок перечёркнутого
             * круга красный, подпись «Разблокировать».
             * Нажатие снимает блокировку.
             *
             * Подпись сокращена до «Блок»: полная
             * не помещалась под иконкой.
             */
            /*
             * «Ещё» — на месте «Блока» (1.3.5, 05.10.2026, предложение автора).
             * «Блок» нужен редко, а стоял в правом нижнем углу — прямо над
             * системной «Назад», куда палец попадает случайно. Случайное «Ещё»
             * лишь откроет шторку. «Блок» — в ней, последним, красным.
             * Кнопка ⋮ в верхней панели убрана: меню одно, внизу.
             */
            QuickAction(OgnelissIcons.MoreVert, R.string.action_more) { moreOpen = true }
        }

        val header: @Composable () -> Unit = {
            /*
             * Шапка карточки на цветном контейнере.
             *
             * Раньше это был белый блок, ничем не отличавшийся
             * от списка ниже, и разделения «кто» и «что с ним делать»
             * не читалось.
             */
            /*
             * ШАПКА КАРТОЧКИ АБОНЕНТА.
             *
             * ── Что было не так ───────────────────────────────────
             * Фото бралось из group.photoUri — кэша журнала вызовов.
             * Система заполняет его в момент звонка и часто оставляет
             * пустым, поэтому фотография не показывалась даже у тех,
             * у кого она есть в книге.
             *
             * Отступы по 24 dp сверху и снизу плюс пустое место
             * под кнопками растягивали шапку почти на весь экран:
             * статистику приходилось искать прокруткой.
             */
            // Шапка — на фоне экрана, без своей панели: обложка во всю
            // ширину сверху, под ней имя и кнопки
            Column(Modifier.fillMaxWidth()) {
                    /*
                     * ══════════════════════════════════════════════════════
                     * ФОТО — ОБЛОЖКОЙ ВО ВСЮ ШИРИНУ, ОТ САМОГО ВЕРХА
                     * ══════════════════════════════════════════════════════
                     * Раньше фото было скруглённым прямоугольником внутри
                     * серой скруглённой панели — «рамка в рамке», — с именем
                     * поверх и тем же именем в заголовке экрана. Смотрелось
                     * вставленным извне: единственный прямоугольник среди
                     * круглых аватаров и кнопок.
                     *
                     * Теперь (вариант B, выбран по наброскам): фото без рамки
                     * и скруглений, во всю ширину, уходит под строку
                     * заголовка до верха экрана и снизу растворяется в фоне.
                     * Имя — под фото, тёмным по светлому, как у контакта
                     * без фото. Сверху — лёгкое затемнение: без него белые
                     * значки «назад» и меню терялись бы на светлом фото.
                     *
                     * Без фото — ничего вместо него: сразу имя и кнопки.
                     */
                    photoUri?.takeIf { it.isNotBlank() }?.let { uri ->
                        /*
                         * ШАПКА С ФОТО — В КЛЮЧЕ ЭКРАНА ВЫЗОВА (1.3.4, 05.10.2026,
                         * скриншот автора: имя мелкое и теряется на фото).
                         * Было: фото внизу переходило в цвет фона страницы,
                         * имя titleLarge цветом onSurface лежало на этом
                         * переходе — под тёмным текстом ещё было видно фото.
                         * Стало: внизу фото — затемнение, имя белым,
                         * headlineMedium, с лёгкой тенью; нижние углы фото
                         * скруглены на 28 dp, как у панелей, — страница
                         * начинается с чистого края.
                         */
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(OgnelissShapeTokens.coverBottom)
                                /*
                                 * Лёжа в две колонки обложка — 3 : 1, а не 1,5 : 1.
                                 * Колонка шириной 360 dp давала обложку в 240 dp
                                 * высотой — на экране лёжа (около 330 dp под
                                 * строкой заголовка) имя и кнопки под ней уходили
                                 * за нижний край. 3 : 1 — это 120 dp: обложка,
                                 * имя и кнопки помещаются целиком.
                                 */
                                .aspectRatio(
                                    when {
                                        flatPhoto -> 3.2f
                                        twoColumns -> 3f
                                        else -> 1.5f
                                    }
                                )
                                .onGloballyPositioned { coverHeightPx = it.size.height }
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = stringResource(
                                    R.string.a11y_contact_photo, title
                                ),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.35f)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                                        )
                                    )
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.45f to Color.Transparent,
                                            1f to Color.Black.copy(alpha = 0.65f)
                                        )
                                    )
                            )
                            /*
                             * ИМЯ — НА ОБЛОЖКЕ, ВНИЗУ (1.2.5).
                             *
                             * Под обложкой имя стояло после её растворённой
                             * в фоне нижней части — а эта часть сама выглядит
                             * пустым местом. Между лицом и именем выходил
                             * большой зазор (с телефона, 30.09.2026).
                             *
                             * Теперь имя лежит на этой растворённой части:
                             * там уже почти цвет фона, поэтому текст обычного
                             * цвета читается в обеих темах — не белый поверх
                             * фото, как было в первой версии, а как остальной
                             * текст карточки.
                             */
                            Text(
                                title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.45f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    // 16 dp от нижнего края: с 4 dp имя лежало
                                    // на самой границе обложки (с телефона, 1.3.0)
                                    .padding(
                                        start = FlowUi.screenPadding * 2,
                                        end = FlowUi.screenPadding * 2,
                                        bottom = Space.l
                                    )
                                    .onGloballyPositioned { coords ->
                                        nameBottomInRoot = coords.positionInRoot().y + coords.size.height
                                    }
                            )
                        }
                    }
                Column(
                    /*
                     * ПОРТРЕТ ПО ЦЕНТРУ.
                     *
                     * Колонка не задавала выравнивания, поэтому фото
                     * прижималось к левому краю, а справа от него
                     * оставалась пустая половина карточки: на экране
                     * это читалось как незагрузившийся блок.
                     *
                     * Кнопку «Позвонить» и ряд быстрых действий это
                     * не трогает — они растянуты на всю ширину
                     * и выравнивание игнорируют.
                     */
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // Поля как в списках: 12 dp. Было 16 — на глаз
                    // немного, но рядом с журналом карточка выглядела
                    // разреженной
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = FlowUi.screenPadding * 2,
                        vertical = FlowUi.screenPadding
                    )
                ) {
                    /*
                     * ПОРТРЕТ ВЕРНУЛСЯ В КАРТОЧКУ.
                     *
                     * ══════════════════════════════════════════════════
                     * ПОЧЕМУ ПРЯМОУГОЛЬНЫЙ
                     * ══════════════════════════════════════════════════
                     * В шапке фотография обрезалась по высоте: шапка
                     * задаёт размер сама и не даёт указать пропорции.
                     * Лицо оказывалось срезанным сверху и снизу.
                     *
                     * Здесь мы задаём соотношение сами. Прямоугольник
                     * со скруглением, а не круг: портреты снимают
                     * вертикально, и круг вырезает из них середину,
                     * теряя и волосы, и подбородок.
                     *
                     * ── Размер согласован с журналом ───────────────────
                     * Ширина 96 dp — в четыре раза больше значка
                     * в списке. Карточка должна показывать человека
                     * крупнее, но не занимать пол-экрана.
                     */
                    /*
                     * ПОРТРЕТ ВО ВСЮ ШИРИНУ.
                     *
                     * ══════════════════════════════════════════════════
                     * ПОЧЕМУ НЕ 96 dp И НЕ ВЕРТИКАЛЬНЫЙ
                     * ══════════════════════════════════════════════════
                     * Прежние 96 dp с пропорцией 4:5 давали марку
                     * посреди пустой карточки: справа и слева от неё
                     * оставалось по трети экрана, и шапка читалась
                     * как незаполненная.
                     *
                     * Теперь снимок занимает всю ширину карточки
                     * с пропорцией 3:2 — как в макете. Горизонтальный
                     * кадр важнее вертикального: снимок обрезается
                     * по центру, и при вертикальном кадре в узкой
                     * полосе оставались бы только глаза.
                     *
                     * ── Почему 1.9, а не 1.5 ───────────────────────────
                     * При 3:2 портрет на экране шириной 360 dp занимает
                     * 240 dp — вместе с именем, кнопкой и плитками
                     * действий это весь первый экран, и номера
                     * приходится искать прокруткой.
                     *
                     * 1.9 оставляет полосу около 190 dp: лицо
                     * по-прежнему узнаётся, но под ним помещается
                     * то, ради чего карточку открыли.
                     */
                    /*
                     * ══════════════════════════════════════════════════
                     * НЕТ ФОТОГРАФИИ — НЕТ И МЕСТА ПОД НЕЁ
                     * ══════════════════════════════════════════════════
                     * Раньше при отсутствии фото рисовалась серая
                     * панель во всю ширину с силуэтом человека
                     * посередине — около 190 dp первого экрана,
                     * которые не сообщали ничего, кроме «фотографии
                     * нет». Это и так видно.
                     *
                     * Фотографии нет у большинства номеров:
                     * у неизвестных её неоткуда взять, а в книге
                     * её ставят немногим. То есть пустая панель
                     * была правилом, а не исключением, и отодвигала
                     * вниз номера, заметку и историю — то, ради чего
                     * карточку открывают.
                     *
                     * Имя при этом остаётся: оно идёт следующей
                     * строкой и держит верх карточки.
                     *
                     * ⚠️ На экране вызова это уже сделано в 1.0.10
                     * по той же причине — здесь просто довели
                     * до конца.
                     */
                    /*
                     * takeIf { isNotBlank() } — не перестраховка.
                     *
                     * И провайдер контактов, и наша группа журнала
                     * отдают для «фотографии нет» не null, а пустую
                     * строку. Проверка на null пропускала её дальше,
                     * и панель рисовалась пустой — ровно то, что
                     * мы убирали.
                     */
                    /*
                     * ══════════════════════════════════════════════════
                     * ИМЯ ЛЕЖИТ НА ФОТОГРАФИИ, А НЕ ПОД НЕЙ
                     * ══════════════════════════════════════════════════
                     * Так устроены карточки в почте, мессенджерах
                     * и галерее: подпись на снимке, а не отдельной
                     * строкой. Выигрыш — высота строки имени плюс два
                     * отступа, около сорока точек первого экрана.
                     * На карточке это заметно: выше поднимаются
                     * номера и заметка.
                     *
                     * ── Почему нужна затемняющая подложка ──────────────
                     * Белый текст на произвольной фотографии читается
                     * через раз: на снимке у окна или на светлом фоне
                     * он исчезает. Градиент от прозрачного к тёмному
                     * снизу даёт постоянный контраст, не закрывая
                     * лицо — а лица на портретах обычно вверху.
                     *
                     * ⚠️ Градиент идёт снизу вверх и до половины
                     * высоты: если затемнить весь снимок, портрет
                     * превращается в фон, и смысла показывать его
                     * не остаётся.
                     */


                    /*
                     * Отступ по ВЫСОТЕ.
                     *
                     * Здесь стоял Spacer(Modifier.width(Space.l)) —
                     * остаток от той версии, где портрет и кнопки
                     * стояли в строке. В колонке ширина ничего
                     * не делает, и кнопка «Позвонить» прижималась
                     * вплотную к фотографии.
                     *
                     * ══════════════════════════════════════════════════
                     * ОДИН ОТСТУП, А НЕ ЧЕТЫРЕ
                     * ══════════════════════════════════════════════════
                     * Между фотографией и кнопками стояли четыре
                     * Spacer по 12 dp подряд — наследство прежних
                     * раскладок: один был после фото, один «по высоте»,
                     * один после имени, один перед рядом действий.
                     * Пока под фото было имя, лишнее пряталось за ним;
                     * когда имя переехало на снимок, осталась пустота
                     * в 48 dp — её и было видно на экране.
                     *
                     * Теперь отступ один — здесь. Имя без фотографии
                     * идёт следующей строкой со своим отступом снизу.
                     *
                     * 1.2.5: с фото имя на обложке, кнопкам хватает поля
                     * колонки и 4 dp; без фото — только поле колонки
                     * под строкой заголовка. Было 16 dp сверх поля —
                     * зазор над именем выглядел пустым местом.
                     */
                    Spacer(Modifier.height(if (photoUri.isNullOrBlank()) 0.dp else 4.dp))

                    /*
                     * ══════════════════════════════════════════════════
                     * ПОДПИСЬ — ОДНА, И ТОЛЬКО ТАМ, ГДЕ ЕЁ НЕТ НА ФОТО
                     * ══════════════════════════════════════════════════
                     * Когда фотография есть, имя лежит на ней —
                     * повторять его строкой ниже незачем.
                     *
                     * Номер под именем убран совсем. Рассуждение
                     * простое: если имя есть, значит номер в книге,
                     * а значит он и так перечислен разделом «Номера»
                     * сразу ниже — со своим типом и своими кнопками.
                     * Дублировать его в шапке — тратить строку
                     * на то, что видно через сантиметр.
                     *
                     * А если имени нет, номер сам стал подписью
                     * карточки: показывать его дважды подряд тем
                     * более не нужно.
                     */
                    // Имя — здесь, если фото нет. С фото имя на самой
                    // обложке, внизу (разбор у обложки). В заголовке экрана
                    // оно появляется, только когда шапка уехала вверх
                    if (photoUri.isNullOrBlank()) {
                        Text(
                            title,
                            /*
                             * titleLarge, а не headlineSmall.
                             *
                             * Заголовки экранов в приложении —
                             * titleLarge. Имя в карточке было крупнее
                             * их, и карточка читалась как отдельное
                             * приложение.
                             */
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            /*
                             * По левому краю, а не по центру.
                             *
                             * Под обложкой во всю ширину имя по центру
                             * висело отдельно от всего остального: номера,
                             * заметка, сведения — все начинаются слева.
                             * Слева имя встаёт в одну линию с ними и читается
                             * как заголовок карточки, а не подпись к фото.
                             */
                            textAlign = TextAlign.Start,
                            // Где имя на экране: когда его нижний край уходит
                            // под строку заголовка, имя появляется в ней
                            modifier = Modifier.fillMaxWidth().onGloballyPositioned { coords ->
                                nameBottomInRoot = coords.positionInRoot().y + coords.size.height
                            }
                        )

                        Spacer(Modifier.height(FlowUi.contentPadding))
                    }

                    /*
                     * Прежний комментарий про отсутствие имени.
                     *
                     * Оба переехали в сворачивающуюся шапку: там они
                     * видны сразу и уезжают при прокрутке. Дублировать
                     * их значило бы занимать место дважды и оставить
                     * ниже пустоту, из-за которой статистику
                     * приходилось искать прокруткой.
                     *
                     * Осталось главное действие и ряд быстрых —
                     * то, ради чего карточку открывают.
                     */
                    /*
                     * ГЛАВНОЕ ДЕЙСТВИЕ ОТДЕЛЕНО ОТ ОСТАЛЬНЫХ.
                     *
                     * ══════════════════════════════════════════════════
                     * ПОЧЕМУ ЗВОНОК ВЫНЕСЕН
                     * ══════════════════════════════════════════════════
                     * Раньше четыре кнопки стояли в ряд равными:
                     * звонок, SMS, напоминание, блокировка. Но звонок
                     * — то, ради чего карточку открывают в девяти
                     * случаях из десяти, а блокировка — раз в месяц.
                     *
                     * Равный вид означал равный поиск глазами: каждый
                     * раз приходилось выбирать из четырёх, хотя выбор
                     * почти всегда очевиден.
                     *
                     * Крупная кнопка во всю ширину не требует выбора:
                     * в неё попадают не глядя.
                     *
                     * ── Почему зелёная ─────────────────────────────────
                     * Зелёный — код телефонии, тот же, что у свайпа
                     * вызова и у кнопки ответа. Одно действие должно
                     * выглядеть одинаково везде.
                     */

                    /*
                     * Ряд быстрых действий — второстепенные.
                     *
                     * SpaceEvenly вместо фиксированного промежутка:
                     * подписи разной длины иначе съезжают, и «Заблокировать
                     * номер» вылезало за край, переносясь на две строки
                     * с обрывом.
                     */
                    // Стоя ряд — в нижней панели (quickActions); здесь — только когда
                    // шапка отдельной колонкой слева: там он и так под рукой (1.3.4)
                    if (twoColumns) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.s)
                        ) { quickActions() }
                    }
                }
            }
        }

        // С обложкой список начинается от самого верха — под строкой
        // заголовка: так фото уходит до верха экрана. Без обложки —
        // как раньше, под строкой заголовка
        val layoutDir = LocalLayoutDirection.current
        Column(
            Modifier
                .padding(
                    start = pad.calculateStartPadding(layoutDir),
                    top = if (coverBehindBar) 0.dp else pad.calculateTopPadding(),
                    end = pad.calculateEndPadding(layoutDir),
                    bottom = pad.calculateBottomPadding()
                )
                .fillMaxSize()
        ) {
        Row(Modifier.weight(1f).fillMaxWidth()) {

        if (twoColumns) {
            /*
             * Левая колонка — кто это и что с ним сделать: фото с именем
             * и круглые кнопки действий. Прокручивается сама по себе:
             * на телефоне лёжа высоты ~360 dp, и шапка может в неё
             * не поместиться.
             */
            Column(
                Modifier
                    .width(CARD_SIDE_COLUMN_WIDTH)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                header()
            }
        }

        LazyColumn(
            state = cardListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (landscape) {
                        Modifier.widthIn(max = CARD_CONTENT_MAX_WIDTH)
                    } else {
                        Modifier
                    }
                )
        ) {

            // ── Шапка: аватар и быстрые действия ───────────────────────────
            // Шапка — отдельной колонкой слева, когда колонок две
            if (!twoColumns) item { header() }

            // ── Сводка по вызовам ──────────────────────────────────────────
            /*
             * СВОДКА ПО ВЫЗОВАМ ПЕРЕЕХАЛА В ДИАЛОГ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ
             * ══════════════════════════════════════════════════════════════
             * «70 входящих, 107 исходящих, 290 минут» — интересно, но
             * нужно редко. А занимало это треть первого экрана карточки,
             * отодвигая номера и заметку вниз.
             *
             * Открывается из меню «Ещё», как остальное редкое.
             */

            // ── Несколько номеров у одного абонента ────────────────────────
            /*
             * НОМЕРА ПОКАЗЫВАЮТСЯ ВСЕГДА.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ УСЛОВИЕ УБРАНО
             * ══════════════════════════════════════════════════════════════
             * Раздел показывался только при нескольких номерах —
             * считалось, что единственный и так виден в заголовке.
             *
             * Но заголовок показывает ИМЯ, если контакт известен. У
             * контакта с одним номером номер не показывался нигде:
             * ни сверху, ни в разделе. Чтобы его узнать, приходилось
             * идти в системные контакты.
             *
             * Заодно пропадали кнопки «написать» и «скопировать» —
             * они живут в этом же разделе.
             */
            /*
             * РАЗДЕЛ ОДНОЙ ПАНЕЛЬЮ.
             *
             * Заголовок и номера лежат в общей карточке, а не порознь
             * на фоне экрана: так видно, где раздел начинается
             * и где кончается. Раньше заголовок висел отдельно, номера
             * отдельно — и на длинном экране «Номера» читались как
             * подпись к первому же номеру, а не ко всем.
             */
            item {
                OgnelissPanel(title = stringResource(R.string.subscriber_numbers)) {
            /*
             * НОМЕР — КАРТОЧКА С ТРЕМЯ ДЕЙСТВИЯМИ.
             *
             * ══════════════════════════════════════════════════════════
             * ЧТО БЫЛО НЕ ТАК
             * ══════════════════════════════════════════════════════════
             * Строка только звонила. Чтобы написать на второй номер
             * или скопировать его, приходилось возвращаться в журнал
             * и искать нужную запись — а она могла быть за неделю
             * до этого.
             *
             * У человека с рабочим и личным номером это самый частый
             * повод открыть карточку: выбрать, на какой именно
             * звонить. И там же должно быть всё остальное.
             *
             * ── Почему копирование ─────────────────────────────────────
             * Номер часто нужен не для звонка: продиктовать, вставить
             * в заявку, отправить коллеге. Без кнопки его переписывают
             * с экрана, и там ошибаются.
             */
                /*
                 * ЛИНИИ МЕЖДУ СТРОКАМИ.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ЗАЧЕМ
                 * ══════════════════════════════════════════════════════════
                 * Внутри панели строки шли подряд, отличаясь только
                 * отступом. Когда у контакта три номера с одинаковыми
                 * тремя кнопками справа, глазу не за что зацепиться:
                 * получается решётка из одинаковых элементов, и строка
                 * теряется при взгляде вскользь.
                 *
                 * Линия тонкая и только между строками — не над первой
                 * и не под последней: там уже есть край панели, и вторая
                 * граница рядом выглядит как ошибка вёрстки.
                 */
                group.numbers.forEachIndexed { index, number ->
                    if (index > 0) OgnelissDivider()

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = NUMBER_ROW_HEIGHT)
                            .padding(vertical = Space.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        /*
                         * ТИП НОМЕРА ПОД САМИМ НОМЕРОМ.
                         *
                         * ══════════════════════════════════════════════════
                         * ЗАЧЕМ
                         * ══════════════════════════════════════════════════
                         * У контакта с тремя номерами все три выглядели
                         * одинаково, и выбирать приходилось по памяти:
                         * какой из них рабочий, а какой домашний.
                         * Подпись есть в книге — мы её просто не показывали.
                         *
                         * Берётся из книги как есть: провайдер сам даёт
                         * её на языке системы для стандартных типов
                         * («Мобильный», «Рабочий») и отдаёт своё название,
                         * если человек завёл собственное.
                         *
                         * Второй строкой и мелко: при выборе номера
                         * читают цифры, а тип — уточнение.
                         */
                        val label = cardContact?.numbers
                            ?.firstOrNull { it.value == number || it.e164 == number }
                            ?.label
                            ?.takeIf { it.isNotBlank() }

                        Column(Modifier.weight(1f)) {
                            Text(
                                PhoneFormatter.format(number),
                                style = MaterialTheme.typography.bodyLarge
                            )

                            label?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        NumberAction(OgnelissIcons.Call, R.string.action_call, tint = CallGreen) {
                            vm.call(number)
                        }
                        NumberAction(
                            OgnelissIcons.Message,
                            R.string.action_sms
                        ) {
                            vm.openSms(number)
                        }
                        NumberAction(
                            OgnelissIcons.ContentCopy,
                            R.string.action_copy
                        ) {
                            vm.copyNumber(number)
                        }
                    }
                }
                }
            }

            // ── Заметка и метки ────────────────────────────────────────────
            item {
                /*
                 * Пустая заметка — одна строка (1.3.4, 05.10.2026, задача автора).
                 * Было: целая панель с заголовком «Заметка» ради серой
                 * надписи «Добавить заметку». Стало: без заголовка — значок
                 * заметки, надпись и карандаш в одну строку; заметка есть —
                 * прежняя панель с заголовком, текстом и метками.
                 */
                val noteEmpty = note?.note.isNullOrBlank() && note?.tags.isNullOrEmpty()
                OgnelissPanel(
                    title = if (noteEmpty) null else stringResource(R.string.subscriber_note_section),
                    /*
                     * ══════════════════════════════════════════════════════
                     * ПАНЕЛЬ ЗАМЕТКИ БОЛЬШЕ НЕ ЗАЛИТА ЦВЕТОМ
                     * ══════════════════════════════════════════════════════
                     * Бирюзовая подложка была единственным цветным пятном
                     * на экране карточки. Заметка при этом не важнее
                     * номеров, по которым звонят: цвет расставлял
                     * внимание не по важности.
                     *
                     * Правило теперь общее: цвет отмечает, а не заливает.
                     * Узнаваемость заметки держит значок — тот же
                     * бирюзовый, что в журнале, — а подложка у панели
                     * общая, как у остальных разделов.
                     */
                    color = null,
                    icon = if (noteEmpty) null else {
                        // Двойные скобки: внешние — блок else, внутренние — лямбда
                        // (одинарные вызывали Icon сразу — ошибка сборки 05.10.2026)
                        {
                            Icon(
                                OgnelissIcons.StickyNote2,
                                contentDescription = null,
                                tint = NoteTeal,
                                modifier = Modifier.size(FlowUi.iconSizeInline)
                            )
                        }
                    }
                ) {

                /*
                 * ЗАМЕТКА ВЫДЕЛЕНА КАРТОЧКОЙ.
                 *
                 * ── Что было не так ────────────────────────────────────
                 * Строка ничем не отличалась от соседних, и было
                 * неясно, что с ней делать: то ли это подпись,
                 * то ли поле, то ли просто текст.
                 *
                 * Рамка и подложка говорят «сюда можно нажать»
                 * без лишних слов, а карандаш подтверждает,
                 * что нажатие ведёт к правке.
                 *
                 * ── Метки переносятся по строкам ───────────────────────
                 * Раньше стоял Row: при трёх и более метках часть
                 * уходила за край экрана, и о них было не догадаться.
                 */
                /*
                 * Заметка — такая же карточка, как номера.
                 *
                 * Была OutlinedCard с рамкой, тогда как номера
                 * и остальные разделы — Surface с подложкой. Рамка
                 * выделяла заметку сильнее, чем она того заслуживает,
                 * и выбивалась из списка.
                 *
                 * Отступы из FlowUi, как везде.
                 */
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingNote = true }
                ) {
                    /*
                     * ВЫСОТА КАК У СТРОКИ НОМЕРА.
                     *
                     * Заметка в одну строку была заметно ниже соседних
                     * карточек с номерами: у тех высоту задают кнопки
                     * действий, у заметки — только текст. Разнобой был
                     * виден сразу, потому что панели идут одна под другой.
                     *
                     * Теперь минимум общий, из одной константы: изменится
                     * строка номера — изменится и заметка.
                     */
                    Row(
                        Modifier.heightIn(min = NUMBER_ROW_HEIGHT),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                            if (noteEmpty) {
                                Icon(
                                    OgnelissIcons.StickyNote2,
                                    contentDescription = null,
                                    tint = NoteTeal,
                                    modifier = Modifier.size(FlowUi.iconSizeInline)
                                )
                                Spacer(Modifier.width(Space.m))
                            }
                            Text(
                                note?.note?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.subscriber_note_add),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (note?.note.isNullOrBlank()) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                OgnelissIcons.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (!note?.tags.isNullOrEmpty()) {
                            Spacer(Modifier.height(Space.m))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Space.s),
                                verticalArrangement = Arrangement.spacedBy(Space.xs)
                            ) {
                                note.tags.forEach { tagKey ->
                                    val labelRes = TagCatalog.labelResFor(tagKey)
                                    AssistChip(
                                        onClick = { editingNote = true },
                                        label = {
                                            Text(
                                                if (labelRes != null) stringResource(labelRes)
                                                else tagKey
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            /*
             * ══════════════════════════════════════════════════════════════
             * ОТМЕТКА — СВОИМ РАЗДЕЛОМ, ОТДЕЛЬНО ОТ ЗАМЕТКИ
             * ══════════════════════════════════════════════════════════════
             * До 1.1.1 «спам» и «мошенники» были фишками внутри заметки,
             * и поставить отметку можно было только через редактор
             * текста. Но отметка — не приписка к заметке, а решение
             * о звонках: при включённой блокировке номер перестаёт
             * звонить. Место ей — своей строкой, на виду.
             *
             * Одна фишка из двух: номер либо спам, либо мошенники.
             * Повторное нажатие на выбранную снимает отметку.
             *
             * Предупреждение под фишками появляется, только когда
             * отметка стоит: пустая строка «при включённой блокировке…»
             * у каждого номера превратилась бы в шум.
             */
            if (showMarkSection) item {
                // Значок блокировки у заголовка: «Отметка» сама по себе
                // не говорит, о чём раздел, — значок сразу связывает его
                // с защитой от нежелательных звонков
                OgnelissPanel(
                    title = stringResource(R.string.spam_mark_section),
                    icon = {
                        Icon(
                            OgnelissIcons.Block,
                            contentDescription = null,
                            tint = BlockRed,
                            modifier = Modifier.size(FlowUi.iconSizeInline)
                        )
                    }
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        listOf(
                            SpamMarkType.SPAM to R.string.tag_spam,
                            SpamMarkType.FRAUD to R.string.tag_fraud
                        ).forEach { (type, labelRes) ->
                            val selected = spamMark == type
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptics()
                                    vm.setSpamMark(group.e164, if (selected) null else type)
                                },
                                // С заглавной: в журнале и на экране вызова та же
                                // отметка пишется «Спам», а здесь строчными
                                // выглядела как другая надпись
                                label = { Text(stringResource(labelRes).replaceFirstChar { it.titlecase() }) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            OgnelissIcons.Report,
                                            contentDescription = null,
                                            modifier = Modifier.size(FlowUi.iconSizeInline),
                                            tint = BlockRed
                                        )
                                    }
                                } else null,
                                shape = MaterialTheme.shapes.small
                            )
                        }
                    }

                    if (spamMark != null) {
                        Spacer(Modifier.height(Space.s))
                        Text(
                            stringResource(R.string.note_spam_tag_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            /*
             * ── СВЕДЕНИЯ ИЗ КНИГИ: ПОЧТА И ДАТЫ ────────────────────────────
             *
             * Раздел появляется, только если есть что показать. Пустая
             * панель «Почта: —» не сообщает ничего и отодвигает вниз
             * историю, ради которой карточку чаще всего и открывают.
             *
             * Данные читаются из телефонной книги и здесь не правятся:
             * менять их нужно там, где они хранятся, иначе появится
             * второй источник правды. Для этого в шапке есть
             * «Изменить контакт».
             */
            cardContact?.let { contact ->
                val hasDetails = contact.emails.isNotEmpty() || contact.events.isNotEmpty()
                if (hasDetails) {
                    item {
                        OgnelissPanel(title = stringResource(R.string.subscriber_details)) {
                            contact.emails.forEachIndexed { index, email ->
                                if (index > 0) OgnelissDivider()
                                /*
                                 * Было (до 1.3.2): только нажатие на всю строку
                                 * открывало почту; скопировать адрес было нельзя.
                                 * Стало (1.3.2, 01.10.2026): справа те же кнопки,
                                 * что у номеров, — «Написать» и «Скопировать».
                                 * Нажатие на строку оставлено: к нему привыкли.
                                 */
                                DetailRow(
                                    icon = OgnelissIcons.Email,
                                    value = email,
                                    label = null,
                                    onClick = { vm.openEmail(email) }
                                ) {
                                    NumberAction(
                                        OgnelissIcons.Send,
                                        R.string.action_write_email
                                    ) {
                                        vm.openEmail(email)
                                    }
                                    NumberAction(
                                        OgnelissIcons.ContentCopy,
                                        R.string.action_copy_email
                                    ) {
                                        vm.copyEmail(email)
                                    }
                                }
                            }

                            contact.events.forEachIndexed { index, event ->
                                // Линия и перед первой датой, если выше
                                // была почта: это разные виды сведений
                                if (index > 0 || contact.emails.isNotEmpty()) {
                                    OgnelissDivider()
                                }
                                DetailRow(
                                    icon = OgnelissIcons.Cake,
                                    value = event.date,
                                    label = event.label,
                                    onClick = null
                                )
                            }
                        }
                    }
                }
            }

            /*
             * ── НАПОМИНАНИЕ ────────────────────────────────────────────────
             *
             * Сразу после заметки: и то, и другое — то, что человек
             * добавил сам, в отличие от номеров и истории, которые
             * пришли из системы.
             *
             * Блок появляется, только когда напоминание есть. Пустая
             * панель «напоминаний нет» ничего не сообщала бы: поставить
             * напоминание можно кнопкой в шапке, и дублировать её здесь
             * незачем.
             */
            reminder?.let { active ->
                item {
                    OgnelissPanel(title = stringResource(R.string.reminder_dialog_title)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = NUMBER_ROW_HEIGHT),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                OgnelissIcons.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    reminderDateFormat.format(
                                        java.util.Date(active.atMillis)
                                    ),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                /*
                                 * Сколько осталось — второй строкой.
                                 *
                                 * Дата отвечает «когда», остаток — «скоро
                                 * ли». Второе человек считает в уме
                                 * каждый раз, когда видит только дату.
                                 */
                                Text(
                                    remainingLabel(active.remainingMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            TextButton(onClick = { pendingReminderAt = active.atMillis }) {
                                Text(stringResource(R.string.action_edit))
                            }
                            TextButton(
                                onClick = {
                                    haptics()
                                    vm.cancelReminder(group.number)
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }

            // ── Другие контакты с этим номером ─────────────────────────────
            if (sameNumberContacts.size > 1) {
                item {
                    /*
                     * Список, а не предложение объединить.
                     *
                     * Объединение контактов — операция провайдера, и делать
                     * её за человека нельзя: он мог завести две записи
                     * намеренно, например рабочую и личную с разными
                     * мелодиями.
                     *
                     * Показываем, что есть, и даём открыть каждую. Решение
                     * остаётся за ним, а объединить он может в системном
                     * приложении контактов — там для этого есть всё.
                     */
                    SectionHeader(stringResource(R.string.subscriber_same_number))

                    sameNumberContacts.forEach { contact ->
                        OgnelissListRow(
                            headline = contact.displayName,
                            // Пометка только у того, чьё имя стоит в шапке:
                            // у остальных строка чистая
                            supporting = stringResource(R.string.subscriber_shown_here)
                                .takeIf { contact.id == knownContactId },
                            leading = {
                                Icon(OgnelissIcons.Person, contentDescription = null)
                            },
                            onClick = { vm.editContact(contact.id) }
                        )
                    }
                }
            }


            // ── Лента вызовов ──────────────────────────────────────────────
            /*
             * ИСТОРИЯ — ЗАГОЛОВОК ПАНЕЛИ, НО НЕ ОДНА ПАНЕЛЬ.
             *
             * У номеров и заметки раздел целиком лежит в одной карточке:
             * их единицы. С историей так нельзя — в ней бывает и триста
             * записей, а всё содержимое одной панели пришлось бы держать
             * в одном элементе списка, без переиспользования строк.
             * На таком экране прокрутка начинает заикаться.
             *
             * Поэтому здесь заголовок панели, а строки идут отдельными
             * элементами того же цвета: визуально это тот же раздел,
             * но LazyColumn переиспользует строки как обычно.
             */
            item { SectionPanelHeader(stringResource(R.string.subscriber_history)) }
            /*
             * ИСТОРИЯ ПОКАЗЫВАЕТСЯ ЧАСТИЧНО.
             *
             * ── Зачем ──────────────────────────────────────────────────────
             * У человека, с которым общаются часто, история занимает
             * десятки экранов. Всё, что под ней — выгрузка, заметка,
             * другие контакты, — оказывается недосягаемым: чтобы
             * добраться, нужно прокрутить весь список.
             *
             * Четырёх последних хватает, чтобы понять, когда общались
             * в последний раз. Остальное раскрывается по нажатию.
             */
            val visibleHistory = if (historyExpanded) history
            else history.take(FlowUi.COLLAPSED_HISTORY)

            /*
             * Группы по датам, как в журнале (1.3.4, 05.10.2026): заголовок
             * «Сегодня», «Вчера», «На этой неделе» — тот же LetterHeader,
             * деление — CallLogDateGroup.of, общий с журналом.
             */
            visibleHistory.groupBy { CallLogDateGroup.of(it.date) }.forEach { (bucket, entriesInBucket) ->
                item(key = "history_${bucket.ordinal}") {
                    LetterHeader(stringResource(dateGroupLabel(bucket)))
                }
                itemsIndexed(entriesInBucket, key = { _, entry -> entry.id }) { index, entry ->
                    /*
                     * История — та же решётка из похожих строк: значок,
                     * дата, длительность. Линии между ними нужны по той же
                     * причине, что и у номеров.
                     *
                     * Первая строка без линии: над ней заголовок раздела.
                     */
                    /*
                     * ══════════════════════════════════════════════════════════
                     * ИСТОРИЯ ВЫГЛЯДИТ КАК ОСТАЛЬНЫЕ ПАНЕЛИ КАРТОЧКИ
                     * ══════════════════════════════════════════════════════════
                     * Строки истории шли во всю ширину экрана, с подложкой
                     * от края до края и без скруглений, — а «Номера»,
                     * «Заметка» и «Сведения» над ними стоят панелями
                     * с полями и скруглёнными углами. Из-за этого низ карточки
                     * выглядел приклеенным к чужому экрану.
                     *
                     * Теперь список — одна панель: поля по бокам те же,
                     * что у соседей, верхние углы скруглены у первой строки,
                     * нижние — у последней. Кнопка «Показать ещё» идёт ниже
                     * и панель не продолжает: это действие, а не запись.
                     *
                     * ⚠️ Углы считаются по позиции в списке, а не рисуются
                     * панелью вокруг: история — это элементы LazyColumn,
                     * и обернуть их одним контейнером значило бы собирать
                     * все двести записей сразу.
                     */
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) HISTORY_CORNER else 0.dp,
                        topEnd = if (index == 0) HISTORY_CORNER else 0.dp,
                        bottomStart = if (index == entriesInBucket.lastIndex) HISTORY_CORNER else 0.dp,
                        bottomEnd = if (index == entriesInBucket.lastIndex) HISTORY_CORNER else 0.dp
                    )

                    Column(
                        Modifier
                            .padding(horizontal = FlowUi.screenPadding)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        // Линия между строками, но не над первой: там уже
                        // заголовок раздела, и вторая граница рядом лишняя
                        if (index > 0) {
                            OgnelissDivider(Modifier.padding(horizontal = FlowUi.contentPadding))
                        }

                        CallHistoryRow(
                            entry,
                            showNumber = group.numbers.size > 1,
                            timeOnly = bucket == CallLogDateGroup.TODAY || bucket == CallLogDateGroup.YESTERDAY
                        )
                    }
                }
            }

            /*
             * Кнопка разворачивания истории.
             *
             * Без неё записи старше четырёх недоступны вовсе: список
             * обрезан, а способа увидеть остальное нет.
             *
             * Число в подписи не случайно: видно, сколько ещё есть,
             * и стоит ли разворачивать — при трёх записях это одно
             * решение, при двухстах другое.
             */
            if (history.size > FlowUi.COLLAPSED_HISTORY) {
                item {
                    /*
                     * Кнопка, а не ссылка.
                     *
                     * TextButton читается как ссылка: текст без границ,
                     * и непонятно, что по нему нажимают. FilledTonalButton
                     * говорит это формой — не притягивая внимания
                     * сильнее, чем заслуживает второстепенное действие.
                     */
                    FilledTonalButton(
                        onClick = { historyExpanded = !historyExpanded },
                        /*
                         * Отступы сверху и снизу.
                         *
                         * Был только боковой, и кнопка прилипала
                         * к последней строке истории сверху и к краю
                         * экрана снизу: выглядело так, будто список
                         * обрезали посередине.
                         *
                         * Снизу больше, чем сверху: под кнопкой конец
                         * прокрутки, и на телефонах с жестовой навигацией
                         * полоса жеста проходит вплотную к нижнему краю.
                         */
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = FlowUi.screenPadding,
                                end = FlowUi.screenPadding,
                                top = Space.m,
                                bottom = Space.xl
                            )
                            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                    ) {
                        Text(
                            if (historyExpanded) {
                                stringResource(R.string.history_collapse)
                            } else {
                                stringResource(
                                    R.string.history_expand,
                                    history.size - FlowUi.COLLAPSED_HISTORY
                                )
                            }
                        )
                        // Стрелка вплотную к тексту читалась как часть
                        // подписи: «Показать ещё 249˅»
                        Spacer(Modifier.width(Space.s))
                        Icon(
                            if (historyExpanded) OgnelissIcons.ExpandLess
                            else OgnelissIcons.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            // ── Действия с контактом ───────────────────────────────────────
            /*
             * ── Выгрузка истории: внизу ────────────────────────────────
             *
             * Ею пользуются редко — при споре с оператором или для
             * заявления. Держать её выше истории значило отодвигать
             * то, ради чего карточку открывают чаще всего.
             */

            /*
             * Выгрузка истории только в меню «Ещё».
             *
             * Здесь она дублировалась: тот же пункт был и внизу списка,
             * и в меню. Два входа в одно действие не дают выбора,
             * а занимают место и заставляют гадать, есть ли разница.
             */

            /*
             * Раздел «Действия с контактом» переехал в меню «Ещё».
             *
             * Мелодия звонка и правка контакта — раз в месяц, а строки
             * занимали место на каждом открытии карточки.
             */

            item { Spacer(Modifier.height(Space.xxl)) }
        }
        }

        // Быстрые действия — в нижней панели (стоя); см. quickActions
        if (!twoColumns) {
            Surface(
                shape = OgnelissShapeTokens.bottomPanel,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FlowUi.contentPadding, vertical = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s)
                ) { quickActions() }
            }
        }
        }
    }

    // Удаление контакта необратимо и затрагивает все аккаунты —
    // подтверждение обязательно (DeleteContactDialog, 1.3.4)
    if (confirmDelete && knownContactId != null) {
        DeleteContactDialog(
            pinned = vm.isContactPinned(knownContactId),
            onConfirm = {
                vm.deleteContact(knownContactId)
                confirmDelete = false
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }

    if (editingNote) {
        NoteEditorDialog(
            initialNote = note?.note.orEmpty(),
            initialTags = note?.tags?.toSet() ?: emptySet(),
            onSave = { text, tags ->
                vm.saveNote(group.e164, text, tags.toList())
                editingNote = false
            },
            onDismiss = { editingNote = false }
        )
    }
}
