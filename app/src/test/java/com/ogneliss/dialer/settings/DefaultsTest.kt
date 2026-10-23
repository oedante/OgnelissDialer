package com.ogneliss.dialer.settings

import com.ogneliss.dialer.ui.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ЗНАЧЕНИЙ ПО УМОЛЧАНИЮ.
 *
 * ── Зачем фиксировать умолчания тестом ─────────────────────────────────────
 * Умолчание задаётся в двух местах: в `Prefs` для чтения из хранилища
 * и в `SettingsState` как начальное значение до его загрузки. Разойтись
 * они могут незаметно — экран покажет одно, а хранилище вернёт другое,
 * и настройка на долю секунды «мигнёт» при запуске.
 *
 * Здесь проверяется вторая половина: значения `SettingsState`.
 * Соответствие первой обеспечивается тем, что обе константы правятся
 * вместе, а тест напоминает об этом при расхождении.
 */
class DefaultsTest {

    private val defaults = SettingsState()

    @Test
    fun `по умолчанию объединяются все вызовы абонента`() {
        /*
         * Режим «подряд идущие» показывал одного человека несколькими
         * карточками: позвонил утром, потом кто-то другой, потом он же
         * вечером. Журнал выглядел длиннее, чем есть, и найти нужного
         * человека было труднее.
         */
        assertEquals(GroupingMode.BY_CONTACT, defaults.groupingMode)
    }

    @Test
    fun `фильтрация спама выключена по умолчанию`() {
        // Ложное срабатывание вредит сильнее, чем один пропущенный
        // спам-звонок: заблокированным окажется звонок из поликлиники
        assertFalse(defaults.blockNotInContacts)
        assertFalse(defaults.blockHidden)
    }

    @Test
    fun `клавиатура при запуске свёрнута`() {
        // Приложение чаще открывают посмотреть, кто звонил,
        // чем набрать номер
        assertFalse(defaults.dialpadOpenAtStart)
    }

    @Test
    fun `цвета из обоев выключены`() {
        // Результат непредсказуем: при одних обоях интерфейс
        // контрастный, при других блёклый, проверить заранее нельзя
        assertFalse(defaults.dynamicColor)
    }

    @Test
    fun `заметки не удаляются автоматически`() {
        // Заметка — то, что человек написал сам, и стирать её
        // без спроса нельзя
        assertEquals(
            com.ogneliss.dialer.data.NotesRetention.Period.NEVER,
            defaults.notesRetention
        )
    }

    @Test
    fun `портрет на экране вызова крупный, ответ — касанием`() {
        // 1.5.8: «Крупный» занимает пустую полосу под портретом (обзор, П4);
        // ответ сдвигом — по выбору, привычку без спроса не меняем (У6)
        assertEquals(CallPhotoSize.LARGE, defaults.callPhotoSize)
        assertEquals(CallPhotoSize.LARGE, CallPhotoSize.from(null))
        assertEquals(AnswerMode.TAP, defaults.answerMode)
        assertEquals(AnswerMode.TAP, AnswerMode.from(null))
    }

    @Test
    fun `отклик клавиш включён`() {
        // Набирают вслепую, и подтверждение нажатия нужно
        assertTrue(defaults.haptic)
    }

    @Test
    fun `экран после звонка выключен по умолчанию`() {
        /*
         * Окно появляется, когда разговор окончен и человек уже
         * переключился на другое дело. Перехватывать внимание,
         * которое ему больше не принадлежит, — плохая цена
         * за возможность, доступную и без него: заметку можно
         * записать из карточки или свайпом.
         */
        assertFalse(defaults.postCallEnabled)
    }

    @Test
    fun `экран после звонка показывается только для незнакомых`() {
        // Всплывающее окно после каждого звонка родным быстро надоедает
        assertTrue(defaults.postCallUnknownOnly)
    }
}
