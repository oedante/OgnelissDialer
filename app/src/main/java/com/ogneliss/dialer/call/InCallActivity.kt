package com.ogneliss.dialer.call

import com.ogneliss.dialer.ui.adaptive.ProvideWindowLayout
import com.ogneliss.dialer.ui.components.Motion
import com.ogneliss.dialer.ui.theme.BlockRed
import com.ogneliss.dialer.ui.theme.CallGreen
import com.ogneliss.dialer.ui.theme.DeclineRed
import com.ogneliss.dialer.ui.theme.FlowUi
import com.ogneliss.dialer.util.TagCatalog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.ui.components.Dialpad
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import androidx.compose.ui.res.stringResource
import com.ogneliss.dialer.R
import com.ogneliss.dialer.ui.adaptive.LocalWindowLayout
import androidx.compose.ui.platform.LocalDensity
import com.ogneliss.dialer.ui.adaptive.FoldPosture
import com.ogneliss.dialer.settings.CarrierInfoMode
import com.ogneliss.dialer.data.NoteDisplay
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.ui.components.OgnelissDivider
import com.ogneliss.dialer.util.PhoneFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    private var proximity: ProximityLock? = null
    private var smartSpeaker: SmartSpeakerController? = null

    @Inject lateinit var prefs: com.ogneliss.dialer.settings.Prefs

    /** Нужен для фотографии звонящего: Details её не содержит. */
    @Inject lateinit var contactsRepo: com.ogneliss.dialer.data.ContactsRepository

    /** Заметка о звонящем: самый нужный момент для неё — входящий вызов. */
    @Inject lateinit var notesRepo: com.ogneliss.dialer.data.NotesRepository

    /** Отметки «спам» и «мошенники» — отдельно от заметок с 1.1.1. */
    @Inject lateinit var spamMarksRepo: com.ogneliss.dialer.data.SpamMarksRepository

    /**
     * Реестр нумерации: оператор и регион звонящего.
     *
     * Именно на этом экране сведения нужнее всего. В журнале человек
     * разбирает звонки задним числом, а здесь решает прямо сейчас,
     * брать ли трубку, — и «Тюменская область» у незнакомого номера
     * отвечает на этот вопрос лучше любой оценки риска.
     */
    @Inject lateinit var numberingPlan: com.ogneliss.dialer.data.NumberingPlan

    /**
     * Область приложения для сохранения заметки.
     *
     * Экран вызова закрывается ровно тогда, когда разговор окончен,
     * и область композиции умирает вместе с ним — запись обрывалась
     * на полпути. Эта живёт дольше.
     */
    @Inject @com.ogneliss.dialer.di.ApplicationScope
    lateinit var appScope: kotlinx.coroutines.CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * EDGE-TO-EDGE НУЖЕН И ЗДЕСЬ.
         *
         * С Android 15 система рисует приложение под панелями всегда,
         * независимо от того, просило оно об этом или нет. Экран вызова
         * это касается наравне с остальными: без явного вызова
         * отступы считаются по старым правилам, и кнопка «Завершить»
         * может оказаться под панелью навигации.
         *
         * Без своих подложек: с API 35 методы, которыми они задавались,
         * ничего не делают, а Google Play помечает их использование
         * как неподдерживаемое.
         */
        enableEdgeToEdge()

        // Показ поверх экрана блокировки — обязателен для входящего вызова
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        proximity = ProximityLock(this).also { it.acquire() }

        // Авто-громкая связь: подписка на датчики только если функция
        // включена, иначе разговор час жёг бы батарею впустую
        lifecycleScope.launch {
            if (prefs.smartSpeaker.first()) {
                smartSpeaker = SmartSpeakerController(this@InCallActivity) { route ->
                    CallManager.setAudioRoute(route)
                }.also { controller ->
                    if (controller.isSupported()) controller.start()
                }
            }
        }

        setContent {
            /*
             * Тема читается из настроек, как и на остальных экранах.
             *
             * Раньше здесь вызывалось OgnelissDialerTheme без параметров,
             * то есть со значениями по умолчанию: экран вызова оставался
             * светлым при выбранной тёмной теме. Заметить это трудно —
             * входящий звонок на эмуляторе не воспроизвести, а на устройстве
             * экран показывается в момент, когда не до разглядывания.
             *
             * Для AMOLED это ещё и вопрос батареи: экран вызова горит
             * дольше остальных, и чёрный фон на нём экономит заметно больше.
             */
            val theme by prefs.theme.collectAsState(initial = ThemeMode.SYSTEM)
            val dynamic by prefs.dynamicColor.collectAsState(initial = false)
            // Подписи под кнопками — та же настройка, что во всём приложении.
            // Экран вызова — отдельная активность, корня приложения у него
            // нет, поэтому оформление ставится здесь
            val buttonLabels by prefs.buttonLabels.collectAsState(initial = true)

            OgnelissDialerTheme(themeMode = theme, dynamicColor = dynamic) {
                // Раскладка окна: телефон, лёжа, раскладушка, планшет.
                // Разбор — в ui/adaptive/WindowLayout.kt
                ProvideWindowLayout(this@InCallActivity) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.ogneliss.dialer.ui.theme.LocalAppearance provides
                        com.ogneliss.dialer.ui.theme.Appearance(buttonLabels = buttonLabels)
                ) {
                val calls by CallManager.calls.collectAsState()
                val revision by CallManager.revision.collectAsState()
                val audio by CallManager.audioState.collectAsState()

                /*
                 * Режим показа оператора берётся из тех же настроек,
                 * что и журнал: человек задаёт правило один раз,
                 * а не отдельно для каждого экрана.
                 */
                val carrierInfo by prefs.carrierInfo
                    .collectAsState(initial = CarrierInfoMode.UNKNOWN_ONLY)

                LaunchedEffect(calls) { if (calls.isEmpty()) finish() }

                // Датчики должны знать о состоянии вызова и аудиомаршрута
                LaunchedEffect(revision, audio) {
                    smartSpeaker?.updateCallState(CallManager.primary, audio)
                }

                /*
                 * ЭКРАН БОЛЬШЕ НЕ ПЕРЕСОБИРАЕТСЯ ЗАНОВО.
                 *
                 * ══════════════════════════════════════════════════════════
                 * ЧТО БЫЛО
                 * ══════════════════════════════════════════════════════════
                 * Здесь стоял key(revision) { InCallScreen(...) }. Это
                 * не «перерисовать», а «выбросить и построить заново»:
                 * при каждом изменении revision Compose уничтожал всё
                 * поддерево вместе с его remember.
                 *
                 * А revision растёт на каждое событие вызова: ответ,
                 * удержание, смена аудиомаршрута. То есть при нажатии
                 * «Ответить» экран строился с нуля: фотография
                 * запрашивалась заново, имя и номер на мгновение пустели,
                 * набранный в заметке текст пропадал. Видно это было
                 * как вспышка.
                 *
                 * ── Как теперь ─────────────────────────────────────────────
                 * revision передаётся параметром. Меняется значение —
                 * функция перерисовывается и перечитывает состояние
                 * из объекта Call (он изменяемый, и сам по себе Compose
                 * его изменений не видит — ради этого revision и заведён).
                 * Но remember при этом остаётся жив: портрет, заметка
                 * и имя переживают ответ на вызов.
                 */
                InCallScreen(
                    numberingPlan = numberingPlan,
                    carrierInfo = carrierInfo,
                    call = CallManager.primary,
                    secondary = CallManager.secondary,
                    revision = revision,
                    audioState = audio,
                    contactsRepo = contactsRepo,
                    appScope = appScope,
                    notesRepo = notesRepo,
                    spamMarksRepo = spamMarksRepo,
                    // Пользователь переключил звук сам — датчики умолкают
                    // до конца разговора
                    onManualAudioChange = { smartSpeaker?.notifyManualToggle() }
                )
            }
            }
                }
        }
    }

    override fun onDestroy() {
        smartSpeaker?.stop()
        proximity?.release()
        super.onDestroy()
    }
}

