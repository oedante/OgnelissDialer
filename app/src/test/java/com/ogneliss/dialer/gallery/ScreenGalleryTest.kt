package com.ogneliss.dialer.gallery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.R
import com.ogneliss.dialer.data.CallLogEntry
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.data.NumberNote
import com.ogneliss.dialer.data.NumberPresentation
import com.ogneliss.dialer.data.NumberCategory
import com.ogneliss.dialer.data.NumberInfo
import com.ogneliss.dialer.data.PhoneNumber
import com.ogneliss.dialer.data.SimSlot
import com.ogneliss.dialer.settings.CarrierInfoMode
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.settings.SwipeAction
import com.ogneliss.dialer.sms.Message
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.settings.UiScale
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.UiState
import com.ogneliss.dialer.ui.screens.CallArchiveScreen
import com.ogneliss.dialer.ui.screens.BlockListScreen
import com.ogneliss.dialer.ui.screens.ConsentScreen
import com.ogneliss.dialer.ui.screens.ContactsScreen
import com.ogneliss.dialer.ui.screens.FaqScreen
import com.ogneliss.dialer.ui.screens.MainDialerScreen
import com.ogneliss.dialer.ui.screens.NotesScreen
import com.ogneliss.dialer.ui.screens.PermissionWizardScreen
import com.ogneliss.dialer.ui.screens.PinnedContactsScreen
import com.ogneliss.dialer.ui.screens.RemindersScreen
import com.ogneliss.dialer.ui.screens.SettingsCategoryScreen
import com.ogneliss.dialer.ui.screens.SettingsHomeScreen
import com.ogneliss.dialer.ui.screens.SpamProtectionScreen
import com.ogneliss.dialer.ui.screens.StatsScreen
import com.ogneliss.dialer.ui.screens.SubscriberScreen
import com.ogneliss.dialer.ui.settings.SettingsCategory
import com.ogneliss.dialer.ui.theme.OgnelissDialerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * ГАЛЕРЕЯ ЭКРАНОВ (2.7.0, обзор вида 15.11.2026; согласие автора на
 * ui-test-junit4 для тестов — в тот же день).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ
 * ══════════════════════════════════════════════════════════════════════════
 * Автор: «чтобы приложение имело единое оформление и не было ощущения, что
 * оно сделано из разных кусков». Разнобой (шапки, пустые экраны, поля ввода,
 * заголовки) не видно ни тестам логики, ни проверкам scripts/ — только
 * глазами, на экранах рядом. Тест рисует экраны приложения с одними и теми
 * же данными в светлой, тёмной теме и при крайних настройках (шрифт 160 %,
 * масштаб 110 %) и складывает снимки в app/build/gallery/.
 *
 * Это не сравнение с эталоном (снимки зависят от шрифтов машины): тест
 * падает, только если экран не собрался — исключение в разметке. Смотреть
 * снимки — человеку или тому, кто правит интерфейс: до и после правки.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * КАК УСТРОЕН
 * ══════════════════════════════════════════════════════════════════════════
 * Экраны берут DialerViewModel из Hilt, поэтому запускается настоящая
 * MainActivity, а её содержимое заменяется нужным экраном. Контакты, журнал
 * и заметки подкладываются в состояние ViewModel напрямую (поле _state):
 * в тестовой среде нет ни телефонной книги, ни журнала вызовов.
 *
 * Часы Compose идут вручную (autoAdvance = false): на экранах есть
 * бесконечные анимации (заглушка журнала), и ожидание «покоя» не
 * закончилось бы.
 *
 * 2.7.5: добавлены журнал С ЗАПИСЯМИ (строки собирает ViewModel из
 * подставленных групп — нужно дать ей время на фоновую работу: часы теста
 * идут вручную, а потоки настоящие, поэтому settle() ещё и спит), его
 * режимы (клавиатура набора, поиск, выбор, цветные стрелки), все разделы
 * настроек, остальные экраны и окна, список SMS и переписка (роль SMS
 * включается теневым RoleManager, данные кладутся в состояние их
 * ViewModel). Экран вызова — в CallScreenGalleryTest (ему нужна своя
 * Activity).
 *
 * Не собравшийся экран из списка убирается, а не ломает тест: причина — в
 * отчёте к выпуску 2.7.5.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h873dp-xxhdpi")
class ScreenGalleryTest {

    /*
     * v2 (2.8.4, предупреждение сборки автора): прежний createAndroidComposeRule
     * устарел. v2 ставит задачи корутин в очередь (StandardTestDispatcher), а не
     * выполняет сразу; галерея и так ведёт часы вручную (settle), очередь
     * разбирается там же.
     */
    @get:Rule val rule = androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<MainActivity>()

    private val out = File("build/gallery").apply { mkdirs() }
    private val now = System.currentTimeMillis()

    /** Снимок «контакта»: силуэт с красными линиями по краям — видно, обрезано ли фото. */
    private val photo: String by lazy {
        val f = File(out, "_photo.png")
        val b = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 720f, 0xFF6A8CAF.toInt(), 0xFFE9C9A0.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 720f, 720f, p)
        p.shader = null
        p.color = 0xFF8B5A3C.toInt(); c.drawCircle(360f, 300f, 150f, p)
        p.color = 0xFF2F4F6F.toInt(); c.drawRoundRect(140f, 470f, 580f, 900f, 160f, 160f, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(310f, 280f, 18f, p); c.drawCircle(410f, 280f, 18f, p)
        p.color = 0xFFFF0000.toInt(); p.strokeWidth = 8f
        c.drawLine(0f, 4f, 720f, 4f, p); c.drawLine(0f, 716f, 720f, 716f, p)
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.recycle()
        "file://" + f.absolutePath
    }

    private fun contact(id: Long, name: String, number: String, company: String = "", title: String = "", withPhoto: Boolean = false, starred: Boolean = false) =
        Contact(
            id, "k$id", name, if (withPhoto) photo else null, starred,
            listOf(PhoneNumber(number, "Мобильный", number)),
            emails = if (withPhoto) listOf("boris@example.com") else emptyList(),
            company = company, jobTitle = title
        )

