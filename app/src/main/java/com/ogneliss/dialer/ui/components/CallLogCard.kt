package com.ogneliss.dialer.ui.components

import com.ogneliss.dialer.ui.theme.Space

import com.ogneliss.dialer.ui.theme.OgnelissIcons

import com.ogneliss.dialer.ui.theme.NoteTeal
import com.ogneliss.dialer.ui.theme.Dimens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.ogneliss.dialer.ui.theme.CallGreen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.ui.components.swipe.SwipeRevealRow
import com.ogneliss.dialer.ui.theme.LocalAppearance

/**
 * КАРТОЧКА ВЫЗОВА В ЖУРНАЛЕ.
 *
 * ── Почему карточка, а не строка списка ────────────────────────────────────
 * Раньше это была плоская строка `ListItem` на общем фоне. Записи
 * сливались в сплошную ленту, и при беглом взгляде было трудно понять,
 * где кончается одна и начинается другая — особенно когда у записи
 * две-три строки подписи.
 *
 * Карточка со своей поверхностью и скруглением очерчивает запись
 * физически. Это же даёт понятную цель для свайпа: видно, что именно
 * уезжает вбок.
 *
 * ── Уровень поверхности ────────────────────────────────────────────────────
 * surfaceContainerLow: чуть светлее фона в светлой теме и чуть светлее
 * чёрного в тёмной. Разница небольшая намеренно — карточки не должны
 * перетягивать внимание с содержимого на себя.
 *
 * ── Что показано и в каком порядке ─────────────────────────────────────────
 * Имя крупно, номер и время мельче под ним. Справа — время последнего
 * вызова и счётчики. Пропущенные выделены цветом ошибки: это
 * единственное, что человек ищет в журнале взглядом.
 */
