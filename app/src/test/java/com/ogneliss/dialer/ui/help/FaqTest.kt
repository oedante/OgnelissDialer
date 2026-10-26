package com.ogneliss.dialer.ui.help

import com.ogneliss.dialer.ui.settings.SettingsCategory
import com.ogneliss.dialer.ui.settings.SettingsRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «ВОПРОСЫ И ОТВЕТЫ» (1.6.0): ссылки ответов ведут туда, где что-то есть.
 *
 * Пункты настроек связаны с ответами строковыми id: переименуют пункт
 * в реестре — компилятор промолчит, а кнопка под ответом перестанет
 * работать и путь в тексте пропадёт. Число подстановок в строках
 * на четырёх языках сверяет scripts/prebuild_check.py (ресурсы здесь
 * не читаются).
 */
class FaqTest {

    @Test
    fun `id вопросов уникальны и не пусты`() {
        val ids = Faq.items.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '_' } })
    }

    @Test
    fun `в каждой группе есть вопросы`() {
        assertTrue(Faq.sections.isNotEmpty())
        Faq.sections.forEach { assertTrue(it.items.isNotEmpty()) }
        assertEquals(Faq.sections.sumOf { it.items.size }, Faq.items.size)
    }

    @Test
    fun `ссылки на пункты настроек ведут на существующие пункты`() {
        Faq.items.forEach { item ->
            item.links.filterIsInstance<FaqLink.Setting>().forEach { link ->
                assertNotNull("${item.id} → ${link.entryId}", SettingsRegistry.byId(link.entryId))
            }
        }
    }

    @Test
    fun `путь в тексте только у мест в настройках`() {
        assertTrue(FaqLink.Setting("battery").inText)
        assertTrue(FaqLink.Section(SettingsCategory.SPAM).inText)
        assertFalse(FaqLink.Mail.inText)
        assertFalse(FaqLink.SourceCode.inText)
        assertFalse(FaqLink.DefaultDialer.inText)
    }

    @Test
    fun `путь без кнопки не прячет кнопку остальным ссылкам`() {
        assertFalse(FaqLink.Setting("backup_import", button = false).isButton)
        assertTrue(FaqLink.Setting("backup_export").isButton)
        assertTrue(FaqLink.Mail.isButton)
        // У каждого ответа со ссылками есть хотя бы одна кнопка
        Faq.items.filter { it.links.isNotEmpty() }.forEach { item ->
            assertTrue(item.id, item.links.any { it.isButton })
        }
    }

    @Test
    fun `поиск вопроса по id`() {
        assertEquals("slide", Faq.byId("slide")?.id)
        assertNull(Faq.byId("нет такого"))
        assertNull(Faq.byId(null))
    }
}
