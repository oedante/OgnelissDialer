package com.ogneliss.dialer.data

import com.ogneliss.dialer.data.DeviceTransfer.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Перенос на новый телефон (2.7.1): когда считать, что данные приехали,
 * и что правило переноса пускает на новый телефон.
 */
class DeviceTransferTest {

    @Test
    fun `файл метки есть — тот же телефон, что бы ни было в настройках`() {
        assertEquals(Origin.SAME_DEVICE, DeviceTransfer.origin(true, "abc", hasData = true, freshInstall = true))
        assertEquals(Origin.SAME_DEVICE, DeviceTransfer.origin(true, null, hasData = true, freshInstall = false))
    }

    @Test
    fun `метка в настройках без файла — данные приехали переносом`() {
        assertEquals(Origin.MOVED, DeviceTransfer.origin(false, "abc", hasData = true, freshInstall = true))
        assertEquals(Origin.MOVED, DeviceTransfer.origin(false, "abc", hasData = true, freshInstall = false))
    }

    @Test
    fun `обновление с версии без метки — не перенос`() {
        assertEquals(Origin.FIRST_RUN, DeviceTransfer.origin(false, null, hasData = true, freshInstall = false))
    }

    @Test
    fun `новая установка без данных — первый запуск`() {
        assertEquals(Origin.FIRST_RUN, DeviceTransfer.origin(false, null, hasData = false, freshInstall = true))
    }

    @Test
    fun `новая установка, а данные уже лежат — перенос со старой версии`() {
        assertEquals(Origin.MOVED, DeviceTransfer.origin(false, null, hasData = true, freshInstall = true))
    }

    @Test
    fun `правило переноса — облако закрыто, на новый телефон только данные человека`() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/data_extraction_rules.xml"))
        fun section(name: String) = doc.getElementsByTagName(name).item(0) as org.w3c.dom.Element
        fun rules(section: org.w3c.dom.Element, tag: String): Set<String> {
            val list = section.getElementsByTagName(tag)
            return (0 until list.length).map {
                val e = list.item(it) as org.w3c.dom.Element
                e.getAttribute("domain") + ":" + e.getAttribute("path")
            }.toSet()
        }

        val cloud = section("cloud-backup")
        assertEquals(emptySet<String>(), rules(cloud, "include"))
        val allDomains = listOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref"
        )
        allDomains.forEach { assertTrue("облако: $it", "$it:" in rules(cloud, "exclude")) }

        val transfer = section("device-transfer")
        assertEquals(
            setOf("database:", "file:datastore/", "sharedpref:sms_settings.xml"),
            rules(transfer, "include")
        )
    }
}