@Composable
fun CallLogCard(
    /**
     * Готовое состояние строки.
     *
     * Все строки уже отформатированы: карточка ничего не считает.
     * Раньше здесь на каждом кадре прокрутки разбирался номер через
     * libphonenumber и собиралась подпись определителя — при десяти
     * карточках на экране это до шестисот разборов в секунду
     * при бюджете 16 мс на кадр.
     */
    row: CallLogRowState,
    /**
     * Звонок по нажатию на кнопку в строке.
     *
     * null — кнопки нет. Так, например, в списке найденных контактов,
     * где строка целиком означает «открыть карточку».
     */
    onCall: ((CallLogRowState) -> Unit)? = null,
    searchQuery: String,
    selected: Boolean,
    /**
     * Активен ли режим множественного выбора.
     *
     * Нужен отдельно от `selected`: в режиме выбора аватар заменяется
     * кружком у ВСЕХ строк, включая неотмеченные. Иначе непонятно,
     * какие строки вообще можно отмечать.
     */
    selectionActive: Boolean = false,
    swipeKey: String,
    swipeController: com.ogneliss.dialer.ui.components.swipe.SwipeRevealController,
    swipeTuning: com.ogneliss.dialer.settings.SwipeTuning,
    /** Список прокручивается — свайп на это время выключен. */
    listScrolling: Boolean = false,
    /** Вид прямого действия для подсказки при свайпе. */
    directStartAction: com.ogneliss.dialer.ui.components.swipe.RevealAction? = null,
    directEndAction: com.ogneliss.dialer.ui.components.swipe.RevealAction? = null,
    startActions: List<com.ogneliss.dialer.ui.components.swipe.RevealAction>,
    endActions: List<com.ogneliss.dialer.ui.components.swipe.RevealAction>,
    onDirectStart: (() -> Unit)?,
    onDirectEnd: (() -> Unit)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /**
     * Место строки в группе — для вида журнала «Группы по дням».
     * SINGLE — отдельная карточка, как было всегда. Разбор — в JournalStyle.
     */
    groupPosition: GroupPosition = GroupPosition.SINGLE
) {
    /*
     * ФОРМА И ОТСТУПЫ ПО МЕСТУ В ГРУППЕ.
     *
     * Строки одной группы лежат вплотную на общей подложке: скруглены
     * только верх первой и низ последней, зазоров между ними нет,
     * вместо зазора — тонкая линия. Снаружи группа выглядит одной
     * карточкой, как панели в карточке абонента и история вызовов в ней.
     */
    val corner = MaterialTheme.shapes.medium
    val zero = androidx.compose.foundation.shape.CornerSize(0.dp)
    val shape = when (groupPosition) {
        GroupPosition.SINGLE -> corner
        GroupPosition.FIRST -> corner.copy(bottomStart = zero, bottomEnd = zero)
        GroupPosition.MIDDLE -> androidx.compose.ui.graphics.RectangleShape
        GroupPosition.LAST -> corner.copy(topStart = zero, topEnd = zero)
    }
    val gap = FlowUi.cardSpacing / 2
    val topGap = if (groupPosition == GroupPosition.SINGLE || groupPosition == GroupPosition.FIRST) gap else 0.dp
    val bottomGap = if (groupPosition == GroupPosition.SINGLE || groupPosition == GroupPosition.LAST) gap else 0.dp

    // Никаких вычислений: всё пришло готовым

    Card(
        /*
         * ОДИН СЛОЙ ФОНА НА КАРТОЧКУ.
         *
         * Card отвечает только за форму и отступы. Цвет задаёт содержимое:
         * оно знает про режим выбора, а Card — нет, и раньше они красили
         * одну площадь по очереди. На отладке наложения это давало
         * розовый (три слоя) по всему списку.
         */
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            /*
             * Отступы из правил FlowUI, а не числами.
             *
             * Строка журнала и строка контакта должны выглядеть
             * одинаково: человек переключается между вкладками
             * десятки раз в день. Числа в двух местах при первой же
             * правке разойдутся — поправят одно, забудут другое.
             */
            .padding(
                start = FlowUi.screenPadding,
                end = FlowUi.screenPadding,
                top = topGap,
                bottom = bottomGap
            )
            .semantics(mergeDescendants = true) {
                contentDescription = row.accessibilityLabel
            }
    ) {
        if (groupPosition == GroupPosition.MIDDLE || groupPosition == GroupPosition.LAST) {
            // От начала текста, а не от края: под аватаром линия
            // разрезала бы колонку лиц, по которой глаз бежит вниз
            Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                OgnelissDivider(Modifier.padding(start = 76.dp))
            }
        }
        SwipeRevealRow(
            key = swipeKey,
            controller = swipeController,
            tuning = swipeTuning,
            listScrolling = listScrolling,
            directStartAction = directStartAction,
            directEndAction = directEndAction,
            startActions = startActions,
            endActions = endActions,
            onDirectStart = onDirectStart,
            onDirectEnd = onDirectEnd
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // Фон задаётся здесь: содержимое SwipeRevealRow
                    // перекрывает цвет карточки, и отметка выбора
                    // была не видна
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .combinedClickableCompat(onClick, onLongClick)
                    // 6 dp вместо 10: строка из трёх коротких строчек
                    // не требует такого запаса, а на экран помещается
                    // на две записи больше
                    .padding(horizontal = Space.m, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // В режиме выбора кружок вместо аватара: он показывает
                // и что строку можно отметить, и отмечена ли она
                if (selectionActive) {
                    Icon(
                        if (selected) OgnelissIcons.CheckCircle
                        else OgnelissIcons.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(LocalAppearance.current.avatarSize)
                    )
                } else {
                    /*
                     * ЗНАЧОК ЗАМЕТКИ ВМЕСТО СИЛУЭТА.
                     *
                     * ══════════════════════════════════════════════════════
                     * ЗАЧЕМ
                     * ══════════════════════════════════════════════════════
                     * Когда номера нет в книге, но есть заметка, имя
                     * в строке берётся из неё: вместо цифр человек видит
                     * «сантехник» или «мошенник».
                     *
                     * С обычным силуэтом это выглядело как контакт без
                     * фотографии — и легко принять запись за настоящий
                     * контакт, которого на деле нет.
                     *
                     * Значок «i» отвечает на вопрос сразу: подпись —
                     * ваша пометка, а не имя из телефонной книги.
                     */
                    if (row.titleFromNote) {
                        NoteAvatar(row.hasMissed)
                    } else {
                        // Имя — только у контактов: незнакомый номер с именем
                        // из определителя остаётся значком (1.3.5)
                        Avatar(row.photoUri, row.hasMissed, name = if (row.contactId != null) row.title else null)
                    }
                }

                Spacer(Modifier.width(Space.m))

                Column(Modifier.weight(1f)) {
                    /*
                     * Имя и значок напоминания в одной строке.
                     *
                     * weight(fill = false) у имени: оно занимает столько,
                     * сколько нужно, но не выталкивает значок за край —
                     * длинное имя обрежется, значок останется виден.
                     */
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    HighlightedName(
                        name = row.title,
                        query = searchQuery,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    /*
                     * ЗНАЧОК НАПОМИНАНИЯ РЯДОМ С ИМЕНЕМ.
                     *
                     * Не отдельной строкой: строк в записи и так три,
                     * а напоминание — состояние, а не сведение. Значок
                     * рядом с именем отвечает на вопрос «я собирался
                     * перезвонить этому?» одним взглядом по списку.
                     *
                     * Без подписи: в списке место дорого, а один будильник
                     * ни с чем не спутать. Для чтения с экрана подпись
                     * есть в contentDescription.
                     */
                    if (row.hasReminder) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            OgnelissIcons.Alarm,
                            contentDescription = stringResource(R.string.filter_with_reminder),
                            modifier = Modifier.size(FlowUi.iconSizeSmall),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    }

                    /*
                     * Заметка отдельной строкой.
                     *
                     * Курсив отличает её от имени: это пометка человека,
                     * а не название из телефонной книги, и смешивать их
                     * в одинаковом начертании было бы обманчиво.
                     */
                    row.noteLine?.let { noteText ->
                        Text(
                            noteText,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(Space.xxs))

                    // Вторая строка: направление, SIM, номер. Всё мелко
                    // и одним цветом — это справочная информация,
                    // а не то, что ищут взглядом
                    /*
                     * КОНТАКТ БЕЗ ЗВОНКОВ — НОМЕР ВМЕСТО СТРЕЛКИ.
                     *
                     * При наборе номера в журнал подмешиваются контакты,
                     * с которыми звонков не было: их строки собираются
                     * из книги, а тип вызова им ставится «входящий» просто
                     * потому, что поле обязательное. На экране это давало
                     * стрелку входящего без даты — будто звонок был,
                     * но неизвестно когда. Найдено по снимку экрана
                     * из тестирования (поиск «585»).
                     *
                     * У такой строки показываем номер: при поиске по цифрам
                     * это и есть то, ради чего строка попала в список, —
                     * видно, какой именно номер совпал.
                     */
                    if (row.count == 0) {
                        Text(
                            row.formattedNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            typeIcon(row.lastType),
                            contentDescription = null,
                            modifier = Modifier.size(FlowUi.iconSizeSmall),
                            tint = if (row.hasMissed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline
                        )
                        // Бейдж показывает НОМЕР СЛОТА, а не номер абонента:
                        // раньше сюда попадал ноль независимо от карты,
                        // и понять, с какой SIM шёл вызов, было нельзя
                        row.simSlotIndex?.let { slot ->
                            Spacer(Modifier.width(Space.xs))
                            SimBadge(slot)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            row.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    /*
                     * ПОМЕТКА О БЛОКИРОВКЕ ВМЕСТО ПОДПИСИ ОПРЕДЕЛИТЕЛЯ.
                     *
                     * ── Почему вместо, а не вместе ─────────────────────
                     * Две служебные строки подряд превращают карточку
                     * в справку. А выбор очевиден: «Заблокирован — вы
                     * отметили как спам» отвечает, почему звонка не было,
                     * тогда как «Московская обл.» на это не отвечает.
                     *
                     * ── Про цвет ───────────────────────────────────────
                     * Цвет ошибки: для человека это не «внимание, спам»,
                     * а «вы не получили звонок». Второе стоит заметить
                     * обязательно — оно могло быть ошибкой правила.
                     */
                    /*
                     * Пометка о блокировке появляется раскрытием.
                     *
                     * Она возникает не при построении списка, а в ответ
                     * на действие человека: заблокировал номер из меню
                     * длинного нажатия — строка тут же стала выше.
                     * Скачок высоты посреди списка читается как сбой
                     * прокрутки; раскрытие показывает, что это следствие
                     * нажатия.
                     */
                    AnimatedVisibility(
                        visible = row.blockedLabel != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val label = row.blockedLabel.orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                OgnelissIcons.Block,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeSmall),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    /*
                     * Метка «спам» или «мошенники» — вместо подписи
                     * определителя, как и блокировка: две служебные
                     * строки подряд превращают карточку в таблицу.
                     *
                     * Значок другой, чем у блокировки: предупреждение,
                     * а не запрет. Номер с меткой звонит как обычно.
                     */
                    if (row.blockedLabel == null) row.warningTag?.let { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                OgnelissIcons.Report,
                                contentDescription = null,
                                modifier = Modifier.size(FlowUi.iconSizeSmall),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                tag,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        }
                    }

                    // Подпись определителя — только когда нет ни блокировки,
                    // ни метки, и есть что сказать: пустая строка съедала бы
                    // высоту зря
                    if (row.blockedLabel == null && row.warningTag == null) row.infoLabel?.let { summary ->
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.infoIsWarning)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                /*
                 * СЧЁТЧИКОВ В ЖУРНАЛЕ БОЛЬШЕ НЕТ.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ПОЧЕМУ УБРАНЫ
                 * ══════════════════════════════════════════════════════════
                 * «47 вызовов, 20 вх · 27 исх» — сведения, которыми
                 * в списке не пользуются. Просматривая журнал, человек
                 * ищет ответ на другой вопрос: кто звонил последним
                 * и ответил ли я.
                 *
                 * Общая статистика за всё время нужна в одном месте —
                 * в карточке абонента, куда заходят именно за ней.
                 * Там она и осталась.
                 *
                 * ── Что это дало ───────────────────────────────────────────
                 * Строка стала короче и читается быстрее: тип вызова,
                 * имя, время. Заодно исчезла причина растущих чисел —
                 * их просто нет.
                 */

                /*
                 * КНОПКА ВЫЗОВА В СТРОКЕ.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ЗАЧЕМ, ЕСЛИ ЕСТЬ СВАЙП
                 * ══════════════════════════════════════════════════════════
                 * Свайп остаётся и работает как раньше. Но о нём знают
                 * не все: жест ничем себя не выдаёт, а подсказку при
                 * первом запуске видят один раз и забывают.
                 *
                 * Кнопка видна всегда. Тому, кто освоил свайп, она
                 * не мешает — он ей просто не пользуется.
                 *
                 * ── Почему не мешает выбору и долгому нажатию ──────────────
                 * В режиме множественного выбора кнопка скрыта: там
                 * нажатие означает «отметить», и звонок посреди выбора
                 * был бы неприятной неожиданностью.
                 */
                if (!selectionActive && onCall != null) {
                    Spacer(Modifier.width(Space.xs))

                    IconButton(
                        onClick = { onCall(row) },
                        modifier = Modifier.size(Dimens.MIN_TOUCH_TARGET)
                    ) {
                        Icon(
                            OgnelissIcons.Call,
                            contentDescription = stringResource(
                                R.string.a11y_call_number, row.title
                            ),
                            tint = CallGreen
                        )
                    }
                }
            }
        }
    }
}

/** Портрет абонента для шапки меню долгого нажатия — тот же, что в строке журнала. */
@Composable
internal fun RowAvatar(
    photoUri: String?,
    size: androidx.compose.ui.unit.Dp? = null,
    /** Имя контакта — для букв без фото (1.3.5); null — значок человека. */
    name: String? = null
) = Avatar(photoUri, hasMissed = false, sizeOverride = size, name = name)

@Composable
private fun Avatar(
    photoUri: String?,
    hasMissed: Boolean,
    /** Свой размер (шапка меню записи, 1.3.5); null — из настроек. */
    sizeOverride: androidx.compose.ui.unit.Dp? = null,
    /*
     * БУКВЫ ВМЕСТО ЗНАЧКА У КОНТАКТОВ (1.3.5, 05.10.2026, задача автора:
     * «чтобы контакты отделять от незнакомых номеров»). Имя передаётся
     * только для контактов; буквы — Initials.of (две первые буквы слов).
     * Фон — primaryContainer: контакт заметен среди серых значков
     * незнакомых номеров. Пропущенный — прежняя подсветка errorContainer.
     */
    name: String? = null
) {
    val appearance = LocalAppearance.current
    val size = sizeOverride ?: appearance.avatarSize
    val initials = remember(name) { com.ogneliss.dialer.util.Initials.of(name) }

    Box(contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            // Пропущенные подсвечиваются на аватаре: это самый крупный
            // элемент карточки, и метка на нём заметна периферийным зрением
            color = when {
                hasMissed -> MaterialTheme.colorScheme.errorContainer
                initials != null -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
            modifier = Modifier.size(size)
        ) {
            /*
             * Пиктограмма рисуется ВСЕГДА, фотография поверх неё.
             *
             * ── Зачем ──────────────────────────────────────────────────
             * Раньше при наличии photoUri рисовался только AsyncImage,
             * и если картинка не загружалась — путь устарел, файл удалён,
             * права отозваны — оставался пустой серый круг. На экране
             * это выглядело так, будто часть контактов чем-то отличается,
             * хотя причина чисто техническая.
             *
             * Теперь под фотографией всегда лежит силуэт: не загрузилась —
             * виден он, загрузилась — перекрыт ею. Лишний слой здесь
             * оправдан: пустой круг читается как поломка.
             */
            Box(contentAlignment = Alignment.Center) {
                if (initials != null) {
                    // 36% диаметра — в крупном режиме и при крупном шрифте
                    // системы буквы растут вместе с кружком, а не вылезают
                    Text(
                        initials,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.36f).toSp() }
                        ),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = if (hasMissed) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                } else {
                    Icon(
                        OgnelissIcons.Person,
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.55f),
                        tint = if (hasMissed) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (appearance.showPhoto && photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(size)
                    )
                }
            }
        }
    }
}


private fun typeIcon(type: CallType) = when (type) {
    CallType.OUTGOING -> OgnelissIcons.CallMade
    CallType.MISSED -> OgnelissIcons.CallMissed
    CallType.REJECTED, CallType.BLOCKED -> OgnelissIcons.Block
    else -> OgnelissIcons.CallReceived
}

private fun typeLabelRes(type: CallType) = when (type) {
    CallType.OUTGOING -> R.string.call_outgoing
    CallType.MISSED -> R.string.call_missed
    CallType.REJECTED -> R.string.call_rejected
    CallType.BLOCKED -> R.string.call_blocked
    CallType.VOICEMAIL -> R.string.call_voicemail
    else -> R.string.call_incoming
}

/**
 * ЗНАЧОК ДЛЯ ЗАПИСИ, ПОДПИСАННОЙ ЗАМЕТКОЙ.
 *
 * ── Почему «i», а не карандаш ──────────────────────────────────────────────
 * Карандаш означает «можно править» и приглашает нажать. Здесь нажатие
 * открывает карточку, как у всех строк, — приглашение было бы обманом.
 *
 * «i» читается как «сведения»: подпись рядом — это то, что вы сами
 * записали об этом номере.
 *
 * ── Цвет ───────────────────────────────────────────────────────────────────
 * Тот же, что у пропущенного, когда вызов пропущен: тревожность строки
 * важнее происхождения подписи. В остальных случаях приглушённый —
 * значок поясняет, а не требует внимания.
 */
@Composable
private fun NoteAvatar(hasMissed: Boolean) {
    /*
     * Размер из настроек внешнего вида — тот же, что у обычного значка.
     *
     * Человек может увеличить аватары в настройках; если взять
     * фиксированный размер, строки с заметкой окажутся ниже соседних,
     * и список пойдёт ступеньками.
     */
    val size = LocalAppearance.current.avatarSize

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        /*
         * Подложка бирюзовая, пока вызов не пропущен.
         *
         * Пропущенный важнее: тревожность строки должна читаться
         * раньше, чем происхождение подписи. Поэтому красный
         * перекрывает.
         */
        color = when {
            hasMissed -> MaterialTheme.colorScheme.errorContainer
            else -> NoteTeal.copy(alpha = 0.18f)
        },
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                // Залитый, как остальные значки приложения. Контурные у нас
                // только там, где контур несёт смысл: булавка и звезда без
                // заливки — «не закреплён», трубка псевдозвонка — иначе
                // на 24 dp стрелка сливается с трубкой
                OgnelissIcons.Info,
                contentDescription = null,
                tint = if (hasMissed) MaterialTheme.colorScheme.onErrorContainer
                else NoteTeal
            )
        }
    }
}

/**
 * Место строки журнала в группе: разбор — у параметра groupPosition
 * в CallLogCard и в JournalStyle.
 */
enum class GroupPosition {
    SINGLE, FIRST, MIDDLE, LAST;

    companion object {
        fun of(index: Int, size: Int): GroupPosition = when {
            size <= 1 -> SINGLE
            index == 0 -> FIRST
            index == size - 1 -> LAST
            else -> MIDDLE
        }
    }
}
