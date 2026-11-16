package com.ogneliss.dialer.gallery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.data.CallLogEntry
import com.ogneliss.dialer.data.CallLogGroup
import com.ogneliss.dialer.data.CallType
import com.ogneliss.dialer.data.Contact
import com.ogneliss.dialer.data.NumberNote
import com.ogneliss.dialer.data.NumberPresentation
import com.ogneliss.dialer.data.PhoneNumber
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
 * Чего здесь нет: журнала с записями и списка SMS (строки собирает
 * ViewModel из системных хранилищ, которых в тесте нет), экрана вызова
 * (ему нужен Telecom).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h873dp-xxhdpi")
class ScreenGalleryTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

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

    private fun group(id: Long, number: String, name: String?, type: CallType, minutesAgo: Long, count: Int = 1, contactId: Long? = null, presentation: NumberPresentation = NumberPresentation.ALLOWED): CallLogGroup {
        val date = now - minutesAgo * 60_000
        val entry = CallLogEntry(id, number, number, name, null, type, date, 75, "sim1", contactId, presentation)
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
            rejectedCount = 0, totalDurationSec = 75L * count,
            numbers = if (number.isBlank()) emptyList() else listOf(number),
            subscriptionLabel = "Beeline", subscriptionId = "sim1",
            presentation = presentation, entries = listOf(entry)
        )
    }

    private val log by lazy {
        listOf(
            group(1, "", null, CallType.MISSED, 12, presentation = NumberPresentation.RESTRICTED),
            group(2, "+79001110001", "Анна Смирнова", CallType.INCOMING, 55, 3, 1),
            group(3, "+79180827805", null, CallType.OUTGOING, 60 * 5),
            group(4, "+79001110002", "Борис Кузнецов", CallType.MISSED, 60 * 26, 2, 2),
            group(5, "+78005553535", null, CallType.INCOMING, 60 * 28),
            group(6, "+79001110008", "Иван Петров", CallType.OUTGOING, 60 * 24 * 4, 1, 8)
        )
    }

    /** Часы вручную: разбор в шапке файла. */
    private fun settle(ms: Long = 800) {
        rule.mainClock.autoAdvance = false
        var t = 0L
        while (t < ms) {
            rule.mainClock.advanceTimeBy(50)
            ShadowLooper.idleMainLooper()
            t += 50
        }
    }

    private fun viewModel(): DialerViewModel {
        rule.mainClock.autoAdvance = false
        val vm = ViewModelProvider(rule.activity)[DialerViewModel::class.java]
        settle(300)
        // 2.7.0: шторки жестов при первом запуске больше нет — закрывать нечего
        val field = DialerViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val state = field.get(vm) as MutableStateFlow<UiState>
        state.value = state.value.copy(
            contacts = contacts, callLog = log, filteredLog = log, loading = false,
            hasContactsPermission = true, hasCallLogPermission = true,
            notes = mapOf("+79180827805" to NumberNote("+79180827805", "Сантехник, после 18:00", listOf("ремонт"), updatedAt = now))
        )
        settle(300)
        return vm
    }

    private class Shot(val name: String, val dialog: Boolean = false, val content: @Composable (DialerViewModel) -> Unit)

    private val shots: List<Shot> = listOf(
        Shot("journal") { vm ->
            MainDialerScreen(
                vm = vm, prefilledNumber = null, onOpenContacts = {}, onOpenSettings = {}, onOpenFakeCallPicker = {},
                onOpenNotes = {}, onOpenReminders = {}, onOpenSubscriber = {}, onOpenBlockLists = {}
            )
        },
        Shot("contacts") { vm -> ContactsScreen(vm = vm, onBack = {}) },
        Shot("card_photo") { vm -> SubscriberScreen(groupKey = "contact:2", vm = vm, onOpenBlockLists = {}, onBack = {}) },
        Shot("card_plain") { vm -> SubscriberScreen(groupKey = "contact:8", vm = vm, onOpenBlockLists = {}, onBack = {}) },
        Shot("card_unknown") { vm -> SubscriberScreen(groupKey = log[2].key, vm = vm, onOpenBlockLists = {}, onBack = {}) },
        Shot("card_hidden") { vm -> SubscriberScreen(groupKey = log[0].key, vm = vm, onOpenBlockLists = {}, onBack = {}) },
        Shot("settings_home") { _ -> SettingsHomeScreen(onBack = {}, onOpenCategory = {}, onOpenEntry = {}) },
        Shot("settings_appearance") { vm -> SettingsCategoryScreen(category = SettingsCategory.APPEARANCE, vm = vm, onBack = {}, onAction = {}) },
        Shot("settings_calls") { vm -> SettingsCategoryScreen(category = SettingsCategory.CALLS, vm = vm, onBack = {}, onAction = {}) },
        // 2.7.2: «Журнал вызовов» — где «Цветные стрелки вызовов» (вопрос автора: «не вижу настройки»)
        Shot("settings_log") { vm -> SettingsCategoryScreen(category = SettingsCategory.CALL_LOG, vm = vm, onBack = {}, onAction = {}) },
        Shot("settings_quick") { vm -> SettingsCategoryScreen(category = SettingsCategory.QUICK_ACCESS, vm = vm, onBack = {}, onAction = {}) },
        Shot("notes") { vm -> NotesScreen(vm = vm, onOpenCard = {}, onBack = {}) },
        Shot("reminders") { vm -> RemindersScreen(vm = vm, onOpenCard = {}, onBack = {}) },
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
            com.ogneliss.dialer.ui.screens.OverflowMenu(
                expanded = true, onDismiss = {}, settings = settings,
                onThemeToggle = {}, onSettings = {}, onFakeCall = {}, onNotes = {}, onReminders = {},
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

    private fun render(suffix: String, mode: ThemeMode, uiScale: UiScale = UiScale.DEFAULT, fontScale: Float = 1f, only: Set<String>? = null) {
        val vm = viewModel()
        val failed = mutableListOf<String>()
        shots.filter { only == null || it.name in only }.forEach { shot ->
            runCatching {
                rule.activity.runOnUiThread {
                    rule.activity.setContent {
                        OgnelissDialerTheme(themeMode = mode, uiScale = uiScale, fontScale = fontScale) { shot.content(vm) }
                    }
                }
                settle()
                val node = if (shot.dialog) rule.onNode(isDialog()) else rule.onRoot()
                val bitmap = node.captureToImage().asAndroidBitmap()
                File(out, "${shot.name}_$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                // Закрыть окно шторки или диалога перед следующим экраном
                rule.activity.runOnUiThread { rule.activity.setContent { } }
                settle(200)
            }.onFailure { failed += "${shot.name}: ${it.javaClass.simpleName}: ${it.message?.lineSequence()?.firstOrNull()}" }
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
        only = setOf("journal", "contacts", "card_plain", "settings_home", "settings_calls", "notes", "contact_edit", "sheet_filters", "menu")
    )
}