/**
 * Экран активного вызова.
 *
 * В ландшафте информация об абоненте и кнопки управления встают рядом:
 * вертикально они не помещаются, и кнопка «Завершить» уезжает за экран —
 * это ровно та жалоба «не могу завершить звонок», что встречается
 * в отзывах на сторонние диалеры.
 */
@Composable
private fun InCallScreen(
    numberingPlan: com.ogneliss.dialer.data.NumberingPlan,
    /** Настройка «Оператор и регион»: та же, что в журнале. */
    carrierInfo: CarrierInfoMode,
    call: Call?,
    secondary: Call?,
    /**
     * Счётчик событий телефонии.
     *
     * Call — изменяемый объект из системного API: его состояние меняется
     * внутри, а Compose таких изменений не видит и перерисовку не назначит.
     * Счётчик растёт на каждое событие и служит признаком «данные вызова
     * могли измениться, перечитай их».
     *
     * Раньше он использовался как key() снаружи — и вместо перечитывания
     * экран пересобирался с нуля, теряя портрет и текст заметки.
     */
    revision: Int,
    audioState: CallAudioState?,
    contactsRepo: com.ogneliss.dialer.data.ContactsRepository,
    /** Область приложения: переживает закрытие экрана вызова. */
    appScope: kotlinx.coroutines.CoroutineScope,
    notesRepo: com.ogneliss.dialer.data.NotesRepository,
    spamMarksRepo: com.ogneliss.dialer.data.SpamMarksRepository,
    onManualAudioChange: () -> Unit = {}
) {
    // Мало места по высоте — раскладка в две колонки. Порог прежний
    // (480 dp), но теперь один на всё приложение: разбор в WindowLayout
    val landscape = LocalWindowLayout.current.isShort
    // Состояние панелей локальное для экрана: при повороте оно должно
    // сбрасываться. Открытая DTMF-панель после поворота перекрыла бы
    // кнопку завершения вызова в ландшафте
    var showDialpad by remember { mutableStateOf(false) }
    var showQuickReply by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var dtmfBuffer by remember { mutableStateOf("") }
    /*
     * Состояние перечитывается по revision.
     *
     * call.state — обычное поле изменяемого объекта, а не состояние
     * Compose: подписаться на него нельзя. Ключ revision заставляет
     * перечитать значение после каждого события телефонии.
     */
    /*
     * Предупреждение «val state is deprecated» — осознанное.
     *
     * Замена, Call.getDetails().getState(), появилась только в API 31,
     * а minSdk у нас 26. Пока он не поднимется, ветка со старым
     * свойством нужна всё равно, и подавлять предупреждение точечно —
     * значит спрятать напоминание о том, что этот код однажды надо
     * переписать.
     */
    @Suppress("DEPRECATION")
    val state = remember(revision, call) { call?.state ?: Call.STATE_DISCONNECTED }
    /*
     * ЛИЧНОСТЬ СОБЕСЕДНИКА «ЗАЛИПАЕТ».
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ФОТО ПРОПАДАЛО ПРИ ЗАВЕРШЕНИИ
     * ══════════════════════════════════════════════════════════════════════
     * Номер, имя и фотография брались напрямую из call. Как только
     * разговор заканчивается, система убирает объект вызова, call
     * становится null — и всё разом обнулялось: имя, номер, портрет.
     *
     * На экране это выглядело так: «Вызов завершён», а вместо человека
     * серый силуэт без имени и номера. Хотя экран ещё секунду виден
     * и должен показывать, с кем говорили.
     *
     * ── Та же причина у мигания при ответе ─────────────────────────────────
     * При нажатии «Ответить» система пересобирает список вызовов,
     * и call на короткое время приходит пустым. Экран успевал
     * перерисоваться пустым и обратно — это и читалось как вспышка.
     *
     * ── Как теперь ─────────────────────────────────────────────────────────
     * Живые значения читаются из call, но в состояние попадают только
     * непустые. Стало известно имя — запомнили; пропал вызов — держим
     * последнее известное. Обратно в «неизвестно» экран уже не падает.
     *
     * Фотографию отдельно чинить не нужно: produceState ниже перезапускается
     * по ключу number, а number больше не обнуляется — значит, и портрет
     * остаётся на месте.
     */
    val liveNumber = remember(revision, call) {
        call?.details?.handle?.schemeSpecificPart.orEmpty()
    }
    val liveName = remember(revision, call) {
        CallDetailsCompat.contactDisplayName(call?.details)
    }

    var number by remember { mutableStateOf(liveNumber) }
    var name by remember { mutableStateOf(liveName) }

    /*
     * Ключи здесь — строки, а не объекты.
     *
     * LaunchedEffect сравнивает ключи по equals, поэтому пересозданная
     * строка с тем же содержимым перезапуска не вызывает. Раньше
     * ключом служил объект details, и он менялся при каждом обновлении
     * от системы — чтение фотографии запускалось заново по нескольку
     * раз в секунду, и портрет мигал.
     */
    LaunchedEffect(liveNumber) {
        if (liveNumber.isNotBlank()) number = liveNumber
    }
    LaunchedEffect(liveName) {
        if (!liveName.isNullOrBlank()) name = liveName
    }

    /*
     * Фотография звонящего.
     *
     * Details её не содержит — система отдаёт только имя. Запрашиваем
     * сами точечным запросом по номеру; он идёт по индексу и занимает
     * миллисекунды, что допустимо даже на экране входящего вызова.
     *
     * produceState: пока запрос идёт, показывается обобщённая иконка,
     * и экран не ждёт результата.
     */
    val photoUri by produceState<String?>(initialValue = null, number) {
        value = runCatching { contactsRepo.photoUriForNumber(number) }.getOrNull()
    }

    /*
     * ЗАМЕТКА О ЗВОНЯЩЕМ.
     *
     * Самый нужный момент для заметки — именно этот: человек решает,
     * брать трубку или нет, и «мошенники» или «доставка мебели» отвечают
     * на вопрос лучше любого номера.
     *
     * Предел длины здесь больше, чем в списке: экран пустой, места хватает.
     */
    /*
     * rememberCoroutineScope здесь больше нет.
     *
     * Он остался от прежней версии сохранения заметки и после перехода
     * на appScope (см. ниже) не использовался ни разу — переменная жила
     * только в предупреждениях компилятора. Область композиции для
     * заметки и не годится: она умирает вместе с экраном вызова,
     * то есть ровно в момент, когда заметку надо дописать.
     */

    /*
     * ЗАМЕТКА ЖИВЁТ ДОЛЬШЕ ЭКРАНА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ НЕ rememberCoroutineScope
     * ══════════════════════════════════════════════════════════════════════
     * Она умирает вместе с композицией. Экран вызова закрывается ровно
     * в тот момент, когда разговор окончен, — и сохранение заметки,
     * запущенное за миг до этого, обрывается на полпути.
     *
     * В логе это выглядело так:
     *   W [InCallNote] Не удалось прочитать заметку
     *   Q.T: The coroutine scope left the composition
     *
     * Текст при этом терялся молча: человек написал, разговор
     * закончился, заметки нет.
     *
     * Область приложения переживает закрытие экрана: запись доходит
     * до базы, даже если экран уже пропал.
     */
    val noteState = remember(number) { InCallNoteState(notesRepo, spamMarksRepo, appScope) }

    /*
     * Панель заметки не закрывается сама.
     *
     * AnimatedVisibility скрывает содержимое, но состояние остаётся:
     * текст не теряется, если человек свернул панель посмотреть
     * на экран вызова и открыл снова.
     */
    LaunchedEffect(number) { noteState.load(number) }

    // Завершение вызова: экран вот-вот исчезнет, ждать паузу нельзя
    DisposableEffect(number) { onDispose { noteState.saveNow() } }

    /*
     * ИМЯ, ЗАМЕТКА И НОМЕР ПО ОБЩИМ ПРАВИЛАМ.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ ЧЕРЕЗ NoteDisplay
     * ══════════════════════════════════════════════════════════════════════
     * Те же правила действуют в журнале вызовов и в карточке абонента:
     *
     * • номер в контактах — имя сверху, заметка курсивом под ним;
     * • номера нет — заметка ВМЕСТО имени: она единственное, что
     *   человек знает об этом номере;
     * • нет ни того, ни другого — только номер.
     *
     * Своя реализация здесь означала бы, что при следующей правке
     * экраны разойдутся: в журнале «Сантехник Иван», а на входящем
     * просто номер. Человек не поймёт, почему приложение узнаёт
     * звонящего в одном месте и не узнаёт в другом.
     *
     * Отдельно важно на экране входящего: решение брать трубку
     * принимается за секунды, и заметка «мошенник» или «врач» —
     * ровно то, ради чего она записывалась.
     */
    /*
     * Ключ — только номер.
     *
     * Имя выводится из тех же данных и меняется вместе с ним; держать
     * его вторым ключом значит перезапускать чтение заметки всякий раз,
     * когда строка имени пересоздалась — а это происходило постоянно.
     */
    /*
     * ОПЕРАТОР И РЕГИОН ЗВОНЯЩЕГО.
     *
     * Читается в фоне: первое обращение к реестру после установки
     * распаковывает файл на четыре мегабайта, и на главном потоке
     * это дало бы заметную задержку ровно в момент звонка.
     *
     * Ключ — номер: пока идёт разговор, он не меняется, и повторных
     * обращений не будет.
     */
    val carrierLine by produceState<String?>(null, number, carrierInfo, name) {
        value = withContext(Dispatchers.IO) {
            val known = !name.isNullOrBlank()
            val show = when (carrierInfo) {
                CarrierInfoMode.HIDDEN -> false
                CarrierInfoMode.ALL -> true
                CarrierInfoMode.UNKNOWN_ONLY -> !known
            }
            if (!show || number.isBlank()) return@withContext null

            numberingPlan.lookup(PhoneFormatter.toE164(number))?.let { entry ->
                listOf(
                    com.ogneliss.dialer.data.NumberingPlan.shortOperatorName(entry.operator),
                    entry.region
                ).filter { it.isNotBlank() }.joinToString(" · ")
            }
        }
    }

    // Метки из заметки — с заглавной: на экране вызова это заголовок
    val fraudLabel = stringResource(R.string.tag_fraud)
        .replaceFirstChar { it.titlecase() }
    val spamLabel = stringResource(R.string.tag_spam)
        .replaceFirstChar { it.titlecase() }

    val callerDisplay by produceState(
        initialValue = NoteDisplay.Result(
            title = PhoneFormatter.format(number),
            subtitle = null,
            titleFromNote = false
        ),
        number,
        /*
         * name — тоже ключ.
         *
         * Имя теперь попадает в состояние через LaunchedEffect, то есть
         * на кадр позже номера. Без этого ключа заголовок считался бы
         * один раз, когда имени ещё нет, и на экране навсегда остался бы
         * номер вместо «Любимая».
         *
         * Ключ — строка, сравнение по содержимому: пока имя не изменилось,
         * перезапуска нет.
         */
        name
    ) {
        value = runCatching {
            val stored = notesRepo.get(PhoneFormatter.toE164(number))
            // Отметка — из своего хранилища, а не из заметки
            val tags = listOfNotNull(
                spamMarksRepo.get(PhoneFormatter.toE164(number))?.type?.key
            )

            /*
             * ══════════════════════════════════════════════════════════════
             * МЕТКА «СПАМ» ИЛИ «МОШЕННИКИ» — ВМЕСТО ИМЕНИ
             * ══════════════════════════════════════════════════════════════
             * Экран вызова — единственное место, где метка нужна по-настоящему:
             * на решение «брать или нет» у человека три секунды, и номер
             * ему ничего не скажет. «Мошенники» крупно и красным — скажет.
             *
             * Сюда вызов попадает, только когда переключатель «Блокировать
             * помеченные» выключен: при включённом SpamPolicy отклоняет
             * помеченный номер ещё до звонка. То есть этот экран — ровно
             * тот случай, когда человек решил сам.
             *
             * ⚠️ Имя из книги метку не вытесняет, а стоит над ней: если
             * номер записан в контактах и при этом помечен, человек сам
             * его записал — и имя важнее. Но и метку показать надо,
             * поэтому она идёт подписью.
             */
            val warning = when {
                "fraud" in tags -> fraudLabel
                "spam" in tags -> spamLabel
                else -> null
            }

            when {
                warning != null && name.isNullOrBlank() -> NoteDisplay.Result(
                    title = warning,
                    subtitle = stored?.note?.takeIf { it.isNotBlank() },
                    titleFromNote = true
                )

                warning != null -> NoteDisplay.Result(
                    title = name.orEmpty(),
                    subtitle = warning,
                    titleFromNote = false
                )

                else -> NoteDisplay.forCallScreen(
                    displayName = name,
                    note = stored?.note,
                    formattedNumber = PhoneFormatter.format(number)
                )
            }
        }.getOrElse {
            NoteDisplay.Result(PhoneFormatter.format(number), null, false)
        }
    }

    /*
     * Есть ли у номера опасная метка — для цвета заголовка.
     *
     * Считается отдельно от callerDisplay, потому что тот отвечает
     * на вопрос «что написать», а этот — «каким цветом». Смешать их
     * значило бы добавить в общий NoteDisplay поле, нужное только здесь.
     */
    val isDangerTagged by produceState(false, number) {
        value = runCatching {
            spamMarksRepo.get(PhoneFormatter.toE164(number)) != null
        }.getOrDefault(false)
    }

    if (showQuickReply) {
        QuickReplySheet(
            templates = QuickReply.defaultTemplates(context),
            onSend = { message ->
                // Если отклонение с текстом не сработало, вызов всё равно
                // нужно сбросить: пользователь этого и хотел
                if (!QuickReply.rejectWith(message)) CallManager.reject()
                showQuickReply = false
            },
            onDismiss = { showQuickReply = false }
        )
    }

    /*
     * ОТСТУПЫ ОТ СИСТЕМНЫХ ПАНЕЛЕЙ.
     *
     * Экран вызова рисуется во весь экран, включая области под строкой
     * состояния и панелью навигации. Раньше это сходило с рук: система
     * сама держала подложки. С Android 15 она этого не делает, и без
     * safeDrawingPadding кнопка «Завершить» уезжает под панель
     * навигации, а таймер разговора — под часы.
     *
     * Здесь, а не в каждой раскладке: портретная и альбомная ветки
     * рисуются внутри этой Surface, и отступ нужен обеим.
     */
    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
        /*
         * Кто звонит — фото, имя, оператор, состояние вызова. Общий блок
         * для раскладок «лёжа» и «ноутбук»: раньше жил только внутри
         * альбомной ветки, и вторая раскладка скопировала бы его целиком.
         */
        val identityBlock: @Composable () -> Unit = {
                    /*
                     * Портрет и в альбомной ориентации.
                     *
                     * Раньше он был только в портретной: при повороте
                     * телефона звонящий превращался в строку текста.
                     * Меньше, чем в портретной — 96 dp против 140:
                     * по высоте места вдвое меньше, и крупный портрет
                     * вытеснил бы имя за край.
                     */
                    // Отступ только вместе с портретом: без фотографии
                    // экран начинается с имени, и зазор сверху был бы
                    // отступом от пустоты
                    if (!photoUri.isNullOrBlank()) {
                        CallerAvatar(
                            photoUri = photoUri,
                            size = FlowUi.callPortraitSizeLandscape
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    CallerIdentity(
                        display = callerDisplay,
                        number = number,
                        carrierLine = carrierLine,
                        danger = isDangerTagged,
                        titleSize = FlowUi.callNameSizeLandscape
                    )

                    Spacer(Modifier.height(8.dp))
                    CallStatusLine(state, call)
                }

        /** Главные кнопки: ответить и отклонить, или завершить. */
        val mainButtons: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (state == Call.STATE_RINGING) {
                            BigButton(
                                stringResource(R.string.incall_decline),
                                DeclineRed,
                                Icons.Default.CallEnd
                            ) {
                                CallManager.reject()
                            }
                            BigButton(
                                stringResource(R.string.incall_answer),
                                CallGreen,
                                Icons.Default.Call
                            ) {
                                CallManager.answer()
                            }
                        } else {
                            BigButton(
                                stringResource(R.string.incall_hangup),
                                DeclineRed,
                                Icons.Default.CallEnd
                            ) {
                                CallManager.hangup()
                            }
                        }
                    }
                }

        /*
         * ══════════════════════════════════════════════════════════════════
         * РАСКЛАДУШКА В ПОЛОЖЕНИИ «НОУТБУК»
         * ══════════════════════════════════════════════════════════════════
         * Полураскрытая раскладушка, сгиб горизонтальный: нижняя половина
         * лежит на столе, верхняя стоит. Так её ставят для громкой связи
         * и видеозвонков — и так же делает системное приложение на Pixel
         * Fold и Galaxy Fold: собеседник — наверху, где его видно,
         * кнопки — внизу, где до них достаёт палец.
         *
         * Граница — по сгибу, а не посередине экрана: у разных моделей
         * сгиб не ровно в центре, и ничего не должно лечь на сам сгиб —
         * там изображение искажено.
         *
         * ⚠️ Проверяется только на раскладушке или эмуляторе Pixel Fold:
         * у телефона сгиба нет, и эта ветка не срабатывает.
         */
        val windowLayout = LocalWindowLayout.current
        val hinge = windowLayout.hinge
        if (windowLayout.posture == FoldPosture.TABLETOP && hinge != null && !hinge.vertical) {
            val density = LocalDensity.current
            // Сгиб — в координатах окна, а содержимое начинается ниже
            // строки состояния: вычитаем её
            val topInsetDp = with(density) {
                WindowInsets.safeDrawing.getTop(density).toDp().value
            }
            val aboveHinge = (hinge.startDp - topInsetDp).coerceAtLeast(0f).dp
            val hingeThickness = (hinge.endDp - hinge.startDp).coerceAtLeast(0f).dp

            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(aboveHinge)
                        .padding(FlowUi.contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        identityBlock()
                    }
                }

                Spacer(Modifier.height(hingeThickness))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(FlowUi.contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    mainButtons()
                }
            }
            return@Surface
        }

        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(FlowUi.contentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    identityBlock()
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    mainButtons()
                }
            }
            return@Surface
        }

        /*
         * ОТСТУПЫ ПО МЕРКАМ ОСТАЛЬНОГО ПРИЛОЖЕНИЯ.
         *
         * Было 24 dp по краям и ещё 40 сверху. В журнале и контактах
         * поля 12 dp, и экран вызова рядом с ними выглядел разреженным:
         * между именем и кнопками оставалась пустота в треть экрана,
         * а сами кнопки уезжали к нижнему краю.
         *
         * Теперь 16 по краям (чуть больше журнальных 12: экран пустой,
         * и совсем прижимать к краю нечего) и 24 сверху вместо 40.
         */
        Column(
            Modifier.fillMaxSize().padding(FlowUi.contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- Верх: кто звонит ---
            Column(
                Modifier.padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /*
                 * ПОРТРЕТ АБОНЕНТА.
                 *
                 * ── Почему его не было ─────────────────────────────────────
                 * Фотография запрашивалась, но нигде не рисовалась:
                 * на экране оставались только имя и номер. Узнать
                 * звонящего в лицо было нельзя.
                 *
                 * ── Почему такой крупный ───────────────────────────────────
                 * 140 dp — экран вызова смотрят издалека, на вытянутой
                 * руке или мельком, когда телефон лежит на столе.
                 * Портрет тут работает быстрее текста: лицо узнаётся
                 * раньше, чем прочитано имя.
                 *
                 * Если фотографии нет, портрета нет вовсе: серый круг
                 * с силуэтом занимал верхнюю треть экрана и не сообщал
                 * ничего — силуэт одинаков у всех. Без него имя
                 * поднимается наверх, и длинному хватает места.
                 */
                if (!photoUri.isNullOrBlank()) {
                    CallerAvatar(photoUri = photoUri, size = FlowUi.callPortraitSize)
                    Spacer(Modifier.height(16.dp))
                }

                CallerIdentity(
                    display = callerDisplay,
                    number = number,
                    carrierLine = carrierLine,
                    danger = isDangerTagged,
                    titleSize = FlowUi.callNameSize
                )
                Spacer(Modifier.height(12.dp))
                CallStatusLine(state, call)
                secondary?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.incall_secondary,
                            CallDetailsCompat.contactDisplayName(it.details)
                                ?: PhoneFormatter.format(
                                    CallDetailsCompat.number(it.details).orEmpty()
                                )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                // Набранное показывает сама панель: дублировать
                // не нужно, иначе цифры видны в двух местах
                /*
                 * Введённые тоны здесь не показываются.
                 *
                 * Блок стоял под `if (false)` — мёртвый код, который
                 * компилятор пропускает, а читатель принимает
                 * за работающий. Цифры видны в самом поле набора,
                 * дублировать их незачем.
                 */
            }

            // --- Низ: управление ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                AnimatedVisibility(showDialpad) {
                    DtmfPanel(
                        entered = dtmfBuffer,
                        onDigit = { digit ->
                            CallManager.playDtmf(digit)
                            dtmfBuffer += digit
                        },
                        onBackspace = { dtmfBuffer = dtmfBuffer.dropLast(1) },
                        onClose = { showDialpad = false }
                    )
                }

                /*
                 * ══════════════════════════════════════════════════════════
                 * ВТОРОЙ ВХОДЯЩИЙ ВО ВРЕМЯ РАЗГОВОРА
                 * ══════════════════════════════════════════════════════════
                 * Раньше его не было на экране вовсе: текущим оставался
                 * идущий разговор, и ответить на второй звонок можно было
                 * только из шторки уведомлений. Теперь — плашка с тремя
                 * решениями: отклонить, ответить с удержанием первого,
                 * ответить и завершить первый.
                 */
                val waitingCall = remember(revision) { CallManager.waiting }
                waitingCall?.let { incoming ->
                    WaitingCallBanner(incoming, contactsRepo)
                    Spacer(Modifier.height(12.dp))
                }

                /*
                 * УЧАСТНИКИ КОНФЕРЕНЦИИ.
                 *
                 * После объединения на экране была одна «конференция»
                 * без людей: кто в ней, не видно, отключить одного
                 * нельзя. Теперь — список участников, у каждого
                 * «поговорить отдельно» и «отключить», если сеть это
                 * позволяет.
                 */
                val participants = remember(revision, call) { CallManager.conferenceParticipants }
                if (participants.isNotEmpty()) {
                    ConferencePanel(participants, contactsRepo)
                    Spacer(Modifier.height(12.dp))
                }

                /*
                 * Панель управления появляется раскрытием.
                 *
                 * Момент ответа — единственный, когда на экране меняется
                 * всё сразу: две кнопки становятся одной, и снизу
                 * выезжает панель управления. Мгновенная подмена читалась
                 * как переход на другой экран, и палец, летевший
                 * к «Ответить», оказывался над «Удержать».
                 *
                 * Раскрытие занимает те же доли секунды, но показывает,
                 * что это тот же экран, а не новый.
                 */
                AnimatedVisibility(
                    visible = state != Call.STATE_RINGING,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    /*
                     * УПРАВЛЕНИЕ — ОДНИМ БЛОКОМ, БЕЗ ПОДЛОЖКИ.
                     *
                     * ── Что было ───────────────────────────────────────────
                     * Кнопки висели прямо на фоне экрана. Вместе с именем,
                     * номером, состоянием и кнопкой завершения получалось
                     * пять уровней, ничем не разделённых: глаз не понимал,
                     * где кончается «кто звонит» и начинается «что я могу
                     * сделать».
                     *
                     * ── Как было в 1.0 — 1.2.0 ─────────────────────────────
                     * Те же кнопки на подложке — по общему правилу
                     * приложения: смысловой блок живёт в карточке.
                     *
                     * ── Как теперь (1.2.1) ─────────────────────────────────
                     * Подложка убрана. Первый же отзыв в Google Play
                     * просил убрать «большую квадратную тень вокруг
                     * кнопок»: большой светло-серый прямоугольник занимал
                     * треть экрана и спорил с портретом за внимание.
                     * Круглые кнопки группируются и сами — одинаковой
                     * формой и ровной сеткой; отдельный фон под ними
                     * не нужен. Сами кружки стали заметнее: теперь они
                     * лежат прямо на светлом фоне экрана, а не на сером.
                     *
                     * Полоски-«ручки» сверху, как в макете, здесь нет:
                     * она обещает, что панель тянется пальцем, а она
                     * не тянется. Обещать жест и не дать его — хуже,
                     * чем не обещать.
                     */
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Возможности читаются заново на каждое событие
                        // телефонии: сеть сообщает их не сразу
                        val canHold = remember(revision, call) { CallManager.canHold }
                        val canMerge = remember(revision, call, secondary) { CallManager.canMerge }
                        val canAddCall = remember(revision, call, secondary) { CallManager.canAddCall }
                        val context = androidx.compose.ui.platform.LocalContext.current

                        ControlsRow(
                            audioState = audioState,
                            onManualAudioChange = onManualAudioChange,
                            onHold = { CallManager.toggleHold() },
                            onDialpad = { showDialpad = !showDialpad },
                            onSwap = { CallManager.swapCalls() },
                            onMerge = { CallManager.merge() },
                            hasSecondary = secondary != null,
                            isHolding = state == Call.STATE_HOLDING,
                            canHold = canHold,
                            canMerge = canMerge,
                            canAddCall = canAddCall,
                            onAddCall = { openDialerForSecondCall(context) }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                  }
                }

                /*
                 * ЗАМЕТКА ВО ВРЕМЯ РАЗГОВОРА.
                 *
                 * Договорённости забываются за минуты: «привезут
                 * во вторник после четырёх» произносится один раз.
                 * Экран после звонка помогает не всегда — разговор
                 * может длиться двадцать минут, и к концу начало
                 * уже забыто.
                 *
                 * Показывается только на активном вызове: во время
                 * гудков записывать нечего.
                 */
                if (state == Call.STATE_ACTIVE) {
                    TextButton(onClick = { showNote = !showNote }) {
                        Icon(Icons.Default.EditNote, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (showNote) R.string.incall_note_hide
                                else R.string.incall_note_add
                            )
                        )
                    }
                }

                AnimatedVisibility(showNote) {
                    InCallNoteField(
                        text = noteState.text,
                        onTextChange = { noteState.onTextChange(it) },
                        selectedTags = noteState.tags,
                        onToggleTag = { noteState.toggleTag(it) },
                        /*
                         * Клавиатура открывается сразу.
                         *
                         * Человек нажал «Записать заметку» во время
                         * разговора — значит, хочет писать сейчас,
                         * а не искать, куда ещё нажать. Лишнее касание
                         * здесь стоит дорого: собеседник ждёт.
                         */
                        requestFocus = showNote
                    )
                }

                // Отклонить с сообщением. Кнопка появляется только если
                // оператор и прошивка это поддерживают: объяснять
                // пользователю особенности его оператора бессмысленно
                if (state == Call.STATE_RINGING && QuickReply.isAvailable()) {
                    /*
                     * Крупнее обычной текстовой кнопки.
                     *
                     * По ней попадают в тех же условиях, что и по ответу:
                     * телефон звонит, человек торопится. Стандартная
                     * высота текстовой кнопки — 40 dp, и промах мимо неё
                     * попадает в «Отклонить» рядом.
                     */
                    TextButton(
                        onClick = { showQuickReply = true },
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) {
                        // Стиль темы вместо числа: кегль подписи кнопки
                        // задаётся типографикой, а не на месте
                        Text(
                            stringResource(R.string.quick_reply_action),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    if (state == Call.STATE_RINGING) {
                        BigButton(
                            stringResource(R.string.incall_decline),
                            DeclineRed,
                            Icons.Default.CallEnd
                        ) { CallManager.reject() }

                        /*
                         * ПУЛЬСАЦИЯ КНОПКИ ОТВЕТА.
                         *
                         * ══════════════════════════════════════════════════
                         * ЗАЧЕМ АНИМАЦИЯ ИМЕННО ЗДЕСЬ
                         * ══════════════════════════════════════════════════
                         * Экран входящего вызова человек видит доли секунды
                         * и в неудобных условиях: телефон достают из кармана,
                         * смотрят искоса. Двигающийся объект глаз находит
                         * раньше неподвижного — это единственное место
                         * в приложении, где движение экономит время,
                         * а не украшает.
                         *
                         * ── Насколько заметная ─────────────────────────────
                         * Размах 4%: кнопка «дышит», а не прыгает. Больше —
                         * и она начинает мешать целиться, а промах здесь
                         * означает сброшенный звонок.
                         *
                         * Период 1.1 секунды: примерно вдвое медленнее
                         * пульса, спокойный ритм. Быстрее — тревожно,
                         * медленнее — не замечается.
                         *
                         * ── Только у ответа ────────────────────────────────
                         * У «Отклонить» пульсации нет намеренно: анимация
                         * притягивает палец, и притягивать его к отказу
                         * от звонка не нужно.
                         */
                        val pulse = rememberInfiniteTransition(label = "answer")
                        val scale by pulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.04f,
                            animationSpec = infiniteRepeatable(
                                tween(1100, easing = FastOutSlowInEasing),
                                RepeatMode.Reverse
                            ),
                            label = "answerScale"
                        )

                        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                            BigButton(
                                stringResource(R.string.incall_answer),
                                CallGreen,
                                Icons.Default.Call
                            ) { CallManager.answer() }
                        }
                    } else {
                        BigButton(
                            stringResource(R.string.incall_hangup),
                            DeclineRed,
                            Icons.Default.CallEnd
                        ) { CallManager.hangup() }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * СОСТОЯНИЕ ВЫЗОВА И ТАЙМЕР.
 *
 * Таймер тикает раз в секунду, только когда вызов активен.
 *
 * ── Почему плашка, а не просто строка ──────────────────────────────────────
 * Голый текст между именем и кнопками терялся: на пустом экране глазу
 * не за что зацепиться, и «Соединение…» читалось как часть имени.
 * Плашка отделяет состояние от того, кому звонишь.
 *
 * ── Почему у разговора зелёный, а у остального нет ─────────────────────────
 * Зелёный в приложении означает ровно одно — «связь есть, разговор идёт».
 * Гудки, удержание и завершение зелёными быть не должны: человек читает
 * цвет раньше текста, и зелёный на «Соединение…» сообщал бы, что
 * соединились, когда ещё нет.
 */
@Composable
private fun CallStatusLine(state: Int, call: Call?) {
    val active = state == Call.STATE_ACTIVE

    val text = if (active) {
        val connectTime = call?.details?.connectTimeMillis ?: System.currentTimeMillis()
        var elapsed by remember { mutableLongStateOf(0L) }
        LaunchedEffect(connectTime) {
            while (true) {
                elapsed = (System.currentTimeMillis() - connectTime) / 1000
                delay(1000)
            }
        }
        /*
         * Locale указывается явно.
         *
         * Без него формат берёт язык системы, а в арабской и некоторых
         * индийских локалях %02d рисуется местными цифрами: таймер
         * разговора превращался в набор незнакомых знаков.
         * ROOT — потому что это часы и минуты, а не текст.
         */
        String.format(java.util.Locale.ROOT, "%02d:%02d", elapsed / 60, elapsed % 60)
    } else {
        when (state) {
            Call.STATE_RINGING -> stringResource(R.string.incall_incoming)
            Call.STATE_DIALING, Call.STATE_CONNECTING -> stringResource(R.string.incall_connecting)
            Call.STATE_HOLDING -> stringResource(R.string.incall_on_hold)
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED ->
                stringResource(R.string.incall_ended)
            else -> ""
        }
    }

    // Пустое состояние плашки не рисуем: пустая пилюля выглядит
    // как незагрузившийся элемент
    if (text.isBlank()) return

    /*
     * ЦВЕТ ПЛАШКИ МЕНЯЕТСЯ ПЛАВНО.
     *
     * Переход «Соединение…» → таймер — момент, когда собеседник взял
     * трубку. Мгновенная смена серого на зелёный читалась как подмена
     * элемента: глаз замечал вспышку, но не связывал её с событием.
     *
     * 300 мс: достаточно, чтобы движение заметили, и не настолько
     * долго, чтобы плашка «догоняла» уже идущий разговор.
     */
    val background by animateColorAsState(
        if (active) CallGreen else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(Motion.NORMAL_MS),
        label = "statusBackground"
    )
    val foreground by animateColorAsState(
        if (active) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(Motion.NORMAL_MS),
        label = "statusForeground"
    )

    Surface(
        shape = CircleShape,
        color = background,
        contentColor = foreground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (active) Icons.Default.Call else Icons.Default.Schedule,
                contentDescription = null,
                // 20 dp вместо 18: значок рядом с titleMedium,
                // и размер по сетке смотрится ровнее с текстом
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/*
 * @OptIn нужен из-за FlowRow: раскладка с переносом до сих пор помечена
 * экспериментальной, и без согласия компилятор не собирает файл вовсе.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsRow(
    audioState: CallAudioState?,
    /**
     * Уведомление о ручном переключении звука.
     *
     * Без него автоматика продолжала бы командовать динамиком после того,
     * как пользователь переключил его сам: он включает громкую связь,
     * подносит телефон к уху что-то сказать — и связь выключается
     * против его воли. Спорить с пользователем недопустимо.
     */
    onManualAudioChange: () -> Unit,
    onHold: () -> Unit,
    onDialpad: () -> Unit,
    onSwap: () -> Unit,
    onMerge: () -> Unit,
    hasSecondary: Boolean,
    isHolding: Boolean,
    /** Сеть позволяет удержание: разбор в CallManager.canHold. */
    canHold: Boolean = true,
    /** Две линии можно объединить: разбор в CallManager.canMerge. */
    canMerge: Boolean = true,
    /** Можно набрать ещё номер, не прерывая разговор. */
    canAddCall: Boolean = false,
    onAddCall: () -> Unit = {}
) {
    val muted = audioState?.isMuted == true
    val speaker = audioState?.route == CallAudioState.ROUTE_SPEAKER
    val btAvailable = (audioState?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH != 0
    val btActive = audioState?.route == CallAudioState.ROUTE_BLUETOOTH

    /*
     * СЕТКА КНОПОК, А НЕ ДВА РЯДА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ЧТО БЫЛО НЕ ТАК
     * ══════════════════════════════════════════════════════════════════════
     * Кнопки стояли двумя жёсткими рядами по три. Но вторая строка
     * заполняется по обстоятельствам: Bluetooth есть не всегда,
     * переключение и объединение — только при втором вызове.
     *
     * В обычном разговоре во второй строке оставалась одна кнопка
     * «Удержать», прижатая к центру, и половина строки пустовала.
     *
     * ── Как теперь ─────────────────────────────────────────────────────────
     * FlowRow раскладывает кнопки сам: три в строке, перенос по мере
     * появления. Пустых мест не остаётся при любом наборе.
     *
     * maxItemsInEachRow = 3 — чтобы на широком экране они не вытянулись
     * в одну строку и не стали мелкими мишенями.
     */
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 3,
        modifier = Modifier.fillMaxWidth()
    ) {
            ToggleTile(muted, Icons.Default.MicOff, stringResource(R.string.incall_mute)) {
                CallManager.setMuted(!muted)
            }
            ToggleTile(speaker, Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.incall_speaker)) {
                onManualAudioChange()
                CallManager.setAudioRoute(
                    if (speaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
                )
            }
            ToggleTile(false, Icons.Default.Dialpad, stringResource(R.string.incall_keypad), onDialpad)

            // Только если сеть умеет: иначе кнопка молча не работала бы
            if (canHold) {
                ToggleTile(isHolding, Icons.Default.Pause, stringResource(R.string.incall_hold), onHold)
            }

            /*
             * «Добавить вызов» — набрать ещё номер, не кладя трубку.
             *
             * Раньше вторая линия появлялась только одним путём: кто-то
             * звонил сам. Позвонить третьему можно было, только выйдя
             * из экрана вызова в журнал, — и догадаться об этом нельзя.
             */
            if (canAddCall) {
                ToggleTile(false, Icons.Default.AddIcCall, stringResource(R.string.incall_add_call), onAddCall)
            }
            if (btAvailable) {
                ToggleTile(btActive, Icons.Default.Bluetooth, stringResource(R.string.incall_bluetooth)) {
                    onManualAudioChange()
                    CallManager.setAudioRoute(
                        if (btActive) CallAudioState.ROUTE_EARPIECE
                        else CallAudioState.ROUTE_BLUETOOTH
                    )
                }
            }
            if (hasSecondary) {
                ToggleTile(false, Icons.Default.SwapCalls, stringResource(R.string.incall_swap), onSwap)
                if (canMerge) {
                    ToggleTile(false, Icons.AutoMirrored.Filled.MergeType, stringResource(R.string.incall_merge), onMerge)
                }
        }
    }
}

@Composable
private fun ToggleTile(
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    /*
     * КНОПКИ УПРАВЛЕНИЯ КРУПНЕЕ.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Стандартный размер FilledIconToggleButton — 40 dp, а подпись
     * была 11 sp. Во время разговора телефон часто держат у уха
     * и нажимают вслепую, глядя одним глазом: промах означает
     * включённый динамик посреди личного разговора.
     *
     * 64 dp и подпись 13 sp дают запас, который заметно снижает число
     * промахов. Места хватает: экран вызова почти пуст.
     */
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconToggleButton(
            checked = active,
            onCheckedChange = { onClick() },
            // Размер из правил: тот же, что у быстрых действий
            // в карточке абонента — они и по смыслу одно и то же
            modifier = Modifier.size(FlowUi.quickActionSize + 16.dp)
        ) {
            Icon(icon, label, Modifier.size(28.dp))
        }
        // Подпись — если не выключена в настройках оформления.
        // Описание для TalkBack у значка есть и без неё
        if (com.ogneliss.dialer.ui.theme.LocalAppearance.current.buttonLabels) {
            Spacer(Modifier.height(8.dp))
            // labelMedium: подпись под значком — тот же стиль,
            // что у подписей в нижней панели журнала
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * ВЫБОР ШАБЛОНА БЫСТРОГО ОТВЕТА.
 *
 * ── Почему шторка, а не диалог ─────────────────────────────────────────────
 * Телефон звонит, и до кнопок по центру экрана не дотянуться одной рукой.
 * Шторка выезжает снизу — прямо под большой палец.
 *
 * ── Про отправку ───────────────────────────────────────────────────────────
 * Сообщение отправляет система через SMS-приложение по умолчанию.
 * Разрешение SEND_SMS приложению не требуется, и запрашивать его
 * в диалере значило бы вызвать те же подозрения, что и микрофон.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplySheet(
    templates: List<String>,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.quick_reply_title),
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge
            )
            HorizontalDivider()
            templates.forEach { template ->
                ListItem(
                    headlineContent = { Text(template) },
                    modifier = Modifier
                        .heightIn(min = com.ogneliss.dialer.ui.theme.Dimens.MIN_TOUCH_TARGET)
                        .clickable { onSend(template) }
                )
            }
        }
    }
}

/**
 * ПОЛЕ ЗАМЕТКИ НА ЭКРАНЕ ВЫЗОВА.
 *
 * ── Почему без кнопки «Сохранить» ──────────────────────────────────────────
 * Во время разговора внимание занято, и требовать подтверждения —
 * верный способ потерять текст: человек допишет мысль, положит трубку,
 * и заметка исчезнет вместе с экраном.
 *
 * Текст сохраняется сам после паузы в наборе и ещё раз при завершении
 * вызова. Подпись под полем сообщает об этом прямо, чтобы не возникало
 * тревоги «а сохранилось ли».
 *
 * ── Про размер поля ────────────────────────────────────────────────────────
 * Три строки: достаточно для «привезут во вторник после четырёх, звонить
 * Марине», но не настолько много, чтобы поле вытеснило кнопку завершения
 * вызова за пределы экрана.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InCallNoteField(
    text: String,
    onTextChange: (String) -> Unit,
    /** Уже выбранные метки: они могли быть поставлены раньше. */
    selectedTags: Set<String> = emptySet(),
    onToggleTag: (String) -> Unit = {},
    requestFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            // Небольшая пауза: панель ещё выезжает, и запрос фокуса
            // до конца анимации система игнорирует
            kotlinx.coroutines.delay(150)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.incall_note_hint)) },
            /*
             * Поле выше: четыре строки вместо двух.
             *
             * Заметку во время разговора пишут не глядя — собеседник
             * говорит, а взгляд на экране лишь мельком. В двух строках
             * текст начинал прокручиваться внутри поля уже на первой
             * фразе, и написанное уходило из виду.
             *
             * Места на экране вызова достаточно: там кроме портрета
             * и кнопок ничего нет.
             */
            minLines = 4,
            maxLines = 6,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        /*
         * МЕТКИ ДОСТУПНЫ И ВО ВРЕМЯ РАЗГОВОРА.
         *
         * ── Зачем ──────────────────────────────────────────────────────────
         * Раньше их можно было поставить только после звонка или
         * из карточки абонента. А самый частый случай — как раз во время:
         * поднял трубку, услышал предложение кредита, отметил «спам»
         * и положил.
         *
         * Одно нажатие вместо «дождаться конца, найти запись, открыть
         * карточку, поставить метку».
         *
         * ── Почему с переносом по строкам ──────────────────────────────────
         * Ряд с прокруткой прятал бы часть меток за краем, а искать
         * их пальцем во время разговора неудобно вдвойне.
         */
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TagCatalog.suggested.forEach { tag ->
                FilterChip(
                    selected = tag.key in selectedTags,
                    onClick = { onToggleTag(tag.key) },
                    // С заглавной — как в карточке, журнале и после звонка
                    label = { Text(stringResource(tag.labelRes).replaceFirstChar { it.titlecase() }) }
                )
            }
        }

        Text(
            stringResource(R.string.incall_note_autosave),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp, start = 8.dp)
        )
    }
}

