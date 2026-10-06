package com.ogneliss.dialer.ui.screens

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.FlowUi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.ui.components.SheetDivider
import com.ogneliss.dialer.ui.components.SheetRow
import com.ogneliss.dialer.ui.components.SheetSwitchRow
import com.ogneliss.dialer.ui.components.SheetGroup
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.ui.components.swipe.RevealActionFactory
import com.ogneliss.dialer.data.ContactMessengers
import com.ogneliss.dialer.data.CallLogEntry
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.util.PhoneFormatter
import java.text.DateFormat
import java.util.Date

/*
 * Карточка абонента — строки истории, разделы, меню «Ещё», строка сведений, шторка «Написать».
 *
 * Вынесено из SubscriberScreen.kt (1.3.4, 04.10.2026, фаза 4 — деление экранов
 * длиннее 2000 строк): вспомогательные части экрана. Видимость private →
 * internal — ими пользуется основной файл. Код не менялся.
 */

/**
 * Скругление углов панели истории.
 *
 * Совпадает с shapes.large темы — тем же, каким скруглены панели
 * «Номера» и «Заметка»: разные радиусы у соседних блоков видны сразу.
 */
internal val HISTORY_CORNER = 16.dp

/** Одна запись в ленте вызовов абонента. */
@Composable
internal fun CallHistoryRow(
    entry: CallLogEntry,
    /**
     * Показывать ли номер в строке.
     *
     * У абонента с одним номером он дублировал бы заголовок карточки —
     * лишний шум. У абонента с несколькими это главное, чего не хватает:
     * «звонил на рабочий или на личный» — вопрос, ради которого
     * в историю и заходят.
     */
    showNumber: Boolean = false,
    /** Только время: день уже в заголовке группы («Сегодня», «Вчера») — 1.3.4. */
    timeOnly: Boolean = false
) {
    // Формат даты зависит от локали, поэтому пересоздаётся при её смене.
    // Статический форматтер показывал бы даты на прежнем языке
    // после переключения языка приложения
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration)[0] ?: java.util.Locale.getDefault()

    val typeRes = when (entry.type) {
        CallType.INCOMING -> R.string.call_incoming
        CallType.OUTGOING -> R.string.call_outgoing
        CallType.MISSED -> R.string.call_missed
        CallType.REJECTED -> R.string.call_rejected
        CallType.BLOCKED -> R.string.call_blocked
        CallType.VOICEMAIL -> R.string.call_voicemail
    }
    val icon = when (entry.type) {
        CallType.OUTGOING -> OgnelissIcons.CallMade
        CallType.MISSED -> OgnelissIcons.CallMissed
        CallType.REJECTED, CallType.BLOCKED -> OgnelissIcons.Block
        else -> OgnelissIcons.CallReceived
    }
    val tint = when (entry.type) {
        CallType.MISSED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    ListItem(
        colors = ListItemDefaults.colors(
            // Прозрачная: цвет даёт панель вокруг списка, а своя подложка
            // легла бы вторым слоем поверх и была бы видна на тёмной теме
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        /*
         * Компактная строка (1.3.4, 05.10.2026, задача автора: «история —
         * как журнал»). Было: три строки — «Исходящий / Вчера, 14:35 /
         * номер», тип словом дублировал значок, дата — заголовок группы.
         * Стало: тип — значком (TalkBack читает его словом), пропущенный —
         * красным; строка — время (в группах «Сегодня», «Вчера» — только
         * время) и длительность справа; номер — второй строкой, только
         * если у абонента номеров несколько.
         */
        leadingContent = { Icon(icon, contentDescription = stringResource(typeRes), tint = tint) },
        headlineContent = {
            Text(
                if (timeOnly) {
                    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale)
                        .format(java.util.Date(entry.date))
                } else {
                    relativeDateLabel(entry.date, locale)
                },
                color = if (entry.type == CallType.MISSED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        },
        supportingContent = if (showNumber) {
            {
                // Номер второй строкой: у абонента с несколькими номерами
                // это главное, чего не хватает в истории
                Text(
                    PhoneFormatter.format(entry.number),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            null
        },
        trailingContent = {
            if (entry.durationSec > 0) {
                Text(
                    String.format(
                        java.util.Locale.getDefault(), "%d:%02d",
                        entry.durationSec / 60, entry.durationSec % 60
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

/**
 * Высота строки в разделах карточки.
 *
 * Задаётся здесь, а не в каждом месте: строку номера растягивают кнопки
 * действий, а заметку — только текст, и без общей константы они
 * расходились по высоте на глаз.
 */
internal val NUMBER_ROW_HEIGHT = 56.dp

/**
 * «Через 2 ч 15 мин» — сколько осталось до напоминания.
 *
 * Минуты показываются только когда их меньше суток: «через 3 дня
 * 4 часа» человек не считает, ему достаточно «через 3 дня».
 */
@Composable
internal fun remainingLabel(remainingMillis: Long): String {
    val minutes = (remainingMillis / 60_000L).coerceAtLeast(0)
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days >= 1 -> pluralStringResource(R.plurals.reminder_in_days, days.toInt(), days.toInt())
        hours >= 1 -> pluralStringResource(
            R.plurals.reminder_in_hours, hours.toInt(), hours.toInt()
        )
        else -> pluralStringResource(
            R.plurals.reminder_in_minutes, minutes.toInt(), minutes.toInt()
        )
    }
}

/**
 * ДАТА ЗВОНКА СЛОВАМИ ДЛЯ БЛИЖАЙШИХ ДНЕЙ.
 *
 * ── Зачем ──────────────────────────────────────────────────────────────────
 * «5 сент. 2026 г. 18:02» человек читает и сравнивает с сегодняшним
 * числом в уме. «Сегодня, 18:02» не требует ничего сравнивать —
 * а именно вчерашние и сегодняшние звонки в истории смотрят чаще всего.
 *
 * Дальше двух дней слова бесполезны: «12 дней назад» хуже даты, потому
 * что по дате видно и день недели, и месяц. Поэтому словами показываются
 * только сегодня и вчера, остальное — обычной датой.
 *
 * ── Почему не DateUtils ────────────────────────────────────────────────────
 * Системный DateUtils.getRelativeTimeSpanString для старых дат отдаёт
 * «N дней назад» и не даёт это отключить. Здесь нужен ровно один порог,
 * и он задан явно.
 */
@Composable
internal fun relativeDateLabel(date: Long, locale: java.util.Locale): String {
    val timeFormat = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    val dateTimeFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }
    // «17 сентября» без года: шаблон берётся у системы, порядок слов
    // в языках разный
    val sameYearFormat = remember(locale) {
        java.text.SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "d MMMM"),
            locale
        )
    }

    val days = daysAgo(date)
    val time = timeFormat.format(Date(date))
    return when (days) {
        0 -> stringResource(R.string.date_group_today) + ", " + time
        1 -> stringResource(R.string.date_group_yesterday) + ", " + time
        /*
         * Старше вчерашнего — дата целиком. Но год показывается,
         * только если он не текущий: «17 сент. 2026 г., 23:08»
         * в сентябре 2026-го — четыре лишних знака в каждой строке,
         * а у абонента таких строк десятки.
         */
        else -> {
            val sameYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) ==
                java.util.Calendar.getInstance().apply { timeInMillis = date }
                    .get(java.util.Calendar.YEAR)

            if (sameYear) {
                sameYearFormat.format(Date(date)) + ", " + time
            } else {
                dateTimeFormat.format(Date(date))
            }
        }
    }
}

/**
 * Сколько КАЛЕНДАРНЫХ дней назад был момент.
 *
 * Считается по датам, а не по разнице в миллисекундах: звонок в 23:50
 * и звонок в 00:10 разделяют двадцать минут, но это «вчера» и «сегодня»,
 * и человек их так и различает.
 */
internal fun daysAgo(date: Long): Int {
    fun startOfDay(millis: Long) = java.util.Calendar.getInstance().apply {
        timeInMillis = millis
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    val diff = startOfDay(System.currentTimeMillis()) - startOfDay(date)
    return (diff / (24L * 60 * 60 * 1000)).toInt()
}


/** Заголовок раздела, содержимое которого идёт отдельными элементами списка. */
@Composable
internal fun SectionPanelHeader(text: String) {
    Text(
        text,
        Modifier
            .padding(
                start = FlowUi.screenPadding + Space.l,
                end = FlowUi.screenPadding,
                top = FlowUi.sheetPadding,
                bottom = Space.xs
            )
            .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        // Отступ сверху больше, чем снизу: заголовок отделяет разделы,
        // и воздух нужен перед ним, а не после
        Modifier
            .padding(start = Space.l, top = FlowUi.sheetPadding, bottom = Space.xs)
            .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        /*
         * Тёмно-серый, а не акцентный.
         *
         * Заголовок раздела — служебная разметка, а не то, ради чего
         * экран открывают. Акцентный цвет притягивал взгляд к подписям
         * вместо содержимого.
         */
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun StatCell(value: Int, labelRes: Int, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            // headlineSmall вместо числа: то же начертание, но из шкалы,
            // и при системном крупном шрифте оно масштабируется вместе
            // со всем остальным
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = if (highlight && value > 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * БЫСТРОЕ ДЕЙСТВИЕ В ШАПКЕ КАРТОЧКИ.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ПОЧЕМУ ПЛИТКА, А НЕ КРУГЛАЯ КНОПКА
 * ══════════════════════════════════════════════════════════════════════════
 * Были круглые кнопки с подписью снизу. Нажимаемым при этом был только
 * кружок 56 dp, а подпись под ним — нет: человек целился в слово
 * «Напомнить перезвонить» и промахивался мимо кнопки.
 *
 * Плитка нажимается целиком, вместе с подписью. Четыре плитки делят
 * ширину поровну через weight, поэтому подписи разной длины больше
 * не растаскивают ряд: «SMS» и «Напомнить перезвонить» занимают
 * одинаковые ячейки.
 *
 * Скруглённый прямоугольник — та же форма, что у карточек номеров
 * и заметки ниже. Круг был единственной круглой формой на экране.
 */
/**
 * КНОПКА ДЕЙСТВИЯ У НОМЕРА.
 *
 * ── Почему без подписи ─────────────────────────────────────────────────────
 * Три кнопки в строке с номером: подписи под ними не поместились бы,
 * а сами значки узнаваемы — трубка, конверт, копия. Это те же значки,
 * что и в остальном приложении.
 *
 * Для TalkBack подпись есть: contentDescription читается вслух,
 * и незрячий человек получает то же, что зрячий видит.
 */
@Composable
internal fun NumberAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    /**
     * Цвет значка.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НЕ ВСЕ СИНИЕ
     * ══════════════════════════════════════════════════════════════════════
     * Все три значка у номера были синими (primary). А звонок во всём
     * остальном приложении зелёный: в журнале, в контактах, круглая кнопка
     * в этой же карточке. Одно действие — два цвета на одном экране.
     *
     * Теперь: звонок — зелёный, как везде; SMS и копирование — нейтральные.
     * Три синих значка в ряд спорили между собой, а главное действие
     * у номера одно — позвонить.
     */
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(Dimens.MIN_TOUCH_TARGET)
    ) {
        Icon(
            icon,
            contentDescription = stringResource(labelRes),
            tint = tint
        )
    }
}

/**
 * МЕНЮ «ЕЩЁ» В ШАПКЕ КАРТОЧКИ.
 *
 * ── Что сюда попало ────────────────────────────────────────────────────────
 * Действия, которыми пользуются раз в месяц: QR-код, выгрузка истории,
 * мелодия звонка, беззвучный режим, ярлык, правка контакта.
 *
 * Каждое из них занимало строку списка на КАЖДОМ открытии карточки
 * ради случая, который может не наступить вовсе.
 *
 * ── Почему отдельным компонентом ───────────────────────────────────────────
 * Шапка и без него длинная: заголовок, звезда, шесть пунктов меню.
 * Читать её целиком в одном месте тяжело, а меню — самостоятельный
 * кусок, который к остальной шапке отношения не имеет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriberMoreMenu(
    knownContactId: Long?,
    onStats: () -> Unit,
    onQr: () -> Unit,
    onExport: () -> Unit,
    onRingtone: () -> Unit,
    /** Состояние «на голосовую почту»; null — ещё читается. */
    silent: Boolean?,
    onSilent: (Long, Boolean) -> Unit,
    onShortcut: () -> Unit,
    onEdit: (Long) -> Unit,
    /** Удаление контакта — с подтверждением в карточке (1.3.4). */
    onDelete: () -> Unit,
    /** Показать блок отметки у контакта; null — блок и так виден (1.3.4). */
    onMarkSpam: (() -> Unit)? = null,
    /*
     * Открывается кнопкой «Ещё» в нижнем ряду карточки (1.3.5): своей
     * кнопки ⋮ в верхней панели у меню больше нет.
     */
    open: Boolean,
    onDismiss: () -> Unit,
    /** Пункт «Блок» / «Разблок.» — перенесён сюда из нижнего ряда (1.3.5). */
    blockLabelRes: Int,
    blockActive: Boolean,
    onBlock: () -> Unit
) {

    /*
     * Меню карточки — шторка снизу с группами, как «Ещё» в журнале
     * и меню записи (1.2.2). Было стандартное выпадающее меню с углами
     * 4 dp и тенью — последнее такое в приложении.
     */
    if (open) {
        val summaryLabel = stringResource(R.string.subscriber_summary)
        val qrLabel = stringResource(R.string.subscriber_qr)
        val exportLabel = stringResource(R.string.subscriber_export)
        val ringtoneLabel = stringResource(R.string.contact_ringtone)
        val silentLabel = stringResource(R.string.contact_silent)
        val shortcutLabel = stringResource(R.string.contact_pin_shortcut)
        val editLabel = stringResource(R.string.action_edit_contact)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { WindowInsets.navigationBars }
        ) {
            Column(Modifier.padding(bottom = Space.l)) {
                // Про номер: сводка, поделиться, выгрузить
                SheetGroup {
                    SheetRow(OgnelissIcons.BarChart, summaryLabel, onClick = { onDismiss(); onStats() })
                    SheetDivider()
                    SheetRow(OgnelissIcons.QrCode2, qrLabel, onClick = { onDismiss(); onQr() })
                    SheetDivider()
                    SheetRow(OgnelissIcons.Share, exportLabel, onClick = { onDismiss(); onExport() })
                }
                // Про контакт в книге — только если он там есть
                if (knownContactId != null) {
                    SheetGroup {
                        SheetRow(OgnelissIcons.MusicNote, ringtoneLabel, onClick = { onDismiss(); onRingtone() })
                        SheetDivider()
                        /*
                         * Переключатель с состоянием (1.3.4, 04.10.2026).
                         * Было: строка без состояния, и нажатие всегда
                         * ВКЛЮЧАЛО отправку на голосовую почту — выключить
                         * из приложения было нельзя. Стало: видно, включено
                         * ли; нажатие переключает в обе стороны; шторка
                         * не закрывается — результат виден сразу.
                         */
                        SheetSwitchRow(
                            OgnelissIcons.NotificationsOff,
                            silentLabel,
                            checked = silent == true,
                            enabled = silent != null,
                            onToggle = { onSilent(knownContactId, it) }
                        )
                        SheetDivider()
                        SheetRow(
                            OgnelissIcons.AddToHomeScreen,
                            shortcutLabel,
                            onClick = { onDismiss(); onShortcut() }
                        )
                        SheetDivider()
                        SheetRow(OgnelissIcons.Edit, editLabel, onClick = { onDismiss(); onEdit(knownContactId) })
                        if (onMarkSpam != null) {
                            SheetDivider()
                            SheetRow(
                                OgnelissIcons.Report,
                                stringResource(R.string.action_mark_spam),
                                onClick = { onDismiss(); onMarkSpam() }
                            )
                        }
                        SheetDivider()
                        // Необратимое — красным, последним в группе (1.3.4)
                        SheetRow(
                            OgnelissIcons.Delete,
                            stringResource(R.string.action_delete_contact),
                            color = BlockRed,
                            onClick = { onDismiss(); onDelete() }
                        )
                    }
                }
                // Блокировка — последней, отдельной группой (1.3.5): перенесена
                // из нижнего ряда, где стояла над системной «Назад». Красным,
                // пока номер не заблокирован; «Разблокировать» — основным цветом
                SheetGroup {
                    SheetRow(
                        OgnelissIcons.Block,
                        stringResource(blockLabelRes),
                        color = if (blockActive) MaterialTheme.colorScheme.primary else BlockRed,
                        onClick = { onDismiss(); onBlock() }
                    )
                }
            }
        }
    }
}


/**
 * Строка раздела «Сведения».
 *
 * @param label подпись типа («День рождения»). У почты её нет: адрес
 *   говорит сам за себя, а «Электронная почта: почта» — это шум.
 * @param onClick null, если нажимать не на что. Дата никуда не ведёт:
 *   открывать календарь на чужой день рождения человек не просил.
 */
@Composable
internal fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String?,
    onClick: (() -> Unit)?,
    // Кнопки справа, как у номеров (1.3.2)
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = NUMBER_ROW_HEIGHT)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

/**
 * Ширина левой колонки карточки — ширина обычного телефона: пять
 * круглых кнопок действий помещаются так же, как в портрете.
 * Та же, что у клавиатуры сбоку в журнале.
 */
internal val CARD_SIDE_COLUMN_WIDTH = 360.dp

/**
 * Предел ширины правой колонки. 640, а не прежние 560: рядом с колонкой
 * шапки правая и так уже, а на планшете строка номера с тремя кнопками
 * во всю оставшуюся ширину растянулась бы на тысячу точек.
 */
internal val CARD_CONTENT_MAX_WIDTH = 640.dp

/** Ключ карточки, который сам является номером: «+79101234567», «89101234567». */
internal val BARE_NUMBER = Regex("""^\+?\d{3,}$""")


/**
 * Шторка «Написать»: SMS на каждый номер контакта — одной группой,
 * мессенджеры — второй, со значками и цветами тех же, что у свайпов.
 * Если один номер есть во всех четырёх мессенджерах — четыре строки:
 * шторка открывается только по нажатию и карточку не загромождает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WriteSheet(
    numbers: List<String>,
    links: List<ContactMessengers.Link>,
    onSms: (String) -> Unit,
    onMessenger: (ContactMessengers.Link) -> Unit,
    onDismiss: () -> Unit,
    /*
     * Мессенджеры без записи в книге (1.3.5, настройка «Мессенджеры для
     * любого номера», отзыв в Google Play): установленные, которых нет
     * среди links; открываются по номеру карточки.
     */
    byNumber: List<SwipeAction> = emptyList(),
    byNumberTarget: String = "",
    onMessengerByNumber: (SwipeAction) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        val smsLabel = stringResource(R.string.action_sms)
        Column(Modifier.padding(bottom = Space.l)) {
            SheetGroup {
                numbers.forEachIndexed { index, number ->
                    SheetRow(
                        OgnelissIcons.Message,
                        smsLabel,
                        // Номер — вторая строка, только если номеров несколько
                        supporting = PhoneFormatter.format(number).takeIf { numbers.size > 1 },
                        onClick = { onSms(number) }
                    )
                    if (index < numbers.lastIndex) SheetDivider()
                }
            }
            SheetGroup {
                links.forEachIndexed { index, link ->
                    val visual = RevealActionFactory.iconOf(link.kind)
                    SheetRow(
                        visual?.first ?: OgnelissIcons.Message,
                        stringResource(
                            when (link.kind) {
                                SwipeAction.WHATSAPP -> R.string.action_whatsapp
                                SwipeAction.TELEGRAM -> R.string.action_telegram
                                SwipeAction.VIBER -> R.string.action_viber
                                else -> R.string.action_max
                            }
                        ),
                        iconTint = visual?.second,
                        // Номер — чтобы различать, если номеров у контакта несколько
                        supporting = link.summary?.let {
                            if (it.startsWith("+")) PhoneFormatter.format(it) else it
                        }.takeIf { numbers.size > 1 || links.count { l -> l.kind == link.kind } > 1 },
                        onClick = { onMessenger(link) }
                    )
                    if (index < links.lastIndex || byNumber.isNotEmpty()) SheetDivider()
                }
                byNumber.forEachIndexed { index, kind ->
                    val visual = RevealActionFactory.iconOf(kind)
                    SheetRow(
                        visual?.first ?: OgnelissIcons.Message,
                        stringResource(
                            when (kind) {
                                SwipeAction.WHATSAPP -> R.string.action_whatsapp
                                SwipeAction.TELEGRAM -> R.string.action_telegram
                                SwipeAction.VIBER -> R.string.action_viber
                                else -> R.string.action_max
                            }
                        ),
                        iconTint = visual?.second,
                        // Номер — если у контакта их несколько: по какому откроется чат
                        supporting = PhoneFormatter.format(byNumberTarget).takeIf { numbers.size > 1 },
                        onClick = { onMessengerByNumber(kind) }
                    )
                    if (index < byNumber.lastIndex) SheetDivider()
                }
            }
        }
    }
}
