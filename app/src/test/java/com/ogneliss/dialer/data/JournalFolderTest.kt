package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Папки» журнала (2.2.2): готовые наборы фильтра и обратное сопоставление. */
class JournalFolderTest {

    @Test
    fun каждаяПапкаУзнаётсяПоСвоемуФильтру() {
        JournalFolder.entries.forEach { folder ->
            assertEquals(folder, JournalFolder.of(folder.filter()))
        }
    }

    @Test
    fun пустойФильтр_всеВызовы() {
        assertEquals(JournalFolder.ALL, JournalFolder.of(CallLogFilter()))
    }

    @Test
    fun свойНаборУсловий_неПапка() {
        assertNull(JournalFolder.of(CallLogFilter(types = setOf(CallType.MISSED), simSlot = 1)))
        assertNull(JournalFolder.of(CallLogFilter(period = CallLogFilter.Period.WEEK)))
    }
}
