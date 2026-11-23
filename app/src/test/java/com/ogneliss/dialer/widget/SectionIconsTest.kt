package com.ogneliss.dialer.widget

import com.ogneliss.dialer.widget.SectionIcons.Reconcile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Сверка значков «SMS» и «Контакты» при запуске (2.9.4, форум: значки пропали
 * после обновления). Приложение само их не убирает — только переключатель.
 */
class SectionIconsTest {

    @Test
    fun `значки есть, а настройка выключена — значки остаются, настройка включается`() {
        assertEquals(Reconcile.REMEMBER_ON, SectionIcons.reconcile(settingOn = false, shownByAndroid = true))
    }

    @Test
    fun `настройка включена, а значков нет — значки возвращаются`() {
        assertEquals(Reconcile.SHOW, SectionIcons.reconcile(settingOn = true, shownByAndroid = false))
    }

    @Test
    fun `совпадают — ничего не делается`() {
        assertEquals(Reconcile.NOTHING, SectionIcons.reconcile(settingOn = true, shownByAndroid = true))
        assertEquals(Reconcile.NOTHING, SectionIcons.reconcile(settingOn = false, shownByAndroid = false))
    }
}