@Composable
private fun DtmfPanel(
    entered: String,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {

            // Поле ввода отделено от клавиш чертой: без разделения
            // непонятно, где заканчивается результат и начинается набор
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entered.ifEmpty { stringResource(R.string.incall_dtmf_hint) },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (entered.isEmpty()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                if (entered.isNotEmpty()) {
                    IconButton(onClick = onBackspace) {
                        Icon(
                            Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.a11y_backspace)
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.dialpad_hide)
                    )
                }
            }

            // Тот же разделитель, что везде: своя толщина здесь была
            // заметнее соседних линий на других экранах
            OgnelissDivider()
            Spacer(Modifier.height(8.dp))

            // Та же клавиатура, что на главном экране: одинаковые размеры
            // и одинаковый отклик — переучиваться не приходится
            Dialpad(onDigit = onDigit, onLongDigit = {})
        }
    }
}


/**
 * КТО ЗВОНИТ: ИМЯ ИЛИ ЗАМЕТКА, НОМЕР, ПОМЕТКА.
 *
 * ── Три случая ─────────────────────────────────────────────────────────────
 * • Номер в контактах — имя крупно, заметка курсивом под ним,
 *   номер третьей строкой.
 * • Номера нет, но есть заметка — она ВМЕСТО имени: единственное,
 *   что человек знает об этом номере.
 * • Нет ни того, ни другого — только номер, и он же заголовок.
 *
 * Правила общие с журналом вызовов и карточкой абонента: их задаёт
 * NoteDisplay. Своя реализация здесь означала бы, что экраны разойдутся
 * при следующей правке.
 *
 * ── Почему номер показывается даже при известном имени ─────────────────────
 * По нему решают, брать ли трубку: один человек звонит с рабочего
 * и личного, и это меняет ответ. Исключение — когда номер уже стоит
 * заголовком, дублировать его незачем.
 *
 * ── Почему заметка курсивом ────────────────────────────────────────────────
 * Курсив отличает пометку человека от данных телефонии. «Мошенник» —
 * это то, что записал он сам, а не то, что сообщила сеть, и путать
 * одно с другим нельзя.
 */
