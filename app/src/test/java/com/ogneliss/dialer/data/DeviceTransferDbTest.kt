package com.ogneliss.dialer.data

import androidx.room.Room
import com.ogneliss.dialer.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Уборка после переноса (2.7.1) — на настоящей схеме базы: опечатка в имени
 * таблицы или столбца всплыла бы только на новом телефоне, при первом запуске.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceTransferDbTest {

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun close() = db.close()

    private fun count(sql: String): Long =
        db.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun `избранное остаётся текстом, но ключ ни с чем не совпадает, повтор не добавляет приставку`() {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO sms_starred (msgKey, address, body, date, outgoing, starredAt) " +
                "VALUES ('sms:15', '+79001112233', 'код 1234', 1000, 0, 2000)"
        )
        DeviceTransfer.forgetDeviceBoundRows(db)
        DeviceTransfer.forgetDeviceBoundRows(db)
        assertEquals(1L, count("SELECT COUNT(*) FROM sms_starred WHERE msgKey = 'moved:sms:15'"))
        assertEquals(0L, count("SELECT COUNT(*) FROM sms_starred WHERE msgKey = 'sms:15'"))
    }

    @Test
    fun `отложенные SMS, кэш контактов и T9-индекс убираются, черновики остаются`() {
        val w = db.openHelper.writableDatabase
        w.execSQL(
            "INSERT INTO sms_scheduled (address, addressKey, body, subId, atMillis, scheduled) " +
                "VALUES ('+79001112233', '9001112233', 'с днём рождения', NULL, 5000, 1)"
        )
        w.execSQL("INSERT INTO sms_drafts (address, body, updatedAt) VALUES ('+79001112233', 'черновик', 1)")
        DeviceTransfer.forgetDeviceBoundRows(db)
        assertEquals(0L, count("SELECT COUNT(*) FROM sms_scheduled"))
        assertEquals(1L, count("SELECT COUNT(*) FROM sms_drafts"))
        assertEquals(0L, count("SELECT COUNT(*) FROM contact_cache"))
        assertEquals(0L, count("SELECT COUNT(*) FROM t9_index"))
    }
}
