package com.ogneliss.dialer.data

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.telecom.TelecomManager
import com.ogneliss.dialer.call.CallActions
import com.ogneliss.dialer.sms.MessageStore
import com.ogneliss.dialer.sms.SmsRole
import com.ogneliss.dialer.util.DefaultDialer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * РОЛИ И РАЗРЕШЕНИЯ (2.9.13, техдолг, этап 1.2).
 *
 * Для звонилки потеря роли — не косметика: без роли SMS приложение не должно
 * читать переписку, без разрешений — журнал и контакты, а звонок должен уходить
 * и без SIM-карт в списке. Здесь — договор слоя данных: при запрете базы
 * вообще не спрашиваются (не «спросили и поймали SecurityException» — так
 * в журнал на каждое обновление падал бы стек, а по экрану не понять, что
 * доступа нет).
 *
 * Базы — подставные (CountingProvider): считают каждое обращение. На настоящем
 * телефоне без роли система сама отказала бы; здесь важно, что мы не спросили.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RolesAndPermissionsTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private val roles get() = shadowOf(app.getSystemService(RoleManager::class.java))

    @Before
    fun setUp() {
        CountingProvider.calls.clear()
        AUTHORITIES.forEach { Robolectric.buildContentProvider(CountingProvider::class.java).create(it) }
    }

    // ── SMS ─────────────────────────────────────────────────────────────────

    @Test
    fun `без роли SMS переписка не читается и не удаляется — даже с разрешением`() {
        // Разрешение READ_SMS могли выдать раньше, а роль — уже у другого приложения
        shadowOf(app).grantPermissions(Manifest.permission.READ_SMS)
        assertFalse(SmsRole.canRead(app))
        assertEmptySmsStore()
        assertEquals("к базе SMS обращались без роли", emptyList<String>(), CountingProvider.calls)
    }

    @Test
    fun `роль SMS есть — читаем, роль отозвали — перестаём`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_SMS)
        roles.addHeldRole(RoleManager.ROLE_SMS)
        assertTrue(SmsRole.canRead(app))
        MessageStore.latest(app, 10)
        assertTrue("с ролью база не спрашивалась", CountingProvider.calls.isNotEmpty())

        // Человек выбрал другое приложение SMS, пока наше открыто
        roles.removeHeldRole(RoleManager.ROLE_SMS)
        CountingProvider.calls.clear()
        assertEmptySmsStore()
        assertEquals("после отзыва роли база спрашивалась", emptyList<String>(), CountingProvider.calls)
    }

    @Test
    fun `роль без разрешения READ_SMS — не читаем`() {
        roles.addHeldRole(RoleManager.ROLE_SMS)
        assertTrue(SmsRole.isDefault(app))
        assertFalse(SmsRole.canRead(app))
        assertTrue(MessageStore.latest(app, 10).isEmpty())
        assertTrue(CountingProvider.calls.none { it.startsWith("query") })
    }

    private fun assertEmptySmsStore() {
        assertTrue(MessageStore.latest(app, 10).isEmpty())
        assertTrue(MessageStore.search(app, "код").isEmpty())
        assertTrue(MessageStore.conversation(app, "+79001234567").isEmpty())
        assertTrue(MessageStore.thread(app, 1L).isEmpty())
        assertTrue(MessageStore.unreadInThread(app, 1L).isEmpty())
        assertTrue(MessageStore.withNumbers(app, listOf("+79001234567")).isEmpty())
        assertTrue(MessageStore.threadSummaries(app).isEmpty())
        assertNull(MessageStore.unreadCounts(app))
        assertNull(MessageStore.threadIdOf(app, "+79001234567"))
        assertEquals(0, MessageStore.delete(app, listOf("sms:1")))
        assertFalse(MessageStore.deleteThread(app, 1L))
        MessageStore.markRead(app, 1L)
    }

    // ── Журнал и контакты ───────────────────────────────────────────────────

    @Test
    fun `без разрешения журнала журнал не читается и не чистится`() = runBlocking {
        val repo = CallLogRepository(app, SimRepository(app, Dispatchers.Unconfined), Dispatchers.Unconfined, Dispatchers.Unconfined)
        assertTrue(repo.loadEntries(50, 0).isEmpty())
        assertTrue(repo.totalsByNumber().isEmpty())
        assertTrue(repo.historyFor("+79001234567", 20).isEmpty())
        assertFalse(repo.clearAll())
        assertEquals("к журналу обращались без разрешения", emptyList<String>(), CountingProvider.calls)
    }

    @Test
    fun `без разрешения контактов книга не читается`() = runBlocking {
        val repo = ContactsRepository(app, Dispatchers.Unconfined)
        assertTrue(repo.loadAll().isEmpty())
        assertNull(repo.loadProfile())
        assertNull(repo.lookupByNumber("+79001234567"))
        assertNull(repo.photoUriForNumber("+79001234567"))
        assertNull(repo.numberTypeLabel("+79001234567"))
        assertFalse(repo.isKnownNumber("+79001234567"))
        assertEquals("к контактам обращались без разрешения", emptyList<String>(), CountingProvider.calls)
    }

    // ── Роль телефона ───────────────────────────────────────────────────────

    @Test
    fun `без роли телефона системный список блокировки не правим`() {
        val telecom = shadowOf(app.getSystemService(TelecomManager::class.java))
        telecom.setDefaultDialerPackage("com.android.dialer")
        assertFalse(DefaultDialer.isDefault(app))
        assertFalse(SystemBlockRepository(app).canModify())
        // Роль вернули — признак меняется сразу, без перезапуска
        telecom.setDefaultDialerPackage(app.packageName)
        assertTrue(DefaultDialer.isDefault(app))
    }

    // ── SIM ─────────────────────────────────────────────────────────────────

    @Test
    fun `нет SIM — звонок уходит без карты, правило не мешает`() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        val sims = SimRepository(app, Dispatchers.Unconfined)
        assertTrue(sims.slotsAsync().isEmpty())
        // Правило «Карты по номеру» указывает на SIM 2, а карт нет
        CallActions(app, sims) { 1 }.placeCall("+7 495 123-45-67")
        val call = shadowOf(app.getSystemService(TelecomManager::class.java)).allOutgoingCalls.single()
        assertFalse(call.extras?.containsKey(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE) == true)
    }

    @Test
    fun `без разрешения на состояние телефона список SIM пуст, звонок уходит`() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        val sims = SimRepository(app, Dispatchers.Unconfined)
        assertTrue(sims.slotsAsync().isEmpty())
        assertTrue(sims.phoneAccounts().isEmpty())
        CallActions(app, sims).placeCall("+7 495 123-45-67")
        assertEquals(1, shadowOf(app.getSystemService(TelecomManager::class.java)).allOutgoingCalls.size)
    }

    /**
     * Подставная база: на каждое обращение — строка «действие authority», ответ пустой.
     * Общий счётчик: одна и та же база стоит на нескольких адресах.
     */
    class CountingProvider : ContentProvider() {
        companion object {
            val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        }

        override fun onCreate() = true
        override fun getType(uri: Uri): String? = null

        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?
        ): Cursor {
            calls += "query ${uri.authority}"
            return MatrixCursor(projection ?: arrayOf("_id"))
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            calls += "insert ${uri.authority}"
            return null
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            calls += "delete ${uri.authority}"
            return 0
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            calls += "update ${uri.authority}"
            return 0
        }
    }

    private companion object {
        val AUTHORITIES = listOf("sms", "mms", "mms-sms", "call_log", "com.android.contacts")
    }
}