@Composable
private fun CallerIdentity(
    display: NoteDisplay.Result,
    number: String,
    /** «Билайн · Москва и Московская область» или null. */
    carrierLine: String?,
    titleSize: TextUnit,
    /**
     * У номера метка «спам» или «мошенники».
     *
     * Заголовок тогда красный: цвет доходит до человека раньше текста,
     * а на экране вызова счёт идёт на секунды.
     */
    danger: Boolean = false
) {
    Text(
        display.title,
        fontSize = titleSize,
        color = if (danger) BlockRed else androidx.compose.ui.graphics.Color.Unspecified,
        /*
         * МЕЖСТРОЧНЫЙ ИНТЕРВАЛ ЗАДАЁТСЯ ВМЕСТЕ С РАЗМЕРОМ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО БЫЛО НЕ ТАК
         * ══════════════════════════════════════════════════════════════════
         * Указан был только fontSize. Интервал при этом остаётся
         * от стиля по умолчанию — он рассчитан на обычный текст,
         * а не на 36 sp.
         *
         * Пока имя помещалось в строку, разницы не было. Длинное —
         * «Дмитрий Плиточник Краснодар» — переносилось на две,
         * и строки наезжали друг на друга.
         *
         * ── Почему 1.15 ────────────────────────────────────────────────────
         * Обычный для заголовков множитель — 1.2–1.3, но здесь текст
         * крупный и в одну-две строки: широкий интервал разрывал бы
         * имя на два несвязанных куска. 1.15 достаточно, чтобы буквы
         * не задевали друг друга, и достаточно тесно, чтобы имя
         * читалось целиком.
         */
        lineHeight = titleSize * 1.15f,
        textAlign = TextAlign.Center,
        /*
         * Три строки — предел.
         *
         * Совсем длинное имя иначе вытеснит номер и кнопки за край
         * экрана. Обрыв многоточием честнее: видно, что имя длиннее,
         * и остальное на месте.
         */
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )

    display.subtitle?.let { note ->
        Spacer(Modifier.height(4.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    // Номер не дублируется, если он уже стоит заголовком
    if (display.title != PhoneFormatter.format(number)) {
        Spacer(Modifier.height(4.dp))
        Text(
            PhoneFormatter.format(number),
            fontSize = FlowUi.callNumberSize,
            color = MaterialTheme.colorScheme.outline
        )
    }

    /*
     * Оператор и регион — мельче номера.
     *
     * Порядок сверху вниз: кто звонит, с какого номера, откуда номер.
     * Последнее — уточнение, и набирать его тем же кеглем значит
     * спорить с именем за внимание в те три секунды, что есть
     * на решение.
     */
    carrierLine?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * ПОРТРЕТ ЗВОНЯЩЕГО.
 *
 * ── Почему отдельным компонентом ───────────────────────────────────────────
 * Рисуется в двух ориентациях с разным размером. Держать две копии
 * означало бы, что при следующей правке одну забудут — и при повороте
 * телефона портрет вёл бы себя иначе.
 *
 * ── Без фотографии портрета нет вовсе ──────────────────────────────────────
 * Раньше при отсутствии снимка рисовался серый круг с силуэтом. На экране
 * вызова он занимал верхнюю треть и не сообщал ничего: силуэт одинаков
 * у всех, кто не завёл фотографию, а таких большинство. Имя при этом
 * уезжало вниз, и у длинного имени в три строки — «Екатерина
 * Ставропольская Квартиросъемщик» — на него оставалась узкая полоса.
 *
 * Теперь: есть снимок — есть портрет, нет снимка — экран начинается
 * с имени.
 *
 * ⚠️ Компонент возвращает пустоту, поэтому отступ ПОСЛЕ него ставится
 * только вместе с портретом: иначе на экране без фотографии остался бы
 * висеть зазор от несуществующего элемента.
 */
@Composable
private fun CallerAvatar(photoUri: String?, size: Dp) {
    // Пустой путь и путь на удалённый файл — одно и то же: показывать
    // нечего. Проверка на пустую строку нужна, потому что часть
    // прошивок отдаёт "" вместо null
    val uri = photoUri?.takeIf { it.isNotBlank() } ?: return

    /*
     * КРУГ, А НЕ СКРУГЛЁННЫЙ КВАДРАТ.
     *
     * ── Почему здесь исключение из общего правила ──────────────────────────
     * По правилам приложения аватары — shapes.extraLarge. На экране
     * вызова портрет остаётся единственным крупным пятном, и квадрат
     * с закруглениями спорил с круглыми кнопками управления
     * и с кнопкой завершения: три разные формы на пустом экране.
     *
     * В списках это правило остаётся: там аватар стоит в ряду
     * прямоугольных карточек, и круг выбивался бы уже он.
     *
     * ── Тень ───────────────────────────────────────────────────────────────
     * 6 dp: портрет отделяется от фона, из-за чего лицо читается быстрее
     * при беглом взгляде. Больше — и тень начинает выглядеть грязным
     * ореолом на тёмной теме.
     */
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 4.dp,
        modifier = Modifier.size(size)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * КНОПКА ОТВЕТА И ОТКЛОНЕНИЯ.
 *
 * ── Почему такая крупная ───────────────────────────────────────────────────
 * По ней попадают в худших условиях, какие бывают: телефон звонит,
 * человек достаёт его из кармана на ходу, часто одной рукой и не глядя
 * толком. Промах здесь означает либо сброшенный нужный звонок, либо
 * принятый нежелательный — и то и другое исправить нельзя.
 *
 * 72 dp против прежних 56 и ширина от 150 dp: это заметно больше
 * рекомендованного минимума в 48 dp, и здесь запас оправдан. Места
 * на экране вызова достаточно — там кроме портрета и двух кнопок
 * ничего нет.
 *
 * Текст крупнее по той же причине: подпись читают боковым зрением,
 * не вглядываясь.
 */
@Composable
private fun BigButton(
    text: String,
    color: Color,
    /**
     * Значок рядом с подписью.
     *
     * ── Зачем он ───────────────────────────────────────────────────────────
     * Подпись читают, значок узнают. На звонящем телефоне вторая
     * операция быстрее, и трубка вниз/трубка вверх различаются
     * боковым зрением, когда слово ещё не прочитано.
     *
     * ── Почему подпись всё-таки осталась ───────────────────────────────────
     * В макете у кнопки только значок. Но значок трубки означает
     * «звонить» столько же, сколько «завершить», — эти две трубки
     * различает наклон, а он мелкий. Ошибка стоит сорванного
     * разговора, поэтому слово оставлено.
     */
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        /*
         * Высота из правил FlowUI.
         *
         * Кнопка ответа — то же по смыслу, что «Позвонить» в карточке:
         * главное действие экрана. Разные числа здесь означали бы,
         * что одно и то же действие выглядит по-разному в зависимости
         * от того, откуда пришёл вызов.
         *
         * Плюс запас: по кнопке ответа попадают в худших условиях —
         * телефон достают из кармана на ходу.
         */
        modifier = Modifier
            .height(FlowUi.primaryActionHeight + 16.dp)
            .widthIn(min = FlowUi.callActionMinWidth)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = Color.White,
            fontSize = FlowUi.callNumberSize,
            fontWeight = FontWeight.Medium
        )
    }
}


/**
 * Открыть набор номера для второго вызова, не завершая текущий.
 *
 * Приложение запускается с флагом «открыть клавиатуру»: человек пришёл
 * набирать номер, а не смотреть журнал. Дальше всё обычным путём —
 * набранный номер уходит в систему, и Telecom сам ставит текущий вызов
 * на удержание (мы проверили заранее, что сеть это умеет, — иначе
 * плитки не было бы).
 *
 * Экран вызова при этом не закрывается: он вернётся, как только
 * новый вызов начнётся.
 */
private fun openDialerForSecondCall(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(context, com.ogneliss.dialer.MainActivity::class.java)
                .putExtra(com.ogneliss.dialer.MainActivity.EXTRA_OPEN_DIALPAD, true)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { Logger.w("InCall", "Не удалось открыть набор для второго вызова", it) }
}

/** Имя по номеру из книги, иначе номер — для плашек второго вызова и участников. */
@Composable
private fun rememberCallerLabel(
    call: Call,
    contactsRepo: com.ogneliss.dialer.data.ContactsRepository
): String {
    val raw = call.details?.handle?.schemeSpecificPart.orEmpty()
    val fallback = if (raw.isBlank()) stringResource(R.string.incall_hidden_number)
    else PhoneFormatter.format(raw)

    val name by produceState<String?>(null, raw) {
        value = if (raw.isBlank()) null
        else runCatching { contactsRepo.lookupByNumber(raw) }.getOrNull()
    }
    return name?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * ПЛАШКА ВТОРОГО ВХОДЯЩЕГО.
 *
 * Три кнопки, а не две: при втором звонке выбор не «ответить или нет»,
 * а «что станет с тем, с кем говорю сейчас». Удержание — первым,
 * как в системном приложении: это самый частый и самый безопасный
 * выбор — никого не теряешь.
 */
@Composable
private fun WaitingCallBanner(
    call: Call,
    contactsRepo: com.ogneliss.dialer.data.ContactsRepository
) {
    val label = rememberCallerLabel(call, contactsRepo)

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.incall_waiting_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { CallManager.answerWaitingHoldCurrent() },
                colors = ButtonDefaults.buttonColors(containerColor = CallGreen),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.incall_waiting_hold_answer)) }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { CallManager.answerWaitingEndCurrent() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.incall_waiting_end_answer),
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
                OutlinedButton(
                    onClick = { CallManager.rejectWaiting() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DeclineRed),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.incall_decline)) }
            }
        }
    }
}

/**
 * УЧАСТНИКИ КОНФЕРЕНЦИИ.
 *
 * Кнопки у участника — только те, что сеть разрешила: «отключить»
 * без разрешения на части сетей кладёт трубку всей конференции,
 * а «поговорить отдельно» без разрешения просто ничего не делает.
 */
@Composable
private fun ConferencePanel(
    participants: List<Call>,
    contactsRepo: com.ogneliss.dialer.data.ContactsRepository
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.incall_conference_title, participants.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            participants.forEach { child ->
                val label = rememberCallerLabel(child, contactsRepo)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = 16.dp, end = 4.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (CallManager.canSplitParticipant(child)) {
                        IconButton(onClick = { CallManager.splitParticipant(child) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.CallSplit,
                                contentDescription = stringResource(R.string.incall_conference_split)
                            )
                        }
                    }

                    if (CallManager.canDropParticipant(child)) {
                        IconButton(onClick = { CallManager.dropParticipant(child) }) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = stringResource(R.string.incall_conference_drop),
                                tint = DeclineRed
                            )
                        }
                    }
                }
            }
        }
    }
}