    private val contacts by lazy {
        listOf(
            contact(1, "Анна Смирнова", "+79001110001", starred = true),
            contact(2, "Борис Кузнецов", "+79001110002", "Газпромбанк", "ведущий специалист", withPhoto = true),
            contact(3, "Валентина Попова", "+79001110003"),
            contact(4, "Григорий Васильев", "+79001110004", "Сбер"),
            contact(5, "Дарья Новикова", "+79001110005"),
            contact(6, "Евгений Фёдоров", "+79001110006"),
            contact(7, "Жанна Морозова", "+79001110007"),
            contact(8, "Иван Петров", "+79001110008", "Газпромбанк", "менеджер")
        )
    }

    /** Начало сегодняшнего дня: «Сегодня», «Вчера» и даты в журнале считаются от него, а не от часа запуска теста. */
    private val startOfDay = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Две карты: значок SIM в строках журнала появляется, только когда карт две. */
    private val sims = listOf(
        SimSlot(1, 0, "SIM 1", null, "Beeline"),
        SimSlot(2, 1, "SIM 2", null, "МТС")
    )

    /**
     * Запись журнала. day — сколько дней назад (0 — сегодня), hour/minute — время суток;
     * сегодняшнее не уходит в будущее, если тест запущен рано утром.
     */
    private fun group(
        id: Long, number: String, name: String?, type: CallType, day: Int, hour: Int, minute: Int = 0,
        count: Int = 1, contactId: Long? = null, presentation: NumberPresentation = NumberPresentation.ALLOWED, sim: Int = 1
    ): CallLogGroup {
        val planned = startOfDay - day * 86_400_000L + hour * 3_600_000L + minute * 60_000L
        val date = if (day == 0) minOf(planned, now - 60_000L) else planned
        val entry = CallLogEntry(id, number, number, name, null, type, date, 75, sim.toString(), contactId, presentation)
        val subscriber = when {
            presentation.hidden -> "hidden:${presentation.name}"
            contactId != null -> "contact:$contactId"
            else -> "number:$number"
        }
        return CallLogGroup(
            key = "$subscriber#$id", lastCallId = id, number = number, e164 = number, displayName = name, photoUri = null,
            contactId = contactId, lastType = type, lastDate = date, durationSec = 75, count = count,
            incomingCount = if (type == CallType.INCOMING) count else 0,
            outgoingCount = if (type == CallType.OUTGOING) count else 0,
            missedCount = if (type == CallType.MISSED) count else 0,
            rejectedCount = if (type == CallType.REJECTED) count else 0, totalDurationSec = 75L * count,
            numbers = if (number.isBlank()) emptyList() else listOf(number),
            subscriptionLabel = if (sim == 1) "Beeline" else "МТС", subscriptionId = sim.toString(),
            presentation = presentation, entries = listOf(entry)
        )
    }

    /*
     * Журнал 2.7.5: все виды записей, три дня-подписи. Первые три — те же, что были
     * (на них ссылаются снимки карточек): скрытый номер, Анна «×3», незнакомый.
     */
    private val log by lazy {
        listOf(
            group(1, "", null, CallType.MISSED, 0, 12, 48, presentation = NumberPresentation.RESTRICTED),
            group(2, "+79001110001", "Анна Смирнова", CallType.INCOMING, 0, 11, 5, 3, 1),
            group(3, "+79180827805", null, CallType.OUTGOING, 0, 9, 30, sim = 2),
            group(7, "+79001110004", "Григорий Васильев", CallType.REJECTED, 0, 8, 15, contactId = 4),
            group(4, "+79001110002", "Борис Кузнецов", CallType.MISSED, 1, 20, 40, 2, 2),
            group(5, "+78005553535", null, CallType.INCOMING, 1, 18, 2),
            group(8, "+79001110003", "Валентина Попова", CallType.OUTGOING, 1, 10, 15, contactId = 3, sim = 2),
            group(6, "+79001110008", "Иван Петров", CallType.OUTGOING, 4, 16, 20, contactId = 8),
            group(9, "+79281234567", null, CallType.INCOMING, 6, 12, 0)
        )
    }

    /** Сведения об операторе и регионе — для незнакомых номеров. */
    private val numberInfo = mapOf(
        "+79180827805" to NumberInfo("+79180827805", "+7 918 082-78-05", "Россия", "Краснодарский край", "МТС", NumberCategory.PERSONAL),
        "+79281234567" to NumberInfo("+79281234567", "+7 928 123-45-67", "Россия", "Ростовская область", "Билайн", NumberCategory.PERSONAL),
        "+78005553535" to NumberInfo("+78005553535", "+7 800 555-35-35", "Россия", null, null, NumberCategory.TOLL_FREE)
    )

    /** Часы вручную: разбор в шапке файла. Спит: строки журнала и SMS собирают настоящие фоновые потоки. */
    private fun settle(ms: Long = 800) {
        rule.mainClock.autoAdvance = false
        var t = 0L
        while (t < ms) {
            rule.mainClock.advanceTimeBy(50)
            ShadowLooper.idleMainLooper()
            Thread.sleep(12)
            t += 50
        }
    }

    /** Подставить состояние в приватное поле _state любой ViewModel (DialerViewModel, ConversationViewModel…). */
    @Suppress("UNCHECKED_CAST")
    private fun <T> mutate(vm: Any, update: (T) -> T) {
        val field = vm.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        val flow = field.get(vm) as MutableStateFlow<T>
        flow.value = update(flow.value)
    }

    /** Исходное состояние перед каждым снимком: прежний снимок мог изменить запрос, журнал, карты. */
    private fun applyBase(vm: DialerViewModel) {
        mutate<UiState>(vm) {
            it.copy(
                query = "", contacts = contacts, callLog = log, filteredLog = log, filteredContacts = emptyList(), loading = false,
                hasContactsPermission = true, hasCallLogPermission = true, sims = sims, numberInfo = numberInfo,
                notes = mapOf("+79180827805" to NumberNote("+79180827805", "Сантехник, после 18:00", listOf("ремонт"), updatedAt = now))
            )
        }
        settle(300)
    }

    private fun viewModel(): DialerViewModel {
        rule.mainClock.autoAdvance = false
        val vm = ViewModelProvider(rule.activity)[DialerViewModel::class.java]
        settle(300)
        // 2.7.0: шторки жестов при первом запуске больше нет — закрывать нечего
        applyBase(vm)
        return vm
    }

