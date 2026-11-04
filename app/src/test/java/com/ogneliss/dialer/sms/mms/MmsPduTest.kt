package com.ogneliss.dialer.sms.mms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Кодек MMS (2.0.0): уведомление, скачанное сообщение, отправка
 * и «получено». Байты собраны по OMA-TS-MMS_ENC 1.2 вручную —
 * так, как их присылает центр MMS.
 */
class MmsPduTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun text(s: String) = s.toByteArray(Charsets.UTF_8) + byteArrayOf(0)

    /** Уведомление: тип, транзакция, версия, от кого, класс, размер, срок, адрес. */
    private fun notification(): ByteArray {
        val from = "+79001234567/TYPE=PLMN"
        val fromValue = bytes(0x80) + text(from)
        return bytes(0x8C, 0x82) +
            bytes(0x98) + text("T42") +
            bytes(0x8D, 0x92) +
            bytes(0x89, fromValue.size) + fromValue +
            bytes(0x8A, 0x80) +
            bytes(0x8E, 0x02, 0x30, 0x39) +
            bytes(0x88, 0x05, 0x81, 0x03, 0x02, 0xA3, 0x00) +
            bytes(0x83) + text("http://mmsc.example/abc")
    }

    @Test
    fun `уведомление — адрес скачивания, отправитель, размер`() {
        val n = MmsPdu.parseNotification(notification())
        assertNotNull(n)
        n!!
        assertEquals("T42", n.transactionId)
        assertEquals("http://mmsc.example/abc", n.contentLocation)
        assertEquals("+79001234567", n.from)
        assertEquals(12345L, n.size)
        assertEquals(172800L, n.expirySeconds)
        assertEquals("personal", n.messageClass)
    }

    @Test
    fun `неизвестный заголовок пропускается, а не ломает разбор`() {
        // Перед адресом — X-Mms-Store (0xA2, байт) и заголовок приложения
        val extra = bytes(0xA2, 0x80) + text("X-Custom") + text("value")
        val pdu = notification()
        val cut = pdu.size - text("http://mmsc.example/abc").size - 1
        val patched = pdu.copyOfRange(0, cut) + extra + pdu.copyOfRange(cut, pdu.size)
        assertEquals("http://mmsc.example/abc", MmsPdu.parseNotification(patched)?.contentLocation)
    }

    @Test
    fun `не уведомление и мусор — null`() {
        assertNull(MmsPdu.parseNotification(bytes(0x8C, 0x84)))
        assertNull(MmsPdu.parseNotification(ByteArray(0)))
        assertNull(MmsPdu.parseNotification(bytes(0x8C)))
        assertNull(MmsPdu.parseMessage(bytes(0x31, 0x32)))
    }

    @Test
    fun `отправка — получатели, SMIL, картинка и текст по-русски`() {
        val image = MmsPdu.Part("image/jpeg", byteArrayOf(1, 2, 3, 4, 5), name = "photo.jpg")
        val pdu = MmsPdu.composeSendReq(
            to = listOf("+7 900 123-45-67", "+79007654321"),
            text = "Привет, это MMS",
            attachments = listOf(image),
            transactionId = "tx-1"
        )
        val m = MmsPdu.parseMessage(pdu)
        assertNotNull(m)
        m!!
        assertEquals(MmsPdu.TYPE_SEND_REQ, m.type)
        assertEquals("tx-1", m.transactionId)
        assertEquals(listOf("+79001234567", "+79007654321"), m.to)
        assertNull(m.from)
        assertEquals(MmsPdu.CT_MULTIPART_RELATED, m.contentType)
        assertEquals(3, m.parts.size)
        assertTrue(m.parts[0].isSmil)
        assertTrue(m.parts[0].decoded().contains("image0.jpg"))
        assertEquals("photo.jpg", m.parts[1].name)
        assertEquals("image/jpeg", m.parts[1].contentType)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), m.parts[1].data)
        assertEquals("<image0>", m.parts[1].contentId)
        assertEquals("Привет, это MMS", m.bodyText())
    }

    @Test
    fun `скачанное сообщение — отправитель, тема, части`() {
        // Собираем как отправку и меняем тип на m-retrieve-conf, вставив
        // отправителя и тему — так выглядит скачанное MMS
        val send = MmsPdu.composeSendReq(listOf("+79001234567"), "Текст", emptyList(), "tx-2")
        val from = bytes(0x80) + text("+79005556677/TYPE=PLMN")
        val subject = bytes(0x96) + text("Тема")
        val date = bytes(0x85, 0x04, 0x65, 0x4F, 0x1A, 0x80)
        val retrieve = bytes(0x8C, 0x84) + bytes(0x8B) + text("msg-1") + date +
            bytes(0x89, from.size) + from + subject + send.copyOfRange(2, send.size)
        val m = MmsPdu.parseMessage(retrieve)!!
        assertEquals(MmsPdu.TYPE_RETRIEVE_CONF, m.type)
        assertEquals("msg-1", m.messageId)
        assertEquals("+79005556677", m.from)
        assertEquals("Тема", m.subject)
        assertEquals(0x654F1A80L, m.dateSeconds)
        assertEquals("Текст", m.bodyText())
        assertEquals(2, m.parts.size) // SMIL и текст
    }

    @Test
    fun `тело одной частью — тоже читается`() {
        val pdu = bytes(0x8C, 0x84, 0x8D, 0x92, 0x84, 0x83) + "plain".toByteArray()
        val m = MmsPdu.parseMessage(pdu)!!
        assertEquals(1, m.parts.size)
        assertEquals("plain", m.bodyText())
    }

    @Test
    fun `получено — тип, транзакция, статус`() {
        val pdu = MmsPdu.composeNotifyResp("T42")
        val m = MmsPdu.parseMessage(pdu)!!
        assertEquals(MmsPdu.TYPE_NOTIFYRESP_IND, m.type)
        assertEquals("T42", m.transactionId)
        assertEquals(MmsPdu.STATUS_RETRIEVED, m.status)
    }

    @Test
    fun `ответ на отправку — статус и номер сообщения`() {
        val conf = bytes(0x8C, 0x81) + bytes(0x98) + text("tx-1") + bytes(0x8D, 0x92) +
            bytes(0x92, 0x80) + bytes(0x8B) + text("mid-77")
        val m = MmsPdu.parseMessage(conf)!!
        assertEquals(MmsPdu.TYPE_SEND_CONF, m.type)
        assertEquals(MmsPdu.RESPONSE_OK, m.responseStatus)
        assertEquals("mid-77", m.messageId)
    }

    @Test
    fun `тема в кодировке windows-1251 и длинные числа`() {
        val cp = "Отчёт".toByteArray(charset("windows-1251"))
        // Encoded-string-value: длина, charset 2251 как Long-integer (2 байта), строка
        val enc = bytes(0x02, 0x08, 0xCB) + cp + byteArrayOf(0)
        val pdu = bytes(0x8C, 0x84, 0x96, enc.size) + enc
        assertEquals("Отчёт", MmsPdu.parseMessage(pdu)!!.subject)
    }

    @Test
    fun `адреса — без TYPE=PLMN и в виде протокола`() {
        assertEquals("+79001234567", MmsPdu.cleanAddress("+79001234567/TYPE=PLMN"))
        assertEquals("+79001234567/TYPE=PLMN", MmsPdu.addressOf("+7 (900) 123-45-67"))
        assertEquals("user@example.org", MmsPdu.addressOf("user@example.org"))
    }

    @Test
    fun `большие части — длины uintvar в несколько байт`() {
        val big = ByteArray(70_000) { (it % 251).toByte() }
        val pdu = MmsPdu.composeSendReq(listOf("+79001234567"), "", listOf(MmsPdu.Part("image/png", big)), "tx-3")
        val m = MmsPdu.parseMessage(pdu)!!
        assertArrayEquals(big, m.parts.first { it.isImage }.data)
        assertEquals("", m.bodyText())
    }
}
