package com.ogneliss.dialer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Порядок и показ папок журнала и контактов (2.5.0) — тем же форматом, что SMS. */
class FolderOrderTest {

    private val all = listOf("ALL", "MISSED", "INCOMING", "OUTGOING")

    @Test
    fun `пусто — все папки по умолчанию и видимы`() {
        assertEquals(all.map { it to true }, FolderOrder.parse(null, all, "ALL"))
        assertEquals(all, FolderOrder.visible("", all, "ALL"))
    }

    @Test
    fun `порядок и скрытые — из строки, новые — в конец видимыми`() {
        val parsed = FolderOrder.parse("MISSED,ALL,-OUTGOING", all, "ALL")
        assertEquals(listOf("MISSED" to true, "ALL" to true, "OUTGOING" to false, "INCOMING" to true), parsed)
        assertEquals(listOf("MISSED", "ALL", "INCOMING"), FolderOrder.visible("MISSED,ALL,-OUTGOING", all, "ALL"))
    }

    @Test
    fun `«Все» не скрыть, незнакомое и повторы пропускаются`() {
        assertEquals("ALL" to true, FolderOrder.parse("-ALL,GONE,MISSED,MISSED", all, "ALL").first())
        assertEquals(listOf("ALL", "MISSED", "INCOMING", "OUTGOING"), FolderOrder.visible("-ALL,GONE,MISSED,MISSED", all, "ALL"))
    }

    @Test
    fun `туда и обратно — та же строка`() {
        val list = FolderOrder.parse("INCOMING,-MISSED,ALL,OUTGOING", all, "ALL")
        assertEquals("INCOMING,-MISSED,ALL,OUTGOING", FolderOrder.encode(list, "ALL"))
    }

    @Test
    fun `сдвиг — внутри списка, за край — без изменений`() {
        val list = all.map { it to true }
        assertEquals(listOf("MISSED", "ALL", "INCOMING", "OUTGOING"), FolderOrder.move(list, 1, -1).map { it.first })
        assertEquals(list, FolderOrder.move(list, 0, -1))
        assertEquals(list, FolderOrder.move(list, 3, 1))
    }
}