    /** Роль SMS (теневой RoleManager): без неё переписка и список SMS передают дело другому приложению. */
    private fun grantSmsRole() {
        val manager = rule.activity.getSystemService(android.app.role.RoleManager::class.java)
        org.robolectric.Shadows.shadowOf(manager).addHeldRole(android.app.role.RoleManager.ROLE_SMS)
    }

    private fun msg(id: Long, address: String, body: String, minutesAgo: Long, outgoing: Boolean, unread: Boolean = false) = Message(
        id, false, 1, address, body, now - minutesAgo * 60_000, outgoing,
        if (outgoing) Message.Status.DELIVERED else Message.Status.RECEIVED, !unread, null
    )

    /** Переписка с Анной: входящие и исходящие вперемешку, последнее — непрочитанное. */
    private val chat by lazy {
        val a = "+79001110001"
        listOf(
            msg(1, a, "Привет! Ты сегодня заедешь за документами?", 190, false),
            msg(2, a, "Привет. Да, после обеда, около трёх.", 185, true),
            msg(3, a, "Отлично. Я буду на месте до шести, звони, если что-то изменится.", 180, false),
            msg(4, a, "Хорошо, договорились.", 176, true),
            msg(5, a, "Код подтверждения: 482913. Никому не сообщайте его.", 120, false),
            msg(6, a, "Заеду в три пятнадцать, пробки на мосту.", 25, true),
            msg(7, a, "Жду!", 22, false, unread = true)
        )
    }

    /** Список переписок: знакомые, незнакомый, сервисный отправитель, одна с непрочитанными. */
    private val threads by lazy {
        fun t(id: Long, address: String, body: String, minutesAgo: Long, outgoing: Boolean, unread: Int = 0) =
            com.ogneliss.dialer.ui.conversation.MessagesViewModel.Thread(id, address, msg(100 + id, address, body, minutesAgo, outgoing, unread > 0).copy(threadId = id), unread)
        listOf(
            t(1, "+79001110001", "Жду!", 22, false, 1),
            t(2, "+79001110002", "Спасибо, получил.", 90, true),
            t(3, "+79180827805", "Мастер будет в четверг после 18:00", 60 * 5, false),
            t(4, "Sber", "Покупка 1 250 ₽, карта *4821. Баланс 38 410 ₽", 60 * 9, false, 2),
            t(5, "+79001110008", "Перезвони, как освободишься", 60 * 30, false)
        )
    }

