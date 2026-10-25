package com.ogneliss.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ПЛАН ЗАПИСИ СВОЕГО РЕДАКТОРА (1.7.0).
 *
 * Главное, что проверяется: пишется только изменённое, строка правится
 * там, где лежит, близнецы (один номер в двух записях) меняются вместе,
 * а новое ложится в основную запись. Имена и номера вымышленные.
 */
class ContactEditPlanTest {

    private val MOBILE = 2
    private val WORK = 3

    private fun loaded(
        draft: ContactDraft,
        nameRows: List<RowRef> = listOf(RowRef(10, 1)),
        companyRow: RowRef? = null,
        noteRow: RowRef? = null
    ) = LoadedContact(
        contactId = 100, draft = draft, nameRows = nameRows,
        companyRow = companyRow, noteRow = noteRow, primaryRawId = 1
    )

    private val anna = ContactDraft(
        given = "Анна", family = "Смирнова",
        phones = listOf(EditableValue(1, "+79001112233", MOBILE, rows = listOf(RowRef(20, 1)))),
        company = "Сервис"
    )

    @Test
    fun безИзмененийНичегоНеПишется() {
        val l = loaded(anna, companyRow = RowRef(30, 1))
        assertEquals(emptyList<EditOp>(), ContactEditPlan.forEdit(l, anna))
    }

    @Test
    fun пробелыПоКраямНеСчитаютсяИзменением() {
        val l = loaded(anna, companyRow = RowRef(30, 1))
        assertEquals(emptyList<EditOp>(), ContactEditPlan.forEdit(l, anna.copy(given = " Анна ", company = "Сервис ")))
    }

    @Test
    fun имяМеняетсяВоВсехЗаписях() {
        val l = loaded(anna, nameRows = listOf(RowRef(10, 1), RowRef(11, 2)), companyRow = RowRef(30, 1))
        val ops = ContactEditPlan.forEdit(l, anna.copy(middle = "Петровна"))
        val name = RowValue.Name("Анна", "Петровна", "Смирнова")
        assertEquals(listOf(EditOp.Update(10, name), EditOp.Update(11, name)), ops)
    }

    @Test
    fun номерПравитсяТамГдеЛежитИБлизнецыВместе() {
        val twin = EditableValue(1, "+79001112233", MOBILE, rows = listOf(RowRef(20, 1), RowRef(21, 2)))
        val before = anna.copy(phones = listOf(twin))
        val l = loaded(before, companyRow = RowRef(30, 1))
        val ops = ContactEditPlan.forEdit(l, before.copy(phones = listOf(twin.copy(type = WORK))))
        val row = RowValue.Phone("+79001112233", WORK, null)
        assertEquals(listOf(EditOp.Update(20, row), EditOp.Update(21, row)), ops)
    }

    @Test
    fun новыйНомерВОсновнуюЗаписьУдалённыйУдаляется() {
        val l = loaded(anna, companyRow = RowRef(30, 1))
        val added = EditableValue(2, "+79004445566", WORK)
        val ops = ContactEditPlan.forEdit(l, anna.copy(phones = listOf(added)))
        assertTrue(EditOp.Delete(20) in ops)
        assertTrue(EditOp.Insert(1, RowValue.Phone("+79004445566", WORK, null)) in ops)
        assertEquals(2, ops.size)
    }

    @Test
    fun очищенноеПолеУдаляетСтроку() {
        val l = loaded(anna, companyRow = RowRef(30, 1))
        val ops = ContactEditPlan.forEdit(l, anna.copy(company = "", phones = listOf(anna.phones[0].copy(value = " "))))
        assertEquals(setOf<EditOp>(EditOp.Delete(20), EditOp.Delete(30)), ops.toSet())
    }

    @Test
    fun заметкаБезСтрокиВставляется() {
        val l = loaded(anna, companyRow = RowRef(30, 1))
        val ops = ContactEditPlan.forEdit(l, anna.copy(note = "Звонить после шести"))
        assertEquals(listOf(EditOp.Insert(1, RowValue.Note("Звонить после шести"))), ops)
    }

    @Test
    fun своюПодписьТипаТолькоУCustom() {
        val l = loaded(anna, companyRow = RowRef(30, 1))
        val custom = anna.phones[0].copy(type = ContactEditPlan.TYPE_CUSTOM, label = "Дача")
        val ops = ContactEditPlan.forEdit(l, anna.copy(phones = listOf(custom)))
        assertEquals(listOf(EditOp.Update(20, RowValue.Phone("+79001112233", 0, "Дача"))), ops)
        // Подпись при обычном типе не пишется
        val plain = ContactEditPlan.forNew(ContactDraft(phones = listOf(EditableValue(5, "+79001112233", MOBILE, "Дача"))))
        assertEquals(listOf(EditOp.Insert(null, RowValue.Phone("+79001112233", MOBILE, null))), plain)
    }

    @Test
    fun новыйКонтактТолькоНепустое() {
        val ops = ContactEditPlan.forNew(
            ContactDraft(
                given = "Борис",
                phones = listOf(EditableValue(1, "+79001112233", MOBILE), EditableValue(2, "", MOBILE)),
                birthday = "--12-05"
            )
        )
        assertEquals(
            listOf(
                EditOp.Insert(null, RowValue.Name("Борис", "", "")),
                EditOp.Insert(null, RowValue.Phone("+79001112233", MOBILE, null)),
                EditOp.Insert(null, RowValue.Birthday("--12-05"))
            ),
            ops
        )
    }

    @Test
    fun безИзменяемойЗаписиПланаНет() {
        val l = LoadedContact(contactId = 1, draft = anna, primaryRawId = null)
        assertEquals(emptyList<EditOp>(), ContactEditPlan.forEdit(l, anna.copy(given = "Ольга")))
    }

    @Test
    fun пустойЧерновик() {
        assertTrue(ContactDraft().isEmpty)
        assertTrue(ContactDraft(phones = listOf(EditableValue(1, " ", MOBILE))).isEmpty)
        assertTrue(!ContactDraft(company = "Сервис доставки").isEmpty)
    }

    @Test
    fun деньРождения() {
        assertEquals(ContactBirthday.Parts(1988, 12, 5), ContactBirthday.parse("1988-12-05"))
        assertEquals(ContactBirthday.Parts(null, 12, 5), ContactBirthday.parse("--12-05"))
        assertEquals(ContactBirthday.Parts(1988, 12, 5), ContactBirthday.parse("1988-12-05T00:00:00Z"))
        assertEquals(null, ContactBirthday.parse("зимой"))
        assertEquals("--03-08", ContactBirthday.format(ContactBirthday.Parts(null, 3, 8)))
        assertEquals("1990-03-08", ContactBirthday.format(ContactBirthday.Parts(1990, 3, 8)))
    }
}
