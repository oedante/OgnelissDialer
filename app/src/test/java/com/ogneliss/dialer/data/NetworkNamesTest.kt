package com.ogneliss.dialer.data

import com.ogneliss.dialer.data.db.NetworkNameDao
import com.ogneliss.dialer.data.db.NetworkNameEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Имена от оператора (1.8.0): что считается именем и как оно хранится.
 * Названия организаций — вымышленные.
 */
class NetworkNamesTest {

    private class FakeDao : NetworkNameDao {
        val rows = linkedMapOf<String, NetworkNameEntity>()
        override suspend fun upsert(entry: NetworkNameEntity) { rows[entry.e164] = entry }
        override suspend fun get(e164: String): NetworkNameEntity? = rows[e164]
        override suspend fun recent(limit: Int): List<NetworkNameEntity> =
            rows.values.sortedByDescending { it.seenAt }.take(limit)
        override suspend fun deleteOlderThan(before: Long) { rows.values.removeAll { it.seenAt < before } }
    }

    @Test
    fun названиеОрганизацииПринимается() {
        assertEquals("Банк Пример", NetworkNames.clean("  Банк   Пример "))
        assertEquals("Поликлиника № 3", NetworkNames.clean("Поликлиника № 3"))
        assertEquals("Доставка 24", NetworkNames.clean("Доставка 24"))
    }

    @Test
    fun заглушкиИНомераОтбрасываются() {
        assertNull(NetworkNames.clean(null))
        assertNull(NetworkNames.clean("   "))
        assertNull(NetworkNames.clean("UNKNOWN"))
        assertNull(NetworkNames.clean("Неизвестный номер"))
        assertNull(NetworkNames.clean("+7 (900) 000-00-00"))
        assertNull(NetworkNames.clean("89000000000"))
    }

    @Test
    fun длинноеОбрезается() {
        val long = "Очень длинное название организации ".repeat(5)
        val cleaned = NetworkNames.clean(long)!!
        assertTrue(cleaned.length <= NetworkNames.MAX_LENGTH)
    }

    @Test
    fun запоминаетсяПоследнееИСтароеЗабывается() = runTest {
        val dao = FakeDao()
        val names = NetworkNames(dao)
        names.remember("+79000000001", "Банк Пример", now = 1_000L)
        names.remember("+79000000001", "Банк Пример Онлайн", now = 2_000L)
        names.remember("+79000000002", "unknown", now = 2_000L)
        assertEquals("Банк Пример Онлайн", names.get("+79000000001"))
        assertNull(names.get("+79000000002"))
        assertEquals(mapOf("+79000000001" to "Банк Пример Онлайн"), names.snapshot())

        // Год и один день спустя — забыто
        names.purgeOlderThan(days = 365, now = 2_000L + 366L * 24 * 60 * 60 * 1000)
        assertNull(names.get("+79000000001"))
    }
}