    /**
     * Раздел с колонкой разделов справа (2.9.3, проба) — как в RootScreen:
     * экран слева, SideSectionRail справа, действия раздела — через LocalSideRail.
     */
    @Composable
    private fun SideMode(current: com.ogneliss.dialer.ui.components.MainSection, content: @Composable () -> Unit) {
        val state = androidx.compose.runtime.remember { com.ogneliss.dialer.ui.components.SideRailState() }
        androidx.compose.runtime.CompositionLocalProvider(com.ogneliss.dialer.ui.components.LocalSideRail provides state) {
            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { content() }
                com.ogneliss.dialer.ui.components.SideSectionRail(
                    state = state,
                    // 2.9.9: «Колонка справа» по умолчанию — журнал у кнопки действия, выше остальное
                    items = com.ogneliss.dialer.ui.screens.RailItem.visible(null),
                    current = current,
                    onOpenSection = {}
                )
            }
        }
    }

    /** Псевдозвонок в облике живого фона [style] (2.9.10): как снимок «call_flame». */
    @Composable
    private fun AmbientShot(style: com.ogneliss.dialer.call.AmbientStyle) {
        androidx.compose.runtime.CompositionLocalProvider(com.ogneliss.dialer.call.LocalAmbientStyle provides style) {
            com.ogneliss.dialer.call.FakeCallScreen(
                name = "Анна Смирнова", number = "+79001110001", photoUri = null,
                ambientBackground = true, bareControls = true, onAnswer = {}, onHangUp = {}
            )
        }
    }

    /** Содержимое журнала — общее для всех его снимков. */
    private val journalContent: @Composable (DialerViewModel) -> Unit = { vm ->
        MainDialerScreen(
            vm = vm, prefilledNumber = null, onOpenContacts = {}, onOpenSettings = {}, onOpenFakeCallPicker = {},
            onOpenNotes = {}, onOpenReminders = {}, onOpenSubscriber = {}, onOpenBlockLists = {}
        )
    }

    /**
     * Один снимок. prepare — до сборки экрана; act — после неё (нажатия, подмена состояния
     * ViewModel экрана); cleanup — после снимка (вернуть настройку). popup — снимать выпадающее
     * окно, dialog — окно-шторку.
     */
    private class Shot(
        val name: String,
        val dialog: Boolean = false,
        val popup: Boolean = false,
        val prepare: (DialerViewModel) -> Unit = {},
        val act: (DialerViewModel) -> Unit = {},
        val cleanup: () -> Unit = {},
        val content: @Composable (DialerViewModel) -> Unit
    )

    private val baseShots: List<Shot> = listOf(
        // 2.7.5: журнал теперь С ЗАПИСЯМИ; прежний, пустой, — journal_empty
        Shot("journal", content = journalContent),
        Shot("journal_empty", prepare = { vm -> mutate<UiState>(vm) { it.copy(callLog = emptyList(), filteredLog = emptyList()) } }, content = journalContent),
        // Клавиатура набора с номером: подсказки T9 — контакты, чьи номера начинаются с набранного
        Shot("journal_dialpad", act = { vm ->
            rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.dialpad_show))[0].performClick()
            settle(500)
            mutate<UiState>(vm) { it.copy(query = "90011100", filteredContacts = contacts.take(4), filteredLog = log.filter { g -> g.contactId != null }) }
            settle(500)
        }, content = journalContent),
        // Поиск в журнале: «Меню» → «Поиск», запрос «Бор» — найден контакт и его запись
        Shot("journal_search", act = { vm ->
            rule.onAllNodesWithText(rule.activity.getString(R.string.bar_menu))[0].performClick()
            settle(400)
            rule.onAllNodesWithText(rule.activity.getString(R.string.nav_search))[0].performClick()
            settle(400)
            mutate<UiState>(vm) {
                it.copy(
                    query = "Бор",
                    filteredContacts = contacts.filter { c -> c.displayName.startsWith("Бор") },
                    filteredLog = log.filter { g -> g.displayName?.startsWith("Бор") == true }
                )
            }
            settle(500)
        }, content = journalContent),
        // Режим выбора: «⋮» шапки → «Выбрать несколько», отмечены две записи
        Shot("journal_select", act = { _ ->
            rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.topbar_list_actions))[0].performClick()
            settle(400)
            rule.onAllNodesWithText(rule.activity.getString(R.string.selection_start))[0].performClick()
            settle(500)
            rule.onAllNodesWithText("Анна Смирнова")[0].performClick()
            settle(300)
            rule.onAllNodesWithText("Иван Петров")[0].performClick()
            settle(500)
        }, content = journalContent),
        // «Цветные стрелки вызовов» включены (настройка «Журнал вызовов»)
        Shot("journal_arrows", prepare = { _ ->
            runBlocking { Prefs(rule.activity.applicationContext).setColoredCallArrows(true) }
            settle(1200)
        }, cleanup = {
            runBlocking { Prefs(rule.activity.applicationContext).setColoredCallArrows(false) }
            settle(600)
        }, content = journalContent),
        // Меню «⋮» шапки журнала — выпадающее окно (popup), а не шторка
        Shot("journal_menu_top", popup = true, act = { _ ->
            rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.topbar_list_actions))[0].performClick()
            settle(500)
        }, content = journalContent),
        // Окно псевдозвонка из «Меню» журнала и выбор времени из него
        Shot("journal_fakecall", dialog = true, act = { _ ->
            rule.onAllNodesWithText(rule.activity.getString(R.string.bar_menu))[0].performClick()
            settle(400)
            rule.onAllNodesWithText(rule.activity.getString(R.string.fake_call_title))[0].performClick()
            settle(500)
        }, content = journalContent),
        Shot("journal_fakecall_time", dialog = true, act = { _ ->
            rule.onAllNodesWithText(rule.activity.getString(R.string.bar_menu))[0].performClick()
            settle(400)
            rule.onAllNodesWithText(rule.activity.getString(R.string.fake_call_title))[0].performClick()
            settle(500)
            rule.onAllNodesWithText(rule.activity.getString(R.string.fake_call_pick_time))[0].performClick()
            settle(500)
        }, content = journalContent),
        Shot("contacts") { vm -> ContactsScreen(vm = vm, onBack = {}) },
        Shot("card_photo") { vm -> SubscriberScreen(groupKey = "contact:2", vm = vm, onOpenBlockLists = {}, onBack = {}) },
        Shot("card_plain") { vm -> SubscriberScreen(groupKey = "contact:8", vm = vm, onOpenBlockLists = {}, onBack = {}) },
        Shot("card_unknown") { vm -> SubscriberScreen(groupKey = log[2].key, vm = vm, onOpenBlockLists = {}, onBack = {}) },
        Shot("card_hidden") { vm -> SubscriberScreen(groupKey = log[0].key, vm = vm, onOpenBlockLists = {}, onBack = {}) },
        Shot("settings_home") { _ -> SettingsHomeScreen(onBack = {}, onOpenCategory = {}, onOpenEntry = {}) },
        Shot("settings_appearance") { vm -> SettingsCategoryScreen(category = SettingsCategory.APPEARANCE, vm = vm, onBack = {}, onOpenAdvanced = {}, onAction = {}) },
        Shot("settings_calls") { vm -> SettingsCategoryScreen(category = SettingsCategory.CALLS, vm = vm, onBack = {}, onOpenAdvanced = {}, onAction = {}) },
        // 2.7.2: «Журнал вызовов» — где «Цветные стрелки вызовов» (вопрос автора: «не вижу настройки»)
        Shot("settings_log") { vm -> SettingsCategoryScreen(category = SettingsCategory.CALL_LOG, vm = vm, onBack = {}, onOpenAdvanced = {}, onAction = {}) },
        Shot("settings_contacts") { vm -> SettingsCategoryScreen(category = SettingsCategory.CONTACTS, vm = vm, onBack = {}, onOpenAdvanced = {}, onAction = {}) },
        Shot("notes") { vm -> NotesScreen(vm = vm, onOpenCard = {}) },
        Shot("reminders") { vm -> RemindersScreen(vm = vm, onOpenCard = {}) },
        Shot("spam") { vm -> SpamProtectionScreen(vm = vm, onBack = {}, onOpenLists = {}) },
        Shot("block_lists") { vm -> BlockListScreen(vm = vm, onBack = {}) },
        Shot("stats") { vm -> StatsScreen(vm = vm, onBack = {}) },
        Shot("faq") { _ -> FaqScreen(onBack = {}, isDefaultDialer = true, onOpenLink = {}) },
        Shot("pinned") { vm -> PinnedContactsScreen(vm = vm, onBack = {}, onAddFromContacts = {}) },
        Shot("archive") { vm -> CallArchiveScreen(vm, onBack = {}) },
        Shot("contact_edit") { _ -> com.ogneliss.dialer.ui.contactedit.ContactEditScreen(onClose = {}, onSaved = {}) },
        Shot("consent") { _ -> ConsentScreen(onAccept = {}, onDecline = {}, onOpenDocument = {}) },
        // 2.7.2: повторное согласие — «Политика обновлена» и что изменилось
        Shot("consent_updated") { _ ->
            ConsentScreen(
                onAccept = {}, onDecline = {}, onOpenDocument = {},
                changes = androidx.compose.ui.res.stringResource(com.ogneliss.dialer.R.string.consent_changes_v2)
            )
        },
        Shot("wizard") { _ -> PermissionWizardScreen(isDefaultDialer = false, onRequestDefaultDialer = { false }, onFinished = {}) },
        // 2.7.4: «Меню» журнала — папки, инструменты, приложение, поиск (снимок автора 16.11.2026)
        Shot("menu", dialog = true) { vm ->
            val settings = vm.settingsState.collectAsState().value
            // 2.9.3: значок «настроить» у «Инструментов» и все разделы, как в журнале
            androidx.compose.runtime.CompositionLocalProvider(com.ogneliss.dialer.ui.screens.LocalOpenMenuTools provides {}) {
            com.ogneliss.dialer.ui.screens.OverflowMenu(
                expanded = true, onDismiss = {}, settings = settings,
                onThemeToggle = {}, onSettings = {}, onFakeCall = {}, onNotes = {}, onReminders = {},
                onMessages = {}, onContacts = {},
                onHelp = {},
                header = {
                    com.ogneliss.dialer.ui.components.MenuFolderRows(
                        title = androidx.compose.ui.res.stringResource(com.ogneliss.dialer.R.string.settings_sms_folders),
                        folders = com.ogneliss.dialer.data.JournalFolder.entries.map { f ->
                            com.ogneliss.dialer.ui.components.MenuFolder(
                                com.ogneliss.dialer.ui.screens.journalFolderIcon(f),
                                androidx.compose.ui.res.stringResource(com.ogneliss.dialer.ui.screens.journalFolderLabel(f)),
                                selected = f == com.ogneliss.dialer.data.JournalFolder.ALL
                            ) {}
                        } + com.ogneliss.dialer.ui.components.MenuFolder(
                            com.ogneliss.dialer.ui.theme.OgnelissIcons.Archive,
                            androidx.compose.ui.res.stringResource(com.ogneliss.dialer.R.string.archive_title)
                        ) {},
                        onEdit = {},
                        editLabel = androidx.compose.ui.res.stringResource(com.ogneliss.dialer.R.string.sms_folders_edit)
                    )
                },
                onFilters = {}, onSearch = {}
            )
            }
        },
        // 2.8.7: «Меню» SMS — папки и «Архив» последним (автор: «в боковой панели такого пункта нет»)
        Shot("menu_sms", dialog = true) { vm ->
            val settings = vm.settingsState.collectAsState().value
            com.ogneliss.dialer.ui.screens.OverflowMenu(
                expanded = true, onDismiss = {}, settings = settings,
                onThemeToggle = {}, onSettings = {}, onFakeCall = {}, onNotes = {}, onReminders = {},
                onHelp = {},
                header = {
                    com.ogneliss.dialer.ui.conversation.SmsFolderRows(
                        folders = com.ogneliss.dialer.sms.SmsFolder.entries,
                        current = com.ogneliss.dialer.sms.SmsFolder.ALL,
                        unread = mapOf(com.ogneliss.dialer.sms.SmsFolder.UNREAD to 2),
                        onSelect = {}, onEdit = {}, onArchive = {}
                    )
                },
                onSearch = {}
            )
        },
        Shot("sheet_filters", dialog = true) { _ ->
            com.ogneliss.dialer.ui.components.FilterSheet(
                filter = com.ogneliss.dialer.data.CallLogFilter(), simCount = 2, onFilterChange = {}, matchCount = 12, onDismiss = {}
            )
        },
        Shot("choice_call_photo", dialog = true) { vm ->
            com.ogneliss.dialer.ui.screens.SettingsChoiceDialog(settingKey = "call_photo_style", vm = vm, onDismiss = {})
        },
        // 2.7.0: выбор языка и цветовой темы — тоже шторки (обзор вида 15.11.2026)
        Shot("choice_language", dialog = true) { vm ->
            com.ogneliss.dialer.ui.screens.SettingsChoiceDialog(settingKey = "language", vm = vm, onDismiss = {})
        },
        Shot("choice_color_theme", dialog = true) { vm ->
            com.ogneliss.dialer.ui.screens.SettingsChoiceDialog(settingKey = "theme_preset", vm = vm, onDismiss = {})
        }
    )

    /** 2.7.5: экраны, которых в галерее не было. */
    private val screenShots: List<Shot> = listOf(
        Shot("about") { _ -> com.ogneliss.dialer.ui.screens.AboutScreen(onBack = {}, onOpenLegal = {}) },
        Shot("backup") { vm -> com.ogneliss.dialer.ui.screens.BackupScreen(vm = vm, onBack = {}) },
        Shot("external_lookup") { vm -> com.ogneliss.dialer.ui.screens.ExternalLookupScreen(vm = vm, onBack = {}) },
        Shot("legal_privacy") { _ -> com.ogneliss.dialer.ui.screens.LegalScreen(document = com.ogneliss.dialer.ui.screens.LegalDocument.PRIVACY, onBack = {}) },
        Shot("menu_folders") { _ ->
            com.ogneliss.dialer.ui.screens.MenuFoldersScreen(
                title = androidx.compose.ui.res.stringResource(com.ogneliss.dialer.R.string.settings_sms_folders),
                folders = com.ogneliss.dialer.data.JournalFolder.entries.map {
                    Triple(it.name, com.ogneliss.dialer.ui.screens.journalFolderLabel(it), com.ogneliss.dialer.ui.screens.journalFolderIcon(it))
                },
                raw = null, onSave = {}, onBack = {}
            )
        },
        Shot("permissions") { _ -> com.ogneliss.dialer.ui.screens.PermissionsSettingsScreen(isDefaultDialer = false, onRequestDefaultDialer = { false }, onBack = {}) },
        Shot("speed_dial") { vm -> com.ogneliss.dialer.ui.screens.SpeedDialScreen(vm = vm, onBack = {}) },
        // 2.9.9: «Колонка справа» — порядок и показ (первый — у кнопки действия)
        Shot("rail_items") { _ ->
            com.ogneliss.dialer.ui.screens.MenuFoldersScreen(
                title = androidx.compose.ui.res.stringResource(R.string.rail_items_title),
                folders = com.ogneliss.dialer.ui.screens.RailItem.items(),
                raw = "JOURNAL,MESSAGES,CONTACTS,-NOTES,REMINDERS,-OWN_CONTACT,FAKE_CALL",
                onSave = {},
                onBack = {},
                fixed = com.ogneliss.dialer.ui.screens.RailItem.FIXED,
                note = { key -> if (key == "JOURNAL") androidx.compose.ui.res.stringResource(R.string.rail_items_near_action) else null }
            )
        },
        Shot("swipe_tuning") { vm -> com.ogneliss.dialer.ui.screens.SwipeTuningScreen(vm = vm, onBack = {}) },
        Shot("sms_folders") { _ -> com.ogneliss.dialer.ui.conversation.SmsFoldersScreen(onBack = {}) },
        // 2.9.2: конструктор нижней панели (порядок «только звонки», меняли — есть «Как по умолчанию»)
        Shot("panel_slots") { _ ->
            com.ogneliss.dialer.ui.settings.PanelSlotsScreen(
                slots = com.ogneliss.dialer.settings.PanelSlots.defaults(smsDefault = false),
                customized = true, onPlace = { _, _ -> }, onReset = {}, onBack = {}
            )
        },
        // 2.9.2: журнал при порядке «только звонки»: Журнал · Контакты · [Набор] · Заметки · Меню
        Shot("journal_dialer_only") { vm ->
            androidx.compose.runtime.CompositionLocalProvider(
                com.ogneliss.dialer.ui.components.LocalPanelSlots provides com.ogneliss.dialer.settings.PanelSlots.defaults(smsDefault = false)
            ) { journalContent(vm) }
        },
        // 2.9.3: разделы справа (проба) — журнал, контакты, клавиатура набора
        Shot("side_journal") { vm -> SideMode(com.ogneliss.dialer.ui.components.MainSection.JOURNAL) { journalContent(vm) } },
        Shot("side_contacts") { vm -> SideMode(com.ogneliss.dialer.ui.components.MainSection.CONTACTS) { ContactsScreen(vm = vm, onBack = {}) } },
        Shot("side_dialpad", act = { _ ->
            rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.dialpad_show))[0].performClick()
            settle(600)
        }) { vm -> SideMode(com.ogneliss.dialer.ui.components.MainSection.JOURNAL) { journalContent(vm) } },
        // 2.9.5: экран входящего — живой фон «как было» и облик «Пламя и лиса»
        Shot("call_theme") { _ ->
            com.ogneliss.dialer.call.FakeCallScreen(
                name = "Анна Смирнова", number = "+79001110001", photoUri = null,
                ambientBackground = true, bareControls = true, onAnswer = {}, onHangUp = {}
            )
        },
        Shot("call_flame") { _ ->
            androidx.compose.runtime.CompositionLocalProvider(
                com.ogneliss.dialer.call.LocalAmbientStyle provides com.ogneliss.dialer.call.AmbientStyle.FLAME
            ) {
                com.ogneliss.dialer.call.FakeCallScreen(
                    name = "Анна Смирнова", number = "+79001110001", photoUri = null,
                    ambientBackground = true, bareControls = true, onAnswer = {}, onHangUp = {}
                )
            }
        },
        // 2.9.10: облики без картинок — «Абстракция», «Закат», «Ночь»
        Shot("call_abstract") { _ -> AmbientShot(com.ogneliss.dialer.call.AmbientStyle.ABSTRACT) },
        Shot("call_sunset") { _ -> AmbientShot(com.ogneliss.dialer.call.AmbientStyle.SUNSET) },
        Shot("call_night") { _ -> AmbientShot(com.ogneliss.dialer.call.AmbientStyle.NIGHT) },
        // 2.9.7: «Подписи состояния вызова» выключены — нет «Входящий вызов», место под плашкой остаётся
        Shot("call_no_labels") { _ ->
            androidx.compose.runtime.CompositionLocalProvider(
                com.ogneliss.dialer.call.LocalCallStatusLabels provides false
            ) {
                com.ogneliss.dialer.call.FakeCallScreen(
                    name = "Анна Смирнова", number = "+79001110001", photoUri = null,
                    ambientBackground = true, bareControls = true, onAnswer = {}, onHangUp = {}
                )
            }
        },
        // 2.9.5: *#06# — окно IMEI (две SIM) и «IMEI недоступен» (приложение не SMS по умолчанию)
        Shot("dialog_imei", dialog = true) { _ ->
            com.ogneliss.dialer.ui.components.ImeiDialog(listOf("356938035643809", "356938035643817"), onDismiss = {})
        },
        Shot("dialog_imei_unavailable", dialog = true) { _ ->
            com.ogneliss.dialer.ui.components.ImeiDialog(emptyList(), onDismiss = {})
        },
        // 2.9.6: меню в режиме колонки — сверху, с «Действиями» из «⋮» шапки
        Shot("side_menu", dialog = true) { vm ->
            val settings = vm.settingsState.collectAsState().value
            val state = androidx.compose.runtime.remember { com.ogneliss.dialer.ui.components.SideRailState() }
            val select = androidx.compose.ui.res.stringResource(R.string.selection_start)
            val clear = androidx.compose.ui.res.stringResource(R.string.log_clear_all)
            androidx.compose.runtime.SideEffect {
                state.publishList(
                    state,
                    listOf(
                        com.ogneliss.dialer.ui.components.TopBarMenuItem(com.ogneliss.dialer.ui.theme.OgnelissIcons.Checklist, select) {},
                        com.ogneliss.dialer.ui.components.TopBarMenuItem(com.ogneliss.dialer.ui.theme.OgnelissIcons.DeleteSweep, clear, destructive = true) {}
                    )
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(com.ogneliss.dialer.ui.components.LocalSideRail provides state) {
                com.ogneliss.dialer.ui.screens.OverflowMenu(
                    expanded = true, onDismiss = {}, settings = settings,
                    onThemeToggle = {}, onSettings = {}, onFakeCall = {}, onNotes = {}, onReminders = {},
                    onMessages = {}, onContacts = {}, onHelp = {}, onSearch = {}, onFilters = {}
                )
            }
        },
        // 2.9.3: «Инструменты меню» — порядок и показ, как папки
        Shot("menu_tools") { _ ->
            com.ogneliss.dialer.ui.screens.MenuFoldersScreen(
                title = androidx.compose.ui.res.stringResource(R.string.menu_tools_title),
                folders = com.ogneliss.dialer.ui.screens.MenuTool.items(),
                raw = "OWN_CONTACT,FAKE_CALL,MESSAGES,CONTACTS,NOTES,-REMINDERS",
                onSave = {}, onBack = {},
                fixed = com.ogneliss.dialer.ui.screens.MenuTool.NO_FIXED,
                note = { key -> if (key == "NOTES" || key == "REMINDERS") androidx.compose.ui.res.stringResource(R.string.menu_tool_off_panel) else null }
            )
        },
        // 2.9.2: «Меню» при порядке «только звонки» — «SMS» и «Напоминания» строками «Инструментов»
        Shot("menu_dialer_only", dialog = true) { vm ->
            val settings = vm.settingsState.collectAsState().value
            androidx.compose.runtime.CompositionLocalProvider(
                com.ogneliss.dialer.ui.components.LocalPanelSlots provides com.ogneliss.dialer.settings.PanelSlots.defaults(smsDefault = false)
            ) {
                com.ogneliss.dialer.ui.screens.OverflowMenu(
                    expanded = true, onDismiss = {}, settings = settings,
                    onThemeToggle = {}, onSettings = {}, onFakeCall = {}, onNotes = {}, onReminders = {},
                    onMessages = {}, onContacts = {}, onHelp = {}, onSearch = {}
                )
            }
        }
    )

    /**
     * 2.7.5: окна, шторки и карточки по удержанию. Окна с полем ввода в фокусе
     * (правило блокировки, названия SIM, быстрый набор, свой номер) не сняты:
     * мигающий курсор не даёт Compose успокоиться, снимок не делается.
     */
    private val windowShots: List<Shot> = listOf(
        Shot("dialog_note", dialog = true) { _ ->
            com.ogneliss.dialer.ui.screens.NoteEditorDialog(
                initialNote = "Сантехник, после 18:00. Обещал прийти в четверг, взять с собой прокладки.",
                initialTags = setOf("ремонт"), onSave = { _, _ -> }, onDismiss = {}, displayName = "Иван Петров"
            )
        },
        Shot("dialog_swipe_actions", dialog = true) { vm ->
            com.ogneliss.dialer.ui.screens.SwipeActionsDialog(
                vm = vm, settingKey = "swipe_right", primary = SwipeAction.CALL,
                extra = listOf(SwipeAction.SMS, SwipeAction.WHATSAPP), onDismiss = {}
            )
        },
        Shot("dialog_reminder_options", dialog = true) { _ ->
            com.ogneliss.dialer.ui.components.ReminderDialog(displayName = "Анна Смирнова", onPick = {}, onDismiss = {})
        },
        Shot("dialog_reminder_time", dialog = true) { _ ->
            com.ogneliss.dialer.ui.components.ReminderTimeDialog(
                displayName = "Анна Смирнова", initialMillis = now + 3_600_000L, onSave = {}, onDismiss = {}
            )
        },
        Shot("dialog_post_call", dialog = true) { _ ->
            com.ogneliss.dialer.ui.screens.PostCallSheet(
                number = "+79180827805", displayName = null, durationSec = 75, isKnownContact = false,
                onSaveNote = { _, _ -> }, onAddContact = {}, onRemind = {}, onBlock = {}, onDismiss = {}
            )
        },
        Shot("cards_record", dialog = true) { vm ->
            com.ogneliss.dialer.ui.screens.RecordActionCards(
                group = log[1], vm = vm, anchorY = null, origin = com.ogneliss.dialer.ui.screens.CardsOrigin.LOG,
                onOpenCard = {}, onOpenBlockLists = {}, onDismiss = {}
            )
        },
        Shot("cards_record_unknown", dialog = true) { vm ->
            com.ogneliss.dialer.ui.screens.RecordActionCards(
                group = log[2], vm = vm, anchorY = null, origin = com.ogneliss.dialer.ui.screens.CardsOrigin.LOG,
                onOpenCard = {}, onOpenBlockLists = {}, onDismiss = {}
            )
        },
        Shot("cards_number", dialog = true) { vm ->
            com.ogneliss.dialer.ui.screens.NumberActionCards(
                target = com.ogneliss.dialer.ui.screens.NumberCardsTarget("+79001110001", "Мобильный"),
                vm = vm, anchorY = null, onDismiss = {}
            )
        },
        Shot("cards_sim_call", dialog = true) { _ ->
            com.ogneliss.dialer.ui.components.SimChoiceOverlay(
                request = DialerViewModel.SimRequest(
                    displayName = "Анна Смирнова", number = "+79001110001", sims = sims, action = DialerViewModel.SimAction.CALL,
                    contactName = "Анна Смирнова", numberOperator = "Beeline", onChosen = {}
                ),
                anchorY = null, onDismiss = {}
            )
        },
        Shot("cards_sim_sms", dialog = true) { _ ->
            com.ogneliss.dialer.ui.components.SimChoiceOverlay(
                request = DialerViewModel.SimRequest(
                    displayName = "Анна Смирнова", number = "+79001110001", sims = sims, action = DialerViewModel.SimAction.SMS,
                    contactName = "Анна Смирнова", onChosen = {}
                ),
                anchorY = null, onDismiss = {}
            )
        }
    )

    /** 2.7.5: SMS — роль включается до сборки экрана, данные подставляются после неё. */
    private val smsShots: List<Shot> = listOf(
        Shot("sms_list", prepare = { grantSmsRole() }, act = { _ ->
            mutate<com.ogneliss.dialer.ui.conversation.MessagesViewModel.UiState>(messagesVm()) { it.copy(loading = false, isDefaultSms = true, threads = threads, problem = null, autostartHint = false) }
            settle(500)
        }) { vm -> messagesContent(vm) },
        Shot("side_sms", prepare = { grantSmsRole() }, act = { _ ->
            mutate<com.ogneliss.dialer.ui.conversation.MessagesViewModel.UiState>(messagesVm()) { it.copy(loading = false, isDefaultSms = true, threads = threads, problem = null, autostartHint = false) }
            settle(500)
        }) { vm -> SideMode(com.ogneliss.dialer.ui.components.MainSection.MESSAGES) { messagesContent(vm) } },
        Shot("sms_list_empty", prepare = { grantSmsRole() }, act = { _ ->
            mutate<com.ogneliss.dialer.ui.conversation.MessagesViewModel.UiState>(messagesVm()) { it.copy(loading = false, isDefaultSms = true, threads = emptyList(), problem = null, autostartHint = false) }
            settle(500)
        }) { vm -> messagesContent(vm) },
        Shot("sms_new_message", prepare = { grantSmsRole() }, act = { _ ->
            mutate<com.ogneliss.dialer.ui.conversation.MessagesViewModel.UiState>(messagesVm()) { it.copy(loading = false, isDefaultSms = true, threads = threads, problem = null, autostartHint = false) }
            settle(500)
            rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.messages_new))[0].performClick()
            settle(600)
        }) { vm -> messagesContent(vm) },
        // 2.9.0: длинная переписка — сверху тень: выше есть старые сообщения
        Shot("sms_chat_long", prepare = { grantSmsRole() }, act = { _ ->
            val older = (1..4).flatMap { round ->
                chat.map { m -> m.copy(id = m.id + round * 100L, date = m.date - round * 86_400_000L) }
            }
            mutate<com.ogneliss.dialer.ui.conversation.ConversationViewModel.UiState>(conversationVm()) {
                it.copy(
                    title = "Анна Смирнова", subtitle = "+7 900 111-00-01", dialable = true,
                    messages = (older + chat).sortedBy { m -> m.date },
                    loading = false, isDefaultSms = true, sims = sims
                )
            }
            settle(600)
        }) { _ -> com.ogneliss.dialer.ui.conversation.ConversationScreen(onBack = {}, onOpenCard = {}, onCall = {}) },
        Shot("sms_chat", prepare = { grantSmsRole() }, act = { _ ->
            mutate<com.ogneliss.dialer.ui.conversation.ConversationViewModel.UiState>(conversationVm()) {
                it.copy(
                    title = "Анна Смирнова", subtitle = "+7 900 111-00-01", dialable = true, messages = chat,
                    loading = false, isDefaultSms = true, sims = sims
                )
            }
            settle(600)
        }) { _ -> com.ogneliss.dialer.ui.conversation.ConversationScreen(onBack = {}, onOpenCard = {}, onCall = {}) }
    )

    private fun messagesVm() = ViewModelProvider(rule.activity)[com.ogneliss.dialer.ui.conversation.MessagesViewModel::class.java]
    private fun conversationVm() = ViewModelProvider(rule.activity)[com.ogneliss.dialer.ui.conversation.ConversationViewModel::class.java]

    @Composable
    private fun messagesContent(vm: DialerViewModel) {
        com.ogneliss.dialer.ui.conversation.MessagesScreen(
            vm = vm, onOpenConversation = {}, onOpenSection = {}, onOpenDialpad = {}, onOpenSettings = {}, onOpenNotes = {},
            onOpenReminders = {}, onOpenHelp = {}, onFakeCall = {}, onOpenCard = {}, onOpenBlockLists = {}
        )
    }

    /** Разделы настроек, которых не было выше: по одному снимку на раздел (2.7.5). */
    private val settingsShots: List<Shot> = SettingsCategory.entries
        .filter { it !in setOf(SettingsCategory.APPEARANCE, SettingsCategory.CALLS, SettingsCategory.CALL_LOG, SettingsCategory.CONTACTS) }
        .map { c -> Shot("settings_" + c.name.lowercase()) { vm -> SettingsCategoryScreen(category = c, vm = vm, onBack = {}, onOpenAdvanced = {}, onAction = {}) } }

    private val expShots: List<Shot> = listOf(
        Shot("xa", dialog = true) { _ ->
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                androidx.compose.material3.Surface { androidx.compose.material3.TextField(value = "a", onValueChange = {}, singleLine = true) }
            }
        },
        Shot("xb", dialog = true) { _ ->
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                androidx.compose.material3.Surface { androidx.compose.material3.Text("b") }
            }
        },
        Shot("xc") { _ -> androidx.compose.material3.TextField(value = "a", onValueChange = {}, singleLine = true) },
        Shot("xd", dialog = true) { _ ->
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                androidx.compose.material3.Surface { androidx.compose.material3.TextField(value = "a", onValueChange = {}, singleLine = false) }
            }
        }
    )
    private val shots: List<Shot> by lazy { if (System.getenv("GALLERY_EXP") != null) expShots else baseShots + settingsShots + screenShots + windowShots + smsShots }

    private fun render(suffix: String, mode: ThemeMode, uiScale: UiScale = UiScale.DEFAULT, fontScale: Float = 1f, only: Set<String>? = null) {
        val vm = viewModel()
        val failed = mutableListOf<String>()
        // GALLERY_ONLY=journal,menu — только эти снимки (быстрая проверка одной правки)
        val envOnly = System.getenv("GALLERY_ONLY")?.split(',')?.map { it.trim() }?.toSet()
        shots.filter { (only == null || it.name in only) && (envOnly == null || it.name in envOnly) }.forEach { shot ->
            runCatching {
                applyBase(vm)
                shot.prepare(vm)
                rule.activity.runOnUiThread {
                    rule.activity.setContent {
                        OgnelissDialerTheme(themeMode = mode, uiScale = uiScale, fontScale = fontScale) { shot.content(vm) }
                    }
                }
                settle()
                shot.act(vm)
                val node = when {
                    shot.dialog -> rule.onNode(isDialog())
                    shot.popup -> rule.onNode(isPopup())
                    else -> rule.onRoot()
                }
                val bitmap = node.captureToImage().asAndroidBitmap()
                /*
                 * Картинка освобождается сразу после записи (24.11.2026, автор: на Windows
                 * процесс тестов падал с кодом 2). В режиме NATIVE снимок — память
                 * вне кучи Java: сборщик мусора её почти не видит, и за сотни снимков
                 * одного процесса она копилась до падения.
                 */
                try {
                    File(out, "${shot.name}_$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    bitmap.recycle()
                }
            }.onFailure { failed += "${shot.name}: ${it.javaClass.simpleName}: ${it.message?.lineSequence()?.firstOrNull()}" }
            runCatching {
                shot.cleanup()
                // Закрыть окно шторки или диалога перед следующим экраном
                rule.activity.runOnUiThread { rule.activity.setContent { } }
                settle(200)
            }
        }
        assertTrue("Экраны не собрались ($suffix):\n" + failed.joinToString("\n"), failed.isEmpty())
    }

    @Test
    fun `светлая тема — все экраны собираются`() = render("light", ThemeMode.LIGHT)

    @Test
    fun `тёмная тема — все экраны собираются`() = render("dark", ThemeMode.DARK)

    @Test
    fun `шрифт 160 процентов и масштаб 110 — главные экраны собираются`() = render(
        "large", ThemeMode.LIGHT, UiScale.S110, 1.6f,
        only = setOf(
            "journal", "contacts", "card_plain", "settings_home", "settings_calls", "notes", "contact_edit", "sheet_filters", "menu",
            // 2.7.5: журнал с записями, переписка, список SMS, окно с полями, «О приложении»
            "sms_chat", "sms_list", "dialog_note", "about",
            // 2.7.7: окно псевдозвонка — кнопки переносятся, «Закрыть» не столбиком
            "journal_fakecall", "journal_dialpad"
        )
    )
}
